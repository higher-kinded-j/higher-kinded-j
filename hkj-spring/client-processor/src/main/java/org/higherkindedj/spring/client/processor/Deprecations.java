// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;

/**
 * The lint categories a generated declaration draws by naming, calling or overriding a deprecated
 * element.
 *
 * <p>The deprecation is the author's: they chose to extend the deprecated method or name the
 * deprecated type, in the client or a base it extends, where javac reports it to them and their own
 * suppression answers for it. The facade and the native interface restate it in files of their own,
 * which that suppression does not reach, and javac's exemption for a use within the outermost class
 * that declares the element never covers them, so each generated declaration that restates it
 * answers for it itself.
 *
 * <p>hkj-processor's {@code ProcessorUtils} reads deprecations the same way for the code it
 * generates, in {@code deprecationOf}, {@code deprecationNamedBy} and {@code typesNamedBy}; a
 * change to one belongs in the other.
 */
final class Deprecations {

  private Deprecations() {
    // Utility class - prevent instantiation
  }

  /**
   * The categories using these elements draws: {@link Lint#DEPRECATION} for one {@link
   * Elements#isDeprecated deprecated} without {@code forRemoval}, which a javadoc tag alone makes
   * it, and {@link Lint#REMOVAL} for one whose {@code @Deprecated} says {@code forRemoval}. javac
   * counts overriding a deprecated method as a use of it, as it does calling one.
   *
   * @param used the elements a declaration names, calls or overrides; consumed by this call
   * @param elements the round's element utilities
   * @return the categories drawn, in name order; empty where none is deprecated
   */
  static Set<Lint> drawnBy(Stream<? extends Element> used, Elements elements) {
    return used.filter(elements::isDeprecated)
        .map(
            element -> {
              Deprecated deprecated = element.getAnnotation(Deprecated.class);
              return deprecated != null && deprecated.forRemoval()
                  ? Lint.REMOVAL
                  : Lint.DEPRECATION;
            })
        .collect(Lint.inNameOrder());
  }

  /**
   * The categories writing these types out draws: those of the classes {@link #typesNamedBy} finds
   * in them.
   *
   * @param written the types a declaration writes out; consumed by this call
   * @param elements the round's element utilities
   * @return the categories drawn, in name order; empty where none is deprecated
   */
  static Set<Lint> namedBy(Stream<? extends TypeMirror> written, Elements elements) {
    return drawnBy(written.flatMap(Deprecations::typesNamedBy), elements);
  }

  /**
   * The classes and interfaces a declaration names when it writes a type out: the type's own, its
   * type arguments', a wildcard's bounds', an array's component's and an intersection's bounds',
   * and every class each is nested in, since a qualified name such as {@code Outer.Inner} names
   * {@code Outer} as well. A type variable names none: it is written by its own name, and its
   * bounds only where it is declared, so a declaration that declares one passes its bounds. A
   * primitive names none, and nor does a type javac could not resolve. javac does not report a
   * deprecated class reached only through a wildcard's capture, so a wildcard's bound is a wider
   * answer than javac's, which only widens where a suppression lands.
   *
   * <p>hkj-processor's twin has no intersection arm, as it reads a type parameter's bounds from its
   * declaration, one by one; a client reads a method's own type variables as a member of the
   * client, where their bounds come back as one intersection.
   *
   * @param type the type as it is written
   * @return the classes it names, possibly with repeats
   */
  private static Stream<TypeElement> typesNamedBy(TypeMirror type) {
    return switch (type.getKind()) {
      case ARRAY -> typesNamedBy(((ArrayType) type).getComponentType());
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        yield Stream.of(wildcard.getExtendsBound(), wildcard.getSuperBound())
            .filter(Objects::nonNull)
            .flatMap(Deprecations::typesNamedBy);
      }
      case INTERSECTION ->
          ((IntersectionType) type).getBounds().stream().flatMap(Deprecations::typesNamedBy);
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        yield Stream.of(
                enclosingTypes((TypeElement) declared.asElement()),
                declared.getTypeArguments().stream().flatMap(Deprecations::typesNamedBy),
                typesNamedBy(declared.getEnclosingType()))
            .flatMap(Function.identity());
      }
      default -> Stream.empty();
    };
  }

  /** A class, followed by each class it is nested in, out to its top-level class. */
  static Stream<TypeElement> enclosingTypes(TypeElement type) {
    return Stream.iterate(
        type,
        Objects::nonNull,
        current ->
            current.getEnclosingElement() instanceof TypeElement enclosing ? enclosing : null);
  }
}
