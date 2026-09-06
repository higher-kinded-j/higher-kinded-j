// Fixture for hkj-book/src/effect/effect_handlers_intro.md
//
// The page opens with a Spring service wired to four systems and then rebuilds it as an effect
// algebra. The domain types come from the payment example in this module's main sources, which is
// on the gate's classpath, so the algebra the page declares is the one that example really uses.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.function.Function;
import org.higherkindedj.example.payment.model.AuthorisationToken;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.model.TransactionId;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.io.IOKind;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

record RefundResult(String reference) {}

record RiskScore(int value) {

  boolean exceeds(int threshold) {
    return value > threshold;
  }
}

record AccountId(String value) {}

record Customer(AccountId accountId, PaymentMethod paymentMethod) {}

record PaymentResult(String detail) {

  static PaymentResult declined(String reason) {
    return new PaymentResult(reason);
  }

  static PaymentResult success(@Nullable TransactionId id) {
    return new PaymentResult(String.valueOf(id));
  }
}

final class PaymentGateway {

  ChargeResult charge(Money amount, PaymentMethod method) {
    return ChargeResult.success(new TransactionId("t-1"), amount);
  }
}

final class FraudDetector {

  RiskScore checkTransaction(Money amount, Customer customer) {
    return new RiskScore(0);
  }
}

final class AccountingLedger {

  Money getBalance(AccountId accountId) {
    return Money.gbp("0");
  }

  void recordTransaction(AccountId accountId, Money amount) {}
}

final class NotificationSender {

  void alertFraudTeam(Customer customer, RiskScore risk) {}

  void sendReceipt(Customer customer, ChargeResult charge) {}
}

// The algebra the page declares, so the interpreter snippet has something to switch over.
@EffectAlgebra
sealed interface PaymentGatewayOp<A>
    permits PaymentGatewayOp.Authorise, PaymentGatewayOp.Charge, PaymentGatewayOp.Refund {

  <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f);

  record Authorise<A>(Money amount, PaymentMethod method, Function<AuthorisationToken, A> k)
      implements PaymentGatewayOp<A> {
    @Override
    public <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Authorise<>(amount, method, k.andThen(f));
    }
  }

  record Charge<A>(Money amount, PaymentMethod method, Function<ChargeResult, A> k)
      implements PaymentGatewayOp<A> {
    @Override
    public <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Charge<>(amount, method, k.andThen(f));
    }
  }

  record Refund<A>(TransactionId txId, Money amount, Function<RefundResult, A> k)
      implements PaymentGatewayOp<A> {
    @Override
    public <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Refund<>(txId, amount, k.andThen(f));
    }
  }
}

class Fixture {

  static final int THRESHOLD = 80;

  <A> Kind<IOKind.Witness, A> handleAuthorise(PaymentGatewayOp.Authorise<A> op) {
    throw new UnsupportedOperationException("stands in for the real handler");
  }

  <A> Kind<IOKind.Witness, A> handleCharge(PaymentGatewayOp.Charge<A> op) {
    throw new UnsupportedOperationException("stands in for the real handler");
  }

  <A> Kind<IOKind.Witness, A> handleRefund(PaymentGatewayOp.Refund<A> op) {
    throw new UnsupportedOperationException("stands in for the real handler");
  }
}
