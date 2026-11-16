// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * The future {@link VTask#runAsync()} returns: a {@link CompletableFuture} whose {@code
 * cancel(true)} interrupts the thread running its task, as a future from an {@link
 * java.util.concurrent.ExecutorService} does. A plain {@code CompletableFuture} ignores {@code
 * mayInterruptIfRunning}.
 *
 * <p>The task runs inside a {@link FutureTask} on a virtual thread started for it alone and never
 * reused, and a task cancelled before it starts never runs. This future completes only once the
 * {@code FutureTask} has finished, so an interrupt can reach the task and nothing else, not even a
 * callback that thread runs for this future afterwards. Stages built on this future, such as the
 * one {@code thenApply} returns, are plain {@code CompletableFuture}s: cancelling one of them
 * leaves the task running.
 *
 * @param <A> the type of the task's result
 */
final class InterruptibleFuture<A> extends CompletableFuture<A> {

  private final FutureTask<A> running;

  private InterruptibleFuture(Supplier<? extends A> task) {
    this.running =
        new FutureTask<>(task::get) {
          @Override
          protected void done() {
            // A cancelled FutureTask leaves the outer future as the cancel left it. A failure is
            // the CompletionException the task threw, which completes the outer future just as
            // CompletableFuture.supplyAsync completes its own.
            if (this.state() == State.SUCCESS) {
              InterruptibleFuture.this.complete(this.resultNow());
            } else if (this.state() == State.FAILED) {
              InterruptibleFuture.this.completeExceptionally(this.exceptionNow());
            }
          }
        };
  }

  /**
   * Starts {@code task} on a new virtual thread.
   *
   * @param task gives the result, or throws a {@link CompletionException} holding the failure
   * @param <A> the type of the result
   * @return the future of the result, which {@code cancel(true)} interrupts
   */
  static <A> CompletableFuture<A> start(Supplier<? extends A> task) {
    InterruptibleFuture<A> future = new InterruptibleFuture<>(task);
    Thread.ofVirtual().start(future.running);
    return future;
  }

  /**
   * Cancels this future and, if {@code mayInterruptIfRunning} is true, interrupts the task's
   * thread. A task that catches the interrupt and carries on keeps running; this method does not
   * wait for it.
   *
   * @param mayInterruptIfRunning whether to interrupt the task if it is running
   * @return whether this future is now cancelled
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean cancelled = super.cancel(mayInterruptIfRunning);
    if (cancelled) {
      running.cancel(mayInterruptIfRunning);
    }
    return cancelled;
  }
}
