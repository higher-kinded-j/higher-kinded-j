// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

public enum Operation {
  AP("ap"),
  MAP("map"),
  MAP_2("map2"),
  MAP_3("map3"),
  MAP_4("map4"),
  MAP_5("map5"),
  FLAT_MAP("flatMap"),
  FOLD_MAP("foldMap"),
  TRAVERSE("traverse"),
  HANDLE_ERROR_WITH("handleErrorWith"),
  RECOVER_WITH("recoverWith"),
  RECOVER("recover"),
  RAISE_ERROR("raiseError"),
  CONSTRUCTION("construction"),
  LEFT("left"),
  RIGHT("right"),
  FROM_KIND("fromKind"),
  FROM_EITHER("fromEither"),
  FROM_MAYBE("fromMaybe"),
  FROM_OPTIONAL("fromOptional"),
  FROM_LIST("fromList"),
  FROM_OPTIONAL_LIST("fromOptionalList"),
  LIFT_F("liftF"),
  OF("of"),
  ASK("ask"),
  SET("set"),
  MODIFY("modify"),
  INSPECT("inspect"),
  RUN_STATE("runState"),
  STATE_T("stateT"),
  RUN_STATE_T("runStateT"),
  EVAL_STATE_T("evalStateT"),
  EXEC_STATE_T("execStateT"),
  JUST("just"),
  DEFER("defer"),
  SOME("some"),
  NONE("none"),
  READER("reader"),
  RUN_READER("runReader"),
  FOLD("fold"),
  TO_EITHER("toEither"),
  MATCH("match"),
  OR_ELSE_GET("orElseGet"),
  OR_EITHER("orEither"),
  RECOVER_FUNCTION("recoverFunction"),
  VALUE("value"),
  TELL("tell"),
  IF_LEFT("ifLeft"),
  IF_RIGHT("ifRight"),
  SEQUENCE_A("sequenceA"),
  SEQUENCE_B("sequenceB"),
  IF_VALID("ifValid"),
  IF_INVALID("ifInvalid"),
  OR_ELSE_THROW("orElseThrow"),
  DELAY("delay"),
  INVALID("invalid"),
  IF_S("ifS"),
  SELECT("select"),
  WHEN_S("whenS"),
  BRANCH("branch"),
  BIMAP("bimap"),
  FIRST("first"),
  SECOND("second"),
  MAP_LEFT("mapLeft"),
  MAP_RIGHT("mapRight"),
  MAP_FIRST("mapFirst"),
  MAP_SECOND("mapSecond"),
  MAP_THIRD("mapThird"),
  MAP_FOURTH("mapFourth"),
  MAP_FIFTH("mapFifth"),
  MAP_ERROR("mapError"),
  MAP_WRITTEN("mapWritten");

  Operation(String label) {
    this.label = label;
  }

  final String label;

  @Override
  public String toString() {
    return label;
  }
}
