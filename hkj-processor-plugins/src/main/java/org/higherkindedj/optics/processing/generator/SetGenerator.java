// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.util.Traversals;

/**
 * A {@link org.higherkindedj.optics.processing.spi.TraversableGenerator} that adds support for
 * traversing fields of type {@link java.util.Set}.
 */
public class SetGenerator extends BaseTraversableGenerator {

  private static final String FQN_SET = "java.util.Set";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) return false;
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_SET);
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final String constructorArgs = generateConstructorArgs(componentName, "newSet", allComponents);

    return CodeBlock.builder()
        // 1. Get the source Set and convert it to a List to ensure ordering for traversal.
        .addStatement(
            "final var sourceList = new $T<>(source.$L())", ArrayList.class, componentName)

        // 2. Call the static helper to traverse the list, yielding Kind<F, List<B>>.
        .addStatement(
            "final var effectOfList = $T.traverseList(sourceList, f, applicative)",
            Traversals.class)

        // 3. Map over the effect to convert the inner List back to a Set.
        .addStatement(
            "final var effectOfSet = applicative.map("
                + "newList -> newList.stream().collect($T.toSet()), effectOfList)",
            Collectors.class)

        // 4. Map over the final effect to reconstruct the record with the new Set.
        .addStatement(
            "return applicative.map(newSet -> new $T($L), effectOfSet)",
            recordClassName,
            constructorArgs)
        .build();
  }
}
