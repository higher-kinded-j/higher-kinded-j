// Fixture for hkj-book/src/transformers/mtl_reader.md
//
// The page writes one connection-string builder against `MonadReader` and runs it under two outer
// monads. The two monads are declared here; the config is declared by the snippets themselves.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.higherkindedj.hkt.reader_t.ReaderTMonadReader;

record AppConfig(String dbUrl, int maxRetries, boolean debugMode) {

  AppConfig withDebug(boolean debug) {
    return new AppConfig(dbUrl, maxRetries, debug);
  }
}

class Fixture {

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());
}
