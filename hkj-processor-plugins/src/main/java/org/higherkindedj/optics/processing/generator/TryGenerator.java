// Copyright (c) 2025 Magnus Smith
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
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@link Try}. This
 * version uses the safe `fold` method to avoid unhandled checked exceptions.
 */
public class TryGenerator extends BaseTraversableGenerator {

  private static final String FQN_TRY = "org.higherkindedj.hkt.trymonad.Try";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_TRY);
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final TypeName genericTypeName = getGenericTypeName(component);

    // Use the inherited helper to generate the constructor arguments.
    // The new value is wrapped in Try.success().
    final String constructorArgs =
        generateConstructorArgs(componentName, "Try.success(newValue)", allComponents);

    return CodeBlock.builder()
        .addStatement("final $T<$T> tryA = source.$L()", Try.class, genericTypeName, componentName)
        // Use the safe `fold` method to handle both cases without throwing exceptions.
        .addStatement(
            "return tryA.fold(\n"
                + "    successValue -> {$>\n"
                + "        // Case 1: The Try is a Success. Apply the effectful function.\n"
                + "        final var g_of_b = f.apply(successValue);\n"
                + "        @SuppressWarnings(\"unchecked\") final var g_of_b_casted = ($T)"
                + " g_of_b;\n"
                + "        // Map over the effect to reconstruct the record with the new value.\n"
                + "        return applicative.map(newValue -> new $T($L), g_of_b_casted);$<\n"
                + "}, \n"
                + "    cause -> {$>\n"
                + "        // Case 2: The Try is a Failure. The traversal has no effect.\n"
                + "        // Return the original, unchanged source record lifted into the"
                + " applicative.\n"
                + "        return applicative.of(source);\n"
                + "    }\n"
                + ")",
            // Type for the g_of_b_casted variable
            ParameterizedTypeName.get(
                ClassName.get(Kind.class), TypeVariableName.get("F"), genericTypeName.box()),
            // The record's class name for reconstruction
            recordClassName,
            // The argument list for the record's constructor
            constructorArgs)
        .build();
  }
}
