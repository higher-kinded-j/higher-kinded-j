// Fixture for hkj-book/src/spring/effect_boundary_integration.md
//
// The page climbs the adoption ladder twice: once over the payment example, whose algebras,
// interpreters and service are in this module's main sources and on the gate's classpath, and once
// over an `OrderOp` the reader is imagined to have. That one is declared here as a REAL
// @EffectAlgebra, so the processor generates OrderOpKind / OrderOpKindHelper / OrderOpInterpreter
// and the page's snippets name the genuine article.
//
// Java has no type alias, so the witness of a composition is spelled out wherever it appears
// (`Interpreters.combine` nests EitherF to the right). The page says so, and spells it too.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.function.Function;
import org.higherkindedj.example.payment.effect.FraudCheckOp;
import org.higherkindedj.example.payment.effect.FraudCheckOpKind;
import org.higherkindedj.example.payment.effect.LedgerOp;
import org.higherkindedj.example.payment.effect.LedgerOpKind;
import org.higherkindedj.example.payment.effect.NotificationOp;
import org.higherkindedj.example.payment.effect.NotificationOpKind;
import org.higherkindedj.example.payment.effect.PaymentEffectsWiring;
import org.higherkindedj.example.payment.effect.PaymentGatewayOp;
import org.higherkindedj.example.payment.effect.PaymentGatewayOpInterpreter;
import org.higherkindedj.example.payment.effect.PaymentGatewayOpKind;
import org.higherkindedj.example.payment.interpreter.ProductionFraudInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionGatewayInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionLedgerInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionNotificationInterpreter;
import org.higherkindedj.example.payment.model.AuthorisationToken;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.model.PaymentResult;
import org.higherkindedj.example.payment.model.TransactionId;
import org.higherkindedj.example.payment.service.PaymentService;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.effect.boundary.TestBoundary;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.spring.actuator.HkjMetricsService;
import org.higherkindedj.spring.actuator.ObservableEffectBoundary;
import org.higherkindedj.spring.autoconfigure.effect.EnableEffectBoundary;
import org.higherkindedj.spring.autoconfigure.effect.Interpreter;
import org.higherkindedj.spring.autoconfigure.test.EffectTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the payment controller binds a request body into. */
record PaymentRequest(Customer customer, Money amount, PaymentMethod method) {}

/** The gateway the interpreter talks to; the reader's own. */
interface StripeClient {
  ChargeResult charge(Money amount, PaymentMethod method);

  AuthorisationToken authorise(Money amount, PaymentMethod method);

  ChargeResult refund(TransactionId transactionId, Money amount);
}

record OrderRequest(String customerId, String sku, int quantity) {}

enum OrderStatus {
  PENDING,
  CONFIRMED
}

record OrderResult(String orderId, OrderStatus status) {
  static OrderResult confirmed(String orderId) {
    return new OrderResult(orderId, OrderStatus.CONFIRMED);
  }
}

/** The reader's own algebra, declared for real so the processor generates its support. */
@EffectAlgebra
sealed interface OrderOp<A> permits OrderOp.PlaceOrder, OrderOp.GetStatus {

  <B> OrderOp<B> mapK(Function<? super A, ? extends B> f);

  record PlaceOrder<A>(OrderRequest request, Function<OrderResult, A> k) implements OrderOp<A> {
    @Override
    public <B> OrderOp<B> mapK(Function<? super A, ? extends B> f) {
      return new PlaceOrder<>(request, k.andThen(f));
    }
  }

  record GetStatus<A>(String orderId, Function<OrderStatus, A> k) implements OrderOp<A> {
    @Override
    public <B> OrderOp<B> mapK(Function<? super A, ? extends B> f) {
      return new GetStatus<>(orderId, k.andThen(f));
    }
  }
}

/** The reader's own service over that algebra. */
class OrderService {

  Free<OrderOpKind.Witness, OrderResult> placeOrder(OrderRequest request) {
    return Fixture.sample();
  }

  Free<OrderOpKind.Witness, OrderStatus> getOrderStatus(String orderId) {
    return Fixture.sample();
  }
}

class Fixture {

  /**
   * The witness of the four-algebra payment composition, spelled out because Java has no alias for
   * it. `Interpreters.combine` nests EitherF to the right.
   */
  static final EffectBoundary<
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      boundary = sample();

  static final PaymentService<
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      service = sample();

  static final StripeClient client = sample();

  static final OrderRequest orderRequest = new OrderRequest("C001", "ITEM-42", 2);

  // The gate compiles snippets; it never runs them.
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
