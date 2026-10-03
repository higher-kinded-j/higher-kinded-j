// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.pathsource;

import java.util.List;
import org.higherkindedj.hkt.effect.annotation.PathSource;

/**
 * A value together with the steps that produced it: the effect a library might ship, for which the
 * book's Custom Paths page generates {@code TracedPath}.
 */
// ANCHOR: traced
@PathSource(witness = TracedKind.Witness.class)
public record Traced<A>(A value, List<String> log) implements TracedKind<A> {

  public Traced {
    log = List.copyOf(log);
  }

  /** A value produced by one step. */
  public static <A> Traced<A> of(A value, String step) {
    return new Traced<>(value, List.of(step));
  }
}
// ANCHOR_END: traced
