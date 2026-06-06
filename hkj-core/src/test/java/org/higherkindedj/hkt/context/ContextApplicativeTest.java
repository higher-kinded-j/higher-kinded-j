// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link ContextApplicative}.
 *
 * <p>Verifies the Applicative operations and laws; the laws are driven by the shipped {@link
 * ApplicativeLaws} over {@link ContextLawFixtures}.
 */
@DisplayName("ContextApplicative<R> Tests")
class ContextApplicativeTest {

  private static final ScopedValue<String> STRING_KEY = ContextLawFixtures.STRING_KEY;

  private ContextApplicative<String> applicative;

  @BeforeEach
  void setUp() {
    applicative = ContextApplicative.instance();
  }

  // No separate Applicative contract smoke: this is the same singleton as the Context Monad, so its
  // of/ap/map2 null-argument validation is already covered by the contract in ContextMonadTest. A
  // dedicated Applicative contract would only duplicate it.

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.context.ContextLawFixtures#kinds")
    void identity(String label, Kind<ContextKind.Witness<String>, String> v) {
      ApplicativeLaws.assertIdentity(applicative, v, ContextLawFixtures.EQ);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.context.ContextLawFixtures#values")
    void homomorphism(Integer value) {
      Function<Integer, String> f = n -> "Number: " + n;
      ApplicativeLaws.assertHomomorphism(applicative, value, f, ContextLawFixtures.EQ);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.context.ContextLawFixtures#values")
    void interchange(Integer value) {
      Kind<ContextKind.Witness<String>, Function<Integer, String>> u =
          CONTEXT.succeed(n -> "Result: " + n);
      ApplicativeLaws.assertInterchange(applicative, u, value, ContextLawFixtures.EQ);
    }

    @Test
    @DisplayName("composition holds")
    void composition() {
      Kind<ContextKind.Witness<String>, Function<String, Integer>> u =
          CONTEXT.succeed(String::length);
      Kind<ContextKind.Witness<String>, Function<Integer, String>> v =
          CONTEXT.succeed(n -> "Result: " + n);
      Kind<ContextKind.Witness<String>, Integer> w = CONTEXT.succeed(42);
      ApplicativeLaws.assertComposition(applicative, u, v, w, ContextLawFixtures.EQ);
    }
  }

  @Nested
  @DisplayName("Singleton Pattern")
  class SingletonPattern {

    @Test
    @DisplayName("instance() should return singleton")
    void instance_shouldReturnSingleton() {
      ContextApplicative<String> first = ContextApplicative.instance();
      ContextApplicative<String> second = ContextApplicative.instance();

      assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("instance() should be same across different type parameters")
    void instance_shouldBeSameAcrossTypes() {
      ContextApplicative<String> stringApplicative = ContextApplicative.instance();
      ContextApplicative<Integer> intApplicative = ContextApplicative.instance();

      assertThat((Object) stringApplicative).isSameAs(intApplicative);
    }

    @Test
    @DisplayName("ContextApplicative should extend ContextFunctor")
    void shouldExtendContextFunctor() {
      assertThat(applicative).isInstanceOf(ContextFunctor.class);
    }
  }

  @Nested
  @DisplayName("of() Operation")
  class OfOperation {

    @Test
    @DisplayName("of() should lift value into Context Kind")
    void of_shouldLiftValue() {
      Kind<ContextKind.Witness<String>, Integer> kind = applicative.of(42);

      Context<String, Integer> ctx = CONTEXT.narrow(kind);
      Integer result = ctx.run();

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("of() should allow null value")
    void of_shouldAllowNullValue() {
      Kind<ContextKind.Witness<String>, Integer> kind = applicative.of(null);

      Context<String, Integer> ctx = CONTEXT.narrow(kind);
      Integer result = ctx.run();

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("ap() Operation")
  class ApOperation {

    @Test
    @DisplayName("ap() should apply function in Kind to value in Kind")
    void ap_shouldApplyFunction() {
      Kind<ContextKind.Witness<String>, Function<Integer, String>> ff =
          CONTEXT.succeed(n -> "Number: " + n);
      Kind<ContextKind.Witness<String>, Integer> fa = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result = applicative.ap(ff, fa);

      Context<String, String> ctx = CONTEXT.narrow(result);
      String value = ctx.run();

      assertThat(value).isEqualTo("Number: 42");
    }

    @Test
    @DisplayName("ap() should work with Context that reads ScopedValue")
    void ap_shouldWorkWithScopedValueReading() {
      Kind<ContextKind.Witness<String>, Function<String, Integer>> ff =
          CONTEXT.succeed(String::length);
      Kind<ContextKind.Witness<String>, String> fa = CONTEXT.ask(STRING_KEY);

      Kind<ContextKind.Witness<String>, Integer> result = applicative.ap(ff, fa);

      Context<String, Integer> ctx = CONTEXT.narrow(result);
      Integer value = ScopedValue.where(STRING_KEY, "hello").call(ctx::run);

      assertThat(value).isEqualTo(5);
    }

    @Test
    @DisplayName("ap() should throw if function is null")
    void ap_shouldThrowIfFunctionIsNull() {
      Kind<ContextKind.Witness<String>, Function<Integer, String>> ff = CONTEXT.succeed(null);
      Kind<ContextKind.Witness<String>, Integer> fa = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result = applicative.ap(ff, fa);
      Context<String, String> ctx = CONTEXT.narrow(result);

      assertThatNullPointerException()
          .isThrownBy(ctx::run)
          .withMessageContaining("Function extracted from Context for 'ap' was null");
    }

    @Test
    @DisplayName("ap() should propagate failure from ff")
    void ap_shouldPropagateFailureFromFf() {
      RuntimeException error = new RuntimeException("ff error");
      Kind<ContextKind.Witness<String>, Function<Integer, String>> ff = CONTEXT.fail(error);
      Kind<ContextKind.Witness<String>, Integer> fa = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result = applicative.ap(ff, fa);
      Context<String, String> ctx = CONTEXT.narrow(result);

      assertThatThrownBy(ctx::run).isSameAs(error);
    }

    @Test
    @DisplayName("ap() should propagate failure from fa")
    void ap_shouldPropagateFailureFromFa() {
      RuntimeException error = new RuntimeException("fa error");
      Kind<ContextKind.Witness<String>, Function<Integer, String>> ff =
          CONTEXT.succeed(n -> "Number: " + n);
      Kind<ContextKind.Witness<String>, Integer> fa = CONTEXT.fail(error);

      Kind<ContextKind.Witness<String>, String> result = applicative.ap(ff, fa);
      Context<String, String> ctx = CONTEXT.narrow(result);

      assertThatThrownBy(ctx::run).isSameAs(error);
    }
  }

  @Nested
  @DisplayName("Map Inheritance")
  class MapInheritance {

    @Test
    @DisplayName("map() should be available from parent ContextFunctor")
    void map_shouldBeAvailableFromParent() {
      Kind<ContextKind.Witness<String>, Integer> fa = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> result = applicative.map(n -> "Number: " + n, fa);

      Context<String, String> ctx = CONTEXT.narrow(result);
      assertThat(ctx.run()).isEqualTo("Number: 42");
    }
  }
}
