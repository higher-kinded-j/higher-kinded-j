// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker.testdata;

import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Test data file: Path type mismatch in zipWith().
 *
 * <p>The receiver is a MaybePath but the first argument is an EitherPath. The checker should report
 * an error.
 */
public class MismatchInZipWith {

  public void mismatchedZipWith() {
    MaybePath<Integer> maybe = Path.just(1);
    // Mismatch: MaybePath.zipWith() with EitherPath argument
    maybe.zipWith(Path.right("x"), (a, b) -> a + b.length());
  }
}
