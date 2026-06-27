// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.instances;

import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.either_t.EitherTMonad;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothKind;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothMonad;
import org.higherkindedj.hkt.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.maybe_t.MaybeTMonad;
import org.higherkindedj.hkt.optional_t.OptionalTKind;
import org.higherkindedj.hkt.optional_t.OptionalTMonad;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.higherkindedj.hkt.reader_t.ReaderTMonad;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.higherkindedj.hkt.state_t.StateTMonad;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.hkt.writer.WriterKind;
import org.higherkindedj.hkt.writer.WriterMonad;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.higherkindedj.hkt.writer_t.WriterTMonad;

/**
 * A uniform, static facade for obtaining type-class instances.
 *
 * <p>Every accessor has the same shape, {@code Instances.x(...)}, regardless of whether the
 * underlying instance is reached today via a static {@code INSTANCE} field, a generic {@code
 * instance()} method, or a constructor that requires an argument.
 *
 * <h2>Zero-argument lookups</h2>
 *
 * <p>{@link #monad(Witnesses.Of)}, {@link #applicative(Witnesses.Of)} and {@link
 * #functor(Witnesses.Of)} take a typed {@link Witnesses} token and return the canonical instance
 * viewed at the requested level. Because the canonical instance implements the whole {@code
 * Functor} &rarr; {@code Applicative} &rarr; {@code Monad} chain in one object, a single token
 * yields all three by Java subtyping.
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.instances.Witnesses.*;
 *
 * Monad<MaybeKind.Witness>       monad       = Instances.monad(maybe());
 * Applicative<MaybeKind.Witness> applicative = Instances.applicative(maybe());
 * Functor<MaybeKind.Witness>     functor     = Instances.functor(maybe());
 * }</pre>
 *
 * <h2>Argument-carrying instances</h2>
 *
 * <p>{@code Validated}, {@code Writer} and the monad transformers have a structurally required
 * dependency (a {@link Semigroup}, a {@link Monoid}, or an outer {@link Monad}). That dependency is
 * part of the method signature, so the compiler enforces it and the IDE documents it:
 *
 * <pre>{@code
 * MonadError<ValidatedKind.Witness<E>, E>  v  = Instances.validated(Semigroups.list());
 * Monad<WriterKind.Witness<String>>        w  = Instances.writer(Monoids.string());
 * MonadError<EitherTKind.Witness<F, L>, L> et = Instances.eitherT(Instances.monad(optional()));
 * }</pre>
 *
 * <p>This facade is a thin static re-export of the existing accessors. It is intentionally not
 * Spring-wired and not backed by {@code PathRegistry}/{@code ServiceLoader}: every method resolves
 * at compile time and never returns {@code Optional} or throws for a missing built-in instance.
 *
 * @see Witnesses
 */
public final class Instances {

  private Instances() {}

  // --- Zero-argument lookups (Functor / Applicative / Monad) ----------------

  /**
   * Returns the canonical {@link Monad} for the type identified by {@code witness}.
   *
   * @param <F> the unary witness type
   * @param witness a typed token from {@link Witnesses} (for example {@code Witnesses.maybe()})
   * @return the canonical monad instance
   */
  public static <F extends WitnessArity<TypeArity.Unary>> Monad<F> monad(Witnesses.Of<F> witness) {
    return witness.monad();
  }

  /**
   * Returns the canonical {@link Applicative} for the type identified by {@code witness}.
   *
   * @param <F> the unary witness type
   * @param witness a typed token from {@link Witnesses}
   * @return the canonical instance, viewed as an {@code Applicative}
   */
  public static <F extends WitnessArity<TypeArity.Unary>> Applicative<F> applicative(
      Witnesses.Of<F> witness) {
    return witness.monad();
  }

  /**
   * Returns the canonical {@link Functor} for the type identified by {@code witness}.
   *
   * @param <F> the unary witness type
   * @param witness a typed token from {@link Witnesses}
   * @return the canonical instance, viewed as a {@code Functor}
   */
  public static <F extends WitnessArity<TypeArity.Unary>> Functor<F> functor(
      Witnesses.Of<F> witness) {
    return witness.monad();
  }

  /**
   * Returns the canonical {@link MonadError} for the type identified by {@code witness}.
   *
   * <p>Unlike {@link #monad(Witnesses.Of)}, this is not total: it is only valid for canonical
   * instances that actually implement {@link MonadError} (for example {@code Maybe}, {@code
   * Optional}, {@code Try}, {@code Either}). The error type {@code E} is inferred from the
   * assignment target, mirroring how the phantom type of {@code Either} is inferred. Calling this
   * for a witness whose canonical instance is not a {@code MonadError} is a programming error and
   * fails fast with a {@link ClassCastException} — exactly as calling a non-existent method would.
   *
   * @param <F> the unary witness type
   * @param <E> the error type of the {@code MonadError}
   * @param witness a typed token from {@link Witnesses}
   * @return the canonical instance, viewed as a {@code MonadError}
   */
  @SuppressWarnings("unchecked")
  public static <F extends WitnessArity<TypeArity.Unary>, E> MonadError<F, E> monadError(
      Witnesses.Of<F> witness) {
    return (MonadError<F, E>) witness.monad();
  }

  /**
   * Returns the canonical {@link MonadZero} for the type identified by {@code witness}.
   *
   * <p>Only valid for canonical instances that implement {@link MonadZero} (for example {@code
   * Maybe}, {@code Optional}, {@code List}, {@code Stream}); see {@link #monadError(Witnesses.Of)}
   * for the same fail-fast semantics.
   *
   * @param <F> the unary witness type
   * @param witness a typed token from {@link Witnesses}
   * @return the canonical instance, viewed as a {@code MonadZero}
   */
  @SuppressWarnings("unchecked")
  public static <F extends WitnessArity<TypeArity.Unary>> MonadZero<F> monadZero(
      Witnesses.Of<F> witness) {
    return (MonadZero<F>) witness.monad();
  }

  /**
   * Returns the canonical {@link Alternative} for the type identified by {@code witness}.
   *
   * <p>Only valid for canonical instances that implement {@link Alternative} (for example {@code
   * Maybe}, {@code Optional}, {@code List}, {@code Stream}); see {@link #monadError(Witnesses.Of)}
   * for the same fail-fast semantics.
   *
   * @param <F> the unary witness type
   * @param witness a typed token from {@link Witnesses}
   * @return the canonical instance, viewed as an {@code Alternative}
   */
  @SuppressWarnings("unchecked")
  public static <F extends WitnessArity<TypeArity.Unary>> Alternative<F> alternative(
      Witnesses.Of<F> witness) {
    return (Alternative<F>) witness.monad();
  }

  // --- Argument-carrying re-exports -----------------------------------------

  /**
   * Returns the {@link MonadError} for {@code Validated}, accumulating errors with {@code
   * semigroup}.
   *
   * @param <E> the error type
   * @param semigroup the semigroup used to combine accumulated errors
   * @return {@code ValidatedMonad.instance(semigroup)}
   */
  public static <E> MonadError<ValidatedKind.Witness<E>, E> validated(Semigroup<E> semigroup) {
    return ValidatedMonad.instance(semigroup);
  }

  /**
   * Returns the {@link Monad} for {@code EitherOrBoth} (inclusive-or), accumulating the left
   * (warning) channel with {@code semigroup}. The common case uses {@code
   * NonEmptyList.semigroup()}.
   *
   * <p>Unlike the sibling error types {@code Either}/{@code Validated}, this is a plain {@link
   * Monad} and not a {@code MonadError}: the {@code Both} case makes {@code raiseError}/{@code
   * handleErrorWith} ill-defined (a value can coexist with errors). Recovery from a fatal {@code
   * Left} is offered at the Path level via {@code EitherOrBothPath}'s {@code Recoverable} methods.
   *
   * @param <L> the left (warning/error) type
   * @param semigroup the semigroup used to combine accumulated left values
   * @return {@code EitherOrBothMonad.instance(semigroup)}
   */
  public static <L> Monad<EitherOrBothKind.Witness<L>> eitherOrBoth(Semigroup<L> semigroup) {
    return EitherOrBothMonad.instance(semigroup);
  }

  /**
   * Returns the {@link Monad} for {@code Writer}, accumulating the log with {@code monoid}.
   *
   * @param <W> the log (accumulator) type
   * @param monoid the monoid used to combine the log
   * @return {@code new WriterMonad<>(monoid)}
   */
  public static <W> Monad<WriterKind.Witness<W>> writer(Monoid<W> monoid) {
    return new WriterMonad<>(monoid);
  }

  /**
   * Returns the {@link MonadError} for {@code EitherT} over the given outer monad.
   *
   * @param <F> the outer monad's witness type
   * @param <L> the "left" (error) type
   * @param outer the outer monad
   * @return {@code new EitherTMonad<>(outer)}
   */
  public static <F extends WitnessArity<TypeArity.Unary>, L>
      MonadError<EitherTKind.Witness<F, L>, L> eitherT(Monad<F> outer) {
    return new EitherTMonad<>(outer);
  }

  /**
   * Returns the {@link MonadError} for {@code MaybeT} over the given outer monad. The error type is
   * {@link Unit} (absence).
   *
   * @param <F> the outer monad's witness type
   * @param outer the outer monad
   * @return {@code new MaybeTMonad<>(outer)}
   */
  public static <F extends WitnessArity<TypeArity.Unary>>
      MonadError<MaybeTKind.Witness<F>, Unit> maybeT(Monad<F> outer) {
    return new MaybeTMonad<>(outer);
  }

  /**
   * Returns the {@link MonadError} for {@code OptionalT} over the given outer monad. The error type
   * is {@link Unit} (absence).
   *
   * @param <F> the outer monad's witness type
   * @param outer the outer monad
   * @return {@code new OptionalTMonad<>(outer)}
   */
  public static <F extends WitnessArity<TypeArity.Unary>>
      MonadError<OptionalTKind.Witness<F>, Unit> optionalT(Monad<F> outer) {
    return new OptionalTMonad<>(outer);
  }

  /**
   * Returns the {@link Monad} for {@code ReaderT} over the given outer monad.
   *
   * @param <F> the outer monad's witness type
   * @param <R> the environment type
   * @param outer the outer monad
   * @return {@code new ReaderTMonad<>(outer)}
   */
  public static <F extends WitnessArity<TypeArity.Unary>, R>
      Monad<ReaderTKind.Witness<F, R>> readerT(Monad<F> outer) {
    return new ReaderTMonad<>(outer);
  }

  /**
   * Returns the {@link Monad} for {@code StateT} over the given outer monad.
   *
   * @param <S> the state type
   * @param <F> the outer monad's witness type
   * @param outer the outer monad
   * @return {@code StateTMonad.instance(outer)}
   */
  public static <S, F extends WitnessArity<TypeArity.Unary>> Monad<StateTKind.Witness<S, F>> stateT(
      Monad<F> outer) {
    return StateTMonad.instance(outer);
  }

  /**
   * Returns the {@link MonadWriter} for {@code WriterT} over the given outer monad, accumulating
   * the log with {@code monoid}.
   *
   * @param <F> the outer monad's witness type
   * @param <W> the log (accumulator) type
   * @param outer the outer monad
   * @param monoid the monoid used to combine the log
   * @return {@code new WriterTMonad<>(outer, monoid)}
   */
  public static <F extends WitnessArity<TypeArity.Unary>, W>
      MonadWriter<WriterTKind.Witness<F, W>, W> writerT(Monad<F> outer, Monoid<W> monoid) {
    return new WriterTMonad<>(outer, monoid);
  }
}
