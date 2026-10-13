// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Each row is a name as written, the names a type declares, and the one offered. A prefix swapped
 * between {@code set} and {@code with} is exactly three edits, {@link ProcessorUtils#NEAR_MISS}, so
 * the rows that offer nothing across conventions would offer a near miss were that raised.
 */
@DisplayName("NamingConvention - the name a spec meant, in the convention the call needs")
class NamingConventionTest {

  private static List<String> declared(String names) {
    return List.of(names.split(" "));
  }

  @Test
  @DisplayName("each convention spells a name in the order it prefers")
  void spellsInPreferredOrder() {
    assertThat(NamingConvention.ACCESSOR.of("name")).containsExactly("name", "getName", "isName");
    assertThat(NamingConvention.SETTER.of("name")).containsExactly("setName");
    assertThat(NamingConvention.BUILDER_SETTER.of("name"))
        .containsExactly("name", "setName", "withName");
    assertThat(NamingConvention.WITHER.of("name")).containsExactly("withName");
  }

  @Test
  @DisplayName("a null name is refused")
  void nullIsRefused() {
    assertThatNullPointerException().isThrownBy(() -> NamingConvention.ACCESSOR.of(null));
    assertThatNullPointerException()
        .isThrownBy(() -> NamingConvention.ACCESSOR.sameValueAmong(null, List.of()));
    assertThatNullPointerException().isThrownBy(() -> NamingConvention.WITHER.spells(null));
  }

  @Test
  @DisplayName("a convention without the bare name spells a name only after one of its prefixes")
  void spellsOnlyAfterItsOwnPrefixes() {
    assertThat(NamingConvention.WITHER.spells("withTitle")).isTrue();
    assertThat(NamingConvention.WITHER.spells("title")).isFalse();
    assertThat(NamingConvention.WITHER.spells("setTitle")).isFalse();
    assertThat(NamingConvention.WITHER.spells("without")).isFalse();
    assertThat(NamingConvention.ACCESSOR.spells("title")).isTrue();
    assertThat(NamingConvention.ACCESSOR.spells("getTitle")).isTrue();
    assertThat(NamingConvention.ACCESSOR.spells("setTitle")).isTrue();
  }

  @ParameterizedTest(name = "{0} among {1} offers {2}")
  @CsvSource(
      delimiter = '|',
      nullValues = "-",
      textBlock =
          """
          name      | getName         | getName
          active    | isActive        | isActive
          getName   | name            | name
          getActive | isActive        | isActive
          getIsOpen | isOpen          | isOpen
          setName   | name            | name
          name      | names getName   | getName
          x         | X getX          | getX
          x         | isX getX        | getX
          isName    | getName name    | name
          getIsolated | isIsolated isolated | isolated
          nmae      | name            | name
          getaway   | away            | -
          get       | getter isGetter | -
          ''        | get             | -
          url       | getURL          | -
          """)
  @DisplayName("an accessor is offered bare, or with a get or is prefix, else as a near miss")
  void accessor(String written, String names, String offered) {
    assertThat(NamingConvention.ACCESSOR.nearest(written, declared(names)))
        .isEqualTo(Optional.ofNullable(offered));
  }

  @ParameterizedTest(name = "{0} among {1} offers {2}")
  @CsvSource(
      delimiter = '|',
      nullValues = "-",
      textBlock =
          """
          name     | setName   | setName
          getName  | setName   | setName
          isActive | setActive | setActive
          withName | setName   | setName
          setNme   | setName   | setName
          setName  | name      | -
          setName  | withName  | -
          name     | withName  | -
          withNam  | withName  | -
          """)
  @DisplayName("a copy's setter is offered only with a set prefix, near miss or not")
  void setter(String written, String names, String offered) {
    assertThat(NamingConvention.SETTER.nearest(written, declared(names)))
        .isEqualTo(Optional.ofNullable(offered));
  }

  @ParameterizedTest(name = "{0} among {1} offers {2}")
  @CsvSource(
      delimiter = '|',
      nullValues = "-",
      textBlock =
          """
          setName | name         | name
          getName | name         | name
          name    | setName      | setName
          url     | withUrl      | withUrl
          setUrl  | withUrl      | withUrl
          getName | setName name | name
          """)
  @DisplayName("a builder's setter is offered bare, or with a set or with prefix")
  void builderSetter(String written, String names, String offered) {
    assertThat(NamingConvention.BUILDER_SETTER.nearest(written, declared(names)))
        .isEqualTo(Optional.ofNullable(offered));
  }

  @ParameterizedTest(name = "{0} among {1} offers {2}")
  @CsvSource(
      delimiter = '|',
      nullValues = "-",
      textBlock =
          """
          name     | withName         | withName
          setName  | withName         | withName
          name     | setName withName | withName
          withName | name             | -
          withName | setName          | -
          withNme  | withName         | withName
          setNam   | setName          | -
          without  | out              | -
          """)
  @DisplayName("a wither is offered only with a with prefix, near miss or not")
  void wither(String written, String names, String offered) {
    assertThat(NamingConvention.WITHER.nearest(written, declared(names)))
        .isEqualTo(Optional.ofNullable(offered));
  }

  @Test
  @DisplayName("the same value alone is offered where a name was found, and no near miss")
  void sameValueOffersNoNearMiss() {
    assertThat(NamingConvention.WITHER.sameValueAmong("withNme", List.of("withName"))).isEmpty();
    assertThat(NamingConvention.WITHER.nearest("withNme", List.of("withName")))
        .contains("withName");
  }
}
