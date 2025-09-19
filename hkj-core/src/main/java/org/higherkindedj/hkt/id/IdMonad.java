// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.util.ErrorHandling.requireNonNullFunction;
import static org.higherkindedj.hkt.util.ErrorHandling.requireNonNullKind;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.Nullable;

/**
 * The {@link Monad} instance for the {@link Id} type.
 *
 * <p>The Identity monad is the simplest monad. It performs no additional computation or context
 * wrapping beyond the basic monadic operations. It simply holds a value. Its primary utility comes
 * from being a base case for monad transformers (e.g., {@code StateT<S, Id.Witness, A>} is
 * equivalent to {@code State<S, A>}) and in generic monadic programming where no additional effects
 * are desired.
 *
 * <ul>
 *   <li>{@link #of(Object)} simply wraps a value {@code a} in an {@code Id(a)}.
 *   <li>{@link #map(Function, Kind)} applies a function to the value inside an {@code Id} and wraps
 *       the result in a new {@code Id}.
 *   <li>{@link #ap(Kind, Kind)} applies a function wrapped in an {@code Id} to a value wrapped in
 *       an {@code Id}.
 *   <li>{@link #flatMap(Function, Kind)} applies a function to the value inside an {@code Id},
 *       where the function itself returns a new {@code Id}.
 * </ul>
 *
 * This class is a singleton, accessible via {@link #instance()}.
 */
public final class IdMonad implements Monad<Id.Witness> {

  private static final IdMonad INSTANCE = new IdMonad();

  // Private constructor to enforce singleton pattern.
  private IdMonad() {}

  /**
   * Returns the singleton instance of {@link IdMonad}.
   *
   * @return The singleton {@code IdMonad} instance.
   */
  public static IdMonad instance() {
    return INSTANCE;
  }

  /**
   * Lifts a value {@code a} into the Identity monad context. This is equivalent to {@code
   * Id.of(a)}.
   *
   * @param a The value to lift. Can be null, in which case {@code Id(null)} is returned.
   * @param <A> The type of the value.
   * @return An {@code Id<A>} wrapping the value, cast to {@code Kind<Id.Witness, A>}. The returned
   *     {@link Kind} is guaranteed non-null, even if {@code a} is null.
   */
  @Override
  public <A> Kind<Id.Witness, A> of(@Nullable A a) {
    return Id.of(a);
  }

  /**
   * Applies a function to the value wrapped within an {@code Id} context.
   *
   * <p>If {@code fa} is {@code Id(x)}, the result is {@code Id(fn.apply(x))}. This operation
   * adheres to the Functor laws:
   *
   * <ol>
   *   <li>Identity: {@code map(x -> x, fa)} is equivalent to {@code fa}.
   *   <li>Composition: {@code map(g.compose(f), fa)} is equivalent to {@code map(g, map(f, fa))}.
   * </ol>
   *
   * @param f The function to apply to the wrapped value. Must not be null.
   * @param fa The {@code Kind<Id.Witness, A>} (which is an {@code Id<A>}) containing the input
   *     value. Must not be null.
   * @param <A> The type of the input value.
   * @param <B> The type of the result of the function.
   * @return A new {@code Id<B>} containing the result of applying the function, cast to {@code
   *     Kind<Id.Witness, B>}. Guaranteed non-null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   */
  @Override
  public <A, B> Kind<Id.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<Id.Witness, A> fa) {
    requireNonNullFunction(f, "function f for map");
    requireNonNullKind(fa, "source Kind for map");
    // Delegate to Id.map for directness. Id.map handles null values wrapped in Id correctly.
    return ID.narrow(fa).map(f);
  }

  /**
   * Applies a function wrapped in an {@code Id} context to a value wrapped in an {@code Id}
   * context.
   *
   * <p>If {@code ff} is {@code Id(f)} and {@code fa} is {@code Id(x)}, the result is {@code
   * Id(f.apply(x))}.
   *
   * @param ff The {@code Kind<Id.Witness, Function<A, B>>} (an {@code Id<Function<A,B>>})
   *     containing the function. Must not be null. The function itself wrapped within {@code ff}
   *     must also not be null.
   * @param fa The {@code Kind<Id.Witness, A>} (an {@code Id<A>}) containing the value to apply the
   *     function to. Must not be null.
   * @param <A> The type of the input value.
   * @param <B> The type of the result of the function.
   * @return A new {@code Id<B>} containing the result of applying the function, cast to {@code
   *     Kind<Id.Witness, B>}. Guaranteed non-null.
   * @throws NullPointerException if {@code ff}, {@code fa}, or the function wrapped in {@code ff}
   *     is null.
   */
  @Override
  public <A, B> Kind<Id.Witness, B> ap(
      Kind<Id.Witness, ? extends Function<A, B>> ff, Kind<Id.Witness, A> fa) {
    requireNonNullKind(ff, "function Kind for ap");
    requireNonNullKind(fa, "argument Kind for ap");

    Function<A, B> function = ID.narrow(ff).value();
    A value = ID.narrow(fa).value();

    if (function == null) {
      throw new NullPointerException("Function wrapped in Id cannot be null for ap");
    }
    return Id.of(function.apply(value));
  }

  /**
   * Applies a function that returns an {@code Id} (as {@code Kind<Id.Witness, B>}) to the value
   * wrapped in an {@code Id} context.
   *
   * <p>This is the core monadic bind operation. If {@code fa} is {@code Id(x)}, the result is
   * {@code f.apply(x)}. Since {@code f} itself returns an {@code Id}, this effectively "flattens"
   * the computation (avoiding {@code Id<Id<B>>}).
   *
   * <p>This operation adheres to the Monad laws (Left Identity, Right Identity, Associativity).
   *
   * @param f The function to apply. It takes a plain {@code A} and returns a {@code
   *     Kind<Id.Witness, B>} (which must be an {@code Id<B>}). Must not be null, and must not
   *     return a null {@link Kind}.
   * @param ma The {@code Kind<Id.Witness, A>} (an {@code Id<A>}) containing the input value. Must
   *     not be null.
   * @param <A> The type of the input value.
   * @param <B> The type of the value within the {@code Id} returned by the function {@code f}.
   * @return The {@code Kind<Id.Witness, B>} returned by the function {@code f}. Guaranteed non-null
   *     if {@code f} adheres to its contract of returning a non-null {@link Kind}.
   * @throws NullPointerException if {@code f}, {@code ma} is null, or if {@code f} returns a null
   *     {@link Kind}.
   */
  @Override
  public <A, B> Kind<Id.Witness, B> flatMap(
      Function<? super A, ? extends Kind<Id.Witness, B>> f, Kind<Id.Witness, A> ma) {
    requireNonNullFunction(f, "function f for flatMap");
    requireNonNullKind(ma, "source Kind for flatMap");

    A valueInA = ID.narrow(ma).value();
    Kind<Id.Witness, B> resultKind = f.apply(valueInA);
    // The function f is expected to return a non-null Kind (which will be an Id instance).
    return Objects.requireNonNull(resultKind, "Function passed to flatMap returned null Kind");
  }
}
