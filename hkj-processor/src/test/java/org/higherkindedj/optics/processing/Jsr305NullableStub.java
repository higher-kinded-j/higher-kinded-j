// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;

/**
 * Stands in for JSR-305's {@code javax.annotation.Nullable}, declared as its artefact declares it,
 * with no {@code @Target}, so a compilation test can annotate a component with it.
 */
final class Jsr305NullableStub {

  static final JavaFileObject SOURCE =
      JavaFileObjects.forSourceString(
          "javax.annotation.Nullable",
          """
          package javax.annotation;

          import java.lang.annotation.Retention;
          import java.lang.annotation.RetentionPolicy;

          @Retention(RetentionPolicy.RUNTIME)
          public @interface Nullable {}
          """);

  private Jsr305NullableStub() {}
}
