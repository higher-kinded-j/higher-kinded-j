// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One sparse update applied to two sources: each result gets a container of its own wherever the
 * update hands over one that can be written into, an array or an {@code IdentityHashMap}, at any
 * level and through a {@code JsonNullable} holder, and shares every unmodifiable one.
 */
@DisplayName("A sparse update applied to several sources gives each its own writable containers")
class SparseUpdateAppliedTwiceTest {

  private static final String PKG = "com.example.twice";

  private static RuntimeCompilationHelper.CompiledResult compiled;

  @BeforeAll
  static void compileFixtures() {
    Compilation compilation =
        javac()
            .withProcessors(new MappingProcessor(), new CompanionAnnotationProcessor())
            .withOptions("-Xlint:all", "-Werror")
            .compile(
                JsonNullableStub.SOURCE,
                JavaFileObjects.forSourceString(
                    PKG + ".Fixtures",
                    """
                    package com.example.twice;

                    import java.util.List;
                    import java.util.Map;
                    import java.util.Optional;
                    import org.higherkindedj.hkt.validated.Validated;
                    import org.higherkindedj.optics.annotations.GenerateMapping;
                    import org.higherkindedj.optics.annotations.MapKey;
                    import org.higherkindedj.optics.annotations.MappingSpec;
                    import org.higherkindedj.optics.annotations.UpdateSpec;
                    import org.higherkindedj.optics.validated.ValidatedPrism;
                    import org.openapitools.jackson.nullable.JsonNullable;

                    public final class Fixtures {
                      private Fixtures() {}

                      public record Code(String value) {}

                      public record Key(String value) {}

                      static ValidatedPrism<String, Code> code() {
                        return ValidatedPrism.of(s -> Validated.validNel(new Code(s)), Code::value);
                      }

                      static ValidatedPrism<String, Key> key() {
                        return ValidatedPrism.of(s -> Validated.validNel(new Key(s)), Key::value);
                      }

                      // The same-typed components, each copied when updateFrom reads the wire.
                      public record Copied(
                          String name,
                          String[] cells,
                          int[] counts,
                          String[][] grid,
                          List<String[]> rows,
                          Map<String, String> byKey,
                          Map<String, String[]> cellsByKey,
                          Optional<String[]> maybe,
                          List<Map<String, String>> maps,
                          List<String> tags,
                          List<List<String>> lists,
                          Map<String, List<String>> listsByKey) {}

                      public static class CopiedPatch {
                        private String name;
                        private String[] cells;
                        private int[] counts;
                        private String[][] grid;
                        private List<String[]> rows;
                        private Map<String, String> byKey;
                        private Map<String, String[]> cellsByKey;
                        private Optional<String[]> maybe;
                        private List<Map<String, String>> maps;
                        private List<String> tags;
                        private List<List<String>> lists;
                        private Map<String, List<String>> listsByKey;

                        public String getName() { return name; }
                        public void setName(String v) { name = v; }
                        public String[] getCells() { return cells; }
                        public void setCells(String[] v) { cells = v; }
                        public int[] getCounts() { return counts; }
                        public void setCounts(int[] v) { counts = v; }
                        public String[][] getGrid() { return grid; }
                        public void setGrid(String[][] v) { grid = v; }
                        public List<String[]> getRows() { return rows; }
                        public void setRows(List<String[]> v) { rows = v; }
                        public Map<String, String> getByKey() { return byKey; }
                        public void setByKey(Map<String, String> v) { byKey = v; }
                        public Map<String, String[]> getCellsByKey() { return cellsByKey; }
                        public void setCellsByKey(Map<String, String[]> v) { cellsByKey = v; }
                        public Optional<String[]> getMaybe() { return maybe; }
                        public void setMaybe(Optional<String[]> v) { maybe = v; }
                        public List<Map<String, String>> getMaps() { return maps; }
                        public void setMaps(List<Map<String, String>> v) { maps = v; }
                        public List<String> getTags() { return tags; }
                        public void setTags(List<String> v) { tags = v; }
                        public List<List<String>> getLists() { return lists; }
                        public void setLists(List<List<String>> v) { lists = v; }
                        public Map<String, List<String>> getListsByKey() { return listsByKey; }
                        public void setListsByKey(Map<String, List<String>> v) { listsByKey = v; }
                      }

                      @GenerateMapping
                      public interface CopiedPatchMapping extends UpdateSpec<Copied, CopiedPatch> {}

                      // The components a leaf lifted over the elements fills, and a @MapKey map.
                      public record Lifted(
                          Code[] codes,
                          List<Code> codeList,
                          Map<String, Code> codeByKey,
                          Map<Key, String[]> keyed,
                          Map<Key, List<String>> keyedLists,
                          Map<Key, String> keyedPlain) {}

                      public static class LiftedPatch {
                        private String[] codes;
                        private List<String> codeList;
                        private Map<String, String> codeByKey;
                        private Map<String, String[]> keyed;
                        private Map<String, List<String>> keyedLists;
                        private Map<String, String> keyedPlain;

                        public String[] getCodes() { return codes; }
                        public void setCodes(String[] v) { codes = v; }
                        public List<String> getCodeList() { return codeList; }
                        public void setCodeList(List<String> v) { codeList = v; }
                        public Map<String, String> getCodeByKey() { return codeByKey; }
                        public void setCodeByKey(Map<String, String> v) { codeByKey = v; }
                        public Map<String, String[]> getKeyed() { return keyed; }
                        public void setKeyed(Map<String, String[]> v) { keyed = v; }
                        public Map<String, List<String>> getKeyedLists() { return keyedLists; }
                        public void setKeyedLists(Map<String, List<String>> v) { keyedLists = v; }
                        public Map<String, String> getKeyedPlain() { return keyedPlain; }
                        public void setKeyedPlain(Map<String, String> v) { keyedPlain = v; }
                      }

                      @GenerateMapping
                      public interface LiftedPatchMapping extends UpdateSpec<Lifted, LiftedPatch> {
                        default ValidatedPrism<String, Code> codes() {
                          return code();
                        }

                        default ValidatedPrism<String, Code> codeList() {
                          return code();
                        }

                        default ValidatedPrism<String, Code> codeByKey() {
                          return code();
                        }

                        @MapKey("keyed")
                        default ValidatedPrism<String, Key> keyedKey() {
                          return key();
                        }

                        @MapKey("keyedLists")
                        default ValidatedPrism<String, Key> keyedListsKey() {
                          return key();
                        }

                        @MapKey("keyedPlain")
                        default ValidatedPrism<String, Key> keyedPlainKey() {
                          return key();
                        }
                      }

                      // The values a JsonNullable holder sends, and what a leaf parses.
                      public record Inner(String[] lines) {}

                      public record InnerDto(String[] lines) {}

                      @GenerateMapping
                      public interface InnerMapping extends MappingSpec<Inner, InnerDto> {}

                      public record Sent(
                          String[] cells,
                          Code[] codes,
                          Map<String, Code> byKey,
                          Optional<String[]> bridged,
                          Optional<List<String>> bridgedList,
                          Inner inner) {}

                      public static class SentPatch {
                        private JsonNullable<String[]> cells = JsonNullable.undefined();
                        private JsonNullable<String[]> codes = JsonNullable.undefined();
                        private JsonNullable<Map<String, String>> byKey = JsonNullable.undefined();
                        private JsonNullable<String[]> bridged = JsonNullable.undefined();
                        private JsonNullable<List<String>> bridgedList = JsonNullable.undefined();
                        private InnerDto inner;

                        public JsonNullable<String[]> getCells() { return cells; }
                        public void setCells(JsonNullable<String[]> v) { cells = v; }
                        public JsonNullable<String[]> getCodes() { return codes; }
                        public void setCodes(JsonNullable<String[]> v) { codes = v; }
                        public JsonNullable<Map<String, String>> getByKey() { return byKey; }
                        public void setByKey(JsonNullable<Map<String, String>> v) { byKey = v; }
                        public JsonNullable<String[]> getBridged() { return bridged; }
                        public void setBridged(JsonNullable<String[]> v) { bridged = v; }
                        public JsonNullable<List<String>> getBridgedList() { return bridgedList; }
                        public void setBridgedList(JsonNullable<List<String>> v) { bridgedList = v; }
                        public InnerDto getInner() { return inner; }
                        public void setInner(InnerDto v) { inner = v; }
                      }

                      @GenerateMapping
                      public interface SentPatchMapping extends UpdateSpec<Sent, SentPatch> {
                        default ValidatedPrism<String, Code> codes() {
                          return code();
                        }

                        default ValidatedPrism<String, Code> byKey() {
                          return code();
                        }
                      }

                      // Bridges into an Optional declared through a wildcard, which copies its value
                      // at the wildcard's bound.
                      public record Wild(
                          Optional<? extends String[]> cells,
                          Optional<? extends String[][]> grid,
                          Optional<? extends Map<String, String>> byKey,
                          Optional<? extends Code[]> codes) {}

                      public static class WildPatch {
                        private JsonNullable<String[]> cells = JsonNullable.undefined();
                        private JsonNullable<String[][]> grid = JsonNullable.undefined();
                        private JsonNullable<Map<String, String>> byKey = JsonNullable.undefined();
                        private JsonNullable<String[]> codes = JsonNullable.undefined();

                        public JsonNullable<String[]> getCells() { return cells; }
                        public void setCells(JsonNullable<String[]> v) { cells = v; }
                        public JsonNullable<String[][]> getGrid() { return grid; }
                        public void setGrid(JsonNullable<String[][]> v) { grid = v; }
                        public JsonNullable<Map<String, String>> getByKey() { return byKey; }
                        public void setByKey(JsonNullable<Map<String, String>> v) { byKey = v; }
                        public JsonNullable<String[]> getCodes() { return codes; }
                        public void setCodes(JsonNullable<String[]> v) { codes = v; }
                      }

                      @GenerateMapping
                      public interface WildPatchMapping extends UpdateSpec<Wild, WildPatch> {
                        default ValidatedPrism<String, Code> codes() {
                          return code();
                        }
                      }

                      // A raw map, copied through its wildcard view on each apply as well.
                      @SuppressWarnings("rawtypes")
                      public record Raw(Map map) {}

                      @SuppressWarnings("rawtypes")
                      public static class RawPatch {
                        private Map map;

                        public Map getMap() { return map; }
                        public void setMap(Map v) { map = v; }
                      }

                      @GenerateMapping
                      public interface RawPatchMapping extends UpdateSpec<Raw, RawPatch> {}
                    }
                    """),
                JavaFileObjects.forSourceString(
                    PKG + ".Probes",
                    """
                    package com.example.twice;

                    import com.example.twice.Fixtures.*;
                    import java.util.ArrayList;
                    import java.util.Arrays;
                    import java.util.IdentityHashMap;
                    import java.util.LinkedHashMap;
                    import java.util.List;
                    import java.util.Map;
                    import java.util.Optional;
                    import java.util.TreeMap;
                    import org.openapitools.jackson.nullable.JsonNullable;

                    public final class Probes {
                      private Probes() {}

                      /** Two keys equals() cannot tell apart, which only an IdentityHashMap keeps. */
                      private static <V> Map<String, V> identity(V first, V second) {
                        Map<String, V> map = new IdentityHashMap<>();
                        map.put(new String("k"), first);
                        map.put(new String("k"), second);
                        return map;
                      }

                      private static <V> Map<String, V> plain(V value) {
                        Map<String, V> map = new LinkedHashMap<>();
                        map.put("k", value);
                        return map;
                      }

                      /** The names whose value is the same object in both results. */
                      private static Map<String, Boolean> same(Object... triples) {
                        Map<String, Boolean> same = new TreeMap<>();
                        for (int i = 0; i < triples.length; i += 3) {
                          same.put((String) triples[i], triples[i + 1] == triples[i + 2]);
                        }
                        return same;
                      }

                      private static Copied copied() {
                        return new Copied(
                            "", new String[0], new int[0], new String[0][], List.of(), Map.of(),
                            Map.of(), Optional.empty(), List.of(), List.of(), List.of(), Map.of());
                      }

                      private static CopiedPatch copiedPatch() {
                        CopiedPatch patch = new CopiedPatch();
                        patch.setName("n");
                        patch.setCells(new String[] {"a", "b"});
                        patch.setCounts(new int[] {1});
                        patch.setGrid(new String[][] {{"g"}});
                        List<String[]> rows = new ArrayList<>();
                        rows.add(new String[] {"r"});
                        patch.setRows(rows);
                        patch.setByKey(identity("v", "w"));
                        patch.setCellsByKey(plain(new String[] {"c"}));
                        patch.setMaybe(Optional.of(new String[] {"m"}));
                        List<Map<String, String>> maps = new ArrayList<>();
                        maps.add(identity("v", "w"));
                        maps.add(plain("v"));
                        patch.setMaps(maps);
                        patch.setTags(new ArrayList<>(List.of("t")));
                        patch.setLists(new ArrayList<>(List.of(new ArrayList<>(List.of("l")))));
                        patch.setListsByKey(plain(new ArrayList<>(List.of("l"))));
                        return patch;
                      }

                      public static Map<String, Boolean> copiedTwice() {
                        var update = FixturesCopiedPatchMappingImpl.INSTANCE.updateFrom(copiedPatch());
                        Copied a = update.apply(copied()).get();
                        Copied b = update.apply(copied()).get();
                        return same(
                            "cells", a.cells(), b.cells(),
                            "counts", a.counts(), b.counts(),
                            "grid", a.grid(), b.grid(),
                            "grid row", a.grid()[0], b.grid()[0],
                            "rows", a.rows(), b.rows(),
                            "rows element", a.rows().get(0), b.rows().get(0),
                            "byKey", a.byKey(), b.byKey(),
                            "cellsByKey", a.cellsByKey(), b.cellsByKey(),
                            "cellsByKey value", a.cellsByKey().get("k"), b.cellsByKey().get("k"),
                            "maybe", a.maybe(), b.maybe(),
                            "maybe value", a.maybe().get(), b.maybe().get(),
                            "maps", a.maps(), b.maps(),
                            "maps identity map", a.maps().get(0), b.maps().get(0),
                            "maps plain map", a.maps().get(1), b.maps().get(1),
                            "tags", a.tags(), b.tags(),
                            "lists", a.lists(), b.lists(),
                            "listsByKey", a.listsByKey(), b.listsByKey());
                      }

                      /** The issue's case: a write into one result reaches neither the other nor the wire. */
                      public static List<String> writesStayApart() {
                        CopiedPatch patch = copiedPatch();
                        var update = FixturesCopiedPatchMappingImpl.INSTANCE.updateFrom(patch);
                        Copied first = update.apply(copied()).get();
                        Copied second = update.apply(copied()).get();
                        first.cells()[0] = "CHANGED";
                        first.byKey().put(new String("k2"), "x");
                        first.grid()[0][0] = "CHANGED";
                        Copied third = update.apply(copied()).get();
                        return List.of(
                            "second.cells " + Arrays.toString(second.cells()),
                            "second.byKey " + second.byKey().size()
                                + " " + second.byKey().getClass().getSimpleName(),
                            "second.grid " + second.grid()[0][0],
                            "third.cells " + Arrays.toString(third.cells()),
                            "third.byKey " + third.byKey().size(),
                            "wire.cells " + Arrays.toString(patch.getCells()),
                            "wire.byKey " + patch.getByKey().size());
                      }

                      public static Map<String, Boolean> liftedTwice() {
                        LiftedPatch patch = new LiftedPatch();
                        patch.setCodes(new String[] {"x"});
                        patch.setCodeList(new ArrayList<>(List.of("x")));
                        patch.setCodeByKey(identity("x", "y"));
                        patch.setKeyed(plain(new String[] {"y"}));
                        patch.setKeyedLists(plain(List.of("l")));
                        patch.setKeyedPlain(plain("p"));
                        Lifted empty = new Lifted(new Code[0], List.of(), Map.of(), Map.of(), Map.of(), Map.of());
                        var update = FixturesLiftedPatchMappingImpl.INSTANCE.updateFrom(patch);
                        Lifted a = update.apply(empty).get();
                        Lifted b = update.apply(empty).get();
                        Key k = new Key("k");
                        Map<String, Boolean> same = same(
                            "codes", a.codes(), b.codes(),
                            "codes element", a.codes()[0], b.codes()[0],
                            "codeList", a.codeList(), b.codeList(),
                            "codeByKey", a.codeByKey(), b.codeByKey(),
                            "keyed", a.keyed(), b.keyed(),
                            "keyed value", a.keyed().get(k), b.keyed().get(k),
                            "keyedLists", a.keyedLists(), b.keyedLists(),
                            "keyedPlain", a.keyedPlain(), b.keyedPlain());
                        same.put(
                            "codeByKey is an IdentityHashMap",
                            a.codeByKey() instanceof IdentityHashMap<?, ?>);
                        return same;
                      }

                      public static Map<String, Boolean> wildTwice() {
                        WildPatch patch = new WildPatch();
                        patch.setCells(JsonNullable.of(new String[] {"w"}));
                        patch.setGrid(JsonNullable.of(new String[][] {{"g"}}));
                        patch.setByKey(JsonNullable.of(identity("v", "w")));
                        patch.setCodes(JsonNullable.of(new String[] {"x"}));
                        Wild empty =
                            new Wild(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                        var update = FixturesWildPatchMappingImpl.INSTANCE.updateFrom(patch);
                        Wild a = update.apply(empty).get();
                        Wild b = update.apply(empty).get();
                        String[][] gridA = a.grid().get();
                        String[][] gridB = b.grid().get();
                        Code[] codesA = a.codes().get();
                        Code[] codesB = b.codes().get();
                        return same(
                            "cells", a.cells().get(), b.cells().get(),
                            "grid", gridA, gridB,
                            "grid row", gridA[0], gridB[0],
                            "byKey", a.byKey().get(), b.byKey().get(),
                            "codes", codesA, codesB,
                            "codes element", codesA[0], codesB[0]);
                      }

                      public static Map<String, Boolean> sentTwice() {
                        SentPatch patch = new SentPatch();
                        patch.setCells(JsonNullable.of(new String[] {"s"}));
                        patch.setCodes(JsonNullable.of(new String[] {"z"}));
                        patch.setByKey(JsonNullable.of(identity("x", "y")));
                        patch.setBridged(JsonNullable.of(new String[] {"b"}));
                        patch.setBridgedList(JsonNullable.of(new ArrayList<>(List.of("l"))));
                        patch.setInner(new InnerDto(new String[] {"i"}));
                        Sent empty =
                            new Sent(
                                new String[0], new Code[0], Map.of(), Optional.empty(),
                                Optional.empty(), new Inner(new String[0]));
                        var update = FixturesSentPatchMappingImpl.INSTANCE.updateFrom(patch);
                        Sent a = update.apply(empty).get();
                        Sent b = update.apply(empty).get();
                        return same(
                            "cells", a.cells(), b.cells(),
                            "codes", a.codes(), b.codes(),
                            "byKey", a.byKey(), b.byKey(),
                            "bridged value", a.bridged().get(), b.bridged().get(),
                            "bridgedList", a.bridgedList(), b.bridgedList(),
                            "inner", a.inner(), b.inner(),
                            "inner lines", a.inner().lines(), b.inner().lines());
                      }

                      /** A bridge's sent null is an empty Optional on every apply. */
                      public static List<String> sentNullTwice() {
                        SentPatch patch = new SentPatch();
                        patch.setBridged(JsonNullable.of(null));
                        Sent empty =
                            new Sent(
                                new String[0], new Code[0], Map.of(), Optional.empty(),
                                Optional.empty(), new Inner(new String[0]));
                        var update = FixturesSentPatchMappingImpl.INSTANCE.updateFrom(patch);
                        return List.of(
                            String.valueOf(update.apply(empty).get().bridged()),
                            String.valueOf(update.apply(empty).get().bridged()));
                      }

                      @SuppressWarnings({"rawtypes", "unchecked"})
                      public static Map<String, Boolean> rawTwice() {
                        RawPatch identityPatch = new RawPatch();
                        identityPatch.setMap(identity("v", "w"));
                        RawPatch plainPatch = new RawPatch();
                        plainPatch.setMap(plain("v"));
                        var identityUpdate = FixturesRawPatchMappingImpl.INSTANCE.updateFrom(identityPatch);
                        var plainUpdate = FixturesRawPatchMappingImpl.INSTANCE.updateFrom(plainPatch);
                        Raw empty = new Raw(Map.of());
                        return same(
                            "identity", identityUpdate.apply(empty).get().map(),
                            identityUpdate.apply(empty).get().map(),
                            "plain", plainUpdate.apply(empty).get().map(),
                            plainUpdate.apply(empty).get().map());
                      }
                    }
                    """));
    assertThat(compilation).succeededWithoutWarnings();
    compiled = new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  private static Object probe(String name) throws ReflectiveOperationException {
    return compiled.invokeStatic(PKG + ".Probes", name);
  }

  /** A probe's names, each with whether its value is the same object in both results. */
  @SuppressWarnings("unchecked") // the Probes method returns exactly this
  private static Map<String, Boolean> same(String name) throws ReflectiveOperationException {
    return (Map<String, Boolean>) probe(name);
  }

  private static String generated(String impl) {
    return compiled.compilation().generatedSourceFiles().stream()
        .filter(file -> file.getName().endsWith("/" + impl + ".java"))
        .findFirst()
        .map(
            file -> {
              try {
                return file.getCharContent(true).toString();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .orElseThrow();
  }

  @Test
  @DisplayName(
      "each apply copies a same-typed array or IdentityHashMap again, at any level, and shares an"
          + " unmodifiable container")
  void eachApplyCopiesWhatCanBeWrittenInto() throws ReflectiveOperationException {
    Assertions.assertThat(same("copiedTwice"))
        .isEqualTo(
            Map.ofEntries(
                Map.entry("cells", false),
                Map.entry("counts", false),
                Map.entry("grid", false),
                Map.entry("grid row", false),
                Map.entry("rows", false),
                Map.entry("rows element", false),
                Map.entry("byKey", false),
                Map.entry("cellsByKey", false),
                Map.entry("cellsByKey value", false),
                Map.entry("maybe", false),
                Map.entry("maybe value", false),
                Map.entry("maps", false),
                Map.entry("maps identity map", false),
                // Nothing can write into these, so every result shares them.
                Map.entry("maps plain map", true),
                Map.entry("tags", true),
                Map.entry("lists", true),
                Map.entry("listsByKey", true)));
  }

  @Test
  @DisplayName("a write into one result reaches neither another result nor the wire")
  void writesStayApart() throws ReflectiveOperationException {
    Assertions.assertThat(probe("writesStayApart"))
        .isEqualTo(
            List.of(
                "second.cells [a, b]",
                "second.byKey 2 IdentityHashMap",
                "second.grid g",
                "third.cells [a, b]",
                "third.byKey 2",
                "wire.cells [a, b]",
                "wire.byKey 2"));
  }

  @Test
  @DisplayName(
      "a lifted array, an IdentityHashMap a lifted map keeps, and a @MapKey map's copied arrays are"
          + " fresh on each apply; what the leaf returns is shared")
  void liftedContainersCopyAgain() throws ReflectiveOperationException {
    Assertions.assertThat(same("liftedTwice"))
        .isEqualTo(
            Map.of(
                "codes", false,
                "codeByKey", false,
                "codeByKey is an IdentityHashMap", true,
                "keyed", false,
                "keyed value", false,
                // The leaf's own values, and the unmodifiable containers around them.
                "codes element", true,
                "codeList", true,
                "keyedLists", true,
                "keyedPlain", true));
  }

  @Test
  @DisplayName(
      "a JsonNullable's sent value is copied again on each apply, a bridged Optional's too, and a"
          + " bridge's sent null stays empty")
  void sentValuesCopyAgain() throws ReflectiveOperationException {
    Assertions.assertThat(same("sentTwice"))
        .containsAllEntriesOf(
            Map.of(
                "cells", false,
                "codes", false,
                "byKey", false,
                "bridged value", false,
                "bridgedList", true));
    Assertions.assertThat(probe("sentNullTwice"))
        .isEqualTo(List.of("Optional.empty", "Optional.empty"));
  }

  @Test
  @DisplayName(
      "what a leaf parses, a nested spec's record and the array it holds included, is shared")
  void whatALeafParsesIsShared() throws ReflectiveOperationException {
    Assertions.assertThat(same("sentTwice"))
        .containsEntry("inner", true)
        .containsEntry("inner lines", true);
  }

  @Test
  @DisplayName(
      "a bridge into an Optional declared through a wildcard copies its value again through map, at"
          + " the wildcard's bound")
  void wildcardBridgesCopyAgain() throws ReflectiveOperationException {
    Assertions.assertThat(same("wildTwice"))
        .isEqualTo(
            Map.of(
                "cells", false,
                "grid", false,
                "grid row", false,
                "byKey", false,
                "codes", false,
                // The leaf's own value.
                "codes element", true));
    Assertions.assertThat(generated("FixturesWildPatchMappingImpl"))
        .contains("(c, v) -> new Components(v.map((String[] e) -> e.clone()), c.grid(),")
        .contains(
            "v.map((String[][] e) -> hkj$copyOf(e, e2 -> hkj$copyOrNull(e2, e3 -> e3.clone())))")
        .contains(
            "v.map((Map<String, String> e) -> (e instanceof IdentityHashMap<?, ?> ? hkj$copyOf(e) :"
                + " e))")
        .contains("c.byKey(), v.map((Fixtures.Code[] e) -> e.clone())))");
  }

  @Test
  @DisplayName("a raw map is copied again on each apply where it is an IdentityHashMap")
  void rawMapsCopyAgain() throws ReflectiveOperationException {
    Assertions.assertThat(same("rawTwice")).isEqualTo(Map.of("identity", false, "plain", true));
    Assertions.assertThat(generated("FixturesRawPatchMappingImpl"))
        .contains("(v instanceof IdentityHashMap<?, ?> ? hkj$copyOf((Map<?, ?>) v) : v)");
  }

  @Test
  @DisplayName(
      "the setter copies on each apply, and copies a map only where it is an IdentityHashMap")
  void theSetterCopies() {
    String copied = generated("FixturesCopiedPatchMappingImpl");
    Assertions.assertThat(copied)
        .contains(
            "Each apply copies again an array or {@code IdentityHashMap} the update's own copies"
                + " made, so no two results of one update share it.")
        .contains("(c, v) -> new Components(c.name(), hkj$copyOrNull(v, e -> e.clone()),")
        .contains("(v instanceof IdentityHashMap<?, ?> ? hkj$copyOf(v) : v)")
        .contains("hkj$copyOf(v, e -> (e instanceof IdentityHashMap<?, ?> ? hkj$copyOf(e) : e))")
        .contains("hkj$copyOf(v, e -> hkj$copyOrNull(e, e2 -> e2.clone()))")
        // Nothing in these can be written into: the setter writes the value as it is.
        .contains("(c, v) -> new Components(v, c.cells(),")
        .contains("c.maps(), v, c.lists(), c.listsByKey()))")
        .contains("c.tags(), v, c.listsByKey()))");
    Assertions.assertThat(generated("FixturesLiftedPatchMappingImpl"))
        .contains("hkj$copyOrNull(v, e -> e.clone())")
        .contains("(v instanceof IdentityHashMap<?, ?> ? hkj$copyOf(v) : v)");
  }
}
