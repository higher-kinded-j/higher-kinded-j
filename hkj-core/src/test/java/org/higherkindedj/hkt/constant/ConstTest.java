// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Const")
class ConstTest {

  @Nested
  @DisplayName("A Const always holds a value")
  class AlwaysAValue {

    @Test
    @DisplayName("a null value is refused at construction")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void nullValueIsRefused() {
      assertThatNullPointerException()
          .isThrownBy(() -> new Const<String, Integer>(null))
          .withMessageContaining("Const")
          .withMessageContaining("value cannot be null");
    }

    @Test
    @DisplayName("a mapFirst function that returns null is refused")
    void mapFirstReturningNullIsRefused() {
      Const<String, Integer> held = new Const<>("LAMP");

      assertThatNullPointerException()
          .isThrownBy(() -> held.mapFirst(_ -> null))
          .withMessageContaining("Const")
          .withMessageContaining("value cannot be null");
    }

    @Test
    @DisplayName("a bimap first function that returns null is refused")
    void bimapReturningNullIsRefused() {
      Const<String, Integer> held = new Const<>("LAMP");

      assertThatNullPointerException()
          .isThrownBy(() -> held.bimap(_ -> null, i -> i + 1))
          .withMessageContaining("Const")
          .withMessageContaining("value cannot be null");
    }
  }

  @Nested
  @DisplayName("A fold built on Const")
  class FoldOnConst {

    private final TraversalPath<List<String>, String> each =
        TraversalPath.of(Traversals.<String>forList());

    @Test
    @DisplayName("fails at the element whose function returns null, rather than folding it in")
    void failsAtTheNullElement() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  each.asFold()
                      .foldMap(
                          Monoids.string(),
                          s -> s.startsWith("L") ? null : s,
                          List.of("LAMP", "BULB")))
          .withMessageContaining("Const");
    }

    @Test
    @DisplayName("a Traversal's own fold also fails at the element whose function returns null")
    void traversalsOwnFoldFailsAtTheNullElement() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  Traversals.<String>forList()
                      .asFold()
                      .foldMap(
                          Monoids.string(),
                          s -> s.startsWith("L") ? null : s,
                          List.of("LAMP", "BULB")))
          .withMessage("a fold's function and monoid must not give null");
    }

    @Test
    @DisplayName("an indexed traversal's fold fails at the element whose function returns null")
    void indexedFoldFailsAtTheNullElement() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  IndexedTraversals.<String>forList()
                      .asIndexedFold()
                      .ifoldMap(
                          Monoids.string(), (i, s) -> i == 1 ? null : s, List.of("LAMP", "BULB")))
          .withMessage("a fold's function and monoid must not give null");
    }

    @Test
    @DisplayName("a monoid whose empty value is null fails the fold")
    @SuppressWarnings("DataFlowIssue") // a monoid that breaks the rule, to show the fold refuses it
    void nullEmptyFailsTheFold() {
      Monoid<String> nullEmpty =
          new Monoid<>() {
            @Override
            public String empty() {
              return null;
            }

            @Override
            public String combine(String a, String b) {
              return a + b;
            }
          };

      assertThatNullPointerException()
          .isThrownBy(
              () -> Traversals.<String>forList().asFold().foldMap(nullEmpty, s -> s, List.of()));
    }

    @Test
    @DisplayName("folds every element whose function gives a value")
    void foldsEveryValue() {
      assertThat(each.asFold().foldMap(Monoids.string(), s -> s + ";", List.of("LAMP", "BULB")))
          .isEqualTo("LAMP;BULB;");
    }
  }
}
