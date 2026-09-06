// Fixture for hkj-book/src/tutorials/transformers/transformers_journey.md
//
// The journey quotes one step per tutorial, so each snippet elides the transformer monad it binds
// through and the services it calls. They are supplied here, with the witnesses named: nothing else
// constrains a transformer's error or state parameter, and it would otherwise infer to Object.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.optional;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.optional_t.OptionalTKind;

record WeatherError(String message) {}

record WeatherReport(String city, int temperature) {}

record TravelAdvice(String city, String advice) {}

record AppError(String message) {}

record AppConfig(String dbUrl, int maxRetries) {}

record TutorialUser(String id, String name) {}

record TutorialProfile(String bio) {}

class Fixture {

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  static final MonadError<OptionalKind.Witness, org.higherkindedj.hkt.Unit> optionalMonad =
      Instances.monadError(optional());

  static final MonadError<
          EitherTKind.Witness<CompletableFutureKind.Witness, WeatherError>, WeatherError>
      eitherTMonad = Instances.eitherT(futureMonad);

  static final MonadError<OptionalTKind.Witness<OptionalKind.Witness>, org.higherkindedj.hkt.Unit>
      optionalTMonad = Instances.optionalT(optionalMonad);

  static final MonadError<EitherTKind.Witness<OptionalKind.Witness, AppError>, AppError>
      eitherTOverOptional = Instances.eitherT(optionalMonad);

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Kind<CompletableFutureKind.Witness, Either<WeatherError, WeatherReport>> fetchWeather(
      String city) {
    return sample();
  }

  static Kind<OptionalKind.Witness, java.util.Optional<TutorialUser>> fetchUser(String name) {
    return sample();
  }

  static Kind<OptionalKind.Witness, java.util.Optional<TutorialProfile>> fetchProfile(String id) {
    return sample();
  }
}
