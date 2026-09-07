// Fixture for hkj-book/src/monads/writer_monad.md
//
// The page prices one basket and accumulates a receipt as it goes.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;

class Fixture {

  static final double subtotal = 100.0;

  static final Monoid<String> logMonoid =
      new Monoid<>() {
        @Override
        public String empty() {
          return "";
        }

        @Override
        public String combine(String x, String y) {
          return x + y;
        }
      };

  static final Monad<WriterKind.Witness<String>> monad = Instances.writer(logMonoid);

  static final Function<Double, Kind<WriterKind.Witness<String>, Double>> addTax =
      price -> WRITER.widen(Writer.of("Tax; ", price * 1.08));

  static final Function<Double, Kind<WriterKind.Witness<String>, Double>> applyDiscount =
      price -> WRITER.widen(Writer.of("Discount; ", price * 0.90));

  static final Function<Double, Kind<WriterKind.Witness<String>, Double>> addShipping =
      price -> WRITER.widen(Writer.of("Shipping; ", price + 5.00));

  static final Kind<WriterKind.Witness<String>, Double> finalPrice =
      WRITER.value(logMonoid, 102.06);

  static double addTax(double price, List<String> log) {
    return price * 1.08;
  }

  static double applyDiscount(double price, List<String> log) {
    return price * 0.9;
  }

  static double addShipping(double price, List<String> log) {
    return price + 5;
  }
}
