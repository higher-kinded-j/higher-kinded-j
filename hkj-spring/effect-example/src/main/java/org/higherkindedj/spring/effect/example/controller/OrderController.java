// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.controller;

import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.spring.effect.example.domain.OrderRequest;
import org.higherkindedj.spring.effect.example.domain.OrderResult;
import org.higherkindedj.spring.effect.example.domain.OrderStatus;
import org.higherkindedj.spring.effect.example.effect.OrderOpKind;
import org.higherkindedj.spring.effect.example.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating EffectBoundary adoption Level 1.
 *
 * <p>Level 1: The controller uses {@code boundary.runIO()} to interpret Free programs and returns
 * {@code IOPath}, which the existing {@code IOPathReturnValueHandler} converts to HTTP responses.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/orders - Place a new order
 *   <li>GET /api/orders/{id}/status - Get order status
 * </ul>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final EffectBoundary<OrderOpKind.Witness> boundary;
  private final OrderService service;

  /**
   * Creates the controller with injected boundary and service.
   *
   * @param boundary the effect boundary for interpreting programs
   * @param service the order service that builds Free programs
   */
  public OrderController(EffectBoundary<OrderOpKind.Witness> boundary, OrderService service) {
    this.boundary = boundary;
    this.service = service;
  }

  /**
   * Place a new order.
   *
   * <p>Demonstrates Level 1: boundary.runIO() returns IOPath, handled by existing
   * IOPathReturnValueHandler.
   *
   * @param request the order request
   * @return an IOPath that will execute the order program when consumed
   */
  @PostMapping
  public IOPath<OrderResult> placeOrder(@RequestBody OrderRequest request) {
    return boundary.runIO(service.placeOrder(request));
  }

  /**
   * Get order status.
   *
   * @param id the order ID
   * @return an IOPath that will look up the status when consumed
   */
  @GetMapping("/{id}/status")
  public IOPath<OrderStatus> getStatus(@PathVariable String id) {
    return boundary.runIO(service.getOrderStatus(id));
  }
}
