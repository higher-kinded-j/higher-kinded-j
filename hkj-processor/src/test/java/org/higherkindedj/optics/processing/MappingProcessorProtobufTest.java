// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.protobuf.Any;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueOptions;
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
          .contains("b.addAllPaths(")
          .contains("wire.getPathsList()");
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
          .contains("b.putAllFields(")
          .contains("wire.getFieldsMap()")
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
              "Declare 'seconds' as long, dropping the Optional, so the field's default encodes"
                  + " nothing, or declare the field optional in its .proto file, so protobuf"
                  + " generates hasSeconds().");
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
              "Declare 'paths' as List<String>, dropping the Optional, so the field's empty"
                  + " collection encodes nothing.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'fields' as Map<String, Value>, dropping the Optional, so the field's empty"
                  + " collection encodes nothing.");
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
                  + " member of 'kind' must be.");
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
    @DisplayName("a message is refused as the PATCH body of a sparse UpdateSpec")
    void sparse() {
      Compilation compilation =
          compile(
              source(
                  "Names",
                  """
                  record Name(String value) {}

                  @GenerateMapping
                  interface NamePatch extends UpdateSpec<Name, StringValue> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the wire 'StringValue' is a protobuf-java message, which a sparse UpdateSpec cannot"
                  + " read as a PATCH body (not supported yet).");
      assertThat(compilation)
          .hadErrorContaining(
              "Map 'StringValue' with a MappingSpec, whose parse reads every field, and apply the"
                  + " fields its FieldMask names to the domain value yourself.");
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
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("not supported yet"));
    }
  }

  @Nested
  @DisplayName("A class shaped like a message is read as protobuf generates one")
  class MessageShapes {

    // A lite message by hand: its builder inherits build() from the runtime's generic base, and a
    // field named brief_case gives it a getBriefCase() that answers no oneof's case.
    private static final JavaFileObject BRIEFING =
        JavaFileObjects.forSourceString(
            "com.example.Briefing",
            """
            package com.example;

            import com.google.protobuf.GeneratedMessageLite;

            public final class Briefing extends GeneratedMessageLite<Briefing, Briefing.Builder> {
              public enum Brief { SHORT, LONG }
              public enum TopicCase { ALPHA, BETA }

              public Brief getBriefCase() { return Brief.SHORT; }
              public boolean hasBriefCase() { return true; }
              public TopicCase getTopicCase() { return TopicCase.ALPHA; }
              public int getCount() { return 0; }

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
                public Builder setCount(long count) { return this; }
                public Builder clearCount() { return this; }
                public Builder clearGone() { return this; }
              }
            }
            """);

    @Test
    @DisplayName(
        "a getXCase() with no X_NOT_SET is a field, and a setter of another type writes none")
    void caseGetterField() {
      Compilation compilation =
          compile(
              BRIEFING,
              source(
                  "Briefs",
                  """
                  record Brief(Briefing.Brief briefCase, Briefing.TopicCase topicCase) {}

                  @GenerateMapping
                  interface BriefMapping extends MappingSpec<Brief, Briefing> {}
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generated(compilation, "BriefMappingImpl"))
          .contains("b.setBriefCase(domain.briefCase());")
          .contains("(wire.hasBriefCase() ? wire.getBriefCase() : null)")
          .contains("b.setTopicCase(domain.topicCase());")
          .doesNotContain("Count");
    }
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
