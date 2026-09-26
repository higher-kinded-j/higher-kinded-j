// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Refuses, at the declaration, a type a generated class could not name.
 *
 * <p>A processor writes top-level classes into a package: an Impl into its spec's, a companion into
 * its annotated type's, or into the package its annotation names. Such a class cannot see a {@code
 * private} type nested beside the declaration, nor a package-private type from another package, and
 * javac then fails inside a file its author never wrote. It fails even where the class never spells
 * the type, since javac checks the parameter types it infers for a lambda too. A processor lists
 * the types its class crosses, each with the words a diagnostic uses for where the declaration
 * meets it, and {@link #check} refuses each hidden one at the declaration, with a fix naming the
 * class to change.
 *
 * @since 0.4.11
 */
public final class Reachability {

  private Reachability() {}

  /**
   * One type a generated class names, and how a diagnostic says where the declaration meets it. A
   * type the class is declared over (the annotated type, a spec's domain, a permitted subtype) may
   * be the hidden type itself; a member's type (a component, a property, a bound) names it. Built
   * through {@link #over} and {@link #member}.
   *
   * @param subject how a diagnostic names where the type is met, for example {@code "record
   *     component 'sku' of 'Item'"}
   * @param type the type the generated class names
   * @param isType whether {@code subject} names the type itself rather than a member of that type
   */
  public record Crossing(String subject, TypeMirror type, boolean isType) {

    /**
     * A type the generated class is declared over.
     *
     * @param subject how a diagnostic names it, for example {@code "domain type 'Item'"}
     * @param type the type
     * @return the crossing
     */
    public static Crossing over(String subject, TypeMirror type) {
      return new Crossing(subject, type, true);
    }

    /**
     * A member whose type the generated class reads or writes.
     *
     * @param subject how a diagnostic names the member, for example {@code "record component 'sku'
     *     of 'Item'"}
     * @param type the member's type
     * @return the crossing
     */
    public static Crossing member(String subject, TypeMirror type) {
      return new Crossing(subject, type, false);
    }
  }

  /**
   * Where a generated class is declared, and what a refusal says about it.
   *
   * @param packageName the package the class is written into; empty for the unnamed package
   * @param why the sentence saying why each crossed type has to be visible there
   * @param relocation given the package a hidden type is declared in, the move of the declaration
   *     that would reach it, as a clause completing "or ...", for example {@code "declare the spec
   *     in 'q'"}; empty where no move does
   */
  public record Target(
      String packageName, String why, Function<String, Optional<String>> relocation) {}

  /**
   * The move a class written under a {@code targetPackage} offers: written beside its declaration
   * instead, it reaches a type from the declaration's own package. A class already written there
   * has no such move.
   *
   * @param packageName the package the class is written into
   * @param ownPackage the package of the declaration it is generated for
   * @return the relocation, for {@link Target#relocation}
   */
  public static Function<String, Optional<String>> removingTargetPackage(
      String packageName, String ownPackage) {
    return home ->
        !packageName.equals(ownPackage) && home.equals(ownPackage)
            ? Optional.of("remove targetPackage")
            : Optional.empty();
  }

  /**
   * Where a companion generated for an annotated type is declared: a top-level class in {@code
   * packageName}, the type's own package or the one its annotation names.
   *
   * @param packageName the package the companion is written into
   * @param ownPackage the annotated type's own package
   * @return the target
   */
  public static Target companion(String packageName, String ownPackage) {
    return new Target(
        packageName,
        "The generated companion is a top-level class in that package, where it names every type"
            + " it reads or writes, so each has to be visible from there.",
        removingTargetPackage(packageName, ownPackage));
  }

  /**
   * An annotated record, the bounds of its type parameters, and the given components of it: what a
   * companion that reads and rebuilds those components names. A companion generated for no
   * component names none of them.
   *
   * @param record the annotated record
   * @param components the components the companion reads or writes
   * @return the crossings, in that order
   */
  public static Stream<Crossing> record(
      TypeElement record, List<? extends RecordComponentElement> components) {
    // With nothing to generate for, the companion names the record in its Javadoc alone.
    if (components.isEmpty()) {
      return Stream.empty();
    }
    return Stream.of(
            Stream.of(declared(record)),
            bounds(record),
            components.stream().map(component -> component(record, component)))
        .flatMap(Function.identity());
  }

  /**
   * A sum type, and each permitted subtype with the bounds of its type parameters and the sum type
   * as its own clause names it: what a companion of prisms into the subtypes names. A prism is
   * written against that clause ({@code Circle implements Shape<Unit>} focuses {@code
   * Shape<Unit>}), never against the sum's own type parameters. An enum permits nothing, so it is
   * the enum alone.
   *
   * @param types the round's type utilities
   * @param sum the sealed interface or enum
   * @return the crossings, in that order
   */
  public static Stream<Crossing> sum(Types types, TypeElement sum) {
    return Stream.concat(
        Stream.of(declared(sum)),
        sum.getPermittedSubclasses().stream()
            .map(subtype -> (TypeElement) types.asElement(subtype))
            .flatMap(
                subtype ->
                    Stream.of(
                            Stream.of(
                                Crossing.over(
                                    "permitted subtype '"
                                        + name(subtype)
                                        + "' of '"
                                        + sum.getSimpleName()
                                        + "'",
                                    subtype.asType())),
                            bounds(subtype),
                            Stream.of(
                                Crossing.member(
                                    "the supertype clause of '" + subtype.getSimpleName() + "'",
                                    ProcessorUtils.sumTypeAsNamedBy(sum, subtype))))
                        .flatMap(Function.identity())));
  }

  /**
   * A type a generated class is declared over, described by its kind and its name as written from
   * the outermost enclosing type, for example {@code "record 'Shop.Item'"}.
   *
   * @param type the type
   * @return the crossing
   */
  public static Crossing declared(TypeElement type) {
    String kind =
        switch (type.getKind()) {
          case RECORD -> "record";
          case ENUM -> "enum";
          case INTERFACE, ANNOTATION_TYPE -> "interface";
          default -> "class";
        };
    return Crossing.over(kind + " '" + name(type) + "'", type.asType());
  }

  /**
   * A declared type's name as written from its outermost enclosing type, without its own type
   * arguments, for example {@code "Shop.Item"}.
   *
   * @param type the type
   * @return the name
   */
  public static String name(TypeElement type) {
    return ProcessorUtils.declaredHead((DeclaredType) type.asType());
  }

  /**
   * The bounds of a generic type's parameters, which a generated class redeclares.
   *
   * @param type the generic type
   * @return one crossing per bound
   */
  public static Stream<Crossing> bounds(TypeElement type) {
    return type.getTypeParameters().stream()
        .flatMap(
            parameter ->
                parameter.getBounds().stream()
                    .map(
                        bound ->
                            Crossing.member(
                                "type parameter '"
                                    + parameter.getSimpleName()
                                    + "' of '"
                                    + type.getSimpleName()
                                    + "'",
                                bound)));
  }

  /**
   * A record component a generated class reads or writes.
   *
   * @param record the record declaring it
   * @param component the component
   * @return the crossing
   */
  public static Crossing component(TypeElement record, RecordComponentElement component) {
    return Crossing.member(
        "record component '" + component.getSimpleName() + "' of '" + record.getSimpleName() + "'",
        component.asType());
  }

  /**
   * Refuses every type the crossings name that {@code target}'s package cannot reach, at the first
   * crossing naming it, so one compilation lists them all. A type is reported once for each set of
   * links that hide it: a {@code private} interface hides every type nested in it too, and that is
   * one cause, with one fix.
   *
   * @param env the processing environment
   * @param tag the annotation tag the diagnostic opens with, for example {@code "@GenerateLenses"}
   * @param at the declaration the refusal is reported on
   * @param target where the generated class is declared
   * @param crossings the types the generated class names
   * @return true when every crossed type is reachable
   */
  public static boolean check(
      ProcessingEnvironment env,
      String tag,
      Element at,
      Target target,
      Stream<Crossing> crossings) {
    Elements elements = env.getElementUtils();
    Set<List<Element>> reported = new HashSet<>();
    crossings.forEach(
        crossing -> {
          TypeElement hidden =
              ProcessorUtils.firstUnreachableIn(elements, crossing.type(), target.packageName());
          if (hidden == null) {
            return;
          }
          Hiding hiding = hiding(elements, hidden, target.packageName());
          if (!reported.add(hiding.links())) {
            return;
          }
          boolean itself =
              crossing.isType() && hidden.equals(env.getTypeUtils().asElement(crossing.type()));
          Diagnostics.error(
              env.getMessager(),
              at,
              tag,
              crossing.subject()
                  + (itself ? "" : " names '" + hidden.getSimpleName() + "', which")
                  + " cannot be reached from "
                  + (target.packageName().isEmpty()
                      ? "the unnamed package"
                      : "'" + target.packageName() + "'")
                  + ".",
              target.why(),
              fix(hidden, hiding, target));
        });
    return reported.isEmpty();
  }

  /**
   * The first type the crossings name that {@code packageName} cannot reach, for a processor that
   * declines a declaration another processor refuses, and so has to ask the same question.
   *
   * @param elements the round's element utilities
   * @param packageName the package the other processor writes into
   * @param crossings the types that processor's class names
   * @return the first hidden type, if any
   */
  public static Optional<TypeElement> firstHidden(
      Elements elements, String packageName, Stream<Crossing> crossings) {
    return crossings
        .map(crossing -> ProcessorUtils.firstUnreachableIn(elements, crossing.type(), packageName))
        .filter(Objects::nonNull)
        .findFirst();
  }

  /**
   * What hides a type from a package: the links of its enclosing chain that do, outermost first,
   * its own package, and whether any link of the chain is {@code private}.
   */
  private record Hiding(
      List<Element> links, String home, boolean samePackage, boolean anyPrivate) {}

  /**
   * In the type's own package only {@code private} hides a link; from another package, anything
   * that is not {@code public} does.
   */
  private static Hiding hiding(Elements elements, TypeElement hidden, String packageName) {
    String home = elements.getPackageOf(hidden).getQualifiedName().toString();
    boolean samePackage = home.equals(packageName);
    List<Element> links = new ArrayList<>();
    boolean anyPrivate = false;
    for (Element link = hidden;
        link.getKind() != ElementKind.PACKAGE;
        link = link.getEnclosingElement()) {
      Set<Modifier> modifiers = link.getModifiers();
      anyPrivate |= modifiers.contains(Modifier.PRIVATE);
      if (samePackage
          ? modifiers.contains(Modifier.PRIVATE)
          : !modifiers.contains(Modifier.PUBLIC)) {
        links.addFirst(link);
      }
    }
    return new Hiding(List.copyOf(links), home, samePackage, anyPrivate);
  }

  /**
   * The fix for a type {@code target}'s package cannot reach, for a caller that reports it in a
   * message of its own rather than refusing through {@link #check}.
   *
   * @param elements the round's element utilities
   * @param hidden the type the package cannot reach
   * @param target where the generated class is declared
   * @return one imperative sentence naming the class to change
   */
  public static String fix(Elements elements, TypeElement hidden, Target target) {
    return fix(hidden, hiding(elements, hidden, target.packageName()), target);
  }

  /**
   * The fix, naming each link that hides the type, and the type itself where only a class enclosing
   * it hides it. Beside the declaration, removing {@code private} is enough; from another package
   * each link has to become public, and where none of the chain is private, the move {@code target}
   * offers for the type's package, if any, reaches it as well.
   */
  private static String fix(TypeElement hidden, Hiding hiding, Target target) {
    String named =
        hiding.links().stream()
            .map(link -> "'" + link.getSimpleName() + "'")
            .collect(Collectors.joining(" and "));
    String encloses =
        hiding.links().contains(hidden) ? "" : ", which encloses '" + hidden.getSimpleName() + "'";
    return hiding.samePackage()
        ? "Remove 'private' from " + named + encloses + "."
        : "Make "
            + named
            + (encloses.isEmpty() ? "" : encloses + ",")
            + " public"
            + (hiding.anyPrivate()
                ? "."
                : target
                    .relocation()
                    .apply(hiding.home())
                    .map(move -> ", or " + move + ".")
                    .orElse("."));
  }
}
