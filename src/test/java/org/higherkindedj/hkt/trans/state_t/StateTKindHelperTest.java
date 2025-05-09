package org.higherkindedj.hkt.trans.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.unwrap;

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

  record Config(String setting) {} // Keep a simple config for R if needed

  final Integer initialState = 10;
  private Monad<OptionalKind<?>> optMonad;
  // Correct order: S=Integer, F=OptionalKind<?>
  private StateT<Integer, OptionalKind<?>, String> baseStateT; // Keep for other tests

  @BeforeEach
  void setUp() {
    optMonad = new OptionalMonad();
    // Create a base StateT returning Optional<StateTuple>
    // Use correct explicit type arguments for StateT.create: <S, F, A>
    baseStateT =
        StateT.<Integer, OptionalKind<?>, String>create(
            s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);
  }

  // Helper to run and unwrap the Optional result
  private <A> Optional<StateTuple<Integer, A>> runOptStateT(
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> kind, Integer startState) {
    // Use correct explicit type arguments for StateTKindHelper.runStateT: <S, F, A>
    Kind<OptionalKind<?>, StateTuple<Integer, A>> resultKind =
        StateTKindHelper.<Integer, OptionalKind<?>, A>runStateT(kind, startState);
    return unwrap(resultKind);
  }

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnCorrectKind() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kind =
          StateTKindHelper.wrap(baseStateT);
      assertThat(kind).isSameAs(baseStateT); // wrap is effectively identity here
      assertThat(kind).isInstanceOf(StateTKind.class);
      assertThat(kind).isInstanceOf(StateT.class);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> StateTKindHelper.wrap(null))
          .withMessageContaining("StateT instance to wrap cannot be null."); // Message from wrap
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_shouldReturnOriginalStateT() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kind = baseStateT;
      assertThat(StateTKindHelper.unwrap(kind)).isSameAs(baseStateT);
    }

    record DummyKind<A>() implements Kind<StateTKind.Witness<Integer, OptionalKind<?>>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      // Now expect ClassCastException from narrow because the explicit null check
      // in the helper delegates to narrow which performs the cast.
      assertThatThrownBy(() -> StateTKindHelper.<Integer, OptionalKind<?>, String>unwrap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(
              "Kind instance to unwrap cannot be null."); // Check specific message if added
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> unknownKind = new DummyKind<>();
      assertThatThrownBy(
              () -> StateTKindHelper.<Integer, OptionalKind<?>, String>unwrap(unknownKind))
          .isInstanceOf(ClassCastException.class) // narrow method uses cast
          .hasMessageContaining("DummyKind cannot be cast");
    }
  }

  @Nested
  @DisplayName("Factory Helpers")
  class FactoryHelpersTests {

    @Test
    void stateT_shouldCreateAndWrapStateT() {
      Function<Integer, Kind<OptionalKind<?>, StateTuple<Integer, String>>> runFn =
          s -> optMonad.of(StateTuple.of(s + 5, "State was " + s));
      StateT<Integer, OptionalKind<?>, String> stateT =
          StateTKindHelper.<Integer, OptionalKind<?>, String>stateT(runFn, optMonad);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kind =
          StateTKindHelper.wrap(stateT);
      assertThat(runOptStateT(kind, initialState))
          .isPresent()
          .contains(StateTuple.of(15, "State was 10"));
    }

    @Test
    void lift_shouldCreateStateTIgnoringState() {
      Kind<OptionalKind<?>, String> outerValue = OptionalKindHelper.wrap(Optional.of("Lifted"));
      StateT<Integer, OptionalKind<?>, String> stateT =
          StateTKindHelper.<Integer, OptionalKind<?>, String>lift(optMonad, outerValue);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kind =
          StateTKindHelper.wrap(stateT);
      assertThat(runOptStateT(kind, initialState))
          .isPresent()
          .contains(StateTuple.of(10, "Lifted"));
      assertThat(runOptStateT(kind, 50)).isPresent().contains(StateTuple.of(50, "Lifted"));
    }

    @Test
    void lift_shouldCreateStateTWithEmptyOuter() {
      Kind<OptionalKind<?>, String> outerEmpty = OptionalKindHelper.wrap(Optional.empty());
      StateT<Integer, OptionalKind<?>, String> stateT =
          StateTKindHelper.<Integer, OptionalKind<?>, String>lift(optMonad, outerEmpty);
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kind =
          StateTKindHelper.wrap(stateT);
      assertThat(runOptStateT(kind, initialState)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Runner Helpers")
  class RunnerHelpersTests {
    Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> baseKind = baseStateT;

    @Test
    void runStateT_shouldExecuteAndReturnResultKind() {

      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kindToTest =
          StateT.<Integer, OptionalKind<?>, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);

      Kind<OptionalKind<?>, StateTuple<Integer, String>> resultKind =
          StateTKindHelper.<Integer, OptionalKind<?>, String>runStateT(
              kindToTest, initialState); // 10
      assertThat(unwrap(resultKind)).isPresent().contains(StateTuple.of(11, "Val:10"));
    }

    @Test
    void evalStateT_shouldExecuteAndExtractValueKind() {

      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kindToTest =
          StateT.<Integer, OptionalKind<?>, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);

      Kind<OptionalKind<?>, String> valueKind =
          StateTKindHelper.<Integer, OptionalKind<?>, String>evalStateT(kindToTest, initialState);
      assertThat(unwrap(valueKind)).isPresent().contains("Val:10");
    }

    @Test
    void execStateT_shouldExecuteAndExtractStateKind() {

      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> kindToTest =
          StateT.<Integer, OptionalKind<?>, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);

      Kind<OptionalKind<?>, Integer> stateKind =
          StateTKindHelper.<Integer, OptionalKind<?>, String>execStateT(kindToTest, initialState);
      assertThat(unwrap(stateKind)).isPresent().contains(11);
    }

    // Other runner helper tests remain unchanged for now...
    @Test
    void runStateT_shouldHandleOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> emptyKind =
          StateT.<Integer, OptionalKind<?>, String>create(
              s -> OptionalKindHelper.wrap(Optional.empty()), optMonad);

      Kind<OptionalKind<?>, StateTuple<Integer, String>> resultKind =
          StateTKindHelper.<Integer, OptionalKind<?>, String>runStateT(emptyKind, initialState);
      assertThat(unwrap(resultKind)).isEmpty();
    }

    @Test
    void evalStateT_shouldHandleOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> emptyKind =
          StateT.<Integer, OptionalKind<?>, String>create(
              s -> OptionalKindHelper.wrap(Optional.empty()), optMonad);

      Kind<OptionalKind<?>, String> valueKind =
          StateTKindHelper.<Integer, OptionalKind<?>, String>evalStateT(emptyKind, initialState);
      assertThat(unwrap(valueKind)).isEmpty();
    }

    @Test
    void execStateT_shouldHandleOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> emptyKind =
          StateT.<Integer, OptionalKind<?>, String>create(
              s -> OptionalKindHelper.wrap(Optional.empty()), optMonad);

      Kind<OptionalKind<?>, Integer> stateKind =
          StateTKindHelper.<Integer, OptionalKind<?>, String>execStateT(emptyKind, initialState);
      assertThat(unwrap(stateKind)).isEmpty();
    }

    @Test
    void runStateT_shouldThrowClassCastFromBadUnwrap() {
      Kind<StateTKind.Witness<Integer, OptionalKind<?>>, String> invalidKind =
          new UnwrapTests.DummyKind<>();
      assertThatThrownBy(
              () ->
                  StateTKindHelper.<Integer, OptionalKind<?>, String>runStateT(
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
