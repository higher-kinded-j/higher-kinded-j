// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.OrderLineRequest;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.service.impl.InMemoryCustomerService;
import org.higherkindedj.example.order.service.impl.InMemoryDiscountService;
import org.higherkindedj.example.order.service.impl.InMemoryInventoryService;
import org.higherkindedj.example.order.service.impl.InMemoryNotificationService;
import org.higherkindedj.example.order.service.impl.InMemoryPaymentService;
import org.higherkindedj.example.order.service.impl.InMemoryShippingService;
import org.higherkindedj.example.order.workflow.OrderWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for the Order Workflow V2 demonstrating ForPath-based composition. */
@DisplayName("OrderWorkflow")
class OrderWorkflowTest {

  private InMemoryCustomerService customerService;
  private InMemoryInventoryService inventoryService;
  private InMemoryDiscountService discountService;
  private InMemoryPaymentService paymentService;
  private InMemoryShippingService shippingService;
  private InMemoryNotificationService notificationService;
  private OrderWorkflow workflow;

  @BeforeEach
  void setUp() {
    customerService = new InMemoryCustomerService();
    inventoryService = new InMemoryInventoryService();
    discountService = new InMemoryDiscountService();
    paymentService = new InMemoryPaymentService();
    shippingService = new InMemoryShippingService();
    notificationService = new InMemoryNotificationService();

    workflow =
        new OrderWorkflow(
            customerService,
            inventoryService,
            discountService,
            paymentService,
            shippingService,
            notificationService);
  }

  @Nested
  @DisplayName("Successful Order Processing")
  class SuccessfulOrderTests {

    @Test
    @DisplayName("processes order with valid inputs")
    void processesOrderWithValidInputs() {
      // Arrange
      var customer =
          new Customer(
              new CustomerId("CUST-001"),
              "John Doe",
              "john@example.com",
              "555-1234",
              Customer.CustomerStatus.ACTIVE,
              Customer.LoyaltyTier.GOLD);
      customerService.addCustomer(customer);

      var request =
          new OrderRequest(
              "CUST-001",
              List.of(new OrderLineRequest("PROD-001", 2), new OrderLineRequest("PROD-002", 1)),
              Optional.empty(),
              createValidAddress(),
              new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123"));

      // Act
      var result = workflow.process(request);

      // Assert
      assertThat(result.run().isRight()).isTrue();

      var orderResult = result.run().getRight();
      assertThat(orderResult.orderId()).isNotNull();
      assertThat(orderResult.trackingNumber()).isNotEmpty();
    }

    @Test
    @DisplayName("applies loyalty discount for Gold customer")
    void appliesLoyaltyDiscountForGoldCustomer() {
      // Arrange
      var customer =
          new Customer(
              new CustomerId("CUST-GOLD"),
              "Gold Member",
              "gold@example.com",
              "555-5678",
              Customer.CustomerStatus.ACTIVE,
              Customer.LoyaltyTier.GOLD);
      customerService.addCustomer(customer);

      var request =
          new OrderRequest(
              "CUST-GOLD",
              List.of(new OrderLineRequest("PROD-001", 1)),
              Optional.empty(),
              createValidAddress(),
              new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123"));

      // Act
      var result = workflow.process(request);

      // Assert
      assertThat(result.run().isRight()).isTrue();
      var orderResult = result.run().getRight();
      assertThat(orderResult.totalCharged()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Validation Errors")
  class ValidationErrorTests {

    @Test
    @DisplayName("fails when customer not found")
    void failsWhenCustomerNotFound() {
      // Arrange
      var request =
          new OrderRequest(
              "UNKNOWN-CUSTOMER",
              List.of(new OrderLineRequest("PROD-001", 1)),
              Optional.empty(),
              createValidAddress(),
              new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123"));

      // Act
      var result = workflow.process(request);

      // Assert
      assertThat(result.run().isLeft()).isTrue();
      var error = result.run().getLeft();
      assertThat(error).isInstanceOf(OrderError.CustomerError.class);
      assertThat(error.code()).isEqualTo("CUSTOMER_NOT_FOUND");
    }

    @Test
    @DisplayName("fails when products are out of stock")
    void failsWhenProductsOutOfStock() {
      // Arrange
      var customer =
          new Customer(
              new CustomerId("CUST-002"),
              "Jane Doe",
              "jane@example.com",
              "555-9012",
              Customer.CustomerStatus.ACTIVE,
              Customer.LoyaltyTier.BRONZE);
      customerService.addCustomer(customer);

      // PROD-004 is out of stock in the default setup
      var request =
          new OrderRequest(
              "CUST-002",
              List.of(new OrderLineRequest("PROD-004", 1)),
              Optional.empty(),
              createValidAddress(),
              new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123"));

      // Act
      var result = workflow.process(request);

      // Assert
      assertThat(result.run().isLeft()).isTrue();
      var error = result.run().getLeft();
      assertThat(error).isInstanceOf(OrderError.InventoryError.class);
    }

    @Test
    @DisplayName("fails when address validation fails")
    void failsWhenAddressValidationFails() {
      // Arrange
      var customer =
          new Customer(
              new CustomerId("CUST-003"),
              "Test User",
              "test@example.com",
              "555-3456",
              Customer.CustomerStatus.ACTIVE,
              Customer.LoyaltyTier.BRONZE);
      customerService.addCustomer(customer);

      var invalidAddress =
          new ShippingAddress(
              "Test User",
              "123 Test St",
              "Test City",
              "", // Missing postcode
              "" // Missing country
              );

      var request =
          new OrderRequest(
              "CUST-003",
              List.of(new OrderLineRequest("PROD-001", 1)),
              Optional.empty(),
              invalidAddress,
              new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123"));

      // Act
      var result = workflow.process(request);

      // Assert
      assertThat(result.run().isLeft()).isTrue();
      var error = result.run().getLeft();
      assertThat(error).isInstanceOf(OrderError.ShippingError.class);
    }
  }

  @Nested
  @DisplayName("Payment Processing")
  class PaymentProcessingTests {

    @Test
    @DisplayName("fails when payment is declined")
    void failsWhenPaymentDeclined() {
      // Arrange
      var customer =
          new Customer(
              new CustomerId("CUST-004"),
              "Declined User",
              "declined@example.com",
              "555-7890",
              Customer.CustomerStatus.ACTIVE,
              Customer.LoyaltyTier.BRONZE);
      customerService.addCustomer(customer);

      // The default InMemoryPaymentService declines this card number
      var request =
          new OrderRequest(
              "CUST-004",
              List.of(new OrderLineRequest("PROD-001", 1)),
              Optional.empty(),
              createValidAddress(),
              new PaymentMethod.CreditCard("4111111111111111", "12", "2025", "123"));

      // Act
      var result = workflow.process(request);

      // Assert
      assertThat(result.run().isLeft()).isTrue();
      var error = result.run().getLeft();
      assertThat(error).isInstanceOf(OrderError.PaymentError.class);
      assertThat(error.code()).isEqualTo("PAYMENT_DECLINED");
    }
  }

  @Nested
  @DisplayName("Promo Code Application")
  class PromoCodeTests {

    @Test
    @DisplayName("applies valid promo code")
    void appliesValidPromoCode() {
      // Arrange
      var customer =
          new Customer(
              new CustomerId("CUST-005"),
              "Promo User",
              "promo@example.com",
              "555-1111",
              Customer.CustomerStatus.ACTIVE,
              Customer.LoyaltyTier.BRONZE);
      customerService.addCustomer(customer);

      var request =
          new OrderRequest(
              "CUST-005",
              List.of(new OrderLineRequest("PROD-001", 1)),
              Optional.of("SAVE10"), // 10% discount promo code
              createValidAddress(),
              new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123"));

      // Act
      var result = workflow.process(request);

      // Assert
      assertThat(result.run().isRight()).isTrue();
      var orderResult = result.run().getRight();
      assertThat(orderResult.totalCharged()).isNotNull();
    }

    @Test
    @DisplayName("fails with invalid promo code")
    void failsWithInvalidPromoCode() {
      // Arrange
      var customer =
          new Customer(
              new CustomerId("CUST-006"),
              "Invalid Promo User",
              "invalid@example.com",
              "555-2222",
              Customer.CustomerStatus.ACTIVE,
              Customer.LoyaltyTier.BRONZE);
      customerService.addCustomer(customer);

      var request =
          new OrderRequest(
              "CUST-006",
              List.of(new OrderLineRequest("PROD-001", 1)),
              Optional.of("INVALID_CODE"),
              createValidAddress(),
              new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123"));

      // Act
      var result = workflow.process(request);

      // Assert
      assertThat(result.run().isLeft()).isTrue();
      var error = result.run().getLeft();
      assertThat(error).isInstanceOf(OrderError.DiscountError.class);
    }
  }

  private ShippingAddress createValidAddress() {
    return new ShippingAddress("Test User", "123 Test Street", "London", "SW1A 1AA", "GB");
  }
}
