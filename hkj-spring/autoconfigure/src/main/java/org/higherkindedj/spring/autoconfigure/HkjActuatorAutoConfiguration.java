// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.higherkindedj.spring.actuator.HkjAsyncHealthIndicator;
import org.higherkindedj.spring.actuator.HkjMetricsEndpoint;
import org.higherkindedj.spring.actuator.HkjMetricsService;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
 * </ul>
 *
 * <p>Configuration properties:
 *
 * <ul>
 *   <li>hkj.actuator.metrics-enabled - Enable/disable metrics (default: true)
 *   <li>management.endpoint.hkj.enabled - Enable/disable custom endpoint (default: true)
 *   <li>management.health.hkj-async.enabled - Enable/disable health indicator (default: true)
 * </ul>
 */
@AutoConfiguration(after = HkjAutoConfiguration.class)
@ConditionalOnClass({HealthIndicator.class, MeterRegistry.class})
@EnableConfigurationProperties(HkjProperties.class)
public class HkjActuatorAutoConfiguration {

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
   * @param metricsService the metrics service (optional)
   * @return the custom endpoint
   */
  @Bean
  @ConditionalOnAvailableEndpoint
  public HkjMetricsEndpoint hkjMetricsEndpoint(
      HkjProperties properties, HkjMetricsService metricsService) {
    return new HkjMetricsEndpoint(properties, metricsService);
  }

  /**
   * Creates the async executor health indicator.
   *
   * <p>Monitors the thread pool used by EitherT async operations.
   *
   * <p>Enabled by default when async executor is configured. Disable with: {@code
   * management.health.hkj-async.enabled=false}
   *
   * @param executor the async executor bean (optional, may not be present)
   * @return the health indicator
   */
  @Bean(name = "hkjAsyncHealthIndicator")
  @ConditionalOnEnabledHealthIndicator("hkj-async")
  @ConditionalOnBean(name = "hkjAsyncExecutor")
  public HkjAsyncHealthIndicator hkjAsyncHealthIndicator(ThreadPoolTaskExecutor executor) {
    return new HkjAsyncHealthIndicator(executor);
  }
}
