// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;
import static org.higherkindedj.hkt.util.validation.Operation.LIFT_F;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateTKindHelper Tests ")
// (F=OptionalKind.Witness)
class StateTKindHelperTest {

  private static final String TYPE_NAME = "StateT";

  private Monad<OptionalKind.Witness> outerMonad;

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;
  }

  private <S, A> StateT<S, OptionalKind.Witness, A> createStateT(
      Function<S, Kind<OptionalKind.Witness, StateTuple<S, A>>> runFn) {
    return StateT.create(runFn, outerMonad);
  }

  @Nested
  @DisplayName("Widen Tests")
  class WidenTests {

    @Test
    @DisplayName("widen should convert non-null StateT to StateTKind")
    void widen_nonNullStateT_shouldReturnStateTKind() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s, 42));
      StateT<String, OptionalKind.Witness, Integer> concreteStateT = createStateT(runFn);

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> wrapped =
          STATE_T.widen(concreteStateT);

      assertThat(wrapped).isNotNull().isInstanceOf(StateTKind.class);
      assertThat(STATE_T.narrow(wrapped)).isSameAs(concreteStateT);
    }

    @Test
    @DisplayName("widen should convert StateT with null value to StateTKind")
    void widen_nonNullStateTNullValue_shouldReturnStateTKind() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s, null));
      StateT<String, OptionalKind.Witness, Integer> concreteStateT = createStateT(runFn);

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> wrapped =
          STATE_T.widen(concreteStateT);

      assertThat(wrapped).isNotNull().isInstanceOf(StateTKind.class);
      assertThat(STATE_T.narrow(wrapped)).isSameAs(concreteStateT);
    }

    @Test
    @DisplayName("widen should throw NullPointerException when given null")
    void widen_nullStateT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> STATE_T.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Input %s cannot be null for widen".formatted(TYPE_NAME));
    }
  }

  @Nested
  @DisplayName("Narrow Tests")
  class NarrowTests {

    @Test
    @DisplayName("narrow should unwrap valid StateTKind to original StateT instance")
    void narrow_validKind_shouldReturnStateT() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s, 42));
      StateT<String, OptionalKind.Witness, Integer> originalStateT = createStateT(runFn);
      var wrappedKind = STATE_T.widen(originalStateT);

      StateT<String, OptionalKind.Witness, Integer> unwrappedStateT = STATE_T.narrow(wrappedKind);

      assertThat(unwrappedStateT).isSameAs(originalStateT);
    }

    @Test
    @DisplayName("narrow should throw KindUnwrapException when given null")
    void narrow_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> STATE_T.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Cannot narrow null Kind for %s".formatted(TYPE_NAME));
    }

    @Test
    @DisplayName("narrow should throw KindUnwrapException when given incorrect Kind type")
    void narrow_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<String, OptionalKind.Witness, Integer> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> kindToTest =
          (Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer>) (Kind) incorrectKind;

      assertThatThrownBy(() -> STATE_T.narrow(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(
              "Kind instance cannot be narrowed to %s (received: %s)"
                  .formatted(StateT.class.getSimpleName(), OtherKind.class.getSimpleName()));
    }
  }

  @Nested
  @DisplayName("Round-Trip Tests")
  class RoundTripTests {

    @Test
    @DisplayName("widen then narrow should preserve identity")
    void roundTrip_shouldPreserveIdentity() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s, 42));
      StateT<String, OptionalKind.Witness, Integer> original = createStateT(runFn);

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> widened =
          STATE_T.widen(original);
      StateT<String, OptionalKind.Witness, Integer> narrowed = STATE_T.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("widen then narrow should preserve identity for null value")
    void roundTrip_nullValue_shouldPreserveIdentity() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s, null));
      StateT<String, OptionalKind.Witness, Integer> original = createStateT(runFn);

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> widened =
          STATE_T.widen(original);
      StateT<String, OptionalKind.Witness, Integer> narrowed = STATE_T.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("multiple round-trips should preserve identity")
    void multipleRoundTrips_shouldPreserveIdentity() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s, 999));
      StateT<String, OptionalKind.Witness, Integer> original = createStateT(runFn);

      StateT<String, OptionalKind.Witness, Integer> current = original;
      for (int i = 0; i < 3; i++) {
        Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> widened =
            STATE_T.widen(current);
        current = STATE_T.narrow(widened);
      }

      assertThat(current).isSameAs(original);
    }
  }

  @Nested
  @DisplayName("StateT Factory Methods")
  class StateTFactoryMethodTests {

    @Test
    @DisplayName("stateT should create StateT instance from function and monad")
    void stateT_shouldCreateInstance() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s + "_modified", 42));

      StateT<String, OptionalKind.Witness, Integer> stateT = STATE_T.stateT(runFn, outerMonad);

      assertThat(stateT).isNotNull();
      assertThat(stateT.runStateTFn()).isSameAs(runFn);
      assertThat(stateT.monadF()).isSameAs(outerMonad);
    }

    @Test
    @DisplayName("stateT should throw when function is null")
    void stateT_nullFunction_shouldThrow() {
      assertThatThrownBy(() -> STATE_T.stateT(null, outerMonad))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("runStateTFn")
          .hasMessageContaining("stateT");
    }

    @Test
    @DisplayName("stateT should throw when monad is null")
    void stateT_nullMonad_shouldThrow() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s, 42));

      ValidationTestBuilder.create()
          .assertTransformerOuterMonadNull(
              () -> STATE_T.stateT(runFn, null), StateT.class, Operation.STATE_T)
          .execute();
    }
  }

  @Nested
  @DisplayName("LiftF Tests")
  class LiftFTests {

    @Test
    @DisplayName("liftF should lift Kind<F, A> into StateT context")
    void liftF_shouldLiftKind() {
      Kind<OptionalKind.Witness, Integer> fa = outerMonad.of(42);
      StateT<String, OptionalKind.Witness, Integer> stateT = STATE_T.liftF(outerMonad, fa);
      assertThat(stateT).isNotNull();
    }

    @Test
    @DisplayName("liftF should preserve state unchanged")
    void liftF_shouldPreserveState() {
      Kind<OptionalKind.Witness, Integer> fa = outerMonad.of(42);
      String testState = "unchanged";
      StateT<String, OptionalKind.Witness, Integer> stateT = STATE_T.liftF(outerMonad, fa);
      Kind<OptionalKind.Witness, String> finalState = stateT.execStateT(testState);
      assertThat(finalState).isNotNull();
    }

    @Test
    @DisplayName("liftF validations")
    void liftF_validations() {
      Kind<OptionalKind.Witness, Integer> fa = outerMonad.of(42);

      ValidationTestBuilder.create()
          .assertTransformerOuterMonadNull(() -> STATE_T.liftF(null, fa), StateT.class, LIFT_F)
          .assertKindNull(
              () -> STATE_T.liftF(outerMonad, null), StateT.class, LIFT_F, "source Kind")
          .execute();
    }
  }

  @Nested
  @DisplayName("Runner Method Tests")
  class RunnerMethodTests {

    private StateT<String, OptionalKind.Witness, Integer> stateT;

    @BeforeEach
    void setUp() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s + "_modified", 42));
      stateT = createStateT(runFn);
    }

    @Test
    @DisplayName("runStateT should execute state transition via helper")
    void runStateT_shouldExecuteTransition() {
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> kind = STATE_T.widen(stateT);

      Kind<OptionalKind.Witness, StateTuple<String, Integer>> result =
          STATE_T.runStateT(kind, "initial");

      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("evalStateT should extract value via helper")
    void evalStateT_shouldExtractValue() {
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> kind = STATE_T.widen(stateT);

      Kind<OptionalKind.Witness, Integer> result = STATE_T.evalStateT(kind, "initial");

      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("execStateT should extract state via helper")
    void execStateT_shouldExtractState() {
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> kind = STATE_T.widen(stateT);

      Kind<OptionalKind.Witness, String> result = STATE_T.execStateT(kind, "initial");

      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("runStateT should throw when Kind is null")
    void runStateT_nullKind_shouldThrow() {
      assertThatThrownBy(() -> STATE_T.runStateT(null, "initial"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("runStateT");
    }

    @Test
    @DisplayName("evalStateT should throw when Kind is null")
    void evalStateT_nullKind_shouldThrow() {
      assertThatThrownBy(() -> STATE_T.evalStateT(null, "initial"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("evalStateT");
    }

    @Test
    @DisplayName("execStateT should throw when Kind is null")
    void execStateT_nullKind_shouldThrow() {
      assertThatThrownBy(() -> STATE_T.execStateT(null, "initial"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("execStateT");
    }
  }

  // Dummy Kind for testing invalid type unwrap
  private static class OtherKind<S, F_Witness, A>
      implements Kind<OtherKind<S, F_Witness, ?>, A>, WitnessArity<TypeArity.Unary> {}
}
