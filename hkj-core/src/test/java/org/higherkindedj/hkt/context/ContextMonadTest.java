// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Comprehensive test suite for {@link ContextMonad}.
 *
 * <p>Coverage includes singleton pattern, flatMap operations, null handling, and monad laws.
 */
@DisplayName("ContextMonad<R> Complete Test Suite")
class ContextMonadTest {

  private static final ScopedValue<String> STRING_KEY = ScopedValue.newInstance();

  private Monad<ContextKind.Witness<String>> monad;

  @BeforeEach
  void setUp() {
    monad = Instances.monad(context());
  }

  @Nested
  @DisplayName("Singleton Pattern")
  class SingletonPattern {

    @Test
    @DisplayName("instance() should return singleton")
    void instance_shouldReturnSingleton() {
      Monad<ContextKind.Witness<String>> first = Instances.monad(context());
      Monad<ContextKind.Witness<String>> second = Instances.monad(context());

      assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("instance() should be same across different type parameters")
    void instance_shouldBeSameAcrossTypes() {
      Monad<ContextKind.Witness<String>> stringMonad = Instances.monad(context());
      Monad<ContextKind.Witness<Integer>> intMonad = Instances.monad(context());

      assertThat((Object) stringMonad).isSameAs(intMonad);
    }

    @Test
    @DisplayName("ContextMonad should extend ContextApplicative")
    void shouldExtendContextApplicative() {
      assertThat(monad).isInstanceOf(ContextApplicative.class);
    }
  }

  @Nested
  @DisplayName("flatMap() Operation")
  class FlatMapOperation {

    @Test
    @DisplayName("flatMap() should sequence computations")
    void flatMap_shouldSequenceComputations() {
      Kind<ContextKind.Witness<String>, Integer> ma = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result =
          monad.flatMap(n -> CONTEXT.succeed("Number: " + n), ma);

      Context<String, String> ctx = CONTEXT.narrow(result);
      String value = ctx.run();

      assertThat(value).isEqualTo("Number: 42");
    }

    @Test
    @DisplayName("flatMap() should work with ScopedValue reading")
    void flatMap_shouldWorkWithScopedValueReading() throws Exception {
      Kind<ContextKind.Witness<String>, String> ma = CONTEXT.ask(STRING_KEY);

      Kind<ContextKind.Witness<String>, Integer> result =
          monad.flatMap(s -> CONTEXT.succeed(s.length()), ma);

      Context<String, Integer> ctx = CONTEXT.narrow(result);
      Integer value = ScopedValue.where(STRING_KEY, "hello").call(ctx::run);

      assertThat(value).isEqualTo(5);
    }

    @Test
    @DisplayName("flatMap() should throw for null function")
    void flatMap_shouldThrowForNullFunction() {
      Kind<ContextKind.Witness<String>, Integer> ma = CONTEXT.succeed(42);

      assertThatNullPointerException().isThrownBy(() -> monad.flatMap(null, ma));
    }

    @Test
    @DisplayName("flatMap() should throw for null ma")
    void flatMap_shouldThrowForNullMa() {
      assertThatNullPointerException()
          .isThrownBy(() -> monad.flatMap(n -> CONTEXT.succeed("Number: " + n), null));
    }

    @Test
    @DisplayName("flatMap() should throw if function returns null")
    void flatMap_shouldThrowIfFunctionReturnsNull() {
      Kind<ContextKind.Witness<String>, Integer> ma = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result = monad.flatMap(n -> null, ma);
      Context<String, String> ctx = CONTEXT.narrow(result);

      assertThatThrownBy(ctx::run).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("flatMap() should propagate failure")
    void flatMap_shouldPropagateFailure() {
      RuntimeException error = new RuntimeException("test error");
      Kind<ContextKind.Witness<String>, Integer> ma = CONTEXT.fail(error);

      Kind<ContextKind.Witness<String>, String> result =
          monad.flatMap(n -> CONTEXT.succeed("Number: " + n), ma);
      Context<String, String> ctx = CONTEXT.narrow(result);

      assertThatThrownBy(ctx::run).isSameAs(error);
    }

    @Test
    @DisplayName("flatMap() should propagate failure from returned context")
    void flatMap_shouldPropagateFailureFromReturnedContext() {
      RuntimeException error = new RuntimeException("inner error");
      Kind<ContextKind.Witness<String>, Integer> ma = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result =
          monad.flatMap(n -> CONTEXT.fail(error), ma);
      Context<String, String> ctx = CONTEXT.narrow(result);

      assertThatThrownBy(ctx::run).isSameAs(error);
    }
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    private final BiPredicate<
            Kind<ContextKind.Witness<String>, ?>, Kind<ContextKind.Witness<String>, ?>>
        eq =
            (k1, k2) -> {
              try {
                return ScopedValue.where(STRING_KEY, "test")
                    .call(() -> Objects.equals(CONTEXT.narrow(k1).run(), CONTEXT.narrow(k2).run()));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            };

    private final Function<Integer, Kind<ContextKind.Witness<String>, String>> intToCtx =
        n -> CONTEXT.succeed("Number: " + n);

    private final Function<String, Kind<ContextKind.Witness<String>, String>> upper =
        s -> CONTEXT.succeed(s.toUpperCase());

    private final Function<String, Kind<ContextKind.Witness<String>, Integer>> length =
        s -> CONTEXT.succeed(s.length());

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, intToCtx, eq);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("stringFixtures")
    void rightIdentity(String label, Kind<ContextKind.Witness<String>, String> ma) {
      MonadLaws.assertRightIdentity(monad, ma, eq);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("stringFixtures")
    void associativity(String label, Kind<ContextKind.Witness<String>, String> ma) {
      MonadLaws.assertAssociativity(monad, ma, upper, length, eq);
    }

    static Stream<Arguments> values() {
      return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
    }

    static Stream<Arguments> stringFixtures() {
      return Stream.of(
          Arguments.of("succeed(\"hello\")", CONTEXT.<String, String>succeed("hello")),
          Arguments.of("ask(STRING_KEY)", CONTEXT.<String>ask(STRING_KEY)));
    }
  }

  @Nested
  @DisplayName("Inherited Operations")
  class InheritedOperations {

    @Test
    @DisplayName("of() should be available from parent")
    void of_shouldBeAvailableFromParent() {
      Kind<ContextKind.Witness<String>, Integer> kind = monad.of(42);

      Context<String, Integer> ctx = CONTEXT.narrow(kind);
      assertThat(ctx.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("map() should be available from parent")
    void map_shouldBeAvailableFromParent() {
      Kind<ContextKind.Witness<String>, Integer> fa = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result = monad.map(n -> "Number: " + n, fa);

      Context<String, String> ctx = CONTEXT.narrow(result);
      assertThat(ctx.run()).isEqualTo("Number: 42");
    }

    @Test
    @DisplayName("ap() should be available from parent")
    void ap_shouldBeAvailableFromParent() {
      Kind<ContextKind.Witness<String>, Function<Integer, String>> ff =
          CONTEXT.succeed(n -> "Number: " + n);
      Kind<ContextKind.Witness<String>, Integer> fa = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result = monad.ap(ff, fa);

      Context<String, String> ctx = CONTEXT.narrow(result);
      assertThat(ctx.run()).isEqualTo("Number: 42");
    }
  }

  @Nested
  @DisplayName("Complex Scenarios")
  class ComplexScenarios {

    @Test
    @DisplayName("Chain of flatMap operations should work correctly")
    void chainOfFlatMapsShouldWork() throws Exception {
      Kind<ContextKind.Witness<String>, String> result =
          monad.flatMap(
              s ->
                  monad.flatMap(
                      upper ->
                          monad.flatMap(
                              len -> CONTEXT.succeed("Result: " + upper + " (" + len + ")"),
                              CONTEXT.succeed(upper.length())),
                      CONTEXT.succeed(s.toUpperCase())),
              CONTEXT.ask(STRING_KEY));

      Context<String, String> ctx = CONTEXT.narrow(result);
      String value = ScopedValue.where(STRING_KEY, "hello").call(ctx::run);

      assertThat(value).isEqualTo("Result: HELLO (5)");
    }
  }
}
