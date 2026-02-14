// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.external.SpecAnalysis.TraversalHintInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.TraversalHintKind;

/**
 * Generates code for traversal optics based on traversal hint annotations.
 *
 * <p>This generator creates traversal code based on the specified hint:
 *
 * <ul>
 *   <li>{@code @TraverseWith} - uses an explicit traversal reference
 *   <li>{@code @ThroughField} - composes a lens to a field with a traversal for the field's
 *       container type
 * </ul>
 */
public class TraversalCodeGenerator {

  // Standard traversal references - these must match actual method names in Traversals class
  private static final String LIST_TRAVERSAL = "org.higherkindedj.optics.util.Traversals.forList()";
  private static final String SET_TRAVERSAL = "org.higherkindedj.optics.util.Traversals.forSet()";
  private static final String OPTIONAL_TRAVERSAL =
      "org.higherkindedj.optics.util.Traversals.forOptional()";
  private static final String ARRAY_TRAVERSAL =
      "org.higherkindedj.optics.util.Traversals.forArray()";
  private static final String MAP_VALUES_TRAVERSAL =
      "org.higherkindedj.optics.util.Traversals.forMapValues()";

  /**
   * Generates the traversal code block.
   *
   * @param hintKind the traversal hint kind
   * @param info the parsed annotation values
   * @param sourceType the source type S
   * @param focusType the focus type A
   * @param specClassName the name of the spec class (for field lens references)
   * @return the generated code block for creating the traversal
   */
  public CodeBlock generateTraversalCode(
      TraversalHintKind hintKind,
      TraversalHintInfo info,
      TypeMirror sourceType,
      TypeMirror focusType,
      String specClassName) {

    return switch (hintKind) {
      case TRAVERSE_WITH -> generateTraverseWithCode(info);
      case THROUGH_FIELD -> generateThroughFieldCode(info, specClassName);
      case NONE -> throw new IllegalArgumentException("No traversal hint specified");
    };
  }

  /**
   * Generates code using an explicit traversal reference.
   *
   * <p>Generated code: {@code org.higherkindedj.optics.Traversals.list()}
   *
   * @param info the @TraverseWith annotation values
   * @return the code block
   */
  private CodeBlock generateTraverseWithCode(TraversalHintInfo info) {
    String traversalReference = info.traversalReference();

    // Parse the reference to generate proper code
    // The reference could be:
    // - A method call: "org.package.Class.method()"
    // - A field reference: "org.package.Class.INSTANCE"
    if (traversalReference.endsWith("()")) {
      // Method call
      return CodeBlock.of("$L", traversalReference);
    } else {
      // Field reference
      return CodeBlock.of("$L", traversalReference);
    }
  }

  /**
   * Generates code composing a field lens with a container traversal.
   *
   * <p>Generated code: {@code SpecClass.fieldName().andThen((Traversal) Traversals.forList())}
   *
   * <p>Note: The traversal is now auto-detected by {@code SpecInterfaceAnalyser} if not explicitly
   * specified, so the traversal parameter should always be populated.
   *
   * <p>The unchecked cast is necessary because the lens may focus on a container subtype (e.g.,
   * ArrayList) while the traversal is typed for the supertype (e.g., List). Java's invariant
   * generics require this cast, but it's safe at runtime because subtypes can be traversed using
   * the supertype's traversal.
   *
   * @param info the @ThroughField annotation values
   * @param specClassName the name of the spec class
   * @return the code block
   */
  private CodeBlock generateThroughFieldCode(TraversalHintInfo info, String specClassName) {
    String fieldName = info.fieldName();
    String traversal = info.fieldTraversal();

    // Traversal should always be populated by SpecInterfaceAnalyser (either explicit or
    // auto-detected)
    if (traversal.isEmpty()) {
      throw new IllegalStateException(
          "Traversal not specified for @ThroughField(field = \""
              + fieldName
              + "\"). "
              + "This should have been auto-detected by SpecInterfaceAnalyser.");
    }

    // Use unchecked cast for container subtypes (e.g., ArrayList for List)
    return CodeBlock.of(
        "$L.$L().andThen(($T) $L)",
        specClassName,
        fieldName,
        ClassName.get("org.higherkindedj.optics", "Traversal"),
        traversal);
  }

  /**
   * Generates the return statement for a traversal method.
   *
   * @param hintKind the traversal hint kind
   * @param info the parsed annotation values
   * @param sourceType the source type S
   * @param focusType the focus type A
   * @param specClassName the name of the spec class
   * @return the code block for the return statement
   */
  public CodeBlock generateTraversalReturnStatement(
      TraversalHintKind hintKind,
      TraversalHintInfo info,
      TypeMirror sourceType,
      TypeMirror focusType,
      String specClassName) {

    return CodeBlock.builder()
        .add("return ")
        .add(generateTraversalCode(hintKind, info, sourceType, focusType, specClassName))
        .add(";")
        .build();
  }

  /**
   * Returns the standard traversal reference for a container type.
   *
   * @param containerKind the kind of container
   * @return the traversal reference string
   */
  public String getStandardTraversal(ContainerType.Kind containerKind) {
    return switch (containerKind) {
      case LIST -> LIST_TRAVERSAL;
      case SET -> SET_TRAVERSAL;
      case OPTIONAL -> OPTIONAL_TRAVERSAL;
      case ARRAY -> ARRAY_TRAVERSAL;
      case MAP -> MAP_VALUES_TRAVERSAL;
    };
  }
}
