// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;

/**
 * Stands in for jackson-databind-nullable's {@code JsonNullable}, which the processor recognises by
 * name, so a compilation test can declare a model holding one.
 */
final class JsonNullableStub {

  static final JavaFileObject SOURCE =
      JavaFileObjects.forSourceString(
          "org.openapitools.jackson.nullable.JsonNullable",
          """
          package org.openapitools.jackson.nullable;

          public final class JsonNullable<T> {
            private static final JsonNullable<?> UNDEFINED = new JsonNullable<>(null, false);
            private final T value;
            private final boolean present;

            private JsonNullable(T value, boolean present) {
              this.value = value;
              this.present = present;
            }

            @SuppressWarnings("unchecked")
            public static <T> JsonNullable<T> undefined() {
              return (JsonNullable<T>) UNDEFINED;
            }

            public static <T> JsonNullable<T> of(T value) {
              return new JsonNullable<>(value, true);
            }

            public boolean isPresent() {
              return present;
            }

            public T get() {
              if (!present) {
                throw new java.util.NoSuchElementException("undefined");
              }
              return value;
            }

            public T orElse(T other) {
              return present ? value : other;
            }
          }
          """);

  private JsonNullableStub() {}
}
