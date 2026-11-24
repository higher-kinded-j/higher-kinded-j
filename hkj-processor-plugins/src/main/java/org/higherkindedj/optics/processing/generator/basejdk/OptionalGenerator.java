// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.basejdk;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import io.avaje.spi.ServiceProvider;
import java.util.List;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.processing.spi.Cardinality;
import org.higherkindedj.optics.processing.spi.GenerationContext;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@link
 * java.util.Optional}.
 *
 * <p>This class is discovered by the {@code TraversalProcessor} using the Java ServiceLoader
 * mechanism. It is responsible for generating the logic to traverse an {@code Optional} field
 * within a record, applying an effectful function only if the {@code Optional} is present.
 *
 * <p>{@code @GenerateFocus} recognises {@code Optional} by name and widens it through the
 * expression {@code WideningAnalysis} writes for it, so this generator supplies no optic expression
 * of its own.
 */
@ServiceProvider(TraversableGenerator.class)
public class OptionalGenerator extends BaseTraversableGenerator {

  /** Creates a new generator for {@link java.util.Optional} fields. */
  public OptionalGenerator() {}

  private static final String FQN_OPTIONAL = "java.util.Optional";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) return false;
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_OPTIONAL);
  }

  @Override
  public Cardinality getCardinality() {
    return Cardinality.ZERO_OR_ONE;
  }

  /** {@inheritDoc} Final, as hkj's processors call the {@link GenerationContext} form. */
  @Override
  public final CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {
    // A caller that names no package gets the record's own, which is where a companion lands
    // unless a targetPackage or an import sends it elsewhere.
    return generateModifyF(
        component, recordClassName, allComponents, recordClassName.packageName());
  }

  /**
   * {@inheritDoc} Final, as hkj's processors call the {@link GenerationContext} form; a type-use
   * annotation deprecated by a javadoc tag alone is kept here, as this form reads
   * {@code @Deprecated} alone.
   */
  @Override
  public final CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents,
      final String targetPackage) {
    return modifyF(
        component,
        recordClassName,
        allComponents,
        type -> ProcessorUtils.typeNameOf(type, targetPackage));
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents,
      final GenerationContext context) {
    return modifyF(component, recordClassName, allComponents, context::typeNameOf);
  }

  /** The body each form writes, with every type it names named by {@code naming}. */
  private CodeBlock modifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents,
      final Function<TypeMirror, TypeName> naming) {

    final String componentName = component.getSimpleName().toString();

    // Use the inherited helper method to generate the constructor arguments.
    // The new value is wrapped in Optional.of().
    final String constructorArgs =
        generateConstructorArgs(componentName, "Optional.of(newValue)", allComponents);

    return CodeBlock.builder()
        // Directly use the concrete Optional from the source record.
        .addStatement(
            "final $T optional = source.$L()", naming.apply(component.asType()), componentName)
        .beginControlFlow("if (optional.isPresent())")
        // If present, apply the effectful function.
        .addStatement("final var g_of_b = f.apply(optional.get())")
        // The result of the map now reconstructs the record.
        .addStatement(
            "return applicative.map(newValue -> new $T($L), g_of_b)",
            recordTypeName(component, recordClassName),
            constructorArgs)
        .nextControlFlow("else")
        // If empty, lift the *original* source record into the applicative, as it's unchanged.
        .addStatement("return applicative.of(source)")
        .endControlFlow()
        .build();
  }
}
