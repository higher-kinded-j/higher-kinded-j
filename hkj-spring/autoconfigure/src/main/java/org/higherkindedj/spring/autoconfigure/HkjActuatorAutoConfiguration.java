// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import org.higherkindedj.spring.actuator.HkjAsyncHealthIndicator;
import org.higherkindedj.spring.actuator.HkjMetricsEndpoint;
import org.higherkindedj.spring.actuator.HkjMetricsService;
import org.higherkindedj.spring.actuator.HkjVirtualThreadHealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for higher-kinded-j Spring Boot Actuator integration.
 *
 * <p>This configuration is activated when:
 *
 * <ul>
 *   <li>Spring Boot Actuator is on the classpath
 *   <li>Micrometer is available (for metrics)
 *   <li>HKJ auto-configuration is enabled
 * </ul>
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>{@link HkjMetricsService} - Micrometer metrics for HKJ handlers
 *   <li>{@link HkjMetricsEndpoint} - Custom actuator endpoint at /actuator/hkj
 *   <li>{@link HkjAsyncHealthIndicator} - Health indicator for async executor
 *   <li>{@link HkjVirtualThreadHealthIndicator} - Health indicator for virtual threads
 * </ul>
 *
 * <p>Configuration properties:
 *
 * <ul>
 *   <li>hkj.actuator.metrics-enabled - Enable/disable metrics (default: true)
 *   <li>management.endpoint.hkj.enabled - Enable/disable custom endpoint (default: true)
 *   <li>management.health.hkj-async.enabled - Enable/disable health indicator (default: true)
 *   <li>management.health.hkj-virtual-threads.enabled - Enable/disable virtual thread health
 *       (default: true)
 * </ul>
 */
@AutoConfiguration(after = HkjAutoConfiguration.class)
@ConditionalOnClass({HealthIndicator.class, MeterRegistry.class})
@EnableConfigurationProperties(HkjProperties.class)
public class HkjActuatorAutoConfiguration {

  /** Creates a new HkjActuatorAutoConfiguration. */
  public HkjActuatorAutoConfiguration() {}

  /**
   * Creates the HKJ metrics service for tracking handler invocations.
   *
   * <p>Enabled by default. Disable with: {@code hkj.actuator.metrics-enabled=false}
   *
   * @param meterRegistry the Micrometer registry
   * @return the metrics service
   */
  @Bean
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnProperty(name = "hkj.actuator.metrics-enabled", matchIfMissing = true)
  public HkjMetricsService hkjMetricsService(MeterRegistry meterRegistry) {
    return new HkjMetricsService(meterRegistry);
  }

  /**
   * Creates the custom HKJ actuator endpoint.
   *
   * <p>Exposed at: {@code /actuator/hkj}
   *
   * <p>Enabled by default. Disable with: {@code management.endpoint.hkj.enabled=false}
   *
   * @param properties the HKJ configuration properties
   * @param metricsService provider of the metrics service (empty when {@code
   *     hkj.actuator.metrics-enabled=false}; the endpoint then reports configuration only)
   * @return the custom endpoint
   */
  @Bean
  @ConditionalOnAvailableEndpoint
  public HkjMetricsEndpoint hkjMetricsEndpoint(
      HkjProperties properties, ObjectProvider<HkjMetricsService> metricsService) {
    return new HkjMetricsEndpoint(properties, metricsService.getIfAvailable());
  }

  /**
   * Creates the async executor health indicator.
   *
   * <p>Monitors the application-defined {@code hkjAsyncExecutor} bean. It is injected as the widest
   * {@link Executor} type — the qualifier binds it to that specific bean, and {@link
   * HkjAsyncHealthIndicator} reports full pool statistics for a {@code ThreadPoolTaskExecutor} or a
   * type-only {@code UP} for any other executor (e.g. a virtual-thread executor). Injecting the
   * concrete pool type would instead fail context startup when the named bean is not one.
   *
   * <p>Enabled by default when the executor bean is defined. Disable with: {@code
   * management.health.hkj-async.enabled=false}
   *
   * @param executor the {@code hkjAsyncExecutor} bean
   * @return the health indicator
   */
  @Bean(name = "hkjAsyncHealthIndicator")
  @ConditionalOnProperty(
      name = "management.health.hkj-async.enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnBean(name = "hkjAsyncExecutor")
  public HkjAsyncHealthIndicator hkjAsyncHealthIndicator(
      @Qualifier("hkjAsyncExecutor") Executor executor) {
    return new HkjAsyncHealthIndicator(executor);
  }

  /**
   * Creates the Virtual Thread health indicator.
   *
   * <p>Monitors the success and error rates of Virtual Thread based operations.
   *
   * <p>Enabled by default. Disable with: {@code
   * management.health.hkj-virtual-threads.enabled=false}
   *
   * @param metricsService the metrics service used to retrieve operation counts
   * @param properties the HKJ configuration properties
   * @return the virtual thread health indicator
   */
  @Bean(name = "hkjVirtualThreadHealthIndicator")
  @ConditionalOnBean(HkjMetricsService.class)
  @ConditionalOnProperty(
      name = "management.health.hkj-virtual-threads.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public HkjVirtualThreadHealthIndicator hkjVirtualThreadHealthIndicator(
      HkjMetricsService metricsService, HkjProperties properties) {
    return new HkjVirtualThreadHealthIndicator(
        metricsService, properties.getVirtualThreads().getHealthErrorThreshold());
  }
}
