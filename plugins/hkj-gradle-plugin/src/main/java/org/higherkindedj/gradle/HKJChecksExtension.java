// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import org.gradle.api.provider.Property;

/** Configuration for HKJ compile-time checks. */
public abstract class HKJChecksExtension {

  /** Enable Path type mismatch detection. Defaults to true. */
  public abstract Property<Boolean> getPathTypeMismatch();
}
