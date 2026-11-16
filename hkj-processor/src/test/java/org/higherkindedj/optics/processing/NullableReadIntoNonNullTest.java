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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
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
 * reaches: a list's element, a map's value or key, an array's element, or one inside an {@code
 * Optional}. A map's key is read at its level, and {@code parse} reports a {@code null} one, though
 * it does not look inside a key.
 *
 * <p>A type is read as the record declares it and as it is instantiated. A type variable with no
 * nullness of its own stands for its type argument, read where that argument is written and in the
 * Impl's package, and an unannotated one in a {@code @NullMarked} scope may not hold a {@code
 * null}; an annotated use decides alone. A wildcard is read through its bound. A pair whose sides
 * agree, or where either side says nothing about {@code null}, keeps the optic it has always had,
 * as do a domain whose canonical constructor takes the {@code null}, a primitive, a key read
 * through {@code ? super} or a raw map, and an {@code Optional}, which cannot hold a {@code null}
 * itself.
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

  /** Specs in a package that says nothing about {@code null}, whose Impls are declared there. */
  private static final JavaFileObject LOOSE_SPECS =
      JavaFileObjects.forSourceString(
          "com.loose.LooseSpecs",
          """
          package com.loose;

          import com.marked.Wires;
          import org.higherkindedj.optics.annotations.Flatten;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.jspecify.annotations.Nullable;

          public final class LooseSpecs {
            private LooseSpecs() {}

            // The argument the spec writes says nothing, here and where the Impl is declared.
            @GenerateMapping
            public interface LooseBagMapping
                extends MappingSpec<Wires.Bag<String>, Wires.LooseCodesDto> {}

            // A type variable in a scope that says nothing may hold a null.
            @GenerateMapping
            public interface LooseItemMapping<T extends @Nullable Object>
                extends MappingSpec<Wires.Item<T>, Wires.ItemDto<T>> {}

            // A type variable whose own bound is non-null rules a null out, whatever its argument.
            @GenerateMapping
            public interface BoundedMapping
                extends MappingSpec<Wires.Bounded<String>, Wires.HoledItemList> {}

            // A flattened group's argument is written by the domain, which is marked.
            @GenerateMapping
            public interface NamedOrderMapping
                extends MappingSpec<Wires.NamedOrder, Wires.NamedOrderDto> {
              @Flatten
              Wires.Named<String> name();
            }
          }
          """);

  private static final JavaFileObject WIRES =
      JavaFileObjects.forSourceString(
          PKG + ".Wires",
          """
          package com.marked;

          import java.lang.annotation.ElementType;
          import java.lang.annotation.Target;
          import java.util.ArrayList;
          import java.util.HashMap;
          import java.util.List;
          import java.util.Map;
          import java.util.Optional;
          import org.higherkindedj.optics.annotations.Flatten;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.jspecify.annotations.NonNull;
          import org.jspecify.annotations.NullUnmarked;
          import org.jspecify.annotations.Nullable;

          public final class Wires {
            private Wires() {}

            // A map whose class names its value type first.
            public static final class Inverted<V, K> extends HashMap<K, V> {
              private static final long serialVersionUID = 1L;
            }

            // A type-use annotation that says nothing of null.
            @Target(ElementType.TYPE_USE)
            public @interface Tag {}

            // Lists whose class names its element through its own type variable.
            public static final class Nested<E extends @Nullable Object> extends ArrayList<List<E>> {
              private static final long serialVersionUID = 1L;
            }

            public static final class ArrayRows<E extends @Nullable Object> extends ArrayList<E[]> {
              private static final long serialVersionUID = 1L;
            }

            public static final class WildRows<E extends @Nullable Object>
                extends ArrayList<List<? extends E>> {
              private static final long serialVersionUID = 1L;
            }

            // A list whose class extends it raw, so it names no element.
            @SuppressWarnings("rawtypes")
            public static final class RawNames extends ArrayList {
              private static final long serialVersionUID = 1L;
            }

            // A list whose element is a member type of another class.
            public static final class Crate {
              public final class Box<T extends @Nullable Object> {}
            }

            public static final class Boxes<E extends @Nullable Object>
                extends ArrayList<Crate.Box<E>> {
              private static final long serialVersionUID = 1L;
            }

            public static final class IntRows extends ArrayList<int[]> {
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

            // A map's key, at any level the copy reaches.
            public record Keyed(Map<String, String> byKey) {}

            public record HoledKeys(Map<@Nullable String, String> byKey) {}

            @GenerateMapping
            public interface HoledKeysMapping extends MappingSpec<Keyed, HoledKeys> {}

            public record HoledKeyLookup(Inverted<String, @Nullable String> byKey) {}

            @GenerateMapping
            public interface HoledKeyLookupMapping extends MappingSpec<Lookup, HoledKeyLookup> {}

            public record KeyRows(List<Map<String, String>> rows) {}

            public record HoledKeyRows(List<Map<@Nullable String, String>> rows) {}

            @GenerateMapping
            public interface HoledKeyRowsMapping extends MappingSpec<KeyRows, HoledKeyRows> {}

            public record MaybeKeys(Optional<Map<String, String>> byKey) {}

            public record HoledMaybeKeys(Optional<Map<@Nullable String, String>> byKey) {}

            @GenerateMapping
            public interface HoledMaybeKeysMapping extends MappingSpec<MaybeKeys, HoledMaybeKeys> {}

            public record WildKeys(Map<? extends String, String> byKey) {}

            public record HoledWildKeys(Map<? extends @Nullable String, String> byKey) {}

            @GenerateMapping
            public interface HoledWildKeysMapping extends MappingSpec<WildKeys, HoledWildKeys> {}

            public record Shelf(String id, Map<String, String> byKey) {}

            @GenerateMapping
            public interface HoledKeysViewMapping extends MappingSpec<Shelf, HoledKeys> {}

            // A key a type variable leaves to its argument, which the spec supplies.
            public record KeyedBy<K extends @Nullable Object>(Map<K, String> byKey) {}

            @GenerateMapping
            public interface KeyedByMapping extends MappingSpec<Keyed, KeyedBy<@Nullable String>> {}

            public record Slot<T extends @Nullable Object>(T byKey) {}

            @GenerateMapping
            public interface SlotMapping
                extends MappingSpec<Keyed, Slot<Map<@Nullable String, String>>> {}

            // An annotated type variable names no key of its own, so its argument's is read.
            public record PinnedKeys<M extends Map<@Nullable String, String>>(@NonNull M byKey) {}

            @GenerateMapping
            public interface PinnedKeysMapping
                extends MappingSpec<Keyed, PinnedKeys<Map<@Nullable String, String>>> {}

            public record PinnedValues<M extends Map<String, @Nullable String>>(@NonNull M labels) {}

            @GenerateMapping
            public interface PinnedValuesMapping
                extends MappingSpec<Labels, PinnedValues<Map<String, @Nullable String>>> {}

            public record PinnedTags<L extends List<@Nullable String>>(@NonNull L codes) {}

            @GenerateMapping
            public interface PinnedTagsMapping
                extends MappingSpec<Codes, PinnedTags<List<@Nullable String>>> {}

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

            // The spec says nothing, but its Impl is declared in the marked package.
            public record UncheckedCodes(List<@Nullable String> codes) {}

            @NullUnmarked
            @GenerateMapping
            public interface UnmarkedSpecMapping extends MappingSpec<Bag<String>, UncheckedCodes> {}

            // An annotated use decides alone, whatever the argument.
            public record Pinned<T extends @Nullable Object>(@NonNull T title) {}

            @GenerateMapping
            public interface PinnedMapping extends MappingSpec<Pinned<@Nullable String>, NoteDto> {}

            public record PinnedBag<T extends @Nullable Object>(List<@NonNull T> codes) {}

            @GenerateMapping
            public interface PinnedBagMapping
                extends MappingSpec<PinnedBag<@Nullable String>, HoledList> {}

            // A level below a type variable is read where its argument is written.
            @NullUnmarked
            public record UPage<T>(List<T> items) {}

            public record HoledItems(List<List<@Nullable String>> items) {}

            @GenerateMapping
            public interface UPageMapping extends MappingSpec<UPage<List<String>>, HoledItems> {}

            // A type variable in a marked scope may stand for a type that holds no null.
            public record Item<T extends @Nullable Object>(T item) {}

            public record ItemDto<T extends @Nullable Object>(@Nullable T item) {}

            @GenerateMapping
            public interface ItemMapping<T extends @Nullable Object>
                extends MappingSpec<Item<T>, ItemDto<T>> {}

            public record Items<T extends @Nullable Object>(List<T> items) {}

            public record ItemsDto<T extends @Nullable Object>(List<@Nullable T> items) {}

            @GenerateMapping
            public interface ItemsMapping<T extends @Nullable Object>
                extends MappingSpec<Items<T>, ItemsDto<T>> {}

            // Below an annotated type variable, the scan's own element type is read.
            public record Racks<L extends List<String>>(List<@NonNull L> racks) {}

            public record HoledRacks(List<List<@Nullable String>> racks) {}

            @GenerateMapping
            public interface RacksMapping extends MappingSpec<Racks<List<String>>, HoledRacks> {}

            public record Labelled<T extends @Nullable Object>(@Tag T title) {}

            @GenerateMapping
            public interface LabelledMapping extends MappingSpec<Labelled<String>, OptDto> {}

            public record Named<T extends @Nullable Object>(T first) {}

            public record NamedOrder(String id, Named<String> name) {}

            public record NamedOrderDto(String id, @Nullable String first) {}

            public record Bounded<T>(List<T> items) {}

            public record HoledItemList(List<@Nullable String> items) {}

            public record Grid2(Nested<String> rows) {}

            public record HoledGrid2(Nested<@Nullable String> rows) {}

            @GenerateMapping
            public interface NestedMapping extends MappingSpec<Grid2, HoledGrid2> {}

            public record Sheet(ArrayRows<String> rows) {}

            public record HoledSheet(ArrayRows<@Nullable String> rows) {}

            @GenerateMapping
            public interface ArrayRowsMapping extends MappingSpec<Sheet, HoledSheet> {}

            public record Ledger(WildRows<String> rows) {}

            public record HoledLedger(WildRows<@Nullable String> rows) {}

            @GenerateMapping
            public interface WildRowsMapping extends MappingSpec<Ledger, HoledLedger> {}

            // Pairs that keep their optic.

            public record RawRoster(RawNames names) {}

            public record RawRosterDto(RawNames names) {}

            @GenerateMapping
            public interface RawRosterMapping extends MappingSpec<RawRoster, RawRosterDto> {}

            public record Shelving(Boxes<String> boxes) {}

            public record ShelvingDto(Boxes<@Nullable String> boxes) {}

            @GenerateMapping
            public interface ShelvingMapping extends MappingSpec<Shelving, ShelvingDto> {}

            public record Ints(IntRows rows) {}

            public record IntsDto(IntRows rows) {}

            @GenerateMapping
            public interface IntsMapping extends MappingSpec<Ints, IntsDto> {}

            // A captured wildcard declares nothing of its own, so it says nothing.
            public record AnyList(List<?> items) {}

            public record HoledAnyList(List<? extends @Nullable Object> items) {}

            @GenerateMapping
            public interface AnyListMapping extends MappingSpec<AnyList, HoledAnyList> {}

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

            // Keys whose sides agree, or that say nothing.
            public record NullableKeys(Map<@Nullable String, String> byKey) {}

            @GenerateMapping
            public interface NullableKeysMapping extends MappingSpec<NullableKeys, HoledKeys> {}

            public record SuperKeys(Map<? super String, String> byKey) {}

            public record HoledSuperKeys(Map<? super @Nullable String, String> byKey) {}

            @GenerateMapping
            public interface HoledSuperKeysMapping extends MappingSpec<SuperKeys, HoledSuperKeys> {}

            @SuppressWarnings("rawtypes")
            public record RawKeys(Map byKey) {}

            @SuppressWarnings("rawtypes")
            public record RawKeysDto(Map byKey) {}

            @GenerateMapping
            public interface RawKeysMapping extends MappingSpec<RawKeys, RawKeysDto> {}

            public record Holding<M extends Map<String, String>>(M byKey) {}

            public record HoldingDto<M extends Map<String, String>>(M byKey) {}

            @GenerateMapping
            public interface HoldingMapping<M extends Map<String, String>>
                extends MappingSpec<Holding<M>, HoldingDto<M>> {}

            // javac takes ? and ? extends Object for one argument: a key unnamed says nothing.
            public record AnyKeys(Map<?, String> byKey) {}

            public record ObjectKeys(Map<? extends @Nullable Object, String> byKey) {}

            @GenerateMapping
            public interface AnyKeysMapping extends MappingSpec<AnyKeys, ObjectKeys> {}

            public record AnyKeyRows(List<Map<?, String>> rows) {}

            public record ObjectKeyRows(List<Map<? extends @Nullable Object, String>> rows) {}

            @GenerateMapping
            public interface AnyKeyRowsMapping extends MappingSpec<AnyKeyRows, ObjectKeyRows> {}

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

            public record NoteDto(@Nullable String title) {}

            @GenerateMapping
            public interface NullableHolderMapping
                extends MappingSpec<Holder<@Nullable String>, NoteDto> {}

            public record Opt<T>(@Nullable T title) {}

            public record OptDto(@Nullable String title) {}

            @GenerateMapping
            public interface OptMapping extends MappingSpec<Opt<String>, OptDto> {}

            public record PinnedForm<T extends @Nullable Object>(@NonNull T title) {}

            @GenerateMapping
            public interface PinnedFormMapping extends MappingSpec<Title, PinnedForm<@Nullable String>> {}

            public record Opened<T extends @Nullable Object>(T title) {
              public Opened(@Nullable T title) {
                this.title = title;
              }
            }

            @GenerateMapping
            public interface OpenedMapping extends MappingSpec<Opened<String>, DraftDto> {}

            public record Count(int n) {}

            public record CountDto(@com.legacy.Nullable int n) {}

            @GenerateMapping
            public interface CountMapping extends MappingSpec<Count, CountDto> {}

            public record LegacyBox<T extends @Nullable Object>(@com.legacy.Nullable T code) {}

            public record CodeDto(@Nullable String code) {}

            @GenerateMapping
            public interface LegacyBoxMapping extends MappingSpec<LegacyBox<String>, CodeDto> {}

            public record OptBag<T extends @Nullable Object>(List<@Nullable T> codes) {}

            @GenerateMapping
            public interface OptBagMapping extends MappingSpec<OptBag<String>, HoledList> {}

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
            .compile(MARKED_PACKAGE, LEGACY_NULLABLE, WIRES, LOOSE_SPECS);
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
        "BagMapping",
        "UnmarkedSpecMapping",
        "PinnedMapping",
        "PinnedBagMapping",
        "UPageMapping",
        "ItemMapping",
        "ItemsMapping",
        "RacksMapping",
        "LabelledMapping",
        "NestedMapping",
        "ArrayRowsMapping",
        "WildRowsMapping",
        "HoledKeysMapping",
        "HoledKeyLookupMapping",
        "HoledKeyRowsMapping",
        "HoledMaybeKeysMapping",
        "HoledWildKeysMapping",
        "KeyedByMapping",
        "SlotMapping",
        "PinnedKeysMapping",
        "PinnedValuesMapping",
        "PinnedTagsMapping");
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
        "NullableKeysMapping",
        "HoledSuperKeysMapping",
        "RawKeysMapping",
        "HoldingMapping",
        "AnyKeysMapping",
        "AnyKeyRowsMapping",
        "HoledOptionalMapping",
        "RawMapping",
        "RosterMapping",
        "NullableBagMapping",
        "NullableHolderMapping",
        "OptMapping",
        "PinnedFormMapping",
        "OpenedMapping",
        "CountMapping",
        "LegacyBoxMapping",
        "OptBagMapping",
        "AnyListMapping",
        "RawRosterMapping",
        "ShelvingMapping",
        "IntsMapping");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  @DisplayName("such a projection maps as the validated patch, with no asLens()")
  void aProjectionMapsAsThePatch(String spec) {
    Assertions.assertThat(methodsOf(spec)).contains("build", "patch").doesNotContain("asLens");
  }

  static Stream<String> aProjectionMapsAsThePatch() {
    return Stream.of("AccountViewMapping", "HoledTagsMapping", "HoledKeysViewMapping");
  }

  @Test
  @DisplayName(
      "a spec in a package that says nothing writes arguments that say nothing, but a flattened"
          + " group's arguments are written by its domain, and a non-null bound holds")
  void aSpecOutsideTheMarkedPackage() {
    Assertions.assertThat(methodsOfImpl("com.loose.LooseSpecsLooseBagMappingImpl"))
        .contains("asIso");
    Assertions.assertThat(methodsOfImpl("com.loose.LooseSpecsLooseItemMappingImpl"))
        .contains("asIso");
    Assertions.assertThat(methodsOfImpl("com.loose.LooseSpecsNamedOrderMappingImpl"))
        .contains("build", "parse")
        .doesNotContain("asIso");
    Assertions.assertThat(methodsOfImpl("com.loose.LooseSpecsBoundedMappingImpl"))
        .contains("build", "parse")
        .doesNotContain("asIso");
  }

  @Test
  @DisplayName("a projection whose sides agree keeps asLens()")
  void aProjectionKeepsItsLens() {
    Assertions.assertThat(methodsOf("NoteViewMapping")).contains("asLens").doesNotContain("patch");
  }

  @Test
  @DisplayName(
      "parse and the patch report the null the optics would have handed over, a map's key"
          + " included")
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

    Object keyless = wire("HoledKeys", Map.class, nullKeyed());
    assertThatValidated(
            validated(invoke(compiled.instance(implOf("HoledKeysMapping")), "parse", keyless)))
        .isInvalid()
        .hasFieldErrors("byKey: must not contain a null key");
    Object shelf =
        compiled
            .loadClass(PKG + ".Wires$Shelf")
            .getDeclaredConstructor(String.class, Map.class)
            .newInstance("s1", Map.of("a", "b"));
    assertThatValidated(
            validated(
                invoke(compiled.instance(implOf("HoledKeysViewMapping")), "patch", shelf, keyless)))
        .isInvalid()
        .hasFieldErrors("byKey: must not contain a null key");
  }

  @Test
  @DisplayName(
      "a mapping whose domain may hold a null key keeps asIso(), which copies the key that parse"
          + " reports")
  void aDomainThatHoldsANullKeyKeepsItsIso() throws ReflectiveOperationException {
    Map<String, String> keys = nullKeyed();
    Object holed = wire("HoledKeys", Map.class, keys);
    Object agreeing = compiled.instance(implOf("NullableKeysMapping"));
    assertThatValidated(validated(invoke(agreeing, "parse", holed)))
        .isInvalid()
        .hasFieldErrors("byKey: must not contain a null key");
    @SuppressWarnings("unchecked")
    Iso<Object, Object> iso = (Iso<Object, Object>) invoke(agreeing, "asIso");
    Assertions.assertThat(invoke(iso.reverseGet(holed), "byKey")).isEqualTo(keys);
  }

  /** A map holding a null key, beside a present one. */
  private static Map<String, String> nullKeyed() {
    Map<String, String> keys = new HashMap<>();
    keys.put(null, "v");
    keys.put("k", "w");
    return keys;
  }

  /** A wire of the given simple name, made by its one-component constructor. */
  private static Object wire(String name, Class<?> component, Object value)
      throws ReflectiveOperationException {
    return compiled
        .loadClass(PKG + ".Wires$" + name)
        .getDeclaredConstructor(component)
        .newInstance(value);
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
    return methodsOfImpl(implOf(spec));
  }

  private static List<String> methodsOfImpl(String impl) {
    try {
      return Arrays.stream(compiled.loadClass(impl).getDeclaredMethods())
          .map(method -> method.getName())
          .toList();
    } catch (ClassNotFoundException e) {
      throw new AssertionError("no Impl was generated as " + impl, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Validated<?, Object> validated(Object result) {
    return (Validated<?, Object>) result;
  }
}
