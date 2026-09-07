// Fixture for hkj-book/src/effect/production_readiness.md
//
// One order becomes one invoice, through the chain the page reads stack traces off.
//
// `countdown` is deliberately absent: two snippets declare it themselves, one returning an
// EitherPath and one a TrampolinePath, which is the contrast the section is drawing.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TrampolinePath;

sealed interface AppError {
  record NotFound(String id) implements AppError {}
}

record Order(String id) {}

record Invoice(String orderId) {

  static Invoice empty() {
    return new Invoice("");
  }
}

final class Logger {

  void debug(String message, Object argument) {}
}

class Fixture {

  static final String orderId = "o-1";

  static final String value = "v";

  static final Logger log = new Logger();

  EitherPath<AppError, Order> lookupOrder(String id) {
    return Path.right(new Order(id));
  }

  EitherPath<AppError, Order> validateOrder(Order order) {
    return Path.right(order);
  }

  Invoice generateInvoice(Order order) {
    return new Invoice(order.id());
  }

  EitherPath<AppError, String> step1(String input) {
    return Path.right(input);
  }

  EitherPath<AppError, String> step2(String input) {
    return Path.right(input);
  }

  String step3(String input) {
    return input;
  }

  String handleError(AppError error) {
    return "recovered";
  }
}
