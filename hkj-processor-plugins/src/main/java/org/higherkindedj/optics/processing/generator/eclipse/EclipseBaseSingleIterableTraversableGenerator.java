// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.eclipse;

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

/// Base of Traversable Generators for Eclipse Collections that are both:
///  * A collection of 0...n, not 1
///  * Of a single type/column
public abstract class EclipseBaseSingleIterableTraversableGenerator
    extends BaseTraversableGenerator {
  protected static final String API_PACKAGE = "org.eclipse.collections.api";
  protected static final String FACTORY_PACKAGE = API_PACKAGE + ".factory";
  protected static final String BAG_PACKAGE = API_PACKAGE + ".bag";
  protected static final String LIST_PACKAGE = API_PACKAGE + ".list";
  protected static final String SET_PACKAGE = API_PACKAGE + ".set";

  public static final ClassName BAGS_API = ClassName.get(FACTORY_PACKAGE, "Bags");
  public static final ClassName LISTS_API = ClassName.get(FACTORY_PACKAGE, "Lists");
  public static final ClassName SETS_API = ClassName.get(FACTORY_PACKAGE, "Sets");

  public static final ClassName IMMUTABLE_BAG = ClassName.get(BAG_PACKAGE, "ImmutableBag");
  public static final ClassName MUTABLE_BAG = ClassName.get(BAG_PACKAGE, "MutableBag");
  public static final ClassName IMMUTABLE_LIST = ClassName.get(LIST_PACKAGE, "ImmutableList");
  public static final ClassName MUTABLE_LIST = ClassName.get(LIST_PACKAGE, "MutableList");
  public static final ClassName IMMUTABLE_SET = ClassName.get(SET_PACKAGE, "ImmutableSet");
  public static final ClassName MUTABLE_SET = ClassName.get(SET_PACKAGE, "MutableSet");

  protected final ClassName supportedElement;
  protected final boolean immutable;
  protected final ClassName api;

  protected EclipseBaseSingleIterableTraversableGenerator(
      final ClassName supportedElement, final boolean immutable, final ClassName api) {
    this.supportedElement = supportedElement;
    this.immutable = immutable;
    this.api = api;
  }

  @Override
  public String generateOpticExpression() {
    String mutability = immutable ? "immutable" : "mutable";
    return "EachInstances.fromIterableCollecting(list -> "
        + api.simpleName()
        + "."
        + mutability
        + ".ofAll(list))";
  }

  @Override
  public Set<String> getRequiredImports() {
    return Set.of(
        "org.higherkindedj.optics.each.EachInstances", api.packageName() + "." + api.simpleName());
  }

  @Override
  public final boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element.toString().equals(supportedElement.canonicalName());
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
            "final var effectOfConvertBack = applicative.map("
                + "newList -> $T.$L.ofAll(newList), effectOfList)",
            api,
            immutable ? "immutable" : "mutable")

        // 4. Map over the final effect to reconstruct the record with the original type.
        .addStatement(
            "return applicative.map(converted -> new $T($L), effectOfConvertBack)",
            recordClassName,
            constructorArgs)
        .build();
  }
}
