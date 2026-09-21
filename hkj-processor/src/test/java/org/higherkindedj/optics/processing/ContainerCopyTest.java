// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The copy an identity leg hands over in place of the container it read: on every tier, in both
 * directions, the wire and the domain never share a container whose declared type has a copy, at
 * any depth; the copy is unmodifiable, keeps the source's order and carries a null element as it
 * is; and a type with no copy (a subtype, an element declared through a wildcard, the collections
 * inside an array or a set) is handed over as it is.
 *
 * <p>Every fixture compiles in one javac run, under {@code -Xlint:all -Werror}, which also holds
 * every emitted copy helper and every call to one clean. Each case calls one static method of the
 * compiled {@code Probes} class, which answers with the components whose container the two sides
 * still share, or with what a copy turned out to be.
 */
@DisplayName("An identity leg hands over a copy, never the container it read")
class ContainerCopyTest {

  private static final String PKG = "com.example.copy";

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
                    package com.example.copy;

                    import java.util.ArrayList;
                    import java.util.Collection;
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
                    import org.higherkindedj.optics.annotations.UpdateSpec;
                    import org.higherkindedj.optics.validated.ValidatedPrism;

                    public final class Fixtures {
                      private Fixtures() {}

                      public record Key(String value) {}

                      static ValidatedPrism<String, Key> key() {
                        return ValidatedPrism.of(raw -> Validated.validNel(new Key(raw)), Key::value);
                      }

                      public record Flat(
                          List<String> list,
                          Set<String> set,
                          Collection<String> coll,
                          Map<String, String> map,
                          CharSequence[] array,
                          int[] ints,
                          byte[] bytes,
                          ArrayList<String> arrayList,
                          Optional<String> plain) {}

                      public record FlatDto(
                          List<String> list,
                          Set<String> set,
                          Collection<String> coll,
                          Map<String, String> map,
                          CharSequence[] array,
                          int[] ints,
                          byte[] bytes,
                          ArrayList<String> arrayList,
                          Optional<String> plain) {}

                      @GenerateMapping
                      public interface FlatMapping extends MappingSpec<Flat, FlatDto> {}

                      public record Nested(
                          List<List<List<String>>> deep,
                          Set<List<String>> sets,
                          Collection<List<String>> colls,
                          Map<String, List<String>> maps,
                          String[][] matrix,
                          Optional<List<String>> maybe,
                          Optional<int[]> maybeInts,
                          List<? extends List<String>> wild,
                          List<ArrayList<String>> subtypes,
                          Set<Collection<String>> bags,
                          Collection<Collection<String>> heaps) {}

                      public record NestedDto(
                          List<List<List<String>>> deep,
                          Set<List<String>> sets,
                          Collection<List<String>> colls,
                          Map<String, List<String>> maps,
                          String[][] matrix,
                          Optional<List<String>> maybe,
                          Optional<int[]> maybeInts,
                          List<? extends List<String>> wild,
                          List<ArrayList<String>> subtypes,
                          Set<Collection<String>> bags,
                          Collection<Collection<String>> heaps) {}

                      @GenerateMapping
                      public interface NestedMapping extends MappingSpec<Nested, NestedDto> {}

                      @SuppressWarnings("rawtypes")
                      public record Raw(
                          List list, Map map, Set set, Collection coll, List<List> lists,
                          List<List<List>> deep) {}

                      @SuppressWarnings("rawtypes")
                      public record RawDto(
                          List list, Map map, Set set, Collection coll, List<List> lists,
                          List<List<List>> deep) {}

                      @GenerateMapping
                      public interface RawMapping extends MappingSpec<Raw, RawDto> {}

                      // A raw type nested in a lambda parameter warns, on asIso and asLens too.
                      @SuppressWarnings("rawtypes")
                      public record RawAccount(String id, List<List<List>> deep) {}

                      @SuppressWarnings("rawtypes")
                      public record RawView(List<List<List>> deep) {}

                      @GenerateMapping
                      public interface RawViewMapping extends MappingSpec<RawAccount, RawView> {}

                      // An array's runtime type can be narrower than it is declared.
                      public record Rows(List<String>[] lists, String[][] grid) {}

                      public record RowsDto(List<String>[] lists, String[][] grid) {}

                      @GenerateMapping
                      public interface RowsMapping extends MappingSpec<Rows, RowsDto> {}

                      // A bean read and written through its getters and setters.
                      public record Member(String name, List<String> tags) {}

                      public static class MemberBean {
                        private String name;
                        private List<String> tags;

                        public String getName() { return name; }
                        public void setName(String name) { this.name = name; }
                        public List<String> getTags() { return tags; }
                        public void setTags(List<String> tags) { this.tags = tags; }
                      }

                      @GenerateMapping
                      public interface MemberMapping extends MappingSpec<Member, MemberBean> {}

                      // A projection whose reads are total keeps asLens(); one with a leaf patches.
                      public record Account(String id, List<String> tags, Map<Key, List<String>> byKey) {}

                      public record AccountView(List<String> tags) {}

                      @GenerateMapping
                      public interface AccountViewMapping extends MappingSpec<Account, AccountView> {}

                      public record AccountForm(String id, List<String> tags) {}

                      @GenerateMapping
                      public interface AccountFormMapping extends MappingSpec<Account, AccountForm> {
                        default ValidatedPrism<String, String> id() {
                          return ValidatedPrism.of(Validated::validNel, id -> id);
                        }
                      }

                      // A @MapKey map whose values copy, one whose values do not, and a bridge.
                      public record Keyed(
                          Map<Key, List<String>> byKey,
                          Map<Key, String> names,
                          Optional<List<String>> nicknames) {}

                      public record KeyedDto(
                          Map<String, List<String>> byKey,
                          Map<String, String> names,
                          List<String> nicknames) {}

                      @GenerateMapping
                      public interface KeyedMapping extends MappingSpec<Keyed, KeyedDto> {
                        @OptionalBridge
                        Optional<List<String>> nicknames();

                        @MapKey("byKey")
                        default ValidatedPrism<String, Key> byKeyKey() {
                          return key();
                        }

                        @MapKey("names")
                        default ValidatedPrism<String, Key> namesKey() {
                          return key();
                        }
                      }

                      public static class AccountPatch {
                        private List<String> tags;
                        private Map<String, List<String>> byKey;

                        public List<String> getTags() { return tags; }
                        public void setTags(List<String> tags) { this.tags = tags; }
                        public Map<String, List<String>> getByKey() { return byKey; }
                        public void setByKey(Map<String, List<String>> byKey) { this.byKey = byKey; }
                      }

                      @GenerateMapping
                      public interface AccountPatchMapping extends UpdateSpec<Account, AccountPatch> {
                        @MapKey("byKey")
                        default ValidatedPrism<String, Key> byKeyKey() {
                          return key();
                        }
                      }

                      public record Scores(int[] scores) {}

                      public static class ScoresPatch {
                        private int[] scores;

                        public int[] getScores() { return scores; }
                        public void setScores(int[] scores) { this.scores = scores; }
                      }

                      @GenerateMapping
                      public interface ScoresPatchMapping extends UpdateSpec<Scores, ScoresPatch> {}

                      public record Names(List<String> names) {}

                      public record Labels(Map<String, String> labels, String note) {}

                      public record Merged(List<String> names, Map<String, String> labels, String note) {}

                      @GenerateMerge
                      public interface MergedAssembly {
                        Merged merge(Names names, Labels labels);
                      }

                      @GenerateMerge
                      public interface CheckedAssembly {
                        Validated<NonEmptyList<FieldError>, Merged> merge(Names names, Labels labels);

                        default ValidatedPrism<String, String> note() {
                          return ValidatedPrism.of(Validated::validNel, note -> note);
                        }
                      }

                      public record Wide(%1$sList<String> tags) {}

                      public record WideDto(%1$sList<String> tags) {}

                      @GenerateMapping
                      public interface WideMapping extends MappingSpec<Wide, WideDto> {}

                      public record Inner(List<String> tags) {}

                      public record Outer(String id, Inner inner) {}

                      public record OuterDto(String id, List<String> tags) {}

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
                    package com.example.copy;

                    import com.example.copy.Fixtures.*;
                    import java.util.ArrayDeque;
                    import java.util.ArrayList;
                    import java.util.Arrays;
                    import java.util.Collection;
                    import java.util.Collections;
                    import java.util.LinkedHashMap;
                    import java.util.LinkedHashSet;
                    import java.util.List;
                    import java.util.Map;
                    import java.util.Optional;
                    import java.util.Set;
                    import java.util.TreeMap;

                    public final class Probes {
                      private Probes() {}

                      private static List<String> list(String... values) {
                        return new ArrayList<>(Arrays.asList(values));
                      }

                      private static <T> List<T> holed(T first) {
                        List<T> list = new ArrayList<>();
                        list.add(first);
                        list.add(null);
                        return list;
                      }

                      private static <T> Map<String, T> map(String key, T value) {
                        Map<String, T> map = new LinkedHashMap<>();
                        map.put(key, value);
                        return map;
                      }

                      /** The names whose two values are one and the same object. */
                      private static List<String> shared(Object... pairs) {
                        List<String> shared = new ArrayList<>();
                        for (int i = 0; i < pairs.length; i += 3) {
                          if (pairs[i + 1] == pairs[i + 2]) {
                            shared.add((String) pairs[i]);
                          }
                        }
                        return shared;
                      }

                      private static Flat flat() {
                        return new Flat(
                            list("a"),
                            new LinkedHashSet<>(list("s")),
                            new ArrayDeque<>(list("c")),
                            map("k", "v"),
                            new String[] {"x"},
                            new int[] {1},
                            new byte[] {2},
                            new ArrayList<>(list("l")),
                            Optional.of("p"));
                      }

                      private static FlatDto flatDto() {
                        Flat f = flat();
                        return new FlatDto(
                            f.list(), f.set(), f.coll(), f.map(), f.array(), f.ints(), f.bytes(),
                            f.arrayList(), f.plain());
                      }

                      private static List<String> flatShared(Flat d, FlatDto w) {
                        return shared(
                            "list", d.list(), w.list(),
                            "set", d.set(), w.set(),
                            "coll", d.coll(), w.coll(),
                            "map", d.map(), w.map(),
                            "array", d.array(), w.array(),
                            "ints", d.ints(), w.ints(),
                            "bytes", d.bytes(), w.bytes(),
                            "arrayList", d.arrayList(), w.arrayList());
                      }

                      public static List<String> flatParse() {
                        FlatDto wire = flatDto();
                        return flatShared(FixturesFlatMappingImpl.INSTANCE.parse(wire).get(), wire);
                      }

                      public static List<String> flatBuild() {
                        Flat domain = flat();
                        return flatShared(domain, FixturesFlatMappingImpl.INSTANCE.build(domain));
                      }

                      public static List<String> flatIso() {
                        FlatDto wire = flatDto();
                        return flatShared(FixturesFlatMappingImpl.INSTANCE.asIso().reverseGet(wire), wire);
                      }

                      private static boolean rejectsAdd(Collection<String> values) {
                        try {
                          values.add("x");
                          return false;
                        } catch (UnsupportedOperationException expected) {
                          return true;
                        }
                      }

                      /** What each copy turned out to be, by name. */
                      public static Map<String, Object> flatCopies() {
                        FlatDto wire = flatDto();
                        wire.coll().clear();
                        Flat parsed = FixturesFlatMappingImpl.INSTANCE.parse(wire).get();
                        Collection<String> fromSet =
                            FixturesFlatMappingImpl.INSTANCE
                                .build(
                                    new Flat(
                                        List.of(), Set.of(), new LinkedHashSet<>(list("z", "y")),
                                        Map.of(), new CharSequence[0], new int[0], new byte[0],
                                        new ArrayList<>(), Optional.empty()))
                                .coll();
                        boolean mapRejects;
                        try {
                          parsed.map().put("x", "y");
                          mapRejects = false;
                        } catch (UnsupportedOperationException expected) {
                          mapRejects = true;
                        }
                        Map<String, Object> seen = new LinkedHashMap<>();
                        seen.put("list refuses a write", rejectsAdd(parsed.list()));
                        seen.put("set refuses a write", rejectsAdd(parsed.set()));
                        seen.put("collection refuses a write", rejectsAdd(parsed.coll()));
                        seen.put("map refuses a write", mapRejects);
                        seen.put("a deque copies as a list", parsed.coll() instanceof List<?>);
                        seen.put("a set copies as a set", fromSet instanceof Set<?>);
                        seen.put("a set keeps its order", List.copyOf(fromSet));
                        seen.put("an array keeps its class", parsed.array().getClass().getSimpleName());
                        return seen;
                      }

                      /** Order kept, a null element carried, and a null container kept null. */
                      public static List<Object> buildCarries() {
                        Set<String> ordered = new LinkedHashSet<>(list("c", "a", "b"));
                        Map<String, String> sorted = new TreeMap<>(Map.of("b", "2", "a", "1"));
                        FlatDto wire =
                            FixturesFlatMappingImpl.INSTANCE.build(
                                new Flat(
                                    holed("a"), ordered, null, sorted, new String[] {null}, null,
                                    null, null, Optional.empty()));
                        return Arrays.asList(
                            wire.list(),
                            List.copyOf(wire.set()),
                            List.copyOf(wire.map().keySet()),
                            wire.coll(),
                            Arrays.asList(wire.array()),
                            wire.ints());
                      }

                      private static Nested nested() {
                        List<List<List<String>>> deep = new ArrayList<>();
                        deep.add(new ArrayList<>(List.of(list("d"))));
                        Set<List<String>> sets = new LinkedHashSet<>();
                        sets.add(list("s"));
                        List<List<String>> colls = new ArrayList<>();
                        colls.add(list("c"));
                        List<List<String>> wild = new ArrayList<>();
                        wild.add(list("w"));
                        List<ArrayList<String>> subtypes = new ArrayList<>();
                        subtypes.add(new ArrayList<>(list("t")));
                        return new Nested(
                            deep, sets, colls, map("k", list("m")), new String[][] {{"x"}},
                            Optional.of(list("o")), Optional.of(new int[] {1}), wild, subtypes,
                            deques(), deques());
                      }

                      private static NestedDto nestedDto() {
                        Nested n = nested();
                        return new NestedDto(
                            n.deep(), n.sets(), n.colls(), n.maps(), n.matrix(), n.maybe(),
                            n.maybeInts(), n.wild(), n.subtypes(), n.bags(), n.heaps());
                      }

                      /** Two deques with the same element: equal as lists, distinct as deques. */
                      private static Set<Collection<String>> deques() {
                        Set<Collection<String>> deques = new LinkedHashSet<>();
                        deques.add(new ArrayDeque<>(list("d")));
                        deques.add(new ArrayDeque<>(list("d")));
                        return deques;
                      }

                      private static List<String> nestedShared(Nested d, NestedDto w) {
                        return shared(
                            "deep", d.deep(), w.deep(),
                            "deep.0", d.deep().get(0), w.deep().get(0),
                            "deep.0.0", d.deep().get(0).get(0), w.deep().get(0).get(0),
                            "sets.0", d.sets().iterator().next(), w.sets().iterator().next(),
                            "colls.0", d.colls().iterator().next(), w.colls().iterator().next(),
                            "maps.k", d.maps().get("k"), w.maps().get("k"),
                            "matrix.0", d.matrix()[0], w.matrix()[0],
                            "maybe", d.maybe().get(), w.maybe().get(),
                            "maybeInts", d.maybeInts().get(), w.maybeInts().get(),
                            "wild", d.wild(), w.wild(),
                            "wild.0", d.wild().get(0), w.wild().get(0),
                            "subtypes", d.subtypes(), w.subtypes(),
                            "subtypes.0", d.subtypes().get(0), w.subtypes().get(0),
                            "bags", d.bags(), w.bags(),
                            "bags.0", d.bags().iterator().next(), w.bags().iterator().next(),
                            "heaps.0", d.heaps().iterator().next(), w.heaps().iterator().next());
                      }

                      public static List<String> nestedParse() {
                        NestedDto wire = nestedDto();
                        return nestedShared(FixturesNestedMappingImpl.INSTANCE.parse(wire).get(), wire);
                      }

                      public static List<String> nestedBuild() {
                        Nested domain = nested();
                        return nestedShared(domain, FixturesNestedMappingImpl.INSTANCE.build(domain));
                      }

                      /** The sizes of the two sets of deques after build and after parse. */
                      public static List<Integer> dequesKeepTheirCount() {
                        Nested domain = nested();
                        NestedDto built = FixturesNestedMappingImpl.INSTANCE.build(domain);
                        Nested parsed = FixturesNestedMappingImpl.INSTANCE.parse(built).get();
                        return List.of(
                            built.bags().size(), built.heaps().size(),
                            parsed.bags().size(), parsed.heaps().size());
                      }

                      /** A null element a nested copy carries, on build, at every level. */
                      public static List<Object> nestedBuildCarries() {
                        List<List<List<String>>> deep = new ArrayList<>();
                        deep.add(null);
                        deep.add(holed(null));
                        Map<String, List<String>> maps = new LinkedHashMap<>();
                        maps.put("k", null);
                        Set<List<String>> sets = new LinkedHashSet<>();
                        sets.add(null);
                        List<List<String>> colls = new ArrayList<>();
                        colls.add(null);
                        NestedDto wire =
                            FixturesNestedMappingImpl.INSTANCE.build(
                                new Nested(
                                    deep, sets, colls, maps, new String[][] {null}, Optional.empty(),
                                    Optional.empty(), List.of(), List.of(), Set.of(), Set.of()));
                        return Arrays.asList(
                            wire.deep(),
                            wire.sets(),
                            wire.colls(),
                            wire.maps(),
                            Arrays.asList(wire.matrix()),
                            wire.maybe());
                      }

                      @SuppressWarnings({"rawtypes", "unchecked"})
                      public static List<String> raw() {
                        List inner = new ArrayList();
                        inner.add("i");
                        List<List> lists = new ArrayList<>();
                        lists.add(inner);
                        List<List<List>> deep = new ArrayList<>();
                        deep.add(lists);
                        RawDto wire =
                            new RawDto(
                                new ArrayList<>(list("a")), new LinkedHashMap<>(map("k", "v")),
                                new LinkedHashSet<>(list("s")), new ArrayList<>(list("c")), lists,
                                deep);
                        Raw parsed = FixturesRawMappingImpl.INSTANCE.parse(wire).get();
                        Raw domain =
                            new Raw(wire.list(), wire.map(), wire.set(), wire.coll(), lists, deep);
                        RawDto built = FixturesRawMappingImpl.INSTANCE.build(domain);
                        List<String> shared = new ArrayList<>();
                        shared.addAll(
                            shared(
                                "list", parsed.list(), wire.list(),
                                "map", parsed.map(), wire.map(),
                                "set", parsed.set(), wire.set(),
                                "coll", parsed.coll(), wire.coll(),
                                "lists.0", parsed.lists().get(0), inner,
                                "deep.0.0", parsed.deep().get(0).get(0), inner));
                        shared.addAll(
                            shared(
                                "built.list", built.list(), domain.list(),
                                "built.lists.0", built.lists().get(0), inner,
                                "iso.deep.0.0",
                                FixturesRawMappingImpl.INSTANCE.asIso().reverseGet(wire).deep().get(0).get(0),
                                inner));
                        RawAccount account = new RawAccount("i", deep);
                        RawView view = new RawView(deep);
                        shared.addAll(
                            shared(
                                "lens.set.deep.0.0",
                                FixturesRawViewMappingImpl.INSTANCE.asLens().set(view, account).deep().get(0).get(0),
                                inner));
                        return shared;
                      }

                      /** An ArrayList[] behind a List<String>[]: the array copies, its lists stay. */
                      @SuppressWarnings({"rawtypes", "unchecked"})
                      public static List<String> rows() {
                        List<String>[] lists = new ArrayList[] {new ArrayList<>(list("a"))};
                        RowsDto wire = new RowsDto(lists, new String[][] {{"g"}});
                        Rows parsed = FixturesRowsMappingImpl.INSTANCE.parse(wire).get();
                        Rows domain = new Rows(lists, wire.grid());
                        RowsDto built = FixturesRowsMappingImpl.INSTANCE.build(domain);
                        Rows back = FixturesRowsMappingImpl.INSTANCE.asIso().reverseGet(wire);
                        return shared(
                            "parse.lists", parsed.lists(), lists,
                            "parse.lists.0", parsed.lists()[0], lists[0],
                            "parse.grid.0", parsed.grid()[0], wire.grid()[0],
                            "build.lists", built.lists(), lists,
                            "build.lists.0", built.lists()[0], lists[0],
                            "iso.lists.0", back.lists()[0], lists[0]);
                      }

                      public static List<String> bean() {
                        MemberBean bean = new MemberBean();
                        bean.setName("n");
                        bean.setTags(list("t"));
                        Member parsed = FixturesMemberMappingImpl.INSTANCE.parse(bean).get();
                        Member domain = new Member("n", list("t"));
                        MemberBean built = FixturesMemberMappingImpl.INSTANCE.build(domain);
                        return shared(
                            "parse", parsed.tags(), bean.getTags(),
                            "build", built.getTags(), domain.tags());
                      }

                      public static List<String> projection() {
                        Account account = new Account("i", list("a"), Map.of());
                        AccountView view = new AccountView(list("v"));
                        Account set = FixturesAccountViewMappingImpl.INSTANCE.asLens().set(view, account);
                        AccountForm form = new AccountForm("i", list("f"));
                        Account patched =
                            FixturesAccountFormMappingImpl.INSTANCE.patch(account, form).get();
                        return shared(
                            "get",
                            FixturesAccountViewMappingImpl.INSTANCE.asLens().get(account).tags(),
                            account.tags(),
                            "set", set.tags(), view.tags(),
                            "patch", patched.tags(), form.tags());
                      }

                      public static List<String> keyed() {
                        List<String> value = list("v");
                        List<String> nickname = list("n");
                        KeyedDto wire = new KeyedDto(map("k", value), map("k", "s"), nickname);
                        Keyed parsed = FixturesKeyedMappingImpl.INSTANCE.parse(wire).get();
                        Keyed domain =
                            new Keyed(
                                Map.of(new Key("k"), value), Map.of(), Optional.of(nickname));
                        KeyedDto built = FixturesKeyedMappingImpl.INSTANCE.build(domain);
                        return shared(
                            "parse.byKey", parsed.byKey().get(new Key("k")), value,
                            "parse.nicknames", parsed.nicknames().get(), nickname,
                            "build.byKey", built.byKey().get("k"), value,
                            "build.nicknames", built.nicknames(), nickname);
                      }

                      public static List<String> sparse() {
                        AccountPatch patch = new AccountPatch();
                        patch.setTags(list("t"));
                        patch.setByKey(map("k", list("v")));
                        Account patched =
                            FixturesAccountPatchMappingImpl.INSTANCE
                                .updateFrom(patch)
                                .apply(new Account("i", List.of(), Map.of()))
                                .get();
                        ScoresPatch scores = new ScoresPatch();
                        scores.setScores(new int[] {1});
                        Scores updated =
                            FixturesScoresPatchMappingImpl.INSTANCE
                                .updateFrom(scores)
                                .apply(new Scores(new int[0]))
                                .get();
                        return shared(
                            "tags", patched.tags(), patch.getTags(),
                            "byKey.k", patched.byKey().get(new Key("k")), patch.getByKey().get("k"),
                            "scores", updated.scores(), scores.getScores());
                      }

                      public static List<String> merge() {
                        Names names = new Names(list("n"));
                        Labels labels = new Labels(map("k", "v"), "note");
                        Merged total = FixturesMergedAssemblyImpl.INSTANCE.merge(names, labels);
                        Merged checked = FixturesCheckedAssemblyImpl.INSTANCE.merge(names, labels).get();
                        return shared(
                            "total.names", total.names(), names.names(),
                            "total.labels", total.labels(), labels.labels(),
                            "checked.names", checked.names(), names.names(),
                            "checked.labels", checked.labels(), labels.labels());
                      }

                      public static List<String> wideAndFlattened() {
                        String[] f = Collections.nCopies(16, "x").toArray(String[]::new);
                        WideDto wide =
                            new WideDto(
                                f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10],
                                f[11], f[12], f[13], f[14], f[15], list("w"));
                        OuterDto outer = new OuterDto("i", list("o"));
                        Outer parsed = FixturesOuterMappingImpl.INSTANCE.parse(outer).get();
                        Outer domain = new Outer("i", new Inner(list("d")));
                        return shared(
                            "wide", FixturesWideMappingImpl.INSTANCE.parse(wide).get().tags(), wide.tags(),
                            "flattened.parse", parsed.inner().tags(), outer.tags(),
                            "flattened.build",
                            FixturesOuterMappingImpl.INSTANCE.build(domain).tags(),
                            domain.inner().tags(),
                            "flattened.iso",
                            FixturesOuterMappingImpl.INSTANCE.asIso().reverseGet(outer).inner().tags(),
                            outer.tags());
                      }
                    }
                    """));
    compiled = new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  private static Object probe(String name) throws ReflectiveOperationException {
    return compiled.invokeStatic(PKG + ".Probes", name);
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
  @DisplayName("every fixture, and every copy helper it emits, compiles under -Xlint:all -Werror")
  void compilesWithoutWarnings() {
    assertThat(compiled.compilation()).succeededWithoutWarnings();
  }

  @Test
  @DisplayName(
      "parse, build and asIso copy a List, Set, Collection, Map and every array; a subtype such"
          + " as ArrayList is shared")
  void everyCopyableLevelIsCopied() throws ReflectiveOperationException {
    assertThat(probe("flatParse")).isEqualTo(List.of("arrayList"));
    assertThat(probe("flatBuild")).isEqualTo(List.of("arrayList"));
    assertThat(probe("flatIso")).isEqualTo(List.of("arrayList"));
    // An Optional of a value with no copy is immutable, and handed over as it is.
    assertThat(generated("FixturesFlatMappingImpl")).contains("domain.plain()");
  }

  @Test
  @DisplayName(
      "a copy is unmodifiable, a set copies as a set and any other collection as a list, and an"
          + " array keeps its runtime component type")
  void aCopyIsUnmodifiable() throws ReflectiveOperationException {
    assertThat(probe("flatCopies"))
        .isEqualTo(
            Map.of(
                "list refuses a write", true,
                "set refuses a write", true,
                "collection refuses a write", true,
                "map refuses a write", true,
                "a deque copies as a list", true,
                "a set copies as a set", true,
                "a set keeps its order", List.of("z", "y"),
                "an array keeps its class", "String[]"));
  }

  @Test
  @DisplayName(
      "build keeps the source's order, carries a null element as it is, and copies a null"
          + " container to null")
  void buildCarriesWhatTheDomainHolds() throws ReflectiveOperationException {
    assertThat(probe("buildCarries"))
        .isEqualTo(
            Arrays.asList(
                Arrays.asList("a", null),
                List.of("c", "a", "b"),
                List.of("a", "b"),
                null,
                Collections.singletonList(null),
                null));
  }

  @Test
  @DisplayName(
      "every level inside is copied too, a list, set, collection, map value, array and Optional"
          + " value alike; below a wildcard or a subtype the elements are shared")
  void nestedLevelsAreCopied() throws ReflectiveOperationException {
    assertThat(probe("nestedParse"))
        .isEqualTo(List.of("wild.0", "subtypes.0", "bags.0", "heaps.0"));
    assertThat(probe("nestedBuild"))
        .isEqualTo(List.of("wild.0", "subtypes.0", "bags.0", "heaps.0"));
    // Three levels deep, each lambda is named for its depth.
    assertThat(generated("FixturesNestedMappingImpl"))
        .contains("hkj$copyOf(domain.deep(), e -> hkj$copyOf(e, e2 -> hkj$copyOf(e2)))");
  }

  @Test
  @DisplayName(
      "the collections inside a set are handed over as they are, so two that are equal only as"
          + " lists stay two")
  void aSetOfCollectionsKeepsEveryElement() throws ReflectiveOperationException {
    assertThat(probe("dequesKeepTheirCount")).isEqualTo(List.of(2, 2, 2, 2));
  }

  @Test
  @DisplayName(
      "an array is copied whatever its runtime type, and so are rows that are arrays; collections"
          + " inside it are handed over as they are")
  void anArrayOfCollectionsIsCloned() throws ReflectiveOperationException {
    assertThat(probe("rows")).isEqualTo(List.of("parse.lists.0", "build.lists.0", "iso.lists.0"));
  }

  @Test
  @DisplayName("a nested copy carries a null element as it is, at every level")
  void nestedCopiesCarryNulls() throws ReflectiveOperationException {
    assertThat(probe("nestedBuildCarries"))
        .isEqualTo(
            List.of(
                Arrays.asList(null, Arrays.asList(null, null)),
                Collections.singleton(null),
                Collections.singletonList(null),
                Collections.singletonMap("k", null),
                Collections.singletonList(null),
                Optional.empty()));
  }

  @Test
  @DisplayName(
      "a raw container copies through its wildcard view, and a raw one inside a parameterised"
          + " one copies too")
  void rawContainersAreCopied() throws ReflectiveOperationException {
    assertThat(probe("raw")).isEqualTo(List.of());
    // A raw type nested in a lambda parameter is answered by the member, asIso and asLens too.
    assertThat(generated("FixturesRawMappingImpl"))
        .containsPattern("@SuppressWarnings\\(\"rawtypes\"\\)\\s+public Iso<");
    assertThat(generated("FixturesRawViewMappingImpl"))
        .containsPattern("@SuppressWarnings\\(\"rawtypes\"\\)\\s+public Lens<");
    assertThat(generated("FixturesRawMappingImpl"))
        .contains("hkj$copyOf((List<?>) wire.list())")
        .contains("hkj$copyOf((Map<?, ?>) wire.map())")
        .contains("hkj$copyOf(wire.lists(), e -> hkj$copyOf((List<?>) e))");
  }

  @Test
  @DisplayName("a bean copies both ways, and a projection's get, set and patch copy too")
  void beansAndProjectionsCopy() throws ReflectiveOperationException {
    assertThat(probe("bean")).isEqualTo(List.of());
    assertThat(probe("projection")).isEqualTo(List.of());
  }

  @Test
  @DisplayName(
      "a @MapKey map's values and a bridged Optional's value copy both ways; a map whose values"
          + " have no copy is not copied first")
  void keyedAndBridgedValuesCopy() throws ReflectiveOperationException {
    assertThat(probe("keyed")).isEqualTo(List.of());
    assertThat(generated("FixturesKeyedMappingImpl"))
        .contains("hkj$ifPresent(wire.names(), namesKey()::parseKeys)")
        .contains("hkj$copyOf(byKeyKey().buildKeys(domain.byKey()), e -> hkj$copyOf(e))");
  }

  @Test
  @DisplayName(
      "the sparse tier copies a present value, an array with no scan and a @MapKey map's values"
          + " included")
  void sparseEditsCopy() throws ReflectiveOperationException {
    assertThat(probe("sparse")).isEqualTo(List.of());
    assertThat(generated("FixturesScoresPatchMappingImpl"))
        .contains("Edit.setIfPresent(")
        .contains("hkj$copyOf(wire.getScores())");
  }

  @Test
  @DisplayName("a merge copies its fills on the total path and the fallible one alike")
  void mergesCopy() throws ReflectiveOperationException {
    assertThat(probe("merge")).isEqualTo(List.of());
  }

  @Test
  @DisplayName("a chunked ladder, a flattened group and its asIso copy like any other leg")
  void chunkedAndFlattenedLegsCopy() throws ReflectiveOperationException {
    assertThat(probe("wideAndFlattened")).isEqualTo(List.of());
  }
}
