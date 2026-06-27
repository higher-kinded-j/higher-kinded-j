// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("NonEmptyList JSON")
class NonEmptyListJsonTest {

  private JsonMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = JsonMapper.builder().addModule(new HkjJacksonModule()).build();
  }

  @Nested
  @DisplayName("serialization")
  class Serialization {

    @Test
    @DisplayName("serializes as a plain JSON array, in order")
    void serializesAsArray() {
      String json = objectMapper.writeValueAsString(NonEmptyList.of(1, 2, 3));
      assertThat(json).isEqualTo("[1,2,3]");
    }

    @Test
    @DisplayName("serializes a single-element list as a one-element array")
    void serializesSingle() {
      String json = objectMapper.writeValueAsString(NonEmptyList.single("a"));
      assertThat(json).isEqualTo("[\"a\"]");
    }

    @Test
    @DisplayName("serializes as the Validated error channel")
    void serializesAsValidatedErrorChannel() {
      Validated<NonEmptyList<String>, Integer> invalid = Validated.invalidNel("bad");
      String json = objectMapper.writeValueAsString(invalid);
      assertThat(json).contains("\"valid\":false").contains("\"errors\":[\"bad\"]");
    }
  }

  @Nested
  @DisplayName("deserialization")
  class Deserialization {

    @Test
    @DisplayName("reads a JSON array into a NonEmptyList")
    void readsArray() {
      NonEmptyList<?> nel = objectMapper.readValue("[1,2,3]", NonEmptyList.class);
      assertThat(nel.head()).isEqualTo(1);
      assertThat(nel.size()).isEqualTo(3);
      assertThat(nel.toJavaList()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    @DisplayName("rejects an empty array — a NonEmptyList cannot be empty")
    void rejectsEmptyArray() {
      assertThatThrownBy(() -> objectMapper.readValue("[]", NonEmptyList.class))
          .isInstanceOf(JacksonException.class)
          .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("rejects a non-array")
    void rejectsNonArray() {
      assertThatThrownBy(() -> objectMapper.readValue("{\"head\":1}", NonEmptyList.class))
          .isInstanceOf(JacksonException.class)
          .hasMessageContaining("array");
    }

    @Test
    @DisplayName("rejects a null head element with a clean JacksonException")
    void rejectsNullHead() {
      assertThatThrownBy(() -> objectMapper.readValue("[null]", NonEmptyList.class))
          .isInstanceOf(JacksonException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("rejects a null tail element with a clean JacksonException")
    void rejectsNullTail() {
      assertThatThrownBy(() -> objectMapper.readValue("[1,null]", NonEmptyList.class))
          .isInstanceOf(JacksonException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("round-trips through JSON")
    void roundTrips() {
      NonEmptyList<Integer> original = NonEmptyList.of(10, 20, 30);
      String json = objectMapper.writeValueAsString(original);
      NonEmptyList<?> back = objectMapper.readValue(json, NonEmptyList.class);
      assertThat(back.toJavaList()).isEqualTo(List.of(10, 20, 30));
    }
  }
}
