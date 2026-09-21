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
 * null at its full path, however deep.
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
                    import java.util.LinkedHashMap;
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

                      public static boolean keyedValuesPassByReference() {
                        List<String> value = List.of("a");
                        Keyed parsed =
                            FixturesKeyedMappingImpl.INSTANCE
                                .parse(new KeyedDto(Map.of("k", value), Map.of(), Map.of(), null, null))
                                .get();
                        return parsed.byKey().get(new Key("k")) == value;
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
      "a @MapKey map's copied values carry the scan, located under the source key, a wildcard"
          + " value type and a bridged map included, and a valid value still passes by reference")
  void keyedMapValuesAreScanned() throws ReflectiveOperationException {
    assertThatValidated(probe("keyed"))
        .hasFieldErrors(
            "byKey.k.1: must not be null",
            "wild.w.1: must not be null",
            "arrays.r.1: must not be null",
            "bridged.m.1: must not be null",
            "grids.0.0.1: must not be null");
    Assertions.assertThat(compiled.invokeStatic(PKG + ".Probes", "keyedValuesPassByReference"))
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
        .contains(".field(\"grid\", hkj$allPresent(e.grid(), e__ -> hkj$allPresent(e__)))");
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
        .contains("wire.getGrid(), e -> hkj$allPresent(e, e2 -> hkj$allPresent(e2))")
        .contains("wire.getNicknames(), e -> hkj$presentWithin(e, e2 -> hkj$allPresent(e2))");
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
  @DisplayName("a chunked ladder and a flattened group scan their members like any other leg")
  void chunkedAndFlattenedLegsAreScanned() throws ReflectiveOperationException {
    assertThatValidated(probe("wide")).hasFieldErrors("grid.0.1: must not be null");
    assertThatValidated(probe("flattened")).hasFieldErrors("inner.grid.0.1: must not be null");
  }
}
