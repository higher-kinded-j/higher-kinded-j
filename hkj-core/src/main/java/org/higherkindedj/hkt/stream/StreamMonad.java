// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * {@link MonadZero} instance for {@link StreamKind.Witness}. This class provides monadic operations
 * for streams, treating streams as a context that can hold zero or more values with lazy evaluation
 * semantics. It also provides a "zero" element (an empty stream).
 *
 * <p><b>Lazy Evaluation:</b> All operations provided by this monad maintain the lazy evaluation
 * characteristics of {@code java.util.stream.Stream}:
 *
 * <ul>
 *   <li>{@link #map} - Lazy transformation, function not applied until terminal operation
 *   <li>{@link #flatMap} - Lazy flattening and transformation using {@code Stream.flatMap}
 *   <li>{@link #ap} - Applies functions to values, but evaluation is still lazy
 * </ul>
 *
 * <p><b>Single-Use Semantics:</b> As with all stream operations, once a terminal operation has been
 * performed on a stream (including operations that narrow and inspect the stream), that stream
 * cannot be reused. This is a fundamental characteristic of {@code java.util.stream.Stream} and is
 * preserved in this HKT representation.
 *
 * <p><b>Monadic Composition:</b> This implementation allows for powerful stream pipeline
 * composition using monadic operations:
 *
 * <pre>{@code
 * // Example: Processing a stream of users
 * Kind<StreamKind.Witness, User> users = STREAM.widen(userRepository.findAll());
 *
 * // Lazy transformation pipeline
 * Kind<StreamKind.Witness, Order> recentOrders = streamMonad.flatMap(
 *     user -> STREAM.widen(orderRepository.findByUser(user.getId())
 *         .filter(order -> order.isRecent())),
 *     users
 * );
 *
 * // Only when we perform a terminal operation does computation happen
 * List<Order> collected = STREAM.narrow(recentOrders)
 *     .limit(100)
 *     .collect(Collectors.toList());
 * }</pre>
 *
 * @see MonadZero
 * @see Stream
 * @see StreamKind
 * @see StreamFunctor
 */
public class StreamMonad implements MonadZero<StreamKind.Witness> {

  /** A StreamMonad singleton */
  public static final StreamMonad INSTANCE = new StreamMonad();

  /** Private constructor to enforce singleton pattern. */
  protected StreamMonad() {}

  /**
   * Lifts a single value {@code a} into the Stream context. If the value is null, it creates an
   * empty stream. Otherwise, it creates a singleton stream containing the value.
   *
   * <p>This operation is eager in the sense that it creates the stream immediately, but the stream
   * itself remains lazy until a terminal operation is performed.
   *
   * @param value The value to lift. Can be {@code null}.
   * @param <A> The type of the value.
   * @return A {@code Kind<StreamKind.Witness, A>} representing a stream. If value is null, it's an
   *     empty stream; otherwise, a stream containing the single value. Never null.
   */
  @Override
  public <A> Kind<StreamKind.Witness, A> of(@Nullable A value) {
    if (value == null) {
      return STREAM.widen(Stream.empty());
    }
    return STREAM.widen(Stream.of(value));
  }

  /**
   * Applies a function contained within a Stream context to a value contained within another Stream
   * context. This involves taking all functions from the first stream and applying each one to all
   * values from the second stream, collecting all results into a new stream.
   *
   * <p><b>Eager Evaluation of Values:</b> Due to the single-use semantics of {@code
   * java.util.stream.Stream}, the values stream {@code fa} is consumed <b>eagerly</b> and collected
   * into a List. This is necessary to allow each function in {@code ff} to be applied to all values
   * without violating the stream's single-use constraint. The functions stream {@code ff} remains
   * lazy and is only consumed during terminal operations.
   *
   * <p><b>Cartesian Product Semantics:</b> Similar to {@code ListMonad}, this operation creates a
   * cartesian product of functions and values. For each function in {@code ff}, it is applied to
   * each value in {@code fa}.
   *
   * <p><b>Note:</b> If the functions stream {@code ff} is infinite, the resulting stream will also
   * be infinite and may require limiting operations to avoid unbounded computation. The values
   * stream {@code fa} must be finite since it is eagerly collected.
   *
   * @param ff A {@code Kind<StreamKind.Witness, Function<A, B>>} (a stream of functions). Must not
   *     be null. Remains lazy until terminal operation.
   * @param fa A {@code Kind<StreamKind.Witness, A>} (a stream of values). Must not be null.
   *     <b>Consumed eagerly</b> into a List.
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @return A {@code Kind<StreamKind.Witness, B>} containing all results of applying each function
   *     in {@code ff} to each value in {@code fa}. Never null. Functions are applied lazily.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped to valid Stream representations.
   */
  @Override
  public <A, B> Kind<StreamKind.Witness, B> ap(
      Kind<StreamKind.Witness, ? extends Function<A, B>> ff, Kind<StreamKind.Witness, A> fa) {

    Validation.kind().requireNonNull(ff, StreamMonad.class, AP, "function");
    Validation.kind().requireNonNull(fa, StreamMonad.class, AP, "argument");

    Stream<? extends Function<A, B>> functions = STREAM.narrow(ff);
    Stream<A> values = STREAM.narrow(fa);

    // Eagerly collect values to avoid single-use stream violation
    // Each function needs to iterate over all values, which would fail if we tried to
    // reuse the values stream. We trade laziness for correctness here.
    List<A> valuesList = values.collect(Collectors.toList());

    // Cartesian product: for each function, apply it to each value from the list
    // The functions stream remains lazy
    Stream<B> resultStream = functions.flatMap(func -> valuesList.stream().map(func));

    return STREAM.widen(resultStream);
  }

  /**
   * Maps a function over a stream in a higher-kinded context. This delegates to the {@link
   * StreamFunctor} instance.
   *
   * <p><b>Lazy Evaluation:</b> The mapping is lazy and does not force evaluation of the stream.
   *
   * @param f The function to apply. Must not be null.
   * @param fa The {@code Kind<StreamKind.Witness, A>} (a stream of values). Must not be null.
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @return A {@code Kind<StreamKind.Witness, B>} containing the results of applying {@code f} to
   *     each element. Never null. Evaluation is lazy.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<StreamKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<StreamKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", StreamMonad.class, MAP);
    Validation.kind().requireNonNull(fa, StreamMonad.class, MAP);

    return StreamFunctor.INSTANCE.map(f, fa);
  }

  /**
   * Applies a function {@code f} to each element of a stream {@code ma}, where {@code f} itself
   * returns a stream (wrapped in {@code Kind<StreamKind.Witness, B>}). All resulting streams are
   * then concatenated (flattened) into a single result stream.
   *
   * <p><b>Lazy Evaluation:</b> This operation leverages {@code Stream.flatMap}, which is lazy. The
   * function {@code f} is not called, and the resulting streams are not generated, until a terminal
   * operation is performed on the result.
   *
   * <p>This is the key operation that distinguishes a Monad from an Applicative. It allows for
   * dependent sequencing where each step can depend on the result of the previous step.
   *
   * <p><b>Example - Dependent Computation:</b>
   *
   * <pre>{@code
   * // Get all orders for each user (dependent on user data)
   * Kind<StreamKind.Witness, User> users = STREAM.widen(Stream.of(user1, user2));
   * Kind<StreamKind.Witness, Order> allOrders = streamMonad.flatMap(
   *     user -> STREAM.widen(getOrdersForUser(user)), // Returns Stream<Order>
   *     users
   * );
   * }</pre>
   *
   * @param f A function from {@code A} to {@code Kind<StreamKind.Witness, B>} (a stream of {@code
   *     B}s). Must not be null.
   * @param ma The input {@code Kind<StreamKind.Witness, A>} (a stream of {@code A}s). Must not be
   *     null.
   * @param <A> The type of elements in the input stream.
   * @param <B> The type of elements in the streams produced by the function {@code f}.
   * @return A {@code Kind<StreamKind.Witness, B>} which is the flattened stream of all results.
   *     Never null. Evaluation is lazy.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the result of
   *     {@code f} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<StreamKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<StreamKind.Witness, B>> f,
      Kind<StreamKind.Witness, A> ma) {

    Validation.function().requireFlatMapper(f, "f", StreamMonad.class, FLAT_MAP);
    Validation.kind().requireNonNull(ma, StreamMonad.class, FLAT_MAP);

    Stream<A> inputStream = STREAM.narrow(ma);

    // Leverage Stream.flatMap for lazy flattening
    Stream<B> resultStream =
        inputStream.flatMap(
            a -> {
              Kind<StreamKind.Witness, B> kindB = f.apply(a);
              Validation.function()
                  .requireNonNullResult(kindB, "f", StreamMonad.class, FLAT_MAP, Kind.class);
              return STREAM.narrow(kindB);
            });

    return STREAM.widen(resultStream);
  }

  /**
   * Returns the "zero" or "empty" value for this Monad, which is an empty stream.
   *
   * <p>The empty stream can be combined with other streams using operations like {@code flatMap} or
   * stream concatenation. It serves as the identity element for stream concatenation.
   *
   * @param <T> The type parameter of the Kind.
   * @return A {@code Kind<StreamKind.Witness, T>} representing an empty stream. Never null.
   */
  @Override
  public <T> Kind<StreamKind.Witness, T> zero() {
    return STREAM.widen(Stream.empty());
  }

  // --- Alternative Methods ---

  /**
   * Combines two streams by concatenating them.
   *
   * <p>This implements the Alternative pattern for Stream. Like List, orElse for Stream
   * concatenates both streams, representing non-deterministic choice (all possibilities). The
   * concatenation is performed lazily using {@link Stream#concat(Stream, Stream)}.
   *
   * <p><b>Lazy Evaluation:</b> The second stream is provided via {@link
   * java.util.function.Supplier}, but due to the semantics of stream concatenation, both streams
   * will be evaluated when the resulting stream is consumed. However, the supplier ensures the
   * second stream is not created until it's needed.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream1 = STREAM.widen(Stream.of(1, 2));
   * Kind<StreamKind.Witness, Integer> stream2 = () -> STREAM.widen(Stream.of(3, 4));
   *
   * Kind<StreamKind.Witness, Integer> result = orElse(stream1, stream2);
   * // result is Stream.of(1, 2, 3, 4)
   * }</pre>
   *
   * @param <A> The type of the elements in the streams
   * @param sa The first stream. Must not be null.
   * @param sb A {@link java.util.function.Supplier} providing the second stream. Must not be null.
   * @return A new stream containing all elements from both streams (sa followed by sb)
   * @throws NullPointerException if sa or sb is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if sa or the result of sb cannot be
   *     unwrapped
   */
  @Override
  public <A> Kind<StreamKind.Witness, A> orElse(
      Kind<StreamKind.Witness, A> sa, Supplier<Kind<StreamKind.Witness, A>> sb) {

    Validation.kind().requireNonNull(sa, StreamMonad.class, OR_ELSE, "first stream");
    Validation.function().requireFunction(sb, "sb", StreamMonad.class, OR_ELSE);

    Stream<A> streamA = STREAM.narrow(sa);

    // Use lazy supplier to delay evaluation of second stream
    Stream<A> concatenated =
        Stream.concat(
            streamA,
            Stream.of((A) null)
                .flatMap(
                    ignored -> {
                      Kind<StreamKind.Witness, A> kindB = sb.get();
                      Validation.function()
                          .requireNonNullResult(
                              kindB, "sb", StreamMonad.class, OR_ELSE, Stream.class);
                      return STREAM.narrow(kindB);
                    }));

    return STREAM.widen(concatenated);
  }
}
