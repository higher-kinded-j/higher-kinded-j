// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.Objects;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * How the streams in this package run a task of their own inside a task they give, such as a pull
 * inside a terminal operation, or a close after one stops early. The task runs through {@link
 * VTask#execute()}, so a failure reaches the caller as the task threw it, a checked one included,
 * where {@link VTask#run()} would wrap a checked one in a {@code VTaskExecutionException}.
 */
final class Executing {

  private Executing() {}

  /**
   * Runs a task and gives its value, throwing the task's own failure. As {@link VTask#run()} does,
   * it refuses a null result, and sets the interrupt status again for an {@link
   * InterruptedException}.
   */
  static <A> A executed(VTask<A> task) throws Throwable {
    try {
      return Objects.requireNonNull(
          task.execute(), "the task returned null, and a VTask always gives a value");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw e;
    }
  }
}
