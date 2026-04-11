// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.test;

import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Auto-configuration for the {@link EffectTest} test slice.
 *
 * <p>This configuration is activated when {@link EffectBoundary} is on the classpath and provides
 * the minimal beans needed for effect boundary testing without the web layer.
 *
 * @see EffectTest
 */
@AutoConfiguration(after = HkjAutoConfiguration.class)
@ConditionalOnClass(EffectBoundary.class)
public class EffectTestAutoConfiguration {

  /** Creates a new EffectTestAutoConfiguration. */
  public EffectTestAutoConfiguration() {}
}
