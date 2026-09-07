// Fixture for hkj-book/src/monads/io_monad.md
//
// The page loads a config, opens a connection and logs, first eagerly and then as a description.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.time.TimeSource;

record Config(String name) {

  String getValue(String key) {
    return key;
  }
}

record Connection(String endpoint) {}

record Order(String id, List<String> items) {}

record Reservation(String orderId, List<String> items, java.time.Instant until) {}

final class Logger {

  void info(String message) {}
}

class Fixture {

  static final Monad<IOKind.Witness> ioMonad = Instances.monad(io());

  static final Logger logger = new Logger();

  static final Duration hold = Duration.ofMinutes(15);

  static final Kind<IOKind.Witness, Integer> pureValueIO = IO_OP.delay(() -> 42);

  static final TimeSource time = TimeSource.system();

  static final Kind<IOKind.Witness, Unit> printHello =
      IO_OP.delay(
          () -> {
            System.out.println("Hello from IO!");
            return Unit.INSTANCE;
          });

  static Config loadConfig() {
    return new Config("app");
  }

  static Connection connectToDb(Config config) {
    return new Connection("db://local");
  }
}
