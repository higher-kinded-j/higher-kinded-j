// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import org.gradle.api.provider.Property;

/** Configuration for HKJ compile-time checks. */
public abstract class HKJChecksExtension {

  /** Creates a new checks extension instance. */
  public HKJChecksExtension() {}

  /**
   * Adds the HKJ compile-time checker to the build. Its checks explain a rejected chain step and
   * report mistakes javac accepts. Defaults to true.
   *
   * @return the property that adds the checker
   */
  public abstract Property<Boolean> getPathTypeMismatch();
}
