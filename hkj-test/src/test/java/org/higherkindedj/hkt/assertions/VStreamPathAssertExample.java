// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.VStreamPathAssert.assertThatVStreamPath;

import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link VStreamPathAssert}. */
@DisplayName("VStreamPathAssert showcase")
class VStreamPathAssertExample {

  @Test
  @DisplayName("producesElements() materialises the stream and checks the elements in order")
  void producesElements() {
    VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);
    assertThatVStreamPath(path).producesElements(1, 2, 3).hasCount(3);
  }

  @Test
  @DisplayName("isEmpty() asserts an empty VStreamPath")
  void emptyStream() {
    VStreamPath<String> path = Path.vstreamEmpty();
    assertThatVStreamPath(path).isEmpty();
  }

  @Test
  @DisplayName("satisfies() exposes the materialised list to a delegated AssertJ block")
  void satisfiesDelegate() {
    VStreamPath<Integer> path = Path.vstreamOf(2, 4, 6);
    assertThatVStreamPath(path)
        .satisfies(elements -> assertThat(elements).allMatch(n -> n % 2 == 0));
  }
}
