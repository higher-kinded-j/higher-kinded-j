// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.NullMarked;

/**
 * Represents a natural transformation between two type constructors F and G.
 *
 * <p>A natural transformation is a polymorphic function that converts {@code Kind<F, A>} to {@code
 * Kind<G, A>} for <em>all</em> types A, while preserving the structure. This is often written as
 * {@code F ~> G} or {@code ∀A. F[A] → G[A]} in mathematical notation.
 *
 * <h2>Core Concept</h2>
 *
 * <p>Natural transformations are "structure-preserving maps between functors". They transform the
 * container/context without knowing or caring about what's inside:
 *
 * <pre>
 * F[A] ──Natural&lt;F, G&gt;──&gt; G[A]
 *
 * Examples:
 * Maybe[A]     ──maybeToList──&gt;    List[A]      (Nothing → [], Just(x) → [x])
 * List[A]      ──headMaybe──&gt;      Maybe[A]     ([] → Nothing, [x,...] → Just(x))
 * Either[E, A] ──eitherToMaybe──&gt;  Maybe[A]     (Left → Nothing, Right(x) → Just(x))
 * Id[A]        ──idToIO──&gt;         IO[A]        (pure value → pure IO action)
 * </pre>
 *
 * <h2>Key Properties</h2>
 *
 * <ul>
 *   <li><b>Polymorphic:</b> Works for any type A without knowing what A is
 *   <li><b>Structure-Preserving:</b> Transforms the "shape" consistently
 *   <li><b>Composable:</b> Natural transformations compose to form new natural transformations
 *   <li><b>Lawful:</b> Must satisfy the naturality condition (see below)
 * </ul>
 *
 * <h2>Naturality Law</h2>
 *
 * <p>For a natural transformation {@code nat: F ~> G} to be valid, it must satisfy:
 *
 * <pre>{@code
 * // For any function f: A → B and any fa: F[A]
 * nat.apply(functorF.map(f, fa)) == functorG.map(f, nat.apply(fa))
 * }</pre>
 *
 * <p>In other words, it doesn't matter whether you map first then transform, or transform first
 * then map—the result is the same. This ensures the transformation is truly "natural" and doesn't
 * depend on the specific values inside.
 *
 * <pre>
 *           map(f)
 *    F[A] ─────────→ F[B]
 *      │               │
 *  nat │               │ nat
 *      ↓               ↓
 *    G[A] ─────────→ G[B]
 *           map(f)
 * </pre>
 *
 * <h2>Common Use Cases</h2>
 *
 * <h3>1. Free Monad Interpretation</h3>
 *
 * <p>The most common use case is interpreting Free monads. A {@code Natural<F, M>} transforms DSL
 * instructions F into a target monad M:
 *
 * <pre>{@code
 * // Define DSL operations
 * sealed interface ConsoleOp<A> { ... }
 * record ReadLine<A>() implements ConsoleOp<String> {}
 * record PrintLine<A>(String line) implements ConsoleOp<Unit> {}
 *
 * // Interpreter as natural transformation
 * Natural<ConsoleOp.Witness, IO.Witness> consoleToIO = new Natural<>() {
 *   @Override
 *   public <A> Kind<IO.Witness, A> apply(Kind<ConsoleOp.Witness, A> fa) {
 *     return switch (ConsoleOpKindHelper.narrow(fa)) {
 *       case ReadLine<?> r -> IO.of(() -> scanner.nextLine());
 *       case PrintLine<?> p -> IO.of(() -> { System.out.println(p.line()); return Unit.UNIT; });
 *     };
 *   }
 * };
 *
 * // Use with Free monad
 * Free<ConsoleOp.Witness, String> program = ...;
 * IO<String> executable = program.foldMap(consoleToIO, ioMonad);
 * }</pre>
 *
 * <h3>2. Type Conversions</h3>
 *
 * <pre>{@code
 * // Maybe to Either
 * Natural<Maybe.Witness, Either.Witness<String>> maybeToEither =
 *     fa -> MaybeKindHelper.narrow(fa)
 *         .fold(
 *             () -> Either.left("No value"),
 *             Either::right
 *         );
 *
 * // Maybe to List
 * Natural<Maybe.Witness, List.Witness> maybeToList =
 *     fa -> MaybeKindHelper.narrow(fa)
 *         .fold(
 *             () -> List.empty(),
 *             List::of
 *         );
 * }</pre>
 *
 * <h3>3. Monad Transformer Lifting</h3>
 *
 * <pre>{@code
 * // Lift M[A] into EitherT[M, E, A]
 * Natural<M, EitherT.Witness<M, E>> liftToEitherT =
 *     ma -> EitherT.liftF(ma, monadM);
 * }</pre>
 *
 * <h2>Composition</h2>
 *
 * <p>Natural transformations compose horizontally:
 *
 * <pre>{@code
 * Natural<F, G> fg = ...;
 * Natural<G, H> gh = ...;
 * Natural<F, H> fh = fg.andThen(gh);  // F ~> G ~> H = F ~> H
 * }</pre>
 *
 * <h2>Relationship to Other Concepts</h2>
 *
 * <ul>
 *   <li><b>Functor:</b> Natural transformations operate on functors; naturality law involves map
 *   <li><b>Free Monad:</b> Interpretation via foldMap uses natural transformations
 *   <li><b>Monad Transformers:</b> lift operations are natural transformations
 *   <li><b>Coyoneda:</b> lowering from Coyoneda uses natural transformation to apply the functor
 * </ul>
 *
 * <h2>Implementation Notes</h2>
 *
 * <p>This interface uses a generic method {@code <A> apply(Kind<F, A>)} rather than a type
 * parameter because natural transformations must work for <em>all</em> types A simultaneously.
 * Java's type system doesn't directly support higher-ranked types, but this encoding achieves the
 * same effect.
 *
 * @param <F> The source type constructor (witness type)
 * @param <G> The target type constructor (witness type)
 * @see Kind
 * @see Functor
 */
@NullMarked
@FunctionalInterface
public interface Natural<F extends WitnessArity<?>, G extends WitnessArity<?>> {

  /**
   * Applies this natural transformation to convert a value in context F to context G.
   *
   * <p>This method must be implemented to work uniformly for all types A, transforming the
   * structure F into structure G without inspecting or depending on the specific type A.
   *
   * <p><b>Implementation Requirements:</b>
   *
   * <ul>
   *   <li>Must work for any type A (cannot pattern match on A or use instanceof)
   *   <li>Must satisfy the naturality law (see class documentation)
   *   <li>Should be pure (no side effects)
   *   <li>Should be total (defined for all inputs)
   * </ul>
   *
   * <p><b>Example Implementation:</b>
   *
   * <pre>{@code
   * // Natural transformation from Maybe to List
   * Natural<Maybe.Witness, List.Witness> maybeToList = new Natural<>() {
   *   @Override
   *   public <A> Kind<List.Witness, A> apply(Kind<Maybe.Witness, A> fa) {
   *     Maybe<A> maybe = MaybeKindHelper.MAYBE.narrow(fa);
   *     return maybe.fold(
   *         () -> ListKindHelper.LIST.widen(List.empty()),
   *         value -> ListKindHelper.LIST.widen(List.of(value))
   *     );
   *   }
   * };
   * }</pre>
   *
   * @param fa The value in context F to transform. Must not be null.
   * @param <A> The type of the value inside the context (polymorphic)
   * @return The transformed value in context G. Must not be null.
   * @throws NullPointerException if fa is null (implementation-dependent)
   */
  <A> Kind<G, A> apply(Kind<F, A> fa);

  /**
   * Composes this natural transformation with another, creating a transformation from F to H.
   *
   * <p>Given {@code this: F ~> G} and {@code after: G ~> H}, produces {@code F ~> H}.
   *
   * <pre>
   *     this        after
   * F ──────→ G ──────→ H
   *
   *    this.andThen(after)
   * F ─────────────────→ H
   * </pre>
   *
   * <p><b>Associativity:</b> Composition is associative:
   *
   * <pre>{@code
   * (f.andThen(g)).andThen(h) == f.andThen(g.andThen(h))
   * }</pre>
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Natural<Maybe.Witness, Either.Witness<String>> maybeToEither = ...;
   * Natural<Either.Witness<String>, IO.Witness> eitherToIO = ...;
   *
   * // Compose to get Maybe ~> IO directly
   * Natural<Maybe.Witness, IO.Witness> maybeToIO = maybeToEither.andThen(eitherToIO);
   * }</pre>
   *
   * @param after The natural transformation to apply after this one. Must not be null.
   * @param <H> The final target type constructor
   * @return A composed natural transformation from F to H
   * @throws NullPointerException if after is null
   */
  default <H extends WitnessArity<?>> Natural<F, H> andThen(Natural<G, H> after) {
    requireNonNull(after, "after natural transformation cannot be null");
    return new Natural<>() {
      @Override
      public <A> Kind<H, A> apply(Kind<F, A> fa) {
        return after.apply(Natural.this.apply(fa));
      }
    };
  }

  /**
   * Composes another natural transformation with this one, creating a transformation from E to G.
   *
   * <p>Given {@code before: E ~> F} and {@code this: F ~> G}, produces {@code E ~> G}.
   *
   * <p>This is the reverse of {@link #andThen(Natural)}:
   *
   * <pre>{@code
   * before.andThen(this) == this.compose(before)
   * }</pre>
   *
   * @param before The natural transformation to apply before this one. Must not be null.
   * @param <E> The initial source type constructor
   * @return A composed natural transformation from E to G
   * @throws NullPointerException if before is null
   */
  default <E extends WitnessArity<?>> Natural<E, G> compose(Natural<E, F> before) {
    requireNonNull(before, "before natural transformation cannot be null");
    return before.andThen(this);
  }

  /**
   * Returns the identity natural transformation for type constructor F.
   *
   * <p>The identity transformation returns its input unchanged:
   *
   * <pre>{@code
   * Natural<F, F> id = Natural.identity();
   * id.apply(fa) == fa  // for all fa
   * }</pre>
   *
   * <p><b>Identity Laws:</b>
   *
   * <pre>{@code
   * // Left identity
   * Natural.identity().andThen(nat) == nat
   *
   * // Right identity
   * nat.andThen(Natural.identity()) == nat
   * }</pre>
   *
   * <p><b>Use Cases:</b>
   *
   * <ul>
   *   <li>Default/no-op transformation
   *   <li>Base case in recursive transformations
   *   <li>Testing and verification
   * </ul>
   *
   * @param <F> The type constructor
   * @return The identity natural transformation
   */
  @SuppressWarnings("unchecked")
  static <F extends WitnessArity<?>> Natural<F, F> identity() {
    return (Natural<F, F>) IDENTITY;
  }

  /** Cached identity instance to avoid allocations on each call. */
  @SuppressWarnings("rawtypes")
  Natural IDENTITY =
      new Natural() {
        @Override
        public Kind apply(Kind fa) {
          return fa;
        }
      };
}
