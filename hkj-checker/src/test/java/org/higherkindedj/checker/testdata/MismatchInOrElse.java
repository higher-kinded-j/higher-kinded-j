// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker.testdata;

import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Test data file: Path type mismatch in orElse().
 *
 * <p>The receiver is an EitherPath but the supplier returns a ValidationPath. Both share the same
 * error type parameter, so this compiles but is a semantic mismatch the checker should detect.
 */
public class MismatchInOrElse {

  private static final Semigroup<String> CONCAT = (a, b) -> a + b;

  public void mismatchedOrElse() {
    EitherPath<String, Integer> either = Path.right(1);
    // Mismatch: EitherPath.orElse() returns ValidationPath
    either.orElse(() -> Path.valid(2, CONCAT));
  }
}
