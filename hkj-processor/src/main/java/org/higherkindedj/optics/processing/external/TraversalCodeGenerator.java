// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
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

  /** Creates a new TraversalCodeGenerator. */
  public TraversalCodeGenerator() {}

  // Standard traversal references - these must match actual method names in Traversals class
  private static final String LIST_TRAVERSAL = "org.higherkindedj.optics.util.Traversals.forList()";
  private static final String SET_TRAVERSAL = "org.higherkindedj.optics.util.Traversals.forSet()";
  private static final String COLLECTION_TRAVERSAL =
      "org.higherkindedj.optics.util.Traversals.forCollection()";
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
   * @param specClassName the name of the spec class (for field lens references)
   * @return the generated code block for creating the traversal
   */
  public CodeBlock generateTraversalCode(
      TraversalHintKind hintKind, TraversalHintInfo info, String specClassName) {

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
   * <p>{@code SpecInterfaceAnalyser} supplies the traversal, auto-detected from the field's type
   * where the annotation names none, so it is always populated here.
   *
   * <p>An auto-detected traversal over a denotable lens focus composes without a cast: the lens
   * focus is exactly the container interface the standard traversal rebuilds, so {@code
   * Lens.andThen(Traversal)} infers unaided and javac checks both halves, the lens focus against
   * the traversal's source and the container's element against the method's declared focus.
   *
   * <p>The raw cast remains for what the processor cannot type-check: an explicit {@code traversal}
   * string, which is the author's undertaking that theirs rebuilds the declared type, and an
   * auto-detected traversal over a lens focus whose own type arguments carry a wildcard, an
   * instantiation the composition cannot name (a nested wildcard, {@code List<List<?>>}, is
   * denotable and stays checked). Both agree with the lens at erasure, auto-detection having
   * accepted the interface types alone (#773); the element-against-focus half is checked at the
   * declaration by {@code SpecInterfaceAnalyser} in either case.
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

    // Raw: this composition hands the processor something it cannot type-check. The checked
    // composition never reaches here; generateTraversalReturnStatement emits its body.
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
   * @param specClassName the name of the spec class
   * @param lensReturnType the checked local's type, or null for the cast path
   * @return the code block for the return statement
   */
  public CodeBlock generateTraversalReturnStatement(
      TraversalHintKind hintKind,
      TraversalHintInfo info,
      String specClassName,
      TypeName lensReturnType) {

    // The checked composition holds the lens in a local declared with the sibling lens method's
    // own return type: the generated lens is a generic method, and called bare as a receiver it
    // would infer against nothing. The assignment target-types the call, and the return
    // statement hands javac both halves of the composition to check. One value drives both the
    // shape and the suppression: a null lensReturnType is the cast path.
    if (hintKind == TraversalHintKind.THROUGH_FIELD && lensReturnType != null) {
      return CodeBlock.builder()
          .addStatement("$T lens = $L.$L()", lensReturnType, specClassName, info.fieldName())
          .addStatement("return lens.andThen($L)", info.fieldTraversal())
          .build();
    }

    return CodeBlock.builder()
        .add("return ")
        .add(generateTraversalCode(hintKind, info, specClassName))
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
      case COLLECTION -> COLLECTION_TRAVERSAL;
      case OPTIONAL -> OPTIONAL_TRAVERSAL;
      case ARRAY -> ARRAY_TRAVERSAL;
      case MAP -> MAP_VALUES_TRAVERSAL;
    };
  }
}
