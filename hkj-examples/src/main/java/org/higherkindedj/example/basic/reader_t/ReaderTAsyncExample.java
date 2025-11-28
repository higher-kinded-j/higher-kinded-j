// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.reader_t;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.higherkindedj.hkt.reader_t.ReaderTMonad;

/**
 * see {<a href="https://higher-kinded-j.github.io/readert_transformer.html">ReaderT
 * Transformer</a>}
 */
public class ReaderTAsyncExample {
  // --- Monad Setup ---
  // Outer Monad F = CompletableFutureKind.Witness
  static final Monad<CompletableFutureKind.Witness> futureMonad = CompletableFutureMonad.INSTANCE;
  // ReaderTMonad for AppConfig and CompletableFutureKind
  static final ReaderTMonad<CompletableFutureKind.Witness, AppConfig> cfReaderTMonad =
      new ReaderTMonad<>(futureMonad);

  // Simulates an async call to an external service
  public static Kind<CompletableFutureKind.Witness, ServiceData> fetchExternalData(
      AppConfig config, String itemId) {
    System.out.println(
        "Thread: "
            + Thread.currentThread().getName()
            + " - Fetching external data for "
            + itemId
            + " using API key: "
            + config.apiKey()
            + " from "
            + config.serviceUrl());
    CompletableFuture<ServiceData> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                TimeUnit.MILLISECONDS.sleep(100); // Simulate network latency
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
              }
              return new ServiceData("Raw data for " + itemId + " from " + config.serviceUrl());
            },
            config.executor());
    return FUTURE.widen(future);
  }

  // Operation 1: Fetch data, wrapped in ReaderT
  // This is R -> F<A> which is the core of ReaderT
  public static ReaderT<CompletableFutureKind.Witness, AppConfig, ServiceData> fetchServiceDataRT(
      String itemId) {
    return ReaderT.of(appConfig -> fetchExternalData(appConfig, itemId));
  }

  // Operation 2: Process data (sync part, depends on AppConfig, then lifts to ReaderT)
  // This uses ReaderT.reader: R -> A, then A is lifted to F<A>
  public static ReaderT<CompletableFutureKind.Witness, AppConfig, ProcessedData> processDataRT(
      ServiceData sData) {
    return ReaderT.reader(
        futureMonad, // Outer monad to lift the result
        appConfig -> { // Function R -> A (Config -> ProcessedData)
          System.out.println(
              "Thread: "
                  + Thread.currentThread().getName()
                  + " - Processing data with config: "
                  + appConfig.apiKey());
          return new ProcessedData(
              "Processed: "
                  + sData.rawData().toUpperCase()
                  + " (API Key Suffix: "
                  + appConfig.apiKey().substring(Math.max(0, appConfig.apiKey().length() - 3))
                  + ")");
        });
  }

  // --- Service Logic (depends on AppConfig, returns Future<ServiceData>) ---

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AppConfig prodConfig =
        new AppConfig("prod_secret_key_xyz", "https://api.prod.example.com", executor);
    AppConfig stagingConfig =
        new AppConfig("stag_test_key_123", "https://api.staging.example.com", executor);

    // --- Composing with ReaderTMonad.flatMap ---
    // Define a workflow: fetch data, then process it.
    // The AppConfig is threaded through automatically by ReaderT.
    Kind<ReaderTKind.Witness<CompletableFutureKind.Witness, AppConfig>, ProcessedData>
        workflowRTKind =
            cfReaderTMonad.flatMap(
                serviceData ->
                    READER_T.widen(
                        processDataRT(
                            serviceData)), // ServiceData -> ReaderTKind<..., ProcessedData>
                READER_T.widen(
                    fetchServiceDataRT("item123")) // Initial ReaderTKind<..., ServiceData>
                );

    // Unwrap to the concrete ReaderT to run it
    ReaderT<CompletableFutureKind.Witness, AppConfig, ProcessedData> composedWorkflow =
        READER_T.narrow(workflowRTKind);

    // --- Running the workflow with different configurations ---

    System.out.println("--- Running with Production Config ---");
    // Run the workflow by providing the 'prodConfig' environment
    // This returns Kind<CompletableFutureKind.Witness, ProcessedData>
    Kind<CompletableFutureKind.Witness, ProcessedData> futureResultProd =
        composedWorkflow.run().apply(prodConfig);
    ProcessedData resultProd = FUTURE.join(futureResultProd); // Blocks for result
    System.out.println("Prod Result: " + resultProd);
    // Expected output will show "prod_secret_key_xyz",
    // "[https://api.prod.example.com](https://api.prod.example.com)" in logs
    // and "Processed: RAW DATA FOR ITEM123 FROM
    // [https://api.prod.example.com](https://api.prod.example.com) (API Key Suffix: xyz)"

    System.out.println("\n--- Running with Staging Config ---");
    // Run the same workflow with 'stagingConfig'
    Kind<CompletableFutureKind.Witness, ProcessedData> futureResultStaging =
        composedWorkflow.run().apply(stagingConfig);
    ProcessedData resultStaging = FUTURE.join(futureResultStaging); // Blocks for result
    System.out.println("Staging Result: " + resultStaging);
    // Expected output will show "stag_test_key_123",
    // "[https://api.staging.example.com](https://api.staging.example.com)" in logs
    // and "Processed: RAW DATA FOR ITEM123 FROM
    // [https://api.staging.example.com](https://api.staging.example.com) (API Key Suffix: 123)"

    // --- Another example: Using ReaderT.ask ---
    ReaderT<CompletableFutureKind.Witness, AppConfig, AppConfig> getConfigSettingRT =
        ReaderT.ask(futureMonad); // Provides the whole AppConfig

    Kind<ReaderTKind.Witness<CompletableFutureKind.Witness, AppConfig>, String> getServiceUrlRT =
        cfReaderTMonad.map(
            (AppConfig cfg) -> "Service URL from ask: " + cfg.serviceUrl(),
            READER_T.widen(getConfigSettingRT));

    String stagingServiceUrl =
        FUTURE.join(READER_T.narrow(getServiceUrlRT).run().apply(stagingConfig));
    System.out.println("\nStaging Service URL via ask: " + stagingServiceUrl);

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

  // --- Environment ---
  record AppConfig(String apiKey, String serviceUrl, ExecutorService executor) {}

  // --- Service Response ---
  record ServiceData(String rawData) {}

  record ProcessedData(String info) {}
}
