// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker.testdata;

import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Test data file: Path type mismatch in recoverWith().
 *
 * <p>The receiver is a MaybePath but the recovery function returns a TryPath. The checker should
 * report an error.
 */
public class MismatchInRecoverWith {

  public void mismatchedRecoverWith() {
    MaybePath<Integer> maybe = Path.just(1);
    // Mismatch: MaybePath.recoverWith() returns TryPath
    maybe.recoverWith(_ -> Path.success(42));
  }
}
