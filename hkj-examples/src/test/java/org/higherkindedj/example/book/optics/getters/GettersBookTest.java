// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.getters;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Getters page's comments show for its included regions. */
@DisplayName("the Getters page: what each getter reads")
class GettersBookTest {

  private static final Address ADDRESS = new Address("123 Main St", "London", "NW1", "UK");

  private static final Person JANE = new Person("Jane", "Smith", 45, ADDRESS);

  private static final List<Person> EMPLOYEES =
      List.of(
          new Person("John", "Doe", 30, new Address("456 Oak St", "Manchester", "M1", "UK")),
          new Person("Alice", "Johnson", 28, new Address("789 Elm Ave", "Birmingham", "B1", "UK")),
          new Person("Bob", "Williams", 35, new Address("321 Pine Rd", "Leeds", "LS1", "UK")));

  @Test
  @DisplayName("get reads a computed full name and a stored age")
  void getReadsOneValue() {
    assertThat(GettersBook.get(ADDRESS)).containsExactly("Jane Smith", 45);
  }

  @Test
  @DisplayName("a composed getter reads the person's city")
  void composedGetterReadsTheCity() {
    assertThat(GettersBook.compose()).isEqualTo("London");
  }

  @Test
  @DisplayName("a three-step chain reads the length of the CEO's full name")
  void deepChainReadsTheNameLength() {
    assertThat(GettersBook.deepChain(JANE, EMPLOYEES, ADDRESS)).isEqualTo("Jane Smith".length());
    assertThat(GettersBook.deepChain(JANE, EMPLOYEES, ADDRESS)).isEqualTo(10);
  }

  @Test
  @DisplayName("a getter answers the fold operations over exactly one value")
  void getterAnswersTheFoldOperations() {
    assertThat(GettersBook.asFold(ADDRESS))
        .containsExactly(Optional.of(45), List.of(45), true, false, Optional.of(45), 1, false);
  }

  @Test
  @DisplayName("composed with a fold, a getter reads every employee's name")
  void composedWithAFoldReadsEveryName() {
    Company company = new Company("TechCorp", JANE, EMPLOYEES, ADDRESS);

    assertThat(GettersBook.withFolds(company, EMPLOYEES))
        .containsExactly(List.of("John Doe", "Alice Johnson", "Bob Williams"), false);
  }

  @Test
  @DisplayName("getMaybe gives Just for a value and Nothing for a null")
  void getMaybeWrapsTheValue() {
    List<Maybe<?>> results = GettersBook.getMaybeBasics();

    assertThat(results).containsExactly(Maybe.just("Jane"), Maybe.nothing());
    assertThat(results).extracting(Object::toString).containsExactly("Just(Jane)", "Nothing");
  }

  @Test
  @DisplayName("flatMap over getMaybe reads the city, and Nothing through a null address")
  void flatMapNavigatesSafely() {
    assertThat(GettersBook.safeNavigation())
        .containsExactly(Maybe.just("London"), Maybe.nothing())
        .extracting(Object::toString)
        .containsExactly("Just(London)", "Nothing");
  }

  @Test
  @DisplayName("a Maybe read maps, defaults, filters through flatMap and reports")
  void maybeOperationsChain() {
    assertThat(GettersBook.maybeOperations())
        .containsExactly(
            Maybe.just("LONDON"), "London", Maybe.just("London"), "Person lives in London");
  }

  @Test
  @DisplayName("identity reads the source itself, and constant always the same value")
  void builtInHelpers() {
    assertThat(GettersBook.identity()).isEqualTo("Hello");
    assertThat(GettersBook.constant()).isEqualTo(42);
  }
}
