// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;

import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.example.order.model.WorkflowModels;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.either.EitherKind2;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;

/**
 * Demonstrates the {@link Bifunctor} type class for elegant dual-channel transformations.
 *
 * <h2>What is Bifunctor?</h2>
 *
 * <p>A Bifunctor represents types with two type parameters (like {@code Either<L, R>}) where both
 * parameters are covariant. This means you can independently transform:
 *
 * <ul>
 *   <li><b>The Left channel</b> using {@code first()} — e.g., transforming error representations
 *   <li><b>The Right channel</b> using {@code second()} — e.g., enriching success values
 *   <li><b>Both channels simultaneously</b> using {@code bimap()} — elegant API boundary
 *       transformations
 * </ul>
 *
 * <h2>Use Case: API Boundary Transformations</h2>
 *
 * <p>This workflow demonstrates a common real-world pattern:
 *
 * <ul>
 *   <li><b>Internal Domain</b>: Workflows use detailed {@code DomainError} types for business
 *       logic
 *   <li><b>External API</b>: Clients receive simplified {@code ClientError} messages suitable for
 *       display
 *   <li><b>Response Enrichment</b>: Success values are enriched with metadata (timestamps, request
 *       IDs, etc.)
 * </ul>
 *
 * <p>Without Bifunctor, you'd need separate {@code map()} and {@code mapLeft()} calls. With {@code
 * bimap()}, you transform both channels in a single, declarative operation.
 *
 * <h2>Comparison to Functor</h2>
 *
 * <p>{@link org.higherkindedj.hkt.Functor} only operates on the "happy path" (Right side of
 * Either). Bifunctor extends this to <b>both sides</b>, giving you symmetric control over error
 * and success transformations.
 *
 * @see EitherBifunctor
 * @see WorkflowModels.EnrichedResult
 * @see ClientError
 */
public class WorkflowBifunctor {

  private final Dependencies dependencies;
  private final OrderWorkflowSteps steps;
  private final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad;
  private final MonadError<
          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad;
  private final Bifunctor<EitherKind2.Witness> eitherBifunctor = EitherBifunctor.INSTANCE;

  public WorkflowBifunctor(
      Dependencies dependencies,
      OrderWorkflowSteps steps,
      MonadError<CompletableFutureKind.Witness, Throwable> futureMonad,
      MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
          eitherTMonad) {
    this.dependencies = dependencies;
    this.steps = steps;
    this.futureMonad = futureMonad;
    this.eitherTMonad = eitherTMonad;
  }

  /**
   * Runs the order processing workflow and transforms the result for client consumption.
   *
   * <p>The workflow executes using internal {@code DomainError} types, then uses {@code bimap()}
   * to simultaneously:
   *
   * <ol>
   *   <li><b>Transform errors:</b> {@code DomainError -> ClientError} (simplified, user-friendly
   *       messages)
   *   <li><b>Enrich results:</b> {@code FinalResult -> EnrichedResult} (add timestamp, request ID,
   *       metadata)
   * </ol>
   *
   * @param orderData The initial order data
   * @return A {@code Kind} representing {@code CompletableFuture<Either<ClientError,
   *     EnrichedResult>>}
   */
  public Kind<CompletableFutureKind.Witness, Either<ClientError, EnrichedResult>> run(
      WorkflowModels.OrderData orderData) {

    dependencies.log(
        "Starting Bifunctor Workflow [WorkflowBifunctor] for Order: " + orderData.orderId());

    var initialContext = WorkflowModels.WorkflowContext.start(orderData);

    // Execute the standard workflow using EitherT
    var workflow =
        For.from(eitherTMonad, eitherTMonad.of(initialContext))
            .from(
                ctx1 -> {
                  var validatedOrderET =
                      EitherT.fromEither(
                          futureMonad, EITHER.narrow(steps.validateOrder(ctx1.initialData())));
                  return eitherTMonad.map(ctx1::withValidatedOrder, validatedOrderET);
                })
            .from(
                ctx2 -> {
                  var inventoryCheckET =
                      EitherT.fromKind(
                          steps.checkInventoryAsync(
                              ctx2.validatedOrder().productId(),
                              ctx2.validatedOrder().quantity()));
                  return eitherTMonad.map(ignored -> ctx2.withInventoryChecked(), inventoryCheckET);
                })
            .from(
                ctx3 -> {
                  var paymentConfirmET =
                      EitherT.fromKind(
                          steps.processPaymentAsync(
                              ctx3.validatedOrder().paymentDetails(),
                              ctx3.validatedOrder().amount()));
                  return eitherTMonad.map(ctx3::withPaymentConfirmation, paymentConfirmET);
                })
            .from(
                ctx4 -> {
                  var shipmentAttemptET =
                      EitherT.fromKind(
                          steps.createShipmentAsync(
                              ctx4.validatedOrder().orderId(),
                              ctx4.validatedOrder().shippingAddress()));
                  var recoveredShipmentET =
                      eitherTMonad.handleErrorWith(
                          shipmentAttemptET,
                          error -> {
                            if (error instanceof DomainError.ShippingError(var reason)
                                && "Temporary Glitch".equals(reason)) {
                              dependencies.log(
                                  "WARN: Recovering from temporary shipping glitch for order "
                                      + ctx4.validatedOrder().orderId());
                              return eitherTMonad.of(
                                  new WorkflowModels.ShipmentInfo("DEFAULT_SHIPPING_USED"));
                            }
                            return eitherTMonad.raiseError(error);
                          });
                  return eitherTMonad.map(ctx4::withShipmentInfo, recoveredShipmentET);
                })
            .yield(
                ctx5 -> {
                  var finalResult =
                      new WorkflowModels.FinalResult(
                          ctx5.validatedOrder().orderId(),
                          ctx5.paymentConfirmation().transactionId(),
                          ctx5.shipmentInfo().trackingId());

                  var notifyET =
                      EitherT.fromKind(
                          steps.notifyCustomerAsync(
                              ctx5.initialData().customerId(),
                              "Order processed: " + finalResult.orderId()));
                  var recoveredNotifyET =
                      eitherTMonad.handleError(
                          notifyET,
                          notifyError -> {
                            dependencies.log(
                                "WARN: Notification failed for order "
                                    + finalResult.orderId()
                                    + ": "
                                    + notifyError.message());
                            return Unit.INSTANCE;
                          });

                  return eitherTMonad.map(ignored -> finalResult, recoveredNotifyET);
                });

    var flattenedFinalResultET = eitherTMonad.flatMap(x -> x, workflow);
    var finalConcreteET = EITHER_T.narrow(flattenedFinalResultET);

    // HERE'S THE BIFUNCTOR MAGIC!
    // Extract the Either<DomainError, FinalResult> from the EitherT
    return futureMonad.map(
        eitherResult -> {
          Kind2<EitherKind2.Witness, DomainError, WorkflowModels.FinalResult> eitherKind2 =
              EITHER.widen2(eitherResult);

          // Use bimap() to transform BOTH channels simultaneously:
          // - Left (error) channel: DomainError -> ClientError
          // - Right (success) channel: FinalResult -> EnrichedResult
          Kind2<EitherKind2.Witness, ClientError, EnrichedResult> transformed =
              eitherBifunctor.bimap(
                  this::toClientError, // Transform internal errors to client-friendly messages
                  this::enrichResult, // Add metadata to success results
                  eitherKind2);

          return EITHER.narrow2(transformed);
        },
        finalConcreteET.value());
  }

  /**
   * Transforms internal {@link DomainError} to client-facing {@link ClientError}.
   *
   * <p>This demonstrates the "first" transformation in {@code bimap()}. We map detailed internal
   * errors to simplified, user-friendly messages appropriate for external APIs.
   *
   * <h3>Benefits:</h3>
   *
   * <ul>
   *   <li>Hides internal implementation details
   *   <li>Provides consistent error format for clients
   *   <li>Allows internationalization of error messages
   *   <li>Maintains error codes for programmatic handling
   * </ul>
   */
  private ClientError toClientError(DomainError domainError) {
    return switch (domainError) {
      case DomainError.ValidationError(var message) ->
          new ClientError("VALIDATION_ERROR", "Invalid order data: " + message);
      case DomainError.StockError(var productId) ->
          new ClientError(
              "OUT_OF_STOCK", "Product " + productId + " is currently unavailable. Please try another item.");
      case DomainError.PaymentError(var reason) ->
          new ClientError("PAYMENT_FAILED", "Payment could not be processed. Please check your payment details.");
      case DomainError.ShippingError(var reason) ->
          new ClientError("SHIPPING_UNAVAILABLE", "Shipping is currently unavailable for your address.");
      case DomainError.NotificationError(var reason) ->
          // This shouldn't normally reach the client as it's a non-critical error
          new ClientError("NOTIFICATION_FAILED", "Your order was processed but we couldn't send a confirmation.");
    };
  }

  /**
   * Enriches the internal {@link WorkflowModels.FinalResult} with metadata for client consumption.
   *
   * <p>This demonstrates the "second" transformation in {@code bimap()}. We add contextual
   * information useful for clients whilst preserving the core result data.
   *
   * <h3>Typical Enrichments:</h3>
   *
   * <ul>
   *   <li>Timestamps (when was the order processed?)
   *   <li>Request IDs (for tracking and support)
   *   <li>API version information
   *   <li>Processing metadata (which server, duration, etc.)
   * </ul>
   */
  private EnrichedResult enrichResult(WorkflowModels.FinalResult finalResult) {
    return new EnrichedResult(
        finalResult.orderId(),
        finalResult.transactionId(),
        finalResult.trackingId(),
        System.currentTimeMillis(), // Timestamp when result was prepared
        java.util.UUID.randomUUID().toString(), // Unique request ID for tracking
        "v1.0" // API version
        );
  }

  /**
   * Client-facing error representation.
   *
   * <p>Simplified error structure suitable for external APIs:
   *
   * <ul>
   *   <li><b>code:</b> Machine-readable error identifier
   *   <li><b>message:</b> Human-readable description
   * </ul>
   *
   * <p>This can be easily serialised to JSON for REST APIs or other client protocols.
   */
  public record ClientError(String code, String message) {}

  /**
   * Enriched result representation for client consumption.
   *
   * <p>Extends the core {@link WorkflowModels.FinalResult} with metadata:
   *
   * <ul>
   *   <li><b>processedAt:</b> Timestamp (epoch millis)
   *   <li><b>requestId:</b> Unique ID for this request
   *   <li><b>apiVersion:</b> API version that processed this request
   * </ul>
   */
  public record EnrichedResult(
      String orderId,
      String transactionId,
      String trackingId,
      long processedAt,
      String requestId,
      String apiVersion) {}

  /**
   * Demonstrates the Bifunctor workflow with test data.
   *
   * <p>Shows how {@code bimap()} elegantly transforms both error and success channels
   * simultaneously.
   */
  public static void main(String[] args) {
    var consoleLogger = System.out::println;
    var dependencies = new Dependencies(consoleLogger);
    var steps = new OrderWorkflowSteps(dependencies);
    var futureMonad =
        (MonadError<CompletableFutureKind.Witness, Throwable>)
            org.higherkindedj.hkt.future.CompletableFutureMonad.INSTANCE;
    var eitherTMonad =
        new org.higherkindedj.hkt.either_t.EitherTMonad<CompletableFutureKind.Witness, DomainError>(
            futureMonad);

    var workflow = new WorkflowBifunctor(dependencies, steps, futureMonad, eitherTMonad);

    var goodData =
        new WorkflowModels.OrderData(
            "Order-Bifunctor-001",
            "PROD-PREMIUM",
            1,
            "VALID_CARD",
            "123 Bifunctor Boulevard",
            "cust-premium",
            java.util.List.of());

    var badQtyData =
        new WorkflowModels.OrderData(
            "Order-Bifunctor-002",
            "PROD-PREMIUM",
            0, // Invalid quantity
            "VALID_CARD",
            "456 Error Lane",
            "cust-error",
            java.util.List.of());

    System.out.println("\n=== Bifunctor Workflow: Transforming Both Channels ===\n");

    // Success case - enriched result
    System.out.println("--- Running: Good Order (Success Channel Transformation) ---");
    var goodFuture =
        org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE.narrow(
            workflow.run(goodData));
    var goodResult = goodFuture.join();
    System.out.println("Client receives: " + goodResult);
    System.out.println();

    // Error case - client-friendly error
    System.out.println("--- Running: Bad Quantity (Error Channel Transformation) ---");
    var badFuture =
        org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE.narrow(
            workflow.run(badQtyData));
    var badResult = badFuture.join();
    System.out.println("Client receives: " + badResult);
    System.out.println();

    System.out.println(
        "Notice how bimap() transformed:\n"
            + "  - Success: FinalResult -> EnrichedResult (with timestamp, requestId, apiVersion)\n"
            + "  - Error: DomainError -> ClientError (simplified, user-friendly message)\n");
  }
}
