// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.apache;

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

/// Base of Traversable Generators for Apache Collections that are both:
///  * A collection of 0...n, not 1
///  * Of a single type/column
public abstract class ApacheBaseSingleIterableTraversableGenerator
    extends BaseTraversableGenerator {
  protected static final String PACKAGE = "org.apache.commons.collections4";
  protected static final String BAG_PACKAGE = PACKAGE + ".bag";
  protected static final String LIST_PACKAGE = PACKAGE + ".list";

  public static final ClassName HASH_BAG = ClassName.get(BAG_PACKAGE, "HashBag");
  public static final ClassName UNMODIFIABLE_LIST = ClassName.get(LIST_PACKAGE, "UnmodifiableList");
  // Unfortunately, the return type of making both `UnmodifiableBag` and `UnmodifiableSet` is
  //   `Bag` and `Set` respectively, which means we would probably need to do an unsafe cast to
  //   properly convert them.

  protected final ClassName supportedType;

  ApacheBaseSingleIterableTraversableGenerator(final ClassName supportedType) {
    this.supportedType = supportedType;
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.fromIterableCollecting(" + supportedType.simpleName() + "::new)";
  }

  @Override
  public Set<String> getRequiredImports() {
    return Set.of(
        "org.higherkindedj.optics.each.EachInstances",
        supportedType.packageName() + "." + supportedType.simpleName());
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
                + "newList -> new $T<>(newList), effectOfList)",
            supportedType)

        // 4. Map over the final effect to reconstruct the record with the original type.
        .addStatement(
            "return applicative.map(converted -> new $T($L), effectOfConvertBack)",
            recordClassName,
            constructorArgs)
        .build();
  }
}
