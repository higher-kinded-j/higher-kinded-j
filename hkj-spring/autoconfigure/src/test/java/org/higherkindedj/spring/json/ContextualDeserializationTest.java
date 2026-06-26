// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies that the HKJ deserializers resolve their generic element types from a {@code
 * TypeReference} (or field type), so nested custom types round-trip to the real type rather than a
 * {@code LinkedHashMap}.
 */
@DisplayName("Contextual deserialization of HKJ types")
class ContextualDeserializationTest {

  record Point(int x, int y) {}

  private JsonMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = JsonMapper.builder().addModule(new HkjJacksonModule()).build();
  }

  @Nested
  @DisplayName("NonEmptyList")
  class NonEmptyLists {

    @Test
    @DisplayName("round-trips a NonEmptyList of custom records to the real element type")
    void roundTripsCustomElements() {
      NonEmptyList<Point> original = NonEmptyList.of(new Point(1, 2), new Point(3, 4));
      String json = mapper.writeValueAsString(original);

      NonEmptyList<Point> back =
          mapper.readValue(json, new TypeReference<NonEmptyList<Point>>() {});

      assertThat(back.head()).isEqualTo(new Point(1, 2)); // not a LinkedHashMap
      assertThat(back.toJavaList()).containsExactly(new Point(1, 2), new Point(3, 4));
    }
  }

  @Nested
  @DisplayName("Either")
  class Eithers {

    @Test
    @DisplayName("round-trips a Right of a custom record")
    void roundTripsRight() {
      Either<String, Point> original = Either.right(new Point(3, 4));
      String json = mapper.writeValueAsString(original);

      Either<String, Point> back =
          mapper.readValue(json, new TypeReference<Either<String, Point>>() {});

      assertThat(back.isRight()).isTrue();
      assertThat(back.<Point>fold(l -> null, r -> r)).isEqualTo(new Point(3, 4));
    }

    @Test
    @DisplayName("round-trips a Left of a custom record")
    void roundTripsLeft() {
      Either<Point, String> original = Either.left(new Point(1, 2));
      String json = mapper.writeValueAsString(original);

      Either<Point, String> back =
          mapper.readValue(json, new TypeReference<Either<Point, String>>() {});

      assertThat(back.isLeft()).isTrue();
      assertThat(back.<Point>fold(l -> l, r -> null)).isEqualTo(new Point(1, 2));
    }
  }

  @Nested
  @DisplayName("Validated")
  class Validateds {

    @Test
    @DisplayName("round-trips a Valid of a custom record")
    void roundTripsValid() {
      Validated<String, Point> original = Validated.valid(new Point(5, 6));
      String json = mapper.writeValueAsString(original);

      Validated<String, Point> back =
          mapper.readValue(json, new TypeReference<Validated<String, Point>>() {});

      assertThat(back.isValid()).isTrue();
      assertThat(back.get()).isEqualTo(new Point(5, 6));
    }

    @Test
    @DisplayName("round-trips an Invalid whose errors are a NonEmptyList of custom records")
    void roundTripsNonEmptyListErrorChannel() {
      Validated<NonEmptyList<Point>, Integer> original =
          Validated.invalid(NonEmptyList.of(new Point(1, 1), new Point(2, 2)));
      String json = mapper.writeValueAsString(original);

      Validated<NonEmptyList<Point>, Integer> back =
          mapper.readValue(json, new TypeReference<Validated<NonEmptyList<Point>, Integer>>() {});

      assertThat(back.isInvalid()).isTrue();
      // Validated -> NonEmptyList -> Point resolved end-to-end
      assertThat(back.getError().head()).isEqualTo(new Point(1, 1));
      assertThat(back.getError().toJavaList()).containsExactly(new Point(1, 1), new Point(2, 2));
    }
  }
}
