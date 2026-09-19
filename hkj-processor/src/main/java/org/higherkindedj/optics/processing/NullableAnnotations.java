// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;

/**
 * Utility class for reading what a declaration says about {@code null}.
 *
 * <p>It identifies record components annotated with common {@code @Nullable} annotations from
 * various libraries, which annotation processors use to decide when to generate null-safe optics
 * ({@link #hasNullableAnnotation}). It also reads whether a site a mapping writes into is declared
 * non-null, by an annotation or by JSpecify's {@code @NullMarked} scope ({@link #nonNullReason}),
 * so a mapping that would write a {@code null} there can be refused.
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
    Stream<AnnotationMirror> field =
        ElementFilter.fieldsIn(component.getEnclosingElement().getEnclosedElements()).stream()
            .filter(candidate -> candidate.getSimpleName().equals(component.getSimpleName()))
            .flatMap(candidate -> candidate.getAnnotationMirrors().stream());
    return declaredNonNull(
        Stream.concat(
                Stream.of(
                        component.getAnnotationMirrors(),
                        component.asType().getAnnotationMirrors(),
                        component.getAccessor().getAnnotationMirrors())
                    .<AnnotationMirror>flatMap(List::stream),
                field)
            .toList(),
        component.asType(),
        component);
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
   * The rule both write sites share, in order:
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

  private static boolean hasNullable(List<? extends AnnotationMirror> mirrors) {
    return mirrors.stream()
        .map(mirror -> mirror.getAnnotationType().toString())
        .anyMatch(NULLABLE_ANNOTATION_NAMES::contains);
  }
}
