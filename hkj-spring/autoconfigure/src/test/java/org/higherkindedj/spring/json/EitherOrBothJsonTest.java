// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("EitherOrBoth JSON (de)serialization")
class EitherOrBothJsonTest {

  private JsonMapper mapper;

  record Payload(String name) {}

  record Holder(EitherOrBoth<String, Payload> result) {}

  @BeforeEach
  void setUp() {
    mapper = JsonMapper.builder().addModule(new HkjJacksonModule()).build();
  }

  @Nested
  @DisplayName("Serialization")
  class Serialization {

    @Test
    void serializesLeft() {
      String json = mapper.writeValueAsString(EitherOrBoth.<String, Integer>left("e"));
      assertThat(json)
          .contains("\"kind\":\"left\"")
          .contains("\"left\":\"e\"")
          .doesNotContain("right");
    }

    @Test
    void serializesRight() {
      String json = mapper.writeValueAsString(EitherOrBoth.<String, Integer>right(42));
      assertThat(json)
          .contains("\"kind\":\"right\"")
          .contains("\"right\":42")
          .doesNotContain("left");
    }

    @Test
    void serializesBoth() {
      String json = mapper.writeValueAsString(EitherOrBoth.both("w", 42));
      assertThat(json)
          .contains("\"kind\":\"both\"")
          .contains("\"left\":\"w\"")
          .contains("\"right\":42");
    }
  }

  @Nested
  @DisplayName("Deserialization (raw)")
  class RawDeserialization {

    @Test
    void deserializesLeft() {
      EitherOrBoth<?, ?> eob =
          mapper.readValue("{\"kind\":\"left\",\"left\":\"e\"}", EitherOrBoth.class);
      assertThat(eob.isLeft()).isTrue();
      assertThat(eob.getLeft().get()).isEqualTo("e");
    }

    @Test
    void deserializesRight() {
      EitherOrBoth<?, ?> eob =
          mapper.readValue("{\"kind\":\"right\",\"right\":42}", EitherOrBoth.class);
      assertThat(eob.isRight()).isTrue();
      assertThat(eob.getRight().get()).isEqualTo(42);
    }

    @Test
    void deserializesBoth() {
      EitherOrBoth<?, ?> eob =
          mapper.readValue("{\"kind\":\"both\",\"left\":\"w\",\"right\":42}", EitherOrBoth.class);
      assertThat(eob.isBoth()).isTrue();
      assertThat(eob.getLeft().get()).isEqualTo("w");
      assertThat(eob.getRight().get()).isEqualTo(42);
    }

    @Test
    void rejectsMissingKind() {
      assertThatExceptionOfType(MismatchedInputException.class)
          .isThrownBy(() -> mapper.readValue("{\"right\":42}", EitherOrBoth.class));
    }

    @Test
    void rejectsUnknownKind() {
      assertThatExceptionOfType(MismatchedInputException.class)
          .isThrownBy(() -> mapper.readValue("{\"kind\":\"middle\"}", EitherOrBoth.class));
    }

    @Test
    void rejectsMissingRequiredField() {
      assertThatExceptionOfType(MismatchedInputException.class)
          .isThrownBy(
              () -> mapper.readValue("{\"kind\":\"both\",\"left\":\"w\"}", EitherOrBoth.class));
    }
  }

  @Nested
  @DisplayName("Deserialization (typed via createContextual)")
  class TypedDeserialization {

    @Test
    void roundTripsTypedBothValue() {
      Holder original = new Holder(EitherOrBoth.both("deprecated", new Payload("cfg")));
      String json = mapper.writeValueAsString(original);

      Holder back = mapper.readValue(json, Holder.class);

      assertThat(back.result().isBoth()).isTrue();
      assertThat(back.result().getLeft().get()).isEqualTo("deprecated");
      // The typed read resolves the right to Payload, not a generic Map.
      assertThat(back.result().getRight().get()).isInstanceOf(Payload.class);
      assertThat(back.result().getRight().get()).isEqualTo(new Payload("cfg"));
    }
  }
}
