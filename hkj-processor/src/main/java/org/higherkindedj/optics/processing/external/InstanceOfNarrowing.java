// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
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
 * <p>This class answers with that type and says which parameters it had to leave free, which
 * parameters it pins have a bound it cannot write, and which arguments it pins javac would refuse
 * as outside their bounds. It also says when the target's own declaration rules the source out:
 * {@code Grid<Y> extends Node<Y[]>} is a {@code Node<int[]>} for no {@code Y}, because a type
 * argument is never a primitive, so no value of that source would pass the test. Whether the answer
 * is the type the prism promised is {@link SpecInterfaceAnalyser}'s question to ask: a {@code
 * Prism<Shape, Circle<T>>} asks for a {@code T} the test cannot deliver, and is rejected at the
 * declaration rather than left to fail on the first read.
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
   * @param outOfBounds the parameters the test pins to an argument outside their bounds, in
   *     declaration order; javac refuses the tested type for any of them
   * @param neverMatches present when no value of the source can be the target, whatever its
   *     arguments; the tested type is then the target as the annotation names it, and the lists are
   *     empty
   */
  record Narrowing(
      TypeMirror testedType,
      List<String> freeParameters,
      List<UnwritableBound> unwritableBounds,
      List<OutOfBound> outOfBounds,
      Optional<NeverMatches> neverMatches) {}

  /**
   * A target whose own declaration rules the source out, so the test would never match.
   *
   * @param target the target under its own parameters, such as {@code Grid<Y>}
   * @param declared the target as the source's class, such as {@code Node<Y[]>} for {@code Grid<Y>
   *     extends Node<Y[]>}; no choice of its parameters makes it the source
   * @param primitiveArgument the first type parameter a primitive stands against, where primitives
   *     alone rule the source out
   */
  record NeverMatches(
      TypeMirror target, TypeMirror declared, Optional<PrimitiveArgument> primitiveArgument) {}

  /**
   * A type parameter at a position where the other side writes a primitive, which no type argument
   * can be.
   *
   * @param parameter the parameter, the target's or the source's
   * @param primitive the primitive the other side writes there
   */
  record PrimitiveArgument(TypeVariable parameter, TypeMirror primitive) {}

  /**
   * A position where the target's view of the source and the source can never be one type.
   *
   * @param primitiveArgument the parameter a primitive stands against there, where that is the
   *     clash
   */
  private record Clash(Optional<PrimitiveArgument> primitiveArgument) {}

  /**
   * A parameter the test pins whose bound is a parameter it leaves free.
   *
   * @param declaration the parameter as declared, such as {@code Y extends X}
   * @param free the free parameters that bound it, as {@link Narrowing#freeParameters} names them
   */
  record UnwritableBound(String declaration, List<String> free) {}

  /**
   * A parameter the test pins to an argument outside its bound.
   *
   * @param declaration the parameter as declared, such as {@code Y extends Number}, an enclosing
   *     class's qualified as {@link Narrowing#freeParameters} qualifies it
   * @param argument the type the source pins it to
   * @param bounds each bound of the parameter that can be read as javac reads it against the test's
   *     arguments, at least one of which the argument is outside
   * @param complete whether those are all of its bounds, and none names a type parameter of the
   *     target or of a class around it, as javac's reading of {@code ? extends X} can; only then
   *     does a bound the spec declares answer every one of them
   */
  record OutOfBound(
      String declaration, TypeMirror argument, List<TypeMirror> bounds, boolean complete) {}

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
          component.unwritableBounds(),
          component.outOfBounds(),
          component.neverMatches());
    }
    // A primitive component, or anything else a class constant can name that carries no arguments.
    if (!(targetType instanceof DeclaredType declaredTarget)) {
      return new Narrowing(targetType, List.of(), List.of(), List.of(), Optional.empty());
    }
    // The element's own prototype, not the annotation's raw mirror, is what carries the enclosing
    // class's parameters as well as the target's: Outer<X>.Inner<Y> for Outer.Inner.class.
    TypeElement targetElement = (TypeElement) declaredTarget.asElement();
    DeclaredType prototype = (DeclaredType) targetElement.asType();

    Map<Element, List<TypeMirror>> matched = new HashMap<>();
    List<Clash> clashes = new ArrayList<>();
    // The target's own declaration, instantiated as the source type's element sees it: Circle<X>
    // reaches Shape as Shape<X>, and matching that against Shape<U> is what pins X. An enclosing
    // class's parameter is pinned the same way, where the target's supertype names it. A target
    // with no parameters is matched as well, for what its declaration rules out.
    TypeMirror asSource = ProcessorUtils.supertypeOf(typeUtils, prototype, sourceTypeElement);
    match(asSource, sourceType, 0, matched, clashes);
    Optional<NeverMatches> neverMatches = neverMatches(prototype, asSource, matched, clashes);
    // A test that would never match has nothing worth pinning, and a target that declares no
    // parameters has nothing to pin.
    if (neverMatches.isPresent() || !ProcessorUtils.carriesInstantiation(prototype)) {
      return new Narrowing(targetType, List.of(), List.of(), List.of(), neverMatches);
    }

    Map<TypeParameterElement, TypeMirror> pinned = new LinkedHashMap<>();
    List<TypeParameterElement> free = new ArrayList<>();
    DeclaredType tested = instantiate(prototype, matched, pinned, free);
    return new Narrowing(
        tested,
        free.stream().map(parameter -> nameOf(parameter, targetElement)).toList(),
        unwritableBounds(targetElement, free),
        outOfBounds(targetElement, pinned, free),
        Optional.empty());
  }

  /**
   * The reason no value of the source can be the target, where the match found one.
   *
   * <p>Either a position clashed, or one parameter was asked to be two different types: {@code
   * Twin<X> extends Node<Pair<X, X>>} is a {@code Node<Pair<String, Integer>>} for no {@code X}. A
   * binding that is a wildcard or names a type variable could still agree with the rest, so only
   * the others are compared: against {@code Node<Pair<U, V>>} a {@code Twin} is one wherever {@code
   * U} and {@code V} are the same. The reason names a primitive only where nothing else rules the
   * source out.
   *
   * @param target the target under its own parameters
   * @param asSource the target as the source's class
   * @param matched the positions each type variable was matched against
   * @param clashes the positions the match found could never be one type
   * @return the reason, or empty where a value of the source can be the target
   */
  private Optional<NeverMatches> neverMatches(
      TypeMirror target,
      TypeMirror asSource,
      Map<Element, List<TypeMirror>> matched,
      List<Clash> clashes) {
    boolean asksForTwo =
        matched.values().stream()
            .anyMatch(
                bindings -> !agree(bindings.stream().filter(InstanceOfNarrowing::closed).toList()));
    if (clashes.isEmpty() && !asksForTwo) {
      return Optional.empty();
    }
    boolean onlyPrimitives =
        !asksForTwo && clashes.stream().allMatch(clash -> clash.primitiveArgument().isPresent());
    return Optional.of(
        new NeverMatches(
            target,
            asSource,
            onlyPrimitives ? clashes.getFirst().primitiveArgument() : Optional.empty()));
  }

  /**
   * Whether a binding stands for exactly one type. A wildcard among the source class's own
   * arguments stands for any, and a type variable for whatever it is given. A binding that is
   * neither, and names no variable, is exactly the type it writes, wildcards inside it included.
   */
  private static boolean closed(TypeMirror binding) {
    return binding.getKind() != TypeKind.WILDCARD && !ProcessorUtils.namesTypeVariable(binding);
  }

  /**
   * Each parameter the test pins whose bound is one it leaves free.
   *
   * <p>The bound itself, not one that merely names it. A free parameter is written as a wildcard,
   * so {@code Y extends X} becomes a bound of the wildcard's capture, which no argument the test
   * could write is within; javac refuses {@code Outer<?>.Inner<U>}. A bound that only names it,
   * {@code Y extends List<X>}, becomes {@code List<?>}, which {@link #outOfBounds} checks the
   * argument against, as javac does. A permitted subtype on the sealed route is refused for either,
   * because its prism redeclares the parameter with a bound that has to name {@code X}.
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
        unwritable.add(new UnwritableBound(declarationOf(parameter, targetElement), named));
      }
    }
    return List.copyOf(unwritable);
  }

  /**
   * Each parameter the test pins to an argument outside its bound.
   *
   * <p>javac checks every argument of the tested type against its parameter's bounds, read with
   * each parameter replaced by the argument the test writes for it (JLS 4.5), and refuses the test
   * in the generated file when one is outside. It refuses {@code Lim<U>} for {@code Lim<Y extends
   * Number>} and an unbounded {@code U}, and the {@code Third<Object>} that a {@code Third<Y
   * extends CharSequence>.Sub3} reached from an {@code Outer<Object>.Base} would be named under.
   * {@link ProcessorUtils#substitute(Types, TypeMirror, Map, java.util.Collection)} reads each
   * bound as javac does; a bound it cannot write is left to javac, and a bound that is a free
   * parameter is {@link #unwritableBounds}' to report. Every argument here is a type, never a
   * wildcard: {@link #agree} pins nothing to a source's wildcard.
   *
   * @param targetElement the target class
   * @param pinned each parameter the test pins, to the argument it pins it to, enclosing classes'
   *     first
   * @param free the parameters left as wildcards
   * @return the offending parameters, in declaration order, enclosing classes' first
   */
  private List<OutOfBound> outOfBounds(
      TypeElement targetElement,
      Map<TypeParameterElement, TypeMirror> pinned,
      List<TypeParameterElement> free) {
    // Every type parameter a bound could name, a static member's outer classes' included: javac
    // leaves an outer parameter in a bound such as In<A>, read as Outer<X>.In<A>.
    List<TypeParameterElement> declaredAround = new ArrayList<>();
    for (Element element = targetElement;
        element instanceof Parameterizable declaring;
        element = element.getEnclosingElement()) {
      declaredAround.addAll(declaring.getTypeParameters());
    }
    List<OutOfBound> outside = new ArrayList<>();
    for (Map.Entry<TypeParameterElement, TypeMirror> entry : pinned.entrySet()) {
      TypeMirror argument = entry.getValue();
      List<TypeMirror> bounds =
          entry.getKey().getBounds().stream()
              .flatMap(bound -> ProcessorUtils.substitute(typeUtils, bound, pinned, free).stream())
              .toList();
      if (bounds.stream().anyMatch(bound -> !typeUtils.isSubtype(argument, bound))) {
        boolean complete =
            bounds.size() == entry.getKey().getBounds().size()
                && bounds.stream()
                    .noneMatch(
                        bound ->
                            declaredAround.stream()
                                .anyMatch(parameter -> ProcessorUtils.mentions(bound, parameter)));
        outside.add(
            new OutOfBound(
                declarationOf(entry.getKey(), targetElement), argument, bounds, complete));
      }
    }
    return List.copyOf(outside);
  }

  /**
   * A parameter as declared, such as {@code Y extends Number & Comparable<Y>}, named as {@link
   * #nameOf} names it.
   */
  private static String declarationOf(TypeParameterElement parameter, TypeElement targetElement) {
    return nameOf(parameter, targetElement)
        + " extends "
        + parameter.getBounds().stream()
            .map(ProcessorUtils::simpleTypeName)
            .collect(Collectors.joining(" & "));
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
   * @param pinned each parameter pinned, to the argument it is pinned to, added to in place
   * @param free the parameters left as wildcards, added to in place
   * @return the instantiated type
   */
  private DeclaredType instantiate(
      DeclaredType prototype,
      Map<Element, List<TypeMirror>> matched,
      Map<TypeParameterElement, TypeMirror> pinned,
      List<TypeParameterElement> free) {
    // A link that carries no instantiation, a static member's NoType included, contributes nothing
    // to write: the element alone names it.
    DeclaredType containing =
        prototype.getEnclosingType() instanceof DeclaredType enclosing
                && ProcessorUtils.carriesInstantiation(enclosing)
            ? instantiate(enclosing, matched, pinned, free)
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
        pinned.put(parameter, bindings.getFirst());
        arguments[index] = bindings.getFirst();
      }
    }
    return typeUtils.getDeclaredType(containing, (TypeElement) prototype.asElement(), arguments);
  }

  /**
   * Whether every position a variable was matched at asks for the same type.
   *
   * <p>{@code Twin<X> extends Node<Pair<X, X>>} matched against {@code Node<Pair<U, V>>} asks for
   * two: no one instantiation of {@code Twin} is right for every {@code U} and {@code V}, so the
   * variable is pinned to neither and the caller rejects the declaration rather than writing a test
   * javac would refuse. Where the bindings that each stand for exactly one type disagree, {@link
   * #neverMatches} rules the source out instead (see {@link #closed}).
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
   * position each type variable it names is asked to be, and every position that can never be one
   * type.
   *
   * <p>A wildcard the source writes among its class's own arguments, or those of a class around it,
   * pins nothing, and rules out only a class or array outside its bounds: no {@code Node<? extends
   * Number>} is a {@code Node<Object>}. Every other position is part of a type the source fixes
   * exactly. Where neither side names a type variable, that type is compared whole: no {@code
   * Node<List<String>>} is a {@code Node<Set<String>>}, a {@code Node<List<?>>} or a {@code
   * Node<List<? extends Number>>}. Where one does, the match goes position by position, and two
   * classes, arrays or primitives that differ clash, as does a wildcard against one of them, unless
   * javac reads the two as one type argument, as it reads {@code ? super Object} as {@code Object}.
   * Inside an argument, such a wildcard pins a variable to that type. Against a type, a type
   * variable clashes only with a primitive, since it stands for some reference type the match
   * cannot see. Against any other wildcard inside an argument, the target's own variable clashes as
   * well, since a type argument is never a wildcard. The source's variable rules nothing out, and
   * what it stands for is left to the checks that follow, as is a {@code ? super} wildcard javac
   * reads otherwise than the model can.
   *
   * @param pattern the hierarchy as the target declares it, or null when the target does not reach
   *     the source element at all
   * @param actual the same position in the source type
   * @param depth how far below the source's class the position lies: 0 for that class and the
   *     classes around it, 1 for their type arguments, and one more for each argument or array
   *     component below those
   * @param matched the positions collected so far, added to in place
   * @param clashes the positions that can never be one type, added to in place
   */
  private void match(
      TypeMirror pattern,
      TypeMirror actual,
      int depth,
      Map<Element, List<TypeMirror>> matched,
      List<Clash> clashes) {
    switch (pattern) {
      case TypeVariable variable -> {
        if (actual.getKind().isPrimitive()) {
          // Grid<Y> extends Node<Y[]> reaches a Node<int[]> with Y at the int, which no argument
          // can be. Pinned, it would be handed to a builder that only takes reference types.
          clashes.add(new Clash(Optional.of(new PrimitiveArgument(variable, actual))));
        } else if (wildcardInsideAnArgument(actual, depth)
            && standsForOneType((WildcardType) actual)) {
          // javac reads ? super Object as Object, and so does the match.
          bind(matched, variable, ((WildcardType) actual).getSuperBound());
        } else if (wildcardInsideAnArgument(actual, depth)) {
          // A type argument is a type, never a wildcard: no List<X> is exactly the type List<?>.
          clashes.add(new Clash(Optional.empty()));
        } else {
          bind(matched, variable, actual);
        }
      }
      // Same class, argument for argument. A raw extends clause on the way to the source names
      // the class with no arguments to read (a raw source type itself is refused at the spec's
      // declaration).
      case DeclaredType declared
          when actual instanceof DeclaredType actualDeclared
              && typeUtils.isSameType(
                  typeUtils.erasure(declared), typeUtils.erasure(actualDeclared)) -> {
        // From an argument down, a type that names no variable is exactly the type it writes.
        if (depth > 0
            && !ProcessorUtils.namesTypeVariable(declared)
            && !ProcessorUtils.namesTypeVariable(actualDeclared)
            && !holdsAnArgumentJavacReadsOtherwise(declared)
            && !holdsAnArgumentJavacReadsOtherwise(actualDeclared)) {
          // Its wildcards and raw types included: no List<?> is a List<? extends Number>.
          if (!typeUtils.isSameType(declared, actualDeclared)) {
            clashes.add(new Clash(Optional.empty()));
          }
        } else if (declared.getTypeArguments().size() == actualDeclared.getTypeArguments().size()) {
          List<? extends TypeParameterElement> parameters =
              ((TypeElement) declared.asElement()).getTypeParameters();
          for (int index = 0; index < declared.getTypeArguments().size(); index++) {
            TypeMirror argument = declared.getTypeArguments().get(index);
            TypeMirror actualArgument = actualDeclared.getTypeArguments().get(index);
            if (readOtherwiseByJavac(parameters.get(index), argument)
                || readOtherwiseByJavac(parameters.get(index), actualArgument)) {
              // Nothing is ruled out here, and a variable is matched to the wildcard as it is,
              // which pins nothing.
              if (argument instanceof TypeVariable variable) {
                bind(matched, variable, actualArgument);
              }
              continue;
            }
            match(argument, actualArgument, depth + 1, matched, clashes);
          }
          // An inner class's enclosing instantiation is part of its type: Outer<X>.Base matched
          // against Outer<String>.Base pins X as surely as an argument would.
          match(
              declared.getEnclosingType(),
              actualDeclared.getEnclosingType(),
              depth,
              matched,
              clashes);
        }
      }
      case ArrayType array when actual instanceof ArrayType actualArray ->
          match(
              array.getComponentType(),
              actualArray.getComponentType(),
              depth + 1,
              matched,
              clashes);
      // A hierarchy the target does not reach pins nothing, which leaves every parameter free.
      case null -> {}
      default -> {
        if (pattern.getKind().isPrimitive() && actual instanceof TypeVariable variable) {
          clashes.add(new Clash(Optional.of(new PrimitiveArgument(variable, pattern))));
        } else if (differ(pattern, actual, depth)) {
          clashes.add(new Clash(Optional.empty()));
        }
      }
    }
  }

  /** Records one more position a type variable was matched at. */
  private static void bind(
      Map<Element, List<TypeMirror>> matched, TypeVariable variable, TypeMirror binding) {
    matched.computeIfAbsent(variable.asElement(), key -> new ArrayList<>()).add(binding);
  }

  /**
   * Whether two positions the match takes no further are types that differ: two classes, arrays or
   * primitives that are not the same type, or, where neither names a type variable, a wildcard that
   * is part of a type against one of them, or against another such wildcard, that javac does not
   * read as the same type argument. The target's wildcards are all part of the type it declares;
   * the source's are only below its class's own arguments. A wildcard among those own arguments
   * differs only from a class or array it does not contain.
   */
  private boolean differ(TypeMirror pattern, TypeMirror actual, int depth) {
    if (hasFixedShape(pattern) && hasFixedShape(actual)) {
      return !typeUtils.isSameType(pattern, actual);
    }
    // A wildcard is weighed only where neither side names a variable, as a whole type is: javac
    // reads a variable as anything at all, and accepts the test.
    if (ProcessorUtils.namesTypeVariable(pattern) || ProcessorUtils.namesTypeVariable(actual)) {
      return false;
    }
    // javac casts from a source's wildcard only to a type the wildcard contains: no
    // Node<? extends Number> is a Node<Object>.
    if (depth == 1 && actual.getKind() == TypeKind.WILDCARD) {
      return hasFixedShape(pattern) && !typeUtils.contains(actual, pattern);
    }
    // What is left against a wildcard of the target's is a class or an array, or a wildcard
    // inside one of the source's arguments.
    if (pattern.getKind() == TypeKind.WILDCARD) {
      return !oneTypeArgument(pattern, actual);
    }
    return wildcardInsideAnArgument(actual, depth) && !oneTypeArgument(actual, pattern);
  }

  /**
   * Whether javac reads a wildcard as exactly one type, as it reads {@code ? super Object}: the
   * only type it admits is its lower bound.
   */
  private boolean standsForOneType(WildcardType wildcard) {
    return wildcard.getSuperBound() != null && oneTypeArgument(wildcard, wildcard.getSuperBound());
  }

  /**
   * Whether javac reads an argument otherwise than the model does. javac reads a {@code ? super L}
   * against its parameter's bound with the class's arguments in place, so {@code PB<Object, ? super
   * Object>} for {@code PB<A, B extends A>} is one type to javac; the model reads it against the
   * declared bound, {@code A}, and cannot tell. Such an argument is left to javac, as is any type
   * that holds one.
   */
  private static boolean readOtherwiseByJavac(TypeParameterElement parameter, TypeMirror argument) {
    return argument instanceof WildcardType wildcard
        && wildcard.getSuperBound() != null
        && parameter.getBounds().stream().anyMatch(ProcessorUtils::namesTypeVariable);
  }

  /** Whether a type holds, at any depth, an argument javac reads otherwise than the model does. */
  static boolean holdsAnArgumentJavacReadsOtherwise(TypeMirror type) {
    return switch (type.getKind()) {
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        List<? extends TypeParameterElement> parameters =
            ((TypeElement) declared.asElement()).getTypeParameters();
        List<? extends TypeMirror> arguments = declared.getTypeArguments();
        yield IntStream.range(0, arguments.size())
                .anyMatch(
                    index -> readOtherwiseByJavac(parameters.get(index), arguments.get(index)))
            || Stream.concat(Stream.of(declared.getEnclosingType()), arguments.stream())
                .anyMatch(InstanceOfNarrowing::holdsAnArgumentJavacReadsOtherwise);
      }
      case ARRAY -> holdsAnArgumentJavacReadsOtherwise(((ArrayType) type).getComponentType());
      case WILDCARD ->
          Stream.concat(
                  Stream.ofNullable(((WildcardType) type).getExtendsBound()),
                  Stream.ofNullable(((WildcardType) type).getSuperBound()))
              .anyMatch(InstanceOfNarrowing::holdsAnArgumentJavacReadsOtherwise);
      default -> false;
    };
  }

  /**
   * Whether a wildcard and a type argument, a type or another wildcard, each contain the other,
   * which makes them one type argument.
   */
  private boolean oneTypeArgument(TypeMirror wildcard, TypeMirror type) {
    return typeUtils.contains(wildcard, type) && typeUtils.contains(type, wildcard);
  }

  /**
   * Whether the source writes a wildcard inside one of its class's arguments rather than as one,
   * where it is part of the type that argument is rather than a stand-in for any.
   */
  private static boolean wildcardInsideAnArgument(TypeMirror actual, int depth) {
    return depth > 1 && actual.getKind() == TypeKind.WILDCARD;
  }

  /**
   * Whether a type's outermost shape is settled: a class, an array or a primitive, rather than a
   * type variable or a wildcard that stands in for one. Kinds rather than {@code instanceof}, since
   * javac's error type is a {@link DeclaredType} too.
   */
  private static boolean hasFixedShape(TypeMirror type) {
    return switch (type.getKind()) {
      case DECLARED, ARRAY -> true;
      default -> type.getKind().isPrimitive();
    };
  }
}
