// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Functor} type class for {@link java.util.stream.Stream}, using {@link
 * StreamKind.Witness} as the higher-kinded type witness.
 *
 * <p>A {@link Functor} provides the ability to apply a function to a value inside a context (in
 * this case, a {@code Stream}) without needing to explicitly extract the value. The {@link
 * #map(Function, Kind)} operation transforms a {@code Stream<A>} into a {@code Stream<B>} by
 * applying a function {@code A -> B} to each element of the stream.
 *
 * <p><b>Lazy Evaluation:</b> Unlike collection-based functors (such as {@code ListFunctor}), the
 * {@code map} operation on streams is lazy. The transformation function is not applied immediately
 * but is instead recorded as part of the stream pipeline. The function will only be executed when a
 * terminal operation (such as {@code forEach}, {@code collect}, or {@code toList}) is performed on
 * the stream.
 *
 * <p>This lazy evaluation has important implications:
 *
 * <ul>
 *   <li><b>Efficiency:</b> Multiple {@code map} operations can be fused together, avoiding
 *       intermediate allocations.
 *   <li><b>Infinite streams:</b> You can map over infinite streams without triggering infinite
 *       loops, as long as downstream operations are finite (e.g., {@code limit}, {@code findFirst}.
 *   <li><b>Short-circuiting:</b> If a terminal operation short-circuits (e.g., {@code findFirst}),
 *       the map function is only applied to elements until the condition is satisfied.
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * // Lazy mapping - no computation performed yet
 * Kind<StreamKind.Witness, Integer> numbers =
 *     STREAM.widen(Stream.iterate(0, n -> n + 1)); // infinite stream
 * Kind<StreamKind.Witness, Integer> squares =
 *     streamFunctor.map(n -> n * n, numbers);
 *
 * // Only when we add a terminal operation does computation happen
 * List<Integer> firstTenSquares =
 *     STREAM.narrow(squares).limit(10).collect(Collectors.toList());
 * // Result: [0, 1, 4, 9, 16, 25, 36, 49, 64, 81]
 * }</pre>
 *
 * @see Functor
 * @see Stream
 * @see StreamKind
 * @see StreamKindHelper
 * @see StreamMonad
 */
class StreamFunctor implements Functor<StreamKind.Witness> {

  /**
   * Singleton instance of {@code StreamFunctor}. Consider accessing Functor operations via {@link
   * StreamMonad#INSTANCE}.
   */
  public static final StreamFunctor INSTANCE = new StreamFunctor();

  /**
   * Package-private constructor to allow instantiation within the package, primarily for {@link
   * StreamMonad#INSTANCE}.
   */
  StreamFunctor() {
    // Constructor for package-level access or for singleton.
  }

  /**
   * Applies a function to each element of a stream wrapped in a {@link Kind}.
   *
   * <p>If the input stream ({@code fa}) is {@code StreamKind(Stream<A>)}, this method applies the
   * function {@code f: A -> B} to each element of the stream, producing a new {@code
   * StreamKind(Stream<B>)}.
   *
   * <p><b>Lazy Evaluation:</b> This operation is lazy and does not force evaluation of the stream.
   * The transformation function {@code f} is added to the stream pipeline but is not executed until
   * a terminal operation is performed on the resulting stream.
   *
   * <p>This operation adheres to the Functor laws:
   *
   * <ol>
   *   <li>Identity: {@code map(x -> x, fa)} is equivalent to {@code fa}.
   *   <li>Composition: {@code map(g.compose(f), fa)} is equivalent to {@code map(g, map(f, fa))}.
   * </ol>
   *
   * <p><b>Single-Use Warning:</b> The input {@code fa} stream is consumed by this operation (in
   * terms of being extracted from the Kind wrapper). The resulting stream is also single-use. Do
   * not attempt to reuse the original stream after calling this method.
   *
   * @param <A> The type of the elements in the input stream.
   * @param <B> The type of the elements in the output stream after applying the function.
   * @param f The non-null function to apply to each element of the stream.
   * @param fa The non-null {@code Kind<StreamKind.Witness, A>} (which is a {@code StreamKind<A>})
   *     containing the input stream.
   * @return A new non-null {@code Kind<StreamKind.Witness, B>} containing a stream with the results
   *     of applying the function {@code f} to each element of the input stream. The transformation
   *     is lazy.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} is not a valid {@code
   *     StreamKind} representation.
   */
  @Override
  public <A, B> Kind<StreamKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<StreamKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", StreamFunctor.class, MAP);
    Validation.kind().requireNonNull(fa, StreamFunctor.class, MAP);

    Stream<A> streamA = STREAM.narrow(fa);
    Stream<B> streamB = streamA.map(f);
    return STREAM.widen(streamB);
  }
}
