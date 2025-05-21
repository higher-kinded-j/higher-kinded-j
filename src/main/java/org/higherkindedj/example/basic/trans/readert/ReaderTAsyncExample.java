// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.trans.readert;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.trans.reader_t.ReaderT;
import org.higherkindedj.hkt.trans.reader_t.ReaderTKind;
import org.higherkindedj.hkt.trans.reader_t.ReaderTKindHelper;
import org.higherkindedj.hkt.trans.reader_t.ReaderTMonad;

/**
 * see {<a href="https://higher-kinded-j.github.io/readert_transformer.html">ReaderT
 * Transformer</a>}
 */
public class ReaderTAsyncExample {
  // --- Monad Setup ---
  // Outer Monad F = CompletableFutureKind.Witness
  static final Monad<CompletableFutureKind.Witness> futureMonad = new CompletableFutureMonad();
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
    return CompletableFutureKindHelper.wrap(future);
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

  public static void main(String[] args) throws Exception {
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
                    ReaderTKindHelper.wrap(
                        processDataRT(
                            serviceData)), // ServiceData -> ReaderTKind<..., ProcessedData>
                ReaderTKindHelper.wrap(
                    fetchServiceDataRT("item123")) // Initial ReaderTKind<..., ServiceData>
                );

    // Unwrap to the concrete ReaderT to run it
    ReaderT<CompletableFutureKind.Witness, AppConfig, ProcessedData> composedWorkflow =
        ReaderTKindHelper.unwrap(workflowRTKind);

    // --- Running the workflow with different configurations ---

    System.out.println("--- Running with Production Config ---");
    // Run the workflow by providing the 'prodConfig' environment
    // This returns Kind<CompletableFutureKind.Witness, ProcessedData>
    Kind<CompletableFutureKind.Witness, ProcessedData> futureResultProd =
        composedWorkflow.run().apply(prodConfig);
    ProcessedData resultProd =
        CompletableFutureKindHelper.join(futureResultProd); // Blocks for result
    System.out.println("Prod Result: " + resultProd);
    // Expected output will show "prod_secret_key_xyz",
    // "[https://api.prod.example.com](https://api.prod.example.com)" in logs
    // and "Processed: RAW DATA FOR ITEM123 FROM
    // [https://api.prod.example.com](https://api.prod.example.com) (API Key Suffix: xyz)"

    System.out.println("\n--- Running with Staging Config ---");
    // Run the same workflow with 'stagingConfig'
    Kind<CompletableFutureKind.Witness, ProcessedData> futureResultStaging =
        composedWorkflow.run().apply(stagingConfig);
    ProcessedData resultStaging =
        CompletableFutureKindHelper.join(futureResultStaging); // Blocks for result
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
            ReaderTKindHelper.wrap(getConfigSettingRT));

    String stagingServiceUrl =
        CompletableFutureKindHelper.join(
            ReaderTKindHelper.unwrap(getServiceUrlRT).run().apply(stagingConfig));
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

  // --- ReaderT-based Service Operations ---

  // --- Environment ---
  record AppConfig(String apiKey, String serviceUrl, ExecutorService executor) {}

  // --- Service Response ---
  record ServiceData(String rawData) {}

  record ProcessedData(String info) {}
}
