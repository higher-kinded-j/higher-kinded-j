// Fixture for hkj-book/src/transformers/readert_transformer.md
//
// The page fetches and processes one item against an injected AppConfig, three ways: passing the
// config by hand, a ReaderPath, and ReaderT. The config, the service calls and the two
// environments the page runs against are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;

record AppConfig(String apiKey, String serviceUrl, ExecutorService executor) {}

record ServiceData(String rawData) {}

record ProcessedData(String info) {}

record Config(String setting) {}

class Fixture {

  static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  static final AppConfig prodConfig =
      new AppConfig("prod-key", "https://api.example.com", executor);

  static final AppConfig stagingConfig =
      new AppConfig("staging-key", "https://api.staging.example.com", executor);

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  static final MonadError<OptionalKind.Witness, Unit> optMonad = Instances.monadError(optional());

  static final Monad<ReaderTKind.Witness<CompletableFutureKind.Witness, AppConfig>> readerTMonad =
      Instances.readerT(futureMonad);

  static final ReaderT<OptionalKind.Witness, Config, String> optReader =
      ReaderT.reader(optMonad, cfg -> "Data based on " + cfg.setting());

  static ServiceData callApi(String apiKey, String serviceUrl, String itemId) {
    return new ServiceData("Raw data for " + itemId + " from " + serviceUrl);
  }

  static ServiceData callApi(String apiKey, String itemId) {
    return new ServiceData("Raw data for " + itemId);
  }

  static ProcessedData transform(ServiceData data, String apiKey) {
    return new ProcessedData("Processed: " + data.rawData().toUpperCase());
  }
}
