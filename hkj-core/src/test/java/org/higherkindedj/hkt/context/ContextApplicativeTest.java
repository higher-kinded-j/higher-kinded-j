// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
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
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    @Test
    @DisplayName("Identity: ap(of(id), v) == v")
    void identityLaw() {
      Kind<ContextKind.Witness<String>, Integer> v = CONTEXT.succeed(42);
      Kind<ContextKind.Witness<String>, Function<Integer, Integer>> ff =
          applicative.of(Function.identity());

      Kind<ContextKind.Witness<String>, Integer> result = applicative.ap(ff, v);

      Context<String, Integer> vCtx = CONTEXT.narrow(v);
      Context<String, Integer> resultCtx = CONTEXT.narrow(result);

      assertThat(resultCtx.run()).isEqualTo(vCtx.run());
    }

    @Test
    @DisplayName("Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphismLaw() {
      Function<Integer, String> f = n -> "Number: " + n;
      Integer x = 42;

      Kind<ContextKind.Witness<String>, Function<Integer, String>> pureF = applicative.of(f);
      Kind<ContextKind.Witness<String>, Integer> pureX = applicative.of(x);

      Kind<ContextKind.Witness<String>, String> left = applicative.ap(pureF, pureX);
      Kind<ContextKind.Witness<String>, String> right = applicative.of(f.apply(x));

      Context<String, String> leftCtx = CONTEXT.narrow(left);
      Context<String, String> rightCtx = CONTEXT.narrow(right);

      assertThat(leftCtx.run()).isEqualTo(rightCtx.run());
    }

    @Test
    @DisplayName("Interchange: ap(u, of(y)) == ap(of(f -> f(y)), u)")
    void interchangeLaw() {
      Kind<ContextKind.Witness<String>, Function<Integer, String>> u =
          CONTEXT.succeed(n -> "Result: " + n);
      Integer y = 42;

      Kind<ContextKind.Witness<String>, String> left = applicative.ap(u, applicative.of(y));
      Kind<ContextKind.Witness<String>, Function<Function<Integer, String>, String>> flipper =
          applicative.of(f -> f.apply(y));
      Kind<ContextKind.Witness<String>, String> right = applicative.ap(flipper, u);

      Context<String, String> leftCtx = CONTEXT.narrow(left);
      Context<String, String> rightCtx = CONTEXT.narrow(right);

      assertThat(leftCtx.run()).isEqualTo(rightCtx.run());
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
