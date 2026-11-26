// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

/**
 * The raw types a generated declaration writes out, and which types javac reifies, raw ones among
 * them.
 *
 * <p>The raw type is the author's: they wrote it in the client or a base it extends, where javac
 * reports it to them and their own suppression answers for it. The facade and the native interface
 * restate it in files of their own, which that suppression does not reach, and a base read from a
 * class file has lost its suppression besides, as {@code SuppressWarnings} is kept in source alone.
 * So each generated declaration that writes one out answers for it itself.
 *
 * <p>hkj-processor's {@code ProcessorUtils} finds a raw type the same way for the code it
 * generates, in {@code firstRawIn}, and reads reifiability as this does in {@code reifiableTarget};
 * a change to one belongs in the other.
 */
final class RawTypes {

  private RawTypes() {
    // Utility class - prevent instantiation
  }

  /**
   * The categories writing these types out draws: {@link Lint#RAWTYPES} where one of them names a
   * raw type anywhere.
   *
   * @param written the types a declaration writes out; consumed by this call
   * @return the categories drawn; empty where none is raw
   */
  static Set<Lint> namedBy(Stream<? extends TypeMirror> written) {
    return written.anyMatch(RawTypes::namesRaw) ? Set.of(Lint.RAWTYPES) : Set.of();
  }

  /**
   * Whether a type names a raw type: a generic class or interface written without its type
   * arguments, at any depth. A bare {@code List} counts, and so does one inside a type argument, a
   * wildcard's bound, an array's component or an intersection's bound. So does the type a class is
   * nested in: {@code Outer.Inner} names {@code Outer} raw where {@code Inner} is an inner class of
   * a generic {@code Outer}, even if {@code Inner} declares nothing of its own (JLS 4.8). A static
   * nested class has no enclosing type to check. A type variable names none, as its bounds are
   * written only where it is declared, and a declaration that declares one passes its bounds.
   *
   * <p>hkj-processor's twin has no intersection arm, as it reads a type parameter's bounds one by
   * one; a client reads a method's own type variables as a member of the client, where their bounds
   * come back as one intersection.
   */
  private static boolean namesRaw(TypeMirror type) {
    return switch (type.getKind()) {
      case ARRAY -> namesRaw(((ArrayType) type).getComponentType());
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        yield Stream.of(wildcard.getExtendsBound(), wildcard.getSuperBound())
            .filter(Objects::nonNull)
            .anyMatch(RawTypes::namesRaw);
      }
      case INTERSECTION ->
          ((IntersectionType) type).getBounds().stream().anyMatch(RawTypes::namesRaw);
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        yield isRaw(declared)
            || declared.getTypeArguments().stream().anyMatch(RawTypes::namesRaw)
            || namesRaw(declared.getEnclosingType());
      }
      default -> false;
    };
  }

  /** Whether a type names a generic class or interface without its type arguments. */
  static boolean isRaw(TypeMirror type) {
    return type.getKind() == TypeKind.DECLARED
        && ((DeclaredType) type).getTypeArguments().isEmpty()
        && !((TypeElement) ((DeclaredType) type).asElement()).getTypeParameters().isEmpty();
  }

  /**
   * Whether javac reifies a type (JLS 4.7). It reifies a primitive, a raw type, and a class or
   * interface type whose type arguments are all unbounded wildcards, as are those of each type it
   * is nested in. It also reifies an array of a type it reifies. A wildcard bounded above by {@code
   * Object} alone is unbounded too, as javac reads it. It does not reify a type variable.
   *
   * <p>hkj-processor's twin asks only of a declared or an array type, the target of an unchecked
   * conversion; a varargs element can also be a type variable.
   *
   * @param type the type as the generated declaration writes it
   * @return true when javac reifies it
   */
  static boolean isReifiable(TypeMirror type) {
    return switch (type.getKind()) {
      case ARRAY -> isReifiable(((ArrayType) type).getComponentType());
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        yield declared.getTypeArguments().stream().allMatch(RawTypes::isUnboundedWildcard)
            && isReifiable(declared.getEnclosingType());
      }
      case TYPEVAR, INTERSECTION -> false;
      default -> true;
    };
  }

  /**
   * Whether a type argument is {@code ?}, or {@code ? extends Object}, which javac reads as the
   * same.
   */
  private static boolean isUnboundedWildcard(TypeMirror argument) {
    if (argument.getKind() != TypeKind.WILDCARD) {
      return false;
    }
    WildcardType wildcard = (WildcardType) argument;
    TypeMirror bound = wildcard.getExtendsBound();
    return wildcard.getSuperBound() == null
        && (bound == null
            || bound.getKind() == TypeKind.DECLARED
                && ((TypeElement) ((DeclaredType) bound).asElement())
                    .getQualifiedName()
                    .contentEquals(Object.class.getCanonicalName()));
  }
}
