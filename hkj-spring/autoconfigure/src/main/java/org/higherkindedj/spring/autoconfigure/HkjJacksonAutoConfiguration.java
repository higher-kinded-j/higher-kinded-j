// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.spring.json.HkjJacksonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Jackson serialization of higher-kinded-j types.
 *
 * <p>This configuration is activated when:
 *
 * <ul>
 *   <li>{@link ObjectMapper} is on the classpath (Jackson)
 *   <li>{@link Either} is on the classpath (higher-kinded-j core)
 *   <li>hkj.json.custom-serializers-enabled is true (default)
 * </ul>
 *
 * <p>Registers {@link HkjJacksonModule} which provides custom serializers and deserializers for:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.either.Either}
 *   <li>{@link org.higherkindedj.hkt.validated.Validated}
 * </ul>
 *
 * <p>The module is automatically picked up by Spring Boot's ObjectMapper auto-configuration and
 * applied to all Jackson ObjectMapper instances in the application.
 *
 * <p>To disable custom serializers:
 *
 * <pre>
 * hkj:
 *   json:
 *     custom-serializers-enabled: false
 * </pre>
 */
@AutoConfiguration(after = HkjAutoConfiguration.class)
@ConditionalOnClass({ObjectMapper.class, Either.class})
@AutoConfigureAfter(JacksonAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "hkj.json",
    name = "custom-serializers-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class HkjJacksonAutoConfiguration {

  /**
   * Provides the HkjJacksonModule bean which will be automatically registered with Spring Boot's
   * ObjectMapper.
   *
   * <p>Spring Boot automatically discovers and registers all {@link Module} beans with the
   * ObjectMapper, so we just need to declare it as a bean.
   *
   * @return the HkjJacksonModule
   */
  @Bean
  @ConditionalOnMissingBean(name = "hkjJacksonModule")
  public Module hkjJacksonModule() {
    return new HkjJacksonModule();
  }
}
