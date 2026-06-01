// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

/**
 * A single named contract check.
 *
 * <p>Checks are plain values produced by each type-class contract and run by the shared {@link
 * ContractEngine}. Modelling a check as data (rather than a hard-coded method on a per-family
 * executor) is what lets one engine serve every algebra.
 */
record Check(String name, Category category, ThrowingBody body) {

  /** A check body that may throw (an {@link AssertionError} on failure). */
  @FunctionalInterface
  interface ThrowingBody {
    void run() throws Throwable;
  }
}
