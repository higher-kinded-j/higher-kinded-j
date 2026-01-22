// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link ContextFunctor}.
 *
 * <p>Coverage includes singleton pattern, map operations, null handling, and functor laws.
 */
@DisplayName("ContextFunctor<R> Complete Test Suite")
class ContextFunctorTest {

  private static final ScopedValue<String> STRING_KEY = ScopedValue.newInstance();

  private ContextFunctor<String> functor;

  @BeforeEach
  void setUp() {
    functor = ContextFunctor.instance();
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
    void map_shouldTransformValue() throws Exception {
      Kind<ContextKind.Witness<String>, String> kindA = CONTEXT.ask(STRING_KEY);

      Kind<ContextKind.Witness<String>, Integer> kindB =
          functor.map(String::length, kindA);

      Context<String, Integer> ctx = CONTEXT.narrow(kindB);
      Integer result = ScopedValue.where(STRING_KEY, "hello").call(ctx::run);

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("map() should work with succeed values")
    void map_shouldWorkWithSucceed() {
      Kind<ContextKind.Witness<String>, Integer> kindA = CONTEXT.succeed(42);

      Kind<ContextKind.Witness<String>, String> kindB =
          functor.map(n -> "Number: " + n, kindA);

      Context<String, String> ctx = CONTEXT.narrow(kindB);
      String result = ctx.run();

      assertThat(result).isEqualTo("Number: 42");
    }

    @Test
    @DisplayName("map() should throw NullPointerException for null function")
    void map_shouldThrowForNullFunction() {
      Kind<ContextKind.Witness<String>, String> kindA = CONTEXT.succeed("test");

      assertThatNullPointerException().isThrownBy(() -> functor.map(null, kindA));
    }

    @Test
    @DisplayName("map() should throw for null Kind")
    void map_shouldThrowForNullKind() {
      assertThatNullPointerException().isThrownBy(() -> functor.map(s -> s, null));
    }

    @Test
    @DisplayName("map() should preserve failure")
    void map_shouldPreserveFailure() {
      RuntimeException error = new RuntimeException("test error");
      Kind<ContextKind.Witness<String>, String> kindA = CONTEXT.fail(error);

      Kind<ContextKind.Witness<String>, Integer> kindB =
          functor.map(String::length, kindA);

      Context<String, Integer> ctx = CONTEXT.narrow(kindB);
      assertThatThrownBy(ctx::run).isSameAs(error);
    }
  }

  @Nested
  @DisplayName("Functor Laws via Kind")
  class FunctorLawsViaKind {

    @Test
    @DisplayName("Identity law: map(identity, fa) == fa")
    void identityLaw() throws Exception {
      Kind<ContextKind.Witness<String>, String> fa = CONTEXT.ask(STRING_KEY);

      Kind<ContextKind.Witness<String>, String> mapped = functor.map(s -> s, fa);

      String value = "test";
      String faResult = ScopedValue.where(STRING_KEY, value).call(() -> CONTEXT.narrow(fa).run());
      String mappedResult =
          ScopedValue.where(STRING_KEY, value).call(() -> CONTEXT.narrow(mapped).run());

      assertThat(mappedResult).isEqualTo(faResult);
    }

    @Test
    @DisplayName("Composition law: map(g, map(f, fa)) == map(g.compose(f), fa)")
    void compositionLaw() throws Exception {
      Kind<ContextKind.Witness<String>, String> fa = CONTEXT.ask(STRING_KEY);

      java.util.function.Function<String, String> f = String::toUpperCase;
      java.util.function.Function<String, Integer> g = String::length;

      Kind<ContextKind.Witness<String>, Integer> left = functor.map(g, functor.map(f, fa));
      Kind<ContextKind.Witness<String>, Integer> right = functor.map(f.andThen(g), fa);

      String value = "test";
      Integer leftResult =
          ScopedValue.where(STRING_KEY, value).call(() -> CONTEXT.narrow(left).run());
      Integer rightResult =
          ScopedValue.where(STRING_KEY, value).call(() -> CONTEXT.narrow(right).run());

      assertThat(leftResult).isEqualTo(rightResult);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("map() should handle null values in Context")
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
    void map_shouldHandleMapperReturningNull() {
      Kind<ContextKind.Witness<String>, String> kindA = CONTEXT.succeed("test");

      Kind<ContextKind.Witness<String>, String> kindB = functor.map(s -> null, kindA);

      Context<String, String> ctx = CONTEXT.narrow(kindB);
      String result = ctx.run();

      assertThat(result).isNull();
    }
  }
}
