// Fixture for hkj-book/src/transformers/common_errors.md
//
// Every section on this page pairs a snippet the compiler refuses with the one that replaces it,
// so the two halves have to be built from the same pieces: one future-backed EitherT stack and the
// domain it carries.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional_t.OptionalT;

record User(String id) {}

record Result(String value) {}

record ValidatedOrder(String id) {}

sealed interface DomainError {
  record UserLookup(String message) implements DomainError {}
}

class Fixture {

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  // Typed, unlike `var eitherTMonad = Instances.eitherT(futureMonad)`: nothing there constrains L.
  static final MonadError<
          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad = Instances.eitherT(futureMonad);

  static final Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, String>
      validatedET = EitherT.fromEither(futureMonad, Either.<DomainError, String>right("u-1"));

  static final ValidatedOrder validated = new ValidatedOrder("o-1");

  static final Kind<CompletableFutureKind.Witness, Optional<User>> future =
      FUTURE.widen(CompletableFuture.completedFuture(Optional.of(new User("u-1"))));

  static Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Result> fetchEither(
      String id) {
    return EitherT.fromEither(futureMonad, Either.<DomainError, Result>right(new Result(id)));
  }
}
