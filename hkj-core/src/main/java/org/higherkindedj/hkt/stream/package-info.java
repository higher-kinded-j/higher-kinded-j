/**
 * Provides components for simulating {@link java.util.stream.Stream} as a Higher-Kinded Type.
 * Includes {@link org.higherkindedj.hkt.stream.StreamKind} (the HKT wrapper for Stream), {@link
 * org.higherkindedj.hkt.stream.StreamMonad} (the Monad instance for Stream), and helper utilities
 * for working with Stream as an HKT.
 *
 * <h2>Stream Characteristics in HKT Context</h2>
 *
 * <p>Unlike collection-based types such as {@link java.util.List}, streams have unique
 * characteristics that are preserved in this HKT simulation:
 *
 * <h3>Lazy Evaluation</h3>
 *
 * <p>Stream operations are lazy and form a pipeline of transformations that are only executed when
 * a terminal operation is invoked. This characteristic is maintained through the type class
 * operations:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.stream.StreamFunctor#map} creates a lazy transformation
 *   <li>{@link org.higherkindedj.hkt.stream.StreamMonad#flatMap} creates a lazy flattening
 *       operation
 *   <li>{@link org.higherkindedj.hkt.stream.StreamMonad#ap} lazily applies functions to values
 * </ul>
 *
 * <h3>Single-Use Semantics</h3>
 *
 * <p>Streams can only be consumed once. After a terminal operation has been performed on a stream,
 * attempting to use it again will result in an {@code IllegalStateException}. This applies to
 * streams wrapped in the HKT representation as well.
 *
 * <h3>Practical Guidelines</h3>
 *
 * <ul>
 *   <li><b>Prefer composing operations:</b> Chain multiple {@code map} and {@code flatMap}
 *       operations before performing a terminal operation
 *   <li><b>Avoid multiple narrowing:</b> Each call to {@link
 *       org.higherkindedj.hkt.stream.StreamKindHelper#narrow} gives you access to the stream, but
 *       using it consumes it
 *   <li><b>Leverage laziness:</b> Take advantage of lazy evaluation for infinite streams and
 *       short-circuiting operations
 *   <li><b>Use with care in tests:</b> Be mindful that asserting on a stream consumes it
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
 *
 * // Create a lazy stream pipeline using HKT operations
 * Kind<StreamKind.Witness, Integer> numbers =
 *     STREAM.widen(Stream.iterate(1, n -> n + 1)); // infinite stream
 *
 * Kind<StreamKind.Witness, Integer> processedNumbers = StreamMonad.INSTANCE.flatMap(
 *     n -> {
 *         if (n % 2 == 0) {
 *             return STREAM.widen(Stream.of(n, n * 10));
 *         } else {
 *             return STREAM.widen(Stream.empty());
 *         }
 *     },
 *     StreamMonad.INSTANCE.map(n -> n * 2, numbers)
 * );
 *
 * // Only when we perform a terminal operation does computation happen
 * List<Integer> result = STREAM.narrow(processedNumbers)
 *     .limit(10)
 *     .collect(Collectors.toList());
 * }</pre>
 *
 * <h2>Comparison with List</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>List</th>
 *     <th>Stream</th>
 *   </tr>
 *   <tr>
 *     <td>Evaluation</td>
 *     <td>Eager</td>
 *     <td>Lazy</td>
 *   </tr>
 *   <tr>
 *     <td>Reusability</td>
 *     <td>Reusable</td>
 *     <td>Single-use</td>
 *   </tr>
 *   <tr>
 *     <td>Storage</td>
 *     <td>Stores elements</td>
 *     <td>Represents computation</td>
 *   </tr>
 *   <tr>
 *     <td>Infinite Sequences</td>
 *     <td>Not possible</td>
 *     <td>Supported</td>
 *   </tr>
 *   <tr>
 *     <td>Performance</td>
 *     <td>Immediate allocation</td>
 *     <td>Delayed, potential fusion</td>
 *   </tr>
 * </table>
 *
 * @see org.higherkindedj.hkt.stream.StreamKind
 * @see org.higherkindedj.hkt.stream.StreamMonad
 * @see org.higherkindedj.hkt.stream.StreamFunctor
 * @see org.higherkindedj.hkt.stream.StreamKindHelper
 * @see java.util.stream.Stream
 */
@NullMarked
package org.higherkindedj.hkt.stream;

import org.jspecify.annotations.NullMarked;
