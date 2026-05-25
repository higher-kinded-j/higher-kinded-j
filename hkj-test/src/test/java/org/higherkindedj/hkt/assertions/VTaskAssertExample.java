// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.time.Duration;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.VTaskAssert}. */
@DisplayName("VTaskAssert showcase")
class VTaskAssertExample {

  @Test
  @DisplayName("whenRun() executes the task and succeeds()/hasValue() check the result")
  void successfulTask() {
    VTask<Integer> task = VTask.delay(() -> 1 + 2);

    assertThatVTask(task).whenRun().succeeds().hasValue(3);
  }

  @Test
  @DisplayName("fails() and withExceptionType() check failure modes")
  void failingTask() {
    VTask<Integer> task = VTask.fail(new IllegalArgumentException("bad input"));

    assertThatVTask(task)
        .whenRun()
        .fails()
        .withExceptionType(IllegalArgumentException.class)
        .withMessageContaining("bad input");
  }

  @Test
  @DisplayName("completesWithin() bounds the run-time of the task")
  void timing() {
    VTask<String> task = VTask.delay(() -> "fast");

    assertThatVTask(task).completesWithin(Duration.ofSeconds(1));
  }

  @Test
  @DisplayName("runSafeSucceeds() exercises VTask.runSafe() which yields a Try")
  void runSafe() {
    VTask<String> task = VTask.delay(() -> "ok");

    assertThatVTask(task).runSafeSucceeds();
  }

  @Test
  @DisplayName("Accepts Kind<VTaskKind.Witness, T> directly without manual narrowing")
  void acceptsKindDirectly() {
    Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(VTask.delay(() -> 99));

    assertThatVTask(kind).whenRun().succeeds().hasValue(99);
  }
}
