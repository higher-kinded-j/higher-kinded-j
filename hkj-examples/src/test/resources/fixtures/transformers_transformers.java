// Fixture for hkj-book/src/transformers/transformers.md
//
// The chapter opener composes user -> order -> payment twice: by hand over nested futures, and
// through EitherT. The Kind-shaped steps are declared here; the by-hand snippet declares its own.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;

record User(String id) {}

record Order(String id) {}

record Receipt(String orderId) {}

sealed interface DomainError {
  record NotFound(String id) implements DomainError {}
}

class Fixture {

  // Instance methods, because the by-hand snippet declares the same three itself.
  CompletableFuture<Either<DomainError, User>> fetchUser(String userId) {
    return CompletableFuture.completedFuture(Either.right(new User(userId)));
  }

  CompletableFuture<Either<DomainError, Order>> createOrder(User user) {
    return CompletableFuture.completedFuture(Either.right(new Order(user.id())));
  }

  CompletableFuture<Either<DomainError, Receipt>> processPayment(Order order) {
    return CompletableFuture.completedFuture(Either.right(new Receipt(order.id())));
  }
}
