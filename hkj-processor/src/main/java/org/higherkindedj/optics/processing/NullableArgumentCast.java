// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.WildcardTypeName;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The cast that types a fresh copy as the type it is handed on as, where that type declares a type
 * argument nullable that the copied type does not: {@code (List<@Nullable String>)
 * hkj$copyOf(domain.labels())}, which {@code build} hands a wire whose elements may be null and a
 * plain merge its target, and which {@code parse} and a fallible merge bind ahead of their ladder
 * for a record whose elements may be.
 *
 * <p>A copy is typed by what it copies, and a nullness checker holds each type argument to the
 * nullness it declares, so to the checker a {@code List<String>} is no {@code List<@Nullable
 * String>}, though a fresh list holding no {@code null} holds only what the wider type allows. The
 * cast says so, and is written only where that holds ({@link ContainerCopy#retyping}): every
 * position it widens is one the copy rebuilds, and none narrows, since either would let one side
 * hold a {@code null} the other's type rules out. At the top it keeps whichever side's {@code
 * Nullable} it finds, since the copy of a read that may be null may be null too. To javac the two
 * types are the same, and javac reports no redundant cast whose type carries a type annotation, so
 * the cast is written only where the type as the generated class writes it still carries one. An
 * annotation the class could not name would be left out, and the cast with it.
 *
 * <p>A copy whose type names a type variable takes no cast: the variable's declaration need not say
 * what its argument is, as a record's {@code T} instantiated as {@code @Nullable String} does not,
 * so its positions cannot be read for a nullness they may hold. The type it is handed on as names
 * one only where the copy's does, since the two pair position by position, kind by kind.
 */
final class NullableArgumentCast {

  private NullableArgumentCast() {}

  /**
   * {@code value}, the copy {@code copy} makes of a {@code from}, typed as {@code to} where that
   * widens it, or as it is otherwise.
   *
   * @param copy the copy, or null where the value is handed over as it is
   * @param from the type copied
   * @param to the type the copy is handed on as
   * @param targetPackage the package the generated class is written into
   * @param value the copy's expression
   * @return the copy, cast where {@code to} widens it
   */
  static CodeBlock on(
      ContainerCopy copy, TypeMirror from, TypeMirror to, String targetPackage, CodeBlock value) {
    if (ProcessorUtils.namesTypeVariable(from)
        || ContainerCopy.retyping(copy, from, to) != ContainerCopy.Retyping.CAST) {
      return value;
    }
    TypeName type = ProcessorUtils.typeNameOf(to, targetPackage);
    if (!NullableAnnotations.nullableHere(to) && NullableAnnotations.nullableHere(from)) {
      type =
          type.withoutAnnotations()
              .annotated(ProcessorUtils.typeNameOf(from, targetPackage).annotations());
    }
    return annotated(type) ? CodeBlock.of("($T) $L", type, value) : value;
  }

  /** Whether a type annotation is written anywhere in {@code type}. */
  private static boolean annotated(TypeName type) {
    return !type.annotations().isEmpty() || inside(type).anyMatch(NullableArgumentCast::annotated);
  }

  /** The types written one level inside {@code type}: its arguments, bounds or component. */
  private static Stream<TypeName> inside(TypeName type) {
    return switch (type) {
      case ParameterizedTypeName parameterized -> parameterized.typeArguments().stream();
      case WildcardTypeName wildcard ->
          Stream.concat(wildcard.upperBounds().stream(), wildcard.lowerBounds().stream());
      case ArrayTypeName array -> Stream.of(array.componentType());
      default -> Stream.empty();
    };
  }
}
