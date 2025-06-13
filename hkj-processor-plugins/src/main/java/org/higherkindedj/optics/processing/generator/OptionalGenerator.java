// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@link
 * java.util.Optional}.
 *
 * <p>This class is discovered by the {@code TraversalProcessor} using the Java ServiceLoader
 * mechanism. It is responsible for generating the logic to traverse an {@code Optional} field
 * within a record, applying an effectful function only if the {@code Optional} is present.
 */
public class OptionalGenerator extends BaseTraversableGenerator {

  private static final String FQN_OPTIONAL = "java.util.Optional";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) return false;
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_OPTIONAL);
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final TypeName genericTypeName = getGenericTypeName(component);

    // Use the inherited helper method to generate the constructor arguments.
    // The new value is wrapped in Optional.of().
    final String constructorArgs =
        generateConstructorArgs(componentName, "Optional.of(newValue)", allComponents);

    return CodeBlock.builder()
        // Directly use the concrete Optional from the source record.
        .addStatement(
            "final $T<$T> optional = source.$L()", Optional.class, genericTypeName, componentName)
        .beginControlFlow("if (optional.isPresent())")
        // If present, apply the effectful function.
        .addStatement("final var g_of_b = f.apply(optional.get())")
        // The cast is necessary because of Java's limitations with higher-kinded types.
        .addStatement(
            "@SuppressWarnings(\"unchecked\") final var g_of_b_casted = ($T) g_of_b",
            ParameterizedTypeName.get(
                ClassName.get(Kind.class), TypeVariableName.get("F"), genericTypeName.box()))
        // The result of the map now reconstructs the record.
        .addStatement(
            "return applicative.map(newValue -> new $T($L), g_of_b_casted)",
            recordClassName,
            constructorArgs)
        .nextControlFlow("else")
        // If empty, lift the *original* source record into the applicative, as it's unchanged.
        .addStatement("return applicative.of(source)")
        .endControlFlow()
        .build();
  }
}
