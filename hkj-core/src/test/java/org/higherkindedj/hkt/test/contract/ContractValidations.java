// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * Shared, production-aligned validation assertions for type-class contracts.
 *
 * <p>Rather than hard-coding expected exception types and messages, each assertion re-derives the
 * expectation from the production validator under {@code org.higherkindedj.hkt.util.validation}, so
 * test expectations track production behaviour exactly (single source of truth — no drift).
 */
final class ContractValidations {

  private ContractValidations() {}

  /**
   * Asserts that {@code underTest} rejects its (null) argument with the exact same exception type
   * and message as the production validator {@code production}.
   *
   * @param production invokes the production validator with the same null argument
   * @param underTest invokes the operation under test with that null argument
   */
  static void assertRejectsLikeProduction(ThrowingCallable production, ThrowingCallable underTest) {
    Throwable expected = catchThrowable(production);
    assertThat(expected)
        .as("production validator must itself reject the null argument")
        .isNotNull();
    assertThatThrownBy(underTest)
        .isInstanceOf(expected.getClass())
        .hasMessage(expected.getMessage());
  }

  /**
   * Asserts that {@code operation} rejects a null argument with a {@link NullPointerException}.
   *
   * <p>Type-only (no message assertion): used for multi-argument operations like {@code ap} and
   * {@code map2} whose rejection <em>message</em> legitimately differs between implementations
   * (e.g. the {@code Applicative.map2} default uses {@code Objects.requireNonNull} literals, while
   * overriding implementations route through {@code util.validation}). The exception type is the
   * stable cross-implementation contract; the exact wording is not.
   */
  static void rejectsNull(ThrowingCallable operation) {
    assertThatThrownBy(operation).isInstanceOf(NullPointerException.class);
  }
}
