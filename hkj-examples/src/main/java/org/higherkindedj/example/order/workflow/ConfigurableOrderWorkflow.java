// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import org.higherkindedj.example.order.config.WorkflowConfig;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.OrderResult;
import org.higherkindedj.example.order.model.PartialFulfilmentResult;
import org.higherkindedj.example.order.model.SplitShipmentResult;
import org.higherkindedj.example.order.model.ValidatedOrder;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.resilience.Resilience;
import org.higherkindedj.example.order.resilience.RetryPolicy;
import org.higherkindedj.example.order.service.CustomerService;
import org.higherkindedj.example.order.service.DiscountService;
import org.higherkindedj.example.order.service.InventoryService;
import org.higherkindedj.example.order.service.NotificationService;
import org.higherkindedj.example.order.service.PaymentService;
import org.higherkindedj.example.order.service.ShippingService;
import org.higherkindedj.example.order.service.WarehouseService;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.jspecify.annotations.Nullable;

/**
 * Order workflow with configuration-driven behaviour.
 *
 * <p>Demonstrates how to use WorkflowConfig to control:
 *
 * <ul>
 *   <li>Retry policies with exponential backoff
 *   <li>Timeout durations for external calls
 *   <li>Feature flags for optional behaviours:
 *       <ul>
 *         <li>Partial fulfilment - ships available items, back-orders the rest
 *         <li>Split shipments - ships from multiple warehouses
 *         <li>Loyalty discounts - applies tier-based discounts
 *       </ul>
 * </ul>
 *
 * <p>This workflow uses the resilience utilities to wrap service calls with retry and timeout logic
 * based on configuration.
 */
public class ConfigurableOrderWorkflow {

  private final OrderWorkflow baseWorkflow;
  private final PartialFulfilmentWorkflow partialFulfilmentWorkflow;
  @Nullable private final SplitShipmentWorkflow splitShipmentWorkflow;
  private final CustomerService customerService;
  private final ShippingService shippingService;
  private final WorkflowConfig config;

  /**
   * Creates a configurable order workflow with all services.
   *
   * @param customerService customer service
   * @param inventoryService inventory service
   * @param discountService discount service
   * @param paymentService payment service
   * @param shippingService shipping service
   * @param notificationService notification service
   * @param warehouseService warehouse service for split shipments
   * @param config workflow configuration
   */
  public ConfigurableOrderWorkflow(
      CustomerService customerService,
      InventoryService inventoryService,
      DiscountService discountService,
      PaymentService paymentService,
      ShippingService shippingService,
      NotificationService notificationService,
      WarehouseService warehouseService,
      WorkflowConfig config) {
    this.customerService = customerService;
    this.shippingService = shippingService;
    this.baseWorkflow =
        new OrderWorkflow(
            customerService,
            inventoryService,
            discountService,
            paymentService,
            shippingService,
            notificationService,
            config);
    this.partialFulfilmentWorkflow =
        new PartialFulfilmentWorkflow(
            inventoryService, paymentService, shippingService, notificationService);
    this.splitShipmentWorkflow = new SplitShipmentWorkflow(warehouseService, shippingService);
    this.config = config;
  }

  /**
   * Creates a configurable order workflow without warehouse service.
   *
   * <p>Split shipments will not be available with this constructor.
   *
   * @param customerService customer service
   * @param inventoryService inventory service
   * @param discountService discount service
   * @param paymentService payment service
   * @param shippingService shipping service
   * @param notificationService notification service
   * @param config workflow configuration
   */
  public ConfigurableOrderWorkflow(
      CustomerService customerService,
      InventoryService inventoryService,
      DiscountService discountService,
      PaymentService paymentService,
      ShippingService shippingService,
      NotificationService notificationService,
      WorkflowConfig config) {
    this.customerService = customerService;
    this.shippingService = shippingService;
    this.baseWorkflow =
        new OrderWorkflow(
            customerService,
            inventoryService,
            discountService,
            paymentService,
            shippingService,
            notificationService,
            config);
    this.partialFulfilmentWorkflow =
        new PartialFulfilmentWorkflow(
            inventoryService, paymentService, shippingService, notificationService);
    this.splitShipmentWorkflow = null;
    this.config = config;
  }

  /**
   * Processes an order with configuration-driven resilience and feature flags.
   *
   * <p>The workflow adapts based on enabled features:
   *
   * <ul>
   *   <li>If partial fulfilment is enabled and inventory is insufficient, attempts partial
   *       fulfilment
   *   <li>Loyalty discounts are applied automatically when enabled (via OrderWorkflow)
   * </ul>
   *
   * @param request the order request
   * @return either an error or the order result
   */
  public EitherPath<OrderError, OrderResult> process(OrderRequest request) {
    var retryPolicy = createRetryPolicy();

    // Calculate total timeout from all service timeouts
    var totalTimeout =
        config
            .timeoutConfig()
            .paymentTimeout()
            .plus(config.timeoutConfig().inventoryTimeout())
            .plus(config.timeoutConfig().shippingTimeout())
            .plus(config.timeoutConfig().notificationTimeout());

    return Resilience.resilient(
        Path.io(() -> executeWorkflow(request)),
        retryPolicy,
        totalTimeout,
        "ConfigurableOrderWorkflow.process");
  }

  /**
   * Processes an order with partial fulfilment support.
   *
   * <p>Only available when partial fulfilment feature is enabled. Ships available items immediately
   * and creates back-orders for unavailable items.
   *
   * @param order the validated order to process
   * @return either an error or the partial fulfilment result
   */
  public EitherPath<OrderError, PartialFulfilmentResult> processWithPartialFulfilment(
      ValidatedOrder order) {
    if (!isPartialFulfilmentEnabled()) {
      return Path.left(
          OrderError.SystemError.unexpected(
              "Partial fulfilment is not enabled", new IllegalStateException("Feature disabled")));
    }
    return partialFulfilmentWorkflow.process(order);
  }

  /**
   * Creates split shipments when items are in multiple warehouses.
   *
   * <p>Only available when split shipments feature is enabled.
   *
   * @param order the validated order
   * @param reservation the inventory reservation with warehouse assignments
   * @return either an error or the split shipment result
   */
  public EitherPath<OrderError, SplitShipmentResult> processWithSplitShipments(
      ValidatedOrder order, InventoryReservation reservation) {
    if (!isSplitShipmentsEnabled()) {
      return Path.left(
          OrderError.SystemError.unexpected(
              "Split shipments are not enabled", new IllegalStateException("Feature disabled")));
    }
    if (splitShipmentWorkflow == null) {
      return Path.left(
          OrderError.SystemError.unexpected(
              "Split shipments require WarehouseService",
              new IllegalStateException("Incompatible configuration")));
    }
    return splitShipmentWorkflow.createSplitShipments(order, reservation);
  }

  /**
   * Determines if an order requires split shipments.
   *
   * @param reservation the inventory reservation
   * @return true if items are in multiple warehouses and split shipments are enabled
   */
  public boolean shouldUseSplitShipments(InventoryReservation reservation) {
    return isSplitShipmentsEnabled()
        && splitShipmentWorkflow != null
        && splitShipmentWorkflow.requiresSplitShipment(reservation);
  }

  private OrderResult executeWorkflow(OrderRequest request) {
    // First, try the standard workflow
    var result = baseWorkflow.process(request);
    return result.run().fold(error -> handleWorkflowError(error, request), success -> success);
  }

  private OrderResult handleWorkflowError(OrderError error, OrderRequest request) {
    // If it's an inventory error and partial fulfilment is enabled, try partial fulfilment
    if (isPartialFulfilmentEnabled() && error instanceof OrderError.InventoryError) {
      return attemptPartialFulfilment(request, error);
    }

    // Otherwise, wrap and throw the error
    throw new WorkflowException(error);
  }

  private OrderResult attemptPartialFulfilment(OrderRequest request, OrderError originalError) {
    // Look up and validate customer first
    var customerId = new CustomerId(request.customerId());
    var customerResult = customerService.findById(customerId);

    if (customerResult.isLeft()) {
      throw new WorkflowException(customerResult.getLeft());
    }

    var customer = customerResult.getRight();

    // Build a validated order for partial fulfilment
    var validatedOrderResult = buildValidatedOrderForPartialFulfilment(request, customer);

    return validatedOrderResult
        .run()
        .fold(
            buildError -> {
              throw new WorkflowException(buildError);
            },
            validatedOrder -> executePartialFulfilment(validatedOrder, customer, originalError));
  }

  private OrderResult executePartialFulfilment(
      ValidatedOrder validatedOrder, Customer customer, OrderError originalError) {
    var partialResult = partialFulfilmentWorkflow.process(validatedOrder);
    return partialResult
        .run()
        .fold(
            partialError -> {
              // Partial fulfilment also failed - throw original error
              throw new WorkflowException(originalError);
            },
            partial -> partial.toOrderResult(customer.id()));
  }

  private EitherPath<OrderError, ValidatedOrder> buildValidatedOrderForPartialFulfilment(
      OrderRequest request, Customer customer) {
    // Validate shipping address first
    return Path.either(shippingService.validateAddress(request.shippingAddress()))
        .map(validAddress -> createValidatedOrder(request, customer, validAddress));
  }

  private ValidatedOrder createValidatedOrder(
      OrderRequest request,
      Customer customer,
      org.higherkindedj.example.order.model.ValidatedShippingAddress validAddress) {
    var lines =
        request.lines().stream()
            .map(
                line ->
                    org.higherkindedj.example.order.model.ValidatedOrderLine.of(
                        new org.higherkindedj.example.order.model.value.ProductId(line.productId()),
                        new org.higherkindedj.example.order.model.Product(
                            new org.higherkindedj.example.order.model.value.ProductId(
                                line.productId()),
                            "Product " + line.productId(),
                            "Description",
                            org.higherkindedj.example.order.model.value.Money.gbp("10.00"),
                            "General",
                            true),
                        line.quantity()))
            .toList();

    return new ValidatedOrder(
        org.higherkindedj.example.order.model.value.OrderId.generate(),
        customer.id(),
        customer,
        lines,
        java.util.Optional.empty(),
        validAddress,
        request.paymentMethod(),
        ValidatedOrder.calculateSubtotal(lines),
        java.time.Instant.now());
  }

  private RetryPolicy createRetryPolicy() {
    var retryConfig = config.retryConfig();
    return RetryPolicy.of(
        retryConfig.maxRetries(),
        retryConfig.initialDelay(),
        retryConfig.maxDelay(),
        retryConfig.backoffMultiplier());
  }

  /**
   * Checks if partial fulfilment is enabled.
   *
   * @return true if partial fulfilment is enabled
   */
  public boolean isPartialFulfilmentEnabled() {
    return config.featureFlags().enablePartialFulfilment();
  }

  /**
   * Checks if split shipments are enabled.
   *
   * @return true if split shipments are enabled
   */
  public boolean isSplitShipmentsEnabled() {
    return config.featureFlags().enableSplitShipments();
  }

  /**
   * Checks if loyalty discounts are enabled.
   *
   * @return true if loyalty discounts are enabled
   */
  public boolean isLoyaltyDiscountsEnabled() {
    return config.featureFlags().enableLoyaltyDiscounts();
  }

  /**
   * Returns the current configuration.
   *
   * @return the workflow configuration
   */
  public WorkflowConfig getConfig() {
    return config;
  }

  /** Exception wrapper for workflow errors that preserves the OrderError. */
  public static class WorkflowException extends RuntimeException {
    private final OrderError error;

    /**
     * Creates a workflow exception wrapping an OrderError.
     *
     * @param error the order error
     */
    public WorkflowException(OrderError error) {
      super(error.message());
      this.error = error;
    }

    /**
     * Returns the underlying OrderError.
     *
     * @return the order error
     */
    public OrderError getError() {
      return error;
    }
  }
}
