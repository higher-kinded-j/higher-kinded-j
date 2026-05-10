// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.FreeAssert.assertThatFree;

import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.FreeAssert}. */
@DisplayName("FreeAssert showcase")
class FreeAssertExample {

  @Test
  @DisplayName("isPure() and hasPureValue() inspect a pure Free program")
  void pureProgram() {
    Free<MaybeKind.Witness, String> program = Free.pure("hello");

    assertThatFree(program).isPure().hasPureValue("hello");
  }

  @Test
  @DisplayName("isFlatMapped() detects a Free.FlatMap node in the program tree")
  void flatMappedProgram() {
    Free<MaybeKind.Witness, Integer> program =
        Free.<MaybeKind.Witness, Integer>pure(1).flatMap(i -> Free.pure(i + 1));

    assertThatFree(program).isFlatMapped();
  }
}
