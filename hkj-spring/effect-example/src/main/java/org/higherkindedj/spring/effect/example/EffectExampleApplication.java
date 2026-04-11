// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example;

import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.spring.actuator.HkjMetricsService;
import org.higherkindedj.spring.actuator.ObservableEffectBoundary;
import org.higherkindedj.spring.effect.example.effect.OrderOpKind;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Example Spring Boot application demonstrating EffectBoundary integration.
 *
 * <p>This application demonstrates Level 1 of the adoption ladder: a manually defined {@code
 * EffectBoundary} bean that interprets Free programs using a single-effect interpreter, with
 * optional metrics via {@link ObservableEffectBoundary}.
 *
 * <p>Try these endpoints:
 *
 * <ul>
 *   <li>POST http://localhost:8081/api/orders - Place a new order
 *   <li>GET http://localhost:8081/api/orders/{id}/status - Get order status
 *   <li>GET http://localhost:8081/actuator/metrics/hkj.effect.boundary.invocations - Boundary
 *       metrics
 * </ul>
 */
@SpringBootApplication
public class EffectExampleApplication {

  /** Creates an EffectExampleApplication instance. */
  public EffectExampleApplication() {}

  /**
   * Application entry point.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(EffectExampleApplication.class, args);
  }

  /**
   * Creates the EffectBoundary bean for OrderOp.
   *
   * @param interpreter the order interpreter (discovered via @Interpreter annotation)
   * @return the effect boundary for order programs
   */
  @Bean
  public EffectBoundary<OrderOpKind.Witness> orderBoundary(
      Natural<OrderOpKind.Witness, IOKind.Witness> interpreter) {
    return EffectBoundary.of(interpreter);
  }

  /**
   * Creates an ObservableEffectBoundary that wraps the boundary with metrics.
   *
   * <p>When actuator is on the classpath and metrics are enabled, this bean records
   * success/error/duration metrics for every boundary execution. The controller can inject this
   * instead of the plain EffectBoundary for instrumented execution.
   *
   * @param boundary the effect boundary
   * @param metricsService the metrics service (null if actuator not present)
   * @return the observable boundary, or null if metrics are not available
   */
  @Bean
  public @Nullable ObservableEffectBoundary<OrderOpKind.Witness> observableOrderBoundary(
      EffectBoundary<OrderOpKind.Witness> boundary, @Nullable HkjMetricsService metricsService) {
    if (metricsService == null) {
      return null;
    }
    return new ObservableEffectBoundary<>(boundary, metricsService);
  }
}
