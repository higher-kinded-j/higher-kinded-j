// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * A stream built afresh each time its pulled {@code VTask} runs. {@link VStream#defer}, the
 * operators that keep state between pulls, and the streams whose runs each start something of their
 * own build on it. It keeps none of the streams it builds, so a recursive definition holds nothing
 * it has already read, and a run that closes it leaves every other run alone, since several runs
 * may read it at once. The stream a run built is closed through the tail its consumer holds.
 *
 * <p>One class for both factories, since a loop that pulls a kind of stream it has not met before
 * becomes slower to call.
 *
 * @param <A> The element type.
 */
final class DeferredStream<A> implements VStream<A> {

  private final VTask<VStream<A>> build;
  private final VStream<?>[] inputs;
  // Only for opening: set as a run starts, after which each run closes what it reads, so closing
  // this closes nothing
  private final @Nullable AtomicBoolean started;

  private DeferredStream(
      VTask<VStream<A>> build, @Nullable AtomicBoolean started, VStream<?>... inputs) {
    this.build = build;
    this.started = started;
    this.inputs = inputs;
  }

  /**
   * A stream the task builds afresh each time its pulled {@code VTask} runs, reading from the given
   * inputs, which closing it closes. Operators that keep state between pulls build on this, so each
   * run has its own state. A failure of the task fails the pull as the task threw it.
   */
  static <A> VStream<A> deferring(VTask<VStream<A>> build, VStream<?>... inputs) {
    return new DeferredStream<>(build, null, inputs);
  }

  /**
   * As {@link #deferring}, for a stream each of whose runs starts something of its own that reads
   * the inputs: {@link VStreamPar#merge}'s producer, {@link VStreamReactive#fromPublisher}'s
   * subscription or {@link VStreamPar#parEvalMapUnordered}'s window. Only the run's own tails reach
   * what it starts, so a run whose first pull fails closes what it built before failing, and a run
   * that stops part-way closes the tail it holds. Each run closes the inputs it reads, so closing
   * this closes them only until a run starts.
   */
  static <A> VStream<A> opening(VTask<VStream<A>> build, VStream<?>... inputs) {
    return new DeferredStream<>(build, new AtomicBoolean(), inputs);
  }

  @Override
  public VTask<Step<A>> pull() {
    AtomicBoolean runs = started;
    if (runs == null) {
      return build.flatMap(VStream::pull);
    }
    return () -> {
      runs.set(true);
      VStream<A> built = build.execute();
      try {
        return built.pull().execute();
      } catch (Throwable t) {
        // No tail of this run reaches anyone, so nothing else would close what it started
        Closing.closeAfterFailure(built, t);
        throw t;
      }
    };
  }

  @Override
  public VTask<Unit> close() {
    AtomicBoolean runs = started;
    if (runs == null) {
      return Closing.closeAll(inputs);
    }
    return () -> runs.get() ? Unit.INSTANCE : Closing.closeAll(inputs).execute();
  }
}
