// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.service;

import java.util.function.Function;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.spring.effect.example.domain.OrderRequest;
import org.higherkindedj.spring.effect.example.domain.OrderResult;
import org.higherkindedj.spring.effect.example.domain.OrderStatus;
import org.higherkindedj.spring.effect.example.effect.OrderOpKind;
import org.higherkindedj.spring.effect.example.effect.OrderOpOps;
import org.springframework.stereotype.Service;

/**
 * Service that builds Free monad programs for order processing.
 *
 * <p>This service constructs pure program descriptions without executing them. The EffectBoundary
 * or TestBoundary interprets and executes these programs.
 *
 * <p>Programs are built from {@code OrderOpOps}, the smart constructors {@code @EffectAlgebra}
 * generates from {@code OrderOp}: they carry the widen and the Functor, so the service names the
 * operation and nothing else.
 *
 * <p>For simplicity, this example uses OrderOpKind.Witness directly as the effect type (single
 * effect). A multi-effect version would use a composed EitherF witness type.
 */
@Service
public class OrderService {

  /**
   * Builds a program to place an order.
   *
   * <p>The program: 1. Places the order 2. Returns the result
   *
   * @param request the order request
   * @return a Free program describing the order placement
   */
  public Free<OrderOpKind.Witness, OrderResult> placeOrder(OrderRequest request) {
    return OrderOpOps.placeOrder(
        request.customerId(), request.itemId(), request.quantity(), Function.identity());
  }

  /**
   * Builds a program to get the status of an order.
   *
   * @param orderId the order ID to look up
   * @return a Free program that returns the order status
   */
  public Free<OrderOpKind.Witness, OrderStatus> getOrderStatus(String orderId) {
    return OrderOpOps.getStatus(orderId, Function.identity());
  }
}
