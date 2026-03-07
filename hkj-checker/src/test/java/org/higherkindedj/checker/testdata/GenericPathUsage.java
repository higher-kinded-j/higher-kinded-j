// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker.testdata;

import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.capability.Chainable;

/**
 * Test data file: generic Path usage that should not trigger false positives.
 *
 * <p>This file exercises various patterns where the checker should remain silent, including
 * same-type chaining and generic/interface-typed receivers.
 */
public class GenericPathUsage {

  public void sameTypeChaining() {
    // Same-type via chaining is correct
    Path.just(1).via(_ -> Path.just(2));
  }

  public void sameTypeZipWith() {
    // Same-type zipWith is correct
    Path.just(1).zipWith(Path.just(2), Integer::sum);
  }

  public void sameTypeThen() {
    // Same-type then is correct
    Path.just(1).then(() -> Path.just(2));
  }

  public <A> void genericReceiver(Chainable<A> path) {
    // Generic receiver: checker cannot resolve type, should skip silently
    path.via(_ -> Path.just(1));
  }

  public void multipleCorrectChains() {
    // Multiple operations chained correctly
    MaybePath<String> result =
        Path.just(1).via(_ -> Path.just(2)).map(String::valueOf).via(_ -> Path.just("hello"));
  }
}
