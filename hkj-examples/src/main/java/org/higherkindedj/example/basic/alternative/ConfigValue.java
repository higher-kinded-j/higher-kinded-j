// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.alternative;

/**
 * Represents a configuration value along with its source.
 *
 * @param value The configuration value
 * @param source The source from which the value was loaded
 */
public record ConfigValue(String value, ConfigSource source) {

  @Override
  public String toString() {
    return String.format("ConfigValue[value='%s', source=%s]", value, source);
  }
}
