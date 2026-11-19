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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
      import java.util.ArrayList;
      import java.util.List;
      import java.util.Map;
      import java.util.Optional;
      import java.util.Set;
      import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
      import org.higherkindedj.hkt.validated.FieldError;
      import org.higherkindedj.hkt.validated.Validated;
      import org.higherkindedj.optics.annotations.Flatten;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.GenerateMerge;
      import org.higherkindedj.optics.annotations.MapKey;
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

  private static Validated<NonEmptyList<FieldError>, Object> parse(Object impl, Object wire) {
    return validated(invoke(impl, "parse", wire));
  }

  @SuppressWarnings("unchecked") // a generated parse's or patch's result, read reflectively
  private static Validated<NonEmptyList<FieldError>, Object> validated(Object result) {
    return (Validated<NonEmptyList<FieldError>, Object>) result;
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
      "a map whose keys pass through holding a nullable type argument names the key its"
          + " parseValues takes, and declares the terminal's parameters where both sides declare"
          + " the key alike and its values hold none, bridged too")
  void aMapWhoseKeysPassThroughNamesTheirType() throws ReflectiveOperationException {
    var result =
        compileClean(
            source(
                "Keyed",
                """
                public final class Keyed {
                  private Keyed() {}

                  public record Code(String value) {}

                  public record Tag<T extends @Nullable Object>(T value) {}

                  static ValidatedPrism<String, Code> code() {
                    return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                  }

                  // Keys holding containers, scanned, the sides agreeing.
                  public record Holed(
                      Map<List<@Nullable String>, Code> byPath,
                      Optional<Map<List<@Nullable String>, Code>> bridged) {}

                  public record HoledDto(
                      Map<List<@Nullable String>, String> byPath,
                      @Nullable Map<List<@Nullable String>, String> bridged) {}

                  @GenerateMapping
                  public interface HoledMapping extends MappingSpec<Holed, HoledDto> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }

                    @OptionalBridge
                    default ValidatedPrism<String, Code> bridged() { return code(); }
                  }

                  // A key that holds no container, so is not scanned.
                  public record Tagged(
                      Map<Tag<@Nullable String>, Code> byTag,
                      Optional<Map<Tag<@Nullable String>, Code>> bridged) {}

                  public record TaggedDto(
                      Map<Tag<@Nullable String>, String> byTag,
                      @Nullable Map<Tag<@Nullable String>, String> bridged) {}

                  @GenerateMapping
                  public interface TaggedMapping extends MappingSpec<Tagged, TaggedDto> {
                    default ValidatedPrism<String, Code> byTag() { return code(); }

                    @OptionalBridge
                    default ValidatedPrism<String, Code> bridged() { return code(); }
                  }

                  // The domain narrows the key, so its parameters stay implicit.
                  public record Narrowed(Map<List<String>, Code> byPath) {}

                  public record NarrowedDto(Map<List<@Nullable String>, String> byPath) {}

                  @GenerateMapping
                  public interface NarrowedMapping extends MappingSpec<Narrowed, NarrowedDto> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }
                  }

                  // Values a leaf converts to a type holding a nullable type argument.
                  public record Valued(Map<List<@Nullable String>, List<@Nullable String>> byPath) {}

                  public record ValuedDto(Map<List<@Nullable String>, String> byPath) {}

                  @GenerateMapping
                  public interface ValuedMapping extends MappingSpec<Valued, ValuedDto> {
                    default ValidatedPrism<String, List<@Nullable String>> byPath() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(List.of(raw)), list -> String.valueOf(list));
                    }
                  }

                  // Values declared nullable themselves, on a view that only parses, since build
                  // could hand no null value on.
                  public record Loose(Map<List<@Nullable String>, @Nullable Code> byPath) {}

                  public static final class LooseView {
                    private final Map<List<@Nullable String>, String> byPath;

                    public LooseView(Map<List<@Nullable String>, String> byPath) {
                      this.byPath = byPath;
                    }

                    public Map<List<@Nullable String>, String> getByPath() { return byPath; }
                  }

                  @GenerateMapping
                  public interface LooseMapping extends MappingSpec<Loose, LooseView> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }
                  }

                  // A key declared nullable itself, which the bulk form never hands on, on a view
                  // that only parses, since build could hand no null key on.
                  public record Opened(Map<@Nullable List<@Nullable String>, Code> byPath) {}

                  public static final class OpenedView {
                    private final Map<@Nullable List<@Nullable String>, String> byPath;

                    public OpenedView(Map<@Nullable List<@Nullable String>, String> byPath) {
                      this.byPath = byPath;
                    }

                    public Map<@Nullable List<@Nullable String>, String> getByPath() {
                      return byPath;
                    }
                  }

                  @GenerateMapping
                  public interface OpenedMapping extends MappingSpec<Opened, OpenedView> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }
                  }

                  // A bridge whose Optional names the map through a wildcard.
                  public record Wild(Optional<? extends Map<List<@Nullable String>, Code>> byPath) {}

                  public record WildDto(@Nullable Map<List<@Nullable String>, String> byPath) {}

                  @GenerateMapping
                  public interface WildMapping extends MappingSpec<Wild, WildDto> {
                    @OptionalBridge
                    default ValidatedPrism<String, Code> byPath() { return code(); }
                  }

                  // A bridge whose Optional is named through a type variable, over a lifted map.
                  public record Bound<O extends Optional<Map<String, Code>>>(O codes) {}

                  public record BoundDto(@Nullable Map<String, String> codes) {}

                  @GenerateMapping
                  public interface BoundMapping
                      extends MappingSpec<Bound<Optional<Map<String, Code>>>, BoundDto> {
                    @OptionalBridge
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }

                  // The domain widens the key, on a view that only parses.
                  public record Widened(Map<List<@Nullable String>, Code> byPath) {}

                  public static final class WidenedView {
                    private final Map<List<String>, String> byPath;

                    public WidenedView(Map<List<String>, String> byPath) { this.byPath = byPath; }

                    public Map<List<String>, String> getByPath() { return byPath; }
                  }

                  @GenerateMapping
                  public interface WidenedMapping extends MappingSpec<Widened, WidenedView> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }
                  }

                  // The domain narrows the key beside a list declared to hold nulls, on a view that
                  // only parses: the list's leg still declares its type.
                  public record Beside(Map<List<String>, Code> byPath, List<@Nullable String> notes) {}

                  public static final class BesideView {
                    private final Map<List<@Nullable String>, String> byPath;
                    private final List<@Nullable String> notes;

                    public BesideView(
                        Map<List<@Nullable String>, String> byPath, List<@Nullable String> notes) {
                      this.byPath = byPath;
                      this.notes = notes;
                    }

                    public Map<List<@Nullable String>, String> getByPath() { return byPath; }

                    public List<@Nullable String> getNotes() { return notes; }
                  }

                  @GenerateMapping
                  public interface BesideMapping extends MappingSpec<Beside, BesideView> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }
                  }

                  // A generic spec, whose key holds a nullable type argument beside its type
                  // variable, bridged, so its parseValues is called outright.
                  public record Pair<A extends @Nullable Object, B extends @Nullable Object>(
                      A first, B second) {}

                  public record Paired<T>(Optional<Map<Pair<@Nullable String, T>, Code>> byPair) {}

                  public record PairedDto<T>(@Nullable Map<Pair<@Nullable String, T>, String> byPair) {}

                  @GenerateMapping
                  public interface PairedMapping<T> extends MappingSpec<Paired<T>, PairedDto<T>> {
                    @OptionalBridge
                    default ValidatedPrism<String, Code> byPair() { return code(); }
                  }
                }
                """));

    Assertions.assertThat(generated(result.compilation(), "KeyedHoledMappingImpl"))
        .contains(
            "v -> hkj$keysScanned(byPath().<List<@Nullable String>>parseValues(v), v, e ->"
                + " hkj$allPresent(e))")
        .contains(
            "v -> hkj$keysScanned(bridged().<List<@Nullable String>>parseValues(v), v, e ->"
                + " hkj$allPresent(e)).map(Optional::of)")
        .contains(
            ".apply((Map<List<@Nullable String>, Keyed.Code> byPath,"
                + " Optional<Map<List<@Nullable String>, Keyed.Code>> bridged) ->");
    // A method reference infers the key from the map whole, so only the bridge's call names it.
    Assertions.assertThat(generated(result.compilation(), "KeyedTaggedMappingImpl"))
        .contains("hkj$ifPresent(wire.byTag(), byTag()::parseValues)")
        .contains("v -> bridged().<Keyed.Tag<@Nullable String>>parseValues(v).map(Optional::of)")
        .contains(
            ".apply((Map<Keyed.Tag<@Nullable String>, Keyed.Code> byTag,"
                + " Optional<Map<Keyed.Tag<@Nullable String>, Keyed.Code>> bridged) ->");
    Assertions.assertThat(generated(result.compilation(), "KeyedNarrowedMappingImpl"))
        .contains("byPath().<List<@Nullable String>>parseValues(v)")
        .contains(".apply((byPath) ->");
    Assertions.assertThat(generated(result.compilation(), "KeyedValuedMappingImpl"))
        .contains(".apply((byPath) ->");
    Assertions.assertThat(generated(result.compilation(), "KeyedLooseMappingImpl"))
        .contains(".apply((byPath) ->");
    // The key's own nullness is left off the argument, as the bulk form's key variable is non-null.
    Assertions.assertThat(generated(result.compilation(), "KeyedOpenedMappingImpl"))
        .contains("byPath().<List<@Nullable String>>parseValues(v)")
        .contains(".apply((byPath) ->");
    Assertions.assertThat(generated(result.compilation(), "KeyedWildMappingImpl"))
        .contains(".apply((byPath) ->");
    Assertions.assertThat(generated(result.compilation(), "KeyedBoundMappingImpl"))
        .contains("v -> codes().parseValues(v).map(Optional::of)");
    Assertions.assertThat(generated(result.compilation(), "KeyedWidenedMappingImpl"))
        .contains(".apply((byPath) ->");
    Assertions.assertThat(generated(result.compilation(), "KeyedBesideMappingImpl"))
        .contains(".apply((Map<List<String>, Keyed.Code> byPath, List<@Nullable String> notes) ->");
    // A key naming a type variable is left to inference, the generated class having its own.
    Assertions.assertThat(generated(result.compilation(), "KeyedPairedMappingImpl"))
        .contains("v -> byPair().parseValues(v).map(Optional::of)");

    Object holed = result.instance("com.example.KeyedHoledMappingImpl");
    Map<List<String>, String> clean = new LinkedHashMap<>();
    clean.put(Arrays.asList("a", "b"), "x");
    assertThatValidated(parse(holed, record(result, "Keyed$HoledDto", clean, null))).isValid();
    // parse refuses a null whatever the domain declares, inside a key as elsewhere.
    Map<List<String>, String> holes = new LinkedHashMap<>();
    holes.put(Arrays.asList("a", null), "x");
    assertThatValidated(parse(holed, record(result, "Keyed$HoledDto", holes, null)))
        .hasFieldErrors("byPath.[a, null].1: must not be null");
  }

  @Test
  @DisplayName(
      "a sparse update names the setter's focus over an array whose elements may be null,"
          + " sent as it is or through a JsonNullable, declares the holder of a map whose keys"
          + " hold a nullable type argument, and leaves every other edit as it was")
  void aSparseArrayOfNullableElementsNamesItsFocus() throws ReflectiveOperationException {
    var result =
        compileClean(
            JsonNullableStub.SOURCE,
            source(
                "Gridded",
                """
                import org.higherkindedj.optics.annotations.UpdateSpec;
                import org.openapitools.jackson.nullable.JsonNullable;

                public final class Gridded {
                  private Gridded() {}

                  public record Code(String value) {}

                  public record Grid(
                      String id,
                      @Nullable String[] cells,
                      String[] plain,
                      List<@Nullable String> tags,
                      String name,
                      Code code) {}

                  public static final class GridPatch {
                    private @Nullable String @Nullable [] cells;
                    private @Nullable String @Nullable [] plain;
                    private @Nullable List<@Nullable String> tags;
                    private @Nullable String name;
                    private @Nullable String code;

                    public @Nullable String @Nullable [] getCells() { return cells; }

                    public void setCells(@Nullable String @Nullable [] cells) { this.cells = cells; }

                    public @Nullable String @Nullable [] getPlain() { return plain; }

                    public void setPlain(@Nullable String @Nullable [] plain) { this.plain = plain; }

                    public @Nullable List<@Nullable String> getTags() { return tags; }

                    public void setTags(@Nullable List<@Nullable String> tags) { this.tags = tags; }

                    public @Nullable String getName() { return name; }

                    public void setName(@Nullable String name) { this.name = name; }

                    public @Nullable String getCode() { return code; }

                    public void setCode(@Nullable String code) { this.code = code; }
                  }

                  @GenerateMapping
                  public interface GridPatchMapping extends UpdateSpec<Grid, GridPatch> {
                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }

                  static ValidatedPrism<String, Code> codes() {
                    return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                  }

                  // The same edits sent through a JsonNullable holder.
                  public record Sent(
                      String id,
                      @Nullable String[] cells,
                      Map<List<@Nullable String>, Code> byPath,
                      Optional<Map<List<@Nullable String>, Code>> bridged,
                      Map<String, Code> plain) {}

                  @SuppressWarnings("NullableOptional")
                  public static final class SentPatch {
                    private @Nullable JsonNullable<@Nullable String @Nullable []> cells;
                    private @Nullable JsonNullable<Map<List<@Nullable String>, String>> byPath;
                    private @Nullable JsonNullable<Map<List<@Nullable String>, String>> bridged;
                    private @Nullable JsonNullable<Map<String, String>> plain;

                    public @Nullable JsonNullable<@Nullable String @Nullable []> getCells() {
                      return cells;
                    }

                    public void setCells(@Nullable JsonNullable<@Nullable String @Nullable []> cells) {
                      this.cells = cells;
                    }

                    public @Nullable JsonNullable<Map<List<@Nullable String>, String>> getByPath() {
                      return byPath;
                    }

                    public void setByPath(
                        @Nullable JsonNullable<Map<List<@Nullable String>, String>> byPath) {
                      this.byPath = byPath;
                    }

                    public @Nullable JsonNullable<Map<List<@Nullable String>, String>> getBridged() {
                      return bridged;
                    }

                    public void setBridged(
                        @Nullable JsonNullable<Map<List<@Nullable String>, String>> bridged) {
                      this.bridged = bridged;
                    }

                    public @Nullable JsonNullable<Map<String, String>> getPlain() { return plain; }

                    public void setPlain(@Nullable JsonNullable<Map<String, String>> plain) {
                      this.plain = plain;
                    }
                  }

                  @GenerateMapping
                  public interface SentPatchMapping extends UpdateSpec<Sent, SentPatch> {
                    default ValidatedPrism<String, Code> byPath() { return codes(); }

                    default ValidatedPrism<String, Code> bridged() { return codes(); }

                    default ValidatedPrism<String, Code> plain() { return codes(); }
                  }
                }
                """));

    Assertions.assertThat(generated(result.compilation(), "GriddedGridPatchMappingImpl"))
        .contains("Setter.<Components, @Nullable String[]>fromGetSet(Components::cells, (c, v) ->")
        .contains("e -> hkj$allPresent(e)).at(\"cells\")")
        .contains("Setter.fromGetSet(Components::plain, (c, v) ->")
        .contains("GriddedGridPatchMappingImpl::hkj$allPresent).at(\"plain\")")
        .contains("Setter.fromGetSet(Components::tags, (c, v) ->")
        .contains("Setter.fromGetSet(Components::name, (c, v) ->")
        .contains("Setter.fromGetSet(Components::code, (c, v) ->");
    Assertions.assertThat(generated(result.compilation(), "GriddedSentPatchMappingImpl"))
        .contains("Setter.<Components, @Nullable String[]>fromGetSet(Components::cells, (c, v) ->")
        .contains(
            "(JsonNullable<Map<List<@Nullable String>, String>> hkj$held) ->"
                + " hkj$ifPresent(hkj$held.get(),")
        .contains(
            "(JsonNullable<Map<List<@Nullable String>, String>> hkj$held) ->"
                + " hkj$parsePresent(hkj$held.get(),")
        .contains("hkj$held -> hkj$ifPresent(hkj$held.get(), plain()::parseValues)");

    // The lambda scan reports what the method reference did.
    Object patch =
        result.loadClass("com.example.Gridded$GridPatch").getDeclaredConstructor().newInstance();
    invoke(patch, "setCells", (Object) new String[] {"a", null});
    Object grid =
        record(
            result,
            "Gridded$Grid",
            "g",
            new String[] {"x"},
            new String[] {"y"},
            List.of(),
            "n",
            record(result, "Gridded$Code", "c"));
    Object accumulated =
        invoke(result.instance("com.example.GriddedGridPatchMappingImpl"), "updateFrom", patch);
    assertThatValidated(validated(invoke(accumulated, "apply", grid)))
        .hasFieldErrors("cells.1: must not be null");
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
      "build casts the container a lifted leaf or nested spec builds into a site whose elements,"
          + " keys or values are declared nullable, where it builds each as the site declares it"
          + " inside: a list, a set, every map form, elements that hold lists, arrays or type"
          + " arguments, a setter, a bridged value and a projection; an array's constructor names"
          + " no annotation, and parse still reports each null")
  void buildCastsALiftedContainerIntoAWiderSite() throws ReflectiveOperationException {
    var result =
        compileClean(
            source(
                "Lifted",
                """
                public final class Lifted {
                  private Lifted() {}

                  public record Code(String value) {}

                  public record Row(String[] cells) {}

                  static ValidatedPrism<String, Code> code() {
                    return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                  }

                  public record Item(Code code) {}

                  public record ItemDto(String code) {}

                  @GenerateMapping
                  public interface ItemMapping extends MappingSpec<Item, ItemDto> {
                    default ValidatedPrism<String, Code> code() { return Lifted.code(); }
                  }

                  public record Pair<A, B>(A first, B second) {}

                  public record Lift(
                      List<Code> list,
                      Set<Code> set,
                      Map<String, Code> values,
                      Map<Code, String> keys,
                      Map<Code, Code> entries,
                      Map<Code, List<String>> rows,
                      List<Item> items,
                      Code[] array,
                      Row[] grid,
                      List<Row> lines,
                      List<Code> pairs) {}

                  public record LiftDto(
                      List<@Nullable String> list,
                      @Nullable Set<@Nullable String> set,
                      Map<@Nullable String, @Nullable String> values,
                      Map<@Nullable String, @Nullable String> keys,
                      Map<@Nullable String, @Nullable String> entries,
                      Map<@Nullable String, List<String>> rows,
                      List<@Nullable ItemDto> items,
                      @Nullable String[] array,
                      String[] @Nullable [] grid,
                      List<String @Nullable []> lines,
                      List<@Nullable Pair<String, String>> pairs) {}

                  @GenerateMapping
                  public interface LiftMapping extends MappingSpec<Lift, LiftDto> {
                    default ValidatedPrism<String, Code> list() { return code(); }

                    default ValidatedPrism<String, Code> set() { return code(); }

                    default ValidatedPrism<String, Code> values() { return code(); }

                    @MapKey("keys")
                    default ValidatedPrism<String, Code> keysKey() { return code(); }

                    default ValidatedPrism<String, Code> entries() { return code(); }

                    @MapKey("entries")
                    default ValidatedPrism<String, Code> entriesKey() { return code(); }

                    @MapKey("rows")
                    default ValidatedPrism<String, Code> rowsKey() { return code(); }

                    default ValidatedPrism<String, Code> array() { return code(); }

                    default ValidatedPrism<String[], Row> grid() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Row(raw)), Row::cells);
                    }

                    default ValidatedPrism<String[], Row> lines() { return grid(); }

                    default ValidatedPrism<Pair<String, String>, Code> pairs() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(new Code(raw.first())),
                          code -> new Pair<>(code.value(), code.value()));
                    }
                  }

                  public record Codes(List<String> values) {}

                  public record Grouped(List<Codes> groups) {}

                  public record GroupedDto(List<@Nullable List<String>> groups) {}

                  @GenerateMapping
                  public interface GroupedMapping extends MappingSpec<Grouped, GroupedDto> {
                    default ValidatedPrism<List<String>, Codes> groups() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Codes(raw)), Codes::values);
                    }
                  }

                  // Bridged through a wildcard, and through a type variable.
                  public record Wild(Optional<? extends List<Code>> codes) {}

                  public record Bound<O extends Optional<List<Code>>>(O codes) {}

                  public record WildDto(@Nullable List<@Nullable String> codes) {}

                  @GenerateMapping
                  public interface WildMapping extends MappingSpec<Wild, WildDto> {
                    @OptionalBridge
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }

                  @GenerateMapping
                  public interface BoundMapping
                      extends MappingSpec<Bound<Optional<List<Code>>>, WildDto> {
                    @OptionalBridge
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }

                  public record Order(List<Code> codes, List<Code> extras, Optional<Set<Code>> maybe) {}

                  public static final class OrderBean {
                    private @Nullable List<@Nullable String> codes;
                    private final List<@Nullable String> extras = new ArrayList<>();
                    private @Nullable Set<@Nullable String> maybe;

                    public @Nullable List<@Nullable String> getCodes() { return codes; }

                    public void setCodes(@Nullable List<@Nullable String> codes) { this.codes = codes; }

                    public List<@Nullable String> getExtras() { return extras; }

                    public @Nullable Set<@Nullable String> getMaybe() { return maybe; }

                    public void setMaybe(@Nullable Set<@Nullable String> maybe) { this.maybe = maybe; }
                  }

                  @GenerateMapping
                  public interface OrderBeanMapping extends MappingSpec<Order, OrderBean> {
                    default ValidatedPrism<String, Code> codes() { return code(); }

                    default ValidatedPrism<String, Code> extras() { return code(); }

                    default ValidatedPrism<String, Code> maybe() { return code(); }
                  }

                  public record Tagged(Optional<Map<String, Code>> prices) {}

                  public record TaggedDto(@Nullable Map<@Nullable String, String> prices) {}

                  @GenerateMapping
                  public interface TaggedMapping extends MappingSpec<Tagged, TaggedDto> {
                    @OptionalBridge
                    default ValidatedPrism<String, Code> prices() { return code(); }
                  }

                  public record Profile(String id, List<Code> codes) {}

                  public record ProfileView(List<@Nullable String> codes) {}

                  @GenerateMapping
                  public interface ProfileViewMapping extends MappingSpec<Profile, ProfileView> {
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }

                  // Parse only, into an array whose elements the domain declares nullable.
                  public record Loose(@Nullable Code[] codes) {}

                  public static final class LooseView {
                    private final String[] codes;

                    public LooseView(String[] codes) { this.codes = codes; }

                    public String[] getCodes() { return codes; }
                  }

                  @GenerateMapping
                  public interface LooseViewMapping extends MappingSpec<Loose, LooseView> {
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }
                }
                """));

    String lift = generated(result.compilation(), "LiftedLiftMappingImpl");
    Assertions.assertThat(lift)
        .contains("(List<@Nullable String>) list().buildAll(domain.list())")
        .contains("(@Nullable Set<@Nullable String>) set().buildAll(domain.set())")
        .contains("(Map<@Nullable String, @Nullable String>) values().buildValues(domain.values())")
        .contains("(Map<@Nullable String, @Nullable String>) keysKey().buildKeys(domain.keys())")
        .contains(
            "(Map<@Nullable String, @Nullable String>)"
                + " entriesKey().buildEntries(domain.entries(), entries())")
        // The values copy, so the cast is the copy's: the keys are the map's own level.
        .contains(
            "(Map<@Nullable String, List<String>>) hkj$copyOf(rowsKey().buildKeys(domain.rows()),"
                + " e -> hkj$copyOf(e))")
        .contains(
            "(List<Lifted. @Nullable ItemDto>)"
                + " LiftedItemMappingImpl.INSTANCE.asValidatedPrism().buildAll(domain.items())")
        // A checker takes a String[] where a @Nullable String[] is asked for, so an array needs
        // no cast, and its constructor carries no annotation, which NullAway cannot read there.
        .contains("array().buildAll(domain.array(), String[]::new)")
        .contains("grid().buildAll(domain.grid(), String[][]::new)")
        .contains("v -> array().parseAll(v, Lifted.Code[]::new)")
        // An element that holds an array or type arguments is cast where the leaf builds it as the
        // site declares it inside.
        .contains("(List<String @Nullable []>) lines().buildAll(domain.lines())")
        .contains(
            "(List<Lifted. @Nullable Pair<String, String>>) pairs().buildAll(domain.pairs())");
    Assertions.assertThat(generated(result.compilation(), "LiftedGroupedMappingImpl"))
        .contains("(List<@Nullable List<String>>) groups().buildAll(domain.groups())");
    Assertions.assertThat(generated(result.compilation(), "LiftedWildMappingImpl"))
        .contains(
            "(@Nullable List<@Nullable String>) domain.codes().map(codes()::buildAll).orElse(null)");
    Assertions.assertThat(generated(result.compilation(), "LiftedBoundMappingImpl"))
        .contains(
            "(@Nullable List<@Nullable String>) domain.codes().map(codes()::buildAll).orElse(null)");
    Assertions.assertThat(generated(result.compilation(), "LiftedOrderBeanMappingImpl"))
        .contains(".setCodes((@Nullable List<@Nullable String>) codes().buildAll(domain.codes()))")
        // A getter-only list takes what its addAll does, a Collection<? extends E>.
        .contains(".getExtras().addAll(extras().buildAll(domain.extras()))")
        .contains(
            ".setMaybe((@Nullable Set<@Nullable String>)"
                + " domain.maybe().map(maybe()::buildAll).orElse(null))");
    Assertions.assertThat(generated(result.compilation(), "LiftedTaggedMappingImpl"))
        .contains(
            "(@Nullable Map<@Nullable String, String>)"
                + " domain.prices().map(prices()::buildValues).orElse(null)");
    Assertions.assertThat(generated(result.compilation(), "LiftedProfileViewMappingImpl"))
        .contains("(List<@Nullable String>) codes().buildAll(domain.codes())");
    Assertions.assertThat(generated(result.compilation(), "LiftedLooseViewMappingImpl"))
        .contains("v -> codes().parseAll(v, Lifted.Code[]::new)");

    Object impl = result.instance("com.example.LiftedProfileViewMappingImpl");
    Object code = record(result, "Lifted$Code", "a");
    Assertions.assertThat(
            invoke(impl, "build", record(result, "Lifted$Profile", "p1", List.of(code))))
        .isEqualTo(record(result, "Lifted$ProfileView", List.of("a")));
    Object holed = record(result, "Lifted$ProfileView", Arrays.asList("a", null));
    assertThatValidated(
            validated(
                invoke(impl, "patch", record(result, "Lifted$Profile", "p1", List.of()), holed)))
        .isInvalid()
        .hasFieldErrors("codes.1: must not be null");
    Map<String, String> keyless = new HashMap<>();
    keyless.put(null, "b");
    assertThatValidated(
            parse(
                result.instance("com.example.LiftedTaggedMappingImpl"),
                record(result, "Lifted$TaggedDto", keyless)))
        .isInvalid()
        .hasFieldErrors("prices: must not contain a null key");
  }

  @Test
  @DisplayName(
      "a lifted container is left uncast where what builds an element types it otherwise inside:"
          + " a leaf over lists the site declares holding nulls, an enclosing type's argument, a"
          + " generic spec, a generic mix-in's leaf and one declared to return its type variable;"
          + " and a copy, where an enclosing type's argument differs")
  void aLiftedContainerBuiltOtherwiseIsLeftUncast() {
    var result =
        compileClean(
            source(
                "Unlifted",
                """
                public final class Unlifted {
                  private Unlifted() {}

                  public record Code(String value) {}

                  public static final class Outer<T> {
                    public final class Inner {}
                  }

                  public record Codes(List<String> values) {}

                  public record Box<T>(T item) {}

                  public record BoxDto<T>(T item) {}

                  @GenerateMapping
                  public interface BoxMapping<T> extends MappingSpec<Box<T>, BoxDto<T>> {}

                  public record Nested(List<Codes> groups, List<Code> inners, List<Box<String>> boxes) {}

                  public record NestedDto(
                      List<List<@Nullable String>> groups,
                      List<Outer<@Nullable String>. @Nullable Inner> inners,
                      List<@Nullable BoxDto<String>> boxes) {}

                  @GenerateMapping
                  public interface NestedMapping extends MappingSpec<Nested, NestedDto> {
                    default ValidatedPrism<List<String>, Codes> groups() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Codes(raw)), Codes::values);
                    }

                    default ValidatedPrism<Outer<String>.Inner, Code> inners() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(new Code("x")),
                          code -> new Outer<String>().new Inner());
                    }
                  }

                  // A generic mix-in's leaf, whose built type names its variable: read under the
                  // spec, Box<@Nullable T> would lose the annotation the Impl's leaf writes back.
                  public interface BoxLeaves<T> {
                    default ValidatedPrism<Box<@Nullable T>, Code> mixed() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(new Code("x")), code -> new Box<@Nullable T>(null));
                    }
                  }

                  public record Mixed(List<Code> mixed) {}

                  public record MixedDto(List<@Nullable Box<String>> mixed) {}

                  @GenerateMapping
                  public interface MixedMapping extends MappingSpec<Mixed, MixedDto>, BoxLeaves<String> {}

                  // A mix-in's leaves declared to return its type variable declare no wire type.
                  public interface Picks<P extends ValidatedPrism<String, Code>> {
                    @SuppressWarnings("unchecked") // P is a ValidatedPrism<String, Code> at each use
                    private static <P> P pick() {
                      return (P) ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }

                    default P picks() {
                      return pick();
                    }

                    @MapKey("priced")
                    default P pricedKey() {
                      return pick();
                    }
                  }

                  public record Picked(List<Code> picks, Map<Code, String> priced) {}

                  public record PickedDto(
                      List<@Nullable String> picks, Map<@Nullable String, String> priced) {}

                  @GenerateMapping
                  public interface PickedMapping
                      extends MappingSpec<Picked, PickedDto>, Picks<ValidatedPrism<String, Code>> {}

                  // A copy: an enclosing type's argument differs, or only the element's own nullness.
                  public record Held(List<Outer<String>.Inner> shared, List<Outer<String>.Inner> own) {}

                  public record HeldDto(
                      List<Outer<@Nullable String>.Inner> shared,
                      List<Outer<String>. @Nullable Inner> own) {}

                  @GenerateMapping
                  public interface HeldMapping extends MappingSpec<Held, HeldDto> {}

                  // A copy bridged through a type variable, whose Optional names no argument.
                  public record Bound<O extends Optional<List<String>>>(O tags) {}

                  public record BoundDto(@Nullable List<@Nullable String> tags) {}

                  @GenerateMapping
                  public interface BoundMapping
                      extends MappingSpec<Bound<Optional<List<String>>>, BoundDto> {
                    @OptionalBridge
                    Optional<List<String>> tags();
                  }
                }
                """));

    Assertions.assertThat(generated(result.compilation(), "UnliftedNestedMappingImpl"))
        .contains(
            "return new Unlifted.NestedDto(groups().buildAll(domain.groups()),"
                + " inners().buildAll(domain.inners()),"
                + " UnliftedBoxMappingImpl.<String>instance().asValidatedPrism().buildAll(domain.boxes()));");
    Assertions.assertThat(generated(result.compilation(), "UnliftedMixedMappingImpl"))
        .contains("return new Unlifted.MixedDto(mixed().buildAll(domain.mixed()));");
    Assertions.assertThat(generated(result.compilation(), "UnliftedPickedMappingImpl"))
        .contains("return new Unlifted.PickedDto(picks().buildAll(domain.picks()),")
        .doesNotContain("(List<@Nullable String>)")
        .doesNotContain("(Map<@Nullable String, String>)");
    Assertions.assertThat(generated(result.compilation(), "UnliftedHeldMappingImpl"))
        .contains("return new Unlifted.HeldDto(hkj$copyOf(domain.shared()),")
        .contains("(List<Unlifted.Outer<String>. @Nullable Inner>) hkj$copyOf(domain.own())");
    Assertions.assertThat(generated(result.compilation(), "UnliftedBoundMappingImpl"))
        .contains("hkj$copyOf(domain.tags().orElse(null))")
        .doesNotContain("(@Nullable List");
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
