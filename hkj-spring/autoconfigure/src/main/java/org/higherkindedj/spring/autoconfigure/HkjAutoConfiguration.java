// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import org.higherkindedj.hkt.Kind;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for higher-kinded-j core functionality.
 *
 * <p>This configuration is activated when {@link Kind} is on the classpath. Type class instances
 * (Monad, Functor, etc.) are accessed via their static factory methods (e.g.,
 * EitherMonad.instance()) rather than being provided as beans.
 *
 * <p>Enables configuration properties via {@link HkjProperties} which can be customized in
 * application.yml/properties with the "hkj" prefix.
 */
@AutoConfiguration
@ConditionalOnClass(Kind.class)
@EnableConfigurationProperties(HkjProperties.class)
public class HkjAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  static class HkjCoreConfiguration {
    // Placeholder for future core beans if needed
    // Type class instances are accessed via static methods:
    // - EitherMonad.instance()
    // - ListMonad.instance()
    // - etc.
  }
}
