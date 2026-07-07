// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javapoet.ClassName;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.optics.annotations.KindSemantics;
import org.higherkindedj.optics.processing.FocusProcessor.WideningStep;
import org.higherkindedj.optics.processing.FocusProcessor.WideningType;
import org.higherkindedj.optics.processing.kind.KindFieldInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link FocusProcessor#buildWideningChainExpression}.
 *
 * <p>{@code analyseNestedType} only ever emits OPTIONAL, COLLECTION and SPI steps, so the NULLABLE,
 * KIND and default arms of the chain switch are unreachable through compile-testing fixtures. The
 * method is pure (no processing environment), so fabricated chains cover those arms directly.
 */
@DisplayName("FocusProcessor widening chain expression")
class FocusProcessorWideningChainTest {

  private final FocusProcessor processor = new FocusProcessor();

  private static WideningStep step(WideningType type) {
    return new WideningStep(type, null, null, null);
  }

  private static WideningStep kindStep(WideningType type, KindSemantics semantics) {
    KindFieldInfo kindInfo =
        KindFieldInfo.of("W", ClassName.get(String.class), "T.INSTANCE", semantics);
    return new WideningStep(type, kindInfo, null, null);
  }

  private String build(WideningStep... steps) {
    return processor.buildWideningChainExpression(List.of(steps), new ArrayList<>());
  }

  @Test
  @DisplayName("should append .nullable() for a NULLABLE step")
  void shouldAppendNullableForNullableStep() {
    String expression = build(step(WideningType.OPTIONAL), step(WideningType.NULLABLE));

    assertThat(expression).isEqualTo(".some().nullable()");
  }

  @Test
  @DisplayName("should append traverseOver().headOption() for a KIND_ZERO_OR_ONE step")
  void shouldAppendHeadOptionForKindZeroOrOne() {
    String expression =
        build(
            step(WideningType.OPTIONAL),
            kindStep(WideningType.KIND_ZERO_OR_ONE, KindSemantics.ZERO_OR_ONE));

    assertThat(expression)
        .isEqualTo(".some().<W, java.lang.String>traverseOver(T.INSTANCE).headOption()");
  }

  @Test
  @DisplayName("should append traverseOver().headOption() for a KIND_EXACTLY_ONE step")
  void shouldAppendHeadOptionForKindExactlyOne() {
    String expression =
        build(
            step(WideningType.OPTIONAL),
            kindStep(WideningType.KIND_EXACTLY_ONE, KindSemantics.EXACTLY_ONE));

    assertThat(expression)
        .isEqualTo(".some().<W, java.lang.String>traverseOver(T.INSTANCE).headOption()");
  }

  @Test
  @DisplayName("should append traverseOver() for a KIND_ZERO_OR_MORE step")
  void shouldAppendTraverseOverForKindZeroOrMore() {
    String expression =
        build(
            step(WideningType.COLLECTION),
            kindStep(WideningType.KIND_ZERO_OR_MORE, KindSemantics.ZERO_OR_MORE));

    assertThat(expression).isEqualTo(".each().<W, java.lang.String>traverseOver(T.INSTANCE)");
  }

  @Test
  @DisplayName("should ignore NONE and NESTED steps in a chain")
  void shouldIgnoreNoneAndNestedSteps() {
    String expression = build(step(WideningType.NONE), step(WideningType.NESTED));

    assertThat(expression).isEmpty();
  }
}
