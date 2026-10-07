// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.each;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Each page's Basic Usage section shows. */
@DisplayName("the Each page: a container's canonical traversal")
class EachBookTest {

  @Test
  @DisplayName("listEach reads every name, upper-cases every name, and sets every name")
  void traversesEveryElement() {
    EachBook.ListResults results = EachBook.traverseAll();

    assertThat(results.all()).containsExactly("alice", "bob", "charlie");
    assertThat(results.upper()).containsExactly("ALICE", "BOB", "CHARLIE");
    assertThat(results.same()).containsExactly("anonymous", "anonymous", "anonymous");
  }

  @Test
  @DisplayName("the indexed traversal numbers each element from its position")
  void numbersEachElement() {
    assertThat(EachBook.numbered()).containsExactly("1. apple", "2. banana", "3. cherry");
  }

  @Test
  @DisplayName("the map's index is its key, so one entrant's score changes alone")
  void adjustsOneValueByKey() {
    EachBook.MapResults results = EachBook.byKey();

    assertThat(results.adjusted()).isEqualTo(Map.of("alice", 100, "bob", 90, "charlie", 92));
    assertThat(results.labelled())
        .isEqualTo(Map.of("alice", "alice: 100", "bob", "bob: 85", "charlie", "charlie: 92"));
  }
}
