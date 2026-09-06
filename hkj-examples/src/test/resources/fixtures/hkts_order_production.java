// Fixture for hkj-book/src/hkts/order-production.md
//
// Unlike the two pages before it, this one quotes the REAL order example: its configurable
// workflow, its error hierarchy with envelopes, its generated lenses, focuses, prisms and path
// bridges. Those live in this module's main sources and are on the gate's classpath, so the page is
// held to them; the fixture supplies only the workflow's own private helpers.
//
// The order package is imported on demand, because two snippets declare a type from it for
// themselves and a single-type import of a name a snippet declares is a duplicate declaration.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.higherkindedj.example.order.config.WorkflowConfig;
import org.higherkindedj.example.order.error.*;
import org.higherkindedj.example.order.model.*;
import org.higherkindedj.example.order.model.value.*;
import org.higherkindedj.example.order.service.*;
import org.higherkindedj.example.order.workflow.*;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

record Receipt(String id) {}

class Fixture {

  static final WorkflowConfig config = WorkflowConfig.defaults();

  static final DiscountService discountService = sample();

  static final OrderRequest request = sample();

  static final OrderWorkflowState state = sample();

  static final ValidatedOrder order = sample();

  static final ValidatedOrder newOrder = sample();

  static final OrderError error = sample();

  static final Duration preflightTimeout = Duration.ofSeconds(5);

  static final Duration commitTimeout = Duration.ofSeconds(30);

  static final CustomerServicePaths customers = sample();

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  // Package-private instance methods: a snippet's declaration may only override one of the same
  // kind and access.
  RetryPolicy createRetryPolicy() {
    return RetryPolicy.exponentialBackoff(3, Duration.ofMillis(50));
  }

  EitherPath<OrderError, DiscountResult> applyDiscounts(ValidatedOrder order, Customer customer) {
    return Path.left(sample());
  }

  static EitherPath<OrderError, Receipt> chargePayment(ValidatedOrder order) {
    return Path.left(sample());
  }

  static ValidatedOrder updateOrderSubtotal(ValidatedOrder order, Money subtotal) {
    return order;
  }

  static OrderError.ShippingError recoverShipping(OrderError.ShippingError error) {
    return error;
  }

  static Unit runPreflight(OrderRequest request) {
    return Unit.INSTANCE;
  }

  static OrderResult executeWorkflow(OrderRequest request) {
    return sample();
  }

  static <A> EitherPath<OrderError, A> toEitherPath(IOPath<A> operation, String operationName) {
    return Path.left(sample());
  }
}
