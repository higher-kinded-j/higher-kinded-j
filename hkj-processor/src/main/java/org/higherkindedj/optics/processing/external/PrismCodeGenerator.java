// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.processing.external.SpecAnalysis.PrismHintInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.PrismHintKind;

/**
 * Generates code for prism optics based on prism hint annotations.
 *
 * <p>This generator creates prism code based on the specified hint:
 *
 * <ul>
 *   <li>{@code @InstanceOf} - uses instanceof pattern matching
 *   <li>{@code @MatchWhen} - uses predicate/getter methods
 * </ul>
 */
public class PrismCodeGenerator {

  private static final ClassName PRISM_CLASS = ClassName.get(Prism.class);
  private static final ClassName OPTIONAL_CLASS = ClassName.get(Optional.class);

  /**
   * Generates the prism code block.
   *
   * @param hintKind the prism hint kind
   * @param info the parsed annotation values
   * @param sourceType the source type S
   * @param focusType the focus type A
   * @return the generated code block for creating the prism
   */
  public CodeBlock generatePrismCode(
      PrismHintKind hintKind, PrismHintInfo info, TypeMirror sourceType, TypeMirror focusType) {

    return switch (hintKind) {
      case INSTANCE_OF -> generateInstanceOfPrism(info, sourceType, focusType);
      case MATCH_WHEN -> generateMatchWhenPrism(info, sourceType, focusType);
      case NONE -> throw new IllegalArgumentException("No prism hint specified");
    };
  }

  /**
   * Generates an instanceof-based prism.
   *
   * <p>Generated code:
   *
   * <pre>{@code
   * Prism.of(
   *     source -> source instanceof SubType s ? Optional.of(s) : Optional.empty(),
   *     subtype -> subtype
   * )
   * }</pre>
   *
   * @param info the @InstanceOf annotation values
   * @param sourceType the source type
   * @param focusType the focus/target type
   * @return the code block
   */
  private CodeBlock generateInstanceOfPrism(
      PrismHintInfo info, TypeMirror sourceType, TypeMirror focusType) {

    TypeMirror targetType = info.targetType();
    TypeName targetTypeName = TypeName.get(targetType != null ? targetType : focusType);

    // Use pattern matching instanceof (Java 16+)
    return CodeBlock.builder()
        .add("$T.of(\n", PRISM_CLASS)
        .indent()
        .add(
            "source -> source instanceof $T t ? $T.of(t) : $T.empty(),\n",
            targetTypeName,
            OPTIONAL_CLASS,
            OPTIONAL_CLASS)
        .add("subtype -> subtype")
        .unindent()
        .add(")")
        .build();
  }

  /**
   * Generates a predicate-based prism.
   *
   * <p>Generated code:
   *
   * <pre>{@code
   * Prism.of(
   *     source -> source.isElement() ? Optional.of(source.asElement()) : Optional.empty(),
   *     element -> element
   * )
   * }</pre>
   *
   * @param info the @MatchWhen annotation values
   * @param sourceType the source type
   * @param focusType the focus type
   * @return the code block
   */
  private CodeBlock generateMatchWhenPrism(
      PrismHintInfo info, TypeMirror sourceType, TypeMirror focusType) {

    String predicate = info.predicate();
    String getter = info.getter();

    return CodeBlock.builder()
        .add("$T.of(\n", PRISM_CLASS)
        .indent()
        .add(
            "source -> source.$L() ? $T.of(source.$L()) : $T.empty(),\n",
            predicate,
            OPTIONAL_CLASS,
            getter,
            OPTIONAL_CLASS)
        .add("value -> value")
        .unindent()
        .add(")")
        .build();
  }

  /**
   * Generates the return statement for a prism method.
   *
   * @param hintKind the prism hint kind
   * @param info the parsed annotation values
   * @param sourceType the source type S
   * @param focusType the focus type A
   * @return the code block for the return statement
   */
  public CodeBlock generatePrismReturnStatement(
      PrismHintKind hintKind, PrismHintInfo info, TypeMirror sourceType, TypeMirror focusType) {

    return CodeBlock.builder()
        .add("return ")
        .add(generatePrismCode(hintKind, info, sourceType, focusType))
        .add(";")
        .build();
  }
}
