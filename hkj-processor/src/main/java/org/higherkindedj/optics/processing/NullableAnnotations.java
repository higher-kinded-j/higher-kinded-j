// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;

/**
 * Utility class for reading what a declaration says about {@code null}.
 *
 * <p>It identifies record components annotated with common {@code @Nullable} annotations from
 * various libraries, which annotation processors use to decide when to generate null-safe optics
 * ({@link #hasNullableAnnotation}). It also reads whether a site a mapping writes into is declared
 * non-null, by an annotation or by JSpecify's {@code @NullMarked} scope ({@link #nonNullReason}),
 * so a mapping that would write a {@code null} there can be refused, and whether a getter says it
 * may answer {@code null} ({@link #declaresNullable}), so a mapping fills the list it answers only
 * when there is one, and whether a type declares one inside it nullable ({@link
 * #hasNullableTypeArgument}), which generated code then writes out where inference drops it.
 */
public final class NullableAnnotations {

  /**
   * Set of fully qualified names for common {@code @Nullable} annotations.
   *
   * <p>Supported annotations:
   *
   * <ul>
   *   <li>{@code org.jspecify.annotations.Nullable} - JSpecify
   *   <li>{@code javax.annotation.Nullable} - JSR-305
   *   <li>{@code jakarta.annotation.Nullable} - Jakarta
   *   <li>{@code org.jetbrains.annotations.Nullable} - JetBrains
   *   <li>{@code androidx.annotation.Nullable} - AndroidX
   *   <li>{@code edu.umd.cs.findbugs.annotations.Nullable} - FindBugs/SpotBugs
   * </ul>
   */
  public static final Set<String> NULLABLE_ANNOTATION_NAMES =
      Set.of(
          "org.jspecify.annotations.Nullable",
          "javax.annotation.Nullable",
          "jakarta.annotation.Nullable",
          "org.jetbrains.annotations.Nullable",
          "androidx.annotation.Nullable",
          "edu.umd.cs.findbugs.annotations.Nullable");

  /**
   * Fully qualified names of the non-null annotations a write site may declare: the non-null twin
   * of each library in {@link #NULLABLE_ANNOTATION_NAMES}, Lombok's, whose generated setters check
   * it, and the Checker Framework's. A validation constraint such as Jakarta Validation's {@code
   * NotNull} is left out: it says what a valid value is, not what the declaration can hold.
   */
  static final Set<String> NON_NULL_ANNOTATION_NAMES =
      Set.of(
          "org.jspecify.annotations.NonNull",
          "javax.annotation.Nonnull",
          "jakarta.annotation.Nonnull",
          "org.jetbrains.annotations.NotNull",
          "androidx.annotation.NonNull",
          "edu.umd.cs.findbugs.annotations.NonNull",
          "lombok.NonNull",
          "org.checkerframework.checker.nullness.qual.NonNull");

  private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";
  private static final String NULL_UNMARKED = "org.jspecify.annotations.NullUnmarked";

  private NullableAnnotations() {
    // Utility class - prevent instantiation
  }

  /**
   * Why a site a mapping writes into takes no {@code null}: an annotation it carries, or the
   * {@code @NullMarked} scope it is declared in.
   */
  sealed interface NonNullReason {

    /** The site carries a recognised non-null annotation, named here by its simple name. */
    record Annotated(String annotation) implements NonNullReason {}

    /**
     * The site is declared inside {@code scope}, the nearest enclosing element annotated {@code
     * NullMarked}, with no {@code NullUnmarked} in between, and carries no {@code @Nullable}.
     */
    record Marked(Element scope) implements NonNullReason {}
  }

  /**
   * Why a record component takes no {@code null}, or empty when it may take one or says nothing
   * either way. The component is read at the three sites {@link #hasNullableAnnotation} reads, and
   * at its backing field as well: an annotation targeting fields and methods but not record
   * components reaches the accessor only when the accessor is implicit, and always reaches the
   * field.
   *
   * @param component the record component a canonical constructor writes
   * @return the reason the component is declared non-null, if it is
   */
  static Optional<NonNullReason> nonNullReason(RecordComponentElement component) {
    return declaredNonNull(declaredOn(component), component.asType(), component);
  }

  /**
   * Whether a record component is declared as one that may hold {@code null}: an annotation on it
   * reads as nullable, by the rule {@link #declaredNonNull} applies first, at the sites {@link
   * #nonNullReason} reads. A component typed by a type variable bounded {@code extends @Nullable
   * Object} carries no annotation of its own, so it says nothing either way: its nullness is the
   * type argument's.
   *
   * @param component the record component a mapping or a merge reads
   * @return whether its declaration says it may hold {@code null}
   */
  static boolean declaresNullable(RecordComponentElement component) {
    return declaredOn(component).stream().anyMatch(NullableAnnotations::readsNullable);
  }

  /**
   * Whether a parameter is declared as one that may take {@code null}: an annotation on it, or on
   * its type, reads as nullable, by the rule {@link #declaredNonNull} applies first.
   *
   * @param parameter the parameter, such as a canonical constructor's
   * @return whether its declaration says it may take {@code null}
   */
  static boolean declaresNullable(VariableElement parameter) {
    return Stream.of(parameter.getAnnotationMirrors(), parameter.asType().getAnnotationMirrors())
        .<AnnotationMirror>flatMap(List::stream)
        .anyMatch(NullableAnnotations::readsNullable);
  }

  /**
   * Whether a type is declared as one that may hold {@code null}: an annotation on it reads as
   * nullable, by the rule {@link #declaredNonNull} applies first. It reads a type that a
   * declaration names inside its own, such as a list's element type, where only a {@code TYPE_USE}
   * annotation can land. It reads any annotation that reads as nullable, as a check that withholds
   * or refuses gives the benefit of the doubt, where {@link #isNullable} decides what code to
   * generate and so names only the libraries it is sure of.
   *
   * @param type the type, such as a container's element type
   * @return whether it is declared as one that may hold {@code null}
   */
  static boolean declaresNullable(TypeMirror type) {
    return type.getAnnotationMirrors().stream().anyMatch(NullableAnnotations::readsNullable);
  }

  /**
   * Whether a type carries an annotation that says anything about {@code null}: one that reads as
   * nullable, or a recognised non-null one. A type variable with none stands for its type argument,
   * whose nullness is then the one that counts.
   *
   * @param type the type, such as a type variable's use
   * @return whether an annotation on it says whether it may hold {@code null}
   */
  static boolean annotatesNullness(TypeMirror type) {
    return type.getAnnotationMirrors().stream()
        .anyMatch(mirror -> readsNullable(mirror) || declaresNonNull(mirror));
  }

  /**
   * Whether a type rules {@code null} out where {@code site} names it: declared non-null, as {@link
   * #nonNullReason(TypeMirror, Element)} reads it, or a type variable with no {@code @Nullable} of
   * its own inside a {@code NullMarked} scope. Such a variable stands for whatever its type
   * argument is, which may be non-null, so it may not hold a {@code null}: JSpecify calls its
   * nullness parametric.
   *
   * @param type the type, such as a container's element type
   * @param site the declaration that names it, whose scope decides an unannotated type
   * @return whether a {@code null} is one the type may not hold
   */
  static boolean rulesOutNull(TypeMirror type, Element site) {
    return nonNullReason(type, site).isPresent()
        || declaredVariable(type) && !declaresNullable(type) && nullMarkedScope(site).isPresent();
  }

  /**
   * Whether a type is a type variable that a declaration introduces, on a class, an interface, a
   * method or a constructor, rather than one javac made by capturing a wildcard. A capture belongs
   * to none of them, and its nullness is its wildcard's bound, not an argument's.
   *
   * @param type the type
   * @return whether it is a declared type variable
   */
  static boolean declaredVariable(TypeMirror type) {
    return type.getKind() == TypeKind.TYPEVAR
        && ((TypeParameterElement) ((TypeVariable) type).asElement()).getGenericElement().getKind()
            != ElementKind.OTHER;
  }

  /**
   * Why a type that a declaration names inside its own, such as a list's element type, holds no
   * {@code null}, or empty when it may hold one or says nothing either way: an annotation on it, or
   * the {@code NullMarked} scope that {@code site}, the declaration, is in, by the rule {@link
   * #declaredNonNull} applies.
   *
   * @param type the type, such as a container's element type
   * @param site the declaration that names it, whose scope decides an unannotated type
   * @return the reason the type is declared non-null, if it is
   */
  static Optional<NonNullReason> nonNullReason(TypeMirror type, Element site) {
    return declaredNonNull(List.copyOf(type.getAnnotationMirrors()), type, site);
  }

  /**
   * The annotations a record component's declaration carries, wherever javac puts them: on the
   * component, its type, its accessor and its backing field. An annotation targeting fields and
   * methods but not record components reaches the accessor only when the accessor is implicit, and
   * always reaches the field.
   */
  private static List<AnnotationMirror> declaredOn(RecordComponentElement component) {
    Stream<AnnotationMirror> field =
        ElementFilter.fieldsIn(component.getEnclosingElement().getEnclosedElements()).stream()
            .filter(candidate -> candidate.getSimpleName().equals(component.getSimpleName()))
            .flatMap(candidate -> candidate.getAnnotationMirrors().stream());
    return Stream.concat(
            Stream.of(
                    component.getAnnotationMirrors(),
                    component.asType().getAnnotationMirrors(),
                    component.getAccessor().getAnnotationMirrors())
                .<AnnotationMirror>flatMap(List::stream),
            field)
        .toList();
  }

  /**
   * Why a setter's or a builder setter's parameter takes no {@code null}, or empty when it may take
   * one or says nothing either way. The parameter is read, and its type: a declaration annotation
   * lands on the one, a {@code TYPE_USE} annotation on the other. An annotation on the method
   * describes what it returns, so it does not count.
   *
   * @param parameter the parameter a write hands its value to
   * @return the reason the parameter is declared non-null, if it is
   */
  static Optional<NonNullReason> nonNullReason(VariableElement parameter) {
    return declaredNonNull(
        Stream.of(parameter.getAnnotationMirrors(), parameter.asType().getAnnotationMirrors())
            .<AnnotationMirror>flatMap(List::stream)
            .toList(),
        parameter.asType(),
        parameter);
  }

  /**
   * The rule every reading of a declared non-null shares, in order:
   *
   * <ol>
   *   <li>An annotation that reads as nullable makes the site nullable: one named {@code Nullable}
   *       or {@code CheckForNull}, from any package, or one qualified by a {@code when} other than
   *       {@code ALWAYS}, as JSR-305's {@code @Nonnull(when = MAYBE)} is, which is how that library
   *       defines {@code CheckForNull}. The check refuses a build, so it gives the benefit of the
   *       doubt to any annotation that reads as nullable, where {@link #NULLABLE_ANNOTATION_NAMES}
   *       decides what code to generate and so names only the libraries it is sure of.
   *   <li>A recognised non-null annotation ({@link #NON_NULL_ANNOTATION_NAMES}) makes it non-null.
   *   <li>Otherwise JSpecify's scope rule decides: a site declared inside a {@code NullMarked}
   *       element, with no {@code NullUnmarked} one in between, is non-null. A site typed by a type
   *       variable is non-null only when the variable is too, which its bounds decide: {@code <T>}
   *       declared in a marked scope has the non-null bound {@code Object}, and {@code <T
   *       extends @Nullable Object>} leaves the nullness to the type argument, which the site
   *       cannot show.
   * </ol>
   *
   * <p>Only the site's own type counts, as for {@link #hasNullableAnnotation}: {@code @Nullable
   * String[]} makes the elements nullable and leaves the array itself non-null.
   */
  private static Optional<NonNullReason> declaredNonNull(
      List<AnnotationMirror> annotations, TypeMirror type, Element site) {
    if (annotations.stream().anyMatch(NullableAnnotations::readsNullable)) {
      return Optional.empty();
    }
    return annotations.stream()
        .filter(NullableAnnotations::declaresNonNull)
        .findFirst()
        .<NonNullReason>map(
            mirror ->
                new NonNullReason.Annotated(
                    mirror.getAnnotationType().asElement().getSimpleName().toString()))
        .or(
            () ->
                type instanceof TypeVariable variable && !hasNonNullBound(variable)
                    ? Optional.empty()
                    : nullMarkedScope(site).map(NonNullReason.Marked::new));
  }

  /**
   * Whether a method is declared as one that may return {@code null}: an annotation on it, or on
   * its return type, reads as nullable, by the rule {@link #declaredNonNull} applies first. A
   * declaration annotation lands on the method and a {@code TYPE_USE} one on the return type; one
   * on a type argument describes the elements, and does not count. The reading is lenient because a
   * getter it misses, if it does answer {@code null}, turns a write into its result into a {@code
   * NullPointerException}, where one it wrongly takes for nullable costs only a check.
   *
   * @param method the method, such as a bean getter
   * @return whether its declaration says it may return {@code null}
   */
  static boolean declaresNullable(ExecutableElement method) {
    return Stream.of(method.getAnnotationMirrors(), method.getReturnType().getAnnotationMirrors())
        .<AnnotationMirror>flatMap(List::stream)
        .anyMatch(NullableAnnotations::readsNullable);
  }

  private static boolean readsNullable(AnnotationMirror mirror) {
    Name name = mirror.getAnnotationType().asElement().getSimpleName();
    return name.contentEquals("Nullable")
        || name.contentEquals("CheckForNull")
        || mirror.getElementValues().entrySet().stream()
            .anyMatch(
                value ->
                    value.getKey().getSimpleName().contentEquals("when")
                        && !value.getValue().getValue().toString().equals("ALWAYS"));
  }

  private static boolean declaresNonNull(AnnotationMirror mirror) {
    return NON_NULL_ANNOTATION_NAMES.contains(qualifiedName(mirror));
  }

  /**
   * Whether a type variable can only stand for non-null types: declared inside a {@code NullMarked}
   * scope, where an unannotated bound is non-null, with no bound that reads as nullable.
   */
  private static boolean hasNonNullBound(TypeVariable variable) {
    TypeParameterElement parameter = (TypeParameterElement) variable.asElement();
    return parameter.getBounds().stream()
            .noneMatch(
                bound ->
                    bound.getAnnotationMirrors().stream()
                        .anyMatch(NullableAnnotations::readsNullable))
        && nullMarkedScope(parameter).isPresent();
  }

  /**
   * The nearest element enclosing {@code site}, itself included, that is annotated {@code
   * NullMarked}, walking out through the method, the classes, the package and the module. Empty
   * when a {@code NullUnmarked} one is nearer, or when neither encloses it.
   */
  private static Optional<Element> nullMarkedScope(Element site) {
    for (Element scope = site; scope != null; scope = scope.getEnclosingElement()) {
      if (annotatedWith(scope, NULL_UNMARKED)) {
        return Optional.empty();
      }
      if (annotatedWith(scope, NULL_MARKED)) {
        return Optional.of(scope);
      }
    }
    return Optional.empty();
  }

  private static boolean annotatedWith(Element element, String qualifiedName) {
    return element.getAnnotationMirrors().stream()
        .anyMatch(mirror -> qualifiedName(mirror).equals(qualifiedName));
  }

  private static String qualifiedName(AnnotationMirror mirror) {
    return ((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().toString();
  }

  /**
   * Checks if a record component has a {@code @Nullable} annotation.
   *
   * <p>An annotation written on a record component does not necessarily land on the component
   * element. javac copies a declaration annotation to every declaration it is applicable to, and a
   * {@code TYPE_USE} annotation lands on the component's type instead, so where it ends up is
   * decided by the annotation's own {@code @Target}. Three sites between them cover every
   * recognised name:
   *
   * <ul>
   *   <li>the component itself: an annotation that declares no {@code @Target} at all, which makes
   *       it applicable to every declaration context (JSR-305's and Jakarta's)
   *   <li>the component's type: a {@code TYPE_USE} annotation (JSpecify's and JetBrains')
   *   <li>the accessor: an annotation targeting {@code METHOD} (JetBrains', AndroidX's and
   *       SpotBugs')
   * </ul>
   *
   * <p>The backing field and the canonical constructor parameter carry the same declaration
   * annotation as the accessor, so they are not probed separately: every recognised name that
   * targets {@code FIELD} or {@code PARAMETER} also targets {@code METHOD}.
   *
   * <p>Only the component's own type counts. A {@code TYPE_USE} annotation on a type argument, as
   * in {@code List<@Nullable String>}, describes the elements rather than the field and leaves the
   * field itself non-null.
   *
   * @param component the record component to check
   * @return {@code true} if the component has a recognised nullable annotation
   */
  public static boolean hasNullableAnnotation(RecordComponentElement component) {
    return hasNullable(component.getAnnotationMirrors())
        || hasNullable(component.asType().getAnnotationMirrors())
        || hasNullable(component.getAccessor().getAnnotationMirrors());
  }

  /**
   * Whether a type carries a recognised nullable {@code TYPE_USE} annotation on itself, as the
   * element of {@code List<@Nullable Leaf>} does, rather than on a type inside it.
   *
   * @param type the type to check
   * @return {@code true} if the type itself is annotated nullable
   */
  static boolean isNullable(TypeMirror type) {
    return hasNullable(type.getAnnotationMirrors());
  }

  private static boolean hasNullable(List<? extends AnnotationMirror> mirrors) {
    return mirrors.stream()
        .map(mirror -> mirror.getAnnotationType().toString())
        .anyMatch(NULLABLE_ANNOTATION_NAMES::contains);
  }

  /**
   * Whether a type inside {@code type}, at any depth, is declared nullable, as {@code String} is in
   * {@code List<@Nullable String>}: a type argument, a wildcard's bound or an array's component,
   * though not {@code type} itself. A nullness checker holds each such position to the nullness it
   * declares, whatever javac infers there.
   *
   * @param type the type to read
   * @return whether a type inside it is declared nullable
   */
  static boolean hasNullableTypeArgument(TypeMirror type) {
    return inside(type).anyMatch(inner -> nullableHere(inner) || hasNullableTypeArgument(inner));
  }

  /**
   * The types one level inside {@code type}: a declared type's arguments, a wildcard's bounds, or
   * an array's component. An enclosing type's arguments are not among them.
   */
  static Stream<? extends TypeMirror> inside(TypeMirror type) {
    return switch (type.getKind()) {
      case DECLARED -> ((DeclaredType) type).getTypeArguments().stream();
      case WILDCARD ->
          Stream.concat(
              Stream.ofNullable(((WildcardType) type).getExtendsBound()),
              Stream.ofNullable(((WildcardType) type).getSuperBound()));
      case ARRAY -> Stream.of(((ArrayType) type).getComponentType());
      default -> Stream.empty();
    };
  }

  /**
   * Whether {@code type} itself is declared nullable, by any annotation that reads so ({@link
   * #readsNullable}): wider than {@link #isNullable}'s recognised names, since a nullness checker
   * takes any annotation named {@code Nullable} as one.
   */
  static boolean nullableHere(TypeMirror type) {
    return type.getAnnotationMirrors().stream().anyMatch(NullableAnnotations::readsNullable);
  }
}
