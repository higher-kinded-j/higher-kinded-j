// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;

/**
 * A deferred-union tree of pending request keys.
 *
 * <p>The batching applicative combines the pending keys of two independent fetches on every {@code
 * ap}. Eagerly merging them into a {@code Set} each time is O(n²) across a wide traversal, because
 * each merge copies the growing accumulator. This structure instead records a union as a single
 * {@link Union} node (an O(1) operation regardless of operand size) and is flattened to an
 * insertion-ordered, de-duplicated set exactly once, when a round is run.
 *
 * <p>It is the binary generalisation of a cons-list (cf. {@code org.higherkindedj.hkt.util.FList}):
 * a cons-list gives O(1) prepend of one element; this gives O(1) merge of two trees.
 *
 * @param <K> the request key type
 */
sealed interface PendingKeys<K> permits PendingKeys.One, PendingKeys.Union {

  /** A single pending key. */
  record One<K>(K key) implements PendingKeys<K> {}

  /** The union of two pending-key trees. Construction is O(1). */
  record Union<K>(PendingKeys<K> left, PendingKeys<K> right) implements PendingKeys<K> {}

  /** A tree holding exactly one key. */
  static <K> PendingKeys<K> one(K key) {
    return new One<>(key);
  }

  /** Merges two trees in O(1); the keys are flattened lazily by {@link #flatten()}. */
  static <K> PendingKeys<K> merge(PendingKeys<K> left, PendingKeys<K> right) {
    return new Union<>(left, right);
  }

  /**
   * Flattens this tree into an insertion-ordered, de-duplicated set. Uses an explicit work stack,
   * so it is stack-safe regardless of tree depth.
   */
  default LinkedHashSet<K> flatten() {
    LinkedHashSet<K> out = new LinkedHashSet<>();
    Deque<PendingKeys<K>> stack = new ArrayDeque<>();
    stack.push(this);
    while (!stack.isEmpty()) {
      switch (stack.pop()) {
        case One<K> one -> out.add(one.key());
        case Union<K> union -> {
          stack.push(union.right());
          stack.push(union.left());
        }
      }
    }
    return out;
  }
}
