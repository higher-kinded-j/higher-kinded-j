// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

/** The categories of check a type-class contract can exercise. */
public enum Category {
  /** Basic operation smoke tests (e.g. {@code map} returns non-null). */
  OPERATIONS,
  /** Null-argument rejection. */
  VALIDATIONS,
  /** Exception propagation from user functions. */
  EXCEPTIONS,
  /** Algebraic laws (delegated to {@code hkj-test} law helpers). */
  LAWS
}
