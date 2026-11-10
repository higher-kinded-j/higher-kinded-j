// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.higherkindedj.hkt.util.validation.Operation.APPEND;
import static org.higherkindedj.hkt.util.validation.Operation.FOLD;
import static org.higherkindedj.hkt.util.validation.Operation.FOLD_LEFT;
import static org.higherkindedj.hkt.util.validation.Operation.ITERATE;
import static org.higherkindedj.hkt.util.validation.Operation.OF;
import static org.higherkindedj.hkt.util.validation.Operation.PREPEND;
import static org.higherkindedj.hkt.util.validation.Operation.REPEAT;
import static org.higherkindedj.hkt.util.validation.Operation.SUCCEED;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.util.Cleanup;
import org.higherkindedj.hkt.util.validation.Validation;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * A lazy, pull-based streaming abstraction whose elements are produced by {@link VTask}s. {@code
 * VStream} fills the gap between {@link VTask} (a single deferred value) and Java's {@code Stream}
 * (single-use, with no effect type for its elements), enabling composable, effectful streaming
 * pipelines.
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
 *   <li><b>Threads:</b> A pull runs on the thread that runs the terminal operation's task. {@link
 *       #runAsync()} runs the stream on a virtual thread, and a stream from {@link VStreamPar}
 *       starts virtual threads as it is pulled, to process elements at the same time.
 *   <li><b>Failures:</b> A terminal operation fails with the exception a pulled task threw, a
 *       checked one included, which {@link VTask#runSafe()} keeps as it is and {@link VTask#run()}
 *       wraps in a {@link org.higherkindedj.hkt.vtask.VTaskExecutionException}. An element whose
 *       task fails, in {@link #mapTask} or in a parallel operation such as {@link
 *       VStreamPar#parEvalMap}, is a {@link Step.Fail}, which a {@link #recover} can step past;
 *       without one, the terminal operation fails with its error.
 *   <li><b>Interrupts:</b> A terminal operation checks the thread's interrupt status before it
 *       starts and between pulls, so a {@link VTask#timeout} or a cancelled {@link
 *       org.higherkindedj.hkt.vtask.Scope} stops it even when the pulls never block. Once the
 *       status is set, it fails with an {@link InterruptedException}, leaving the status set, and
 *       closes what it has read, so the finalisers there run. A run on a thread that is already
 *       interrupted fails before it reads, and closes nothing. An operator that reads ahead, such
 *       as {@link #chunk}, checks between its pulls too. An interrupt is a stop, not a failure, so
 *       {@link #recover}, {@link #recoverWith}, {@link #mapError} and {@link #onError} pass it
 *       through unchanged. To give a value when a run is stopped, recover outside what stops it, as
 *       in {@code toList().timeout(d).recover(e -> List.of())}: a {@code recover} inside the
 *       timeout gives a value the timeout discards.
 *   <li><b>Composability:</b> Rich set of combinators ({@link #map}, {@link #flatMap}, {@link
 *       #filter}, {@link #take}, etc.) that preserve laziness.
 *   <li><b>Reusability:</b> Unlike Java streams, a {@code VStream} can be pulled multiple times,
 *       producing a fresh traversal each time.
 * </ul>
 *
 * <p>A stream never emits {@code null}. A factory given an element refuses a null one at the call.
 * A null element from a list, iterator, Java stream or publisher, or a null returned by a function
 * the stream applies, fails the stream with a {@link NullPointerException} when it is pulled.
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
   * pull()} to request the next element. The returned {@link VTask} is lazy, and runs on the thread
   * that runs it. A stream from {@link VStreamPar} also starts virtual threads as it is pulled, to
   * run its elements' tasks or read its sources.
   *
   * <p>The returned {@link Step} is one of:
   *
   * <ul>
   *   <li>{@link Step.Emit} — an element is available, with a continuation stream for more.
   *   <li>{@link Step.Done} — the stream is exhausted, no more elements.
   *   <li>{@link Step.Skip} — no element produced this step (used by {@link #filter}), but the
   *       stream continues.
   *   <li>{@link Step.Fail} — this element's task failed, in {@link #mapTask} or in a parallel
   *       operation such as {@link VStreamPar#parEvalMap}, with a continuation stream for the
   *       elements after it.
   * </ul>
   *
   * <p>A pull that fails, rather than giving a step, ends the stream.
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
   * Closes a stream whose pull failed, so its finalisers run, as {@link #close()} does.
   *
   * <p>If closing fails, the returned task fails with the first failure to close, with later ones
   * suppressed onto it. The pull's failure is left as it is.
   *
   * @param stream The stream whose pull failed. Must not be null.
   * @param failure What the pull failed with. Must not be null.
   * @return A {@link VTask} that closes the stream. Never null.
   * @throws NullPointerException if {@code stream} or {@code failure} is null.
   * @deprecated since 0.6.0, scheduled for removal in 0.7.0. Use {@code stream.close()}. A {@link
   *     #mapTask} task that fails gives a {@link Step.Fail}, whose tail is closed, or read on from,
   *     as a {@link Step.Emit}'s is, so a pull that throws leaves nothing that closing the stream
   *     misses.
   */
  @Deprecated(since = "0.6.0", forRemoval = true)
  static VTask<Unit> closeAfterFailure(VStream<?> stream, Throwable failure) {
    Objects.requireNonNull(stream, "stream must not be null");
    Objects.requireNonNull(failure, "failure must not be null");
    return stream.close();
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
  sealed interface Step<A> permits Step.Emit, Step.Done, Step.Skip, Step.Fail {

    /**
     * A step that produces an element and a continuation stream.
     *
     * @param value The emitted element. Must not be null.
     * @param tail The continuation stream for subsequent elements. Must not be null.
     * @param <A> The type of the element.
     * @throws NullPointerException if {@code value} or {@code tail} is null.
     */
    record Emit<A>(A value, VStream<A> tail) implements Step<A> {
      public Emit {
        // A literal message, since a stream builds one of these for every element
        Objects.requireNonNull(value, "VStream.construction value cannot be null");
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

    /**
     * A step at an element that failed, with the stream after it. A {@link VStream#mapTask} task
     * that fails gives one, as does a failed task of {@link VStreamPar#parEvalMap}, {@link
     * VStreamPar#parEvalMapUnordered} or {@link VStreamPar#parEvalFlatMap}, so a {@link
     * VStream#recover} can go on from the next element, and each operator in between passes it on
     * with the tail it would give an {@link Emit}. Whoever holds the tail and stops closes it, as
     * for an {@link Emit}. A terminal operation closes it and fails with the error. Every pull of
     * this step gives the same error instance.
     *
     * @param error What the element failed with. Must not be null.
     * @param tail The stream after the failed element. Must not be null.
     * @param <A> The type of elements in the stream.
     * @throws NullPointerException if {@code error} or {@code tail} is null.
     */
    record Fail<A>(Throwable error, VStream<A> tail) implements Step<A> {
      public Fail {
        Objects.requireNonNull(error, "error must not be null");
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
   * @param value The element to emit. Must not be null.
   * @param next The next state to pass to the unfold function.
   * @param <A> The type of the emitted element.
   * @param <S> The type of the unfold state.
   * @throws NullPointerException if {@code value} is null.
   */
  record Seed<A, S>(A value, S next) {
    public Seed {
      Objects.requireNonNull(value, "VStream.construction value cannot be null");
    }
  }

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
   * @param value The element to emit. Must not be null.
   * @param <A> The type of the element.
   * @return A single-element {@code VStream}. Never null.
   * @throws NullPointerException if {@code value} is null.
   */
  static <A> VStream<A> of(A value) {
    Validation.coreType().requireValue(value, VStream.class, OF);
    return () -> VTask.succeed(new Step.Emit<>(value, empty()));
  }

  /**
   * Creates a stream from the given elements.
   *
   * @param values The elements to emit, in order. Must not be null, nor contain null.
   * @param <A> The type of the elements.
   * @return A {@code VStream} of the given elements. Never null.
   * @throws NullPointerException if {@code values} is null or contains null.
   */
  @SafeVarargs
  static <A> VStream<A> of(A... values) {
    Objects.requireNonNull(values, "values must not be null");
    for (A value : values) {
      Validation.coreType().requireValue(value, "element", VStream.class, OF);
    }
    return fromList(List.of(values));
  }

  /**
   * Creates a stream that lazily iterates through the elements of the given list.
   *
   * <p>The list is not copied; elements are accessed by index during traversal. Modifications to
   * the list after stream creation may affect the stream's output. A null element fails the stream
   * when it is pulled.
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
   * the resulting {@code VStream} will be empty on subsequent traversals. A null element fails the
   * stream when it is pulled.
   *
   * @param stream The Java stream to consume. Must not be null.
   * @param <A> The type of the elements.
   * @return A {@code VStream} wrapping the Java stream's iterator. Never null.
   * @throws NullPointerException if {@code stream} is null.
   */
  static <A> VStream<A> fromStream(Stream<A> stream) {
    Objects.requireNonNull(stream, "stream must not be null");
    return fromIteratorRefusing(stream.iterator(), "VStream.fromStream element cannot be null");
  }

  /**
   * Creates a stream that lazily consumes elements from the given iterator.
   *
   * <p><b>Warning:</b> The iterator is consumed incrementally. Once exhausted, the resulting {@code
   * VStream} will be empty on subsequent traversals. If you need a reusable stream, prefer {@link
   * #fromList(List)}. A null element fails the stream when it is pulled.
   *
   * @param iterator The iterator to consume. Must not be null.
   * @param <A> The type of the elements.
   * @return A {@code VStream} wrapping the iterator. Never null.
   * @throws NullPointerException if {@code iterator} is null.
   */
  static <A> VStream<A> fromIterator(Iterator<A> iterator) {
    Objects.requireNonNull(iterator, "iterator must not be null");
    return fromIteratorRefusing(iterator, "VStream.fromIterator element cannot be null");
  }

  /**
   * Creates a single-element stream. Alias for {@link #of(Object)}, consistent with {@link
   * VTask#succeed(Object)}.
   *
   * @param value The element to emit. Must not be null.
   * @param <A> The type of the element.
   * @return A single-element {@code VStream}. Never null.
   * @throws NullPointerException if {@code value} is null.
   */
  static <A> VStream<A> succeed(A value) {
    Validation.coreType().requireValue(value, VStream.class, SUCCEED);
    return of(value);
  }

  /**
   * Creates a stream whose first pull fails with the given error. A pull that fails is not a failed
   * element, so a {@link #recover} after it ends the stream.
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
   * @param seed The initial value. Must not be null.
   * @param f The function to produce the next value from the current one. Must not be null. It must
   *     not return null.
   * @param <A> The type of the elements.
   * @return An infinite {@code VStream}. Never null.
   * @throws NullPointerException if {@code seed} or {@code f} is null.
   */
  static <A> VStream<A> iterate(A seed, UnaryOperator<A> f) {
    Validation.coreType().requireValue(seed, "seed", VStream.class, ITERATE);
    Objects.requireNonNull(f, "f must not be null");
    return iterateFrom(seed, f);
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
   *     null. A {@link Seed} refuses a null value.
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
   * @param supplier The supplier to call for each element. Must not be null. It must not return
   *     null.
   * @param <A> The type of the elements.
   * @return An infinite {@code VStream}. Never null.
   * @throws NullPointerException if {@code supplier} is null.
   */
  static <A> VStream<A> generate(Supplier<A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return () ->
        VTask.delay(
            () ->
                new Step.Emit<>(
                    Objects.requireNonNull(supplier.get(), "supplier must not return null"),
                    generate(supplier)));
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
   * @param value The value to repeat. Must not be null.
   * @param <A> The type of the element.
   * @return An infinite repeating {@code VStream}. Never null.
   * @throws NullPointerException if {@code value} is null.
   */
  static <A> VStream<A> repeat(A value) {
    Validation.coreType().requireValue(value, VStream.class, REPEAT);
    return repeating(value);
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
    return DeferredStream.deferring(VTask.delay(supplier));
  }

  // =====================================================================
  // Transformation combinators
  // =====================================================================

  /**
   * Transforms each element of this stream using the given function. The transformation is lazy:
   * the function is applied only when elements are pulled.
   *
   * @param f The mapping function. Must not be null. It must not return null.
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
   * <p>Closing it closes this stream and the sub-stream it is reading. A sub-stream it has not yet
   * pulled is left as it is, as {@link #concat(VStream)} leaves a stream it has not reached. So
   * open a resource a sub-stream reads with {@link #bracket}, which acquires it only when read.
   *
   * @param f A function producing a sub-stream for each element. Must not be null.
   * @param <B> The type of elements in the resulting stream.
   * @return A new flattened {@code VStream}. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> VStream<B> flatMap(Function<? super A, ? extends VStream<B>> f) {
    Objects.requireNonNull(f, "f must not be null");
    VStream<A> outer = this;
    return new FlatMapStream<>(outer, f, false);
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
   * <p>An element whose task fails is a {@link Step.Fail} with the task's error, and the stream
   * goes on after it, so a {@link #recover} further down gives a value in its place. Without one, a
   * terminal operation fails with the error. A task that fails with an {@link
   * InterruptedException}, bare or as {@link VTask#run()} wraps it, ends the stream instead,
   * closing the rest of it.
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
        return pullFlatMap(
            step ->
                switch (step) {
                  case Step.Emit<A> e -> {
                    VStream<B> tail = e.tail().mapTask(f);
                    VTask<B> task =
                        Closing.applyNonNullOrClose(
                            f, e.value(), e.tail(), "mapTask function returned null task");
                    // A failed task fails its element, and the stream goes on after it
                    yield task.map(b -> (Step<B>) new Step.Emit<>(b, tail))
                        .recoverWith(
                            error -> {
                              if (Executing.isInterrupt(error)) {
                                // An interrupt stops the stream, so the rest, which only this
                                // step holds, is closed
                                Closing.closeAfterFailure(tail, error);
                                return VTask.fail(error);
                              }
                              return VTask.succeed(new Step.Fail<>(error, tail));
                            });
                  }
                  case Step.Skip<A> s ->
                      VTask.succeed((Step<B>) new Step.Skip<>(s.tail().mapTask(f)));
                  case Step.Fail<A> failed ->
                      VTask.succeed(
                          (Step<B>) new Step.Fail<>(failed.error(), failed.tail().mapTask(f)));
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
        return pullMap(
            step ->
                switch (step) {
                  case Step.Emit<A> e ->
                      Closing.testOrClose(predicate, e.value(), e.tail())
                          ? new Step.Emit<>(e.value(), e.tail().filter(predicate))
                          : new Step.Skip<>(e.tail().filter(predicate));
                  case Step.Skip<A> s -> new Step.Skip<>(s.tail().filter(predicate));
                  case Step.Fail<A> failed ->
                      new Step.Fail<>(failed.error(), failed.tail().filter(predicate));
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
        return pullFlatMap(
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
                  case Step.Fail<A> failed ->
                      VTask.<Step<A>>succeed(
                          new Step.Fail<>(failed.error(), failed.tail().takeWhile(predicate)));
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
        return pullMap(
            step ->
                switch (step) {
                  case Step.Emit<A> e ->
                      Closing.testOrClose(predicate, e.value(), e.tail())
                          ? new Step.Skip<>(e.tail().dropWhile(predicate))
                          : new Step.Emit<>(e.value(), e.tail());
                  case Step.Skip<A> s -> new Step.Skip<>(s.tail().dropWhile(predicate));
                  // A failed element holds its place, and is dropped with the prefix
                  case Step.Fail<A> failed -> new Step.Skip<>(failed.tail().dropWhile(predicate));
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
        return pullMap(
            step ->
                switch (step) {
                  case Step.Emit<A> e -> new Step.Emit<>(e.value(), e.tail().take(n - 1));
                  case Step.Skip<A> s -> new Step.Skip<>(s.tail().take(n));
                  // The failed element counts as one taken
                  case Step.Fail<A> failed ->
                      new Step.Fail<>(failed.error(), failed.tail().take(n - 1));
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
        return pullMap(
            step ->
                switch (step) {
                  case Step.Emit<A> e -> new Step.Skip<>(e.tail().drop(n - 1));
                  case Step.Skip<A> s -> new Step.Skip<>(s.tail().drop(n));
                  // A failed element holds its place, and is dropped as one of the n
                  case Step.Fail<A> failed -> new Step.Skip<>(failed.tail().drop(n - 1));
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
   * Initial capacity of a chunk batch. A larger size still collects up to that many elements; the
   * list grows as they arrive, so a size such as {@code Integer.MAX_VALUE} does not allocate that
   * many slots before the first element is read.
   */
  private static int chunkBatchInitialCapacity(int size) {
    return Math.min(size, 1024);
  }

  /**
   * Groups elements into non-empty lists of at most {@code size} elements. The last chunk may
   * contain fewer than {@code size} elements if the stream length is not an exact multiple.
   *
   * <p>A chunk is only emitted once it holds an element, so each one is a {@link NonEmptyList}: its
   * {@link NonEmptyList#head() head}, {@link NonEmptyList#reduce reduce}, {@link NonEmptyList#min
   * min} and {@link NonEmptyList#max max} always give a value. An empty stream gives no chunks.
   *
   * <p>Chunks are produced lazily (on demand), but elements within each chunk are collected
   * eagerly. This means a chunk is only produced when pulled, but once pulled, all {@code size}
   * elements for that chunk are consumed from the source stream immediately.
   *
   * <p>A size at least as large as the stream yields one chunk of every element. The batch list
   * grows as elements arrive, so a size such as {@link Integer#MAX_VALUE} does not reserve a slot
   * for every place in it before the first element is read.
   *
   * <p>{@code chunk(1)} is equivalent to {@code map(NonEmptyList::single)}, wrapping each element
   * in a single-element list.
   *
   * <p>If an element fails part-way through a chunk, the elements read before it are handed on
   * first, as a shorter chunk, then the failed element, and the chunks after it start afresh, so a
   * {@link #recover} after a failed {@link #mapTask} task goes on from the next element. If reading
   * fails, the shorter chunk comes first, then the failure, which ends the stream.
   *
   * <p><b>Warning:</b> For infinite streams, this produces an infinite stream of chunks. Use {@link
   * #take(long)} to limit before or after chunking.
   *
   * @param size The maximum number of elements per chunk. Must be positive.
   * @return A new {@code VStream} of non-empty element lists. Never null.
   * @throws IllegalArgumentException if size is not positive.
   */
  default VStream<NonEmptyList<A>> chunk(int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("chunk size must be positive, got: " + size);
    }
    VStream<A> self = this;
    return DeferredStream.deferring(
        () -> {
          List<A> batch = new ArrayList<>(chunkBatchInitialCapacity(size));
          VStream<A> current = self;

          try {
            Step<A> step = Executing.executed(current.pull());
            while (true) {
              switch (step) {
                case Step.Emit<A> e -> {
                  batch.add(e.value());
                  current = e.tail();
                  if (batch.size() == size) {
                    return DerivedStream.concatContinuing(
                        VStream.of(chunkOf(batch)), e.tail().chunk(size));
                  }
                }
                case Step.Skip<A> s -> current = s.tail();
                case Step.Fail<A> failed -> {
                  // What the batch read comes first, as a shorter chunk, then the failed element,
                  // after which chunking starts afresh
                  return DerivedStream.handingOnThenFailedElement(
                      chunksSoFar(batch), failed, rest -> rest.chunk(size));
                }
                case Step.Done<A> _ -> {
                  if (batch.isEmpty()) {
                    return VStream.empty();
                  }
                  return VStream.of(chunkOf(batch));
                }
              }
              // Between pulls, so a stop here closes only what this run has reached
              step = Executing.pulledUnlessInterrupted(current);
            }
          } catch (Throwable t) {
            return DerivedStream.handingOnThenEnding(chunksSoFar(batch), t, current);
          }
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
   * <p>Each chunk contains at least one element, so each one is a {@link NonEmptyList}.
   * Single-element chunks are produced when the predicate fails for every pair of adjacent
   * elements. An empty stream gives no chunks.
   *
   * <p>If an element fails, or reading fails, part-way through a chunk, the chunk so far is handed
   * on first, as {@link #chunk(int)} describes.
   *
   * @param sameChunk A predicate testing whether two adjacent elements belong in the same chunk.
   *     Must not be null.
   * @return A new {@code VStream} of non-empty element lists. Never null.
   * @throws NullPointerException if sameChunk is null.
   */
  default VStream<NonEmptyList<A>> chunkWhile(BiPredicate<? super A, ? super A> sameChunk) {
    Objects.requireNonNull(sameChunk, "sameChunk must not be null");
    VStream<A> self = this;
    return DeferredStream.deferring(
        () -> {
          // Pull the first element
          VStream<A> current = self;
          try {
            Step<A> step = Executing.executed(current.pull());
            while (true) {
              switch (step) {
                case Step.Emit<A> e -> {
                  // Start building a chunk with this first element
                  return buildChunkWhile(e.value(), e.tail(), sameChunk);
                }
                case Step.Skip<A> s -> current = s.tail();
                // No chunk has started, so the failed element comes first
                case Step.Fail<A> failed -> {
                  return DerivedStream.handingOnThenFailedElement(
                      List.of(), failed, rest -> rest.chunkWhile(sameChunk));
                }
                case Step.Done<A> _ -> {
                  return VStream.empty();
                }
              }
              // Between pulls, so a stop here closes only what this run has reached
              step = Executing.pulledUnlessInterrupted(current);
            }
          } catch (Throwable t) {
            return DerivedStream.handingOnThenEnding(List.of(), t, current);
          }
        },
        self);
  }

  /**
   * Chunks the stream into batches of {@code size} elements, applies a batch function to each
   * chunk, and flattens the results back into a single stream.
   *
   * <p>This enables efficient batch operations such as bulk database inserts or batch API calls.
   * The batch function receives each chunk {@link #chunk(int)} emits, a {@link NonEmptyList} of up
   * to {@code size} elements, and returns a list of transformed elements. The result lists are
   * concatenated in order. Pass {@link NonEmptyList#toJavaList() batch.toJavaList()} to a batch API
   * that takes a {@link List}.
   *
   * <p>The batch function may return a list of a different size than its input, allowing expansion
   * or contraction of elements. If reading fails part-way through a batch, the batch function
   * receives the elements read before the failure first, as {@link #chunk(int)} describes.
   *
   * @param <B> The type of elements in the output stream.
   * @param size The maximum number of elements per batch. Must be positive.
   * @param f The batch function to apply to each chunk. Must not be null. It must not return null,
   *     nor a list that contains null.
   * @return A new {@code VStream} with transformed elements. Never null.
   * @throws IllegalArgumentException if size is not positive.
   * @throws NullPointerException if f is null.
   */
  default <B> VStream<B> mapChunked(
      int size, Function<? super NonEmptyList<A>, ? extends List<B>> f) {
    Objects.requireNonNull(f, "f must not be null");
    return chunk(size)
        .flatMap(
            chunk ->
                VStream.fromList(Objects.requireNonNull(f.apply(chunk), "f must not return null")));
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
   * <p>Closing it leaves this stream as it is until this stream is read, whether the element has
   * been read or not, as {@link #concat(VStream)} leaves a stream it has not reached. So a {@code
   * headOption()}, which reads only the element, closes nothing of this stream. Open a resource it
   * reads with {@link #bracket}, which acquires it only when the stream is read.
   *
   * @param value The element to prepend. Must not be null.
   * @return A new {@code VStream} with the element at the front. Never null.
   * @throws NullPointerException if {@code value} is null.
   */
  default VStream<A> prepend(A value) {
    Validation.coreType().requireValue(value, VStream.class, PREPEND);
    return () -> VTask.succeed(new Step.Emit<>(value, DerivedStream.unreached(this)));
  }

  /**
   * Adds an element at the end of this stream.
   *
   * @param value The element to append. Must not be null.
   * @return A new {@code VStream} with the element at the end. Never null.
   * @throws NullPointerException if {@code value} is null.
   */
  default VStream<A> append(A value) {
    Validation.coreType().requireValue(value, VStream.class, APPEND);
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
   * @param combiner The function to combine paired elements. Must not be null. It must not return
   *     null.
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
        return pullMap(
            step ->
                switch (step) {
                  case Step.Emit<A> e -> {
                    Closing.acceptOrClose(action, e.value(), e.tail());
                    yield new Step.Emit<>(e.value(), e.tail().peek(action));
                  }
                  case Step.Skip<A> s -> new Step.Skip<>(s.tail().peek(action));
                  case Step.Fail<A> failed ->
                      new Step.Fail<>(failed.error(), failed.tail().peek(action));
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
        return pullMap(
            step ->
                switch (step) {
                  case Step.Emit<A> e -> new Step.Emit<>(e.value(), e.tail().onComplete(action));
                  case Step.Skip<A> s -> new Step.Skip<>(s.tail().onComplete(action));
                  case Step.Fail<A> failed ->
                      new Step.Fail<>(failed.error(), failed.tail().onComplete(action));
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
   * <p><b>Warning:</b> For infinite streams, this does not end until its thread is interrupted, by
   * a timeout for example. Use {@link #take(long)} to limit the stream first.
   *
   * @return A {@link VTask} that produces the list of all elements. Never null.
   */
  default VTask<List<A>> toList() {
    return () -> {
      // Before it starts, so a run on an interrupted thread fails without reading or closing
      Executing.stopIfInterrupted();
      List<A> result = new ArrayList<>();
      VStream<A> current = this;
      try {
        Step<A> step = Executing.executed(current.pull());
        while (true) {
          switch (step) {
            case Step.Emit<A> e -> {
              result.add(e.value());
              current = e.tail();
            }
            case Step.Skip<A> s -> current = s.tail();
            case Step.Fail<A> failed -> {
              // The catch closes the stream after the failed element
              current = failed.tail();
              throw failed.error();
            }
            case Step.Done<A> _ -> {
              return Collections.unmodifiableList(result);
            }
          }
          // Between pulls, so a stop here closes only what this run has reached
          step = Executing.pulledUnlessInterrupted(current);
        }
      } catch (Throwable t) {
        Closing.closeAfterFailure(current, t);
        throw t;
      }
    };
  }

  /**
   * Left-folds all elements with the given identity and operator.
   *
   * @param identity The initial accumulator value. Must not be null.
   * @param op The binary operator to combine accumulator with each element. Must not be null. It
   *     must not return null.
   * @return A {@link VTask} that produces the fold result. Never null.
   * @throws NullPointerException if {@code identity} or {@code op} is null.
   */
  default VTask<A> fold(A identity, BinaryOperator<A> op) {
    Validation.coreType().requireValue(identity, "identity", VStream.class, FOLD);
    Objects.requireNonNull(op, "op must not be null");
    return folding(identity, op, "op must not return null");
  }

  /**
   * Left-folds all elements with the given initial value and accumulator function.
   *
   * @param initial The initial accumulator value. Must not be null.
   * @param f The accumulator function. Must not be null. It must not return null.
   * @param <B> The type of the accumulator.
   * @return A {@link VTask} that produces the fold result. Never null.
   * @throws NullPointerException if {@code initial} or {@code f} is null.
   */
  default <B> VTask<B> foldLeft(B initial, BiFunction<B, A, B> f) {
    Validation.coreType().requireValue(initial, "initial", VStream.class, FOLD_LEFT);
    Objects.requireNonNull(f, "f must not be null");
    return folding(initial, f, "f must not return null");
  }

  /**
   * Left-folds all elements, failing with {@code nullResult} if the accumulator function returns
   * null.
   */
  private <B> VTask<B> folding(B initial, BiFunction<B, A, B> f, String nullResult) {
    return () -> {
      // Before it starts, so a run on an interrupted thread fails without reading or closing
      Executing.stopIfInterrupted();
      B acc = initial;
      VStream<A> current = this;
      try {
        Step<A> step = Executing.executed(current.pull());
        while (true) {
          switch (step) {
            case Step.Emit<A> e -> {
              current = e.tail();
              acc = Objects.requireNonNull(f.apply(acc, e.value()), nullResult);
            }
            case Step.Skip<A> s -> current = s.tail();
            case Step.Fail<A> failed -> {
              current = failed.tail();
              throw failed.error();
            }
            case Step.Done<A> _ -> {
              return acc;
            }
          }
          // Between pulls, so a stop here closes only what this run has reached
          step = Executing.pulledUnlessInterrupted(current);
        }
      } catch (Throwable t) {
        Closing.closeAfterFailure(current, t);
        throw t;
      }
    };
  }

  /**
   * Returns the first element of this stream, or empty if the stream is empty, closing the rest of
   * the stream.
   *
   * @return A {@link VTask} that produces the first element wrapped in an {@link Optional}. Never
   *     null.
   */
  default VTask<Optional<A>> headOption() {
    return () -> firstStop(this, Optional::of, Optional.empty());
  }

  /**
   * Returns the last element of this stream, or empty if the stream is empty.
   *
   * <p><b>Warning:</b> For infinite streams, this does not end until its thread is interrupted, by
   * a timeout for example.
   *
   * @return A {@link VTask} that produces the last element wrapped in an {@link Optional}. Never
   *     null.
   */
  default VTask<Optional<A>> lastOption() {
    return () -> {
      // Before it starts, so a run on an interrupted thread fails without reading or closing
      Executing.stopIfInterrupted();
      Optional<A> last = Optional.empty();
      VStream<A> current = this;
      try {
        Step<A> step = Executing.executed(current.pull());
        while (true) {
          switch (step) {
            case Step.Emit<A> e -> {
              last = Optional.of(e.value());
              current = e.tail();
            }
            case Step.Skip<A> s -> current = s.tail();
            case Step.Fail<A> failed -> {
              current = failed.tail();
              throw failed.error();
            }
            case Step.Done<A> _ -> {
              return last;
            }
          }
          // Between pulls, so a stop here closes only what this run has reached
          step = Executing.pulledUnlessInterrupted(current);
        }
      } catch (Throwable t) {
        Closing.closeAfterFailure(current, t);
        throw t;
      }
    };
  }

  /**
   * Counts the number of elements in this stream.
   *
   * <p><b>Warning:</b> For infinite streams, this does not end until its thread is interrupted, by
   * a timeout for example.
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
    return () -> firstStop(this, a -> predicate.test(a) ? Boolean.TRUE : null, Boolean.FALSE);
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
    return () -> firstStop(this, a -> predicate.test(a) ? null : Boolean.FALSE, Boolean.TRUE);
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
    return () -> firstStop(this, a -> predicate.test(a) ? Optional.of(a) : null, Optional.empty());
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
    return () -> {
      // Before it starts, so a run on an interrupted thread fails without reading or closing
      Executing.stopIfInterrupted();
      VStream<A> current = this;
      try {
        Step<A> step = Executing.executed(current.pull());
        while (true) {
          switch (step) {
            case Step.Emit<A> e -> {
              current = e.tail();
              action.accept(e.value());
            }
            case Step.Skip<A> s -> current = s.tail();
            case Step.Fail<A> failed -> {
              current = failed.tail();
              throw failed.error();
            }
            case Step.Done<A> _ -> {
              return Unit.INSTANCE;
            }
          }
          // Between pulls, so a stop here closes only what this run has reached
          step = Executing.pulledUnlessInterrupted(current);
        }
      } catch (Throwable t) {
        Closing.closeAfterFailure(current, t);
        throw t;
      }
    };
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
   * Recovers from a failure by replacing the error with a value. After a failed task of {@link
   * #mapTask} or of a parallel operation such as {@link VStreamPar#parEvalMap}, a {@link
   * Step.Fail}, the recovery value takes the failed element's place, and the stream goes on from
   * the next element. After any other failure, such as a {@code map} function that throws or a
   * failed source, the stream ends after the recovery value, and what this stream was reading is
   * closed.
   *
   * <p>A pull that fails with an interrupt, an {@link InterruptedException} bare or as {@link
   * VTask#run()} wraps it, is not recovered: an interrupt is a stop, not a failure. It passes
   * through unchanged, so the terminal operation fails with it and closes the stream, and {@code
   * recoveryFunction} does not see it.
   *
   * <p>The operators between the failed task and this {@code recover} apply to the elements after
   * the failed one as they did to the ones before it, and the failed element holds its place, as an
   * element without a value: {@code take(3)} and {@code drop(3)} count it as one of their three,
   * {@code drop} and {@code dropWhile} drop it within the prefix they drop, {@code zipWith} pairs
   * it with the other stream's element, and {@code throttle} and {@code metered} pace it. {@code
   * filter} and {@code takeWhile}, which have no value to test, pass it on. An operator that reads
   * ahead, such as {@code chunk} or {@code VStreamPar.parEvalMap}, hands on what it read before the
   * failed element first, then reads afresh after it. {@code VStreamPar.merge} of several streams
   * stops every source at a failure, so a {@code recover} after it ends the stream. A finaliser
   * between them runs when the stream after the failed element ends or is closed, not at the
   * failure.
   *
   * @param recoveryFunction A function that produces a recovery value from the error. Must not be
   *     null. It must not return null.
   * @return A new {@code VStream} with error recovery. Never null.
   * @throws NullPointerException if {@code recoveryFunction} is null.
   */
  default VStream<A> recover(Function<? super Throwable, ? extends A> recoveryFunction) {
    Objects.requireNonNull(recoveryFunction, "recoveryFunction must not be null");
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        VTask<Step<A>> pulled = upstream.pull();
        return () -> {
          Step<A> step;
          try {
            step = pulled.execute();
          } catch (Throwable error) {
            if (Executing.isInterrupt(error)) {
              // A stop, not a failure, so it goes on unchanged, for the holder to close
              throw error;
            }
            // The stream itself failed, so it ends here: close what this was reading
            Closing.closeAfterFailure(upstream, error);
            return new Step.Emit<>(
                Objects.requireNonNull(
                    recoveryFunction.apply(error), "recoveryFunction must not return null"),
                VStream.empty());
          }
          return switch (step) {
            case Step.Emit<A> e -> new Step.Emit<>(e.value(), e.tail().recover(recoveryFunction));
            case Step.Skip<A> s -> new Step.Skip<>(s.tail().recover(recoveryFunction));
            // An element failed: its recovery value takes its place, and the stream goes on
            case Step.Fail<A> failed ->
                new Step.Emit<>(
                    Closing.applyNonNullOrClose(
                        recoveryFunction,
                        failed.error(),
                        failed.tail(),
                        "recoveryFunction must not return null"),
                    failed.tail().recover(recoveryFunction));
            case Step.Done<A> d -> d;
          };
        };
      }
    };
  }

  /**
   * Recovers from a failure by substituting a recovery stream. If an element fails, or a pull
   * fails, the recovery stream replaces the rest of this stream, which is closed. A failure of the
   * recovery stream itself is not recovered, nor is an interrupt, which passes through unchanged,
   * as {@link #recover} describes.
   *
   * @param recoveryFunction A function that produces a recovery stream from the error. Must not be
   *     null. It must not return null.
   * @return A new {@code VStream} with error recovery. Never null.
   * @throws NullPointerException if {@code recoveryFunction} is null.
   */
  default VStream<A> recoverWith(
      Function<? super Throwable, ? extends VStream<A>> recoveryFunction) {
    Objects.requireNonNull(recoveryFunction, "recoveryFunction must not be null");
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        VTask<Step<A>> pulled = upstream.pull();
        return () -> {
          Step<A> step;
          try {
            step = pulled.execute();
          } catch (Throwable error) {
            if (Executing.isInterrupt(error)) {
              // A stop, not a failure, so it goes on unchanged, for the holder to close
              throw error;
            }
            // The recovery replaces the rest of the stream, so close what this was reading
            Closing.closeAfterFailure(upstream, error);
            return recovering(error);
          }
          return switch (step) {
            case Step.Emit<A> e ->
                new Step.Emit<>(e.value(), e.tail().recoverWith(recoveryFunction));
            case Step.Skip<A> s -> new Step.Skip<>(s.tail().recoverWith(recoveryFunction));
            case Step.Fail<A> failed -> {
              // The recovery replaces the rest of the stream after the failed element
              Closing.closeAfterFailure(failed.tail(), failed.error());
              yield recovering(failed.error());
            }
            case Step.Done<A> d -> d;
          };
        };
      }

      private Step<A> recovering(Throwable error) throws Throwable {
        return Objects.requireNonNull(
                recoveryFunction.apply(error), "recoveryFunction must not return null")
            .pull()
            .execute();
      }
    };
  }

  /**
   * Transforms the error of a failed element, or of a failed pull, using the given function. A
   * failed element keeps its place with the new error, so a {@link #recover} after this goes on
   * from the next element, with this mapping still applied. If {@code f} throws, what it threw
   * takes the error's place in the same way. An interrupt passes through unmapped, as {@link
   * #recover} describes.
   *
   * @param f A function that transforms the error. Must not be null. It must not return null.
   * @return A new {@code VStream} with transformed errors. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default VStream<A> mapError(Function<? super Throwable, ? extends Throwable> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new DerivedStream<A, A>(this) {
      @Override
      public VTask<Step<A>> pull() {
        VTask<Step<A>> pulled = upstream.pull();
        return () -> {
          Step<A> step;
          try {
            step = pulled.execute();
          } catch (Throwable error) {
            // An interrupt is a stop, not a failure, so it goes on unmapped
            throw Executing.isInterrupt(error) ? error : mapped(error);
          }
          return switch (step) {
            case Step.Emit<A> e -> new Step.Emit<>(e.value(), e.tail().mapError(f));
            case Step.Skip<A> s -> new Step.Skip<>(s.tail().mapError(f));
            case Step.Fail<A> failed ->
                new Step.Fail<>(mapped(failed.error()), failed.tail().mapError(f));
            case Step.Done<A> d -> d;
          };
        };
      }

      /** What {@code f} gives for an error, or what it throws. */
      private Throwable mapped(Throwable error) {
        try {
          return Objects.requireNonNull(f.apply(error), "f must not return null");
        } catch (Throwable thrown) {
          return thrown;
        }
      }
    };
  }

  /**
   * Observes the error of a failed element, or of a failed pull, without modifying it. Useful for
   * logging. A {@link #recover} after this goes on with the observer still applied. If the action
   * throws, what it threw takes the error's place, as {@link #mapError} describes. An interrupt
   * passes through unchanged, without the action seeing it, as {@link #recover} describes.
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
        VTask<Step<A>> pulled = upstream.pull();
        return () -> {
          Step<A> step;
          try {
            step = pulled.execute();
          } catch (Throwable error) {
            // An interrupt is a stop, not a failure, so the action does not see it
            throw Executing.isInterrupt(error) ? error : observed(error);
          }
          return switch (step) {
            case Step.Emit<A> e -> new Step.Emit<>(e.value(), e.tail().onError(action));
            case Step.Skip<A> s -> new Step.Skip<>(s.tail().onError(action));
            case Step.Fail<A> failed ->
                new Step.Fail<>(observed(failed.error()), failed.tail().onError(action));
            case Step.Done<A> d -> d;
          };
        };
      }

      /** The error, once the action has seen it, or what the action throws. */
      private Throwable observed(Throwable error) {
        try {
          action.accept(error);
          return error;
        } catch (Throwable thrown) {
          return thrown;
        }
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
   *   <li>An error occurs during pulling. A failed element, such as a failed {@link #mapTask} task
   *       further up, does not end the stream, so this runs when the stream after it ends or is
   *       closed: every terminal operation closes it, and code that pulls by hand closes a {@link
   *       Step.Fail}'s tail as it does a {@link Step.Emit}'s
   *   <li>The {@code use} function throws
   *   <li>The stream is partially consumed (via {@link #take}, {@link #headOption}, etc.), which
   *       closes the rest of it
   * </ul>
   *
   * <p>The release runs with the thread's interrupt status cleared, as {@link #onFinalize}
   * describes.
   *
   * <p><b>Limitation:</b> A consumer that pulls steps by hand and drops the stream, or the tail of
   * a step, without draining it or calling {@link #close()} never runs the release. Every terminal
   * operation, {@link #take} and {@link #takeWhile} take care of this.
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
   *     // close() throws IOException, which a Runnable cannot, so VTask.of
   *     reader -> VTask.of(() -> {
   *         reader.close();
   *         return Unit.INSTANCE;
   *     })
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
   *   <li>An error occurs during pulling. A failed element, such as a failed {@link #mapTask} task
   *       further up, does not end the stream, so this runs when the stream after it ends or is
   *       closed: every terminal operation closes it, and code that pulls by hand closes a {@link
   *       Step.Fail}'s tail as it does a {@link Step.Emit}'s
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
   * finaliser that throws fails the operation that completed or closed the stream with the
   * exception it threw, a checked one included.
   *
   * <p>The finaliser runs with the thread's interrupt status cleared, and the status is restored
   * afterwards, so a finaliser that blocks is not cut short when the reading was cancelled. The
   * release of a {@link #bracket} runs the same way.
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
      VStream<A> stream, Function<? super A, ? extends @Nullable R> stop, R atEnd)
      throws Throwable {
    // Before it starts, so a run on an interrupted thread fails without reading or closing
    Executing.stopIfInterrupted();
    VStream<A> current = stream;
    R result;
    try {
      Step<A> step = Executing.executed(current.pull());
      search:
      while (true) {
        switch (step) {
          case Step.Emit<A> e -> {
            current = e.tail();
            result = stop.apply(e.value());
            if (result != null) {
              break search;
            }
          }
          case Step.Skip<A> s -> current = s.tail();
          case Step.Fail<A> failed -> {
            // The catch closes the stream after the failed element
            current = failed.tail();
            throw failed.error();
          }
          case Step.Done<A> _ -> {
            return atEnd;
          }
        }
        // Between pulls, so a stop here closes only what this run has reached
        step = Executing.pulledUnlessInterrupted(current);
      }
    } catch (Throwable t) {
      Closing.closeAfterFailure(current, t);
      throw t;
    }
    // Stopping before the end, so close what is left unread
    Executing.executed(current.close());
    return result;
  }

  /**
   * Internal helper for chunkWhile: builds the chunk that starts with {@code first}, then
   * recursively creates subsequent chunks.
   */
  private static <A> VStream<NonEmptyList<A>> buildChunkWhile(
      A first, VStream<A> tail, BiPredicate<? super A, ? super A> sameChunk) {
    return DeferredStream.deferring(
        () -> {
          VStream<A> current = tail;
          // A list of this run's own, so a second pull starts again from the first element
          List<A> currentChunk = new ArrayList<>();
          currentChunk.add(first);
          A currentPrev = first;

          while (true) {
            Step<A> step;
            try {
              // The tail came from this run's own pull, so a stop here closes only what it reached
              step = Executing.pulledUnlessInterrupted(current);
            } catch (Throwable t) {
              // The chunk so far comes first, then the failure
              return DerivedStream.handingOnThenEnding(List.of(chunkOf(currentChunk)), t, current);
            }
            switch (step) {
              case Step.Emit<A> e -> {
                // Past this element before testing it, so a predicate that throws closes the rest
                current = e.tail();
                boolean sameAsBefore;
                try {
                  sameAsBefore = sameChunk.test(currentPrev, e.value());
                } catch (Throwable t) {
                  Closing.closeAfterFailure(current, t);
                  throw t;
                }
                if (sameAsBefore) {
                  currentChunk.add(e.value());
                  currentPrev = e.value();
                } else {
                  // Current chunk is complete, start a new one
                  return DerivedStream.concatContinuing(
                      VStream.of(chunkOf(currentChunk)),
                      buildChunkWhile(e.value(), e.tail(), sameChunk));
                }
              }
              case Step.Skip<A> s -> current = s.tail();
              case Step.Fail<A> failed -> {
                // The chunk so far comes first, then the failed element, after which chunking
                // starts afresh
                return DerivedStream.handingOnThenFailedElement(
                    List.of(chunkOf(currentChunk)), failed, rest -> rest.chunkWhile(sameChunk));
              }
              case Step.Done<A> _ -> {
                return VStream.of(chunkOf(currentChunk));
              }
            }
          }
        },
        tail);
  }

  /** The chunk a batch makes, which the caller has checked holds an element. */
  private static <A> NonEmptyList<A> chunkOf(List<A> batch) {
    return NonEmptyList.of(batch.getFirst(), batch.subList(1, batch.size()));
  }

  /** The chunk a batch read before a failure makes, if it read an element. */
  private static <A> List<NonEmptyList<A>> chunksSoFar(List<A> batch) {
    return batch.isEmpty() ? List.of() : List.of(chunkOf(batch));
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
    return () ->
        VTask.delay(
            () ->
                new Step.Emit<>(
                    Objects.requireNonNull(
                        list.get(index), "VStream.fromList element cannot be null"),
                    fromListAt(list, index + 1)));
  }

  /** Streams an iterator's elements, failing with {@code nullElement} on a null one. */
  private static <A> VStream<A> fromIteratorRefusing(Iterator<A> iterator, String nullElement) {
    return () ->
        VTask.delay(
            () -> {
              if (iterator.hasNext()) {
                A value = Objects.requireNonNull(iterator.next(), nullElement);
                return new Step.Emit<>(value, fromIteratorRefusing(iterator, nullElement));
              } else {
                return new Step.Done<>();
              }
            });
  }

  /** Streams {@code seed}, then each value {@code f} gives from the one before. */
  private static <A> VStream<A> iterateFrom(A seed, UnaryOperator<A> f) {
    return () ->
        VTask.delay(
            () ->
                new Step.Emit<>(
                    seed,
                    iterateFrom(
                        Objects.requireNonNull(f.apply(seed), "f must not return null"), f)));
  }

  /** Streams {@code value} for ever. */
  private static <A> VStream<A> repeating(A value) {
    return () -> VTask.succeed(new Step.Emit<>(value, repeating(value)));
  }
}

// =====================================================================
// Internal stream implementations
// =====================================================================

/**
 * Stack-safe flatMap implementation. Maintains an inner stream reference and processes steps
 * iteratively within each pull.
 *
 * <p>A sub-stream is handed on unreached, so a stop before its first pull leaves it as it is,
 * unless the stream owns its sub-streams, as {@code VStreamPar.parEvalFlatMap} does, whose batch
 * built them: closing it then closes a sub-stream it has not reached too.
 */
final class FlatMapStream<A, B> extends DerivedStream<A, B> {

  private final Function<? super A, ? extends VStream<B>> f;
  private final boolean ownsSubStreams;

  FlatMapStream(
      VStream<A> outer, Function<? super A, ? extends VStream<B>> f, boolean ownsSubStreams) {
    super(outer);
    this.f = f;
    this.ownsSubStreams = ownsSubStreams;
  }

  /** The flattening of what follows, which owns its sub-streams as this one does. */
  private FlatMapStream<A, B> rest(VStream<A> tail) {
    return new FlatMapStream<>(tail, f, ownsSubStreams);
  }

  @Override
  public VTask<Step<B>> pull() {
    return pullFlatMap(
        step ->
            switch (step) {
              case Step.Emit<A> e -> {
                VStream<B> inner =
                    Closing.applyNonNullOrClose(
                        f, e.value(), e.tail(), "flatMap function returned null stream");
                yield VTask.succeed(
                    new Step.Skip<>(
                        ownsSubStreams
                            ? DerivedStream.concatContinuing(inner, rest(e.tail()))
                            : DerivedStream.concatContinuingUnreached(inner, rest(e.tail()))));
              }
              case Step.Skip<A> s -> VTask.succeed(new Step.Skip<>(rest(s.tail())));
              case Step.Fail<A> failed ->
                  VTask.succeed(new Step.Fail<>(failed.error(), rest(failed.tail())));
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
                  // A failed left element pairs with the right one, as an element does
                  case Step.Fail<A> failed -> pairWithRight(failed);
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
                      // A failed right element fails the pair, and the left element goes with it
                      case Step.Fail<B> failed ->
                          VTask.succeed(
                              new Step.Fail<>(
                                  failed.error(),
                                  new ZipWithStream<>(
                                      leftEmit.tail(),
                                      failed.tail(),
                                      combiner,
                                      Closing.Reading.BOTH)));
                      case Step.Done<B> _ ->
                          leftEmit.tail().close().<Step<C>>map(_ -> new Step.Done<>());
                      case Step.Emit<B> rightEmit -> {
                        C combined;
                        try {
                          combined =
                              Objects.requireNonNull(
                                  combiner.apply(leftEmit.value(), rightEmit.value()),
                                  "combiner must not return null");
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
   * Pairs a failed left element with the right element beside it, which goes with it, so the
   * elements after them pair as before. A failed right element there too fails the same pair, with
   * the left element's error. If right has ended, the zip ends there.
   */
  private VTask<Step<C>> pairWithRight(Step.Fail<A> leftFailed) {
    VTask<Step<C>> paired =
        right
            .pull()
            .flatMap(
                rightStep ->
                    switch (rightStep) {
                      // Read the failed left element again beside the skip tail
                      case Step.Skip<B> s ->
                          VTask.succeed(
                              new Step.Skip<>(
                                  new ZipWithStream<>(
                                      DerivedStream.failedElement(
                                          leftFailed.error(), leftFailed.tail()),
                                      s.tail(),
                                      combiner,
                                      Closing.Reading.BOTH)));
                      case Step.Emit<B> rightEmit ->
                          VTask.succeed(failedPair(leftFailed, rightEmit.tail()));
                      case Step.Fail<B> rightFailed ->
                          VTask.succeed(failedPair(leftFailed, rightFailed.tail()));
                      case Step.Done<B> _ ->
                          leftFailed.tail().close().<Step<C>>map(_ -> new Step.Done<>());
                    });
    // Only this pull holds the rest of left, and a right it starts reading here is one closing this
    // zip would not reach, so a failure closes them
    return reading == Closing.Reading.BOTH
        ? Closing.closingOnFailure(paired, leftFailed.tail())
        : Closing.closingOnFailure(paired, leftFailed.tail(), right);
  }

  private Step<C> failedPair(Step.Fail<A> leftFailed, VStream<B> rightTail) {
    return new Step.Fail<>(
        leftFailed.error(),
        new ZipWithStream<>(leftFailed.tail(), rightTail, combiner, Closing.Reading.BOTH));
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
                      // A failed element takes its turn, so the other stream gives the next
                      case Step.Fail<A> failed ->
                          new Step.Fail<>(
                              failed.error(),
                              new InterleaveStream<>(second, failed.tail(), reading.next()));
                      // Before either has been read, the second stream is handed on unreached
                      case Step.Done<A> _ ->
                          new Step.Skip<>(
                              reading == Closing.Reading.NEITHER
                                  ? DerivedStream.unreached(second)
                                  : second);
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
    return pullMap(
        step ->
            switch (step) {
              case Step.Emit<A> e ->
                  new Step.Emit<>(
                      Closing.applyNonNullOrClose(
                          mapper, e.value(), e.tail(), "f must not return null"),
                      e.tail().map(mapper));
              case Step.Skip<A> s -> new Step.Skip<>(s.tail().map(mapper));
              case Step.Fail<A> failed ->
                  new Step.Fail<>(failed.error(), failed.tail().map(mapper));
              case Step.Done<A> _ -> new Step.Done<>();
            });
  }

  @Override
  @SuppressWarnings("unchecked")
  public <C> VStream<C> map(Function<? super B, ? extends C> f) {
    Objects.requireNonNull(f, "f must not be null");
    // Fuse consecutive maps: chain functions instead of nesting streams.
    // This keeps the pull() delegation depth at 1 regardless of chain length.
    Function<? super A, ? extends C> fused =
        (Function<A, C>) (Function<?, ?>) new Fused((Function<Object, Object>) mapper, f);
    return new MappedStream<>(upstream, fused);
  }

  /**
   * Consecutive map functions fused into one. Each fusion adds a link, and an application runs the
   * links in a loop, checking each result, so a chain of any length needs no stack.
   */
  private static final class Fused implements Function<Object, Object> {

    private final Function<Object, Object> before;
    private final Function<Object, Object> stage;
    // The functions oldest first, built on the first application
    private volatile @Nullable List<Function<Object, Object>> chain;

    @SuppressWarnings("unchecked")
    Fused(Function<Object, Object> before, Function<?, ?> stage) {
      this.before = before;
      this.stage = (Function<Object, Object>) stage;
    }

    @Override
    public Object apply(Object value) {
      List<Function<Object, Object>> functions = chain;
      if (functions == null) {
        functions = flatten();
        chain = functions;
      }
      Object result = value;
      for (Function<Object, Object> function : functions) {
        result = Objects.requireNonNull(function.apply(result), "f must not return null");
      }
      return result;
    }

    private List<Function<Object, Object>> flatten() {
      Deque<Function<Object, Object>> functions = new ArrayDeque<>();
      Function<Object, Object> link = this;
      while (link instanceof Fused fused) {
        functions.push(fused.stage);
        link = fused.before;
      }
      functions.push(link);
      return List.copyOf(functions);
    }
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
          // The finaliser's failure is reported as it threw it, as at the end of the stream
          Cleanup.withInterruptCleared(() -> Executing.executed(finalizer));
        }
      } catch (Throwable t) {
        failure = Cleanup.keep(failure, t);
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
            .<Step<A>>flatMap(
                step ->
                    switch (step) {
                      case Step.Emit<A> e ->
                          VTask.succeed(
                              new Step.Emit<>(
                                  e.value(), consumption(e.tail(), finalizer, released)));
                      case Step.Skip<A> s ->
                          VTask.succeed(
                              new Step.Skip<>(consumption(s.tail(), finalizer, released)));
                      // The consumption goes on after a failed element, and releases when it ends
                      // or closes
                      case Step.Fail<A> failed ->
                          VTask.succeed(
                              new Step.Fail<>(
                                  failed.error(), consumption(failed.tail(), finalizer, released)));
                      // A task of its own, so the finaliser's failure is reported as it threw it
                      case Step.Done<A> _ ->
                          () -> {
                            if (released.compareAndSet(false, true)) {
                              Cleanup.withInterruptCleared(() -> Executing.executed(finalizer));
                            }
                            return new Step.Done<A>();
                          };
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
