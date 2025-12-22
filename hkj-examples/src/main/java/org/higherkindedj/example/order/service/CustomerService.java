// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service;

import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.either.Either;

/**
 * Service for customer operations.
 *
 * <p>The {@code @GeneratePathBridge} annotation generates a {@code CustomerServicePaths} class that
 * wraps return values in Effect Path types for fluent composition.
 */
@GeneratePathBridge
public interface CustomerService {

  /**
   * Looks up a customer by their ID.
   *
   * @param customerId the customer identifier
   * @return either an error or the customer details
   */
  @PathVia(doc = "Looks up customer details by ID, returning EitherPath for composition")
  Either<OrderError, Customer> findById(CustomerId customerId);

  /**
   * Validates that a customer is eligible to place orders.
   *
   * @param customer the customer to validate
   * @return either an error (if ineligible) or the validated customer
   */
  @PathVia(doc = "Validates customer eligibility for order placement")
  Either<OrderError, Customer> validateEligibility(Customer customer);
}
