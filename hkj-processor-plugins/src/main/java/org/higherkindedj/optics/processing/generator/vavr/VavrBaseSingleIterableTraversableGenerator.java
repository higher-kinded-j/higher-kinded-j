// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.vavr;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.util.Traversals;

/// Base of Traversable Generators for Vavr's collections that are both:
///  * A collection of 0...n, not 1
///  * Of a single type/column
public abstract class VavrBaseSingleIterableTraversableGenerator extends BaseTraversableGenerator {
  private static final String COLLECTION_PACKAGE = "io.vavr.collection";

  public static final ClassName SET = ClassName.get(COLLECTION_PACKAGE, "Set");
  public static final ClassName HASH_SET = ClassName.get(COLLECTION_PACKAGE, "HashSet");
  public static final ClassName LIST = ClassName.get(COLLECTION_PACKAGE, "List");

  protected final ClassName supportedType;
  protected final ClassName constructedType;

  VavrBaseSingleIterableTraversableGenerator(
      final ClassName supportedType, final ClassName constructedType) {
    this.supportedType = supportedType;
    this.constructedType = constructedType;
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.fromIterableCollecting(list -> "
        + constructedType.simpleName()
        + ".ofAll(list))";
  }

  @Override
  public Set<String> getRequiredImports() {
    return Set.of(
        "org.higherkindedj.optics.each.EachInstances",
        constructedType.packageName() + "." + constructedType.simpleName());
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
            "final var sourceList = source.$L().toJavaCollection($T::new)",
            componentName,
            ArrayList.class)

        // 2. Call the static helper to traverse the list, yielding Kind<F, List<B>>.
        .addStatement(
            "final var effectOfList = $T.traverseList(sourceList, f, applicative)",
            Traversals.class)

        // 3. Map over the effect to convert the inner List back to our type.
        .addStatement(
            "final var effectOfConvertBack = applicative.map("
                + "newList -> $T.ofAll(newList), effectOfList)",
            constructedType)

        // 4. Map over the final effect to reconstruct the record with the original type.
        .addStatement(
            "return applicative.map(converted -> new $T($L), effectOfConvertBack)",
            recordClassName,
            constructorArgs)
        .build();
  }
}
