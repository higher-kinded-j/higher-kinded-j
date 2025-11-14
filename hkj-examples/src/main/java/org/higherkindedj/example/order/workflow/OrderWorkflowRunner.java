// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import static java.util.Objects.requireNonNull;
import static org.higherkindedj.example.order.model.WorkflowModels.*;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.either_t.EitherTKindHelper;
import org.higherkindedj.hkt.either_t.EitherTMonad;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.jspecify.annotations.NonNull;

/**
 * Orchestrates progressive demonstrations of order processing workflows using Higher-Kinded-J.
 *
 * <p>This runner executes <b>multiple workflow implementations</b>, each showcasing different
 * Higher-Kinded-J features. Together, they illustrate how functional abstractions solve real-world
 * problems in async programming with typed error handling.
 *
 * <h2>The Workflows</h2>
 *
 * <p>Each workflow processes the same order through validation, inventory checks, payment,
 * shipping, and customer notification, but demonstrates different techniques:
 *
 * <table border="1">
 *   <caption>Workflow Comparison</caption>
 *   <tr><th>Workflow</th><th>Feature</th><th>Purpose</th></tr>
 *   <tr><td>{@link OrderWorkflowTraditional}</td><td>Plain Java (the "before")</td>
 *       <td>Shows the pain: nested callbacks, no type-safe errors, manual short-circuiting</td></tr>
 *   <tr><td>{@link Workflow1}</td><td>EitherT + For comprehension</td>
 *       <td>Clean async composition with typed errors and automatic short-circuiting</td></tr>
 *   <tr><td>{@link Workflow2}</td><td>Try integration</td>
 *       <td>Wrapping exception-throwing code and converting to Either</td></tr>
 *   <tr><td>{@link WorkflowLensAndPrism}</td><td>Optics (Lenses & Prisms)</td>
 *       <td>Declarative immutable state updates and pattern matching on errors</td></tr>
 *   <tr><td>{@link WorkflowTraverse}</td><td>Traverse + Validated</td>
 *       <td>Accumulating multiple validation errors instead of fail-fast</td></tr>
 *   <tr><td>{@link WorkflowBifunctor}</td><td>Bifunctor</td>
 *       <td>Transforming both error and success channels for API boundaries</td></tr>
 * </table>
 *
 * <h2>Key Demonstrations</h2>
 *
 * <ul>
 *   <li><b>Dependency Injection:</b> Clean separation via {@link Dependencies} record
 *   <li><b>Structured Logging:</b> Explicit logger dependency instead of {@code System.out}
 *   <li><b>EitherT for Async/Error Flow:</b> Flattening {@code CompletableFuture<Either<DomainError, T>>}
 *   <li><b>Error Handling Strategies:</b> Fail-fast vs. error accumulation
 *   <li><b>Error Recovery:</b> Using {@code handleErrorWith} for conditional recovery
 *   <li><b>Type Safety:</b> Compile-time guarantees about error types
 *   <li><b>Optics:</b> Lenses for immutable updates, Prisms for pattern matching
 *   <li><b>Bifunctor:</b> Simultaneous transformations of both channels
 * </ul>
 *
 * <h2>Documentation</h2>
 *
 * <p>For a detailed walkthrough with explanations of the functional programming concepts, see
 * <a href="https://higher-kinded-j.github.io/hkts/order-walkthrough.html">order-walkthrough.md</a>
 *
 * @see EitherT
 * @see DomainError
 * @see WorkflowModels
 */
public class OrderWorkflowRunner {

  private final OrderWorkflowTraditional workflowTraditional;
  private final Workflow1 workflowEitherT;
  private final Workflow2 workflowEitherTWithTryValidation;
  private final WorkflowLensAndPrism workflowLensAndPrism;
  private final WorkflowTraverse workflowTraverse;
  private final WorkflowBifunctor workflowBifunctor;

  /**
   * Constructs an {@code OrderWorkflowRunner} with the specified dependencies.
   *
   * <p>Initialises all workflow variants:
   *
   * <ul>
   *   <li>{@link OrderWorkflowTraditional} - Plain Java for comparison
   *   <li>{@link Workflow1} - EitherT with Either-based validation
   *   <li>{@link Workflow2} - EitherT with Try-based validation
   *   <li>{@link WorkflowLensAndPrism} - Optics for immutable updates
   *   <li>{@link WorkflowTraverse} - Error accumulation with Validated
   *   <li>{@link WorkflowBifunctor} - Bifunctor for dual transformations
   * </ul>
   *
   * @param dependencies The dependencies required for the workflows, such as logging. Cannot be
   *     null.
   */
  public OrderWorkflowRunner(@NonNull Dependencies dependencies) {
    requireNonNull(dependencies, "Dependencies cannot be null");
    OrderWorkflowSteps steps = new OrderWorkflowSteps(dependencies);
    MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
        CompletableFutureMonad.INSTANCE;
    MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
        eitherTMonad = new EitherTMonad<>(futureMonad);

    workflowTraditional = new OrderWorkflowTraditional(dependencies);
    workflowEitherT = new Workflow1(dependencies, steps, futureMonad, eitherTMonad);
    workflowEitherTWithTryValidation =
        new Workflow2(dependencies, steps, futureMonad, eitherTMonad);
    workflowLensAndPrism = new WorkflowLensAndPrism(dependencies, steps, futureMonad, eitherTMonad);
    workflowTraverse = new WorkflowTraverse(dependencies, steps, futureMonad, eitherTMonad);
    workflowBifunctor = new WorkflowBifunctor(dependencies, steps, futureMonad, eitherTMonad);
  }

  public static void main(String[] args) {

    Consumer<String> consoleLogger = System.out::println;
    var appDependencies = new Dependencies(consoleLogger);
    var runner = new OrderWorkflowRunner(appDependencies);

    // Test Data
    var goodData =
        new OrderData(
            "Order-Good-001", "PROD-123", 2, "VALID_CARD", "123 Main St", "cust-good", List.of());
    var badQtyData =
        new OrderData(
            "Order-BadQty-002", "PROD-456", 0, "VALID_CARD", "456 Oak Ave", "cust-ok", List.of());
    var stockData =
        new OrderData(
            "Order-Stock-003",
            "OUT_OF_STOCK",
            1,
            "VALID_CARD",
            "789 Pine Ln",
            "cust-stock",
            List.of());
    var paymentData =
        new OrderData(
            "Order-Pay-004", "PROD-789", 1, "INVALID_CARD", "101 Maple Dr", "cust-pay", List.of());
    var recoverableShippingData =
        new OrderData(
            "FAIL_SHIPMENT",
            "PROD-SHIP-REC",
            1,
            "VALID_CARD",
            "1 Recovery Lane",
            "cust-ship-rec",
            List.of());
    var nonRecoverableShippingData =
        new OrderData(
            "Order-ShipFail-005", "PROD-SHIP", 1, "VALID_CARD", "", "cust-ship", List.of());
    var goodPromoData =
        new OrderData(
            "Order-GoodPromo-006",
            "PROD-PROMO",
            1,
            "VALID_CARD",
            "777 Promo Ave",
            "cust-promo",
            List.of("PROMO_A", "PROMO_B"));
    var badPromoData =
        new OrderData(
            "Order-BadPromo-007",
            "PROD-PROMO",
            1,
            "VALID_CARD",
            "888 Promo Ave",
            "cust-promo-bad",
            List.of("PROMO_A", "BAD_CODE"));

    // ====================================================================
    // PART 1: The Traditional Approach (The "Before")
    // ====================================================================
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║  PART 1: Traditional Java Approach (What We're Avoiding)      ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
    System.out.println("\nNotice: Deep nesting, no type-safe errors, complex error handling\n");

    runTraditionalWorkflow("Good Order", runner.workflowTraditional::processOrder, goodData);
    runTraditionalWorkflow("Bad Quantity", runner.workflowTraditional::processOrder, badQtyData);

    // ====================================================================
    // PART 2: Higher-Kinded-J Workflows (The "After")
    // ====================================================================
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║  PART 2: Higher-Kinded-J Solutions (Clean & Type-Safe)        ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("\n--- Workflow 1: EitherT (Validate using Either) ---");
    runAndPrintResult("Good (Either Valid)", runner.workflowEitherT::run, goodData);
    runAndPrintResult("Bad Qty (Either Valid)", runner.workflowEitherT::run, badQtyData);
    runAndPrintResult("Stock Error (Either Valid)", runner.workflowEitherT::run, stockData);
    runAndPrintResult("Payment Error (Either Valid)", runner.workflowEitherT::run, paymentData);
    runAndPrintResult(
        "Recoverable Shipping (Either Valid)",
        runner.workflowEitherT::run,
        recoverableShippingData);
    runAndPrintResult(
        "Non-Recoverable Shipping (Either Valid)",
        runner.workflowEitherT::run,
        nonRecoverableShippingData);

    System.out.println("\n--- Running Workflow with EitherT (Validate using Try) ---");
    runAndPrintResult("Good (Try Valid)", runner.workflowEitherTWithTryValidation::run, goodData);
    runAndPrintResult(
        "Bad Qty (Try Valid)", runner.workflowEitherTWithTryValidation::run, badQtyData);
    runAndPrintResult(
        "Stock Error (Try Valid)", runner.workflowEitherTWithTryValidation::run, stockData);
    runAndPrintResult(
        "Payment Error (Try Valid)", runner.workflowEitherTWithTryValidation::run, paymentData);

    // Run the Optics workflows
    System.out.println(
        "\n--- Running Refactored Workflow with Lenses and Prisms (WorkflowLensAndPrism) ---");
    runAndPrintResult("Good Data (Optics)", runner.workflowLensAndPrism::run, goodData);
    runAndPrintResult(
        "Recoverable Shipping (Optics)", runner.workflowLensAndPrism::run, recoverableShippingData);

    System.out.println("\n--- Workflow 4: Traverse + Validated (Error Accumulation) ---");
    runAndPrintResult("Good Promo Codes (Traverse)", runner.workflowTraverse::run, goodPromoData);
    runAndPrintResult("Bad Promo Code (Traverse)", runner.workflowTraverse::run, badPromoData);

    // ====================================================================
    // PART 3: Bifunctor - API Boundary Transformations
    // ====================================================================
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║  PART 3: Bifunctor - Transforming Both Channels               ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
    System.out.println("\nDemonstrates using bimap() to transform error AND success channels simultaneously");
    System.out.println("Use Case: Converting internal types to client-facing API responses\n");

    System.out.println("\n--- Workflow 5: Bifunctor (Dual Channel Transformation) ---");
    runBifunctorWorkflow("Good Order (Bifunctor)", runner.workflowBifunctor::run, goodData);
    runBifunctorWorkflow("Bad Quantity (Bifunctor)", runner.workflowBifunctor::run, badQtyData);

    System.out.println(
        "\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║  Summary: Higher-Kinded-J Value Proposition                   ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
    System.out.println("\n✓ Flat composition instead of nested callbacks");
    System.out.println("✓ Type-safe error handling with Either<DomainError, T>");
    System.out.println("✓ Automatic short-circuiting on errors");
    System.out.println("✓ Principled error recovery with MonadError");
    System.out.println("✓ Error accumulation with Validated and Traverse");
    System.out.println("✓ Elegant dual transformations with Bifunctor");
    System.out.println("✓ Testable, composable, maintainable code\n");
  }

  /**
   * Helper method to run the traditional workflow and demonstrate the problems.
   *
   * @param label A descriptive label for the scenario
   * @param runnerMethod The traditional workflow method
   * @param data The order data
   */
  private static void runTraditionalWorkflow(
      String label,
      Function<OrderData, java.util.concurrent.CompletableFuture<FinalResult>> runnerMethod,
      OrderData data) {
    try {
      System.out.println("--- Running: " + label + " (Traditional) ---");
      var result = runnerMethod.apply(data).join();
      System.out.println("SUCCESS: " + result);
    } catch (CompletionException e) {
      if (e.getCause() instanceof OrderWorkflowTraditional.DomainErrorException domainEx) {
        System.err.println("BUSINESS ERROR: " + domainEx.getDomainError().message());
        System.err.println("  (Notice: Error type requires runtime checks, no compile-time safety)");
      } else {
        System.err.println("SYSTEM ERROR: " + e.getCause());
      }
    }
    System.out.println();
  }

  /**
   * Helper method to run the bifunctor workflow and show transformed results.
   *
   * @param label A descriptive label for the scenario
   * @param runnerMethod The bifunctor workflow method
   * @param data The order data
   */
  private static void runBifunctorWorkflow(
      String label,
      Function<
              OrderData,
              Kind<
                  CompletableFutureKind.Witness,
                  Either<WorkflowBifunctor.ClientError, WorkflowBifunctor.EnrichedResult>>>
          runnerMethod,
      OrderData data) {
    try {
      System.out.println("=== Executing: " + label + " ===");
      var resultKind = runnerMethod.apply(data);
      var future = FUTURE.narrow(resultKind);
      var resultEither = future.join();

      resultEither.match(
          clientError -> {
            System.out.println("CLIENT ERROR:");
            System.out.println("  Code: " + clientError.code());
            System.out.println("  Message: " + clientError.message());
            System.out.println(
                "  (Notice: Internal DomainError was transformed to client-friendly format)");
            return null;
          },
          enrichedResult -> {
            System.out.println("CLIENT SUCCESS:");
            System.out.println("  Order ID: " + enrichedResult.orderId());
            System.out.println("  Transaction ID: " + enrichedResult.transactionId());
            System.out.println("  Tracking ID: " + enrichedResult.trackingId());
            System.out.println("  Processed At: " + enrichedResult.processedAt());
            System.out.println("  Request ID: " + enrichedResult.requestId());
            System.out.println("  API Version: " + enrichedResult.apiVersion());
            System.out.println(
                "  (Notice: Result was enriched with metadata for client consumption)");
            return null;
          });
    } catch (Exception e) {
      System.err.println("ERROR (" + label + "): " + e);
    }
    System.out.println("========================================");
  }

  /**
   * Helper method to execute a given workflow function with the provided order data and print the
   * final result or any encountered errors to the console.
   *
   * @param label A descriptive label for the workflow execution scenario.
   * @param runnerMethod The workflow function to execute.
   * @param data The order data to process.
   */
  private static void runAndPrintResult(
      String label,
      Function<OrderData, Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>>
          runnerMethod,
      OrderData data) {
    try {
      System.out.println(
          "=== Executing Workflow: " + label + " for Order: " + data.orderId() + " ===");
      var resultKind = runnerMethod.apply(data);
      var future = FUTURE.narrow(resultKind);

      var resultEither = future.join();
      System.out.println("Final Result (" + label + "): " + resultEither);
    } catch (CompletionException e) {
      var cause = e.getCause();
      System.err.println(
          "ERROR ("
              + label
              + "): Workflow execution failed unexpectedly: "
              + (cause != null ? cause : e));
    } catch (Exception e) {
      System.err.println("ERROR (" + label + "): Unexpected error during result processing: " + e);
    }
    System.out.println("========================================");
  }
}
