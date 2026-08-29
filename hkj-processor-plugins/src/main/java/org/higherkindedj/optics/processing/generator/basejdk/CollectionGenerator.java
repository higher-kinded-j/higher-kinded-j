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
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@link
 * java.util.Collection}.
 *
 * <p>A {@code Collection} names no more than "holds elements", so the generated traversal does not
 * settle on a shape of its own: it calls {@link Traversals#traverseCollection}, which rebuilds a
 * {@link java.util.Set} source as a set and every other source as a {@link List}. That is the one
 * rebuild policy behind {@code Traversals.forCollection()} and {@code
 * EachInstances.collectionEach()}, so a {@code Collection} component rebuilds the same way
 * whichever annotation reads it. The limits that policy carries — a sorted set keeps its elements
 * but not its comparator, and a source that is neither list nor set comes back a list — are
 * documented on {@code Traversals.forCollection()}.
 */
@ServiceProvider(TraversableGenerator.class)
public class CollectionGenerator extends BaseTraversableGenerator {

  /** Creates a new generator for {@link java.util.Collection} fields. */
  public CollectionGenerator() {}

  private static final String FQN_COLLECTION = "java.util.Collection";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) return false;
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_COLLECTION);
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.collectionEach()";
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
    final String constructorArgs =
        generateConstructorArgs(componentName, "newCollection", allComponents);

    return CodeBlock.builder()
        // 1. Traverse the collection through the one helper that decides how it is rebuilt: a set
        // source comes back a set, anything else a list. The decision is not written into the
        // generated source, so it cannot drift from the one every other route composes.
        .addStatement(
            "final var effectOfCollection = $T.traverseCollection(source.$L(), f, applicative)",
            Traversals.class,
            componentName)

        // 2. Map over the final effect to reconstruct the record with the new Collection.
        .addStatement(
            "return applicative.map(newCollection -> new $T($L), effectOfCollection)",
            recordTypeName(component, recordClassName),
            constructorArgs)
        .build();
  }
}
