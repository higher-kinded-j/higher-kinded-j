// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A lightweight, immutable, singly-linked list used internally for O(1) prepend accumulation in
 * traverse operations.
 *
 * <p>This structure supports safe accumulation with multi-branch applicatives (e.g., List
 * applicative) where a shared accumulator must not be mutated. Each {@link Cons} node is immutable,
 * so branches created by multi-branch {@code map2} calls share structure without interference.
 *
 * <p>Elements are prepended in O(1) via {@link #cons}, then converted to a correctly-ordered {@link
 * List} or {@link Stream} in O(n) via {@link #toList} or {@link #toStream}.
 *
 * @param <T> the element type
 */
public sealed interface FList<T> {

  /** The empty list. */
  record Nil<T>() implements FList<T> {}

  /**
   * A cons cell holding a head element and a tail.
   *
   * @param head the element
   * @param tail the rest of the list
   */
  record Cons<T>(T head, FList<T> tail) implements FList<T> {}

  /**
   * Prepends an element to this list in O(1).
   *
   * @param value the element to prepend
   * @return a new list with the element at the front
   */
  default FList<T> cons(T value) {
    return new Cons<>(value, this);
  }

  /**
   * Converts this list to a {@link java.util.List} in correct (reversed) order.
   *
   * <p>Since elements are prepended during accumulation, the internal order is reversed. This
   * method iteratively collects elements and reverses them, producing the correct output order in
   * O(n).
   *
   * @return an unmodifiable list with elements in the original insertion order
   */
  default List<T> toList() {
    ArrayList<T> result = new ArrayList<>();
    FList<T> current = this;
    while (current instanceof Cons<T>(T head, FList<T> tail)) {
      result.add(head);
      current = tail;
    }
    Collections.reverse(result);
    return Collections.unmodifiableList(result);
  }

  /**
   * Converts this list to a {@link Stream} in correct (reversed) order.
   *
   * <p>Delegates to {@link #toList()} and streams the result.
   *
   * @return a stream of elements in the original insertion order
   */
  default Stream<T> toStream() {
    return toList().stream();
  }
}
