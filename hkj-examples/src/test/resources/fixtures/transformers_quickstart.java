// Fixture for hkj-book/src/transformers/quickstart.md
//
// Three quickstarts - EitherT, OptionalT and MonadReader - each over the same order and user
// lookups. Those lookups are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional_t.OptionalT;

record Order(String id) {}

record ValidatedOrder(String id) {}

record ReservedOrder(String id) {}

record Receipt(String orderId) {}

record User(String id) {}

record Profile(String userId) {}

record UserPreferences(String theme) {}

class Fixture {

  static final Order order = new Order("order-1");

  static final String userId = "user-1";

  static Kind<CompletableFutureKind.Witness, Either<OrderError, ValidatedOrder>> validateOrder(
      Order order) {
    return FUTURE.widen(
        CompletableFuture.completedFuture(
            Either.<OrderError, ValidatedOrder>right(new ValidatedOrder(order.id()))));
  }

  static Kind<CompletableFutureKind.Witness, Either<OrderError, ReservedOrder>> checkInventory(
      ValidatedOrder order) {
    return FUTURE.widen(
        CompletableFuture.completedFuture(
            Either.<OrderError, ReservedOrder>right(new ReservedOrder(order.id()))));
  }

  static Kind<CompletableFutureKind.Witness, Either<OrderError, Receipt>> processPayment(
      ReservedOrder order) {
    return FUTURE.widen(
        CompletableFuture.completedFuture(
            Either.<OrderError, Receipt>right(new Receipt(order.id()))));
  }

  static Kind<CompletableFutureKind.Witness, Optional<User>> fetchUserAsync(String userId) {
    return FUTURE.widen(CompletableFuture.completedFuture(Optional.of(new User(userId))));
  }

  static Kind<CompletableFutureKind.Witness, Optional<Profile>> fetchProfileAsync(String userId) {
    return FUTURE.widen(CompletableFuture.completedFuture(Optional.of(new Profile(userId))));
  }

  static Kind<CompletableFutureKind.Witness, Optional<UserPreferences>> fetchPrefsAsync(
      String userId) {
    return FUTURE.widen(
        CompletableFuture.completedFuture(Optional.of(new UserPreferences("dark"))));
  }
}

sealed interface OrderError {
  record InvalidOrder(String reason) implements OrderError {}

  record OutOfStock(String sku) implements OrderError {}
}
