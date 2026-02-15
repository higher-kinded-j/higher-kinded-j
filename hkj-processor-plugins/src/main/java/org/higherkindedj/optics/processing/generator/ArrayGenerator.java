// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.util.Traversals;

/**
 * A {@link org.higherkindedj.optics.processing.spi.TraversableGenerator} that adds support for
 * traversing native Java arrays (e.g., {@code String[]}, {@code int[]}).
 *
 * <p>This class is discovered by the {@code TraversalProcessor} using the Java ServiceLoader
 * mechanism.
 */
public class ArrayGenerator extends BaseTraversableGenerator {

  /** Supports any type that is an instance of {@link ArrayType}. */
  @Override
  public boolean supports(final TypeMirror type) {
    return type instanceof ArrayType;
  }

  /** Generates the body of the `modifyF` method for a traversal over an array. */
  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final TypeName componentTypeName = getGenericTypeName(component); // e.g., String for String[]

    // Use the inherited helper to generate constructor args.
    // The new value is in a variable named `newArray`.
    final String constructorArgs =
        generateConstructorArgs(componentName, "newArray", allComponents);

    return CodeBlock.builder()
        // 1. Get the source array and convert it to a List to use the traverseList helper.
        .addStatement(
            "final var sourceList = $T.stream(source.$L()).collect($T.toList())",
            Arrays.class,
            componentName,
            Collectors.class)

        // 2. Call the static helper to traverse the list, yielding Kind<F, List<B>>.
        .addStatement(
            "final var effectOfList = $T.traverseList(sourceList, f, applicative)",
            Traversals.class)

        // 3. Map over the effect to convert the inner List back to an array.
        .addStatement(
            "final var effectOfArray = applicative.map("
                + "newList -> newList.toArray(size -> new $T[size]), effectOfList)",
            componentTypeName)

        // 4. Map over the final effect to reconstruct the record with the new array.
        .addStatement(
            "return applicative.map(newArray -> new $T($L), effectOfArray)",
            recordClassName,
            constructorArgs)
        .build();
  }

  /**
   * Gets the component type of an array. This overrides the base implementation to handle {@link
   * ArrayType}.
   *
   * @param component The record component which must be an array type.
   * @return The {@link TypeName} of the array's component type (e.g., {@code String} for a {@code
   *     String[]}).
   */
  @Override
  protected TypeName getGenericTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof ArrayType arrayType) {
      return TypeName.get(arrayType.getComponentType()).box();
    }
    return ClassName.get(Object.class); // Fallback
  }
}
