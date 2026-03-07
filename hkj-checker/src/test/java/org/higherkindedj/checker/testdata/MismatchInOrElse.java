// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker.testdata;

import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Test data file: Path type mismatch in orElse().
 *
 * <p>The receiver is a MaybePath but the supplier returns a TryPath. The checker should report an
 * error.
 */
public class MismatchInOrElse {

  public void mismatchedOrElse() {
    MaybePath<Integer> maybe = Path.just(1);
    // Mismatch: MaybePath.orElse() returns TryPath
    maybe.orElse(() -> Path.success(2));
  }
}
