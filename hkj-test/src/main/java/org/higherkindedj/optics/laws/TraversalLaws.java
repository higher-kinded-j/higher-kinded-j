// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * Law-verification helpers for {@link Traversal}: identity and fusion (composition).
 *
 * <p>Flat {@code assert...} helpers in the same style as {@code org.higherkindedj.hkt.laws};
 * comparison is by {@code equals} — right for records and standard collections.
 */
public final class TraversalLaws {

  private TraversalLaws() {}

  /** Identity: {@code modify(identity, s) == s}. */
  public static <S, A> void assertIdentity(Traversal<S, A> traversal, S s) {
    S result = Traversals.modify(traversal, Function.identity(), s);
    assertThat(result)
        .as("Traversal identity: modify(identity, %s) changes nothing; got %s", s, result)
        .isEqualTo(s);
  }

  /** Fusion: {@code modify(g, modify(f, s)) == modify(g compose f, s)}. */
  public static <S, A> void assertFusion(
      Traversal<S, A> traversal, S s, Function<A, A> f, Function<A, A> g) {
    S sequential = Traversals.modify(traversal, g, Traversals.modify(traversal, f, s));
    S fused = Traversals.modify(traversal, f.andThen(g), s);
    assertThat(sequential)
        .as(
            "Traversal fusion: modify(g) after modify(f) == modify(g.compose(f)) for %s; got %s vs %s",
            s, sequential, fused)
        .isEqualTo(fused);
  }

  /** All traversal laws for one fixture. */
  public static <S, A> void assertTraversalLaws(
      Traversal<S, A> traversal, S s, Function<A, A> f, Function<A, A> g) {
    assertIdentity(traversal, s);
    assertFusion(traversal, s, f, g);
  }
}
