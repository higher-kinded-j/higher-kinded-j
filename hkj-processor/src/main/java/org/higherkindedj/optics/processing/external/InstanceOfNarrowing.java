// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 * <p>An inner class of a generic class is answered under its enclosing class's arguments, by the
 * same rule: {@code Outer.Inner.class} names a raw {@code Outer.Inner}, and narrowed from a source
 * that pins nothing of {@code Outer}'s the test earns {@code Outer<?>.Inner}, a test javac checks.
 * {@code SubtypePrismGenerator} names a permitted subtype of a sealed interface the same way, from
 * what the subtype's clause binds rather than from a match against the source.
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
   * @param freeParameters the names of the type parameters that reached that wildcard, those of the
   *     classes the target is an inner class of first, each qualified by its class as {@code
   *     Outer's X}, then the target's own; empty when the test is as precise as the target's
   *     declaration allows
   * @param unwritableBounds the parameters the test pins whose bound is one it leaves free, in
   *     declaration order; the tested type cannot be written for any of them, because the wildcard
   *     leaves the bound nothing to name
   */
  record Narrowing(
      TypeMirror testedType, List<String> freeParameters, List<UnwritableBound> unwritableBounds) {}

  /**
   * A parameter the test pins whose bound is a parameter it leaves free.
   *
   * @param declaration the parameter as declared, such as {@code Y extends X}
   * @param free the free parameters that bound it, as {@link Narrowing#freeParameters} names them
   */
  record UnwritableBound(String declaration, List<String> free) {}

  /**
   * Narrows the {@code @InstanceOf} target as far as the source type allows.
   *
   * @param targetType the type the annotation's class constant resolves to; an array is narrowed
   *     through its component; must not be null
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
          typeUtils.getArrayType(component.testedType()),
          component.freeParameters(),
          component.unwritableBounds());
    }
    // A primitive component, or anything else a class constant can name that carries no arguments.
    if (!(targetType instanceof DeclaredType declaredTarget)) {
      return new Narrowing(targetType, List.of(), List.of());
    }
    // The element's own prototype, not the annotation's raw mirror, is what carries the enclosing
    // class's parameters as well as the target's: Outer<X>.Inner<Y> for Outer.Inner.class.
    TypeElement targetElement = (TypeElement) declaredTarget.asElement();
    DeclaredType prototype = (DeclaredType) targetElement.asType();
    if (!ProcessorUtils.carriesInstantiation(prototype)) {
      return new Narrowing(targetType, List.of(), List.of());
    }

    Map<Element, List<TypeMirror>> matched = new HashMap<>();
    // The target's own declaration, instantiated as the source type's element sees it: Circle<X>
    // reaches Shape as Shape<X>, and matching that against Shape<U> is what pins X. An enclosing
    // class's parameter is pinned the same way, where the target's supertype names it.
    TypeMirror asSource = ProcessorUtils.supertypeOf(typeUtils, prototype, sourceTypeElement);
    match(asSource, sourceType, matched);

    List<TypeParameterElement> free = new ArrayList<>();
    DeclaredType tested = instantiate(prototype, matched, free);
    return new Narrowing(
        tested,
        free.stream().map(parameter -> nameOf(parameter, targetElement)).toList(),
        unwritableBounds(targetElement, free));
  }

  /**
   * Each parameter the test pins whose bound is one it leaves free.
   *
   * <p>The bound itself, not one that merely names it. A free parameter is written as a wildcard,
   * so {@code Y extends X} becomes a bound of the wildcard's capture, which no argument the test
   * could write is within; javac refuses {@code Outer<?>.Inner<U>}. A bound that only names it,
   * {@code Y extends List<X>}, becomes {@code List<?>}, which javac checks the argument against as
   * usual. A permitted subtype on the sealed route is refused for either, because its prism
   * redeclares the parameter with a bound that has to name {@code X}.
   *
   * @param targetElement the target class
   * @param free the parameters left as wildcards
   * @return the offending parameters, in declaration order, enclosing classes' first
   */
  private static List<UnwritableBound> unwritableBounds(
      TypeElement targetElement, List<TypeParameterElement> free) {
    List<UnwritableBound> unwritable = new ArrayList<>();
    for (TypeParameterElement parameter : ProcessorUtils.typeParametersInScope(targetElement)) {
      if (free.contains(parameter)) {
        continue;
      }
      List<String> named =
          parameter.getBounds().stream()
              .filter(
                  bound ->
                      bound instanceof TypeVariable variable && free.contains(variable.asElement()))
              .map(
                  bound ->
                      nameOf(
                          (TypeParameterElement) ((TypeVariable) bound).asElement(), targetElement))
              .toList();
      if (!named.isEmpty()) {
        unwritable.add(
            new UnwritableBound(
                parameter.getSimpleName()
                    + " extends "
                    + parameter.getBounds().stream()
                        .map(ProcessorUtils::simpleTypeName)
                        .collect(Collectors.joining(" & ")),
                named));
      }
    }
    return List.copyOf(unwritable);
  }

  /**
   * A parameter's name as a diagnostic gives it: bare for the target's own, and qualified by its
   * class for an enclosing class's, which may share a name with the target's or the source's.
   */
  private static String nameOf(TypeParameterElement parameter, TypeElement targetElement) {
    return parameter.getGenericElement().equals(targetElement)
        ? parameter.getSimpleName().toString()
        : parameter.getGenericElement().getSimpleName() + "'s " + parameter.getSimpleName();
  }

  /**
   * The prototype under what the match pinned, through each class it is an inner class of,
   * outermost first: a parameter at the one type it was matched to, and an unbounded wildcard where
   * it was matched to none or to two that disagree.
   *
   * @param prototype the target's prototype, or one of its enclosing types
   * @param matched the positions each type variable was matched against
   * @param free the parameters left as wildcards, added to in place
   * @return the instantiated type
   */
  private DeclaredType instantiate(
      DeclaredType prototype,
      Map<Element, List<TypeMirror>> matched,
      List<TypeParameterElement> free) {
    // A link that carries no instantiation, a static member's NoType included, contributes nothing
    // to write: the element alone names it.
    DeclaredType containing =
        prototype.getEnclosingType() instanceof DeclaredType enclosing
                && ProcessorUtils.carriesInstantiation(enclosing)
            ? instantiate(enclosing, matched, free)
            : null;
    List<? extends TypeMirror> parameters = prototype.getTypeArguments();
    TypeMirror[] arguments = new TypeMirror[parameters.size()];
    for (int index = 0; index < parameters.size(); index++) {
      TypeParameterElement parameter =
          (TypeParameterElement) ((TypeVariable) parameters.get(index)).asElement();
      List<TypeMirror> bindings = matched.get(parameter);
      if (bindings == null || !agree(bindings)) {
        free.add(parameter);
        arguments[index] = typeUtils.getWildcardType(null, null);
      } else {
        arguments[index] = bindings.getFirst();
      }
    }
    return typeUtils.getDeclaredType(containing, (TypeElement) prototype.asElement(), arguments);
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
          // An inner class's enclosing instantiation is part of its type: Outer<X>.Base matched
          // against Outer<String>.Base pins X as surely as an argument would.
          match(declared.getEnclosingType(), actualDeclared.getEnclosingType(), matched);
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
