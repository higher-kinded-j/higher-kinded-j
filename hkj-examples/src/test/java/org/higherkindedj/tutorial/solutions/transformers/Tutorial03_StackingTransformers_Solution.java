// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.transformers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial03 StackingTransformers — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial 03: Stacking Transformers - Solutions")
public class Tutorial03_StackingTransformers_Solution {

  sealed interface AppError {
    record InvalidInput(String reason) implements AppError {}
  }

  private MonadError<OptionalKind.Witness, Unit> optionalMonad;
  private MonadError<EitherTKind.Witness<OptionalKind.Witness, AppError>, AppError>
      eitherTOverOptional;

  @BeforeEach
  void setUp() {
    optionalMonad = Instances.monadError(optional());
    eitherTOverOptional = Instances.eitherT(optionalMonad);
  }

  @Nested
  @DisplayName("Part 1: Building the stack")
  class BuildingExercises {

    /**
     * Why this is idiomatic: stacking {@code EitherT} over {@code Optional} combines two effect
     * channels — absence (Optional) and typed error (Either). {@code fromEither} lifts a
     * synchronous Either into the stack.
     *
     * <p>Alternative: keep the two layers separate and weave them by hand. Tedious; the stacked
     * transformer hides the plumbing.
     *
     * <p>Common wrong attempt: assume the order does not matter. {@code EitherT over Optional}
     * differs semantically from {@code OptionalT over Either}; pick by which channel should
     * short-circuit which.
     */
    @Test
    @DisplayName("Exercise 1: fromEither lifts a plain Either")
    void exercise1_fromEitherLifts() {
      Either<AppError, Integer> success = Either.right(42);

      // SOLUTION: fromEither lifts the synchronous Either through the lower-stack monad.
      EitherT<OptionalKind.Witness, AppError, Integer> wrapped =
          EitherT.fromEither(optionalMonad, success);

      Optional<Either<AppError, Integer>> outer = OPTIONAL.narrow(wrapped.value());
      assertThat(outer).isPresent();
      assertThatEither(outer.get()).hasRight(42);
    }

    /**
     * Why this is idiomatic: an empty outer {@code Optional} dominates — the inner {@code Either}
     * cannot be inspected because there is nothing present. The stacked monad respects the outer
     * absence.
     *
     * <p>Alternative: handle the outer Optional explicitly with {@code orElse}. The transformer
     * carries it through; explicit handling at the boundary is usually cleaner.
     *
     * <p>Common wrong attempt: assume {@code fromKind} fills in a default Either for the empty
     * case. It does not — the outer absence stands.
     */
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

    /**
     * Why this is idiomatic: {@code For} comprehension over the stacked monad threads bindings
     * through both layers. Each step combines outer presence/absence and inner success/failure in
     * one chain.
     *
     * <p>Alternative: nested {@code thenApply} + {@code if-present} + {@code fold}. Same outcome;
     * the stacked comprehension stays declarative.
     *
     * <p>Common wrong attempt: confuse which monad to pass to {@code For.from}. Pass the stacked
     * transformer monad ({@code eitherTOverOptional}), not the underlying Optional or Either monad
     * alone.
     */
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
      assertThatEither(outer.get()).hasRight(42);
    }

    /**
     * Why this is idiomatic: an inner {@code Left} short-circuits the comprehension, but the outer
     * {@code Optional} remains {@code present} — the failure carries the typed error while the
     * layer above stays alive.
     *
     * <p>Alternative: collapse the outer Optional to handle errors uniformly. Loses the per-layer
     * information; keep the layers separate.
     *
     * <p>Common wrong attempt: assume an inner {@code Left} makes the outer empty. The two layers
     * are independent — one short-circuits without affecting the other.
     */
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
      assertThatEither(outer.get()).isLeft();
      assertThatEither(outer.get())
          .hasLeftSatisfying(err -> assertThat(err).isInstanceOf(AppError.InvalidInput.class));
    }
  }
}
