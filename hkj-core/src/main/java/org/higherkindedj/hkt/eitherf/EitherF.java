// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * A sum type for composing effect algebras at the type constructor level.
 *
 * <p>{@code EitherF<F, G, A>} represents an instruction that is either from effect set {@code F} or
 * effect set {@code G}. Just as {@code Either<L, R>} represents "a value that is either an L or an
 * R," {@code EitherF} represents "an instruction that is either from effect-set F or effect-set G."
 *
 * <p>The name follows the established {@code modifyF} convention where the {@code F} suffix means
 * "lifted to the functor/effect level."
 *
 * <h2>Usage</h2>
 *
 * <p>EitherF is used to compose multiple effect algebras into a single combined effect type. For
 * example, combining console operations and database operations:
 *
 * <pre>{@code
 * // Two independent effect algebras
 * sealed interface ConsoleOp<A> { ... }
 * sealed interface DbOp<A> { ... }
 *
 * // Combined: an instruction from either ConsoleOp or DbOp
 * EitherF<ConsoleOpKind.Witness, DbOpKind.Witness, A>
 * }</pre>
 *
 * <p>For three or more effects, nesting is right-associated: {@code EitherF<F,
 * EitherFKind.Witness<G, H>, A>}. Users never construct this manually; {@code
 * Interpreters.combine()} and {@code Inject} handle the nesting.
 *
 * @param <F> The witness type for the left effect algebra
 * @param <G> The witness type for the right effect algebra
 * @param <A> The result type of the instruction
 * @see EitherFKind
 * @see EitherFFunctor
 * @see org.higherkindedj.hkt.inject.Inject
 */
@NullMarked
public sealed interface EitherF<F extends WitnessArity<?>, G extends WitnessArity<?>, A>
    permits EitherF.Left, EitherF.Right {

  /**
   * An instruction from the left effect algebra F.
   *
   * @param value The instruction in F
   * @param <F> The witness type for the left effect algebra
   * @param <G> The witness type for the right effect algebra
   * @param <A> The result type
   */
  record Left<F extends WitnessArity<?>, G extends WitnessArity<?>, A>(Kind<F, A> value)
      implements EitherF<F, G, A> {

    /** Creates a Left wrapping an instruction from effect algebra F. */
    public Left {
      Validation.function().require(value, "value", LEFT);
    }
  }

  /**
   * An instruction from the right effect algebra G.
   *
   * @param value The instruction in G
   * @param <F> The witness type for the left effect algebra
   * @param <G> The witness type for the right effect algebra
   * @param <A> The result type
   */
  record Right<F extends WitnessArity<?>, G extends WitnessArity<?>, A>(Kind<G, A> value)
      implements EitherF<F, G, A> {

    /** Creates a Right wrapping an instruction from effect algebra G. */
    public Right {
      Validation.function().require(value, "value", RIGHT);
    }
  }

  /**
   * Eliminates this EitherF by applying one of two functions depending on whether it is a Left or a
   * Right.
   *
   * @param onLeft The function to apply if this is a Left (instruction from F)
   * @param onRight The function to apply if this is a Right (instruction from G)
   * @param <B> The result type of the fold
   * @return The result of applying the appropriate function
   */
  default <B> B fold(
      Function<? super Kind<F, A>, ? extends B> onLeft,
      Function<? super Kind<G, A>, ? extends B> onRight) {
    Validation.function().require(onLeft, "onLeft", FOLD);
    Validation.function().require(onRight, "onRight", FOLD);
    return switch (this) {
      case Left<F, G, A> l -> onLeft.apply(l.value());
      case Right<F, G, A> r -> onRight.apply(r.value());
    };
  }

  /**
   * Creates a Left wrapping an instruction from effect algebra F.
   *
   * @param value The instruction in F
   * @param <F> The witness type for the left effect algebra
   * @param <G> The witness type for the right effect algebra
   * @param <A> The result type
   * @return A new EitherF.Left
   */
  static <F extends WitnessArity<?>, G extends WitnessArity<?>, A> EitherF<F, G, A> left(
      Kind<F, A> value) {
    return new Left<>(value);
  }

  /**
   * Creates a Right wrapping an instruction from effect algebra G.
   *
   * @param value The instruction in G
   * @param <F> The witness type for the left effect algebra
   * @param <G> The witness type for the right effect algebra
   * @param <A> The result type
   * @return A new EitherF.Right
   */
  static <F extends WitnessArity<?>, G extends WitnessArity<?>, A> EitherF<F, G, A> right(
      Kind<G, A> value) {
    return new Right<>(value);
  }
}
