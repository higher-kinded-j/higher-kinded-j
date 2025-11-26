// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Implements the {@link Traverse} and {@link Foldable} typeclasses for {@link
 * java.util.stream.Stream}, using {@link StreamKind.Witness} as the higher-kinded type witness.
 *
 * <p><b>Important: Stream Evaluation Semantics</b>
 *
 * <p>Unlike the lazy operations in {@link StreamFunctor} and {@link StreamMonad}, the operations
 * provided by {@code Traverse} and {@code Foldable} necessarily force evaluation of the stream:
 *
 * <ul>
 *   <li>{@link #traverse(Applicative, Function, Kind)} - Must consume the entire stream to collect
 *       results and sequence effects
 *   <li>{@link #foldMap(Monoid, Function, Kind)} - Must consume the stream to compute the folded
 *       result
 * </ul>
 *
 * <p>This is a fundamental property of these typeclasses: to produce a result that combines all
 * elements (whether sequencing effects or folding values), the entire structure must be traversed.
 *
 * <p><b>Use Cases and Benefits</b>
 *
 * <p>Despite forcing evaluation, {@code StreamTraverse} provides valuable functionality:
 *
 * <h3>1. Sequencing Effects from Stream Pipelines</h3>
 *
 * <pre>{@code
 * // Process stream results with validation
 * Stream<String> inputs = Files.lines(path);
 * Kind<StreamKind.Witness, String> streamKind = STREAM.widen(inputs);
 *
 * Kind<ValidationKind.Witness, Kind<StreamKind.Witness, Integer>> validated =
 *     StreamTraverse.INSTANCE.traverse(
 *         validationApplicative,
 *         str -> parseAndValidate(str),
 *         streamKind
 *     );
 * // Result: Either all valid (stream of integers) or accumulated errors
 * }</pre>
 *
 * <h3>2. Converting Stream Computations to Collections</h3>
 *
 * <pre>{@code
 * // Lazy computation pipeline that eventually needs materialization
 * Stream<Integer> lazyComputation = Stream.iterate(1, n -> n + 1)
 *     .filter(n -> n % 2 == 0)
 *     .limit(100)
 *     .map(n -> n * n);
 *
 * // Traverse with Optional applicative (fail-fast)
 * Kind<StreamKind.Witness, Integer> streamKind = STREAM.widen(lazyComputation);
 * Kind<OptionalKind.Witness, Kind<StreamKind.Witness, Integer>> result =
 *     StreamTraverse.INSTANCE.traverse(
 *         optionalApplicative,
 *         n -> n > 0 ? Optional.of(n) : Optional.empty(),
 *         streamKind
 *     );
 * }</pre>
 *
 * <h3>3. Generic Algorithms</h3>
 *
 * <pre>{@code
 * // Generic function that works with ANY Traverse instance
 * <T> Kind<ValidationKind.Witness, Kind<T, Integer>> validateAll(
 *     Traverse<T> traverse,
 *     Kind<T, String> inputs
 * ) {
 *     return traverse.traverse(validationApp, validateInteger, inputs);
 * }
 *
 * // Works with Stream
 * validateAll(StreamTraverse.INSTANCE, STREAM.widen(stream));
 * // Also works with List, Optional, Tree, etc.
 * validateAll(ListTraverse.INSTANCE, LIST.widen(list));
 * }</pre>
 *
 * <p><b>Performance Considerations</b>
 *
 * <ul>
 *   <li>The stream is consumed exactly once during traverse/foldMap
 *   <li>Results are collected into a new stream, requiring memory allocation
 *   <li>For large streams, consider chunking or using other streaming abstractions
 *   <li>Parallel streams are processed sequentially by traverse (maintains left-to-right order)
 * </ul>
 *
 * <p><b>Comparison with List</b>
 *
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>ListTraverse</th>
 *     <th>StreamTraverse</th>
 *   </tr>
 *   <tr>
 *     <td>Input</td>
 *     <td>Already materialized</td>
 *     <td>Can be lazy pipeline</td>
 *   </tr>
 *   <tr>
 *     <td>Output</td>
 *     <td>List (reusable)</td>
 *     <td>Stream (single-use)</td>
 *   </tr>
 *   <tr>
 *     <td>Memory</td>
 *     <td>Already allocated</td>
 *     <td>Allocates during traverse</td>
 *   </tr>
 *   <tr>
 *     <td>Use Case</td>
 *     <td>Working with collections</td>
 *     <td>Pipeline endpoints</td>
 *   </tr>
 * </table>
 *
 * @see Traverse
 * @see Stream
 * @see StreamKind
 * @see StreamMonad
 * @see StreamFunctor
 */
@NullMarked
public enum StreamTraverse implements Traverse<StreamKind.Witness> {
  /**
   * Singleton instance of {@code StreamTraverse}. This instance can be used to access {@code
   * Traverse} and {@code Foldable} operations for streams.
   */
  INSTANCE;

  private static final Class<StreamTraverse> STREAM_TRAVERSE_CLASS = StreamTraverse.class;

  /**
   * Maps a function over a stream in a higher-kinded context. This operation is inherited from
   * {@link Functor} via {@link Traverse}.
   *
   * <p><b>Note:</b> This operation delegates to {@link StreamFunctor} and maintains lazy
   * evaluation. It does NOT force evaluation of the stream.
   *
   * @param <A> The type of elements in the input stream.
   * @param <B> The type of elements in the output stream after applying the function.
   * @param f The non-null function to apply to each element of the stream.
   * @param fa The non-null {@code Kind<StreamKind.Witness, A>} representing the input stream.
   * @return A new non-null {@code Kind<StreamKind.Witness, B>} containing a stream with the results
   *     of applying the function {@code f}. The transformation is lazy.
   * @throws NullPointerException if f or fa is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if fa cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<StreamKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<StreamKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", STREAM_TRAVERSE_CLASS, MAP);
    Validation.kind().requireNonNull(fa, STREAM_TRAVERSE_CLASS, MAP);

    return StreamFunctor.INSTANCE.map(f, fa);
  }

  /**
   * Traverses a stream from left to right, applying an effectful function {@code f} to each element
   * and collecting the results within the context of the {@link Applicative} {@code G}.
   *
   * <p><b>Stream Consumption Warning:</b> This operation forces evaluation of the entire stream.
   * The stream is consumed during traversal as elements are processed sequentially from left to
   * right. After this operation completes, the input stream cannot be reused.
   *
   * <p>The traversal proceeds as follows:
   *
   * <ol>
   *   <li>Start with an empty stream wrapped in the applicative context
   *   <li>For each element in the input stream (left to right):
   *       <ul>
   *         <li>Apply the effectful function {@code f} to get {@code Kind<G, B>}
   *         <li>Combine with the accumulator using the applicative's {@code map2}
   *         <li>Append the new element to the result stream
   *       </ul>
   *   <li>Return the final accumulated result
   * </ol>
   *
   * <p><b>Stack Safety Considerations:</b>
   *
   * <p>This implementation uses an iterative loop with {@code applicative.map2()}, which is
   * generally stack-safe for most standard {@code Applicative} instances. However, stack safety
   * ultimately depends on the {@code Applicative} instance provided:
   *
   * <ul>
   *   <li><b>Stack-Safe Applicatives:</b> If {@code map2} is implemented iteratively or uses
   *       trampolining internally (as in {@code Id}, {@code Optional}, {@code Either}), this
   *       traversal is stack-safe for arbitrarily large streams.
   *   <li><b>Potentially Unsafe Applicatives:</b> If {@code map2} is implemented in terms of {@code
   *       flatMap} without stack-safety measures, traversing very large streams (>10,000 elements)
   *       may cause {@code StackOverflowError}. In such cases, ensure your {@code Applicative}
   *       instance uses {@link org.higherkindedj.hkt.trampoline.Trampoline} or similar techniques.
   * </ul>
   *
   * <p><b>Effect Sequencing:</b> The behaviour depends on the applicative instance:
   *
   * <ul>
   *   <li><b>Optional:</b> Returns {@code Some(stream)} if all applications succeed, {@code None}
   *       on first failure (short-circuits)
   *   <li><b>Validation:</b> Accumulates all errors, returning either all errors or a valid result
   *       stream
   *   <li><b>Either:</b> Returns first error encountered (Left), or the result stream (Right)
   *   <li><b>IO:</b> Sequences all IO effects, producing a single IO action that yields the result
   *       stream
   *   <li><b>List:</b> Generates all combinations (cartesian product) of possible results
   * </ul>
   *
   * <p><b>Example Usage:</b>
   *
   * <pre>{@code
   * // Validate integers from a stream of strings
   * Stream<String> inputs = Stream.of("1", "2", "abc", "4");
   * Kind<StreamKind.Witness, String> streamKind = STREAM.widen(inputs);
   *
   * Function<String, Kind<ValidationKind.Witness, Integer>> validate = str -> {
   *     try {
   *         int value = Integer.parseInt(str);
   *         return Validation.valid(value);
   *     } catch (NumberFormatException e) {
   *         return Validation.invalid("Not a number: " + str);
   *     }
   * };
   *
   * Kind<ValidationKind.Witness, Kind<StreamKind.Witness, Integer>> result =
   *     StreamTraverse.INSTANCE.traverse(validationApplicative, validate, streamKind);
   * // Result: Invalid with error ["Not a number: abc"]
   * // The stream is fully consumed, including elements after the error
   * }</pre>
   *
   * @param <G> The higher-kinded type witness for the {@link Applicative} context.
   * @param <A> The type of elements in the input stream {@code ta}.
   * @param <B> The type of elements in the resulting stream, wrapped within the context {@code G}.
   * @param applicative The non-null {@link Applicative} instance for the context {@code G}.
   * @param f A non-null function from {@code A} to {@code Kind<G, ? extends B>}, producing an
   *     effectful value for each element.
   * @param ta The non-null {@code Kind<StreamKind.Witness, A>} (a stream of {@code A}s) to
   *     traverse. This stream will be consumed.
   * @return A {@code Kind<G, Kind<StreamKind.Witness, B>>}. This represents the stream of results
   *     (each of type {@code B}), with the entire resulting stream structure itself wrapped in the
   *     applicative context {@code G}. Never null.
   * @throws NullPointerException if applicative, f, or ta is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if ta cannot be unwrapped.
   */
  @Override
  public <G, A, B> Kind<G, Kind<StreamKind.Witness, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<StreamKind.Witness, A> ta) {

    Validation.function()
        .requireApplicative(applicative, "applicative", STREAM_TRAVERSE_CLASS, TRAVERSE);
    Validation.function().requireMapper(f, "f", STREAM_TRAVERSE_CLASS, TRAVERSE);
    Validation.kind().requireNonNull(ta, STREAM_TRAVERSE_CLASS, TRAVERSE);

    Stream<A> streamA = STREAM.narrow(ta);

    // Collect stream to list to enable sequential processing
    // We need to materialize because we're building up an applicative result
    // and Stream's single-use nature prevents us from iterating multiple times
    var elements = streamA.collect(Collectors.toList());

    // Start with empty stream in applicative context
    Kind<G, Stream.Builder<B>> result = applicative.of(Stream.builder());

    // Process each element, accumulating in applicative context
    for (A a : elements) {
      Kind<G, ? extends B> effectOfB = f.apply(a);
      result =
          applicative.map2(
              result,
              effectOfB,
              (builder, b) -> {
                // Add element to the builder
                builder.add((B) b);
                return builder;
              });
    }

    // Convert builder to stream and wrap
    return applicative.map(builder -> STREAM.widen(builder.build()), result);
  }

  /**
   * Maps each element of the stream to a {@link Monoid} {@code M} and combines the results using
   * the monoid's binary operation.
   *
   * <p><b>Stream Consumption Warning:</b> This operation forces evaluation of the entire stream.
   * The stream is consumed as it is folded from left to right. After this operation completes, the
   * input stream cannot be reused.
   *
   * <p>The folding proceeds as follows:
   *
   * <ol>
   *   <li>Start with the monoid's empty (identity) value
   *   <li>For each element in the stream (left to right):
   *       <ul>
   *         <li>Apply the mapping function {@code f} to get a value of type {@code M}
   *         <li>Combine with the accumulator using the monoid's {@code combine} operation
   *       </ul>
   *   <li>Return the final accumulated value
   * </ol>
   *
   * <p><b>Example Usage:</b>
   *
   * <pre>{@code
   * // Sum elements from an infinite stream (with limit)
   * Stream<Integer> numbers = Stream.iterate(1, n -> n + 1).limit(100);
   * Kind<StreamKind.Witness, Integer> streamKind = STREAM.widen(numbers);
   *
   * Monoid<Integer> sumMonoid = Monoids.integerAddition();
   * Integer sum = StreamTraverse.INSTANCE.foldMap(
   *     sumMonoid,
   *     Function.identity(),
   *     streamKind
   * );
   * // Result: 5050 (sum of 1 to 100)
   * // The stream is fully consumed
   *
   * // Concatenate string representations
   * Stream<Integer> nums = Stream.of(1, 2, 3);
   * Monoid<String> stringMonoid = Monoids.string();
   * String result = StreamTraverse.INSTANCE.foldMap(
   *     stringMonoid,
   *     n -> "n" + n + ",",
   *     STREAM.widen(nums)
   * );
   * // Result: "n1,n2,n3,"
   * }</pre>
   *
   * <p><b>Performance Note:</b> For simple reductions where you don't need the monoid abstraction,
   * consider using {@code Stream.reduce()} directly for better performance. Use {@code foldMap}
   * when you need:
   *
   * <ul>
   *   <li>Generic code that works across different traversable structures
   *   <li>The ability to map and fold in a single operation
   *   <li>Type-safe folding with monoid laws guaranteed
   * </ul>
   *
   * @param <A> The type of elements in the stream.
   * @param <M> The Monoidal type to which elements are mapped and combined.
   * @param monoid The {@code Monoid} used to combine the results. Must not be null.
   * @param f A function to map each element of type {@code A} to the Monoidal type {@code M}. Must
   *     not be null.
   * @param fa The {@code Kind<StreamKind.Witness, A>} representing the stream to fold. Must not be
   *     null. This stream will be consumed.
   * @return The aggregated result of type {@code M}. Never null.
   * @throws NullPointerException if monoid, f, or fa is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if fa cannot be unwrapped.
   */
  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<StreamKind.Witness, A> fa) {

    Validation.function().requireMonoid(monoid, "monoid", STREAM_TRAVERSE_CLASS, FOLD_MAP);
    Validation.function().requireMapper(f, "f", STREAM_TRAVERSE_CLASS, FOLD_MAP);
    Validation.kind().requireNonNull(fa, STREAM_TRAVERSE_CLASS, FOLD_MAP);

    // Collect mapped values first to avoid wildcard capture issues with stream reduce.
    // The function f returns ? extends M, which makes map(f) return Stream<? extends M>.
    // Java's reduce operation requires exact type match, so we collect and fold manually.
    List<?> mappedValues = STREAM.narrow(fa).map(f).collect(Collectors.toList());

    M result = monoid.empty();
    for (Object value : mappedValues) {
      @SuppressWarnings("unchecked")
      M m = (M) value; // Safe: values are of type ? extends M, covariant-safe to read as M
      result = monoid.combine(result, m);
    }
    return result;
  }
}
