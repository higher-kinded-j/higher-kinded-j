// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.util.Traversals;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@link
 * java.util.List}.
 *
 * <p>This implementation has been refactored to delegate its complex HKT logic to the {@link
 * Traversals#traverseList} helper method, which greatly simplifies the generated code.
 */
public class ListGenerator extends BaseTraversableGenerator {

  private static final String FQN_LIST = "java.util.List";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) return false;
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_LIST);
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    // Use the inherited helper to generate the constructor args. The new value
    // for the list is assumed to be in a variable named `newList`.
    final String constructorArgs = generateConstructorArgs(componentName, "newList", allComponents);

    // The generated code is now a clean, two-step process.
    return CodeBlock.builder()
        // 1. Call the static helper to traverse the list.
        .addStatement(
            "final var effectOfList = $T.traverseList(source.$L(), f, applicative)",
            Traversals.class,
            componentName)
        // 2. Map over the final effect to reconstruct the record with the new list.
        .addStatement(
            "return applicative.map(newList -> new $T($L), effectOfList)",
            recordClassName,
            constructorArgs)
        .build();
  }
}
