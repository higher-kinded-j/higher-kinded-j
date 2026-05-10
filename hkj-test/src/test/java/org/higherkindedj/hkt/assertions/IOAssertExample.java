// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.IOAssert.assertThatIO;

import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.io.IO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.IOAssert}. */
@DisplayName("IOAssert showcase")
class IOAssertExample {

  @Test
  @DisplayName("whenExecuted() forces the IO before checking the value")
  void executeAndCheckValue() {
    IO<Integer> effect = IO.delay(() -> 1 + 1);

    assertThatIO(effect).whenExecuted().hasValue(2);
  }

  @Test
  @DisplayName("isNotExecutedYet() proves the IO is lazy until you say so")
  void laziness() {
    AtomicInteger sideEffect = new AtomicInteger();
    IO<Integer> effect = IO.delay(sideEffect::incrementAndGet);

    assertThatIO(effect).isNotExecutedYet();
  }

  @Test
  @DisplayName("isRepeatable() runs the IO twice and checks both yield the same value")
  void repeatable() {
    IO<String> effect = IO.delay(() -> "constant");

    assertThatIO(effect).isRepeatable();
  }

  @Test
  @DisplayName("throwsException() asserts the exception thrown when the IO is run")
  void throwsExpected() {
    IO<String> effect =
        IO.delay(
            () -> {
              throw new IllegalStateException("kaboom");
            });

    assertThatIO(effect)
        .throwsException(IllegalStateException.class)
        .withMessageContaining("kaboom");
  }
}
