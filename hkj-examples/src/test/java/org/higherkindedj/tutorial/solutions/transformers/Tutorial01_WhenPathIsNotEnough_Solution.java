// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.transformers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.either_t.EitherTMonad;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 01: When Path Isn't Enough.
 *
 * <p>This file contains the completed solutions for all exercises. Compare your answers with these
 * solutions after attempting the tutorial.
 */
@DisplayName("Tutorial 01: When Path Isn't Enough - Solutions")
public class Tutorial01_WhenPathIsNotEnough_Solution {

  sealed interface WeatherError {
    record CityNotFound(String city) implements WeatherError {}

    record ServiceUnavailable(String reason) implements WeatherError {}
  }

  record WeatherReport(String city, int temperature) {}

  record TravelAdvice(String city, String advice) {}

  private CompletableFutureMonad futureMonad;
  private EitherTMonad<CompletableFutureKind.Witness, WeatherError> eitherTMonad;

  @BeforeEach
  void setUp() {
    futureMonad = CompletableFutureMonad.INSTANCE;
    eitherTMonad = new EitherTMonad<>(futureMonad);
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
