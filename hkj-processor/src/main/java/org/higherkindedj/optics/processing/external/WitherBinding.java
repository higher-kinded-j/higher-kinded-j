// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The method a generated wither call binds to.
 *
 * <p>A spec's {@code @Wither} lens sets through {@code source.withX(newValue)}, and javac chooses
 * among the methods named {@code withX} by the type of {@code newValue}, which is the lens's focus.
 * The generated lens hands back what <em>that</em> method returns, so a check has to read the
 * method the call binds, not any method that carries the name. This repeats javac's choice for a
 * call with one argument, phase by phase:
 *
 * <ol>
 *   <li>a one-parameter method the argument reaches without boxing or unboxing;
 *   <li>failing that, one it reaches with them, which for a reference argument means a primitive
 *       parameter;
 *   <li>failing that, a variable-arity method.
 * </ol>
 *
 * <p>Within the first phase that finds any, the method whose parameter is assignable to every other
 * one's is the one called. Methods whose parameters are assignable both ways are one signature
 * inherited from more than one supertype, and the language takes the one with the most specific
 * return.
 *
 * <p>Two shapes are not repeated, and are answered {@link Undecided}: a method whose parameter
 * names a type variable of its own, whose applicability rests on inference, and the variable-arity
 * phase. The first is read as applicable wherever it might be, through its erasure, so that no
 * method javac would call is ever left out of the answer, and it leaves the choice undecided only
 * where it is one of the methods the call chooses between.
 */
sealed interface WitherBinding {

  /**
   * The call binds this method.
   *
   * @param method the method the call binds
   */
  record Binds(ExecutableElement method) implements WitherBinding {}

  /** No method of the name takes the argument, so the call does not compile. */
  record NoneApplies() implements WitherBinding {}

  /**
   * More than one method takes the argument and none is more specific than the rest, so the call
   * does not compile.
   *
   * @param methods the methods the call cannot choose between, always more than one
   */
  record Ambiguous(List<ExecutableElement> methods) implements WitherBinding {}

  /**
   * The call binds one of these methods, if it compiles at all; which one rests on inference that
   * is not repeated here.
   *
   * @param candidates every method the call might bind, never empty
   */
  record Undecided(List<ExecutableElement> candidates) implements WitherBinding {}

  /**
   * Which of {@code named} the call {@code source.name(argument)} binds.
   *
   * @param types the round's type utilities; must not be null
   * @param source the type of the receiver, as the generated lens names it; must not be null
   * @param argument the type of the argument; a reference type, since a lens's focus is a type
   *     argument; must not be null
   * @param named every method of the name the generated class can reach, of any arity, static or
   *     not, since javac weighs them all; must not be null
   * @return the binding (non-null)
   */
  static WitherBinding resolve(
      Types types, DeclaredType source, TypeMirror argument, List<ExecutableElement> named) {
    // The call reads each method on its receiver's captured type, as javac reads it on 'source'.
    DeclaredType receiver = (DeclaredType) types.capture(source);
    List<ExecutableElement> strict = applicable(types, receiver, argument, named, true);
    List<ExecutableElement> applicable =
        strict.isEmpty() ? applicable(types, receiver, argument, named, false) : strict;
    if (applicable.isEmpty()) {
      // A variable-arity method takes one argument as its first parameter, or as the only
      // element of its array.
      List<ExecutableElement> variableArity =
          named.stream()
              .filter(method -> method.isVarArgs() && method.getParameters().size() <= 2)
              .toList();
      return variableArity.isEmpty() ? new NoneApplies() : new Undecided(variableArity);
    }
    List<ExecutableElement> maximal =
        applicable.stream()
            .filter(
                method ->
                    applicable.stream()
                        .noneMatch(
                            other ->
                                takesParameterOf(types, receiver, other, method)
                                    && !takesParameterOf(types, receiver, method, other)))
            .toList();
    // Asked of the most specific methods rather than of every applicable one: a method whose
    // parameter inference decides puts the call in doubt only where it is one of those the call
    // chooses between, and an overload that loses outright says nothing about it.
    if (maximal.stream().anyMatch(WitherBinding::infersItsParameter)) {
      return new Undecided(maximal);
    }
    if (maximal.size() == 1) {
      return new Binds(maximal.getFirst());
    }
    // Two maximal methods either take each other's parameter, one signature reached twice, or
    // neither takes the other's, which leaves the call nothing to choose by.
    if (!maximal.stream()
        .allMatch(
            method ->
                maximal.stream()
                    .allMatch(other -> takesParameterOf(types, receiver, method, other)))) {
      return new Ambiguous(maximal);
    }
    return maximal.stream()
        .filter(
            method ->
                maximal.stream()
                    .allMatch(
                        other ->
                            types.isSubtype(
                                ProcessorUtils.returnTypeIn(types, receiver, method),
                                ProcessorUtils.returnTypeIn(types, receiver, other))))
        .<WitherBinding>map(Binds::new)
        .findFirst()
        // A type none of whose inherited signatures has the most specific return is itself an
        // error, which javac reports at its declaration; the call there is ambiguous too.
        .orElse(new Ambiguous(applicable));
  }

  /**
   * The one-parameter methods the argument reaches, strictly (by subtyping alone) or loosely (with
   * boxing and unboxing too).
   */
  private static List<ExecutableElement> applicable(
      Types types,
      DeclaredType receiver,
      TypeMirror argument,
      List<ExecutableElement> named,
      boolean strict) {
    return named.stream()
        .filter(method -> method.getParameters().size() == 1)
        .filter(
            method -> {
              TypeMirror parameter = parameterOf(types, receiver, method);
              // The argument is a reference, so only a primitive parameter needs unboxing.
              return (!strict || !parameter.getKind().isPrimitive())
                  && types.isAssignable(argument, parameter);
            })
        .toList();
  }

  /** Whether {@code method}'s parameter is assignable to {@code other}'s, so it is as specific. */
  private static boolean takesParameterOf(
      Types types, DeclaredType receiver, ExecutableElement method, ExecutableElement other) {
    return types.isAssignable(
        parameterOf(types, receiver, method), parameterOf(types, receiver, other));
  }

  /**
   * The method's one parameter read on the receiver, or its erasure where it names a type variable
   * of the method's own: an argument the erasure takes is one inference might accept.
   */
  private static TypeMirror parameterOf(
      Types types, DeclaredType receiver, ExecutableElement method) {
    TypeMirror parameter = ProcessorUtils.firstParameterTypeIn(types, receiver, method);
    return infersItsParameter(method) ? types.erasure(parameter) : parameter;
  }

  private static boolean infersItsParameter(ExecutableElement method) {
    TypeMirror parameter = method.getParameters().getFirst().asType();
    return method.getTypeParameters().stream()
        .anyMatch(variable -> ProcessorUtils.mentions(parameter, variable));
  }
}
