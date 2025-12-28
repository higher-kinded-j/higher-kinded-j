// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Lens;
import org.jspecify.annotations.NullMarked;

/**
 * An indexed lens that provides focused access to a single field along with its index/key.
 *
 * <p>An {@code IndexedLens} is the indexed equivalent of a {@link Lens}. While a regular lens
 * focuses on exactly one field that is guaranteed to exist, an indexed lens also provides index
 * information, which can represent:
 *
 * <ul>
 *   <li>A field name (for introspection/debugging)
 *   <li>A map key
 *   <li>A position in a fixed-size structure
 *   <li>A path component
 * </ul>
 *
 * <p>Common use cases include:
 *
 * <ul>
 *   <li>Tracking field names during transformations
 *   <li>Building audit trails with field provenance
 *   <li>Conditional logic based on which field is being accessed
 *   <li>Generic field processing with field identification
 * </ul>
 *
 * @param <I> The index type (e.g., String for field names, K for Map keys)
 * @param <S> The source/target structure type
 * @param <A> The focused element type
 */
@NullMarked
public interface IndexedLens<I, S, A> extends IndexedOptic<I, S, A> {

  /**
   * Gets the index associated with this lens.
   *
   * <p>Unlike traversals where each element has its own index, a lens has a single fixed index
   * representing the field or key being focused on.
   *
   * @return The index value
   */
  I index();

  /**
   * Gets the focused part {@code A} from the whole structure {@code S}.
   *
   * @param source The whole structure
   * @return The focused part
   */
  A get(S source);

  /**
   * Sets a new value for the focused part {@code A}, returning a new, updated structure {@code S}.
   *
   * <p>This operation is immutable; the original {@code source} object is not changed.
   *
   * @param newValue The new value for the focused part
   * @param source The original structure
   * @return A new structure with the focused part updated
   */
  S set(A newValue, S source);

  /**
   * Gets the focused part along with its index as a pair.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IndexedLens<String, User, Integer> ageLens = IndexedLens.of("age", User::age, User::withAge);
   * Pair<String, Integer> indexed = ageLens.iget(user);
   * // Pair("age", 25)
   * }</pre>
   *
   * @param source The whole structure
   * @return A pair of (index, value)
   */
  default Pair<I, A> iget(S source) {
    return new Pair<>(index(), get(source));
  }

  /**
   * Modifies the focused part using a function that receives both index and value.
   *
   * @param modifier The function to apply, receiving both index and value
   * @param source The whole structure
   * @return A new structure with the modified part
   */
  default S imodify(BiFunction<I, A, A> modifier, S source) {
    return set(modifier.apply(index(), get(source)), source);
  }

  /**
   * Modifies the focused part using a pure function (ignoring the index).
   *
   * @param modifier The function to apply to the focused part
   * @param source The whole structure
   * @return A new structure with the modified part
   */
  default S modify(Function<A, A> modifier, S source) {
    return set(modifier.apply(get(source)), source);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Modifies the focused part with an effectful function that receives both index and value.
   */
  @Override
  default <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> imodifyF(
      BiFunction<I, A, Kind<F, A>> f, S source, Applicative<F> app) {
    Kind<F, A> fa = f.apply(index(), get(source));
    return app.map(a -> set(a, source), fa);
  }

  /**
   * Modifies the focused part with an effectful function that receives only the value.
   *
   * @param f The effectful function to apply
   * @param source The whole structure
   * @param functor The Functor instance for the context F
   * @param <F> The witness type for the Functor context
   * @return The updated structure wrapped in the context F
   */
  default <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
      Function<A, Kind<F, A>> f, S source, Functor<F> functor) {
    Kind<F, A> fa = f.apply(get(source));
    return functor.map(a -> set(a, source), fa);
  }

  /**
   * Composes this {@code IndexedLens<I, S, A>} with another {@code IndexedLens<J, A, B>} to create
   * a new {@code IndexedLens<Pair<I, J>, S, B>} with paired indices.
   *
   * @param other The {@link IndexedLens} to compose with
   * @param <J> The index type of the other lens
   * @param <B> The focus type of the other lens
   * @return A new {@link IndexedLens} with paired indices
   */
  default <J, B> IndexedLens<Pair<I, J>, S, B> iandThen(IndexedLens<J, A, B> other) {
    IndexedLens<I, S, A> self = this;
    return new IndexedLens<>() {
      @Override
      public Pair<I, J> index() {
        return new Pair<>(self.index(), other.index());
      }

      @Override
      public B get(S source) {
        return other.get(self.get(source));
      }

      @Override
      public S set(B newValue, S source) {
        return self.set(other.set(newValue, self.get(source)), source);
      }
    };
  }

  /**
   * Composes this {@code IndexedLens<I, S, A>} with a regular {@code Lens<A, B>} to create a new
   * {@code IndexedLens<I, S, B>} that preserves the outer index.
   *
   * @param other The {@link Lens} to compose with
   * @param <B> The focus type of the other lens
   * @return A new {@link IndexedLens} preserving the outer index
   */
  default <B> IndexedLens<I, S, B> andThen(Lens<A, B> other) {
    IndexedLens<I, S, A> self = this;
    return new IndexedLens<>() {
      @Override
      public I index() {
        return self.index();
      }

      @Override
      public B get(S source) {
        return other.get(self.get(source));
      }

      @Override
      public S set(B newValue, S source) {
        return self.set(other.set(newValue, self.get(source)), source);
      }
    };
  }

  /**
   * Views this {@code IndexedLens} as a regular (non-indexed) {@link Lens}.
   *
   * @return A {@link Lens} that ignores index information
   */
  default Lens<S, A> asLens() {
    IndexedLens<I, S, A> self = this;
    return new Lens<>() {
      @Override
      public A get(S source) {
        return self.get(source);
      }

      @Override
      public S set(A newValue, S source) {
        return self.set(newValue, source);
      }

      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S source, Functor<F> functor) {
        return self.modifyF(f, source, functor);
      }
    };
  }

  /**
   * Views this {@code IndexedLens} as an {@link IndexedTraversal}.
   *
   * <p>This is always possible because a lens is a traversal that focuses on exactly one element.
   *
   * @return An {@link IndexedTraversal} that represents this lens
   */
  default IndexedTraversal<I, S, A> asIndexedTraversal() {
    IndexedLens<I, S, A> self = this;
    return self::imodifyF;
  }

  /**
   * Views this {@code IndexedLens} as an {@link IndexedFold}.
   *
   * @return An {@link IndexedFold} that represents this lens
   */
  default IndexedFold<I, S, A> asIndexedFold() {
    IndexedLens<I, S, A> self = this;
    return new IndexedFold<I, S, A>() {
      @Override
      public <M> M ifoldMap(
          Monoid<M> monoid, BiFunction<? super I, ? super A, ? extends M> f, S source) {
        return f.apply(self.index(), self.get(source));
      }
    };
  }

  /**
   * Creates an {@code IndexedLens} from its components: an index, a getter, and a setter.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IndexedLens<String, User, String> nameLens = IndexedLens.of(
   *     "name",                           // Index (field name)
   *     User::name,                       // Getter
   *     (user, name) -> user.withName(name)  // Setter
   * );
   *
   * // Use with index awareness
   * String fieldName = nameLens.index();        // "name"
   * Pair<String, String> indexed = nameLens.iget(user);  // Pair("name", "Alice")
   * User updated = nameLens.imodify((field, value) -> value.toUpperCase(), user);
   * }</pre>
   *
   * @param index The index value for this lens
   * @param getter A function to extract the part from the structure
   * @param setter A function to immutably update the part within the structure
   * @param <I> The index type
   * @param <S> The structure type
   * @param <A> The focused part type
   * @return A new {@code IndexedLens} instance
   */
  static <I, S, A> IndexedLens<I, S, A> of(
      I index, Function<S, A> getter, BiFunction<S, A, S> setter) {
    return new IndexedLens<>() {
      @Override
      public I index() {
        return index;
      }

      @Override
      public A get(S source) {
        return getter.apply(source);
      }

      @Override
      public S set(A newValue, S source) {
        return setter.apply(source, newValue);
      }
    };
  }

  /**
   * Creates an {@code IndexedLens} from an existing {@link Lens} plus an index.
   *
   * <p>This is useful when you have an existing lens and want to add index tracking to it.
   *
   * @param index The index value for this lens
   * @param lens The underlying lens
   * @param <I> The index type
   * @param <S> The structure type
   * @param <A> The focused part type
   * @return A new {@code IndexedLens} wrapping the provided lens
   */
  static <I, S, A> IndexedLens<I, S, A> from(I index, Lens<S, A> lens) {
    return new IndexedLens<>() {
      @Override
      public I index() {
        return index;
      }

      @Override
      public A get(S source) {
        return lens.get(source);
      }

      @Override
      public S set(A newValue, S source) {
        return lens.set(newValue, source);
      }

      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S source, Functor<F> functor) {
        return lens.modifyF(f, source, functor);
      }
    };
  }
}
