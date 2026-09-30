// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.example.proto.NamesProto.Oddity;
import com.google.protobuf.Any;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueOptions;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Duration;
import com.google.protobuf.Field;
import com.google.protobuf.FieldMask;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Option;
import com.google.protobuf.SourceContext;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Syntax;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Type;
import com.google.protobuf.Value;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.higherkindedj.optics.laws.ValidatedPrismLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * protobuf-java messages as wires, pinned against protobuf-java's own well-known types and
 * descriptor messages: each is a message protoc generated, so its accessors are the ones a user's
 * messages have, companions included.
 */
@DisplayName("MappingProcessor - protobuf-java messages")
class MappingProcessorProtobufTest {

  private static Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new MappingProcessor()).compile(sources);
  }

  /**
   * Compiles under the lints a user's build may run with, so a generated warning fails it. A
   * fixture declares its types side by side in one file, which is all the auxiliary-class lint
   * would object to.
   */
  private static RuntimeCompilationHelper.CompiledResult compileClean(JavaFileObject... sources) {
    Compilation compilation =
        javac()
            .withProcessors(new MappingProcessor())
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(sources);
    assertThat(compilation).succeeded();
    return new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  /** One source in {@code com.example}, with the imports every fixture here needs. */
  private static JavaFileObject source(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;

        import com.google.protobuf.*;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import org.higherkindedj.optics.Getter;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.annotations.UpdateSpec;

        """
            + body);
  }

  @SuppressWarnings("unchecked")
  private static ValidatedPrism<Object, Object> prism(Object impl) {
    return (ValidatedPrism<Object, Object>) invoke(impl, "asValidatedPrism");
  }

  @SuppressWarnings("unchecked")
  private static Validated<NonEmptyList<FieldError>, Object> parse(Object impl, Object wire) {
    return (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", wire);
  }

  /** A {@code com.example} record, built through its canonical constructor. */
  private static Object record(
      RuntimeCompilationHelper.CompiledResult result, String name, Object... components)
      throws ReflectiveOperationException {
    Class<?> type = result.loadClass("com.example." + name);
    Constructor<?> canonical =
        type.getDeclaredConstructor(
            Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new));
    canonical.setAccessible(true);
    return canonical.newInstance(components);
  }

  @SuppressWarnings("unchecked")
  private static Validated<NonEmptyList<FieldError>, Object> validated(Object value) {
    return (Validated<NonEmptyList<FieldError>, Object>) value;
  }

  private static String generated(Compilation compilation, String impl) {
    try {
      return compilation
          .generatedFile(StandardLocation.SOURCE_OUTPUT, "com/example/" + impl + ".java")
          .orElseThrow()
          .getCharContent(false)
          .toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Nested
  @DisplayName("A well-known type maps both ways over its fields, its companions left out")
  class WellKnownTypes {

    @Test
    @DisplayName("Timestamp and Duration: scalar fields, beside the unknown fields")
    void scalars() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Times",
                  """
                  record Stamp(long seconds, int nanos) {}

                  record Span(long seconds, int nanos) {}

                  @GenerateMapping
                  interface StampMapping extends MappingSpec<Stamp, Timestamp> {}

                  @GenerateMapping
                  interface SpanMapping extends MappingSpec<Span, Duration> {}
                  """));
      Object stamps = result.instance("com.example.StampMappingImpl");
      Object stamp = record(result, "Stamp", 1_700_000_000L, 42);
      Timestamp timestamp = Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(42).build();
      Assertions.assertThat(invoke(stamps, "build", stamp)).isEqualTo(timestamp);
      assertThatValidated(parse(stamps, timestamp)).isValid().hasValue(stamp);
      ValidatedPrismLaws.assertParseBuild(prism(stamps), stamp);
      ValidatedPrismLaws.assertBuildParse(prism(stamps), timestamp);

      Object spans = result.instance("com.example.SpanMappingImpl");
      Object span = record(result, "Span", 90L, 5);
      Duration duration = Duration.newBuilder().setSeconds(90).setNanos(5).build();
      Assertions.assertThat(invoke(spans, "build", span)).isEqualTo(duration);
      ValidatedPrismLaws.assertParseBuild(prism(spans), span);
      ValidatedPrismLaws.assertBuildParse(prism(spans), duration);
    }

    @Test
    @DisplayName("StringValue: a string field, written once and never through its bytes")
    void stringField() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Names",
                  """
                  record Name(String value) {}

                  @GenerateMapping
                  interface NameMapping extends MappingSpec<Name, StringValue> {}
                  """));
      Assertions.assertThat(generated(result.compilation(), "NameMappingImpl"))
          .contains("b.setValue(domain.value());")
          .doesNotContain("Bytes")
          .doesNotContain("UnknownFields");
      Object impl = result.instance("com.example.NameMappingImpl");
      Object name = record(result, "Name", "Ada");
      Assertions.assertThat(invoke(impl, "build", name)).isEqualTo(StringValue.of("Ada"));
      ValidatedPrismLaws.assertParseBuild(prism(impl), name);
      ValidatedPrismLaws.assertBuildParse(prism(impl), StringValue.of("Lovelace"));
    }

    @Test
    @DisplayName("FieldMask: a repeated string field, read as its list and added whole")
    void repeatedField() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Masks",
                  """
                  record Mask(List<String> paths) {}

                  @GenerateMapping
                  interface MaskMapping extends MappingSpec<Mask, FieldMask> {}
                  """));
      Assertions.assertThat(generated(result.compilation(), "MaskMappingImpl"))
          // Both directions copy, so neither side holds the other's list.
          .contains("b.addAllPaths(hkj$copyOf(domain.paths()));")
          .contains("hkj$copyOf(wire.getPathsList())");
      Object impl = result.instance("com.example.MaskMappingImpl");
      Object mask = record(result, "Mask", List.of("user.name", "user.email"));
      FieldMask wire = FieldMask.newBuilder().addPaths("user.name").addPaths("user.email").build();
      Assertions.assertThat(invoke(impl, "build", mask)).isEqualTo(wire);
      assertThatValidated(parse(impl, wire)).isValid().hasValue(mask);
      ValidatedPrismLaws.assertParseBuild(prism(impl), mask);
      ValidatedPrismLaws.assertBuildParse(prism(impl), wire);
      ValidatedPrismLaws.assertBuildParse(prism(impl), FieldMask.getDefaultInstance());
    }

    @Test
    @DisplayName("Struct: a map field, read as its map and put whole, its deprecated getter unread")
    void mapField() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Docs",
                  """
                  record Doc(Map<String, Value> fields) {}

                  @GenerateMapping
                  interface DocMapping extends MappingSpec<Doc, Struct> {}
                  """));
      Assertions.assertThat(generated(result.compilation(), "DocMappingImpl"))
          .contains("b.putAllFields(hkj$copyOf(domain.fields()));")
          .contains("hkj$copyOf(wire.getFieldsMap())")
          .doesNotContain("wire.getFields()");
      Object impl = result.instance("com.example.DocMappingImpl");
      Map<String, Value> fields =
          Map.of(
              "name", Value.newBuilder().setStringValue("Ada").build(),
              "age", Value.newBuilder().setNumberValue(36).build());
      Object doc = record(result, "Doc", fields);
      Struct wire = Struct.newBuilder().putAllFields(fields).build();
      Assertions.assertThat(invoke(impl, "build", doc)).isEqualTo(wire);
      ValidatedPrismLaws.assertParseBuild(prism(impl), doc);
      ValidatedPrismLaws.assertBuildParse(prism(impl), wire);
    }

    @Test
    @DisplayName("Type: strings, an enum, repeated messages and a message field in one message")
    void mixedFields() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Types",
                  """
                  record TypeDef(
                      String name,
                      List<Field> fields,
                      List<String> oneofs,
                      List<Option> options,
                      Optional<SourceContext> sourceContext,
                      Syntax syntax,
                      String edition) {}

                  @GenerateMapping
                  interface TypeMapping extends MappingSpec<TypeDef, Type> {}
                  """));
      Assertions.assertThat(generated(result.compilation(), "TypeMappingImpl"))
          .doesNotContain("SyntaxValue")
          .doesNotContain("OrBuilder")
          .doesNotContain("Count");
      Object impl = result.instance("com.example.TypeMappingImpl");
      Type wire =
          Type.newBuilder()
              .setName("Person")
              .addFields(Field.newBuilder().setName("name").setNumber(1))
              .addOneofs("contact")
              .addOptions(Option.newBuilder().setName("deprecated"))
              .setSourceContext(SourceContext.newBuilder().setFileName("person.proto"))
              .setSyntax(Syntax.SYNTAX_PROTO3)
              .setEdition("2023")
              .build();
      Object parsed = parse(impl, wire).fold(errors -> errors, domain -> domain);
      Assertions.assertThat(invoke(parsed, "syntax")).isEqualTo(Syntax.SYNTAX_PROTO3);
      ValidatedPrismLaws.assertBuildParse(prism(impl), wire);
      ValidatedPrismLaws.assertBuildParse(
          prism(impl), wire.toBuilder().clearSourceContext().build());
      ValidatedPrismLaws.assertParseBuild(prism(impl), parsed);
    }

    @Test
    @DisplayName("Option: a message field, set through the setter taking the value, not a builder")
    void messageField() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Options",
                  """
                  record Opt(String name, Any value) {}

                  @GenerateMapping
                  interface OptMapping extends MappingSpec<Opt, Option> {}
                  """));
      Object impl = result.instance("com.example.OptMappingImpl");
      Any any = Any.pack(StringValue.of("on"));
      Object opt = record(result, "Opt", "flag", any);
      Option wire = Option.newBuilder().setName("flag").setValue(any).build();
      Assertions.assertThat(invoke(impl, "build", opt)).isEqualTo(wire);
      MappingLaws.assertMappingLaws(prism(impl), wire, Option.newBuilder().setName("x").build());
    }

    @Test
    @DisplayName("Empty: a message with no fields maps to a record with no components")
    void noFields() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Nothings",
                  """
                  record Nothing() {}

                  @GenerateMapping
                  interface NothingMapping extends MappingSpec<Nothing, com.google.protobuf.Empty> {}
                  """));
      Object impl = result.instance("com.example.NothingMappingImpl");
      Assertions.assertThat(invoke(impl, "build", record(result, "Nothing")))
          .isEqualTo(com.google.protobuf.Empty.getDefaultInstance());
    }

    @Test
    @DisplayName("nested messages map through their own specs, lifted and bridged alike")
    void nestedSpecs() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Schemas",
                  """
                  record Opt(String name, Optional<Any> value) {}

                  record Source(String fileName) {}

                  record Schema(
                      String name,
                      List<Field> fields,
                      List<String> oneofs,
                      List<Opt> options,
                      Optional<Source> sourceContext,
                      Syntax syntax,
                      String edition) {}

                  @GenerateMapping
                  interface OptMapping extends MappingSpec<Opt, Option> {}

                  @GenerateMapping
                  interface SourceMapping extends MappingSpec<Source, SourceContext> {}

                  @GenerateMapping
                  interface SchemaMapping extends MappingSpec<Schema, Type> {}
                  """));
      Object impl = result.instance("com.example.SchemaMappingImpl");
      Type wire =
          Type.newBuilder()
              .setName("Person")
              .addOptions(Option.newBuilder().setName("a"))
              .addOptions(Option.newBuilder().setName("b").setValue(Any.getDefaultInstance()))
              .setSourceContext(SourceContext.newBuilder().setFileName("person.proto"))
              .build();
      ValidatedPrismLaws.assertBuildParse(prism(impl), wire);
      ValidatedPrismLaws.assertBuildParse(
          prism(impl), wire.toBuilder().clearSourceContext().build());
      Object parsed = parse(impl, wire).fold(errors -> errors, domain -> domain);
      Assertions.assertThat(invoke(parsed, "sourceContext"))
          .isEqualTo(Optional.of(record(result, "Source", "person.proto")));
    }
  }

  @Nested
  @DisplayName("A field that tracks its presence reads null when unset")
  class Presence {

    @Test
    @DisplayName("an Optional over a field with hasX() is empty when unset, and leaves it unset")
    void optionalOverPresence() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Constants",
                  """
                  record Constant(
                      Optional<String> name,
                      Optional<Integer> number,
                      Optional<DescriptorProtos.EnumValueOptions> options) {}

                  @GenerateMapping
                  interface ConstantMapping
                      extends MappingSpec<Constant, DescriptorProtos.EnumValueDescriptorProto> {}
                  """));
      Assertions.assertThat(generated(result.compilation(), "ConstantMappingImpl"))
          .contains("if (domain.number().isPresent())")
          .contains("wire.hasNumber() ? wire.getNumber() : null");
      Object impl = result.instance("com.example.ConstantMappingImpl");
      Object empty =
          record(result, "Constant", Optional.empty(), Optional.empty(), Optional.empty());
      EnumValueDescriptorProto unset = (EnumValueDescriptorProto) invoke(impl, "build", empty);
      Assertions.assertThat(unset).isEqualTo(EnumValueDescriptorProto.getDefaultInstance());
      Assertions.assertThat(unset.hasNumber()).isFalse();
      Object zero =
          record(
              result,
              "Constant",
              Optional.of(""),
              Optional.of(0),
              Optional.of(EnumValueOptions.getDefaultInstance()));
      EnumValueDescriptorProto set = (EnumValueDescriptorProto) invoke(impl, "build", zero);
      Assertions.assertThat(set.hasName()).isTrue();
      Assertions.assertThat(set.hasNumber()).isTrue();
      Assertions.assertThat(set.hasOptions()).isTrue();
      ValidatedPrismLaws.assertParseBuild(prism(impl), empty);
      ValidatedPrismLaws.assertParseBuild(prism(impl), zero);
      ValidatedPrismLaws.assertBuildParse(prism(impl), unset);
      ValidatedPrismLaws.assertBuildParse(prism(impl), set);
    }

    @Test
    @DisplayName("a component that is no Optional needs its field set, primitive or not")
    void requiredPresence() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Numbered",
                  """
                  record Numbered(
                      String name, int number, DescriptorProtos.EnumValueOptions options) {}

                  @GenerateMapping
                  interface NumberedMapping
                      extends MappingSpec<Numbered, DescriptorProtos.EnumValueDescriptorProto> {}
                  """));
      Object impl = result.instance("com.example.NumberedMappingImpl");
      EnumValueDescriptorProto set =
          EnumValueDescriptorProto.newBuilder()
              .setName("RED")
              .setNumber(0)
              .setOptions(EnumValueOptions.getDefaultInstance())
              .build();
      MappingLaws.assertMappingLaws(prism(impl), set, set.toBuilder().clearNumber().build());
      assertThatValidated(parse(impl, EnumValueDescriptorProto.getDefaultInstance()))
          .isInvalid()
          .hasFieldErrors(
              "name: must not be null", "number: must not be null", "options: must not be null");
    }

    @Test
    @DisplayName("a oneof's members map as Optionals: build sets the one present, parse reads it")
    void oneof() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Json",
                  """
                  record Json(
                      Optional<NullValue> nullValue,
                      Optional<Double> numberValue,
                      Optional<String> stringValue,
                      Optional<Boolean> boolValue,
                      Optional<Struct> structValue,
                      Optional<ListValue> listValue) {}

                  @GenerateMapping
                  interface JsonMapping extends MappingSpec<Json, Value> {}
                  """));
      Object impl = result.instance("com.example.JsonMappingImpl");
      Object text =
          record(
              result,
              "Json",
              Optional.empty(),
              Optional.empty(),
              Optional.of("Ada"),
              Optional.empty(),
              Optional.empty(),
              Optional.empty());
      Value built = (Value) invoke(impl, "build", text);
      Assertions.assertThat(built.getKindCase()).isEqualTo(Value.KindCase.STRING_VALUE);
      ValidatedPrismLaws.assertParseBuild(prism(impl), text);
      for (Value wire :
          List.of(
              Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build(),
              Value.newBuilder().setNumberValue(2.5).build(),
              Value.newBuilder().setBoolValue(false).build(),
              Value.newBuilder().setStructValue(Struct.getDefaultInstance()).build(),
              Value.newBuilder().setListValue(ListValue.getDefaultInstance()).build(),
              Value.getDefaultInstance())) {
        ValidatedPrismLaws.assertBuildParse(prism(impl), wire);
      }
    }
  }

  @Nested
  @DisplayName("What a message cannot carry is refused")
  class Refusals {

    @Test
    @DisplayName("an Optional over a scalar with no hasX() is refused, pointed at optional")
    void presencelessScalar() {
      Compilation compilation =
          compile(
              source(
                  "Scalars",
                  """
                  record Stamp(Optional<Long> seconds, int nanos) {}

                  record Name(Optional<String> value) {}

                  record Moment(Optional<java.time.Instant> seconds, int nanos) {}

                  @GenerateMapping
                  interface StampMapping extends MappingSpec<Stamp, Timestamp> {}

                  @GenerateMapping
                  interface NameMapping extends MappingSpec<Name, StringValue> {}

                  @GenerateMapping
                  interface MomentMapping extends MappingSpec<Moment, Timestamp> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Stamp.seconds' is Optional<Long>, bridged to the field 'seconds' of"
                  + " the protobuf-java message 'Timestamp', which does not track whether it is"
                  + " set.");
      assertThat(compilation)
          .hadErrorContaining(
              "The bridge needs hasSeconds() to tell an unset field from a set one, and protobuf"
                  + " generates none for a proto3 scalar declared without optional, which reads"
                  + " its default when unset, so an empty Optional would read back as present.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'seconds' as long, dropping the Optional, so the field's default encodes"
                  + " nothing; declare the field optional in its .proto file, so protoc generates"
                  + " hasSeconds(); or give 'seconds' a leaf over the whole Optional, a"
                  + " ValidatedPrism<Long, Optional<Long>> that reads the default as empty.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'value' as String, dropping the Optional, so the field's default encodes"
                  + " nothing");
      assertThat(compilation).hadErrorContaining("Declare 'seconds' as Instant, dropping");
    }

    @Test
    @DisplayName("an Optional over a repeated or a map field is refused, its empty the absence")
    void presencelessCollections() {
      Compilation compilation =
          compile(
              source(
                  "Collections",
                  """
                  record Mask(Optional<List<String>> paths) {}

                  record Doc(Optional<Map<String, Value>> fields) {}

                  @GenerateMapping
                  interface MaskMapping extends MappingSpec<Mask, FieldMask> {}

                  @GenerateMapping
                  interface DocMapping extends MappingSpec<Doc, Struct> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "protobuf generates none for a repeated or map field, which reads as an empty"
                  + " collection when unset");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'paths' as List<String>, dropping the Optional, so the field's empty"
                  + " collection encodes nothing; or give 'paths' a leaf over the whole Optional, a"
                  + " ValidatedPrism<List<String>, Optional<List<String>>> that reads the empty"
                  + " collection as empty.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'fields' as Map<String, Value>, dropping the Optional, so the field's empty"
                  + " collection encodes nothing;");
    }

    @Test
    @DisplayName("a oneof member filled by anything but an Optional is refused")
    void oneofMembers() {
      Compilation compilation =
          compile(
              source(
                  "Json",
                  """
                  record Json(
                      Optional<NullValue> nullValue,
                      double numberValue,
                      String stringValue,
                      Optional<Boolean> boolValue,
                      Optional<Struct> structValue) {}

                  @GenerateMapping
                  interface JsonMapping extends MappingSpec<Json, Value> {
                    default Getter<Json, ListValue> listValue() {
                      return Getter.of(json -> ListValue.getDefaultInstance());
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Json.stringValue' is String, and fills 'stringValue', a member of the"
                  + " oneof 'kind' of the protobuf-java message 'Value'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Setting one member of a oneof clears the others, so build, which writes every field"
                  + " it fills, would keep only the last member of 'kind' it wrote.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'stringValue' as Optional<String>, as every domain component filling a"
                  + " member of 'kind' must be; or map the whole oneof to one component named"
                  + " 'kind', a sealed interface with a record named after each member.");
      assertThat(compilation).hadErrorContaining("Declare 'numberValue' as Optional<Double>");
      assertThat(compilation)
          .hadErrorContaining(
              "the derived field 'listValue' fills 'listValue', a member of the oneof 'kind'");
      assertThat(compilation)
          .hadErrorContaining(
              "Remove the derived field 'listValue', and fill the member from an Optional domain"
                  + " component instead.");
    }

    @Test
    @DisplayName("a projection over a oneof member is refused the same way")
    void oneofProjection() {
      Compilation compilation =
          compile(
              source(
                  "Texts",
                  """
                  record Text(
                      Optional<NullValue> nullValue,
                      Optional<Double> numberValue,
                      String stringValue,
                      Optional<Boolean> boolValue,
                      Optional<Struct> structValue,
                      Optional<ListValue> listValue,
                      String note) {}

                  @GenerateMapping
                  interface TextMapping extends MappingSpec<Text, Value> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Text.stringValue' is String, and fills 'stringValue', a member of the"
                  + " oneof 'kind'");
    }

    @Test
    @DisplayName("a companion is no field, so a component named after one has no counterpart")
    void companionIsNoField() {
      Compilation compilation =
          compile(
              source(
                  "Names",
                  """
                  record Name(ByteString valueBytes) {}

                  @GenerateMapping
                  interface NameMapping extends MappingSpec<Name, StringValue> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Name.valueBytes' has no wire counterpart named 'valueBytes'. Found on"
                  + " StringValue: [value].");
    }

    @Test
    @DisplayName("a domain narrower than the message is offered components, never removing fields")
    void narrowerDomain() {
      Compilation compilation =
          compile(
              source(
                  "Stamps",
                  """
                  record Stamp(long seconds) {}

                  @GenerateMapping
                  interface StampMapping extends MappingSpec<Stamp, Timestamp> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Add matching domain components (for a oneof, one named after it, a sealed interface"
                  + " with a record named after each member, or an Optional one for each member), a"
                  + " @MapField rename where a name differs, or derived fields ('default"
                  + " Getter<Stamp, ComponentType>' methods named after the extras that are members"
                  + " of no oneof).");
    }

    @Test
    @DisplayName("an @Unmapped or @ReadOnly marker on a message field is told every field maps")
    void markersOnFields() {
      Compilation compilation =
          compile(
              source(
                  "Names",
                  """
                  record Name(String value) {}

                  record Label(String value) {}

                  @GenerateMapping
                  interface NameMapping extends MappingSpec<Name, StringValue> {
                    @org.higherkindedj.optics.annotations.Unmapped
                    String value();
                  }

                  @GenerateMapping
                  interface LabelMapping extends MappingSpec<Label, StringValue> {
                    @org.higherkindedj.optics.annotations.ReadOnly
                    String value();
                  }

                  record Tag(String value) {}

                  @GenerateMapping
                  interface TagPatch extends UpdateSpec<Tag, StringValue> {
                    @org.higherkindedj.optics.annotations.ReadOnly
                    String value();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Remove the marker: every field of a protobuf-java message maps, so fill 'value' from"
                  + " a domain component or a derived field.");
      assertThat(compilation)
          .hadErrorContaining(
              "Remove the marker: every field of a protobuf-java message maps, and the update edits"
                  + " 'value' when its FieldMask names it.");
      assertThat(compilation)
          .hadErrorContaining(
              "Remove the marker: build writes every field of a protobuf-java message it maps.");
    }
  }

  @Nested
  @DisplayName("A primitive field converts through its wrapper")
  class PrimitiveFields {

    @Test
    @DisplayName("a leaf over the wrapper converts a primitive field, and a wrapper maps as it is")
    void leafOverWrapper() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Moments",
                  """
                  record Moment(java.time.Instant seconds, Integer nanos) {}

                  @GenerateMapping
                  interface MomentMapping extends MappingSpec<Moment, Timestamp> {
                    default org.higherkindedj.optics.validated.ValidatedPrism<
                            Long, java.time.Instant>
                        seconds() {
                      return org.higherkindedj.optics.validated.ValidatedPrism.of(
                          raw ->
                              org.higherkindedj.hkt.validated.Validated.validNel(
                                  java.time.Instant.ofEpochSecond(raw)),
                          java.time.Instant::getEpochSecond);
                    }
                  }
                  """));
      Object impl = result.instance("com.example.MomentMappingImpl");
      Timestamp wire = Timestamp.newBuilder().setSeconds(90).setNanos(5).build();
      Object moment = record(result, "Moment", java.time.Instant.ofEpochSecond(90), 5);
      assertThatValidated(parse(impl, wire)).isValid().hasValue(moment);
      Assertions.assertThat(invoke(impl, "build", moment)).isEqualTo(wire);
    }

    @Test
    @DisplayName("a derived field fills a primitive field through its wrapper")
    void derivedPrimitive() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Stamps",
                  """
                  record Stamp(long seconds) {}

                  @GenerateMapping
                  interface StampMapping extends MappingSpec<Stamp, Timestamp> {
                    default Getter<Stamp, Integer> nanos() {
                      return Getter.of(stamp -> 7);
                    }
                  }
                  """));
      Object built =
          invoke(
              result.instance("com.example.StampMappingImpl"),
              "build",
              record(result, "Stamp", 90L));
      Assertions.assertThat(built)
          .isEqualTo(Timestamp.newBuilder().setSeconds(90).setNanos(7).build());
    }

    @Test
    @DisplayName("a derived field over a primitive record component is declared over its wrapper")
    void derivedPrimitiveRecordComponent() {
      Compilation compilation =
          compile(
              source(
                  "Ranks",
                  """
                  record Person(String name) {}

                  record PersonDto(String name, int rank) {}

                  @GenerateMapping
                  interface PersonMapping extends MappingSpec<Person, PersonDto> {
                    default Getter<Person, Long> rank() {
                      return Getter.of(person -> 1L);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "derived field 'rank' must return Getter<Person, java.lang.Integer> but returns");
      assertThat(compilation)
          .hadErrorContaining("Declare 'default Getter<Person, java.lang.Integer> rank()'.");
    }

    @Test
    @DisplayName("a whole-Optional leaf reads a primitive field's default as empty")
    void wholeOptionalLeaf() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Stamps",
                  """
                  record Stamp(Optional<Long> seconds, int nanos) {}

                  @GenerateMapping
                  interface StampMapping extends MappingSpec<Stamp, Timestamp> {
                    default org.higherkindedj.optics.validated.ValidatedPrism<
                            Long, Optional<Long>>
                        seconds() {
                      return org.higherkindedj.optics.validated.ValidatedPrism.of(
                          raw ->
                              org.higherkindedj.hkt.validated.Validated.validNel(
                                  raw == 0 ? Optional.empty() : Optional.of(raw)),
                          seconds -> seconds.orElse(0L));
                    }
                  }
                  """));
      Object impl = result.instance("com.example.StampMappingImpl");
      Object unset = record(result, "Stamp", Optional.empty(), 0);
      Assertions.assertThat(invoke(impl, "build", unset)).isEqualTo(Timestamp.getDefaultInstance());
      assertThatValidated(parse(impl, Timestamp.getDefaultInstance())).isValid().hasValue(unset);
    }

    @Test
    @DisplayName(
        "an @OptionalBridge marker on a primitive message field is left to the bridge, which takes"
            + " one with hasX() and refuses one without")
    void bridgeMarkers() {
      Compilation taken =
          compile(
              source(
                  "Numbers",
                  """
                  record Reading(
                      Optional<NullValue> nullValue,
                      Optional<Double> numberValue,
                      Optional<String> stringValue,
                      Optional<Boolean> boolValue,
                      Optional<Struct> structValue,
                      Optional<ListValue> listValue) {}

                  @GenerateMapping
                  interface ReadingMapping extends MappingSpec<Reading, Value> {
                    @org.higherkindedj.optics.annotations.OptionalBridge
                    Optional<Double> numberValue();
                  }
                  """));
      assertThat(taken).succeeded();
      assertThat(taken).hadNoteContaining("@OptionalBridge on 'numberValue' is redundant");

      Compilation refused =
          compile(
              source(
                  "Stamps",
                  """
                  record Stamp(Optional<Long> seconds, int nanos) {}

                  @GenerateMapping
                  interface StampMapping extends MappingSpec<Stamp, Timestamp> {
                    @org.higherkindedj.optics.annotations.OptionalBridge
                    Optional<Long> seconds();
                  }
                  """));
      assertThat(refused).failed();
      assertThat(refused).hadErrorContaining("which does not track whether it is set.");
      Assertions.assertThat(refused.errors())
          .noneMatch(error -> error.getMessage(null).contains("can never be null"));
    }
  }

  // google.protobuf.Value's oneof 'kind' as a sealed type: a record named after each member, the
  // two message members through specs of their own. The records shadow protobuf's own names in
  // the fixture's package, so protobuf's are qualified.
  private static final String JSON =
      """
      sealed interface Json
          permits NullValue, NumberValue, StringValue, BoolValue, StructValue, ListValue {}

      record NullValue(com.google.protobuf.NullValue value) implements Json {}

      record NumberValue(double number) implements Json {}

      record StringValue(String text) implements Json {
        StringValue {
          if (text.isBlank()) {
            throw new IllegalArgumentException("must not be blank");
          }
        }
      }

      record BoolValue(boolean flag) implements Json {}

      record StructValue(Map<String, Value> fields) implements Json {}

      record ListValue(List<Value> values) implements Json {}

      @GenerateMapping
      interface StructValueMapping extends MappingSpec<StructValue, Struct> {}

      @GenerateMapping
      interface ListValueMapping extends MappingSpec<ListValue, com.google.protobuf.ListValue> {}
      """;

  @Nested
  @DisplayName("A oneof maps to a sealed component, a record for each member")
  class SealedOneofs {

    @Test
    @DisplayName("each member builds and parses as its variant, and none set is a missing value")
    void required() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Jsons",
                  JSON
                      + """

                      record JsonValue(Json kind) {}

                      @GenerateMapping
                      interface JsonValueMapping extends MappingSpec<JsonValue, Value> {}
                      """));
      Assertions.assertThat(generated(result.compilation(), "JsonValueMappingImpl"))
          .contains("switch (wire.getKindCase())")
          .contains(
              "if (Objects.requireNonNull(domain.kind(), \"kind\") instanceof NullValue hkj$v)")
          .contains("if (domain.kind() instanceof NumberValue hkj$v)")
          .contains("b.setNumberValue(hkj$v.number());");
      Object impl = result.instance("com.example.JsonValueMappingImpl");
      // A null the component holds fails build, as a protobuf setter fails on any other.
      Object missing = record(result, "JsonValue", (Object) null);
      Assertions.assertThatThrownBy(() -> invoke(impl, "build", missing))
          .hasRootCauseInstanceOf(NullPointerException.class)
          .hasRootCauseMessage("kind");
      Object number = record(result, "JsonValue", record(result, "NumberValue", 2.5));
      Assertions.assertThat(invoke(impl, "build", number))
          .isEqualTo(Value.newBuilder().setNumberValue(2.5).build());
      Value text = Value.newBuilder().setStringValue("Ada").build();
      MappingLaws.assertMappingLaws(prism(impl), text, Value.getDefaultInstance());
      for (Value wire :
          List.of(
              Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build(),
              Value.newBuilder().setNumberValue(-1).build(),
              Value.newBuilder().setBoolValue(true).build(),
              Value.newBuilder()
                  .setStructValue(Struct.newBuilder().putFields("k", text).build())
                  .build(),
              Value.newBuilder()
                  .setListValue(com.google.protobuf.ListValue.newBuilder().addValues(text).build())
                  .build())) {
        ValidatedPrismLaws.assertBuildParse(prism(impl), wire);
      }
      assertThatValidated(parse(impl, Value.getDefaultInstance()))
          .isInvalid()
          .hasFieldErrors("kind: must not be null");
      // A variant's own invariant refuses the member's value, located under the component.
      assertThatValidated(parse(impl, Value.newBuilder().setStringValue(" ").build()))
          .isInvalid()
          .hasFieldErrors("kind: must not be blank");
    }

    @Test
    @DisplayName(
        "an Optional, of any subtype too, reads a oneof with no member set as empty, and leaves it"
            + " unset")
    void optional() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Jsons",
                  JSON
                      + """

                      record MaybeJson(Optional<? extends Json> kind) {}

                      @GenerateMapping
                      interface MaybeJsonMapping extends MappingSpec<MaybeJson, Value> {}
                      """));
      Object impl = result.instance("com.example.MaybeJsonMappingImpl");
      Object empty = record(result, "MaybeJson", Optional.empty());
      Assertions.assertThat(invoke(impl, "build", empty)).isEqualTo(Value.getDefaultInstance());
      ValidatedPrismLaws.assertParseBuild(prism(impl), empty);
      ValidatedPrismLaws.assertBuildParse(
          prism(impl), Value.newBuilder().setBoolValue(false).build());
    }

    @Test
    @DisplayName(
        "a mapping with a sealed oneof nests as a full mapping, and a projection patches it")
    void nestsAndProjects() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Jsons",
                  JSON
                      + """

                      record JsonValue(Json kind) {}

                      record Doc(Map<String, JsonValue> fields) {}

                      record WideJson(Json kind, String note) {}

                      @GenerateMapping
                      interface JsonValueMapping extends MappingSpec<JsonValue, Value> {}

                      @GenerateMapping
                      interface DocMapping extends MappingSpec<Doc, Struct> {}

                      @GenerateMapping
                      interface WideJsonMapping extends MappingSpec<WideJson, Value> {}
                      """));
      Struct struct =
          Struct.newBuilder().putFields("n", Value.newBuilder().setNumberValue(3).build()).build();
      ValidatedPrismLaws.assertBuildParse(
          prism(result.instance("com.example.DocMappingImpl")), struct);
      Object wide = record(result, "WideJson", record(result, "BoolValue", true), "kept");
      Object patched =
          invoke(
              result.instance("com.example.WideJsonMappingImpl"),
              "patch",
              wide,
              Value.newBuilder().setNumberValue(3).build());
      assertThatValidated(validated(patched))
          .isValid()
          .hasValue(record(result, "WideJson", record(result, "NumberValue", 3.0), "kept"));
    }

    @Test
    @DisplayName("a component named after a oneof that is no sealed type is refused")
    void unsealed() {
      Compilation compilation =
          compile(
              source(
                  "Plains",
                  """
                  record Plain(String kind) {}

                  @GenerateMapping
                  interface PlainMapping extends MappingSpec<Plain, Value> {}

                  record Wild(Optional<?> kind) {}

                  @GenerateMapping
                  interface WildMapping extends MappingSpec<Wild, Value> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("domain field 'Wild.kind' is Optional<?>");
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Plain.kind' is String, and names the oneof 'kind' of the"
                  + " protobuf-java message 'Value', which maps to a sealed interface or an"
                  + " Optional of one.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'kind' as a sealed interface permitting records named after the members,"
                  + " [NullValue, NumberValue, StringValue, BoolValue, StructValue, ListValue]");
    }

    @Test
    @DisplayName("a variant that is no record is refused")
    void variantNotRecord() {
      Compilation compilation =
          compile(
              source(
                  "Loose",
                  JSON.replace(
                          "record BoolValue(boolean flag) implements Json {}",
                          "final class BoolValue implements Json {}")
                      + """

                      record JsonValue(Json kind) {}

                      @GenerateMapping
                      interface JsonValueMapping extends MappingSpec<JsonValue, Value> {}
                      """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the variant 'BoolValue' of 'Json', which domain field 'JsonValue.kind' maps a oneof"
                  + " to, is not a record.");
    }

    private Compilation pairing(String permits, String extra) {
      return compile(
          source(
              "Pairs",
              JSON.replace(
                      "permits NullValue, NumberValue, StringValue, BoolValue, StructValue,"
                          + " ListValue",
                      "permits " + permits)
                  + extra
                  + """

                  record JsonValue(Json kind) {}

                  @GenerateMapping
                  interface JsonValueMapping extends MappingSpec<JsonValue, Value> {}
                  """));
    }

    @Test
    @DisplayName("members and variants that do not pair by name are refused, both ways")
    void unpaired() {
      Compilation both =
          pairing(
              "NullValue, NumberValue, StringValue, BoolValue, StructValue, Other",
              "\nrecord Other(String text) implements Json {}\n");
      assertThat(both).failed();
      assertThat(both)
          .hadErrorContaining(
              "the variants of 'Json' do not pair with the members of the oneof 'kind' of 'Value',"
                  + " which domain field 'JsonValue.kind' maps: members [listValue] have no"
                  + " variant, and variants [Other] name no member.");
      assertThat(both)
          .hadErrorContaining(
              "Name a record of 'Json' after each member, adding [ListValue], and rename or"
                  + " remove [Other].");

      Compilation membersOnly =
          pairing("NullValue, NumberValue, StringValue, BoolValue, StructValue", "");
      assertThat(membersOnly).hadErrorContaining("members [listValue] have no variant.");

      Compilation variantsOnly =
          pairing(
              "NullValue, NumberValue, StringValue, BoolValue, StructValue, ListValue, Other",
              "\nrecord Other(String text) implements Json {}\n");
      assertThat(variantsOnly).hadErrorContaining(": variants [Other] name no member.");
      assertThat(variantsOnly).hadErrorContaining("after each member, and rename or remove");
    }

    private Compilation unfilled(String from, String to, String wire) {
      return compile(
          source(
              "Unfilled",
              JSON.replace(from, to)
                  + """

                  record JsonValue(Json kind) {}

                  record WideJson(Json kind, String note) {}

                  @GenerateMapping
                  interface JsonValueMapping extends MappingSpec<%s, Value> {}
                  """
                      .formatted(wire)));
    }

    @Test
    @DisplayName("a variant nothing fills from its member is refused, offered a spec for a message")
    void variantUnfilled() {
      Compilation primitive =
          unfilled(
              "record NumberValue(double number)",
              "record NumberValue(double number, String unit)",
              "JsonValue");
      assertThat(primitive).failed();
      assertThat(primitive)
          .hadErrorContaining(
              "the variant 'NumberValue' of 'Json' pairs with the oneof member 'numberValue', of"
                  + " type double, and nothing fills it from one.");
      assertThat(primitive).hadErrorContaining("Give 'NumberValue' one component of type double.");

      Compilation text =
          unfilled("record StringValue(String text)", "record StringValue(int text)", "JsonValue");
      assertThat(text)
          .hadErrorContaining("Give 'StringValue' one component of type java.lang.String.");

      Compilation message =
          unfilled(
              "@GenerateMapping\ninterface StructValueMapping extends MappingSpec<StructValue,"
                  + " Struct> {}",
              "",
              "JsonValue");
      assertThat(message)
          .hadErrorContaining(
              "Give 'StructValue' one component of type com.google.protobuf.Struct, or declare"
                  + " '@GenerateMapping interface StructValueMapping extends"
                  + " MappingSpec<com.example.StructValue, com.google.protobuf.Struct> {}'.");

      // A projection resolves the same arms.
      Compilation projected =
          unfilled(
              "record NumberValue(double number)", "record NumberValue(String number)", "WideJson");
      assertThat(projected).hadErrorContaining("pairs with the oneof member 'numberValue'");
    }

    @Test
    @DisplayName(
        "a generic sealed type or variant, two variants of one name, a method named after the"
            + " component and a member held twice are refused")
    void shapes() {
      String variants =
          """
            record NullValue(com.google.protobuf.NullValue value) implements %1$s {}

            record NumberValue(double value) implements %1$s {}

            record StringValue(String value) implements %1$s {}

            record StructValue(com.google.protobuf.Struct value) implements %1$s {}

            record ListValue(com.google.protobuf.ListValue value) implements %1$s {}
          """;
      Compilation compilation =
          compile(
              source(
                  "Shapes",
                  """
                  sealed interface Kind {
                  %1$s
                    record BoolValue(boolean value) implements Kind {}
                  }

                  sealed interface Twin {
                  %2$s
                    record BoolValue(boolean value) implements Twin {}

                    interface Legacy {
                      record StringValue(String value) implements Twin {}
                    }
                  }

                  sealed interface Boxed<T> {
                  %3$s
                    record BoolValue(boolean value) implements Boxed<Object> {}
                  }

                  sealed interface Loose {
                  %4$s
                    record BoolValue<T>(boolean value) implements Loose {}
                  }

                  record KindValue(Kind kind) {}

                  record Both(Kind kind, Optional<String> stringValue) {}

                  record TwinValue(Twin kind) {}

                  record BoxedValue(Boxed<?> kind) {}

                  record LooseValue(Loose kind) {}

                  @GenerateMapping
                  interface KindValueMapping extends MappingSpec<KindValue, Value> {
                    default org.higherkindedj.optics.validated.ValidatedPrism<String, Kind> kind() {
                      throw new UnsupportedOperationException();
                    }
                  }

                  @GenerateMapping
                  interface BothMapping extends MappingSpec<Both, Value> {}

                  @GenerateMapping
                  interface TwinValueMapping extends MappingSpec<TwinValue, Value> {}

                  @GenerateMapping
                  interface BoxedValueMapping extends MappingSpec<BoxedValue, Value> {}

                  @GenerateMapping
                  interface LooseValueMapping extends MappingSpec<LooseValue, Value> {}
                  """
                      .formatted(
                          variants.formatted("Kind"),
                          variants.formatted("Twin"),
                          variants.formatted("Boxed<Object>"),
                          variants.formatted("Loose"))));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'kind()' is named after domain field 'KindValue.kind', which maps the oneof 'kind'"
                  + " through its variants, so nothing calls it.");
      assertThat(compilation).hadErrorContaining("Remove 'kind()'.");
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Both.stringValue' fills 'stringValue', a member of the oneof that"
                  + " domain field 'Both.kind' maps whole.");
      assertThat(compilation)
          .hadErrorContaining(
              "of 'Twin', which domain field 'TwinValue.kind' maps a oneof to, share the name"
                  + " 'StringValue'.");
      assertThat(compilation).hadErrorContaining("Keep one record of 'Twin' named 'StringValue'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'Boxed', the sealed type which domain field 'BoxedValue.kind' maps a oneof to,"
                  + " declares type parameters (not supported yet).");
      assertThat(compilation)
          .hadErrorContaining(
              "'BoolValue', a variant of 'Loose', which domain field 'LooseValue.kind' maps a oneof"
                  + " to, declares type parameters (not supported yet).");
    }

    @Test
    @DisplayName("a component renamed away from a oneof's name maps no oneof, so its mapping nests")
    void renamedAway() {
      Compilation compilation =
          compile(
              source(
                  "Members",
                  """
                  record Members(
                      Optional<NullValue> nullValue,
                      Optional<Double> numberValue,
                      Optional<String> kind,
                      Optional<Boolean> boolValue,
                      Optional<Struct> structValue,
                      Optional<ListValue> listValue) {}

                  @GenerateMapping
                  interface MembersMapping extends MappingSpec<Members, Value> {
                    @org.higherkindedj.optics.annotations.MapField(to = "stringValue")
                    Optional<String> kind();
                  }

                  record Doc(Map<String, Members> fields) {}

                  @GenerateMapping
                  interface DocMapping extends MappingSpec<Doc, Struct> {}
                  """));
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("two specs for a variant's pair are refused, offering no leaf in their place")
    void ambiguousVariantSpec() {
      Compilation compilation =
          unfilled(
              "@GenerateMapping\ninterface StructValueMapping",
              "@GenerateMapping\ninterface OtherStructMapping extends MappingSpec<StructValue,"
                  + " Struct> {}\n\n@GenerateMapping\ninterface StructValueMapping",
              "JsonValue");
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "field 'kind' matches more than one mapping spec: [OtherStructMapping,"
                  + " StructValueMapping].");
      assertThat(compilation).hadErrorContaining("arbitrary. Remove the duplicate spec.");
    }
  }

  @Nested
  @DisplayName("An UpdateSpec over a message applies the fields a FieldMask names")
  class FieldMaskUpdates {

    private static final String TYPES =
        """
        record TypeDef(
            String name,
            List<Field> fields,
            List<String> oneofs,
            List<Option> options,
            Optional<SourceContext> sourceContext,
            Syntax syntax,
            String edition) {}

        @GenerateMapping
        interface TypePatch extends UpdateSpec<TypeDef, Type> {}

        // The edition is derived, through a vocabulary both tiers share.
        record TypeName(
            String name,
            List<Field> fields,
            List<String> oneofs,
            List<Option> options,
            Optional<SourceContext> sourceContext,
            Syntax syntax) {}

        interface TypeVocabulary {
          default Getter<TypeName, String> edition() {
            return Getter.of(type -> "2023");
          }

          // Named after a component, a Getter derives nothing: the component maps its field.
          default Getter<TypeName, String> name() {
            return Getter.of(TypeName::name);
          }
        }

        @GenerateMapping
        interface TypeNameMapping extends MappingSpec<TypeName, Type>, TypeVocabulary {}

        @GenerateMapping
        interface TypeNamePatch extends UpdateSpec<TypeName, Type>, TypeVocabulary {}
        """;

    @SuppressWarnings("unchecked")
    private static Validated<NonEmptyList<FieldError>, Object> update(
        Object impl, Object wire, FieldMask mask, Object current) {
      Object accumulated = invoke(impl, "updateFrom", wire, mask);
      return (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
    }

    private static FieldMask mask(String... paths) {
      return FieldMask.newBuilder().addAllPaths(List.of(paths)).build();
    }

    @Test
    @DisplayName(
        "a named field is set as parse reads it, cleared when unset, and the rest left as they are,"
            + " a derived field's included")
    void namedFields() throws ReflectiveOperationException {
      var result = compileClean(source("Types", TYPES));
      Assertions.assertThat(generated(result.compilation(), "TypePatchImpl"))
          .contains("public Edits.Accumulated<TypeDef> updateFrom(Type wire, FieldMask mask)")
          // A message that carries its descriptor names each path exactly as its .proto file does.
          .contains(
              "hkj$path$sourceContext ="
                  + " Type.getDescriptor().findFieldByNumber(Type.SOURCE_CONTEXT_FIELD_NUMBER).getName();")
          .contains("all || paths.contains(hkj$path$sourceContext) ? wire : null")
          // The domain takes a copy of what the message holds, as parse does.
          .contains("hkj$copyOf(wire.getOneofsList())")
          .contains("Map.ofEntries()");
      Object impl = result.instance("com.example.TypePatchImpl");
      Object current =
          record(
              result,
              "TypeDef",
              "Person",
              List.of(),
              List.of("contact"),
              List.of(),
              Optional.of(SourceContext.newBuilder().setFileName("person.proto").build()),
              Syntax.SYNTAX_PROTO3,
              "2023");
      Type request = Type.newBuilder().setName("People").addOneofs("reach").build();
      assertThatValidated(update(impl, request, mask("name", "source_context"), current))
          .isValid()
          .hasValue(
              record(
                  result,
                  "TypeDef",
                  "People",
                  List.of(),
                  List.of("contact"),
                  List.of(),
                  Optional.empty(),
                  Syntax.SYNTAX_PROTO3,
                  "2023"));
      assertThatValidated(update(impl, request, mask("*"), current))
          .isValid()
          .hasValue(
              record(
                  result,
                  "TypeDef",
                  "People",
                  List.of(),
                  List.of("reach"),
                  List.of(),
                  Optional.empty(),
                  Syntax.SYNTAX_PROTO2,
                  ""));
      assertThatValidated(update(impl, request, FieldMask.getDefaultInstance(), current))
          .isValid()
          .hasValue(current);
      assertThatValidated(
              update(
                  impl, request, mask("source_context.file_name", "nope", "nope", "name"), current))
          .isInvalid()
          .hasFieldErrors(
              "source_context.file_name: nested paths are not supported yet",
              "nope: names no field of Type");

      // A field a derived field fills is the domain's to compute: named, it edits nothing.
      Object named = result.instance("com.example.TypeNamePatchImpl");
      Object currentName =
          record(
              result,
              "TypeName",
              "Person",
              List.of(),
              List.of(),
              List.of(),
              Optional.empty(),
              Syntax.SYNTAX_PROTO3);
      assertThatValidated(update(named, request, mask("edition", "name"), currentName))
          .isValid()
          .hasValue(
              record(
                  result,
                  "TypeName",
                  "People",
                  List.of(),
                  List.of(),
                  List.of(),
                  Optional.empty(),
                  Syntax.SYNTAX_PROTO3));
    }

    @Test
    @DisplayName("a primitive field updates too, reading its default when named and unset")
    void primitiveFields() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Stamps",
                  """
                  record Stamp(long seconds, int nanos) {}

                  @GenerateMapping
                  interface StampPatch extends UpdateSpec<Stamp, Timestamp> {}
                  """));
      Object impl = result.instance("com.example.StampPatchImpl");
      Object current = record(result, "Stamp", 10L, 5);
      Timestamp request = Timestamp.newBuilder().setSeconds(20).build();
      assertThatValidated(update(impl, request, mask("seconds"), current))
          .isValid()
          .hasValue(record(result, "Stamp", 20L, 5));
      assertThatValidated(update(impl, request, mask("nanos"), current))
          .isValid()
          .hasValue(record(result, "Stamp", 10L, 0));
    }

    @Test
    @DisplayName("a path is the name the message's descriptor gives, however its .proto cases it")
    void descriptorNames() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Odds",
                  """
                  sealed interface Mode {
                    record KInt(String value) implements Mode {}

                    record Plain(String value) implements Mode {}
                  }

                  record Odd(String foo2Bar, String xRay, String uRL, Optional<Mode> mode) {}

                  @GenerateMapping
                  interface OddPatch
                      extends UpdateSpec<Odd, com.example.proto.NamesProto.Oddity> {}
                  """));
      Object impl = result.instance("com.example.OddPatchImpl");
      Oddity wire =
          Oddity.newBuilder().setFoo2Bar("f").setXRay("x").setURL("u").setKInt("k").build();
      Object current = record(result, "Odd", "", "", "", Optional.empty());
      for (FieldDescriptor field : Oddity.getDescriptor().getFields()) {
        assertThatValidated(update(impl, wire, mask(field.getName()), current)).isValid();
      }
      assertThatValidated(update(impl, wire, mask("foo2Bar", "x_Ray", "URL", "kInt"), current))
          .isValid()
          .hasValue(
              record(result, "Odd", "f", "x", "u", Optional.of(record(result, "Mode$KInt", "k"))));
      assertThatValidated(update(impl, wire, mask("Mode"), current))
          .isInvalid()
          .hasFieldErrors(
              "Mode: names a oneof, not a field: name one of its members [kInt, plain]");
    }

    @Test
    @DisplayName("a named member updates the sealed component its oneof maps, and a rename binds")
    void oneofAndRename() throws ReflectiveOperationException {
      var result =
          compileClean(
              source(
                  "Jsons",
                  JSON
                      + """

                      record MaybeJson(Optional<Json> kind) {}

                      record Titled(String title, Optional<Any> value) {}

                      @GenerateMapping
                      interface MaybeJsonPatch extends UpdateSpec<MaybeJson, Value> {}

                      @GenerateMapping
                      interface TitledPatch extends UpdateSpec<Titled, Option> {
                        @org.higherkindedj.optics.annotations.MapField(to = "name")
                        String title();
                      }
                      """));
      Object json = result.instance("com.example.MaybeJsonPatchImpl");
      assertThatValidated(
              update(
                  json,
                  Value.newBuilder().setStringValue("Ada").build(),
                  mask("string_value"),
                  record(result, "MaybeJson", Optional.empty())))
          .isValid()
          .hasValue(record(result, "MaybeJson", Optional.of(record(result, "StringValue", "Ada"))));
      // The mask names the member the message holds, or the component keeps what it holds.
      Object old = record(result, "MaybeJson", Optional.of(record(result, "StringValue", "Old")));
      Value number = Value.newBuilder().setNumberValue(2).build();
      assertThatValidated(update(json, number, mask("string_value"), old)).isValid().hasValue(old);
      assertThatValidated(update(json, number, mask("number_value"), old))
          .isValid()
          .hasValue(record(result, "MaybeJson", Optional.of(record(result, "NumberValue", 2.0))));
      // A message holding no member clears the component when the mask names any member.
      assertThatValidated(update(json, Value.getDefaultInstance(), mask("string_value"), old))
          .isValid()
          .hasValue(record(result, "MaybeJson", Optional.empty()));
      assertThatValidated(update(json, number, mask("kind"), old))
          .isInvalid()
          .hasFieldErrors(
              "kind: names a oneof, not a field: name one of its members [null_value,"
                  + " number_value, string_value, bool_value, struct_value, list_value]");
      Object titled = result.instance("com.example.TitledPatchImpl");
      assertThatValidated(
              update(
                  titled,
                  Option.newBuilder().setName("deprecated").build(),
                  mask("name"),
                  record(result, "Titled", "old", Optional.empty())))
          .isValid()
          .hasValue(record(result, "Titled", "deprecated", Optional.empty()));
    }

    @Test
    @DisplayName("a field no domain component takes is refused, since a mask may name it")
    void unnamedField() {
      Compilation compilation =
          compile(
              source(
                  "Named",
                  """
                  record Named(String name) {}

                  @GenerateMapping
                  interface NamedPatch extends UpdateSpec<Named, Option> {}

                  record Plain(String name) {}

                  @GenerateMapping
                  interface PlainPatch extends UpdateSpec<Plain, Value> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the fields [value] of the protobuf-java message 'Option' name no component of"
                  + " Named.");
      assertThat(compilation)
          .hadErrorContaining(
              "Add to Named a component named after each, or a @MapField rename to it from one, on"
                  + " its leaf where it has one.");
      // Every field at once, and a oneof's members offered the component named after it.
      assertThat(compilation)
          .hadErrorContaining(
              "the fields [nullValue, numberValue, stringValue, boolValue, structValue, listValue]"
                  + " of the protobuf-java message 'Value' name no component of Plain.");
      assertThat(compilation)
          .hadErrorContaining(
              "; the members of the oneof 'kind' take one component named 'kind', a sealed"
                  + " interface with a record named after each member.");
    }

    @Test
    @DisplayName("a masked update refuses what the dense tiers refuse")
    void refusals() {
      Compilation unsealed =
          compile(
              source(
                  "Plains",
                  """
                  record Plain(String kind) {}

                  @GenerateMapping
                  interface PlainPatch extends UpdateSpec<Plain, Value> {}
                  """));
      assertThat(unsealed).hadErrorContaining("which maps to a sealed interface");

      Compilation unconverted =
          compile(
              source(
                  "Opts",
                  """
                  record Opt(Integer name, Optional<Any> value) {}

                  @GenerateMapping
                  interface OptPatch extends UpdateSpec<Opt, Option> {}
                  """));
      assertThat(unconverted).hadErrorContaining("has no usable source");

      Compilation plainMember =
          compile(
              source(
                  "Members",
                  """
                  record Members(
                      Optional<NullValue> nullValue,
                      Optional<Double> numberValue,
                      String stringValue,
                      Optional<Boolean> boolValue,
                      Optional<Struct> structValue,
                      Optional<ListValue> listValue) {}

                  @GenerateMapping
                  interface MembersPatch extends UpdateSpec<Members, Value> {}
                  """));
      assertThat(plainMember).hadErrorContaining("a member of the oneof 'kind'");

      Compilation colliding =
          compile(
              source(
                  "Colliding",
                  """
                  record Opt(String name, Optional<Any> value) {}

                  @GenerateMapping
                  interface OptPatch extends UpdateSpec<Opt, Option> {
                    default org.higherkindedj.optics.edit.Edits.Accumulated<Opt> updateFrom(
                        Option wire, FieldMask mask) {
                      return null;
                    }
                  }
                  """));
      assertThat(colliding)
          .hadErrorContaining(
              "collides with the 'updateFrom' member the generated OptPatchImpl emits");

      Compilation twice =
          compile(
              source(
                  "Twice",
                  """
                  record Renamed(String name, Optional<Any> value) {}

                  @GenerateMapping
                  interface RenamedPatch extends UpdateSpec<Renamed, Option> {
                    @org.higherkindedj.optics.annotations.MapField(to = "value")
                    String name();
                  }
                  """));
      // A rename onto another field leaves the field it moved from with no component.
      assertThat(twice)
          .hadErrorContaining(
              "the fields [name] of the protobuf-java message 'Option' name no component of"
                  + " Renamed.");
    }

    @Test
    @DisplayName("a masked update names the types it writes only where its package can see them")
    void unreachable() {
      JavaFileObject other =
          JavaFileObjects.forSourceString(
              "com.other.Doc",
              """
              package com.other;

              import com.google.protobuf.Any;
              import com.google.protobuf.ByteString;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public record Doc(String name, Optional<Tag> value) {}

              record Tag(String typeUrl, ByteString value) {}

              @GenerateMapping
              interface TagMapping extends MappingSpec<Tag, Any> {}
              """);
      Compilation compilation =
          compile(
              other,
              source(
                  "DocPatches",
                  """
                  @GenerateMapping
                  interface DocPatch extends UpdateSpec<com.other.Doc, Option> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot be reached from");
    }
  }

  @Nested
  @DisplayName("A class shaped like a message is read as protobuf generates one")
  class MessageShapes {

    // A lite message by hand, compiled and never run. Its builder inherits build() from the
    // runtime's generic base. A field named brief_case gives it a getBriefCase() that answers no
    // oneof's case, and so does topic_case. The oneof 'choice' has a member declared plain_text,
    // one declared richText in camel case, one declared x_ray, and a constant naming no field,
    // beside a field plaintext that is none of its members. tags is a repeated field beside
    // all_tags, whose single adder shares addAllTags. k_int is named kInt, as protobuf names it,
    // and marker holds an Empty. Each field has the number constant protoc gives it.
    private static final JavaFileObject BRIEFING =
        JavaFileObjects.forSourceString(
            "com.example.Briefing",
            """
            package com.example;

            import com.google.protobuf.Empty;
            import com.google.protobuf.GeneratedMessageLite;
            import java.util.List;

            public final class Briefing extends GeneratedMessageLite<Briefing, Briefing.Builder> {
              public enum Brief { SHORT, LONG }
              public enum TopicCase { ALPHA, BETA }
              public enum ChoiceCase { PLAIN_TEXT, RICHTEXT, X_RAY, LOST, CHOICE_NOT_SET }

              public static final int BRIEF_CASE_FIELD_NUMBER = 1;
              public static final int TOPIC_CASE_FIELD_NUMBER = 2;
              public static final int PLAIN_TEXT_FIELD_NUMBER = 3;
              public static final int RICHTEXT_FIELD_NUMBER = 4;
              public static final int X_RAY_FIELD_NUMBER = 5;
              public static final int PLAINTEXT_FIELD_NUMBER = 6;
              public static final int TAGS_FIELD_NUMBER = 7;
              public static final int K_INT_FIELD_NUMBER = 8;
              public static final int MARKER_FIELD_NUMBER = 9;

              public Brief getBriefCase() { return Brief.SHORT; }
              public boolean hasBriefCase() { return true; }
              public TopicCase getTopicCase() { return TopicCase.ALPHA; }
              public ChoiceCase getChoiceCase() { return ChoiceCase.CHOICE_NOT_SET; }
              public String getPlainText() { return ""; }
              public boolean hasPlainText() { return false; }
              public String getRichText() { return ""; }
              public boolean hasRichText() { return false; }
              public String getXRay() { return ""; }
              public boolean hasXRay() { return false; }
              public String getPlaintext() { return ""; }
              public List<String> getTagsList() { return List.of(); }
              public int getKInt() { return 0; }
              public Empty getMarker() { return Empty.getDefaultInstance(); }
              public boolean hasMarker() { return false; }
              public int getCount() { return 0; }
              public int getCase() { return 0; }

              @Override
              protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
                throw new UnsupportedOperationException();
              }

              public static Builder newBuilder() { return new Builder(); }

              public static final class Builder
                  extends GeneratedMessageLite.Builder<Briefing, Builder> {
                private Builder() { super(null); }
                public Builder setBriefCase(Brief brief) { return this; }
                public Builder clearBriefCase() { return this; }
                public Builder setTopicCase(TopicCase topic) { return this; }
                public Builder clearTopicCase() { return this; }
                public Builder setPlainText(String text) { return this; }
                public Builder clearPlainText() { return this; }
                public Builder setRichText(String text) { return this; }
                public Builder clearRichText() { return this; }
                public Builder setXRay(String image) { return this; }
                public Builder clearXRay() { return this; }
                public Builder clearChoice() { return this; }
                public Builder setPlaintext(String text) { return this; }
                public Builder clearPlaintext() { return this; }
                public Builder addAllTags(String allTag) { return this; }
                public Builder addAllTags(Iterable<String> tags) { return this; }
                public Builder clearTags() { return this; }
                public Builder setKInt(int value) { return this; }
                public Builder clearKInt() { return this; }
                public Builder setMarker(Empty marker) { return this; }
                public Builder clearMarker() { return this; }
                public Builder setCount(long count) { return this; }
                public Builder clearCount() { return this; }
                public Builder clearGone() { return this; }
              }
            }
            """);

    private static JavaFileObject briefs(String richText) {
      return source(
          "Briefs",
          """
          record Nothing() {}

          record Brief(
              Briefing.Brief briefCase,
              Briefing.TopicCase topicCase,
              Optional<String> plainText,
              %s richText,
              Optional<String> xRay,
              String plaintext,
              List<String> tags,
              int kInt,
              Optional<Nothing> marker) {}

          @GenerateMapping
          interface NothingMapping extends MappingSpec<Nothing, com.google.protobuf.Empty> {}

          @GenerateMapping
          interface BriefMapping extends MappingSpec<Brief, Briefing> {}
          """
              .formatted(richText));
    }

    @Test
    @DisplayName("each field is read by protobuf's conventions, and nothing else is a field")
    void fields() {
      Compilation compilation = compile(BRIEFING, briefs("Optional<String>"));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generated(compilation, "BriefMappingImpl"))
          // A getXCase() whose enum has no X_NOT_SET reads a field.
          .contains("b.setBriefCase(domain.briefCase());")
          .contains("(wire.hasBriefCase() ? wire.getBriefCase() : null)")
          .contains("b.setTopicCase(domain.topicCase());")
          // plaintext is no member of the oneof, so a plain String fills it.
          .contains("b.setPlaintext(domain.plaintext());")
          .contains("b.addAllTags(hkj$copyOf(domain.tags()));")
          .contains("b.setKInt(domain.kInt());")
          // A message with no fields nests through its own spec.
          .contains("NothingMappingImpl")
          // A setter of another type writes no field, and nor does a clear method alone.
          .doesNotContain("Count")
          .doesNotContain("Gone");
    }

    @Test
    @DisplayName("a member declared in camel case is a member of its oneof too")
    void camelCaseMember() {
      Compilation compilation = compile(BRIEFING, briefs("String"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Brief.richText' is String, and fills 'richText', a member of the"
                  + " oneof 'choice'");
    }

    @Test
    @DisplayName("a sealed oneof fills its members, so only the other fields are left unfilled")
    void sealedOneofOnAWiderMessage() {
      Compilation compilation =
          compile(
              BRIEFING,
              source(
                  "Choices",
                  """
                  sealed interface Choice {
                    record PlainText(String text) implements Choice {}

                    record RichText(String text) implements Choice {}

                    record XRay(String image) implements Choice {}
                  }

                  record Short(Optional<Choice> choice, String plaintext) {}

                  @GenerateMapping
                  interface ShortMapping extends MappingSpec<Short, Briefing> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("leaving [briefCase, topicCase, tags, kInt, marker] unfilled.");
    }

    @Test
    @DisplayName(
        "a FieldMask path is a field's .proto name, camel or snake case, and a oneof's name is none")
    void protoNames() {
      Compilation compilation =
          compile(
              BRIEFING,
              source(
                  "Wholes",
                  """
                  sealed interface Choice {
                    record PlainText(String text) implements Choice {}

                    record RichText(String text) implements Choice {}

                    record XRay(String image) implements Choice {}
                  }

                  record Nothing() {}

                  record Whole(
                      Briefing.Brief briefCase,
                      Briefing.TopicCase topicCase,
                      Optional<Choice> choice,
                      String plaintext,
                      List<String> tags,
                      int kInt,
                      Optional<Nothing> marker) {}

                  @GenerateMapping
                  interface NothingMapping extends MappingSpec<Nothing, com.google.protobuf.Empty> {}

                  @GenerateMapping
                  interface WholePatch extends UpdateSpec<Whole, Briefing> {}
                  """));
      assertThat(compilation).succeeded();
      // A lite message keeps no names, so each is read from protoc's constant and the Java name.
      Assertions.assertThat(generated(compilation, "WholePatchImpl"))
          .contains("hkj$path$briefCase = \"brief_case\";")
          .contains("hkj$path$plainText = \"plain_text\";")
          .contains("hkj$path$richText = \"richText\";")
          .contains("hkj$path$xRay = \"x_ray\";")
          .contains("hkj$path$kInt = \"k_int\";")
          .contains(
              "Set.of(hkj$path$briefCase, hkj$path$topicCase, hkj$path$plainText,"
                  + " hkj$path$richText, hkj$path$xRay, hkj$path$plaintext, hkj$path$tags,"
                  + " hkj$path$kInt, hkj$path$marker)")
          .contains(
              "Map.entry(\"choice\", \"names a oneof, not a field: name one of its members \" +"
                  + " List.of(hkj$path$plainText, hkj$path$richText, hkj$path$xRay))")
          // The member the message holds decides whether the mask names the oneof's component.
          .contains("case RICHTEXT -> paths.contains(hkj$path$richText);")
          // A case constant naming no member reads as none set.
          .contains("default -> Validated.validNel(Optional.empty());");
    }

    @Test
    @DisplayName("a bean that is no message keeps its companion-like accessors as properties")
    void noMessage() {
      Compilation compilation =
          compile(
              source(
                  "Users",
                  """
                  record User(String name) {}

                  final class UserMessage {
                    private final String name;
                    private UserMessage(String name) { this.name = name; }
                    public String getName() { return name; }
                    public String getNameBytes() { return name; }
                    public static Builder newBuilder() { return new Builder(); }
                    public static final class Builder {
                      private String name;
                      public Builder setName(String name) { this.name = name; return this; }
                      public Builder clearName() { return this; }
                      public Builder setNameBytes(String bytes) { return this; }
                      public UserMessage build() { return new UserMessage(name); }
                    }
                  }

                  @GenerateMapping
                  interface UserMapping extends MappingSpec<User, UserMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'UserMessage' has more components than 'User', leaving [nameBytes] unfilled.");
      assertThat(compilation).hadErrorContaining("declare derived fields");
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("protobuf"));
    }
  }
}
