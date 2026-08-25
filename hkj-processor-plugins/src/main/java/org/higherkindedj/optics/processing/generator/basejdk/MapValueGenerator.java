// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.basejdk;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.avaje.spi.ServiceProvider;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.util.Traversals;

/**
 * A {@link org.higherkindedj.optics.processing.spi.TraversableGenerator} that adds support for
 * traversing the **values** of a field of type {@link java.util.Map}.
 */
@ServiceProvider(TraversableGenerator.class)
public class MapValueGenerator extends BaseTraversableGenerator {

  /** Creates a new generator for {@link java.util.Map} value traversal. */
  public MapValueGenerator() {}

  private static final String FQN_MAP = "java.util.Map";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) return false;
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_MAP);
  }

  @Override
  public int getFocusTypeArgumentIndex() {
    return 1; // Map<K, V> focuses on V (the second type argument)
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.mapValuesEach()";
  }

  @Override
  public Set<String> getRequiredImports() {
    return Set.of("org.higherkindedj.optics.each.EachInstances");
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final String constructorArgs = generateConstructorArgs(componentName, "newMap", allComponents);

    return CodeBlock.builder()
        // 1. Call the static helper to traverse the map's values, keys untouched.
        .addStatement(
            "final var effectOfMap = $T.traverseMapValues(source.$L(), f, applicative)",
            Traversals.class,
            componentName)
        // 2. Map over the final effect to reconstruct the record with the new map.
        .addStatement(
            "return applicative.map(newMap -> new $T($L), effectOfMap)",
            recordTypeName(component, recordClassName),
            constructorArgs)
        .build();
  }
}
