// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.basejdk;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import io.avaje.spi.ServiceProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.util.Traversals;

/**
 * A {@link org.higherkindedj.optics.processing.spi.TraversableGenerator} that adds support for
 * traversing native Java arrays (e.g., {@code String[]}, {@code int[]}).
 *
 * <p>This class is discovered by the {@code TraversalProcessor} using the Java ServiceLoader
 * mechanism.
 */
@ServiceProvider(TraversableGenerator.class)
public class ArrayGenerator extends BaseTraversableGenerator {

  /** Creates a new generator for native Java array fields. */
  public ArrayGenerator() {}

  /** Supports any type that is an instance of {@link ArrayType}. */
  @Override
  public boolean supports(final TypeMirror type) {
    return type instanceof ArrayType;
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.arrayEach()";
  }

  @Override
  public Set<String> getRequiredImports() {
    return Set.of("org.higherkindedj.optics.each.EachInstances");
  }

  /** Generates the body of the `modifyF` method for a traversal over an array. */
  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();
    final TypeName elementType = elementType(component);
    final TypeName focusType = elementType.box();

    // The new value is in a variable named `newArray`.
    final String constructorArgs =
        generateConstructorArgs(componentName, "newArray", allComponents);

    return CodeBlock.builder()
        // 1. Read the source array into a List, so that the traverseList helper can be used.
        .add(readIntoList(componentName, elementType, focusType))

        // 2. Call the static helper to traverse the list, yielding Kind<F, List<B>>.
        .addStatement(
            "final var effectOfList = $T.traverseList(sourceList, f, applicative)",
            Traversals.class)

        // 3. Map over the effect to put the traversed elements back into an array.
        .add(rebuildArray(componentName, elementType))

        // 4. Map over the final effect to reconstruct the record with the new array.
        .addStatement(
            "return applicative.map(newArray -> new $T($L), effectOfArray)",
            recordTypeName(component, recordClassName),
            constructorArgs)
        .build();
  }

  /**
   * Reads the source array into a {@code List} of the traversal's focus type.
   *
   * <p>A primitive array is walked and boxed an element at a time: {@code Arrays.stream} has an
   * overload for only three of the eight primitive types, and the two it does have return a stream
   * that cannot collect into a {@code List}.
   */
  private CodeBlock readIntoList(
      final String componentName, final TypeName elementType, final TypeName focusType) {
    if (!elementType.isPrimitive()) {
      return CodeBlock.builder()
          .addStatement(
              "final var sourceList = $T.stream(source.$L()).collect($T.toList())",
              Arrays.class,
              componentName,
              Collectors.class)
          .build();
    }
    return CodeBlock.builder()
        .addStatement("final var sourceArray = source.$L()", componentName)
        .addStatement(
            "final $T<$T> sourceList = new $T<>(sourceArray.length)",
            List.class,
            focusType,
            ArrayList.class)
        .beginControlFlow("for (final $T element : sourceArray)", elementType)
        .addStatement("sourceList.add(element)")
        .endControlFlow()
        .build();
  }

  /**
   * Puts the traversed elements back into an array of the component's own type.
   *
   * <p>Which array to allocate depends on what the element type is. A class can be named in a
   * {@code new} expression, and naming it keeps the array wide enough to hold whatever the
   * traversal produced. A primitive can be named too, and its elements unbox on the way in.
   * Anything else — a parameterised type, a type variable, an array — cannot be created by name at
   * all, so the source array is copied to length instead, which keeps its runtime component type.
   */
  private CodeBlock rebuildArray(final String componentName, final TypeName elementType) {
    if (elementType instanceof ClassName) {
      return CodeBlock.builder()
          .addStatement(
              "final var effectOfArray = applicative.map("
                  + "newList -> newList.toArray(size -> new $T[size]), effectOfList)",
              elementType)
          .build();
    }
    if (elementType.isPrimitive()) {
      return fill(
          CodeBlock.of("final $T[] rebuilt = new $T[newList.size()]", elementType, elementType));
    }
    return fill(
        CodeBlock.of(
            "final $T[] rebuilt = $T.copyOf(source.$L(), newList.size())",
            elementType,
            Arrays.class,
            componentName));
  }

  /** Allocates the array, copies the traversed elements into it, and hands it back. */
  private CodeBlock fill(final CodeBlock allocation) {
    return CodeBlock.builder()
        .add("final var effectOfArray =$>$>\n")
        .add("applicative.map($>$>\n")
        .add("newList -> {$>\n")
        .addStatement(allocation)
        .beginControlFlow("for (int index = 0; index < rebuilt.length; index++)")
        .addStatement("rebuilt[index] = newList.get(index)")
        .endControlFlow()
        .addStatement("return rebuilt")
        .add("$<},\n")
        .add("effectOfList);$<$<$<$<\n")
        .build();
  }

  /**
   * The array's element type, unboxed: {@code int} for an {@code int[]}, {@code String} for a
   * {@code String[]}. The base implementation reads a type argument, which an array does not have.
   */
  private TypeName elementType(final RecordComponentElement component) {
    if (component.asType() instanceof ArrayType arrayType) {
      return TypeName.get(arrayType.getComponentType());
    }
    return ClassName.get(Object.class); // Fallback
  }
}
