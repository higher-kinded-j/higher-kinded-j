// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Instruction set for optic operations in the Free monad DSL.
 *
 * <p>Each sealed subtype represents a single optic operation that can be composed into larger
 * programs using the Free monad. Operations are described as data and can be interpreted in
 * different ways.
 *
 * <p>This enables:
 *
 * <ul>
 *   <li>Building complex optic workflows as data structures
 *   <li>Multiple interpreters (direct execution, logging, validation, optimisation)
 *   <li>Program inspection and transformation before execution
 *   <li>Transactional semantics (validate then commit)
 * </ul>
 *
 * @param <S> The source structure type
 * @param <A> The result type of the operation
 */
@NullMarked
public sealed interface OpticOp<S extends @Nullable Object, A> extends OpticOpKind<A> {

  /**
   * Get operation - reads a single value through a Getter or Lens.
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record Get<S extends @Nullable Object, A>(S source, Getter<S, A> optic)
      implements OpticOp<S, A> {}

  /**
   * Preview operation - reads an optional value through a Fold (first element).
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record Preview<S extends @Nullable Object, A extends @Nullable Object>(S source, Fold<S, A> optic)
      implements OpticOp<S, Optional<@NonNull A>> {}

  /**
   * GetAll operation - reads all values through a Fold or Traversal.
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record GetAll<S extends @Nullable Object, A extends @Nullable Object>(S source, Fold<S, A> optic)
      implements OpticOp<S, List<A>> {}

  /**
   * Set operation - writes a value through a Lens.
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record Set<S, A extends @Nullable Object>(S source, Lens<S, A> optic, A newValue)
      implements OpticOp<S, S> {}

  /**
   * SetAll operation - writes the same value to all focuses of a Traversal.
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record SetAll<S, A extends @Nullable Object>(S source, Traversal<S, A> optic, A newValue)
      implements OpticOp<S, S> {}

  /**
   * Modify operation - transforms a value through a Lens.
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record Modify<S, A extends @Nullable Object>(S source, Lens<S, A> optic, Function<A, A> modifier)
      implements OpticOp<S, S> {}

  /**
   * ModifyAll operation - transforms all values through a Traversal.
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record ModifyAll<S, A extends @Nullable Object>(
      S source, Traversal<S, A> optic, Function<A, A> modifier) implements OpticOp<S, S> {}

  /**
   * Query operation - checks if any focused element matches a predicate.
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record Exists<S extends @Nullable Object, A extends @Nullable Object>(
      S source, Fold<S, A> optic, Predicate<A> predicate) implements OpticOp<S, Boolean> {}

  /**
   * Query operation - checks if all focused elements match a predicate.
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record All<S extends @Nullable Object, A extends @Nullable Object>(
      S source, Fold<S, A> optic, Predicate<A> predicate) implements OpticOp<S, Boolean> {}

  /**
   * Query operation - counts the number of focused elements.
   *
   * @param <S> The source type
   * @param <A> The value type
   */
  record Count<S extends @Nullable Object, A extends @Nullable Object>(S source, Fold<S, A> optic)
      implements OpticOp<S, Integer> {}
}
