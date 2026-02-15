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
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@link Maybe}.
 *
 * <p>This class is discovered by the {@code TraversalProcessor} using the Java ServiceLoader
 * mechanism.
 */
public class MaybeGenerator extends BaseTraversableGenerator {

  private static final String FQN_MAYBE = "org.higherkindedj.hkt.maybe.Maybe";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_MAYBE);
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final TypeName genericTypeName = getGenericTypeName(component);

    // Use the inherited helper to generate the constructor arguments.
    // The new value is wrapped in Maybe.just().
    final String constructorArgs =
        generateConstructorArgs(componentName, "Maybe.just(newValue)", allComponents);

    return CodeBlock.builder()
        // Directly use the concrete Maybe from the source record.
        .addStatement(
            "final $T<$T> maybe = source.$L()", Maybe.class, genericTypeName, componentName)
        .beginControlFlow("if (maybe.isJust())")
        // If Just, apply the effectful function.
        .addStatement("final var g_of_b = f.apply(maybe.get())")
        .addStatement(
            "@SuppressWarnings(\"unchecked\") final var g_of_b_casted = ($T) g_of_b",
            ParameterizedTypeName.get(
                ClassName.get(Kind.class), TypeVariableName.get("F"), genericTypeName.box()))
        .addStatement(
            "return applicative.map(newValue -> new $T($L), g_of_b_casted)",
            recordClassName,
            constructorArgs)
        .nextControlFlow("else")
        .addStatement("return applicative.of(source)")
        .endControlFlow()
        .build();
  }
}
