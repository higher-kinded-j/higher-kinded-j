// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NonEmptyList validation integration")
class NonEmptyListValidationTest {

  @Nested
  @DisplayName("Validated NEL factories")
  class ValidatedFactories {

    @Test
    @DisplayName("validNel creates a Valid")
    void validNel() {
      Validated<NonEmptyList<String>, Integer> v = Validated.validNel(42);
      assertThat(v.isValid()).isTrue();
      assertThat(v.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("invalidNel wraps a single error in a singleton NonEmptyList")
    void invalidNel() {
      Validated<NonEmptyList<String>, Integer> v = Validated.invalidNel("bad");
      assertThat(v.isInvalid()).isTrue();
      // total access — head() never throws
      assertThat(v.getError().head()).isEqualTo("bad");
      assertThat(v.getError().toJavaList()).containsExactly("bad");
    }

    @Test
    @DisplayName("invalidNel rejects a null error")
    void invalidNelNull() {
      assertThatThrownBy(() -> Validated.invalidNel(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Path NEL factories")
  class PathFactories {

    @Test
    @DisplayName("validNel creates a valid ValidationPath")
    void validNel() {
      ValidationPath<NonEmptyList<String>, Integer> path = Path.validNel(42);
      assertThat(path.isValid()).isTrue();
      assertThat(path.getOrElse(-1)).isEqualTo(42);
    }

    @Test
    @DisplayName("invalidNel creates an invalid ValidationPath with a total head()")
    void invalidNel() {
      ValidationPath<NonEmptyList<String>, Integer> path = Path.invalidNel("bad");
      assertThat(path.isInvalid()).isTrue();
      assertThat(path.run().getError().head()).isEqualTo("bad");
    }

    @Test
    @DisplayName("invalidNel rejects a null error")
    void invalidNelNull() {
      assertThatThrownBy(() -> Path.invalidNel(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("validatedNel wraps an existing Validated")
    void validatedNel() {
      ValidationPath<NonEmptyList<String>, Integer> path =
          Path.validatedNel(Validated.invalidNel("bad"));
      assertThat(path.isInvalid()).isTrue();
      assertThat(path.run().getError().toJavaList()).containsExactly("bad");
    }

    @Test
    @DisplayName("validatedNel rejects null")
    void validatedNelNull() {
      assertThatThrownBy(() -> Path.validatedNel(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("accumulation (baked-in NonEmptyList.semigroup)")
  class Accumulation {

    @Test
    @DisplayName("andAlso concatenates two invalid NEL channels, left-to-right")
    void andAlsoAccumulates() {
      ValidationPath<NonEmptyList<String>, String> result =
          Path.<String, String>invalidNel("e1").andAlso(Path.<String, String>invalidNel("e2"));
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.run().getError().toJavaList()).containsExactly("e1", "e2");
    }

    @Test
    @DisplayName("zipWithAccum collects errors from both sides in order")
    void zipWithAccumCollects() {
      ValidationPath<NonEmptyList<String>, String> result =
          Path.<String, String>invalidNel("e1")
              .zipWithAccum(Path.<String, String>invalidNel("e2"), (a, b) -> a + b);
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.run().getError().toJavaList()).containsExactly("e1", "e2");
    }

    @Test
    @DisplayName("zipWithAccum combines two valid values with no Semigroup argument")
    void zipWithAccumValid() {
      ValidationPath<NonEmptyList<String>, String> result =
          Path.<String, String>validNel("a")
              .zipWithAccum(Path.<String, String>validNel("b"), (a, b) -> a + b);
      assertThat(result.isValid()).isTrue();
      assertThat(result.getOrElse("?")).isEqualTo("ab");
    }
  }
}
