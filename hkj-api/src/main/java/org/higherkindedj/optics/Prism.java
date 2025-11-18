// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;

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
   * Views this {@code Prism} as a {@link Fold}.
   *
   * <p>This is always possible because a {@code Prism} can be used as a read-only query that
   * focuses on zero or one element.
   *
   * @return A {@link Fold} that represents this {@code Prism}.
   */
  default Fold<S, A> asFold() {
    Prism<S, A> self = this;
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        Optional<A> opt = self.getOptional(source);
        if (opt.isPresent()) {
          return f.apply(opt.get());
        } else {
          return monoid.empty();
        }
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
   * Checks if this prism matches the given structure.
   *
   * <p>This is useful for type checking without extraction, providing a cleaner alternative to
   * {@code getOptional(source).isPresent()}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();
   * JsonValue value = new JsonString("hello");
   *
   * if (stringPrism.matches(value)) {
   *   // Process as a string
   * }
   * }</pre>
   *
   * @param source The source structure to test.
   * @return {@code true} if the prism matches, {@code false} otherwise.
   */
  default boolean matches(S source) {
    return getOptional(source).isPresent();
  }

  /**
   * Provides a default value if the prism doesn't match.
   *
   * <p>This is a convenient shortcut for {@code getOptional(source).orElse(defaultValue)}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();
   * JsonValue value = new JsonNumber(42);
   *
   * String result = stringPrism.getOrElse(new JsonString("default"), value);
   * // Returns "default" since the value is a number, not a string
   * }</pre>
   *
   * @param defaultValue The default value to use if the prism doesn't match.
   * @param source The source structure.
   * @return The matched value or the default value.
   */
  default A getOrElse(A defaultValue, S source) {
    return getOptional(source).orElse(defaultValue);
  }

  /**
   * Applies a function to the matched value and returns the result wrapped in an {@link Optional}.
   *
   * <p>This is useful for transforming matched values without building them back into the source
   * structure. It's equivalent to {@code getOptional(source).map(f)}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();
   * JsonValue value = new JsonString("hello");
   *
   * Optional<Integer> length = stringPrism.mapOptional(String::length, value);
   * // Returns Optional.of(5)
   * }</pre>
   *
   * @param f The function to apply to the matched value.
   * @param source The source structure.
   * @param <B> The result type of the function.
   * @return An {@link Optional} containing the result if the prism matches, or empty otherwise.
   */
  default <B> Optional<B> mapOptional(Function<? super A, ? extends B> f, S source) {
    return getOptional(source).map(f);
  }

  /**
   * Modifies the focused part {@code A} using a pure function, if the prism matches.
   *
   * <p>This is a convenient shortcut similar to {@link Lens#modify}, but for prisms. If the prism
   * matches, the focused value is extracted, modified, and built back into the structure. If the
   * prism doesn't match, the original structure is returned unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();
   * JsonValue value = new JsonString("hello");
   *
   * JsonValue result = stringPrism.modify(
   *     s -> new JsonString(s.value().toUpperCase()),
   *     value
   * );
   * // Returns new JsonString("HELLO")
   *
   * JsonValue number = new JsonNumber(42);
   * JsonValue unchanged = stringPrism.modify(
   *     s -> new JsonString(s.value().toUpperCase()),
   *     number
   * );
   * // Returns the original JsonNumber(42) unchanged
   * }</pre>
   *
   * @param modifier The function to apply to the focused part.
   * @param source The source structure.
   * @return A new structure with the modified part, or the original structure if the prism doesn't
   *     match.
   */
  default S modify(Function<A, A> modifier, S source) {
    return getOptional(source).map(a -> build(modifier.apply(a))).orElse(source);
  }

  /**
   * Modifies the focused part only when it meets a specified condition.
   *
   * <p>This combines matching and conditional modification: the prism must match, and the extracted
   * value must satisfy the predicate for modification to occur.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();
   * JsonValue value = new JsonString("hello");
   *
   * // Only uppercase strings longer than 3 characters
   * JsonValue result = stringPrism.modifyWhen(
   *   s -> s.length() > 3,
   *   String::toUpperCase,
   *   value
   * );
   * // Returns new JsonString("HELLO")
   *
   * JsonValue shortValue = new JsonString("hi");
   * JsonValue unchanged = stringPrism.modifyWhen(
   *   s -> s.length() > 3,
   *   String::toUpperCase,
   *   shortValue
   * );
   * // Returns original JsonString("hi") since condition not met
   * }</pre>
   *
   * @param condition The predicate that the focused value must satisfy.
   * @param modifier The function to apply if the condition is met.
   * @param source The source structure.
   * @return A new structure with the conditionally modified part, or the original structure if the
   *     prism doesn't match or the condition is not met.
   */
  default S modifyWhen(Predicate<? super A> condition, Function<A, A> modifier, S source) {
    return getOptional(source).filter(condition).map(a -> build(modifier.apply(a))).orElse(source);
  }

  /**
   * Sets a new value only when the current value meets a specified condition.
   *
   * <p>This is useful for conditional updates based on the current state. The prism must match, and
   * the extracted value must satisfy the predicate for the set operation to occur.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();
   * JsonValue value = new JsonString("old");
   *
   * // Only replace non-empty strings
   * JsonValue result = stringPrism.setWhen(
   *   s -> !s.isEmpty(),
   *   "new",
   *   value
   * );
   * // Returns new JsonString("new")
   *
   * JsonValue emptyValue = new JsonString("");
   * JsonValue unchanged = stringPrism.setWhen(
   *   s -> !s.isEmpty(),
   *   "new",
   *   emptyValue
   * );
   * // Returns original JsonString("") since condition not met
   * }</pre>
   *
   * @param condition The predicate that the current value must satisfy.
   * @param newValue The new value to set if the condition is met.
   * @param source The source structure.
   * @return A new structure with the conditionally set value, or the original structure if the
   *     prism doesn't match or the condition is not met.
   */
  default S setWhen(Predicate<? super A> condition, A newValue, S source) {
    return getOptional(source).filter(condition).map(a -> build(newValue)).orElse(source);
  }

  /**
   * Chains multiple prisms, returning the first match.
   *
   * <p>This creates a new prism that tries this prism first, and if it doesn't match, tries the
   * other prism. The resulting prism uses the first prism's {@code build} function for
   * construction.
   *
   * <p>This is useful for providing fallback matching strategies or handling multiple alternative
   * cases.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<JsonValue, Number> intPrism = JsonValuePrisms.jsonInt();
   * Prism<JsonValue, Number> doublePrism = JsonValuePrisms.jsonDouble();
   *
   * Prism<JsonValue, Number> numberPrism = intPrism.orElse(doublePrism);
   *
   * // Matches either int or double
   * JsonValue intValue = new JsonInt(42);
   * Optional<Number> result1 = numberPrism.getOptional(intValue);  // Optional.of(42)
   *
   * JsonValue doubleValue = new JsonDouble(3.14);
   * Optional<Number> result2 = numberPrism.getOptional(doubleValue);  // Optional.of(3.14)
   *
   * // Building uses the first prism's builder
   * JsonValue built = numberPrism.build(100);  // Uses intPrism.build
   * }</pre>
   *
   * @param other Another prism to try if this one doesn't match.
   * @return A prism that tries this one first, then the other.
   */
  default Prism<S, A> orElse(Prism<S, A> other) {
    Prism<S, A> self = this;
    return Prism.of(
        source -> self.getOptional(source).or(() -> other.getOptional(source)),
        self::build // Always use the first prism's builder
        );
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
