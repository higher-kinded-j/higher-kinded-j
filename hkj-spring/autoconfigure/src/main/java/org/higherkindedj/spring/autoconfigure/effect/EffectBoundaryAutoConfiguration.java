// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.effect;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Auto-configuration for the EffectBoundary system.
 *
 * <p>This configuration is activated when:
 *
 * <ul>
 *   <li>{@link Kind} is on the classpath (higher-kinded-j core)
 *   <li>{@link EffectBoundary} is on the classpath (effect boundary module)
 *   <li>The {@code hkj.effect-boundary.enabled} property is not explicitly set to false
 * </ul>
 *
 * <p>The actual {@link EffectBoundary} bean registration is handled by {@link
 * EffectBoundaryRegistrar}, which is imported via {@link EnableEffectBoundary}. This
 * auto-configuration class serves as the conditional gate and integration point.
 *
 * @see EnableEffectBoundary
 * @see EffectBoundaryRegistrar
 */
@AutoConfiguration(after = HkjAutoConfiguration.class)
@ConditionalOnClass({Kind.class, EffectBoundary.class})
@ConditionalOnProperty(prefix = "hkj.effect-boundary", name = "enabled", matchIfMissing = true)
public class EffectBoundaryAutoConfiguration {

  /** Creates a new EffectBoundaryAutoConfiguration. */
  public EffectBoundaryAutoConfiguration() {}
}
