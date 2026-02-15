// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * Represents the arity of a type constructor witness in the HKT encoding.
 *
 * <p>In type theory terms, this encodes the "kind" of a type:
 *
 * <ul>
 *   <li>{@link Unary} - A type constructor taking one parameter (kind: * → *)
 *   <li>{@link Binary} - A type constructor taking two parameters (kind: * → * → *)
 * </ul>
 *
 * <p>This uses familiar Java terminology:
 *
 * <ul>
 *   <li>"Arity" - as in function arity, how many arguments something takes
 *   <li>"Unary/Binary" - as in unary/binary operators
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <p>Witness types implement {@link WitnessArity} parameterized by one of these arity types to
 * declare their role in the HKT encoding:
 *
 * <pre>{@code
 * // Unary witness for Maybe<A>
 * final class Witness implements WitnessArity<TypeArity.Unary> {
 *     private Witness() {}
 * }
 *
 * // Binary witness for Either<L, R>
 * final class Witness implements WitnessArity<TypeArity.Binary> {
 *     private Witness() {}
 * }
 * }</pre>
 *
 * <h2>Relationship to Kind Interfaces</h2>
 *
 * <ul>
 *   <li>{@link Kind Kind&lt;F, A&gt;} - Used with witnesses of any arity
 *   <li>{@link Kind2 Kind2&lt;F, A, B&gt;} - Used specifically with {@link Binary} witnesses
 * </ul>
 *
 * @see WitnessArity
 * @see Kind
 * @see Kind2
 */
@NullMarked
public sealed interface TypeArity permits TypeArity.Unary, TypeArity.Binary {

  /**
   * Represents a type constructor that takes exactly one type parameter.
   *
   * <p>Examples of unary type constructors:
   *
   * <ul>
   *   <li>{@code List<_>} - A list parameterized by element type
   *   <li>{@code Optional<_>} - An optional parameterized by value type
   *   <li>{@code Maybe<_>} - A maybe parameterized by value type
   *   <li>{@code IO<_>} - An IO action parameterized by result type
   *   <li>{@code Either<L, _>} - Either with left type fixed (partial application)
   * </ul>
   *
   * <p>Kind notation: * → *
   *
   * <p>This arity is used with type classes like {@link Functor}, {@link Applicative}, and {@link
   * Monad}.
   */
  final class Unary implements TypeArity {
    private Unary() {}
  }

  /**
   * Represents a type constructor that takes exactly two type parameters.
   *
   * <p>Examples of binary type constructors:
   *
   * <ul>
   *   <li>{@code Either<_, _>} - Sum type with left and right
   *   <li>{@code Tuple2<_, _>} - Product type (pair)
   *   <li>{@code Function<_, _>} - Function from input to output
   *   <li>{@code Map<_, _>} - Map with key and value types
   * </ul>
   *
   * <p>Kind notation: * → * → *
   *
   * <p>This arity is used with type classes like {@link Bifunctor} and {@link Profunctor}.
   */
  final class Binary implements TypeArity {
    private Binary() {}
  }
}
