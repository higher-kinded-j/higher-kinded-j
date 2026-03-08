// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker.testdata;

import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Test data file: Path type mismatch in recoverWith().
 *
 * <p>The receiver is an EitherPath but the recovery function returns a ValidationPath. Both share
 * the same error type parameter, so this compiles but is a semantic mismatch the checker should
 * detect.
 */
public class MismatchInRecoverWith {

  private static final Semigroup<String> CONCAT = (a, b) -> a + b;

  public void mismatchedRecoverWith() {
    EitherPath<String, Integer> either = Path.right(1);
    // Mismatch: EitherPath.recoverWith() returns ValidationPath
    either.recoverWith(_ -> Path.valid(42, CONCAT));
  }
}
