package org.higherkindedj.hkt.writer;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;

/**
 * A higher-kinded type marker for the {@link Writer} monad.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link Writer} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs). A {@code Writer<W, A>}
 * produces a value {@code A} alongside an accumulated log of type {@code W}.
 *
 * <p>For HKT purposes, {@code Writer<W, ?>} (a {@code Writer} with a fixed log type {@code W}) is
 * treated as a type constructor {@code F} that takes one type argument {@code A} (the value type).
 * The {@link Monoid} for {@code W} is essential for combining logs.
 *
 * <p>Specifically, when using {@code WriterKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" ({@code F} in {@code Kind<F, A>}) becomes {@code
 *       WriterKind.Witness<W>}. This represents the {@code Writer} type constructor, partially
 *       applied with the log type {@code W}.
 *   <li>The "value type" ({@code A} in {@code Kind<F, A>}) is {@code A}, representing the primary
 *       result of the {@link Writer} computation.
 * </ul>
 *
 * <p>Instances of {@code Kind<WriterKind.Witness<W>, A>} can be converted to/from concrete {@code
 * Writer<W, A>} instances using {@link WriterKindHelper}.
 *
 * @param <W> The type of the log or accumulated output. This type must have an associated {@link
 *     Monoid} instance. This parameter is captured by the {@link Witness} type.
 * @param <A> The type of the primary value produced by the {@link Writer} computation. This is the
 *     type parameter that varies for the higher-kinded type {@code WriterKind.Witness<W>}.
 * @see Writer
 * @see WriterKind.Witness
 * @see WriterKindHelper
 * @see Kind
 * @see Monoid
 */
public interface WriterKind<W, A> extends Kind<WriterKind.Witness<W>, A> {

  /**
   * The phantom type marker (witness type) for the {@code Writer<W, ?>} type constructor. This
   * class is parameterized by {@code TYPE_W} (the log type) and is used as the first type argument
   * to {@link Kind} (i.e., {@code F} in {@code Kind<F, A>}) for {@code Writer} instances with a
   * fixed log type.
   *
   * @param <TYPE_W> The type of the log {@code W} associated with this witness.
   */
  final class Witness<TYPE_W> {
    private Witness() { // Private constructor to prevent instantiation.
    }
  }
}
