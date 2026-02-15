// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.alternative;

/** Represents different sources from which configuration can be loaded. */
public enum ConfigSource {
  ENVIRONMENT_VARIABLE,
  SYSTEM_PROPERTY,
  CONFIG_FILE,
  DATABASE,
  DEFAULT_VALUE
}
