package org.higherkindedj.hkt.trans.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.unwrap;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for StateTMonad<S, F> where F = OptionalKind<?> and S = Integer. */
@DisplayName("StateTMonad Tests (F=Optional, S=Integer)")
class StateTMonadTest {

  private Monad<OptionalKind<?>> optMonad;
  // Explicitly type the monad instance: S=Integer, F=OptionalKind<?>
  private StateTMonad<Integer, OptionalKind<?>> stateTMonad;
  private final Integer initialState = 10;

  // --- Fields for Law Tests - FIX: Initialize in setUp ---
  private Function<Integer, String> intToString;
  private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> mValue;
  private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> mEmpty;
  private Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>> f;
  private Function<String, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>> g;

  // --- End Fields for Law Tests ---

  // Helper to run and unwrap the Optional<StateTuple>
  private <A> Optional<StateTuple<Integer, A>> runOptStateT(
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> kind, Integer startState) {
    // Add explicit type arguments to the helper call
    Kind<OptionalKind<?>, StateTuple<Integer, A>> resultKind =
        StateTKindHelper.<Integer, OptionalKind<?>, A>runStateT(kind, startState);
    return unwrap(resultKind);
  }

  // Helper to create a StateT Kind with explicit types
  private <A> Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> createStateTKind(
      Function<Integer, Kind<OptionalKind<?>, StateTuple<Integer, A>>> runFn) {
    // Ensure optMonad is initialized before calling this
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

    // --- FIX: Initialize law test fields here ---
    intToString = Object::toString;
    mValue = createStateTKind(s -> optMonad.of(StateTuple.of(s + 1, s * 10)));
    mEmpty = createStateTKind(s -> OptionalKindHelper.wrap(Optional.empty()));
    f = i -> createStateTKind(s -> optMonad.of(StateTuple.of(s + i, "v" + i)));
    g = str -> createStateTKind(s -> optMonad.of(StateTuple.of(s + str.length(), str + "!")));
    // --- End FIX ---
  }

  // --- Basic Operations ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateStateTReturningValueAndUnchangedStateInOptional() {
      // stateTMonad is initialized in setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kind =
          stateTMonad.of("constantValue");
      Optional<StateTuple<Integer, String>> result = runOptStateT(kind, initialState);

      assertThat(result).isPresent().contains(StateTuple.of(initialState, "constantValue"));
    }

    @Test
    void of_shouldWrapNullAsPresentOptionalHoldingTupleWithNullValue() {
      // stateTMonad is initialized in setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kind = stateTMonad.of(null);
      Optional<StateTuple<Integer, String>> result = runOptStateT(kind, initialState);
      assertThat(result).isPresent().contains(StateTuple.of(initialState, null));
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {

    // FIX: Declare fields, initialize in @BeforeEach of this nested class
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> initialKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> emptyKind;

    @BeforeEach
    void setUpMapTests() {
      initialKind = createStateTKind(s -> optMonad.of(StateTuple.of(s + 1, s * 2)));
      emptyKind = createStateTKind(s -> OptionalKindHelper.wrap(Optional.empty()));
    }

    @Test
    void map_shouldApplyFunctionToValueWhenOuterIsPresent() {
      // stateTMonad is initialized in outer setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> mappedKind =
          stateTMonad.map(i -> "V:" + i, initialKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(mappedKind, initialState); // 10
      assertThat(result).isPresent().contains(StateTuple.of(11, "V:20"));
    }

    @Test
    void map_shouldPropagateOuterEmpty() {
      // stateTMonad is initialized in outer setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> mappedKind =
          stateTMonad.map(i -> "V:" + i, emptyKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(mappedKind, initialState);
      assertThat(result).isEmpty();
    }

    @Test
    void map_shouldHandleMappingValueToNullAsPresentOptionalHoldingTupleWithNullValue() {
      // stateTMonad is initialized in outer setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> mappedKind =
          stateTMonad.map(i -> null, initialKind); // Function returns null
      Optional<StateTuple<Integer, String>> result = runOptStateT(mappedKind, initialState);
      assertThat(result).isPresent().contains(StateTuple.of(11, null));
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {

    // FIX: Declare fields, initialize in @BeforeEach of this nested class
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Function<Integer, String>> funcKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> valKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> emptyValKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Function<Integer, String>>
        emptyFuncKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Function<Integer, String>>
        nullFuncKind;

    @BeforeEach
    void setUpApTests() {
      funcKind = createStateTKind(s -> optMonad.of(StateTuple.of(s + 1, i -> "F" + i + s)));
      valKind = createStateTKind(s -> optMonad.of(StateTuple.of(s * 2, s + 10)));
      emptyValKind = createStateTKind(s -> OptionalKindHelper.wrap(Optional.empty()));
      emptyFuncKind = createStateTKind(s -> OptionalKindHelper.wrap(Optional.empty()));
      nullFuncKind = createStateTKind(s -> optMonad.of(StateTuple.of(s + 1, null)));
    }

    @Test
    void ap_shouldApplyPresentFuncToPresentValue() {
      // stateTMonad is initialized in outer setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.ap(funcKind, valKind);
      Optional<StateTuple<Integer, String>> result = runOptStateT(resultKind, 5); // Initial state 5
      // Calculation:
      // 1. Run funcKind(5): state=6, func=i->"F"+i+5
      // 2. Run valKind(6): state=12, val=16
      // 3. Apply func(16): "F"+16+5 = "F165"
      // Result: state=12, value="F165"
      assertThat(result).isPresent().contains(StateTuple.of(12, "F165"));
    }

    @Test
    void ap_shouldReturnEmptyIfFuncOuterIsEmpty() {
      // stateTMonad is initialized in outer setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.ap(emptyFuncKind, valKind);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfValueOuterIsEmpty() {
      // stateTMonad is initialized in outer setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.ap(funcKind, emptyValKind);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfFunctionInTupleIsNull() {
      // stateTMonad is initialized in outer setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.ap(nullFuncKind, valKind);
      // Optional.map on the tuple containing null function will result in empty
      assertThat(runOptStateT(resultKind, 5)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    // FIX: Declare fields, initialize in @BeforeEach of this nested class
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> initialKind;
    private Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> emptyKind;
    private Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>> f;
    private Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>> fEmpty;

    @BeforeEach
    void setUpFlatMapTests() {
      initialKind = createStateTKind(s -> optMonad.of(StateTuple.of(s + 1, s * 2)));
      emptyKind = createStateTKind(s -> OptionalKindHelper.wrap(Optional.empty()));
      // Note: 'f' and 'fEmpty' capture 'optMonad' from the outer scope, which is fine
      // as long as they are used after setUp runs.
      f = i -> createStateTKind(s -> optMonad.of(StateTuple.of(s + i, "Val:" + i)));
      fEmpty = i -> createStateTKind(s -> OptionalKindHelper.wrap(Optional.empty()));
    }

    @Test
    void flatMap_shouldSequenceComputations() {
      // stateTMonad is initialized in outer setUp
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.flatMap(f, initialKind);
      Optional<StateTuple<Integer, String>> result =
          runOptStateT(resultKind, 10); // Initial state 10
      // Calculation:
      // 1. Run initialKind(10): state=11, value=20
      // 2. Apply f(20): returns kind representing s -> Optional(StateTuple(s+20, "Val:20"))
      // 3. Run the result of f(20) with state=11: state=11+20=31, value="Val:20"
      // Result: state=31, value="Val:20"
      assertThat(result).isPresent().contains(StateTuple.of(31, "Val:20"));
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyFromInitial() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.flatMap(f, emptyKind);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyFromFunction() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> resultKind =
          stateTMonad.flatMap(fEmpty, initialKind);
      assertThat(runOptStateT(resultKind, initialState)).isEmpty();
    }
  }

  // --- Law Tests ---
  // Helper to assert law equality for different initial states
  private <A> void assertStateTEquals(
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> k1,
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> k2) {
    assertThat(runOptStateT(k1, initialState)).isEqualTo(runOptStateT(k2, initialState));
    assertThat(runOptStateT(k1, initialState + 5)).isEqualTo(runOptStateT(k2, initialState + 5));
    assertThat(runOptStateT(k1, -1)).isEqualTo(runOptStateT(k2, -1));
  }

  // Functions and values for laws are initialized in setUp

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
          i -> stateTMonad.of(i);
      // Success case
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> leftSideSuccess =
          stateTMonad.flatMap(ofFunc, mValue);
      assertStateTEquals(leftSideSuccess, mValue);
      // Empty case
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, Integer> leftSideEmpty =
          stateTMonad.flatMap(ofFunc, mEmpty);
      assertStateTEquals(leftSideEmpty, mEmpty);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      // Success case
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> innerSuccess =
          stateTMonad.flatMap(f, mValue);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> leftSideSuccess =
          stateTMonad.flatMap(g, innerSuccess);
      Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String>> rightSideFunc =
          a -> stateTMonad.flatMap(g, f.apply(a));
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> rightSideSuccess =
          stateTMonad.flatMap(rightSideFunc, mValue);
      assertStateTEquals(leftSideSuccess, rightSideSuccess);

      // Empty case
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> innerEmpty =
          stateTMonad.flatMap(f, mEmpty);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> leftSideEmpty =
          stateTMonad.flatMap(g, innerEmpty);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> rightSideEmpty =
          stateTMonad.flatMap(rightSideFunc, mEmpty);
      assertStateTEquals(leftSideEmpty, rightSideEmpty);
    }
  }
}
