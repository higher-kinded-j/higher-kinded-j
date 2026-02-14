// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.util.Locale;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyKind;

/**
 * Generates code for lens setter lambdas based on copy strategy annotations.
 *
 * <p>This generator creates the setter function {@code (S, A) -> S} for lenses based on the
 * specified copy strategy:
 *
 * <ul>
 *   <li>{@code @ViaBuilder} - uses builder pattern: {@code s.toBuilder().field(a).build()}
 *   <li>{@code @Wither} - uses wither method: {@code s.withField(a)}
 *   <li>{@code @ViaConstructor} - uses constructor: {@code new S(s.f1(), a, s.f3())}
 *   <li>{@code @ViaCopyAndSet} - uses copy and set: {@code S copy = new S(s); copy.setField(a);
 *       return copy;}
 * </ul>
 */
public class CopyStrategyCodeGenerator {

  /**
   * Generates the setter lambda code block for a lens.
   *
   * @param strategy the copy strategy kind
   * @param info the parsed annotation values
   * @param fieldName the field name (derived from optic method name)
   * @param sourceType the source type S
   * @param focusType the focus type A
   * @return the generated code block for the setter lambda
   */
  public CodeBlock generateSetterLambda(
      CopyStrategyKind strategy,
      CopyStrategyInfo info,
      String fieldName,
      TypeMirror sourceType,
      TypeMirror focusType) {

    return switch (strategy) {
      case VIA_BUILDER -> generateBuilderSetter(info, fieldName, sourceType);
      case WITHER -> generateWitherSetter(info, sourceType);
      case VIA_CONSTRUCTOR -> generateConstructorSetter(info, fieldName, sourceType);
      case VIA_COPY_AND_SET -> generateCopyAndSetSetter(info, sourceType);
      case NONE -> throw new IllegalArgumentException("No copy strategy specified for lens");
    };
  }

  /**
   * Generates a builder-pattern setter lambda.
   *
   * <p>Generated code: {@code (source, newValue) -> source.toBuilder().field(newValue).build()}
   *
   * @param info the @ViaBuilder annotation values
   * @param fieldName the field name
   * @param sourceType the source type
   * @return the code block
   */
  private CodeBlock generateBuilderSetter(
      CopyStrategyInfo info, String fieldName, TypeMirror sourceType) {

    String toBuilder = info.toBuilder().isEmpty() ? "toBuilder" : info.toBuilder();
    String setter = info.setter().isEmpty() ? fieldName : info.setter();
    String build = info.build().isEmpty() ? "build" : info.build();

    return CodeBlock.of(
        "(source, newValue) -> source.$L().$L(newValue).$L()", toBuilder, setter, build);
  }

  /**
   * Generates a wither-pattern setter lambda.
   *
   * <p>Generated code: {@code (source, newValue) -> source.withField(newValue)}
   *
   * @param info the @Wither annotation values
   * @param sourceType the source type
   * @return the code block
   */
  private CodeBlock generateWitherSetter(CopyStrategyInfo info, TypeMirror sourceType) {
    String witherMethod = info.witherMethod();
    return CodeBlock.of("(source, newValue) -> source.$L(newValue)", witherMethod);
  }

  /**
   * Generates a constructor-pattern setter lambda.
   *
   * <p>Generated code: {@code (source, newValue) -> new Type(source.f1(), newValue, source.f3())}
   *
   * <p>This generates a statement lambda that calls the constructor. If parameter order is not
   * specified, a simplified version is generated that may need manual adjustment.
   *
   * @param info the @ViaConstructor annotation values
   * @param fieldName the field name being set
   * @param sourceType the source type
   * @return the code block
   */
  private CodeBlock generateConstructorSetter(
      CopyStrategyInfo info, String fieldName, TypeMirror sourceType) {

    TypeName sourceTypeName = TypeName.get(sourceType);
    String[] parameterOrder = info.parameterOrder();

    if (parameterOrder.length == 0) {
      // No parameter order specified - generate a simplified version
      // The user may need to add parameterOrder if this doesn't work
      return CodeBlock.of(
          "(source, newValue) -> {\n"
              + "  // TODO: Specify parameterOrder in @ViaConstructor if this doesn't compile\n"
              + "  throw new $T(\"@ViaConstructor requires parameterOrder for field: $L\");\n"
              + "}",
          UnsupportedOperationException.class,
          fieldName);
    }

    // Build constructor call with proper argument substitution
    CodeBlock.Builder constructorArgs = CodeBlock.builder();
    for (int i = 0; i < parameterOrder.length; i++) {
      if (i > 0) {
        constructorArgs.add(", ");
      }
      String param = parameterOrder[i];
      if (param.equals(fieldName)) {
        constructorArgs.add("newValue");
      } else {
        // Assume getter method matches parameter name
        constructorArgs.add("source.$L()", param);
      }
    }

    return CodeBlock.of(
        "(source, newValue) -> new $T($L)", sourceTypeName, constructorArgs.build());
  }

  /**
   * Generates a copy-and-set setter lambda.
   *
   * <p>Generated code:
   *
   * <pre>{@code
   * (source, newValue) -> {
   *   Type copy = new Type(source);
   *   copy.setField(newValue);
   *   return copy;
   * }
   * }</pre>
   *
   * @param info the @ViaCopyAndSet annotation values
   * @param sourceType the source type
   * @return the code block
   */
  private CodeBlock generateCopyAndSetSetter(CopyStrategyInfo info, TypeMirror sourceType) {
    TypeName sourceTypeName = TypeName.get(sourceType);
    String setter = info.setter();

    // If copyConstructor is specified, use that type; otherwise use source type
    if (!info.copyConstructor().isEmpty()) {
      // Complex case with different copy constructor type - not commonly needed
      return CodeBlock.of(
          "(source, newValue) -> {\n"
              + "  $T copy = new $T(source);\n"
              + "  copy.$L(newValue);\n"
              + "  return copy;\n"
              + "}",
          sourceTypeName,
          sourceTypeName,
          setter);
    }

    return CodeBlock.of(
        "(source, newValue) -> {\n"
            + "  $T copy = new $T(source);\n"
            + "  copy.$L(newValue);\n"
            + "  return copy;\n"
            + "}",
        sourceTypeName,
        sourceTypeName,
        setter);
  }

  /**
   * Generates the getter lambda code block for a lens.
   *
   * @param fieldName the field name (used to derive getter method)
   * @param sourceType the source type S
   * @return the generated code block for the getter lambda
   */
  public CodeBlock generateGetterLambda(String fieldName, TypeMirror sourceType) {
    // Default to record-style accessor (fieldName)
    return CodeBlock.of("source -> source.$L()", fieldName);
  }

  /**
   * Generates the getter lambda code block for a lens with copy strategy info.
   *
   * <p>If the copy strategy specifies an explicit getter, use it. Otherwise, fall back to using the
   * field name (record-style accessor).
   *
   * @param fieldName the field name (used to derive getter method if not specified)
   * @param info the copy strategy info (may contain explicit getter)
   * @param sourceType the source type S
   * @return the generated code block for the getter lambda
   */
  public CodeBlock generateGetterLambda(
      String fieldName, CopyStrategyInfo info, TypeMirror sourceType) {
    String getter = info.getter().isEmpty() ? fieldName : info.getter();
    return CodeBlock.of("source -> source.$L()", getter);
  }

  /**
   * Determines the getter method name for a field.
   *
   * @param fieldName the field name
   * @return the most likely getter method name
   */
  public String getterMethodName(String fieldName) {
    // Default to record-style accessor
    return fieldName;
  }

  /**
   * Determines the JavaBean-style getter method name for a field.
   *
   * @param fieldName the field name
   * @return the JavaBean getter method name
   */
  public String javaBeanGetterMethodName(String fieldName) {
    return "get" + capitalise(fieldName);
  }

  private String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
  }
}
