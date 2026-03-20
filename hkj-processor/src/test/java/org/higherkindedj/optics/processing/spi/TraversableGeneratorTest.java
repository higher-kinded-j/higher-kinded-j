// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.spi;

import static org.junit.jupiter.api.Assertions.*;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.TypeMirror;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link TraversableGenerator} default methods. */
@DisplayName("TraversableGenerator default methods")
class TraversableGeneratorTest {

  /** Minimal implementation for testing defaults. */
  private static final TraversableGenerator MINIMAL_IMPL =
      new TraversableGenerator() {
        @Override
        public boolean supports(TypeMirror type) {
          return false;
        }

        @Override
        public CodeBlock generateModifyF(
            RecordComponentElement component,
            ClassName recordClassName,
            List<? extends RecordComponentElement> allComponents) {
          return CodeBlock.builder().build();
        }
      };

  @Test
  @DisplayName("getCardinality should default to ZERO_OR_MORE")
  void getCardinalityShouldDefaultToZeroOrMore() {
    assertEquals(Cardinality.ZERO_OR_MORE, MINIMAL_IMPL.getCardinality());
  }

  @Test
  @DisplayName("generateOpticExpression should default to empty string")
  void generateOpticExpressionShouldDefaultToEmpty() {
    assertEquals("", MINIMAL_IMPL.generateOpticExpression());
  }

  @Test
  @DisplayName("getRequiredImports should default to empty set")
  void getRequiredImportsShouldDefaultToEmpty() {
    assertEquals(Set.of(), MINIMAL_IMPL.getRequiredImports());
  }

  @Test
  @DisplayName("getFocusTypeArgumentIndex should default to 0")
  void getFocusTypeArgumentIndexShouldDefaultToZero() {
    assertEquals(0, MINIMAL_IMPL.getFocusTypeArgumentIndex());
  }

  @Test
  @DisplayName("Cardinality enum should have two values")
  void cardinalityEnumShouldHaveTwoValues() {
    Cardinality[] values = Cardinality.values();
    assertEquals(2, values.length);
    assertEquals(Cardinality.ZERO_OR_ONE, values[0]);
    assertEquals(Cardinality.ZERO_OR_MORE, values[1]);
  }
}
