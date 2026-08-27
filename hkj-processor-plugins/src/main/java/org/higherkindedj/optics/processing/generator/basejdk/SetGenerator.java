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
 * traversing fields of type {@link java.util.Set}.
 */
@ServiceProvider(TraversableGenerator.class)
public class SetGenerator extends BaseTraversableGenerator {

  /** Creates a new generator for {@link java.util.Set} fields. */
  public SetGenerator() {}

  private static final String FQN_SET = "java.util.Set";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) return false;
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_SET);
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.setEach()";
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
    final String constructorArgs = generateConstructorArgs(componentName, "newSet", allComponents);

    return CodeBlock.builder()
        // 1. Traverse the set through the one helper that rebuilds one: source iteration order
        // preserved, nulls carried through, and the result unmodifiable. Every other route to a
        // Set traversal -- @GenerateFocus through EachInstances.setEach(), @ImportOptics and
        // @ThroughField through Traversals.forSet() -- bottoms out here too, so a Set component
        // rebuilds the same way whichever annotation reads it (issue #725).
        .addStatement(
            "final var effectOfSet = $T.traverseSet(source.$L(), f, applicative)",
            Traversals.class,
            componentName)

        // 2. Map over the final effect to reconstruct the record with the new Set.
        .addStatement(
            "return applicative.map(newSet -> new $T($L), effectOfSet)",
            recordTypeName(component, recordClassName),
            constructorArgs)
        .build();
  }
}
