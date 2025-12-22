// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service;

import java.util.List;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.ValidatedOrder;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Service for order validation.
 *
 * <p>This service demonstrates both Either-based validation (fail-fast) and Validated-based
 * validation (accumulating errors).
 */
@GeneratePathBridge
public interface OrderValidationService {

  /**
   * Validates an order request, failing fast on the first error.
   *
   * @param request the order request to validate
   * @return either an error or the validated order
   */
  @PathVia(doc = "Validates order request, failing fast on first error")
  Either<OrderError, ValidatedOrder> validateOrder(OrderRequest request);

  /**
   * Validates an order request, accumulating all errors.
   *
   * <p>This is useful when you want to show all validation errors at once rather than making the
   * user fix them one at a time.
   *
   * @param request the order request to validate
   * @return validated result with all errors or the validated order
   */
  @PathVia(doc = "Validates order request, accumulating all validation errors")
  Validated<List<OrderError.FieldError>, ValidatedOrder> validateOrderAccumulating(
      OrderRequest request);

  /**
   * Validates individual order fields.
   *
   * @param request the order request
   * @return validated result with field errors or the request
   */
  @PathVia(doc = "Validates order fields for format and constraints")
  Validated<List<OrderError.FieldError>, OrderRequest> validateFields(OrderRequest request);
}
