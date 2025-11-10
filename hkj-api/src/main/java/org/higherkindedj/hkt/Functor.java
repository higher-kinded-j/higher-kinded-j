// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * Represents the Functor type class, the foundation of functional data transformation in
 * higher-kinded types.
 *
 * <p>A Functor is a type constructor {@code F} that supports a {@code map} operation, allowing you
 * to apply a pure function to the value(s) inside a context or structure without changing the
 * context itself. Think of it as a "wrapper-preserving transformer" that lets you transform wrapped
 * values while maintaining their wrapper.
 *
 * <h2>Core Concept</h2>
 *
 * <p>If you have a value wrapped in some context {@code F<A>} (like {@code Optional<String>},
 * {@code List<Integer>}, or {@code Future<User>}), and you have a function {@code A -> B}, the
 * {@code map} operation lets you apply that function to get {@code F<B>}, preserving the context:
 *
 * <pre>
 * F<A> + (A -> B) ──map──> F<B>
 * </pre>
 *
 * <h2>Common Examples</h2>
 *
 * <pre>{@code
 * // Example 1: Optional (Maybe)
 * Optional<String> name = Optional.of("Alice");
 * Optional<Integer> length = functor.map(String::length, name);
 * // Result: Optional.of(5)
 *
 * Optional<String> empty = Optional.empty();
 * Optional<Integer> stillEmpty = functor.map(String::length, empty);
 * // Result: Optional.empty() - context preserved
 *
 * // Example 2: List
 * List<Integer> numbers = List.of(1, 2, 3, 4);
 * List<Integer> doubled = functor.map(n -> n * 2, numbers);
 * // Result: List.of(2, 4, 6, 8)
 *
 * // Example 3: Future/CompletableFuture
 * CompletableFuture<User> futureUser = fetchUser(userId);
 * CompletableFuture<String> futureName = functor.map(User::getName, futureUser);
 * // Result: A future that will contain the user's name when completed
 *
 * // Example 4: Either (Result type)
 * Either<Error, Integer> result = Right(42);
 * Either<Error, String> message = functor.map(n -> "Value: " + n, result);
 * // Result: Right("Value: 42")
 *
 * Either<Error, Integer> failure = Left(new Error("Not found"));
 * Either<Error, String> stillFailure = functor.map(n -> "Value: " + n, failure);
 * // Result: Left(Error("Not found")) - error preserved
 * }</pre>
 *
 * <h2>Key Characteristics</h2>
 *
 * <ul>
 *   <li><b>Structure Preservation:</b> The shape and context of the container remain unchanged;
 *       only the values inside are transformed.
 *   <li><b>Pure Functions Only:</b> The function passed to {@code map} must be pure (no side
 *       effects), though it transforms values within an effectful context.
 *   <li><b>Composability:</b> Multiple {@code map} operations can be chained together, and they
 *       compose according to function composition.
 *   <li><b>No Context Inspection:</b> Functor cannot look at or change the context itself - it only
 *       transforms the values inside.
 * </ul>
 *
 * <h2>What Functor Cannot Do</h2>
 *
 * <p>Functor is intentionally limited. It cannot:
 *
 * <ul>
 *   <li><b>Flatten nested structures:</b> Cannot turn {@code F<F<A>>} into {@code F<A>} (that's
 *       {@link Monad})
 *   <li><b>Combine multiple contexts:</b> Cannot combine {@code F<A>} and {@code F<B>} into {@code
 *       F<C>} (that's {@link Applicative})
 *   <li><b>Create new contexts:</b> Cannot lift a pure value {@code A} into {@code F<A>} (that's
 *       {@link Applicative})
 *   <li><b>Sequence dependent operations:</b> Cannot chain operations where the next step depends
 *       on the previous result (that's {@link Monad})
 * </ul>
 *
 * <h2>Functor Laws</h2>
 *
 * <p>All Functor implementations must satisfy two fundamental laws to ensure predictable and
 * composable behaviour:
 *
 * <h3>1. Identity Law</h3>
 *
 * <p>Mapping with the identity function does nothing:
 *
 * <pre>{@code
 * map(x -> x, fa) ≡ fa
 * }</pre>
 *
 * <p>This ensures that {@code map} doesn't add any extra behaviour beyond function application.
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * List<Integer> numbers = List.of(1, 2, 3);
 * List<Integer> same = functor.map(x -> x, numbers);
 * // same.equals(numbers) must be true
 * }</pre>
 *
 * <h3>2. Composition Law</h3>
 *
 * <p>Mapping with a composed function is the same as mapping twice:
 *
 * <pre>{@code
 * map(g.compose(f), fa) ≡ map(g, map(f, fa))
 * }</pre>
 *
 * <p>This ensures that {@code map} operations can be optimised through fusion, and that the order
 * of operations is preserved.
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * Function<Integer, Integer> f = x -> x + 1;
 * Function<Integer, Integer> g = x -> x * 2;
 * List<Integer> numbers = List.of(1, 2, 3);
 *
 * // These two should be equivalent:
 * List<Integer> composed = functor.map(g.compose(f), numbers);
 * List<Integer> chained = functor.map(g, functor.map(f, numbers));
 * // Both result in: List.of(4, 6, 8)
 * }</pre>
 *
 * <p><b>Why these laws matter:</b>
 *
 * <ul>
 *   <li>Enable compiler optimisations (fusion, deforestation)
 *   <li>Guarantee that refactoring doesn't change behaviour
 *   <li>Allow reasoning about code using equational reasoning
 *   <li>Ensure Functors compose properly in larger systems
 * </ul>
 *
 * <h2>Relationship to Other Type Classes</h2>
 *
 * <p>Functor is the foundation of a hierarchy of increasingly powerful abstractions:
 *
 * <pre>
 * Functor
 *    ↑
 *    |
 * Applicative (adds: combining multiple contexts, lifting pure values)
 *    ↑
 *    |
 * Monad (adds: sequencing dependent computations, flattening)
 * </pre>
 *
 * <ul>
 *   <li>{@link Applicative} extends Functor with the ability to apply wrapped functions to wrapped
 *       values
 *   <li>{@link Monad} extends Applicative with the ability to chain operations where each depends
 *       on the previous result
 *   <li>{@link Traverse} combines Functor with {@link Foldable} to enable traversal with effects
 * </ul>
 *
 * <h2>Common Functor Instances</h2>
 *
 * <p>Many familiar types are Functors:
 *
 * <ul>
 *   <li><b>Optional/Maybe:</b> Transforms the value if present, preserves empty
 *   <li><b>List/Stream:</b> Transforms each element independently
 *   <li><b>Either/Result:</b> Transforms the success value, preserves errors
 *   <li><b>Future/Promise:</b> Transforms the eventual result
 *   <li><b>Function (Reader):</b> Transforms the output while keeping the input fixed
 *   <li><b>Validation:</b> Transforms valid values, preserves accumulated errors
 *   <li><b>Tree/Graph:</b> Transforms values at all nodes
 *   <li><b>IO:</b> Transforms the result of an I/O action
 * </ul>
 *
 * <h2>Design Pattern: Lifting Functions</h2>
 *
 * <p>Functor enables a powerful pattern called "lifting", where regular functions are transformed
 * to work with wrapped values:
 *
 * <pre>{@code
 * // Regular function
 * Function<String, Integer> length = String::length;
 *
 * // Lifted to work with Optional
 * Function<Optional<String>, Optional<Integer>> liftedLength =
 *     opt -> functor.map(length, opt);
 *
 * // Lifted to work with List
 * Function<List<String>, List<Integer>> liftedForList =
 *     list -> functor.map(length, list);
 * }</pre>
 *
 * <h2>Implementation Guidelines</h2>
 *
 * <p>When implementing Functor for a custom type:
 *
 * <ol>
 *   <li><b>Preserve structure:</b> Don't change the shape or context, only transform values
 *   <li><b>Verify laws:</b> Ensure both identity and composition laws hold
 *   <li><b>Handle all cases:</b> Consider empty, error, and pending states appropriately
 *   <li><b>Be stack-safe:</b> For recursive structures, use trampolining or tail recursion
 *   <li><b>Maintain referential transparency:</b> The function should be applied purely
 * </ol>
 *
 * <h2>Performance Considerations</h2>
 *
 * <ul>
 *   <li><b>Fusion:</b> Multiple {@code map} calls can often be fused into a single traversal
 *   <li><b>Laziness:</b> Some Functors (like {@code Stream}) can implement {@code map} lazily
 *   <li><b>Parallelism:</b> For collection-based Functors, mapping can sometimes be parallelised
 *   <li><b>Short-circuiting:</b> Functors like {@code Optional} can short-circuit on empty
 * </ul>
 *
 * <h2>Common Pitfalls</h2>
 *
 * <ul>
 *   <li><b>Side effects in the function:</b> The function passed to {@code map} should be pure
 *   <li><b>Trying to inspect context:</b> Functor can't look inside or change the wrapper
 *   <li><b>Expecting sequencing:</b> Multiple {@code map} calls don't sequence operations (use
 *       Monad for that)
 *   <li><b>Forgetting null handling:</b> Ensure proper null safety in implementations
 * </ul>
 *
 * @param <F> The witness type representing the type constructor of the functor context (e.g.,
 *     {@code OptionalKind.Witness}, {@code ListKind.Witness}, {@code FutureKind.Witness}). This
 *     {@code F} represents the "wrapper" or "context" type, not the type of the values inside.
 * @see Applicative
 * @see Monad
 * @see Traverse
 * @see Kind
 */
@NullMarked
public interface Functor<F> {

  /**
   * Applies a pure function to the value(s) contained within the Functor context, producing a new
   * Functor with the transformed values while preserving the original structure and context.
   *
   * <p>This is the fundamental operation of the Functor type class. It allows you to transform the
   * "contents" of a context without affecting the context itself. The transformation is applied
   * uniformly to all values within the structure.
   *
   * <p><b>Type Transformation:</b>
   *
   * <pre>
   * F<A> ──map(f: A -> B)──> F<B>
   *
   * Examples:
   * Optional<String>        ──map(String::length)──>      Optional<Integer>
   * List<User>              ──map(User::getName)──>       List<String>
   * Future<Response>        ──map(Response::getBody)──>   Future<String>
   * Either<Error, Int>      ──map(n -> n * 2)──>          Either<Error, Int>
   * </pre>
   *
   * <p><b>Behaviour by Functor Type:</b>
   *
   * <ul>
   *   <li><b>Optional:</b> Applies {@code f} to the value if present, returns {@code
   *       Optional.empty()} if absent
   *   <li><b>List:</b> Applies {@code f} to each element, returning a new list of the same length
   *   <li><b>Either (Right-biased):</b> Applies {@code f} to the right value if successful, passes
   *       through left (error) values unchanged
   *   <li><b>Future:</b> Transforms the eventual result when the future completes
   *   <li><b>Function (Reader):</b> Composes {@code f} after the function, transforming its output
   *   <li><b>Validation:</b> Applies {@code f} to valid values, preserves validation errors
   * </ul>
   *
   * <p><b>Example Usage:</b>
   *
   * <pre>{@code
   * // Example 1: Transforming an Optional
   * Functor<OptionalKind.Witness> optFunctor = ...;
   * Kind<OptionalKind.Witness, String> name = OptionalKind.of("Alice");
   * Kind<OptionalKind.Witness, Integer> length = optFunctor.map(String::length, name);
   * // Result: OptionalKind containing 5
   *
   * // Example 2: Chaining transformations
   * Kind<OptionalKind.Witness, String> result = optFunctor.map(
   *     String::toUpperCase,
   *     optFunctor.map(String::trim, name)
   * );
   * // Result: OptionalKind containing "ALICE"
   *
   * // Example 3: Transforming a List
   * Functor<ListKind.Witness> listFunctor = ...;
   * Kind<ListKind.Witness, User> users = ListKind.of(user1, user2, user3);
   * Kind<ListKind.Witness, String> emails = listFunctor.map(User::getEmail, users);
   * // Result: ListKind containing the three email addresses
   *
   * // Example 4: Transforming with method references
   * Kind<ListKind.Witness, Integer> ages = listFunctor.map(User::getAge, users);
   * Kind<ListKind.Witness, Boolean> adults = listFunctor.map(age -> age >= 18, ages);
   * // Result: ListKind of booleans indicating adult status
   * }</pre>
   *
   * <p><b>Law Verification:</b>
   *
   * <p>When implementing this method, ensure both Functor laws are satisfied:
   *
   * <pre>{@code
   * // Identity law: mapping with identity is a no-op
   * map(x -> x, fa).equals(fa)  // must be true
   *
   * // Composition law: mapping twice = mapping with composed function
   * map(g, map(f, fa)).equals(map(g.compose(f), fa))  // must be true
   * }</pre>
   *
   * <p><b>Variance and Wildcards:</b>
   *
   * <p>The signature uses wildcards to provide flexibility:
   *
   * <ul>
   *   <li>{@code ? super A}: The function can accept any supertype of A
   *   <li>{@code ? extends B}: The function can return any subtype of B
   * </ul>
   *
   * <p>This allows for more flexible type inference and better integration with Java's type system.
   *
   * <p><b>Stack Safety:</b>
   *
   * <p>For deeply nested or recursive structures, implementations should consider stack safety. Use
   * techniques like trampolining or tail recursion to avoid stack overflow when mapping over large
   * structures.
   *
   * <p><b>Thread Safety:</b>
   *
   * <p>The {@code map} operation itself is pure and thread-safe. However, the resulting structure's
   * thread safety depends on the specific Functor implementation (e.g., immutable collections are
   * thread-safe, mutable ones may not be).
   *
   * @param <A> The type of the value(s) inside the input Functor structure {@code fa}. This is the
   *     "before" type that will be transformed.
   * @param <B> The type of the value(s) inside the output Functor structure. This is the "after"
   *     type resulting from applying function {@code f}.
   * @param f The pure function to apply to the wrapped value(s). Must not be null. The function
   *     should:
   *     <ul>
   *       <li>Be pure (no side effects, referentially transparent)
   *       <li>Be total (defined for all inputs of type A)
   *       <li>Return non-null for non-null inputs (unless B allows null)
   *     </ul>
   *
   * @param fa The Functor structure containing the value(s) of type {@code A}. Must not be null.
   *     This is the source structure to be transformed.
   * @return A new Functor structure of type {@code Kind<F, B>} containing the result(s) of applying
   *     function {@code f}. The structure {@code F} is preserved - only the contained values are
   *     transformed. Guaranteed to be non-null.
   * @throws NullPointerException if {@code f} or {@code fa} is null (implementation-dependent)
   * @see #map(Function, Kind) for the same operation with different parameter order
   * @see Applicative#ap(Kind, Kind) for applying wrapped functions to wrapped values
   * @see Monad#flatMap(Function, Kind) for dependent sequencing of operations
   */
  <A, B> Kind<F, B> map(final Function<? super A, ? extends B> f, final Kind<F, A> fa);
}
