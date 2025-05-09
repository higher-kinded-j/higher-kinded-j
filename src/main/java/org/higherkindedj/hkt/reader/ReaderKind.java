package org.higherkindedj.hkt.reader;

import org.higherkindedj.hkt.Kind;

/**
 * A higher-kinded type marker for the {@link Reader} monad.
 *
 * <p>In the higher-kinded types (HKT) emulation provided by this library, Java's lack of native
 * type constructor polymorphism is addressed using "witness" types or marker interfaces like {@code
 * ReaderKind}. This interface allows {@link Reader} to be treated abstractly in contexts requiring
 * HKTs.
 *
 * <p>This interface, {@code ReaderKind<R, A>}, serves as a "kinded" version of {@code Reader<R,
 * A>}. It allows {@code Reader} to be treated as a type constructor {@code ReaderKind<R, ?>}. This
 * constructor takes one type argument {@code A} (the value type of the Reader's computation) while
 * keeping {@code R} (the environment type) fixed for a particular "kinded" instance.
 *
 * <p>Specifically, when using {@code ReaderKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" (often denoted as {@code F} or {@code Mu} in HKT
 *       literature) becomes {@code ReaderKind<R, ?>}. This represents the {@code Reader} type
 *       constructor, partially applied with the environment type {@code R}.
 *   <li>The "value type" (often denoted as {@code A}) is {@code A}, representing the result type of
 *       the computation encapsulated by the {@code Reader}.
 * </ul>
 *
 * <p>An instance of {@code Kind<ReaderKind<R, ?>, A>} can be converted back to a concrete {@code
 * Reader<R, A>} using the {@link ReaderKindHelper#unwrap(Kind)} method. This helper method handles
 * the necessary type casting, often via an internal representation (like a private record
 * implementing this {@code ReaderKind} interface).
 *
 * <p>The {@link Reader} monad represents a computation that depends on an environment of type
 * {@code R} to produce a value of type {@code A}. It is useful for dependency injection and
 * managing shared context or configuration.
 *
 * @param <R> The type of the environment that the {@link Reader} computation will access. This
 *     parameter is part of the "witness type" {@code ReaderKind<R, ?>}.
 * @param <A> The type of the value produced by the {@link Reader} computation. This is the type
 *     parameter that varies for the higher-kinded type.
 * @see Reader
 * @see ReaderKindHelper
 * @see ReaderKindHelper#unwrap(Kind)
 * @see org.higherkindedj.hkt.Kind
 */
public interface ReaderKind<R, A> extends Kind<ReaderKind<R, ?>, A> {
  // This interface is a marker and does not declare additional methods.
  // It signifies that a type is a Kind<F, A> where F is ReaderKind<R, ?>
  // (the Reader type constructor fixed with environment type R) and A is the result type.
  // The concrete Reader<R,A> type is typically wrapped by an internal class/record
  // within ReaderKindHelper that implements this interface.
}
