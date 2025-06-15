// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Optional;
import java.util.function.Function;

/**
 * A Prism focuses on a single case 'A' of a sum type 'S' (like a sealed interface or enum). It
 * provides a way to optionally get the value if it's the right case, and a way to construct the
 * structure 'S' from a value 'A'.
 *
 * @param <S> The type of the sum type (e.g., DomainError).
 * @param <A> The type of the focused case (e.g., DomainError.InventoryError).
 */
public interface Prism<S, A> {

  /** Optionally gets the part 'A' from the structure 'S' if it matches the focus. */
  Optional<A> getOptional(S source);

  /**
   * Constructs the structure 'S' from a value of the focused part 'A'. Also known as 'reverseGet'
   * or 'review'.
   */
  S build(A value);

  /**
   * Static factory method to create a Prism from a partial getter and a builder function.
   *
   * @param getter A function that attempts to get the part, returning an Optional.
   * @param builder A function that builds the whole from the part.
   * @return A new Prism instance.
   */
  static <S, A> Prism<S, A> of(Function<S, Optional<A>> getter, Function<A, S> builder) {
    return new Prism<>() {
      @Override
      public Optional<A> getOptional(S source) {
        return getter.apply(source);
      }

      @Override
      public S build(A value) {
        return builder.apply(value);
      }
    };
  }
}
