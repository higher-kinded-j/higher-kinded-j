// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * A lazy, pull-based streaming abstraction that executes element production on virtual threads via
 * {@link VTask}. {@code VStream} fills the gap between {@link VTask} (single-value effect on
 * virtual threads) and Java's {@code Stream} (single-use, no virtual thread integration), enabling
 * composable, effectful streaming pipelines.
 *
 * <p>A {@code VStream<A>} does not produce any elements when created. It acts as a description or
 * "recipe" for a stream that will be evaluated only when a terminal operation is called. Each
 * element is produced by pulling from the stream, which returns a {@link VTask} of a {@link Step}.
 *
 * <p><b>Key Characteristics:</b>
 *
 * <ul>
 *   <li><b>Laziness:</b> Elements are produced on demand only when pulled.
 *   <li><b>Pull-based:</b> The consumer drives evaluation by calling {@link #pull()}.
 *   <li><b>Virtual Threads:</b> Each pull executes via {@link VTask}, leveraging virtual threads
 *       for scalable concurrent processing.
 *   <li><b>Composability:</b> Rich set of combinators ({@link #map}, {@link #flatMap}, {@link
 *       #filter}, {@link #take}, etc.) that preserve laziness.
 *   <li><b>Reusability:</b> Unlike Java streams, a {@code VStream} can be pulled multiple times,
 *       producing a fresh traversal each time.
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * // Create a stream from a list
 * VStream<Integer> numbers = VStream.fromList(List.of(1, 2, 3, 4, 5));
 *
 * // Build a lazy pipeline — nothing executes yet
 * VStream<String> pipeline = numbers
 *     .filter(n -> n % 2 == 0)
 *     .map(n -> "Even: " + n);
 *
 * // Terminal operation triggers evaluation
 * List<String> result = pipeline.toList().run();
 * // result: ["Even: 2", "Even: 4"]
 * }</pre>
 *
 * @param <A> The type of elements produced by this stream.
 * @see VTask
 * @see Step
 * @see VStreamKind
 */
@FunctionalInterface
public interface VStream<A> extends VStreamKind<A> {

  /**
   * Pulls the next step from this stream. This is the core operation: the consumer calls {@code
   * pull()} to request the next element. The returned {@link VTask} is lazy and executes on a
   * virtual thread when run.
   *
   * <p>The returned {@link Step} is one of:
   *
   * <ul>
   *   <li>{@link Step.Emit} — an element is available, with a continuation stream for more.
   *   <li>{@link Step.Done} — the stream is exhausted, no more elements.
   *   <li>{@link Step.Skip} — no element produced this step (used by {@link #filter}), but the
   *       stream continues.
   * </ul>
   *
   * @return A {@link VTask} that, when executed, produces the next {@link Step}. Never null.
   */
  VTask<Step<A>> pull();

  // =====================================================================
  // Step sealed type
  // =====================================================================

  /**
   * Represents a single step in pulling from a {@link VStream}. Each pull produces exactly one
   * step.
   *
   * @param <A> The type of elements in the stream.
   */
  sealed interface Step<A> permits Step.Emit, Step.Done, Step.Skip {

    /**
     * A step that produces an element and a continuation stream.
     *
     * @param value The emitted element. May be {@code null} if the stream produces nullable values.
     * @param tail The continuation stream for subsequent elements. Must not be null.
     * @param <A> The type of the element.
     */
    record Emit<A>(@Nullable A value, VStream<A> tail) implements Step<A> {
      public Emit {
        Objects.requireNonNull(tail, "tail must not be null");
      }
    }

    /**
     * A step indicating that the stream is exhausted. No more elements will be produced.
     *
     * @param <A> The phantom type parameter.
     */
    record Done<A>() implements Step<A> {}

    /**
     * A step that produces no element but indicates the stream continues. This is used internally
     * by operations like {@link VStream#filter(Predicate)} to skip non-matching elements without
     * allocating a value.
     *
     * @param tail The continuation stream. Must not be null.
     * @param <A> The type of elements in the stream.
     */
    record Skip<A>(VStream<A> tail) implements Step<A> {
      public Skip {
        Objects.requireNonNull(tail, "tail must not be null");
      }
    }
  }

  // =====================================================================
  // Seed type for unfold
  // =====================================================================

  /**
   * A value pair used by {@link VStream#unfold} to produce both an emitted element and the next
   * state for the unfold operation.
   *
   * @param value The element to emit.
   * @param next The next state to pass to the unfold function.
   * @param <A> The type of the emitted element.
   * @param <S> The type of the unfold state.
   */
  record Seed<A, S>(@Nullable A value, S next) {}

  // =====================================================================
  // Factory methods
  // =====================================================================

  /**
   * Creates an empty stream that immediately completes on the first pull.
   *
   * @param <A> The phantom type parameter.
   * @return An empty {@code VStream}. Never null.
   */
  static <A> VStream<A> empty() {
    return () -> VTask.succeed(new Step.Done<>());
  }

  /**
   * Creates a single-element stream.
   *
   * @param value The element to emit. May be {@code null}.
   * @param <A> The type of the element.
   * @return A single-element {@code VStream}. Never null.
   */
  static <A> VStream<A> of(@Nullable A value) {
    return () -> VTask.succeed(new Step.Emit<>(value, empty()));
  }

  /**
   * Creates a stream from the given elements.
   *
   * @param values The elements to emit, in order. Must not be null.
   * @param <A> The type of the elements.
   * @return A {@code VStream} of the given elements. Never null.
   * @throws NullPointerException if {@code values} is null.
   */
  @SafeVarargs
  static <A> VStream<A> of(A... values) {
    Objects.requireNonNull(values, "values must not be null");
    return fromList(List.of(values));
  }

  /**
   * Creates a stream that lazily iterates through the elements of the given list.
   *
   * <p>The list is not copied; elements are accessed by index during traversal. Modifications to
   * the list after stream creation may affect the stream's output.
   *
   * @param list The list to stream from. Must not be null.
   * @param <A> The type of the elements.
   * @return A {@code VStream} of the list's elements. Never null.
   * @throws NullPointerException if {@code list} is null.
   */
  static <A> VStream<A> fromList(List<A> list) {
    Objects.requireNonNull(list, "list must not be null");
    return fromListAt(list, 0);
  }

  /**
   * Creates a stream that lazily consumes elements from the given Java {@code Stream} via its
   * iterator.
   *
   * <p><b>Warning:</b> The Java stream is consumed incrementally. Once its iterator is exhausted,
   * the resulting {@code VStream} will be empty on subsequent traversals.
   *
   * @param stream The Java stream to consume. Must not be null.
   * @param <A> The type of the elements.
   * @return A {@code VStream} wrapping the Java stream's iterator. Never null.
   * @throws NullPointerException if {@code stream} is null.
   */
  static <A> VStream<A> fromStream(Stream<A> stream) {
    Objects.requireNonNull(stream, "stream must not be null");
    return fromIterator(stream.iterator());
  }

  /**
   * Creates a stream that lazily consumes elements from the given iterator.
   *
   * <p><b>Warning:</b> The iterator is consumed incrementally. Once exhausted, the resulting {@code
   * VStream} will be empty on subsequent traversals. If you need a reusable stream, prefer {@link
   * #fromList(List)}.
   *
   * @param iterator The iterator to consume. Must not be null.
   * @param <A> The type of the elements.
   * @return A {@code VStream} wrapping the iterator. Never null.
   * @throws NullPointerException if {@code iterator} is null.
   */
  static <A> VStream<A> fromIterator(Iterator<A> iterator) {
    Objects.requireNonNull(iterator, "iterator must not be null");
    return () ->
        VTask.delay(
            () -> {
              if (iterator.hasNext()) {
                A value = iterator.next();
                return new Step.Emit<>(value, fromIterator(iterator));
              } else {
                return new Step.Done<>();
              }
            });
  }

  /**
   * Creates a single-element stream. Alias for {@link #of(Object)}, consistent with {@link
   * VTask#succeed(Object)}.
   *
   * @param value The element to emit. May be {@code null}.
   * @param <A> The type of the element.
   * @return A single-element {@code VStream}. Never null.
   */
  static <A> VStream<A> succeed(@Nullable A value) {
    return of(value);
  }

  /**
   * Creates a stream whose first pull fails with the given error.
   *
   * @param error The error to raise. Must not be null.
   * @param <A> The phantom type parameter.
   * @return A failing {@code VStream}. Never null.
   * @throws NullPointerException if {@code error} is null.
   */
  static <A> VStream<A> fail(Throwable error) {
    Objects.requireNonNull(error, "error must not be null");
    return () -> VTask.fail(error);
  }

  /**
   * Creates an infinite stream by repeatedly applying a function to the previous value, starting
   * from the given seed.
   *
   * <p><b>Warning:</b> This produces an infinite stream. Use {@link #take(long)} or {@link
   * #takeWhile(Predicate)} to limit consumption.
   *
   * @param seed The initial value.
   * @param f The function to produce the next value from the current one. Must not be null.
   * @param <A> The type of the elements.
   * @return An infinite {@code VStream}. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  static <A> VStream<A> iterate(@Nullable A seed, UnaryOperator<A> f) {
    Objects.requireNonNull(f, "f must not be null");
    return () -> VTask.succeed(new Step.Emit<>(seed, iterate(f.apply(seed), f)));
  }

  /**
   * Creates a stream by effectfully unfolding from an initial state. The function is applied to the
   * current state to produce either a {@link Seed} containing the emitted value and the next state,
   * or {@link Optional#empty()} to signal completion.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * // Paginated API fetch
   * VStream<Page> pages = VStream.unfold(1, page ->
   *     VTask.of(() -> {
   *         Page result = api.fetchPage(page);
   *         if (result.isEmpty()) return Optional.empty();
   *         return Optional.of(new VStream.Seed<>(result, page + 1));
   *     }));
   * }</pre>
   *
   * @param initialState The initial state for the unfold operation.
   * @param f A function that produces the next value and state, or empty to complete. Must not be
   *     null.
   * @param <S> The type of the unfold state.
   * @param <A> The type of the emitted elements.
   * @return A {@code VStream} produced by unfolding. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  static <S, A> VStream<A> unfold(S initialState, Function<S, VTask<Optional<Seed<A, S>>>> f) {
    Objects.requireNonNull(f, "f must not be null");
    return () ->
        f.apply(initialState)
            .map(
                opt ->
                    opt.<Step<A>>map(seed -> new Step.Emit<>(seed.value(), unfold(seed.next(), f)))
                        .orElseGet(Step.Done::new));
  }

  /**
   * Creates an infinite stream by repeatedly calling the given supplier.
   *
   * <p><b>Warning:</b> This produces an infinite stream. Use {@link #take(long)} or {@link
   * #takeWhile(Predicate)} to limit consumption.
   *
   * @param supplier The supplier to call for each element. Must not be null.
   * @param <A> The type of the elements.
   * @return An infinite {@code VStream}. Never null.
   * @throws NullPointerException if {@code supplier} is null.
   */
  static <A> VStream<A> generate(Supplier<A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return () -> VTask.succeed(new Step.Emit<>(supplier.get(), generate(supplier)));
  }

  /**
   * Concatenates two streams. All elements from {@code first} are emitted before elements from
   * {@code second}.
   *
   * @param first The first stream. Must not be null.
   * @param second The second stream. Must not be null.
   * @param <A> The type of the elements.
   * @return A concatenated {@code VStream}. Never null.
   * @throws NullPointerException if either argument is null.
   */
  static <A> VStream<A> concat(VStream<A> first, VStream<A> second) {
    Objects.requireNonNull(first, "first must not be null");
    Objects.requireNonNull(second, "second must not be null");
    return () ->
        first
            .pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e -> new Step.Emit<>(e.value(), concat(e.tail(), second));
                      case Step.Skip<A> s -> new Step.Skip<>(concat(s.tail(), second));
                      case Step.Done<A> _ -> new Step.Skip<>(second);
                    });
  }

  /**
   * Creates an infinite stream that repeats the given value.
   *
   * <p><b>Warning:</b> This produces an infinite stream.
   *
   * @param value The value to repeat. May be {@code null}.
   * @param <A> The type of the element.
   * @return An infinite repeating {@code VStream}. Never null.
   */
  static <A> VStream<A> repeat(@Nullable A value) {
    return () -> VTask.succeed(new Step.Emit<>(value, repeat(value)));
  }

  /**
   * Creates a stream of integers in the range {@code [start, end)}.
   *
   * @param start The inclusive start of the range.
   * @param end The exclusive end of the range.
   * @return A {@code VStream} of integers. Never null.
   */
  static VStream<Integer> range(int start, int end) {
    if (start >= end) {
      return empty();
    }
    return () -> VTask.succeed(new Step.Emit<>(start, range(start + 1, end)));
  }

  /**
   * Defers the construction of a stream until the first pull. This enables recursive stream
   * definitions without stack overflow.
   *
   * @param supplier A supplier that produces the stream. Must not be null.
   * @param <A> The type of the elements.
   * @return A deferred {@code VStream}. Never null.
   * @throws NullPointerException if {@code supplier} is null.
   */
  static <A> VStream<A> defer(Supplier<VStream<A>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return () -> supplier.get().pull();
  }

  // =====================================================================
  // Transformation combinators
  // =====================================================================

  /**
   * Transforms each element of this stream using the given function. The transformation is lazy:
   * the function is applied only when elements are pulled.
   *
   * @param f The mapping function. Must not be null.
   * @param <B> The type of the transformed elements.
   * @return A new {@code VStream} with transformed elements. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> VStream<B> map(Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new MappedStream<>(this, f);
  }

  /**
   * Substitutes each element with a sub-stream produced by the given function, and flattens the
   * results into a single stream. This is the monadic bind (flatMap) operation for {@code VStream}.
   *
   * <p>The implementation uses an iterative approach for processing Skip and Done steps from inner
   * streams, ensuring stack safety for deep flatMap chains.
   *
   * @param f A function producing a sub-stream for each element. Must not be null.
   * @param <B> The type of elements in the resulting stream.
   * @return A new flattened {@code VStream}. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> VStream<B> flatMap(Function<? super A, ? extends VStream<B>> f) {
    Objects.requireNonNull(f, "f must not be null");
    VStream<A> outer = this;
    return new FlatMapStream<>(outer, f);
  }

  /**
   * Alias for {@link #flatMap(Function)}. Chains this stream with a sub-stream-producing function.
   * Named to align with the FocusDSL vocabulary.
   *
   * @param f A function producing a sub-stream for each element. Must not be null.
   * @param <B> The type of elements in the resulting stream.
   * @return A new flattened {@code VStream}. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> VStream<B> via(Function<? super A, ? extends VStream<B>> f) {
    return flatMap(f);
  }

  /**
   * Transforms each element using an effectful function that returns a {@link VTask}.
   *
   * @param f A function producing a {@link VTask} for each element. Must not be null.
   * @param <B> The type of the transformed elements.
   * @return A new {@code VStream} with effectfully transformed elements. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> VStream<B> mapTask(Function<? super A, ? extends VTask<B>> f) {
    Objects.requireNonNull(f, "f must not be null");
    return () ->
        this.pull()
            .flatMap(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          f.apply(e.value())
                              .map(b -> (Step<B>) new Step.Emit<>(b, e.tail().mapTask(f)));
                      case Step.Skip<A> s ->
                          VTask.succeed((Step<B>) new Step.Skip<>(s.tail().mapTask(f)));
                      case Step.Done<A> _ -> VTask.succeed(new Step.Done<>());
                    });
  }

  // =====================================================================
  // Filtering combinators
  // =====================================================================

  /**
   * Keeps only elements matching the given predicate. Non-matching elements produce a {@link
   * Step.Skip}, preserving laziness without allocating values.
   *
   * @param predicate The predicate to test elements against. Must not be null.
   * @return A new filtered {@code VStream}. Never null.
   * @throws NullPointerException if {@code predicate} is null.
   */
  default VStream<A> filter(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return () ->
        this.pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          predicate.test(e.value())
                              ? new Step.Emit<>(e.value(), e.tail().filter(predicate))
                              : new Step.Skip<>(e.tail().filter(predicate));
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().filter(predicate));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
  }

  /**
   * Takes elements while the predicate holds, then completes.
   *
   * @param predicate The predicate to test elements against. Must not be null.
   * @return A new {@code VStream} that completes when the predicate fails. Never null.
   * @throws NullPointerException if {@code predicate} is null.
   */
  default VStream<A> takeWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return () ->
        this.pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          predicate.test(e.value())
                              ? new Step.Emit<>(e.value(), e.tail().takeWhile(predicate))
                              : new Step.Done<>();
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().takeWhile(predicate));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
  }

  /**
   * Drops elements while the predicate holds, then emits all remaining elements.
   *
   * @param predicate The predicate to test elements against. Must not be null.
   * @return A new {@code VStream} that skips initial matching elements. Never null.
   * @throws NullPointerException if {@code predicate} is null.
   */
  default VStream<A> dropWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return () ->
        this.pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          predicate.test(e.value())
                              ? new Step.Skip<>(e.tail().dropWhile(predicate))
                              : new Step.Emit<>(e.value(), e.tail());
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().dropWhile(predicate));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
  }

  /**
   * Takes at most the first {@code n} elements from this stream.
   *
   * @param n The maximum number of elements to take. Must be non-negative.
   * @return A new {@code VStream} limited to {@code n} elements. Never null.
   */
  default VStream<A> take(long n) {
    if (n <= 0) {
      return empty();
    }
    return () ->
        this.pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e -> new Step.Emit<>(e.value(), e.tail().take(n - 1));
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().take(n));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
  }

  /**
   * Drops the first {@code n} elements from this stream, then emits all remaining.
   *
   * @param n The number of elements to drop. Must be non-negative.
   * @return A new {@code VStream} with the first {@code n} elements removed. Never null.
   */
  default VStream<A> drop(long n) {
    if (n <= 0) {
      return this;
    }
    return () ->
        this.pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e -> new Step.Skip<>(e.tail().drop(n - 1));
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().drop(n));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
  }

  /**
   * Removes duplicate elements from this stream. Elements are compared using {@link
   * Object#equals(Object)} and tracked in a {@link HashSet}.
   *
   * <p><b>Warning:</b> For infinite streams, the internal set grows without bound. Use with caution
   * on large or infinite streams.
   *
   * @return A new {@code VStream} with duplicates removed. Never null.
   */
  default VStream<A> distinct() {
    return VStream.defer(
        () -> {
          Set<A> seen = new HashSet<>();
          return filter(seen::add);
        });
  }

  // =====================================================================
  // Combination operations
  // =====================================================================

  /**
   * Appends another stream after this one. All elements from this stream are emitted before
   * elements from {@code other}.
   *
   * @param other The stream to append. Must not be null.
   * @return A concatenated {@code VStream}. Never null.
   * @throws NullPointerException if {@code other} is null.
   */
  default VStream<A> concat(VStream<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    return VStream.concat(this, other);
  }

  /**
   * Adds an element at the beginning of this stream.
   *
   * @param value The element to prepend. May be {@code null}.
   * @return A new {@code VStream} with the element at the front. Never null.
   */
  default VStream<A> prepend(@Nullable A value) {
    return () -> VTask.succeed(new Step.Emit<>(value, this));
  }

  /**
   * Adds an element at the end of this stream.
   *
   * @param value The element to append. May be {@code null}.
   * @return A new {@code VStream} with the element at the end. Never null.
   */
  default VStream<A> append(@Nullable A value) {
    return this.concat(VStream.of(value));
  }

  /**
   * Pairs elements from this stream with elements from another stream using the given combiner
   * function. The resulting stream has length equal to the shorter of the two input streams.
   *
   * @param other The other stream to zip with. Must not be null.
   * @param combiner The function to combine paired elements. Must not be null.
   * @param <B> The type of elements in the other stream.
   * @param <C> The type of elements in the resulting stream.
   * @return A new zipped {@code VStream}. Never null.
   * @throws NullPointerException if either argument is null.
   */
  default <B, C> VStream<C> zipWith(VStream<B> other, BiFunction<A, B, C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");
    return new ZipWithStream<>(this, other, combiner);
  }

  /**
   * Alternates elements from this stream and the other stream. If one stream is shorter, the
   * remaining elements from the longer stream are appended.
   *
   * @param other The other stream to interleave with. Must not be null.
   * @return A new interleaved {@code VStream}. Never null.
   * @throws NullPointerException if {@code other} is null.
   */
  default VStream<A> interleave(VStream<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    return new InterleaveStream<>(this, other);
  }

  // =====================================================================
  // Observation
  // =====================================================================

  /**
   * Performs a side effect on each element without modifying it. Useful for debugging and logging.
   *
   * @param action The action to perform on each element. Must not be null.
   * @return A new {@code VStream} that performs the action. Never null.
   * @throws NullPointerException if {@code action} is null.
   */
  default VStream<A> peek(Consumer<? super A> action) {
    Objects.requireNonNull(action, "action must not be null");
    return () ->
        this.pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e -> {
                        action.accept(e.value());
                        yield new Step.Emit<>(e.value(), e.tail().peek(action));
                      }
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().peek(action));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
  }

  /**
   * Runs an action when this stream completes (reaches {@link Step.Done}).
   *
   * @param action The action to run on completion. Must not be null.
   * @return A new {@code VStream} that runs the action on completion. Never null.
   * @throws NullPointerException if {@code action} is null.
   */
  default VStream<A> onComplete(Runnable action) {
    Objects.requireNonNull(action, "action must not be null");
    return () ->
        this.pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          new Step.Emit<>(e.value(), e.tail().onComplete(action));
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().onComplete(action));
                      case Step.Done<A> _ -> {
                        action.run();
                        yield new Step.Done<>();
                      }
                    });
  }

  // =====================================================================
  // Terminal operations (return VTask)
  // =====================================================================

  /**
   * Collects all elements into a list. This is a terminal operation that executes the stream.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate. Use {@link #take(long)} to
   * limit the stream first.
   *
   * @return A {@link VTask} that produces the list of all elements. Never null.
   */
  default VTask<List<A>> toList() {
    return VTask.of(
        () -> {
          List<A> result = new ArrayList<>();
          VStream<A> current = this;
          while (true) {
            Step<A> step = current.pull().run();
            switch (step) {
              case Step.Emit<A> e -> {
                result.add(e.value());
                current = e.tail();
              }
              case Step.Skip<A> s -> current = s.tail();
              case Step.Done<A> _ -> {
                return Collections.unmodifiableList(result);
              }
            }
          }
        });
  }

  /**
   * Left-folds all elements with the given identity and operator.
   *
   * @param identity The initial accumulator value.
   * @param op The binary operator to combine accumulator with each element. Must not be null.
   * @return A {@link VTask} that produces the fold result. Never null.
   * @throws NullPointerException if {@code op} is null.
   */
  default VTask<A> fold(@Nullable A identity, BinaryOperator<A> op) {
    Objects.requireNonNull(op, "op must not be null");
    return foldLeft(identity, op);
  }

  /**
   * Left-folds all elements with the given initial value and accumulator function.
   *
   * @param initial The initial accumulator value.
   * @param f The accumulator function. Must not be null.
   * @param <B> The type of the accumulator.
   * @return A {@link VTask} that produces the fold result. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> VTask<B> foldLeft(@Nullable B initial, BiFunction<B, A, B> f) {
    Objects.requireNonNull(f, "f must not be null");
    return VTask.of(
        () -> {
          B acc = initial;
          VStream<A> current = this;
          while (true) {
            Step<A> step = current.pull().run();
            switch (step) {
              case Step.Emit<A> e -> {
                acc = f.apply(acc, e.value());
                current = e.tail();
              }
              case Step.Skip<A> s -> current = s.tail();
              case Step.Done<A> _ -> {
                return acc;
              }
            }
          }
        });
  }

  /**
   * Returns the first element of this stream, or empty if the stream is empty.
   *
   * @return A {@link VTask} that produces the first element wrapped in an {@link Optional}. Never
   *     null.
   */
  default VTask<Optional<A>> headOption() {
    return VTask.of(
        () -> {
          VStream<A> current = this;
          while (true) {
            Step<A> step = current.pull().run();
            switch (step) {
              case Step.Emit<A> e -> {
                return Optional.ofNullable(e.value());
              }
              case Step.Skip<A> s -> current = s.tail();
              case Step.Done<A> _ -> {
                return Optional.empty();
              }
            }
          }
        });
  }

  /**
   * Returns the last element of this stream, or empty if the stream is empty.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate.
   *
   * @return A {@link VTask} that produces the last element wrapped in an {@link Optional}. Never
   *     null.
   */
  default VTask<Optional<A>> lastOption() {
    return VTask.of(
        () -> {
          A last = null;
          boolean found = false;
          VStream<A> current = this;
          while (true) {
            Step<A> step = current.pull().run();
            switch (step) {
              case Step.Emit<A> e -> {
                last = e.value();
                found = true;
                current = e.tail();
              }
              case Step.Skip<A> s -> current = s.tail();
              case Step.Done<A> _ -> {
                return found ? Optional.ofNullable(last) : Optional.empty();
              }
            }
          }
        });
  }

  /**
   * Counts the number of elements in this stream.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate.
   *
   * @return A {@link VTask} that produces the element count. Never null.
   */
  default VTask<Long> count() {
    return foldLeft(0L, (acc, _) -> acc + 1);
  }

  /**
   * Checks whether any element matches the given predicate. Short-circuits on the first match.
   *
   * @param predicate The predicate to test. Must not be null.
   * @return A {@link VTask} that produces {@code true} if any element matches. Never null.
   * @throws NullPointerException if {@code predicate} is null.
   */
  default VTask<Boolean> exists(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return VTask.of(
        () -> {
          VStream<A> current = this;
          while (true) {
            Step<A> step = current.pull().run();
            switch (step) {
              case Step.Emit<A> e -> {
                if (predicate.test(e.value())) return true;
                current = e.tail();
              }
              case Step.Skip<A> s -> current = s.tail();
              case Step.Done<A> _ -> {
                return false;
              }
            }
          }
        });
  }

  /**
   * Checks whether all elements match the given predicate. Short-circuits on the first non-match.
   *
   * <p>Returns {@code true} for an empty stream (vacuous truth).
   *
   * @param predicate The predicate to test. Must not be null.
   * @return A {@link VTask} that produces {@code true} if all elements match. Never null.
   * @throws NullPointerException if {@code predicate} is null.
   */
  default VTask<Boolean> forAll(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return VTask.of(
        () -> {
          VStream<A> current = this;
          while (true) {
            Step<A> step = current.pull().run();
            switch (step) {
              case Step.Emit<A> e -> {
                if (!predicate.test(e.value())) return false;
                current = e.tail();
              }
              case Step.Skip<A> s -> current = s.tail();
              case Step.Done<A> _ -> {
                return true;
              }
            }
          }
        });
  }

  /**
   * Finds the first element matching the given predicate. Short-circuits on the first match.
   *
   * @param predicate The predicate to test. Must not be null.
   * @return A {@link VTask} that produces the first match wrapped in an {@link Optional}. Never
   *     null.
   * @throws NullPointerException if {@code predicate} is null.
   */
  default VTask<Optional<A>> find(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return VTask.of(
        () -> {
          VStream<A> current = this;
          while (true) {
            Step<A> step = current.pull().run();
            switch (step) {
              case Step.Emit<A> e -> {
                if (predicate.test(e.value())) return Optional.ofNullable(e.value());
                current = e.tail();
              }
              case Step.Skip<A> s -> current = s.tail();
              case Step.Done<A> _ -> {
                return Optional.empty();
              }
            }
          }
        });
  }

  /**
   * Executes a side effect for each element in this stream.
   *
   * @param action The action to perform on each element. Must not be null.
   * @return A {@link VTask} that completes when all elements have been processed. Never null.
   * @throws NullPointerException if {@code action} is null.
   */
  default VTask<Unit> forEach(Consumer<? super A> action) {
    Objects.requireNonNull(action, "action must not be null");
    return VTask.of(
        () -> {
          VStream<A> current = this;
          while (true) {
            Step<A> step = current.pull().run();
            switch (step) {
              case Step.Emit<A> e -> {
                action.accept(e.value());
                current = e.tail();
              }
              case Step.Skip<A> s -> current = s.tail();
              case Step.Done<A> _ -> {
                return Unit.INSTANCE;
              }
            }
          }
        });
  }

  /**
   * Drains this stream, discarding all elements. Useful for executing side effects attached via
   * {@link #peek(Consumer)}.
   *
   * @return A {@link VTask} that completes when the stream is fully consumed. Never null.
   */
  default VTask<Unit> drain() {
    return forEach(_ -> {});
  }

  /**
   * Maps all elements to {@link Unit}, discarding values but preserving stream structure.
   *
   * @return A new {@code VStream<Unit>}. Never null.
   */
  default VStream<Unit> asUnit() {
    return this.map(_ -> Unit.INSTANCE);
  }

  // =====================================================================
  // Error handling
  // =====================================================================

  /**
   * Recovers from a failed pull by replacing the error with a value. Recovery applies per-pull: if
   * a single element's pull fails, the recovery value is emitted and the stream continues.
   *
   * @param recoveryFunction A function that produces a recovery value from the error. Must not be
   *     null.
   * @return A new {@code VStream} with per-pull error recovery. Never null.
   * @throws NullPointerException if {@code recoveryFunction} is null.
   */
  default VStream<A> recover(Function<? super Throwable, ? extends A> recoveryFunction) {
    Objects.requireNonNull(recoveryFunction, "recoveryFunction must not be null");
    return () ->
        this.pull()
            .recover(error -> new Step.Emit<>(recoveryFunction.apply(error), VStream.<A>empty()));
  }

  /**
   * Recovers from a failed pull by substituting a recovery stream. Recovery applies per-pull: if a
   * single element's pull fails, the recovery stream replaces the remainder.
   *
   * @param recoveryFunction A function that produces a recovery stream from the error. Must not be
   *     null.
   * @return A new {@code VStream} with per-pull error recovery. Never null.
   * @throws NullPointerException if {@code recoveryFunction} is null.
   */
  default VStream<A> recoverWith(
      Function<? super Throwable, ? extends VStream<A>> recoveryFunction) {
    Objects.requireNonNull(recoveryFunction, "recoveryFunction must not be null");
    return () -> this.pull().recoverWith(error -> recoveryFunction.apply(error).pull());
  }

  /**
   * Transforms errors from failed pulls using the given function.
   *
   * @param f A function that transforms the error. Must not be null.
   * @return A new {@code VStream} with transformed errors. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default VStream<A> mapError(Function<? super Throwable, ? extends Throwable> f) {
    Objects.requireNonNull(f, "f must not be null");
    return () -> this.pull().mapError(f);
  }

  /**
   * Observes errors from failed pulls without modifying them. Useful for logging.
   *
   * @param action The action to perform on errors. Must not be null.
   * @return A new {@code VStream} that observes errors. Never null.
   * @throws NullPointerException if {@code action} is null.
   */
  default VStream<A> onError(Consumer<? super Throwable> action) {
    Objects.requireNonNull(action, "action must not be null");
    return () ->
        this.pull()
            .mapError(
                error -> {
                  action.accept(error);
                  return error;
                });
  }

  // =====================================================================
  // Utility
  // =====================================================================

  /**
   * Collects all elements to a list, capturing any error in a {@link
   * org.higherkindedj.hkt.trymonad.Try}.
   *
   * @return A {@link VTask} producing a {@link org.higherkindedj.hkt.trymonad.Try} of the element
   *     list. Never null.
   */
  default VTask<Try<List<A>>> runSafe() {
    return VTask.delay(() -> toList().runSafe());
  }

  /**
   * Collects all elements to a list asynchronously on a virtual thread.
   *
   * @return A {@link java.util.concurrent.CompletableFuture} of the element list. Never null.
   */
  default CompletableFuture<List<A>> runAsync() {
    return toList().runAsync();
  }

  // =====================================================================
  // Internal helpers
  // =====================================================================

  /**
   * Creates a stream starting at the given index in a list.
   *
   * @param list The list to stream from.
   * @param index The starting index.
   * @param <A> The element type.
   * @return A VStream starting at the given index.
   */
  private static <A> VStream<A> fromListAt(List<A> list, int index) {
    if (index >= list.size()) {
      return empty();
    }
    return () -> VTask.succeed(new Step.Emit<>(list.get(index), fromListAt(list, index + 1)));
  }
}

// =====================================================================
// Internal stream implementations
// =====================================================================

/**
 * Stack-safe flatMap implementation. Maintains an inner stream reference and processes steps
 * iteratively within each pull.
 */
final class FlatMapStream<A, B> implements VStream<B> {

  private final VStream<A> outer;
  private final Function<? super A, ? extends VStream<B>> f;

  FlatMapStream(VStream<A> outer, Function<? super A, ? extends VStream<B>> f) {
    this.outer = outer;
    this.f = f;
  }

  @Override
  public VTask<Step<B>> pull() {
    return outer
        .pull()
        .flatMap(
            step ->
                switch (step) {
                  case Step.Emit<A> e -> {
                    VStream<B> inner = f.apply(e.value());
                    Objects.requireNonNull(inner, "flatMap function returned null stream");
                    yield VTask.succeed(
                        new Step.Skip<>(VStream.concat(inner, e.tail().flatMap(f))));
                  }
                  case Step.Skip<A> s -> VTask.succeed(new Step.Skip<>(s.tail().flatMap(f)));
                  case Step.Done<A> _ -> VTask.succeed(new Step.Done<>());
                });
  }
}

/**
 * Positional zip implementation. Pairs elements from two streams, stopping at the shorter one.
 * Handles Skip steps from either side by retrying until both sides produce Emit or one produces
 * Done.
 */
final class ZipWithStream<A, B, C> implements VStream<C> {

  private final VStream<A> left;
  private final VStream<B> right;
  private final BiFunction<A, B, C> combiner;

  ZipWithStream(VStream<A> left, VStream<B> right, BiFunction<A, B, C> combiner) {
    this.left = left;
    this.right = right;
    this.combiner = combiner;
  }

  @Override
  public VTask<Step<C>> pull() {
    return left.pull()
        .flatMap(
            leftStep ->
                switch (leftStep) {
                  case Step.Skip<A> s ->
                      VTask.succeed(new Step.Skip<>(s.tail().zipWith(right, combiner)));
                  case Step.Done<A> _ -> VTask.succeed(new Step.Done<>());
                  case Step.Emit<A> leftEmit ->
                      right
                          .pull()
                          .flatMap(
                              rightStep ->
                                  switch (rightStep) {
                                    case Step.Skip<B> s ->
                                        // Re-emit leftEmit by wrapping in a single-element stream
                                        // zipped with the skip tail
                                        VTask.succeed(
                                            new Step.Skip<>(
                                                VStream.concat(
                                                        VStream.of(leftEmit.value()),
                                                        leftEmit.tail())
                                                    .zipWith(s.tail(), combiner)));
                                    case Step.Done<B> _ -> VTask.succeed(new Step.Done<>());
                                    case Step.Emit<B> rightEmit -> {
                                      C combined =
                                          combiner.apply(leftEmit.value(), rightEmit.value());
                                      yield VTask.succeed(
                                          new Step.Emit<>(
                                              combined,
                                              leftEmit.tail().zipWith(rightEmit.tail(), combiner)));
                                    }
                                  });
                });
  }
}

/**
 * Interleave implementation. Alternates elements from two streams, appending any remaining elements
 * from the longer stream.
 */
final class InterleaveStream<A> implements VStream<A> {

  private final VStream<A> first;
  private final VStream<A> second;

  InterleaveStream(VStream<A> first, VStream<A> second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public VTask<Step<A>> pull() {
    return first
        .pull()
        .map(
            step ->
                switch (step) {
                  case Step.Emit<A> e ->
                      new Step.Emit<>(e.value(), new InterleaveStream<>(second, e.tail()));
                  case Step.Skip<A> s -> new Step.Skip<>(new InterleaveStream<>(second, s.tail()));
                  case Step.Done<A> _ -> new Step.Skip<>(second);
                });
  }
}

/**
 * Map-fusion implementation. Consecutive {@code map()} calls compose their functions rather than
 * nesting pull delegates, preventing stack overflow on deep map chains.
 *
 * <p>When {@code map()} is called on a {@code MappedStream}, the functions are composed into a
 * single {@code MappedStream} with a composed function, keeping the pull delegation depth at one
 * regardless of how many {@code map()} calls are chained.
 *
 * @param <A> The source element type.
 * @param <B> The output element type.
 */
final class MappedStream<A, B> implements VStream<B> {

  private final VStream<A> source;
  private final Function<? super A, ? extends B> mapper;

  MappedStream(VStream<A> source, Function<? super A, ? extends B> mapper) {
    this.source = source;
    this.mapper = mapper;
  }

  @Override
  public VTask<Step<B>> pull() {
    return source
        .pull()
        .map(
            step ->
                switch (step) {
                  case Step.Emit<A> e ->
                      new Step.Emit<>(mapper.apply(e.value()), e.tail().map(mapper));
                  case Step.Skip<A> s -> new Step.Skip<>(s.tail().map(mapper));
                  case Step.Done<A> _ -> new Step.Done<>();
                });
  }

  @Override
  @SuppressWarnings("unchecked")
  public <C> VStream<C> map(Function<? super B, ? extends C> f) {
    Objects.requireNonNull(f, "f must not be null");
    // Fuse consecutive maps: compose functions instead of nesting streams.
    // This keeps the pull() delegation depth at 1 regardless of chain length.
    Function<? super A, ? extends C> composed = ((Function<A, B>) mapper).andThen(f);
    return new MappedStream<>(source, composed);
  }
}
