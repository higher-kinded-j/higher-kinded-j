// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker.testdata;

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Test data file: Path type mismatch in then().
 *
 * <p>The receiver is an EitherPath but the supplier returns a MaybePath. The checker should report
 * an error.
 */
public class MismatchInThen {

  public void mismatchedThen() {
    EitherPath<String, String> either = Path.right("a");
    // Mismatch: EitherPath.then() returns MaybePath
    either.then(() -> Path.just(1));
  }
}
