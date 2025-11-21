// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.reader_t;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTMonad;

public class ReaderTAsyncUnitExample {

  record AppConfig(String apiKey, String serviceUrl, ExecutorService executor) {}

  static final Monad<CompletableFutureKind.Witness> futureMonad = CompletableFutureMonad.INSTANCE;
  static final ReaderTMonad<CompletableFutureKind.Witness, AppConfig> cfReaderTMonad =
      new ReaderTMonad<>(futureMonad);

  // Action: Log a message using AppConfig, complete asynchronously returning F<Unit>
  public static Kind<CompletableFutureKind.Witness, Unit> logInitialisationAsync(AppConfig config) {
    CompletableFuture<Unit> future =
        CompletableFuture.runAsync(
                () -> {
                  System.out.println(
                      "Thread: "
                          + Thread.currentThread().getName()
                          + " - Initialising component with API Key: "
                          + config.apiKey()
                          + " for Service URL: "
                          + config.serviceUrl());
                  // Simulate some work
                  try {
                    TimeUnit.MILLISECONDS.sleep(50);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                  }
                  System.out.println(
                      "Thread: "
                          + Thread.currentThread().getName()
                          + " - Initialisation complete for: "
                          + config.serviceUrl());
                },
                config.executor())
            .thenApply(v -> Unit.INSTANCE);
    return FUTURE.widen(future);
  }

  // Wrap the action in ReaderT: R -> F<Unit>
  public static ReaderT<CompletableFutureKind.Witness, AppConfig, Unit> initialiseComponentRT() {
    return ReaderT.of(ReaderTAsyncUnitExample::logInitialisationAsync);
  }

  public static void main(String[] args) {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AppConfig prodConfig =
        new AppConfig(
            "prod_secret_for_init",
            "[https://init.prod.service](https://init.prod.service)",
            executor);

    // Get the ReaderT for the initialisation action
    ReaderT<CompletableFutureKind.Witness, AppConfig, Unit> initAction = initialiseComponentRT();

    System.out.println("--- Running Initialisation Action with Prod Config ---");
    // Run the action by providing the prodConfig environment
    // This returns Kind<CompletableFutureKind.Witness, Unit>
    Kind<CompletableFutureKind.Witness, Unit> futureUnit = initAction.run().apply(prodConfig);

    // Wait for completion and get the Unit result (which is just Unit.INSTANCE)
    Unit result = FUTURE.join(futureUnit);
    System.out.println("Initialisation Result: " + result); // Expected: Initialisation Result: ()

    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
