package org.higherkindedj.hkt.writer;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.typeclass.Monoid;

/**
 * A higher-kinded type marker for the {@link Writer} monad.
 *
 * <p>In the higher-kinded types (HKT) emulation provided by this library, Java's lack of native
 * type constructor polymorphism is addressed using "witness" types or marker interfaces like {@code
 * WriterKind}. This interface allows {@link Writer} to be treated abstractly in contexts requiring
 * HKTs.
 *
 * <p>This interface, {@code WriterKind<W, A>}, serves as a "kinded" version of {@code Writer<W,
 * A>}. It allows {@code Writer} to be treated as a type constructor {@code WriterKind<W, ?>}. This
 * constructor takes one type argument {@code A} (the value type of the Writer's computation) while
 * keeping {@code W} (the "log" or "output" type, which must have a {@link Monoid} instance) fixed
 * for a particular "kinded" instance.
 *
 * <p>Specifically, when using {@code WriterKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" (often denoted as {@code F} or {@code Mu} in HKT
 *       literature) becomes {@code WriterKind<W, ?>}. This represents the {@code Writer} type
 *       constructor, partially applied with the log type {@code W}.
 *   <li>The "value type" (often denoted as {@code A}) is {@code A}, representing the primary result
 *       type of the computation encapsulated by the {@code Writer}.
 * </ul>
 *
 * <p>An instance of {@code Kind<WriterKind<W, ?>, A>} can be converted back to a concrete {@code
 * Writer<W, A>} using the {@link WriterKindHelper#unwrap(Kind)} method. This helper method handles
 * the necessary type casting, often via an internal representation (like a private record
 * implementing this {@code WriterKind} interface).
 *
 * @param <W> The type of the "log" or accumulated output. This type must have an associated {@link
 *     Monoid} instance for the {@code Writer} to function correctly (e.g., for combining logs).
 *     This parameter is part of the "witness type" {@code WriterKind<W, ?>}.
 * @param <A> The type of the primary value produced by the {@link Writer} computation. This is the
 *     type parameter that varies for the higher-kinded type.
 * @see Writer
 * @see WriterKindHelper
 * @see WriterKindHelper#unwrap(Kind)
 * @see org.higherkindedj.hkt.Kind
 * @see org.higherkindedj.hkt.typeclass.Monoid
 */
public interface WriterKind<W, A> extends Kind<WriterKind<W, ?>, A> {
  // This interface is a marker and does not declare additional methods.
  // It signifies that a type is a Kind<F, A> where F is WriterKind<W, ?>
  // (the Writer type constructor fixed with log type W) and A is the result type.
  // The concrete Writer<W,A> type is typically wrapped by an internal class/record
  // within WriterKindHelper that implements this interface.
}
