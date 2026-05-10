// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.VTaskContextAssert.assertThatVTaskContext;

import java.time.Duration;
import org.higherkindedj.hkt.effect.context.VTaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link VTaskContextAssert}. */
@DisplayName("VTaskContextAssert showcase")
class VTaskContextAssertExample {

  @Test
  @DisplayName("succeeds().hasValue() executes the context and checks the result")
  void successfulContext() {
    VTaskContext<Integer> ctx = VTaskContext.pure(42);
    assertThatVTaskContext(ctx).succeeds().hasValue(42);
  }

  @Test
  @DisplayName("fails().withExceptionType() checks the failure mode")
  void failingContext() {
    VTaskContext<Integer> ctx = VTaskContext.fail(new IllegalArgumentException("bad input"));
    assertThatVTaskContext(ctx)
        .fails()
        .withExceptionType(IllegalArgumentException.class)
        .withExceptionMessageContaining("bad input");
  }

  @Test
  @DisplayName("completesWithin() bounds the run-time of the context")
  void timing() {
    VTaskContext<Integer> ctx = VTaskContext.pure(1);
    assertThatVTaskContext(ctx).completesWithin(Duration.ofSeconds(1));
  }
}
