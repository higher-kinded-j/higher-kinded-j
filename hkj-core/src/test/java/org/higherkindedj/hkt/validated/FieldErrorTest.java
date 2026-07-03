// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FieldError}: construction guards, path composition, and rendering. */
@DisplayName("FieldError")
class FieldErrorTest {

  @Nested
  @DisplayName("Construction")
  class Construction {

    @Test
    @DisplayName("of() creates an unlabelled error with an empty path")
    void ofCreatesUnlabelled() {
      FieldError error = FieldError.of("boom");

      assertThat(error.path()).isEmpty();
      assertThat(error.message()).isEqualTo("boom");
    }

    @Test
    @DisplayName("Null path, message, or segment is rejected")
    void nullsRejected() {
      assertThatNullPointerException().isThrownBy(() -> new FieldError(null, "m"));
      assertThatNullPointerException().isThrownBy(() -> new FieldError(List.of(), null));
      assertThatNullPointerException().isThrownBy(() -> FieldError.of(null));
      assertThatNullPointerException().isThrownBy(() -> FieldError.of("m").at(null));
    }

    @Test
    @DisplayName("The path is defensively copied and immutable")
    void pathIsDefensivelyCopied() {
      List<String> mutable = new ArrayList<>(List.of("zip"));
      FieldError error = new FieldError(mutable, "boom");
      mutable.add("hacked");

      assertThat(error.path()).containsExactly("zip");
      assertThatThrownBy(() -> error.path().add("nope"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("Path composition")
  class PathComposition {

    @Test
    @DisplayName("at() prepends, so outer labels compose around inner ones")
    void atPrepends() {
      FieldError error = FieldError.of("not a postcode").at("zip").at("address").at("order");

      assertThat(error.path()).containsExactly("order", "address", "zip");
      assertThat(error.pathString()).isEqualTo("order.address.zip");
    }

    @Test
    @DisplayName("at() returns a new instance; the original is unchanged")
    void atIsImmutable() {
      FieldError original = FieldError.of("boom").at("zip");
      FieldError nested = original.at("address");

      assertThat(original.pathString()).isEqualTo("zip");
      assertThat(nested.pathString()).isEqualTo("address.zip");
      assertThat(nested).isNotEqualTo(original);
    }
  }

  @Nested
  @DisplayName("Rendering and equality")
  class Rendering {

    @Test
    @DisplayName("toString() renders 'path: message', or just the message when unlabelled")
    void toStringRendering() {
      assertThat(FieldError.of("boom")).hasToString("boom");
      assertThat(FieldError.of("boom").at("zip").at("address")).hasToString("address.zip: boom");
    }

    @Test
    @DisplayName("pathString() is empty for unlabelled errors")
    void emptyPathString() {
      assertThat(FieldError.of("boom").pathString()).isEmpty();
    }

    @Test
    @DisplayName("Value equality holds for equal path and message")
    void valueEquality() {
      assertThat(FieldError.of("boom").at("zip"))
          .isEqualTo(new FieldError(List.of("zip"), "boom"))
          .hasSameHashCodeAs(new FieldError(List.of("zip"), "boom"));
      assertThat(FieldError.of("boom")).isNotEqualTo(FieldError.of("bang"));
    }
  }
}
