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
  @DisplayName("AP should have correct label")
  void apShouldHaveCorrectLabel() {
    assertThat(AP.toString()).isEqualTo("ap");
  }

  @Test
  @DisplayName("MAP should have correct label")
  void mapShouldHaveCorrectLabel() {
    assertThat(MAP.toString()).isEqualTo("map");
  }

  @Test
  @DisplayName("MAP_2 should have correct label")
  void map2ShouldHaveCorrectLabel() {
    assertThat(MAP_2.toString()).isEqualTo("map2");
  }

  @Test
  @DisplayName("MAP_3 should have correct label")
  void map3ShouldHaveCorrectLabel() {
    assertThat(MAP_3.toString()).isEqualTo("map3");
  }

  @Test
  @DisplayName("MAP_4 should have correct label")
  void map4ShouldHaveCorrectLabel() {
    assertThat(MAP_4.toString()).isEqualTo("map4");
  }

  @Test
  @DisplayName("MAP_5 should have correct label")
  void map5ShouldHaveCorrectLabel() {
    assertThat(MAP_5.toString()).isEqualTo("map5");
  }

  @Test
  @DisplayName("FLAT_MAP should have correct label")
  void flatMapShouldHaveCorrectLabel() {
    assertThat(FLAT_MAP.toString()).isEqualTo("flatMap");
  }

  @Test
  @DisplayName("FOLD_MAP should have correct label")
  void foldMapShouldHaveCorrectLabel() {
    assertThat(FOLD_MAP.toString()).isEqualTo("foldMap");
  }

  @Test
  @DisplayName("TRAVERSE should have correct label")
  void traverseShouldHaveCorrectLabel() {
    assertThat(TRAVERSE.toString()).isEqualTo("traverse");
  }

  @Test
  @DisplayName("HANDLE_ERROR_WITH should have correct label")
  void handleErrorWithShouldHaveCorrectLabel() {
    assertThat(HANDLE_ERROR_WITH.toString()).isEqualTo("handleErrorWith");
  }

  @Test
  @DisplayName("RECOVER_WITH should have correct label")
  void recoverWithShouldHaveCorrectLabel() {
    assertThat(RECOVER_WITH.toString()).isEqualTo("recoverWith");
  }

  @Test
  @DisplayName("RECOVER should have correct label")
  void recoverShouldHaveCorrectLabel() {
    assertThat(RECOVER.toString()).isEqualTo("recover");
  }

  @Test
  @DisplayName("RAISE_ERROR should have correct label")
  void raiseErrorShouldHaveCorrectLabel() {
    assertThat(RAISE_ERROR.toString()).isEqualTo("raiseError");
  }

  @Test
  @DisplayName("CONSTRUCTION should have correct label")
  void constructionShouldHaveCorrectLabel() {
    assertThat(CONSTRUCTION.toString()).isEqualTo("construction");
  }

  @Test
  @DisplayName("LEFT should have correct label")
  void leftShouldHaveCorrectLabel() {
    assertThat(LEFT.toString()).isEqualTo("left");
  }

  @Test
  @DisplayName("RIGHT should have correct label")
  void rightShouldHaveCorrectLabel() {
    assertThat(RIGHT.toString()).isEqualTo("right");
  }

  @Test
  @DisplayName("FROM_KIND should have correct label")
  void fromKindShouldHaveCorrectLabel() {
    assertThat(FROM_KIND.toString()).isEqualTo("fromKind");
  }

  @Test
  @DisplayName("FROM_EITHER should have correct label")
  void fromEitherShouldHaveCorrectLabel() {
    assertThat(FROM_EITHER.toString()).isEqualTo("fromEither");
  }

  @Test
  @DisplayName("FROM_MAYBE should have correct label")
  void fromMaybeShouldHaveCorrectLabel() {
    assertThat(FROM_MAYBE.toString()).isEqualTo("fromMaybe");
  }

  @Test
  @DisplayName("FROM_OPTIONAL should have correct label")
  void fromOptionalShouldHaveCorrectLabel() {
    assertThat(FROM_OPTIONAL.toString()).isEqualTo("fromOptional");
  }

  @Test
  @DisplayName("FROM_LIST should have correct label")
  void fromListShouldHaveCorrectLabel() {
    assertThat(FROM_LIST.toString()).isEqualTo("fromList");
  }

  @Test
  @DisplayName("FROM_OPTIONAL_LIST should have correct label")
  void fromOptionalListShouldHaveCorrectLabel() {
    assertThat(FROM_OPTIONAL_LIST.toString()).isEqualTo("fromOptionalList");
  }

  @Test
  @DisplayName("LIFT_F should have correct label")
  void liftFShouldHaveCorrectLabel() {
    assertThat(LIFT_F.toString()).isEqualTo("liftF");
  }

  @Test
  @DisplayName("OF should have correct label")
  void ofShouldHaveCorrectLabel() {
    assertThat(OF.toString()).isEqualTo("of");
  }

  @Test
  @DisplayName("ASK should have correct label")
  void askShouldHaveCorrectLabel() {
    assertThat(ASK.toString()).isEqualTo("ask");
  }

  @Test
  @DisplayName("SET should have correct label")
  void setShouldHaveCorrectLabel() {
    assertThat(SET.toString()).isEqualTo("set");
  }

  @Test
  @DisplayName("MODIFY should have correct label")
  void modifyShouldHaveCorrectLabel() {
    assertThat(MODIFY.toString()).isEqualTo("modify");
  }

  @Test
  @DisplayName("INSPECT should have correct label")
  void inspectShouldHaveCorrectLabel() {
    assertThat(INSPECT.toString()).isEqualTo("inspect");
  }

  @Test
  @DisplayName("RUN_STATE should have correct label")
  void runStateShouldHaveCorrectLabel() {
    assertThat(RUN_STATE.toString()).isEqualTo("runState");
  }

  @Test
  @DisplayName("STATE_T should have correct label")
  void stateTShouldHaveCorrectLabel() {
    assertThat(STATE_T.toString()).isEqualTo("stateT");
  }

  @Test
  @DisplayName("RUN_STATE_T should have correct label")
  void runStateTShouldHaveCorrectLabel() {
    assertThat(RUN_STATE_T.toString()).isEqualTo("runStateT");
  }

  @Test
  @DisplayName("EVAL_STATE_T should have correct label")
  void evalStateTShouldHaveCorrectLabel() {
    assertThat(EVAL_STATE_T.toString()).isEqualTo("evalStateT");
  }

  @Test
  @DisplayName("EXEC_STATE_T should have correct label")
  void execStateTShouldHaveCorrectLabel() {
    assertThat(EXEC_STATE_T.toString()).isEqualTo("execStateT");
  }

  @Test
  @DisplayName("JUST should have correct label")
  void justShouldHaveCorrectLabel() {
    assertThat(JUST.toString()).isEqualTo("just");
  }

  @Test
  @DisplayName("DEFER should have correct label")
  void deferShouldHaveCorrectLabel() {
    assertThat(DEFER.toString()).isEqualTo("defer");
  }

  @Test
  @DisplayName("SOME should have correct label")
  void someShouldHaveCorrectLabel() {
    assertThat(SOME.toString()).isEqualTo("some");
  }

  @Test
  @DisplayName("NONE should have correct label")
  void noneShouldHaveCorrectLabel() {
    assertThat(NONE.toString()).isEqualTo("none");
  }

  @Test
  @DisplayName("READER should have correct label")
  void readerShouldHaveCorrectLabel() {
    assertThat(READER.toString()).isEqualTo("reader");
  }

  @Test
  @DisplayName("RUN_READER should have correct label")
  void runReaderShouldHaveCorrectLabel() {
    assertThat(RUN_READER.toString()).isEqualTo("runReader");
  }

  @Test
  @DisplayName("FOLD should have correct label")
  void foldShouldHaveCorrectLabel() {
    assertThat(FOLD.toString()).isEqualTo("fold");
  }

  @Test
  @DisplayName("TO_EITHER should have correct label")
  void toEitherShouldHaveCorrectLabel() {
    assertThat(TO_EITHER.toString()).isEqualTo("toEither");
  }

  @Test
  @DisplayName("MATCH should have correct label")
  void matchShouldHaveCorrectLabel() {
    assertThat(MATCH.toString()).isEqualTo("match");
  }

  @Test
  @DisplayName("OR_ELSE_GET should have correct label")
  void orElseGetShouldHaveCorrectLabel() {
    assertThat(OR_ELSE_GET.toString()).isEqualTo("orElseGet");
  }

  @Test
  @DisplayName("OR_EITHER should have correct label")
  void orEitherShouldHaveCorrectLabel() {
    assertThat(OR_EITHER.toString()).isEqualTo("orEither");
  }

  @Test
  @DisplayName("RECOVER_FUNCTION should have correct label")
  void recoverFunctionShouldHaveCorrectLabel() {
    assertThat(RECOVER_FUNCTION.toString()).isEqualTo("recoverFunction");
  }

  @Test
  @DisplayName("VALUE should have correct label")
  void valueShouldHaveCorrectLabel() {
    assertThat(VALUE.toString()).isEqualTo("value");
  }

  @Test
  @DisplayName("TELL should have correct label")
  void tellShouldHaveCorrectLabel() {
    assertThat(TELL.toString()).isEqualTo("tell");
  }

  @Test
  @DisplayName("IF_LEFT should have correct label")
  void ifLeftShouldHaveCorrectLabel() {
    assertThat(IF_LEFT.toString()).isEqualTo("ifLeft");
  }

  @Test
  @DisplayName("IF_RIGHT should have correct label")
  void ifRightShouldHaveCorrectLabel() {
    assertThat(IF_RIGHT.toString()).isEqualTo("ifRight");
  }

  @Test
  @DisplayName("SEQUENCE_A should have correct label")
  void sequenceAShouldHaveCorrectLabel() {
    assertThat(SEQUENCE_A.toString()).isEqualTo("sequenceA");
  }

  @Test
  @DisplayName("SEQUENCE_B should have correct label")
  void sequenceBShouldHaveCorrectLabel() {
    assertThat(SEQUENCE_B.toString()).isEqualTo("sequenceB");
  }

  @Test
  @DisplayName("IF_VALID should have correct label")
  void ifValidShouldHaveCorrectLabel() {
    assertThat(IF_VALID.toString()).isEqualTo("ifValid");
  }

  @Test
  @DisplayName("IF_INVALID should have correct label")
  void ifInvalidShouldHaveCorrectLabel() {
    assertThat(IF_INVALID.toString()).isEqualTo("ifInvalid");
  }

  @Test
  @DisplayName("OR_ELSE_THROW should have correct label")
  void orElseThrowShouldHaveCorrectLabel() {
    assertThat(OR_ELSE_THROW.toString()).isEqualTo("orElseThrow");
  }

  @Test
  @DisplayName("DELAY should have correct label")
  void delayShouldHaveCorrectLabel() {
    assertThat(DELAY.toString()).isEqualTo("delay");
  }

  @Test
  @DisplayName("INVALID should have correct label")
  void invalidShouldHaveCorrectLabel() {
    assertThat(INVALID.toString()).isEqualTo("invalid");
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
