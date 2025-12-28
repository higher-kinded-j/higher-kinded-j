// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Coyoneda is the "free functor" - it provides a Functor instance for any type constructor F
 * without requiring F to have a Functor instance.
 *
 * <p>Coyoneda works by deferring and accumulating {@code map} operations. Instead of immediately
 * applying functions, it stores them as a composed function that will be applied later when the
 * Coyoneda is "lowered" back to F using an actual Functor instance.
 *
 * <h2>Core Concept</h2>
 *
 * <p>Coyoneda encapsulates:
 *
 * <ul>
 *   <li>A value of type {@code Kind<F, X>} where X is an "existential" (hidden) type
 *   <li>A function {@code X → A} representing the accumulated transformations
 * </ul>
 *
 * <p>When you call {@code map(f)}, instead of requiring a Functor for F, Coyoneda simply composes f
 * with the existing transformation function. This means:
 *
 * <pre>
 * coyoneda.map(f).map(g).map(h)
 * </pre>
 *
 * <p>Results in a single Coyoneda with the composed function {@code h ∘ g ∘ f}, achieving <b>map
 * fusion</b> automatically.
 *
 * <h2>Key Benefits</h2>
 *
 * <ul>
 *   <li><b>Automatic Functor instances:</b> Any {@code Kind<F, A>} can be lifted into Coyoneda and
 *       mapped over, even if F doesn't have a Functor instance
 *   <li><b>Map fusion:</b> Multiple map operations are fused into a single traversal
 *   <li><b>Deferred execution:</b> No actual mapping happens until {@link #lower(Functor)} is
 *       called
 *   <li><b>DSL simplification:</b> Combined with Free monad, eliminates the need for instruction
 *       sets to be Functors
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Lift any Kind<F, A> into Coyoneda
 * Kind<MyType.Witness, Integer> myValue = ...;
 * Coyoneda<MyType.Witness, Integer> coyo = Coyoneda.lift(myValue);
 *
 * // Map without needing a Functor instance for MyType
 * Coyoneda<MyType.Witness, String> mapped = coyo
 *     .map(x -> x * 2)
 *     .map(x -> x + 1)
 *     .map(Object::toString);
 * // All three maps are fused into one function!
 *
 * // When ready, lower back to Kind<F, A> using a Functor
 * Functor<MyType.Witness> functor = ...;
 * Kind<MyType.Witness, String> result = mapped.lower(functor);
 * // Only ONE actual map operation is performed on MyType
 * }</pre>
 *
 * <h2>Mathematical Background</h2>
 *
 * <p>Coyoneda is the <em>covariant Yoneda lemma</em> applied to functors. For any functor F and
 * type A:
 *
 * <pre>
 * Coyoneda[F, A] ≅ F[A]
 * </pre>
 *
 * <p>This isomorphism is witnessed by:
 *
 * <ul>
 *   <li>{@code lift}: F[A] → Coyoneda[F, A] (wraps with identity function)
 *   <li>{@code lower}: Coyoneda[F, A] → F[A] (applies the accumulated function via Functor.map)
 * </ul>
 *
 * <h2>Functor Laws</h2>
 *
 * <p>The Coyoneda Functor instance automatically satisfies the functor laws:
 *
 * <ul>
 *   <li><b>Identity:</b> {@code coyo.map(x -> x) ≡ coyo}
 *   <li><b>Composition:</b> {@code coyo.map(f).map(g) ≡ coyo.map(g.compose(f))}
 * </ul>
 *
 * <p>These hold by construction since map simply composes functions.
 *
 * @param <F> The type constructor being wrapped (witness type)
 * @param <A> The "current" result type after accumulated transformations
 * @see CoyonedaFunctor
 * @see org.higherkindedj.hkt.Functor
 */
public sealed interface Coyoneda<F extends WitnessArity<TypeArity.Unary>, A> permits Coyoneda.Impl {

  /**
   * Lifts a {@code Kind<F, A>} into Coyoneda with the identity transformation.
   *
   * <p>This is the canonical way to create a Coyoneda. The value is wrapped with the identity
   * function, ready for subsequent map operations.
   *
   * <pre>{@code
   * Kind<Maybe.Witness, Integer> maybe = MAYBE.just(42);
   * Coyoneda<Maybe.Witness, Integer> coyo = Coyoneda.lift(maybe);
   * }</pre>
   *
   * @param fa The value to lift into Coyoneda. Must not be null.
   * @param <F> The type constructor
   * @param <A> The value type
   * @return A Coyoneda wrapping the value with identity transformation
   * @throws NullPointerException if fa is null
   */
  static <F extends WitnessArity<TypeArity.Unary>, A> Coyoneda<F, A> lift(Kind<F, A> fa) {
    requireNonNull(fa, "Kind to lift cannot be null");
    return new Impl<>(fa, Function.identity());
  }

  /**
   * Creates a Coyoneda from a value and a transformation function.
   *
   * <p>This factory method is useful when you already have a transformation to apply. It's
   * equivalent to {@code Coyoneda.lift(fx).map(transform)} but more direct.
   *
   * @param fx The underlying value. Must not be null.
   * @param transform The transformation function. Must not be null.
   * @param <F> The type constructor
   * @param <X> The original value type in F
   * @param <A> The result type after transformation
   * @return A Coyoneda with the given value and transformation
   * @throws NullPointerException if fx or transform is null
   */
  static <F extends WitnessArity<TypeArity.Unary>, X, A> Coyoneda<F, A> apply(
      Kind<F, X> fx, Function<? super X, ? extends A> transform) {
    requireNonNull(fx, "Kind value cannot be null");
    requireNonNull(transform, "Transform function cannot be null");
    return new Impl<>(fx, transform);
  }

  /**
   * Maps a function over this Coyoneda, accumulating it with existing transformations.
   *
   * <p>This operation does NOT require a Functor instance for F. The function is simply composed
   * with the existing transformation, achieving map fusion:
   *
   * <pre>{@code
   * coyo.map(f).map(g).map(h)
   * // Internally becomes: Coyoneda(fx, h.compose(g).compose(f))
   * // Only ONE actual map when lowered!
   * }</pre>
   *
   * @param f The function to apply. Must not be null.
   * @param <B> The result type
   * @return A new Coyoneda with the function composed into the transformation
   * @throws NullPointerException if f is null
   */
  <B> Coyoneda<F, B> map(Function<? super A, ? extends B> f);

  /**
   * Lowers this Coyoneda back to {@code Kind<F, A>} by applying the accumulated transformation.
   *
   * <p>This is where the actual mapping happens. The Functor instance for F is used to apply all
   * the accumulated transformations in a single map operation.
   *
   * <pre>{@code
   * Coyoneda<Maybe.Witness, String> coyo = Coyoneda.lift(MAYBE.just(42))
   *     .map(x -> x * 2)
   *     .map(Object::toString);
   *
   * // Lower using Maybe's Functor
   * Kind<Maybe.Witness, String> result = coyo.lower(maybeFunctor);
   * // Result: Just("84")
   * }</pre>
   *
   * @param functor The Functor instance for F. Must not be null.
   * @return The value with all transformations applied
   * @throws NullPointerException if functor is null
   */
  Kind<F, A> lower(Functor<F> functor);

  /**
   * Returns the underlying Kind value before any transformations.
   *
   * <p>This provides access to the original wrapped value. Note that the type parameter is
   * existentially quantified (hidden), so this returns a wildcard type.
   *
   * @return The underlying Kind value
   */
  Kind<F, ?> underlying();

  /**
   * Internal implementation of Coyoneda.
   *
   * <p>This record holds:
   *
   * <ul>
   *   <li>{@code fx}: The underlying value of type {@code Kind<F, X>}
   *   <li>{@code transform}: The accumulated transformation from X to A
   * </ul>
   *
   * <p>The type parameter X is existentially quantified - it's hidden from the outside and only
   * known internally. This is achieved by having the record be package-private with a wildcard type
   * in some contexts.
   *
   * @param <F> The type constructor
   * @param <X> The original value type (existential)
   * @param <A> The current result type
   */
  record Impl<F extends WitnessArity<TypeArity.Unary>, X, A>(
      Kind<F, X> fx, Function<? super X, ? extends A> transform) implements Coyoneda<F, A> {

    /**
     * Creates a new Impl with validation.
     *
     * @param fx The underlying value
     * @param transform The transformation function
     */
    public Impl {
      requireNonNull(fx, "Underlying Kind cannot be null");
      requireNonNull(transform, "Transform function cannot be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <B> Coyoneda<F, B> map(Function<? super A, ? extends B> f) {
      requireNonNull(f, "Map function cannot be null");
      // Compose the new function with the existing transformation
      // This achieves map fusion: multiple maps become one composed function
      Function<X, B> composed = ((Function<X, A>) transform).andThen((Function<A, B>) f);
      return new Impl<>(fx, composed);
    }

    @Override
    public Kind<F, A> lower(Functor<F> functor) {
      requireNonNull(functor, "Functor cannot be null");
      // Apply the accumulated transformation using the Functor
      // This is the only place where actual mapping happens
      return functor.map(transform, fx);
    }

    @Override
    public Kind<F, ?> underlying() {
      return fx;
    }
  }
}
