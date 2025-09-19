// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.optional.OptionalKind;
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
      @NonNull Function<? super A, ? extends B> f, @NonNull Kind<IdKind.Witness, A> fa) {
    return IdKind.of(f.apply(IdKind.narrow(fa).value));
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> ap(
      @NonNull Kind<IdKind.Witness, ? extends Function<A, B>> ff,
      @NonNull Kind<IdKind.Witness, A> fa) {
    assert IdKind.narrow(ff).value != null;
    return IdKind.of(IdKind.narrow(ff).value.apply(IdKind.narrow(fa).value));
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> flatMap(
      @NonNull Function<? super A, ? extends Kind<IdKind.Witness, B>> f,
      @NonNull Kind<IdKind.Witness, A> fa) {
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
      @NonNull Function<? super A, ? extends B> f, @NonNull Kind<IdKind.Witness, A> fa) {
    IdKind<A> idFa = IdKind.narrow(fa);
    if (idFa.value == null) {
      f.apply(null);
    }
    if (idFa.value == null) return IdKind.of(f.apply(null)); // if map(null) is allowed
    return IdKind.of(f.apply(idFa.value));
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> ap(
      @NonNull Kind<IdKind.Witness, ? extends Function<A, B>> ff,
      @NonNull Kind<IdKind.Witness, A> fa) {
    IdKind<? extends Function<A, B>> idFf = IdKind.narrow(ff);
    IdKind<A> idFa = IdKind.narrow(fa);
    if (idFf.value == null) throw new NullPointerException("Function in ap is null");
    // If value is null, and function can handle null, it's okay.
    return IdKind.of(idFf.value.apply(idFa.value));
  }

  @Override
  public <A, B> @NonNull Kind<IdKind.Witness, B> flatMap(
      @NonNull Function<? super A, ? extends Kind<IdKind.Witness, B>> f,
      @NonNull Kind<IdKind.Witness, A> fa) {
    IdKind<A> idFa = IdKind.narrow(fa);
    // If value is null, function f might or might not handle it.
    return f.apply(idFa.value);
  }

  @Override
  public <A> @NonNull Kind<IdKind.Witness, A> raiseError(@Nullable String error) {
    if (error == null) {
      throw new ClassCastException(
          "Simulated CCE: Null received by StringErrorMonad.raiseError, "
              + "which expects a String representation or has specific null handling "
              + "incompatible with being treated as MonadError<F, Void>.raiseError(null).");
    }
    return IdKind.of(null);
  }

  @Override
  public <A> @NonNull Kind<IdKind.Witness, A> handleErrorWith(
      @NonNull Kind<IdKind.Witness, A> ma,
      @NonNull Function<? super String, ? extends Kind<IdKind.Witness, A>> handler) {
    IdKind<A> idMa = IdKind.narrow(ma);
    if (idMa.value != null) {
      return ma;
    }
    return handler.apply("SIMULATED_ERROR_VALUE_FOR_HANDLER");
  }
}

// --- End Helper Stub Monads ---

@DisplayName("StateTMonad Tests (F=OptionalKind.Witness, S=Integer)")
class StateTMonadTest {

  private Monad<OptionalKind.Witness> optMonad;
  private StateTMonad<Integer, OptionalKind.Witness> stateTMonad;
  private final Integer initialState = 10;

  private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> mValue;
  private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> mEmpty;

  private Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>> f;
  private Function<String, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>> g;

  private <A> Optional<StateTuple<Integer, A>> runOptStateT(
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, A> kind, Integer startState) {
    Kind<OptionalKind.Witness, StateTuple<Integer, A>> resultKind =
        STATE_T.runStateT(kind, startState);
    return OPTIONAL.narrow(resultKind);
  }

  private <A>
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, A> createStateTKindForOptional(
          Function<Integer, Kind<OptionalKind.Witness, StateTuple<Integer, A>>> runFn) {
    StateT<Integer, OptionalKind.Witness, A> stateT = STATE_T.stateT(runFn, optMonad);
    return STATE_T.widen(stateT);
  }

  @BeforeEach
  void setUp() {
    optMonad = OptionalMonad.INSTANCE;
    stateTMonad = StateTMonad.instance(optMonad);

    mValue = createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, s * 10)));
    mEmpty = createStateTKindForOptional(s -> OPTIONAL.widen(Optional.empty()));
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
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind =
          stateTMonad.of("constantValue");
      Optional<StateTuple<Integer, String>> result = runOptStateT(kind, initialState);

      assertThat(result).isPresent().contains(StateTuple.of(initialState, "constantValue"));
    }

    @Test
    void of_shouldWrapNullAsPresentOptionalHoldingTupleWithNullValue() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind = stateTMonad.of(null);
      Optional<StateTuple<Integer, String>> result = runOptStateT(kind, initialState);
      assertThat(result).isPresent().contains(StateTuple.of(initialState, null));
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> initialKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> emptyKind;

    @BeforeEach
    void setUpMapTests() {
      initialKind = createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, s * 2)));
      emptyKind = createStateTKindForOptional(s -> OPTIONAL.widen(Optional.empty()));
    }

    @Test
    void map_shouldApplyFunctionToValueWhenOuterIsPresent() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> mappedKind =
          stateTMonad.map(i -> "V:" + i, initialKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(mappedKind, initialState);
      assertThat(result)
          .isPresent()
          .contains(StateTuple.of(initialState + 1, "V:" + (initialState * 2)));
    }

    @Test
    void map_shouldPropagateOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> mappedKind =
          stateTMonad.map(i -> "V:" + i, emptyKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(mappedKind, initialState);
      assertThat(result).isEmpty();
    }

    @Test
    void map_shouldHandleMappingValueToNullAsPresentOptionalHoldingTupleWithNullValue() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> mappedKind =
          stateTMonad.map(i -> null, initialKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(mappedKind, initialState);
      assertThat(result).isPresent().contains(StateTuple.of(initialState + 1, null));
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Function<Integer, String>>
        funcKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> valKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> emptyValKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Function<Integer, String>>
        emptyFuncKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Function<Integer, String>>
        nullFuncKind;

    @BeforeEach
    void setUpApTests() {
      funcKind =
          createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, i -> "F" + i + s)));
      valKind = createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s * 2, s + 10)));
      emptyValKind = createStateTKindForOptional(s -> OPTIONAL.widen(Optional.empty()));
      emptyFuncKind = createStateTKindForOptional(s -> OPTIONAL.widen(Optional.empty()));
      nullFuncKind = createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, null)));
    }

    @Test
    void ap_shouldApplyPresentFuncToPresentValue() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> resultKind =
          stateTMonad.ap(funcKind, valKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(resultKind, 5);
      assertThat(result).isPresent().contains(StateTuple.of(12, "F165"));
    }

    @Test
    void ap_shouldReturnEmptyIfFuncOuterIsEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> resultKind =
          stateTMonad.ap(emptyFuncKind, valKind);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfValueOuterIsEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> resultKind =
          stateTMonad.ap(funcKind, emptyValKind);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }

    @Test
    @DisplayName("ap should throw NPE when function in tuple is null")
    void ap_shouldThrowNPEWhenFunctionInTupleIsNull() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> resultKind =
          stateTMonad.ap(nullFuncKind, valKind);

      assertThatThrownBy(() -> runOptStateT(resultKind, 5))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Function wrapped in StateT for 'ap' cannot be null");
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> initialKindFlatMap;
    private Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> emptyKindFlatMap;
    private Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>>
        fFlatMap;
    private Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>>
        fEmptyFlatMap;

    @BeforeEach
    void setUpFlatMapTests() {
      initialKindFlatMap =
          createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + 1, s * 2)));
      emptyKindFlatMap = createStateTKindForOptional(s -> OPTIONAL.widen(Optional.empty()));
      fFlatMap =
          i -> createStateTKindForOptional(s -> optMonad.of(StateTuple.of(s + i, "Val:" + i)));
      fEmptyFlatMap = i -> createStateTKindForOptional(s -> OPTIONAL.widen(Optional.empty()));
    }

    @Test
    void flatMap_shouldSequenceComputations() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> resultKind =
          stateTMonad.flatMap(fFlatMap, initialKindFlatMap);
      Optional<StateTuple<Integer, String>> result = runOptStateT(resultKind, 10);
      assertThat(result).isPresent().contains(StateTuple.of(31, "Val:20"));
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyFromInitial() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> resultKind =
          stateTMonad.flatMap(fFlatMap, emptyKindFlatMap);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyFromFunction() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> resultKind =
          stateTMonad.flatMap(fEmptyFlatMap, initialKindFlatMap);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }
  }

  private <A> void assertStateTEquals(
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, A> k1,
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, A> k2) {
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
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> ofValue =
          stateTMonad.of(value);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> leftSide =
          stateTMonad.flatMap(f, ofValue);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> rightSide = f.apply(value);
      assertStateTEquals(leftSide, rightSide);
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer>> ofFunc =
          stateTMonad::of;
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> leftSideSuccess =
          stateTMonad.flatMap(ofFunc, mValue);
      assertStateTEquals(leftSideSuccess, mValue);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> leftSideEmpty =
          stateTMonad.flatMap(ofFunc, mEmpty);
      assertStateTEquals(leftSideEmpty, mEmpty);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> innerSuccess =
          stateTMonad.flatMap(f, mValue);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> leftSideSuccess =
          stateTMonad.flatMap(g, innerSuccess);
      Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>>
          rightSideInnerFunc = a -> stateTMonad.flatMap(g, f.apply(a));
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> rightSideSuccess =
          stateTMonad.flatMap(rightSideInnerFunc, mValue);
      assertStateTEquals(leftSideSuccess, rightSideSuccess);

      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> innerEmpty =
          stateTMonad.flatMap(f, mEmpty);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> leftSideEmpty =
          stateTMonad.flatMap(g, innerEmpty);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> rightSideEmpty =
          stateTMonad.flatMap(rightSideInnerFunc, mEmpty);
      assertStateTEquals(leftSideEmpty, rightSideEmpty);
    }
  }

  @Nested
  @DisplayName("StateTMonad Constructor and Error Handling Path Tests")
  class ConstructorAndErrorPathTests {

    private final Integer localInitialState = 10;

    @Test
    @DisplayName("constructor should set monadErrorF to null if underlying monad is not MonadError")
    void constructorWithNonMonadError() {
      NonErrorMonad nonErrorMonad = new NonErrorMonad(); // This uses IdKind.Witness
      StateTMonad<Integer, IdKind.Witness> stateTWithNonErrorMonad =
          StateTMonad.instance(nonErrorMonad);

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> kind =
          stateTWithNonErrorMonad.of("testValue");
      Kind<IdKind.Witness, StateTuple<Integer, String>> resultWrapped =
          STATE_T.runStateT(kind, localInitialState);
      StateTuple<Integer, String> resultTuple = IdKind.narrow(resultWrapped).value;

      Assertions.assertNotNull(resultTuple);
      assertThat(resultTuple.state()).isEqualTo(localInitialState);
      assertThat(resultTuple.value()).isEqualTo("testValue");
    }

    private <S, V> Kind<StateTKind.Witness<S, IdKind.Witness>, V> createStateTIdKind(
        Monad<IdKind.Witness> idMonad, Function<S, Kind<IdKind.Witness, StateTuple<S, V>>> runFn) {
      StateT<S, IdKind.Witness, V> stateT = STATE_T.stateT(runFn, idMonad);
      return STATE_T.widen(stateT);
    }

    @Test
    @DisplayName("ap should throw NullPointerException when function is null")
    void ap_throwsNPEWhenFunctionIsNullRegardlessOfMonadError() {
      NonErrorMonad nonErrorMonad = new NonErrorMonad(); // Uses IdKind.Witness
      StateTMonad<Integer, IdKind.Witness> stateTWithNonErrorMonad =
          StateTMonad.instance(nonErrorMonad);

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, Function<String, String>> nullFuncKind =
          createStateTIdKind(nonErrorMonad, s -> nonErrorMonad.of(StateTuple.of(s, null)));

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> valKind =
          createStateTIdKind(nonErrorMonad, s -> nonErrorMonad.of(StateTuple.of(s, "value")));

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> resultKind =
          stateTWithNonErrorMonad.ap(nullFuncKind, valKind);

      assertThatThrownBy(() -> STATE_T.runStateT(resultKind, localInitialState))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function wrapped in StateT for 'ap' cannot be null");
    }

    @Test
    @DisplayName("ap should throw NullPointerException for null function even with MonadError")
    void ap_throwsNPEWhenFunctionIsNullForAnyMonadErrorType() {
      StringErrorMonad stringErrorMonad = new StringErrorMonad(); // Uses IdKind.Witness
      StateTMonad<Integer, IdKind.Witness> stateTWithStringErrorMonad =
          StateTMonad.instance(stringErrorMonad);

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, Function<String, String>> nullFuncKind =
          createStateTIdKind(stringErrorMonad, s -> stringErrorMonad.of(StateTuple.of(s, null)));

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> valKind =
          createStateTIdKind(stringErrorMonad, s -> stringErrorMonad.of(StateTuple.of(s, "value")));

      Kind<StateTKind.Witness<Integer, IdKind.Witness>, String> resultKind =
          stateTWithStringErrorMonad.ap(nullFuncKind, valKind);

      assertThatThrownBy(() -> STATE_T.runStateT(resultKind, localInitialState))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function wrapped in StateT for 'ap' cannot be null");
    }
  }
}
