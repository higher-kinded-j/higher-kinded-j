// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the {@code Path.accumulate()} / {@code Path.fields()} staged assembly over
 * {@link ValidationPath} (issue #581), exercising every generated arity of both flavours plus the
 * railway round-trip and semigroup normalisation guarantees.
 */
@DisplayName("ValidationPath accumulate()/fields() assembly")
class ValidationPathAssemblyTest {

  private static ValidationPath<NonEmptyList<String>, String> ok(String value) {
    return Path.validNel(value);
  }

  private static ValidationPath<NonEmptyList<String>, String> bad(String error) {
    return Path.invalidNel(error);
  }

  private static ValidationPath<NonEmptyList<FieldError>, String> okF(String value) {
    return Path.validNel(value);
  }

  private static ValidationPath<NonEmptyList<FieldError>, String> badF(String message) {
    return Path.invalidNel(FieldError.of(message));
  }

  private static List<String> errors(ValidationPath<NonEmptyList<String>, ?> result) {
    return result.run().fold(NonEmptyList::toJavaList, _ -> List.<String>of());
  }

  private static List<String> errorPaths(ValidationPath<NonEmptyList<FieldError>, ?> result) {
    return result
        .run()
        .fold(nel -> nel.map(FieldError::pathString).toJavaList(), _ -> List.<String>of());
  }

  @Nested
  @DisplayName("accumulate(): generic error payload, and() chains")
  class Accumulate {

    @Test
    @DisplayName("Arity 1: and() chain assembles; errors accumulate in declaration order")
    void arity1() {
      var valid = Path.accumulate().and(ok("v1")).apply(a1 -> "v:" + a1);
      assertThatValidated(valid.run()).isValid().hasValue("v:v1");

      var invalid = Path.accumulate().and(bad("e1")).apply(a1 -> "v:" + a1);
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1");
    }

    @Test
    @DisplayName("Arity 2: and() chain assembles; errors accumulate in declaration order")
    void arity2() {
      var valid =
          Path.accumulate().and(ok("v1")).and(ok("v2")).apply((a1, a2) -> String.join("+", a1, a2));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(bad("e2"))
              .apply((a1, a2) -> String.join("+", a1, a2));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e2");
    }

    @Test
    @DisplayName("Arity 3: and() chain assembles; errors accumulate in declaration order")
    void arity3() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .apply((a1, a2, a3) -> String.join("+", a1, a2, a3));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(bad("e3"))
              .apply((a1, a2, a3) -> String.join("+", a1, a2, a3));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e3");
    }

    @Test
    @DisplayName("Arity 4: and() chain assembles; errors accumulate in declaration order")
    void arity4() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .apply((a1, a2, a3, a4) -> String.join("+", a1, a2, a3, a4));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(bad("e4"))
              .apply((a1, a2, a3, a4) -> String.join("+", a1, a2, a3, a4));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e4");
    }

    @Test
    @DisplayName("Arity 5: and() chain assembles; errors accumulate in declaration order")
    void arity5() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .apply((a1, a2, a3, a4, a5) -> String.join("+", a1, a2, a3, a4, a5));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(bad("e5"))
              .apply((a1, a2, a3, a4, a5) -> String.join("+", a1, a2, a3, a4, a5));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e5");
    }

    @Test
    @DisplayName("Arity 6: and() chain assembles; errors accumulate in declaration order")
    void arity6() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .apply((a1, a2, a3, a4, a5, a6) -> String.join("+", a1, a2, a3, a4, a5, a6));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(bad("e6"))
              .apply((a1, a2, a3, a4, a5, a6) -> String.join("+", a1, a2, a3, a4, a5, a6));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e6");
    }

    @Test
    @DisplayName("Arity 7: and() chain assembles; errors accumulate in declaration order")
    void arity7() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .apply((a1, a2, a3, a4, a5, a6, a7) -> String.join("+", a1, a2, a3, a4, a5, a6, a7));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(bad("e7"))
              .apply((a1, a2, a3, a4, a5, a6, a7) -> String.join("+", a1, a2, a3, a4, a5, a6, a7));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e7");
    }

    @Test
    @DisplayName("Arity 8: and() chain assembles; errors accumulate in declaration order")
    void arity8() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(ok("v8"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(bad("e8"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e8");
    }

    @Test
    @DisplayName("Arity 9: and() chain assembles; errors accumulate in declaration order")
    void arity9() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(ok("v8"))
              .and(ok("v9"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8+v9");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(ok("v8"))
              .and(bad("e9"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e9");
    }

    @Test
    @DisplayName("Arity 10: and() chain assembles; errors accumulate in declaration order")
    void arity10() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(ok("v8"))
              .and(ok("v9"))
              .and(ok("v10"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(ok("v8"))
              .and(ok("v9"))
              .and(bad("e10"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e10");
    }

    @Test
    @DisplayName("Arity 11: and() chain assembles; errors accumulate in declaration order")
    void arity11() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(ok("v8"))
              .and(ok("v9"))
              .and(ok("v10"))
              .and(ok("v11"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(ok("v8"))
              .and(ok("v9"))
              .and(ok("v10"))
              .and(bad("e11"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e11");
    }

    @Test
    @DisplayName("Arity 12: and() chain assembles; errors accumulate in declaration order")
    void arity12() {
      var valid =
          Path.accumulate()
              .and(ok("v1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(ok("v8"))
              .and(ok("v9"))
              .and(ok("v10"))
              .and(ok("v11"))
              .and(ok("v12"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12");

      var invalid =
          Path.accumulate()
              .and(bad("e1"))
              .and(ok("v2"))
              .and(ok("v3"))
              .and(ok("v4"))
              .and(ok("v5"))
              .and(ok("v6"))
              .and(ok("v7"))
              .and(ok("v8"))
              .and(ok("v9"))
              .and(ok("v10"))
              .and(ok("v11"))
              .and(bad("e12"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errors(invalid)).containsExactly("e1", "e12");
    }
  }

  @Nested
  @DisplayName("fields(): FieldError channel, field() chains")
  class Fields {

    @Test
    @DisplayName("Arity 1: field() chain assembles; errors accumulate in declaration order")
    void arity1() {
      var valid = Path.fields().field("f1", okF("v1")).apply(a1 -> "v:" + a1);
      assertThatValidated(valid.run()).isValid().hasValue("v:v1");

      var invalid = Path.fields().field("f1", badF("bad 1")).apply(a1 -> "v:" + a1);
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1");
    }

    @Test
    @DisplayName("Arity 2: field() chain assembles; errors accumulate in declaration order")
    void arity2() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .apply((a1, a2) -> String.join("+", a1, a2));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", badF("bad 2"))
              .apply((a1, a2) -> String.join("+", a1, a2));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f2");
    }

    @Test
    @DisplayName("Arity 3: field() chain assembles; errors accumulate in declaration order")
    void arity3() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .apply((a1, a2, a3) -> String.join("+", a1, a2, a3));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", badF("bad 3"))
              .apply((a1, a2, a3) -> String.join("+", a1, a2, a3));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f3");
    }

    @Test
    @DisplayName("Arity 4: field() chain assembles; errors accumulate in declaration order")
    void arity4() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .apply((a1, a2, a3, a4) -> String.join("+", a1, a2, a3, a4));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", badF("bad 4"))
              .apply((a1, a2, a3, a4) -> String.join("+", a1, a2, a3, a4));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f4");
    }

    @Test
    @DisplayName("Arity 5: field() chain assembles; errors accumulate in declaration order")
    void arity5() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .apply((a1, a2, a3, a4, a5) -> String.join("+", a1, a2, a3, a4, a5));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", badF("bad 5"))
              .apply((a1, a2, a3, a4, a5) -> String.join("+", a1, a2, a3, a4, a5));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f5");
    }

    @Test
    @DisplayName("Arity 6: field() chain assembles; errors accumulate in declaration order")
    void arity6() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .apply((a1, a2, a3, a4, a5, a6) -> String.join("+", a1, a2, a3, a4, a5, a6));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", badF("bad 6"))
              .apply((a1, a2, a3, a4, a5, a6) -> String.join("+", a1, a2, a3, a4, a5, a6));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f6");
    }

    @Test
    @DisplayName("Arity 7: field() chain assembles; errors accumulate in declaration order")
    void arity7() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .apply((a1, a2, a3, a4, a5, a6, a7) -> String.join("+", a1, a2, a3, a4, a5, a6, a7));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", badF("bad 7"))
              .apply((a1, a2, a3, a4, a5, a6, a7) -> String.join("+", a1, a2, a3, a4, a5, a6, a7));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f7");
    }

    @Test
    @DisplayName("Arity 8: field() chain assembles; errors accumulate in declaration order")
    void arity8() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", okF("v8"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", badF("bad 8"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f8");
    }

    @Test
    @DisplayName("Arity 9: field() chain assembles; errors accumulate in declaration order")
    void arity9() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", okF("v8"))
              .field("f9", okF("v9"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8+v9");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", okF("v8"))
              .field("f9", badF("bad 9"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f9");
    }

    @Test
    @DisplayName("Arity 10: field() chain assembles; errors accumulate in declaration order")
    void arity10() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", okF("v8"))
              .field("f9", okF("v9"))
              .field("f10", okF("v10"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", okF("v8"))
              .field("f9", okF("v9"))
              .field("f10", badF("bad 10"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f10");
    }

    @Test
    @DisplayName("Arity 11: field() chain assembles; errors accumulate in declaration order")
    void arity11() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", okF("v8"))
              .field("f9", okF("v9"))
              .field("f10", okF("v10"))
              .field("f11", okF("v11"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", okF("v8"))
              .field("f9", okF("v9"))
              .field("f10", okF("v10"))
              .field("f11", badF("bad 11"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f11");
    }

    @Test
    @DisplayName("Arity 12: field() chain assembles; errors accumulate in declaration order")
    void arity12() {
      var valid =
          Path.fields()
              .field("f1", okF("v1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", okF("v8"))
              .field("f9", okF("v9"))
              .field("f10", okF("v10"))
              .field("f11", okF("v11"))
              .field("f12", okF("v12"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12));
      assertThatValidated(valid.run()).isValid().hasValue("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12");

      var invalid =
          Path.fields()
              .field("f1", badF("bad 1"))
              .field("f2", okF("v2"))
              .field("f3", okF("v3"))
              .field("f4", okF("v4"))
              .field("f5", okF("v5"))
              .field("f6", okF("v6"))
              .field("f7", okF("v7"))
              .field("f8", okF("v8"))
              .field("f9", okF("v9"))
              .field("f10", okF("v10"))
              .field("f11", okF("v11"))
              .field("f12", badF("bad 12"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12));
      assertThatValidated(invalid.run()).isInvalid();
      assertThat(errorPaths(invalid)).containsExactly("f1", "f12");
    }
  }

  @Nested
  @DisplayName("Railway behaviour")
  class Railway {

    @Test
    @DisplayName("The assembled path composes onward: further accumulation still collects")
    void assembledPathComposesOnward() {
      var assembled = Path.accumulate().and(bad("e1")).and(bad("e2")).apply((a1, a2) -> a1 + a2);
      var extended = assembled.zipWithAccum(bad("e3"), (a, b) -> a + b);

      assertThat(errors(extended)).containsExactly("e1", "e2", "e3");
    }

    @Test
    @DisplayName(
        "Incoming paths' own semigroups are normalised away: NEL concatenation, fixed once")
    void semigroupNormalisation() {
      Semigroup<NonEmptyList<String>> reversing = (a, b) -> b.concat(a);
      var weird1 =
          Path.validated(
              Validated.<NonEmptyList<String>, String>invalid(NonEmptyList.single("e1")),
              reversing);
      var weird2 =
          Path.validated(
              Validated.<NonEmptyList<String>, String>invalid(NonEmptyList.single("e2")),
              reversing);

      var result = Path.accumulate().and(weird1).and(weird2).apply((a1, a2) -> a1 + a2);

      assertThat(errors(result)).containsExactly("e1", "e2");
    }

    @Test
    @DisplayName("Nesting a sub-assembly prepends the outer segment: address.zip")
    void nestedPathsCompose() {
      var address =
          Path.fields()
              .field("street", okF("Main St"))
              .field("zip", badF("not a postcode"))
              .apply((a1, a2) -> a1 + a2);
      var customer =
          Path.fields()
              .field("name", okF("Ada"))
              .field("address", address)
              .apply((a1, a2) -> a1 + a2);

      assertThat(errorPaths(customer)).containsExactly("address.zip");
    }

    @Test
    @DisplayName("An unlabelled first field keeps an empty path")
    void unlabelledFirstField() {
      var result =
          Path.fields()
              .and(badF("just wrong"))
              .field("zip", badF("bad"))
              .apply((a1, a2) -> a1 + a2);

      assertThat(errorPaths(result)).containsExactly("", "zip");
    }

    @Test
    @DisplayName("The entry stages are stateless singletons")
    void entryStagesAreSingletons() {
      assertThat(Path.accumulate()).isSameAs(Path.accumulate());
      assertThat(Path.fields()).isSameAs(Path.fields());
    }
  }

  @Nested
  @DisplayName("Null contracts")
  class NullGuards {

    @Test
    @DisplayName("The entry stages reject null arguments")
    void entryStagesRejectNulls() {
      assertThatNullPointerException().isThrownBy(() -> Path.accumulate().and(null));
      assertThatNullPointerException().isThrownBy(() -> Path.fields().and(null));
      assertThatNullPointerException().isThrownBy(() -> Path.fields().field(null, okF("v")));
      assertThatNullPointerException().isThrownBy(() -> Path.fields().field("f", null));
    }

    @Test
    @DisplayName("The generated stages reject null arguments")
    void generatedStagesRejectNulls() {
      assertThatNullPointerException().isThrownBy(() -> Path.accumulate().and(ok("v")).and(null));
      assertThatNullPointerException()
          .isThrownBy(() -> Path.fields().field("f1", okF("v")).field(null, okF("w")));
      assertThatNullPointerException().isThrownBy(() -> Path.accumulate().and(ok("v")).apply(null));
    }
  }
}
