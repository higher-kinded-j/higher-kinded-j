// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.Nullable;

/**
 * Coyoneda is the "free functor" - it provides a Functor instance for any type constructor F
 * without requiring F to have a Functor instance.
 *
 * <p>Coyoneda works by deferring and accumulating {@code map} operations. Instead of immediately
 * applying functions, it holds them, to be applied later when the Coyoneda is "lowered" back to F
 * using an actual Functor instance, or run through an interpreter with {@link #foldMap}.
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
 * <p>When you call {@code map(f)}, instead of requiring a Functor for F, Coyoneda adds f to the
 * functions it holds. This means:
 *
 * <pre>
 * coyoneda.map(f).map(g).map(h)
 * </pre>
 *
 * <p>Results in a single Coyoneda holding f, g and h, applied in that order to each value in one
 * {@code map} when it is lowered, achieving <b>map fusion</b> automatically.
 *
 * <h2>Key Benefits</h2>
 *
 * <ul>
 *   <li><b>Automatic Functor instances:</b> Any {@code Kind<F, A>} can be lifted into Coyoneda and
 *       mapped over, even if F doesn't have a Functor instance
 *   <li><b>Map fusion:</b> Multiple map operations are fused into a single traversal, and their
 *       functions are applied in a loop, so a chain of any length does not overflow the stack
 *   <li><b>Deferred execution:</b> No actual mapping happens until {@link #lower(Functor)} is
 *       called
 *   <li><b>DSL simplification:</b> Combined with the Free monad, an instruction set needs no
 *       Functor of its own: lift each instruction with {@link #lift}, give the {@code Free} program
 *       {@link CoyonedaFunctor} as its Functor, and run it with {@link #interpreter(Natural,
 *       Functor)}
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
 * <p>These hold by construction, since map only adds a function to the ones held.
 *
 * <p>As part of the HKT simulation, {@code Coyoneda} extends {@link CoyonedaKind}, so every {@code
 * Coyoneda} is already a {@code Kind<CoyonedaKind.Witness<F>, A>}. Widening via {@link
 * CoyonedaKindHelper} is therefore a cast-free, allocation-free upcast (no wrapper object).
 *
 * @param <F> The type constructor being wrapped (witness type)
 * @param <A> The "current" result type after accumulated transformations
 * @see CoyonedaFunctor
 * @see org.higherkindedj.hkt.Functor
 */
public sealed interface Coyoneda<F extends WitnessArity<TypeArity.Unary>, A>
    extends CoyonedaKind<F, A> permits Coyoneda.Impl {

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
   * <p>This operation does NOT require a Functor instance for F. The function is added to the ones
   * already held, achieving map fusion:
   *
   * <pre>{@code
   * coyo.map(f).map(g).map(h)
   * // Holds fx with the steps f, g and h, applied in that order, in a loop
   * // Only ONE actual map when lowered!
   * }</pre>
   *
   * @param f The function to apply. Must not be null.
   * @param <B> The result type
   * @return A new Coyoneda holding the function after the existing ones
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
   * Runs this Coyoneda through a natural transformation, which needs no Functor for {@code F}.
   *
   * <p>The natural transformation turns the held value of {@code F} into one of {@code G}, and
   * {@code G}'s Functor applies the accumulated function to it in a single {@code map}. This is how
   * a value of an instruction set with no Functor of its own is interpreted:
   *
   * <pre>{@code
   * // StockOp's records fix their own result types and carry no continuation, so StockOp has no
   * // Functor: sealed interface StockOp<A> extends Kind<StockOp.Witness, A>
   * Coyoneda<StockOp.Witness, String> reserved =
   *     Coyoneda.lift(new StockOp.Reserve("LAMP", 2)).map(ok -> ok ? "reserved" : "short");
   *
   * Kind<IdKind.Witness, String> result = reserved.foldMap(inMemoryStock, IdMonad.instance());
   * }</pre>
   *
   * @param natural The interpreter from {@code F} to {@code G}. Must not be null.
   * @param functor The Functor instance for {@code G}. Must not be null.
   * @param <G> The target type constructor
   * @return The interpreted value with the accumulated function applied
   * @throws NullPointerException if natural or functor is null
   * @see #interpreter(Natural, Functor)
   */
  <G extends WitnessArity<TypeArity.Unary>> Kind<G, A> foldMap(
      Natural<F, G> natural, Functor<G> functor);

  /**
   * Gives an interpreter for a program whose instructions are Coyoneda values, such as a {@code
   * FreePath} whose Functor is {@link CoyonedaFunctor}, so that the instruction set needs no
   * Functor of its own.
   *
   * <p>Each instruction is run with {@link #foldMap(Natural, Functor)}:
   *
   * <pre>{@code
   * CoyonedaFunctor<StockOp.Witness> functor = CoyonedaFunctor.instance();
   * FreePath<CoyonedaKind.Witness<StockOp.Witness>, String> program =
   *     Path.freeLift(COYONEDA.lift(new StockOp.Reserve("LAMP", 2)), functor)
   *         .map(ok -> ok ? "reserved" : "short");
   *
   * IdMonad id = IdMonad.instance();
   * GenericPath<IdKind.Witness, String> result =
   *     program.foldMap(Coyoneda.interpreter(inMemoryStock, id), id);
   * }</pre>
   *
   * @param natural The interpreter for the instruction set. Must not be null.
   * @param functor The Functor instance for {@code G}. Must not be null.
   * @param <F> The instruction set
   * @param <G> The target type constructor
   * @return A natural transformation from {@code Coyoneda<F, _>} to {@code G}
   * @throws NullPointerException if natural or functor is null
   */
  static <F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>>
      Natural<CoyonedaKind.Witness<F>, G> interpreter(Natural<F, G> natural, Functor<G> functor) {
    requireNonNull(natural, "Natural transformation cannot be null");
    requireNonNull(functor, "Functor cannot be null");
    return new Natural<>() {
      @Override
      public <A> Kind<G, A> apply(Kind<CoyonedaKind.Witness<F>, A> fa) {
        return CoyonedaKindHelper.COYONEDA.narrow(fa).foldMap(natural, functor);
      }
    };
  }

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
    public <B> Coyoneda<F, B> map(Function<? super A, ? extends B> f) {
      requireNonNull(f, "Map function cannot be null");
      // Map fusion: the new function joins the held steps, and nothing is applied yet. The steps
      // are applied in a loop, so a long chain of maps does not nest one call inside another.
      return new Impl<>(fx, Fused.<X, A>of(transform).then(f));
    }

    @Override
    public Kind<F, A> lower(Functor<F> functor) {
      requireNonNull(functor, "Functor cannot be null");
      // The held functions are applied here, in one map of F
      return functor.map(Fused.flatten(transform), fx);
    }

    @Override
    public <G extends WitnessArity<TypeArity.Unary>> Kind<G, A> foldMap(
        Natural<F, G> natural, Functor<G> functor) {
      requireNonNull(natural, "Natural transformation cannot be null");
      requireNonNull(functor, "Functor cannot be null");
      // The held functions are applied here, in one map of G
      return functor.map(Fused.flatten(transform), natural.apply(fx));
    }

    @Override
    public Kind<F, ?> underlying() {
      return fx;
    }

    /**
     * The functions a chain of {@code map} calls has held back, applied one after another in a
     * loop. Composing with {@link Function#andThen} instead would nest one call per map, and a long
     * chain would overflow the stack when it is applied. A chain is an immutable list, newest step
     * first, and {@link #flatten} gathers its steps once for a whole {@code lower} or {@code
     * foldMap}.
     *
     * @param <X> The type the first step takes
     * @param <A> The type the last step gives
     */
    private static final class Fused<X, A> implements Function<X, A> {
      private final @Nullable Fused<X, ?> previous;
      private final Function<Object, Object> step;
      private final int size;

      private Fused(@Nullable Fused<X, ?> previous, Function<Object, Object> step) {
        this.previous = previous;
        this.step = step;
        this.size = previous == null ? 1 : previous.size + 1;
      }

      @SuppressWarnings("unchecked") // a transform from X to A, fused or not, takes X and gives A
      static <X, A> Fused<X, A> of(Function<? super X, ? extends A> transform) {
        return transform instanceof Fused<?, ?> fused
            ? (Fused<X, A>) fused
            : new Fused<>(null, (Function<Object, Object>) transform);
      }

      @SuppressWarnings("unchecked") // a step's input type is the previous step's output type
      <B> Fused<X, B> then(Function<? super A, ? extends B> next) {
        return new Fused<>(this, (Function<Object, Object>) next);
      }

      @Override
      @SuppressWarnings("unchecked") // the last step gives an A
      public A apply(X x) {
        return (A) applyAll(steps(), x);
      }

      /**
       * Gives a transform whose steps are gathered once, so that a functor applying it to many
       * values does not walk the chain for each one. A transform that is not a chain is returned as
       * it is.
       */
      @SuppressWarnings("unchecked") // as for of, and the last step gives an A
      static <X, A> Function<? super X, ? extends A> flatten(
          Function<? super X, ? extends A> transform) {
        if (transform instanceof Fused<?, ?> fused) {
          Function<Object, Object>[] steps = fused.steps();
          Function<X, A> flat = x -> (A) applyAll(steps, x);
          return flat;
        }
        return transform;
      }

      private static Object applyAll(Function<Object, Object>[] steps, Object x) {
        Object value = x;
        for (Function<Object, Object> each : steps) {
          value = each.apply(value);
        }
        return value;
      }

      @SuppressWarnings("unchecked") // a generic array cannot be created directly
      private Function<Object, Object>[] steps() {
        Function<Object, Object>[] gathered = (Function<Object, Object>[]) new Function<?, ?>[size];
        int i = size;
        for (@Nullable Fused<X, ?> node = this; node != null; node = node.previous) {
          gathered[--i] = node.step;
        }
        return gathered;
      }
    }
  }
}
