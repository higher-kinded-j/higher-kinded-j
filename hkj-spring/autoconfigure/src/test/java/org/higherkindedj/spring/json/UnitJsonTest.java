// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Pins the one wire form of a {@link Unit} in a body, JSON {@code null}, under both Jackson 3's
 * defaults and Jackson 2's, whose enum settings would otherwise write {@code "()"} or {@code
 * "INSTANCE"}.
 */
@DisplayName("Unit JSON")
class UnitJsonTest {

  record Outcome(String id, Either<String, Unit> result) {}

  record Ack(String id, Unit ack) {}

  record Holder(Object value) {}

  private static final TypeReference<Either<String, Unit>> EITHER_UNIT = new TypeReference<>() {};

  private static final String RIGHT_NULL = "{\"isRight\":true,\"right\":null}";

  private static final String REFUSAL = "Unit JSON must be null, \"()\" or \"INSTANCE\", not ";

  static JsonMapper jackson3Defaults() {
    return JsonMapper.builder().addModule(new HkjJacksonModule()).build();
  }

  static Stream<Named<JsonMapper>> namedMappers() {
    return Stream.of(
        named("Jackson 3 defaults", jackson3Defaults()),
        named(
            "configureForJackson2()",
            JsonMapper.builder().configureForJackson2().addModule(new HkjJacksonModule()).build()));
  }

  static Stream<Arguments> mappers() {
    return namedMappers().map(Arguments::arguments);
  }

  static Stream<Arguments> writerAndReader() {
    return namedMappers()
        .flatMap(writer -> namedMappers().map(reader -> arguments(writer, reader)));
  }

  static Stream<Arguments> mapperAndAcceptedForm() {
    return namedMappers()
        .flatMap(
            mapper ->
                Stream.of("null", "\"()\"", "\"INSTANCE\"").map(form -> arguments(mapper, form)));
  }

  /** Each refused value beside how the refusal names it. */
  static Stream<Arguments> refusedValues() {
    String longText = "x".repeat(150);
    return Stream.of(
        arguments("\"instance\"", "\"instance\""),
        arguments("\"\"", "\"\""),
        arguments("\"a\\\"b\\nc\"", "\"a\\\"b\\nc\""),
        arguments("\"" + longText + "\"", "\"" + "x".repeat(100) + "...\""),
        // a cut at the limit would split the emoji's surrogate pair, so it falls one short
        arguments("\"" + "x".repeat(99) + "\uD83D\uDE00\"", "\"" + "x".repeat(99) + "...\""),
        arguments("0", "0"),
        arguments("true", "true"),
        arguments("{}", "an object"),
        arguments("[]", "an array"));
  }

  /** Objects a type id names as a Unit, which the parser enters past the opening brace. */
  static Stream<String> refusedTypedObjects() {
    return Stream.of(
        "{\"@class\":\"org.higherkindedj.hkt.Unit\",\"x\":1}",
        "{\"@class\":\"org.higherkindedj.hkt.Unit\"}");
  }

  static JsonMapper typed(JsonMapper mapper, JsonTypeInfo.As inclusion) {
    return mapper
        .rebuild()
        .activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
            DefaultTyping.JAVA_LANG_OBJECT,
            inclusion)
        .build();
  }

  @Nested
  @DisplayName("writing")
  class Writing {

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#mappers")
    @DisplayName("writes a Unit in a list element's Either as null")
    void writesUnitInListElementAsNull(JsonMapper mapper) {
      String json = mapper.writeValueAsString(List.of(Either.<String, Unit>right(Unit.INSTANCE)));

      assertThat(json).isEqualTo("[" + RIGHT_NULL + "]");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#mappers")
    @DisplayName("writes a Unit in a record field's Either as null")
    void writesUnitInRecordFieldAsNull(JsonMapper mapper) {
      String json = mapper.writeValueAsString(new Outcome("a1", Either.right(Unit.INSTANCE)));

      assertThat(json).isEqualTo("{\"id\":\"a1\",\"result\":" + RIGHT_NULL + "}");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#mappers")
    @DisplayName("writes a Unit as null in every slot that holds one")
    void writesUnitAsNullInEverySlot(JsonMapper mapper) {
      assertThat(mapper.writeValueAsString(new Ack("a1", Unit.INSTANCE)))
          .isEqualTo("{\"id\":\"a1\",\"ack\":null}");
      assertThat(mapper.writeValueAsString(Either.<Unit, String>left(Unit.INSTANCE)))
          .isEqualTo("{\"isRight\":false,\"left\":null}");
      assertThat(mapper.writeValueAsString(Validated.<String, Unit>valid(Unit.INSTANCE)))
          .isEqualTo("{\"valid\":true,\"value\":null}");
      assertThat(mapper.writeValueAsString(EitherOrBoth.<String, Unit>both("w", Unit.INSTANCE)))
          .isEqualTo("{\"kind\":\"both\",\"left\":\"w\",\"right\":null}");
      assertThat(mapper.writeValueAsString(NonEmptyList.of(Unit.INSTANCE, Unit.INSTANCE)))
          .isEqualTo("[null,null]");
    }

    @Test
    @DisplayName("describes a Unit to a schema generator as null, the form it writes")
    void describesUnitAsNullToSchemaGenerator() {
      List<String> formats = new ArrayList<>();

      jackson3Defaults()
          .acceptJsonFormatVisitor(
              Unit.class,
              new JsonFormatVisitorWrapper.Base() {
                @Override
                public JsonNullFormatVisitor expectNullFormat(JavaType type) {
                  formats.add("null");
                  return null;
                }

                @Override
                public JsonStringFormatVisitor expectStringFormat(JavaType type) {
                  formats.add("string");
                  return null;
                }
              });

      assertThat(formats).containsExactly("null");
    }
  }

  @Nested
  @DisplayName("reading")
  class Reading {

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#mapperAndAcceptedForm")
    @DisplayName("reads null, \"()\" and \"INSTANCE\" as Unit")
    void readsEachAcceptedForm(JsonMapper mapper, String form) {
      Either<String, Unit> read =
          mapper.readValue("{\"isRight\":true,\"right\":" + form + "}", EITHER_UNIT);

      assertThat(read).isEqualTo(Either.right(Unit.INSTANCE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#mappers")
    @DisplayName("reads the null a Void success wrote into a record field's Either")
    void readsVoidPayload(JsonMapper mapper) {
      Outcome read =
          mapper.readValue("{\"id\":\"a1\",\"result\":" + RIGHT_NULL + "}", Outcome.class);

      assertThat(read).isEqualTo(new Outcome("a1", Either.right(Unit.INSTANCE)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#mappers")
    @DisplayName("reads a null Unit in every slot that holds one")
    void readsNullInEverySlot(JsonMapper mapper) {
      assertThat(
              mapper.readValue(
                  "[" + RIGHT_NULL + "]", new TypeReference<List<Either<String, Unit>>>() {}))
          .containsExactly(Either.right(Unit.INSTANCE));
      assertThat(
              mapper.readValue(
                  "{\"isRight\":false,\"left\":null}",
                  new TypeReference<Either<Unit, String>>() {}))
          .isEqualTo(Either.left(Unit.INSTANCE));
      assertThat(
              mapper.readValue(
                  "{\"valid\":true,\"value\":null}",
                  new TypeReference<Validated<String, Unit>>() {}))
          .isEqualTo(Validated.valid(Unit.INSTANCE));
      assertThat(
              mapper.readValue(
                  "{\"kind\":\"both\",\"left\":\"w\",\"right\":null}",
                  new TypeReference<EitherOrBoth<String, Unit>>() {}))
          .isEqualTo(EitherOrBoth.both("w", Unit.INSTANCE));
      assertThat(mapper.readValue("[null,\"()\"]", new TypeReference<NonEmptyList<Unit>>() {}))
          .isEqualTo(NonEmptyList.of(Unit.INSTANCE, Unit.INSTANCE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#mappers")
    @DisplayName("reads a Unit record component that is null or left out")
    void readsNullOrMissingRecordComponent(JsonMapper mapper) {
      Ack expected = new Ack("a1", Unit.INSTANCE);

      assertThat(mapper.readValue("{\"id\":\"a1\",\"ack\":null}", Ack.class)).isEqualTo(expected);
      assertThat(mapper.readValue("{\"id\":\"a1\"}", Ack.class)).isEqualTo(expected);
    }

    @Test
    @DisplayName("reads a Unit from a one-element array where the mapper unwraps them")
    void readsUnwrappedSingleElementArray() {
      JsonMapper unwrapping =
          jackson3Defaults()
              .rebuild()
              .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
              .build();

      assertThat(unwrapping.readValue("{\"isRight\":true,\"right\":[\"()\"]}", EITHER_UNIT))
          .isEqualTo(Either.right(Unit.INSTANCE));
      assertThat(unwrapping.readValue("{\"isRight\":true,\"right\":[null]}", EITHER_UNIT))
          .isEqualTo(Either.right(Unit.INSTANCE));
    }

    @Test
    @DisplayName("reads an empty array as Unit where the mapper reads one as null")
    void readsEmptyArrayWhereMapperReadsItAsNull() {
      JsonMapper emptyAsNull =
          jackson3Defaults()
              .rebuild()
              .enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
              .build();

      assertThat(emptyAsNull.readValue("{\"isRight\":true,\"right\":[]}", EITHER_UNIT))
          .isEqualTo(Either.right(Unit.INSTANCE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#refusedValues")
    @DisplayName("refuses any other value, naming the three it reads and what it found")
    void refusesOtherValues(String value, String found) {
      String json = "{\"isRight\":true,\"right\":" + value + "}";

      assertThatExceptionOfType(MismatchedInputException.class)
          .isThrownBy(() -> jackson3Defaults().readValue(json, EITHER_UNIT))
          .withMessageContaining(REFUSAL + found);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#refusedTypedObjects")
    @DisplayName("names an object inside a type id as an object")
    void namesObjectInsideTypeIdAsObject(String value) {
      JsonMapper mapper = typed(jackson3Defaults(), JsonTypeInfo.As.PROPERTY);

      assertThatExceptionOfType(MismatchedInputException.class)
          .isThrownBy(() -> mapper.readValue("{\"value\":" + value + "}", Holder.class))
          .withMessageContaining(REFUSAL + "an object");
    }

    @Test
    @DisplayName("names a value with no text by its token")
    void namesValueWithNoTextByToken() {
      assertThatThrownBy(() -> jackson3Defaults().convertValue(new byte[] {1, 2}, Unit.class))
          .hasMessageContaining(REFUSAL + "VALUE_EMBEDDED_OBJECT");
    }

    @Test
    @DisplayName("cuts a long value that is not a string, as it cuts a long string")
    void cutsLongNonStringValue() {
      JsonMapper mapper = jackson3Defaults();
      // 150 zero bytes give 200 characters of base64, all "A"
      JsonNode binary = mapper.getNodeFactory().binaryNode(new byte[150]);

      assertThatExceptionOfType(MismatchedInputException.class)
          .isThrownBy(() -> mapper.treeToValue(binary, Unit.class))
          .withMessageContaining(REFUSAL + "A".repeat(100) + "...");
    }

    @Test
    @DisplayName("refuses a null in a slot whose declared type is not Unit")
    void refusesNullInNonUnitSlot() {
      assertThatExceptionOfType(MismatchedInputException.class)
          .isThrownBy(
              () ->
                  jackson3Defaults()
                      .readValue(RIGHT_NULL, new TypeReference<Either<String, String>>() {}))
          .withMessageContaining("Either 'right' must not be null");
    }
  }

  @Nested
  @DisplayName("exchanging")
  class Exchanging {

    @ParameterizedTest(name = "written under {0}, read under {1}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#writerAndReader")
    @DisplayName("reads back what a mapper under either defaults wrote")
    void readsWhatEitherDefaultsWrote(JsonMapper writer, JsonMapper reader) {
      Outcome original = new Outcome("a1", Either.right(Unit.INSTANCE));

      Outcome read = reader.readValue(writer.writeValueAsString(original), Outcome.class);

      assertThat(read).isEqualTo(original);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.spring.json.UnitJsonTest#mappers")
    @DisplayName("round-trips a Unit under polymorphic typing, as the enum's own handling did")
    void roundTripsUnderDefaultTyping(JsonMapper mapper) {
      JsonMapper typed = typed(mapper, JsonTypeInfo.As.WRAPPER_ARRAY);
      Holder original = new Holder(Unit.INSTANCE);

      String json = typed.writeValueAsString(original);

      assertThat(json).isEqualTo("{\"value\":[\"org.higherkindedj.hkt.Unit\",null]}");
      assertThat(typed.readValue(json, Holder.class)).isEqualTo(original);
    }
  }
}
