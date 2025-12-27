// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * Utility class providing {@link Traversal} and {@link Prism} instances for working with {@link
 * Maybe} types.
 *
 * <p>This class provides optics for focusing on the value inside a {@code Maybe} when it's present
 * (the {@code Just} case).
 */
@NullMarked
public final class MaybeTraversals {
  /** Private constructor to prevent instantiation. */
  private MaybeTraversals() {}

  /**
   * Creates a {@link Traversal} that focuses on the value inside a {@link Maybe} when present.
   *
   * <p>This traversal matches the {@code Just} case and allows modification of the contained value.
   * When the {@code Maybe} is {@code Nothing}, the traversal has zero targets and the result is
   * unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<Maybe<String>, String> justTraversal = MaybeTraversals.just();
   *
   * Maybe<String> present = Maybe.just("hello");
   * Maybe<String> modified = Traversals.modify(
   *     justTraversal,
   *     String::toUpperCase,
   *     present
   * );  // Maybe.just("HELLO")
   *
   * Maybe<String> nothing = Maybe.nothing();
   * Maybe<String> unchanged = Traversals.modify(
   *     justTraversal,
   *     String::toUpperCase,
   *     nothing
   * );  // Maybe.nothing() - no change
   *
   * // Composing with other traversals
   * Traversal<List<Maybe<String>>, String> allPresentStrings =
   *     Traversals.forList().andThen(justTraversal);
   * }</pre>
   *
   * @param <A> The type of the value inside the {@code Maybe}.
   * @return A {@link Traversal} focusing on the {@code Just} case.
   */
  public static <A> Traversal<Maybe<A>, A> just() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Maybe<A>> modifyF(
          Function<A, Kind<F, A>> f, Maybe<A> source, Applicative<F> applicative) {
        return source.isJust()
            ? applicative.map(Maybe::just, f.apply(source.get()))
            : applicative.of(source);
      }
    };
  }

  /**
   * Creates a {@link Prism} that focuses on the value inside a {@link Maybe} when present.
   *
   * <p>This is the same as {@link Prisms#just()}, provided here for convenience and
   * discoverability.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Maybe<String>, String> justPrism = MaybeTraversals.justPrism();
   *
   * Maybe<String> present = Maybe.just("hello");
   * Optional<String> extracted = justPrism.getOptional(present);  // Optional.of("hello")
   *
   * Maybe<String> nothing = Maybe.nothing();
   * Optional<String> noMatch = justPrism.getOptional(nothing);  // Optional.empty()
   * }</pre>
   *
   * @param <A> The type of the value inside the {@code Maybe}.
   * @return A {@link Prism} focusing on the {@code Just} case.
   */
  public static <A> Prism<Maybe<A>, A> justPrism() {
    return Prisms.just();
  }
}
