// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Represents {@link java.util.stream.Stream} as a Higher-Kinded Type. This interface, {@code
 * StreamKind<A>}, is the HKT representation for {@code Stream<A>}. It extends {@code
 * Kind<StreamKind.Witness, A>}, where {@code StreamKind.Witness} is the phantom type marker for the
 * Stream type constructor.
 *
 * <p><b>Important Note on Stream Single-Use Semantics:</b> Unlike collections such as {@code List},
 * {@code java.util.stream.Stream} is designed to be consumed only once. After a terminal operation
 * has been performed on a stream (such as {@code forEach}, {@code collect}, or in our case,
 * operations that narrow and re-widen the stream), the stream cannot be reused. Attempting to
 * operate on an already-consumed stream will result in an {@code IllegalStateException}.
 *
 * <p>This has important implications for using {@code Stream} in a higher-kinded context:
 *
 * <ul>
 *   <li><b>Narrow with care:</b> Each call to {@link StreamKindHelper#narrow} that internally
 *       consumes the stream makes it unusable for subsequent operations.
 *   <li><b>Lazy operations preferred:</b> Operations like {@link StreamFunctor#map} and {@link
 *       StreamMonad#flatMap} work lazily and don't force evaluation, maintaining Stream semantics.
 *   <li><b>Terminal operations force evaluation:</b> Operations that require inspecting all
 *       elements (like those in {@code Traverse} or {@code Foldable}) will consume the stream.
 *   <li><b>Testing and composition:</b> Be mindful when chaining operations in tests or
 *       compositions, as intermediate narrowing for assertions will consume the stream.
 * </ul>
 *
 * <p><b>Best Practices:</b>
 *
 * <pre>{@code
 * // Good: Operations remain lazy
 * Kind<StreamKind.Witness, Integer> numbers = STREAM.widen(Stream.of(1, 2, 3));
 * Kind<StreamKind.Witness, Integer> doubled = streamFunctor.map(x -> x * 2, numbers);
 * Kind<StreamKind.Witness, Integer> filtered = streamMonad.flatMap(x ->
 *     x > 5 ? STREAM.widen(Stream.of(x)) : STREAM.widen(Stream.empty()), doubled);
 * // Stream not yet consumed, can perform terminal operation
 *
 * // Problematic: Multiple uses of narrowed stream
 * Stream<Integer> stream = STREAM.narrow(numbers);
 * stream.forEach(System.out::println); // First use - OK
 * stream.count(); // Second use - IllegalStateException!
 * }</pre>
 *
 * @param <A> The element type of the stream.
 * @see java.util.stream.Stream
 * @see StreamKindHelper
 * @see StreamFunctor
 * @see StreamMonad
 */
public interface StreamKind<A> extends Kind<StreamKind.Witness, A> {

  /**
   * The phantom type marker for the Stream type constructor. This is used as the 'F' in {@code
   * Kind<F, A>}.
   */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
