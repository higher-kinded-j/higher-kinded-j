// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.DiscountResult;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.NotificationResult;
import org.higherkindedj.example.order.model.PaymentConfirmation;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.ValidatedOrder;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Immutable state record that accumulates {@link OrderWorkflow} results with named fields.
 *
 * <p>Promoted to a top-level record and annotated with {@link GenerateLenses} so the annotation
 * processor generates {@code ProcessingStateLenses} — one lens per field — instead of the workflow
 * hand-writing them. Unlike tuple-based accumulation where values are accessed by position ({@code
 * t._3()}, {@code t._5()}), this record provides named accessors ({@code state.order()}, {@code
 * state.discount()}) that are self-documenting and refactoring-safe.
 *
 * <p>Fields populated during the gather phase (address, customer, order) are always non-null.
 * Fields populated during the enrich phase start as null and are set by {@code fromThen()} steps.
 * The monadic short-circuit guarantees each field is populated before subsequent steps access it.
 *
 * @param address the validated shipping address (gather phase)
 * @param customer the validated customer (gather phase)
 * @param order the validated order (gather phase)
 * @param reservation the inventory reservation (enrich phase)
 * @param discount the discount calculation result (enrich phase)
 * @param payment the payment confirmation (enrich phase)
 * @param shipment the shipment info (enrich phase)
 * @param notification the notification result (enrich phase)
 */
@GenerateLenses
public record ProcessingState(
    ValidatedShippingAddress address,
    Customer customer,
    ValidatedOrder order,
    InventoryReservation reservation,
    DiscountResult discount,
    PaymentConfirmation payment,
    ShipmentInfo shipment,
    NotificationResult notification) {

  /**
   * Creates the initial state from the three values gathered by the For comprehension. Remaining
   * fields are null until populated by ForState steps.
   *
   * @param address the validated shipping address
   * @param customer the validated customer
   * @param order the validated order
   * @return a ProcessingState with only the gather-phase fields populated
   */
  static ProcessingState initial(
      ValidatedShippingAddress address, Customer customer, ValidatedOrder order) {
    return new ProcessingState(address, customer, order, null, null, null, null, null);
  }
}
