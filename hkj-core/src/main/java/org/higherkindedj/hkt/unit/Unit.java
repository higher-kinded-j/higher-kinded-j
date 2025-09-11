// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.unit;

/**
 * Represents a type with a single value, {@link Unit#INSTANCE}. It is used to signify the absence
 * of a specific, meaningful value or the completion of an operation that produces no specific
 * result. This is a non-nullable type, typically used as a singleton.
 */
public enum Unit {
  /** The single, canonical instance of the Unit type. */
  INSTANCE;

  /**
   * Returns the conventional string representation of the Unit type.
   *
   * @return "()"
   */
  @Override
  public String toString() {
    return "()";
  }
}
