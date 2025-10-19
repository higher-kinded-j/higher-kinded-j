// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;

/**
 * Implements the {@link Traverse} and Foldable type classes for {@link java.util.Optional}, using
 * {@link OptionalKind.Witness} as the higher-kinded type witness.
 *
 * <p>This class provides traversal and folding operations for {@code Optional} values within the
 * Higher-Kinded-J framework. The operations handle both present and empty {@code Optional} values
 * appropriately:
 *
 * <ul>
 *   <li><b>Present values</b>: Operations are applied to the contained value
 *   <li><b>Empty values</b>: Operations short-circuit, returning appropriate identity/empty values
 * </ul>
 *
 * <p><b>Traverse Operations:</b> Allow transforming {@code Optional<A>} into {@code G<Optional<B>>}
 * for any applicative functor {@code G}, enabling composition with other computational contexts.
 *
 * <p><b>Foldable Operations:</b> Enable reduction of {@code Optional} values using monoid
 * structures, useful for aggregation and accumulation patterns.
 *
 * <p>This implementation is a singleton enum, accessible via {@link #INSTANCE}.
 *
 * @see Optional
 * @see OptionalKind
 * @see OptionalKindHelper
 * @see Traverse
 * @see org.higherkindedj.hkt.Foldable
 */
public enum OptionalTraverse implements Traverse<OptionalKind.Witness> {

  /** Singleton instance of {@code OptionalTraverse}. */
  INSTANCE;

  private static final Class<OptionalTraverse> OPTIONAL_TRAVERSE_CLASS = OptionalTraverse.class;

  /**
   * Maps a function over the value contained within an {@code OptionalKind} context, if a value is
   * present. This provides the Functor behavior required by the Traverse type class.
   *
   * <p>If the input {@code OptionalKind} ({@code fa}) represents an {@code Optional.of(a)}, the
   * function {@code f} is applied to {@code a}. If {@code fa} represents {@code Optional.empty()},
   * an empty {@code OptionalKind} is returned without applying the function.
   *
   * <p>This implementation delegates to {@code Optional.map()}, ensuring consistent behavior with
   * Java's standard library.
   *
   * @param <A> The type of the value in the input {@code OptionalKind}.
   * @param <B> The type of the value in the output {@code OptionalKind} after applying the
   *     function.
   * @param f The non-null function to apply to the value inside the {@code OptionalKind} if
   *     present.
   * @param fa The non-null {@code Kind<OptionalKind.Witness, A>} representing the {@code
   *     Optional<A>} whose value is to be transformed.
   * @return A non-null {@code Kind<OptionalKind.Witness, B>} representing the transformed {@code
   *     Optional<B>}.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} is not a valid {@code
   *     OptionalKind} representation.
   */
  @Override
  public <A, B> Kind<OptionalKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<OptionalKind.Witness, A> fa) {

    FunctionValidator.requireMapper(f, "f", OPTIONAL_TRAVERSE_CLASS, MAP);
    KindValidator.requireNonNull(fa, OPTIONAL_TRAVERSE_CLASS, MAP);

    return OPTIONAL.widen(OPTIONAL.narrow(fa).map(f));
  }

  /**
   * Traverses an {@code OptionalKind} structure, transforming it from {@code Optional<A>} to {@code
   * G<Optional<B>>} using an applicative functor {@code G}.
   *
   * <p>This operation allows sequencing effects: if the {@code Optional} contains a value, the
   * function {@code f} is applied to produce a {@code G<B>}, which is then wrapped in {@code
   * Optional} within the {@code G} context. If the {@code Optional} is empty, the result is a
   * {@code G} containing {@code Optional.empty()}.
   *
   * <p><b>Behavior:</b>
   *
   * <ul>
   *   <li><b>Present value</b>: {@code f(a)} produces {@code G<B>}, result is {@code
   *       G<Optional<B>>}
   *   <li><b>Empty</b>: Result is {@code applicative.of(Optional.empty())}
   * </ul>
   *
   * <p><b>Example Use Cases:</b>
   *
   * <ul>
   *   <li>Sequencing {@code Optional} with {@code List}: {@code Optional<A> -> List<Optional<B>>}
   *   <li>Combining {@code Optional} with {@code IO}: {@code Optional<A> -> IO<Optional<B>>}
   *   <li>Validation scenarios: {@code Optional<A> -> Validation<E, Optional<B>>}
   * </ul>
   *
   * @param <G> The witness type of the target applicative functor.
   * @param <A> The type of the value in the source {@code Optional}.
   * @param <B> The type of the value in the target applicative context.
   * @param applicative The {@link Applicative} instance for the target context {@code G}. Must not
   *     be null.
   * @param f The transformation function that takes a value of type {@code A} and produces a {@code
   *     Kind<G, B>}. Must not be null.
   * @param ta The {@code Kind<OptionalKind.Witness, A>} to traverse. Must not be null.
   * @return A {@code Kind<G, Kind<OptionalKind.Witness, B>>} representing the traversed structure.
   *     Never null.
   * @throws NullPointerException if {@code applicative}, {@code f}, or {@code ta} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ta} is not a valid {@code
   *     OptionalKind} representation.
   */
  @Override
  public <G, A, B> Kind<G, Kind<OptionalKind.Witness, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<OptionalKind.Witness, A> ta) {

    FunctionValidator.requireApplicative(
        applicative, "applicative", OPTIONAL_TRAVERSE_CLASS, TRAVERSE);
    FunctionValidator.requireMapper(f, "f", OPTIONAL_TRAVERSE_CLASS, TRAVERSE);
    KindValidator.requireNonNull(ta, OPTIONAL_TRAVERSE_CLASS, TRAVERSE);

    return OPTIONAL
        .narrow(ta)
        .map(f)
        .map(gb -> applicative.map(b -> OPTIONAL.widen(Optional.ofNullable(b)), gb))
        .orElse(applicative.of(OPTIONAL.widen(Optional.empty())));
  }

  /**
   * Folds an {@code OptionalKind} by mapping its value (if present) through a function and
   * combining the result using a {@link Monoid}.
   *
   * <p>This operation enables reduction of {@code Optional} values into a monoid type {@code M}:
   *
   * <ul>
   *   <li><b>Present value</b>: Applies {@code f} to the value and returns the result
   *   <li><b>Empty</b>: Returns the monoid's identity element ({@code monoid.empty()})
   * </ul>
   *
   * <p><b>Common Use Cases:</b>
   *
   * <ul>
   *   <li>Summing numeric optionals: {@code foldMap(String::length, stringOptional)}
   *   <li>Concatenating string optionals: {@code foldMap(identity, stringOptional)}
   *   <li>Collecting to lists: {@code foldMap(Collections::singletonList, optional)}
   * </ul>
   *
   * <p><b>Monoid Laws:</b> This operation respects monoid laws, ensuring that folding empty
   * optionals produces the identity element, and folding preserves associativity when chaining
   * multiple operations.
   *
   * @param <A> The type of the value in the {@code Optional}.
   * @param <M> The monoid type used for combining results.
   * @param monoid The {@link Monoid} instance defining the combination operation and identity
   *     element. Must not be null.
   * @param f The function to apply to the {@code Optional}'s value (if present) to produce a value
   *     of type {@code M}. Must not be null.
   * @param fa The {@code Kind<OptionalKind.Witness, A>} to fold. Must not be null.
   * @return A value of type {@code M}: either {@code f(value)} if the optional contains a value, or
   *     {@code monoid.empty()} if the optional is empty. Never null.
   * @throws NullPointerException if {@code monoid}, {@code f}, or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} is not a valid {@code
   *     OptionalKind} representation.
   */
  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<OptionalKind.Witness, A> fa) {

    FunctionValidator.requireMonoid(monoid, "monoid", OPTIONAL_TRAVERSE_CLASS, FOLD_MAP);
    FunctionValidator.requireMapper(f, "f", OPTIONAL_TRAVERSE_CLASS, FOLD_MAP);
    KindValidator.requireNonNull(fa, OPTIONAL_TRAVERSE_CLASS, FOLD_MAP);

    Optional<A> optional = OPTIONAL.narrow(fa);
    // If present, map the value. If empty, return the monoid's empty value.
    if (optional.isPresent()) {
      return f.apply(optional.get());
    } else {
      return monoid.empty();
    }
  }
}
