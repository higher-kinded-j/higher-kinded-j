// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.assertions.EitherOrBothAssert.assertThatEitherOrBoth;

import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the {@code EitherOrBoth.accumulate()} / {@code EitherOrBoth.fields()}
 * tolerant staged assembly (issue #581): warnings accumulate ({@code Both}) while the value keeps
 * flowing; any {@code Left} makes the whole assembly {@code Left}, still with every warning
 * collected, in field-declaration order.
 */
@DisplayName("EitherOrBoth accumulate()/fields() assembly")
class EitherOrBothAssemblyTest {

  private static EitherOrBoth<NonEmptyList<String>, String> right(String value) {
    return EitherOrBoth.right(value);
  }

  private static EitherOrBoth<NonEmptyList<String>, String> both(String warning, String value) {
    return EitherOrBoth.both(NonEmptyList.single(warning), value);
  }

  private static EitherOrBoth<NonEmptyList<String>, String> left(String warning) {
    return EitherOrBoth.left(NonEmptyList.single(warning));
  }

  private static EitherOrBoth<NonEmptyList<FieldError>, String> rightF(String value) {
    return EitherOrBoth.right(value);
  }

  private static EitherOrBoth<NonEmptyList<FieldError>, String> bothF(
      String message, String value) {
    return EitherOrBoth.both(NonEmptyList.single(FieldError.of(message)), value);
  }

  private static List<String> warnings(EitherOrBoth<NonEmptyList<String>, ?> result) {
    return result.fold(
        NonEmptyList::toJavaList, _ -> List.<String>of(), (nel, _) -> nel.toJavaList());
  }

  private static List<String> warningPaths(EitherOrBoth<NonEmptyList<FieldError>, ?> result) {
    return result.fold(
        nel -> nel.map(FieldError::pathString).toJavaList(),
        _ -> List.<String>of(),
        (nel, _) -> nel.map(FieldError::pathString).toJavaList());
  }

  private static String valueOf(EitherOrBoth<?, String> result) {
    return result.fold(_ -> "", value -> value, (_, value) -> value);
  }

  @Nested
  @DisplayName("accumulate(): generic warning payload, and() chains")
  class Accumulate {

    @Test
    @DisplayName("Arity 1: warnings accumulate in declaration order while the value flows")
    void arity1() {
      var allRight = EitherOrBoth.accumulate().and(right("v1")).apply(a1 -> "v:" + a1);
      assertThatEitherOrBoth(allRight).hasRight("v:v1");

      var tolerant = EitherOrBoth.accumulate().and(both("w1", "v1")).apply(a1 -> "v:" + a1);
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1");
      assertThat(valueOf(tolerant)).isEqualTo("v:v1");
    }

    @Test
    @DisplayName("Arity 2: warnings accumulate in declaration order while the value flows")
    void arity2() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .apply((a1, a2) -> String.join("+", a1, a2));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(both("w2", "v2"))
              .apply((a1, a2) -> String.join("+", a1, a2));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w2");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2");
    }

    @Test
    @DisplayName("Arity 3: warnings accumulate in declaration order while the value flows")
    void arity3() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .apply((a1, a2, a3) -> String.join("+", a1, a2, a3));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(both("w3", "v3"))
              .apply((a1, a2, a3) -> String.join("+", a1, a2, a3));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w3");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3");
    }

    @Test
    @DisplayName("Arity 4: warnings accumulate in declaration order while the value flows")
    void arity4() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .apply((a1, a2, a3, a4) -> String.join("+", a1, a2, a3, a4));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(both("w4", "v4"))
              .apply((a1, a2, a3, a4) -> String.join("+", a1, a2, a3, a4));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w4");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4");
    }

    @Test
    @DisplayName("Arity 5: warnings accumulate in declaration order while the value flows")
    void arity5() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .apply((a1, a2, a3, a4, a5) -> String.join("+", a1, a2, a3, a4, a5));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(both("w5", "v5"))
              .apply((a1, a2, a3, a4, a5) -> String.join("+", a1, a2, a3, a4, a5));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w5");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5");
    }

    @Test
    @DisplayName("Arity 6: warnings accumulate in declaration order while the value flows")
    void arity6() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .apply((a1, a2, a3, a4, a5, a6) -> String.join("+", a1, a2, a3, a4, a5, a6));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(both("w6", "v6"))
              .apply((a1, a2, a3, a4, a5, a6) -> String.join("+", a1, a2, a3, a4, a5, a6));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w6");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6");
    }

    @Test
    @DisplayName("Arity 7: warnings accumulate in declaration order while the value flows")
    void arity7() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .apply((a1, a2, a3, a4, a5, a6, a7) -> String.join("+", a1, a2, a3, a4, a5, a6, a7));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(both("w7", "v7"))
              .apply((a1, a2, a3, a4, a5, a6, a7) -> String.join("+", a1, a2, a3, a4, a5, a6, a7));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w7");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7");
    }

    @Test
    @DisplayName("Arity 8: warnings accumulate in declaration order while the value flows")
    void arity8() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(both("w8", "v8"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w8");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8");
    }

    @Test
    @DisplayName("Arity 9: warnings accumulate in declaration order while the value flows")
    void arity9() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(both("w9", "v9"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w9");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9");
    }

    @Test
    @DisplayName("Arity 10: warnings accumulate in declaration order while the value flows")
    void arity10() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(both("w10", "v10"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w10");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10");
    }

    @Test
    @DisplayName("Arity 11: warnings accumulate in declaration order while the value flows")
    void arity11() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(both("w11", "v11"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w11");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11");
    }

    @Test
    @DisplayName("Arity 12: warnings accumulate in declaration order while the value flows")
    void arity12() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(right("v12"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(both("w12", "v12"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w12");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12");
    }

    @Test
    @DisplayName("Arity 13: warnings accumulate in declaration order while the value flows")
    void arity13() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(right("v12"))
              .and(right("v13"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(right("v12"))
              .and(both("w13", "v13"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w13");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13");
    }

    @Test
    @DisplayName("Arity 14: warnings accumulate in declaration order while the value flows")
    void arity14() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(right("v12"))
              .and(right("v13"))
              .and(right("v14"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(right("v12"))
              .and(right("v13"))
              .and(both("w14", "v14"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w14");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14");
    }

    @Test
    @DisplayName("Arity 15: warnings accumulate in declaration order while the value flows")
    void arity15() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(right("v12"))
              .and(right("v13"))
              .and(right("v14"))
              .and(right("v15"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15));
      assertThatEitherOrBoth(allRight)
          .hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14+v15");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(right("v12"))
              .and(right("v13"))
              .and(right("v14"))
              .and(both("w15", "v15"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w15");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14+v15");
    }

    @Test
    @DisplayName("Arity 16: warnings accumulate in declaration order while the value flows")
    void arity16() {
      var allRight =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(right("v12"))
              .and(right("v13"))
              .and(right("v14"))
              .and(right("v15"))
              .and(right("v16"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15,
                          a16));
      assertThatEitherOrBoth(allRight)
          .hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14+v15+v16");

      var tolerant =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .and(right("v3"))
              .and(right("v4"))
              .and(right("v5"))
              .and(right("v6"))
              .and(right("v7"))
              .and(right("v8"))
              .and(right("v9"))
              .and(right("v10"))
              .and(right("v11"))
              .and(right("v12"))
              .and(right("v13"))
              .and(right("v14"))
              .and(right("v15"))
              .and(both("w16", "v16"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15,
                          a16));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warnings(tolerant)).containsExactly("w1", "w16");
      assertThat(valueOf(tolerant))
          .isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14+v15+v16");
    }
  }

  @Nested
  @DisplayName("fields(): FieldError channel, field() chains")
  class Fields {

    @Test
    @DisplayName("Arity 1: warnings accumulate in declaration order while the value flows")
    void arity1() {
      var allRight = EitherOrBoth.fields().field("f1", rightF("v1")).apply(a1 -> "v:" + a1);
      assertThatEitherOrBoth(allRight).hasRight("v:v1");

      var tolerant =
          EitherOrBoth.fields().field("f1", bothF("warn 1", "v1")).apply(a1 -> "v:" + a1);
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1");
      assertThat(valueOf(tolerant)).isEqualTo("v:v1");
    }

    @Test
    @DisplayName("Arity 2: warnings accumulate in declaration order while the value flows")
    void arity2() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .apply((a1, a2) -> String.join("+", a1, a2));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", bothF("warn 2", "v2"))
              .apply((a1, a2) -> String.join("+", a1, a2));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f2");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2");
    }

    @Test
    @DisplayName("Arity 3: warnings accumulate in declaration order while the value flows")
    void arity3() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .apply((a1, a2, a3) -> String.join("+", a1, a2, a3));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", bothF("warn 3", "v3"))
              .apply((a1, a2, a3) -> String.join("+", a1, a2, a3));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f3");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3");
    }

    @Test
    @DisplayName("Arity 4: warnings accumulate in declaration order while the value flows")
    void arity4() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .apply((a1, a2, a3, a4) -> String.join("+", a1, a2, a3, a4));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", bothF("warn 4", "v4"))
              .apply((a1, a2, a3, a4) -> String.join("+", a1, a2, a3, a4));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f4");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4");
    }

    @Test
    @DisplayName("Arity 5: warnings accumulate in declaration order while the value flows")
    void arity5() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .apply((a1, a2, a3, a4, a5) -> String.join("+", a1, a2, a3, a4, a5));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", bothF("warn 5", "v5"))
              .apply((a1, a2, a3, a4, a5) -> String.join("+", a1, a2, a3, a4, a5));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f5");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5");
    }

    @Test
    @DisplayName("Arity 6: warnings accumulate in declaration order while the value flows")
    void arity6() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .apply((a1, a2, a3, a4, a5, a6) -> String.join("+", a1, a2, a3, a4, a5, a6));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", bothF("warn 6", "v6"))
              .apply((a1, a2, a3, a4, a5, a6) -> String.join("+", a1, a2, a3, a4, a5, a6));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f6");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6");
    }

    @Test
    @DisplayName("Arity 7: warnings accumulate in declaration order while the value flows")
    void arity7() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .apply((a1, a2, a3, a4, a5, a6, a7) -> String.join("+", a1, a2, a3, a4, a5, a6, a7));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", bothF("warn 7", "v7"))
              .apply((a1, a2, a3, a4, a5, a6, a7) -> String.join("+", a1, a2, a3, a4, a5, a6, a7));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f7");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7");
    }

    @Test
    @DisplayName("Arity 8: warnings accumulate in declaration order while the value flows")
    void arity8() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", bothF("warn 8", "v8"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f8");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8");
    }

    @Test
    @DisplayName("Arity 9: warnings accumulate in declaration order while the value flows")
    void arity9() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", bothF("warn 9", "v9"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f9");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9");
    }

    @Test
    @DisplayName("Arity 10: warnings accumulate in declaration order while the value flows")
    void arity10() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", bothF("warn 10", "v10"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f10");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10");
    }

    @Test
    @DisplayName("Arity 11: warnings accumulate in declaration order while the value flows")
    void arity11() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", bothF("warn 11", "v11"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f11");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11");
    }

    @Test
    @DisplayName("Arity 12: warnings accumulate in declaration order while the value flows")
    void arity12() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", rightF("v12"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", bothF("warn 12", "v12"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f12");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12");
    }

    @Test
    @DisplayName("Arity 13: warnings accumulate in declaration order while the value flows")
    void arity13() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", rightF("v12"))
              .field("f13", rightF("v13"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", rightF("v12"))
              .field("f13", bothF("warn 13", "v13"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13) ->
                      String.join("+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f13");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13");
    }

    @Test
    @DisplayName("Arity 14: warnings accumulate in declaration order while the value flows")
    void arity14() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", rightF("v12"))
              .field("f13", rightF("v13"))
              .field("f14", rightF("v14"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14));
      assertThatEitherOrBoth(allRight).hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", rightF("v12"))
              .field("f13", rightF("v13"))
              .field("f14", bothF("warn 14", "v14"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f14");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14");
    }

    @Test
    @DisplayName("Arity 15: warnings accumulate in declaration order while the value flows")
    void arity15() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", rightF("v12"))
              .field("f13", rightF("v13"))
              .field("f14", rightF("v14"))
              .field("f15", rightF("v15"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15));
      assertThatEitherOrBoth(allRight)
          .hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14+v15");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", rightF("v12"))
              .field("f13", rightF("v13"))
              .field("f14", rightF("v14"))
              .field("f15", bothF("warn 15", "v15"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f15");
      assertThat(valueOf(tolerant)).isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14+v15");
    }

    @Test
    @DisplayName("Arity 16: warnings accumulate in declaration order while the value flows")
    void arity16() {
      var allRight =
          EitherOrBoth.fields()
              .field("f1", rightF("v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", rightF("v12"))
              .field("f13", rightF("v13"))
              .field("f14", rightF("v14"))
              .field("f15", rightF("v15"))
              .field("f16", rightF("v16"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15,
                          a16));
      assertThatEitherOrBoth(allRight)
          .hasRight("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14+v15+v16");

      var tolerant =
          EitherOrBoth.fields()
              .field("f1", bothF("warn 1", "v1"))
              .field("f2", rightF("v2"))
              .field("f3", rightF("v3"))
              .field("f4", rightF("v4"))
              .field("f5", rightF("v5"))
              .field("f6", rightF("v6"))
              .field("f7", rightF("v7"))
              .field("f8", rightF("v8"))
              .field("f9", rightF("v9"))
              .field("f10", rightF("v10"))
              .field("f11", rightF("v11"))
              .field("f12", rightF("v12"))
              .field("f13", rightF("v13"))
              .field("f14", rightF("v14"))
              .field("f15", rightF("v15"))
              .field("f16", bothF("warn 16", "v16"))
              .apply(
                  (a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16) ->
                      String.join(
                          "+", a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15,
                          a16));
      assertThatEitherOrBoth(tolerant).isBoth();
      assertThat(warningPaths(tolerant)).containsExactly("f1", "f16");
      assertThat(valueOf(tolerant))
          .isEqualTo("v1+v2+v3+v4+v5+v6+v7+v8+v9+v10+v11+v12+v13+v14+v15+v16");
    }
  }

  @Nested
  @DisplayName("The Both matrix: warnings never dropped, Left dominates")
  class BothMatrix {

    @Test
    @DisplayName("Right and Right assemble to Right")
    void rightRight() {
      var result =
          EitherOrBoth.accumulate().and(right("v1")).and(right("v2")).apply((a1, a2) -> a1 + a2);
      assertThatEitherOrBoth(result).hasRight("v1v2");
    }

    @Test
    @DisplayName("Right and Both assemble to Both, keeping the warning")
    void rightBoth() {
      var result =
          EitherOrBoth.accumulate()
              .and(right("v1"))
              .and(both("w2", "v2"))
              .apply((a1, a2) -> a1 + a2);
      assertThatEitherOrBoth(result).isBoth();
      assertThat(warnings(result)).containsExactly("w2");
      assertThat(valueOf(result)).isEqualTo("v1v2");
    }

    @Test
    @DisplayName("Both and Both assemble to Both, warnings in declaration order")
    void bothBoth() {
      var result =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(both("w2", "v2"))
              .apply((a1, a2) -> a1 + a2);
      assertThatEitherOrBoth(result).isBoth();
      assertThat(warnings(result)).containsExactly("w1", "w2");
    }

    @Test
    @DisplayName("Both and Left collapse to Left, still keeping the Both warning first")
    void bothLeft() {
      var result =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(left("w2"))
              .apply((a1, a2) -> a1 + a2);
      assertThatEitherOrBoth(result).isLeft();
      assertThat(warnings(result)).containsExactly("w1", "w2");
    }

    @Test
    @DisplayName("Left and Right collapse to Left")
    void leftRight() {
      var result =
          EitherOrBoth.accumulate().and(left("w1")).and(right("v2")).apply((a1, a2) -> a1 + a2);
      assertThatEitherOrBoth(result).isLeft();
      assertThat(warnings(result)).containsExactly("w1");
    }

    @Test
    @DisplayName("Left and Left collapse to Left with every warning, in order")
    void leftLeft() {
      var result =
          EitherOrBoth.accumulate().and(left("w1")).and(left("w2")).apply((a1, a2) -> a1 + a2);
      assertThatEitherOrBoth(result).isLeft();
      assertThat(warnings(result)).containsExactly("w1", "w2");
    }

    @Test
    @DisplayName("Both and Right assemble to Both, keeping the warning")
    void bothRight() {
      var result =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(right("v2"))
              .apply((a1, a2) -> a1 + a2);
      assertThatEitherOrBoth(result).isBoth();
      assertThat(warnings(result)).containsExactly("w1");
      assertThat(valueOf(result)).isEqualTo("v1v2");
    }

    @Test
    @DisplayName("Right and Left collapse to Left")
    void rightLeft() {
      var result =
          EitherOrBoth.accumulate().and(right("v1")).and(left("w2")).apply((a1, a2) -> a1 + a2);
      assertThatEitherOrBoth(result).isLeft();
      assertThat(warnings(result)).containsExactly("w2");
    }

    @Test
    @DisplayName("Left and Both collapse to Left, still keeping the Both warning")
    void leftBoth() {
      var result =
          EitherOrBoth.accumulate()
              .and(left("w1"))
              .and(both("w2", "v2"))
              .apply((a1, a2) -> a1 + a2);
      assertThatEitherOrBoth(result).isLeft();
      assertThat(warnings(result)).containsExactly("w1", "w2");
    }
  }

  @Nested
  @DisplayName("EitherOrBoth.zipWithAccum (the combination primitive)")
  class ZipWithAccumContract {

    @Test
    @DisplayName("Delegation check: direct zipWithAccum matches the builder result")
    void directCallMatchesBuilder() {
      var viaBuilder =
          EitherOrBoth.accumulate()
              .and(both("w1", "v1"))
              .and(left("w2"))
              .apply((a1, a2) -> a1 + a2);
      var direct =
          both("w1", "v1").zipWithAccum(left("w2"), NonEmptyList.semigroup(), (a1, a2) -> a1 + a2);

      assertThat(direct).isEqualTo(viaBuilder);
    }

    @Test
    @DisplayName("Null arguments are rejected")
    void nullArgumentsRejected() {
      var value = right("v1");
      assertThatNullPointerException()
          .isThrownBy(() -> value.zipWithAccum(null, NonEmptyList.semigroup(), (a, b) -> a));
      assertThatNullPointerException()
          .isThrownBy(() -> value.zipWithAccum(right("v2"), null, (a, b) -> a));
      assertThatNullPointerException()
          .isThrownBy(() -> value.zipWithAccum(right("v2"), NonEmptyList.semigroup(), null));
    }
  }

  @Nested
  @DisplayName("Labels and nesting")
  class Labels {

    @Test
    @DisplayName("Nesting a sub-assembly prepends the outer segment: address.zip")
    void nestedPathsCompose() {
      var address =
          EitherOrBoth.fields()
              .field("street", rightF("Main St"))
              .field("zip", bothF("odd format", "ZZ1"))
              .apply((a1, a2) -> a1 + " " + a2);
      var customer =
          EitherOrBoth.fields()
              .field("name", rightF("Ada"))
              .field("address", address)
              .apply((a1, a2) -> a1 + " @ " + a2);

      assertThatEitherOrBoth(customer).isBoth();
      assertThat(warningPaths(customer)).containsExactly("address.zip");
      assertThat(valueOf(customer)).isEqualTo("Ada @ Main St ZZ1");
    }

    @Test
    @DisplayName("An unlabelled first field keeps an empty warning path")
    void unlabelledFirstField() {
      var result =
          EitherOrBoth.fields()
              .and(bothF("odd", "v1"))
              .field("zip", bothF("bad", "v2"))
              .apply((a1, a2) -> a1 + a2);

      assertThat(warningPaths(result)).containsExactly("", "zip");
    }

    @Test
    @DisplayName("The entry stages are stateless singletons")
    void entryStagesAreSingletons() {
      assertThat(EitherOrBoth.accumulate()).isSameAs(EitherOrBoth.accumulate());
      assertThat(EitherOrBoth.fields()).isSameAs(EitherOrBoth.fields());
    }
  }

  @Nested
  @DisplayName("Null contracts")
  class NullGuards {

    @Test
    @DisplayName("The entry stages reject null arguments")
    void entryStagesRejectNulls() {
      assertThatNullPointerException().isThrownBy(() -> EitherOrBoth.accumulate().and(null));
      assertThatNullPointerException().isThrownBy(() -> EitherOrBoth.fields().and(null));
      assertThatNullPointerException()
          .isThrownBy(() -> EitherOrBoth.fields().field(null, rightF("v")));
      assertThatNullPointerException().isThrownBy(() -> EitherOrBoth.fields().field("f", null));
    }

    @Test
    @DisplayName("The generated stages reject null arguments")
    void generatedStagesRejectNulls() {
      assertThatNullPointerException()
          .isThrownBy(() -> EitherOrBoth.accumulate().and(right("v")).and(null));
      assertThatNullPointerException()
          .isThrownBy(
              () -> EitherOrBoth.fields().field("f1", rightF("v")).field(null, rightF("w")));
      assertThatNullPointerException()
          .isThrownBy(() -> EitherOrBoth.accumulate().and(right("v")).apply(null));
    }
  }
}
