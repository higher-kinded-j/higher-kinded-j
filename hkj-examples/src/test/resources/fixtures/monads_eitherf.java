// Fixture for hkj-book/src/monads/eitherf.md
//
// The page opens on two effect algebras that cannot be combined, then composes four of them. The
// two algebras carry their `@EffectAlgebra` annotations here so the processor generates the Kind,
// Functor and smart constructors the opening snippets name; the four-algebra half runs against the
// payment example in this module's main sources, which is on the gate's classpath.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;

import java.util.function.Function;
import org.higherkindedj.example.payment.effect.PaymentEffectsWiring;
import org.higherkindedj.example.payment.interpreter.ProductionFraudInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionGatewayInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionLedgerInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionNotificationInterpreter;
import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.CustomerId;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.model.PaymentResult;
import org.higherkindedj.example.payment.service.PaymentService;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKindHelper;

@EffectAlgebra
sealed interface ConsoleOp<A> permits ConsoleOp.ReadLine, ConsoleOp.PrintLine {

  <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f);

  record ReadLine<A>(Function<String, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new ReadLine<>(k.andThen(f));
    }
  }

  record PrintLine<A>(String message, Function<Unit, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new PrintLine<>(message, k.andThen(f));
    }
  }
}

@EffectAlgebra
sealed interface DbOp<A> permits DbOp.Save, DbOp.Load {

  <B> DbOp<B> mapK(Function<? super A, ? extends B> f);

  record Save<A>(String value, Function<String, A> k) implements DbOp<A> {
    @Override
    public <B> DbOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Save<>(value, k.andThen(f));
    }
  }

  record Load<A>(String key, Function<String, A> k) implements DbOp<A> {
    @Override
    public <B> DbOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Load<>(key, k.andThen(f));
    }
  }
}

record User(String name) {}

class Fixture {

  static final Customer customer =
      new Customer(
          new CustomerId("cust-001"),
          "Jane Smith",
          "jane@example.com",
          new CustomerId("acc-001"),
          new PaymentMethod.CreditCard("4242", "VISA"));

  static final Money amount = Money.gbp("10.00");

  static final PaymentMethod method = new PaymentMethod.CreditCard("4242", "VISA");

  static final ProductionGatewayInterpreter gatewayInterpreter = new ProductionGatewayInterpreter();

  static final ProductionFraudInterpreter fraudInterpreter = new ProductionFraudInterpreter();

  static final ProductionLedgerInterpreter ledgerInterpreter = new ProductionLedgerInterpreter();

  static final ProductionNotificationInterpreter notificationInterpreter =
      new ProductionNotificationInterpreter();

  static Free<ConsoleOpKind.Witness, Unit> printLine(String message) {
    return ConsoleOpOps.printLine(message, Function.identity());
  }

  static Free<ConsoleOpKind.Witness, String> readLine() {
    return ConsoleOpOps.readLine(Function.identity());
  }

  static Free<DbOpKind.Witness, User> dbLookup(String key) {
    return DbOpOps.load(key, User::new);
  }
}
