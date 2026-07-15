// Fixture for .claude/skills/hkj-effects/reference/interpreter-patterns.md
//
// The page's patterns are drawn from the payment example's interpreters, so the fixture imports the
// real ones: the generated interpreter skeletons (PaymentGatewayOpInterpreter, FraudCheckOpInterpreter,
// ...) come from the real annotation processor, and a snippet that declares its own interpreter is
// compiled against the same skeleton the example extends.
//
// The imports are on-demand rather than single-type on purpose: a snippet that declares its own
// FixedRiskInterpreter shadows the imported one, whereas a single-type import of a name the
// compilation unit also declares is a compile error.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.example.payment.effect.*;
import org.higherkindedj.example.payment.interpreter.*;
import org.higherkindedj.example.payment.model.*;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;

/** The result type the "combine and run" snippet is written against. */
record Result(String value) {}

class Fixture {

  static final ProductionGatewayInterpreter gateway = new ProductionGatewayInterpreter();
  static final ProductionFraudInterpreter fraud = new ProductionFraudInterpreter();
  static final ProductionLedgerInterpreter ledger = new ProductionLedgerInterpreter();
  static final ProductionNotificationInterpreter notification =
      new ProductionNotificationInterpreter();

  static final ProductionGatewayInterpreter gatewayInterpreter = gateway;
  static final ProductionFraudInterpreter fraudInterpreter = fraud;
  static final ProductionLedgerInterpreter ledgerInterpreter = ledger;
  static final ProductionNotificationInterpreter notificationInterpreter = notification;

  /** The program under interpretation. The composed witness has no name in Java: spell it once. */
  static final Free<
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>,
          Result>
      program = Free.pure(new Result("stand-in"));
}
