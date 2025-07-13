// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.configaudit;

import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Represents a single key-value pair in the application configuration. Lenses will be generated for
 * its properties.
 */
@GenerateLenses
public record Setting(String key, SettingValue value) {}
