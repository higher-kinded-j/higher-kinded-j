// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HkjJacksonModule Tests")
class HkjJacksonModuleTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new HkjJacksonModule());
  }

  @Nested
  @DisplayName("Either Serialization")
  class EitherSerializationTests {

    @Test
    @DisplayName("Should serialize Either.Right to JSON")
    void shouldSerializeEitherRight() throws Exception {
      Either<String, Integer> either = Either.right(42);

      String json = objectMapper.writeValueAsString(either);

      assertThat(json).contains("\"isRight\":true");
      assertThat(json).contains("\"right\":42");
      assertThat(json).doesNotContain("left");
    }

    @Test
    @DisplayName("Should serialize Either.Left to JSON")
    void shouldSerializeEitherLeft() throws Exception {
      Either<String, Integer> either = Either.left("Error occurred");

      String json = objectMapper.writeValueAsString(either);

      assertThat(json).contains("\"isRight\":false");
      assertThat(json).contains("\"left\":\"Error occurred\"");
      assertThat(json).doesNotContain("right");
    }

    @Test
    @DisplayName("Should serialize Either.Right with complex object")
    void shouldSerializeEitherRightWithComplexObject() throws Exception {
      record User(String id, String name) {}
      Either<String, User> either = Either.right(new User("1", "Alice"));

      String json = objectMapper.writeValueAsString(either);

      assertThat(json).contains("\"isRight\":true");
      assertThat(json).contains("\"id\":\"1\"");
      assertThat(json).contains("\"name\":\"Alice\"");
    }

    @Test
    @DisplayName("Should deserialize JSON to Either.Right")
    void shouldDeserializeEitherRight() throws Exception {
      String json = "{\"isRight\":true,\"right\":42}";

      Either<?, ?> either = objectMapper.readValue(json, Either.class);

      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("Should deserialize JSON to Either.Left")
    void shouldDeserializeEitherLeft() throws Exception {
      String json = "{\"isRight\":false,\"left\":\"Error occurred\"}";

      Either<?, ?> either = objectMapper.readValue(json, Either.class);

      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("Error occurred");
    }
  }

  @Nested
  @DisplayName("Validated Serialization")
  class ValidatedSerializationTests {

    @Test
    @DisplayName("Should serialize Validated.Valid to JSON")
    void shouldSerializeValidatedValid() throws Exception {
      Validated<String, Integer> validated = Validated.valid(42);

      String json = objectMapper.writeValueAsString(validated);

      assertThat(json).contains("\"valid\":true");
      assertThat(json).contains("\"value\":42");
      assertThat(json).doesNotContain("errors");
    }

    @Test
    @DisplayName("Should serialize Validated.Invalid to JSON")
    void shouldSerializeValidatedInvalid() throws Exception {
      Validated<String, Integer> validated = Validated.invalid("Validation failed");

      String json = objectMapper.writeValueAsString(validated);

      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("\"errors\":\"Validation failed\"");
      assertThat(json).doesNotContain("value");
    }

    @Test
    @DisplayName("Should serialize Validated.Invalid with List of errors")
    void shouldSerializeValidatedInvalidWithList() throws Exception {
      record ValidationError(String field, String message) {}
      Validated<List<ValidationError>, Integer> validated =
          Validated.invalid(
              List.of(
                  new ValidationError("email", "Invalid format"),
                  new ValidationError("name", "Required")));

      String json = objectMapper.writeValueAsString(validated);

      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("\"field\":\"email\"");
      assertThat(json).contains("\"field\":\"name\"");
    }

    @Test
    @DisplayName("Should deserialize JSON to Validated.Valid")
    void shouldDeserializeValidatedValid() throws Exception {
      String json = "{\"valid\":true,\"value\":42}";

      Validated<?, ?> validated = objectMapper.readValue(json, Validated.class);

      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Should deserialize JSON to Validated.Invalid")
    void shouldDeserializeValidatedInvalid() throws Exception {
      String json = "{\"valid\":false,\"errors\":\"Validation failed\"}";

      Validated<?, ?> validated = objectMapper.readValue(json, Validated.class);

      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("Validation failed");
    }
  }

  @Nested
  @DisplayName("Nested Serialization")
  class NestedSerializationTests {

    @Test
    @DisplayName("Should serialize DTO with nested Either")
    void shouldSerializeDtoWithNestedEither() throws Exception {
      record Response(String status, Either<String, Integer> result) {}
      Response response = new Response("success", Either.right(42));

      String json = objectMapper.writeValueAsString(response);

      assertThat(json).contains("\"status\":\"success\"");
      assertThat(json).contains("\"isRight\":true");
      assertThat(json).contains("\"right\":42");
    }

    @Test
    @DisplayName("Should serialize DTO with nested Validated")
    void shouldSerializeDtoWithNestedValidated() throws Exception {
      record Response(String id, Validated<String, Integer> data) {}
      Response response = new Response("123", Validated.valid(42));

      String json = objectMapper.writeValueAsString(response);

      assertThat(json).contains("\"id\":\"123\"");
      assertThat(json).contains("\"valid\":true");
      assertThat(json).contains("\"value\":42");
    }

    @Test
    @DisplayName("Should serialize Map containing Either values")
    void shouldSerializeMapWithEitherValues() throws Exception {
      Map<String, Either<String, Integer>> map =
          Map.of(
              "first", Either.right(1),
              "second", Either.left("error"));

      String json = objectMapper.writeValueAsString(map);

      assertThat(json).contains("\"first\"");
      assertThat(json).contains("\"second\"");
      assertThat(json).contains("\"isRight\":true");
      assertThat(json).contains("\"isRight\":false");
    }

    @Test
    @DisplayName("Should serialize List of Validated values")
    void shouldSerializeListOfValidated() throws Exception {
      List<Validated<String, Integer>> list =
          List.of(Validated.valid(1), Validated.invalid("error"), Validated.valid(3));

      String json = objectMapper.writeValueAsString(list);

      assertThat(json).contains("\"valid\":true");
      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("\"value\":1");
      assertThat(json).contains("\"errors\":\"error\"");
      assertThat(json).contains("\"value\":3");
    }
  }

  @Nested
  @DisplayName("Round-trip Serialization")
  class RoundTripTests {

    @Test
    @DisplayName("Should round-trip Either.Right")
    void shouldRoundTripEitherRight() throws Exception {
      Either<String, Integer> original = Either.right(42);

      String json = objectMapper.writeValueAsString(original);
      Either<?, ?> deserialized = objectMapper.readValue(json, Either.class);

      assertThat(deserialized.isRight()).isTrue();
      assertThat(deserialized.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("Should round-trip Either.Left")
    void shouldRoundTripEitherLeft() throws Exception {
      Either<String, Integer> original = Either.left("error");

      String json = objectMapper.writeValueAsString(original);
      Either<?, ?> deserialized = objectMapper.readValue(json, Either.class);

      assertThat(deserialized.isLeft()).isTrue();
      assertThat(deserialized.getLeft()).isEqualTo("error");
    }

    @Test
    @DisplayName("Should round-trip Validated.Valid")
    void shouldRoundTripValidatedValid() throws Exception {
      Validated<String, Integer> original = Validated.valid(42);

      String json = objectMapper.writeValueAsString(original);
      Validated<?, ?> deserialized = objectMapper.readValue(json, Validated.class);

      assertThat(deserialized.isValid()).isTrue();
      assertThat(deserialized.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Should round-trip Validated.Invalid")
    void shouldRoundTripValidatedInvalid() throws Exception {
      Validated<String, Integer> original = Validated.invalid("error");

      String json = objectMapper.writeValueAsString(original);
      Validated<?, ?> deserialized = objectMapper.readValue(json, Validated.class);

      assertThat(deserialized.isInvalid()).isTrue();
      assertThat(deserialized.getError()).isEqualTo("error");
    }
  }
}
