// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A record wire whose component may hold {@code null}, by its declaration, read into a domain
 * component declared non-null: {@code parse} refuses the {@code null} with a located error, so no
 * total optic can hand it over. Such a full mapping offers no {@code asIso()}, and such a
 * projection maps as the validated {@code patch}, with no {@code asLens()}, as a bean's guarded
 * read does. The same holds where the {@code null} sits inside the value, at any level the copy
 * reaches: a list's element, a map's value, an array's element, or one inside an {@code Optional}.
 *
 * <p>A type is read as the record declares it and as the spec instantiates it, so a type argument
 * the spec supplies counts, and a wildcard is read through its bound. A pair whose sides agree, or
 * where either side says nothing about {@code null}, keeps the optic it has always had, as does a
 * domain whose canonical constructor takes the {@code null}, a map's keys, which {@code parse} does
 * not scan, and an {@code Optional}, which cannot hold a {@code null} itself.
 *
 * <p>Every spec shares one javac run, under every lint but the processing one, as errors, and is
 * then run.
 */
@DisplayName("A nullable record wire component read into a non-null domain component")
class NullableReadIntoNonNullTest {

  private static final String PKG = "com.marked";

  private static final JavaFileObject MARKED_PACKAGE =
      JavaFileObjects.forSourceString(
          PKG + ".package-info",
          """
          @NullMarked
          package com.marked;

          import org.jspecify.annotations.NullMarked;
          """);

  private static final JavaFileObject WIRES =
      JavaFileObjects.forSourceString(
          PKG + ".Wires",
          """
          package com.marked;

          import java.util.ArrayList;
          import java.util.HashMap;
          import java.util.List;
          import java.util.Map;
          import java.util.Optional;
          import org.higherkindedj.optics.annotations.Flatten;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.jspecify.annotations.NullUnmarked;
          import org.jspecify.annotations.Nullable;

          public final class Wires {
            private Wires() {}

            // A map whose class names its value type first.
            public static final class Inverted<V, K> extends HashMap<K, V> {
              private static final long serialVersionUID = 1L;
            }

            // A list whose class fixes its element type, so it passes none through.
            public static final class Names extends ArrayList<String> {
              private static final long serialVersionUID = 1L;
            }

            // The issue's two shapes: a full mapping and a projection.
            public record Customer(String name, List<String> tags) {}

            public record CustomerDto(@Nullable String name, @Nullable List<String> tags) {}

            @GenerateMapping
            public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}

            public record Account(String id, String name) {}

            public record AccountView(@Nullable String name) {}

            @GenerateMapping
            public interface AccountViewMapping extends MappingSpec<Account, AccountView> {}

            // A flattened member, read into the record it belongs to.
            public record Address(String street) {}

            public record Order(String id, Address address) {}

            public record OrderDto(String id, @Nullable String street) {}

            @GenerateMapping
            public interface OrderMapping extends MappingSpec<Order, OrderDto> {
              @Flatten
              Address address();
            }

            // A null the copy reaches inside the value, one shape to a spec.
            public record Codes(List<String> codes) {}

            public record HoledList(List<@Nullable String> codes) {}

            @GenerateMapping
            public interface HoledListMapping extends MappingSpec<Codes, HoledList> {}

            public record Labels(Map<String, String> labels) {}

            public record HoledMap(Map<String, @Nullable String> labels) {}

            @GenerateMapping
            public interface HoledMapMapping extends MappingSpec<Labels, HoledMap> {}

            public record Tokens(String[] tokens) {}

            public record HoledArray(@Nullable String[] tokens) {}

            @GenerateMapping
            public interface HoledArrayMapping extends MappingSpec<Tokens, HoledArray> {}

            public record Grid(List<List<String>> rows) {}

            public record HoledGrid(List<List<@Nullable String>> rows) {}

            @GenerateMapping
            public interface HoledGridMapping extends MappingSpec<Grid, HoledGrid> {}

            public record Kept(ArrayList<String> kept) {}

            public record HoledKept(ArrayList<@Nullable String> kept) {}

            @GenerateMapping
            public interface HoledKeptMapping extends MappingSpec<Kept, HoledKept> {}

            public record Maybe(Optional<List<String>> maybe) {}

            public record HoledMaybe(Optional<List<@Nullable String>> maybe) {}

            @GenerateMapping
            public interface HoledMaybeMapping extends MappingSpec<Maybe, HoledMaybe> {}

            public record Tagged(String id, List<String> tags) {}

            public record HoledTags(List<@Nullable String> tags) {}

            @GenerateMapping
            public interface HoledTagsMapping extends MappingSpec<Tagged, HoledTags> {}

            public record Wild(List<? extends String> codes) {}

            public record HoledWild(List<? extends @Nullable String> codes) {}

            @GenerateMapping
            public interface HoledWildMapping extends MappingSpec<Wild, HoledWild> {}

            public record Lookup(Inverted<String, String> byKey) {}

            public record HoledLookup(Inverted<@Nullable String, String> byKey) {}

            @GenerateMapping
            public interface HoledLookupMapping extends MappingSpec<Lookup, HoledLookup> {}

            // Nullness a generic record leaves to its type argument, which the spec supplies.
            public record Form<T extends @Nullable Object>(T title) {}

            @GenerateMapping
            public interface FormMapping extends MappingSpec<Title, Form<@Nullable String>> {}

            public record ListForm<T extends @Nullable Object>(List<T> codes) {}

            @GenerateMapping
            public interface ListFormMapping extends MappingSpec<Codes, ListForm<@Nullable String>> {}

            public record Holder<T extends @Nullable Object>(T title) {}

            @GenerateMapping
            public interface HolderMapping extends MappingSpec<Holder<String>, DraftDto> {}

            public record Bag<T extends @Nullable Object>(List<T> codes) {}

            @GenerateMapping
            public interface BagMapping extends MappingSpec<Bag<String>, HoledList> {}

            // Pairs that keep their optic.
            public record Draft(@Nullable String title) {}

            public record DraftDto(@Nullable String title) {}

            @GenerateMapping
            public interface DraftMapping extends MappingSpec<Draft, DraftDto> {}

            @NullUnmarked
            public record Loose(String title) {}

            @GenerateMapping
            public interface LooseMapping extends MappingSpec<Loose, DraftDto> {}

            public record Title(String title) {}

            @NullUnmarked
            public record LooseDto(String title) {}

            @GenerateMapping
            public interface LooseDtoMapping extends MappingSpec<Title, LooseDto> {}

            public record Defaulted(String title) {
              public Defaulted(@Nullable String title) {
                this.title = title == null ? "" : title;
              }
            }

            @GenerateMapping
            public interface DefaultedMapping extends MappingSpec<Defaulted, DraftDto> {}

            @NullUnmarked
            public record LooseCodes(List<String> codes) {}

            @GenerateMapping
            public interface LooseCodesMapping extends MappingSpec<LooseCodes, HoledList> {}

            public record Holes(List<@Nullable String> codes) {}

            @GenerateMapping
            public interface HolesMapping extends MappingSpec<Holes, HoledList> {}

            public record Keyed(Map<String, String> byKey) {}

            public record HoledKeys(Map<@Nullable String, String> byKey) {}

            @GenerateMapping
            public interface HoledKeysMapping extends MappingSpec<Keyed, HoledKeys> {}

            public record HoledKeyLookup(Inverted<String, @Nullable String> byKey) {}

            @GenerateMapping
            public interface HoledKeyLookupMapping extends MappingSpec<Lookup, HoledKeyLookup> {}

            public record HoledOptional(Optional<@Nullable List<String>> maybe) {}

            @GenerateMapping
            public interface HoledOptionalMapping extends MappingSpec<Maybe, HoledOptional> {}

            @SuppressWarnings("rawtypes")
            public record Raw(List codes) {}

            @SuppressWarnings("rawtypes")
            public record RawDto(List codes) {}

            @GenerateMapping
            public interface RawMapping extends MappingSpec<Raw, RawDto> {}

            public record Roster(Names names) {}

            public record RosterDto(Names names) {}

            @GenerateMapping
            public interface RosterMapping extends MappingSpec<Roster, RosterDto> {}

            public record HoledCodes(List<@Nullable String> codes) {}

            @GenerateMapping
            public interface NullableBagMapping extends MappingSpec<Bag<@Nullable String>, HoledCodes> {}

            public record LooseCodesDto(List<@Nullable String> codes) {}

            // The spec says nothing of the type argument it supplies.
            @NullUnmarked
            @GenerateMapping
            public interface LooseBagMapping extends MappingSpec<Bag<String>, LooseCodesDto> {}

            public record NoteDto(@Nullable String title) {}

            @GenerateMapping
            public interface NullableHolderMapping
                extends MappingSpec<Holder<@Nullable String>, NoteDto> {}

            public record Opt<T>(@Nullable T title) {}

            public record OptDto(@Nullable String title) {}

            @GenerateMapping
            public interface OptMapping extends MappingSpec<Opt<String>, OptDto> {}

            public record Note(String id, @Nullable String title) {}

            @GenerateMapping
            public interface NoteViewMapping extends MappingSpec<Note, DraftDto> {}
          }
          """);

  private static RuntimeCompilationHelper.CompiledResult compiled;

  @BeforeAll
  static void compileWires() {
    Compilation compilation =
        javac()
            // The companion processor claims the vocabulary annotations the mapping only reads;
            // no processor claims JSpecify's, which is all the processing lint would report.
            .withProcessors(new MappingProcessor(), new CompanionAnnotationProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(MARKED_PACKAGE, WIRES);
    assertThat(compilation).succeeded();
    compiled = new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  static Stream<String> withoutAnIso() {
    return Stream.of(
        "CustomerMapping",
        "OrderMapping",
        "HoledListMapping",
        "HoledMapMapping",
        "HoledArrayMapping",
        "HoledGridMapping",
        "HoledKeptMapping",
        "HoledMaybeMapping",
        "HoledWildMapping",
        "HoledLookupMapping",
        "FormMapping",
        "ListFormMapping",
        "HolderMapping",
        "BagMapping");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("withoutAnIso")
  @DisplayName("a full mapping whose wire may hand the domain a null it rules out has no asIso()")
  void aFullMappingHasNoIso(String spec) {
    Assertions.assertThat(methodsOf(spec))
        .contains("build", "parse", "asValidatedPrism")
        .doesNotContain("asIso");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  @DisplayName("a full mapping whose sides agree, or where either says nothing, keeps asIso()")
  void aFullMappingKeepsItsIso(String spec) {
    Assertions.assertThat(methodsOf(spec)).contains("asIso");
  }

  static Stream<String> aFullMappingKeepsItsIso() {
    return Stream.of(
        "DraftMapping",
        "LooseMapping",
        "LooseDtoMapping",
        "DefaultedMapping",
        "LooseCodesMapping",
        "HolesMapping",
        "HoledKeysMapping",
        "HoledKeyLookupMapping",
        "HoledOptionalMapping",
        "RawMapping",
        "RosterMapping",
        "NullableBagMapping",
        "LooseBagMapping",
        "NullableHolderMapping",
        "OptMapping");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  @DisplayName("such a projection maps as the validated patch, with no asLens()")
  void aProjectionMapsAsThePatch(String spec) {
    Assertions.assertThat(methodsOf(spec)).contains("build", "patch").doesNotContain("asLens");
  }

  static Stream<String> aProjectionMapsAsThePatch() {
    return Stream.of("AccountViewMapping", "HoledTagsMapping");
  }

  @Test
  @DisplayName("a projection whose sides agree keeps asLens()")
  void aProjectionKeepsItsLens() {
    Assertions.assertThat(methodsOf("NoteViewMapping")).contains("asLens").doesNotContain("patch");
  }

  @Test
  @DisplayName("parse and the patch locate the null the optics would have handed over")
  void parseAndThePatchLocateTheNull() throws ReflectiveOperationException {
    Object customers = compiled.instance(implOf("CustomerMapping"));
    Object empty =
        compiled
            .loadClass(PKG + ".Wires$CustomerDto")
            .getDeclaredConstructor(String.class, List.class)
            .newInstance(null, null);
    assertThatValidated(validated(invoke(customers, "parse", empty)))
        .isInvalid()
        .hasFieldErrors("name: must not be null", "tags: must not be null");

    Object accounts = compiled.instance(implOf("AccountViewMapping"));
    Object account = compiled.newInstance(PKG + ".Wires$Account", "a1", "Ann");
    Object unnamed =
        compiled
            .loadClass(PKG + ".Wires$AccountView")
            .getDeclaredConstructor(String.class)
            .newInstance((Object) null);
    assertThatValidated(validated(invoke(accounts, "patch", account, unnamed)))
        .isInvalid()
        .hasFieldErrors("name: must not be null");
    Object renamed = compiled.newInstance(PKG + ".Wires$AccountView", "Bo");
    assertThatValidated(validated(invoke(accounts, "patch", account, renamed)))
        .isValid()
        .hasValue(compiled.newInstance(PKG + ".Wires$Account", "a1", "Bo"));
  }

  @Test
  @DisplayName("the patch hands the domain a copy of the list it read, not the wire's own")
  void thePatchCopiesWhatItReads() throws ReflectiveOperationException {
    Object tags = compiled.instance(implOf("HoledTagsMapping"));
    Object tagged =
        compiled
            .loadClass(PKG + ".Wires$Tagged")
            .getDeclaredConstructor(String.class, List.class)
            .newInstance("t1", List.of("a"));
    List<String> read = new ArrayList<>(List.of("b", "c"));
    Object wire =
        compiled
            .loadClass(PKG + ".Wires$HoledTags")
            .getDeclaredConstructor(List.class)
            .newInstance(read);
    Object patched =
        validated(invoke(tags, "patch", tagged, wire))
            .orElseThrow(() -> new AssertionError("the patch did not validate"));
    Assertions.assertThat(invoke(patched, "tags")).isEqualTo(read).isNotSameAs(read);
  }

  /** A nested spec's Impl joins the simple names it is nested in. */
  private static String implOf(String spec) {
    return PKG + ".Wires" + spec + "Impl";
  }

  private static List<String> methodsOf(String spec) {
    try {
      return Arrays.stream(compiled.loadClass(implOf(spec)).getDeclaredMethods())
          .map(method -> method.getName())
          .toList();
    } catch (ClassNotFoundException e) {
      throw new AssertionError("no Impl was generated for " + spec, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Validated<?, Object> validated(Object result) {
    return (Validated<?, Object>) result;
  }
}
