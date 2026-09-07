// Fixture for hkj-book/src/monads/reader_monad.md
//
// One application configuration, read by several computations and then run against two
// environments.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.reader.ReaderMonad;

record AppConfig(String databaseUrl, int timeoutMillis, String apiKey) {}

class Fixture {

  static final ReaderMonad<AppConfig> readerMonad = ReaderMonad.instance();

  static final Kind<ReaderKind.Witness<AppConfig>, String> getDbUrl =
      READER.reader(AppConfig::databaseUrl);

  static final Kind<ReaderKind.Witness<AppConfig>, Integer> getTimeout =
      READER.reader(AppConfig::timeoutMillis);

  static final Kind<ReaderKind.Witness<AppConfig>, String> timeoutMessage =
      readerMonad.map(timeout -> "Timeout is: " + timeout + "ms", getTimeout);

  static final Kind<ReaderKind.Witness<AppConfig>, String> connectionStringReader =
      readerMonad.flatMap(
          dbUrl -> READER.reader(config -> dbUrl + "?apiKey=" + config.apiKey()), getDbUrl);

  static final Kind<ReaderKind.Witness<AppConfig>, String> dbInfo = connectionStringReader;

  static final Kind<ReaderKind.Witness<AppConfig>, AppConfig> getConfig = READER.ask();
}
