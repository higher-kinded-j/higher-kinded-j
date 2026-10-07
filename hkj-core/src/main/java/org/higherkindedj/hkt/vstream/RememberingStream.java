// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * A stream built afresh each time its pulled {@code VTask} runs, which remembers the stream its
 * latest run built, so closing it after a pull closes that run. {@link VStream#bracket} builds on
 * this, so closing its head after a pull releases what that run acquired, and so does {@link
 * VStreamPar#merge}, so closing its head stops the run's producer. Before any run, or after a run
 * whose build failed, closing it closes the streams it reads from.
 *
 * <p>Each run's stream is the run itself, not a step of a recursion; a recursive {@link
 * VStream#defer} that remembered its runs would keep everything already read reachable from its
 * head, which is why it does not. A recursion inside a {@code bracket}'s {@code use} function
 * chains its runs in the same way, so recurse outside the bracket instead.
 *
 * @param <A> The element type.
 */
final class RememberingStream<A> implements VStream<A> {

  private final VTask<VStream<A>> build;
  private final VStream<?>[] inputs;
  private final AtomicReference<@Nullable VStream<A>> latest = new AtomicReference<>();

  RememberingStream(VTask<VStream<A>> build, VStream<?>... inputs) {
    this.build = build;
    this.inputs = inputs;
  }

  @Override
  public VTask<Step<A>> pull() {
    return () -> {
      // A run whose build fails leaves nothing of an earlier run for closing to reach
      latest.set(null);
      VStream<A> built = build.execute();
      latest.set(built);
      return built.pull().execute();
    };
  }

  @Override
  public VTask<Unit> close() {
    return () -> {
      VStream<A> built = latest.get();
      return (built == null ? Closing.closeAll(inputs) : built.close()).execute();
    };
  }
}
