// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;

/**
 * An **Iso** (Isomorphism) is a reversible optic representing a lossless, two-way conversion
 * between two types. It's a "universal translator" 🔄 or a type-safe adapter.
 *
 * <p>An Iso is the right tool when you have two types that are informationally equivalent but
 * structurally different. Common use cases include:
 *
 * <ul>
 *   <li>Converting a wrapper type to its raw value (e.g., {@code UserId <-> long}).
 *   <li>Handling data encoding (e.g., {@code byte[] <-> Base64 String}).
 *   <li>Bridging two different data structures (e.g., {@code Point <-> Tuple2}).
 * </ul>
 *
 * <p>It extends the generic {@link Optic}, specializing it for {@code S = T} and {@code A = B}.
 *
 * @param <S> The source type of the conversion.
 * @param <A> The target type of the conversion.
 */
public interface Iso<S, A> extends Optic<S, S, A, A> {

  /**
   * Performs the forward conversion from the source type {@code S} to the target type {@code A}.
   *
   * @param s The source object.
   * @return The target object.
   */
  A get(S s);

  /**
   * Performs the backward conversion from the target type {@code A} back to the source type {@code
   * S}. This is also commonly known as `build` or `review`.
   *
   * @param a The target object.
   * @return The source object.
   */
  S reverseGet(A a);

  /**
   * {@inheritDoc}
   *
   * <p>The implementation for an {@code Iso} is a pure mapping operation: it {@code get}s the
   * {@code A} from the {@code S}, applies the function {@code f}, and then uses {@code reverseGet}
   * to construct a new {@code S} from the result.
   */
  @Override
  default <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
    return app.map(this::reverseGet, f.apply(this.get(s)));
  }

  /**
   * Composes this {@code Iso<S, A>} with a {@code Lens<A, B>} to produce a new {@code Lens<S, B>}.
   *
   * <p>This is possible because composing a lossless, two-way conversion with a one-way focus
   * results in a new one-way focus. This specialized overload ensures the result is correctly and
   * conveniently typed as a {@link Lens}.
   *
   * @param other The {@link Lens} to compose with.
   * @param <B> The final target type of the new {@link Lens}.
   * @return A new {@link Lens} that focuses from {@code S} to {@code B}.
   */
  default <B> Lens<S, B> andThen(Lens<A, B> other) {
    return Lens.of(
        s -> other.get(this.get(s)), (s, b) -> this.reverseGet(other.set(b, this.get(s))));
  }

  /**
   * Composes this {@code Iso<S, A>} with a {@code Prism<A, B>} to produce a new {@code Prism<S,
   * B>}.
   *
   * <p>This is possible because composing a lossless, two-way conversion with a partial focus
   * results in a new partial focus. This specialized overload ensures the result is correctly and
   * conveniently typed as a {@link Prism}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Iso from UserId wrapper to raw long
   * Iso<UserId, Long> userIdIso = Iso.of(UserId::value, UserId::new);
   *
   * // Prism for positive longs
   * Prism<Long, Long> positivePrism = Prism.of(
   *     n -> n > 0 ? Optional.of(n) : Optional.empty(),
   *     n -> n
   * );
   *
   * // Compose: Iso + Prism = Prism
   * Prism<UserId, Long> positiveUserIdPrism = userIdIso.andThen(positivePrism);
   * }</pre>
   *
   * @param other The {@link Prism} to compose with.
   * @param <B> The final target type of the new {@link Prism}.
   * @return A new {@link Prism} that focuses from {@code S} to {@code B}.
   */
  default <B> Prism<S, B> andThen(Prism<A, B> other) {
    Iso<S, A> self = this;
    return Prism.of(s -> other.getOptional(self.get(s)), b -> self.reverseGet(other.build(b)));
  }

  /**
   * Composes this {@code Iso<S, A>} with an {@code Affine<A, B>} to produce a new {@code Affine<S,
   * B>}.
   *
   * <p>This is possible because composing a lossless, two-way conversion with a partial focus
   * results in a new partial focus. This specialized overload ensures the result is correctly and
   * conveniently typed as an {@link Affine}.
   *
   * @param other The {@link Affine} to compose with.
   * @param <B> The final target type of the new {@link Affine}.
   * @return A new {@link Affine} that focuses from {@code S} to {@code B}.
   */
  default <B> Affine<S, B> andThen(Affine<A, B> other) {
    Iso<S, A> self = this;
    return new Affine<>() {
      @Override
      public Optional<B> getOptional(S source) {
        return other.getOptional(self.get(source));
      }

      @Override
      public S set(B newValue, S source) {
        return self.reverseGet(other.set(newValue, self.get(source)));
      }
    };
  }

  /**
   * Composes this {@code Iso<S, A>} with another {@code Iso<A, B>} to produce a new {@code Iso<S,
   * B>}.
   *
   * <p>Two isomorphisms compose to produce another isomorphism. This is the most natural form of
   * Iso composition.
   *
   * @param other The {@link Iso} to compose with.
   * @param <B> The final target type of the new {@link Iso}.
   * @return A new {@link Iso} that converts from {@code S} to {@code B}.
   */
  default <B> Iso<S, B> andThen(Iso<A, B> other) {
    Iso<S, A> self = this;
    return Iso.of(s -> other.get(self.get(s)), b -> self.reverseGet(other.reverseGet(b)));
  }

  /**
   * Creates a new {@code Iso} that performs the conversion in the opposite direction.
   *
   * @return A new {@code Iso<A, S>}.
   */
  default Iso<A, S> reverse() {
    return Iso.of(this::reverseGet, this::get);
  }

  /**
   * Views this {@code Iso} as a {@link Lens}.
   *
   * <p>This is always possible because an {@code Iso} is a more powerful {@code Lens}; its "setter"
   * ({@code reverseGet}) is lossless and doesn't require the original structure.
   *
   * @return A {@link Lens} that represents this {@code Iso}.
   */
  default Lens<S, A> asLens() {
    return Lens.of(this::get, (s, a) -> this.reverseGet(a));
  }

  /**
   * Views this {@code Iso} as a {@link Traversal}.
   *
   * <p>This is always possible because an {@code Iso} can be seen as a {@code Traversal} that
   * focuses on exactly one element.
   *
   * @return A {@link Traversal} that represents this {@code Iso}.
   */
  default Traversal<S, A> asTraversal() {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
        return Iso.this.modifyF(f, s, app);
      }
    };
  }

  /**
   * Views this {@code Iso} as a {@link Fold}.
   *
   * <p>This is always possible because an {@code Iso} can be used as a read-only query that focuses
   * on exactly one element.
   *
   * @return A {@link Fold} that represents this {@code Iso}.
   */
  default Fold<S, A> asFold() {
    Iso<S, A> self = this;
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        return f.apply(self.get(source));
      }
    };
  }

  /**
   * Creates an {@code Iso} from its two fundamental functions: a getter and a reverse getter.
   *
   * @param get The function for forward conversion (from {@code S} to {@code A}).
   * @param reverseGet The function for backward conversion (from {@code A} to {@code S}).
   * @param <S> The source type.
   * @param <A> The target type.
   * @return A new {@code Iso} instance.
   */
  static <S, A> Iso<S, A> of(Function<S, A> get, Function<A, S> reverseGet) {
    return new Iso<>() {
      @Override
      public A get(S s) {
        return get.apply(s);
      }

      @Override
      public S reverseGet(A a) {
        return reverseGet.apply(a);
      }
    };
  }
}
