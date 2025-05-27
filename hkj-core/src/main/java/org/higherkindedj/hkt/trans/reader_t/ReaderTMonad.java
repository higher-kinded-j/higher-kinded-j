// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trans.reader_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Monad} interface for the {@link ReaderT} monad transformer.
 *
 * <p>This class allows {@link ReaderT} to be used as a monad, provided that the outer type
 * constructor {@code F} (for which {@code F} is the witness type) is also a monad. Operations like
 * {@code of}, {@code map}, {@code ap}, and {@code flatMap} are defined to work with {@code
 * ReaderT<F, R_ENV, A>} by appropriately using the monadic operations of {@code F}.
 *
 * <p>The type constructor for which this is a monad instance is {@code ReaderTKind.Witness<F,
 * R_ENV>}, representing {@code ReaderT} with a fixed outer monad witness {@code F} and a fixed
 * environment type {@code R_ENV}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind.Witness}). This outer
 *     type constructor {@code F} must itself be a {@link Monad}.
 * @param <R_ENV> The fixed environment type for this {@code ReaderTMonad} instance.
 * @see ReaderT
 * @see ReaderTKind
 * @see Monad
 * @see Applicative
 * @see ReaderTKindHelper
 */
public class ReaderTMonad<F, R_ENV> implements Monad<ReaderTKind.Witness<F, R_ENV>> {

  private final @NonNull Monad<F> outerMonad;

  /**
   * Constructs a {@link ReaderTMonad} instance.
   *
   * @param outerMonad The {@link Monad} instance for the outer type constructor {@code F}. This is
   *     crucial for defining the monadic operations of {@code ReaderT}. Must not be null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public ReaderTMonad(@NonNull Monad<F> outerMonad) {
    this.outerMonad =
        Objects.requireNonNull(outerMonad, "Outer Monad instance cannot be null for ReaderTMonad");
  }

  /**
   * Lifts a pure value {@code value} into the {@code ReaderT<F, R_ENV, A>} context. The resulting
   * {@code ReaderT} will, for any environment {@code R_ENV}, produce {@code outerMonad.of(value)}.
   *
   * @param value The value to lift. Can be {@code null} if the outer monad {@code F} and type
   *     {@code A} support it.
   * @param <A> The type of the lifted value.
   * @return A {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} representing the new {@code ReaderT}.
   *     Never null.
   */
  @Override
  public <A> @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, A> of(@Nullable A value) {
    ReaderT<F, R_ENV, A> readerT = new ReaderT<>(r -> outerMonad.of(value));
    return ReaderTKindHelper.wrap(readerT);
  }

  /**
   * Applies a function wrapped in {@code ReaderT<F, R_ENV, Function<A, B>>} to a value wrapped in
   * {@code ReaderT<F, R_ENV, A>}.
   *
   * <p>For a given environment {@code r}, this method retrieves {@code Kind<F, Function<A, B>>} and
   * {@code Kind<F, A>}, and then uses the {@code ap} method of the {@code outerMonad} (which must
   * be an {@link Applicative}) to combine them into {@code Kind<F, B>}. This result is then wrapped
   * in a new {@code ReaderT}.
   *
   * @param ff A {@code Kind} representing {@code ReaderT<F, R_ENV, Function<A, B>>}. Must not be
   *     null.
   * @param fa A {@code Kind} representing {@code ReaderT<F, R_ENV, A>}. Must not be null.
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @return A {@code Kind<ReaderTKind.Witness<F, R_ENV>, B>} representing the resulting {@code
   *     ReaderT}. Never null.
   */
  @Override
  public <A, B> @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, B> ap(
      @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, Function<A, B>> ff,
      @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, A> fa) {

    ReaderT<F, R_ENV, Function<A, B>> ffT = ReaderTKindHelper.unwrap(ff);
    ReaderT<F, R_ENV, A> faT = ReaderTKindHelper.unwrap(fa);

    Applicative<F> outerApplicative = this.outerMonad;

    Function<R_ENV, Kind<F, B>> newRun =
        r -> {
          Kind<F, Function<A, B>> funcKind = ffT.run().apply(r);
          Kind<F, A> valKind = faT.run().apply(r);
          return outerApplicative.ap(funcKind, valKind);
        };

    ReaderT<F, R_ENV, B> resultReaderT = new ReaderT<>(newRun);
    return ReaderTKindHelper.wrap(resultReaderT);
  }

  /**
   * Maps a function {@code f} over the value {@code A} within {@code ReaderT<F, R_ENV, A>}.
   *
   * <p>For a given environment {@code r}, this method retrieves {@code Kind<F, A>} and then uses
   * the {@code map} method of the {@code outerMonad} to transform it into {@code Kind<F, B>}. This
   * result is then wrapped in a new {@code ReaderT}.
   *
   * @param f The function to apply to the value. Must not be null.
   * @param fa A {@code Kind} representing {@code ReaderT<F, R_ENV, A>}. Must not be null.
   * @param <A> The original value type.
   * @param <B> The new value type after applying the function {@code f}.
   * @return A {@code Kind<ReaderTKind.Witness<F, R_ENV>, B>} representing the resulting {@code
   *     ReaderT}. Never null.
   */
  @Override
  public <A, B> @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, A> fa) {

    ReaderT<F, R_ENV, A> faT = ReaderTKindHelper.unwrap(fa);

    Function<R_ENV, Kind<F, B>> newRun =
        r -> {
          Kind<F, A> kindA = faT.run().apply(r);
          return outerMonad.map(f, kindA);
        };

    ReaderT<F, R_ENV, B> resultReaderT = new ReaderT<>(newRun);
    return ReaderTKindHelper.wrap(resultReaderT);
  }

  /**
   * Sequentially composes two {@link ReaderT} actions, passing the result of the first into a
   * function that produces the second.
   *
   * <p>For a given environment {@code r}, this method first runs the initial {@code ReaderT}
   * ({@code maT}) to get a {@code Kind<F, A>}. Then, it uses the {@code flatMap} of the {@code
   * outerMonad} to combine this {@code Kind<F, A>} with a function {@code A -> Kind<F, B>}. This
   * function {@code A -> Kind<F, B>} is derived from {@code f: A ->
   * Kind<ReaderTKind.Witness<F,R_ENV>, B>} by unwrapping the inner {@code ReaderT} and applying the
   * environment {@code r} to its run function.
   *
   * @param f A function that takes a value of type {@code A} and returns a {@code
   *     Kind<ReaderTKind.Witness<F, R_ENV>, B>}. Must not be null.
   * @param ma A {@code Kind} representing the initial {@code ReaderT<F, R_ENV, A>}. Must not be
   *     null.
   * @param <A> The value type of the initial {@code ReaderT}.
   * @param <B> The value type of the {@code ReaderT} produced by the function {@code f}.
   * @return A {@code Kind<ReaderTKind.Witness<F, R_ENV>, B>} representing the resulting composed
   *     {@code ReaderT}. Never null.
   */
  @Override
  public <A, B> @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, B> flatMap(
      @NonNull Function<A, Kind<ReaderTKind.Witness<F, R_ENV>, B>> f,
      @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, A> ma) {

    ReaderT<F, R_ENV, A> maT = ReaderTKindHelper.unwrap(ma);

    Function<R_ENV, Kind<F, B>> newRun =
        r -> {
          Kind<F, A> kindA = maT.run().apply(r);

          Function<A, Kind<F, B>> functionForOuterFlatMap =
              a -> {
                Kind<ReaderTKind.Witness<F, R_ENV>, B> resultReaderTKind = f.apply(a);
                ReaderT<F, R_ENV, B> nextReaderT = ReaderTKindHelper.unwrap(resultReaderTKind);
                return nextReaderT.run().apply(r);
              };
          return outerMonad.flatMap(functionForOuterFlatMap, kindA);
        };

    ReaderT<F, R_ENV, B> resultReaderT = new ReaderT<>(newRun);
    return ReaderTKindHelper.wrap(resultReaderT);
  }
}
