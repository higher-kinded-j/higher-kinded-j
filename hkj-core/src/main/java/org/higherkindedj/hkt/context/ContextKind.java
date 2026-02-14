// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * A higher-kinded type marker for the {@link Context} effect type.
 *
 * <p>In the higher-kinded types (HKT) emulation provided by this library, Java's lack of native
 * type constructor polymorphism is addressed using "witness" types or marker interfaces. This
 * interface, {@code ContextKind<R, A>}, along with its nested {@link Witness} class, allows {@link
 * Context} to be treated abstractly in contexts requiring HKTs.
 *
 * <p>A {@code Context<R, A>} represents a computation that reads a value of type {@code R} from a
 * {@link java.lang.ScopedValue ScopedValue<R>} and produces a value of type {@code A}. For HKT
 * purposes, we treat {@code Context<R, ?>} (a {@code Context} with a fixed scoped value type {@code
 * R}) as a type constructor {@code F} that takes one type argument {@code A} (the result type).
 *
 * <p>Specifically, when using {@code ContextKind} in generic HKT abstractions (like {@link
 * org.higherkindedj.hkt.Functor Functor}, {@link org.higherkindedj.hkt.Applicative Applicative},
 * {@link org.higherkindedj.hkt.Monad Monad}):
 *
 * <ul>
 *   <li>The "higher-kinded type witness" (often denoted as {@code F} or {@code Mu}) becomes {@code
 *       ContextKind.Witness<R>}. This represents the {@code Context} type constructor, partially
 *       applied with the scoped value type {@code R}.
 *   <li>The "value type" (often denoted as {@code A}) is {@code A}, representing the result type of
 *       the computation encapsulated by the {@code Context}.
 * </ul>
 *
 * <p>An instance of {@code Kind<ContextKind.Witness<R>, A>} can be converted back to a concrete
 * {@code Context<R, A>} using methods in {@link ContextKindHelper}, primarily {@link
 * ContextKindHelper#narrow(Kind)}.
 *
 * @param <R> The type of the value read from the {@link java.lang.ScopedValue ScopedValue}. This
 *     parameter is captured by the {@link Witness} type.
 * @param <A> The type of the value produced by the {@link Context} computation. This is the type
 *     parameter that varies for the higher-kinded type {@code ContextKind.Witness<R>}.
 * @see Context
 * @see ContextKind.Witness
 * @see ContextKindHelper
 * @see Kind
 * @see org.higherkindedj.hkt.Functor
 * @see org.higherkindedj.hkt.Applicative
 * @see org.higherkindedj.hkt.Monad
 */
public interface ContextKind<R, A> extends Kind<ContextKind.Witness<R>, A> {

  /**
   * The phantom type marker (witness type) for the {@code Context<R, ?>} type constructor. This
   * class is parameterised by {@code TYPE_R} (the scoped value type) and is used as the first type
   * argument to {@link Kind} (i.e., {@code F} in {@code Kind<F, A>}) for {@code Context} instances
   * with a fixed scoped value type.
   *
   * @param <TYPE_R> The type of the scoped value {@code R} associated with this witness.
   */
  final class Witness<TYPE_R> implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
