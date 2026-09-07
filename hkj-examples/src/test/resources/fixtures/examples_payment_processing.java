// Fixture for hkj-book/src/examples/payment_processing.md
//
// The page walks the Free-monad payment example: one program, four interpretations. The algebras,
// the interpreters and the wiring live in this module's main sources, which are on the gate's
// classpath, so the fixture supplies only what the quoted code reads.
//
// The effect package is imported on demand: two snippets declare types from it for themselves
// (the algebra and the composed record), and a single-type import of a name a snippet declares
// is a duplicate declaration.
//
// The fixture is generic in the program's witness `G`, because that is what the service is: the
// four algebras are bound to one witness, and the program is written without knowing which. The
// wrapper the gate builds around a snippet inherits the parameter, bound and all.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.io;

import java.math.BigDecimal;
import java.util.function.Function;
import org.higherkindedj.example.payment.effect.*;
import org.higherkindedj.example.payment.interpreter.*;
import org.higherkindedj.example.payment.model.AuthorisationToken;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.model.PaymentResult;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.example.payment.model.TransactionId;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.junit.jupiter.api.Test;

class Fixture<G extends WitnessArity<TypeArity.Unary>> {

  /** Risk score threshold above which a transaction is declined. */
  static final int RISK_THRESHOLD = 70;

  static final BigDecimal FEE_RATE = new BigDecimal("0.029");

  static final BigDecimal FIXED_FEE = new BigDecimal("0.30");

  /** The witness stack `PaymentEffectsWiring.interpret` is written against. */
  static final Free<
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>,
          PaymentResult>
      program = sample();

  // The gate compiles snippets; it never runs them. `sample()` stands in where building a value
  // would say nothing about the code the page is showing.
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  PaymentGatewayOpOps.Bound<G> gateway;

  FraudCheckOpOps.Bound<G> fraud;

  LedgerOpOps.Bound<G> ledger;

  NotificationOpOps.Bound<G> notification;

  Free<G, Unit> receipt;

  Free<G, PaymentResult> alertAndDecline(Customer customer, RiskScore risk) {
    return sample();
  }

  Free<G, PaymentResult> chargeAndRecord(
      Customer customer, Money amount, PaymentMethod method, RiskScore risk) {
    return sample();
  }
}
