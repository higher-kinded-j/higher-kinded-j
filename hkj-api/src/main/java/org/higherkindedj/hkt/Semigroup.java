// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NonNull;
/**
 *   <ol>
 *     <li>A Semigroup: Provides only one operation: combine. It's for things that can be combined.</li>
 *     <li> A Monoid: Provides combine and empty. It's for things that can be combined and also have a "zero" or "identity" value.</li>
 *   </ol>
 * <p>By only having Monoid, you force every operation that needs to combine things to also require an empty value, even when it makes no sense.
 * This comes to the front in Validated. To accumulate errors we only need a way to combine. We don't need an empty error.
 * There are many useful types that are Semigroups but cannot be Monoids.
 * <p>A classic example is a non-empty list. You can always combine two non-empty lists to get a new, larger non-empty list. However, there is no "empty" non-empty list, so it can't be a Monoid.
 * Represents the Monoid type class, a fundamental concept in abstract algebra and functional
 * programming. A Monoid defines a structure for a specific type {@code A} that allows elements of
 * that type to be combined, and it includes a special "identity" element for this combination.
 **/
public interface Semigroup<A> {

  /**
   * Combines two elements of type {@code A} into a single element of type {@code A}.
   *
   * <p>This operation must be associative: {@code combine(x, combine(y, z)) == combine(combine(x,
   * y), z)} for all {@code x, y, z} of type {@code A}.
   *
   * <p>Associativity allows for flexible grouping of operations, which is crucial for tasks like
   * parallel processing or efficient folding of data structures.
   *
   * @param x The first non-null element of type {@code A}.
   * @param y The second non-null element of type {@code A}.
   * @return The non-null result of combining {@code x} and {@code y}, also of type {@code A}. The
   *     non-null annotation implies that combining two non-null values should typically result in a
   *     non-null value, fitting most common Monoid definitions.
   */
  @NonNull A combine(@NonNull A x, @NonNull A y);
}
