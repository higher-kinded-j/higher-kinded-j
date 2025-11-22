// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Operation")
class OperationTest {

  @Test
  @DisplayName("Each operation should have correct toString() label in camelCase format")
  void eachOperationShouldHaveCorrectLabel() {
    // Verify specific operations have their expected camelCase labels
    assertThat(AP.toString()).isEqualTo("ap");
    assertThat(MAP.toString()).isEqualTo("map");
    assertThat(MAP_2.toString()).isEqualTo("map2");
    assertThat(FLAT_MAP.toString()).isEqualTo("flatMap");
    assertThat(FOLD_MAP.toString()).isEqualTo("foldMap");
    assertThat(HANDLE_ERROR_WITH.toString()).isEqualTo("handleErrorWith");
    assertThat(FROM_KIND.toString()).isEqualTo("fromKind");
    assertThat(RUN_STATE.toString()).isEqualTo("runState");
    assertThat(OR_ELSE_GET.toString()).isEqualTo("orElseGet");
    assertThat(IF_VALID.toString()).isEqualTo("ifValid");

    // Verify all operations have non-null, non-empty labels
    for (var operation : Operation.values()) {
      assertThat(operation.toString()).isNotNull().isNotEmpty();
    }
  }

  @Test
  @DisplayName("should have all expected operations")
  void shouldHaveAllExpectedOperations() {
    var operations = Operation.values();

    assertThat(operations).hasSizeGreaterThanOrEqualTo(50);
    assertThat(operations)
        .contains(
            AP,
            MAP,
            MAP_2,
            MAP_3,
            MAP_4,
            MAP_5,
            FLAT_MAP,
            FOLD_MAP,
            TRAVERSE,
            HANDLE_ERROR_WITH,
            RECOVER_WITH,
            RECOVER,
            RAISE_ERROR,
            CONSTRUCTION,
            LEFT,
            RIGHT,
            FROM_KIND,
            FROM_EITHER,
            FROM_MAYBE,
            FROM_OPTIONAL,
            FROM_LIST,
            FROM_OPTIONAL_LIST,
            LIFT_F,
            OF,
            ASK,
            SET,
            MODIFY,
            INSPECT,
            RUN_STATE,
            STATE_T,
            RUN_STATE_T,
            EVAL_STATE_T,
            EXEC_STATE_T,
            JUST,
            DEFER,
            SOME,
            NONE,
            READER,
            RUN_READER,
            FOLD,
            TO_EITHER,
            MATCH,
            OR_ELSE_GET,
            OR_EITHER,
            RECOVER_FUNCTION,
            VALUE,
            TELL,
            IF_LEFT,
            IF_RIGHT,
            SEQUENCE_A,
            SEQUENCE_B,
            IF_VALID,
            IF_INVALID,
            OR_ELSE_THROW,
            DELAY,
            INVALID);
  }

  @Test
  @DisplayName("each operation should have a non-null label")
  void eachOperationShouldHaveNonNullLabel() {
    for (var operation : Operation.values()) {
      assertThat(operation.toString()).isNotNull();
      assertThat(operation.toString()).isNotEmpty();
    }
  }
}
