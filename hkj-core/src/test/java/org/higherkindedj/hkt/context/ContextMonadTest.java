// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link ContextMonad}.
 *
 * <p>Coverage includes singleton pattern, flatMap operations, null handling, and monad laws.
 */
@DisplayName("ContextMonad<R> Complete Test Suite")
class ContextMonadTest {

  private static final ScopedValue<String> STRING_KEY = ScopedValue.newInstance();

  private ContextMonad<String> monad;

  @BeforeEach
  void setUp() {
    monad = ContextMonad.instance();
  }

  @Nested
  @DisplayName("Singleton Pattern")
  class SingletonPattern {

    @Test
    @DisplayName("instance() should return singleton")
    void instance_shouldReturnSingleton() {
      ContextMonad<String> first = ContextMonad.instance();
      ContextMonad<String> second = ContextMonad.instance();

      assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("instance() should be same across different type parameters")
    void instance_shouldBeSameAcrossTypes() {
      ContextMonad<String> stringMonad = ContextMonad.instance();
      ContextMonad<Integer> intMonad = ContextMonad.instance();

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
  @DisplayName("Monad Laws")
  class MonadLaws {

    @Test
    @DisplayName("Left identity: flatMap(f, of(a)) == f(a)")
    void leftIdentityLaw() {
      Integer a = 42;
      Function<Integer, Kind<ContextKind.Witness<String>, String>> f =
          n -> CONTEXT.succeed("Number: " + n);

      Kind<ContextKind.Witness<String>, String> left = monad.flatMap(f, monad.of(a));
      Kind<ContextKind.Witness<String>, String> right = f.apply(a);

      Context<String, String> leftCtx = CONTEXT.narrow(left);
      Context<String, String> rightCtx = CONTEXT.narrow(right);

      assertThat(leftCtx.run()).isEqualTo(rightCtx.run());
    }

    @Test
    @DisplayName("Right identity: flatMap(of, m) == m")
    void rightIdentityLaw() throws Exception {
      Kind<ContextKind.Witness<String>, String> m = CONTEXT.ask(STRING_KEY);

      Kind<ContextKind.Witness<String>, String> left = monad.flatMap(monad::of, m);

      String value = "test";
      String mResult = ScopedValue.where(STRING_KEY, value).call(() -> CONTEXT.narrow(m).run());
      String leftResult =
          ScopedValue.where(STRING_KEY, value).call(() -> CONTEXT.narrow(left).run());

      assertThat(leftResult).isEqualTo(mResult);
    }

    @Test
    @DisplayName("Associativity: flatMap(g, flatMap(f, m)) == flatMap(a -> flatMap(g, f(a)), m)")
    void associativityLaw() throws Exception {
      Kind<ContextKind.Witness<String>, String> m = CONTEXT.ask(STRING_KEY);

      Function<String, Kind<ContextKind.Witness<String>, String>> f =
          s -> CONTEXT.succeed(s.toUpperCase());
      Function<String, Kind<ContextKind.Witness<String>, Integer>> g =
          s -> CONTEXT.succeed(s.length());

      Kind<ContextKind.Witness<String>, Integer> left = monad.flatMap(g, monad.flatMap(f, m));
      Kind<ContextKind.Witness<String>, Integer> right =
          monad.flatMap(a -> monad.flatMap(g, f.apply(a)), m);

      String value = "test";
      Integer leftResult =
          ScopedValue.where(STRING_KEY, value).call(() -> CONTEXT.narrow(left).run());
      Integer rightResult =
          ScopedValue.where(STRING_KEY, value).call(() -> CONTEXT.narrow(right).run());

      assertThat(leftResult).isEqualTo(rightResult);
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
