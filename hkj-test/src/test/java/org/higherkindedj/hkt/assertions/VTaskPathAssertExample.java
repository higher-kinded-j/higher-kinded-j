// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.VTaskPathAssert.assertThatVTaskPath;

import java.time.Duration;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link VTaskPathAssert}. */
@DisplayName("VTaskPathAssert showcase")
class VTaskPathAssertExample {

  @Test
  @DisplayName("succeeds().hasValue() executes a pure path and checks the result")
  void successfulPath() {
    VTaskPath<Integer> path = Path.vtaskPure(42);
    assertThatVTaskPath(path).succeeds().hasValue(42);
  }

  @Test
  @DisplayName("fails().withExceptionType() checks the failure mode")
  void failingPath() {
    VTaskPath<Integer> path = Path.vtaskFail(new IllegalArgumentException("bad input"));
    assertThatVTaskPath(path)
        .fails()
        .withExceptionType(IllegalArgumentException.class)
        .withExceptionMessageContaining("bad input");
  }

  @Test
  @DisplayName("completesWithin() bounds the run-time of the underlying VTask")
  void timing() {
    VTaskPath<Integer> path = Path.vtaskPure(1);
    assertThatVTaskPath(path).completesWithin(Duration.ofSeconds(1));
  }
}
