// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link ContextApplicative}.
 *
 * <p>Coverage includes singleton pattern, of() and ap() operations, null handling, and applicative
 * laws.
 */
@DisplayName("ContextApplicative<R> Complete Test Suite")
class ContextApplicativeTest {

  private static final ScopedValue<String> STRING_KEY = ScopedValue.newInstance();

  private ContextApplicative<String> applicative;

  @BeforeEach
  void setUp() {
    applicative = ContextApplicative.instance();
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
    void ap_shouldApplyFunction() throws Exception {
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
    void ap_shouldWorkWithScopedValueReading() throws Exception {
      Kind<ContextKind.Witness<String>, Function<String, Integer>> ff =
          CONTEXT.succeed(String::length);
      Kind<ContextKind.Witness<String>, String> fa = CONTEXT.ask(STRING_KEY);

      Kind<ContextKind.Witness<String>, Integer> result = applicative.ap(ff, fa);

      Context<String, Integer> ctx = CONTEXT.narrow(result);
      Integer value = ScopedValue.where(STRING_KEY, "hello").call(ctx::run);

      assertThat(value).isEqualTo(5);
    }

    @Test
    @DisplayName("ap() should throw for null ff")
    void ap_shouldThrowForNullFf() {
      Kind<ContextKind.Witness<String>, Integer> fa = CONTEXT.succeed(42);

      assertThatNullPointerException().isThrownBy(() -> applicative.ap(null, fa));
    }

    @Test
    @DisplayName("ap() should throw for null fa")
    void ap_shouldThrowForNullFa() {
      Kind<ContextKind.Witness<String>, Function<Integer, String>> ff =
          CONTEXT.succeed(n -> "Number: " + n);

      assertThatNullPointerException().isThrownBy(() -> applicative.ap(ff, null));
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

    @Test
    void identity() {
      Kind<ContextKind.Witness<String>, Integer> v = CONTEXT.succeed(42);
      ApplicativeLaws.assertIdentity(applicative, v, eq);
    }

    @Test
    void homomorphism() {
      Function<Integer, String> f = n -> "Number: " + n;
      ApplicativeLaws.assertHomomorphism(applicative, 42, f, eq);
    }

    @Test
    void interchange() {
      Kind<ContextKind.Witness<String>, Function<Integer, String>> u =
          CONTEXT.succeed(n -> "Result: " + n);
      ApplicativeLaws.assertInterchange(applicative, u, 42, eq);
    }

    @Test
    void composition() {
      Kind<ContextKind.Witness<String>, Function<String, Integer>> u =
          CONTEXT.succeed(String::length);
      Kind<ContextKind.Witness<String>, Function<Integer, String>> v =
          CONTEXT.succeed(n -> "Result: " + n);
      Kind<ContextKind.Witness<String>, Integer> w = CONTEXT.succeed(42);
      ApplicativeLaws.assertComposition(applicative, u, v, w, eq);
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
