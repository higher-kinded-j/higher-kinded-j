// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * A Bifunctor is a type constructor {@code F} of kind {@code * -> * -> *} that is covariant in
 * both of its type arguments. In other words, it represents "things that contain two independent
 * types that can both be mapped over".
 *
 * <p>Bifunctor is similar to {@link Functor}, but operates on types with two type parameters
 * instead of one. Whilst Functor provides {@code map} to transform a single type parameter,
 * Bifunctor provides {@code bimap} to transform both type parameters simultaneously.
 *
 * <p>The key operations are:
 *
 * <ul>
 *   <li>{@code first}: Map over the first type parameter only (covariant)
 *   <li>{@code second}: Map over the second type parameter only (covariant)
 *   <li>{@code bimap}: Map over both type parameters simultaneously
 * </ul>
 *
 * <h2>Core Concept</h2>
 *
 * <p>If you have a value wrapped in a two-parameter context {@code F<A, B>} (like {@code
 * Either<String, Integer>}, {@code Tuple2<String, Integer>}, or {@code Validated<Error,
 * Success>}), and you have functions {@code A -> C} and {@code B -> D}, the {@code bimap}
 * operation lets you apply both functions to get {@code F<C, D>}, preserving the structure:
 *
 * <pre>
 * F<A, B> + (A -> C) + (B -> D) ──bimap──> F<C, D>
 * </pre>
 *
 * <h2>Common Examples</h2>
 *
 * <pre>{@code
 * // Example 1: Either<L, R> (sum type representing success or failure)
 * Either<String, Integer> result = Either.right(42);
 * Either<Exception, String> transformed = bifunctor.bimap(
 *     error -> new Exception(error),  // Transform left (error) type
 *     value -> "Value: " + value,     // Transform right (success) type
 *     result
 * );
 * // Result: Either.right("Value: 42")
 *
 * Either<String, Integer> failure = Either.left("not found");
 * Either<Exception, String> transformedError = bifunctor.bimap(
 *     error -> new Exception(error),
 *     value -> "Value: " + value,
 *     failure
 * );
 * // Result: Either.left(new Exception("not found"))
 *
 * // Example 2: Tuple2<A, B> (product type representing a pair)
 * Tuple2<String, Integer> pair = new Tuple2<>("Alice", 30);
 * Tuple2<Integer, String> transformed = bifunctor.bimap(
 *     String::length,              // Transform first element
 *     age -> age + " years old",   // Transform second element
 *     pair
 * );
 * // Result: new Tuple2<>(5, "30 years old")
 *
 * // Example 3: Validated<E, A> (accumulating validation)
 * Validated<List<String>, User> validated = Validated.valid(user);
 * Validated<List<Exception>, String> transformed = bifunctor.bimap(
 *     errors -> errors.stream().map(Exception::new).collect(toList()),
 *     User::getName,
 *     validated
 * );
 * // Result: Validated.valid(user.getName())
 *
 * // Example 4: Using first() to transform only the first parameter
 * Either<String, Integer> result = Either.right(42);
 * Either<Exception, Integer> mapped = bifunctor.first(
 *     Exception::new,
 *     result
 * );
 * // Result: Either.right(42) - right side unchanged
 *
 * // Example 5: Using second() to transform only the second parameter
 * Tuple2<String, Integer> pair = new Tuple2<>("Alice", 30);
 * Tuple2<String, String> mapped = bifunctor.second(
 *     age -> age + " years",
 *     pair
 * );
 * // Result: new Tuple2<>("Alice", "30 years")
 * }</pre>
 *
 * <h2>Key Characteristics</h2>
 *
 * <ul>
 *   <li><b>Structure Preservation:</b> The shape and context of the container remain unchanged;
 *       only the values inside are transformed.
 *   <li><b>Independent Transformations:</b> The two type parameters can be transformed completely
 *       independently of each other.
 *   <li><b>Pure Functions Only:</b> Functions passed to bifunctor operations must be pure (no side
 *       effects).
 *   <li><b>Both Parameters are Covariant:</b> Unlike {@link Profunctor} where the first parameter
 *       is contravariant, Bifunctor is covariant in both parameters.
 * </ul>
 *
 * <h2>Bifunctor vs Profunctor</h2>
 *
 * <p>It's important to distinguish Bifunctor from {@link Profunctor}:
 *
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>Bifunctor</th>
 *     <th>Profunctor</th>
 *   </tr>
 *   <tr>
 *     <td>First parameter</td>
 *     <td>Covariant (output-like)</td>
 *     <td>Contravariant (input-like)</td>
 *   </tr>
 *   <tr>
 *     <td>Second parameter</td>
 *     <td>Covariant (output-like)</td>
 *     <td>Covariant (output-like)</td>
 *   </tr>
 *   <tr>
 *     <td>Core operation</td>
 *     <td>{@code bimap(f: A→C, g: B→D)}</td>
 *     <td>{@code dimap(f: C→A, g: B→D)}</td>
 *   </tr>
 *   <tr>
 *     <td>Typical instances</td>
 *     <td>Either, Tuple2, Validated</td>
 *     <td>Function, Reader, Optics</td>
 *   </tr>
 *   <tr>
 *     <td>Use case</td>
 *     <td>Data structures with two types</td>
 *     <td>Transformations (input → output)</td>
 *   </tr>
 * </table>
 *
 * <h2>Bifunctor Laws</h2>
 *
 * <p>All Bifunctor implementations must satisfy two fundamental laws to ensure predictable and
 * composable behaviour:
 *
 * <h3>1. Identity Law</h3>
 *
 * <p>Mapping both parameters with the identity function does nothing:
 *
 * <pre>{@code
 * bimap(x -> x, y -> y, fab) ≡ fab
 * }</pre>
 *
 * <p>This ensures that {@code bimap} doesn't add any extra behaviour beyond function application.
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * Either<String, Integer> result = Either.right(42);
 * Either<String, Integer> same = bifunctor.bimap(x -> x, y -> y, result);
 * // same.equals(result) must be true
 * }</pre>
 *
 * <h3>2. Composition Law</h3>
 *
 * <p>Mapping with composed functions is the same as mapping twice:
 *
 * <pre>{@code
 * bimap(f2.compose(f1), g2.compose(g1), fab) ≡ bimap(f2, g2, bimap(f1, g1, fab))
 * }</pre>
 *
 * <p>Or equivalently, using {@code andThen}:
 *
 * <pre>{@code
 * bimap(f1.andThen(f2), g1.andThen(g2), fab) ≡ bimap(f2, g2, bimap(f1, g1, fab))
 * }</pre>
 *
 * <p>This ensures that bifunctor operations compose properly and can be optimised through fusion.
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * Function<String, Integer> f1 = String::length;
 * Function<Integer, String> f2 = Object::toString;
 * Function<Integer, Double> g1 = Integer::doubleValue;
 * Function<Double, String> g2 = d -> String.format("%.2f", d);
 *
 * Either<String, Integer> either = Either.right(42);
 *
 * // These two should be equivalent:
 * Either<String, String> composed = bifunctor.bimap(
 *     f1.andThen(f2),
 *     g1.andThen(g2),
 *     either
 * );
 *
 * Either<String, String> chained = bifunctor.bimap(
 *     f2, g2,
 *     bifunctor.bimap(f1, g1, either)
 * );
 * // Both result in: Either.right("42.00")
 * }</pre>
 *
 * <p><b>Why these laws matter:</b>
 *
 * <ul>
 *   <li>Enable compiler optimisations (fusion, deforestation)
 *   <li>Guarantee that refactoring doesn't change behaviour
 *   <li>Allow reasoning about code using equational reasoning
 *   <li>Ensure Bifunctors compose properly in larger systems
 * </ul>
 *
 * <h2>Relationship to Other Type Classes</h2>
 *
 * <p>Bifunctor is related to but distinct from other type classes:
 *
 * <ul>
 *   <li>{@link Functor} - Maps over a single type parameter
 *   <li>{@link Profunctor} - Maps over two parameters, but first is contravariant
 *   <li>Any type that is a Bifunctor in its last parameter can be a Functor
 * </ul>
 *
 * <h2>Common Bifunctor Instances</h2>
 *
 * <p>Many familiar types with two type parameters are Bifunctors:
 *
 * <ul>
 *   <li><b>Either&lt;L, R&gt;:</b> Sum type - transforms left or right value depending on which is
 *       present
 *   <li><b>Tuple2&lt;A, B&gt;:</b> Product type - transforms both elements of the pair
 *   <li><b>Validated&lt;E, A&gt;:</b> Validation type - transforms error or success value
 *   <li><b>Writer&lt;W, A&gt;:</b> Writer monad - transforms log and result independently
 * </ul>
 *
 * <h2>Implementation Guidelines</h2>
 *
 * <p>When implementing Bifunctor for a custom type:
 *
 * <ol>
 *   <li><b>Preserve structure:</b> Don't change the shape or context, only transform values
 *   <li><b>Verify laws:</b> Ensure both identity and composition laws hold
 *   <li><b>Handle all cases:</b> Consider all possible states of the bifunctor (left/right, both,
 *       etc.)
 *   <li><b>Maintain referential transparency:</b> Functions should be applied purely
 *   <li><b>Use default methods:</b> {@code first} and {@code second} have default implementations
 *       via {@code bimap}
 * </ol>
 *
 * <h2>Performance Considerations</h2>
 *
 * <ul>
 *   <li><b>Fusion:</b> Multiple {@code bimap} calls can often be fused into a single operation
 *   <li><b>Efficient implementations:</b> Types can provide optimised implementations of {@code
 *       first} and {@code second} if more efficient than {@code bimap}
 *   <li><b>Allocation:</b> Bifunctor operations typically create new instances; consider immutable
 *       data structures
 * </ul>
 *
 * @param <F> The witness type representing the type constructor of the bifunctor context (e.g.,
 *     {@code EitherKind2.Witness}, {@code Tuple2Kind2.Witness}). This {@code F} represents the
 *     two-parameter "wrapper" or "context" type.
 * @see Functor
 * @see Profunctor
 * @see Kind2
 */
@NullMarked
public interface Bifunctor<F> {

  /**
   * Maps over both type parameters simultaneously, applying {@code f} to the first parameter and
   * {@code g} to the second parameter.
   *
   * <p>This is the fundamental operation of the Bifunctor type class. It allows you to transform
   * both "contents" of a two-parameter context without affecting the structure itself.
   *
   * <p><b>Type Transformation:</b>
   *
   * <pre>
   * F<A, B> ──bimap(f: A → C, g: B → D)──> F<C, D>
   *
   * Examples:
   * Either<String, Integer>         ──bimap(Exception::new, n -> n * 2)──>
   *                                                     Either<Exception, Integer>
   * Tuple2<String, Integer>         ──bimap(String::length, Object::toString)──>
   *                                                     Tuple2<Integer, String>
   * Validated<List<Error>, User>    ──bimap(errors -> ..., User::getName)──>
   *                                                     Validated<List<String>, String>
   * </pre>
   *
   * <p><b>Example Usage:</b>
   *
   * <pre>{@code
   * // Example 1: Transforming an Either
   * Bifunctor<EitherKind2.Witness> eitherBifunctor = ...;
   * Kind2<EitherKind2.Witness, String, Integer> either =
   *     EitherKind2.widen(Either.right(42));
   *
   * Kind2<EitherKind2.Witness, Exception, String> result = eitherBifunctor.bimap(
   *     Exception::new,           // Transform left (error) type
   *     n -> "Number: " + n,      // Transform right (success) type
   *     either
   * );
   * // Result: Either.right("Number: 42")
   *
   * // Example 2: Transforming a Tuple2
   * Bifunctor<Tuple2Kind2.Witness> tupleBifunctor = ...;
   * Kind2<Tuple2Kind2.Witness, String, Integer> tuple =
   *     Tuple2Kind2.widen(new Tuple2<>("Alice", 30));
   *
   * Kind2<Tuple2Kind2.Witness, Integer, String> result = tupleBifunctor.bimap(
   *     String::length,           // Transform first element
   *     age -> age + " years",    // Transform second element
   *     tuple
   * );
   * // Result: new Tuple2<>(5, "30 years")
   * }</pre>
   *
   * <p><b>Law Verification:</b>
   *
   * <p>When implementing this method, ensure both Bifunctor laws are satisfied:
   *
   * <pre>{@code
   * // Identity law: mapping both with identity is a no-op
   * bimap(x -> x, y -> y, fab).equals(fab)  // must be true
   *
   * // Composition law: mapping twice = mapping with composed functions
   * bimap(f2.compose(f1), g2.compose(g1), fab).equals(
   *     bimap(f2, g2, bimap(f1, g1, fab))
   * )  // must be true
   * }</pre>
   *
   * @param <A> The type of the first parameter in the input bifunctor {@code fab}
   * @param <B> The type of the second parameter in the input bifunctor {@code fab}
   * @param <C> The type of the first parameter in the output bifunctor
   * @param <D> The type of the second parameter in the output bifunctor
   * @param f The pure function to apply to the first parameter. Must not be null.
   * @param g The pure function to apply to the second parameter. Must not be null.
   * @param fab The bifunctor structure containing values of types {@code A} and {@code B}. Must
   *     not be null.
   * @return A new bifunctor structure of type {@code Kind2<F, C, D>} with both parameters
   *     transformed. Guaranteed to be non-null.
   * @throws NullPointerException if {@code f}, {@code g}, or {@code fab} is null
   *     (implementation-dependent)
   * @see #first(Function, Kind2)
   * @see #second(Function, Kind2)
   */
  <A, B, C, D> Kind2<F, C, D> bimap(
      Function<? super A, ? extends C> f,
      Function<? super B, ? extends D> g,
      Kind2<F, A, B> fab);

  /**
   * Maps over the first type parameter only, leaving the second parameter unchanged.
   *
   * <p>This is a convenience method that applies a transformation to only the first type
   * parameter. It is equivalent to calling {@code bimap(f, Function.identity(), fab)}.
   *
   * <p><b>Type Transformation:</b>
   *
   * <pre>
   * F<A, B> ──first(f: A → C)──> F<C, B>
   *
   * Examples:
   * Either<String, Integer>     ──first(Exception::new)──>     Either<Exception, Integer>
   * Tuple2<String, Integer>     ──first(String::length)──>     Tuple2<Integer, Integer>
   * Validated<Error, User>      ──first(Error::getMessage)──>  Validated<String, User>
   * </pre>
   *
   * <p><b>Example Usage:</b>
   *
   * <pre>{@code
   * // Example 1: Transforming only the left side of an Either
   * Bifunctor<EitherKind2.Witness> bifunctor = ...;
   * Kind2<EitherKind2.Witness, String, Integer> either =
   *     EitherKind2.widen(Either.left("error"));
   *
   * Kind2<EitherKind2.Witness, Exception, Integer> result = bifunctor.first(
   *     Exception::new,
   *     either
   * );
   * // Result: Either.left(new Exception("error"))
   *
   * // Example 2: Transforming the first element of a Tuple2
   * Bifunctor<Tuple2Kind2.Witness> bifunctor = ...;
   * Kind2<Tuple2Kind2.Witness, String, Integer> tuple =
   *     Tuple2Kind2.widen(new Tuple2<>("Alice", 30));
   *
   * Kind2<Tuple2Kind2.Witness, Integer, Integer> result = bifunctor.first(
   *     String::length,
   *     tuple
   * );
   * // Result: new Tuple2<>(5, 30)
   * }</pre>
   *
   * <p><b>Default Implementation:</b>
   *
   * <p>The default implementation delegates to {@code bimap} with {@code Function.identity()} for
   * the second parameter. Implementations may override this for better performance.
   *
   * @param <A> The type of the first parameter in the input bifunctor {@code fab}
   * @param <B> The type of the second parameter (unchanged)
   * @param <C> The type of the first parameter in the output bifunctor
   * @param f The pure function to apply to the first parameter. Must not be null.
   * @param fab The bifunctor structure containing values of types {@code A} and {@code B}. Must
   *     not be null.
   * @return A new bifunctor structure of type {@code Kind2<F, C, B>} with the first parameter
   *     transformed. Guaranteed to be non-null.
   * @throws NullPointerException if {@code f} or {@code fab} is null (implementation-dependent)
   * @see #bimap(Function, Function, Kind2)
   * @see #second(Function, Kind2)
   */
  default <A, B, C> Kind2<F, C, B> first(
      Function<? super A, ? extends C> f, Kind2<F, A, B> fab) {
    return bimap(f, Function.identity(), fab);
  }

  /**
   * Maps over the second type parameter only, leaving the first parameter unchanged.
   *
   * <p>This is a convenience method that applies a transformation to only the second type
   * parameter. It is equivalent to calling {@code bimap(Function.identity(), g, fab)}.
   *
   * <p><b>Type Transformation:</b>
   *
   * <pre>
   * F<A, B> ──second(g: B → D)──> F<A, D>
   *
   * Examples:
   * Either<String, Integer>     ──second(n -> n * 2)──>        Either<String, Integer>
   * Tuple2<String, Integer>     ──second(Object::toString)──>  Tuple2<String, String>
   * Validated<Error, User>      ──second(User::getName)──>     Validated<Error, String>
   * </pre>
   *
   * <p><b>Example Usage:</b>
   *
   * <pre>{@code
   * // Example 1: Transforming only the right side of an Either
   * Bifunctor<EitherKind2.Witness> bifunctor = ...;
   * Kind2<EitherKind2.Witness, String, Integer> either =
   *     EitherKind2.widen(Either.right(42));
   *
   * Kind2<EitherKind2.Witness, String, String> result = bifunctor.second(
   *     n -> "Number: " + n,
   *     either
   * );
   * // Result: Either.right("Number: 42")
   *
   * // Example 2: Transforming the second element of a Tuple2
   * Bifunctor<Tuple2Kind2.Witness> bifunctor = ...;
   * Kind2<Tuple2Kind2.Witness, String, Integer> tuple =
   *     Tuple2Kind2.widen(new Tuple2<>("Alice", 30));
   *
   * Kind2<Tuple2Kind2.Witness, String, String> result = bifunctor.second(
   *     age -> age + " years old",
   *     tuple
   * );
   * // Result: new Tuple2<>("Alice", "30 years old")
   * }</pre>
   *
   * <p><b>Default Implementation:</b>
   *
   * <p>The default implementation delegates to {@code bimap} with {@code Function.identity()} for
   * the first parameter. Implementations may override this for better performance.
   *
   * <p><b>Note on Functor:</b>
   *
   * <p>For many bifunctors (like {@code Either}, {@code Validated}), {@code second} is equivalent
   * to the {@link Functor#map} operation, as these types are typically "right-biased" when viewed
   * as single-parameter functors.
   *
   * @param <A> The type of the first parameter (unchanged)
   * @param <B> The type of the second parameter in the input bifunctor {@code fab}
   * @param <D> The type of the second parameter in the output bifunctor
   * @param g The pure function to apply to the second parameter. Must not be null.
   * @param fab The bifunctor structure containing values of types {@code A} and {@code B}. Must
   *     not be null.
   * @return A new bifunctor structure of type {@code Kind2<F, A, D>} with the second parameter
   *     transformed. Guaranteed to be non-null.
   * @throws NullPointerException if {@code g} or {@code fab} is null (implementation-dependent)
   * @see #bimap(Function, Function, Kind2)
   * @see #first(Function, Kind2)
   */
  default <A, B, D> Kind2<F, A, D> second(
      Function<? super B, ? extends D> g, Kind2<F, A, B> fab) {
    return bimap(Function.identity(), g, fab);
  }
}
