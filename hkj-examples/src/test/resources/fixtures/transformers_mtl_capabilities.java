// Fixture for hkj-book/src/transformers/mtl_capabilities.md
//
// The page shows one profile lookup written twice: pinned to ReaderT over CompletableFuture, and
// against `MonadReader` for any outer monad. The config and the API call are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.reader_t.ReaderT;

record AppConfig(String apiUrl) {}

record UserProfile(String userId) {}

class Fixture {

  static UserProfile callApi(String apiUrl, String userId) {
    return new UserProfile(userId);
  }
}
