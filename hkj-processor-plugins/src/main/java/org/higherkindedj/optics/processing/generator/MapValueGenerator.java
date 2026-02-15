// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.util.Traversals;

/**
 * A {@link org.higherkindedj.optics.processing.spi.TraversableGenerator} that adds support for
 * traversing the **values** of a field of type {@link java.util.Map}.
 */
public class MapValueGenerator extends BaseTraversableGenerator {

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
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final String constructorArgs = generateConstructorArgs(componentName, "newMap", allComponents);

    return CodeBlock.builder()
        // 1. Get the source Map's entry set as a List to traverse.
        .addStatement(
            "final var sourceEntries = new $T<>(source.$L().entrySet())",
            ArrayList.class,
            componentName)
        // 2. Define a function that takes an entry, applies the user's function `f` to the value,
        //    and then reconstructs the entry with the new value.
        .addStatement(
            "final $T<Map.Entry<$T, $T>, $T<F, Map.Entry<$T, $T>>> entryF = \n"
                + "    entry -> applicative.map(newValue -> $T.entry(entry.getKey(), newValue),"
                + " f.apply(entry.getValue()))",
            Function.class,
            getKeyTypeName(component),
            getValueTypeName(component),
            Kind.class,
            getKeyTypeName(component),
            getValueTypeName(component), // This assumes f: V -> F<V>, so new value is same type.
            Map.class)
        // 3. Call the static helper to traverse the list of entries using our new function.
        .addStatement(
            "final var effectOfEntries = $T.traverseList(sourceEntries, entryF, applicative)",
            Traversals.class)
        // 4. Map over the effect to convert the inner List of entries back to a Map.
        .addStatement(
            "final var effectOfMap = applicative.map(\n"
                + "    newEntries -> newEntries.stream().collect($T.toMap($T.Entry::getKey,"
                + " $T.Entry::getValue)),\n"
                + "    effectOfEntries)",
            Collectors.class,
            Map.class,
            Map.class)
        // 5. Map over the final effect to reconstruct the record with the new Map.
        .addStatement(
            "return applicative.map(newMap -> new $T($L), effectOfMap)",
            recordClassName,
            constructorArgs)
        .build();
  }

  /** Gets the 'Value' type from a {@code Map<K, V>} component. */
  private TypeName getValueTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof DeclaredType containerType) {
      if (containerType.getTypeArguments().size() < 2) {
        return ClassName.get(Object.class);
      }
      return TypeName.get(containerType.getTypeArguments().get(1)).box();
    }
    return ClassName.get(Object.class);
  }

  /** Gets the 'Key' type from a {@code Map<K, V>} component. */
  private TypeName getKeyTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof DeclaredType containerType) {
      if (containerType.getTypeArguments().isEmpty()) {
        return ClassName.get(Object.class);
      }
      return TypeName.get(containerType.getTypeArguments().getFirst()).box();
    }
    return ClassName.get(Object.class);
  }
}
