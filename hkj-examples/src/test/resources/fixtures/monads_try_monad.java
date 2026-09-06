// Fixture for hkj-book/src/monads/try_monad.md
//
// The page divides by zero, reads a file and parses input, and recovers from each.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;

record Config(String name) {

  String getValue(String key) {
    return key;
  }
}

class Fixture {

  static final Try<Double> result1 = Try.success(5.0);

  static final Try<Double> result2 = Try.failure(new ArithmeticException("Div by zero"));

  static final Try<Double> result3 = Try.failure(new RuntimeException("Initial fail"));

  static Config loadConfig() {
    return new Config("app");
  }
}
