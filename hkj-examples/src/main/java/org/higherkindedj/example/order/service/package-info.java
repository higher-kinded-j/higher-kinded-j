// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Service interfaces for the Order Workflow V2 example.
 *
 * <p>All service interfaces in this package use the {@code @GeneratePathBridge} annotation to
 * generate companion classes that wrap methods in Effect Path types. This enables fluent,
 * composable workflow construction.
 *
 * <h2>Service Interfaces</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.order.service.CustomerService} - Customer lookup and
 *       validation
 *   <li>{@link org.higherkindedj.example.order.service.ProductService} - Product catalogue
 *       operations
 *   <li>{@link org.higherkindedj.example.order.service.InventoryService} - Stock checking and
 *       reservation
 *   <li>{@link org.higherkindedj.example.order.service.DiscountService} - Promo codes and loyalty
 *       discounts
 *   <li>{@link org.higherkindedj.example.order.service.PaymentService} - Payment processing
 *   <li>{@link org.higherkindedj.example.order.service.ShippingService} - Shipment creation
 *   <li>{@link org.higherkindedj.example.order.service.NotificationService} - Customer
 *       notifications
 * </ul>
 *
 * <h2>Generated Path Bridges</h2>
 *
 * <p>For each service interface, an annotation processor generates a companion {@code *Paths}
 * class. For example, {@code CustomerService} gets a companion {@code CustomerServicePaths} class:
 *
 * <pre>{@code
 * // Original interface method:
 * Either<OrderError, Customer> findById(CustomerId customerId);
 *
 * // Generated Path bridge method:
 * EitherPath<OrderError, Customer> findById(CustomerId customerId);
 * }</pre>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * CustomerServicePaths customerPaths = new CustomerServicePaths(customerService);
 *
 * // Fluent composition with Effect Paths
 * EitherPath<OrderError, Customer> result = customerPaths.findById(customerId)
 *     .flatMap(customer -> customerPaths.validateEligibility(customer));
 * }</pre>
 *
 * @see org.higherkindedj.hkt.effect.annotation.GeneratePathBridge
 * @see org.higherkindedj.hkt.effect.annotation.PathVia
 */
@NullMarked
package org.higherkindedj.example.order.service;

import org.jspecify.annotations.NullMarked;
