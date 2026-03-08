// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker.testdata;

import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Test data file: Path type mismatch in via().
 *
 * <p>The receiver is a MaybePath but the lambda returns an IOPath. The checker should report an
 * error.
 */
public class MismatchInVia {

  public void mismatchedVia() {
    MaybePath<Integer> maybe = Path.just(1);
    // Mismatch: MaybePath.via() returns IOPath
    maybe.via(_ -> Path.io(() -> 2));
  }
}
