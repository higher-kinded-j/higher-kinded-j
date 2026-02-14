// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.Set;
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
   * @param component the record component to check
   * @return {@code true} if the component has a recognised nullable annotation
   */
  public static boolean hasNullableAnnotation(RecordComponentElement component) {
    return component.getAnnotationMirrors().stream()
        .map(am -> am.getAnnotationType().toString())
        .anyMatch(NULLABLE_ANNOTATION_NAMES::contains);
  }
}
