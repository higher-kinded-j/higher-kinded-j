// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import org.higherkindedj.hkt.vtask.VTaskExecutionException;

/**
 * Keeps an assertion from leaving the test thread interrupted. Running a subject that fails with an
 * {@link InterruptedException} sets the thread's interrupt status again, as code that catches one
 * should, so a later blocking call in the test would throw at once. An assertion clears the status
 * after such a failure, unless the thread was interrupted before the run.
 */
final class Interrupts {

  private Interrupts() {}

  /**
   * Clears the thread's interrupt status after a cancellation, unless it was set before the run.
   *
   * @param interruptedBefore whether the thread was interrupted before the subject ran
   * @param failure the failure the run reported
   */
  static void clearAfterCancellation(boolean interruptedBefore, Throwable failure) {
    if (!interruptedBefore && isCancellation(failure)) {
      Thread.interrupted();
    }
  }

  private static boolean isCancellation(Throwable failure) {
    return failure instanceof InterruptedException
        || (failure instanceof VTaskExecutionException wrapped
            && wrapped.getCause() instanceof InterruptedException);
  }
}
