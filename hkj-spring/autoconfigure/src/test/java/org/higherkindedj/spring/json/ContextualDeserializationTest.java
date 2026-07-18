// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies that the HKJ deserializers resolve their generic element types from a {@code
 * TypeReference} (or field type), so nested custom types round-trip to the real type rather than a
 * {@code LinkedHashMap}.
 */
@DisplayName("Contextual deserialisation of HKJ types")
class ContextualDeserializationTest {

  record Point(int x, int y) {}

  private JsonMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = JsonMapper.builder().addModule(new HkjJacksonModule()).build();
  }

  @Nested
  @DisplayName("HKJ types nested inside collections and maps")
  class NestedInCollections {

    record ListHolder(List<Either<String, Point>> items) {}

    record MapHolder(Map<String, Either<String, Point>> byKey) {}

    @Test
    @DisplayName("binds Either elements of a List bean field to the real branch types")
    void bindsEitherInsideListField() {
      ListHolder original =
          new ListHolder(List.of(Either.right(new Point(1, 2)), Either.left("bad")));
      String json = mapper.writeValueAsString(original);

      ListHolder back = mapper.readValue(json, ListHolder.class);

      // Regression: element contextualisation used to fall back to Object binding,
      // producing Right(LinkedHashMap) and a ClassCastException on first typed access
      assertThat(back.items().getFirst().getRight()).isEqualTo(new Point(1, 2));
      assertThat(back.items().getLast().getLeft()).isEqualTo("bad");
    }

    @Test
    @DisplayName("binds Either values of a Map bean field to the real branch types")
    void bindsEitherInsideMapField() {
      MapHolder original = new MapHolder(Map.of("a", Either.right(new Point(3, 4))));
      String json = mapper.writeValueAsString(original);

      MapHolder back = mapper.readValue(json, MapHolder.class);

      assertThat(back.byKey().get("a").getRight()).isEqualTo(new Point(3, 4));
    }

    @Test
    @DisplayName("binds Either elements of a root-level List read via TypeReference")
    void bindsEitherInsideRootList() {
      String json = "[{\"isRight\":true,\"right\":{\"x\":1,\"y\":2}}]";

      List<Either<String, Point>> back =
          mapper.readValue(json, new TypeReference<List<Either<String, Point>>>() {});

      assertThat(back.getFirst().getRight()).isEqualTo(new Point(1, 2));
    }

    record Wrapper(Either<Integer, Point> inner) {}

    @Test
    @DisplayName("binds an Either field inside a bean that is itself inside an Either")
    void bindsEitherFieldNestedInsideOuterEither() {
      // The inner Either field resolves to its own branch types, not the outer Either's
      Either<String, Wrapper> original = Either.right(new Wrapper(Either.right(new Point(7, 8))));
      String json = mapper.writeValueAsString(original);

      Either<String, Wrapper> back =
          mapper.readValue(json, new TypeReference<Either<String, Wrapper>>() {});

      assertThat(back.getRight().inner().getRight()).isEqualTo(new Point(7, 8));
    }
  }

  @Nested
  @DisplayName("Null branch values")
  class NullBranchValues {

    @Test
    @DisplayName("Validated with null value reports a clean mapping error, not an NPE")
    void validatedNullValueIsCleanError() {
      assertThatExceptionOfType(DatabindException.class)
          .isThrownBy(() -> mapper.readValue("{\"valid\":true,\"value\":null}", Validated.class))
          .withMessageContaining("must not be null");
    }

    @Test
    @DisplayName("EitherOrBoth with null left reports a clean mapping error, not an NPE")
    void eitherOrBothNullLeftIsCleanError() {
      assertThatExceptionOfType(DatabindException.class)
          .isThrownBy(
              () -> mapper.readValue("{\"kind\":\"left\",\"left\":null}", EitherOrBoth.class))
          .withMessageContaining("must not be null");
    }

    @Test
    @DisplayName("Either discriminator must be a boolean")
    void eitherNonBooleanDiscriminatorRejected() {
      assertThatExceptionOfType(DatabindException.class)
          .isThrownBy(() -> mapper.readValue("{\"isRight\":null,\"left\":\"boom\"}", Either.class))
          .withMessageContaining("boolean 'isRight'");
    }
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
