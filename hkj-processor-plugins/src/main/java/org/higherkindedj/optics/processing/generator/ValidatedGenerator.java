// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@link Validated}.
 * This version uses a robust if/else block to avoid type inference issues.
 */
public class ValidatedGenerator extends BaseTraversableGenerator {

  private static final String FQN_VALIDATED = "org.higherkindedj.hkt.validated.Validated";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_VALIDATED);
  }

  @Override
  public int getFocusTypeArgumentIndex() {
    return 1; // Validated<E, A> focuses on A (the second type argument)
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final TypeName errorTypeName = getErrorTypeName(component);
    final TypeName genericTypeName = getGenericTypeName(component);

    // Use the inherited helper to generate the constructor arguments.
    // The new value is wrapped in Validated.valid().
    final String constructorArgs =
        generateConstructorArgs(componentName, "Validated.valid(newValue)", allComponents);

    return CodeBlock.builder()
        // Directly use the concrete Validated type from the source record.
        .addStatement(
            "final $T<$T, $T> validated = source.$L()",
            Validated.class,
            errorTypeName,
            genericTypeName,
            componentName)
        .beginControlFlow("if (validated.isValid())")
        // If Valid, apply the effectful function.
        .addStatement("final var g_of_b = f.apply(validated.get())")
        .addStatement(
            "@SuppressWarnings(\"unchecked\") final var g_of_b_casted = ($T) g_of_b",
            ParameterizedTypeName.get(
                ClassName.get(Kind.class), TypeVariableName.get("F"), genericTypeName.box()))
        // Map the result to reconstruct the parent record with the new Validated value.
        .addStatement(
            "return applicative.map(newValue -> new $T($L), g_of_b_casted)",
            recordClassName,
            constructorArgs)
        .nextControlFlow("else")
        // If Invalid, the traversal has no effect. Return the original source record lifted into
        // the applicative.
        .addStatement("return applicative.of(source)")
        .endControlFlow()
        .build();
  }

  /**
   * Gets the 'valid' type from a {@code Validated<E, A>} component. This overrides the base
   * implementation to get the second generic argument.
   */
  @Override
  protected TypeName getGenericTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof DeclaredType containerType) {
      if (containerType.getTypeArguments().size() < 2) {
        return ClassName.get(Object.class); // Fallback
      }
      // For Validated<E, A>, the 'valid' type A is the second argument.
      return TypeName.get(containerType.getTypeArguments().get(1));
    }
    return ClassName.get(Object.class);
  }

  /** Gets the 'error' type from a {@code Validated<E, A>} component. */
  private TypeName getErrorTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof DeclaredType containerType) {
      if (containerType.getTypeArguments().isEmpty()) {
        return ClassName.get(Object.class); // Fallback
      }
      // For Validated<E, A>, the 'error' type E is the first argument.
      return TypeName.get(containerType.getTypeArguments().getFirst());
    }
    return ClassName.get(Object.class);
  }
}
