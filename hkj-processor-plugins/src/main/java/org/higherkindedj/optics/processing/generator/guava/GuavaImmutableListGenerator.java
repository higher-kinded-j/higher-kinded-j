// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.guava;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.avaje.spi.ServiceProvider;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.util.Traversals;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields that are Eclipse
 * Collections' {@code ImmutableSet}.
 */
@ServiceProvider(TraversableGenerator.class)
public final class GuavaImmutableListGenerator extends BaseGuavaSingleIterableTraversableGenerator {

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element.toString().equals(IMMUTABLE_LIST.canonicalName());
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final String constructorArgs =
        generateConstructorArgs(componentName, "converted", allComponents);

    return CodeBlock.builder()
        // 1. Convert to Java ArrayList (like the `basejdk/SetGenerator.java` does)
        .addStatement(
            "final var sourceList = new $T<>(source.$L())", ArrayList.class, componentName)

        // 2. Call the static helper to traverse the list, yielding Kind<F, List<B>>.
        .addStatement(
            "final var effectOfList = $T.traverseList(sourceList, f, applicative)",
            Traversals.class)

        // 3. Map over the effect to convert the inner List back to our type.
        .addStatement(
            "final var effectOfSet = applicative.map("
                + "newList -> $T.copyOf(newList), effectOfList)",
            IMMUTABLE_LIST)

        // 4. Map over the final effect to reconstruct the record with the new Set.
        .addStatement(
            "return applicative.map(converted -> new $T($L), effectOfSet)",
            recordClassName,
            constructorArgs)
        .build();
  }
}
