// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code build} hands each domain value on, and absence in a mapping is an {@code Optional} the
 * bridge carries, so a {@code null} the domain declares it may hold is refused wherever {@code
 * build} would hand it to something that cannot take it: a leaf, a nested spec or a lifted
 * container, whose build throws, an {@code Optional} it maps, a flattened group or a oneof it
 * reads, a Lombok {@code @Singular} method, a getter-only list or a protobuf-java setter, which
 * throw, and a site whose element type rules the null out. A side that says nothing, a site that
 * takes the null, and a mapping that only parses compile as before. {@link
 * NullableCopyIntoNonNullTest} holds the plain copy into a site declared non-null.
 *
 * <p>Each compilation carries several specs, to keep the javac runs few.
 */
@DisplayName("A null build hands on where it cannot be taken")
class NullableIntoBuildTest {

  @TempDir Path tmp;

  private static final JavaFileObject MARKED_PACKAGE =
      JavaFileObjects.forSourceString(
          "com.marked.package-info",
          """
          @NullMarked
          package com.marked;

          import org.jspecify.annotations.NullMarked;
          """);

  /** A declaration annotation named {@code Nullable}, which javac copies to field and accessor. */
  private static final JavaFileObject LEGACY_NULLABLE =
      JavaFileObjects.forSourceString(
          "com.legacy.Nullable",
          """
          package com.legacy;

          import java.lang.annotation.ElementType;
          import java.lang.annotation.Target;

          @Target({ElementType.FIELD, ElementType.METHOD})
          public @interface Nullable {}
          """);

  /** The leaf every fixture here converts a code through, so each spec states only its pair. */
  private static final JavaFileObject CODES =
      JavaFileObjects.forSourceString(
          "com.marked.Codes",
          """
          package com.marked;

          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          public final class Codes {
            private Codes() {}

            public record Code(String value) {}

            public record Address(String city) {}

            public record AddressDto(String city) {}

            public static final ValidatedPrism<String, Code> CODE =
                ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
          }
          """);

  private static Compilation compile(JavaFileObject... fixtures) {
    List<JavaFileObject> sources = new ArrayList<>(List.of(MARKED_PACKAGE, LEGACY_NULLABLE, CODES));
    sources.addAll(List.of(fixtures));
    return javac()
        .withProcessors(
            new MappingProcessor(), new MergeProcessor(), new CompanionAnnotationProcessor())
        .withOptions("-Xlint:all,-processing", "-Werror")
        .compile(sources);
  }

  private static Compilation compileWithLombok(JavaFileObject fixtures) {
    return javac()
        .withProcessors(LombokInteropTest.lombok(), new MappingProcessor())
        .compile(MARKED_PACKAGE, CODES, fixtures);
  }

  @Test
  @DisplayName(
      "a nullable component a leaf, a nested spec or a lifted container converts is refused, naming"
          + " what converts it and how to hold it as an Optional")
  void aComponentALegConvertsIsRefused() {
    JavaFileObject legs =
        JavaFileObjects.forSourceString(
            "com.marked.Legs",
            """
            package com.marked;

            import com.marked.Codes.Address;
            import com.marked.Codes.AddressDto;
            import com.marked.Codes.Code;
            import java.util.ArrayList;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MapKey;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.OptionalBridge;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.Nullable;

            public final class Legs {
              private Legs() {}

              @GenerateMapping
              public interface AddressMapping extends MappingSpec<Address, AddressDto> {}

              public record Coded(@Nullable Code code) {}

              public record CodedDto(@Nullable String code) {}

              @GenerateMapping
              public interface CodedMapping extends MappingSpec<Coded, CodedDto> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              public record Shipped(@Nullable Address address) {}

              public record ShippedDto(@Nullable AddressDto address) {}

              @GenerateMapping
              public interface ShippedMapping extends MappingSpec<Shipped, ShippedDto> {}

              public record Coding(@Nullable List<Code> codes) {}

              public record CodingDto(@Nullable List<String> codes) {}

              @GenerateMapping
              public interface CodingMapping extends MappingSpec<Coding, CodingDto> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              public record Stops(@Nullable List<Address> stops) {}

              public record StopsDto(@Nullable List<AddressDto> stops) {}

              @GenerateMapping
              public interface StopsMapping extends MappingSpec<Stops, StopsDto> {}

              public record Rows(Code @Nullable [] codes) {}

              public record RowsDto(String @Nullable [] codes) {}

              @GenerateMapping
              public interface RowsMapping extends MappingSpec<Rows, RowsDto> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              public record Valued(@Nullable Map<String, Code> codes) {}

              public record ValuedDto(@Nullable Map<String, String> codes) {}

              @GenerateMapping
              public interface ValuedMapping extends MappingSpec<Valued, ValuedDto> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              public record Keyed(@Nullable Map<Code, String> byCode) {}

              public record KeyedDto(@Nullable Map<String, String> byCode) {}

              @GenerateMapping
              public interface KeyedMapping extends MappingSpec<Keyed, KeyedDto> {
                @MapKey("byCode")
                default ValidatedPrism<String, Code> codeKey() { return Codes.CODE; }
              }

              public record Paired(@Nullable Map<Code, Code> pairs) {}

              public record PairedDto(@Nullable Map<String, String> pairs) {}

              @GenerateMapping
              public interface PairedMapping extends MappingSpec<Paired, PairedDto> {
                @MapKey("pairs")
                default ValidatedPrism<String, Code> pairKey() { return Codes.CODE; }

                default ValidatedPrism<String, Code> pairs() { return Codes.CODE; }
              }

              // The wire's site is declared non-null, so the bridge needs it nullable too.
              public record StrictDto(String code) {}

              @GenerateMapping
              public interface StrictMapping extends MappingSpec<Coded, StrictDto> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              public static class CodedBean {
                private @Nullable String code;

                public @Nullable String getCode() { return code; }
                public void setCode(@Nullable String code) { this.code = code; }
              }

              @GenerateMapping
              public interface CodedBeanMapping extends MappingSpec<Coded, CodedBean> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              public static class StrictBean {
                private @Nullable String code;

                public @Nullable String getCode() { return code; }
                public void setCode(String code) { this.code = code; }
              }

              @GenerateMapping
              public interface StrictBeanMapping extends MappingSpec<Coded, StrictBean> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              // A getter-only list has no absent state, so no Optional is offered.
              public static class FilledBean {
                private final List<String> codes = new ArrayList<>();

                public List<String> getCodes() { return codes; }
              }

              @GenerateMapping
              public interface FilledMapping extends MappingSpec<Coding, FilledBean> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              public record Listed(@Nullable List<String> codes) {}

              @GenerateMapping
              public interface ListedMapping extends MappingSpec<Listed, FilledBean> {}

              public record One<T extends @Nullable Object>(T code) {}

              // The type argument says it may be null, so holding it as an Optional is no fix.
              @GenerateMapping
              public interface OneMapping extends MappingSpec<One<@Nullable Code>, CodedDto> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              // The spec gives the wire's T a non-null argument, which the bridge needs nullable.
              public record ItemDto<T extends @Nullable Object>(T code) {}

              @GenerateMapping
              public interface ItemMapping extends MappingSpec<Coded, ItemDto<String>> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              // A mix-in's leaf serves specs whose component is no Optional, so it is overridden.
              public interface CodeVocabulary {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              @GenerateMapping
              public interface SharedMapping extends MappingSpec<Coded, CodedDto>, CodeVocabulary {}

              // Setters only: built, and never parsed.
              public static class SinkBean {
                private @Nullable String code;

                public void setCode(@Nullable String code) { this.code = code; }
              }

              @GenerateMapping
              public interface SinkMapping extends MappingSpec<Coded, SinkBean> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              public record HoledKeys(Map<@Nullable Code, String> byCode) {}

              @GenerateMapping
              public interface HoledKeysMapping extends MappingSpec<HoledKeys, KeyedDto> {
                @MapKey("byCode")
                default ValidatedPrism<String, Code> codeKey() { return Codes.CODE; }
              }

              public record HoledPairs(Map<Code, @Nullable Code> pairs) {}

              @GenerateMapping
              public interface HoledPairsMapping extends MappingSpec<HoledPairs, PairedDto> {
                @MapKey("pairs")
                default ValidatedPrism<String, Code> pairKey() { return Codes.CODE; }

                default ValidatedPrism<String, Code> pairs() { return Codes.CODE; }
              }

              public record KeyedPairs(Map<@Nullable Code, Code> pairs) {}

              @GenerateMapping
              public interface KeyedPairsMapping extends MappingSpec<KeyedPairs, PairedDto> {
                @MapKey("pairs")
                default ValidatedPrism<String, Code> pairKey() { return Codes.CODE; }

                default ValidatedPrism<String, Code> pairs() { return Codes.CODE; }
              }

              public record BridgedKeys(Optional<Map<@Nullable Code, String>> byCode) {}

              @GenerateMapping
              public interface BridgedKeysMapping extends MappingSpec<BridgedKeys, KeyedDto> {
                @OptionalBridge
                Optional<Map<@Nullable Code, String>> byCode();

                @MapKey("byCode")
                default ValidatedPrism<String, Code> codeKey() { return Codes.CODE; }
              }

              public record Items<T extends @Nullable Object>(List<T> codes) {}

              @GenerateMapping
              public interface ItemsMapping extends MappingSpec<Items<@Nullable Code>, CodingDto> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }
            }
            """);

    Compilation compilation = compile(legs);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Coded.code' is declared @Nullable, and build converts it through the"
                + " leaf 'code()'. A leaf's or a nested spec's build takes no null, and build would"
                + " throw on it. parse never reads a null into it either, since it refuses any null"
                + " that no Optional bridges. As 'Coded.code' may be absent, declare it"
                + " Optional<Codes.Code> and annotate 'code()' with @OptionalBridge, so build writes an"
                + " absent value as null and parse reads it back as empty; or declare 'Coded.code'"
                + " non-null, if build never meets it null.")
        .inFile(legs)
        .onLineContaining("interface CodedMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Shipped.address' is declared @Nullable, and build converts it through"
                + " the spec 'AddressMapping'.")
        .inFile(legs)
        .onLineContaining("interface ShippedMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "declare it Optional<Codes.Address> and add '@OptionalBridge"
                + " Optional<com.marked.Codes.Address> address();' to the spec, so build writes");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Coding.codes' is declared @Nullable, and build converts its elements"
                + " through the leaf 'codes()'. A lifted container is built element by element, so"
                + " a null one has none to build, and build would throw on it.")
        .inFile(legs)
        .onLineContaining("interface CodingMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Stops.stops' is declared @Nullable, and build converts its elements"
                + " through the spec 'AddressMapping'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Rows.codes' is declared @Nullable, and build converts its elements"
                + " through the leaf 'codes()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Valued.codes' is declared @Nullable, and build converts its values"
                + " through the leaf 'codes()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Keyed.byCode' is declared @Nullable, and build converts its keys"
                + " through the leaf 'codeKey()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Paired.pairs' is declared @Nullable, and build converts its keys"
                + " through the leaf 'pairKey()' and its values through the leaf 'pairs()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "declare it Optional<Codes.Code> and annotate 'code()' with @OptionalBridge, and mark"
                + " 'StrictDto.code' @Nullable, so build writes")
        .inFile(legs)
        .onLineContaining("interface StrictMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "As 'Coded.code' may be absent, declare it Optional<Codes.Code>, which a bean wire bridges"
                + " as it is, so build writes")
        .inFile(legs)
        .onLineContaining("interface CodedBeanMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "declare it Optional<Codes.Code>, which a bean wire bridges as it is, and mark the"
                + " parameter of setCode(String) @Nullable, so build writes");
    assertThat(compilation)
        .hadErrorContaining(
            "parse never reads a null into it either, since it refuses any null that no Optional"
                + " bridges. Declare 'Coding.codes' non-null, if build never meets it null.")
        .inFile(legs)
        .onLineContaining("interface FilledMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Listed.codes' is declared @Nullable, and build hands it to the"
                + " getter-only bean property 'codes'. 'codes' is filled through its own getter (the"
                + " JAXB convention, getCodes().addAll(...)), and addAll throws on a null, so build"
                + " would throw on it. parse never reads a null into it either, since it refuses any"
                + " null that no Optional bridges. Declare 'Listed.codes' non-null, if build never"
                + " meets it null, and hold an absent list as an empty one.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'One.code' is T, whose type argument the spec declares @Nullable, and"
                + " build converts it through the leaf 'code()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "bridges. Declare that type argument non-null, if build never meets it null.");
    assertThat(compilation)
        .hadErrorContaining(
            "declare it Optional<Codes.Code> and annotate 'code()' with @OptionalBridge, and mark"
                + " the type argument String the spec gives 'ItemDto.code' @Nullable, so build"
                + " writes")
        .inFile(legs)
        .onLineContaining("interface ItemMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "declare it Optional<Codes.Code> and override 'code()' on the spec with"
                + " @OptionalBridge, returning 'CodeVocabulary.super.code()', so build writes")
        .inFile(legs)
        .onLineContaining("interface SharedMapping");
    // A mapping that only builds has no parse to name.
    assertThat(compilation)
        .hadErrorContaining(
            "and build would throw on it. As 'Coded.code' may be absent, declare it"
                + " Optional<Codes.Code>, which a bean wire bridges as it is, so build writes an"
                + " absent value as null; or declare 'Coded.code' non-null, if build never meets it"
                + " null.")
        .inFile(legs)
        .onLineContaining("interface SinkMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'HoledKeys.byCode' declares its keys @Nullable, and build converts them"
                + " through the leaf 'codeKey()'. A leaf's or a nested spec's build takes no null,"
                + " so build would throw on a null one. parse never reads one either, since it"
                + " refuses a null key. Declare the keys of 'HoledKeys.byCode' non-null, if build"
                + " never meets a null one.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'HoledPairs.pairs' declares its values @Nullable, and build converts"
                + " them through the leaf 'pairs()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Items.codes' declares its elements @Nullable through its type argument,"
                + " and build converts them through the leaf 'codes()'. A leaf's or a nested spec's"
                + " build takes no null, so build would throw on a null one. parse never reads one"
                + " either, since it refuses a null element. Declare them non-null in the type"
                + " argument the spec gives 'Items.codes', if build never meets a null one.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'KeyedPairs.pairs' declares its keys @Nullable, and build converts them"
                + " through the leaf 'pairKey()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'BridgedKeys.byCode' declares its value's keys @Nullable, and build"
                + " converts them through the leaf 'codeKey()'.");
    Assertions.assertThat(compilation.errors()).hasSize(22);
  }

  @Test
  @DisplayName(
      "a nullable Optional, flattened group or oneof's sealed component is refused, as build reads"
          + " a value from it")
  void aComponentBuildReadsIsRefused() {
    JavaFileObject read =
        JavaFileObjects.forSourceString(
            "com.marked.Read",
            """
            package com.marked;

            import com.marked.Codes.Code;
            import java.util.Optional;
            import org.higherkindedj.optics.annotations.Flatten;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.OptionalBridge;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.Nullable;

            public final class Read {
              private Read() {}

              public record Lifted(@Nullable Optional<Code> code) {}

              public record LiftedDto(@Nullable Optional<String> code) {}

              @GenerateMapping
              public interface LiftedMapping extends MappingSpec<Lifted, LiftedDto> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              public record Bridged(@Nullable Optional<String> nickname) {}

              public record BridgedDto(@Nullable String nickname) {}

              @GenerateMapping
              public interface BridgedMapping extends MappingSpec<Bridged, BridgedDto> {
                @OptionalBridge
                Optional<String> nickname();
              }

              public record Note(String text) {}

              public record Entry(String title, @Nullable Note note) {}

              public record EntryDto(String title, @Nullable String text) {}

              @GenerateMapping
              public interface EntryMapping extends MappingSpec<Entry, EntryDto> {
                @Flatten
                Note note();
              }
            }
            """);

    Compilation compilation = compile(read);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Lifted.code' is declared @Nullable, and build maps the value it holds."
                + " An empty Optional is how a mapping says a value is absent, and build maps a"
                + " present one, so it would throw on a null Optional. parse never reads a null"
                + " into it either, since it refuses any null that no Optional bridges. Declare"
                + " 'Lifted.code' non-null, since an empty Optional already says the value is"
                + " absent.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Bridged.nickname' is declared @Nullable, and build maps the value it"
                + " holds.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Entry.note' is declared @Nullable, and build spreads it across the"
                + " wire, reading its components 'text'. build reads each of them from it, so it"
                + " would throw on a null, and parse never reads one back, since it builds the"
                + " record from those components. Declare 'Entry.note' non-null, if build never"
                + " meets it null; a flattened record has no absent state of its own on the"
                + " wire.");
    Assertions.assertThat(compilation.errors()).hasSize(3);
  }

  @Test
  @DisplayName(
      "an element declared nullable is refused where a lifted leg converts it, or a copy hands it"
          + " to a site whose element type rules it out, at every level the copy reaches")
  void aNullableElementIsRefused() {
    JavaFileObject elements =
        JavaFileObjects.forSourceString(
            "com.marked.Elements",
            """
            package com.marked;

            import com.marked.Codes.Code;
            import java.util.ArrayList;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import org.higherkindedj.optics.annotations.Flatten;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.GenerateMerge;
            import org.higherkindedj.optics.annotations.MapKey;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.OptionalBridge;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.NullUnmarked;
            import org.jspecify.annotations.Nullable;

            public final class Elements {
              private Elements() {}

              public record Holed(List<@Nullable String> tags) {}

              public record HoledDto(List<String> tags) {}

              @GenerateMapping
              public interface HoledMapping extends MappingSpec<Holed, HoledDto> {}

              public record Valued(Map<String, @Nullable String> notes) {}

              public record ValuedDto(Map<String, String> notes) {}

              @GenerateMapping
              public interface ValuedMapping extends MappingSpec<Valued, ValuedDto> {}

              public record Grid(List<List<@Nullable String>> rows) {}

              public record GridDto(List<List<String>> rows) {}

              @GenerateMapping
              public interface GridMapping extends MappingSpec<Grid, GridDto> {}

              public record Kept(Optional<List<@Nullable String>> names) {}

              public record KeptDto(Optional<List<String>> names) {}

              @GenerateMapping
              public interface KeptMapping extends MappingSpec<Kept, KeptDto> {}

              public record Absent(Optional<List<@Nullable String>> names) {}

              public record AbsentDto(@Nullable List<String> names) {}

              @GenerateMapping
              public interface AbsentMapping extends MappingSpec<Absent, AbsentDto> {
                @OptionalBridge
                Optional<List<@Nullable String>> names();
              }

              public record Keyed(Map<Code, List<@Nullable String>> byCode) {}

              public record KeyedDto(Map<String, List<String>> byCode) {}

              @GenerateMapping
              public interface KeyedMapping extends MappingSpec<Keyed, KeyedDto> {
                @MapKey("byCode")
                default ValidatedPrism<String, Code> codeKey() { return Codes.CODE; }
              }

              public static class SetBean {
                private List<String> tags = new ArrayList<>();

                public List<String> getTags() { return tags; }
                public void setTags(List<String> tags) { this.tags = tags; }
              }

              @GenerateMapping
              public interface SetBeanMapping extends MappingSpec<Holed, SetBean> {}

              public static class FilledBean {
                private final List<String> tags = new ArrayList<>();

                public List<String> getTags() { return tags; }
              }

              @GenerateMapping
              public interface FilledMapping extends MappingSpec<Holed, FilledBean> {}

              // The element is a type variable, which the spec instantiates non-null.
              public record ItemsDto<T extends @Nullable Object>(List<T> tags) {}

              @GenerateMapping
              public interface ItemsMapping extends MappingSpec<Holed, ItemsDto<String>> {}

              // The component is a type variable, so every level below it is the spec's.
              public record BoxDto<T extends @Nullable Object>(T tags) {}

              @GenerateMapping
              public interface BoxMapping extends MappingSpec<Holed, BoxDto<List<String>>> {}

              public record Coding(List<@Nullable Code> codes) {}

              public record CodingDto(List<String> codes) {}

              @GenerateMapping
              public interface CodingMapping extends MappingSpec<Coding, CodingDto> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              public record Valuing(Map<String, @Nullable Code> codes) {}

              public record ValuingDto(Map<String, String> codes) {}

              @GenerateMapping
              public interface ValuingMapping extends MappingSpec<Valuing, ValuingDto> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              public record Present(Optional<List<@Nullable Code>> codes) {}

              public record PresentDto(@Nullable List<String> codes) {}

              @GenerateMapping
              public interface PresentMapping extends MappingSpec<Present, PresentDto> {
                @OptionalBridge
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              public record Names(List<@Nullable String> names) {}

              public record Labels(String label) {}

              public record Merged(List<String> names, String label) {}

              @GenerateMerge
              public interface MergedAssembly {
                Merged merge(Names names, Labels labels);
              }

              // A flattened group's records say nothing, and the wire's argument is the spec's.
              @NullUnmarked
              public record Inner(List<@Nullable String> items) {}

              @NullUnmarked
              public record Outer(String title, Inner inner) {}

              public record PageDto<T extends @Nullable Object>(String title, List<T> items) {}

              @GenerateMapping
              public interface OuterMapping extends MappingSpec<Outer, PageDto<String>> {
                @Flatten
                Inner inner();
              }

              // The setter's T is non-null by its own bound, whatever a subclass gives it.
              public static class StrictBase<T> {
                private List<T> tags = new ArrayList<>();

                public List<T> getTags() { return tags; }
                public void setTags(List<T> tags) { this.tags = tags; }
              }

              public static class StrictBag extends StrictBase<String> {}

              @GenerateMapping
              public interface StrictBagMapping extends MappingSpec<Holed, StrictBag> {}

              public record StrictItems<T>(List<T> tags) {}

              @GenerateMapping
              public interface StrictItemsMapping
                  extends MappingSpec<Holed, StrictItems<@Nullable String>> {}

              public record Generic<T extends @Nullable Object>(T tags) {}

              @GenerateMapping
              public interface GenericMapping
                  extends MappingSpec<Generic<List<@Nullable String>>, HoledDto> {}

              public record Lifted(@Nullable Optional<String> nick) {}

              public record LiftedDto(Optional<String> nick) {}

              @GenerateMapping
              public interface LiftedMapping extends MappingSpec<Lifted, LiftedDto> {}

              // A copied map's keys count at its own level, as its values do.
              public record Indexed(Map<@Nullable String, Integer> counts) {}

              public record IndexedDto(Map<String, Integer> counts) {}

              @GenerateMapping
              public interface IndexedMapping extends MappingSpec<Indexed, IndexedDto> {}

              public record Shelves(List<Map<@Nullable String, Integer>> shelves) {}

              public record ShelvesDto(List<Map<String, Integer>> shelves) {}

              @GenerateMapping
              public interface ShelvesMapping extends MappingSpec<Shelves, ShelvesDto> {}

              public record CountsDto<K extends @Nullable Object>(Map<K, Integer> counts) {}

              @GenerateMapping
              public interface CountsMapping extends MappingSpec<Indexed, CountsDto<String>> {}

              public record IndexedBox<T extends @Nullable Object>(T counts) {}

              @GenerateMapping
              public interface IndexedBoxMapping
                  extends MappingSpec<Indexed, IndexedBox<Map<String, Integer>>> {}

              @GenerateMapping
              public interface BoxedIndexMapping
                  extends MappingSpec<IndexedBox<Map<@Nullable String, Integer>>, IndexedDto> {}

              // The bound of L rules a null element out, whatever the spec's argument says.
              public record ShelfDto<L extends List<String>>(L tags) {}

              // The argument holds the level two containers down.
              public record GridBox<T extends @Nullable Object>(T rows) {}

              @GenerateMapping
              public interface GridBoxMapping
                  extends MappingSpec<Grid, GridBox<List<List<String>>>> {}

              // The spec's argument and L's bound both rule the null out.
              @GenerateMapping
              public interface MarkedShelfMapping
                  extends MappingSpec<Holed, ShelfDto<List<String>>> {}

              public record Rows(Map<String, Map<@Nullable String, Integer>> rows) {}

              public record RowsDto(Map<String, Map<String, Integer>> rows) {}

              @GenerateMapping
              public interface RowsMapping extends MappingSpec<Rows, RowsDto> {}

              public record Tallied(Map<String, Integer> counts, String label) {}

              @GenerateMerge
              public interface TalliedAssembly {
                Tallied merge(Indexed indexed, Labels labels);
              }

              // build calls the accessor, which declares its elements nullable.
              public record Loud(List<String> tags) {
                @Override
                public List<@Nullable String> tags() {
                  return new ArrayList<>(tags);
                }
              }

              public record LoudDto(List<String> tags) {}

              @GenerateMapping
              public interface LoudMapping extends MappingSpec<Loud, LoudDto> {}

              public record Shout(String name) {}

              public record Heard(List<String> tags, String name) {}

              @GenerateMerge
              public interface HeardAssembly {
                Heard merge(Loud loud, Shout shout);
              }
            }
            """);
    // The spec's package is not @NullMarked, so its argument says nothing of its own.
    JavaFileObject loose =
        JavaFileObjects.forSourceString(
            "com.loose.ShelfMapping",
            """
            package com.loose;

            import com.marked.Elements.Holed;
            import com.marked.Elements.ShelfDto;
            import java.util.List;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface ShelfMapping extends MappingSpec<Holed, ShelfDto<List<String>>> {}
            """);

    Compilation compilation = compile(elements, loose);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Holed.tags' declares its elements @Nullable, and build copies them to"
                + " the record component 'HoledDto.tags'. A same-typed component is copied as it"
                + " is, null elements included, and the element type String in 'HoledDto.tags'"
                + " carries no @Nullable inside @NullMarked package 'com.marked', so build would"
                + " hand the wire a null its type says it cannot hold. Give the two one nullness:"
                + " mark the element type String in 'HoledDto.tags' @Nullable; or declare the"
                + " elements of 'Holed.tags' non-null, if build never meets a null one.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Valued.notes' declares its values @Nullable, and build copies them to"
                + " the record component 'ValuedDto.notes'. A same-typed component is copied as it"
                + " is, null values included, and the value type String in 'ValuedDto.notes'");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Grid.rows' declares its elements' elements @Nullable, and build copies"
                + " them to the record component 'GridDto.rows'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Kept.names' declares its value's elements @Nullable, and build copies"
                + " them to the record component 'KeptDto.names'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Absent.names' declares its value's elements @Nullable, and build copies"
                + " them to the record component 'AbsentDto.names'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Keyed.byCode' declares its values' elements @Nullable, and build copies"
                + " them to the record component 'KeyedDto.byCode'.");
    assertThat(compilation)
        .hadErrorContaining(
            "build copies them to the bean property 'tags', through setTags(List<String>). A"
                + " same-typed component is copied as it is, null elements included, and the"
                + " element type String in the parameter of setTags(List<String>) carries no"
                + " @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "build copies them to the getter-only bean property 'tags'. A same-typed component is"
                + " copied as it is, null elements included, and the element type String in the"
                + " return type of getTags() carries no @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "and the type argument String the spec gives 'ItemsDto.tags' carries no @Nullable"
                + " inside @NullMarked package 'com.marked', so build would hand the wire a null its"
                + " type says it cannot hold. Give the two one nullness: mark the type argument"
                + " String the spec gives 'ItemsDto.tags' @Nullable;");
    assertThat(compilation)
        .hadErrorContaining(
            "and the element type String of the type argument the spec gives 'BoxDto.tags'"
                + " carries no @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Coding.codes' declares its elements @Nullable, and build converts them"
                + " through the leaf 'codes()'. A leaf's or a nested spec's build takes no null, so"
                + " build would throw on a null one. parse never reads one either, since it refuses"
                + " a null element. Declare the elements of 'Coding.codes' non-null, if build never"
                + " meets a null one.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Valuing.codes' declares its values @Nullable, and build converts them"
                + " through the leaf 'codes()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Present.codes' declares its value's elements @Nullable, and build"
                + " converts them through the leaf 'codes()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "source component 'Names.names' declares its elements @Nullable, and the merge copies"
                + " them to the record component 'Merged.names'. A plain merge copies each source"
                + " value as it is, null elements included, and the element type String in"
                + " 'Merged.names' carries no @Nullable inside @NullMarked package 'com.marked', so"
                + " the merge would hand the target a null its type says it cannot hold. Give the"
                + " two one nullness: mark the element type String in 'Merged.names' @Nullable; or"
                + " declare the elements of 'Names.names' non-null, if the merge never meets a null"
                + " one. Or give any fill a leaf and return Validated<NonEmptyList<FieldError>,"
                + " Merged>, so the merge, now fallible, reports a null element as a located"
                + " error.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Inner.items' declares its elements @Nullable, and build copies them to"
                + " the record component 'PageDto.items'. A same-typed component is copied as it is,"
                + " null elements included, and the type argument String the spec gives"
                + " 'PageDto.items' carries no @Nullable inside @NullMarked package 'com.marked'");
    assertThat(compilation)
        .hadErrorContaining(
            "build copies them to the bean property 'tags', through setTags(List<T>) (declared on"
                + " 'StrictBase'). A same-typed component is copied as it is, null elements included,"
                + " and the element type T in the parameter of setTags(List<T>) (declared on"
                + " 'StrictBase') carries no @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "and the element type T in 'StrictItems.tags' carries no @Nullable inside @NullMarked"
                + " package 'com.marked', so build would hand the wire a null its type says it cannot"
                + " hold. Give the two one nullness: mark the element type T in 'StrictItems.tags'"
                + " @Nullable;");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Generic.tags' declares its elements @Nullable through its type argument,"
                + " and build copies them to the record component 'HoledDto.tags'.");
    assertThat(compilation)
        .hadErrorContaining(
            "or declare them non-null in the type argument the spec gives 'Generic.tags', if build"
                + " never meets a null one.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Lifted.nick' is declared @Nullable, and build copies it to the record"
                + " component 'LiftedDto.nick', which is declared non-null.")
        .inFile(elements)
        .onLineContaining("interface LiftedMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "Declare 'Lifted.nick' non-null, since an empty Optional already says the value is"
                + " absent; or mark 'LiftedDto.nick' @Nullable.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Indexed.counts' declares its keys @Nullable, and build copies them to"
                + " the record component 'IndexedDto.counts'. A same-typed component is copied as it"
                + " is, null keys included, and the key type String in 'IndexedDto.counts' carries"
                + " no @Nullable inside @NullMarked package 'com.marked', so build would hand the"
                + " wire a null its type says it cannot hold. Give the two one nullness: mark the"
                + " key type String in 'IndexedDto.counts' @Nullable; or declare the keys of"
                + " 'Indexed.counts' non-null, if build never meets a null one.")
        .inFile(elements)
        .onLineContaining("interface IndexedMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Shelves.shelves' declares its elements' keys @Nullable, and build copies"
                + " them to the record component 'ShelvesDto.shelves'.");
    assertThat(compilation)
        .hadErrorContaining(
            "and the type argument String the spec gives 'CountsDto.counts' carries no @Nullable")
        .inFile(elements)
        .onLineContaining("interface CountsMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "and the key type String of the type argument the spec gives 'IndexedBox.counts'"
                + " carries no @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'IndexedBox.counts' declares its keys @Nullable through its type"
                + " argument, and build copies them to the record component 'IndexedDto.counts'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Holed.tags' declares its elements @Nullable, and build copies them to"
                + " the record component 'ShelfDto.tags'. A same-typed component is copied as it is,"
                + " null elements included, and the element type String in the bound of the type"
                + " variable L in 'ShelfDto.tags' carries no @Nullable inside @NullMarked package"
                + " 'com.marked', so build would hand the wire a null its type says it cannot hold."
                + " Give the two one nullness: mark the element type String in the bound of the"
                + " type variable L in 'ShelfDto.tags' @Nullable; or declare the elements of"
                + " 'Holed.tags' non-null, if build never meets a null one.")
        .inFile(loose)
        .onLineContaining("interface ShelfMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "source component 'Indexed.counts' declares its keys @Nullable, and the merge copies"
                + " them to the record component 'Tallied.counts'. A plain merge copies each source"
                + " value as it is, null keys included, and the key type String in 'Tallied.counts'"
                + " carries no @Nullable");
    assertThat(compilation)
        .hadErrorContaining("so the merge, now fallible, reports a null key as an error.");
    assertThat(compilation)
        .hadErrorContaining(
            "Give the two one nullness: mark the element type String of the type argument the spec"
                + " gives 'ShelfDto.tags' @Nullable, and mark the element type String in the bound"
                + " of the type variable L in 'ShelfDto.tags' @Nullable;")
        .inFile(elements)
        .onLineContaining("interface MarkedShelfMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "Give the two one nullness: mark the key type String at the values' keys of"
                + " 'RowsDto.rows' @Nullable;");
    assertThat(compilation)
        .hadErrorContaining(
            "and the element type String at the elements' elements of the type argument the spec"
                + " gives 'GridBox.rows' carries no @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Loud.tags' declares its elements @Nullable, and build copies them to"
                + " the record component 'LoudDto.tags'.")
        .inFile(elements)
        .onLineContaining("interface LoudMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "source component 'Loud.tags' declares its elements @Nullable, and the merge copies"
                + " them to the record component 'Heard.tags'.")
        .inFile(elements)
        .onLineContaining("Heard merge(Loud loud, Shout shout)");
    Assertions.assertThat(compilation.errors()).hasSize(31);
  }

  @Test
  @DisplayName(
      "a type argument is read as the spec writes it: one that says the domain's value may be null"
          + " is refused, and so is one that says the wire's may not")
  void aTypeArgumentIsReadWhereTheSpecWritesIt() {
    JavaFileObject generic =
        JavaFileObjects.forSourceString(
            "com.marked.Generic",
            """
            package com.marked;

            import java.util.Optional;
            import org.higherkindedj.optics.annotations.Flatten;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.OptionalBridge;
            import org.jspecify.annotations.Nullable;

            public final class Generic {
              private Generic() {}

              public record One<T extends @Nullable Object>(T value) {}

              public record ValueDto(String value) {}

              @GenerateMapping
              public interface OneMapping extends MappingSpec<One<@Nullable String>, ValueDto> {}

              public record Plain(@Nullable String value) {}

              public record ItemDto<T extends @Nullable Object>(T value) {}

              @GenerateMapping
              public interface ItemMapping extends MappingSpec<Plain, ItemDto<String>> {}

              // A flattened group's type argument is written on the domain that holds it.
              public record Note<T extends @Nullable Object>(T text) {}

              public record Entry(String title, Note<@Nullable String> note) {}

              public record EntryDto(String title, String text) {}

              @GenerateMapping
              public interface EntryMapping extends MappingSpec<Entry, EntryDto> {
                @Flatten
                Note<@Nullable String> note();
              }

              // The bridge writes an empty Optional as null, which ItemDto<String> rules out.
              public record Absent(Optional<String> value) {}

              @GenerateMapping
              public interface AbsentMapping extends MappingSpec<Absent, ItemDto<String>> {
                @OptionalBridge
                Optional<String> value();
              }
            }
            """);

    Compilation compilation = compile(generic);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'One.value' is T, whose type argument the spec declares @Nullable, and"
                + " build copies it to the record component 'ValueDto.value', which is declared"
                + " non-null.")
        .inFile(generic)
        .onLineContaining("interface OneMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "As 'One.value' may be absent, mark 'ValueDto.value' @Nullable; or declare that type"
                + " argument non-null, if build never meets it null.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Plain.value' is declared @Nullable, and build copies it to the record"
                + " component 'ItemDto.value', whose type T the spec gives a non-null argument. A"
                + " same-typed component is copied as it is, a null included, and the type argument"
                + " String the spec gives 'ItemDto.value' carries no @Nullable inside @NullMarked"
                + " package 'com.marked', so build would write a null its declaration rules out, and"
                + " either throw or leave a null its type says it cannot hold. As 'Plain.value' may"
                + " be absent, mark the type argument String the spec gives 'ItemDto.value'"
                + " @Nullable; or declare 'Plain.value' non-null, if build never meets it null.")
        .inFile(generic)
        .onLineContaining("interface ItemMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Note.text' is T, whose type argument 'Entry' declares @Nullable, and"
                + " build copies it to the record component 'EntryDto.text', which is declared"
                + " non-null.")
        .inFile(generic)
        .onLineContaining("interface EntryMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Absent.value' is Optional<String>, bridged to the record component"
                + " 'ItemDto.value', whose type T the spec gives a non-null argument.")
        .inFile(generic)
        .onLineContaining("interface AbsentMapping");
    Assertions.assertThat(compilation.errors()).hasSize(4);
  }

  @Test
  @DisplayName(
      "a type argument crossing a nested spec is refused where the spec rules its null out, as it"
          + " takes the value or as it builds the wire, a mix-in's leaf converting it included")
  void aTypeArgumentCrossingANestedSpecIsRefused() {
    JavaFileObject nested =
        JavaFileObjects.forSourceString(
            "com.marked.Nested",
            """
            package com.marked;

            import com.marked.Codes.Code;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.OptionalBridge;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.Nullable;

            public final class Nested {
              private Nested() {}

              public record One<T extends @Nullable Object>(T value) {}

              public record StrDto(String value) {}

              @GenerateMapping
              public interface OneMapping extends MappingSpec<One<String>, StrDto> {}

              public record Outer(One<@Nullable String> one) {}

              public record OuterDto(StrDto one) {}

              @GenerateMapping
              public interface OuterMapping extends MappingSpec<Outer, OuterDto> {}

              public record Many(List<One<@Nullable String>> ones) {}

              public record ManyDto(List<StrDto> ones) {}

              @GenerateMapping
              public interface ManyMapping extends MappingSpec<Many, ManyDto> {}

              public record Maybe(Optional<One<@Nullable String>> one) {}

              public record MaybeDto(@Nullable StrDto one) {}

              @GenerateMapping
              public interface MaybeMapping extends MappingSpec<Maybe, MaybeDto> {
                @OptionalBridge
                Optional<One<@Nullable String>> one();
              }

              public record RowsDto(List<String> value) {}

              @GenerateMapping
              public interface RowsMapping extends MappingSpec<One<List<String>>, RowsDto> {}

              public record Deep(One<List<@Nullable String>> one) {}

              public record DeepDto(RowsDto one) {}

              @GenerateMapping
              public interface DeepMapping extends MappingSpec<Deep, DeepDto> {}

              // The domain's own type argument gives the nested value its nullable argument.
              public record Wrapper<X extends @Nullable Object>(X one) {}

              @GenerateMapping
              public interface WrapperMapping
                  extends MappingSpec<Wrapper<One<@Nullable String>>, OuterDto> {}

              // T's own bound is non-null.
              public record Box<T extends @Nullable Object>(T item) {}

              public record BoxDto<T extends @Nullable Object>(T item) {}

              @GenerateMapping
              public interface BoxMapping<T> extends MappingSpec<Box<T>, BoxDto<T>> {}

              public record Crate(Box<@Nullable String> box) {}

              public record CrateDto(BoxDto<String> box) {}

              @GenerateMapping
              public interface CrateMapping extends MappingSpec<Crate, CrateDto> {}

              // T takes a null, and builds it into the wire.
              public record LooseBox<T extends @Nullable Object>(T item) {}

              public record LooseBoxDto<T extends @Nullable Object>(T item) {}

              @GenerateMapping
              public interface LooseBoxMapping<T extends @Nullable Object>
                  extends MappingSpec<LooseBox<T>, LooseBoxDto<T>> {}

              public record Bin(LooseBox<@Nullable String> box) {}

              public record BinDto(LooseBoxDto<String> box) {}

              @GenerateMapping
              public interface BinMapping extends MappingSpec<Bin, BinDto> {}

              public record Shelf(LooseBox<List<@Nullable String>> box) {}

              public record ShelfDto(LooseBoxDto<List<String>> box) {}

              @GenerateMapping
              public interface ShelfMapping extends MappingSpec<Shelf, ShelfDto> {}

              public record Open(LooseBox<@Nullable String> box) {}

              public record OpenDto(LooseBoxDto<@Nullable String> box) {}

              // Both sides take the null.
              @GenerateMapping
              public interface OpenMapping extends MappingSpec<Open, OpenDto> {}

              public record Kept(One<String> one) {}

              @GenerateMapping
              public interface KeptMapping extends MappingSpec<Kept, OuterDto> {}

              // The nested spec's own argument takes the null.
              public record Pair<T extends @Nullable Object>(T value) {}

              public record PairDto(@Nullable String value) {}

              @GenerateMapping
              public interface PairMapping extends MappingSpec<Pair<@Nullable String>, PairDto> {}

              public record Holds(Pair<@Nullable String> pair) {}

              public record HoldsDto(PairDto pair) {}

              @GenerateMapping
              public interface HoldsMapping extends MappingSpec<Holds, HoldsDto> {}

              public record Lifted(Optional<One<@Nullable String>> one) {}

              public record LiftedDto(Optional<StrDto> one) {}

              @GenerateMapping
              public interface LiftedMapping extends MappingSpec<Lifted, LiftedDto> {}

              public record Valued(Map<String, One<@Nullable String>> ones) {}

              public record ValuedDto(Map<String, StrDto> ones) {}

              @GenerateMapping
              public interface ValuedMapping extends MappingSpec<Valued, ValuedDto> {}

              // An element-mapped spec's leaf converts T, and a leaf's build takes no null.
              public record Page<T extends @Nullable Object>(List<T> items) {}

              public record PageDto<D extends @Nullable Object>(List<D> items) {}

              @GenerateMapping
              public interface PageMapping<T extends @Nullable Object, D extends @Nullable Object>
                  extends MappingSpec<Page<T>, PageDto<D>> {
                ValidatedPrism<D, T> items();
              }

              public record Catalog(Page<@Nullable Code> page) {}

              public record CatalogDto(PageDto<String> page) {}

              @GenerateMapping
              public interface CatalogMapping extends MappingSpec<Catalog, CatalogDto> {
                default ValidatedPrism<String, Code> page() { return Codes.CODE; }
              }

              // A mix-in's leaf converts the spec's T as well: one generic over the pair, and one
              // declared to return its type variable.
              public interface RackLeaf<X extends @Nullable Object, Y extends @Nullable Object> {
                ValidatedPrism<Y, X> items();
              }

              public record Rack<T extends @Nullable Object>(List<T> items) {}

              public record RackDto<D extends @Nullable Object>(List<D> items) {}

              @GenerateMapping
              public interface RackMapping<T extends @Nullable Object, D extends @Nullable Object>
                  extends MappingSpec<Rack<T>, RackDto<D>>, RackLeaf<T, D> {}

              public record Library(Rack<@Nullable Code> rack) {}

              public record LibraryDto(RackDto<String> rack) {}

              @GenerateMapping
              public interface LibraryMapping extends MappingSpec<Library, LibraryDto> {
                default ValidatedPrism<String, Code> rack() { return Codes.CODE; }
              }

              public interface TrayLeaf<P> {
                P items();
              }

              public record Tray<T extends @Nullable Object>(List<T> items) {}

              public record TrayDto<D extends @Nullable Object>(List<D> items) {}

              @GenerateMapping
              public interface TrayMapping<T extends @Nullable Object, D extends @Nullable Object>
                  extends MappingSpec<Tray<T>, TrayDto<D>>, TrayLeaf<ValidatedPrism<D, T>> {}

              public record Kitchen(Tray<@Nullable Code> tray) {}

              public record KitchenDto(TrayDto<String> tray) {}

              @GenerateMapping
              public interface KitchenMapping extends MappingSpec<Kitchen, KitchenDto> {
                default ValidatedPrism<String, Code> tray() { return Codes.CODE; }
              }
            }
            """);

    Compilation compilation = compile(nested);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Outer.one' declares the type argument String @Nullable, and build passes"
                + " it to the spec 'OneMapping', which maps Nested.One<String>. The spec's build"
                + " takes the value as it declares it, and the type argument String of"
                + " Nested.One<String> on the spec 'OneMapping' carries no @Nullable inside"
                + " @NullMarked package 'com.marked', so it would hand on a null its own types rule"
                + " out. Declare that type argument non-null in 'Outer.one', if build never meets a"
                + " null there; or mark the type argument String of Nested.One<String> on the spec"
                + " 'OneMapping' @Nullable, whose own types must then take it.")
        .inFile(nested)
        .onLineContaining("interface OuterMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Many.ones' declares the type argument String @Nullable, and build passes"
                + " its elements to the spec 'OneMapping'");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Maybe.one' declares the type argument String @Nullable, and build passes"
                + " it to the spec 'OneMapping'");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Deep.one' declares the type argument String @Nullable, and build passes"
                + " it to the spec 'RowsMapping', which maps Nested.One<List<String>>.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Wrapper.one' declares the type argument String @Nullable through its"
                + " type argument, and build passes it to the spec 'OneMapping'");
    assertThat(compilation)
        .hadErrorContaining(
            "Declare it non-null in the type argument the spec gives 'Wrapper.one', if build never"
                + " meets a null there;");
    assertThat(compilation)
        .hadErrorContaining(
            "and build passes it to the spec 'BoxMapping', which maps Nested.Box<T>. The spec's"
                + " build takes the value as it declares it, and the bound of its type variable T"
                + " carries no @Nullable inside @NullMarked package 'com.marked', so it would hand on"
                + " a null its own types rule out. Declare that type argument non-null in"
                + " 'Crate.box', if build never meets a null there; or declare T extends @Nullable"
                + " Object on the spec 'BoxMapping', whose own types must then take it.");
    assertThat(compilation)
        .hadErrorContaining(
            "the spec 'LooseBoxMapping' builds Nested.LooseBoxDto<T> with the type argument String"
                + " @Nullable from 'Bin.box', and build copies it to the record component"
                + " 'BinDto.box'. The spec builds the wire as it declares it, its type variables"
                + " standing for the domain's arguments, and the type argument String of"
                + " Nested.LooseBoxDto<String> in 'BinDto.box' carries no @Nullable inside"
                + " @NullMarked package 'com.marked', so build would hand the wire a null its type"
                + " says it cannot hold. Give the two one nullness: mark the type argument String of"
                + " Nested.LooseBoxDto<String> in 'BinDto.box' @Nullable; or declare the type"
                + " argument of 'Bin.box' non-null, if build never meets a null there.");
    assertThat(compilation)
        .hadErrorContaining(
            "the spec 'LooseBoxMapping' builds Nested.LooseBoxDto<T> with the type argument String"
                + " @Nullable from 'Shelf.box'");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Catalog.page' declares the type argument Codes.Code @Nullable, and"
                + " build passes it to the spec 'PageMapping', which maps Nested.Page<T>. The spec's"
                + " build takes the value as it declares it, and its leaf 'items()' converts its"
                + " type variable T, whose build takes no null, so it would hand on a null its own"
                + " types rule out. Declare that type argument non-null in 'Catalog.page', if build"
                + " never meets a null there.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Library.rack' declares the type argument Codes.Code @Nullable, and"
                + " build passes it to the spec 'RackMapping', which maps Nested.Rack<T>. The"
                + " spec's build takes the value as it declares it, and its leaf 'items()' converts"
                + " its type variable T, whose build takes no null");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Kitchen.tray' declares the type argument Codes.Code @Nullable, and"
                + " build passes it to the spec 'TrayMapping', which maps Nested.Tray<T>. The"
                + " spec's build takes the value as it declares it, and its leaf 'items()' converts"
                + " its type variable T, whose build takes no null");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Lifted.one' declares the type argument String @Nullable, and build"
                + " passes the value it holds to the spec 'OneMapping'");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Valued.ones' declares the type argument String @Nullable, and build"
                + " passes its values to the spec 'OneMapping'");
    Assertions.assertThat(compilation.errors()).hasSize(13);
  }

  @Test
  @DisplayName(
      "a nested spec is read as it declares itself: a use annotated non-null, a bound and what lies"
          + " in it, a binding at any depth, and the package its Impl is declared in")
  void aNestedSpecIsReadThroughItsBoundsAndBindings() {
    JavaFileObject bounds =
        JavaFileObjects.forSourceString(
            "com.marked.Bounds",
            """
            package com.marked;

            import java.util.List;
            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.jspecify.annotations.NonNull;
            import org.jspecify.annotations.NullUnmarked;
            import org.jspecify.annotations.Nullable;

            public final class Bounds {
              private Bounds() {}

              public record UseBox<T extends @Nullable Object>(T item) {}

              public record UseBoxDto<T extends @Nullable Object>(T item) {}

              // T's bound takes a null; only its use in the domain is annotated non-null.
              @GenerateMapping
              public interface UseMapping<T extends @Nullable Object>
                  extends MappingSpec<UseBox<@NonNull T>, UseBoxDto<T>> {}

              public record Used(UseBox<@Nullable String> box) {}

              public record UsedDto(UseBoxDto<@Nullable String> box) {}

              @GenerateMapping
              public interface UsedMapping extends MappingSpec<Used, UsedDto> {}

              // The fix rewrites the bound as it is declared.
              public record TextBox<T extends @Nullable Object>(T item) {}

              public record TextBoxDto<T extends @Nullable Object>(T item) {}

              @GenerateMapping
              public interface TextMapping<T extends CharSequence>
                  extends MappingSpec<TextBox<T>, TextBoxDto<T>> {}

              public record NumBox<T extends @Nullable Object>(T item) {}

              public record NumBoxDto<T extends @Nullable Object>(T item) {}

              // Each bound of an intersection must take the null.
              @GenerateMapping
              public interface NumMapping<T extends Number & Comparable<T>>
                  extends MappingSpec<NumBox<T>, NumBoxDto<T>> {}

              public record Nums(NumBox<@Nullable Long> box) {}

              public record NumsDto(NumBoxDto<@Nullable Long> box) {}

              @GenerateMapping
              public interface NumsMapping extends MappingSpec<Nums, NumsDto> {}

              public record Texts(TextBox<@Nullable String> box) {}

              public record TextsDto(TextBoxDto<@Nullable String> box) {}

              @GenerateMapping
              public interface TextsMapping extends MappingSpec<Texts, TextsDto> {}

              // L stands for its whole argument, and its bound rules a null element out.
              public record RowsBox<L extends @Nullable Object>(L item) {}

              public record RowsBoxDto<L extends @Nullable Object>(L item) {}

              @GenerateMapping
              public interface RowsMapping<L extends List<String>>
                  extends MappingSpec<RowsBox<L>, RowsBoxDto<L>> {}

              public record Table(RowsBox<List<@Nullable String>> rows) {}

              public record TableDto(RowsBoxDto<List<@Nullable String>> rows) {}

              @GenerateMapping
              public interface TableMapping extends MappingSpec<Table, TableDto> {}

              // Two type arguments spelt alike are told apart by their place.
              public record Box<T extends @Nullable Object>(T item) {}

              public record MapDto(Map<String, String> item) {}

              @GenerateMapping
              public interface MapBoxMapping extends MappingSpec<Box<Map<String, String>>, MapDto> {}

              public record StrictRows<L extends @Nullable Object>(L item) {}

              public record StrictRowsDto<L extends @Nullable Object>(L item) {}

              @GenerateMapping
              public interface StrictRowsMapping<L extends List<? extends String>>
                  extends MappingSpec<StrictRows<L>, StrictRowsDto<L>> {}

              public record Strict(StrictRows<List<@Nullable String>> rows) {}

              public record StrictDto(StrictRowsDto<List<@Nullable String>> rows) {}

              @GenerateMapping
              public interface StrictMapping extends MappingSpec<Strict, StrictDto> {}

              public record Mapped(Box<Map<String, @Nullable String>> box) {}

              public record MappedDto(MapDto box) {}

              @GenerateMapping
              public interface MappedMapping extends MappingSpec<Mapped, MappedDto> {}

              // The spec is unmarked, but its Impl is declared in a @NullMarked package.
              public record StrDto(@Nullable String item) {}

              @NullUnmarked
              @GenerateMapping
              public interface LooseStrMapping extends MappingSpec<Box<String>, StrDto> {}

              public record Holder(Box<@Nullable String> box) {}

              public record HolderDto(StrDto box) {}

              @GenerateMapping
              public interface HolderMapping extends MappingSpec<Holder, HolderDto> {}

              // T is bound below the top of the wire the nested spec builds.
              public record Loose<T extends @Nullable Object>(List<T> items) {}

              public record WrapDto<X extends @Nullable Object>(X items) {}

              @GenerateMapping
              public interface ListedMapping<T extends @Nullable Object>
                  extends MappingSpec<Loose<T>, WrapDto<List<T>>> {}

              public record Listed(Loose<@Nullable String> box) {}

              public record ListedDto(WrapDto<List<String>> box) {}

              @GenerateMapping
              public interface ListedOuterMapping extends MappingSpec<Listed, ListedDto> {}

              // The site's type argument is the outer spec's.
              public record LooseBox<T extends @Nullable Object>(T item) {}

              public record LooseBoxDto<T extends @Nullable Object>(T item) {}

              @GenerateMapping
              public interface LooseBoxMapping<T extends @Nullable Object>
                  extends MappingSpec<LooseBox<T>, LooseBoxDto<T>> {}

              public record Can(LooseBox<@Nullable String> box) {}

              public record CanDto<S extends @Nullable Object>(LooseBoxDto<S> box) {}

              @GenerateMapping
              public interface CanMapping extends MappingSpec<Can, CanDto<String>> {}

              // A bound that names another class gives the argument nothing.
              public record PlainJarDto<L extends @Nullable Object>(L box) {}

              @GenerateMapping
              public interface PlainJarMapping
                  extends MappingSpec<Can, PlainJarDto<LooseBoxDto<String>>> {}

              // An array argument holds no type argument to read.
              public record ArrDto(String[] item) {}

              @GenerateMapping
              public interface ArrBoxMapping extends MappingSpec<Box<String[]>, ArrDto> {}

              public record Arrays(Box<String[]> box) {}

              public record ArraysDto(ArrDto box) {}

              @GenerateMapping
              public interface ArraysMapping extends MappingSpec<Arrays, ArraysDto> {}
            }
            """);
    // The spec's package is not @NullMarked, so only L's bound rules the null out.
    JavaFileObject jar =
        JavaFileObjects.forSourceString(
            "com.loose.JarMapping",
            """
            package com.loose;

            import com.marked.Bounds.LooseBoxDto;
            import com.marked.Jars.Jar;
            import com.marked.Jars.JarDto;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface JarMapping extends MappingSpec<Jar, JarDto<LooseBoxDto<String>>> {}
            """);
    JavaFileObject jars =
        JavaFileObjects.forSourceString(
            "com.marked.Jars",
            """
            package com.marked;

            import com.marked.Bounds.LooseBox;
            import com.marked.Bounds.LooseBoxDto;
            import org.jspecify.annotations.Nullable;

            public final class Jars {
              private Jars() {}

              public record Jar(LooseBox<@Nullable String> box) {}

              public record JarDto<L extends LooseBoxDto<String>>(L box) {}
            }
            """);

    Compilation compilation = compile(bounds, jars, jar);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Used.box' declares the type argument String @Nullable, and build passes"
                + " it to the spec 'UseMapping', which maps Bounds.UseBox<T>. The spec's build takes"
                + " the value as it declares it, and the type argument T of Bounds.UseBox<T> on the"
                + " spec 'UseMapping' is annotated @NonNull, so it would hand on a null its own"
                + " types rule out. Declare that type argument non-null in 'Used.box', if build"
                + " never meets a null there; or replace @NonNull on the type argument T of"
                + " Bounds.UseBox<T> on the spec 'UseMapping' with @Nullable, whose own types must"
                + " then take it.")
        .inFile(bounds)
        .onLineContaining("interface UsedMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "or declare T extends @Nullable CharSequence on the spec 'TextMapping', whose own"
                + " types must then take it.")
        .inFile(bounds)
        .onLineContaining("interface TextsMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "or declare T extends @Nullable Number & @Nullable Comparable<T> on the spec"
                + " 'NumMapping', whose own types must then take it.")
        .inFile(bounds)
        .onLineContaining("interface NumsMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "and the type argument String of List<String> in the bound of L on the spec"
                + " 'RowsMapping' carries no @Nullable inside @NullMarked package 'com.marked'");
    assertThat(compilation)
        .hadErrorContaining(
            "and the type argument String at position 2 of Map<String, String> in"
                + " Bounds.Box<Map<String, String>> on the spec 'MapBoxMapping' carries no"
                + " @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Holder.box' declares the type argument String @Nullable, and build"
                + " passes it to the spec 'LooseStrMapping'");
    assertThat(compilation)
        .hadErrorContaining(
            "the spec 'ListedMapping' builds Bounds.WrapDto<List<T>> with the type argument String"
                + " @Nullable from 'Listed.box', and build copies it to the record component"
                + " 'ListedDto.box'. The spec builds the wire as it declares it, its type variables"
                + " standing for the domain's arguments, and the type argument String of"
                + " List<String> in 'ListedDto.box' carries no @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "and the type argument String the spec gives 'CanDto.box' carries no @Nullable")
        .inFile(bounds)
        .onLineContaining("interface CanMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "and the type argument String in the bound of the type variable L in 'JarDto.box'"
                + " carries no @Nullable inside @NullMarked package 'com.marked', so build would"
                + " hand the wire a null its type says it cannot hold. Give the two one nullness:"
                + " mark the type argument String in the bound of the type variable L in"
                + " 'JarDto.box' @Nullable;")
        .inFile(jar)
        .onLineContaining("interface JarMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "and the type argument String the spec gives 'PlainJarDto.box' carries no @Nullable")
        .inFile(bounds)
        .onLineContaining("interface PlainJarMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "and the type argument String of List<? extends String> in the bound of L on the spec"
                + " 'StrictRowsMapping' carries no @Nullable")
        .inFile(bounds)
        .onLineContaining("interface StrictMapping");
    Assertions.assertThat(compilation.errors()).hasSize(11);
  }

  @Test
  @DisplayName(
      "what a nested spec builds is read at every level it writes: a use annotated @Nullable, an"
          + " array's elements, and the canonical constructor build hands it to")
  void aNestedSpecsWireIsReadAtEveryLevel() {
    JavaFileObject built =
        JavaFileObjects.forSourceString(
            "com.marked.Built",
            """
            package com.marked;

            import java.util.List;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.jspecify.annotations.Nullable;

            public final class Built {
              private Built() {}

              public record Box<T extends @Nullable Object>(T item) {}

              public record BoxDto<T extends @Nullable Object>(T item) {}

              // The use says what the spec builds, whatever T is bound to.
              @GenerateMapping
              public interface UseMapping<T> extends MappingSpec<Box<T>, BoxDto<@Nullable T>> {}

              public record Used(Box<String> box) {}

              public record UsedDto(BoxDto<String> box) {}

              @GenerateMapping
              public interface UsedMapping extends MappingSpec<Used, UsedDto> {}

              public record Lists(List<String>[] items) {}

              public record ListsDto<T extends @Nullable Object>(T items) {}

              @GenerateMapping
              public interface ListsMapping
                  extends MappingSpec<Lists, ListsDto<List<@Nullable String>[]>> {}

              public record Shelved(Lists lists) {}

              public record ShelvedDto(ListsDto<List<String>[]> lists) {}

              @GenerateMapping
              public interface ShelvedMapping extends MappingSpec<Shelved, ShelvedDto> {}

              public record Names(String[] items) {}

              @GenerateMapping
              public interface NamesMapping extends MappingSpec<Names, ListsDto<@Nullable String[]>> {}

              public record Roll(Names names) {}

              public record RollDto(ListsDto<String[]> names) {}

              @GenerateMapping
              public interface RollMapping extends MappingSpec<Roll, RollDto> {}

              public record Plain(String item) {}

              @GenerateMapping
              public interface PlainMapping extends MappingSpec<Plain, BoxDto<@Nullable String>> {}

              public record Taken(Plain box) {}

              // The component takes the null; the canonical constructor build calls rules it out.
              public record TakenDto(BoxDto<@Nullable String> box) {
                public TakenDto(BoxDto<String> box) {
                  this.box = box;
                }
              }

              @GenerateMapping
              public interface TakenMapping extends MappingSpec<Taken, TakenDto> {}
            }
            """);
    // Outside any @NullMarked scope the parameter says nothing, so the component decides.
    JavaFileObject loose =
        JavaFileObjects.forSourceString(
            "com.loose.Strict",
            """
            package com.loose;

            import com.marked.Built.BoxDto;
            import com.marked.Built.Plain;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.jspecify.annotations.NonNull;

            public final class Strict {
              private Strict() {}

              public record Held(Plain box) {}

              public record HeldDto(BoxDto<@NonNull String> box) {
                public HeldDto(BoxDto<String> box) {
                  this.box = box;
                }
              }

              @GenerateMapping
              public interface HeldMapping extends MappingSpec<Held, HeldDto> {}
            }
            """);

    Compilation compilation = compile(built, loose);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "the spec 'UseMapping' builds Built.BoxDto<T> with the type argument T @Nullable from"
                + " 'Used.box', and build copies it to the record component 'UsedDto.box'.")
        .inFile(built)
        .onLineContaining("interface UsedMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "the spec 'ListsMapping' builds Built.ListsDto<List<String>[]> with the type argument"
                + " String @Nullable from 'Shelved.lists', and build copies it to the record"
                + " component 'ShelvedDto.lists'. The spec builds the wire as it declares it, its"
                + " type variables standing for the domain's arguments, and the type argument String"
                + " of List<String> in 'ShelvedDto.lists' carries no @Nullable")
        .inFile(built)
        .onLineContaining("interface ShelvedMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "the spec 'NamesMapping' builds Built.ListsDto<String[]> with the element type String"
                + " @Nullable from 'Roll.names', and build copies it to the record component"
                + " 'RollDto.names'. The spec builds the wire as it declares it, its type variables"
                + " standing for the domain's arguments, and the element type String of String[]"
                + " in 'RollDto.names' carries no @Nullable")
        .inFile(built)
        .onLineContaining("interface RollMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "the spec 'PlainMapping' builds Built.BoxDto<String> with the type argument String"
                + " @Nullable from 'Taken.box', and build copies it to the record component"
                + " 'TakenDto.box'. The spec builds the wire as it declares it, its type variables"
                + " standing for the domain's arguments, and the type argument String of"
                + " Built.BoxDto<String> in the parameter 'box' of the canonical constructor"
                + " TakenDto(Built.BoxDto<String>) carries no @Nullable inside @NullMarked package"
                + " 'com.marked', so build would hand the wire a null its type says it cannot hold."
                + " Give the two one nullness: mark the type argument String of"
                + " Built.BoxDto<String> in the parameter 'box' of the canonical constructor"
                + " TakenDto(Built.BoxDto<String>) @Nullable;")
        .inFile(built)
        .onLineContaining("interface TakenMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "the spec 'PlainMapping' builds Built.BoxDto<String> with the type argument String"
                + " @Nullable from 'Held.box', and build copies it to the record component"
                + " 'HeldDto.box'. The spec builds the wire as it declares it, its type variables"
                + " standing for the domain's arguments, and the type argument String of"
                + " Built.BoxDto<String> in 'HeldDto.box' is annotated @NonNull")
        .inFile(loose)
        .onLineContaining("interface HeldMapping");
    Assertions.assertThat(compilation.errors()).hasSize(5);
  }

  @Test
  @DisplayName(
      "a lifted map that converts its values passes its keys through, and one that converts its"
          + " keys passes its values, to a bulk build that throws on a null either way")
  void aLiftedMapRefusesTheNullItPassesThrough() {
    JavaFileObject half =
        JavaFileObjects.forSourceString(
            "com.marked.Half",
            """
            package com.marked;

            import com.marked.Codes.Address;
            import com.marked.Codes.AddressDto;
            import com.marked.Codes.Code;
            import java.util.Locale;
            import java.util.Map;
            import java.util.Optional;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MapKey;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.OptionalBridge;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.Nullable;

            public final class Half {
              private Half() {}

              public record Coded(Map<@Nullable String, Code> codes) {}

              public record CodedDto(Map<@Nullable String, String> codes) {}

              @GenerateMapping
              public interface CodedMapping extends MappingSpec<Coded, CodedDto> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              @GenerateMapping
              public interface AddressMapping extends MappingSpec<Address, AddressDto> {}

              public record Stops(Map<@Nullable String, Address> stops) {}

              public record StopsDto(Map<String, AddressDto> stops) {}

              @GenerateMapping
              public interface StopsMapping extends MappingSpec<Stops, StopsDto> {}

              public static final ValidatedPrism<String, Locale> LOCALE =
                  ValidatedPrism.of(
                      raw -> Validated.validNel(Locale.forLanguageTag(raw)), Locale::toLanguageTag);

              public record Leads(Map<Locale, @Nullable Integer> leads) {}

              public record LeadsDto(Map<String, @Nullable Integer> leads) {}

              @GenerateMapping
              public interface LeadsMapping extends MappingSpec<Leads, LeadsDto> {
                @MapKey("leads")
                default ValidatedPrism<String, Locale> leadLocale() { return LOCALE; }
              }

              public record Keyed<K extends @Nullable Object>(Map<K, Code> codes) {}

              @GenerateMapping
              public interface KeyedMapping extends MappingSpec<Keyed<@Nullable String>, CodedDto> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              public record Kept(Optional<Map<@Nullable String, Code>> codes) {}

              public record KeptDto(@Nullable Map<String, String> codes) {}

              @GenerateMapping
              public interface KeptMapping extends MappingSpec<Kept, KeptDto> {
                @OptionalBridge
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }
            }
            """);

    Compilation compilation = compile(half);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Coded.codes' declares its keys @Nullable, and build passes them through"
                + " as it converts the values through the leaf 'codes()'. A lifted map is built"
                + " entry by entry, and that build takes no null key either, so build would throw on"
                + " a null one. parse never reads one either, since it refuses a null key. Declare"
                + " the keys of 'Coded.codes' non-null, if build never meets a null one.")
        .inFile(half)
        .onLineContaining("interface CodedMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Stops.stops' declares its keys @Nullable, and build passes them through"
                + " as it converts the values through the spec 'AddressMapping'.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Leads.leads' declares its values @Nullable, and build passes them"
                + " through as it converts the keys through the leaf 'leadLocale()'. A lifted"
                + " map is built entry by entry, and that build takes no null value either, so build"
                + " would throw on a null one. parse never reads one either, since it refuses a null"
                + " value. Declare the values of 'Leads.leads' non-null, if build never meets a null"
                + " one.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Keyed.codes' declares its keys @Nullable through its type argument, and"
                + " build passes them through as it converts the values through the leaf"
                + " 'codes()'.");
    assertThat(compilation)
        .hadErrorContaining(
            "Declare them non-null in the type argument the spec gives 'Keyed.codes', if build"
                + " never meets a null one.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Kept.codes' declares its value's keys @Nullable, and build passes them"
                + " through as it converts the values through the leaf 'codes()'.");
    Assertions.assertThat(compilation.errors()).hasSize(5);
  }

  @Test
  @DisplayName(
      "a record's canonical constructor is a site of its own: its parameter decides a level it"
          + " says anything at, in a mapping and in a plain merge")
  void aCanonicalConstructorsParameterIsASite() {
    JavaFileObject loose =
        JavaFileObjects.forSourceString(
            "com.loose.Params",
            """
            package com.loose;

            import java.util.List;
            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.GenerateMerge;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.jspecify.annotations.NonNull;
            import org.jspecify.annotations.Nullable;

            public final class Params {
              private Params() {}

              public record Holed(List<@Nullable String> tags) {}

              // The component says nothing, and the parameter rules a null element out.
              public record HoledDto(List<String> tags) {
                public HoledDto(List<@NonNull String> tags) {
                  this.tags = tags;
                }
              }

              @GenerateMapping
              public interface HoledMapping extends MappingSpec<Holed, HoledDto> {}

              public record Grid(List<Map<@Nullable String, String>> rows) {}

              public record GridDto(List<Map<String, String>> rows) {
                public GridDto(List<Map<@NonNull String, String>> rows) {
                  this.rows = rows;
                }
              }

              @GenerateMapping
              public interface GridMapping extends MappingSpec<Grid, GridDto> {}

              public record Label(String name) {}

              public record Tagged(List<String> tags, String name) {
                public Tagged(List<@NonNull String> tags, String name) {
                  this.tags = tags;
                  this.name = name;
                }
              }

              @GenerateMerge
              public interface TaggedAssembly {
                Tagged merge(Holed holed, Label label);
              }
            }
            """);
    JavaFileObject marked =
        JavaFileObjects.forSourceString(
            "com.marked.Takes",
            """
            package com.marked;

            import java.util.List;
            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.GenerateMerge;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.jspecify.annotations.Nullable;

            public final class Takes {
              private Takes() {}

              public record Keyed(Map<@Nullable String, List<@Nullable String>> rows) {}

              // The parameter takes the null keys and elements its component rules out.
              public record KeyedDto(Map<String, List<String>> rows) {
                public KeyedDto(Map<@Nullable String, List<@Nullable String>> rows) {
                  this.rows = rows;
                }
              }

              @GenerateMapping
              public interface KeyedMapping extends MappingSpec<Keyed, KeyedDto> {}

              public record Names(List<@Nullable String> names) {}

              public record Label(String name) {}

              public record Merged(List<String> names, String name) {
                public Merged(List<@Nullable String> names, String name) {
                  this.names = names.stream().filter(n -> n != null).toList();
                  this.name = name;
                }
              }

              @GenerateMerge
              public interface MergedAssembly {
                Merged merge(Names names, Label label);
              }
            }
            """);

    Compilation compilation = compile(loose, marked);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Holed.tags' declares its elements @Nullable, and build copies them to"
                + " the record component 'HoledDto.tags'. A same-typed component is copied as it is,"
                + " null elements included, and the element type String in the parameter 'tags' of"
                + " the canonical constructor HoledDto(List<String>) is annotated @NonNull, so build"
                + " would hand the wire a null its type says it cannot hold. Give the two one"
                + " nullness: replace @NonNull on the element type String in the parameter 'tags' of"
                + " the canonical constructor HoledDto(List<String>) with @Nullable;")
        .inFile(loose)
        .onLineContaining("interface HoledMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "and the key type String at the elements' keys of the parameter 'rows' of the"
                + " canonical constructor GridDto(List<Map<String, String>>) is annotated"
                + " @NonNull");
    assertThat(compilation)
        .hadErrorContaining(
            "source component 'Holed.tags' declares its elements @Nullable, and the merge copies"
                + " them to the record component 'Tagged.tags'. A plain merge copies each source"
                + " value as it is, null elements included, and the element type String in the"
                + " parameter 'tags' of the canonical constructor Tagged(List<String>, String) is"
                + " annotated @NonNull");
    Assertions.assertThat(compilation.errors()).hasSize(3);
    assertThat(compile(marked)).succeededWithoutWarnings();
  }

  @Test
  @DisplayName(
      "a Lombok @Singular collection that throws on a null is refused, read on the builder build"
          + " calls, and one that skips it, or a hand-written one that says nothing, is not")
  void aSingularCollectionThatThrowsIsRefused() {
    JavaFileObject singular =
        JavaFileObjects.forSourceString(
            "com.marked.Singulars",
            """
            package com.marked;

            import com.marked.Codes.Code;
            import java.util.Collection;
            import java.util.List;
            import lombok.Builder;
            import lombok.Getter;
            import lombok.Singular;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.NullUnmarked;
            import org.jspecify.annotations.Nullable;

            public final class Singulars {
              private Singulars() {}

              public record Tagged(@Nullable List<String> tags) {}

              @Builder
              @Getter
              public static final class Card {
                @Singular private final List<String> tags;
              }

              @GenerateMapping
              public interface CardMapping extends MappingSpec<Tagged, Card> {}

              @Builder
              @Getter
              public static final class Named {
                @Singular("tag") private final List<String> tags;
              }

              @GenerateMapping
              public interface NamedMapping extends MappingSpec<Tagged, Named> {}

              @Builder
              @Getter
              public static final class Kept {
                @Singular(ignoreNullCollections = false) private final List<String> tags;
              }

              @GenerateMapping
              public interface KeptMapping extends MappingSpec<Tagged, Kept> {}

              @Builder
              @Getter
              public static final class Skipping {
                @Singular(ignoreNullCollections = true) private final List<String> tags;
              }

              @GenerateMapping
              public interface SkippingMapping extends MappingSpec<Tagged, Skipping> {}

              // Lombok reads the annotation on the parameter of a constructor carrying @Builder.
              @Getter
              public static final class Ticket {
                private final List<String> tags;

                @Builder
                Ticket(@Singular(ignoreNullCollections = true) List<String> tags) {
                  this.tags = tags;
                }
              }

              @GenerateMapping
              public interface TicketMapping extends MappingSpec<Tagged, Ticket> {}

              // Lombok reads the constructor's parameter, not the field of the same name.
              @Getter
              public static final class Split {
                @Singular(ignoreNullCollections = true) private final List<String> tags;

                @Builder
                Split(@Singular List<String> tags) {
                  this.tags = tags;
                }
              }

              @GenerateMapping
              public interface SplitMapping extends MappingSpec<Tagged, Split> {}

              public record Coding(@Nullable List<Code> tags) {}

              // A leaf's buildAll throws before the builder could skip the null.
              @GenerateMapping
              public interface CodingMapping extends MappingSpec<Coding, Skipping> {
                default ValidatedPrism<String, Code> tags() { return Codes.CODE; }
              }

              public record Holed(List<@Nullable String> tags) {}

              @GenerateMapping
              public interface HoledMapping extends MappingSpec<Holed, Card> {}

              @Builder
              @Getter
              public static final class HoledCard {
                @Singular private final List<@Nullable String> tags;
              }

              @GenerateMapping
              public interface HoledCardMapping extends MappingSpec<Holed, HoledCard> {}

              // A builder written by hand in Lombok's shape, read as any builder setter is.
              public static final class Bundle {
                private final List<String> tags;

                private Bundle(List<String> tags) { this.tags = tags; }

                public List<String> getTags() { return tags; }
                public static Builder builder() { return new Builder(); }

                public static final class Builder {
                  private List<String> tags = List.of();

                  public Builder tag(String tag) { return this; }
                  public Builder tags(Collection<? extends String> tags) {
                    this.tags = List.copyOf(tags);
                    return this;
                  }
                  public Builder clearTags() { return this; }
                  public Bundle build() { return new Bundle(tags); }
                }
              }

              @GenerateMapping
              public interface BundleMapping extends MappingSpec<Tagged, Bundle> {}

              @NullUnmarked
              public static final class LooseBundle {
                private final List<String> tags;

                private LooseBundle(List<String> tags) { this.tags = tags; }

                public List<String> getTags() { return tags; }
                public static Builder builder() { return new Builder(); }

                public static final class Builder {
                  private List<String> tags = List.of();

                  public Builder tag(String tag) { return this; }
                  public Builder tags(Collection<? extends String> tags) {
                    this.tags = tags == null ? List.of() : List.copyOf(tags);
                    return this;
                  }
                  public Builder clearTags() { return this; }
                  public LooseBundle build() { return new LooseBundle(tags); }
                }
              }

              @GenerateMapping
              public interface LooseBundleMapping extends MappingSpec<Tagged, LooseBundle> {}

              // build calls builder(), whose builder throws; another builder's skips.
              @Getter
              public static final class Twice {
                private final List<String> tags;

                @Builder
                Twice(@Singular List<String> tags) {
                  this.tags = tags;
                }

                @Builder(builderMethodName = "skipping", builderClassName = "SkippingBuilder")
                Twice(@Singular(ignoreNullCollections = true) List<String> tags, int extra) {
                  this.tags = tags;
                }
              }

              @GenerateMapping
              public interface TwiceMapping extends MappingSpec<Tagged, Twice> {}

              // build calls builder(), whose builder skips; another builder's throws.
              @Getter
              public static final class Lenient {
                private final List<String> tags;

                @Builder
                Lenient(@Singular(ignoreNullCollections = true) List<String> tags) {
                  this.tags = tags;
                }

                @Builder(builderMethodName = "strict", builderClassName = "StrictBuilder")
                Lenient(@Singular List<String> tags, int extra) {
                  this.tags = tags;
                }
              }

              @GenerateMapping
              public interface LenientMapping extends MappingSpec<Tagged, Lenient> {}

              // Lombok names a method's builder after the type it returns.
              @Getter
              public static final class Made {
                private final List<String> tags;

                private Made(List<String> tags) {
                  this.tags = tags;
                }

                @Builder
                static Made make(@Singular List<String> tags) {
                  return new Made(tags);
                }
              }

              @GenerateMapping
              public interface MadeMapping extends MappingSpec<Tagged, Made> {}
            }
            """);

    Compilation compilation = compileWithLombok(singular);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Tagged.tags' is declared @Nullable, and build hands it to the"
                + " @Singular bean property 'tags'. Lombok writes tags(Collection<? extends String>)"
                + " to throw on a null collection, so build would throw on it. parse never reads a"
                + " null into it either, since it refuses any null that no Optional bridges. Declare"
                + " 'Tagged.tags' non-null, if build never meets it null; or add"
                + " ignoreNullCollections = true to the @Singular on 'tags', so the builder skips a"
                + " null and builds an empty collection.")
        .inFile(singular)
        .onLineContaining("interface CardMapping");
    assertThat(compilation)
        .hadErrorContaining("build hands it to the @Singular bean property 'tags'.")
        .inFile(singular)
        .onLineContaining("interface NamedMapping");
    assertThat(compilation)
        .hadErrorContaining("build hands it to the @Singular bean property 'tags'.")
        .inFile(singular)
        .onLineContaining("interface KeptMapping");
    assertThat(compilation)
        .hadErrorContaining("build hands it to the @Singular bean property 'tags'.")
        .inFile(singular)
        .onLineContaining("interface SplitMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Coding.tags' is declared @Nullable, and build converts its elements"
                + " through the leaf 'tags()'.")
        .inFile(singular)
        .onLineContaining("interface CodingMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Holed.tags' declares its elements @Nullable, and build copies them to"
                + " the bean property 'tags', through tags(Collection<? extends String>), which"
                + " Lombok writes from the field 'Card.tags'. A same-typed component is copied as"
                + " it is, null elements included, and the element type String in the field"
                + " 'Card.tags' carries no @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "build copies it to the bean property 'tags', whose builder setter tags(Collection<?"
                + " extends String>) is declared non-null.")
        .inFile(singular)
        .onLineContaining("interface BundleMapping");
    assertThat(compilation)
        .hadErrorContaining("build hands it to the @Singular bean property 'tags'.")
        .inFile(singular)
        .onLineContaining("interface TwiceMapping");
    assertThat(compilation)
        .hadErrorContaining("build hands it to the @Singular bean property 'tags'.")
        .inFile(singular)
        .onLineContaining("interface MadeMapping");
    Assertions.assertThat(compilation.errors()).hasSize(9);
  }

  @Test
  @DisplayName(
      "a protobuf-java setter throws on a null, so a nullable component, element or oneof copied"
          + " into a message is refused, and an update that only parses is not")
  void aMessageSetterIsRefusedANull() {
    JavaFileObject messages =
        JavaFileObjects.forSourceString(
            "com.marked.Messages",
            """
            package com.marked;

            import com.example.proto.NamesProto;
            import com.google.protobuf.Any;
            import com.google.protobuf.FieldMask;
            import com.google.protobuf.Option;
            import com.google.protobuf.Struct;
            import com.google.protobuf.Value;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.UpdateSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.Nullable;

            public final class Messages {
              private Messages() {}

              public record Valued(String name, @Nullable Any value) {}

              @GenerateMapping
              public interface ValuedMapping extends MappingSpec<Valued, Option> {}

              public record Named(@Nullable String name, Optional<Any> value) {}

              public record Labelled(Codes.@Nullable Code name, Optional<Any> value) {}

              @GenerateMapping
              public interface LabelledMapping extends MappingSpec<Labelled, Option> {
                default ValidatedPrism<String, Codes.Code> name() { return Codes.CODE; }
              }

              @GenerateMapping
              public interface NamedMapping extends MappingSpec<Named, Option> {}

              public record Masked(@Nullable List<String> paths) {}

              @GenerateMapping
              public interface MaskedMapping extends MappingSpec<Masked, FieldMask> {}

              public record Holed(List<@Nullable String> paths) {}

              @GenerateMapping
              public interface HoledMapping extends MappingSpec<Holed, FieldMask> {}

              public sealed interface Mode {
                record KInt(String value) implements Mode {}

                record Plain(String value) implements Mode {}
              }

              public record Odd(String foo2Bar, String xRay, String uRL, @Nullable Mode mode) {}

              @GenerateMapping
              public interface OddMapping extends MappingSpec<Odd, NamesProto.Oddity> {}

              public record Maybe(
                  String foo2Bar, String xRay, String uRL, @Nullable Optional<Mode> mode) {}

              @GenerateMapping
              public interface MaybeMapping extends MappingSpec<Maybe, NamesProto.Oddity> {}

              // A projection reads the oneof through each of its members.
              public record Wide(
                  String foo2Bar, String xRay, String uRL, @Nullable Mode mode, String extra) {}

              @GenerateMapping
              public interface WideMapping extends MappingSpec<Wide, NamesProto.Oddity> {}

              // A variant's one component fills its member as it is.
              public sealed interface Loose {
                record KInt(@Nullable String value) implements Loose {}

                record Plain(String value) implements Loose {}
              }

              public record Odds(String foo2Bar, String xRay, String uRL, Loose mode) {}

              @GenerateMapping
              public interface OddsMapping extends MappingSpec<Odds, NamesProto.Oddity> {}

              // An update only parses, so it hands no setter anything.
              @GenerateMapping
              public interface OddPatch extends UpdateSpec<Odd, NamesProto.Oddity> {}

              // A map field's builder throws on a null key, as on a null value.
              public record Doc(Map<@Nullable String, Value> fields) {}

              @GenerateMapping
              public interface DocMapping extends MappingSpec<Doc, Struct> {}
            }
            """);

    Compilation compilation =
        javac().withProcessors(new MappingProcessor()).compile(MARKED_PACKAGE, CODES, messages);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Valued.value' is declared @Nullable, and build hands it to the field"
                + " 'value' of the protobuf-java message 'Option'. A protobuf-java builder's setter"
                + " throws on a null, so build would throw on it. parse never reads a null into it"
                + " either, since it refuses any null that no Optional bridges. As 'Valued.value'"
                + " may be absent, declare it Optional<Any>, so build leaves the field unset for an"
                + " empty one and parse reads it back as empty; or declare 'Valued.value' non-null,"
                + " if build never meets it null.");
    assertThat(compilation)
        .hadErrorContaining(
            "As 'Named.name' may be absent, declare it Optional<String>, and the field optional in"
                + " its .proto file, which gives it hasName(), so build leaves the field unset for an"
                + " empty one and parse reads it back as empty; or declare 'Named.name' non-null, if"
                + " build never meets it null.");
    assertThat(compilation)
        .hadErrorContaining(
            "bridges. Declare 'Masked.paths' non-null, if build never meets it null.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Holed.paths' declares its elements @Nullable, and build copies them to"
                + " the field 'paths' of the protobuf-java message 'FieldMask'. A protobuf-java"
                + " builder throws on a null element, so build would throw on one. parse never"
                + " reads one either, since it refuses a null element. Declare the elements of"
                + " 'Holed.paths' non-null, if build never meets a null one.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Odd.mode' is declared @Nullable, and build writes it to the oneof 'mode'"
                + " of the protobuf-java message 'Oddity'. build sets the member its variant names,"
                + " so it would throw on a null, which names none. parse never reads a null into it"
                + " either, since it refuses any null that no Optional bridges. As 'Odd.mode' may be"
                + " absent, declare it Optional<Messages.Mode>, which sets no member when empty; or declare"
                + " 'Odd.mode' non-null, if build never meets it null.")
        .inFile(messages)
        .onLineContaining("interface OddMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Maybe.mode' is declared @Nullable, and build maps the value it holds.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Wide.mode' is declared @Nullable, and build writes it to the oneof"
                + " 'mode'")
        .inFile(messages)
        .onLineContaining("interface WideMapping");
    assertThat(compilation)
        .hadErrorContaining(
            "variant component 'KInt.value' is declared @Nullable, and build copies it to the field"
                + " 'kInt' of the protobuf-java message 'Oddity'. A protobuf-java builder's setter"
                + " throws on a null, so build would throw on it. Declare 'KInt.value' non-null: a"
                + " set member always holds a value, and an empty Optional<Messages.Loose> is how a"
                + " oneof says no member is set.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Labelled.name' is declared @Nullable, and build converts it through the"
                + " leaf 'name()'. A leaf's or a nested spec's build takes no null, and build would"
                + " throw on it. parse never reads a null into it either, since it refuses any null"
                + " that no Optional bridges. As 'Labelled.name' may be absent, declare it"
                + " Optional<Codes.Code>, and the field optional in its .proto file, which gives it"
                + " hasName(), so build leaves the field unset for an empty one and parse reads it"
                + " back as empty; or declare 'Labelled.name' non-null, if build never meets it"
                + " null.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Doc.fields' declares its keys @Nullable, and build copies them to the"
                + " field 'fields' of the protobuf-java message 'Struct'. A protobuf-java builder"
                + " throws on a null key, so build would throw on one. parse never reads one either,"
                + " since it refuses a null key. Declare the keys of 'Doc.fields' non-null, if build"
                + " never meets a null one.");
    Assertions.assertThat(compilation.errors()).hasSize(10);
  }

  @Test
  @DisplayName(
      "a class read from a class file is read as it is kept there: a domain record by its"
          + " component, and a Lombok bean, which keeps no @Singular, as one that may take the null")
  void aClassFileIsReadAsItIsKept() throws IOException {
    Compilation upstream =
        javac()
            .withProcessors(LombokInteropTest.lombok())
            .compile(
                JavaFileObjects.forSourceString(
                    "com.upstream.Kept",
                    """
                    package com.upstream;

                    import java.util.List;
                    import lombok.Builder;
                    import lombok.Getter;
                    import lombok.Singular;
                    import org.jspecify.annotations.Nullable;

                    public final class Kept {
                      private Kept() {}

                      public record Code(String value) {}

                      public record Coded(@Nullable Code code) {}

                      @Builder
                      @Getter
                      public static final class Skipping {
                        @Singular(ignoreNullCollections = true) private final List<String> tags;
                      }
                    }
                    """));
    assertThat(upstream).succeeded();
    Path classes = classDirectory(upstream, tmp.resolve("upstream"));

    JavaFileObject downstream =
        JavaFileObjects.forSourceString(
            "com.marked.Downstream",
            """
            package com.marked;

            import com.upstream.Kept;
            import java.util.List;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.Nullable;

            public final class Downstream {
              private Downstream() {}

              public record CodedDto(@Nullable String code) {}

              @GenerateMapping
              public interface CodedMapping extends MappingSpec<Kept.Coded, CodedDto> {
                default ValidatedPrism<String, Kept.Code> code() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new Kept.Code(raw)), Kept.Code::value);
                }
              }

              public record Tagged(@Nullable List<String> tags) {}

              @GenerateMapping
              public interface SkippingMapping extends MappingSpec<Tagged, Kept.Skipping> {}
            }
            """);
    Compilation compilation =
        javac()
            .withProcessors(new MappingProcessor())
            .withClasspath(classpathWith(classes))
            .compile(MARKED_PACKAGE, downstream);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Coded.code' is declared @Nullable, and build converts it through the"
                + " leaf 'code()'.")
        .inFile(downstream)
        .onLineContaining("interface CodedMapping");
    Assertions.assertThat(compilation.errors()).hasSize(1);
  }

  @Test
  @DisplayName(
      "a side that says nothing, a site that takes the null, a bridge, a fallible merge and a"
          + " mapping that only parses compile as before")
  void everyOtherShapeCompiles() {
    JavaFileObject accepted =
        JavaFileObjects.forSourceString(
            "com.marked.Accepted",
            """
            package com.marked;

            import com.marked.Codes.Address;
            import com.marked.Codes.AddressDto;
            import com.marked.Codes.Code;
            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.GenerateMerge;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.OptionalBridge;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.NullUnmarked;
            import org.jspecify.annotations.Nullable;

            public final class Accepted {
              private Accepted() {}

              @GenerateMapping
              public interface AddressMapping extends MappingSpec<Address, AddressDto> {}

              // The bridge the refusals offer: absence held as an Optional.
              public record Order(Optional<Code> code, Optional<Address> address) {}

              public record OrderDto(@Nullable String code, @Nullable AddressDto address) {}

              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Order, OrderDto> {
                @OptionalBridge
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }

                @OptionalBridge
                Optional<Address> address();
              }

              public record Holed(List<@Nullable String> tags) {}

              public record HoledDto(List<@Nullable String> tags) {}

              @GenerateMapping
              public interface HoledMapping extends MappingSpec<Holed, HoledDto> {}

              // A wire whose elements may be null takes a domain's that may not.
              public record Plain(List<String> tags) {}

              @GenerateMapping
              public interface WideningMapping extends MappingSpec<Plain, HoledDto> {}

              @NullUnmarked
              public record LooseDto(List<String> tags) {}

              @GenerateMapping
              public interface LooseMapping extends MappingSpec<Holed, LooseDto> {}

              public record ItemsDto<T extends @Nullable Object>(List<T> tags) {}

              @GenerateMapping
              public interface ItemsMapping
                  extends MappingSpec<Holed, ItemsDto<@Nullable String>> {}

              public record One<T extends @Nullable Object>(T value) {}

              public record ValueDto(String value) {}

              @GenerateMapping
              public interface OneMapping extends MappingSpec<One<String>, ValueDto> {}

              public record Absent(@Nullable String value) {}

              public record ItemDto<T extends @Nullable Object>(T value) {}

              @GenerateMapping
              public interface ItemMapping
                  extends MappingSpec<Absent, ItemDto<@Nullable String>> {}

              // The canonical constructor build calls takes the null.
              public record TakenDto<T extends @Nullable Object>(T value) {
                public TakenDto(@Nullable T value) {
                  this.value = value;
                }
              }

              @GenerateMapping
              public interface TakenMapping extends MappingSpec<Absent, TakenDto<String>> {}

              // A declaration annotation says the component may hold one.
              public record LegacyDto<T extends @Nullable Object>(@com.legacy.Nullable T value) {}

              @GenerateMapping
              public interface LegacyMapping extends MappingSpec<Absent, LegacyDto<String>> {}

              public static class NickBean {
                private @Nullable String nick;

                public @Nullable String getNick() { return nick; }
                public void setNick(@Nullable String nick) { this.nick = nick; }
              }

              public record Nicked(@Nullable String nick) {}

              @GenerateMapping
              public interface NickMapping extends MappingSpec<Nicked, NickBean> {}

              // An explicit canonical constructor takes what its component's elements rule out.
              public record Takes(List<String> tags) {
                public Takes(List<@Nullable String> tags) {
                  this.tags = tags.stream().filter(java.util.Objects::nonNull).toList();
                }
              }

              public record Gappy(List<@Nullable String> tags) {}

              @GenerateMapping
              public interface TakesMapping extends MappingSpec<Gappy, Takes> {}

              // build calls the accessor, which answers a value where the field holds null.
              public record Defaulted(@Nullable Code code) {
                @Override
                public Code code() {
                  return code == null ? new Code("none") : code;
                }
              }

              public record DefaultedDto(String code) {}

              @GenerateMapping
              public interface DefaultedMapping extends MappingSpec<Defaulted, DefaultedDto> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              // What a subclass gives an inherited setter's T cannot be read, so it says nothing.
              public static class Base<T extends @Nullable Object> {
                private List<T> tags = List.of();

                public List<T> getTags() { return tags; }
                public void setTags(List<T> tags) { this.tags = tags; }
              }

              public static class OpenBag extends Base<@Nullable String> {}

              @GenerateMapping
              public interface OpenBagMapping extends MappingSpec<Gappy, OpenBag> {}

              // Getters only: parsed, and never built.
              public static class CodedView {
                public @Nullable String getCode() { return null; }
              }

              public record Coded(@Nullable Code code) {}

              @GenerateMapping
              public interface CodedViewMapping extends MappingSpec<Coded, CodedView> {
                default ValidatedPrism<String, Code> code() { return Codes.CODE; }
              }

              public record Names(List<@Nullable String> names, String code) {}

              public record Labels(String label) {}

              public record Checked(List<String> names, String code, String label) {}

              // A fallible merge scans what it copies, so a null element is a failure.
              @GenerateMerge
              public interface CheckedAssembly {
                Validated<NonEmptyList<FieldError>, Checked> merge(Names names, Labels labels);

                default ValidatedPrism<String, String> code() {
                  return ValidatedPrism.of(Validated::validNel, code -> code);
                }
              }

              // build calls the accessor, which leaves the component's gaps out.
              public record Quiet(List<@Nullable String> tags) {
                @Override
                public List<String> tags() {
                  return tags.stream().filter(java.util.Objects::nonNull).toList();
                }
              }

              public record QuietDto(List<String> tags) {}

              @GenerateMapping
              public interface QuietMapping extends MappingSpec<Quiet, QuietDto> {}

              public record Hushed(List<String> tags, String label) {}

              @GenerateMerge
              public interface HushedAssembly {
                Hushed merge(Quiet quiet, Labels labels);
              }

              public record Box(String item) {}

              public record BoxDto<T extends @Nullable Object>(T item) {}

              @GenerateMapping
              public interface BoxMapping extends MappingSpec<Box, BoxDto<@Nullable String>> {}

              // The canonical constructor build calls takes the null its component rules out.
              public record Boxed(Box box) {}

              public record BoxedDto(BoxDto<String> box) {
                public BoxedDto(BoxDto<@Nullable String> box) {
                  this.box = new BoxDto<>(box.item() == null ? "" : box.item());
                }
              }

              @GenerateMapping
              public interface BoxedMapping extends MappingSpec<Boxed, BoxedDto> {}

              // A primitive array holds no null.
              public record Ints(int[] items) {}

              public record IntsDto<T extends @Nullable Object>(T items) {}

              @GenerateMapping
              public interface IntsMapping extends MappingSpec<Ints, IntsDto<int[]>> {}

              public record Counted(Ints ints) {}

              public record CountedDto(IntsDto<int[]> ints) {}

              @GenerateMapping
              public interface CountedMapping extends MappingSpec<Counted, CountedDto> {}
            }
            """);
    // A bound that is raw, holds a wildcard, or names another class than the argument gives
    // nothing to read at a level below it.
    JavaFileObject bounds =
        JavaFileObjects.forSourceString(
            "com.marked.Unread",
            """
            package com.marked;

            import com.marked.Codes.Code;
            import java.util.ArrayList;
            import java.util.List;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.jspecify.annotations.Nullable;

            public final class Unread {
              private Unread() {}

              public record Box<T extends @Nullable Object>(T value) {}

              public record BoxDto<T extends @Nullable Object>(T value) {}

              @GenerateMapping
              public interface BoxMapping<T extends @Nullable Object>
                  extends MappingSpec<Box<T>, BoxDto<T>> {}

              @SuppressWarnings("rawtypes")
              public record RawCodes<L extends List>(L codes) {}

              public record CodesDto(List<String> codes) {}

              @GenerateMapping
              public interface RawCodesMapping extends MappingSpec<RawCodes<List<Code>>, CodesDto> {
                default ValidatedPrism<String, Code> codes() { return Codes.CODE; }
              }

              @SuppressWarnings("rawtypes")
              public record RawBoxes<L extends List>(L boxes) {}

              public record BoxesDto(List<BoxDto<String>> boxes) {}

              @GenerateMapping
              public interface RawBoxesMapping
                  extends MappingSpec<RawBoxes<List<Box<String>>>, BoxesDto> {}

              public record AnyBoxes<L extends List<?>>(L boxes) {}

              @GenerateMapping
              public interface AnyBoxesMapping
                  extends MappingSpec<AnyBoxes<List<Box<String>>>, BoxesDto> {}

              public record SomeBoxes<L extends List<? extends Box<String>>>(L boxes) {}

              @GenerateMapping
              public interface SomeBoxesMapping
                  extends MappingSpec<SomeBoxes<List<Box<String>>>, BoxesDto> {}

              public record Shelf(List<Box<String>> boxes) {}

              public record ShelfDto<L extends List<? extends BoxDto<String>>>(L boxes) {}

              @GenerateMapping
              public interface ShelfMapping
                  extends MappingSpec<Shelf, ShelfDto<List<BoxDto<String>>>> {}

              // Named's own argument says nothing of the strings it holds.
              @SuppressWarnings("serial")
              public static class Named<X extends @Nullable Object> extends ArrayList<String> {}

              public record Rows<L extends List<String>>(L rows) {}

              public record RowsDto<L extends List<String>>(L rows) {}

              @GenerateMapping
              public interface RowsMapping<L extends List<String>>
                  extends MappingSpec<Rows<L>, RowsDto<L>> {}

              public record Sheet(Rows<Named<@Nullable Integer>> rows) {}

              public record SheetDto(RowsDto<Named<@Nullable Integer>> rows) {}

              @GenerateMapping
              public interface SheetMapping extends MappingSpec<Sheet, SheetDto> {}

              // A wildcard rules out what its extends bound does, and a ? or ? super nothing.
              public record AnyRows<L extends @Nullable Object>(L rows) {}

              public record AnyRowsDto<L extends @Nullable Object>(L rows) {}

              @GenerateMapping
              public interface AnyRowsMapping<L extends List<?>>
                  extends MappingSpec<AnyRows<L>, AnyRowsDto<L>> {}

              public record AnySheet(AnyRows<List<@Nullable String>> rows) {}

              public record AnySheetDto(AnyRowsDto<List<@Nullable String>> rows) {}

              @GenerateMapping
              public interface AnySheetMapping extends MappingSpec<AnySheet, AnySheetDto> {}

              public record SuperRows<L extends @Nullable Object>(L rows) {}

              public record SuperRowsDto<L extends @Nullable Object>(L rows) {}

              @GenerateMapping
              public interface SuperRowsMapping<L extends List<? super String>>
                  extends MappingSpec<SuperRows<L>, SuperRowsDto<L>> {}

              public record SuperSheet(SuperRows<List<@Nullable String>> rows) {}

              public record SuperSheetDto(SuperRowsDto<List<@Nullable String>> rows) {}

              @GenerateMapping
              public interface SuperSheetMapping extends MappingSpec<SuperSheet, SuperSheetDto> {}

              public record LooseRows<L extends @Nullable Object>(L rows) {}

              public record LooseRowsDto<L extends @Nullable Object>(L rows) {}

              @GenerateMapping
              public interface LooseRowsMapping<L extends List<? extends @Nullable String>>
                  extends MappingSpec<LooseRows<L>, LooseRowsDto<L>> {}

              public record LooseSheet(LooseRows<List<@Nullable String>> rows) {}

              public record LooseSheetDto(LooseRowsDto<List<@Nullable String>> rows) {}

              @GenerateMapping
              public interface LooseSheetMapping extends MappingSpec<LooseSheet, LooseSheetDto> {}

              public record Bins(Box<@Nullable String> box) {}

              public record AnyBinsDto<B extends BoxDto<?>>(B box) {}

              @GenerateMapping
              public interface AnyBinsMapping
                  extends MappingSpec<Bins, AnyBinsDto<BoxDto<@Nullable String>>> {}

              public record SuperBinsDto<B extends BoxDto<? super String>>(B box) {}

              @GenerateMapping
              public interface SuperBinsMapping
                  extends MappingSpec<Bins, SuperBinsDto<BoxDto<@Nullable String>>> {}
            }
            """);

    assertThat(compile(accepted, bounds)).succeededWithoutWarnings();
  }
}
