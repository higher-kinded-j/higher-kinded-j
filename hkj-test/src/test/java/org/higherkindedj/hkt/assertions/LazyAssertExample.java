// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.LazyAssert.assertThatLazy;

import org.higherkindedj.hkt.lazy.Lazy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.LazyAssert}. */
@DisplayName("LazyAssert showcase")
class LazyAssertExample {

  @Test
  @DisplayName("isNotEvaluated() asserts the thunk has not been forced")
  void notYetEvaluated() {
    Lazy<Integer> lazy = Lazy.defer(() -> 42);

    assertThatLazy(lazy).isNotEvaluated();
  }

  @Test
  @DisplayName("whenForcedHasValue() forces the lazy and asserts on the value")
  void forceAndCheck() throws Throwable {
    Lazy<String> lazy = Lazy.defer(() -> "computed");

    assertThatLazy(lazy).whenForcedHasValue("computed").isEvaluated();
  }

  @Test
  @DisplayName("whenForcedThrows() asserts the exception thrown when forced")
  void forceFails() {
    Lazy<Object> lazy =
        Lazy.defer(
            () -> {
              throw new IllegalStateException("not ready");
            });

    assertThatLazy(lazy).whenForcedThrows(IllegalStateException.class);
  }
}
