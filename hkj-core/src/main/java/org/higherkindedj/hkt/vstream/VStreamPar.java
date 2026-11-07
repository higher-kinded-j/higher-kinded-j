// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.Cleanup;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * Utility class providing parallel combinators for {@link VStream} computations.
 *
 * <p>{@code VStreamPar} provides operations for processing stream elements concurrently using Java
 * 25's virtual threads via {@code StructuredTaskScope}. Every operation but {@link #merge(List)}
 * bounds the number of elements in flight, so a slow consumer slows the reading without an explicit
 * backpressure protocol.
 *
 * <p><b>Key operations:</b>
 *
 * <ul>
 *   <li>{@link #parEvalMap(VStream, int, Function)} - Apply effectful function with bounded
 *       concurrency, preserving input order
 *   <li>{@link #parEvalMapUnordered(VStream, int, Function)} - Apply effectful function with
 *       bounded concurrency, emitting each result as its task completes
 *   <li>{@link #parEvalFlatMap(VStream, int, Function)} - Apply stream-producing function with
 *       bounded concurrency, interleaving results
 *   <li>{@link #merge(List)} - Merge multiple streams concurrently
 *   <li>{@link #parCollect(VStream, int)} - Collect all elements in parallel batches
 * </ul>
 *
 * <p><b>Backpressure model:</b> VStream is pull-based; the consumer drives evaluation by requesting
 * elements. The parallel operations in this class add bounded concurrency on top of this pull
 * model. {@link #parEvalMap}, and {@link #parEvalFlatMap}, which is built on it, read a batch of up
 * to {@code concurrency} elements when they are pulled, and {@link #parEvalMapUnordered} keeps no
 * more than {@code concurrency} tasks and results ahead of the consumer, so a slow consumer slows
 * the reading. {@link #merge} of several streams is the exception: it reads each source as fast as
 * the source gives elements, and holds those not yet pulled without a limit.
 *
 * <p><b>Error handling:</b> All operations use fail-fast semantics. If any element's computation
 * fails, the entire stream fails and other in-flight tasks are cancelled via {@code
 * StructuredTaskScope}. If reading the source fails, the elements read before the failure are
 * processed and handed on first, then the stream fails; an interrupt stops the processing at once.
 * A failed element, such as one whose {@link VStream#mapTask} task failed further up, is handed on
 * in the same way, after which the stream goes on, so a {@link VStream#recover} after it gives a
 * value in its place. {@link #merge} is the exception: it stops every source at a failed element,
 * as at any other failure.
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * VStream<String> userIds = VStream.fromList(List.of("u1", "u2", "u3", "u4"));
 *
 * // Fetch user profiles concurrently, at most 2 in flight at a time
 * VStream<UserProfile> profiles = VStreamPar.parEvalMap(
 *     userIds, 2,
 *     id -> VTask.of(() -> apiClient.fetchProfile(id))
 * );
 *
 * List<UserProfile> result = profiles.toList().run();
 * }</pre>
 *
 * <p><b>Concurrency guidance:</b>
 *
 * <ul>
 *   <li>For I/O-bound tasks (API calls, database queries): use higher concurrency (8-64)
 *   <li>For CPU-bound tasks: use concurrency matching available processors
 *   <li>For mixed workloads: start with 4-8 and tune based on benchmarks
 *   <li>Concurrency of 1 is equivalent to sequential {@code mapTask}
 * </ul>
 *
 * @see VStream
 * @see VTask
 * @see StructuredTaskScope
 */
public final class VStreamPar {

  private static final String NULL_TASK_RESULT =
      "the task returned null, and a VTask always gives a value";

  private VStreamPar() {
    // Utility class - prevent instantiation
  }

  // ===== Internal merge signal types =====

  /**
   * Signal type for the queue-based merge. Each source stream pushes signals to a shared queue; the
   * consumer VStream pulls from the queue to build the output.
   */
  private sealed interface MergeSignal<A> {
    /** An element produced by a source stream. */
    record Element<A>(A value) implements MergeSignal<A> {}

    /** A source stream has been exhausted. */
    record SourceDone<A>() implements MergeSignal<A> {}

    /** A source stream failed with an exception. */
    record SourceError<A>(Throwable cause) implements MergeSignal<A> {}
  }

  /**
   * Applies an effectful function to each element with bounded concurrency, preserving input order.
   *
   * <p>Elements are pulled from the source stream in batches of up to {@code concurrency} elements.
   * Each element's {@link VTask} is forked onto a virtual thread via {@code StructuredTaskScope}.
   * Results are collected in the same order as the input elements, then emitted before the next
   * batch is pulled. If reading the source fails part-way through a batch, the elements read before
   * the failure are processed and emitted first, then the stream fails.
   *
   * <p>A task that fails ends the stream, so a {@link VStream#recover} after it ends there too,
   * where it would step past a failed {@link VStream#mapTask} task.
   *
   * <p>This is the primary parallel processing operation for VStream. Use this when the order of
   * output elements must match the order of input elements.
   *
   * <p>A concurrency at least as large as the stream processes every element, forking one task per
   * element. The batch list grows as elements arrive, so a concurrency such as {@link
   * Integer#MAX_VALUE} does not reserve a slot for every place in it before the first element is
   * read.
   *
   * @param <A> The type of elements in the input stream.
   * @param <B> The type of elements in the output stream.
   * @param stream The source stream. Must not be null.
   * @param concurrency The maximum number of elements to process concurrently. Must be positive.
   * @param f The effectful function to apply to each element. Must not be null.
   * @return A new {@code VStream} with parallel-processed elements in input order.
   * @throws NullPointerException if stream or f is null.
   * @throws IllegalArgumentException if concurrency is not positive.
   */
  @SuppressWarnings("preview")
  public static <A, B> VStream<B> parEvalMap(
      VStream<A> stream, int concurrency, Function<? super A, VTask<B>> f) {
    Objects.requireNonNull(stream, "stream must not be null");
    Objects.requireNonNull(f, "f must not be null");
    if (concurrency <= 0) {
      throw new IllegalArgumentException("concurrency must be positive, got: " + concurrency);
    }

    return DeferredStream.deferring(
        () ->
            mapBatch(
                stream,
                concurrency,
                batch -> processBatchOrdered(batch, f),
                rest -> parEvalMap(rest, concurrency, f)),
        stream);
  }

  /**
   * Applies an effectful function to each element with bounded concurrency, emitting each result as
   * its task completes, so the output is in completion order rather than input order.
   *
   * <p>Up to {@code concurrency} tasks run at once, and each result is emitted as its task
   * completes, so a slow task holds back only its own result, where {@link #parEvalMap} waits for
   * the slowest task in each batch. When the consumer takes a result, the next element is read and
   * its task starts. The tasks running and the results not yet pulled are never more than {@code
   * concurrency} together, so a consumer that stops pulling stops the reading. The output contains
   * the same elements as {@code parEvalMap}'s.
   *
   * <p>Each run of the pulled {@code VTask} starts a virtual thread that reads the source and forks
   * each element's task. A tail the stream gives keeps its step, so pulling it again gives the same
   * step. A failed element of the source, such as one whose {@link VStream#mapTask} task failed, is
   * handed on as it is read, and the stream goes on after it, so a {@link VStream#recover} after it
   * gives a value in its place. If a task or the function fails, the tasks still running are
   * cancelled, the source is closed where the reading got to, and the stream fails with that
   * failure, so a {@code recover} after it ends there. If a pull from the source fails, the tasks
   * already running finish, and their results come before the failure; an interrupt cancels them at
   * once. A function that closes the stream gets what closing the source throws. Closing the stream
   * part-way, as {@link VStream#take} does once it has its elements, cancels the tasks still
   * running and closes the source. A run that is neither read to its end nor closed leaves its
   * thread waiting, so close the stream you stop reading. Closing, or a failed task, interrupts the
   * thread reading the source, so a source finaliser that runs inside a pull, as one does at the
   * source's end, can be cut short, as with {@link #merge}.
   *
   * <p>The source is read, and the tasks run, on threads this stream starts, which do not see the
   * caller's {@code ScopedValue} bindings. Bind a value the tasks need inside each task, or use
   * {@link #parEvalMap}, whose tasks see the caller's bindings.
   *
   * @param <A> The type of elements in the input stream.
   * @param <B> The type of elements in the output stream.
   * @param stream The source stream. Must not be null.
   * @param concurrency The maximum number of elements to process concurrently. Must be positive.
   * @param f The effectful function to apply to each element. Must not be null.
   * @return A new {@code VStream} with parallel-processed elements in completion order.
   * @throws NullPointerException if stream or f is null.
   * @throws IllegalArgumentException if concurrency is not positive.
   */
  public static <A, B> VStream<B> parEvalMapUnordered(
      VStream<A> stream, int concurrency, Function<? super A, VTask<B>> f) {
    Objects.requireNonNull(stream, "stream must not be null");
    Objects.requireNonNull(f, "f must not be null");
    if (concurrency <= 0) {
      throw new IllegalArgumentException("concurrency must be positive, got: " + concurrency);
    }
    return new CompletionOrderStream<>(stream, concurrency, f);
  }

  /**
   * Applies a stream-producing function to each element with bounded concurrency, concatenating the
   * results from each sub-stream.
   *
   * <p>For each element, the function produces a {@code VStream}. Up to {@code concurrency}
   * sub-stream <em>creation</em> calls are executed concurrently via {@link #parEvalMap}. The
   * resulting sub-streams are then concatenated lazily via {@link VStream#flatMap}; sub-stream
   * contents are never materialised into intermediate lists.
   *
   * @param <A> The type of elements in the input stream.
   * @param <B> The type of elements in the output stream.
   * @param stream The source stream. Must not be null.
   * @param concurrency The maximum number of sub-streams to create concurrently. Must be positive.
   * @param f The function producing a stream for each element. Must not be null.
   * @return A new {@code VStream} with concatenated results from parallel sub-stream creation.
   * @throws NullPointerException if stream or f is null.
   * @throws IllegalArgumentException if concurrency is not positive.
   */
  @SuppressWarnings("preview")
  public static <A, B> VStream<B> parEvalFlatMap(
      VStream<A> stream, int concurrency, Function<A, VStream<B>> f) {
    Objects.requireNonNull(stream, "stream must not be null");
    Objects.requireNonNull(f, "f must not be null");
    if (concurrency <= 0) {
      throw new IllegalArgumentException("concurrency must be positive, got: " + concurrency);
    }

    // Create sub-streams concurrently, then concatenate lazily without materialising
    return parEvalMap(
            stream,
            concurrency,
            a -> VTask.of(() -> Objects.requireNonNull(f.apply(a), "f must not return null")))
        .flatMap(Function.identity());
  }

  /**
   * Merges multiple streams concurrently into a single stream. Elements are emitted as they become
   * available from any source stream.
   *
   * <p>Each source stream is consumed concurrently on its own virtual thread within a {@code
   * StructuredTaskScope}. Elements are pushed to a shared queue as they are produced and the output
   * stream pulls from the queue, so the first element is available as soon as any source produces
   * one — without waiting for all sources to finish.
   *
   * <p>If any source stream fails, at a pull or at a failed element, the error is propagated to the
   * consumer and remaining sources are signalled to stop. A {@link VStream#recover} after a merge
   * of several streams therefore ends the stream there, even after a failed {@link VStream#mapTask}
   * task; a merge of one stream is that stream.
   *
   * <p>Each run of the pulled {@code VTask} starts its own producer. Closing the merge, or a source
   * failing, interrupts any source still being pulled, then closes each source that did not finish,
   * on a thread that nothing interrupts, so their finalisers run in full. A source finaliser that
   * fails while the merge closes is dropped, since the merge has already stopped.
   *
   * <p>With more than one source, the sources are read on threads the merge starts, which do not
   * see the caller's {@code ScopedValue} bindings, and each is read as fast as it gives elements,
   * with the elements not yet pulled held without a limit. A merge of one stream is that stream.
   *
   * @param <A> The type of elements in the streams.
   * @param sources The list of streams to merge, which is copied. Must not be null, nor contain
   *     null.
   * @return A new {@code VStream} containing all elements from all source streams.
   * @throws NullPointerException if streams is null or contains null.
   */
  @SuppressWarnings("preview")
  public static <A> VStream<A> merge(List<VStream<A>> sources) {
    // A copy, so a list changed after this call cannot change what each run reads
    List<VStream<A>> streams =
        List.copyOf(Objects.requireNonNull(sources, "streams must not be null"));

    if (streams.isEmpty()) {
      return VStream.empty();
    }

    if (streams.size() == 1) {
      return streams.getFirst();
    }

    int sourceCount = streams.size();

    // Each run of the pulled VTask starts its own producer, so producer threads are not started
    // until the first pull runs. Closing the merge before then closes every source; closing it
    // after closes the latest run.
    return new RememberingStream<>(
        VTask.delay(
            () -> {
              LinkedBlockingQueue<MergeSignal<A>> queue = new LinkedBlockingQueue<>();
              AtomicBoolean cancelled = new AtomicBoolean(false);

              // Background thread manages a StructuredTaskScope with one subtask per source.
              // Each subtask pulls elements and pushes them to the shared queue.
              Thread producer =
                  Thread.ofVirtual().start(() -> runMergeScope(streams, queue, cancelled));

              return mergeFromQueue(queue, sourceCount, cancelled, producer);
            }),
        streams.toArray(VStream<?>[]::new));
  }

  /**
   * Opens a {@link StructuredTaskScope}, forks one subtask per source stream (each running {@link
   * #consumeSource}), and joins. Runs on a background virtual thread started by {@link
   * #merge(List)}.
   *
   * <p>If the background thread is interrupted during {@code scope.join()}, the interrupt flag is
   * restored and the scope is closed via try-with-resources, which stops every subtask. Only then
   * are the sources that did not finish closed, so no subtask is still reading one.
   */
  @SuppressWarnings("preview")
  // Package-private for testing (allows direct invocation from same-package tests)
  static <A> void runMergeScope(
      List<VStream<A>> streams,
      LinkedBlockingQueue<MergeSignal<A>> queue,
      AtomicBoolean cancelled) {
    AtomicReferenceArray<@Nullable VStream<A>> positions =
        new AtomicReferenceArray<>(streams.size());
    try (var scope = StructuredTaskScope.open()) {
      for (int i = 0; i < streams.size(); i++) {
        int index = i;
        positions.set(index, streams.get(index));
        scope.fork(() -> consumeSource(index, positions, queue, cancelled));
      }
      scope.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      closeUnfinished(positions);
    }
  }

  /**
   * Consumes a single source stream, pushing elements to the merge queue. Called on a virtual
   * thread within a {@code StructuredTaskScope}.
   *
   * <p>On error, the first failure sets the {@code cancelled} flag and pushes a {@link
   * MergeSignal.SourceError} so that the consumer fails fast. Subsequent source errors are
   * discarded (only the first is reported). A failed element stops the merge in the same way, and
   * the source's position moves past it, so the stream after it is closed with the source.
   */
  @SuppressWarnings("preview")
  private static <A> Void consumeSource(
      int index,
      AtomicReferenceArray<@Nullable VStream<A>> positions,
      LinkedBlockingQueue<MergeSignal<A>> queue,
      AtomicBoolean cancelled) {
    try {
      while (!cancelled.get()) {
        VStream<A> current = Objects.requireNonNull(positions.get(index));
        VStream.Step<A> step = Executing.executed(current.pull());
        switch (step) {
          case VStream.Step.Emit<A> e -> {
            positions.set(index, e.tail());
            queue.put(new MergeSignal.Element<>(e.value()));
          }
          case VStream.Step.Skip<A> s -> positions.set(index, s.tail());
          case VStream.Step.Fail<A> failed -> {
            // A failed element stops the merge, which closes this source from after it
            positions.set(index, failed.tail());
            throw failed.error();
          }
          case VStream.Step.Done<A> _ -> {
            positions.set(index, null);
            queue.put(new MergeSignal.SourceDone<>());
            return null;
          }
        }
      }
      return null;
    } catch (Throwable t) {
      // The merge sets cancelled before it interrupts a source, so the interrupt it causes goes
      // unreported below; one that stopped the source's own pull before then is its failure
      if (t instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      if (!cancelled.getAndSet(true)) {
        // offer() is non-blocking on an unbounded LinkedBlockingQueue, so it always succeeds
        // and cannot throw InterruptedException (unlike put()).
        queue.offer(new MergeSignal.SourceError<>(t));
      }
      return null;
    }
  }

  /**
   * Closes the sources this merge stopped reading before they finished, so their finalisers run.
   * They close on a thread of their own, which nothing interrupts, so a finaliser that waits is not
   * cut short when the merge is closed. A failure to close is dropped: the merge has already
   * stopped, or reported the failure that stopped it.
   */
  private static <A> void closeUnfinished(AtomicReferenceArray<@Nullable VStream<A>> positions) {
    List<VStream<?>> unfinished = new ArrayList<>(positions.length());
    for (int i = 0; i < positions.length(); i++) {
      VStream<A> position = positions.get(i);
      if (position != null) {
        unfinished.add(position);
      }
    }
    if (unfinished.isEmpty()) {
      return;
    }
    Thread closer =
        Thread.ofVirtual()
            .start(() -> Closing.closeAll(unfinished.toArray(VStream<?>[]::new)).runSafe());
    boolean interrupted = false;
    while (true) {
      try {
        closer.join();
        break;
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Builds a {@code VStream} that pulls elements from the merge queue. Each pull blocks on {@link
   * LinkedBlockingQueue#take()} until a signal arrives.
   *
   * <p>The returned stream overrides {@link VStream#close()} to set the {@code cancelled} flag and
   * interrupt the producer, which signals the source subtasks to stop, then waits while the
   * producer closes the sources that did not finish. This is essential for preventing memory leaks
   * when the consumer terminates early (e.g. via {@link VStream#take}).
   *
   * @param queue the shared queue populated by source-consuming subtasks.
   * @param remainingSources the number of source streams that have not yet sent {@link
   *     MergeSignal.SourceDone}.
   * @param cancelled shared cancellation flag; set to {@code true} to stop producers.
   * @param producer the background producer thread to join on close.
   */
  private static <A> VStream<A> mergeFromQueue(
      LinkedBlockingQueue<MergeSignal<A>> queue,
      int remainingSources,
      AtomicBoolean cancelled,
      Thread producer) {
    // No guard needed: merge() guarantees remainingSources >= 2 on the initial call,
    // and recursive calls pass newRemaining >= 1 (the newRemaining <= 0 branch yields Done).
    return new VStream<>() {
      @Override
      public VTask<VStream.Step<A>> pull() {
        return () -> {
          MergeSignal<A> signal;
          try {
            signal = queue.take();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Merge interrupted", e);
          }
          return switch (signal) {
            case MergeSignal.Element<A> e ->
                new VStream.Step.Emit<>(
                    e.value(), mergeFromQueue(queue, remainingSources, cancelled, producer));
            case MergeSignal.SourceDone<A> _ -> {
              int newRemaining = remainingSources - 1;
              if (newRemaining <= 0) {
                yield new VStream.Step.Done<>();
              }
              yield new VStream.Step.Skip<>(
                  mergeFromQueue(queue, newRemaining, cancelled, producer));
            }
            case MergeSignal.SourceError<A> err -> {
              // Stop this run before failing, since a consumer reading through a stream that
              // keeps nothing it built has nothing that reaches it. The failure is the source's
              // own, an InterruptedException included, so it is thrown outside the take's catch
              Throwable failure = err.cause();
              Closing.runAfterFailure(close(), failure);
              throw failure;
            }
          };
        };
      }

      @Override
      public VTask<Unit> close() {
        return VTask.delay(
            () -> {
              cancelled.set(true);
              queue.clear();
              // Interrupt the producer so a source blocked in a pull stops; its scope stops the
              // others, then the producer closes the sources that did not finish. Wait for that
              // with an interrupt already pending held back, as cleanup runs, so the sources are
              // closed when this returns; an interrupt that arrives while waiting stops the wait
              producer.interrupt();
              try {
                Cleanup.withInterruptCleared(producer::join);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return Unit.INSTANCE;
            });
      }
    };
  }

  /**
   * Merges two streams concurrently into a single stream.
   *
   * <p>This is a convenience method equivalent to {@code merge(List.of(first, second))}.
   *
   * @param <A> The type of elements in the streams.
   * @param first The first stream. Must not be null.
   * @param second The second stream. Must not be null.
   * @return A new {@code VStream} containing all elements from both streams.
   * @throws NullPointerException if either stream is null.
   */
  public static <A> VStream<A> merge(VStream<A> first, VStream<A> second) {
    Objects.requireNonNull(first, "first must not be null");
    Objects.requireNonNull(second, "second must not be null");
    return merge(List.of(first, second));
  }

  /**
   * Collects all elements from a stream using parallel batch processing. Elements are pulled in
   * batches of {@code batchSize} and each batch is processed concurrently via {@link #parEvalMap}.
   *
   * <p>This is a terminal operation that produces a {@link VTask} containing the collected list.
   * Elements in the result list are in the same order as in the source stream.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate. Use {@link
   * VStream#take(long)} to limit the stream first.
   *
   * @param <A> The type of elements in the stream.
   * @param stream The source stream. Must not be null.
   * @param batchSize The number of elements to pull and process concurrently per batch. Must be
   *     positive.
   * @return A {@link VTask} that produces a list of all elements.
   * @throws NullPointerException if stream is null.
   * @throws IllegalArgumentException if batchSize is not positive.
   */
  public static <A> VTask<List<A>> parCollect(VStream<A> stream, int batchSize) {
    Objects.requireNonNull(stream, "stream must not be null");
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive, got: " + batchSize);
    }

    // Delegate to parEvalMap with identity, which genuinely processes each batch concurrently
    return parEvalMap(stream, batchSize, VTask::succeed).toList();
  }

  // ===== Internal helpers =====

  /**
   * One batch of a parallel map: reads up to {@code concurrency} elements, runs their tasks with
   * {@code process}, and gives their results, then the rest of the stream, which {@code next} maps
   * in the same way.
   *
   * <p>If an element fails while the batch is read, the results for the elements read before it
   * come first, then the failed element, with {@code next} over the stream after it, so a {@link
   * VStream#recover} after it goes on from the next element. If reading fails, the results come
   * first, then the failure, which ends the stream; an interrupt ends it at once, and runs none of
   * the batch. If a task of the batch fails, that failure comes first, and ends the stream.
   */
  private static <A, B> VStream<B> mapBatch(
      VStream<A> stream,
      int concurrency,
      Function<List<A>, VTask<List<B>>> process,
      Function<VStream<A>, VStream<B>> next)
      throws Throwable {
    List<A> batch = new ArrayList<>(batchInitialCapacity(concurrency));
    AtomicReference<VStream<A>> tailHolder = new AtomicReference<>(stream);
    Function<List<B>, VStream<B>> after;
    try {
      after = pullBatch(tailHolder, batch, concurrency, next);
    } catch (Throwable readFailure) {
      // An interrupt stops the stream, so none of the batch runs
      List<B> results =
          Executing.isInterrupt(readFailure) ? List.of() : results(batch, process, tailHolder);
      return DerivedStream.handingOnThenEnding(results, readFailure, tailHolder.get());
    }
    return after.apply(results(batch, process, tailHolder));
  }

  /**
   * Runs the tasks of a batch with {@code process}. If one fails, the stream is closed where the
   * batch got to, which its start may not reach, and the failure is rethrown.
   */
  private static <A, B> List<B> results(
      List<A> batch,
      Function<List<A>, VTask<List<B>>> process,
      AtomicReference<VStream<A>> tailHolder)
      throws Throwable {
    if (batch.isEmpty()) {
      return List.of();
    }
    try {
      return Executing.executed(process.apply(batch));
    } catch (Throwable t) {
      Closing.closeAfterFailure(tailHolder.get(), t);
      throw t;
    }
  }

  /**
   * Initial capacity of a parallel batch. A larger concurrency still forks up to that many tasks;
   * the list grows as elements arrive, so a concurrency such as {@code Integer.MAX_VALUE} does not
   * allocate that many slots before the first element is read.
   *
   * <p>The same cap is private to {@link VStream#chunk(int)}, so this class keeps its own. {@code
   * pullBatch} still reads up to {@code concurrency} elements, and each of those still forks a
   * task.
   */
  private static int batchInitialCapacity(int concurrency) {
    return Math.min(concurrency, 1024);
  }

  /**
   * Pulls up to {@code count} elements from the stream, storing them in the batch list and moving
   * the tail holder past each step, so it holds the position reached if a pull fails.
   *
   * @return what follows the results of the batch: nothing at the end of the stream, a failed
   *     element with {@code next} over the stream after it, or, once the batch is full, {@code
   *     next} over the stream from where it got to
   */
  private static <A, B> Function<List<B>, VStream<B>> pullBatch(
      AtomicReference<VStream<A>> tailHolder,
      List<A> batch,
      int count,
      Function<VStream<A>, VStream<B>> next)
      throws Throwable {
    while (true) {
      VStream.Step<A> step = Executing.executed(tailHolder.get().pull());
      switch (step) {
        case VStream.Step.Emit<A> e -> {
          batch.add(e.value());
          tailHolder.set(e.tail());
          if (batch.size() == count) {
            return results ->
                DerivedStream.concatContinuing(VStream.fromList(results), next.apply(e.tail()));
          }
        }
        case VStream.Step.Skip<A> s -> tailHolder.set(s.tail());
        case VStream.Step.Fail<A> failed -> {
          tailHolder.set(failed.tail());
          return results -> DerivedStream.handingOnThenFailedElement(results, failed, next);
        }
        case VStream.Step.Done<A> _ -> {
          return VStream::fromList;
        }
      }
    }
  }

  /** Processes a batch of elements in parallel using StructuredTaskScope, preserving order. */
  @SuppressWarnings("preview")
  private static <A, B> VTask<List<B>> processBatchOrdered(
      List<A> batch, Function<? super A, VTask<B>> f) {
    return () -> {
      try (var scope = StructuredTaskScope.open()) {
        List<StructuredTaskScope.Subtask<B>> subtasks = new ArrayList<>(batch.size());

        for (A element : batch) {
          VTask<B> task = f.apply(element);
          Objects.requireNonNull(task, () -> "function returned null task for element: " + element);
          subtasks.add(scope.fork(task.asCallable()));
        }

        scope.join();

        List<B> results = new ArrayList<>(subtasks.size());
        for (var subtask : subtasks) {
          // A task written by hand can still return null
          results.add(Objects.requireNonNull(subtask.get(), NULL_TASK_RESULT));
        }
        return results;
      } catch (StructuredTaskScope.FailedException e) {
        throw e.getCause();
      }
    };
  }
}
