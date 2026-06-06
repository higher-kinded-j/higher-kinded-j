// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link ContextMonad}.
 *
 * <p>Verifies the Monad operations and laws; the laws are driven by the shipped {@link MonadLaws}
 * over {@link ContextLawFixtures}.
 */
@DisplayName("ContextMonad<R> Tests")
class ContextMonadTest {

  private static final ScopedValue<String> STRING_KEY = ContextLawFixtures.STRING_KEY;

  private Monad<ContextKind.Witness<String>> monad;

  @BeforeEach
  void setUp() {
    monad = Instances.monad(context());
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    private final Function<Integer, Kind<ContextKind.Witness<String>, String>> intToCtx =
        n -> CONTEXT.succeed("Number: " + n);

    private final Function<String, Kind<ContextKind.Witness<String>, String>> upper =
        s -> CONTEXT.succeed(s.toUpperCase());

    private final Function<String, Kind<ContextKind.Witness<String>, Integer>> length =
        s -> CONTEXT.succeed(s.length());

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.context.ContextLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, intToCtx, ContextLawFixtures.EQ);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.context.ContextLawFixtures#kinds")
    void rightIdentity(String label, Kind<ContextKind.Witness<String>, String> ma) {
      MonadLaws.assertRightIdentity(monad, ma, ContextLawFixtures.EQ);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.context.ContextLawFixtures#kinds")
    void associativity(String label, Kind<ContextKind.Witness<String>, String> ma) {
      MonadLaws.assertAssociativity(monad, ma, upper, length, ContextLawFixtures.EQ);
    }
  }

  /**
   * {@code Context} is lazy — {@code map}/{@code flatMap} only wrap the user function, which is
   * applied at {@link Context#run()} time — so {@link Category#EXCEPTIONS} is omitted (a thrown
   * function does not surface until the context is run). That deferral is covered explicitly in
   * {@code ContextTest}/{@code ContextExceptionTest}. The Monad laws are verified in the {@code
   * Laws} block above.
   */
  @Test
  @DisplayName("Monad contract — operations & validations (laws verified above)")
  void monadContract() {
    Kind<ContextKind.Witness<String>, Integer> validKind = CONTEXT.succeed(42);
    Kind<ContextKind.Witness<String>, Integer> validKind2 = CONTEXT.succeed(7);
    Function<Integer, String> mapper = n -> "Number: " + n;
    Function<Integer, Kind<ContextKind.Witness<String>, String>> flatMapper =
        n -> CONTEXT.succeed("Number: " + n);
    Kind<ContextKind.Witness<String>, Function<Integer, String>> functionKind =
        CONTEXT.succeed(n -> "fn:" + n);

    TypeClassContract.<ContextKind.Witness<String>>monad(ContextMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(validKind2, mapper, flatMapper, functionKind, (a, b) -> a + ":" + b)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
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
    void flatMap_shouldWorkWithScopedValueReading() {
      Kind<ContextKind.Witness<String>, String> ma = CONTEXT.ask(STRING_KEY);

      Kind<ContextKind.Witness<String>, Integer> result =
          monad.flatMap(s -> CONTEXT.succeed(s.length()), ma);

      Context<String, Integer> ctx = CONTEXT.narrow(result);
      Integer value = ScopedValue.where(STRING_KEY, "hello").call(ctx::run);

      assertThat(value).isEqualTo(5);
    }

    @Test
    @DisplayName("flatMap() should throw if function returns null")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void flatMap_shouldThrowIfFunctionReturnsNull() {
      Kind<ContextKind.Witness<String>, Integer> ma = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result = monad.flatMap(_ -> null, ma);
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
          monad.flatMap(_ -> CONTEXT.fail(error), ma);
      Context<String, String> ctx = CONTEXT.narrow(result);

      assertThatThrownBy(ctx::run).isSameAs(error);
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
    void chainOfFlatMapsShouldWork() {
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
