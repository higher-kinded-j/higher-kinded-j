// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.*;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Functor} type class for {@link java.util.Optional}, using {@link
 * OptionalKind.Witness} as the higher-kinded type witness.
 *
 * <p>A {@link Functor} provides the ability to apply a function to a value inside a context (in
 * this case, an {@code Optional}) without needing to explicitly extract the value. The {@link
 * #map(Function, Kind)} operation transforms an {@code Optional<A>} into an {@code Optional<B>} by
 * applying a function {@code A -> B} to the value if it is present.
 *
 * @see Functor
 * @see Optional
 * @see OptionalKind
 * @see OptionalKindHelper
 * @see OptionalMonad
 */
public class OptionalFunctor implements Functor<OptionalKind.Witness> {

  /**
   * Constructs a new {@code OptionalFunctor} instance. This constructor is public to allow
   * instantiation where needed, although typically functor operations are accessed via a {@link
   * OptionalMonad} instance.
   */
  public OptionalFunctor() {
    // Default constructor
  }

  /**
   * Applies a function to the value contained within an {@code OptionalKind} context, if a value is
   * present.
   *
   * <p>If the input {@code OptionalKind} ({@code fa}) represents an {@code Optional.of(a)}, the
   * function {@code f} is applied to {@code a}. If {@code f} returns a non-null value {@code b},
   * the result is an {@code OptionalKind} representing {@code Optional.of(b)}. If {@code f} returns
   * {@code null}, the result is an empty {@code OptionalKind} (representing {@code
   * Optional.empty()}).
   *
   * <p>If {@code fa} represents {@code Optional.empty()}, an empty {@code OptionalKind} is
   * returned, and the function {@code f} is not applied.
   *
   * <p>This operation adheres to the Functor laws:
   *
   * <ol>
   *   <li>Identity: {@code map(x -> x, fa)} is equivalent to {@code fa}.
   *   <li>Composition: {@code map(g.compose(f), fa)} is equivalent to {@code map(g, map(f, fa))}.
   * </ol>
   *
   * @param <A> The type of the value in the input {@code OptionalKind}.
   * @param <B> The type of the value in the output {@code OptionalKind} after applying the
   *     function.
   * @param f The non-null function to apply to the value inside the {@code OptionalKind} if
   *     present. This function can return {@code @Nullable B}.
   * @param fa The non-null {@code Kind<OptionalKind.Witness, A>} (which is an {@code
   *     OptionalKind<A>}) representing the {@code Optional<A>} whose value is to be transformed.
   * @return A non-null {@code Kind<OptionalKind.Witness, B>} representing a new {@code Optional<B>}
   *     that will contain the transformed value if the input was present and the function returned
   *     non-null, or will be empty otherwise.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} is not a valid {@code
   *     OptionalKind} representation.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   */
  @Override
  public <A, B> @NonNull Kind<OptionalKind.Witness, B> map(
      @NonNull Function<A, @Nullable B> f, @NonNull Kind<OptionalKind.Witness, A> fa) {
    Optional<A> optionalA = OPTIONAL.narrow(fa);
    // Optional.map correctly handles f returning null by creating Optional.empty()
    Optional<B> resultOptional = optionalA.map(f);
    return OPTIONAL.widen(resultOptional);
  }
}
