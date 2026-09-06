// Fixture for hkj-book/src/transformers/common_errors.md
//
// Every section on this page pairs a snippet the compiler refuses with the one that replaces it,
// so the two halves have to be built from the same pieces: one future-backed EitherT stack, one
// Id-backed StateT, and the domain they carry.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;

record User(String id) {}

record Result(String value) {}

record ValidatedOrder(String id) {}

record Counter(int count) {}

sealed interface DomainError {
  record UserLookup(String message) implements DomainError {}
}

class Fixture {

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  // Typed, unlike `var eitherTMonad = Instances.eitherT(futureMonad)`: nothing there constrains L.
  static final MonadError<
          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad = Instances.eitherT(futureMonad);

  static final Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, String>
      validatedET = EitherT.fromEither(futureMonad, Either.<DomainError, String>right("u-1"));

  static final ValidatedOrder validated = new ValidatedOrder("o-1");

  static final Kind<CompletableFutureKind.Witness, Optional<User>> future =
      FUTURE.widen(CompletableFuture.completedFuture(Optional.of(new User("u-1"))));

  static final StateT<Counter, IdKind.Witness, Integer> idState =
      StateT.create(
          counter -> ID.widen(Id.of(StateTuple.of(new Counter(counter.count() + 1), counter.count()))),
          idMonad);

  static final Function<
          Kind<IdKind.Witness, StateTuple<Counter, Integer>>,
          Kind<OptionalKind.Witness, StateTuple<Counter, Integer>>>
      idToOptional = idKind -> OPTIONAL.widen(Optional.of(ID.narrow(idKind).value()));

  static Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Result> fetchEither(
      String id) {
    return EitherT.fromEither(futureMonad, Either.<DomainError, Result>right(new Result(id)));
  }
}
