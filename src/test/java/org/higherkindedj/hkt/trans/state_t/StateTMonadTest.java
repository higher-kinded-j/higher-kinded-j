package org.higherkindedj.hkt.trans.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy; // Added
import static org.higherkindedj.hkt.optional.OptionalKindHelper.unwrap;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;

// --- Helper Stub Monads ---

// Witness type for a simple identity-like monad
final class IdKind<A> implements Kind<IdKind.Witness, A> {
  public static class Witness {}

  public final @Nullable A value;

  private IdKind(@Nullable A value) {
    this.value = value;
  }

  public static <A> IdKind<A> of(@Nullable A value) {
    return new IdKind<>(value);
  }

  public static <A> IdKind<A> narrow(Kind<Witness, A> kind) {
    return (IdKind<A>) kind;
  }
}

class NonErrorMonad implements Monad<IdKind.Witness> {
  @Override
  public <A> @NonNull Kind<IdKind.Witness, A> of(@Nullable A a) {
    return IdKind.of(a);
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<IdKind.Witness, A> fa) {
    return IdKind.of(f.apply(IdKind.narrow(fa).value));
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> ap(
      @NonNull Kind<IdKind.Witness, Function<A, B>> ff, @NonNull Kind<IdKind.Witness, A> fa) {
    assert IdKind.narrow(ff).value != null;
    return IdKind.of(IdKind.narrow(ff).value.apply(IdKind.narrow(fa).value));
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> flatMap(
      @NonNull Function<A, Kind<IdKind.Witness, B>> f, @NonNull Kind<IdKind.Witness, A> fa) {
    return f.apply(IdKind.narrow(fa).value);
  }
}

class StringErrorMonad implements MonadError<IdKind.Witness, String> {
  @Override
  public <A> @NonNull Kind<IdKind.Witness, A> of(@Nullable A a) {
    return IdKind.of(a);
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<IdKind.Witness, A> fa) {
    IdKind<A> idFa = IdKind.narrow(fa);
    if (idFa.value == null) return IdKind.of(null);
    return IdKind.of(f.apply(idFa.value));
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> ap(
      @NonNull Kind<IdKind.Witness, Function<A, B>> ff, @NonNull Kind<IdKind.Witness, A> fa) {
    IdKind<Function<A, B>> idFf = IdKind.narrow(ff);
    IdKind<A> idFa = IdKind.narrow(fa);
    if (idFf.value == null || idFa.value == null) return IdKind.of(null);
    return IdKind.of(idFf.value.apply(idFa.value));
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> flatMap(
      @NonNull Function<A, Kind<IdKind.Witness, B>> f, @NonNull Kind<IdKind.Witness, A> fa) {
    IdKind<A> idFa = IdKind.narrow(fa);
    if (idFa.value == null) return IdKind.of(null);
    return f.apply(idFa.value);
  }

  @Override
  public <A> @NonNull Kind<IdKind.Witness, A> raiseError(@Nullable String error) {
    // If StateTMonad calls this with 'null' (treating us like a MonadError<F, Void>),
    // but we are a MonadError<F, String> that expects a non-null string or has
    // a specific representation for its errors, receiving 'null' could be a type error.
    // We simulate this by throwing a ClassCastException if null is passed.
    if (error == null) {
      throw new ClassCastException(
          "Simulated CCE: Null received by StringErrorMonad.raiseError, "
              + "which expects a String representation or has specific null handling "
              + "incompatible with being treated as MonadError<F, Void>.raiseError(null).");
    }
    return IdKind.of(null); // Still represent the error state with a null IdKind value
  }

  @Override
  public <A> @NonNull Kind<IdKind.Witness, A> handleErrorWith(
      @NonNull Kind<IdKind.Witness, A> ma,
      @NonNull Function<String, Kind<IdKind.Witness, A>> handler) {
    IdKind<A> idMa = IdKind.narrow(ma);
    if (idMa.value != null) {
      return ma;
    }
    return handler.apply("SIMULATED_ERROR_VALUE_FOR_HANDLER");
  }
}

// --- End Helper Stub Monads ---

@DisplayName("StateTMonad Tests (F=Optional, S=Integer)")
class StateTMonadTest {

  private Monad<OptionalKind<?>> optMonad;
  private StateTMonad<Integer, OptionalKind<?>> stateTMonad;
  private final Integer initialState = 10;

  private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> mValue;
  private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> mEmpty;
  private Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>> f;
  private Function<String, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>> g;

  private <A> Optional<StateTuple<Integer, A>> runOptStateT(
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> kind, Integer startState) {
    Kind<OptionalKind<?>, StateTuple<Integer, A>> resultKind =
        StateTKindHelper.<Integer, OptionalKind<?>, A>runStateT(kind, startState);
    return unwrap(resultKind);
  }

  private <A> Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> createStateTKindForOptional(
      Function<Integer, Kind<OptionalKind<?>, StateTuple<Integer, A>>> runFn) {
    if (optMonad == null) {
      throw new IllegalStateException("optMonad must be initialized before creating StateTKind");
    }
    StateT<Integer, OptionalKind<?>, A> stateT =
        StateTKindHelper.<Integer, OptionalKind<?>, A>stateT(runFn, optMonad);
    return StateTKindHelper.<Integer, OptionalKind<?>, A>wrap(stateT);
  }

  @BeforeEach
  void setUp() {
    optMonad = new OptionalMonad();
    stateTMonad = StateTMonad.instance(optMonad);

    Function<Integer, String> intToString = Object::toString;
    mValue = createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, s * 10)));
    mEmpty = createStateTKindForOptional(s -> OptionalKindHelper.wrap(Optional.empty()));
    f = i -> createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + i, "v" + i)));
    g =
        str ->
            createStateTKindForOptional(
                s -> optMonad.of(StateTuple.of(s + str.length(), str + "!")));
  }

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateStateTReturningValueAndUnchangedStateInOptional() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kind =
          stateTMonad.of("constantValue");
      Optional<StateTuple<Integer, String>> result = runOptStateT(kind, initialState);

      assertThat(result).isPresent().contains(StateTuple.of(initialState, "constantValue"));
    }

    @Test
    void of_shouldWrapNullAsPresentOptionalHoldingTupleWithNullValue() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kind = stateTMonad.of(null);
      Optional<StateTuple<Integer, String>> result = runOptStateT(kind, initialState);
      assertThat(result).isPresent().contains(StateTuple.of(initialState, null));
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> initialKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> emptyKind;

    @BeforeEach
    void setUpMapTests() {
      initialKind = createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, s * 2)));
      emptyKind = createStateTKindForOptional(s -> OptionalKindHelper.wrap(Optional.empty()));
    }

    @Test
    void map_shouldApplyFunctionToValueWhenOuterIsPresent() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> mappedKind =
          stateTMonad.map(i -> "V:" + i, initialKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(mappedKind, initialState);
      assertThat(result)
          .isPresent()
          .contains(StateTuple.of(initialState + 1, "V:" + (initialState * 2)));
    }

    @Test
    void map_shouldPropagateOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> mappedKind =
          stateTMonad.map(i -> "V:" + i, emptyKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(mappedKind, initialState);
      assertThat(result).isEmpty();
    }

    @Test
    void map_shouldHandleMappingValueToNullAsPresentOptionalHoldingTupleWithNullValue() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> mappedKind =
          stateTMonad.map(i -> null, initialKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(mappedKind, initialState);
      assertThat(result).isPresent().contains(StateTuple.of(initialState + 1, null));
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Function<Integer, String>> funcKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> valKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> emptyValKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Function<Integer, String>>
        emptyFuncKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Function<Integer, String>>
        nullFuncKind;

    @BeforeEach
    void setUpApTests() {
      funcKind =
          createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, i -> "F" + i + s)));
      valKind = createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s * 2, s + 10)));
      emptyValKind = createStateTKindForOptional(s -> OptionalKindHelper.wrap(Optional.empty()));
      emptyFuncKind = createStateTKindForOptional(s -> OptionalKindHelper.wrap(Optional.empty()));
      nullFuncKind = createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, null)));
    }

    @Test
    void ap_shouldApplyPresentFuncToPresentValue() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.ap(funcKind, valKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(resultKind, 5);
      assertThat(result).isPresent().contains(StateTuple.of(12, "F165"));
    }

    @Test
    void ap_shouldReturnEmptyIfFuncOuterIsEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.ap(emptyFuncKind, valKind);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfValueOuterIsEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.ap(funcKind, emptyValKind);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfFunctionInTupleIsNull() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.ap(nullFuncKind, valKind);
      assertThat(runOptStateT(resultKind, 5)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    /* ... existing tests ... */
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> initialKindFlatMap;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> emptyKindFlatMap;
    private Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>> fFlatMap;
    private Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>>
        fEmptyFlatMap;

    @BeforeEach
    void setUpFlatMapTests() {
      initialKindFlatMap =
          createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, s * 2)));
      emptyKindFlatMap =
          createStateTKindForOptional(s -> OptionalKindHelper.wrap(Optional.empty()));
      fFlatMap =
          i -> createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + i, "Val:" + i)));
      fEmptyFlatMap =
          i -> createStateTKindForOptional(s -> OptionalKindHelper.wrap(Optional.empty()));
    }

    @Test
    void flatMap_shouldSequenceComputations() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.flatMap(fFlatMap, initialKindFlatMap);
      Optional<StateTuple<Integer, String>> result = runOptStateT(resultKind, 10);
      assertThat(result).isPresent().contains(StateTuple.of(31, "Val:20"));
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyFromInitial() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.flatMap(fFlatMap, emptyKindFlatMap);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyFromFunction() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.flatMap(fEmptyFlatMap, initialKindFlatMap);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }
  }

  private <A> void assertStateTEquals(
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> k1,
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> k2) {
    Integer s1 = initialState;
    Integer s2 = initialState + 5;
    Integer s3 = -1;
    assertThat(runOptStateT(k1, s1)).as("State %d", s1).isEqualTo(runOptStateT(k2, s1));
    assertThat(runOptStateT(k1, s2)).as("State %d", s2).isEqualTo(runOptStateT(k2, s2));
    assertThat(runOptStateT(k1, s3)).as("State %d", s3).isEqualTo(runOptStateT(k2, s3));
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      int value = 5;
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> ofValue = stateTMonad.of(value);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> leftSide =
          stateTMonad.flatMap(f, ofValue);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> rightSide = f.apply(value);
      assertStateTEquals(leftSide, rightSide);
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer>> ofFunc =
          stateTMonad::of;
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> leftSideSuccess =
          stateTMonad.flatMap(ofFunc, mValue);
      assertStateTEquals(leftSideSuccess, mValue);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> leftSideEmpty =
          stateTMonad.flatMap(ofFunc, mEmpty);
      assertStateTEquals(leftSideEmpty, mEmpty);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> innerSuccess =
          stateTMonad.flatMap(f, mValue);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> leftSideSuccess =
          stateTMonad.flatMap(g, innerSuccess);
      Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>>
          rightSideInnerFunc = a -> stateTMonad.flatMap(g, f.apply(a));
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> rightSideSuccess =
          stateTMonad.flatMap(rightSideInnerFunc, mValue);
      assertStateTEquals(leftSideSuccess, rightSideSuccess);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> innerEmpty =
          stateTMonad.flatMap(f, mEmpty);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> leftSideEmpty =
          stateTMonad.flatMap(g, innerEmpty);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> rightSideEmpty =
          stateTMonad.flatMap(rightSideInnerFunc, mEmpty);
      assertStateTEquals(leftSideEmpty, rightSideEmpty);
    }
  }

  private <S, V> Kind<StateTKind.Witness<S, IdKind.Witness>, V> createStateTIdKind(
      Monad<IdKind.Witness> idMonad, Function<S, Kind<IdKind.Witness, StateTuple<S, V>>> runFn) {
    StateT<S, IdKind.Witness, V> stateT =
        StateTKindHelper.<S, IdKind.Witness, V>stateT(runFn, idMonad);
    return StateTKindHelper.<S, IdKind.Witness, V>wrap(stateT);
  }

  @Nested
  @DisplayName("StateTMonad Constructor and Error Handling Path Tests")
  class ConstructorAndErrorPathTests {

    private final Integer localInitialState = 10;

    @Test
    @DisplayName("constructor should set monadErrorF to null if underlying monad is not MonadError")
    void constructorWithNonMonadError() {
      NonErrorMonad nonErrorMonad = new NonErrorMonad();
      StateTMonad<Integer, IdKind.Witness> stateTWithNonErrorMonad =
          StateTMonad.instance(nonErrorMonad);

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> kind =
          stateTWithNonErrorMonad.of("testValue");
      Kind<IdKind.Witness, StateTuple<Integer, String>> resultWrapped =
          StateTKindHelper.<Integer, IdKind.Witness, String>runStateT(kind, localInitialState);
      StateTuple<Integer, String> resultTuple = IdKind.narrow(resultWrapped).value;

      Assertions.assertNotNull(resultTuple);
      assertThat(resultTuple.state()).isEqualTo(localInitialState);
      assertThat(resultTuple.value()).isEqualTo("testValue");
    }

    @Test
    @DisplayName(
        "ap should throw IllegalStateException when monadErrorF is null and function is null")
    void ap_throwsIllegalStateWhenMonadErrorFIsNullAndFunctionIsNull() {
      NonErrorMonad nonErrorMonad = new NonErrorMonad();
      StateTMonad<Integer, IdKind.Witness> stateTWithNonErrorMonad =
          StateTMonad.instance(nonErrorMonad);

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, Function<String, String>> nullFuncKind =
          createStateTIdKind(nonErrorMonad, s -> nonErrorMonad.of(StateTuple.of(s, null)));

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> valKind =
          createStateTIdKind(nonErrorMonad, s -> nonErrorMonad.of(StateTuple.of(s, "value")));

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> resultKind =
          stateTWithNonErrorMonad.ap(nullFuncKind, valKind);

      assertThatThrownBy(
              () ->
                  StateTKindHelper.<Integer, IdKind.Witness, String>runStateT(
                      resultKind, localInitialState))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("MonadError<F> instance not available");
    }

    @Test
    @DisplayName(
        "ap should throw IllegalStateException via ClassCastException for non-Void MonadError")
    void ap_throwsIllegalStateFromClassCastExceptionForNonVoidErrorType() {
      StringErrorMonad stringErrorMonad = new StringErrorMonad();
      StateTMonad<Integer, IdKind.Witness> stateTWithStringErrorMonad =
          StateTMonad.instance(stringErrorMonad);

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, Function<String, String>> nullFuncKind =
          createStateTIdKind(stringErrorMonad, s -> stringErrorMonad.of(StateTuple.of(s, null)));

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> valKind =
          createStateTIdKind(stringErrorMonad, s -> stringErrorMonad.of(StateTuple.of(s, "value")));

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> resultKind =
          stateTWithStringErrorMonad.ap(nullFuncKind, valKind);

      assertThatThrownBy(
              () ->
                  StateTKindHelper.<Integer, IdKind.Witness, String>runStateT(
                      resultKind, localInitialState))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "Underlying MonadError<F> does not have Void error type as expected.")
          .hasCauseInstanceOf(ClassCastException.class);
    }
  }
}
