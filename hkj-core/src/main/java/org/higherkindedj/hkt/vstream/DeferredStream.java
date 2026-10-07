// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * A stream built afresh each time its pulled {@code VTask} runs, as {@link VStream#defer} and the
 * operators that keep state between pulls build theirs. It keeps none of the streams it builds, so
 * a recursive definition holds nothing it has already read. Closed, it closes the streams it reads
 * from; the stream a run built is closed through the tail its consumer holds.
 *
 * @param <A> The element type.
 */
final class DeferredStream<A> implements VStream<A> {

  private final VTask<VStream<A>> build;
  private final VStream<?>[] inputs;

  private DeferredStream(VTask<VStream<A>> build, VStream<?>... inputs) {
    this.build = build;
    this.inputs = inputs;
  }

  /**
   * A stream the supplier builds afresh each time its pulled {@code VTask} runs, reading from the
   * given inputs, which closing it closes. Operators that keep state between pulls build on this,
   * so each run has its own state.
   */
  static <A> VStream<A> deferring(Supplier<VStream<A>> supplier, VStream<?>... inputs) {
    return new DeferredStream<>(VTask.delay(supplier), inputs);
  }

  @Override
  public VTask<Step<A>> pull() {
    return build.flatMap(VStream::pull);
  }

  @Override
  public VTask<Unit> close() {
    return Closing.closeAll(inputs);
  }
}
