// Fixture for .claude/skills/hkj-effects/reference/payment-example.md
//
// This page walks through a *shipped* example, so the fixture imports it rather than restating it:
// the snippets are compiled against the real algebras, the real generated interpreter skeletons and
// the real interpreters in hkj-examples/src/main/java/org/higherkindedj/example/payment/. If one of
// those is renamed, the page fails to compile instead of quietly going stale.
//
// The imports are on-demand rather than single-type on purpose: a snippet that declares its own
// PaymentEffects (or PaymentResult) shadows the imported one, whereas a single-type import of a name
// the compilation unit also declares is a compile error.
//
// What is left is what the page elides: the program under interpretation and its result.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.function.Function;
import org.higherkindedj.example.payment.effect.*;
import org.higherkindedj.example.payment.interpreter.*;
import org.higherkindedj.example.payment.model.*;
import org.higherkindedj.example.payment.service.*;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;

class Fixture {

  /** The result of the payment program: the page interprets, it does not build, the program. */
  static final PaymentResult result = new PaymentResult.Declined("stand-in");

  /**
   * The program every interpretation mode runs. Java has no type alias, so the composed witness is
   * spelled out once here, exactly as PaymentEffectsWiring.interpret() expects it.
   */
  static final Free<
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>,
          PaymentResult>
      program = Free.pure(result);
}
