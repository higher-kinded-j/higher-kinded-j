// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.List;
import java.util.stream.IntStream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The method or constructor a generated call binds to.
 *
 * <p>A spec's lens writes through a call the processor generates: {@code source.withX(newValue)}
 * for a {@code @Wither}, the setter of a {@code @ViaCopyAndSet} or of a {@code @ViaBuilder}'s
 * builder, and {@code new S(source.a(), newValue)} for a {@code @ViaConstructor}. javac chooses
 * among the candidates the call could mean by the types of its arguments, and the generated lens
 * rebuilds through the one <em>that</em> choice makes. So a check has to read the candidate the
 * call binds, not any that carries the name. This repeats javac's choice, phase by phase:
 *
 * <ol>
 *   <li>a candidate of the call's arity that every argument reaches without boxing or unboxing;
 *   <li>failing that, one they reach with them: a primitive parameter for a reference argument, and
 *       a reference one for the primitive a generated cast passes;
 *   <li>failing that, a variable-arity candidate.
 * </ol>
 *
 * <p>Within the first phase that finds any, the candidate each of whose parameters is a subtype of
 * every other one's at its place is the one called (JLS 15.12.2.5). No boxing enters that
 * comparison: {@code int} is no subtype of {@code Object}, so {@code X(int, Object)} and {@code
 * X(Object, Object)} leave a call passing an {@code Integer} and an {@code int} ambiguous.
 * Candidates whose parameters are the same types are one signature reached more than once: the one
 * concrete method among them is called, and abstract or default ones alone are settled by the most
 * specific return. Any other candidates the comparison cannot order, two concrete methods or two
 * constructors among them, leave the call nothing to choose by.
 *
 * <p>Two shapes are not repeated, and are answered {@link Undecided}: a candidate whose parameters
 * name a type variable of its own, whose applicability rests on inference, and the variable-arity
 * phase. The first is read as applicable wherever it might be, through its erasure, so that no
 * candidate javac would call is ever left out of the answer, and it leaves the choice undecided
 * only where it is one of the candidates the call chooses between.
 */
sealed interface CallBinding {

  /**
   * The call binds this method or constructor.
   *
   * @param candidate the method or constructor the call binds
   */
  record Binds(ExecutableElement candidate) implements CallBinding {}

  /** No candidate takes the arguments, so the call does not compile. */
  record NoneApplies() implements CallBinding {}

  /**
   * More than one candidate takes the arguments and none is more specific than the rest, so the
   * call does not compile.
   *
   * @param candidates the candidates the call cannot choose between, always more than one
   */
  record Ambiguous(List<ExecutableElement> candidates) implements CallBinding {}

  /**
   * The call binds one of these candidates, if it compiles at all; which one rests on inference
   * that is not repeated here.
   *
   * @param candidates every candidate the call might bind, never empty
   */
  record Undecided(List<ExecutableElement> candidates) implements CallBinding {}

  /**
   * Which of {@code named} a call passing {@code arguments} binds, made on {@code source} or
   * constructing one.
   *
   * @param types the round's type utilities; must not be null
   * @param source the type the call is made on, or constructs, as the generated lens names it; must
   *     not be null
   * @param arguments the types of the arguments in order: a getter's read, the lens's focus, or the
   *     primitive a generated cast unboxes it to; must not be null
   * @param named every candidate the generated class can reach: the methods of the name, of any
   *     arity, static or not, or the constructors of {@code source}, since javac weighs them all;
   *     must not be null
   * @return the binding (non-null)
   */
  static CallBinding resolve(
      Types types,
      DeclaredType source,
      List<? extends TypeMirror> arguments,
      List<ExecutableElement> named) {
    // Each candidate is read on the captured type the call is made on, or constructs, as javac
    // reads it there.
    DeclaredType receiver = (DeclaredType) types.capture(source);
    List<ExecutableElement> strict = applicable(types, receiver, arguments, named, true);
    List<ExecutableElement> applicable =
        strict.isEmpty() ? applicable(types, receiver, arguments, named, false) : strict;
    if (applicable.isEmpty()) {
      // A variable-arity candidate takes whatever arguments follow its fixed parameters as the
      // elements of its array, however many there are, and none at all.
      List<ExecutableElement> variableArity =
          named.stream()
              .filter(
                  method ->
                      method.isVarArgs() && method.getParameters().size() <= arguments.size() + 1)
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
                                asSpecific(types, receiver, other, method)
                                    && !asSpecific(types, receiver, method, other)))
            .toList();
    // Asked of the most specific candidates rather than of every applicable one: a candidate whose
    // parameters inference decides puts the call in doubt only where it is one of those the call
    // chooses between, and an overload that loses outright says nothing about it.
    if (maximal.stream().anyMatch(CallBinding::infersItsParameters)) {
      return new Undecided(maximal);
    }
    if (maximal.size() == 1) {
      return new Binds(implementationOf(types, receiver, maximal.getFirst(), applicable));
    }
    // Candidates the comparison cannot order are one signature reached more than once, or leave
    // the call nothing to choose by (JLS 15.12.2.5). Reached more than once, a concrete method
    // beside the abstract or default ones it implements is the one called, as a class's own
    // setName(String) is beside the interface that declares it; two concrete ones, as a generic
    // class's two constructors can be under one instantiation, are ambiguous; and abstract or
    // default ones alone are merged by their return, where their erasures agree.
    if (!maximal.stream()
        .allMatch(
            method ->
                maximal.stream()
                    .allMatch(other -> sameParameters(types, receiver, method, other)))) {
      return new Ambiguous(maximal);
    }
    List<ExecutableElement> concrete =
        maximal.stream()
            .filter(
                method -> !method.getModifiers().contains(Modifier.ABSTRACT) && !method.isDefault())
            .toList();
    if (concrete.size() == 1) {
      return new Binds(concrete.getFirst());
    }
    if (!concrete.isEmpty() || !sameErasures(types, maximal)) {
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
        .<CallBinding>map(Binds::new)
        .findFirst()
        // A type none of whose inherited signatures has the most specific return is itself an
        // error, which javac reports at its declaration; the call there is ambiguous too.
        .orElse(new Ambiguous(applicable));
  }

  /**
   * The candidates of the call's arity that every argument reaches, strictly (by subtyping alone)
   * or loosely (with boxing and unboxing too).
   */
  private static List<ExecutableElement> applicable(
      Types types,
      DeclaredType receiver,
      List<? extends TypeMirror> arguments,
      List<ExecutableElement> named,
      boolean strict) {
    return named.stream()
        .filter(method -> method.getParameters().size() == arguments.size())
        .filter(
            method -> {
              List<TypeMirror> parameters = parametersOf(types, receiver, method);
              return IntStream.range(0, arguments.size())
                  .allMatch(
                      at -> {
                        TypeMirror parameter = parameters.get(at);
                        TypeMirror argument = arguments.get(at);
                        // Strict invocation is the one that boxes and unboxes nothing, so a
                        // parameter and its argument have to be both primitive or both not.
                        return (!strict
                                || parameter.getKind().isPrimitive()
                                    == argument.getKind().isPrimitive())
                            && types.isAssignable(argument, parameter);
                      });
            })
        .toList();
  }

  /**
   * Whether each of {@code method}'s parameters is a subtype of {@code other}'s at its place, which
   * makes it as specific; a primitive is a subtype of a wider primitive, and of no reference type.
   */
  private static boolean asSpecific(
      Types types, DeclaredType receiver, ExecutableElement method, ExecutableElement other) {
    List<TypeMirror> parameters = parametersOf(types, receiver, method);
    List<TypeMirror> others = parametersOf(types, receiver, other);
    return IntStream.range(0, parameters.size())
        .allMatch(at -> types.isSubtype(parameters.get(at), others.get(at)));
  }

  /**
   * The method a call to {@code chosen} runs: {@code chosen} itself, or where it is abstract, an
   * applicable concrete method a class the receiver extends declares, whose signature it subsumes.
   * javac takes that concrete method, as a raw {@code withX(List)} a class inherits is taken for
   * the {@code withX(List<String>)} an interface it implements declares.
   */
  private static ExecutableElement implementationOf(
      Types types,
      DeclaredType receiver,
      ExecutableElement chosen,
      List<ExecutableElement> applicable) {
    if (!chosen.getModifiers().contains(Modifier.ABSTRACT)) {
      return chosen;
    }
    ExecutableType declared = ProcessorUtils.memberOf(types, receiver, chosen);
    return applicable.stream()
        // Only a method a class declares implements another: a default never does.
        .filter(
            candidate ->
                !candidate.getEnclosingElement().getKind().isInterface()
                    && !candidate.getModifiers().contains(Modifier.ABSTRACT))
        .filter(
            candidate ->
                types.isSubsignature(ProcessorUtils.memberOf(types, receiver, candidate), declared))
        .findFirst()
        .orElse(chosen);
  }

  /**
   * Whether the candidates declare the same parameter types once erased, which abstract methods
   * reached through different supertypes need before their returns can settle the call: {@code
   * withX(T)} of an {@code A<String>} and {@code withX(String)} take the same types there, and are
   * still two methods to javac.
   */
  private static boolean sameErasures(Types types, List<ExecutableElement> candidates) {
    List<TypeMirror> first = erasedParameters(types, candidates.getFirst());
    return candidates.stream()
        .map(candidate -> erasedParameters(types, candidate))
        .allMatch(
            erased ->
                IntStream.range(0, first.size())
                    .allMatch(at -> types.isSameType(erased.get(at), first.get(at))));
  }

  private static List<TypeMirror> erasedParameters(Types types, ExecutableElement candidate) {
    return candidate.getParameters().stream()
        .map(parameter -> types.erasure(parameter.asType()))
        .toList();
  }

  /** Whether two candidates take the same parameter types, one signature reached twice. */
  private static boolean sameParameters(
      Types types, DeclaredType receiver, ExecutableElement method, ExecutableElement other) {
    List<TypeMirror> parameters = parametersOf(types, receiver, method);
    List<TypeMirror> others = parametersOf(types, receiver, other);
    return IntStream.range(0, parameters.size())
        .allMatch(at -> types.isSameType(parameters.get(at), others.get(at)));
  }

  /**
   * The candidate's parameters read on the receiver, each one that names a type variable of the
   * candidate's own as its erasure: an argument the erasure takes is one inference might accept.
   *
   * @param types the round's type utilities; must not be null
   * @param receiver the captured type the call is made on, or constructs; must not be null
   * @param method the method or constructor to read; must not be null
   * @return its parameter types, in order (non-null)
   */
  static List<TypeMirror> parametersOf(
      Types types, DeclaredType receiver, ExecutableElement method) {
    List<? extends TypeMirror> read =
        ProcessorUtils.memberOf(types, receiver, method).getParameterTypes();
    return IntStream.range(0, read.size())
        .mapToObj(
            at ->
                infers(method, method.getParameters().get(at).asType())
                    ? types.erasure(read.get(at))
                    : read.get(at))
        .toList();
  }

  private static boolean infersItsParameters(ExecutableElement method) {
    return method.getParameters().stream()
        .anyMatch(parameter -> infers(method, parameter.asType()));
  }

  /** Whether a parameter, as the candidate declares it, names a type variable of its own. */
  private static boolean infers(ExecutableElement method, TypeMirror parameter) {
    return method.getTypeParameters().stream()
        .anyMatch(variable -> ProcessorUtils.mentions(parameter, variable));
  }
}
