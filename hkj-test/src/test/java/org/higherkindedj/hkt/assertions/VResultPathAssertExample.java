// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.VResultPathAssert.assertThatVResultPath;

import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link VResultPathAssert}. */
@DisplayName("VResultPathAssert showcase")
class VResultPathAssertExample {

  @Test
  @DisplayName("isRight().hasRight() executes the path and checks the typed success")
  void typedSuccess() {
    VResultPath<String, Integer> path = Path.<String, Integer>vresultRight(42).map(v -> v + 1);
    assertThatVResultPath(path).isRight().hasRight(43);
  }

  @Test
  @DisplayName("isLeft().hasLeftSatisfying() checks the typed domain error")
  void typedError() {
    VResultPath<String, Integer> path =
        Path.<String, Integer>vresultLeft("not found").mapError(String::toUpperCase);
    assertThatVResultPath(path)
        .isLeft()
        .hasLeft("NOT FOUND")
        .hasLeftSatisfying(e -> org.assertj.core.api.Assertions.assertThat(e).contains("FOUND"));
  }

  @Test
  @DisplayName("hasDefect().withDefectType() distinguishes defects from typed errors")
  void defect() {
    VResultPath<String, Integer> path =
        Path.vresult(
            VTask.of(
                () -> {
                  throw new IllegalStateException("bug, not a domain error");
                }));
    assertThatVResultPath(path)
        .hasDefect()
        .withDefectType(IllegalStateException.class)
        .withDefectMessageContaining("bug");
  }
}
