// Fixture for hkj-book/src/transformers/eithert_transformer.md
//
// The page takes one order through validate -> inventory -> payment -> receipt, three ways: nested
// futures, a synchronous EitherPath, and EitherT. Each way needs the steps in a different shape,
// so all three are declared here, along with the shipping recovery the advanced example uses.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;

record OrderData(String id) {}

record ValidatedOrder(String id) {}

record Inventory(String orderId) {}

record Payment(String orderId) {}

record Receipt(String orderId) {}

record ShipmentInfo(String carrier) {}

sealed interface DomainError {
  record ShippingError(String reason) implements DomainError {}

  record ValidationError(String message) implements DomainError {}
}

final class WorkflowSteps {

  Kind<CompletableFutureKind.Witness, Either<DomainError, ShipmentInfo>> createShipmentAsync(
      String orderId, String address) {
    return FUTURE.widen(
        CompletableFuture.completedFuture(
            Either.<DomainError, ShipmentInfo>right(new ShipmentInfo("ROYAL_MAIL"))));
  }
}

class Fixture {

  static final String input = "order-1";

  static final String orderId = "order-1";

  static final String address = "221B Baker Street";

  static final OrderData data = new OrderData("order-1");

  static final WorkflowSteps steps = new WorkflowSteps();

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  static final MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
          DomainError>
      eitherTMonad = Instances.eitherT(futureMonad);

  static final EitherT<CompletableFutureKind.Witness, DomainError, Receipt> futureET =
      EitherT.fromEither(
          futureMonad, Either.<DomainError, Receipt>right(new Receipt("order-1")));

  /** The plain-future steps the "problem" snippet nests by hand. */
  static CompletableFuture<Either<DomainError, ValidatedOrder>> validateOrderFuture(
      OrderData data) {
    return CompletableFuture.completedFuture(
        Either.<DomainError, ValidatedOrder>right(new ValidatedOrder(data.id())));
  }

  static CompletableFuture<Either<DomainError, Inventory>> checkInventoryFuture(
      ValidatedOrder order) {
    return CompletableFuture.completedFuture(
        Either.<DomainError, Inventory>right(new Inventory(order.id())));
  }

  static CompletableFuture<Either<DomainError, Payment>> processPaymentFuture(
      Inventory inventory) {
    return CompletableFuture.completedFuture(
        Either.<DomainError, Payment>right(new Payment(inventory.orderId())));
  }

  static CompletableFuture<Either<DomainError, Receipt>> createReceiptFuture(Payment payment) {
    return CompletableFuture.completedFuture(
        Either.<DomainError, Receipt>right(new Receipt(payment.orderId())));
  }

  /** The synchronous steps EitherPath composes. */
  static Either<DomainError, ValidatedOrder> validateOrder(OrderData data) {
    return Either.right(new ValidatedOrder(data.id()));
  }

  static Either<DomainError, Inventory> checkInventory(ValidatedOrder order) {
    return Either.right(new Inventory(order.id()));
  }

  static Either<DomainError, Payment> processPayment(Inventory inventory) {
    return Either.right(new Payment(inventory.orderId()));
  }

  static Either<DomainError, Receipt> createReceipt(Payment payment) {
    return Either.right(new Receipt(payment.orderId()));
  }

  /** The Kind-shaped steps EitherT wraps. */
  static Kind<CompletableFutureKind.Witness, Either<DomainError, ValidatedOrder>>
      validateOrderAsync(OrderData data) {
    return FUTURE.widen(validateOrderFuture(data));
  }

  static Kind<CompletableFutureKind.Witness, Either<DomainError, Inventory>> checkInventoryAsync(
      ValidatedOrder order) {
    return FUTURE.widen(checkInventoryFuture(order));
  }

  static Kind<CompletableFutureKind.Witness, Either<DomainError, Payment>> processPaymentAsync(
      Inventory inventory) {
    return FUTURE.widen(processPaymentFuture(inventory));
  }

  static Kind<CompletableFutureKind.Witness, Either<DomainError, Receipt>> createReceiptAsync(
      Payment payment) {
    return FUTURE.widen(createReceiptFuture(payment));
  }
}
