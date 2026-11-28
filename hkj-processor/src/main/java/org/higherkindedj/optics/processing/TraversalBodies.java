// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.List;
import javax.lang.model.element.RecordComponentElement;
import org.higherkindedj.optics.processing.spi.GenerationContext;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * The body of a generated traversal's {@code modifyF}, as {@code @GenerateTraversals} and
 * {@code @ImportOptics} both write it.
 *
 * <p>A generator's body reads the component and hands it straight on, to {@code
 * Traversals.traverseList}, to {@code new ArrayList<>(...)} or to a {@code modifyF} of its own, so
 * a null container would throw from {@code modify} and {@code getAll} alike. Where the component is
 * declared nullable ({@link NullableAnnotations#hasNullableAnnotation}), the body is written behind
 * a null check that hands the record back as it is. A null container then has no elements, as it
 * has for the Focus path, which steps through {@code .nullable()} before the container's own step
 * ({@link WideningAnalysis}). The check is written here rather than in each generator, so it covers
 * a plugin's generator too, and the generator reads the component as non-null.
 */
public final class TraversalBodies {

  private TraversalBodies() {}

  /**
   * The {@code modifyF} body for {@code component}: the generator's own, behind a null check when
   * the component is declared nullable.
   *
   * @param generator the generator that claims the component
   * @param component the record component traversed
   * @param recordClassName the record's class name
   * @param allComponents every component of the record, which the generator rebuilds it from
   * @param context where the generated file is written, and the round's element utilities
   * @return the body of the generated {@code modifyF}
   */
  public static CodeBlock modifyF(
      TraversableGenerator generator,
      RecordComponentElement component,
      ClassName recordClassName,
      List<? extends RecordComponentElement> allComponents,
      GenerationContext context) {
    CodeBlock body = generator.generateModifyF(component, recordClassName, allComponents, context);
    if (!NullableAnnotations.hasNullableAnnotation(component)) {
      return body;
    }
    return CodeBlock.builder()
        .beginControlFlow("if (source.$L() == null)", component.getSimpleName())
        .addStatement("return applicative.of(source)")
        .endControlFlow()
        .add(body)
        .build();
  }
}
