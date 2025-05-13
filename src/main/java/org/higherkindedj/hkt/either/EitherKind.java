package org.higherkindedj.hkt.either;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the {@link Either Either&lt;L, R&gt;} type in Higher-Kinded-J.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link Either} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs). An {@code Either<L, R>}
 * represents a value that is either a {@code Left<L>} or a {@code Right<R>}.
 *
 * <p>For HKT purposes, {@code Either<L, ?>} (an {@code Either} with a fixed "Left" type {@code L})
 * is treated as a type constructor {@code F} that takes one type argument {@code R} (the "Right"
 * type). This structure facilitates defining typeclass instances (like {@link
 * org.higherkindedj.hkt.Functor Functor}, {@link org.higherkindedj.hkt.Monad Monad}) that are
 * right-biased.
 *
 * <p>Specifically, when using {@code EitherKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" ({@code F} in {@code Kind<F, A>}) becomes {@code
 *       EitherKind.Witness<L>}. This represents the {@code Either} type constructor, partially
 *       applied with the "Left" type {@code L}.
 *   <li>The "value type" ({@code A} in {@code Kind<F, A>}) corresponds to {@code R}, the "Right"
 *       type.
 * </ul>
 *
 * <p>Instances of {@code Kind<EitherKind.Witness<L>, R>} can be converted to/from concrete {@code
 * Either<L, R>} instances using {@link EitherKindHelper}.
 *
 * @param <L> The type of the "Left" value. This parameter is captured by the {@link Witness} type
 *     for HKT representation.
 * @param <R> The type of the "Right" value. This is the type parameter that varies for the
 *     higher-kinded type {@code EitherKind.Witness<L>}.
 * @see Either
 * @see Either.Left
 * @see Either.Right
 * @see EitherKind.Witness
 * @see EitherKindHelper
 * @see Kind
 */
public interface EitherKind<L, R> extends Kind<EitherKind.Witness<L>, R> {

  /**
   * The phantom type marker (witness type) for the {@code Either<L, ?>} type constructor. This
   * class is parameterized by {@code TYPE_L} (the "Left" type) and is used as the first type
   * argument to {@link Kind} (i.e., {@code F} in {@code Kind<F, A>}) for {@code Either} instances
   * with a fixed "Left" type.
   *
   * @param <TYPE_L> The type of the "Left" value {@code L} associated with this witness.
   */
  final class Witness<TYPE_L> {
    private Witness() { // Private constructor to prevent instantiation.
    }
  }
}
