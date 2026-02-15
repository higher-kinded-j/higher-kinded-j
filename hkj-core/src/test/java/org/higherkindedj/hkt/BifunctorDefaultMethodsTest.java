// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.either.EitherKind2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Bifunctor Default Methods Tests")
class BifunctorDefaultMethodsTest {

  private Bifunctor<EitherKind2.Witness> bifunctor;

  /**
   * A minimal Bifunctor implementation that only implements bimap, relying on the default
   * implementations for first and second. This is needed because EitherBifunctor overrides those
   * methods with optimized implementations.
   */
  private static class DefaultMethodsBifunctor implements Bifunctor<EitherKind2.Witness> {
    @Override
    public <A, B, C, D> Kind2<EitherKind2.Witness, C, D> bimap(
        Function<? super A, ? extends C> f,
        Function<? super B, ? extends D> g,
        Kind2<EitherKind2.Witness, A, B> fab) {
      Either<A, B> either = EITHER.narrow2(fab);
      if (either.isLeft()) {
        return EITHER.widen2(Either.left(f.apply(either.getLeft())));
      } else {
        return EITHER.widen2(Either.right(g.apply(either.getRight())));
      }
    }
  }

  @BeforeEach
  void setUp() {
    bifunctor = EitherBifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("first Operation Tests")
  class FirstOperationTests {

    @Test
    @DisplayName("first should transform Left value")
    void firstTransformsLeft() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.left("error"));

      Kind2<EitherKind2.Witness, Integer, Integer> result = bifunctor.first(String::length, either);

      Either<Integer, Integer> narrowed = EITHER.narrow2(result);
      assertThat(narrowed.isLeft()).isTrue();
      assertThat(narrowed.getLeft()).isEqualTo(5);
    }

    @Test
    @DisplayName("first should not affect Right value")
    void firstDoesNotAffectRight() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.right(42));

      Kind2<EitherKind2.Witness, Integer, Integer> result = bifunctor.first(String::length, either);

      Either<Integer, Integer> narrowed = EITHER.narrow2(result);
      assertThat(narrowed.isRight()).isTrue();
      assertThat(narrowed.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("first with identity should not change the value")
    void firstWithIdentity() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.left("error"));

      Kind2<EitherKind2.Witness, String, Integer> result = bifunctor.first(s -> s, either);

      Either<String, Integer> narrowed = EITHER.narrow2(result);
      assertThat(narrowed.isLeft()).isTrue();
      assertThat(narrowed.getLeft()).isEqualTo("error");
    }

    @Test
    @DisplayName("first should compose with multiple transformations")
    void firstComposes() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.left("hello"));

      Kind2<EitherKind2.Witness, Integer, Integer> step1 = bifunctor.first(String::length, either);

      Kind2<EitherKind2.Witness, String, Integer> step2 = bifunctor.first(Object::toString, step1);

      Either<String, Integer> narrowed = EITHER.narrow2(step2);
      assertThat(narrowed.isLeft()).isTrue();
      assertThat(narrowed.getLeft()).isEqualTo("5");
    }
  }

  @Nested
  @DisplayName("second Operation Tests")
  class SecondOperationTests {

    @Test
    @DisplayName("second should transform Right value")
    void secondTransformsRight() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.right(42));

      Kind2<EitherKind2.Witness, String, String> result =
          bifunctor.second(i -> "Value: " + i, either);

      Either<String, String> narrowed = EITHER.narrow2(result);
      assertThat(narrowed.isRight()).isTrue();
      assertThat(narrowed.getRight()).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("second should not affect Left value")
    void secondDoesNotAffectLeft() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.left("error"));

      Kind2<EitherKind2.Witness, String, String> result =
          bifunctor.second(i -> "Value: " + i, either);

      Either<String, String> narrowed = EITHER.narrow2(result);
      assertThat(narrowed.isLeft()).isTrue();
      assertThat(narrowed.getLeft()).isEqualTo("error");
    }

    @Test
    @DisplayName("second with identity should not change the value")
    void secondWithIdentity() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.right(42));

      Kind2<EitherKind2.Witness, String, Integer> result = bifunctor.second(i -> i, either);

      Either<String, Integer> narrowed = EITHER.narrow2(result);
      assertThat(narrowed.isRight()).isTrue();
      assertThat(narrowed.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("second should compose with multiple transformations")
    void secondComposes() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.right(42));

      Kind2<EitherKind2.Witness, String, String> step1 = bifunctor.second(Object::toString, either);

      Kind2<EitherKind2.Witness, String, Integer> step2 = bifunctor.second(String::length, step1);

      Either<String, Integer> narrowed = EITHER.narrow2(step2);
      assertThat(narrowed.isRight()).isTrue();
      assertThat(narrowed.getRight()).isEqualTo(2); // "42".length()
    }
  }

  @Nested
  @DisplayName("first and second Relationship Tests")
  class FirstSecondRelationshipTests {

    @Test
    @DisplayName("first and second should be equivalent to bimap when applied separately")
    void firstSecondEquivalentToBimap() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.left("error"));

      // Using bimap
      Kind2<EitherKind2.Witness, Integer, String> bimapped =
          bifunctor.bimap(String::length, Object::toString, either);

      // Using first then second
      Kind2<EitherKind2.Witness, Integer, Integer> afterFirst =
          bifunctor.first(String::length, either);
      Kind2<EitherKind2.Witness, Integer, String> afterBoth =
          bifunctor.second(Object::toString, afterFirst);

      Either<Integer, String> bimappedResult = EITHER.narrow2(bimapped);
      Either<Integer, String> separateResult = EITHER.narrow2(afterBoth);

      assertThat(bimappedResult).isEqualTo(separateResult);
    }

    @Test
    @DisplayName("order of first and second should not matter")
    void orderDoesNotMatter() {
      Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.right(42));

      // first then second
      Kind2<EitherKind2.Witness, Integer, String> firstThenSecond =
          bifunctor.second(Object::toString, bifunctor.first(String::length, either));

      // second then first
      Kind2<EitherKind2.Witness, Integer, String> secondThenFirst =
          bifunctor.first(String::length, bifunctor.second(Object::toString, either));

      Either<Integer, String> result1 = EITHER.narrow2(firstThenSecond);
      Either<Integer, String> result2 = EITHER.narrow2(secondThenFirst);

      assertThat(result1).isEqualTo(result2);
    }
  }

  /**
   * Tests that exercise the default method implementations in the Bifunctor interface. Uses
   * DefaultMethodsBifunctor which doesn't override first or second, forcing use of the default
   * implementations.
   */
  @Nested
  @DisplayName("Default Method Implementations (not overridden)")
  class DefaultMethodImplementationTests {

    private DefaultMethodsBifunctor defaultBifunctor;

    @BeforeEach
    void setUp() {
      defaultBifunctor = new DefaultMethodsBifunctor();
    }

    @Nested
    @DisplayName("first default implementation")
    class FirstDefaultTests {

      @Test
      @DisplayName("first should transform Left value using default implementation")
      void firstTransformsLeftDefault() {
        Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.left("error"));

        Kind2<EitherKind2.Witness, Integer, Integer> result =
            defaultBifunctor.first(String::length, either);

        Either<Integer, Integer> narrowed = EITHER.narrow2(result);
        assertThat(narrowed.isLeft()).isTrue();
        assertThat(narrowed.getLeft()).isEqualTo(5);
      }

      @Test
      @DisplayName("first should not affect Right value using default implementation")
      void firstDoesNotAffectRightDefault() {
        Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.right(42));

        Kind2<EitherKind2.Witness, Integer, Integer> result =
            defaultBifunctor.first(String::length, either);

        Either<Integer, Integer> narrowed = EITHER.narrow2(result);
        assertThat(narrowed.isRight()).isTrue();
        assertThat(narrowed.getRight()).isEqualTo(42);
      }
    }

    @Nested
    @DisplayName("second default implementation")
    class SecondDefaultTests {

      @Test
      @DisplayName("second should transform Right value using default implementation")
      void secondTransformsRightDefault() {
        Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.right(42));

        Kind2<EitherKind2.Witness, String, String> result =
            defaultBifunctor.second(i -> "Value: " + i, either);

        Either<String, String> narrowed = EITHER.narrow2(result);
        assertThat(narrowed.isRight()).isTrue();
        assertThat(narrowed.getRight()).isEqualTo("Value: 42");
      }

      @Test
      @DisplayName("second should not affect Left value using default implementation")
      void secondDoesNotAffectLeftDefault() {
        Kind2<EitherKind2.Witness, String, Integer> either = EITHER.widen2(Either.left("error"));

        Kind2<EitherKind2.Witness, String, String> result =
            defaultBifunctor.second(i -> "Value: " + i, either);

        Either<String, String> narrowed = EITHER.narrow2(result);
        assertThat(narrowed.isLeft()).isTrue();
        assertThat(narrowed.getLeft()).isEqualTo("error");
      }
    }
  }
}
