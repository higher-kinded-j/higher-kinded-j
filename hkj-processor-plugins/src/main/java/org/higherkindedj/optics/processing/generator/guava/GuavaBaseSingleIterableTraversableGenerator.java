// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.guava;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.util.Traversals;

/// Base of Traversable Generators for Google Guava Collections that are both:
///  * A collection of 0...n, not 1
///  * Of a single type/column
public abstract class GuavaBaseSingleIterableTraversableGenerator extends BaseTraversableGenerator {
  private static final String COLLECTIONS_PACKAGE = "com.google.common.collect";

  public static final ClassName IMMUTABLE_LIST =
      ClassName.get(COLLECTIONS_PACKAGE, "ImmutableList");
  public static final ClassName IMMUTABLE_SET = ClassName.get(COLLECTIONS_PACKAGE, "ImmutableSet");

  protected final ClassName supportedType;

  GuavaBaseSingleIterableTraversableGenerator(final ClassName supportedType) {
    this.supportedType = supportedType;
  }

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element.toString().equals(supportedType.canonicalName());
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
            "final var effectOfConvertBack = applicative.map("
                + "newList -> $T.copyOf(newList), effectOfList)",
            supportedType)

        // 4. Map over the final effect to reconstruct the record with the original type.
        .addStatement(
            "return applicative.map(converted -> new $T($L), effectOfConvertBack)",
            recordClassName,
            constructorArgs)
        .build();
  }
}
