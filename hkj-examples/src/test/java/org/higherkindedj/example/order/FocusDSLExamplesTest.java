// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.DiscountResult;
import org.higherkindedj.example.order.model.OrderLineRequest;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.Product;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.model.ValidatedOrder;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.example.order.model.ValidatedShippingAddress.ShippingZone;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.model.value.Percentage;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.example.order.model.value.PromoCode;
import org.higherkindedj.example.order.workflow.FocusDSLExamples;
import org.higherkindedj.example.order.workflow.OrderWorkflowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for Focus DSL integration examples. */
@DisplayName("FocusDSLExamples")
class FocusDSLExamplesTest {

  @Nested
  @DisplayName("Immutable State Updates")
  class ImmutableStateUpdateTests {

    @Test
    @DisplayName("applyDiscount updates workflow state with discount")
    void applyDiscountUpdatesState() {
      // Arrange
      var order = createValidatedOrder();
      var request = createOrderRequest();
      var state = OrderWorkflowState.start(request).withValidatedOrder(order);

      var discount =
          DiscountResult.withPromoCode(
              new PromoCode("SAVE10", Percentage.of(10)), order.subtotal());

      // Act
      var updatedState = FocusDSLExamples.applyDiscount(state, discount);

      // Assert
      assertThat(updatedState.discountResult()).isPresent();
      assertThat(updatedState.discountResult().get().discountPercentage())
          .isEqualTo(Percentage.of(10));
    }

    @Test
    @DisplayName("updateOrderSubtotal creates new order with updated subtotal")
    void updateOrderSubtotalCreatesNewOrder() {
      // Arrange
      var order = createValidatedOrder();
      var newSubtotal = Money.gbp(new BigDecimal("500.00"));

      // Act
      var updatedOrder = FocusDSLExamples.updateOrderSubtotal(order, newSubtotal);

      // Assert
      assertThat(updatedOrder.subtotal()).isEqualTo(newSubtotal);
      // Original unchanged
      assertThat(order.subtotal()).isNotEqualTo(newSubtotal);
      // Other fields unchanged
      assertThat(updatedOrder.orderId()).isEqualTo(order.orderId());
      assertThat(updatedOrder.customer()).isEqualTo(order.customer());
    }
  }

  @Nested
  @DisplayName("Collection Operations")
  class CollectionOperationTests {

    @Test
    @DisplayName("applyDiscountToAllLines applies percentage to all lines")
    void applyDiscountToAllLinesUpdatesAllLines() {
      // Arrange
      var order = createValidatedOrder();
      var originalSubtotal = order.subtotal();

      // Act
      var discounted = FocusDSLExamples.applyDiscountToAllLines(order, Percentage.of(10));

      // Assert
      // 10% discount should reduce subtotal by 10%
      var expectedSubtotal = originalSubtotal.applyDiscount(Percentage.of(10));
      assertThat(discounted.subtotal().amount()).isEqualByComparingTo(expectedSubtotal.amount());
    }

    @Test
    @DisplayName("applyBulkDiscount only discounts lines over threshold")
    void applyBulkDiscountOnlyAffectsLargeQuantities() {
      // Arrange - order with quantity 2 and quantity 10
      var product1 =
          new Product(
              new ProductId("PROD-001"),
              "Product One",
              "Description",
              Money.gbp(new BigDecimal("10.00")),
              "General",
              true);
      var product2 =
          new Product(
              new ProductId("PROD-002"),
              "Product Two",
              "Description",
              Money.gbp(new BigDecimal("10.00")),
              "General",
              true);
      var lines =
          List.of(
              new ValidatedOrderLine(
                  new ProductId("PROD-001"),
                  product1,
                  2,
                  Money.gbp(new BigDecimal("10.00")),
                  Money.gbp(new BigDecimal("20.00"))),
              new ValidatedOrderLine(
                  new ProductId("PROD-002"),
                  product2,
                  10,
                  Money.gbp(new BigDecimal("10.00")),
                  Money.gbp(new BigDecimal("100.00"))));

      var order = createValidatedOrderWithLines(lines);
      var bulkThreshold = 5;
      var bulkDiscount = Percentage.of(20);

      // Act
      var discounted = FocusDSLExamples.applyBulkDiscount(order, bulkThreshold, bulkDiscount);

      // Assert
      // First line (qty 2) should be unchanged
      assertThat(discounted.lines().get(0).unitPrice().amount())
          .isEqualByComparingTo(new BigDecimal("10.00"));
      // Second line (qty 10) should be 20% off
      assertThat(discounted.lines().get(1).unitPrice().amount())
          .isEqualByComparingTo(new BigDecimal("8.00"));
    }

    @Test
    @DisplayName("getAllLineTotals extracts totals from all lines")
    void getAllLineTotalsExtractsValues() {
      // Arrange
      var order = createValidatedOrder();

      // Act
      var totals = FocusDSLExamples.getAllLineTotals(order);

      // Assert
      assertThat(totals).hasSize(order.lines().size());
      assertThat(totals).allSatisfy(total -> assertThat(total.amount()).isPositive());
    }
  }

  @Nested
  @DisplayName("Pattern Matching")
  class PatternMatchingTests {

    @Test
    @DisplayName("extractMaskedCardNumber masks credit card correctly")
    void extractMaskedCardNumberMasksCreditCard() {
      // Arrange
      var creditCard = new PaymentMethod.CreditCard("4000000000001234", "12", "2025", "123");

      // Act
      var masked = FocusDSLExamples.extractMaskedCardNumber(creditCard);

      // Assert
      assertThat(masked).isPresent();
      assertThat(masked.get()).endsWith("1234");
      assertThat(masked.get()).startsWith("****");
    }

    @Test
    @DisplayName("extractMaskedCardNumber returns empty for non-card methods")
    void extractMaskedCardNumberEmptyForNonCard() {
      // Arrange
      var bankTransfer = new PaymentMethod.BankTransfer("12345678", "12-34-56");

      // Act
      var masked = FocusDSLExamples.extractMaskedCardNumber(bankTransfer);

      // Assert
      assertThat(masked).isEmpty();
    }

    @Test
    @DisplayName("validatePaymentMethod validates credit card length")
    void validatePaymentMethodChecksCardLength() {
      // Arrange
      var shortCard = new PaymentMethod.CreditCard("12345", "12", "2025", "123");
      var validCard = new PaymentMethod.CreditCard("4000000000001234", "12", "2025", "123");

      // Act
      var shortResult = FocusDSLExamples.validatePaymentMethod(shortCard);
      var validResult = FocusDSLExamples.validatePaymentMethod(validCard);

      // Assert
      assertThat(shortResult.run().isLeft()).isTrue();
      assertThat(validResult.run().isRight()).isTrue();
    }

    @Test
    @DisplayName("getUserFriendlyMessage returns appropriate message for each error type")
    void getUserFriendlyMessageHandlesAllTypes() {
      // Arrange
      var validationError = OrderError.ValidationError.forField("email", "Invalid format");
      var paymentError = OrderError.PaymentError.declined("Card expired");
      var systemError = OrderError.SystemError.timeout("payment", Duration.ofSeconds(30));

      // Act & Assert
      assertThat(FocusDSLExamples.getUserFriendlyMessage(validationError))
          .startsWith("Please check your order details");
      assertThat(FocusDSLExamples.getUserFriendlyMessage(paymentError)).startsWith("Payment issue");
      assertThat(FocusDSLExamples.getUserFriendlyMessage(systemError)).startsWith("System error");
    }

    @Test
    @DisplayName("isRecoverable returns true for timeout errors")
    void isRecoverableTrueForTimeout() {
      // Arrange
      var timeoutError = OrderError.SystemError.timeout("payment");
      var validationError = OrderError.ValidationError.forField("email", "Invalid");

      // Act & Assert
      assertThat(FocusDSLExamples.isRecoverable(timeoutError)).isTrue();
      assertThat(FocusDSLExamples.isRecoverable(validationError)).isFalse();
    }
  }

  private OrderRequest createOrderRequest() {
    return new OrderRequest(
        "CUST-001",
        List.of(new OrderLineRequest("PROD-001", 2)),
        Optional.empty(),
        new ShippingAddress("Test User", "123 Test St", "London", "SW1A 1AA", "GB"),
        new PaymentMethod.CreditCard("4000000000001234", "12", "2025", "123"));
  }

  private ValidatedOrder createValidatedOrder() {
    var product =
        new Product(
            new ProductId("PROD-001"),
            "Test Product",
            "Description",
            Money.gbp(new BigDecimal("50.00")),
            "General",
            true);
    return createValidatedOrderWithLines(
        List.of(
            new ValidatedOrderLine(
                new ProductId("PROD-001"),
                product,
                2,
                Money.gbp(new BigDecimal("50.00")),
                Money.gbp(new BigDecimal("100.00")))));
  }

  private ValidatedOrder createValidatedOrderWithLines(List<ValidatedOrderLine> lines) {
    var customer =
        new Customer(
            new CustomerId("CUST-001"),
            "Test Customer",
            "test@example.com",
            "555-1234",
            Customer.CustomerStatus.ACTIVE,
            Customer.LoyaltyTier.BRONZE);

    var address =
        ValidatedShippingAddress.from(
            new ShippingAddress("Test User", "123 Test St", "London", "SW1A 1AA", "GB"),
            ShippingZone.DOMESTIC);

    var subtotal =
        lines.stream().map(ValidatedOrderLine::lineTotal).reduce(Money.ZERO_GBP, Money::add);

    return new ValidatedOrder(
        OrderId.generate(),
        customer.id(),
        customer,
        lines,
        Optional.empty(),
        address,
        new PaymentMethod.CreditCard("4000000000001234", "12", "2025", "123"),
        subtotal,
        Instant.now());
  }
}
