// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link ContextFunctor}.
 *
 * <p>Verifies the Functor operations and laws; the laws are driven by the shipped {@link
 * FunctorLaws} over {@link ContextLawFixtures}.
 */
@DisplayName("ContextFunctor<R> Tests")
class ContextFunctorTest {

  private static final ScopedValue<String> STRING_KEY = ContextLawFixtures.STRING_KEY;

  private ContextFunctor<String> functor;

  @BeforeEach
  void setUp() {
    functor = ContextFunctor.instance();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.context.ContextLawFixtures#kinds")
    void identity(String label, Kind<ContextKind.Witness<String>, String> fa) {
      FunctorLaws.assertIdentity(functor, fa, ContextLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.context.ContextLawFixtures#kinds")
    void composition(String label, Kind<ContextKind.Witness<String>, String> fa) {
      FunctorLaws.assertComposition(
          functor, fa, String::toUpperCase, String::length, ContextLawFixtures.EQ);
    }
  }

  /**
   * {@code Context} is lazy — {@code map} only wraps the mapper, which is applied at {@link
   * Context#run()} time — so {@link Category#EXCEPTIONS} is omitted (a thrown mapper does not
   * surface until the context is run). That deferral is covered explicitly in {@code
   * ContextTest}/{@code ContextExceptionTest}. The Functor laws are verified in the {@code Laws}
   * block above.
   */
  @Test
  @DisplayName("Functor contract — operations & validations (laws verified above)")
  void functorContract() {
    TypeClassContract.<ContextKind.Witness<String>>functor(ContextFunctor.class)
        .<String>instance(functor)
        .<Integer>withKind(CONTEXT.succeed("seed"))
        .withMapper(String::length)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Singleton Pattern")
  class SingletonPattern {

    @Test
    @DisplayName("instance() should return singleton")
    void instance_shouldReturnSingleton() {
      ContextFunctor<String> first = ContextFunctor.instance();
      ContextFunctor<String> second = ContextFunctor.instance();

      assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("instance() should be same across different type parameters")
    void instance_shouldBeSameAcrossTypes() {
      ContextFunctor<String> stringFunctor = ContextFunctor.instance();
      ContextFunctor<Integer> intFunctor = ContextFunctor.instance();

      // Due to type erasure, they're the same instance
      assertThat((Object) stringFunctor).isSameAs(intFunctor);
    }
  }

  @Nested
  @DisplayName("map() Operation")
  class MapOperation {

    @Test
    @DisplayName("map() should transform value in Kind wrapper")
    void map_shouldTransformValue() {
      Kind<ContextKind.Witness<String>, String> kindA = CONTEXT.ask(STRING_KEY);

      Kind<ContextKind.Witness<String>, Integer> kindB = functor.map(String::length, kindA);

      Context<String, Integer> ctx = CONTEXT.narrow(kindB);
      Integer result = ScopedValue.where(STRING_KEY, "hello").call(ctx::run);

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("map() should work with succeed values")
    void map_shouldWorkWithSucceed() {
      Kind<ContextKind.Witness<String>, Integer> kindA = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> kindB = functor.map(n -> "Number: " + n, kindA);

      Context<String, String> ctx = CONTEXT.narrow(kindB);
      String result = ctx.run();

      assertThat(result).isEqualTo("Number: 42");
    }

    @Test
    @DisplayName("map() should preserve failure")
    void map_shouldPreserveFailure() {
      RuntimeException error = new RuntimeException("test error");
      Kind<ContextKind.Witness<String>, String> kindA = CONTEXT.fail(error);

      Kind<ContextKind.Witness<String>, Integer> kindB = functor.map(String::length, kindA);

      Context<String, Integer> ctx = CONTEXT.narrow(kindB);
      assertThatThrownBy(ctx::run).isSameAs(error);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("map() should handle null values in Context")
    @SuppressWarnings("ConstantValue") // succeed(null) feeds a null value the mapper must handle
    void map_shouldHandleNullValues() {
      Kind<ContextKind.Witness<String>, String> kindA = CONTEXT.succeed(null);

      Kind<ContextKind.Witness<String>, String> kindB =
          functor.map(s -> s == null ? "was-null" : s, kindA);

      Context<String, String> ctx = CONTEXT.narrow(kindB);
      String result = ctx.run();

      assertThat(result).isEqualTo("was-null");
    }

    @Test
    @DisplayName("map() should handle mapper returning null")
    @SuppressWarnings("DataFlowIssue") // mapper deliberately returns null to verify pass-through
    void map_shouldHandleMapperReturningNull() {
      Kind<ContextKind.Witness<String>, String> kindA = CONTEXT.succeed("test");

      Kind<ContextKind.Witness<String>, String> kindB = functor.map(_ -> null, kindA);

      Context<String, String> ctx = CONTEXT.narrow(kindB);
      String result = ctx.run();

      assertThat(result).isNull();
    }
  }
}
