// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static java.util.Objects.requireNonNull;

import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

/**
 * Represents the Alternative type class, which combines the structure of {@link Applicative} with
 * choice and failure.
 *
 * <p>Alternative provides two key operations:
 *
 * <ul>
 *   <li>{@link #empty()} - Represents failure or the identity of choice
 *   <li>{@link #orElse(Kind, Supplier)} - Combines two alternatives, preferring the first if it
 *       succeeds
 * </ul>
 *
 * <p>Alternative sits at the same level as {@link Applicative} in the type class hierarchy,
 * providing a more general abstraction than {@link MonadZero}. While MonadZero provides similar
 * functionality for monads, Alternative works at the applicative level, making it applicable to a
 * broader range of types.
 *
 * <p><b>Common Use Cases:</b>
 *
 * <ul>
 *   <li><b>Parser combinators:</b> Try one parser, if it fails, try another
 *   <li><b>Validation:</b> Attempt multiple validation strategies
 *   <li><b>Non-deterministic computation:</b> Represent multiple possible results (e.g., List)
 *   <li><b>Optional values:</b> Provide fallback chains (Maybe, Optional)
 *   <li><b>Racing computations:</b> Take the first successful result
 * </ul>
 *
 * <p><b>Alternative Laws:</b>
 *
 * <p>Alternative instances must satisfy these laws:
 *
 * <pre>
 * 1. Left Identity:     orElse(empty(), () -&gt; fa) ≡ fa
 * 2. Right Identity:    orElse(fa, () -&gt; empty()) ≡ fa
 * 3. Associativity:     orElse(fa, () -&gt; orElse(fb, () -&gt; fc)) ≡ orElse(orElse(fa, () -&gt; fb), () -&gt; fc)
 * 4. Left Absorption:   ap(empty(), fa) ≡ empty()
 * 5. Right Absorption:  ap(ff, empty()) ≡ empty()
 * </pre>
 *
 * <p><b>Examples:</b>
 *
 * <pre>{@code
 * // Maybe Alternative: first Just wins
 * Alternative<MaybeKind.Witness> alt = MaybeMonad.INSTANCE;
 * Kind<MaybeKind.Witness, Integer> result =
 *     alt.orElse(
 *         alt.of(42),
 *         () -> alt.of(10)
 *     ); // Just(42)
 *
 * Kind<MaybeKind.Witness, Integer> fallback =
 *     alt.orElse(
 *         alt.empty(),
 *         () -> alt.of(10)
 *     ); // Just(10)
 *
 * // List Alternative: concatenation
 * Alternative<ListKind.Witness> listAlt = ListMonad.INSTANCE;
 * Kind<ListKind.Witness, Integer> combined =
 *     listAlt.orElse(
 *         ListKind.widen(List.of(1, 2)),
 *         () -> ListKind.widen(List.of(3, 4))
 *     ); // [1, 2, 3, 4]
 *
 * // Guard: conditional success
 * Kind<MaybeKind.Witness, Unit> check = alt.guard(someCondition);
 * // Returns of(Unit.INSTANCE) if true, empty() if false
 * }</pre>
 *
 * @param <F> The higher-kinded type witness representing the type constructor (e.g., {@code
 *     MaybeKind.Witness}, {@code ListKind.Witness}).
 * @see Applicative
 * @see MonadZero
 * @see Kind
 */
@NullMarked
public interface Alternative<F extends WitnessArity<TypeArity.Unary>> extends Applicative<F> {

  /**
   * Returns the identity element for the {@code orElse} operation, representing failure or an empty
   * result.
   *
   * <p>For different types:
   *
   * <ul>
   *   <li>{@code Maybe}: {@code Nothing}
   *   <li>{@code Optional}: {@code Optional.empty()}
   *   <li>{@code List}: {@code []} (empty list)
   *   <li>{@code Stream}: empty stream
   * </ul>
   *
   * <p>This is the left and right identity for {@link #orElse(Kind, Supplier)}.
   *
   * @param <A> The type parameter of the result, which is not present (phantom type)
   * @return A non-null {@link Kind Kind&lt;F, A&gt;} representing the empty/failure state
   */
  <A> Kind<F, A> empty();

  /**
   * Combines two alternatives, preferring the first if it succeeds, otherwise evaluating and
   * returning the second.
   *
   * <p>The second argument is lazy (provided via {@link Supplier}) to avoid unnecessary computation
   * when the first alternative succeeds.
   *
   * <p><b>Semantics by type:</b>
   *
   * <ul>
   *   <li><b>Maybe/Optional:</b> Returns {@code fa} if it's Just/present, otherwise evaluates and
   *       returns {@code fb}
   *   <li><b>List/Stream:</b> Concatenates {@code fa} and {@code fb} (always evaluates both)
   *   <li><b>Parser:</b> Tries {@code fa}, if it fails, tries {@code fb}
   * </ul>
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * // Fallback chain with Maybe
   * Kind<MaybeKind.Witness, String> result =
   *     alt.orElse(
   *         tryPrimarySource(),
   *         () -> alt.orElse(
   *             trySecondarySource(),
   *             () -> alt.of("default")
   *         )
   *     );
   * }</pre>
   *
   * @param fa The first non-null alternative {@code Kind<F, A>}
   * @param fb A non-null {@link Supplier} providing the second alternative (evaluated lazily)
   * @param <A> The type of the value
   * @return A non-null {@link Kind Kind&lt;F, A&gt;} representing the combined result
   * @throws NullPointerException if {@code fa} or {@code fb} is null
   */
  <A> Kind<F, A> orElse(Kind<F, A> fa, Supplier<Kind<F, A>> fb);

  /**
   * Conditionally returns a success value based on a boolean condition.
   *
   * <p>If the condition is {@code true}, returns {@code of(Unit.INSTANCE)}. If {@code false},
   * returns {@code empty()}.
   *
   * <p>This is useful for filtering and conditional logic in for-comprehensions and do-notation
   * style code.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * // Only proceed if user is authenticated
   * Kind<MaybeKind.Witness, Unit> authCheck =
   *     alt.guard(user.isAuthenticated());
   *
   * // In a for-comprehension context:
   * Kind<MaybeKind.Witness, Result> result =
   *     alt.flatMap(
   *         alt.guard(user.hasPermission()),
   *         unit -> performAction()
   *     );
   * // Returns empty() if guard fails, otherwise performs action
   * }</pre>
   *
   * @param condition The boolean condition to check
   * @return A non-null {@link Kind Kind&lt;F, Unit&gt;} containing {@link Unit#INSTANCE} if the
   *     condition is true, otherwise {@code empty()}
   */
  default Kind<F, Unit> guard(boolean condition) {
    return condition ? of(Unit.INSTANCE) : empty();
  }

  /**
   * Combines multiple alternatives in sequence, returning the result of the first successful one.
   *
   * <p>This is a convenience method for chaining multiple {@link #orElse(Kind, Supplier)} calls.
   * The alternatives are evaluated lazily from left to right.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<MaybeKind.Witness, Config> config =
   *     alt.orElseAll(
   *         readFromEnv(),
   *         () -> readFromFile(),
   *         () -> readFromDefaults()
   *     );
   * }</pre>
   *
   * @param fa The first non-null alternative
   * @param fb The second non-null alternative supplier
   * @param more Additional non-null alternative suppliers
   * @param <A> The type of the value
   * @return A non-null {@link Kind Kind&lt;F, A&gt;} representing the first successful alternative
   * @throws NullPointerException if any argument is null
   */
  @SuppressWarnings("unchecked")
  default <A> Kind<F, A> orElseAll(
      Kind<F, A> fa, Supplier<Kind<F, A>> fb, Supplier<Kind<F, A>>... more) {
    requireNonNull(fa, "First alternative for orElseAll cannot be null");
    requireNonNull(fb, "Second alternative for orElseAll cannot be null");
    requireNonNull(more, "Additional alternatives for orElseAll cannot be null");

    Kind<F, A> result = orElse(fa, fb);

    for (Supplier<Kind<F, A>> supplier : more) {
      requireNonNull(supplier, "Alternative supplier in orElseAll cannot be null");
      result = orElse(result, supplier);
    }

    return result;
  }

  /**
   * Folds an {@link Iterable} of alternatives into a single value by repeatedly applying {@link
   * #orElse(Kind, Supplier)}, starting from {@link #empty()}.
   *
   * <p>This is the {@code Alternative} analogue of folding a monoid, and the dynamically-sized
   * counterpart to {@link #orElseAll(Kind, Supplier, Supplier...)}. It is useful when the
   * alternatives are produced at runtime (e.g. mapping a {@code List} of strategies to their
   * results) and the varargs form is not applicable.
   *
   * <p><b>Semantics by type:</b>
   *
   * <ul>
   *   <li><b>Maybe/Optional:</b> Returns the first non-empty value, or {@code empty()} if all are
   *       empty.
   *   <li><b>List/Stream/VStream:</b> Concatenates all elements in order.
   * </ul>
   *
   * <p><b>Note on evaluation:</b> The default implementation iterates the entire collection — for
   * short-circuit types like {@code Maybe} and {@code Optional} it does not stop after the first
   * non-empty value, and an infinite {@code Iterable} will not terminate. Concrete instances may
   * provide stronger laziness; in particular, stream-like instances can override this method to
   * traverse the iterable lazily so that a lazy {@code Iterable} stays lazy end-to-end. If you need
   * lazy short-circuit evaluation of each candidate, use the varargs {@link #orElseAll(Kind,
   * Supplier, Supplier...)} form or wrap each candidate in a {@code Supplier} yourself.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Alternative<MaybeKind.Witness> alt = MaybeMonad.INSTANCE;
   * List<Kind<MaybeKind.Witness, String>> candidates = searchStrategies.stream()
   *     .map(s -> s.search(query))
   *     .toList();
   * Kind<MaybeKind.Witness, String> result = alt.orElseAll(candidates);
   * // result is the first non-empty search result, or Nothing if all failed
   * }</pre>
   *
   * <p>Concrete instances may override with a more efficient single-pass implementation (e.g.
   * {@code ListMonad} and {@code StreamMonad} avoid the quadratic concatenation the derived form
   * would produce).
   *
   * <p><b>Laws:</b>
   *
   * <pre>
   * orElseAll([])              ≡ empty()                          -- identity
   * orElseAll([x])             ≡ x                                -- singleton
   * orElseAll([x, y])          ≡ orElse(x, () -&gt; y)               -- pairwise
   * </pre>
   *
   * @param alternatives A non-null {@link Iterable} of alternative values.
   * @param <A> The type of the value within the alternatives.
   * @return A non-null {@link Kind Kind&lt;F, A&gt;} representing the combined result, or {@link
   *     #empty()} if the iterable is empty.
   * @throws NullPointerException if {@code alternatives} is null, or if it contains a null element
   *     (lazy instances may defer this check until the result is consumed).
   */
  default <A> Kind<F, A> orElseAll(Iterable<Kind<F, A>> alternatives) {
    requireNonNull(alternatives, "alternatives for orElseAll cannot be null");
    Kind<F, A> result = empty();
    for (Kind<F, A> alt : alternatives) {
      requireNonNull(alt, "Element in alternatives for orElseAll cannot be null");
      final Kind<F, A> next = alt;
      result = orElse(result, () -> next);
    }
    return result;
  }
}
