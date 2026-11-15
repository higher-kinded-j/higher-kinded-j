// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A nullness checker holds each type argument to the nullness it declares, so generated code that
 * hands on a value whose type carries a nullable type argument writes that type out where inference
 * would drop it. A {@code fields()} ladder's terminal declares its lambda parameters, and a fresh
 * copy is cast to a type whose type arguments are declared more nullable than the copy's: the site
 * {@code build} or a plain merge hands it to, or the record {@code parse} or a fallible merge
 * assembles. Neither changes what {@code parse} or {@code build} does, and both compile clean under
 * {@code -Xlint:all}. Whether NullAway then accepts the Impl is the nullness suite's {@code
 * MappingNullnessTest}.
 */
@DisplayName("A nullable type argument is written out where inference would drop it")
class NullableTypeArgumentTest {

  private static final String IMPORTS =
      """
      import java.util.List;
      import java.util.Map;
      import java.util.Optional;
      import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
      import org.higherkindedj.hkt.validated.FieldError;
      import org.higherkindedj.hkt.validated.Validated;
      import org.higherkindedj.optics.annotations.Flatten;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.GenerateMerge;
      import org.higherkindedj.optics.annotations.MappingSpec;
      import org.higherkindedj.optics.annotations.OptionalBridge;
      import org.higherkindedj.optics.validated.ValidatedPrism;
      import org.jspecify.annotations.Nullable;
      """;

  private static RuntimeCompilationHelper.CompiledResult compileClean(JavaFileObject... sources) {
    Compilation compilation =
        javac()
            // The companion processor claims the vocabulary annotations the others only read; no
            // processor claims JSpecify's, which is all the processing lint would report.
            .withProcessors(
                new MappingProcessor(), new MergeProcessor(), new CompanionAnnotationProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(sources);
    assertThat(compilation).succeededWithoutWarnings();
    return new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  private static JavaFileObject source(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name, "package com.example;\n\n" + IMPORTS + "\n" + body);
  }

  private static String generated(Compilation compilation, String impl) {
    try {
      return compilation
          .generatedSourceFile("com.example." + impl)
          .orElseThrow(() -> new AssertionError(impl + " was not generated"))
          .getCharContent(false)
          .toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A {@code com.example} record, by its binary name, built through its canonical constructor. */
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

  @SuppressWarnings("unchecked") // the generated parse's result, read reflectively
  private static Validated<NonEmptyList<FieldError>, Object> parse(Object impl, Object wire) {
    return (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", wire);
  }

  @Test
  @DisplayName(
      "a ladder's terminal declares its parameters where a leg's type carries a nullable type"
          + " argument, a primitive boxed, in parse, a flattened group, patch, a fallible merge"
          + " and a chunked ladder, and a copy the domain widens is cast ahead of the ladder")
  void aLadderDeclaresItsParameters() throws ReflectiveOperationException {
    String chunked =
        IntStream.range(0, 15)
            .mapToObj(i -> "List<@Nullable String> c" + i)
            .collect(Collectors.joining(", ", "List<? extends Number> w, ", ", double d"));
    var result =
        compileClean(
            source(
                "Ladders",
                """
                public final class Ladders {
                  private Ladders() {}

                  public record Code(String value) {}

                  public record Holed(
                      List<@Nullable String> tags,
                      List<String @Nullable []> rows,
                      int count,
                      String name) {}

                  public record HoledDto(
                      List<@Nullable String> tags,
                      List<String @Nullable []> rows,
                      int count,
                      String name) {}

                  // The wire's elements are never null, the domain's may be.
                  public record Grown(List<@Nullable String> tags) {}

                  public record GrownDto(List<String> tags) {}

                  @GenerateMapping
                  public interface GrownMapping extends MappingSpec<Grown, GrownDto> {}

                  // A bridged copy keeps the wire's own nullness at the top.
                  public record Nicked(Optional<List<@Nullable String>> nicks) {}

                  public record NickedDto(@Nullable List<String> nicks) {}

                  @GenerateMapping
                  public interface NickedMapping extends MappingSpec<Nicked, NickedDto> {
                    @OptionalBridge
                    Optional<List<@Nullable String>> nicks();
                  }

                  @GenerateMapping
                  public interface HoledMapping extends MappingSpec<Holed, HoledDto> {}

                  public record Inner(List<@Nullable String> tags, String note) {}

                  public record Outer(String id, Inner inner) {}

                  public record OuterDto(String id, List<@Nullable String> tags, String note) {}

                  @GenerateMapping
                  public interface OuterMapping extends MappingSpec<Outer, OuterDto> {
                    @Flatten
                    Inner inner();
                  }

                  // A leaf's write-back can fail, so the projection maps as the validated patch.
                  public record Account(String id, List<@Nullable String> tags, Code code) {}

                  public record AccountView(List<@Nullable String> tags, String code) {}

                  @GenerateMapping
                  public interface AccountViewMapping extends MappingSpec<Account, AccountView> {
                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }

                  public record Names(List<String> names) {}

                  public record Codes(String code) {}

                  public record Merged(List<@Nullable String> names, String code) {}

                  @GenerateMerge
                  public interface MergedAssembly {
                    Validated<NonEmptyList<FieldError>, Merged> merge(Names names, Codes codes);

                    default ValidatedPrism<String, String> code() {
                      return ValidatedPrism.of(Validated::validNel, code -> code);
                    }
                  }

                  public record Wide(%s) {}

                  public record WideDto(%s) {}

                  @GenerateMapping
                  public interface WideMapping extends MappingSpec<Wide, WideDto> {}
                }
                """
                    .formatted(chunked, chunked)));

    String holed = generated(result.compilation(), "LaddersHoledMappingImpl");
    Assertions.assertThat(holed)
        .contains(
            ".apply((List<@Nullable String> tags, List<String @Nullable []> rows, Integer count,"
                + " String name) -> () -> new Ladders.Holed(tags, rows, count, name))");
    Assertions.assertThat(generated(result.compilation(), "LaddersGrownMappingImpl"))
        .contains("var hkj$tags = (List<@Nullable String>) hkj$copyOf(wire.tags());")
        .contains(".apply((List<@Nullable String> tags) -> () -> new Ladders.Grown(tags))");
    Assertions.assertThat(generated(result.compilation(), "LaddersNickedMappingImpl"))
        .contains("var hkj$nicks = (@Nullable List<@Nullable String>) hkj$copyOf(wire.nicks());");
    Assertions.assertThat(generated(result.compilation(), "LaddersOuterMappingImpl"))
        .contains(
            ".apply((List<@Nullable String> tags, String note) -> () -> new Ladders.Inner(tags,"
                + " note))")
        // The outer ladder's legs carry none, so its parameters stay implicit.
        .contains(".apply((id, inner) -> () -> new Ladders.Outer(id, inner))");
    Assertions.assertThat(generated(result.compilation(), "LaddersAccountViewMappingImpl"))
        .contains(".apply((List<@Nullable String> tags, Ladders.Code code) ->");
    Assertions.assertThat(generated(result.compilation(), "LaddersMergedAssemblyImpl"))
        .contains("var hkj$names = (List<@Nullable String>) hkj$copyOf(names.names());")
        .contains(".apply((List<@Nullable String> names_, String code_) ->");
    String nullableStrings =
        IntStream.range(0, 15)
            .mapToObj(i -> "List<@Nullable String>")
            .collect(Collectors.joining(", "));
    Assertions.assertThat(generated(result.compilation(), "LaddersWideMappingImpl"))
        // The chunk's tuple takes its type arguments as written, never a wildcard's capture.
        .contains("-> new Tuple16<List<? extends Number>, " + nullableStrings + ">(v1, v2, v3,")
        .contains("c1_.map((Tuple16<List<? extends Number>, " + nullableStrings + "> t1) ->")
        .contains("(Double t2) ->")
        .contains(".apply((Double v) -> v)");

    Object impl = result.instance("com.example.LaddersHoledMappingImpl");
    Object wire = record(result, "Ladders$HoledDto", List.of("a", "b"), List.of(), 2, "n");
    assertThatValidated(parse(impl, wire))
        .isValid()
        .hasValue(record(result, "Ladders$Holed", List.of("a", "b"), List.of(), 2, "n"));
    Object wide = result.instance("com.example.LaddersWideMappingImpl");
    Object[] values = new Object[17];
    Arrays.fill(values, List.of("x"));
    values[0] = List.of(1);
    values[16] = 1.5;
    assertThatValidated(parse(wide, record(result, "Ladders$WideDto", values)))
        .isValid()
        .hasValue(record(result, "Ladders$Wide", values));
  }

  @Test
  @DisplayName(
      "a ladder whose legs carry no nullable type argument, or name a type variable, keeps its"
          + " parameters implicit")
  void aLadderWithoutOneKeepsItsParametersImplicit() {
    var result =
        compileClean(
            source(
                "Implicit",
                """
                public final class Implicit {
                  private Implicit() {}

                  public record Plain(List<String> tags, String name) {}

                  public record PlainDto(List<String> tags, String name) {}

                  @GenerateMapping
                  public interface PlainMapping extends MappingSpec<Plain, PlainDto> {}

                  public record Box<T>(List<@Nullable T> items, String name) {}

                  public record BoxDto<T>(List<@Nullable T> items, String name) {}

                  @GenerateMapping
                  public interface BoxMapping<T> extends MappingSpec<Box<T>, BoxDto<T>> {}
                }
                """));

    Assertions.assertThat(generated(result.compilation(), "ImplicitPlainMappingImpl"))
        .contains(".apply((tags, name) -> () -> new Implicit.Plain(tags, name))");
    Assertions.assertThat(generated(result.compilation(), "ImplicitBoxMappingImpl"))
        .contains(".apply((items, name) -> () -> new Implicit.Box<T>(items, name))");
  }

  @Test
  @DisplayName(
      "build casts a copy into a site whose type arguments are declared more nullable: a record"
          + " component, a setter, a builder setter, a plain merge's target and a bridged value")
  void buildCastsACopyIntoAWiderSite() throws ReflectiveOperationException {
    var result =
        compileClean(
            source(
                "Widened",
                """
                public final class Widened {
                  private Widened() {}

                  public record Code(String value) {}

                  // A leaf in each two-way pair withholds asIso, whose reverse copies the other way.
                  public record Wid(
                      @Nullable List<String> maybe,
                      List<List<String>> grid,
                      String[] cells,
                      List<? extends Number> nums,
                      Map<@Nullable String, String> keyed,
                      Code code) {}

                  public record WidDto(
                      @Nullable List<@Nullable String> maybe,
                      List<List<@Nullable String>> grid,
                      @Nullable String[] cells,
                      List<? extends @Nullable Number> nums,
                      Map<@Nullable String, @Nullable String> keyed,
                      String code) {}

                  @GenerateMapping
                  public interface WidMapping extends MappingSpec<Wid, WidDto> {
                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }

                  public record Tagged(List<String> tags) {}

                  public static class TaggedBean {
                    private List<@Nullable String> tags = List.of();

                    public List<@Nullable String> getTags() { return tags; }

                    public void setTags(List<@Nullable String> tags) { this.tags = tags; }
                  }

                  @GenerateMapping
                  public interface TaggedBeanMapping extends MappingSpec<Tagged, TaggedBean> {}

                  public static final class TaggedCard {
                    private final List<@Nullable String> tags;

                    private TaggedCard(List<@Nullable String> tags) { this.tags = tags; }

                    public List<@Nullable String> getTags() { return tags; }

                    public static Builder builder() { return new Builder(); }

                    public static final class Builder {
                      private List<@Nullable String> tags = List.of();

                      public Builder tags(List<@Nullable String> tags) {
                        this.tags = tags;
                        return this;
                      }

                      public TaggedCard build() { return new TaggedCard(tags); }
                    }
                  }

                  @GenerateMapping
                  public interface TaggedCardMapping extends MappingSpec<Tagged, TaggedCard> {}

                  public record Names(List<String> names) {}

                  public record Codes(String code) {}

                  public record Merged(List<@Nullable String> names, String code) {}

                  @GenerateMerge
                  public interface MergedAssembly {
                    Merged merge(Names names, Codes codes);
                  }

                  public record Opt(Optional<List<String>> nicks, Code code) {}

                  public record OptDto(@Nullable List<@Nullable String> nicks, String code) {}

                  @GenerateMapping
                  public interface OptMapping extends MappingSpec<Opt, OptDto> {
                    @OptionalBridge
                    Optional<List<String>> nicks();

                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }

                  public record Inner(List<String> tags) {}

                  public record Outer(String id, Inner inner, Code code) {}

                  public record OuterDto(String id, List<@Nullable String> tags, String code) {}

                  @GenerateMapping
                  public interface OuterMapping extends MappingSpec<Outer, OuterDto> {
                    @Flatten
                    Inner inner();

                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }
                }
                """));

    Assertions.assertThat(generated(result.compilation(), "WidenedWidMappingImpl"))
        .contains("(@Nullable List<@Nullable String>) hkj$copyOf(domain.maybe())")
        .contains("(List<List<@Nullable String>>) hkj$copyOf(domain.grid(), e -> hkj$copyOf(e))")
        .contains("(@Nullable String[]) hkj$copyOrNull(domain.cells(), e -> e.clone())")
        .contains("(List<? extends @Nullable Number>) hkj$copyOf(domain.nums())")
        // A position already nullable on both sides widens no further.
        .contains("(Map<@Nullable String, @Nullable String>) hkj$copyOf(domain.keyed())");
    Assertions.assertThat(generated(result.compilation(), "WidenedTaggedBeanMappingImpl"))
        .contains(".setTags((List<@Nullable String>) hkj$copyOf(domain.tags()))");
    Assertions.assertThat(generated(result.compilation(), "WidenedTaggedCardMappingImpl"))
        .contains(".tags((List<@Nullable String>) hkj$copyOf(domain.tags()))");
    Assertions.assertThat(generated(result.compilation(), "WidenedMergedAssemblyImpl"))
        .contains(
            "return new Widened.Merged((List<@Nullable String>) hkj$copyOf(names.names()),"
                + " codes.code())");
    Assertions.assertThat(generated(result.compilation(), "WidenedOptMappingImpl"))
        .contains("(@Nullable List<@Nullable String>) hkj$copyOf(domain.nicks().orElse(null))");
    Assertions.assertThat(generated(result.compilation(), "WidenedOuterMappingImpl"))
        .contains("(List<@Nullable String>) hkj$copyOf(domain.inner().tags())");

    Object bean =
        invoke(
            result.instance("com.example.WidenedTaggedBeanMappingImpl"),
            "build",
            record(result, "Widened$Tagged", List.of("a")));
    Assertions.assertThat(invoke(bean, "getTags")).isEqualTo(List.of("a"));
    Object merged =
        invoke(
            result.instance("com.example.WidenedMergedAssemblyImpl"),
            "merge",
            record(result, "Widened$Names", List.of("a")),
            record(result, "Widened$Codes", "c"));
    Assertions.assertThat(merged).isEqualTo(record(result, "Widened$Merged", List.of("a"), "c"));
  }

  @Test
  @DisplayName(
      "a copy is left uncast, and a ladder implicit, where retyping would hide a null: a level"
          + " the copy shares either way, a map's key, a converted value, a leg that widens and"
          + " narrows, a nested array, a narrower constructor or setter, a build that narrows, an"
          + " unwritable annotation, a type variable and shapes that do not pair")
  void otherCopiesAreLeftUncast() {
    var result =
        compileClean(
            JavaFileObjects.forSourceString(
                "com.example.wire.Nullable",
                """
                package com.example.wire;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;

                // Named as a nullness checker reads it, and not writable from com.example.
                @Target(ElementType.TYPE_USE)
                @interface Nullable {}
                """),
            JavaFileObjects.forSourceString(
                "com.example.wire.LooseDto",
                """
                package com.example.wire;

                import java.util.List;

                public record LooseDto(List<@Nullable String> tags) {}
                """),
            source(
                "Uncast",
                """
                public final class Uncast {
                  private Uncast() {}

                  // The copy holds nulls the site rules out: a widening must not hide them.
                  public record Narrow(List<@Nullable String> tags) {}

                  public record NarrowDto(List<String> tags) {}

                  @GenerateMapping
                  public interface NarrowMapping extends MappingSpec<Narrow, NarrowDto> {}

                  public record Mixed(Map<@Nullable String, String> byKey) {}

                  public record MixedDto(Map<String, @Nullable String> byKey) {}

                  @GenerateMapping
                  public interface MixedMapping extends MappingSpec<Mixed, MixedDto> {}

                  public record Loose(List<String> tags) {}

                  @GenerateMapping
                  public interface LooseMapping
                      extends MappingSpec<Loose, com.example.wire.LooseDto> {}

                  public record Plain<T>(Map<T, String> byKey) {}

                  public record Holey<T>(Map<T, @Nullable String> byKey) {}

                  @GenerateMapping
                  public interface HoleyMapping<T> extends MappingSpec<Plain<T>, Holey<T>> {}

                  public record Code(String value) {}

                  // A level handed over as it is, its elements shared with the other side.
                  public record Shared(java.util.ArrayList<String> tags) {}

                  public record SharedDto(java.util.ArrayList<@Nullable String> tags) {}

                  @GenerateMapping
                  public interface SharedMapping extends MappingSpec<Shared, SharedDto> {}

                  public record Rows(List<java.util.ArrayList<String>> rows) {}

                  public record RowsDto(List<java.util.ArrayList<@Nullable String>> rows) {}

                  @GenerateMapping
                  public interface RowsMapping extends MappingSpec<Rows, RowsDto> {}

                  // A map's keys are never copied inside.
                  public record Keys(Map<List<String>, String> byKey) {}

                  public record KeysDto(Map<List<@Nullable String>, String> byKey) {}

                  @GenerateMapping
                  public interface KeysMapping extends MappingSpec<Keys, KeysDto> {}

                  public record Pile(java.util.ArrayList<String> names) {}

                  public record Tag(String tag) {}

                  public record Piled(java.util.ArrayList<@Nullable String> names, String tag) {}

                  @GenerateMerge
                  public interface PiledAssembly {
                    Piled merge(Pile pile, Tag tag);
                  }

                  // parse would widen a list the domain shares with the wire, so the terminal's
                  // parameters stay implicit, even beside one carrying a nullable type argument.
                  public record Grown(
                      java.util.ArrayList<@Nullable String> tags, List<@Nullable String> more) {}

                  public record GrownDto(
                      java.util.ArrayList<String> tags, List<@Nullable String> more) {}

                  @GenerateMapping
                  public interface GrownMapping extends MappingSpec<Grown, GrownDto> {}

                  // A leaf's value is its own, whose nullness is not read.
                  public record Leafed(List<@Nullable String> tags, String name) {}

                  public record LeafedDto(String tags, String name) {}

                  @GenerateMapping
                  public interface LeafedMapping extends MappingSpec<Leafed, LeafedDto> {
                    default ValidatedPrism<String, List<@Nullable String>> tags() {
                      return ValidatedPrism.of(raw -> Validated.validNel(List.of(raw)), List::getFirst);
                    }
                  }

                  // T stands for @Nullable String here, which its declaration does not say.
                  public record Bag<T extends @Nullable Object>(Map<String, T> m) {}

                  public record BagDto(Map<@Nullable String, String> m) {}

                  @GenerateMapping
                  public interface BagMapping extends MappingSpec<Bag<@Nullable String>, BagDto> {}

                  public record Boxed<T>(List<@Nullable T> items) {}

                  public record BoxedDto(List<String> items) {}

                  @GenerateMapping
                  public interface BoxedMapping extends MappingSpec<Boxed<String>, BoxedDto> {}

                  // A bridged wildcard and the wire's map do not pair position by position.
                  public record Bridged(Optional<? extends Map<String, @Nullable String>> m) {}

                  public record BridgedDto(@Nullable Map<@Nullable String, String> m) {}

                  @GenerateMapping
                  public interface BridgedMapping extends MappingSpec<Bridged, BridgedDto> {
                    @OptionalBridge
                    Optional<? extends Map<String, @Nullable String>> m();
                  }

                  // The setter takes less than the getter answers.
                  public record Tagged(List<String> tags) {}

                  public static class NarrowBean {
                    private List<@Nullable String> tags = List.of();

                    public List<@Nullable String> getTags() { return tags; }

                    public void setTags(List<String> tags) { this.tags = List.copyOf(tags); }
                  }

                  @GenerateMapping
                  public interface NarrowBeanMapping extends MappingSpec<Tagged, NarrowBean> {}

                  // A leg that widens one position and narrows another stays implicit.
                  public record MixedIn(
                      Map<@Nullable String, String> byKey, List<@Nullable String> more) {}

                  public static class MixedView {
                    public Map<String, @Nullable String> getByKey() { return Map.of(); }

                    public List<@Nullable String> getMore() { return List.of(); }
                  }

                  @GenerateMapping
                  public interface MixedInMapping extends MappingSpec<MixedIn, MixedView> {}

                  // An array's cells below the top keep no nullness through the null scan.
                  public record GridIn(@Nullable String[][] grid, List<@Nullable String> more) {}

                  public static class GridView {
                    public String[][] getGrid() { return new String[0][]; }

                    public List<@Nullable String> getMore() { return List.of(); }
                  }

                  @GenerateMapping
                  public interface GridInMapping extends MappingSpec<GridIn, GridView> {}

                  // A narrowing over a container the source keeps is not checked for long.
                  public record Spare(java.util.ArrayList<@Nullable String> names) {}

                  public record Extra(List<@Nullable String> more, String code) {}

                  public record Kept(
                      java.util.ArrayList<String> names, List<@Nullable String> more, String code) {}

                  @GenerateMerge
                  public interface KeptAssembly {
                    Validated<NonEmptyList<FieldError>, Kept> merge(Spare spare, Extra extra);

                    default ValidatedPrism<String, String> code() {
                      return ValidatedPrism.of(Validated::validNel, code -> code);
                    }
                  }

                  // The canonical constructor takes less than the component declares.
                  public record CtorDto(List<@Nullable String> tags) {
                    public CtorDto(List<String> tags) {
                      this.tags = List.copyOf(tags);
                    }
                  }

                  @GenerateMapping
                  public interface CtorMapping extends MappingSpec<Tagged, CtorDto> {}

                  // A merge's leaf converts its fill, so the terminal's parameters stay implicit.
                  public record Raw(String tags) {}

                  public record Labelled(String code) {}

                  public record Made(List<@Nullable String> tags, String code) {}

                  @GenerateMerge
                  public interface MadeAssembly {
                    Validated<NonEmptyList<FieldError>, Made> merge(Raw raw, Labelled labelled);

                    default ValidatedPrism<String, List<@Nullable String>> tags() {
                      return ValidatedPrism.of(raw -> Validated.validNel(List.of(raw)), List::getFirst);
                    }
                  }
                }
                """));

    Assertions.assertThat(generated(result.compilation(), "UncastNarrowMappingImpl"))
        .contains("return new Uncast.NarrowDto(hkj$copyOf(domain.tags()));");
    Assertions.assertThat(generated(result.compilation(), "UncastMixedMappingImpl"))
        .contains("return new Uncast.MixedDto(hkj$copyOf(domain.byKey()));");
    Assertions.assertThat(generated(result.compilation(), "UncastLooseMappingImpl"))
        .contains("return new LooseDto(hkj$copyOf(domain.tags()));");
    Assertions.assertThat(generated(result.compilation(), "UncastHoleyMappingImpl"))
        .contains("return new Uncast.Holey<T>(hkj$copyOf(domain.byKey()));");
    Assertions.assertThat(generated(result.compilation(), "UncastSharedMappingImpl"))
        .contains("return new Uncast.SharedDto(domain.tags());");
    Assertions.assertThat(generated(result.compilation(), "UncastRowsMappingImpl"))
        .contains("return new Uncast.RowsDto(hkj$copyOf(domain.rows()));");
    Assertions.assertThat(generated(result.compilation(), "UncastKeysMappingImpl"))
        .contains("return new Uncast.KeysDto(hkj$copyOf(domain.byKey()));");
    Assertions.assertThat(generated(result.compilation(), "UncastPiledAssemblyImpl"))
        .contains("return new Uncast.Piled(pile.names(), tag.tag());");
    Assertions.assertThat(generated(result.compilation(), "UncastGrownMappingImpl"))
        .contains(".apply((tags, more) -> () -> new Uncast.Grown(tags, more))");
    Assertions.assertThat(generated(result.compilation(), "UncastLeafedMappingImpl"))
        .contains(".apply((tags, name) -> () -> new Uncast.Leafed(tags, name))");
    Assertions.assertThat(generated(result.compilation(), "UncastBagMappingImpl"))
        .contains("return new Uncast.BagDto(hkj$copyOf(domain.m()));");
    Assertions.assertThat(generated(result.compilation(), "UncastBoxedMappingImpl"))
        .contains("var hkj$items = hkj$copyOf(wire.items());");
    Assertions.assertThat(generated(result.compilation(), "UncastBridgedMappingImpl"))
        .contains("hkj$copyOf(domain.m().orElse(null))")
        .doesNotContain("(@Nullable Map");
    Assertions.assertThat(generated(result.compilation(), "UncastNarrowBeanMappingImpl"))
        .contains(".setTags(hkj$copyOf(domain.tags()))");
    Assertions.assertThat(generated(result.compilation(), "UncastMixedInMappingImpl"))
        .contains(".apply((byKey, more) -> () -> new Uncast.MixedIn(byKey, more))")
        .contains("var hkj$byKey = hkj$copyOf(wire.getByKey());");
    Assertions.assertThat(generated(result.compilation(), "UncastGridInMappingImpl"))
        .contains(".apply((grid, more) -> () -> new Uncast.GridIn(grid, more))")
        .doesNotContain("(@Nullable String[][])");
    Assertions.assertThat(generated(result.compilation(), "UncastKeptAssemblyImpl"))
        .contains(".apply((names_, more_, code_) -> () -> new Uncast.Kept(names_, more_, code_))");
    Assertions.assertThat(generated(result.compilation(), "UncastCtorMappingImpl"))
        .contains("return new Uncast.CtorDto(hkj$copyOf(domain.tags()));");
    Assertions.assertThat(generated(result.compilation(), "UncastMadeAssemblyImpl"))
        .contains(".apply((tags_, code_) -> () -> new Uncast.Made(tags_, code_))");
  }
}
