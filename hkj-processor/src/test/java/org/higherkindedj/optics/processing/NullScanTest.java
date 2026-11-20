// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static java.util.stream.Collectors.joining;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The null scan an identity-copied container carries, on the shapes {@code MappingTierMatrixTest}
 * does not reach: containers nested through several levels and kinds, a set of containers, a queue,
 * a sorted or enum-keyed map, the copied values of a {@code @MapKey} map, a type variable bounded
 * by a container, alone or beside other bounds, a container class that fixes its own element, a raw
 * type only a container's declaration names, the fallible merge, the sparse {@code UpdateSpec}
 * tier, a mapping wider than one {@code fields()} ladder, and a flattened group. Each locates a
 * null at its full path, however deep. A map's null key, which has no rendering to locate by, is
 * reported unlocated under the map, at any depth, and the value under it is not scanned. A key that
 * is itself a container is scanned as a value is, its failures located under its rendering, whether
 * the map is copied or passes its keys through beside the values a leaf or spec converts.
 *
 * <p>Every fixture compiles in one javac run, under {@code -Xlint:all -Werror}, which also holds
 * every emitted scan helper and every call to one clean. Each case calls one static method of the
 * compiled {@code Probes} class.
 */
@DisplayName("A null inside an identity container locates at its full path, at any depth")
class NullScanTest {

  private static final String PKG = "com.example.nullscan";

  private static RuntimeCompilationHelper.CompiledResult compiled;

  @BeforeAll
  static void compileFixtures() {
    String wideComponents =
        IntStream.rangeClosed(1, 16).mapToObj(i -> "String f" + i + ", ").collect(joining());
    Compilation compilation =
        javac()
            // The companion processor claims the vocabulary annotations the others only read.
            .withProcessors(
                new MappingProcessor(), new MergeProcessor(), new CompanionAnnotationProcessor())
            .withOptions("-Xlint:all", "-Werror")
            .compile(
                JavaFileObjects.forSourceString(
                    PKG + ".Fixtures",
                    """
                    package com.example.nullscan;

                    import java.util.ArrayList;
                    import java.util.Deque;
                    import java.util.EnumMap;
                    import java.util.HashMap;
                    import java.util.LinkedHashMap;
                    import java.util.List;
                    import java.util.Map;
                    import java.util.Optional;
                    import java.util.RandomAccess;
                    import java.util.Set;
                    import java.util.SortedMap;
                    import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
                    import org.higherkindedj.hkt.validated.FieldError;
                    import org.higherkindedj.hkt.validated.Validated;
                    import org.higherkindedj.optics.annotations.Flatten;
                    import org.higherkindedj.optics.annotations.GenerateMapping;
                    import org.higherkindedj.optics.annotations.GenerateMerge;
                    import org.higherkindedj.optics.annotations.MapKey;
                    import org.higherkindedj.optics.annotations.MappingSpec;
                    import org.higherkindedj.optics.annotations.OptionalBridge;
                    import org.higherkindedj.optics.annotations.UpdateSpec;
                    import org.higherkindedj.optics.validated.ValidatedPrism;

                    public final class Fixtures {
                      private Fixtures() {}

                      public enum Colour { RED }

                      public record Key(String value) {}

                      static ValidatedPrism<String, Key> key() {
                        return ValidatedPrism.of(raw -> Validated.validNel(new Key(raw)), Key::value);
                      }

                      public record Deep(
                          List<Map<String, String[]>> deep,
                          Set<List<String>> tagged,
                          Deque<String> queue,
                          SortedMap<String, List<String>> sorted,
                          EnumMap<Colour, List<String>> byColour) {}

                      public record DeepDto(
                          List<Map<String, String[]>> deep,
                          Set<List<String>> tagged,
                          Deque<String> queue,
                          SortedMap<String, List<String>> sorted,
                          EnumMap<Colour, List<String>> byColour) {}

                      @GenerateMapping
                      public interface DeepMapping extends MappingSpec<Deep, DeepDto> {}

                      public record Keys(
                          Map<String, String> labels,
                          Map<String, List<String>> byKey,
                          List<Map<String, String>> rows) {}

                      public record KeysDto(
                          Map<String, String> labels,
                          Map<String, List<String>> byKey,
                          List<Map<String, String>> rows) {}

                      @GenerateMapping
                      public interface KeysMapping extends MappingSpec<Keys, KeysDto> {}

                      // Container classes that fix their own element, which can lead back to them.
                      public static class Node extends ArrayList<Node> {
                        private static final long serialVersionUID = 1L;
                      }

                      public static class Json extends LinkedHashMap<String, Json> {
                        private static final long serialVersionUID = 1L;
                      }

                      public interface TreeList extends List<TreeList> {}

                      public static class TreeNode extends ArrayList<TreeList> implements TreeList {
                        private static final long serialVersionUID = 1L;
                      }

                      public static class Grow<T> extends ArrayList<Grow<List<T>>> {
                        private static final long serialVersionUID = 1L;
                      }

                      // A raw type that only the container's own declaration names.
                      @SuppressWarnings("rawtypes")
                      public static class Grid extends ArrayList<List<List>> {
                        private static final long serialVersionUID = 1L;
                      }

                      // A wildcard argument, read through the type parameter's own bound.
                      public static class Bucket<E extends List<String>> extends ArrayList<E> {
                        private static final long serialVersionUID = 1L;
                      }

                      public record Shapes(
                          List<Node> nodes,
                          Json config,
                          TreeList tree,
                          Grow<String> grow,
                          Grid grid,
                          Bucket<?> buckets,
                          List<? extends List<String>> rows) {}

                      public record ShapesDto(
                          List<Node> nodes,
                          Json config,
                          TreeList tree,
                          Grow<String> grow,
                          Grid grid,
                          Bucket<?> buckets,
                          List<? extends List<String>> rows) {}

                      @GenerateMapping
                      public interface ShapesMapping extends MappingSpec<Shapes, ShapesDto> {}

                      public record Keyed(
                          Map<Key, List<String>> byKey,
                          Map<Key, List<? extends CharSequence>> wild,
                          Map<Key, String[]> arrays,
                          Optional<Map<Key, List<String>>> bridged,
                          Optional<Grid> grids) {}

                      public record KeyedDto(
                          Map<String, List<String>> byKey,
                          Map<String, List<? extends CharSequence>> wild,
                          Map<String, String[]> arrays,
                          Map<String, List<String>> bridged,
                          Grid grids) {}

                      @GenerateMapping
                      public interface KeyedMapping extends MappingSpec<Keyed, KeyedDto> {
                        @OptionalBridge
                        Optional<Map<Key, List<String>>> bridged();

                        @OptionalBridge
                        Optional<Grid> grids();

                        @MapKey("bridged")
                        default ValidatedPrism<String, Key> bridgedKey() {
                          return key();
                        }

                        @MapKey("arrays")
                        default ValidatedPrism<String, Key> arraysKey() {
                          return key();
                        }

                        @MapKey("byKey")
                        default ValidatedPrism<String, Key> byKeyKey() {
                          return key();
                        }

                        @MapKey("wild")
                        default ValidatedPrism<String, Key> wildKey() {
                          return key();
                        }
                      }

                      public record Box<
                              T extends List<String>, I extends List<String> & RandomAccess>(
                          T items, List<T> many, I both) {}

                      public record BoxDto<
                              T extends List<String>, I extends List<String> & RandomAccess>(
                          T items, List<T> many, I both) {}

                      @GenerateMapping
                      public interface BoxMapping<
                              T extends List<String>, I extends List<String> & RandomAccess>
                          extends MappingSpec<Box<T, I>, BoxDto<T, I>> {}

                      public record Tree<T extends List<T>>(T kids) {}

                      public record TreeDto<T extends List<T>>(T kids) {}

                      @GenerateMapping
                      public interface TreeMapping<T extends List<T>>
                          extends MappingSpec<Tree<T>, TreeDto<T>> {}

                      public record Names(
                          ArrayList<String> names, List<List<String>> grid, Grid table) {}

                      public record Labels(
                          LinkedHashMap<String, String> labels,
                          Map<String, List<String>> byKey,
                          String note) {}

                      public record Merged(
                          ArrayList<String> names,
                          List<List<String>> grid,
                          LinkedHashMap<String, String> labels,
                          Map<String, List<String>> byKey,
                          String note,
                          Grid table) {}

                      @GenerateMerge
                      public interface MergedAssembly {
                        Validated<NonEmptyList<FieldError>, Merged> merge(Names e, Labels e_);

                        default ValidatedPrism<String, String> note() {
                          return ValidatedPrism.of(Validated::validNel, note -> note);
                        }
                      }

                      public record ConfigSource(Json config) {}

                      public record Configured(Json config, LinkedHashMap<String, String> labels) {}

                      // Total, so it never scans; its fills are still classified.
                      @GenerateMerge
                      public interface ConfigAssembly {
                        Configured merge(ConfigSource source, Labels labels);
                      }

                      public record Profile(
                          List<List<String>> grid,
                          Optional<List<String>> nicknames,
                          String[][] matrix,
                          Map<Key, List<String>> byKey,
                          Grid table,
                          List<? extends List<String>> rows) {}

                      public static class ProfilePatch {
                        private List<List<String>> grid;
                        private Optional<List<String>> nicknames;
                        private String[][] matrix;
                        private Map<String, List<String>> byKey;
                        private Grid table;
                        private List<? extends List<String>> rows;

                        public List<List<String>> getGrid() { return grid; }
                        public void setGrid(List<List<String>> grid) { this.grid = grid; }
                        public Optional<List<String>> getNicknames() { return nicknames; }
                        public void setNicknames(Optional<List<String>> nicknames) {
                          this.nicknames = nicknames;
                        }
                        public String[][] getMatrix() { return matrix; }
                        public void setMatrix(String[][] matrix) { this.matrix = matrix; }
                        public Map<String, List<String>> getByKey() { return byKey; }
                        public void setByKey(Map<String, List<String>> byKey) { this.byKey = byKey; }
                        public Grid getTable() { return table; }
                        public void setTable(Grid table) { this.table = table; }
                        public List<? extends List<String>> getRows() { return rows; }
                        public void setRows(List<? extends List<String>> rows) { this.rows = rows; }
                      }

                      @GenerateMapping
                      public interface ProfilePatchMapping extends UpdateSpec<Profile, ProfilePatch> {
                        @MapKey("byKey")
                        default ValidatedPrism<String, Key> byKeyKey() {
                          return key();
                        }
                      }

                      public record Wide(%1$sList<List<String>> grid) {}

                      public record WideDto(%1$sList<List<String>> grid) {}

                      @GenerateMapping
                      public interface WideMapping extends MappingSpec<Wide, WideDto> {}

                      // Keys that are themselves containers, scanned as values are.
                      public static class Index extends HashMap<Index, String> {
                        private static final long serialVersionUID = 1L;
                      }

                      // A raw key only the map's own declaration names, which the key scan infers.
                      @SuppressWarnings("rawtypes")
                      public static class RawIndex extends HashMap<List, String> {
                        private static final long serialVersionUID = 1L;
                      }

                      public record Paths(
                          Map<List<String>, String> byPath,
                          Map<List<String>, List<String>> both,
                          List<Map<Set<String>, String>> rows,
                          Map<String[], String> arrays,
                          Map<Optional<List<String>>, String> optionals,
                          Index index,
                          RawIndex rawIndex) {}

                      public record PathsDto(
                          Map<List<String>, String> byPath,
                          Map<List<String>, List<String>> both,
                          List<Map<Set<String>, String>> rows,
                          Map<String[], String> arrays,
                          Map<Optional<List<String>>, String> optionals,
                          Index index,
                          RawIndex rawIndex) {}

                      @GenerateMapping
                      public interface PathsMapping extends MappingSpec<Paths, PathsDto> {}

                      static ValidatedPrism<String, Key> checked() {
                        return ValidatedPrism.of(
                            raw ->
                                raw.equals("bad")
                                    ? Validated.invalidNel(FieldError.of("not a key"))
                                    : Validated.validNel(new Key(raw)),
                            Key::value);
                      }

                      public record Item(String name) {}

                      public record ItemDto(String name) {}

                      @GenerateMapping
                      public interface ItemMapping extends MappingSpec<Item, ItemDto> {}

                      public record Coded(
                          Map<List<String>, Key> byPath,
                          Optional<Map<List<String>, Key>> bridged,
                          Map<List<String>, Item> items,
                          Map<Key, Map<List<String>, String>> nested) {}

                      public record CodedDto(
                          Map<List<String>, String> byPath,
                          Map<List<String>, String> bridged,
                          Map<List<String>, ItemDto> items,
                          Map<String, Map<List<String>, String>> nested) {}

                      @GenerateMapping
                      public interface CodedMapping extends MappingSpec<Coded, CodedDto> {
                        default ValidatedPrism<String, Key> byPath() {
                          return checked();
                        }

                        @OptionalBridge
                        default ValidatedPrism<String, Key> bridged() {
                          return checked();
                        }

                        @MapKey("nested")
                        default ValidatedPrism<String, Key> nestedKey() {
                          return key();
                        }
                      }

                      public record PathSource(Map<List<String>, String> byPath) {}

                      public record PathNote(String note) {}

                      public record Pathed(Map<List<String>, String> byPath, String note) {}

                      @GenerateMerge
                      public interface PathedAssembly {
                        Validated<NonEmptyList<FieldError>, Pathed> merge(
                            PathSource source, PathNote note);

                        default ValidatedPrism<String, String> note() {
                          return ValidatedPrism.of(Validated::validNel, note -> note);
                        }
                      }

                      public record Racked(Map<List<String>, String> byPath, Map<List<String>, Key> coded) {}

                      public static class RackPatch {
                        private Map<List<String>, String> byPath;
                        private Map<List<String>, String> coded;

                        public Map<List<String>, String> getByPath() { return byPath; }
                        public void setByPath(Map<List<String>, String> byPath) { this.byPath = byPath; }
                        public Map<List<String>, String> getCoded() { return coded; }
                        public void setCoded(Map<List<String>, String> coded) { this.coded = coded; }
                      }

                      @GenerateMapping
                      public interface RackPatchMapping extends UpdateSpec<Racked, RackPatch> {
                        default ValidatedPrism<String, Key> coded() {
                          return key();
                        }
                      }

                      public record Catalogue(
                          String id, Map<List<String>, Key> coded, Map<List<String>, String> byPath) {}

                      public record CatalogueView(
                          Map<List<String>, String> coded, Map<List<String>, String> byPath) {}

                      @GenerateMapping
                      public interface CatalogueMapping extends MappingSpec<Catalogue, CatalogueView> {
                        default ValidatedPrism<String, Key> coded() {
                          return key();
                        }
                      }

                      public record Inner(List<List<String>> grid) {}

                      public record Outer(String id, Inner inner) {}

                      public record OuterDto(String id, List<List<String>> grid) {}

                      @GenerateMapping
                      public interface OuterMapping extends MappingSpec<Outer, OuterDto> {
                        @Flatten
                        Inner inner();
                      }
                    }
                    """
                        .formatted(wideComponents)),
                JavaFileObjects.forSourceString(
                    PKG + ".Probes",
                    """
                    package com.example.nullscan;

                    import com.example.nullscan.Fixtures.*;
                    import java.util.ArrayList;
                    import java.util.Arrays;
                    import java.util.Collections;
                    import java.util.EnumMap;
                    import java.util.IdentityHashMap;
                    import java.util.LinkedHashMap;
                    import java.util.LinkedHashSet;
                    import java.util.LinkedList;
                    import java.util.List;
                    import java.util.Map;
                    import java.util.Optional;
                    import java.util.Set;
                    import java.util.TreeMap;
                    import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
                    import org.higherkindedj.hkt.validated.FieldError;
                    import org.higherkindedj.hkt.validated.Validated;

                    public final class Probes {
                      private Probes() {}

                      private static <T> List<T> holed(T first) {
                        List<T> list = new ArrayList<>();
                        list.add(first);
                        list.add(null);
                        return list;
                      }

                      public static Validated<NonEmptyList<FieldError>, Deep> deep() {
                        return FixturesDeepMappingImpl.INSTANCE.parse(
                            new DeepDto(
                                List.of(Map.of("k", new String[] {"a", null})),
                                Set.of(holed("b")),
                                new LinkedList<>(holed("c")),
                                new TreeMap<>(Map.of("k", holed("d"))),
                                new EnumMap<>(Map.of(Colour.RED, holed("e")))));
                      }

                      // A null key first, then a present one.
                      private static <V> Map<String, V> nullKeyed(V atNull, V atKey) {
                        Map<String, V> map = new LinkedHashMap<>();
                        map.put(null, atNull);
                        map.put("k", atKey);
                        return map;
                      }

                      public static Validated<NonEmptyList<FieldError>, Keys> keys() {
                        return FixturesKeysMappingImpl.INSTANCE.parse(
                            new KeysDto(
                                nullKeyed("a", null),
                                nullKeyed(holed("b"), holed("c")),
                                List.of(nullKeyed("d", "e"))));
                      }

                      // A Grid holding one List<List>, whose one raw List holds a null second.
                      @SuppressWarnings("rawtypes")
                      private static Grid grid() {
                        Grid grid = new Grid();
                        grid.add(List.<List>of(Arrays.asList("a", null)));
                        return grid;
                      }

                      public static Validated<NonEmptyList<FieldError>, Shapes> shapes() {
                        Node leaf = new Node();
                        leaf.add(null);
                        Node node = new Node();
                        node.add(leaf);
                        Json child = new Json();
                        child.put("x", null);
                        Json config = new Json();
                        config.put("k", child);
                        Bucket<List<String>> buckets = new Bucket<>();
                        buckets.add(holed("b"));
                        return FixturesShapesMappingImpl.INSTANCE.parse(
                            new ShapesDto(
                                List.of(node),
                                config,
                                new TreeNode(),
                                new Grow<>(),
                                grid(),
                                buckets,
                                List.of(holed("r"))));
                      }

                      public static Validated<NonEmptyList<FieldError>, Keyed> keyed() {
                        return FixturesKeyedMappingImpl.INSTANCE.parse(
                            new KeyedDto(
                                Map.of("k", holed("a")),
                                Map.of("w", holed("b")),
                                Map.of("r", new String[] {"c", null}),
                                Map.of("m", holed("d")),
                                grid()));
                      }

                      public static boolean keyedValuesAreCopied() {
                        List<String> value = List.of("a");
                        Keyed parsed =
                            FixturesKeyedMappingImpl.INSTANCE
                                .parse(new KeyedDto(Map.of("k", value), Map.of(), Map.of(), null, null))
                                .get();
                        List<String> copied = parsed.byKey().get(new Key("k"));
                        return copied != value && copied.equals(value);
                      }

                      public static Validated<
                              NonEmptyList<FieldError>, Box<List<String>, ArrayList<String>>>
                          generic() {
                        return FixturesBoxMappingImpl.<List<String>, ArrayList<String>>instance()
                            .parse(
                                new BoxDto<>(
                                    holed("a"), List.of(holed("b")), new ArrayList<>(holed("c"))));
                      }

                      public static Validated<NonEmptyList<FieldError>, Merged> merge() {
                        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
                        labels.put("k", null);
                        return FixturesMergedAssemblyImpl.INSTANCE.merge(
                            new Names(new ArrayList<>(holed("a")), List.of(holed("b")), grid()),
                            new Labels(labels, Map.of("k", holed("c")), "n"));
                      }

                      private static Profile profile() {
                        return new Profile(
                            List.of(),
                            Optional.of(List.of("x")),
                            new String[0][],
                            Map.of(),
                            new Grid(),
                            List.of());
                      }

                      public static Validated<NonEmptyList<FieldError>, Profile> sparse() {
                        ProfilePatch patch = new ProfilePatch();
                        patch.setGrid(List.of(holed("a")));
                        patch.setNicknames(Optional.of(holed("b")));
                        patch.setMatrix(new String[][] {{"c", null}});
                        patch.setByKey(Map.of("k", holed("d")));
                        patch.setTable(grid());
                        patch.setRows(List.of(holed("e")));
                        return FixturesProfilePatchMappingImpl.INSTANCE.updateFrom(patch).apply(profile());
                      }

                      public static Optional<List<String>> sparseEmptyOptional() {
                        ProfilePatch patch = new ProfilePatch();
                        patch.setNicknames(Optional.empty());
                        return FixturesProfilePatchMappingImpl.INSTANCE
                            .updateFrom(patch)
                            .apply(profile())
                            .get()
                            .nicknames();
                      }

                      public static Validated<NonEmptyList<FieldError>, Wide> wide() {
                        String[] f = Collections.nCopies(16, "x").toArray(String[]::new);
                        return FixturesWideMappingImpl.INSTANCE.parse(
                            new WideDto(
                                f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10],
                                f[11], f[12], f[13], f[14], f[15], List.of(holed("a"))));
                      }

                      public static Validated<NonEmptyList<FieldError>, Paths> paths() {
                        Map<List<String>, List<String>> both = new LinkedHashMap<>();
                        both.put(holed("a"), Arrays.asList((String) null));
                        Index inner = new Index();
                        inner.put(null, "x");
                        Index index = new Index();
                        index.put(inner, "v");
                        // A null key first, which is reported unlocated and its value not scanned.
                        Map<List<String>, String> byPath = new LinkedHashMap<>();
                        byPath.put(null, null);
                        byPath.put(holed("p"), "v");
                        return FixturesPathsMappingImpl.INSTANCE.parse(
                            new PathsDto(
                                byPath,
                                both,
                                List.of(Map.of(new LinkedHashSet<>(holed("s")), "v")),
                                Map.of(),
                                Map.of(Optional.of(holed("o")), "v"),
                                index,
                                new RawIndex()));
                      }

                      public static Validated<NonEmptyList<FieldError>, Paths> arrayKeyed() {
                        return FixturesPathsMappingImpl.INSTANCE.parse(
                            new PathsDto(
                                Map.of(),
                                Map.of(),
                                List.of(),
                                Map.of(new String[] {"r", null}, "v"),
                                Map.of(),
                                new Index(),
                                new RawIndex()));
                      }

                      public static Validated<NonEmptyList<FieldError>, Coded> coded() {
                        // The bulk form reports the bad value and the null key, after every key's own.
                        Map<List<String>, String> byPath = new LinkedHashMap<>();
                        byPath.put(List.of("a"), "bad");
                        byPath.put(holed("b"), "x");
                        byPath.put(null, "y");
                        return FixturesCodedMappingImpl.INSTANCE.parse(
                            new CodedDto(
                                byPath,
                                Map.of(holed("c"), "y"),
                                Map.of(holed("d"), new ItemDto("i")),
                                Map.of("k", Map.of(holed("e"), "z"))));
                      }

                      // Two keys equal by equals() but not the same object, which the map keeps,
                      // each under its own object with its own parsed value.
                      public static boolean codedKeepsAnIdentityMap() {
                        List<String> first = new ArrayList<>(List.of("x"));
                        List<String> second = new ArrayList<>(List.of("x"));
                        Map<List<String>, String> byPath = new IdentityHashMap<>();
                        byPath.put(first, "a");
                        byPath.put(second, "b");
                        Map<List<String>, Key> parsed =
                            FixturesCodedMappingImpl.INSTANCE
                                .parse(new CodedDto(byPath, null, Map.of(), Map.of()))
                                .get()
                                .byPath();
                        return parsed instanceof IdentityHashMap<?, ?>
                            && parsed.size() == 2
                            && new Key("a").equals(parsed.get(first))
                            && new Key("b").equals(parsed.get(second));
                      }

                      // Every key is clean, so the bulk form's failure is all there is.
                      public static Validated<NonEmptyList<FieldError>, Coded> codedCleanKeys() {
                        return FixturesCodedMappingImpl.INSTANCE.parse(
                            new CodedDto(Map.of(List.of("a"), "bad"), null, Map.of(), Map.of()));
                      }

                      public static Validated<NonEmptyList<FieldError>, Pathed> pathed() {
                        return FixturesPathedAssemblyImpl.INSTANCE.merge(
                            new PathSource(Map.of(holed("m"), "v")), new PathNote("n"));
                      }

                      public static Validated<NonEmptyList<FieldError>, Racked> racked() {
                        RackPatch patch = new RackPatch();
                        patch.setByPath(Map.of(holed("s"), "v"));
                        patch.setCoded(Map.of(holed("t"), "w"));
                        return FixturesRackPatchMappingImpl.INSTANCE
                            .updateFrom(patch)
                            .apply(new Racked(Map.of(), Map.of()));
                      }

                      public static Validated<NonEmptyList<FieldError>, Catalogue> catalogue() {
                        return FixturesCatalogueMappingImpl.INSTANCE.patch(
                            new Catalogue("c1", Map.of(), Map.of()),
                            new CatalogueView(Map.of(holed("u"), "v"), Map.of(holed("w"), "x")));
                      }

                      public static Validated<NonEmptyList<FieldError>, Outer> flattened() {
                        return FixturesOuterMappingImpl.INSTANCE.parse(
                            new OuterDto("i", List.of(holed("a"))));
                      }
                    }
                    """));
    compiled = new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  @SuppressWarnings("unchecked") // the probes return the generated Validated as Object
  private static Validated<NonEmptyList<FieldError>, Object> probe(String name)
      throws ReflectiveOperationException {
    return (Validated<NonEmptyList<FieldError>, Object>)
        compiled.invokeStatic(PKG + ".Probes", name);
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
        .orElseThrow(() -> new AssertionError("generated source not found: " + impl));
  }

  @Test
  @DisplayName("every fixture, and every scan helper it emits, compiles under -Xlint:all -Werror")
  void compilesWithoutWarnings() {
    assertThat(compiled.compilation()).succeededWithoutWarnings();
  }

  @Test
  @DisplayName(
      "each level locates as its container identifies an element: a list and a queue by"
          + " position, a map by key, a set by the element's rendering, an array by index")
  void everyLevelLocatesAsItsContainerDoes() throws ReflectiveOperationException {
    assertThatValidated(probe("deep"))
        .hasFieldErrors(
            "deep.0.k.1: must not be null",
            "tagged.[b, null].1: must not be null",
            "queue.1: must not be null",
            "sorted.k.1: must not be null",
            "byColour.RED.1: must not be null");
  }

  @Test
  @DisplayName(
      "a map's null key is reported unlocated under the map, at any depth, beside the located"
          + " failures of its other entries, and the value under it is not scanned")
  void aNullKeyIsReportedUnderItsMap() throws ReflectiveOperationException {
    assertThatValidated(probe("keys"))
        .hasFieldErrors(
            "labels: must not contain a null key",
            "labels.k: must not be null",
            "byKey: must not contain a null key",
            "byKey.k.1: must not be null",
            "rows.0: must not contain a null key");
  }

  @Test
  @DisplayName(
      "a @MapKey map's copied values carry the scan, located under the source key, a wildcard"
          + " value type and a bridged map included, and a valid value is handed over as a copy")
  void keyedMapValuesAreScanned() throws ReflectiveOperationException {
    assertThatValidated(probe("keyed"))
        .hasFieldErrors(
            "byKey.k.1: must not be null",
            "wild.w.1: must not be null",
            "arrays.r.1: must not be null",
            "bridged.m.1: must not be null",
            "grids.0.0.1: must not be null");
    Assertions.assertThat(compiled.invokeStatic(PKG + ".Probes", "keyedValuesAreCopied"))
        .isEqualTo(true);
    // The wildcard value type is named: parseEntries would otherwise infer a captured one.
    Assertions.assertThat(generated("FixturesKeyedMappingImpl"))
        .contains(
            "ValidatedParse.<List<? extends CharSequence>, List<? extends CharSequence>>of(e ->"
                + " hkj$allPresent(e))")
        .contains("byKeyKey().parseEntries(v, ValidatedParse.of(e -> hkj$allPresent(e)))");
  }

  @Test
  @DisplayName(
      "a type variable bounded by a container is scanned through its bound, one of several"
          + " included, and a bound naming the variable again ends the walk")
  void typeVariablesAreScannedThroughTheirBound() throws ReflectiveOperationException {
    assertThatValidated(probe("generic"))
        .hasFieldErrors(
            "items.1: must not be null", "many.0.1: must not be null", "both.1: must not be null");
    Assertions.assertThat(generated("FixturesTreeMappingImpl"))
        .contains(".field(\"kids\", hkj$allPresent(wire.kids()))");
  }

  @Test
  @DisplayName(
      "a fallible merge scans its nested and subtype-declared fills, its lambdas clear of the"
          + " author's own parameter names")
  void mergeFillsAreScanned() throws ReflectiveOperationException {
    assertThatValidated(probe("merge"))
        .hasFieldErrors(
            "names.1: must not be null",
            "grid.0.1: must not be null",
            "labels.k: must not be null",
            "byKey.k.1: must not be null",
            "table.0.0.1: must not be null");
    Assertions.assertThat(generated("FixturesMergedAssemblyImpl"))
        .contains("var hkj$grid = hkj$copyOf(e.grid(), e__ -> hkj$copyOf(e__));")
        .contains(".field(\"grid\", hkj$allPresent(hkj$grid, e__ -> hkj$allPresent(e__)))");
  }

  @Test
  @DisplayName(
      "the sparse tier scans nested containers, an Optional's List and a @MapKey map's values,"
          + " and a present empty Optional still sets empty")
  void sparseEditsAreScanned() throws ReflectiveOperationException {
    assertThatValidated(probe("sparse"))
        .hasFieldErrors(
            "grid.0.1: must not be null",
            "nicknames.1: must not be null",
            "matrix.0.1: must not be null",
            "byKey.k.1: must not be null",
            "table.0.0.1: must not be null",
            "rows.0.1: must not be null");
    Assertions.assertThat(compiled.invokeStatic(PKG + ".Probes", "sparseEmptyOptional"))
        .isEqualTo(Optional.empty());
    Assertions.assertThat(generated("FixturesProfilePatchMappingImpl"))
        .contains(
            "hkj$copyOf(wire.getGrid(), e -> hkj$copyOf(e)), e -> hkj$allPresent(e, e2 -> hkj$allPresent(e2))")
        .contains(
            "hkj$copyOf(wire.getNicknames(), e -> hkj$copyOf(e)), e -> hkj$presentWithin(e, e2 -> hkj$allPresent(e2))");
  }

  @Test
  @DisplayName(
      "a container class that fixes its own element is followed once, so the walk ends; a raw"
          + " type its declaration names, and a wildcard read through its parameter's bound, are"
          + " scanned too")
  void selfContainingAndDeclaredContainersAreScanned() throws ReflectiveOperationException {
    // Node inside the List scans its children, and the children, where Node recurs, are checked.
    assertThatValidated(probe("shapes"))
        .hasFieldErrors(
            "nodes.0.0.0: must not be null",
            "config.k.x: must not be null",
            "grid.0.0.1: must not be null",
            "buckets.0.1: must not be null",
            "rows.0.1: must not be null");
    // The raw List the Grid's declaration names reaches an inferred lambda parameter, so the
    // member holding the scan answers for it.
    Assertions.assertThat(generated("FixturesShapesMappingImpl"))
        .containsPattern("@SuppressWarnings\\(\"rawtypes\"\\)\\s+public [^(]* parse\\(");
  }

  @Test
  @DisplayName(
      "a key that is itself a container is scanned as a value is, of any kind and at each level"
          + " its type declares, a class recurring through its own key followed once, its failures"
          + " located under its rendering, an entry's key before its value")
  void aKeyIsScannedInside() throws ReflectiveOperationException {
    assertThatValidated(probe("paths"))
        .hasFieldErrors(
            "byPath: must not contain a null key",
            "byPath.[p, null].1: must not be null",
            "both.[a, null].1: must not be null",
            "both.[a, null].0: must not be null",
            "rows.0.[s, null]: must not contain a null element",
            "optionals.Optional[[o, null]].1: must not be null",
            "index.{null=x}: must not contain a null key");
    // An array renders by its identity, so only the path's ends are fixed.
    FieldError arrayKey = probe("arrayKeyed").getError().head();
    Assertions.assertThat(arrayKey.path()).hasSize(3).startsWith("arrays").endsWith("1");
    Assertions.assertThat(arrayKey.message()).isEqualTo("must not be null");
    Assertions.assertThat(generated("FixturesPathsMappingImpl"))
        .contains(
            ".field(\"both\", hkj$entriesPresent(hkj$both, e -> hkj$allPresent(e), e ->"
                + " hkj$allPresent(e)))")
        // A class that recurs through its own key is followed once.
        .contains(".field(\"index\", hkj$entriesPresent(wire.index(), e -> hkj$valuesPresent(e)))")
        // The raw key a map's own declaration names reaches the key scan's lambda.
        .containsPattern("@SuppressWarnings\\(\"rawtypes\"\\)\\s+public [^(]* parse\\(");
  }

  @Test
  @DisplayName(
      "a map whose values a leaf or spec converts reports the keys it passes through before the"
          + " values, bridged and beside a @MapKey map too, hands back the bulk form's result"
          + " when every key is clean, and keeps an IdentityHashMap")
  void aLiftedMapScansTheKeysItPassesThrough() throws ReflectiveOperationException {
    assertThatValidated(probe("coded"))
        .hasFieldErrors(
            "byPath.[b, null].1: must not be null",
            "byPath.[a]: not a key",
            "byPath: must not contain a null key",
            "bridged.[c, null].1: must not be null",
            "items.[d, null].1: must not be null",
            "nested.k.[e, null].1: must not be null");
    assertThatValidated(probe("codedCleanKeys")).hasFieldErrors("byPath.[a]: not a key");
    Assertions.assertThat(compiled.invokeStatic(PKG + ".Probes", "codedKeepsAnIdentityMap"))
        .isEqualTo(true);
    Assertions.assertThat(generated("FixturesCodedMappingImpl"))
        .contains(
            "hkj$ifPresent(wire.byPath(), v -> hkj$keysScanned(byPath().parseValues(v), v,"
                + " e -> hkj$allPresent(e)))");
  }

  @Test
  @DisplayName("a fallible merge, the sparse tier and the patch scan inside a key too")
  void everyParsingTierScansInsideAKey() throws ReflectiveOperationException {
    assertThatValidated(probe("pathed")).hasFieldErrors("byPath.[m, null].1: must not be null");
    assertThatValidated(probe("racked"))
        .hasFieldErrors(
            "byPath.[s, null].1: must not be null", "coded.[t, null].1: must not be null");
    assertThatValidated(probe("catalogue"))
        .hasFieldErrors(
            "coded.[u, null].1: must not be null", "byPath.[w, null].1: must not be null");
  }

  @Test
  @DisplayName("a chunked ladder and a flattened group scan their members like any other leg")
  void chunkedAndFlattenedLegsAreScanned() throws ReflectiveOperationException {
    assertThatValidated(probe("wide")).hasFieldErrors("grid.0.1: must not be null");
    assertThatValidated(probe("flattened")).hasFieldErrors("inner.grid.0.1: must not be null");
  }
}
