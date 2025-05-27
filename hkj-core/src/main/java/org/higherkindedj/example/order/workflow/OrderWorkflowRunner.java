// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import static java.util.Objects.requireNonNull;
import static org.higherkindedj.example.order.model.WorkflowModels.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureMonadError;
import org.higherkindedj.hkt.trans.either_t.EitherT;
import org.higherkindedj.hkt.trans.either_t.EitherTKind;
import org.higherkindedj.hkt.trans.either_t.EitherTKindHelper;
import org.higherkindedj.hkt.trans.either_t.EitherTMonad;
import org.jspecify.annotations.NonNull;

/**
 * Orchestrates an order processing workflow using the Higher-Kinded-J framework, primarily
 * leveraging the {@link EitherT} monad transformer over {@link CompletableFuture}.
 *
 * <p>This example primarily demonstrates:
 *
 * <ul>
 *   <li><b>Dependency Injection:</b> The {@link OrderWorkflowSteps} instance is now created with a
 *       {@link Dependencies} object, making dependencies like logging explicit.
 *   <li><b>Structured Logging:</b> Workflow steps use the logger provided in {@code Dependencies}
 *       instead of direct console output.
 *   <li><b>EitherT for Async/Error Flow:</b> Continues to use {@code EitherT} to manage the nested
 *       {@code CompletableFuture<Either<DomainError, T>>} structure, handling asynchronicity and
 *       domain errors gracefully.
 *   <li><b>Integration of Sync/Async Steps:</b> Shows lifting results from synchronous steps
 *       (returning {@code Either} or {@code Try}) and asynchronous steps into the unified {@code
 *       EitherT} context.
 *   <li><b>Error Handling and Recovery:</b> Uses {@code MonadError} capabilities of {@code
 *       EitherTMonad} to handle specific {@link DomainError}s (e.g., recoverable shipping errors).
 *   <li><b>Usage of Kind Helpers:</b> Demonstrates using {@link EitherTKindHelper} for
 *       wrapping/unwrapping {@code EitherT} instances when interacting with the HKT system.
 *   <li><b>Use of `var` keyword:</b> Local variables use `var` for conciseness where type is clear.
 * </ul>
 *
 * <h2>Workflow Structure:</h2>
 *
 * <p>The workflow is a sequence of operations chained using {@code eitherTMonad.flatMap}. If any
 * step results in a {@code Left<DomainError>}, subsequent steps are skipped. System-level errors
 * during async execution are caught by the underlying {@code CompletableFuture}. Logging occurs
 * within each step via the injected logger. See `order-walkthrough.md` for a detailed step-by-step
 * explanation.
 */
public class OrderWorkflowRunner {

  Workflow1 workflowEitherT;
  Workflow2 workflowEitherTWithTryValidation;

  /**
   * Constructs an {@code OrderWorkflowRunner} with the specified dependencies. Initializes two
   * workflow instances: {@link Workflow1} (using Either for validation) and {@link Workflow2}
   * (using Try for validation).
   *
   * @param dependencies The dependencies required for the workflows, such as logging. Cannot be
   *     null.
   */
  public OrderWorkflowRunner(@NonNull Dependencies dependencies) {
    requireNonNull(dependencies, "Dependencies cannot be null");
    OrderWorkflowSteps steps = new OrderWorkflowSteps(dependencies);
    MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
        new CompletableFutureMonadError();
    MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
        eitherTMonad = new EitherTMonad<>(futureMonad);

    workflowEitherT = new Workflow1(dependencies, steps, futureMonad, eitherTMonad);

    workflowEitherTWithTryValidation =
        new Workflow2(dependencies, steps, futureMonad, eitherTMonad);
  }

  public static void main(String[] args) {

    Consumer<String> consoleLogger = System.out::println;
    var appDependencies = new Dependencies(consoleLogger);
    var runner = new OrderWorkflowRunner(appDependencies);

    // Test Data
    var goodData =
        new OrderData("Order-Good-001", "PROD-123", 2, "VALID_CARD", "123 Main St", "cust-good");
    var badQtyData =
        new OrderData("Order-BadQty-002", "PROD-456", 0, "VALID_CARD", "456 Oak Ave", "cust-ok");
    var stockData =
        new OrderData(
            "Order-Stock-003", "OUT_OF_STOCK", 1, "VALID_CARD", "789 Pine Ln", "cust-stock");
    var paymentData =
        new OrderData("Order-Pay-004", "PROD-789", 1, "INVALID_CARD", "101 Maple Dr", "cust-pay");
    var recoverableShippingData =
        new OrderData(
            "FAIL_SHIPMENT", "PROD-SHIP-REC", 1, "VALID_CARD", "1 Recovery Lane", "cust-ship-rec");
    var nonRecoverableShippingData =
        new OrderData("Order-ShipFail-005", "PROD-SHIP", 1, "VALID_CARD", "", "cust-ship");

    System.out.println("\n--- Running Workflow with EitherT (Validate using Either) ---");
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
      var future = CompletableFutureKindHelper.unwrap(resultKind);

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
