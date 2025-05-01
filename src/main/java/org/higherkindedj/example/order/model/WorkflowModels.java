package org.higherkindedj.example.order.model;

import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class WorkflowModels {
  // Simple data carriers for the example
  public record OrderData(
      @NonNull String orderId,
      @NonNull String productId,
      int quantity,
      @NonNull String paymentDetails,
      @NonNull String shippingAddress,
      @NonNull String customerId) {}

  public record ValidatedOrder(
      @NonNull String orderId,
      @NonNull String productId,
      int quantity,
      @NonNull String paymentDetails,
      double amount,
      @NonNull String shippingAddress,
      @NonNull String customerId) {}

  public record PaymentConfirmation(@NonNull String transactionId) {}

  public record ShipmentInfo(@NonNull String trackingId) {}

  public record FinalResult(
      @NonNull String orderId, @NonNull String transactionId, @NonNull String trackingId) {}

  /**
   * Holds the state as the workflow progresses. Fields are @Nullable initially and populated by
   * workflow steps. Accessors now return @NonNull, relying on the workflow's flow control to ensure
   * they are only called after the corresponding data is available.
   */
  public record WorkflowContext(
      @NonNull OrderData initialData,
      @Nullable ValidatedOrder validatedOrder,
      boolean inventoryChecked,
      @Nullable PaymentConfirmation paymentConfirmation,
      @Nullable ShipmentInfo shipmentInfo) {
    // Static factory for initial state
    public static @NonNull WorkflowContext start(@NonNull OrderData data) {
      // Assuming OrderData itself should not be null
      Objects.requireNonNull(data, "Initial OrderData cannot be null");
      return new WorkflowContext(data, null, false, null, null);
    }

    // "Lens"-like methods to create updated context copies
    public @NonNull WorkflowContext withValidatedOrder(@NonNull ValidatedOrder vo) {
      // Assuming the validated order passed in is non-null
      java.util.Objects.requireNonNull(vo, "ValidatedOrder cannot be null for withValidatedOrder");
      return new WorkflowContext(
          this.initialData, vo, this.inventoryChecked, this.paymentConfirmation, this.shipmentInfo);
    }

    public @NonNull WorkflowContext withInventoryChecked() {
      return new WorkflowContext(
          this.initialData, this.validatedOrder, true, this.paymentConfirmation, this.shipmentInfo);
    }

    public @NonNull WorkflowContext withPaymentConfirmation(@NonNull PaymentConfirmation pc) {
      // Assuming the payment confirmation passed in is non-null
      Objects.requireNonNull(pc, "PaymentConfirmation cannot be null for withPaymentConfirmation");
      return new WorkflowContext(
          this.initialData, this.validatedOrder, this.inventoryChecked, pc, this.shipmentInfo);
    }

    public @NonNull WorkflowContext withShipmentInfo(@NonNull ShipmentInfo si) {
      // Assuming the shipment info passed in is non-null
      java.util.Objects.requireNonNull(si, "ShipmentInfo cannot be null for withShipmentInfo");
      return new WorkflowContext(
          this.initialData,
          this.validatedOrder,
          this.inventoryChecked,
          this.paymentConfirmation,
          si);
    }

    // --- Accessors ---

    // Relies on the caller (workflow runner) ensuring the field is populated
    // before calling the accessor.

    /**
     * Returns the validated order details. Assumes this is only called after the validation step
     * has successfully completed.
     *
     * @return The non-null ValidatedOrder.
     */
    public @NonNull ValidatedOrder validatedOrder() {
      // No runtime check - relies on flow control in the runner
      // Add assertion for documentation/debugging if desired, but not a runtime guard
      assert validatedOrder != null
          : "validatedOrder() called before validation completed successfully";
      return validatedOrder;
    }

    /**
     * Returns the payment confirmation details. Assumes this is only called after the payment step
     * has successfully completed.
     *
     * @return The non-null PaymentConfirmation.
     */
    public @NonNull PaymentConfirmation paymentConfirmation() {
      // No runtime check - relies on flow control in the runner
      assert paymentConfirmation != null
          : "paymentConfirmation() called before payment completed successfully";
      return paymentConfirmation;
    }

    /**
     * Returns the shipment information. Assumes this is only called after the shipment step has
     * successfully completed.
     *
     * @return The non-null ShipmentInfo.
     */
    public @NonNull ShipmentInfo shipmentInfo() {
      // No runtime check - relies on flow control in the runner
      assert shipmentInfo != null : "shipmentInfo() called before shipment completed successfully";
      return shipmentInfo;
    }

    /**
     * Returns the initial order data. Assumed to be always present.
     *
     * @return The non-null initial OrderData.
     */
    public @NonNull OrderData initialData() {
      return initialData;
    }
  }
}
