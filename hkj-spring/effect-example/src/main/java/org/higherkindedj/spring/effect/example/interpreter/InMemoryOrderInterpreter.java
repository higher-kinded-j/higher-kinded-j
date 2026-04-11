// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.interpreter;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.spring.autoconfigure.effect.Interpreter;
import org.higherkindedj.spring.effect.example.domain.OrderResult;
import org.higherkindedj.spring.effect.example.domain.OrderStatus;
import org.higherkindedj.spring.effect.example.effect.OrderOp;
import org.higherkindedj.spring.effect.example.effect.OrderOpKind;
import org.higherkindedj.spring.effect.example.effect.OrderOpKindHelper;

/**
 * In-memory interpreter for {@link OrderOp} targeting the IO monad.
 *
 * <p>Stores orders in a concurrent map. In production, this would use a database.
 */
@Interpreter(OrderOp.class)
public class InMemoryOrderInterpreter implements Natural<OrderOpKind.Witness, IOKind.Witness> {

  private final ConcurrentMap<String, OrderStatus> orders = new ConcurrentHashMap<>();

  @Override
  @SuppressWarnings("unchecked")
  public <A> Kind<IOKind.Witness, A> apply(Kind<OrderOpKind.Witness, A> fa) {
    OrderOp<A> op = OrderOpKindHelper.ORDER_OP.narrow(fa);
    return switch (op) {
      case OrderOp.PlaceOrder<A> place ->
          IOKindHelper.IO_OP.widen(
              IO.delay(
                  () -> {
                    String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
                    orders.put(orderId, OrderStatus.CONFIRMED);
                    return place.k().apply(OrderResult.confirmed(orderId));
                  }));
      case OrderOp.GetStatus<A> get ->
          IOKindHelper.IO_OP.widen(
              IO.delay(
                  () -> {
                    OrderStatus status = orders.getOrDefault(get.orderId(), OrderStatus.PENDING);
                    return get.k().apply(status);
                  }));
    };
  }

  /** Returns the orders map for testing/inspection. */
  public ConcurrentMap<String, OrderStatus> orders() {
    return orders;
  }
}
