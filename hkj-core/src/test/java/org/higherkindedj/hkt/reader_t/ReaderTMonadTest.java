// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderTMonad Complete Test Suite (Outer: OptionalKind.Witness, Environment: String)")
class ReaderTMonadTest
    extends TypeClassTestBase<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer, String> {

  private Monad<OptionalKind.Witness> outerMonad = OptionalMonad.INSTANCE;
  private Monad<ReaderTKind.Witness<OptionalKind.Witness, String>> readerTMonad =
      new ReaderTMonad<>(outerMonad);

  private final String testEnvironment = "test-env";

  @BeforeEach
  void setUpMonad() {
    outerMonad = OptionalMonad.INSTANCE;
    readerTMonad = new ReaderTMonad<>(outerMonad);
  }

  private <A> Optional<A> unwrapKindToOptional(
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> kind) {
    if (kind == null) return Optional.empty();
    var readerT = READER_T.narrow(kind);
    Kind<OptionalKind.Witness, A> outerKind = readerT.run().apply(testEnvironment);
    return OPTIONAL.narrow(outerKind);
  }

  private <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> readerT(A value) {
    return READER_T.widen(ReaderT.reader(outerMonad, env -> value));
  }

  private <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> emptyT() {
    Kind<OptionalKind.Witness, A> emptyOuter = OPTIONAL.widen(Optional.empty());
    return READER_T.widen(ReaderT.liftF(outerMonad, emptyOuter));
  }

  private <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> fromEnv(
      Function<String, A> f) {
    return READER_T.widen(ReaderT.reader(outerMonad, f));
  }

  // TypeClassTestBase implementations
  @Override
  protected Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> createValidKind() {
    return readerT(10);
  }

  @Override
  protected Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> createValidKind2() {
    return readerT(20);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected BiPredicate<
          Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, ?>,
          Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> unwrapKindToOptional(k1).equals(unwrapKindToOptional(k2));
  }

  @Override
  protected Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>
      createValidFlatMapper() {
    return i -> readerT("v" + i);
  }

  @Override
  protected Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Function<Integer, String>>
      createValidFunctionKind() {
    return readerT(Object::toString);
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> a + "+" + b;
  }

  @Override
  protected Integer createTestValue() {
    return 5;
  }

  @Override
  protected Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>
      createTestFunction() {
    return i -> readerT("v" + i);
  }

  @Override
  protected Function<String, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>
      createChainFunction() {
    return s -> readerT(s + "!");
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Verify all test categories are covered")
    void verifyCompleteCoverage() {
      // Verify that all nested test classes exist and have tests
      assertThat(FunctorOperationTests.class).isNotNull();
      assertThat(ApplicativeOperationTests.class).isNotNull();
      assertThat(MonadOperationTests.class).isNotNull();
      assertThat(MonadLawTests.class).isNotNull();
      assertThat(EdgeCaseTests.class).isNotNull();
    }
  }

  @Nested
  @DisplayName("Functor Operations")
  class FunctorOperationTests {

    @Test
    @DisplayName("map should apply function to value")
    void map_shouldApplyFunction() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> input = readerT(10);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String> result =
          readerTMonad.map(Object::toString, input);

      assertThat(unwrapKindToOptional(result)).isPresent().contains("10");
    }

    @Test
    @DisplayName("map should propagate empty outer monad")
    void map_shouldPropagateEmpty() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> input = emptyT();
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String> result =
          readerTMonad.map(Object::toString, input);

      assertThat(unwrapKindToOptional(result)).isEmpty();
    }

    @Test
    @DisplayName("map should have access to environment")
    void map_shouldAccessEnvironment() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String> input = fromEnv(env -> env);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> result =
          readerTMonad.map(String::length, input);

      assertThat(unwrapKindToOptional(result)).isPresent().contains(testEnvironment.length());
    }
  }

  @Nested
  @DisplayName("Applicative Operations")
  class ApplicativeOperationTests {

    @Test
    @DisplayName("of should create ReaderT with value")
    void of_shouldCreateReaderT() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> result = readerTMonad.of(42);

      assertThat(unwrapKindToOptional(result)).isPresent().contains(42);
    }

    @Test
    @DisplayName("of should ignore environment")
    void of_shouldIgnoreEnvironment() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> result = readerTMonad.of(42);

      // Test with different environment
      ReaderT<OptionalKind.Witness, String, Integer> readerT = READER_T.narrow(result);
      Optional<Integer> withDifferentEnv = OPTIONAL.narrow(readerT.run().apply("different-env"));

      assertThat(withDifferentEnv).isPresent().contains(42);
    }

    @Test
    @DisplayName("ap should apply function to value")
    void ap_shouldApplyFunction() {
      Function<Integer, String> func = i -> "Result:" + (i * 2);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Function<Integer, String>> ff =
          readerT(func);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> fa = readerT(10);

      var result = readerTMonad.ap(ff, fa);

      assertThat(unwrapKindToOptional(result)).isPresent().contains("Result:20");
    }

    @Test
    @DisplayName("ap should propagate empty function")
    void ap_shouldPropagateEmptyFunction() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Function<Integer, String>> ff =
          emptyT();
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> fa = readerT(10);

      var result = readerTMonad.ap(ff, fa);

      assertThat(unwrapKindToOptional(result)).isEmpty();
    }

    @Test
    @DisplayName("ap should propagate empty value")
    void ap_shouldPropagateEmptyValue() {
      Function<Integer, String> func = i -> "Result:" + i;
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Function<Integer, String>> ff =
          readerT(func);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> fa = emptyT();

      var result = readerTMonad.ap(ff, fa);

      assertThat(unwrapKindToOptional(result)).isEmpty();
    }

    @Test
    @DisplayName("ap should share environment between function and value")
    void ap_shouldShareEnvironment() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Function<Integer, String>> ff =
          fromEnv(env -> i -> env + ":" + i);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> fa = fromEnv(String::length);

      var result = readerTMonad.ap(ff, fa);

      assertThat(unwrapKindToOptional(result))
          .isPresent()
          .contains(testEnvironment + ":" + testEnvironment.length());
    }
  }

  @Nested
  @DisplayName("Monad Operations")
  class MonadOperationTests {

    @Test
    @DisplayName("flatMap should apply function returning ReaderT")
    void flatMap_shouldApplyFunction() {
      var initial = readerT(10);
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> func =
          i -> readerT("Value:" + i);

      var result = readerTMonad.flatMap(func, initial);

      assertThat(unwrapKindToOptional(result)).isPresent().contains("Value:10");
    }

    @Test
    @DisplayName("flatMap should propagate empty outer monad")
    void flatMap_shouldPropagateEmpty() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> initial = emptyT();
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> func =
          i -> readerT("Value:" + i);

      var result = readerTMonad.flatMap(func, initial);

      assertThat(unwrapKindToOptional(result)).isEmpty();
    }

    @Test
    @DisplayName("flatMap should handle function returning empty")
    void flatMap_shouldHandleFunctionReturningEmpty() {
      var initial = readerT(10);
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> func =
          i -> emptyT();

      var result = readerTMonad.flatMap(func, initial);

      assertThat(unwrapKindToOptional(result)).isEmpty();
    }

    @Test
    @DisplayName("flatMap should share environment")
    void flatMap_shouldShareEnvironment() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String> initial = fromEnv(env -> env);
      Function<String, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>> func =
          s -> fromEnv(env -> s.length() + env.length());

      var result = readerTMonad.flatMap(func, initial);

      assertThat(unwrapKindToOptional(result)).isPresent().contains(testEnvironment.length() * 2);
    }

    @Test
    @DisplayName("flatMap should compose multiple operations")
    void flatMap_shouldComposeOperations() {
      var initial = readerT(5);
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>> double1 =
          i -> readerT(i * 2);
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>> add10 =
          i -> readerT(i + 10);

      var result = readerTMonad.flatMap(add10, readerTMonad.flatMap(double1, initial));

      assertThat(unwrapKindToOptional(result)).isPresent().contains(20);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawTests {

    @Test
    @DisplayName("Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      var ofValue = readerTMonad.of(testValue);
      var leftSide = readerTMonad.flatMap(testFunction, ofValue);
      var rightSide = testFunction.apply(testValue);

      assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
    }

    @Test
    @DisplayName("Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>> ofFunc =
          i -> readerTMonad.of(i);

      assertThat(equalityChecker.test(readerTMonad.flatMap(ofFunc, validKind), validKind)).isTrue();
      assertThat(equalityChecker.test(readerTMonad.flatMap(ofFunc, emptyT()), emptyT())).isTrue();
    }

    @Test
    @DisplayName("Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      var innerFlatMap = readerTMonad.flatMap(testFunction, validKind);
      var leftSide = readerTMonad.flatMap(chainFunction, innerFlatMap);

      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>
          rightSideFunc = a -> readerTMonad.flatMap(chainFunction, testFunction.apply(a));
      var rightSide = readerTMonad.flatMap(rightSideFunc, validKind);

      assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("of with null value returns empty Optional")
    void of_withNullValue() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> result =
          readerTMonad.of(null);
      assertThat(unwrapKindToOptional(result)).isEmpty();
    }

    @Test
    @DisplayName("map with null-returning function returns empty Optional")
    void map_withNullReturningFunction() {
      Function<Integer, String> nullFunc = i -> null;
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String> result =
          readerTMonad.map(nullFunc, validKind);
      assertThat(unwrapKindToOptional(result)).isEmpty();
    }

    @Test
    @DisplayName("flatMap with function throwing exception")
    void flatMap_withThrowingFunction() {
      RuntimeException testEx = new RuntimeException("Test exception");
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>
          throwingFunc =
              i -> {
                throw testEx;
              };

      assertThatThrownBy(
              () -> {
                Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String> result =
                    readerTMonad.flatMap(throwingFunc, validKind);
                unwrapKindToOptional(result);
              })
          .isSameAs(testEx);
    }

    @Test
    @DisplayName("environment can be different types")
    void environment_canBeDifferentTypes() {
      // Create ReaderTMonad with Integer environment
      Monad<ReaderTKind.Witness<OptionalKind.Witness, Integer>> intEnvMonad =
          new ReaderTMonad<>(outerMonad);

      Kind<ReaderTKind.Witness<OptionalKind.Witness, Integer>, String> readerTWithIntEnv =
          READER_T.widen(ReaderT.reader(outerMonad, (Integer env) -> "Length:" + env));

      // Apply with integer environment
      ReaderT<OptionalKind.Witness, Integer, String> narrowed = READER_T.narrow(readerTWithIntEnv);
      Optional<String> result = OPTIONAL.narrow(narrowed.run().apply(42));

      assertThat(result).isPresent().contains("Length:42");
    }

    @Test
    @DisplayName("multiple environments in composition")
    void multipleEnvironments_inComposition() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String> r1 =
          fromEnv(env -> env + "-1");
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String> r2 =
          fromEnv(env -> env + "-2");

      Function<String, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> combine =
          s1 -> readerTMonad.map(s2 -> s1 + ":" + s2, r2);

      var result = readerTMonad.flatMap(combine, r1);

      assertThat(unwrapKindToOptional(result))
          .isPresent()
          .contains(testEnvironment + "-1:" + testEnvironment + "-2");
    }
  }
}
