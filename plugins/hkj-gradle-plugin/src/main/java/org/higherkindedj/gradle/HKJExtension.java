// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * Extension DSL for the HKJ Gradle plugin.
 *
 * <pre>{@code
 * hkj {
 *     version = "0.3.7-SNAPSHOT"
 *     preview = true
 *     spring = false
 *     checks {
 *         pathTypeMismatch = true
 *     }
 * }
 * }</pre>
 */
public abstract class HKJExtension {

  private final HKJChecksExtension checks;

  @Inject
  public HKJExtension(ObjectFactory objects) {
    this.checks = objects.newInstance(HKJChecksExtension.class);
  }

  /** HKJ library version. Defaults to the plugin version. */
  public abstract Property<String> getVersion();

  /** Whether to add --enable-preview flags. Defaults to true. */
  public abstract Property<Boolean> getPreview();

  /** Whether to add hkj-spring-boot-starter. Defaults to false. */
  public abstract Property<Boolean> getSpring();

  /** Compile-time check configuration. */
  public HKJChecksExtension getChecks() {
    return checks;
  }

  /** Configures compile-time checks. */
  public void checks(Action<? super HKJChecksExtension> action) {
    action.execute(checks);
  }
}
