// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The one answer to what an {@code @InstanceOf} test can narrow a value to.
 *
 * <p>The annotation carries a class constant, which is always raw, and the {@code instanceof} the
 * generator writes runs after erasure. The type arguments of the narrowed value are therefore never
 * read at runtime: either the source type the prism starts from pins them down, or nothing does.
 *
 * <p>{@code Circle<X> implements Shape<X>}, narrowed from {@code Shape<U>}, pins {@code X} to
 * {@code U}: a value that is a {@code Shape<U>} and passes {@code instanceof Circle} can only be a
 * {@code Circle<U>}, and javac accepts {@code source instanceof Circle<U>} as a checked test.
 * {@code Circle<X> extends Shape}, narrowed from a {@code Shape} that declares no parameters, pins
 * nothing: every instantiation passes the same test, so the only type the test earns is {@code
 * Circle<?>}.
 *
 * <p>This class answers with that type and says which parameters it had to leave free. Whether the
 * answer is the type the prism promised is {@link SpecInterfaceAnalyser}'s question to ask: a
 * {@code Prism<Shape, Circle<T>>} asks for a {@code T} the test cannot deliver, and is rejected at
 * the declaration rather than left to fail on the first read (issue #733).
 */
final class InstanceOfNarrowing {

  private final Types typeUtils;

  /**
   * Creates a narrowing analysis over the round's type utilities.
   *
   * @param typeUtils the round's type utilities; must not be null
   */
  InstanceOfNarrowing(Types typeUtils) {
    this.typeUtils = typeUtils;
  }

  /**
   * The test an {@code @InstanceOf} target earns, and what it could not check.
   *
   * @param testedType the type the generated {@code instanceof} names: the target under the
   *     arguments the source pins down, with an unbounded wildcard in every position it pins none
   * @param freeParameters the simple names of the target's own type parameters that reached that
   *     wildcard, in declaration order; empty when the test is as precise as the target's
   *     declaration allows
   */
  record Narrowing(TypeMirror testedType, List<String> freeParameters) {}

  /**
   * The element of {@code targetType} that no test could name with type arguments of its own, or
   * null when every layer of it can be named.
   *
   * <p>A parameterised member of a generic type is the one shape with no rendering: {@code
   * Outer<X>.Inner<Y>} can only carry its own arguments alongside the enclosing type's, and an
   * {@code instanceof} names neither. The remaining raw {@code Outer.Inner} checks nothing about
   * {@code Y} and is a {@code rawtypes} warning in the consuming build besides, so the caller
   * rejects this rather than narrowing it. An array of one is the same type one layer out, and
   * {@link #narrow} would reach the component regardless.
   *
   * <p>The element is answered rather than a yes or no because it is the element, not the array
   * wrapped around it, that the diagnostic has to name.
   *
   * @param targetType the type the annotation's class constant resolves to; must not be null
   * @return the offending element, or null when the target can be named
   */
  static TypeElement unnameableElement(TypeMirror targetType) {
    if (targetType instanceof ArrayType array) {
      return unnameableElement(array.getComponentType());
    }
    if (!(targetType instanceof DeclaredType declared)) {
      return null;
    }
    // A declared type's element is a TypeElement, and it is that element's own prototype - not the
    // annotation's mirror - that carries the enclosing type's arguments.
    TypeElement element = (TypeElement) declared.asElement();
    DeclaredType prototype = (DeclaredType) element.asType();
    boolean unnameable =
        !element.getTypeParameters().isEmpty()
            && prototype.getEnclosingType() instanceof DeclaredType enclosing
            && !enclosing.getTypeArguments().isEmpty();
    return unnameable ? element : null;
  }

  /**
   * Narrows the {@code @InstanceOf} target as far as the source type allows.
   *
   * @param targetType the type the annotation's class constant resolves to, which {@link
   *     #unnameableElement} has already cleared; an array is narrowed through its component; must
   *     not be null
   * @param sourceType the source type {@code S} the prism starts from; must not be null
   * @param sourceTypeElement the element {@code sourceType} instantiates; must not be null
   * @return the narrowing (non-null)
   */
  Narrowing narrow(TypeMirror targetType, TypeMirror sourceType, TypeElement sourceTypeElement) {
    // An array target asks the same question one layer down: List[].class is exactly as raw as
    // List.class, and only its component can carry arguments. An array of a reifiable component -
    // int[], String[] - narrows to itself, because the recursion finds nothing to wildcard.
    if (targetType instanceof ArrayType targetArray) {
      Narrowing component = narrow(targetArray.getComponentType(), sourceType, sourceTypeElement);
      return new Narrowing(
          typeUtils.getArrayType(component.testedType()), component.freeParameters());
    }
    // A primitive component, or anything else a class constant can name that carries no arguments.
    if (!(targetType instanceof DeclaredType declaredTarget)) {
      return new Narrowing(targetType, List.of());
    }
    TypeElement targetElement = (TypeElement) declaredTarget.asElement();
    List<? extends TypeParameterElement> parameters = targetElement.getTypeParameters();
    if (parameters.isEmpty()) {
      return new Narrowing(targetType, List.of());
    }

    Map<Element, List<TypeMirror>> matched = new HashMap<>();
    // The target's own declaration, instantiated as the source type's element sees it: Circle<X>
    // reaches Shape as Shape<X>, and matching that against Shape<U> is what pins X.
    TypeMirror asSource =
        ProcessorUtils.supertypeOf(typeUtils, targetElement.asType(), sourceTypeElement);
    match(asSource, sourceType, matched);

    List<String> free = new ArrayList<>();
    TypeMirror[] arguments = new TypeMirror[parameters.size()];
    for (int index = 0; index < parameters.size(); index++) {
      TypeParameterElement parameter = parameters.get(index);
      List<TypeMirror> bindings = matched.get(parameter);
      if (bindings == null || !agree(bindings)) {
        free.add(parameter.getSimpleName().toString());
        arguments[index] = typeUtils.getWildcardType(null, null);
      } else {
        arguments[index] = bindings.getFirst();
      }
    }
    return new Narrowing(typeUtils.getDeclaredType(targetElement, arguments), List.copyOf(free));
  }

  /**
   * Whether every position a variable was matched at asks for the same type.
   *
   * <p>{@code Twin<X> extends Node<Pair<X, X>>} matched against {@code Node<Pair<U, V>>} asks for
   * two: no instantiation of {@code Twin} satisfies both, so the variable is pinned to neither and
   * the caller rejects the declaration rather than writing a test javac would refuse.
   *
   * @param bindings the types the variable was matched against, in encounter order
   * @return true when they are all the same type
   */
  private boolean agree(List<TypeMirror> bindings) {
    return bindings.stream()
        .allMatch(binding -> typeUtils.isSameType(binding, bindings.getFirst()));
  }

  /**
   * Matches the target's view of the source hierarchy against the source type, recording every
   * position each type variable it names is asked to be.
   *
   * @param pattern the hierarchy as the target declares it, or null when the target does not reach
   *     the source element at all
   * @param actual the same position in the source type
   * @param matched the positions collected so far, added to in place
   */
  private void match(
      TypeMirror pattern, TypeMirror actual, Map<Element, List<TypeMirror>> matched) {
    switch (pattern) {
      case TypeVariable variable ->
          matched.computeIfAbsent(variable.asElement(), key -> new ArrayList<>()).add(actual);
      case DeclaredType declared when actual instanceof DeclaredType actualDeclared -> {
        // Same class, argument for argument. Two different classes of the same arity line up
        // position by position and would pin every variable to a type it was never asked to be;
        // a raw extends clause on the way to the source names the class with no arguments to
        // read (a raw source type itself is refused at the spec's declaration).
        if (typeUtils.isSameType(typeUtils.erasure(declared), typeUtils.erasure(actualDeclared))
            && declared.getTypeArguments().size() == actualDeclared.getTypeArguments().size()) {
          for (int index = 0; index < declared.getTypeArguments().size(); index++) {
            match(
                declared.getTypeArguments().get(index),
                actualDeclared.getTypeArguments().get(index),
                matched);
          }
        }
      }
      case ArrayType array when actual instanceof ArrayType actualArray ->
          match(array.getComponentType(), actualArray.getComponentType(), matched);
      // Anything else - a wildcard, or a hierarchy the target does not reach - pins nothing,
      // which is the answer that leaves the parameter free.
      case null, default -> {}
    }
  }
}
