// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Prism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Prisms Utility Class Tests")
class PrismsTest {

  @Nested
  @DisplayName("some() - Optional Prism")
  class SomePrism {
    private final Prism<Optional<String>, String> prism = Prisms.some();

    @Test
    @DisplayName("should extract value from present Optional")
    void shouldExtractFromPresent() {
      Optional<String> opt = Optional.of("hello");
      Optional<String> result = prism.getOptional(opt);
      assertThat(result).isPresent().contains("hello");
    }

    @Test
    @DisplayName("should return empty for empty Optional")
    void shouldReturnEmptyForEmpty() {
      Optional<String> opt = Optional.empty();
      Optional<String> result = prism.getOptional(opt);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build Optional from value")
    void shouldBuild() {
      Optional<String> result = prism.build("world");
      assertThat(result).isPresent().contains("world");
    }

    @Test
    @DisplayName("should match present Optional")
    void shouldMatchPresent() {
      assertThat(prism.matches(Optional.of("test"))).isTrue();
    }

    @Test
    @DisplayName("should not match empty Optional")
    void shouldNotMatchEmpty() {
      assertThat(prism.matches(Optional.empty())).isFalse();
    }
  }

  @Nested
  @DisplayName("left() - Either Left Prism")
  class LeftPrism {
    private final Prism<Either<String, Integer>, String> prism = Prisms.left();

    @Test
    @DisplayName("should extract value from Left")
    void shouldExtractFromLeft() {
      Either<String, Integer> either = Either.left("error");
      Optional<String> result = prism.getOptional(either);
      assertThat(result).isPresent().contains("error");
    }

    @Test
    @DisplayName("should return empty for Right")
    void shouldReturnEmptyForRight() {
      Either<String, Integer> either = Either.right(42);
      Optional<String> result = prism.getOptional(either);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build Left from value")
    void shouldBuild() {
      Either<String, Integer> result = prism.build("failure");
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("failure");
    }

    @Test
    @DisplayName("should match Left")
    void shouldMatchLeft() {
      assertThat(prism.matches(Either.left("error"))).isTrue();
    }

    @Test
    @DisplayName("should not match Right")
    void shouldNotMatchRight() {
      assertThat(prism.matches(Either.right(42))).isFalse();
    }
  }

  @Nested
  @DisplayName("right() - Either Right Prism")
  class RightPrism {
    private final Prism<Either<String, Integer>, Integer> prism = Prisms.right();

    @Test
    @DisplayName("should extract value from Right")
    void shouldExtractFromRight() {
      Either<String, Integer> either = Either.right(42);
      Optional<Integer> result = prism.getOptional(either);
      assertThat(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("should return empty for Left")
    void shouldReturnEmptyForLeft() {
      Either<String, Integer> either = Either.left("error");
      Optional<Integer> result = prism.getOptional(either);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build Right from value")
    void shouldBuild() {
      Either<String, Integer> result = prism.build(100);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(100);
    }

    @Test
    @DisplayName("should match Right")
    void shouldMatchRight() {
      assertThat(prism.matches(Either.right(42))).isTrue();
    }

    @Test
    @DisplayName("should not match Left")
    void shouldNotMatchLeft() {
      assertThat(prism.matches(Either.left("error"))).isFalse();
    }
  }

  @Nested
  @DisplayName("only() - Specific Value Prism")
  class OnlyPrism {
    private final Prism<String, Unit> prism = Prisms.only("hello");

    @Test
    @DisplayName("should match expected value")
    void shouldMatchExpectedValue() {
      Optional<Unit> result = prism.getOptional("hello");
      assertThat(result).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("should not match different value")
    void shouldNotMatchDifferentValue() {
      Optional<Unit> result = prism.getOptional("world");
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build expected value")
    void shouldBuild() {
      String result = prism.build(Unit.INSTANCE);
      assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("should work with null values")
    void shouldWorkWithNull() {
      Prism<String, Unit> nullPrism = Prisms.only(null);
      assertThat(nullPrism.matches(null)).isTrue();
      assertThat(nullPrism.matches("not null")).isFalse();
    }

    @Test
    @DisplayName("matches should work for equality check")
    void matchesShouldWork() {
      assertThat(prism.matches("hello")).isTrue();
      assertThat(prism.matches("world")).isFalse();
    }
  }

  @Nested
  @DisplayName("notNull() - Non-null Prism")
  class NotNullPrism {
    private final Prism<String, String> prism = Prisms.notNull();

    @Test
    @DisplayName("should extract non-null value")
    void shouldExtractNonNull() {
      Optional<String> result = prism.getOptional("hello");
      assertThat(result).isPresent().contains("hello");
    }

    @Test
    @DisplayName("should return empty for null")
    void shouldReturnEmptyForNull() {
      Optional<String> result = prism.getOptional(null);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build non-null value")
    void shouldBuild() {
      String result = prism.build("world");
      assertThat(result).isEqualTo("world");
    }

    @Test
    @DisplayName("should match non-null values")
    void shouldMatchNonNull() {
      assertThat(prism.matches("test")).isTrue();
    }

    @Test
    @DisplayName("should not match null")
    void shouldNotMatchNull() {
      assertThat(prism.matches(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("instanceOf() - Type-based Prism")
  class InstanceOfPrism {
    // Test hierarchy
    sealed interface Animal permits Dog, Cat {}

    record Dog(String name) implements Animal {}

    record Cat(String name) implements Animal {}

    private final Prism<Animal, Dog> dogPrism = Prisms.instanceOf(Dog.class);

    @Test
    @DisplayName("should extract matching type")
    void shouldExtractMatchingType() {
      Animal animal = new Dog("Buddy");
      Optional<Dog> result = dogPrism.getOptional(animal);
      assertThat(result).isPresent().contains(new Dog("Buddy"));
    }

    @Test
    @DisplayName("should return empty for non-matching type")
    void shouldReturnEmptyForNonMatchingType() {
      Animal animal = new Cat("Whiskers");
      Optional<Dog> result = dogPrism.getOptional(animal);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build with identity")
    void shouldBuild() {
      Dog dog = new Dog("Max");
      Animal result = dogPrism.build(dog);
      assertThat(result).isEqualTo(dog);
    }

    @Test
    @DisplayName("should match correct type")
    void shouldMatchCorrectType() {
      assertThat(dogPrism.matches(new Dog("Rex"))).isTrue();
    }

    @Test
    @DisplayName("should not match incorrect type")
    void shouldNotMatchIncorrectType() {
      assertThat(dogPrism.matches(new Cat("Fluffy"))).isFalse();
    }

    @Test
    @DisplayName("should work with Number hierarchy")
    void shouldWorkWithNumberHierarchy() {
      Prism<Number, Integer> intPrism = Prisms.instanceOf(Integer.class);
      Number num = Integer.valueOf(42);
      assertThat(intPrism.getOptional(num)).isPresent().contains(42);
      assertThat(intPrism.getOptional(Double.valueOf(3.14))).isEmpty();
    }
  }

  @Nested
  @DisplayName("listHead() - First Element Prism")
  class ListHeadPrism {
    private final Prism<List<String>, String> prism = Prisms.listHead();

    @Test
    @DisplayName("should extract first element from non-empty list")
    void shouldExtractFirstElement() {
      List<String> list = List.of("first", "second", "third");
      Optional<String> result = prism.getOptional(list);
      assertThat(result).isPresent().contains("first");
    }

    @Test
    @DisplayName("should return empty for empty list")
    void shouldReturnEmptyForEmptyList() {
      List<String> list = List.of();
      Optional<String> result = prism.getOptional(list);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build singleton list")
    void shouldBuild() {
      List<String> result = prism.build("new");
      assertThat(result).containsExactly("new");
    }

    @Test
    @DisplayName("should match non-empty list")
    void shouldMatchNonEmpty() {
      assertThat(prism.matches(List.of("a", "b"))).isTrue();
    }

    @Test
    @DisplayName("should not match empty list")
    void shouldNotMatchEmpty() {
      assertThat(prism.matches(List.of())).isFalse();
    }

    @Test
    @DisplayName("should extract head from single element list")
    void shouldExtractFromSingleElement() {
      List<String> list = List.of("only");
      Optional<String> result = prism.getOptional(list);
      assertThat(result).isPresent().contains("only");
    }
  }

  @Nested
  @DisplayName("listAt() - Index-based Prism")
  class ListAtPrism {
    private final Prism<List<String>, String> prism = Prisms.listAt(1);

    @Test
    @DisplayName("should extract element at valid index")
    void shouldExtractAtValidIndex() {
      List<String> list = List.of("first", "second", "third");
      Optional<String> result = prism.getOptional(list);
      assertThat(result).isPresent().contains("second");
    }

    @Test
    @DisplayName("should return empty for index out of bounds")
    void shouldReturnEmptyForOutOfBounds() {
      List<String> list = List.of("only");
      Optional<String> result = prism.getOptional(list);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty for negative index")
    void shouldReturnEmptyForNegativeIndex() {
      Prism<List<String>, String> negativePrism = Prisms.listAt(-1);
      List<String> list = List.of("a", "b", "c");
      Optional<String> result = negativePrism.getOptional(list);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should throw UnsupportedOperationException on build")
    void shouldThrowOnBuild() {
      assertThatThrownBy(() -> prism.build("value"))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("Cannot build a list from an indexed element");
    }

    @Test
    @DisplayName("should match when index exists")
    void shouldMatchWhenIndexExists() {
      assertThat(prism.matches(List.of("a", "b", "c"))).isTrue();
    }

    @Test
    @DisplayName("should not match when index does not exist")
    void shouldNotMatchWhenIndexNotExists() {
      assertThat(prism.matches(List.of("only"))).isFalse();
    }

    @Test
    @DisplayName("should work with index 0")
    void shouldWorkWithIndexZero() {
      Prism<List<String>, String> zeroPrism = Prisms.listAt(0);
      List<String> list = List.of("first", "second");
      assertThat(zeroPrism.getOptional(list)).isPresent().contains("first");
    }
  }

  @Nested
  @DisplayName("listLast() - Last Element Prism")
  class ListLastPrism {
    private final Prism<List<String>, String> prism = Prisms.listLast();

    @Test
    @DisplayName("should extract last element from non-empty list")
    void shouldExtractLastElement() {
      List<String> list = List.of("first", "second", "third");
      Optional<String> result = prism.getOptional(list);
      assertThat(result).isPresent().contains("third");
    }

    @Test
    @DisplayName("should return empty for empty list")
    void shouldReturnEmptyForEmptyList() {
      List<String> list = List.of();
      Optional<String> result = prism.getOptional(list);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build singleton list")
    void shouldBuild() {
      List<String> result = prism.build("new");
      assertThat(result).containsExactly("new");
    }

    @Test
    @DisplayName("should match non-empty list")
    void shouldMatchNonEmpty() {
      assertThat(prism.matches(List.of("a", "b"))).isTrue();
    }

    @Test
    @DisplayName("should not match empty list")
    void shouldNotMatchEmpty() {
      assertThat(prism.matches(List.of())).isFalse();
    }

    @Test
    @DisplayName("should extract last from single element list")
    void shouldExtractFromSingleElement() {
      List<String> list = List.of("only");
      Optional<String> result = prism.getOptional(list);
      assertThat(result).isPresent().contains("only");
    }
  }

  @Nested
  @DisplayName("just() - Maybe Just Prism")
  class JustPrism {
    private final Prism<Maybe<String>, String> prism = Prisms.just();

    @Test
    @DisplayName("should extract value from Just")
    void shouldExtractFromJust() {
      Maybe<String> maybe = Maybe.just("hello");
      Optional<String> result = prism.getOptional(maybe);
      assertThat(result).isPresent().contains("hello");
    }

    @Test
    @DisplayName("should return empty for Nothing")
    void shouldReturnEmptyForNothing() {
      Maybe<String> maybe = Maybe.nothing();
      Optional<String> result = prism.getOptional(maybe);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build Just from value")
    void shouldBuild() {
      Maybe<String> result = prism.build("world");
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo("world");
    }

    @Test
    @DisplayName("should match Just")
    void shouldMatchJust() {
      assertThat(prism.matches(Maybe.just("test"))).isTrue();
    }

    @Test
    @DisplayName("should not match Nothing")
    void shouldNotMatchNothing() {
      assertThat(prism.matches(Maybe.nothing())).isFalse();
    }
  }

  @Nested
  @DisplayName("valid() - Validated Valid Prism")
  class ValidPrism {
    private final Prism<Validated<String, Integer>, Integer> prism = Prisms.valid();

    @Test
    @DisplayName("should extract value from Valid")
    void shouldExtractFromValid() {
      Validated<String, Integer> validated = Validated.valid(42);
      Optional<Integer> result = prism.getOptional(validated);
      assertThat(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("should return empty for Invalid")
    void shouldReturnEmptyForInvalid() {
      Validated<String, Integer> validated = Validated.invalid("error");
      Optional<Integer> result = prism.getOptional(validated);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build Valid from value")
    void shouldBuild() {
      Validated<String, Integer> result = prism.build(100);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).isEqualTo(100);
    }

    @Test
    @DisplayName("should match Valid")
    void shouldMatchValid() {
      assertThat(prism.matches(Validated.valid(42))).isTrue();
    }

    @Test
    @DisplayName("should not match Invalid")
    void shouldNotMatchInvalid() {
      assertThat(prism.matches(Validated.invalid("error"))).isFalse();
    }
  }

  @Nested
  @DisplayName("invalid() - Validated Invalid Prism")
  class InvalidPrism {
    private final Prism<Validated<String, Integer>, String> prism = Prisms.invalid();

    @Test
    @DisplayName("should extract error from Invalid")
    void shouldExtractFromInvalid() {
      Validated<String, Integer> validated = Validated.invalid("error");
      Optional<String> result = prism.getOptional(validated);
      assertThat(result).isPresent().contains("error");
    }

    @Test
    @DisplayName("should return empty for Valid")
    void shouldReturnEmptyForValid() {
      Validated<String, Integer> validated = Validated.valid(42);
      Optional<String> result = prism.getOptional(validated);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build Invalid from error")
    void shouldBuild() {
      Validated<String, Integer> result = prism.build("failure");
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).isEqualTo("failure");
    }

    @Test
    @DisplayName("should match Invalid")
    void shouldMatchInvalid() {
      assertThat(prism.matches(Validated.invalid("error"))).isTrue();
    }

    @Test
    @DisplayName("should not match Valid")
    void shouldNotMatchValid() {
      assertThat(prism.matches(Validated.valid(42))).isFalse();
    }
  }

  @Nested
  @DisplayName("success() - Try Success Prism")
  class SuccessPrism {
    private final Prism<Try<Integer>, Integer> prism = Prisms.success();

    @Test
    @DisplayName("should extract value from Success")
    void shouldExtractFromSuccess() {
      Try<Integer> tryValue = Try.success(42);
      Optional<Integer> result = prism.getOptional(tryValue);
      assertThat(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("should return empty for Failure")
    void shouldReturnEmptyForFailure() {
      Try<Integer> tryValue = Try.failure(new Exception("error"));
      Optional<Integer> result = prism.getOptional(tryValue);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build Success from value")
    void shouldBuild() {
      Try<Integer> result = prism.build(100);
      assertThat(result.isSuccess()).isTrue();
      Integer value =
          result.fold(
              v -> v,
              error -> {
                throw new AssertionError("Expected success", error);
              });
      assertThat(value).isEqualTo(100);
    }

    @Test
    @DisplayName("should match Success")
    void shouldMatchSuccess() {
      assertThat(prism.matches(Try.success(42))).isTrue();
    }

    @Test
    @DisplayName("should not match Failure")
    void shouldNotMatchFailure() {
      assertThat(prism.matches(Try.failure(new Exception("error")))).isFalse();
    }
  }

  @Nested
  @DisplayName("failure() - Try Failure Prism")
  class FailurePrism {
    private final Prism<Try<Integer>, Throwable> prism = Prisms.failure();

    @Test
    @DisplayName("should extract exception from Failure")
    void shouldExtractFromFailure() {
      Exception error = new Exception("error");
      Try<Integer> tryValue = Try.failure(error);
      Optional<Throwable> result = prism.getOptional(tryValue);
      assertThat(result).isPresent().contains(error);
    }

    @Test
    @DisplayName("should return empty for Success")
    void shouldReturnEmptyForSuccess() {
      Try<Integer> tryValue = Try.success(42);
      Optional<Throwable> result = prism.getOptional(tryValue);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build Failure from exception")
    void shouldBuild() {
      RuntimeException error = new RuntimeException("fail");
      Try<Integer> result = prism.build(error);
      assertThat(result.isFailure()).isTrue();
      Throwable actual =
          result.fold(
              success -> {
                throw new AssertionError();
              },
              failure -> failure);
      assertThat(actual).isEqualTo(error);
    }

    @Test
    @DisplayName("should match Failure")
    void shouldMatchFailure() {
      assertThat(prism.matches(Try.failure(new Exception("error")))).isTrue();
    }

    @Test
    @DisplayName("should not match Success")
    void shouldNotMatchSuccess() {
      assertThat(prism.matches(Try.success(42))).isFalse();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {
    @Test
    @DisplayName("should compose prisms for deep navigation")
    void shouldComposePrisms() {
      Prism<Optional<Either<String, Integer>>, Either<String, Integer>> somePrism = Prisms.some();
      Prism<Either<String, Integer>, Integer> rightPrism = Prisms.right();
      Prism<Optional<Either<String, Integer>>, Integer> composed = somePrism.andThen(rightPrism);

      Optional<Either<String, Integer>> value = Optional.of(Either.right(42));
      assertThat(composed.getOptional(value)).isPresent().contains(42);

      Optional<Either<String, Integer>> leftValue = Optional.of(Either.left("error"));
      assertThat(composed.getOptional(leftValue)).isEmpty();

      Optional<Either<String, Integer>> emptyValue = Optional.empty();
      assertThat(composed.getOptional(emptyValue)).isEmpty();
    }

    @Test
    @DisplayName("should use orElse for fallback matching")
    void shouldUseOrElseForFallback() {
      Prism<Either<String, Integer>, String> leftPrism = Prisms.left();
      Prism<Either<String, Integer>, String> rightAsString =
          Prism.of(
              either ->
                  either.isRight()
                      ? Optional.of(String.valueOf(either.getRight()))
                      : Optional.empty(),
              s -> Either.right(Integer.parseInt(s)));

      Prism<Either<String, Integer>, String> combined = leftPrism.orElse(rightAsString);

      assertThat(combined.getOptional(Either.left("error"))).isPresent().contains("error");
      assertThat(combined.getOptional(Either.right(42))).isPresent().contains("42");
    }

    @Test
    @DisplayName("should combine with modify for type-safe transformations")
    void shouldCombineWithModify() {
      Prism<List<String>, String> headPrism = Prisms.listHead();
      List<String> list = List.of("hello", "world");
      List<String> result = headPrism.modify(String::toUpperCase, list);
      // Note: build creates singleton list
      assertThat(result).containsExactly("HELLO");
    }
  }
}
