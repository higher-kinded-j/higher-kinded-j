// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static java.util.Objects.requireNonNull;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;

/**
 * Free Applicative functor for independent/parallel composition.
 *
 * <p>FreeAp is the "free applicative" - it captures applicative structure (independent
 * computations) without requiring the underlying type F to have any instances. Unlike the Free
 * monad which captures sequential, dependent computations, FreeAp captures computations that are
 * independent and can potentially be executed in parallel.
 *
 * <h2>Core Concept</h2>
 *
 * <p>FreeAp has three constructors:
 *
 * <ul>
 *   <li>{@code Pure(a)} - A completed computation with value a
 *   <li>{@code Lift(fa)} - A suspended single operation in F
 *   <li>{@code Ap(ff, fa)} - Application of a wrapped function to a wrapped value (independent
 *       computations)
 * </ul>
 *
 * <p>The key insight is that in {@code Ap(ff, fa)}, both {@code ff} and {@code fa} are
 * <em>independent</em> - neither depends on the other's result. This is in contrast to Free monad's
 * FlatMapped where the continuation depends on the previous result.
 *
 * <h2>Key Benefits</h2>
 *
 * <ul>
 *   <li><b>Parallel execution:</b> Interpreters can safely parallelize independent computations
 *   <li><b>Static analysis:</b> The structure can be analyzed before interpretation
 *   <li><b>Batching:</b> Similar operations can be batched (e.g., multiple DB queries)
 *   <li><b>Optimization:</b> Independent computations can be reordered for efficiency
 *   <li><b>Validation:</b> Collect all errors rather than failing on first (with Validated)
 * </ul>
 *
 * <h2>Free Monad vs Free Applicative</h2>
 *
 * <table border="1">
 * <tr><th>Free Monad</th><th>FreeAp</th></tr>
 * <tr><td>Sequential/dependent</td><td>Independent/parallel</td></tr>
 * <tr><td>flatMap: A → Free[F, B]</td><td>ap: FreeAp[F, A→B] × FreeAp[F, A]</td></tr>
 * <tr><td>Next step depends on previous result</td><td>Steps are independent</td></tr>
 * <tr><td>Cannot analyze structure ahead of time</td><td>Full structure visible before interpretation</td></tr>
 * </table>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Define independent operations
 * sealed interface UserOp<A> { ... }
 * record GetUser(int id) implements UserOp<User> {}
 * record GetPosts(int userId) implements UserOp<List<Post>> {}
 *
 * // Build a FreeAp program with independent fetches
 * FreeAp<UserOp.Witness, User> userFetch = FreeAp.lift(getUser(1));
 * FreeAp<UserOp.Witness, List<Post>> postsFetch = FreeAp.lift(getPosts(1));
 *
 * // Combine them - these are INDEPENDENT and can be parallelized
 * FreeAp<UserOp.Witness, UserProfile> profile = userFetch.map2(
 *     postsFetch,
 *     (user, posts) -> new UserProfile(user, posts)
 * );
 *
 * // Interpret - smart interpreter can execute both fetches in parallel
 * UserProfile result = profile.foldMap(interpreter, ioApplicative);
 * }</pre>
 *
 * <h2>Applicative Laws</h2>
 *
 * <p>FreeAp satisfies the Applicative laws by construction:
 *
 * <ul>
 *   <li><b>Identity:</b> {@code pure(id).ap(fa) ≡ fa}
 *   <li><b>Homomorphism:</b> {@code pure(f).ap(pure(x)) ≡ pure(f(x))}
 *   <li><b>Interchange:</b> {@code ff.ap(pure(x)) ≡ pure(f -> f(x)).ap(ff)}
 *   <li><b>Composition:</b> {@code pure(.).ap(ff).ap(fg).ap(fa) ≡ ff.ap(fg.ap(fa))}
 * </ul>
 *
 * @param <F> The type constructor representing the instruction set (witness type)
 * @param <A> The result type
 * @see FreeApApplicative
 * @see org.higherkindedj.hkt.Applicative
 * @see org.higherkindedj.hkt.free.Free
 */
public sealed interface FreeAp<F, A> permits FreeAp.Pure, FreeAp.Lift, FreeAp.Ap {

  /**
   * Terminal case representing a pure value.
   *
   * <p>A computation that immediately produces a value without any effects.
   *
   * @param <F> The functor type
   * @param <A> The value type
   */
  record Pure<F, A>(A value) implements FreeAp<F, A> {}

  /**
   * Suspended computation lifting a single instruction in F.
   *
   * <p>Represents a single operation in the instruction set F that will produce a value of type A.
   *
   * @param <F> The functor type
   * @param <A> The result type
   */
  record Lift<F, A>(Kind<F, A> fa) implements FreeAp<F, A> {
    public Lift {
      requireNonNull(fa, "Lifted Kind cannot be null");
    }
  }

  /**
   * Application of a wrapped function to a wrapped value.
   *
   * <p>This represents two <em>independent</em> computations: one producing a function, one
   * producing a value. Neither depends on the other's result, enabling parallel execution.
   *
   * <p>Note: The type parameter X is existentially quantified (hidden from external users).
   *
   * @param <F> The functor type
   * @param <X> The intermediate type (existential)
   * @param <A> The final result type
   */
  record Ap<F, X, A>(FreeAp<F, Function<X, A>> ff, FreeAp<F, X> fa) implements FreeAp<F, A> {
    public Ap {
      requireNonNull(ff, "Function FreeAp cannot be null");
      requireNonNull(fa, "Value FreeAp cannot be null");
    }
  }

  /**
   * Creates a FreeAp from a pure value.
   *
   * @param value The value to lift
   * @param <F> The functor type
   * @param <A> The value type
   * @return A FreeAp containing the pure value
   */
  static <F, A> FreeAp<F, A> pure(A value) {
    return new Pure<>(value);
  }

  /**
   * Lifts a single instruction in F into FreeAp.
   *
   * @param fa The instruction to lift. Must not be null.
   * @param <F> The functor type
   * @param <A> The result type
   * @return A FreeAp containing the lifted instruction
   * @throws NullPointerException if fa is null
   */
  static <F, A> FreeAp<F, A> lift(Kind<F, A> fa) {
    requireNonNull(fa, "Kind to lift cannot be null");
    return new Lift<>(fa);
  }

  /**
   * Maps a function over the result of this FreeAp.
   *
   * @param f The function to apply. Must not be null.
   * @param <B> The result type
   * @return A new FreeAp with the function applied
   * @throws NullPointerException if f is null
   */
  default <B> FreeAp<F, B> map(Function<? super A, ? extends B> f) {
    requireNonNull(f, "Map function cannot be null");
    return ap(pure(f));
  }

  /**
   * Applies a FreeAp containing a function to this FreeAp.
   *
   * <p>This is the core applicative operation. The function and this value are <em>independent</em>
   * computations - neither depends on the other's result.
   *
   * @param ff The FreeAp containing the function. Must not be null.
   * @param <B> The result type
   * @return A new FreeAp representing the application
   * @throws NullPointerException if ff is null
   */
  default <B> FreeAp<F, B> ap(FreeAp<F, ? extends Function<? super A, ? extends B>> ff) {
    requireNonNull(ff, "Function FreeAp cannot be null");
    // Need to handle the variance carefully
    @SuppressWarnings("unchecked")
    FreeAp<F, Function<A, B>> safeFF = (FreeAp<F, Function<A, B>>) (FreeAp<F, ?>) ff;
    return new Ap<>(safeFF, this);
  }

  /**
   * Combines this FreeAp with another using a combining function.
   *
   * <p>Both computations are independent and can be executed in parallel.
   *
   * @param fb The other FreeAp. Must not be null.
   * @param combine The function to combine results. Must not be null.
   * @param <B> The type of the other value
   * @param <C> The result type
   * @return A new FreeAp combining both values
   * @throws NullPointerException if fb or combine is null
   */
  default <B, C> FreeAp<F, C> map2(
      FreeAp<F, B> fb, BiFunction<? super A, ? super B, ? extends C> combine) {
    requireNonNull(fb, "Second FreeAp cannot be null");
    requireNonNull(combine, "Combine function cannot be null");
    return fb.ap(this.map(a -> b -> combine.apply(a, b)));
  }

  /**
   * Interprets this FreeAp into a target applicative G using a natural transformation.
   *
   * <p>The natural transformation converts each instruction in F to the target applicative G. The
   * Applicative instance for G is used to combine the results.
   *
   * <h2>Parallel Execution</h2>
   *
   * <p>If the target Applicative supports parallel execution (like a parallel IO or
   * CompletableFuture), the independent computations in FreeAp can be executed in parallel.
   *
   * <h2>Example</h2>
   *
   * <pre>{@code
   * // Define interpreter
   * Natural<MyOp.Witness, IO.Witness> interpreter = ...;
   *
   * // Interpret the FreeAp program
   * FreeAp<MyOp.Witness, Result> program = ...;
   * Kind<IO.Witness, Result> ioResult = program.foldMap(interpreter, ioApplicative);
   * }</pre>
   *
   * @param transform The natural transformation from F to G. Must not be null.
   * @param applicative The Applicative instance for G. Must not be null.
   * @param <G> The target applicative type
   * @return The interpreted result in G
   * @throws NullPointerException if transform or applicative is null
   */
  default <G> Kind<G, A> foldMap(Natural<F, G> transform, Applicative<G> applicative) {
    requireNonNull(transform, "Natural transformation cannot be null");
    requireNonNull(applicative, "Applicative cannot be null");
    return interpretFreeAp(this, transform, applicative);
  }

  /**
   * Analyses the structure of this FreeAp, returning information in the applicative G.
   *
   * <p>This is similar to {@link #foldMap} but is specifically named to emphasise that the
   * structure can be analysed before any actual execution happens.
   *
   * @param transform The natural transformation from F to G. Must not be null.
   * @param applicative The Applicative instance for G. Must not be null.
   * @param <G> The target applicative type
   * @return The analysis result in G
   * @throws NullPointerException if transform or applicative is null
   */
  default <G> Kind<G, A> analyse(Natural<F, G> transform, Applicative<G> applicative) {
    return foldMap(transform, applicative);
  }

  /**
   * Internal helper that interprets a FreeAp using the given natural transformation and
   * applicative.
   *
   * @param freeAp The FreeAp to interpret
   * @param transform The natural transformation from F to G
   * @param applicative The Applicative instance for G
   * @param <F> The source functor type
   * @param <G> The target applicative type
   * @param <A> The result type
   * @return The interpreted result in G
   */
  private static <F, G, A> Kind<G, A> interpretFreeAp(
      FreeAp<F, A> freeAp, Natural<F, G> transform, Applicative<G> applicative) {

    return switch (freeAp) {
      case Pure<F, A> pure ->
          // Pure values are lifted into the applicative
          applicative.of(pure.value());

      case Lift<F, A> lift ->
          // Lifted instructions are transformed
          transform.apply(lift.fa());

      case Ap<F, ?, A> ap -> {
        // Handle the Ap case by recursively interpreting both branches
        // Both branches are INDEPENDENT - this is where parallelism can happen
        @SuppressWarnings("unchecked")
        Ap<F, Object, A> typed = (Ap<F, Object, A>) ap;

        Kind<G, Function<Object, A>> gf = interpretFreeAp(typed.ff(), transform, applicative);
        Kind<G, Object> ga = interpretFreeAp(typed.fa(), transform, applicative);

        yield applicative.ap(gf, ga);
      }
    };
  }

  /**
   * Retract this FreeAp back to F when F is an Applicative.
   *
   * <p>This is the inverse of lift when F has an Applicative instance. It's equivalent to:
   *
   * <pre>{@code
   * freeAp.foldMap(Natural.identity(), applicative)
   * }</pre>
   *
   * @param applicative The Applicative instance for F. Must not be null.
   * @return The computation in F
   * @throws NullPointerException if applicative is null
   */
  default Kind<F, A> retract(Applicative<F> applicative) {
    requireNonNull(applicative, "Applicative cannot be null");
    return foldMap(Natural.identity(), applicative);
  }
}
