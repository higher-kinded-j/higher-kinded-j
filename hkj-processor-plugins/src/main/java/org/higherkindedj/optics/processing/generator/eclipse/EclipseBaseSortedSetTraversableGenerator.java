// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.eclipse;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.RecordComponentElement;
import org.higherkindedj.optics.util.Traversals;

public abstract class EclipseBaseSortedSetTraversableGenerator
    extends EclipseBaseSingleIterableTraversableGenerator {

  public static final String SORTED_SET_PACKAGE = SET_PACKAGE + ".sorted";

  public static final ClassName SORTED_SETS_API = ClassName.get(FACTORY_PACKAGE, "SortedSets");

  public static final ClassName IMMUTABLE_SORTED_SET =
      ClassName.get(SORTED_SET_PACKAGE, "ImmutableSortedSet");
  public static final ClassName MUTABLE_SORTED_SET =
      ClassName.get(SORTED_SET_PACKAGE, "MutableSortedSet");

  protected EclipseBaseSortedSetTraversableGenerator(
      final ClassName supportedElement, final boolean immutable, final ClassName api) {
    super(supportedElement, immutable, api);
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
            "final var sourceList = source.$L().into(new $T<>(source.$L().size()))",
            componentName,
            ArrayList.class,
            componentName)

        // 2. Call the static helper to traverse the list, yielding Kind<F, List<B>>.
        .addStatement(
            "final var effectOfList = $T.traverseList(sourceList, f, applicative)",
            Traversals.class)

        // 3. Map over the effect to convert the inner List back to our type.
        .addStatement(
            "final var effectOfSet = applicative.map("
                + "newList -> $T.isNull(source.$L().comparator()) ? $T.$L.ofAll(newList) : $T.$L.ofAll(source.$L().comparator(), newList), effectOfList)",
            Objects.class,
            componentName,
            api,
            immutable ? "immutable" : "mutable",
            api,
            immutable ? "immutable" : "mutable",
            componentName)

        // 4. Map over the final effect to reconstruct the record with the new Set.
        .addStatement(
            "return applicative.map(converted -> new $T($L), effectOfSet)",
            recordClassName,
            constructorArgs)
        .build();
  }
}
