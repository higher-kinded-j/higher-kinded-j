// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;

/**
 * A **Prism** is an optic that provides a focused view into a part of a sum type (e.g., a {@code
 * sealed interface} or {@code enum}). Think of it as a safe-cracker's tool 🔬; it attempts to focus
 * on a single, specific case 'A' within a larger structure 'S' and only succeeds if the structure
 * is of that case.
 *
 * <p>A Prism is the right tool for "is-a" relationships. It provides a functional, type-safe
 * alternative to {@code instanceof} checks and casting. It is defined by two core operations: a
 * failable getter (`getOptional`) and a constructor (`build`).
 *
 * <p>It extends the generic {@link Optic}, specializing it for {@code S = T} and {@code A = B}.
 *
 * @param <S> The source type of the whole structure (e.g., a sealed interface like {@code
 *     JsonValue}).
 * @param <A> The target type of the focused case (e.g., a specific implementation like {@code
 *     JsonString}).
 */
public interface Prism<S, A> extends Optic<S, S, A, A> {

  /**
   * Attempts to get the focused part {@code A} from the whole structure {@code S}.
   *
   * <p>This is the primary "getter" for a Prism, providing a safe way to access the value of a
   * specific case of a sum type.
   *
   * @param source The whole structure.
   * @return An {@link Optional} containing the focused part if the prism matches, otherwise an
   *     empty {@code Optional}.
   */
  Optional<A> getOptional(S source);

  /**
   * Builds the whole structure {@code S} from a part {@code A}.
   *
   * <p>This is the "constructor" or reverse operation for a Prism.
   *
   * @param value The part to build the structure from.
   * @return A new instance of the whole structure {@code S}.
   */
  S build(A value);

  /**
   * {@inheritDoc}
   *
   * <p>The implementation for a {@code Prism} will only apply the function {@code f} if the prism
   * successfully matches the source {@code s}. If it does not match, it returns the original
   * structure {@code s} wrapped in the {@link Applicative} context, effectively performing a no-op.
   */
  @Override
  default <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
    return getOptional(s).map(a -> app.map(this::build, f.apply(a))).orElse(app.of(s));
  }

  /**
   * Views this {@code Prism} as a {@link Traversal}.
   *
   * <p>This is always possible because a {@code Prism} is fundamentally a {@code Traversal} that
   * focuses on zero or one element.
   *
   * @return A {@link Traversal} that represents this {@code Prism}.
   */
  default Traversal<S, A> asTraversal() {
    Prism<S, A> self = this;
    return new Traversal<>() {
      @Override
      public <F> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S source, Applicative<F> applicative) {
        return self.modifyF(f, source, applicative);
      }
    };
  }

  /**
   * Composes this {@code Prism<S, A>} with another {@code Prism<A, B>} to create a new {@code
   * Prism<S, B>}.
   *
   * <p>This specialized version is kept for efficiency and to ensure the result is correctly and
   * conveniently typed as a {@code Prism}.
   *
   * @param other The {@link Prism} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Prism} that focuses from {@code S} to {@code B}.
   */
  default <B> Prism<S, B> andThen(final Prism<A, B> other) {
    Prism<S, A> self = this;
    return new Prism<>() {
      @Override
      public Optional<B> getOptional(S source) {
        return self.getOptional(source).flatMap(other::getOptional);
      }

      @Override
      public S build(B value) {
        return self.build(other.build(value));
      }
    };
  }

  /**
   * Creates a {@code Prism} from its two fundamental operations: a failable getter and a builder
   * function.
   *
   * @param getter A function that attempts to extract part {@code A} from structure {@code S}.
   * @param builder A function that constructs the structure {@code S} from a part {@code A}.
   * @param <S> The type of the whole structure.
   * @param <A> The type of the focused part.
   * @return A new {@code Prism} instance.
   */
  static <S, A> Prism<S, A> of(Function<S, Optional<A>> getter, Function<A, S> builder) {
    return new Prism<>() {
      @Override
      public Optional<A> getOptional(S source) {
        return getter.apply(source);
      }

      @Override
      public S build(A value) {
        return builder.apply(value);
      }
    };
  }
}
