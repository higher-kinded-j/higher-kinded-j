// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.transformers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial01 WhenPathIsNotEnough — teaching-solution format.
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
@DisplayName("Tutorial 01: When Path Isn't Enough - Solutions")
public class Tutorial01_WhenPathIsNotEnough_Solution {

  sealed interface WeatherError {
    record CityNotFound(String city) implements WeatherError {}

    record ServiceUnavailable(String reason) implements WeatherError {}
  }

  record WeatherReport(String city, int temperature) {}

  record TravelAdvice(String city, String advice) {}

  private MonadError<CompletableFutureKind.Witness, Throwable> futureMonad;
  private MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, WeatherError>, WeatherError>
      eitherTMonad;

  @BeforeEach
  void setUp() {
    futureMonad = Instances.monadError(completableFuture());
    eitherTMonad = Instances.eitherT(futureMonad);
  }

  private Kind<CompletableFutureKind.Witness, Either<WeatherError, WeatherReport>> fetchWeather(
      String city) {
    if (city.equals("Atlantis")) {
      return FUTURE.widen(
          CompletableFuture.completedFuture(Either.left(new WeatherError.CityNotFound(city))));
    }
    return FUTURE.widen(
        CompletableFuture.completedFuture(Either.right(new WeatherReport(city, 18))));
  }

  @Nested
  @DisplayName("Part 1: Bridging the gap")
  class BridgingExercises {

    /**
     * Why this is idiomatic: {@code EitherT.fromKind} lifts a {@code Kind<F, Either<L, R>>} into
     * {@code EitherT<F, L, R>}. The transformer combines the outer effect (Future) with the inner
     * sum-type (Either) into one monad.
     *
     * <p>Alternative: chain {@code thenCompose} on the future and pattern-match the {@code Either}
     * manually. Same answer; the transformer hides the plumbing.
     *
     * <p>Common wrong attempt: assume {@code fromKind} runs the future eagerly. The transformer
     * wraps a value; the future is materialised when {@code .value()} is called and joined.
     */
    @Test
    @DisplayName("Exercise 1: fromKind lifts a Future<Either> into EitherT")
    void exercise1_fromKindLiftsFutureEither() {
      var london = fetchWeather("London");

      // SOLUTION: lift the Kind<F, Either<L, R>> into EitherT.
      EitherT<CompletableFutureKind.Witness, WeatherError, WeatherReport> wrapped =
          EitherT.fromKind(london);

      var report = FUTURE.join(wrapped.value());
      assertThat(report.isRight()).isTrue();
      assertThat(report.getRight().city()).isEqualTo("London");
    }

    /**
     * Why this is idiomatic: {@code .value()} unwraps the transformer back to its concrete {@code
     * Kind<F, Either<L, R>>}. Use it at the boundary where the outer monad's runner is needed.
     *
     * <p>Alternative: keep the transformer for the entire pipeline. Convert only at the very end.
     *
     * <p>Common wrong attempt: call {@code value()} mid-pipeline and rewrap. Loses the
     * transformer's combined-monad structure; chain operators on the transformer instead.
     */
    @Test
    @DisplayName("Exercise 2: .value() returns to the underlying Future<Either>")
    void exercise2_valueReturnsUnderlyingShape() {
      var wrapped = EitherT.fromKind(fetchWeather("Paris"));

      // SOLUTION: .value() collapses the EitherT back to its Kind<F, Either<L, R>>.
      Kind<CompletableFutureKind.Witness, Either<WeatherError, WeatherReport>> underlying =
          wrapped.value();

      var report = FUTURE.join(underlying);
      assertThat(report.getRight().city()).isEqualTo("Paris");
    }
  }

  @Nested
  @DisplayName("Part 2: Composing steps")
  class ComposingExercises {

    /**
     * Why this is idiomatic: {@code EitherT.fromEither(monad, either)} lifts a synchronous {@code
     * Either} into the transformer using the supplied monad for {@code F}. The transformer then
     * composes with async steps uniformly.
     *
     * <p>Alternative: wrap the {@code Either} in {@code futureMonad.of(either)} and call {@code
     * fromKind}. Equivalent; {@code fromEither} is the named shortcut.
     *
     * <p>Common wrong attempt: forget to supply the monad for {@code F}. The lift needs the monad
     * to know how to embed the synchronous value into {@code F}; supply it explicitly.
     */
    @Test
    @DisplayName("Exercise 3: fromEither lifts a synchronous Either")
    void exercise3_fromEitherLiftsSyncEither() {
      Either<WeatherError, String> validated = Either.right("Rome");

      // SOLUTION: fromEither needs the outer monad plus the synchronous Either.
      EitherT<CompletableFutureKind.Witness, WeatherError, String> lifted =
          EitherT.fromEither(futureMonad, validated);

      var result = FUTURE.join(lifted.value());
      assertThat(result.getRight()).isEqualTo("Rome");
    }

    /**
     * Why this is idiomatic: {@code For.from(monad, ...)} comprehension threads EitherT values
     * through a workflow. Each {@code yield} produces a new {@code Kind}; {@code EITHER_T.narrow}
     * converts back at the boundary.
     *
     * <p>Alternative: chain {@code flatMap} on the transformer directly. Same answer; the
     * comprehension keeps every binding accessible.
     *
     * <p>Common wrong attempt: forget {@code EITHER_T.narrow} before calling {@code .value()}. The
     * widened {@code Kind} cannot be narrowed implicitly; call the helper.
     */
    @Test
    @DisplayName("Exercise 4: For.from chains two async-Either steps")
    void exercise4_forChainsAsyncSteps() {
      // SOLUTION: For.yield returns a Kind; narrow before extracting .value() at the boundary.
      Kind<EitherTKind.Witness<CompletableFutureKind.Witness, WeatherError>, TravelAdvice>
          workflow =
              For.from(eitherTMonad, EitherT.fromKind(fetchWeather("Berlin")))
                  .yield(
                      report ->
                          new TravelAdvice(
                              report.city(),
                              report.temperature() < 10 ? "Pack a coat" : "Travel light"));

      var result = FUTURE.join(EITHER_T.narrow(workflow).value());
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight().city()).isEqualTo("Berlin");
      assertThat(result.getRight().advice()).isEqualTo("Travel light");
    }

    /**
     * Why this is idiomatic: a {@code Left} from any step short-circuits the EitherT comprehension;
     * later steps never run. The transformer preserves the inner monad's failure semantics.
     *
     * <p>Alternative: explicit branches after each {@code thenCompose}. Equivalent; the transformer
     * does it automatically.
     *
     * <p>Common wrong attempt: assume the future short-circuits. The future always completes; it
     * carries an {@code Either} whose {@code Left} skips later EitherT steps.
     */
    @Test
    @DisplayName("Exercise 5: Left short-circuits the comprehension")
    void exercise5_leftShortCircuits() {
      // SOLUTION: same shape but the service rejects "Atlantis" with a CityNotFound Left.
      Kind<EitherTKind.Witness<CompletableFutureKind.Witness, WeatherError>, TravelAdvice>
          workflow =
              For.from(eitherTMonad, EitherT.fromKind(fetchWeather("Atlantis")))
                  .yield(report -> new TravelAdvice(report.city(), "any advice"));

      var result = FUTURE.join(EITHER_T.narrow(workflow).value());
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isInstanceOf(WeatherError.CityNotFound.class);
    }
  }

  @Nested
  @DisplayName("Part 3: Recovery")
  class RecoveryExercises {

    /**
     * Why this is idiomatic: {@code handleErrorWith} on the EitherT monad inspects the {@code Left}
     * and may substitute a fresh EitherT (recovery) or re-raise (passthrough). The semantics extend
     * the underlying Either's recovery.
     *
     * <p>Alternative: pattern-match the {@code Either} after running. Same answer; the transformer
     * keeps the recovery composable.
     *
     * <p>Common wrong attempt: treat all errors uniformly. Recover only the known-and-handled
     * cases; let unrecognised errors propagate so callers see them.
     */
    @Test
    @DisplayName("Exercise 6: handleErrorWith substitutes a default for known errors")
    void exercise6_handleErrorWithRecovers() {
      var attempt = EitherT.fromKind(fetchWeather("Atlantis"));

      // SOLUTION: handleErrorWith receives the Left, returns a fresh EitherT for known errors,
      // and re-raises the rest.
      Kind<EitherTKind.Witness<CompletableFutureKind.Witness, WeatherError>, WeatherReport>
          recovered =
              eitherTMonad.handleErrorWith(
                  attempt,
                  err ->
                      err instanceof WeatherError.CityNotFound
                          ? eitherTMonad.of(new WeatherReport("Unknown", 15))
                          : eitherTMonad.raiseError(err));

      var result = FUTURE.join(EITHER_T.narrow(recovered).value());

      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight().city()).isEqualTo("Unknown");
    }
  }
}
