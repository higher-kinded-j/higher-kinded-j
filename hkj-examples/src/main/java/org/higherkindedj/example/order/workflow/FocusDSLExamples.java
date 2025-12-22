// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.DiscountResult;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.PaymentConfirmation;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.Product;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.ValidatedOrder;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.Percentage;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Examples demonstrating Focus DSL integration with the order workflow.
 *
 * <p>Shows how to use optics (Lenses, Prisms) for:
 *
 * <ul>
 *   <li>Immutable state updates
 *   <li>Safe navigation of nested structures
 *   <li>Integration with EitherPath for effect composition
 *   <li>Pattern matching on sealed types using Prisms
 * </ul>
 *
 * <p>All domain records have @GenerateLenses and @GenerateFocus annotations, so the generated
 * optics can be used for composable data access and modification.
 */
public final class FocusDSLExamples {

  private FocusDSLExamples() {}

  // =========================================================================
  // Immutable State Updates
  // =========================================================================

  /**
   * Updates the workflow state when a discount is applied. Demonstrates using generated lenses for
   * immutable updates.
   *
   * @param state current workflow state
   * @param discount the discount result
   * @return updated workflow state with discount applied
   */
  public static OrderWorkflowState applyDiscount(
      OrderWorkflowState state, DiscountResult discount) {
    // Update discountResult field
    var withDiscount = state.withDiscountResult(discount);

    // Update validated order's subtotal if present
    return state
        .validatedOrder()
        .map(
            order -> {
              var updatedOrder = updateOrderSubtotal(order, discount.finalTotal());
              return withDiscount.withValidatedOrder(updatedOrder);
            })
        .orElse(withDiscount);
  }

  /**
   * Updates the subtotal in a validated order.
   *
   * @param order the order to update
   * @param newSubtotal the new subtotal
   * @return updated order with new subtotal
   */
  public static ValidatedOrder updateOrderSubtotal(ValidatedOrder order, Money newSubtotal) {
    return new ValidatedOrder(
        order.orderId(),
        order.customerId(),
        order.customer(),
        order.lines(),
        order.promoCode(),
        order.shippingAddress(),
        order.paymentMethod(),
        newSubtotal,
        order.createdAt());
  }

  /**
   * Updates workflow state after successful payment.
   *
   * @param state current workflow state
   * @param payment payment confirmation
   * @return updated workflow state
   */
  public static OrderWorkflowState recordPayment(
      OrderWorkflowState state, PaymentConfirmation payment) {
    return state.withPaymentConfirmation(payment);
  }

  /**
   * Updates workflow state after reservation.
   *
   * @param state current workflow state
   * @param reservation inventory reservation
   * @return updated workflow state
   */
  public static OrderWorkflowState recordReservation(
      OrderWorkflowState state, InventoryReservation reservation) {
    return state.withInventoryReservation(reservation);
  }

  /**
   * Updates workflow state after shipment creation.
   *
   * @param state current workflow state
   * @param shipment shipment info
   * @return updated workflow state
   */
  public static OrderWorkflowState recordShipment(OrderWorkflowState state, ShipmentInfo shipment) {
    return state.withShipmentInfo(shipment);
  }

  // =========================================================================
  // Collection Operations with Focus
  // =========================================================================

  /**
   * Applies a discount to all order lines. Demonstrates traversing collections in immutable data.
   *
   * @param order the order with lines
   * @param percentage the discount percentage
   * @return order with discounted line totals
   */
  public static ValidatedOrder applyDiscountToAllLines(
      ValidatedOrder order, Percentage percentage) {
    var discountedLines =
        order.lines().stream().map(line -> discountLine(line, percentage)).toList();

    return new ValidatedOrder(
        order.orderId(),
        order.customerId(),
        order.customer(),
        discountedLines,
        order.promoCode(),
        order.shippingAddress(),
        order.paymentMethod(),
        calculateSubtotal(discountedLines),
        order.createdAt());
  }

  /**
   * Applies a bulk discount to lines with quantity over a threshold.
   *
   * @param order the order
   * @param bulkThreshold minimum quantity for bulk discount
   * @param bulkDiscount the discount to apply
   * @return order with bulk discounts applied
   */
  public static ValidatedOrder applyBulkDiscount(
      ValidatedOrder order, int bulkThreshold, Percentage bulkDiscount) {
    var updatedLines =
        order.lines().stream()
            .map(line -> line.quantity() >= bulkThreshold ? discountLine(line, bulkDiscount) : line)
            .toList();

    return new ValidatedOrder(
        order.orderId(),
        order.customerId(),
        order.customer(),
        updatedLines,
        order.promoCode(),
        order.shippingAddress(),
        order.paymentMethod(),
        calculateSubtotal(updatedLines),
        order.createdAt());
  }

  private static ValidatedOrderLine discountLine(ValidatedOrderLine line, Percentage discount) {
    var discountedPrice = line.unitPrice().applyDiscount(discount);
    // Create a product with the discounted price for the updated line
    var updatedProduct =
        new Product(
            line.product().id(),
            line.product().name(),
            line.product().description(),
            discountedPrice,
            line.product().category(),
            line.product().inStock());
    return new ValidatedOrderLine(
        line.productId(),
        updatedProduct,
        line.quantity(),
        discountedPrice,
        discountedPrice.multiply(line.quantity()));
  }

  private static Money calculateSubtotal(List<ValidatedOrderLine> lines) {
    return lines.stream().map(ValidatedOrderLine::lineTotal).reduce(Money.ZERO_GBP, Money::add);
  }

  /**
   * Extracts all line totals from an order.
   *
   * @param order the order
   * @return list of line totals
   */
  public static List<Money> getAllLineTotals(ValidatedOrder order) {
    return order.lines().stream().map(ValidatedOrderLine::lineTotal).collect(Collectors.toList());
  }

  // =========================================================================
  // Pattern Matching with Prisms (PaymentMethod)
  // =========================================================================

  /**
   * Extracts credit card number if payment method is a credit card. Demonstrates safe pattern
   * matching using prism-like access.
   *
   * @param method the payment method
   * @return the masked card number if credit card, empty otherwise
   */
  public static Optional<String> extractMaskedCardNumber(PaymentMethod method) {
    return switch (method) {
      case PaymentMethod.CreditCard card -> Optional.of(maskCardNumber(card.cardNumber()));
      case PaymentMethod.DebitCard card -> Optional.of(maskCardNumber(card.cardNumber()));
      default -> Optional.empty();
    };
  }

  private static String maskCardNumber(String cardNumber) {
    if (cardNumber.length() < 4) {
      return "****";
    }
    return "*".repeat(cardNumber.length() - 4) + cardNumber.substring(cardNumber.length() - 4);
  }

  /**
   * Validates payment method and converts to EitherPath. Shows integration of pattern matching with
   * Effect API.
   *
   * @param method the payment method to validate
   * @return either validation error or the validated method
   */
  public static EitherPath<OrderError, PaymentMethod> validatePaymentMethod(PaymentMethod method) {
    return switch (method) {
      case PaymentMethod.CreditCard card -> {
        if (card.cardNumber().length() < 13) {
          yield Path.left(
              OrderError.ValidationError.forField("cardNumber", "Card number too short"));
        }
        yield Path.right(method);
      }
      case PaymentMethod.DebitCard card -> {
        if (card.cardNumber().length() < 13) {
          yield Path.left(
              OrderError.ValidationError.forField("cardNumber", "Card number too short"));
        }
        yield Path.right(method);
      }
      case PaymentMethod.BankTransfer transfer -> {
        if (transfer.accountNumber().isBlank()) {
          yield Path.left(
              OrderError.ValidationError.forField("accountNumber", "Account number required"));
        }
        yield Path.right(method);
      }
      case PaymentMethod.DigitalWallet wallet -> {
        if (wallet.walletId().isBlank()) {
          yield Path.left(OrderError.ValidationError.forField("walletId", "Wallet ID required"));
        }
        yield Path.right(method);
      }
    };
  }

  // =========================================================================
  // Pattern Matching with OrderError Prisms
  // =========================================================================

  /**
   * Categorises an error and returns appropriate user message. Demonstrates exhaustive pattern
   * matching on sealed error types.
   *
   * @param error the order error
   * @return user-friendly error message
   */
  public static String getUserFriendlyMessage(OrderError error) {
    return switch (error) {
      case OrderError.ValidationError e -> "Please check your order details: " + e.message();
      case OrderError.CustomerError e -> "Account issue: " + e.message();
      case OrderError.InventoryError e -> "Stock availability issue: " + e.message();
      case OrderError.DiscountError e -> "Discount code issue: " + e.message();
      case OrderError.PaymentError e -> "Payment issue: " + e.message();
      case OrderError.ShippingError e -> "Shipping issue: " + e.message();
      case OrderError.NotificationError e ->
          "Notification issue (order still processed): " + e.message();
      case OrderError.SystemError e -> "System error - please try again: " + e.message();
    };
  }

  /**
   * Checks if an error is recoverable (can retry).
   *
   * @param error the order error
   * @return true if the operation can be retried
   */
  public static boolean isRecoverable(OrderError error) {
    return switch (error) {
      case OrderError.ShippingError e -> e.recoverable();
      case OrderError.SystemError e ->
          e.code().equals("TIMEOUT") || e.code().equals("CIRCUIT_BREAKER_OPEN");
      case OrderError.NotificationError _ -> true; // Notifications can always retry
      default -> false;
    };
  }
}
