// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.effect.capability.Effectful;

/**
 * Examples demonstrating the polymorphic {@link Effectful} capability surface. {@code handleError},
 * {@code handleErrorWith}, and {@code guarantee} are all declared at the interface level, so
 * application code can write helpers against {@code Effectful<A>} and have them work unchanged for
 * both {@link IOPath} and {@link VTaskPath}.
 */
public class EffectfulPolymorphismExample {

  public static void main(String[] args) {
    EffectfulPolymorphismExample example = new EffectfulPolymorphismExample();
    example.polymorphicHelperWorksForBothImplementations();
    example.handleErrorWithAcceptsCrossImplementationRecovery();
    example.guaranteeRunsFinalizerWhetherEffectSucceedsOrFails();
  }

  /**
   * Shared helper: written against the abstract {@code Effectful<A>} type, not any specific
   * implementation. Reused across both IOPath and VTaskPath call sites.
   */
  private static <A> Effectful<A> safeOrDefault(Effectful<A> effect, A fallback) {
    return effect.handleError(t -> fallback);
  }

  public void polymorphicHelperWorksForBothImplementations() {
    // An IO-backed effect that always fails.
    Effectful<String> failingIo =
        Path.io(
            () -> {
              throw new RuntimeException("io boom");
            });

    // A VTask-backed effect that always fails.
    Effectful<String> failingVtask =
        Path.vtask(
            () -> {
              throw new RuntimeException("vtask boom");
            });

    // One helper, two effect types, same recovery behaviour.
    Effectful<String> recoveredIo = safeOrDefault(failingIo, "io-default");
    Effectful<String> recoveredVtask = safeOrDefault(failingVtask, "vtask-default");

    require(recoveredIo instanceof IOPath, "recovered IO effect stays an IOPath");
    require(recoveredVtask instanceof VTaskPath, "recovered VTask effect stays a VTaskPath");

    require(recoveredIo.unsafeRun().equals("io-default"), "IO recovers to fallback");
    require(recoveredVtask.unsafeRun().equals("vtask-default"), "VTask recovers to fallback");

    System.out.println("polymorphicHelperWorksForBothImplementations: ok");
  }

  /**
   * {@code handleErrorWith} takes {@code Function<? super Throwable, ? extends Effectful<A>>}, so
   * the recovery function can return either an IOPath or a VTaskPath even when the receiver is the
   * other kind. The receiver's concrete type is preserved.
   */
  public void handleErrorWithAcceptsCrossImplementationRecovery() {
    // IOPath recovering via a VTaskPath fallback.
    Effectful<String> io =
        Path.io(
            () -> {
              throw new RuntimeException("io failed");
            });
    Effectful<String> recoveredAsIo = io.handleErrorWith(t -> Path.vtask(() -> "from-vtask"));
    require(recoveredAsIo instanceof IOPath, "receiver type preserved (IOPath)");
    require(recoveredAsIo.unsafeRun().equals("from-vtask"), "fallback ran and returned its value");

    // VTaskPath recovering via an IOPath fallback.
    Effectful<String> vtask =
        Path.vtask(
            () -> {
              throw new RuntimeException("vtask failed");
            });
    Effectful<String> recoveredAsVtask = vtask.handleErrorWith(t -> Path.io(() -> "from-io"));
    require(recoveredAsVtask instanceof VTaskPath, "receiver type preserved (VTaskPath)");
    require(recoveredAsVtask.unsafeRun().equals("from-io"), "fallback ran and returned its value");

    System.out.println("handleErrorWithAcceptsCrossImplementationRecovery: ok");
  }

  /** {@code guarantee} runs the finalizer whether the effect succeeds or fails. */
  public void guaranteeRunsFinalizerWhetherEffectSucceedsOrFails() {
    // Success path.
    AtomicBoolean successFinalizerRan = new AtomicBoolean(false);
    Effectful<String> ok = Path.io(() -> "ok").guarantee(() -> successFinalizerRan.set(true));
    require(ok.unsafeRun().equals("ok"), "effect value returned");
    require(successFinalizerRan.get(), "finalizer ran after success");

    // Failure path.
    AtomicBoolean failureFinalizerRan = new AtomicBoolean(false);
    // Explicit <String> type witness: the throw-only lambda gives Java no
    // argument information to infer A, and target-type inference does not
    // propagate through the chained .guarantee() call.
    Effectful<String> fails =
        Path.<String>io(
                () -> {
                  throw new RuntimeException("boom");
                })
            .guarantee(() -> failureFinalizerRan.set(true));
    try {
      fails.unsafeRun();
      throw new AssertionError("expected RuntimeException");
    } catch (RuntimeException expected) {
      // Finalizer must still have run.
      require(failureFinalizerRan.get(), "finalizer ran after failure");
    }

    System.out.println("guaranteeRunsFinalizerWhetherEffectSucceedsOrFails: ok");
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
