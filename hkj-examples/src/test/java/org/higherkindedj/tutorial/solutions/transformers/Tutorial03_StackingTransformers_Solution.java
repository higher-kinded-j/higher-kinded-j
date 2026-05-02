// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.transformers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.either_t.EitherTMonad;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 03: Stacking Transformers.
 *
 * <p>This file contains the completed solutions for all exercises. Compare your answers with these
 * solutions after attempting the tutorial.
 */
@DisplayName("Tutorial 03: Stacking Transformers - Solutions")
public class Tutorial03_StackingTransformers_Solution {

  sealed interface AppError {
    record InvalidInput(String reason) implements AppError {}
  }

  private OptionalMonad optionalMonad;
  private EitherTMonad<OptionalKind.Witness, AppError> eitherTOverOptional;

  @BeforeEach
  void setUp() {
    optionalMonad = OptionalMonad.INSTANCE;
    eitherTOverOptional = new EitherTMonad<>(optionalMonad);
  }

  @Nested
  @DisplayName("Part 1: Building the stack")
  class BuildingExercises {

    @Test
    @DisplayName("Exercise 1: fromEither lifts a plain Either")
    void exercise1_fromEitherLifts() {
      Either<AppError, Integer> success = Either.right(42);

      // SOLUTION: fromEither lifts the synchronous Either through the lower-stack monad.
      EitherT<OptionalKind.Witness, AppError, Integer> wrapped =
          EitherT.fromEither(optionalMonad, success);

      Optional<Either<AppError, Integer>> outer = OPTIONAL.narrow(wrapped.value());
      assertThat(outer).isPresent();
      assertThat(outer.get().getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("Exercise 2: fromKind on Optional.empty() makes the whole stack absent")
    void exercise2_emptyOuterOptional() {
      var emptyOuter = OPTIONAL.<Either<AppError, Integer>>widen(Optional.empty());

      // SOLUTION: when the outer Optional is empty, the whole computation is skipped.
      EitherT<OptionalKind.Witness, AppError, Integer> wrapped = EitherT.fromKind(emptyOuter);

      Optional<Either<AppError, Integer>> outer = OPTIONAL.narrow(wrapped.value());
      assertThat(outer).isEmpty();
    }
  }

  @Nested
  @DisplayName("Part 2: Composing stacked steps")
  class ComposingExercises {

    @Test
    @DisplayName("Exercise 3: For threads through both layers")
    void exercise3_forOverStackedMonad() {
      Either<AppError, Integer> first = Either.right(10);
      Either<AppError, Integer> second = Either.right(32);

      // SOLUTION: For.from with the stacked monad keeps the comprehension body readable.
      Kind<EitherTKind.Witness<OptionalKind.Witness, AppError>, Integer> sum =
          For.from(eitherTOverOptional, EitherT.fromEither(optionalMonad, first))
              .from(_ -> EitherT.fromEither(optionalMonad, second))
              .yield((a, b) -> a + b);

      Optional<Either<AppError, Integer>> outer = OPTIONAL.narrow(EITHER_T.narrow(sum).value());
      assertThat(outer.get().getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("Exercise 4: Inner Left propagates while the outer Optional stays present")
    void exercise4_innerLeftPropagates() {
      Either<AppError, Integer> ok = Either.right(10);
      Either<AppError, Integer> bad = Either.left(new AppError.InvalidInput("nope"));

      // SOLUTION: Left short-circuits the comprehension; the outer Optional is still present.
      Kind<EitherTKind.Witness<OptionalKind.Witness, AppError>, Integer> result =
          For.from(eitherTOverOptional, EitherT.fromEither(optionalMonad, ok))
              .from(_ -> EitherT.fromEither(optionalMonad, bad))
              .yield((a, b) -> a + b);

      Optional<Either<AppError, Integer>> outer = OPTIONAL.narrow(EITHER_T.narrow(result).value());
      assertThat(outer).isPresent();
      assertThat(outer.get().isLeft()).isTrue();
      assertThat(outer.get().getLeft()).isInstanceOf(AppError.InvalidInput.class);
    }
  }
}
