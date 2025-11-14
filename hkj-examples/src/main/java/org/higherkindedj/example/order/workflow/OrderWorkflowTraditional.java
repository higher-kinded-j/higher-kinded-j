// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.example.order.model.WorkflowModels.*;

/**
 * THE TRADITIONAL APPROACH - What we're avoiding with Higher-Kinded-J
 *
 * <p>This class demonstrates the pain points of implementing async workflows with error handling
 * using only standard Java {@code CompletableFuture} APIs.
 *
 * <h2>Problems Illustrated</h2>
 *
 * <ul>
 *   <li><b>Callback Hell:</b> Deep nesting of {@code thenCompose()} creates rightward drift and
 *       poor readability
 *   <li><b>No Type-Safe Error Handling:</b> All errors are {@code Throwable}; no way to
 *       distinguish business errors from system failures at compile time
 *   <li><b>Manual Error Propagation:</b> Error checking and short-circuiting must be implemented
 *       manually in each step
 *   <li><b>Complex Error Recovery:</b> Recovering from specific errors whilst propagating others
 *       requires verbose {@code exceptionally()} or {@code handle()} chains
 *   <li><b>State Management:</b> Passing context through nested lambdas becomes cumbersome
 *   <li><b>Testing Challenges:</b> Difficult to test individual steps in isolation due to tight
 *       coupling
 * </ul>
 *
 * <p><b>Compare this to {@link Workflow1}</b> which uses {@code EitherT} to solve all these issues
 * elegantly.
 *
 * <h2>Why This Doesn't Scale</h2>
 *
 * <p>For simple workflows with 2-3 steps, this approach is manageable. But as workflows grow:
 *
 * <ul>
 *   <li>Nesting depth increases linearly with steps (5 steps = 5 levels of nesting)
 *   <li>Error handling becomes scattered across multiple {@code exceptionally()} blocks
 *   <li>Adding conditional logic or branching multiplies complexity exponentially
 *   <li>Maintaining and debugging becomes increasingly difficult
 * </ul>
 *
 * <p>This is why functional abstractions like {@code EitherT} exist: to provide compositional
 * primitives that keep code flat, type-safe, and maintainable.
 */
public class OrderWorkflowTraditional {

  private final Consumer<String> logger;
  private final OrderWorkflowSteps steps;

  public OrderWorkflowTraditional(Dependencies dependencies) {
    this.logger = dependencies.log();
    this.steps = new OrderWorkflowSteps(dependencies);
  }

  /**
   * Runs the order processing workflow using traditional {@code CompletableFuture} composition.
   *
   * <p>Notice:
   *
   * <ul>
   *   <li>Deep nesting (5 levels for 5 steps)
   *   <li>Each step must manually check for null/error conditions
   *   <li>Error handling is mixed with business logic
   *   <li>Context passing becomes increasingly awkward
   *   <li>No compile-time guarantee that errors are handled
   * </ul>
   *
   * @param orderData The initial order data
   * @return A future that completes with {@code FinalResult} on success, or fails with a {@code
   *     DomainErrorException} on business errors
   */
  public CompletableFuture<FinalResult> processOrder(OrderData orderData) {
    logger.accept("Starting Traditional Workflow for Order: " + orderData.orderId());

    // Step 1: Synchronous validation - wrap in CompletableFuture
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                var eitherResult = steps.validateOrder(orderData);
                // We have to manually unwrap the Either and throw if it's a Left
                if (eitherResult instanceof org.higherkindedj.hkt.either.Either.Left<?, ?> left) {
                  throw new DomainErrorException((DomainError) left.value());
                }
                var right = (org.higherkindedj.hkt.either.Either.Right<?, ValidatedOrder>) eitherResult;
                return right.value();
              } catch (DomainErrorException e) {
                throw e; // Rethrow business errors
              } catch (Exception e) {
                // System errors must be wrapped
                throw new CompletionException("Validation failed unexpectedly", e);
              }
            })
        // Step 2: Async inventory check
        .thenCompose(
            validatedOrder -> {
              return steps
                  .checkInventoryAsync(validatedOrder.productId(), validatedOrder.quantity())
                  .thenCompose(
                      inventoryEither -> {
                        // Again, manually unwrap Either and propagate errors
                        if (inventoryEither instanceof org.higherkindedj.hkt.either.Either.Left<?, ?> left) {
                          return CompletableFuture.failedFuture(
                              new DomainErrorException((DomainError) left.value()));
                        }
                        // Step 3: Payment processing (now 3 levels deep)
                        return steps
                            .processPaymentAsync(
                                validatedOrder.paymentDetails(), validatedOrder.amount())
                            .thenCompose(
                                paymentEither -> {
                                  if (paymentEither instanceof org.higherkindedj.hkt.either.Either.Left<?, ?> left) {
                                    return CompletableFuture.failedFuture(
                                        new DomainErrorException((DomainError) left.value()));
                                  }
                                  var payment =
                                      ((org.higherkindedj.hkt.either.Either.Right<?, PaymentConfirmation>) paymentEither).value();

                                  // Step 4: Shipping (now 4 levels deep)
                                  return steps
                                      .createShipmentAsync(
                                          validatedOrder.orderId(),
                                          validatedOrder.shippingAddress())
                                      .thenCompose(
                                          shipmentEither -> {
                                            // Error recovery for temporary shipping glitches
                                            if (shipmentEither instanceof org.higherkindedj.hkt.either.Either.Left<?, ?> left) {
                                              var error = (DomainError) left.value();
                                              if (error
                                                      instanceof DomainError.ShippingError shippingError
                                                  && "Temporary Glitch".equals(shippingError.reason())) {
                                                logger.accept(
                                                    "WARN: Recovering from temporary shipping glitch");
                                                // Recover with default shipping
                                                return CompletableFuture.completedFuture(
                                                    new ShipmentInfo("DEFAULT_SHIPPING_USED"));
                                              }
                                              // Non-recoverable shipping error
                                              return CompletableFuture.failedFuture(
                                                  new DomainErrorException(error));
                                            }
                                            var shipment =
                                                ((org.higherkindedj.hkt.either.Either.Right<?, ShipmentInfo>) shipmentEither).value();

                                            // Step 5: Notification (now 5 levels deep!)
                                            return steps
                                                .notifyCustomerAsync(
                                                    validatedOrder.customerId(),
                                                    "Order processed: " + validatedOrder.orderId())
                                                .thenApply(
                                                    notifyEither -> {
                                                      // Notification is non-critical - log failure
                                                      // but proceed
                                                      if (notifyEither instanceof org.higherkindedj.hkt.either.Either.Left<?, ?> left) {
                                                        var notifyError = (DomainError) left.value();
                                                        logger.accept(
                                                            "WARN: Notification failed: "
                                                                + notifyError.message());
                                                      }
                                                      // Finally! Build the result after all this
                                                      // nesting
                                                      return new FinalResult(
                                                          validatedOrder.orderId(),
                                                          payment.transactionId(),
                                                          shipment.trackingId());
                                                    });
                                          });
                                });
                      });
            });
  }

  /**
   * Custom exception to wrap {@link DomainError} instances in the exception hierarchy.
   *
   * <p>This is necessary because {@code CompletableFuture}'s error handling is based on {@code
   * Throwable}, not our domain error types. This approach loses type safety and requires runtime
   * checks.
   */
  public static class DomainErrorException extends RuntimeException {
    private final DomainError domainError;

    public DomainErrorException(DomainError error) {
      super(error.message());
      this.domainError = error;
    }

    public DomainError getDomainError() {
      return domainError;
    }
  }

  /**
   * Demonstrates running the traditional workflow with multiple test cases.
   *
   * <p>Notice how error handling at the call site is also problematic: we must catch exceptions
   * and check their type to determine what went wrong.
   */
  public static void main(String[] args) {
    Consumer<String> consoleLogger = System.out::println;
    var dependencies = new Dependencies(consoleLogger);
    var workflow = new OrderWorkflowTraditional(dependencies);

    var goodData =
        new OrderData(
            "Order-Good-001", "PROD-123", 2, "VALID_CARD", "123 Main St", "cust-good", java.util.List.of());
    var badQtyData =
        new OrderData(
            "Order-BadQty-002", "PROD-456", 0, "VALID_CARD", "456 Oak Ave", "cust-ok", java.util.List.of());

    System.out.println("\n=== Traditional Java Approach (The Problem) ===\n");

    // Success case
    try {
      System.out.println("--- Running: Good Order ---");
      var result = workflow.processOrder(goodData).join();
      System.out.println("SUCCESS: " + result);
    } catch (CompletionException e) {
      if (e.getCause() instanceof DomainErrorException domainEx) {
        System.err.println("BUSINESS ERROR: " + domainEx.getDomainError().message());
      } else {
        System.err.println("SYSTEM ERROR: " + e.getCause());
      }
    }
    System.out.println();

    // Failure case
    try {
      System.out.println("--- Running: Bad Quantity Order ---");
      var result = workflow.processOrder(badQtyData).join();
      System.out.println("SUCCESS: " + result);
    } catch (CompletionException e) {
      // Error handling requires instanceof checks and casting
      if (e.getCause() instanceof DomainErrorException domainEx) {
        System.err.println("BUSINESS ERROR: " + domainEx.getDomainError().message());
        // What type of error is it? More instanceof checks needed...
        if (domainEx.getDomainError() instanceof DomainError.ValidationError validationError) {
          System.err.println("  -> Validation issue: " + validationError.message());
        }
      } else {
        System.err.println("SYSTEM ERROR: " + e.getCause());
      }
    }

    System.out.println(
        "\n=== Compare this to Workflow1 using EitherT for elegant, type-safe composition! ===\n");
  }
}
