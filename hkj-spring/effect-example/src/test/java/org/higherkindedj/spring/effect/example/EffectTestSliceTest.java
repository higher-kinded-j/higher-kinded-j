// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example;

import static org.assertj.core.api.Assertions.*;

import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.spring.autoconfigure.test.EffectTest;
import org.higherkindedj.spring.effect.example.domain.OrderRequest;
import org.higherkindedj.spring.effect.example.domain.OrderResult;
import org.higherkindedj.spring.effect.example.domain.OrderStatus;
import org.higherkindedj.spring.effect.example.effect.OrderOp;
import org.higherkindedj.spring.effect.example.effect.OrderOpKind;
import org.higherkindedj.spring.effect.example.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Demonstrates {@code @EffectTest(effects = {...})} test slice.
 *
 * <p>The {@code @EffectTest} annotation auto-discovers the {@code @Interpreter(OrderOp.class)} bean
 * (InMemoryOrderInterpreter), combines interpreters, and registers an {@code EffectBoundary} bean.
 * No manual boundary wiring needed in the test.
 *
 * <p>This test runs without the web layer ({@code WebEnvironment.NONE}), making it faster than full
 * MockMvc integration tests while still using Spring's dependency injection.
 */
@SpringBootTest(
    classes = EffectExampleApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EffectTest(effects = {OrderOp.class})
@DisplayName("@EffectTest Slice Tests")
class EffectTestSliceTest {

  @Autowired private EffectBoundary<OrderOpKind.Witness> boundary;

  @Autowired private OrderService service;

  @Test
  @DisplayName("Should auto-wire EffectBoundary from @EffectTest(effects = {OrderOp.class})")
  void shouldAutoWireBoundary() {
    assertThat(boundary).isNotNull();
  }

  @Test
  @DisplayName("Should place order via auto-wired boundary")
  void shouldPlaceOrder() {
    OrderResult result = boundary.run(service.placeOrder(new OrderRequest("C001", "ITEM-42", 2)));

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(result.orderId()).startsWith("ORD-");
  }

  @Test
  @DisplayName("Should get order status via auto-wired boundary")
  void shouldGetStatus() {
    OrderStatus status = boundary.run(service.getOrderStatus("ORD-123"));

    assertThat(status).isEqualTo(OrderStatus.PENDING);
  }
}
