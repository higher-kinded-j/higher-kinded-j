// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for {@link Id}'s instance-level {@code flatMap}.
 *
 * <p>{@code Id} is otherwise a trivial record whose factory ({@code of}), accessor ({@code value})
 * and {@code map} are exercised through the type-class tests ({@code IdMonadTest}, {@code
 * IdKindHelperTest}, {@code IdTraverseTest}); only the instance {@code flatMap} — with its
 * null-function and null-result guards — needs direct coverage.
 */
@DisplayName("Id instance methods")
class IdTest {

  @Test
  @DisplayName("flatMap applies the function and unwraps the resulting Id")
  void flatMapAppliesAndUnwraps() {
    Id<Integer> id = Id.of(21);
    Id<String> result = id.flatMap(i -> Id.of("v" + (i * 2)));
    assertThat(result.value()).isEqualTo("v42");
  }

  @Test
  @DisplayName("flatMap rejects a null function")
  void flatMapRejectsNullFunction() {
    Function<Integer, Id<String>> nullFn = null;
    assertThatThrownBy(() -> Id.of(1).flatMap(nullFn)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("flatMap rejects a function that returns a null Id")
  void flatMapRejectsNullResult() {
    Function<Integer, Id<String>> nullReturning = i -> null;
    assertThatThrownBy(() -> Id.of(1).flatMap(nullReturning))
        .isInstanceOf(KindUnwrapException.class)
        .hasMessageContaining("returned null");
  }
}
