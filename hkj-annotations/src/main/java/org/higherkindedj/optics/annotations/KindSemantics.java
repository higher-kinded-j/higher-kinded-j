// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

/**
 * Defines the cardinality semantics for Kind-wrapped types.
 *
 * <p>This enum is used by the Focus DSL annotation processor to determine the appropriate path type
 * when generating code for {@code Kind<F, A>} fields.
 *
 * <h2>Path Type Mapping</h2>
 *
 * <table border="1">
 *   <caption>Semantics to Path Type Mapping</caption>
 *   <tr><th>Semantic</th><th>Generated Path Type</th><th>Examples</th></tr>
 *   <tr><td>{@link #EXACTLY_ONE}</td><td>{@code AffinePath}</td><td>{@code IdKind}</td></tr>
 *   <tr><td>{@link #ZERO_OR_ONE}</td><td>{@code AffinePath}</td><td>{@code MaybeKind}, {@code OptionalKind}, {@code EitherKind}</td></tr>
 *   <tr><td>{@link #ZERO_OR_MORE}</td><td>{@code TraversalPath}</td><td>{@code ListKind}, {@code StreamKind}</td></tr>
 * </table>
 *
 * @see TraverseField
 * @see GenerateFocus
 */
public enum KindSemantics {

  /**
   * The Kind contains exactly one element.
   *
   * <p>This semantic applies to identity-like types where the container always holds exactly one
   * value. The generated path will be an {@code AffinePath} (type-safe narrowing from the
   * underlying TraversalPath).
   *
   * <p>Example: {@code IdKind.Witness}
   */
  EXACTLY_ONE,

  /**
   * The Kind contains zero or one element.
   *
   * <p>This semantic applies to optional-like types where the container may or may not hold a
   * value. The generated path will be an {@code AffinePath}.
   *
   * <p>Examples: {@code MaybeKind.Witness}, {@code OptionalKind.Witness}, {@code
   * EitherKind.Witness<E>}, {@code TryKind.Witness}, {@code ValidatedKind.Witness<E>}
   */
  ZERO_OR_ONE,

  /**
   * The Kind contains zero or more elements.
   *
   * <p>This semantic applies to collection-like types where the container may hold any number of
   * values. The generated path will be a {@code TraversalPath}.
   *
   * <p>Examples: {@code ListKind.Witness}, {@code StreamKind.Witness}
   */
  ZERO_OR_MORE
}
