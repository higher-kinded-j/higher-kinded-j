// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * An <b>Affine</b> is an optic that focuses on <b>zero or one</b> element within a structure. It
 * combines the partial access of a {@link Prism} with the update capability of a {@link Lens}.
 *
 * <p>An Affine is the right tool when you have a field that might not exist, but when it does (or
 * when you set it), you want full lens-like access. Common use cases include:
 *
 * <ul>
 *   <li>Accessing {@code Optional<T>} fields in records
 *   <li>Working with nullable fields in legacy code
 *   <li>Navigating through optional intermediate structures
 *   <li>The result of composing a {@link Lens} with a {@link Prism}
 * </ul>
 *
 * <h2>Relationship to Other Optics</h2>
 *
 * <p>In the optic hierarchy, Affine sits between Lens and Traversal:
 *
 * <ul>
 *   <li>A {@link Lens} focuses on <b>exactly one</b> element (always present)
 *   <li>An <b>Affine</b> focuses on <b>zero or one</b> element (may be absent)
 *   <li>A {@link Traversal} focuses on <b>zero or more</b> elements
 * </ul>
 *
 * <p>The key distinction from {@link Prism}: a Prism's {@code build} creates a new structure from
 * scratch, whilst an Affine's {@code set} updates an existing structure. This makes Affine ideal
 * for optional fields within product types, whereas Prism is ideal for sum type variants.
 *
 * <h2>Laws</h2>
 *
 * <p>A well-behaved Affine must satisfy these laws:
 *
 * <ul>
 *   <li><b>Get-Set:</b> {@code getOptional(set(a, s))} = {@code Optional.of(a)} &mdash; Setting a
 *       value then getting it returns what was set
 *   <li><b>Set-Set:</b> {@code set(b, set(a, s))} = {@code set(b, s)} &mdash; Second set wins
 *   <li><b>GetOptional-Set:</b> If {@code getOptional(s)} = {@code Optional.of(a)}, then {@code
 *       set(a, s)} = {@code s} &mdash; Setting the current value changes nothing
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * // A record with an optional email field
 * record UserProfile(String name, Optional<String> email) {}
 *
 * // An Affine focusing on the email value (not the Optional wrapper)
 * Affine<UserProfile, String> emailAffine = Affine.of(
 *     UserProfile::email,
 *     (profile, newEmail) -> new UserProfile(profile.name(), Optional.of(newEmail))
 * );
 *
 * UserProfile alice = new UserProfile("Alice", Optional.of("alice@example.com"));
 * UserProfile bob = new UserProfile("Bob", Optional.empty());
 *
 * // Get: returns Optional
 * emailAffine.getOptional(alice);  // Optional.of("alice@example.com")
 * emailAffine.getOptional(bob);    // Optional.empty()
 *
 * // Set: always succeeds
 * emailAffine.set("new@example.com", alice);  // UserProfile[name=Alice, email=Optional[new@example.com]]
 * emailAffine.set("bob@example.com", bob);    // UserProfile[name=Bob, email=Optional[bob@example.com]]
 *
 * // Modify: only applies if present
 * emailAffine.modify(String::toUpperCase, alice);  // Email uppercased
 * emailAffine.modify(String::toUpperCase, bob);    // Unchanged (no email to modify)
 * }</pre>
 *
 * <p>It extends the generic {@link Optic}, specialising it for {@code S = T} and {@code A = B}.
 *
 * @param <S> The source type of the whole structure (e.g., {@code UserProfile}).
 * @param <A> The target type of the focused part (e.g., {@code String} for the email value).
 */
public interface Affine<S, A> extends Optic<S, S, A, A> {

  /**
   * Attempts to get the focused part {@code A} from the whole structure {@code S}.
   *
   * <p>This is the primary "getter" for an Affine. Unlike a {@link Lens}, it may return empty if
   * the focused element is absent.
   *
   * @param source The whole structure.
   * @return An {@link Optional} containing the focused part if present, otherwise empty.
   */
  Optional<A> getOptional(S source);

  /**
   * Sets a new value for the focused part {@code A}, returning a new, updated structure {@code S}.
   *
   * <p>Unlike a {@link Prism}, this operation always succeeds. If the focused element was
   * previously absent, it becomes present with the new value. This operation must be immutable; the
   * original {@code source} object is not changed.
   *
   * @param newValue The new value for the focused part.
   * @param source The original structure.
   * @return A new structure with the focused part set to the new value.
   */
  S set(A newValue, S source);

  /**
   * Modifies the focused part {@code A} using a pure function, if present.
   *
   * <p>If the focused element is absent, the original structure is returned unchanged. This differs
   * from {@link #set}, which always updates the structure.
   *
   * @param modifier The function to apply to the focused part.
   * @param source The whole structure.
   * @return A new structure with the modified part, or the original if the focus is absent.
   */
  default S modify(Function<A, A> modifier, S source) {
    return getOptional(source).map(a -> set(modifier.apply(a), source)).orElse(source);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The implementation for an {@code Affine} will only apply the function {@code f} if the
   * affine successfully focuses on a value. If no value is present, it returns the original
   * structure {@code s} wrapped in the {@link Applicative} context, effectively performing a no-op.
   */
  @Override
  default <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
      Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
    return getOptional(s).map(a -> app.map(newA -> set(newA, s), f.apply(a))).orElse(app.of(s));
  }

  /**
   * Views this {@code Affine} as a {@link Traversal}.
   *
   * <p>This is always possible because an {@code Affine} is fundamentally a {@code Traversal} that
   * focuses on zero or one element.
   *
   * @return A {@link Traversal} that represents this {@code Affine}.
   */
  default Traversal<S, A> asTraversal() {
    Affine<S, A> self = this;
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S source, Applicative<F> app) {
        return self.modifyF(f, source, app);
      }
    };
  }

  /**
   * Views this {@code Affine} as a {@link Fold}.
   *
   * <p>This is always possible because an {@code Affine} can be used as a read-only query that
   * focuses on zero or one element.
   *
   * @return A {@link Fold} that represents this {@code Affine}.
   */
  default Fold<S, A> asFold() {
    Affine<S, A> self = this;
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        Optional<A> opt = self.getOptional(source);
        if (opt.isPresent()) {
          return f.apply(opt.get());
        }
        return monoid.empty();
      }
    };
  }

  // ===== Composition Methods =====

  /**
   * Composes this {@code Affine<S, A>} with another {@code Affine<A, B>} to create a new {@code
   * Affine<S, B>}.
   *
   * <p>The resulting Affine focuses on zero or one element: it finds a value only if both this
   * Affine and the other Affine successfully focus.
   *
   * @param other The {@link Affine} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Affine} that focuses from {@code S} to {@code B}.
   */
  default <B> Affine<S, B> andThen(Affine<A, B> other) {
    Affine<S, A> self = this;
    return new Affine<>() {
      @Override
      public Optional<B> getOptional(S source) {
        return self.getOptional(source).flatMap(other::getOptional);
      }

      @Override
      public S set(B newValue, S source) {
        return self.getOptional(source)
            .map(a -> self.set(other.set(newValue, a), source))
            .orElse(source);
      }
    };
  }

  /**
   * Composes this {@code Affine<S, A>} with a {@code Lens<A, B>} to create a new {@code Affine<S,
   * B>}.
   *
   * <p>The resulting Affine focuses on zero or one element: it finds a value only if this Affine
   * successfully focuses, and then the Lens always succeeds on that value.
   *
   * @param lens The {@link Lens} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Affine} that focuses from {@code S} to {@code B}.
   */
  default <B> Affine<S, B> andThen(Lens<A, B> lens) {
    Affine<S, A> self = this;
    return new Affine<>() {
      @Override
      public Optional<B> getOptional(S source) {
        return self.getOptional(source).map(lens::get);
      }

      @Override
      public S set(B newValue, S source) {
        return self.getOptional(source)
            .map(a -> self.set(lens.set(newValue, a), source))
            .orElse(source);
      }
    };
  }

  /**
   * Composes this {@code Affine<S, A>} with a {@code Prism<A, B>} to create a new {@code Affine<S,
   * B>}.
   *
   * <p>The resulting Affine focuses on zero or one element: it finds a value only if both this
   * Affine successfully focuses and the Prism matches.
   *
   * @param prism The {@link Prism} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Affine} that focuses from {@code S} to {@code B}.
   */
  default <B> Affine<S, B> andThen(Prism<A, B> prism) {
    Affine<S, A> self = this;
    return new Affine<>() {
      @Override
      public Optional<B> getOptional(S source) {
        return self.getOptional(source).flatMap(prism::getOptional);
      }

      @Override
      public S set(B newValue, S source) {
        return self.getOptional(source)
            .map(a -> self.set(prism.build(newValue), source))
            .orElse(source);
      }
    };
  }

  /**
   * Composes this {@code Affine<S, A>} with an {@code Iso<A, B>} to create a new {@code Affine<S,
   * B>}.
   *
   * <p>This is possible because composing a partial focus with a lossless, two-way conversion
   * results in a new partial focus.
   *
   * @param iso The {@link Iso} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Affine} that focuses from {@code S} to {@code B}.
   */
  default <B> Affine<S, B> andThen(Iso<A, B> iso) {
    Affine<S, A> self = this;
    return Affine.of(
        s -> self.getOptional(s).map(iso::get), (s, b) -> self.set(iso.reverseGet(b), s));
  }

  /**
   * Composes this {@code Affine<S, A>} with a {@code Traversal<A, B>} to create a new {@code
   * Traversal<S, B>}.
   *
   * <p>The result is a Traversal because the inner Traversal may focus on zero or more elements.
   *
   * @param traversal The {@link Traversal} to compose with.
   * @param <B> The type of the final focused parts.
   * @return A new {@link Traversal} that focuses from {@code S} to {@code B}.
   */
  default <B> Traversal<S, B> andThen(Traversal<A, B> traversal) {
    Affine<S, A> self = this;
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<B, Kind<F, B>> f, S source, Applicative<F> app) {
        return self.getOptional(source)
            .map(a -> app.map(newA -> self.set(newA, source), traversal.modifyF(f, a, app)))
            .orElse(app.of(source));
      }
    };
  }

  // ===== Convenience Methods =====

  /**
   * Checks if this affine focuses on a value in the given structure.
   *
   * <p>This is a convenient alternative to {@code getOptional(source).isPresent()}.
   *
   * @param source The source structure to test.
   * @return {@code true} if a value is present, {@code false} otherwise.
   */
  default boolean matches(S source) {
    return getOptional(source).isPresent();
  }

  /**
   * Checks if this affine does NOT focus on a value in the given structure.
   *
   * <p>This is the logical negation of {@link #matches(Object)}.
   *
   * @param source The source structure to test.
   * @return {@code true} if no value is present, {@code false} if a value exists.
   */
  default boolean doesNotMatch(S source) {
    return !matches(source);
  }

  /**
   * Returns the focused value or a default if absent.
   *
   * <p>This is a convenient shortcut for {@code getOptional(source).orElse(defaultValue)}.
   *
   * @param defaultValue The default value to use if no focus is present.
   * @param source The source structure.
   * @return The focused value or the default value.
   */
  default A getOrElse(A defaultValue, S source) {
    return getOptional(source).orElse(defaultValue);
  }

  /**
   * Applies a function to the focused value and returns the result wrapped in an {@link Optional}.
   *
   * <p>This is useful for transforming focused values without modifying the source structure. It is
   * equivalent to {@code getOptional(source).map(f)}.
   *
   * @param f The function to apply to the focused value.
   * @param source The source structure.
   * @param <B> The result type of the function.
   * @return An {@link Optional} containing the result if a value is present, or empty otherwise.
   */
  default <B> Optional<B> mapOptional(Function<? super A, ? extends B> f, S source) {
    return getOptional(source).map(f);
  }

  /**
   * Modifies the focused part only when it is present and meets a specified condition.
   *
   * <p>This combines presence checking and conditional modification: the affine must focus on a
   * value, and that value must satisfy the predicate for modification to occur.
   *
   * @param condition The predicate that the focused value must satisfy.
   * @param modifier The function to apply if the condition is met.
   * @param source The source structure.
   * @return A new structure with the conditionally modified part, or the original if absent or
   *     condition not met.
   */
  default S modifyWhen(Predicate<? super A> condition, Function<A, A> modifier, S source) {
    return getOptional(source)
        .filter(condition)
        .map(a -> set(modifier.apply(a), source))
        .orElse(source);
  }

  /**
   * Sets a new value only when the current value is present and meets a specified condition.
   *
   * <p>This is useful for conditional updates based on the current state.
   *
   * @param condition The predicate that the current value must satisfy.
   * @param newValue The new value to set if the condition is met.
   * @param source The source structure.
   * @return A new structure with the conditionally set value, or the original if absent or
   *     condition not met.
   */
  default S setWhen(Predicate<? super A> condition, A newValue, S source) {
    return getOptional(source).filter(condition).map(a -> set(newValue, source)).orElse(source);
  }

  /**
   * Removes the focused value, setting it to empty/absent.
   *
   * <p>This operation requires a way to represent "absence" in the structure. Implementations
   * should return a structure where {@code getOptional} returns empty.
   *
   * <p>Note: This default implementation throws {@link UnsupportedOperationException}. Affines
   * created via {@link #of(Function, BiFunction)} do not support removal. Use {@link #of(Function,
   * BiFunction, Function)} to create an Affine with removal support.
   *
   * @param source The source structure.
   * @return A new structure with the focused value removed.
   * @throws UnsupportedOperationException if removal is not supported by this Affine.
   */
  default S remove(S source) {
    throw new UnsupportedOperationException(
        "This Affine does not support removal. Use Affine.of(getter, setter, remover) to create "
            + "an Affine with removal support.");
  }

  // ===== Factory Methods =====

  /**
   * Creates an {@code Affine} from its two fundamental operations: a failable getter and a setter.
   *
   * <p>The resulting Affine does not support the {@link #remove} operation.
   *
   * @param getter A function that attempts to extract part {@code A} from structure {@code S}.
   * @param setter A function that sets the part {@code A} within the structure {@code S}.
   * @param <S> The type of the whole structure.
   * @param <A> The type of the focused part.
   * @return A new {@code Affine} instance.
   */
  static <S, A> Affine<S, A> of(Function<S, Optional<A>> getter, BiFunction<S, A, S> setter) {
    return new Affine<>() {
      @Override
      public Optional<A> getOptional(S source) {
        return getter.apply(source);
      }

      @Override
      public S set(A newValue, S source) {
        return setter.apply(source, newValue);
      }
    };
  }

  /**
   * Creates an {@code Affine} with full support for getting, setting, and removing values.
   *
   * <p>This factory method creates an Affine that supports the {@link #remove} operation, which is
   * useful for optional fields that can be cleared.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<UserProfile, String> emailAffine = Affine.of(
   *     UserProfile::email,
   *     (profile, email) -> new UserProfile(profile.name(), Optional.of(email)),
   *     profile -> new UserProfile(profile.name(), Optional.empty())
   * );
   *
   * // Now remove() works
   * UserProfile withoutEmail = emailAffine.remove(profileWithEmail);
   * }</pre>
   *
   * @param getter A function that attempts to extract part {@code A} from structure {@code S}.
   * @param setter A function that sets the part {@code A} within the structure {@code S}.
   * @param remover A function that removes the part, returning a structure where the focus is
   *     absent.
   * @param <S> The type of the whole structure.
   * @param <A> The type of the focused part.
   * @return A new {@code Affine} instance with removal support.
   */
  static <S, A> Affine<S, A> of(
      Function<S, Optional<A>> getter, BiFunction<S, A, S> setter, Function<S, S> remover) {
    return new Affine<>() {
      @Override
      public Optional<A> getOptional(S source) {
        return getter.apply(source);
      }

      @Override
      public S set(A newValue, S source) {
        return setter.apply(source, newValue);
      }

      @Override
      public S remove(S source) {
        return remover.apply(source);
      }
    };
  }

  /**
   * Creates an Affine from a Lens and a Prism, which is a common pattern.
   *
   * <p>This is equivalent to {@code lens.andThen(prism)} but provided here for discoverability.
   *
   * @param lens The lens to apply first.
   * @param prism The prism to apply second.
   * @param <S> The source type.
   * @param <A> The intermediate type.
   * @param <B> The target type.
   * @return An Affine from S to B.
   */
  static <S, A, B> Affine<S, B> fromLensAndPrism(Lens<S, A> lens, Prism<A, B> prism) {
    return new Affine<>() {
      @Override
      public Optional<B> getOptional(S source) {
        return prism.getOptional(lens.get(source));
      }

      @Override
      public S set(B newValue, S source) {
        return lens.set(prism.build(newValue), source);
      }
    };
  }

  /**
   * Creates an Affine from a Prism and a Lens, which is a common pattern.
   *
   * <p>This is equivalent to {@code prism.andThen(lens)} but provided here for discoverability.
   *
   * @param prism The prism to apply first.
   * @param lens The lens to apply second.
   * @param <S> The source type.
   * @param <A> The intermediate type.
   * @param <B> The target type.
   * @return An Affine from S to B.
   */
  static <S, A, B> Affine<S, B> fromPrismAndLens(Prism<S, A> prism, Lens<A, B> lens) {
    return new Affine<>() {
      @Override
      public Optional<B> getOptional(S source) {
        return prism.getOptional(source).map(lens::get);
      }

      @Override
      public S set(B newValue, S source) {
        return prism
            .getOptional(source)
            .map(a -> prism.build(lens.set(newValue, a)))
            .orElse(source);
      }
    };
  }
}
