// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javapoet.ClassName;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.optics.annotations.KindSemantics;
import org.higherkindedj.optics.processing.WideningAnalysis.Step;
import org.higherkindedj.optics.processing.WideningAnalysis.StepKind;
import org.higherkindedj.optics.processing.kind.KindFieldInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link WideningAnalysis#expression}.
 *
 * <p>The analysis reads a Kind from the component's own declaration, so a {@code KIND_*} layer only
 * ever arrives outermost and never after another layer. The fold that renders a chain does not know
 * that, and the mixed chains below pin what it would emit if it ever did. Rendering needs no
 * processing environment, so they are fabricated rather than compiled.
 */
@DisplayName("Widening expression")
class WideningExpressionTest {

  private static Step step(StepKind kind) {
    return new Step(kind, null, null, null);
  }

  private static Step kindStep(StepKind kind, KindSemantics semantics) {
    KindFieldInfo kindInfo =
        KindFieldInfo.of("W", ClassName.get(String.class), "T.INSTANCE", semantics);
    return new Step(kind, kindInfo, null, null);
  }

  private static String build(Step... steps) {
    return WideningAnalysis.expression(List.of(steps), new ArrayList<>());
  }

  @Test
  @DisplayName("should append .nullable() for a NULLABLE step")
  void shouldAppendNullableForNullableStep() {
    String expression = build(step(StepKind.OPTIONAL), step(StepKind.NULLABLE));

    assertThat(expression).isEqualTo(".some().nullable()");
  }

  @Test
  @DisplayName("should append traverseOver().headOption() for a KIND_ZERO_OR_ONE step")
  void shouldAppendHeadOptionForKindZeroOrOne() {
    String expression =
        build(
            step(StepKind.OPTIONAL),
            kindStep(StepKind.KIND_ZERO_OR_ONE, KindSemantics.ZERO_OR_ONE));

    assertThat(expression)
        .isEqualTo(".some().<W, java.lang.String>traverseOver(T.INSTANCE).headOption()");
  }

  @Test
  @DisplayName("should append traverseOver().headOption() for a KIND_EXACTLY_ONE step")
  void shouldAppendHeadOptionForKindExactlyOne() {
    String expression =
        build(
            step(StepKind.OPTIONAL),
            kindStep(StepKind.KIND_EXACTLY_ONE, KindSemantics.EXACTLY_ONE));

    assertThat(expression)
        .isEqualTo(".some().<W, java.lang.String>traverseOver(T.INSTANCE).headOption()");
  }

  @Test
  @DisplayName("should append traverseOver() for a KIND_ZERO_OR_MORE step")
  void shouldAppendTraverseOverForKindZeroOrMore() {
    String expression =
        build(
            step(StepKind.COLLECTION),
            kindStep(StepKind.KIND_ZERO_OR_MORE, KindSemantics.ZERO_OR_MORE));

    assertThat(expression).isEqualTo(".each().<W, java.lang.String>traverseOver(T.INSTANCE)");
  }

  @Test
  @DisplayName("should render nothing for a component nothing widens")
  void shouldRenderNothingWhenNothingWidens() {
    assertThat(build()).isEmpty();
  }
}
