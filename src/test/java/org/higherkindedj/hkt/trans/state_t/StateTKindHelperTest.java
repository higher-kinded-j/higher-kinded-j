package org.higherkindedj.hkt.trans.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

@DisplayName("StateTKindHelper Tests (F=Optional, S=Integer)")
class StateTKindHelperTest {

  record Config(String setting) {}

  final Integer initialState = 10;
  private Monad<OptionalKind.Witness> optMonad;
  private StateT<Integer, OptionalKind.Witness, String> baseStateT;

  @BeforeEach
  void setUp() {
    optMonad = new OptionalMonad();
    baseStateT =
        StateT.<Integer, OptionalKind.Witness, String>create(
            s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);
  }

  // Helper to run and unwrap the Optional result
  private <A> Optional<StateTuple<Integer, A>> runOptStateT(
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, A> kind, Integer startState) {
    Kind<OptionalKind.Witness, StateTuple<Integer, A>> resultKind =
        StateTKindHelper.<Integer, OptionalKind.Witness, A>runStateT(kind, startState);
    return OptionalKindHelper.unwrap(resultKind);
  }

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnCorrectKind() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind =
          StateTKindHelper.wrap(baseStateT);
      assertThat(kind).isSameAs(baseStateT);
      assertThat(kind).isInstanceOf(StateTKind.class);
      assertThat(kind).isInstanceOf(StateT.class);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> StateTKindHelper.wrap(null))
          .withMessageContaining("StateT instance to wrap cannot be null.");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_shouldReturnOriginalStateT() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind = baseStateT;
      assertThat(StateTKindHelper.unwrap(kind)).isSameAs(baseStateT);
    }

    record DummyKind<A>() implements Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> StateTKindHelper.<Integer, OptionalKind.Witness, String>unwrap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind instance to unwrap cannot be null.");
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> unknownKind =
          new DummyKind<>();
      assertThatThrownBy(
              () -> StateTKindHelper.<Integer, OptionalKind.Witness, String>unwrap(unknownKind))
          .isInstanceOf(ClassCastException.class)
          .hasMessageContaining("DummyKind cannot be cast");
    }
  }

  @Nested
  @DisplayName("Factory Helpers")
  class FactoryHelpersTests {

    @Test
    void stateT_shouldCreateAndWrapStateT() {
      Function<Integer, Kind<OptionalKind.Witness, StateTuple<Integer, String>>> runFn =
          s -> optMonad.of(StateTuple.of(s + 5, "State was " + s));
      StateT<Integer, OptionalKind.Witness, String> stateT =
          StateTKindHelper.<Integer, OptionalKind.Witness, String>stateT(runFn, optMonad);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind =
          StateTKindHelper.wrap(stateT);
      assertThat(runOptStateT(kind, initialState))
          .isPresent()
          .contains(StateTuple.of(15, "State was 10"));
    }

    @Test
    void lift_shouldCreateStateTIgnoringState() {
      Kind<OptionalKind.Witness, String> outerValue =
          OptionalKindHelper.wrap(Optional.of("Lifted"));
      StateT<Integer, OptionalKind.Witness, String> stateT =
          StateTKindHelper.<Integer, OptionalKind.Witness, String>lift(optMonad, outerValue);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind =
          StateTKindHelper.wrap(stateT);
      assertThat(runOptStateT(kind, initialState))
          .isPresent()
          .contains(StateTuple.of(10, "Lifted"));
      assertThat(runOptStateT(kind, 50)).isPresent().contains(StateTuple.of(50, "Lifted"));
    }

    @Test
    void lift_shouldCreateStateTWithEmptyOuter() {
      Kind<OptionalKind.Witness, String> outerEmpty = OptionalKindHelper.wrap(Optional.empty());
      StateT<Integer, OptionalKind.Witness, String> stateT =
          StateTKindHelper.<Integer, OptionalKind.Witness, String>lift(optMonad, outerEmpty);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind =
          StateTKindHelper.wrap(stateT);
      assertThat(runOptStateT(kind, initialState)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Runner Helpers")
  class RunnerHelpersTests {
    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> baseKind = baseStateT;

    @Test
    void runStateT_shouldExecuteAndReturnResultKind() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kindToTest =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);

      Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultKind =
          StateTKindHelper.<Integer, OptionalKind.Witness, String>runStateT(
              kindToTest, initialState); // 10
      assertThat(OptionalKindHelper.unwrap(resultKind))
          .isPresent()
          .contains(StateTuple.of(11, "Val:10"));
    }

    @Test
    void evalStateT_shouldExecuteAndExtractValueKind() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kindToTest =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);

      Kind<OptionalKind.Witness, String> valueKind =
          StateTKindHelper.<Integer, OptionalKind.Witness, String>evalStateT(
              kindToTest, initialState);
      assertThat(OptionalKindHelper.unwrap(valueKind)).isPresent().contains("Val:10");
    }

    @Test
    void execStateT_shouldExecuteAndExtractStateKind() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kindToTest =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);

      Kind<OptionalKind.Witness, Integer> stateKind =
          StateTKindHelper.<Integer, OptionalKind.Witness, String>execStateT(
              kindToTest, initialState);
      assertThat(OptionalKindHelper.unwrap(stateKind)).isPresent().contains(11);
    }

    @Test
    void runStateT_shouldHandleOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> emptyKind =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> OptionalKindHelper.wrap(Optional.empty()), optMonad);

      Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultKind =
          StateTKindHelper.<Integer, OptionalKind.Witness, String>runStateT(
              emptyKind, initialState);
      assertThat(OptionalKindHelper.unwrap(resultKind)).isEmpty();
    }

    @Test
    void evalStateT_shouldHandleOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> emptyKind =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> OptionalKindHelper.wrap(Optional.empty()), optMonad);

      Kind<OptionalKind.Witness, String> valueKind =
          StateTKindHelper.<Integer, OptionalKind.Witness, String>evalStateT(
              emptyKind, initialState);
      assertThat(OptionalKindHelper.unwrap(valueKind)).isEmpty();
    }

    @Test
    void execStateT_shouldHandleOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> emptyKind =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> OptionalKindHelper.wrap(Optional.empty()), optMonad);

      Kind<OptionalKind.Witness, Integer> stateKind =
          StateTKindHelper.<Integer, OptionalKind.Witness, String>execStateT(
              emptyKind, initialState);
      assertThat(OptionalKindHelper.unwrap(stateKind)).isEmpty();
    }

    @Test
    void runStateT_shouldThrowClassCastFromBadUnwrap() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> invalidKind =
          new UnwrapTests.DummyKind<>();
      assertThatThrownBy(
              () ->
                  StateTKindHelper.<Integer, OptionalKind.Witness, String>runStateT(
                      invalidKind, initialState))
          .isInstanceOf(ClassCastException.class);
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {
    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<StateTKindHelper> constructor = StateTKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
