// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import org.higherkindedj.optics.Prism;

/**
 * The prism factory method for one permitted subtype of a sealed type.
 *
 * <p>{@code @GeneratePrisms} writes this method into a companion class beside the sealed type, and
 * {@code @ImportOptics} writes the same method into a generated class beside the spec. The two
 * differ only in which messager reports a rejection and which annotation the diagnostic names, so
 * they ask for the method here rather than each holding a copy: the wording of the rejection is
 * asserted by tests on both sides, and two copies would let it drift on one.
 */
public final class SubtypePrismGenerator {

  private SubtypePrismGenerator() {}

  /**
   * Builds the prism factory method for a permitted subtype, or reports why it cannot be written.
   *
   * <p>The prism is written in the subtype's vocabulary: its own type parameters, and the sum type
   * as the subtype's own extends/implements clause instantiates it. Reading the sum type's
   * declaration instead would name variables the method never declares.
   *
   * @param messager the round's messager, for a rejection
   * @param tag the annotation tag naming the caller, for the diagnostic
   * @param sumType the sealed type
   * @param subtype the permitted subtype to focus on
   * @return the factory method, or null when the subtype was rejected and an error reported
   */
  public static MethodSpec prismMethodFor(
      Messager messager, String tag, TypeElement sumType, TypeElement subtype) {

    String methodName = ProcessorUtils.toCamelCase(subtype.getSimpleName().toString());
    DeclaredType namedSumType = ProcessorUtils.sumTypeAsNamedBy(sumType, subtype);
    TypeName sourceTypeName = ProcessorUtils.typeNameOf(namedSumType);
    if (rejectsUnboundParameter(messager, tag, sumType, subtype, namedSumType)) {
      return null;
    }

    TypeName subTypeName =
        subtype.getTypeParameters().isEmpty()
            ? ClassName.get(subtype)
            : ParameterizedTypeName.get(
                ClassName.get(subtype),
                subtype.getTypeParameters().stream()
                    .map(ProcessorUtils::typeVariableOf)
                    .toArray(TypeName[]::new));

    ParameterizedTypeName prismTypeName =
        ParameterizedTypeName.get(ClassName.get(Prism.class), sourceTypeName, subTypeName);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addJavadoc(
                "Creates a {@link $T} that focuses on the {@link $T} subtype of the {@link $T} sum"
                    + " type.\n\n"
                    + "@return A non-null {@code Prism<$T, $T>}.",
                Prism.class,
                ClassName.get(subtype),
                ClassName.get(sumType),
                sourceTypeName,
                subTypeName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(prismTypeName);

    for (TypeParameterElement typeParameter : subtype.getTypeParameters()) {
      methodBuilder.addTypeVariable(ProcessorUtils.typeVariableOf(typeParameter));
    }

    return methodBuilder
        .addStatement(
            "return $T.of(source -> source instanceof $T ? $T.of(($T) source) : $T.empty(), value"
                + " -> value)",
            Prism.class,
            ClassName.get(subtype),
            Optional.class,
            subTypeName,
            Optional.class)
        .build();
  }

  /**
   * Reports a permitted subtype the sum type cannot pin, and returns whether it did.
   *
   * <p>A prism narrows by {@code instanceof}, which tests an erasure. Where the subtype's clause
   * binds every one of its parameters - {@code Circle<T> implements Shape<T>} - the hierarchy pins
   * them and the cast is one javac proves. A parameter the clause leaves free is pinned by nothing,
   * so two callers can read one value at different types and the second gets a {@link
   * ClassCastException} from a call site that compiled without a warning.
   *
   * @param messager the round's messager
   * @param tag the annotation tag, for the diagnostic
   * @param sumType the sealed type
   * @param subtype the permitted subtype
   * @param namedSumType the sum type as the subtype's clause names it
   * @return true when the subtype was rejected and an error reported
   */
  private static boolean rejectsUnboundParameter(
      Messager messager,
      String tag,
      TypeElement sumType,
      TypeElement subtype,
      DeclaredType namedSumType) {

    List<String> unbound =
        subtype.getTypeParameters().stream()
            .filter(parameter -> !ProcessorUtils.mentions(namedSumType, parameter))
            .map(parameter -> parameter.getSimpleName().toString())
            .toList();
    if (unbound.isEmpty()) {
      return false;
    }
    Diagnostics.error(
        messager,
        subtype,
        tag,
        "'"
            + subtype.getSimpleName()
            + "' declares "
            + unbound
            + ", which '"
            + sumType.getSimpleName()
            + "' does not bind.",
        "A prism narrows by instanceof, which tests an erasure, so only what the clause pins is"
            + " checked; a free parameter lets two callers read one value at different types, and"
            + " the second gets a ClassCastException from a call site that compiled cleanly.",
        "Bind it in the clause, as '"
            + subtype.getSimpleName()
            + " implements "
            + sumType.getSimpleName()
            + "<"
            + String.join(", ", unbound)
            + ">', or write the prism by hand where the unsoundness is visible.");
    return true;
  }
}
