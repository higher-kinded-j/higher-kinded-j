// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

  /**
   * Signals early termination of this stream, allowing any attached finalisers to run.
   *
   * <p>The default implementation is a no-op, for a stream that holds nothing to release. Streams
   * created by {@link #onFinalize(VTask)} override this to execute their finaliser, and every
   * operator closes the streams it is reading from, so closing a stream reaches every finaliser
   * upstream of it, the innermost first. A {@link #defer} stream builds a new stream each time its
   * pulled {@code VTask} runs and keeps none of them, so closing it closes nothing: close the
   * stream you are reading. A {@link #bracket} stream remembers its latest run, so closing its head
   * after a pull releases the resource that run acquired. An operator that applies your function to
   * an element closes the rest of the stream if the function throws. Operations that stop before a
   * stream ends, such as {@link #take}, {@link #takeWhile}, {@link #headOption} and {@link #find},
   * and terminal operations that fail, close what they leave unread. {@link #concat(VStream)} and
   * {@link #prepend} close only the stream they are reading, and leave a stream they have not
   * reached as it is, since it may be another consumer's.
   *
   * <p>A stream you write that reads from another should override this to close it, or closing
   * stops there.
   *
   * <p>One finaliser failing does not stop the others. The first failure is thrown, with later ones
   * suppressed onto it. Closing a stream more than once is safe: each finaliser runs once for each
   * consumption, however often it is closed.
   *
   * @return A {@link VTask} that completes after all finalisers have run. Never null.
   */
  default VTask<Unit> close() {
    return VTask.succeed(Unit.INSTANCE);
  }

  /**
   * Closes a stream whose pull failed, together with the rest of the stream the failure carries, so
   * their finalisers run. A {@link #mapTask} task that fails keeps the rest of the stream after its
   * element on the failure, for {@link #recover} to resume from, and closing the stream that was
   * pulled may not reach it: a {@link #defer} stream keeps nothing it builds. Terminal operations
   * close both themselves; use this where code that pulls a stream step by step stops at a failed
   * pull.
   *
   * <p>If closing fails, the returned task fails with the first failure to close, with later ones
   * suppressed onto it. The pull's failure is left as it is.
   *
   * @param stream The stream whose pull failed. Must not be null.
   * @param failure What the pull failed with, as {@link VTask#run()} or {@link VTask#execute()}
   *     threw it. Must not be null.
   * @return A {@link VTask} that closes the stream, then the rest the failure carries. Never null.
   * @throws NullPointerException if {@code stream} or {@code failure} is null.
   */
  static VTask<Unit> closeAfterFailure(VStream<?> stream, Throwable failure) {
    Objects.requireNonNull(stream, "stream must not be null");
    Objects.requireNonNull(failure, "failure must not be null");
    return () -> {
      VStream<?> rest = Closing.markedRest(failure);
      return (rest == null ? stream.close() : Closing.closeAll(stream, rest)).execute();
    };
  }

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
    return () -> VTask.delay(() -> new Step.Emit<>(seed, iterate(f.apply(seed), f)));
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
        VTask.delay(() -> f.apply(initialState))
            .flatMap(task -> task)
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
    return () -> VTask.delay(() -> new Step.Emit<>(supplier.get(), generate(supplier)));
  }

  /**
   * Concatenates two streams. All elements from {@code first} are emitted before elements from
   * {@code second}.
   *
   * <p>Closing it closes the stream it is reading: {@code first} until it ends, then {@code
   * second}. A {@code second} it has not reached yet is left as it is.
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
    return DerivedStream.concatReading(first, second);
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
   * Defers building a stream until the {@code VTask} that {@link #pull()} returns runs. This
   * enables recursive stream definitions without stack overflow.
   *
   * <p>The supplier is called again on each run, not when {@code pull()} is called, so any state it
   * creates belongs to that run. The deferred stream keeps none of the streams it builds, so a
   * recursive definition holds nothing it has already read; closing it closes nothing, so close the
   * stream you are reading.
   *
   * @param supplier A supplier that produces the stream. Must not be null.
   * @param <A> The type of the elements.
   * @return A deferred {@code VStream}. Never null.
   * @throws NullPointerException if {@code supplier} is null.
   */
  static <A> VStream<A> defer(Supplier<VStream<A>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return DeferredStream.deferring(supplier);
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
    return new DerivedStream<A, B>(this) {
      @Override
      public VTask<Step<B>> pull() {
        return upstream
            .pull()
            .flatMap(
                step ->
                    switch (step) {
                      case Step.Emit<A> e -> {
                        VStream<B> tail = e.tail().mapTask(f);
                        VTask<B> task =
                            Closing.applyNonNullOrClose(
                                f, e.value(), e.tail(), "mapTask function returned null task");
                        // A failed task carries its tail, for recover to resume from, or for
                        // whatever stops at the failure to close
                        yield task.map(b -> (Step<B>) new Step.Emit<>(b, tail))
                            .recoverWith(
                                error -> {
                                  error.addSuppressed(new StreamTailMarker(tail));
                                  return VTask.fail(error);
                                });
                      }
                      case Step.Skip<A> s ->
                          VTask.succeed((Step<B>) new Step.Skip<>(s.tail().mapTask(f)));
                      case Step.Done<A> _ -> VTask.succeed(new Step.Done<>());
                    });
      }
    };
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
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream
            .pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          Closing.testOrClose(predicate, e.value(), e.tail())
                              ? new Step.Emit<>(e.value(), e.tail().filter(predicate))
                              : new Step.Skip<>(e.tail().filter(predicate));
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().filter(predicate));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
      }
    };
  }

  /**
   * Takes elements while the predicate holds, then completes, closing the rest of this stream.
   *
   * @param predicate The predicate to test elements against. Must not be null.
   * @return A new {@code VStream} that completes when the predicate fails. Never null.
   * @throws NullPointerException if {@code predicate} is null.
   */
  default VStream<A> takeWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream
            .pull()
            .flatMap(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          Closing.testOrClose(predicate, e.value(), e.tail())
                              ? VTask.<Step<A>>succeed(
                                  new Step.Emit<>(e.value(), e.tail().takeWhile(predicate)))
                              // The stream stops here, so close what is left unread
                              : e.tail().close().<Step<A>>map(_ -> new Step.Done<>());
                      case Step.Skip<A> s ->
                          VTask.<Step<A>>succeed(new Step.Skip<>(s.tail().takeWhile(predicate)));
                      case Step.Done<A> _ -> VTask.<Step<A>>succeed(new Step.Done<>());
                    });
      }
    };
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
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream
            .pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          Closing.testOrClose(predicate, e.value(), e.tail())
                              ? new Step.Skip<>(e.tail().dropWhile(predicate))
                              : new Step.Emit<>(e.value(), e.tail());
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().dropWhile(predicate));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
      }
    };
  }

  /**
   * Takes at most the first {@code n} elements from this stream, then closes the rest of it.
   *
   * @param n The maximum number of elements to take. Must be non-negative.
   * @return A new {@code VStream} limited to {@code n} elements. Never null.
   */
  default VStream<A> take(long n) {
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        if (n <= 0) {
          // Nothing more is wanted, so close what is left
          return upstream.close().map(unit -> new Step.Done<>());
        }
        return upstream
            .pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e -> new Step.Emit<>(e.value(), e.tail().take(n - 1));
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().take(n));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
      }
    };
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
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream
            .pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e -> new Step.Skip<>(e.tail().drop(n - 1));
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().drop(n));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
      }
    };
  }

  /**
   * Removes duplicate elements from this stream. Elements are compared using {@link
   * Object#equals(Object)}, and each is emitted where it first appears.
   *
   * <p>Each run of the stream records the position at which every element first appeared, so a tail
   * pulled again replays the same elements, provided the source gives the same element at each
   * position when pulled again, as a list does and {@link #generate} does not.
   *
   * <p><b>Warning:</b> For infinite streams, the recorded positions grow without bound. Use with
   * caution on large or infinite streams.
   *
   * @return A new {@code VStream} with duplicates removed. Never null.
   */
  default VStream<A> distinct() {
    return DeferredStream.deferring(
        () -> new DistinctStream<>(this, 0, new ConcurrentHashMap<>()), this);
  }

  // =====================================================================
  // Chunking operations
  // =====================================================================

  /**
   * Groups elements into lists of at most {@code size} elements. The last chunk may contain fewer
   * than {@code size} elements if the stream length is not an exact multiple.
   *
   * <p>Chunks are produced lazily (on demand), but elements within each chunk are collected
   * eagerly. This means a chunk is only produced when pulled, but once pulled, all {@code size}
   * elements for that chunk are consumed from the source stream immediately.
   *
   * <p>{@code chunk(1)} is equivalent to {@code map(List::of)}, wrapping each element in a
   * singleton list.
   *
   * <p><b>Warning:</b> For infinite streams, this produces an infinite stream of chunks. Use {@link
   * #take(long)} to limit before or after chunking.
   *
   * @param size The maximum number of elements per chunk. Must be positive.
   * @return A new {@code VStream} of element lists. Never null.
   * @throws IllegalArgumentException if size is not positive.
   */
  default VStream<List<A>> chunk(int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("chunk size must be positive, got: " + size);
    }
    VStream<A> self = this;
    return DeferredStream.deferring(
        () -> {
          List<A> batch = new ArrayList<>(size);
          VStream<A> current = self;

          try {
            while (batch.size() < size) {
              Step<A> step = current.pull().run();
              switch (step) {
                case Step.Emit<A> e -> {
                  batch.add(e.value());
                  current = e.tail();
                }
                case Step.Skip<A> s -> current = s.tail();
                case Step.Done<A> _ -> {
                  if (batch.isEmpty()) {
                    return VStream.empty();
                  }
                  return VStream.of(Collections.unmodifiableList(batch));
                }
              }
            }
          } catch (Throwable t) {
            // Close where the batch got to, which its start may not reach
            Closing.closeAfterFailure(current, t);
            throw t;
          }

          VStream<A> remaining = current;
          return DerivedStream.concatContinuing(
              VStream.of(Collections.unmodifiableList(batch)), remaining.chunk(size));
        },
        self);
  }

  /**
   * Groups consecutive elements while the predicate holds between adjacent pairs. A new chunk is
   * started whenever the predicate returns {@code false} for two adjacent elements.
   *
   * <p>This is useful for grouping sorted data where consecutive elements with the same key should
   * be batched together.
   *
   * <p>Each chunk contains at least one element. Single-element chunks are produced when the
   * predicate fails for every pair of adjacent elements.
   *
   * @param sameChunk A predicate testing whether two adjacent elements belong in the same chunk.
   *     Must not be null.
   * @return A new {@code VStream} of element lists. Never null.
   * @throws NullPointerException if sameChunk is null.
   */
  default VStream<List<A>> chunkWhile(BiPredicate<A, A> sameChunk) {
    Objects.requireNonNull(sameChunk, "sameChunk must not be null");
    VStream<A> self = this;
    return DeferredStream.deferring(
        () -> {
          // Pull the first element
          VStream<A> current = self;
          try {
            while (true) {
              Step<A> step = current.pull().run();
              switch (step) {
                case Step.Emit<A> e -> {
                  // Start building a chunk with this first element
                  List<A> chunk = new ArrayList<>();
                  chunk.add(e.value());
                  A prev = e.value();
                  VStream<A> tail = e.tail();
                  return buildChunkWhile(chunk, prev, tail, sameChunk);
                }
                case Step.Skip<A> s -> current = s.tail();
                case Step.Done<A> _ -> {
                  return VStream.empty();
                }
              }
            }
          } catch (Throwable t) {
            Closing.closeAfterFailure(current, t);
            throw t;
          }
        },
        self);
  }

  /**
   * Chunks the stream into batches of {@code size} elements, applies a batch function to each
   * chunk, and flattens the results back into a single stream.
   *
   * <p>This enables efficient batch operations such as bulk database inserts or batch API calls.
   * The batch function receives a list of up to {@code size} elements and returns a list of
   * transformed elements. The result lists are concatenated in order.
   *
   * <p>The batch function may return a list of a different size than its input, allowing expansion
   * or contraction of elements.
   *
   * @param <B> The type of elements in the output stream.
   * @param size The maximum number of elements per batch. Must be positive.
   * @param f The batch function to apply to each chunk. Must not be null.
   * @return A new {@code VStream} with transformed elements. Never null.
   * @throws IllegalArgumentException if size is not positive.
   * @throws NullPointerException if f is null.
   */
  default <B> VStream<B> mapChunked(int size, Function<List<A>, List<B>> f) {
    Objects.requireNonNull(f, "f must not be null");
    return chunk(size).flatMap(chunk -> VStream.fromList(f.apply(chunk)));
  }

  // =====================================================================
  // Combination operations
  // =====================================================================

  /**
   * Appends another stream after this one. All elements from this stream are emitted before
   * elements from {@code other}.
   *
   * <p>Closing it closes the stream it is reading: this one until it ends, then {@code other}. An
   * {@code other} it has not reached yet is left as it is.
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
   * <p>Closing it before the element is read leaves this stream as it is, as {@link
   * #concat(VStream)} leaves a stream it has not reached.
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
   * <p>When one stream ends, the rest of the other is closed if it has been read from. Closing a
   * zipped stream closes the streams it has read from, or both before anything has been read.
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
   * <p>Closing an interleaved stream closes the streams it has read from, or both before anything
   * has been read.
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
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream
            .pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e -> {
                        Closing.acceptOrClose(action, e.value(), e.tail());
                        yield new Step.Emit<>(e.value(), e.tail().peek(action));
                      }
                      case Step.Skip<A> s -> new Step.Skip<>(s.tail().peek(action));
                      case Step.Done<A> _ -> new Step.Done<>();
                    });
      }
    };
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
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream
            .pull()
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
    };
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
          try {
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
          } catch (Throwable t) {
            Closing.closeAfterFailure(current, t);
            throw t;
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
          try {
            while (true) {
              Step<A> step = current.pull().run();
              switch (step) {
                case Step.Emit<A> e -> {
                  current = e.tail();
                  acc = f.apply(acc, e.value());
                }
                case Step.Skip<A> s -> current = s.tail();
                case Step.Done<A> _ -> {
                  return acc;
                }
              }
            }
          } catch (Throwable t) {
            Closing.closeAfterFailure(current, t);
            throw t;
          }
        });
  }

  /**
   * Returns the first element of this stream, or empty if the stream is empty, closing the rest of
   * the stream.
   *
   * @return A {@link VTask} that produces the first element wrapped in an {@link Optional}. Never
   *     null.
   */
  default VTask<Optional<A>> headOption() {
    return VTask.of(() -> firstStop(this, Optional::ofNullable, Optional.empty()));
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
          try {
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
          } catch (Throwable t) {
            Closing.closeAfterFailure(current, t);
            throw t;
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
   * Checks whether any element matches the given predicate. Short-circuits on the first match,
   * closing the rest of the stream.
   *
   * @param predicate The predicate to test. Must not be null.
   * @return A {@link VTask} that produces {@code true} if any element matches. Never null.
   * @throws NullPointerException if {@code predicate} is null.
   */
  default VTask<Boolean> exists(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return VTask.of(
        () -> firstStop(this, a -> predicate.test(a) ? Boolean.TRUE : null, Boolean.FALSE));
  }

  /**
   * Checks whether all elements match the given predicate. Short-circuits on the first non-match,
   * closing the rest of the stream.
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
        () -> firstStop(this, a -> predicate.test(a) ? null : Boolean.FALSE, Boolean.TRUE));
  }

  /**
   * Finds the first element matching the given predicate. Short-circuits on the first match,
   * closing the rest of the stream.
   *
   * @param predicate The predicate to test. Must not be null.
   * @return A {@link VTask} that produces the first match wrapped in an {@link Optional}. Never
   *     null.
   * @throws NullPointerException if {@code predicate} is null.
   */
  default VTask<Optional<A>> find(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return VTask.of(
        () ->
            firstStop(
                this, a -> predicate.test(a) ? Optional.ofNullable(a) : null, Optional.empty()));
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
          try {
            while (true) {
              Step<A> step = current.pull().run();
              switch (step) {
                case Step.Emit<A> e -> {
                  current = e.tail();
                  action.accept(e.value());
                }
                case Step.Skip<A> s -> current = s.tail();
                case Step.Done<A> _ -> {
                  return Unit.INSTANCE;
                }
              }
            }
          } catch (Throwable t) {
            Closing.closeAfterFailure(current, t);
            throw t;
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
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream
            .pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          (Step<A>) new Step.Emit<>(e.value(), e.tail().recover(recoveryFunction));
                      case Step.Skip<A> s ->
                          (Step<A>) new Step.Skip<>(s.tail().recover(recoveryFunction));
                      case Step.Done<A> d -> d;
                    })
            .recover(
                error -> {
                  @SuppressWarnings("unchecked") // the rest of this stream, which mapTask carried
                  VStream<A> marked = (VStream<A>) Closing.markedRest(error);
                  VStream<A> tail =
                      marked == null ? VStream.empty() : marked.recover(recoveryFunction);
                  return new Step.Emit<>(Closing.applyOrClose(recoveryFunction, error, tail), tail);
                });
      }
    };
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
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream
            .pull()
            .recoverWith(
                error -> {
                  // The recovery replaces the rest of the stream, so close what a failure carries
                  Closing.closeMarkedRest(error);
                  return recoveryFunction.apply(error).pull();
                });
      }
    };
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
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream.pull().mapError(f);
      }
    };
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
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        return upstream
            .pull()
            .mapError(
                error -> {
                  action.accept(error);
                  return error;
                });
      }
    };
  }

  // =====================================================================
  // Resource management
  // =====================================================================

  /**
   * Acquires a resource, uses it to produce a stream, and guarantees release on completion, error,
   * or partial consumption.
   *
   * <p>The resource is acquired lazily, when the {@code VTask} that {@link #pull()} returns runs,
   * and afresh on each run (not when {@code bracket} is called). The release function is guaranteed
   * to run exactly once for each acquisition when:
   *
   * <ul>
   *   <li>The stream completes normally ({@link Step.Done})
   *   <li>An error occurs during pulling
   *   <li>The {@code use} function throws
   *   <li>The stream is partially consumed (via {@link #take}, {@link #headOption}, etc.), which
   *       closes the rest of it
   * </ul>
   *
   * <p><b>Limitation:</b> A consumer that pulls steps by hand and drops the stream without draining
   * it or calling {@link #close()} never runs the release. Every terminal operation, {@link #take}
   * and {@link #takeWhile} take care of this.
   *
   * <p>To read one resource after another, recurse outside the bracket, as in {@code
   * bracket(acquire, use, release).concat(VStream.defer(() -> next()))}. A recursion inside {@code
   * use} keeps every earlier resource open until the last one ends, and its head keeps every
   * earlier run reachable.
   *
   * <h2>Example: File I/O</h2>
   *
   * <pre>{@code
   * VStream<String> lines = VStream.bracket(
   *     VTask.of(() -> Files.newBufferedReader(path)),
   *     reader -> VStream.unfold(reader, r ->
   *         VTask.of(() -> {
   *             String line = r.readLine();
   *             return line == null
   *                 ? Optional.empty()
   *                 : Optional.of(new Seed<>(line, r));
   *         })),
   *     reader -> VTask.exec(() -> reader.close())
   * );
   * }</pre>
   *
   * @param acquire a VTask that acquires the resource; must not be null
   * @param use a function that takes the resource and produces a stream; must not be null
   * @param release a function that takes the resource and produces a cleanup VTask; must not be
   *     null
   * @param <R> the resource type
   * @param <A> the element type
   * @return a resource-safe VStream; never null
   * @throws NullPointerException if any argument is null
   */
  static <R, A> VStream<A> bracket(
      VTask<R> acquire, Function<R, VStream<A>> use, Function<R, VTask<Unit>> release) {
    Objects.requireNonNull(acquire, "acquire must not be null");
    Objects.requireNonNull(use, "use must not be null");
    Objects.requireNonNull(release, "release must not be null");

    // Each run acquires afresh; the head remembers its latest run, so closing it releases that run
    return new RememberingStream<>(
        acquire.flatMap(
            resource -> {
              VTask<Unit> releasing =
                  Objects.requireNonNull(release.apply(resource), "release must not return null");
              VStream<A> inner;
              try {
                inner = Objects.requireNonNull(use.apply(resource), "use must not return null");
              } catch (Throwable useFailure) {
                // use failed after the acquire, so release before failing
                return () -> {
                  Closing.runAfterFailure(releasing, useFailure);
                  throw useFailure;
                };
              }
              return VTask.succeed(inner.onFinalize(releasing));
            }));
  }

  /**
   * Ensures a finaliser runs when this stream completes, encounters an error, or is closed.
   *
   * <p>The finaliser VTask is executed when:
   *
   * <ul>
   *   <li>The stream completes normally ({@link Step.Done})
   *   <li>An error occurs during pulling
   *   <li>The stream is closed with {@link #close()}
   * </ul>
   *
   * <p>The finaliser runs once for each consumption that completes, fails or is closed, so
   * consuming the stream again runs it again. {@link #close()} on the returned stream runs it for
   * the latest consumption unless that consumption has run it already, or at once if the stream has
   * not been pulled. When the stream is consumed by several threads at once, close the tail a
   * consumer holds rather than the returned stream, since the latest consumption may be another
   * thread's. If the finaliser itself throws an exception and the stream also failed, the original
   * error is preserved and the finaliser error is added as a suppressed exception. Otherwise a
   * finaliser that throws fails the operation that completed or closed the stream.
   *
   * <h2>Example</h2>
   *
   * <pre>{@code
   * VStream<String> stream = VStream.of("a", "b", "c")
   *     .onFinalize(VTask.exec(() -> System.out.println("Stream completed")));
   * }</pre>
   *
   * @param finalizer the VTask to execute on stream completion, error or close; must not be null
   * @return a new VStream with the finaliser attached; never null
   * @throws NullPointerException if finaliser is null
   */
  default VStream<A> onFinalize(VTask<Unit> finalizer) {
    Objects.requireNonNull(finalizer, "finalizer must not be null");
    return new FinalizedStream<>(this, finalizer);
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
   * Reads a stream until {@code stop} gives a result for an element, then closes the rest of it, so
   * its finalisers run; at the end of the stream, gives {@code atEnd}. A failure closes the stream
   * it was reading, as every terminal operation does.
   */
  private static <A, R> R firstStop(
      VStream<A> stream, Function<? super A, ? extends @Nullable R> stop, R atEnd) {
    VStream<A> current = stream;
    R result;
    try {
      search:
      while (true) {
        Step<A> step = current.pull().run();
        switch (step) {
          case Step.Emit<A> e -> {
            current = e.tail();
            result = stop.apply(e.value());
            if (result != null) {
              break search;
            }
          }
          case Step.Skip<A> s -> current = s.tail();
          case Step.Done<A> _ -> {
            return atEnd;
          }
        }
      }
    } catch (Throwable t) {
      Closing.closeAfterFailure(current, t);
      throw t;
    }
    // Stopping before the end, so close what is left unread
    current.close().run();
    return result;
  }

  /**
   * Internal helper for chunkWhile: continues building a chunk and recursively creates subsequent
   * chunks.
   */
  private static <A> VStream<List<A>> buildChunkWhile(
      List<A> chunk, A prev, VStream<A> tail, BiPredicate<A, A> sameChunk) {
    return DeferredStream.deferring(
        () -> {
          VStream<A> current = tail;
          // A copy, so a second pull of this stream cannot change a chunk the first one emitted
          List<A> currentChunk = new ArrayList<>(chunk);
          A currentPrev = prev;

          try {
            while (true) {
              Step<A> step = current.pull().run();
              switch (step) {
                case Step.Emit<A> e -> {
                  // Past this element before testing it, so a predicate that throws closes the rest
                  current = e.tail();
                  if (sameChunk.test(currentPrev, e.value())) {
                    currentChunk.add(e.value());
                    currentPrev = e.value();
                  } else {
                    // Current chunk is complete, start a new one
                    List<A> emitChunk = Collections.unmodifiableList(currentChunk);
                    List<A> newChunk = new ArrayList<>();
                    newChunk.add(e.value());
                    return DerivedStream.concatContinuing(
                        VStream.of(emitChunk),
                        buildChunkWhile(newChunk, e.value(), e.tail(), sameChunk));
                  }
                }
                case Step.Skip<A> s -> current = s.tail();
                case Step.Done<A> _ -> {
                  return VStream.of(Collections.unmodifiableList(currentChunk));
                }
              }
            }
          } catch (Throwable t) {
            // Close where the chunk got to, which its start may not reach
            Closing.closeAfterFailure(current, t);
            throw t;
          }
        },
        tail);
  }

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
final class FlatMapStream<A, B> extends DerivedStream<A, B> {

  private final Function<? super A, ? extends VStream<B>> f;

  FlatMapStream(VStream<A> outer, Function<? super A, ? extends VStream<B>> f) {
    super(outer);
    this.f = f;
  }

  @Override
  public VTask<Step<B>> pull() {
    return upstream
        .pull()
        .flatMap(
            step ->
                switch (step) {
                  case Step.Emit<A> e -> {
                    VStream<B> inner =
                        Closing.applyNonNullOrClose(
                            f, e.value(), e.tail(), "flatMap function returned null stream");
                    yield VTask.succeed(
                        new Step.Skip<>(
                            DerivedStream.concatContinuing(inner, e.tail().flatMap(f))));
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
  // How far this zip has read; it reads left first, so ONE is left
  private final Closing.Reading reading;

  ZipWithStream(VStream<A> left, VStream<B> right, BiFunction<A, B, C> combiner) {
    this(left, right, combiner, Closing.Reading.NEITHER);
  }

  private ZipWithStream(
      VStream<A> left, VStream<B> right, BiFunction<A, B, C> combiner, Closing.Reading reading) {
    this.left = left;
    this.right = right;
    this.combiner = combiner;
    this.reading = reading;
  }

  @Override
  public VTask<Step<C>> pull() {
    return left.pull()
        .flatMap(
            leftStep ->
                switch (leftStep) {
                  case Step.Skip<A> s ->
                      VTask.succeed(
                          new Step.Skip<>(
                              new ZipWithStream<>(
                                  s.tail(), right, combiner, reading.withFirstRead())));
                  // Left has ended, so close right, if this zip has read it
                  case Step.Done<A> _ ->
                      reading == Closing.Reading.BOTH
                          ? right.close().<Step<C>>map(_ -> new Step.Done<>())
                          : VTask.<Step<C>>succeed(new Step.Done<>());
                  case Step.Emit<A> leftEmit -> pairWithRight(leftEmit);
                });
  }

  private VTask<Step<C>> pairWithRight(Step.Emit<A> leftEmit) {
    VTask<Step<C>> paired =
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
                                  new ZipWithStream<>(
                                      DerivedStream.concatContinuing(
                                          VStream.of(leftEmit.value()), leftEmit.tail()),
                                      s.tail(),
                                      combiner,
                                      Closing.Reading.BOTH)));
                      case Step.Done<B> _ ->
                          leftEmit.tail().close().<Step<C>>map(_ -> new Step.Done<>());
                      case Step.Emit<B> rightEmit -> {
                        C combined;
                        try {
                          combined = combiner.apply(leftEmit.value(), rightEmit.value());
                        } catch (Throwable t) {
                          // Only this step holds the rest of each stream
                          Closing.runAfterFailure(
                              Closing.closeAll(leftEmit.tail(), rightEmit.tail()), t);
                          throw t;
                        }
                        yield VTask.succeed(
                            new Step.Emit<>(
                                combined,
                                new ZipWithStream<>(
                                    leftEmit.tail(),
                                    rightEmit.tail(),
                                    combiner,
                                    Closing.Reading.BOTH)));
                      }
                    });
    // Only this pull holds the rest of left, and a right it starts reading here is one closing this
    // zip would not reach, so a failure closes them
    return reading == Closing.Reading.BOTH
        ? Closing.closingOnFailure(paired, leftEmit.tail())
        : Closing.closingOnFailure(paired, leftEmit.tail(), right);
  }

  /**
   * Closes the streams this zip has read, or both before it has read either, so a right stream it
   * never reached is left as it is.
   */
  @Override
  public VTask<Unit> close() {
    return reading.close(left, right);
  }
}

/**
 * Interleave implementation. Alternates elements from two streams, appending any remaining elements
 * from the longer stream.
 */
final class InterleaveStream<A> implements VStream<A> {

  private final VStream<A> first;
  private final VStream<A> second;
  // How far this interleave has read; a tail has read its second stream, so ONE is second
  private final Closing.Reading reading;

  InterleaveStream(VStream<A> first, VStream<A> second) {
    this(first, second, Closing.Reading.NEITHER);
  }

  private InterleaveStream(VStream<A> first, VStream<A> second, Closing.Reading reading) {
    this.first = first;
    this.second = second;
    this.reading = reading;
  }

  @Override
  public VTask<Step<A>> pull() {
    VTask<Step<A>> pulled =
        first
            .pull()
            .map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          new Step.Emit<>(
                              e.value(), new InterleaveStream<>(second, e.tail(), reading.next()));
                      case Step.Skip<A> s ->
                          new Step.Skip<>(new InterleaveStream<>(second, s.tail(), reading.next()));
                      case Step.Done<A> _ -> new Step.Skip<>(second);
                    });
    if (reading != Closing.Reading.ONE) {
      return pulled;
    }
    // This pull starts reading first, which closing this interleave would not reach, so a failure
    // closes it
    return Closing.closingOnFailure(pulled, first);
  }

  /**
   * Closes the streams this interleave has read, or both before it has read either, so a stream it
   * never reached is left as it is.
   */
  @Override
  public VTask<Unit> close() {
    return reading.close(second, first);
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
final class MappedStream<A, B> extends DerivedStream<A, B> {

  private final Function<? super A, ? extends B> mapper;

  MappedStream(VStream<A> source, Function<? super A, ? extends B> mapper) {
    super(source);
    this.mapper = mapper;
  }

  @Override
  public VTask<Step<B>> pull() {
    return upstream
        .pull()
        .map(
            step ->
                switch (step) {
                  case Step.Emit<A> e ->
                      new Step.Emit<>(
                          Closing.applyOrClose(mapper, e.value(), e.tail()), e.tail().map(mapper));
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
    return new MappedStream<>(upstream, composed);
  }
}

/**
 * The head of a stream with a finaliser, as {@link VStream#onFinalize} returns it.
 *
 * <p>A stream can be consumed more than once, so each pull of the head starts a consumption with
 * its own flag, and the finaliser runs once for each consumption: when it completes, fails or is
 * closed. {@link #close()} on the head ends the latest consumption, or one not yet started, so
 * consuming the stream again afterwards runs the finaliser again. Under concurrent consumptions the
 * latest one may belong to another consumer, so each consumer closes the tail it holds.
 *
 * @param <A> The element type.
 */
final class FinalizedStream<A> implements VStream<A> {

  private final VStream<A> source;
  private final VTask<Unit> finalizer;
  private final AtomicReference<AtomicBoolean> latest =
      new AtomicReference<>(new AtomicBoolean(false));

  FinalizedStream(VStream<A> source, VTask<Unit> finalizer) {
    this.source = source;
    this.finalizer = finalizer;
  }

  @Override
  public VTask<Step<A>> pull() {
    return VTask.delay(
            () -> {
              AtomicBoolean released = new AtomicBoolean(false);
              latest.set(released);
              return consumption(source, finalizer, released);
            })
        .flatMap(VStream::pull);
  }

  @Override
  public VTask<Unit> close() {
    return VTask.delay(latest::get).flatMap(released -> release(released, finalizer, source));
  }

  /**
   * Closes the source first, so the finalisers further upstream run before this one, as they do
   * when the stream completes. It then runs the finaliser if this consumption has not, even if
   * closing the source failed. A later failure is suppressed onto the first.
   */
  private static VTask<Unit> release(
      AtomicBoolean released, VTask<Unit> finalizer, VStream<?> source) {
    return () -> {
      Throwable failure = null;
      try {
        source.close().execute();
      } catch (Throwable t) {
        failure = t;
      }
      try {
        if (released.compareAndSet(false, true)) {
          // run() wraps a checked failure, as the finaliser at the end of the stream does
          finalizer.run();
        }
      } catch (Throwable t) {
        failure = Closing.keep(failure, t);
      }
      if (failure != null) {
        throw failure;
      }
      return Unit.INSTANCE;
    };
  }

  /**
   * Wraps one consumption of the stream, sharing its flag along the chain so that the finaliser
   * runs once for it.
   */
  private static <A> VStream<A> consumption(
      VStream<A> source, VTask<Unit> finalizer, AtomicBoolean released) {
    return new VStream<>() {
      @Override
      public VTask<Step<A>> pull() {
        // A source that fails as it is pulled still reaches the recovery that runs the finaliser
        VTask<Step<A>> pullTask = VTask.delay(source::pull).flatMap(task -> task);
        return pullTask
            .<Step<A>>map(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          new Step.Emit<>(e.value(), consumption(e.tail(), finalizer, released));
                      case Step.Skip<A> s ->
                          new Step.Skip<>(consumption(s.tail(), finalizer, released));
                      case Step.Done<A> _ -> {
                        if (released.compareAndSet(false, true)) {
                          finalizer.run();
                        }
                        yield new Step.Done<A>();
                      }
                    })
            .recoverWith(
                error ->
                    () -> {
                      // Release as closing does, source first, so the finalisers upstream run
                      // before this one; a failure to release is suppressed onto the error
                      Closing.runAfterFailure(release(released, finalizer, source), error);
                      throw error;
                    });
      }

      @Override
      public VTask<Unit> close() {
        return release(released, finalizer, source);
      }
    };
  }
}
