// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.transformers;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 03: Stacking Transformers — Two Effects in One Workflow
 *
 * <p>Pain → Promise. The shape we want is "may be absent AND may have failed validation". The
 * imperative version nests an {@code if (optional.isEmpty())} around a try/catch (or around a
 * {@code switch} on a sealed error sum), and every step has to remember both exits:
 *
 * <pre>
 *   if (input.isEmpty()) {
 *     return Optional.empty();        // skipped
 *   }
 *   try {
 *     return Optional.of(parse(input.get()));   // value or thrown error
 *   } catch (ValidationException e) {
 *     return Optional.of(Either.left(toError(e)));
 *   }
 * </pre>
 *
 * <p>The stacked-transformer version is one comprehension. Empty propagates as empty; {@code Left}
 * propagates as {@code Left}; the happy path looks like ordinary monadic chaining:
 *
 * <pre>
 *   For.from(eitherTOverOptionalMonad, lift(parse(input)))
 *      .from(value -&gt; lift(validate(value)))
 *      .yield(...);
 * </pre>
 *
 * <p>Java idiom anchor. A stacked transformer is two effects glued together so a single
 * comprehension can talk about them both. We pay a more verbose witness type in exchange for not
 * having to manage the two short-circuit paths by hand.
 *
 * <p>Most workflows need only one transformer. When you genuinely need two effects together (for
 * example, "a value that may be absent AND may have failed validation"), transformers can stack:
 * one layer wraps another. The outer monad of the upper layer is the witness type of the lower
 * layer.
 *
 * <p>This tutorial walks through one stack: {@code EitherT<OptionalKind.Witness, AppError, A>}, an
 * Either that lives inside an Optional. The semantics: each step can fail with a typed error
 * (Either) and the whole computation can also be skipped if the host Optional is empty.
 *
 * <p>When stacking gets uncomfortable, there is a better tool: MTL (Tutorial 04) lets you write
 * polymorphic code without naming any concrete stack at all.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>The outer monad of an upper transformer is the witness type of the lower transformer
 *   <li>You need a {@code Monad} instance for the lower stack to build the upper
 *   <li>{@code For} comprehensions still work; the witness type is just longer
 *   <li>Reach for MTL when the type signatures start to dominate the code
 * </ul>
 *
 * <p>Prerequisites: complete Tutorial 01 and 02.
 *
 * <p>Estimated time: 15-20 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 03: Stacking Transformers")
public class Tutorial03_StackingTransformers {

  // --- Domain types ---

  sealed interface AppError {
    record InvalidInput(String reason) implements AppError {}
  }

  // --- Fixtures ---

  private MonadError<OptionalKind.Witness, Unit> optionalMonad;
  private MonadError<EitherTKind.Witness<OptionalKind.Witness, AppError>, AppError>
      eitherTOverOptional;

  @BeforeEach
  void setUp() {
    optionalMonad = Instances.monadError(optional());
    // The outer monad of EitherT is OptionalKind.Witness — an Optional sits underneath.
    eitherTOverOptional = Instances.eitherT(optionalMonad);
  }

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Building a stacked value
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Building the stack")
  class BuildingExercises {

    /**
     * Exercise 1: Lift a plain Either into the stacked transformer.
     *
     * <p>{@code EitherT.fromEither} accepts the lower-stack monad ({@code optionalMonad} here) and
     * a synchronous {@code Either}, then wraps everything so the result can compose with other
     * stacked operations.
     *
     * <p>Task: Lift {@code Either.right(42)} into the {@code EitherT<OptionalKind.Witness,
     * AppError, Integer>} stack.
     */
    @Test
    @DisplayName("Exercise 1: fromEither lifts a plain Either")
    void exercise1_fromEitherLifts() {
      Either<AppError, Integer> success = Either.right(42);

      // TODO: Replace answerRequired() with EitherT.fromEither(optionalMonad, success)
      EitherT<OptionalKind.Witness, AppError, Integer> wrapped = answerRequired();

      Optional<Either<AppError, Integer>> outer = OPTIONAL.narrow(wrapped.value());
      assertThat(outer).isPresent();
      assertThat(outer.get().getRight()).isEqualTo(42);
    }

    /**
     * Exercise 2: Build a stacked value where the host Optional is empty.
     *
     * <p>Use {@code EitherT.fromKind} with an outer {@code Optional.empty()} to represent "the
     * whole computation is skipped". The inner {@code Either} layer never even gets evaluated.
     *
     * <p>Task: Wrap an empty Optional so the resulting EitherT carries no value at all.
     */
    @Test
    @DisplayName("Exercise 2: fromKind on Optional.empty() makes the whole stack absent")
    void exercise2_emptyOuterOptional() {
      var emptyOuter = OPTIONAL.<Either<AppError, Integer>>widen(Optional.empty());

      // TODO: Replace answerRequired() with EitherT.fromKind(emptyOuter)
      EitherT<OptionalKind.Witness, AppError, Integer> wrapped = answerRequired();

      Optional<Either<AppError, Integer>> outer = OPTIONAL.narrow(wrapped.value());
      assertThat(outer).isEmpty();
    }
  }

  // ===========================================================================
  // Part 2: Composing
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Composing stacked steps")
  class ComposingExercises {

    /**
     * Exercise 3: For still works on a stacked monad.
     *
     * <p>The witness type {@code EitherTKind.Witness<OptionalKind.Witness, AppError>} is verbose,
     * but {@code For.from(eitherTOverOptional, ...)} hides it. The body of the comprehension looks
     * identical to a single-layer workflow.
     *
     * <p>Task: Chain two {@code fromEither} steps and yield their sum.
     */
    @Test
    @DisplayName("Exercise 3: For threads through both layers")
    void exercise3_forOverStackedMonad() {
      Either<AppError, Integer> first = Either.right(10);
      Either<AppError, Integer> second = Either.right(32);

      // TODO: Replace answerRequired() with:
      // For.from(eitherTOverOptional, EitherT.fromEither(optionalMonad, first))
      //     .from(_ -> EitherT.fromEither(optionalMonad, second))
      //     .yield((a, b) -> a + b)
      Kind<EitherTKind.Witness<OptionalKind.Witness, AppError>, Integer> sum = answerRequired();

      Optional<Either<AppError, Integer>> outer = OPTIONAL.narrow(EITHER_T.narrow(sum).value());
      assertThat(outer.get().getRight()).isEqualTo(42);
    }

    /**
     * Exercise 4: An inner Left short-circuits the comprehension.
     *
     * <p>Just like the single-layer case, a {@code Left} stops further steps. The outer Optional is
     * still present (the workflow ran), but its content is the propagated error.
     *
     * <p>Task: Use the same shape as exercise 3, but the second step is a Left.
     */
    @Test
    @DisplayName("Exercise 4: Inner Left propagates while the outer Optional stays present")
    void exercise4_innerLeftPropagates() {
      Either<AppError, Integer> ok = Either.right(10);
      Either<AppError, Integer> bad = Either.left(new AppError.InvalidInput("nope"));

      // TODO: Replace answerRequired() with:
      // For.from(eitherTOverOptional, EitherT.fromEither(optionalMonad, ok))
      //     .from(_ -> EitherT.fromEither(optionalMonad, bad))
      //     .yield((a, b) -> a + b)
      Kind<EitherTKind.Witness<OptionalKind.Witness, AppError>, Integer> result = answerRequired();

      Optional<Either<AppError, Integer>> outer = OPTIONAL.narrow(EITHER_T.narrow(result).value());
      assertThat(outer).isPresent();
      assertThat(outer.get().isLeft()).isTrue();
      assertThat(outer.get().getLeft()).isInstanceOf(AppError.InvalidInput.class);
    }
  }

  /**
   * Congratulations! You've completed Tutorial 03: Stacking Transformers.
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How a transformer's outer monad can itself be the witness of another transformer
   *   <li>Why the corresponding {@code Monad} instance for the inner stack matters
   *   <li>That {@code For} comprehensions absorb the verbosity of stacked witness types
   *   <li>That stacking adds cognitive load fast; MTL is usually the cleaner answer
   * </ul>
   *
   * <p>Next up: Tutorial 04 introduces the MTL capabilities (MonadReader, MonadState, MonadWriter)
   * that let library authors write polymorphic code without naming any concrete stack.
   */
}
