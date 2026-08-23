// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.RecordComponentElement;

/**
 * Utility class for detecting nullable annotations on record components.
 *
 * <p>This class provides shared functionality for identifying fields annotated with common
 * {@code @Nullable} annotations from various libraries. It is used by annotation processors to
 * determine when to generate null-safe optics.
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

  private NullableAnnotations() {
    // Utility class - prevent instantiation
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
