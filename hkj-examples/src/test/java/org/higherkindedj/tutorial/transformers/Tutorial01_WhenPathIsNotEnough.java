// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.transformers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 01: When Path Isn't Enough — Async Workflows with Typed Errors
 *
 * <p>Pain → Promise. The shape we are wrestling with is {@code CompletableFuture<Either<L, R>>}: an
 * async result that itself carries a typed error. The imperative version chains {@code thenCompose}
 * with {@code fold}, manually re-raising the {@code Left} into the next future:
 *
 * <pre>
 *   fetchWeather(city)
 *     .thenCompose(eitherReport -&gt;
 *       eitherReport.fold(
 *         err   -&gt; CompletableFuture.completedFuture(Either.&lt;L, Advice&gt;left(err)),
 *         report -&gt; buildAdvice(report).thenApply(Either::right)));
 * </pre>
 *
 * <p>The {@code EitherT} version is one fluent comprehension; a {@code Left} short-circuits the
 * rest automatically:
 *
 * <pre>
 *   For.from(eitherTMonad, EitherT.fromKind(fetchWeather(city)))
 *      .yield(this::buildAdvice);
 * </pre>
 *
 * <p>Java idiom anchor. {@code EitherT} is the shape {@code CompletableFuture&lt;Either&lt;L,
 * R&gt;&gt;} collapsed into one composable layer. We treat it as if it were a single monad while we
 * are inside it, then call {@code .value()} to get the original future-of-either back at the
 * boundary.
 *
 * <p>The Effect Path API ({@code EitherPath}, {@code MaybePath}, ...) is the recommended starting
 * point for most workflows. It handles the type plumbing for you and reads naturally in Java. But
 * sometimes you need to integrate with code that already exposes a different shape, for example a
 * third-party library that returns {@code CompletableFuture<Either<DomainError, A>>}. {@code
 * EitherPath} cannot reach inside the future, and writing nested {@code thenCompose} plus {@code
 * fold} calls is verbose and error-prone.
 *
 * <p>{@code EitherT} is the escape hatch. It lets you compose {@code CompletableFuture<Either<L,
 * R>>} as if it were a single layer, then collapse back to the future when you cross the boundary
 * into ordinary Java.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Bridge an existing {@code Future<Either>} into {@code EitherT} with {@code fromKind}
 *   <li>Lift a synchronous {@code Either} into {@code EitherT} with {@code fromEither}
 *   <li>Use {@code For} comprehensions to compose async-and-typed-error steps
 *   <li>Recover from typed errors with {@code handleErrorWith}
 *   <li>Collapse back to {@code CompletableFuture<Either<L, R>>} via {@code .value()}
 * </ul>
 *
 * <p>Prerequisites: complete Tutorial 01 and 02 of the Effect Path Journey first.
 *
 * <p>Estimated time: 25-35 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 01: When Path Isn't Enough")
public class Tutorial01_WhenPathIsNotEnough {

  // --- Domain types ---

  sealed interface WeatherError {
    record CityNotFound(String city) implements WeatherError {}

    record ServiceUnavailable(String reason) implements WeatherError {}
  }

  record WeatherReport(String city, int temperature) {}

  record TravelAdvice(String city, String advice) {}

  // --- Fixtures ---

  private MonadError<CompletableFutureKind.Witness, Throwable> futureMonad;
  private MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, WeatherError>, WeatherError>
      eitherTMonad;

  @BeforeEach
  void setUp() {
    futureMonad = Instances.monadError(completableFuture());
    eitherTMonad = Instances.eitherT(futureMonad);
  }

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  /**
   * A simulated third-party weather service. It returns the kind of shape you cannot reach into
   * with {@code EitherPath}: a {@code CompletableFuture} whose result is an {@code Either}.
   */
  private Kind<CompletableFutureKind.Witness, Either<WeatherError, WeatherReport>> fetchWeather(
      String city) {
    if (city.equals("Atlantis")) {
      return FUTURE.widen(
          CompletableFuture.completedFuture(Either.left(new WeatherError.CityNotFound(city))));
    }
    return FUTURE.widen(
        CompletableFuture.completedFuture(Either.right(new WeatherReport(city, 18))));
  }

  // ===========================================================================
  // Part 1: Bridging Future<Either> into EitherT
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Bridging the gap")
  class BridgingExercises {

    /**
     * Exercise 1: Wrap an existing Future&lt;Either&gt; into EitherT.
     *
     * <p>Given a value of type {@code Kind<F, Either<L, R>>}, {@code EitherT.fromKind} turns it
     * into an {@code EitherT<F, L, R>} that you can compose with For comprehensions.
     *
     * <p>Task: Lift the result of {@code fetchWeather("London")} into {@code EitherT}.
     */
    @Test
    @DisplayName("Exercise 1: fromKind lifts a Future<Either> into EitherT")
    void exercise1_fromKindLiftsFutureEither() {
      var london = fetchWeather("London");

      // TODO: Replace answerRequired() with EitherT.fromKind(london)
      EitherT<CompletableFutureKind.Witness, WeatherError, WeatherReport> wrapped =
          answerRequired();

      var report = FUTURE.join(wrapped.value());
      assertThatEither(report).isRight();
      assertThatEither(report).hasRightSatisfying(r -> assertThat(r.city()).isEqualTo("London"));
    }

    /**
     * Exercise 2: Collapse an EitherT back to its underlying shape.
     *
     * <p>Once you reach the edge of your effectful code, call {@code .value()} on an {@code
     * EitherT} to recover the original {@code Kind<F, Either<L, R>>}. From there you can join the
     * future or hand it back to legacy Java code.
     *
     * <p>Task: Extract the underlying future-of-either from the EitherT.
     */
    @Test
    @DisplayName("Exercise 2: .value() returns to the underlying Future<Either>")
    void exercise2_valueReturnsUnderlyingShape() {
      var wrapped = EitherT.fromKind(fetchWeather("Paris"));

      // TODO: Replace answerRequired() with wrapped.value()
      Kind<CompletableFutureKind.Witness, Either<WeatherError, WeatherReport>> underlying =
          answerRequired();

      var report = FUTURE.join(underlying);
      assertThatEither(report).hasRightSatisfying(r -> assertThat(r.city()).isEqualTo("Paris"));
    }
  }

  // ===========================================================================
  // Part 2: Composing Steps with For
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Composing steps")
  class ComposingExercises {

    /**
     * Exercise 3: Lift a synchronous Either into EitherT.
     *
     * <p>Not every step in a workflow is async. A synchronous validation step returns a plain
     * {@code Either<L, R>}. {@code EitherT.fromEither} lifts it into the same transformer you are
     * using for async steps, so a single {@code For} comprehension covers the whole workflow.
     *
     * <p>Task: Wrap a synchronous validation result so it can sit alongside async steps.
     */
    @Test
    @DisplayName("Exercise 3: fromEither lifts a synchronous Either")
    void exercise3_fromEitherLiftsSyncEither() {
      Either<WeatherError, String> validated = Either.right("Rome");

      // TODO: Replace answerRequired() with EitherT.fromEither(futureMonad, validated)
      EitherT<CompletableFutureKind.Witness, WeatherError, String> lifted = answerRequired();

      var result = FUTURE.join(lifted.value());
      assertThatEither(result).hasRight("Rome");
    }

    /**
     * Exercise 4: Chain two async steps with For.
     *
     * <p>The whole point of {@code EitherT} is that two {@code Future<Either>} steps compose like
     * ordinary monadic actions. If the first step yields {@code Left}, the second is skipped and
     * the error propagates through the future.
     *
     * <p>Task: First fetch the weather for the city, then build a {@code TravelAdvice} from the
     * report. Use {@code For.from(eitherTMonad, ...)} with one {@code .from(...)} step and a {@code
     * .yield(...)}.
     */
    @Test
    @DisplayName("Exercise 4: For.from chains two async-Either steps")
    void exercise4_forChainsAsyncSteps() {
      // TODO: Replace answerRequired() with:
      // For.from(eitherTMonad, EitherT.fromKind(fetchWeather("Berlin")))
      //     .yield(report -> new TravelAdvice(report.city(),
      //         report.temperature() < 10 ? "Pack a coat" : "Travel light"))
      Kind<EitherTKind.Witness<CompletableFutureKind.Witness, WeatherError>, TravelAdvice>
          workflow = answerRequired();

      var result = FUTURE.join(EITHER_T.narrow(workflow).value());
      assertThatEither(result).isRight();
      assertThatEither(result)
          .hasRightSatisfying(
              advice -> {
                assertThat(advice.city()).isEqualTo("Berlin");
                assertThat(advice.advice()).isEqualTo("Travel light");
              });
    }

    /**
     * Exercise 5: An error in the middle short-circuits the rest.
     *
     * <p>If any step in the comprehension yields {@code Left}, every subsequent step is skipped. No
     * nested {@code thenCompose}/{@code fold} calls, no manual error propagation.
     *
     * <p>Task: Use the same shape as exercise 4 but for the city {@code "Atlantis"}, which the
     * service rejects with {@code CityNotFound}.
     */
    @Test
    @DisplayName("Exercise 5: Left short-circuits the comprehension")
    void exercise5_leftShortCircuits() {
      // TODO: Replace answerRequired() with the same For comprehension as exercise 4 but for
      // "Atlantis":
      // For.from(eitherTMonad, EitherT.fromKind(fetchWeather("Atlantis")))
      //     .yield(report -> new TravelAdvice(report.city(), "any advice"))
      Kind<EitherTKind.Witness<CompletableFutureKind.Witness, WeatherError>, TravelAdvice>
          workflow = answerRequired();

      var result = FUTURE.join(EITHER_T.narrow(workflow).value());
      assertThatEither(result).isLeft();
      assertThatEither(result)
          .hasLeftSatisfying(err -> assertThat(err).isInstanceOf(WeatherError.CityNotFound.class));
    }
  }

  // ===========================================================================
  // Part 3: Recovery
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Recovery")
  class RecoveryExercises {

    /**
     * Exercise 6: Recover from a specific error with handleErrorWith.
     *
     * <p>{@code handleErrorWith} only fires when the inner {@code Either} is {@code Left}. Use it
     * to substitute a default report for a known recoverable error, while letting other errors
     * propagate.
     *
     * <p>Note: {@code EitherT} already implements the {@code Kind} interface, so you can pass it
     * directly to {@code handleErrorWith} without an explicit widen.
     *
     * <p>Task: When fetching weather for {@code "Atlantis"} returns {@code CityNotFound}, return a
     * default {@code WeatherReport("Unknown", 15)} instead. For other errors, re-raise.
     */
    @Test
    @DisplayName("Exercise 6: handleErrorWith substitutes a default for known errors")
    void exercise6_handleErrorWithRecovers() {
      var attempt = EitherT.fromKind(fetchWeather("Atlantis"));

      // TODO: Replace answerRequired() with:
      // eitherTMonad.handleErrorWith(
      //     attempt,
      //     err -> err instanceof WeatherError.CityNotFound
      //         ? eitherTMonad.of(new WeatherReport("Unknown", 15))
      //         : eitherTMonad.raiseError(err))
      Kind<EitherTKind.Witness<CompletableFutureKind.Witness, WeatherError>, WeatherReport>
          recovered = answerRequired();

      var result = FUTURE.join(EITHER_T.narrow(recovered).value());

      assertThatEither(result).isRight();
      assertThatEither(result).hasRightSatisfying(r -> assertThat(r.city()).isEqualTo("Unknown"));
    }
  }

  /**
   * Congratulations! You've completed Tutorial 01: When Path Isn't Enough.
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to bridge a third-party {@code Future<Either>} into the transformer world
   *   <li>How to mix synchronous validation with async steps in a single {@code For} comprehension
   *   <li>How to recover from typed errors without rebuilding the workflow
   *   <li>How to collapse back to {@code CompletableFuture<Either>} at the boundary
   * </ul>
   *
   * <p>Next up: Tutorial 02 applies the same shape to async lookups that may return nothing.
   */
}
