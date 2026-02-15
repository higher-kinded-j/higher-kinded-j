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
import org.higherkindedj.hkt.either.Either;

public class EitherGenerator extends BaseTraversableGenerator {

  private static final String FQN_EITHER = "org.higherkindedj.hkt.either.Either";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element != null && element.toString().equals(FQN_EITHER);
  }

  @Override
  public int getFocusTypeArgumentIndex() {
    return 1; // Either<L, R> focuses on R (the second type argument)
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final TypeName leftTypeName = getLeftTypeName(component);
    final TypeName rightTypeName = getRightTypeName(component);

    final String constructorArgs =
        generateConstructorArgs(componentName, "Either.right(newValue)", allComponents);

    return CodeBlock.builder()
        .addStatement(
            "final $T<$T, $T> either = source.$L()",
            Either.class,
            leftTypeName,
            rightTypeName,
            componentName)
        .beginControlFlow("if (either.isRight())")
        .addStatement("final var g_of_b = f.apply(either.getRight())")
        .addStatement(
            "@SuppressWarnings(\"unchecked\") final var g_of_b_casted = ($T) g_of_b",
            ParameterizedTypeName.get(
                ClassName.get(Kind.class), TypeVariableName.get("F"), rightTypeName.box()))
        .addStatement(
            "return applicative.map(newValue -> new $T($L), g_of_b_casted)",
            recordClassName,
            constructorArgs)
        .nextControlFlow("else")
        .addStatement("return applicative.of(source)")
        .endControlFlow()
        .build();
  }

  private TypeName getRightTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof DeclaredType containerType) {
      if (containerType.getTypeArguments().size() < 2) {
        return ClassName.get(Object.class);
      }
      return TypeName.get(containerType.getTypeArguments().get(1));
    }
    return ClassName.get(Object.class);
  }

  private TypeName getLeftTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof DeclaredType containerType) {
      if (containerType.getTypeArguments().isEmpty()) {
        return ClassName.get(Object.class);
      }
      return TypeName.get(containerType.getTypeArguments().getFirst());
    }
    return ClassName.get(Object.class);
  }
}
