// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.service;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.spring.effect.example.domain.OrderRequest;
import org.higherkindedj.spring.effect.example.domain.OrderResult;
import org.higherkindedj.spring.effect.example.domain.OrderStatus;
import org.higherkindedj.spring.effect.example.effect.OrderOp;
import org.higherkindedj.spring.effect.example.effect.OrderOpFunctor;
import org.higherkindedj.spring.effect.example.effect.OrderOpKind;
import org.higherkindedj.spring.effect.example.effect.OrderOpKindHelper;
import org.springframework.stereotype.Service;

/**
 * Service that builds Free monad programs for order processing.
 *
 * <p>This service constructs pure program descriptions without executing them. The EffectBoundary
 * or TestBoundary interprets and executes these programs.
 *
 * <p>For simplicity, this example uses OrderOpKind.Witness directly as the effect type (single
 * effect). A multi-effect version would use a composed EitherF witness type.
 */
@Service
public class OrderService {

  private static final Functor<OrderOpKind.Witness> ORDER_FUNCTOR = OrderOpFunctor.instance();

  /**
   * Builds a program to place an order.
   *
   * <p>The program: 1. Places the order 2. Returns the result
   *
   * @param request the order request
   * @return a Free program describing the order placement
   */
  public Free<OrderOpKind.Witness, OrderResult> placeOrder(OrderRequest request) {
    return Free.liftF(
        OrderOpKindHelper.ORDER_OP.widen(
            new OrderOp.PlaceOrder<>(
                request.customerId(), request.itemId(), request.quantity(), Function.identity())),
        ORDER_FUNCTOR);
  }

  /**
   * Builds a program to get the status of an order.
   *
   * @param orderId the order ID to look up
   * @return a Free program that returns the order status
   */
  public Free<OrderOpKind.Witness, OrderStatus> getOrderStatus(String orderId) {
    return Free.liftF(
        OrderOpKindHelper.ORDER_OP.widen(new OrderOp.GetStatus<>(orderId, Function.identity())),
        ORDER_FUNCTOR);
  }
}
