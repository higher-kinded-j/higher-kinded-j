// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import org.higherkindedj.hkt.Kind;

/**
 * A higher-kinded type marker for the {@link Reader} monad.
 *
 * <p>In the higher-kinded types (HKT) emulation provided by this library, Java's lack of native
 * type constructor polymorphism is addressed using "witness" types or marker interfaces. This
 * interface, {@code ReaderKind<R, A>}, along with its nested {@link Witness} class, allows {@link
 * Reader} to be treated abstractly in contexts requiring HKTs.
 *
 * <p>A {@code Reader<R, A>} represents a computation that depends on an environment of type {@code
 * R} to produce a value of type {@code A}. For HKT purposes, we want to treat {@code Reader<R, ? >}
 * (a {@code Reader} with a fixed environment type {@code R}) as a type constructor {@code F} that
 * takes one type argument {@code A} (the value type).
 *
 * <p>Specifically, when using {@code ReaderKind} in generic HKT abstractions (like {@link
 * org.higherkindedj.hkt.Functor Functor}, {@link org.higherkindedj.hkt.Applicative Applicative},
 * {@link org.higherkindedj.hkt.Monad Monad}):
 *
 * <ul>
 *   <li>The "higher-kinded type witness" (often denoted as {@code F} or {@code Mu}) becomes {@code
 *       ReaderKind.Witness<R>}. This represents the {@code Reader} type constructor, partially
 *       applied with the environment type {@code R}.
 *   <li>The "value type" (often denoted as {@code A}) is {@code A}, representing the result type of
 *       the computation encapsulated by the {@code Reader}.
 * </ul>
 *
 * <p>An instance of {@code Kind<ReaderKind.Witness<R>, A>} can be converted back to a concrete
 * {@code Reader<R, A>} using methods in {@link ReaderKindHelper}, primarily {@link
 * ReaderKindHelper#unwrap(Kind)}.
 *
 * @param <R> The type of the environment that the {@link Reader} computation will access. This
 *     parameter is captured by the {@link Witness} type.
 * @param <A> The type of the value produced by the {@link Reader} computation. This is the type
 *     parameter that varies for the higher-kinded type {@code ReaderKind.Witness<R>}.
 * @see Reader
 * @see ReaderKind.Witness
 * @see ReaderKindHelper
 * @see Kind
 * @see org.higherkindedj.hkt.Functor
 * @see org.higherkindedj.hkt.Applicative
 * @see org.higherkindedj.hkt.Monad
 */
public interface ReaderKind<R, A> extends Kind<ReaderKind.Witness<R>, A> {

  /**
   * The phantom type marker (witness type) for the {@code Reader<R, ?>} type constructor. This
   * class is parameterized by {@code TYPE_R} (the environment type) and is used as the first type
   * argument to {@link Kind} (i.e., {@code F} in {@code Kind<F, A>}) for {@code Reader} instances
   * with a fixed environment type.
   *
   * @param <TYPE_R> The type of the environment {@code R} associated with this witness.
   */
  final class Witness<TYPE_R> {
    private Witness() { // Private constructor to prevent instantiation.
    }
  }
}
