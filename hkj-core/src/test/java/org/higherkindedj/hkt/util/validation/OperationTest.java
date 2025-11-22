// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Operation")
class OperationTest {

  @ParameterizedTest(name = "{0} should have label \"{1}\"")
  @MethodSource("operationLabels")
  @DisplayName("Each operation should have correct toString() label in camelCase format")
  void eachOperationShouldHaveCorrectLabel(Operation operation, String expectedLabel) {
    assertThat(operation.toString()).isEqualTo(expectedLabel);
  }

  private static Stream<Arguments> operationLabels() {
    return Stream.of(
        Arguments.of(AP, "ap"),
        Arguments.of(MAP, "map"),
        Arguments.of(MAP_2, "map2"),
        Arguments.of(MAP_3, "map3"),
        Arguments.of(MAP_4, "map4"),
        Arguments.of(MAP_5, "map5"),
        Arguments.of(FLAT_MAP, "flatMap"),
        Arguments.of(FOLD_MAP, "foldMap"),
        Arguments.of(TRAVERSE, "traverse"),
        Arguments.of(HANDLE_ERROR_WITH, "handleErrorWith"),
        Arguments.of(RECOVER_WITH, "recoverWith"),
        Arguments.of(RECOVER, "recover"),
        Arguments.of(RAISE_ERROR, "raiseError"),
        Arguments.of(CONSTRUCTION, "construction"),
        Arguments.of(LEFT, "left"),
        Arguments.of(RIGHT, "right"),
        Arguments.of(FROM_KIND, "fromKind"),
        Arguments.of(FROM_EITHER, "fromEither"),
        Arguments.of(FROM_MAYBE, "fromMaybe"),
        Arguments.of(FROM_OPTIONAL, "fromOptional"),
        Arguments.of(FROM_LIST, "fromList"),
        Arguments.of(FROM_OPTIONAL_LIST, "fromOptionalList"),
        Arguments.of(LIFT_F, "liftF"),
        Arguments.of(OF, "of"),
        Arguments.of(ASK, "ask"),
        Arguments.of(SET, "set"),
        Arguments.of(MODIFY, "modify"),
        Arguments.of(INSPECT, "inspect"),
        Arguments.of(RUN_STATE, "runState"),
        Arguments.of(STATE_T, "stateT"),
        Arguments.of(RUN_STATE_T, "runStateT"),
        Arguments.of(EVAL_STATE_T, "evalStateT"),
        Arguments.of(EXEC_STATE_T, "execStateT"),
        Arguments.of(JUST, "just"),
        Arguments.of(DEFER, "defer"),
        Arguments.of(SOME, "some"),
        Arguments.of(NONE, "none"),
        Arguments.of(READER, "reader"),
        Arguments.of(RUN_READER, "runReader"),
        Arguments.of(FOLD, "fold"),
        Arguments.of(TO_EITHER, "toEither"),
        Arguments.of(MATCH, "match"),
        Arguments.of(OR_ELSE, "orElse"),
        Arguments.of(OR_ELSE_GET, "orElseGet"),
        Arguments.of(OR_EITHER, "orEither"),
        Arguments.of(RECOVER_FUNCTION, "recoverFunction"),
        Arguments.of(VALUE, "value"),
        Arguments.of(TELL, "tell"),
        Arguments.of(IF_LEFT, "ifLeft"),
        Arguments.of(IF_RIGHT, "ifRight"),
        Arguments.of(SEQUENCE_A, "sequenceA"),
        Arguments.of(SEQUENCE_B, "sequenceB"),
        Arguments.of(IF_VALID, "ifValid"),
        Arguments.of(IF_INVALID, "ifInvalid"),
        Arguments.of(OR_ELSE_THROW, "orElseThrow"),
        Arguments.of(DELAY, "delay"),
        Arguments.of(INVALID, "invalid"),
        Arguments.of(IF_S, "ifS"),
        Arguments.of(SELECT, "select"),
        Arguments.of(WHEN_S, "whenS"),
        Arguments.of(BRANCH, "branch"),
        Arguments.of(BIMAP, "bimap"),
        Arguments.of(FIRST, "first"),
        Arguments.of(SECOND, "second"),
        Arguments.of(MAP_LEFT, "mapLeft"),
        Arguments.of(MAP_RIGHT, "mapRight"),
        Arguments.of(MAP_FIRST, "mapFirst"),
        Arguments.of(MAP_SECOND, "mapSecond"),
        Arguments.of(MAP_THIRD, "mapThird"),
        Arguments.of(MAP_FOURTH, "mapFourth"),
        Arguments.of(MAP_FIFTH, "mapFifth"),
        Arguments.of(MAP_ERROR, "mapError"),
        Arguments.of(MAP_WRITTEN, "mapWritten"));
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
}
