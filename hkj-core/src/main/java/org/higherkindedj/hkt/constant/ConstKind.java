// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface marker for partially-applied {@link Const Const&lt;M, ?&gt;} in Higher-Kinded-J.
 *
 * <p>This interface represents {@link Const} with its first type parameter ({@code M}) fixed,
 * leaving only the second (phantom) type parameter ({@code A}) free. This partial application is
 * necessary for using {@code Const} with type classes like {@link
 * org.higherkindedj.hkt.Applicative} that expect a type constructor of kind {@code * -> *}.
 *
 * <p>For example, {@code ConstKind<Integer>} represents the type constructor {@code Const<Integer,
 * ?>}, which can be used with {@code Applicative<ConstKind<Integer>.Witness>}.
 *
 * @param <M> The fixed constant value type
 * @param <A> The free phantom type parameter
 * @see Const
 * @see ConstApplicative
 */
public interface ConstKind<M, A> extends Kind<ConstKind.Witness<M>, A> {

  /**
   * The phantom type marker (witness type) for the partially-applied {@code Const<M, ?>} type
   * constructor.
   *
   * <p>This witness type is parameterized by {@code M} to represent different partial applications
   * of {@code Const}. For example:
   *
   * <ul>
   *   <li>{@code Witness<Integer>} represents {@code Const<Integer, ?>}
   *   <li>{@code Witness<String>} represents {@code Const<String, ?>}
   * </ul>
   *
   * @param <M> The fixed constant value type
   */
  final class Witness<M> implements WitnessArity<TypeArity.Unary> {
    private Witness() {} // Private constructor to prevent instantiation.
  }
}
