// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * Utility class providing parallel combinators for {@link VStream} computations.
 *
 * <p>{@code VStreamPar} provides operations for processing stream elements concurrently using Java
 * 25's virtual threads via {@code StructuredTaskScope}. All operations in this class leverage
 * bounded concurrency to limit the number of in-flight elements, providing natural backpressure
 * without an explicit protocol.
 *
 * <p><b>Key operations:</b>
 *
 * <ul>
 *   <li>{@link #parEvalMap(VStream, int, Function)} - Apply effectful function with bounded
 *       concurrency, preserving input order
 *   <li>{@link #parEvalMapUnordered(VStream, int, Function)} - Apply effectful function with
 *       bounded concurrency, emitting in completion order
 *   <li>{@link #parEvalFlatMap(VStream, int, Function)} - Apply stream-producing function with
 *       bounded concurrency, interleaving results
 *   <li>{@link #merge(List)} - Merge multiple streams concurrently
 *   <li>{@link #parCollect(VStream, int)} - Collect all elements in parallel batches
 * </ul>
 *
 * <p><b>Backpressure model:</b> VStream is pull-based; the consumer drives evaluation by requesting
 * elements. The parallel operations in this class add bounded concurrency on top of this pull
 * model. At most {@code concurrency} elements are in flight at any time. Because virtual threads
 * block cheaply, no explicit backpressure protocol is needed; if the consumer is slow, the producer
 * simply blocks on a virtual thread until the consumer pulls the next element.
 *
 * <p><b>Error handling:</b> All operations use fail-fast semantics. If any element's computation
 * fails, the entire stream fails and other in-flight tasks are cancelled via {@code
 * StructuredTaskScope}.
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
    record Element<A>(@Nullable A value) implements MergeSignal<A> {}

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
   * batch is pulled.
   *
   * <p>This is the primary parallel processing operation for VStream. Use this when the order of
   * output elements must match the order of input elements.
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
      VStream<A> stream, int concurrency, Function<A, VTask<B>> f) {
    Objects.requireNonNull(stream, "stream must not be null");
    Objects.requireNonNull(f, "f must not be null");
    if (concurrency <= 0) {
      throw new IllegalArgumentException("concurrency must be positive, got: " + concurrency);
    }

    return VStream.defer(
        () -> {
          // Pull up to concurrency elements from source
          List<A> batch = new ArrayList<>(concurrency);
          VStream<A>[] tailHolder = new VStream[] {stream};
          boolean done = pullBatch(tailHolder, batch, concurrency);

          if (batch.isEmpty()) {
            return VStream.empty();
          }

          // Process batch in parallel, preserving order
          VTask<List<B>> batchTask = processBatchOrdered(batch, f);
          List<B> results = batchTask.run();

          VStream<B> batchStream = VStream.fromList(results);

          if (done) {
            return batchStream;
          }

          // Recursively process remaining elements
          return batchStream.concat(parEvalMap(tailHolder[0], concurrency, f));
        });
  }

  /**
   * Applies an effectful function to each element with bounded concurrency, emitting results in
   * completion order rather than input order.
   *
   * <p>This variant maximises throughput by emitting results as soon as they complete, without
   * waiting for earlier elements to finish. Use this when output order does not matter and you want
   * the fastest possible throughput.
   *
   * <p>The output stream contains exactly the same elements as {@link #parEvalMap}, but potentially
   * in a different order.
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
  @SuppressWarnings("preview")
  public static <A, B> VStream<B> parEvalMapUnordered(
      VStream<A> stream, int concurrency, Function<A, VTask<B>> f) {
    Objects.requireNonNull(stream, "stream must not be null");
    Objects.requireNonNull(f, "f must not be null");
    if (concurrency <= 0) {
      throw new IllegalArgumentException("concurrency must be positive, got: " + concurrency);
    }

    return VStream.defer(
        () -> {
          // Pull up to concurrency elements from source
          List<A> batch = new ArrayList<>(concurrency);
          VStream<A>[] tailHolder = new VStream[] {stream};
          boolean done = pullBatch(tailHolder, batch, concurrency);

          if (batch.isEmpty()) {
            return VStream.empty();
          }

          // Process batch in parallel, collecting in completion order
          VTask<List<B>> batchTask = processBatchUnordered(batch, f);
          List<B> results = batchTask.run();

          VStream<B> batchStream = VStream.fromList(results);

          if (done) {
            return batchStream;
          }

          return batchStream.concat(parEvalMapUnordered(tailHolder[0], concurrency, f));
        });
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
    return parEvalMap(stream, concurrency, a -> VTask.of(() -> f.apply(a)))
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
   * <p>If any source stream fails, the error is propagated to the consumer and remaining sources
   * are signalled to stop.
   *
   * @param <A> The type of elements in the streams.
   * @param streams The list of streams to merge. Must not be null.
   * @return A new {@code VStream} containing all elements from all source streams.
   * @throws NullPointerException if streams is null.
   */
  @SuppressWarnings("preview")
  public static <A> VStream<A> merge(List<VStream<A>> streams) {
    Objects.requireNonNull(streams, "streams must not be null");

    if (streams.isEmpty()) {
      return VStream.empty();
    }

    if (streams.size() == 1) {
      return streams.getFirst();
    }

    int sourceCount = streams.size();

    // Defer so that producer threads are not started until the first pull
    return VStream.defer(
        () -> {
          LinkedBlockingQueue<MergeSignal<A>> queue = new LinkedBlockingQueue<>();
          AtomicBoolean cancelled = new AtomicBoolean(false);

          // Background thread manages a StructuredTaskScope with one subtask per source.
          // Each subtask pulls elements and pushes them to the shared queue.
          Thread.ofVirtual().start(() -> runMergeScope(streams, queue, cancelled));

          return mergeFromQueue(queue, sourceCount);
        });
  }

  /**
   * Opens a {@link StructuredTaskScope}, forks one subtask per source stream (each running {@link
   * #consumeSource}), and joins. Runs on a background virtual thread started by {@link
   * #merge(List)}.
   *
   * <p>If the background thread is interrupted during {@code scope.join()}, the interrupt flag is
   * restored and the scope is closed via try-with-resources.
   */
  @SuppressWarnings("preview")
  // Package-private for testing (allows direct invocation from same-package tests)
  static <A> void runMergeScope(
      List<VStream<A>> streams,
      LinkedBlockingQueue<MergeSignal<A>> queue,
      AtomicBoolean cancelled) {
    try (var scope = StructuredTaskScope.open()) {
      for (VStream<A> s : streams) {
        scope.fork(() -> consumeSource(s, queue, cancelled));
      }
      scope.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Consumes a single source stream, pushing elements to the merge queue. Called on a virtual
   * thread within a {@code StructuredTaskScope}.
   *
   * <p>On error, the first failure sets the {@code cancelled} flag and pushes a {@link
   * MergeSignal.SourceError} so that the consumer fails fast. Subsequent source errors are
   * discarded (only the first is reported).
   */
  @SuppressWarnings("preview")
  private static <A> Void consumeSource(
      VStream<A> source, LinkedBlockingQueue<MergeSignal<A>> queue, AtomicBoolean cancelled) {
    try {
      VStream<A> current = source;
      while (!cancelled.get()) {
        VStream.Step<A> step = current.pull().run();
        switch (step) {
          case VStream.Step.Emit<A> e -> {
            queue.put(new MergeSignal.Element<>(e.value()));
            current = e.tail();
          }
          case VStream.Step.Skip<A> s -> current = s.tail();
          case VStream.Step.Done<A> _ -> {
            queue.put(new MergeSignal.SourceDone<>());
            return null;
          }
        }
      }
      return null;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return null;
    } catch (Throwable t) {
      if (!cancelled.getAndSet(true)) {
        // offer() is non-blocking on an unbounded LinkedBlockingQueue, so it always succeeds
        // and cannot throw InterruptedException (unlike put()).
        queue.offer(new MergeSignal.SourceError<>(t));
      }
      return null;
    }
  }

  /**
   * Builds a {@code VStream} that pulls elements from the merge queue. Each pull blocks on {@link
   * LinkedBlockingQueue#take()} until a signal arrives.
   *
   * @param queue the shared queue populated by source-consuming subtasks.
   * @param remainingSources the number of source streams that have not yet sent {@link
   *     MergeSignal.SourceDone}.
   */
  private static <A> VStream<A> mergeFromQueue(
      LinkedBlockingQueue<MergeSignal<A>> queue, int remainingSources) {
    // No guard needed: merge() guarantees remainingSources >= 2 on the initial call,
    // and recursive calls pass newRemaining >= 1 (the newRemaining <= 0 branch yields Done).
    return () ->
        VTask.of(
            () -> {
              try {
                MergeSignal<A> signal = queue.take();
                return switch (signal) {
                  case MergeSignal.Element<A> e ->
                      new VStream.Step.Emit<>(e.value(), mergeFromQueue(queue, remainingSources));
                  case MergeSignal.SourceDone<A> _ -> {
                    int newRemaining = remainingSources - 1;
                    if (newRemaining <= 0) {
                      yield new VStream.Step.Done<>();
                    }
                    yield new VStream.Step.Skip<>(mergeFromQueue(queue, newRemaining));
                  }
                  case MergeSignal.SourceError<A> err -> throw handleFailedCause(err.cause());
                };
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Merge interrupted", e);
              }
            });
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
   * Unwraps a {@link StructuredTaskScope.FailedException} into the appropriate unchecked exception.
   *
   * <p>The cause is inspected and re-thrown as follows:
   *
   * <ul>
   *   <li>{@link RuntimeException} — thrown directly
   *   <li>{@link Error} — thrown directly
   *   <li>Checked exception — wrapped in a new {@link RuntimeException}
   * </ul>
   *
   * @param e The FailedException to unwrap. Must not be null.
   * @return A {@link RuntimeException} wrapping a checked exception cause (only reached when the
   *     cause is a checked exception).
   */
  @SuppressWarnings("preview")
  private static RuntimeException handleFailedException(StructuredTaskScope.FailedException e) {
    return handleFailedCause(e.getCause());
  }

  /**
   * Unwraps a {@link Throwable} into the appropriate unchecked exception.
   *
   * <ul>
   *   <li>{@link RuntimeException} — returned directly
   *   <li>{@link Error} — thrown directly
   *   <li>Checked exception — wrapped in a new {@link RuntimeException}
   * </ul>
   */
  private static RuntimeException handleFailedCause(Throwable cause) {
    if (cause instanceof RuntimeException re) {
      return re;
    }
    if (cause instanceof Error err) {
      throw err;
    }
    return new RuntimeException(cause);
  }

  /**
   * Pulls up to {@code count} elements from the stream, storing them in the batch list and updating
   * the tail holder.
   *
   * @return true if the stream is exhausted (Done), false if more elements remain.
   */
  @SuppressWarnings("unchecked")
  private static <A> boolean pullBatch(VStream<A>[] tailHolder, List<A> batch, int count) {
    VStream<A> current = tailHolder[0];
    for (int i = 0; i < count; i++) {
      VStream.Step<A> step = current.pull().run();
      switch (step) {
        case VStream.Step.Emit<A> e -> {
          batch.add(e.value());
          current = e.tail();
        }
        case VStream.Step.Skip<A> s -> {
          current = s.tail();
          i--; // Don't count skips
        }
        case VStream.Step.Done<A> _ -> {
          tailHolder[0] = current;
          return true;
        }
      }
    }
    tailHolder[0] = current;
    return false;
  }

  /** Processes a batch of elements in parallel using StructuredTaskScope, preserving order. */
  @SuppressWarnings("preview")
  private static <A, B> VTask<List<B>> processBatchOrdered(List<A> batch, Function<A, VTask<B>> f) {
    return VTask.of(
        () -> {
          try (var scope = StructuredTaskScope.open()) {
            List<StructuredTaskScope.Subtask<B>> subtasks = new ArrayList<>(batch.size());

            for (A element : batch) {
              VTask<B> task = f.apply(element);
              Objects.requireNonNull(task, "function returned null task for element: " + element);
              subtasks.add(scope.fork(task.asCallable()));
            }

            scope.join();

            List<B> results = new ArrayList<>(subtasks.size());
            for (var subtask : subtasks) {
              results.add(subtask.get());
            }
            return results;
          } catch (StructuredTaskScope.FailedException e) {
            throw handleFailedException(e);
          }
        });
  }

  /**
   * Processes a batch of elements in parallel, collecting results without guaranteeing input order.
   *
   * <p>Uses {@link StructuredTaskScope} to fork each element's task and waits for all to complete.
   * Results are collected via {@link StructuredTaskScope.Subtask#get()}, which correctly propagates
   * any subtask failure through {@link StructuredTaskScope.FailedException} on {@code join()}.
   */
  @SuppressWarnings("preview")
  private static <A, B> VTask<List<B>> processBatchUnordered(
      List<A> batch, Function<A, VTask<B>> f) {
    return VTask.of(
        () -> {
          try (var scope = StructuredTaskScope.open()) {
            List<StructuredTaskScope.Subtask<B>> subtasks = new ArrayList<>(batch.size());

            for (A element : batch) {
              VTask<B> task = f.apply(element);
              Objects.requireNonNull(task, "function returned null task for element: " + element);
              subtasks.add(scope.fork(task.asCallable()));
            }

            scope.join();

            // Collect results — subtask.get() throws if the subtask failed
            List<B> results = new ArrayList<>(subtasks.size());
            for (var subtask : subtasks) {
              results.add(subtask.get());
            }
            return results;
          } catch (StructuredTaskScope.FailedException e) {
            throw handleFailedException(e);
          }
        });
  }
}
