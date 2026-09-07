// Fixture for hkj-book/src/tutorials/effect/effect_journey.md
//
// The journey quotes one exercise per tutorial, so each snippet elides the steps it chains and the
// configuration it reads. They are supplied here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.expression.ForPath;

record AppConfig(String apiUrl, int timeout) {}

class Fixture {

  static final AppConfig config = new AppConfig("https://api.test", 30);

  static final Function<String, EitherPath<String, Double>> parseNumber =
      raw -> Path.right(Double.parseDouble(raw));

  static final Function<Double, EitherPath<String, Double>> validatePositive =
      value -> value > 0 ? Path.right(value) : Path.left("not positive");

  static final Function<Double, EitherPath<String, Double>> divideHundredBy =
      value -> Path.right(100.0 / value);
}
