// Fixture for hkj-book/src/resilience/saga.md
//
// The page runs one order through payment, inventory and shipping, with a compensation for each.
// The three services and the assembled saga the later snippets run are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.Saga;
import org.higherkindedj.hkt.resilience.SagaBuilder;
import org.higherkindedj.hkt.resilience.SagaError;
import org.higherkindedj.hkt.resilience.SagaExecutionException;
import org.higherkindedj.hkt.vtask.VTask;

record Order(String id) {}

final class PaymentService {

  String charge(Order order) {
    return "payment-1";
  }

  void refund(String paymentId) {}
}

final class InventoryService {

  String reserve(Order order) {
    return "reservation-1";
  }

  String reserve(Order order, String paymentId) {
    return "reservation-1";
  }

  void release(String reservationId) {}
}

final class ShippingService {

  String schedule(Order order) {
    return "tracking-1";
  }

  String schedule(Order order, String reservationId) {
    return "tracking-1";
  }

  void cancel(String trackingId) {}
}

final class EmailService {

  Unit sendConfirmation(Order order, String paymentId) {
    return Unit.INSTANCE;
  }
}

final class SimpleLogger {

  void info(String message, Object... args) {}

  void error(String message, Object... args) {}
}

class Fixture {

  static final Order order = new Order("order-1");

  static final PaymentService paymentService = new PaymentService();

  static final InventoryService inventoryService = new InventoryService();

  static final ShippingService shippingService = new ShippingService();

  static final EmailService emailService = new EmailService();

  static final SimpleLogger log = new SimpleLogger();

  static final Saga<String> orderSaga =
      Saga.of(
          VTask.of(() -> paymentService.charge(new Order("order-1"))),
          paymentService::refund);

  static final SagaError error =
      orderSaga
          .runSafe()
          .run()
          .fold(sagaError -> sagaError, ignored -> null);

  static void alertOps(String stepName, Object failure) {}
}
