// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.util.List;
import java.util.Objects;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Contains the data model records used throughout the order processing workflow example. These
 * models are designed as simple, immutable data carriers, primarily using Java's {@link Record}
 * feature for conciseness and automatic implementation of data-related methods like {@code
 * equals()}, {@code hashCode()}, and {@code toString()}.
 *
 * <p>This class serves as a container for these related record definitions to keep them organised
 * within the example.
 */
public final class WorkflowModels {

  // Private constructor to prevent instantiation of this utility class.
  private WorkflowModels() {
    throw new IllegalStateException("Utility class for model records, not to be instantiated.");
  }

  /**
   * Represents the initial, raw data received for an order request. This record captures all
   * necessary information to begin processing an order. All fields are expected to be non-null at
   * the point of creation.
   *
   * @param orderId The unique identifier for the order.
   * @param productId The identifier of the product being ordered.
   * @param quantity The number of units of the product being ordered.
   * @param paymentDetails String containing details for processing payment (e.g., card token,
   *     type).
   * @param shippingAddress The address to which the order should be shipped.
   * @param customerId The unique identifier for the customer placing the order.
   */
  public record OrderData(
      @NonNull String orderId,
      @NonNull String productId,
      int quantity,
      @NonNull String paymentDetails,
      @NonNull String shippingAddress,
      @NonNull String customerId,
      @NonNull List<String> promoCodes) {}

  /**
   * Represents an order after it has undergone initial validation. This record typically includes
   * the original order data plus any derived information, such as the calculated total amount. All
   * fields are expected to be non-null.
   *
   * @param orderId The unique identifier for the order.
   * @param productId The identifier of the product being ordered.
   * @param quantity The number of units of the product.
   * @param paymentDetails Payment details for the order.
   * @param amount The calculated total amount for the order.
   * @param shippingAddress The shipping address for the order.
   * @param customerId The customer's unique identifier.
   */
  public record ValidatedOrder(
      @NonNull String orderId,
      @NonNull String productId,
      int quantity,
      @NonNull String paymentDetails,
      double amount,
      @NonNull String shippingAddress,
      @NonNull String customerId,
      @NonNull List<String> promoCodes) {}

  /**
   * Represents the confirmation received after a successful payment processing attempt.
   *
   * @param transactionId The unique identifier for the payment transaction. Must be non-null.
   */
  public record PaymentConfirmation(@NonNull String transactionId) {}

  /**
   * Contains information about the shipment after it has been successfully created or processed.
   *
   * @param trackingId The tracking identifier for the shipment. Must be non-null.
   */
  public record ShipmentInfo(@NonNull String trackingId) {}

  /**
   * Represents the final, successfully processed state of an order, containing key identifiers from
   * various stages of the workflow. This record is typically the ultimate success result of the
   * entire order processing pipeline.
   *
   * @param orderId The original order identifier.
   * @param transactionId The payment transaction identifier.
   * @param trackingId The shipment tracking identifier.
   */
  public record FinalResult(
      @NonNull String orderId, @NonNull String transactionId, @NonNull String trackingId) {}

  /**
   * An immutable record that accumulates the state of an order as it progresses through the various
   * stages of the {@code OrderWorkflowRunner}.
   *
   * <p>It starts with initial {@link OrderData} and is progressively updated with results from each
   * workflow step (e.g., {@link ValidatedOrder}, {@link PaymentConfirmation}). Fields representing
   * later stages are {@link Nullable} in the record's components to reflect that they might not be
   * populated yet.
   *
   * <p>The "withX" methods (e.g., {@link #withValidatedOrder(ValidatedOrder)}) are used to create
   * new, updated instances of the context, preserving immutability.
   *
   * <p>Accessors for potentially nullable fields (like {@link #validatedOrder()}) are annotated
   * with {@link NonNull}. This reflects a design contract: the workflow logic in {@code
   * OrderWorkflowRunner} must ensure these accessors are only called *after* the corresponding data
   * has been successfully populated by a preceding workflow step. This relies on the controlled,
   * sequential nature of the workflow. Assertions are included in these accessors as a development
   * aid to catch out-of-sequence calls during testing or debugging, but they are not intended as
   * runtime validation for production flows.
   *
   * @param initialData The initial {@link OrderData} that starts the workflow. Always non-null.
   * @param validatedOrder The {@link ValidatedOrder} after successful validation. Null until
   *     populated.
   * @param inventoryChecked A boolean flag indicating if inventory check was successful.
   * @param paymentConfirmation The {@link PaymentConfirmation} after successful payment. Null until
   *     populated.
   * @param shipmentInfo The {@link ShipmentInfo} after successful shipment creation. Null until
   *     populated.
   */
  @GenerateLenses
  public record WorkflowContext(
      @NonNull OrderData initialData,
      @Nullable ValidatedOrder validatedOrder,
      boolean inventoryChecked,
      @Nullable PaymentConfirmation paymentConfirmation,
      @Nullable ShipmentInfo shipmentInfo) {

    /**
     * Static factory method to initialise the {@code WorkflowContext} with the starting order data.
     * All subsequent state fields are initialised to their default (e.g., null, false) values.
     *
     * @param data The non-null initial {@link OrderData} for the workflow.
     * @return A new, non-null {@link WorkflowContext} instance representing the beginning of the
     *     workflow.
     * @throws NullPointerException if {@code data} is null.
     */
    public static @NonNull WorkflowContext start(@NonNull OrderData data) {
      Objects.requireNonNull(
          data, "Initial OrderData cannot be null when starting WorkflowContext");
      return new WorkflowContext(data, null, false, null, null);
    }

    /**
     * Creates a new {@code WorkflowContext} instance by updating it with the provided {@link
     * ValidatedOrder}. This method enforces immutability by returning a new record.
     *
     * @param vo The non-null {@link ValidatedOrder} result from the validation step.
     * @return A new, non-null {@link WorkflowContext} with the validated order information
     *     populated.
     * @throws NullPointerException if {@code vo} is null.
     */
    public @NonNull WorkflowContext withValidatedOrder(@NonNull ValidatedOrder vo) {
      Objects.requireNonNull(vo, "ValidatedOrder cannot be null for withValidatedOrder");
      return new WorkflowContext(
          this.initialData, vo, this.inventoryChecked, this.paymentConfirmation, this.shipmentInfo);
    }

    /**
     * Creates a new {@code WorkflowContext} instance, marking the inventory as checked. This method
     * is typically called after a successful inventory verification step.
     *
     * @return A new, non-null {@link WorkflowContext} with {@code inventoryChecked} set to true.
     */
    public @NonNull WorkflowContext withInventoryChecked() {
      return new WorkflowContext(
          this.initialData, this.validatedOrder, true, this.paymentConfirmation, this.shipmentInfo);
    }

    /**
     * Creates a new {@code WorkflowContext} instance by updating it with the provided {@link
     * PaymentConfirmation}. This method enforces immutability.
     *
     * @param pc The non-null {@link PaymentConfirmation} result from the payment step.
     * @return A new, non-null {@link WorkflowContext} with the payment confirmation populated.
     * @throws NullPointerException if {@code pc} is null.
     */
    public @NonNull WorkflowContext withPaymentConfirmation(@NonNull PaymentConfirmation pc) {
      Objects.requireNonNull(pc, "PaymentConfirmation cannot be null for withPaymentConfirmation");
      return new WorkflowContext(
          this.initialData, this.validatedOrder, this.inventoryChecked, pc, this.shipmentInfo);
    }

    /**
     * Creates a new {@code WorkflowContext} instance by updating it with the provided {@link
     * ShipmentInfo}. This method enforces immutability.
     *
     * @param si The non-null {@link ShipmentInfo} result from the shipment creation step.
     * @return A new, non-null {@link WorkflowContext} with the shipment information populated.
     * @throws NullPointerException if {@code si} is null.
     */
    public @NonNull WorkflowContext withShipmentInfo(@NonNull ShipmentInfo si) {
      Objects.requireNonNull(si, "ShipmentInfo cannot be null for withShipmentInfo");
      return new WorkflowContext(
          this.initialData,
          this.validatedOrder,
          this.inventoryChecked,
          this.paymentConfirmation,
          si);
    }

    // --- Accessors ---
    // These accessors return @NonNull types. This is a contract based on the workflow's
    // sequential execution ensuring that by the time an accessor is called, the corresponding
    // data has been populated by a preceding successful step. Assertions are included as
    // development-time checks rather than production runtime guards.

    /**
     * Returns the {@link ValidatedOrder} from the context.
     *
     * <p><b>Contract:</b> This method should only be called after the order validation step in the
     * workflow has successfully completed and populated this field. The {@code OrderWorkflowRunner}
     * is responsible for ensuring this sequence.
     *
     * @return The non-null {@link ValidatedOrder}.
     * @throws AssertionError if called prematurely during development/testing (when assertions are
     *     enabled).
     */
    public @NonNull ValidatedOrder validatedOrder() {
      assert validatedOrder != null
          : "validatedOrder() called before validation completed successfully. Workflow sequencing"
              + " error.";
      // If validatedOrder could truly be null at this point despite workflow control,
      // a more robust approach would be:
      // return Objects.requireNonNull(validatedOrder, "ValidatedOrder not populated when
      // accessed.");
      // However, the current design relies on workflow control for non-nullness here.
      return validatedOrder;
    }

    /**
     * Returns the {@link PaymentConfirmation} from the context.
     *
     * <p><b>Contract:</b> This method should only be called after the payment processing step in
     * the workflow has successfully completed and populated this field.
     *
     * @return The non-null {@link PaymentConfirmation}.
     * @throws AssertionError if called prematurely during development/testing.
     */
    public @NonNull PaymentConfirmation paymentConfirmation() {
      assert paymentConfirmation != null
          : "paymentConfirmation() called before payment completed successfully. Workflow"
              + " sequencing error.";
      return paymentConfirmation;
    }

    /**
     * Returns the {@link ShipmentInfo} from the context.
     *
     * <p><b>Contract:</b> This method should only be called after the shipment creation step in the
     * workflow has successfully completed (or recovered successfully) and populated this field.
     *
     * @return The non-null {@link ShipmentInfo}.
     * @throws AssertionError if called prematurely during development/testing.
     */
    public @NonNull ShipmentInfo shipmentInfo() {
      assert shipmentInfo != null
          : "shipmentInfo() called before shipment completed successfully. Workflow sequencing"
              + " error.";
      return shipmentInfo;
    }

    /**
     * Returns the initial {@link OrderData} that started the workflow. This field is guaranteed to
     * be non-null from the point the context is created.
     *
     * @return The non-null initial {@link OrderData}.
     */
    public @NonNull OrderData initialData() {
      // initialData is @NonNull from the constructor and start() method.
      return initialData;
    }
  }
}
