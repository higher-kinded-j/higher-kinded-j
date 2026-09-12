// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The binding a sparse PATCH relies on for an {@code Optional}-typed bean property. A generated
 * {@code updateFrom} leaves a component unchanged when the property reads {@code null}, clears it
 * when the property reads {@code Optional.empty()}, and sets it otherwise. Which of those a request
 * body produces is Jackson's decision, not the processor's, so it is pinned here against a mapper
 * carrying the module the starter registers.
 */
@DisplayName("Optional PATCH property JSON binding")
class OptionalPatchPropertyJsonTest {

  /** The recommended shape: the field defaults to null, so an omitted property stays absent. */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // intentional Optional-typed fixtures
  static class NullDefaultPatch {
    private Optional<String> nickname;

    public Optional<String> getNickname() {
      return nickname;
    }

    public void setNickname(Optional<String> nickname) {
      this.nickname = nickname;
    }
  }

  /** The idiomatic default, under which an omitted property and an explicit null read the same. */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // intentional Optional-typed fixtures
  static class EmptyDefaultPatch {
    private Optional<String> nickname = Optional.empty();

    public Optional<String> getNickname() {
      return nickname;
    }

    public void setNickname(Optional<String> nickname) {
      this.nickname = nickname;
    }
  }

  private JsonMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = JsonMapper.builder().addModule(new HkjJacksonModule()).build();
  }

  @Nested
  @DisplayName("a field defaulting to null carries all three states")
  class NullDefault {

    @Test
    @DisplayName("an omitted property reads null, which a sparse update leaves unchanged")
    void omittedPropertyIsAbsent() {
      assertThat(objectMapper.readValue("{}", NullDefaultPatch.class).getNickname()).isNull();
    }

    @Test
    @DisplayName("an explicit null reads Optional.empty(), which clears the component")
    void explicitNullIsEmpty() {
      assertThat(
              objectMapper.readValue("{\"nickname\":null}", NullDefaultPatch.class).getNickname())
          .isEmpty();
    }

    @Test
    @DisplayName("a value reads Optional.of(value), which sets the component")
    void valueIsPresent() {
      assertThat(
              objectMapper
                  .readValue("{\"nickname\":\"ada\"}", NullDefaultPatch.class)
                  .getNickname())
          .contains("ada");
    }
  }

  @Nested
  @DisplayName("a field defaulting to Optional.empty() loses absence")
  class EmptyDefault {

    @Test
    @DisplayName(
        "an omitted property reads empty, so every request omitting it clears the component")
    void omittedPropertyReadsAsAClear() {
      assertThat(objectMapper.readValue("{}", EmptyDefaultPatch.class).getNickname()).isEmpty();
    }
  }
}
