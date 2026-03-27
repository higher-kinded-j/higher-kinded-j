// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Represents the concrete implementation of the Maybe Transformer Monad (MaybeT). It wraps a
 * monadic value of type {@code Kind<F, Maybe<A>>}, where {@code F} is the outer monad and {@code
 * Maybe<A>} is the inner optional value.
 *
 * <p>This class is a record, making it an immutable data holder for the wrapped value. It
 * implements {@link MaybeTKind} to participate in higher-kinded type simulations, allowing it to be
 * treated as {@code Kind<MaybeTKind.Witness<F>, A>}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind.Witness}).
 * @param <A> The type of the value potentially held by the inner {@link Maybe}.
 * @param value The underlying monadic value {@code Kind<F, Maybe<A>>}. Must not be null.
 * @see MaybeTKind
 * @see MaybeTMonad
 * @see MaybeTKindHelper
 */
public record MaybeT<F extends WitnessArity<TypeArity.Unary>, A>(Kind<F, Maybe<A>> value)
    implements MaybeTKind<F, A> {

  private static final Class<MaybeT> MAYBE_T_CLASS = MaybeT.class;

  /**
   * Canonical constructor for {@code MaybeT}.
   *
   * @param value The underlying monadic value {@code Kind<F, Maybe<A>>}.
   * @throws NullPointerException if {@code value} is null.
   */
  public MaybeT {
    Validation.coreType().requireValue(value, MAYBE_T_CLASS, CONSTRUCTION);
  }

  /**
   * Creates a {@code MaybeT} from an existing {@code Kind<F, Maybe<A>>}.
   *
   * @param value The monadic value to wrap. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the inner {@link Maybe}.
   * @return A new {@code MaybeT} instance.
   * @throws NullPointerException if {@code value} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> MaybeT<F, A> fromKind(
      Kind<F, Maybe<A>> value) {
    return new MaybeT<>(value);
  }

  /**
   * Lifts a non-null value {@code a} into {@code MaybeT<F, A>}, resulting in {@code F<Just(a)>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param a The value to wrap. Must not be null (enforced by {@link Maybe#just(Object)}).
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value to wrap.
   * @return A new {@code MaybeT} instance representing {@code outerMonad.of(Maybe.just(a))}.
   * @throws NullPointerException if {@code outerMonad} or {@code a} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A extends Object> MaybeT<F, A> just(
      Monad<F> outerMonad, A a) {
    Validation.transformer().requireOuterMonad(outerMonad, MAYBE_T_CLASS, JUST);
    Kind<F, Maybe<A>> lifted = outerMonad.of(Maybe.just(a));
    return new MaybeT<>(lifted);
  }

  /**
   * Creates a {@code MaybeT<F, A>} representing the {@code Nothing} state, resulting in {@code
   * F<Nothing>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the inner {@link Maybe} (will be absent).
   * @return A new {@code MaybeT} instance representing {@code outerMonad.of(Maybe.nothing())}.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> MaybeT<F, A> nothing(
      Monad<F> outerMonad) {
    Validation.transformer().requireOuterMonad(outerMonad, MAYBE_T_CLASS, NONE);
    Kind<F, Maybe<A>> lifted = outerMonad.of(Maybe.nothing());
    return new MaybeT<>(lifted);
  }

  /**
   * Lifts a plain {@link Maybe<A>} into {@code MaybeT<F, A>}, resulting in {@code F<Maybe<A>>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param maybe The {@link Maybe} instance to lift. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the {@link Maybe}.
   * @return A new {@code MaybeT} instance representing {@code outerMonad.of(maybe)}.
   * @throws NullPointerException if {@code outerMonad} or {@code maybe} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> MaybeT<F, A> fromMaybe(
      Monad<F> outerMonad, Maybe<A> maybe) {
    Validation.transformer().requireOuterMonad(outerMonad, MAYBE_T_CLASS, FROM_MAYBE);
    Validation.transformer()
        .requireTransformerComponent(maybe, "inner Maybe", MAYBE_T_CLASS, FROM_MAYBE);
    Kind<F, Maybe<A>> lifted = outerMonad.of(maybe);
    return new MaybeT<>(lifted);
  }

  /**
   * Lifts a monadic value {@code Kind<F, A>} into {@code MaybeT<F, A>}, resulting in {@code
   * F<Maybe<A>>}. The value {@code A} inside {@code F} is mapped to {@code Maybe<A>} using {@link
   * Maybe#fromNullable(Object)}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param fa The monadic value {@code Kind<F, A>} to lift. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in {@code fa}.
   * @return A new {@code MaybeT} instance representing {@code outerMonad.map(Maybe::fromNullable,
   *     fa)}.
   * @throws NullPointerException if {@code outerMonad} or {@code fa} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> MaybeT<F, A> liftF(
      Monad<F> outerMonad, Kind<F, A> fa) {
    Validation.transformer().requireOuterMonad(outerMonad, MAYBE_T_CLASS, LIFT_F);
    Validation.kind().requireNonNull(fa, LIFT_F, "source Kind");
    Kind<F, Maybe<A>> mapped = outerMonad.map(Maybe::fromNullable, fa);
    return new MaybeT<>(mapped);
  }

  /**
   * Transforms the outer monad layer of this {@code MaybeT} by applying the given function to the
   * underlying {@code Kind<F, Maybe<A>>}, producing a new {@code MaybeT<G, A>} wrapping the result.
   * The inner {@link Maybe} value is left untouched — only the surrounding monadic context changes.
   *
   * <p>This is useful for applying cross-cutting concerns (logging, retry, timeout) at the monad
   * level, or for switching between monadic contexts via a natural transformation.
   *
   * <p><b>Example — switching from IO to Task via a natural transformation:</b>
   *
   * <pre>{@code
   * MaybeT<IOKind.Witness, String> ioResult = ...;
   * Natural<IOKind.Witness, TaskKind.Witness> ioToTask = ...;
   *
   * MaybeT<TaskKind.Witness, String> taskResult = ioResult.mapT(ioToTask::apply);
   * }</pre>
   *
   * @param f The function to apply to the underlying {@code Kind<F, Maybe<A>>}. Must not be null.
   * @param <G> The witness type of the target outer monad.
   * @return A new {@code MaybeT<G, A>} wrapping the transformed monadic value.
   * @throws NullPointerException if {@code f} is null.
   */
  public <G extends WitnessArity<TypeArity.Unary>> MaybeT<G, A> mapT(
      Function<Kind<F, Maybe<A>>, Kind<G, Maybe<A>>> f) {
    Validation.function().require(f, "f", MAP_T);
    return MaybeT.fromKind(f.apply(this.value()));
  }

  /**
   * Accessor for the underlying monadic value.
   *
   * @return The {@code Kind<F, Maybe<A>>} wrapped by this {@code MaybeT}.
   */
  @Override
  public Kind<F, Maybe<A>> value() {
    return value;
  }
}
