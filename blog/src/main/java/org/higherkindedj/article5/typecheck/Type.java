// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.typecheck;

/**
 * The types available in our expression language.
 *
 * <p>This is a simple type system with three primitive types:
 *
 * <ul>
 *   <li>{@link #INT} - integer values
 *   <li>{@link #BOOL} - boolean values
 *   <li>{@link #STRING} - string values
 * </ul>
 */
public enum Type {
  /** Integer type. */
  INT,

  /** Boolean type. */
  BOOL,

  /** String type. */
  STRING
}
