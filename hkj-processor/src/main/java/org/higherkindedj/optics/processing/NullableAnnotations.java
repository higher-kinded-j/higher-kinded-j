// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

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
   * either way. The component is read at the three sites {@link #hasNullableAnnotation} reads.
   *
   * @param component the record component a canonical constructor writes
   * @return the reason the component is declared non-null, if it is
   */
  static Optional<NonNullReason> nonNullReason(RecordComponentElement component) {
    return nonNullReason(
        Stream.of(
                component.getAnnotationMirrors(),
                component.asType().getAnnotationMirrors(),
                component.getAccessor().getAnnotationMirrors())
            .<AnnotationMirror>flatMap(List::stream)
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
    return nonNullReason(
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
   *   <li>An annotation named {@code Nullable}, from any package, makes the site nullable. The
   *       check refuses a build, so it gives the benefit of the doubt to any annotation that reads
   *       as nullable, where {@link #NULLABLE_ANNOTATION_NAMES} decides what code to generate and
   *       so names only the libraries it is sure of.
   *   <li>A recognised non-null annotation ({@link #NON_NULL_ANNOTATION_NAMES}) makes it non-null.
   *   <li>Otherwise JSpecify's scope rule decides: a class or array type declared inside a {@code
   *       NullMarked} element, with no {@code NullUnmarked} one in between, is non-null. A type
   *       variable takes its nullness from the argument it stands for, which the site cannot show,
   *       so it gives no signal.
   * </ol>
   *
   * <p>Only the site's own type counts, as for {@link #hasNullableAnnotation}: {@code @Nullable
   * String[]} makes the elements nullable and leaves the array itself non-null.
   */
  private static Optional<NonNullReason> nonNullReason(
      List<AnnotationMirror> annotations, TypeMirror type, Element site) {
    if (annotations.stream().anyMatch(NullableAnnotations::readsNullable)) {
      return Optional.empty();
    }
    Optional<NonNullReason> annotated =
        annotations.stream()
            .map(mirror -> (TypeElement) mirror.getAnnotationType().asElement())
            .filter(
                annotation ->
                    NON_NULL_ANNOTATION_NAMES.contains(annotation.getQualifiedName().toString()))
            .findFirst()
            .map(annotation -> new NonNullReason.Annotated(annotation.getSimpleName().toString()));
    if (annotated.isPresent()) {
      return annotated;
    }
    boolean reference = type.getKind() == TypeKind.DECLARED || type.getKind() == TypeKind.ARRAY;
    return reference ? nullMarkedScope(site).map(NonNullReason.Marked::new) : Optional.empty();
  }

  private static boolean readsNullable(AnnotationMirror mirror) {
    return mirror.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable");
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
        .anyMatch(mirror -> mirror.getAnnotationType().toString().equals(qualifiedName));
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
