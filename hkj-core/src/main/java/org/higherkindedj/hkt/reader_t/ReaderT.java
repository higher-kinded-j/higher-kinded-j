// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Represents the Reader Transformer Monad, {@code ReaderT<F, R, A>}. It wraps a computation that
 * depends on a read-only environment of type {@code R} to produce a monadic value {@code Kind<F,
 * A>}, where {@code F} is the witness type of an outer monad.
 *
 * <p>This class is implemented as an immutable record that holds the core function {@code R ->
 * Kind<F, A>}. It allows composing operations that require reading from an environment with other
 * monadic effects.
 *
 * <p>It implements {@link ReaderTKind ReaderTKind&lt;F, R, A&gt;}, which allows {@code ReaderT} to
 * be treated as {@code Kind<ReaderTKind.Witness<F, R>, A>}. The type constructor {@code
 * ReaderTKind.Witness<F, R>} serves as the witness for a ReaderT with a fixed outer monad witness
 * {@code F} and a fixed environment type {@code R}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind.Witness}, {@code
 *     IOKind.Witness}). This represents the type constructor of the underlying monad.
 * @param <R_ENV> The type of the read-only environment.
 * @param <A> The type of the value produced within the outer monad {@code F}.
 * @param run The core function of this {@code ReaderT}, which takes an environment of type {@code
 *     R_ENV} and returns a monadic value {@code Kind<F, A>}. Must not be null.
 * @see ReaderTKind
 * @see ReaderTMonad
 * @see ReaderTKindHelper
 * @see Monad
 * @see Kind
 */
public record ReaderT<F extends WitnessArity<TypeArity.Unary>, R_ENV, A>(
    Function<R_ENV, Kind<F, A>> run) implements ReaderTKind<F, R_ENV, A> {

  private static final Class<ReaderT> READER_T_CLASS = ReaderT.class;

  /**
   * Constructs a new {@link ReaderT}. The {@code run} function is the core of the {@code ReaderT},
   * defining how to produce a monadic value {@code Kind<F, A>} from an environment {@code R_ENV}.
   *
   * @param run The function {@code R_ENV -> Kind<F, A>}. Must not be null.
   * @throws NullPointerException if {@code run} is null.
   */
  public ReaderT {
    Validation.function().requireFunction(run, "run", READER_T_CLASS, CONSTRUCTION);
  }

  /**
   * Factory method to create a {@link ReaderT} instance from a given run function.
   *
   * @param <F> The witness type of the outer monad.
   * @param <R_ENV> The type of the environment.
   * @param <A> The type of the value.
   * @param runFunction The function {@code R_ENV -> Kind<F, A>} that defines the {@code ReaderT}.
   *     Must not be null.
   * @return A new {@link ReaderT} instance. Never null.
   * @throws NullPointerException if {@code runFunction} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, R_ENV, A> ReaderT<F, R_ENV, A> of(
      Function<R_ENV, Kind<F, A>> runFunction) {
    return new ReaderT<>(runFunction);
  }

  /**
   * Lifts a monadic value {@code Kind<F, A>} into the {@link ReaderT} context. The resulting {@code
   * ReaderT} will produce the given monadic value {@code fa} regardless of the environment {@code
   * R_ENV}.
   *
   * @param <F> The witness type of the outer monad.
   * @param <R_ENV> The type of the environment (it will be ignored).
   * @param <A> The type of the value within the outer monad.
   * @param outerMonad An instance of {@link Monad} for the outer type {@code F}. Must not be null.
   * @param fa The monadic value {@code Kind<F, A>} to lift. Must not be null.
   * @return A new {@link ReaderT} that wraps {@code fa}. Never null.
   * @throws NullPointerException if {@code outerMonad} or {@code fa} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, R_ENV, A> ReaderT<F, R_ENV, A> liftF(
      Monad<F> outerMonad, Kind<F, A> fa) {
    Validation.transformer().requireOuterMonad(outerMonad, READER_T_CLASS, LIFT_F);
    Validation.kind().requireNonNull(fa, READER_T_CLASS, LIFT_F, "source Kind");
    return new ReaderT<>(r -> fa);
  }

  /**
   * Creates a {@link ReaderT} from a function {@code R_ENV -> A}, lifting the result {@code A} into
   * the outer monad {@code F} using the provided {@code outerMonad}'s {@code of} method.
   *
   * @param <F> The witness type of the outer monad.
   * @param <R_ENV> The type of the environment.
   * @param <A> The type of the value produced by the function {@code f} and lifted into {@code F}.
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param f The function {@code R_ENV -> A} which computes a value from the environment. Must not
   *     be null.
   * @return A new {@link ReaderT} instance. Never null.
   * @throws NullPointerException if {@code outerMonad} or {@code f} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, R_ENV, A> ReaderT<F, R_ENV, A> reader(
      Monad<F> outerMonad, Function<R_ENV, A> f) {
    Validation.transformer().requireOuterMonad(outerMonad, READER_T_CLASS, READER);
    Validation.function().requireFunction(f, "environment function", READER_T_CLASS, READER);
    return new ReaderT<>(r -> outerMonad.of(f.apply(r)));
  }

  /**
   * Creates a {@link ReaderT} that provides the environment {@code R_ENV} itself, lifted into the
   * outer monad {@code F}. This is the "ask" operation for {@code ReaderT}.
   *
   * @param <F> The witness type of the outer monad.
   * @param <R_ENV> The type of the environment, which will also be the value type within {@code F}.
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @return A new {@link ReaderT} that, when run, yields {@code outerMonad.of(r)}. Never null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, R_ENV> ReaderT<F, R_ENV, R_ENV> ask(
      Monad<F> outerMonad) {
    Validation.transformer().requireOuterMonad(outerMonad, READER_T_CLASS, ASK);
    return new ReaderT<>(r -> outerMonad.of(r));
  }

  /**
   * Accesses the underlying function {@code R_ENV -> Kind<F, A>} of this {@link ReaderT}.
   *
   * @return The function {@code R_ENV -> Kind<F, A>}. Never null.
   */
  @Override
  public Function<R_ENV, Kind<F, A>> run() {
    return run;
  }
}
