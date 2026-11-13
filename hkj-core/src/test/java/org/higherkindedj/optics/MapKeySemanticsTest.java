// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.FocusPaths;
import org.higherkindedj.optics.ixed.IxedInstances;
import org.higherkindedj.optics.laws.TraversalLaws;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every optic that writes a map keeps every entry of its source, telling keys apart as the source
 * does.
 *
 * <p>The identity source holds six keys that are equal but not the same object, at the default
 * capacity, so an optic that rebuilt it as a {@code LinkedHashMap} would keep one entry, and one
 * that rebuilt it as a fresh {@code IdentityHashMap} would usually reorder it. The merging source
 * is a {@code TreeMap} whose comparator tells apart two keys that {@code equals} merges, which no
 * map an optic can build keeps apart, so every optic refuses it.
 */
@DisplayName("A map written through an optic keeps every entry of its source")
class MapKeySemanticsTest {

  /** A key that equals by name alone, so a map ordered by rank holds two that equals merges. */
  private record Tag(String name, int rank) {
    @Override
    public boolean equals(Object other) {
      return other instanceof Tag tag && tag.name.equals(name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  private static IdentityHashMap<String, Integer> sixEqualKeys() {
    IdentityHashMap<String, Integer> map = new IdentityHashMap<>();
    for (int i = 0; i < 6; i++) {
      map.put(new String("k"), i);
    }
    return map;
  }

  private static TreeMap<Tag, Integer> mergingSource() {
    TreeMap<Tag, Integer> map = new TreeMap<>(Comparator.comparingInt(Tag::rank));
    map.put(new Tag("k", 1), 1);
    map.put(new Tag("k", 2), 2);
    return map;
  }

  /**
   * The result is an identity map holding the source's own keys, the same objects, in its order,
   * with each value changed as {@code expected} says.
   */
  private static void assertEveryEntry(
      Map<String, Integer> result,
      Collection<Map.Entry<String, Integer>> source,
      Function<Integer, Integer> expected) {
    assertThat(result).isExactlyInstanceOf(IdentityHashMap.class).hasSize(source.size());
    Iterator<Map.Entry<String, Integer>> resultEntries = result.entrySet().iterator();
    for (Map.Entry<String, Integer> entry : source) {
      Map.Entry<String, Integer> rebuilt = resultEntries.next();
      assertThat(rebuilt.getKey()).isSameAs(entry.getKey());
      assertThat(rebuilt.getValue()).isEqualTo(expected.apply(entry.getValue()));
    }
  }

  private static <S> void assertRefused(Function<S, ?> write, S source) {
    assertThatThrownBy(() -> write.apply(source))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot rebuild this java.util.TreeMap without losing an entry");
  }

  @Nested
  @DisplayName("An IdentityHashMap source comes back an IdentityHashMap with every key, in order")
  class IdentitySource {

    @Test
    @DisplayName(
        "two equal keys survive forMapValues and forMapValuesCollecting(IdentityHashMap::new)")
    void twoEqualKeys() {
      Map<String, Integer> source = new IdentityHashMap<>();
      source.put(new String("k"), 1);
      source.put(new String("k"), 2);

      assertThat(Traversals.modify(Traversals.forMapValues(), n -> n * 10, source)).hasSize(2);
      assertThat(
              Traversals.modify(
                  Traversals.forMapValuesCollecting(IdentityHashMap::new),
                  n -> n * 10,
                  new IdentityHashMap<>(source)))
          .hasSize(2);
    }

    @Test
    @DisplayName("Traversals.forMapValues(), FocusPaths.mapValues() and mapValuesEach()")
    void forMapValues() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();

      assertEveryEntry(
          Traversals.modify(Traversals.<String, Integer>forMapValues(), n -> n * 10, source),
          source.entrySet(),
          n -> n * 10);
      assertEveryEntry(
          Traversals.modify(FocusPaths.<String, Integer>mapValues(), n -> n * 10, source),
          source.entrySet(),
          n -> n * 10);
      assertEveryEntry(
          Traversals.modify(
              EachInstances.<String, Integer>mapValuesEach().each(), n -> n * 10, source),
          source.entrySet(),
          n -> n * 10);
    }

    @Test
    @DisplayName("the collecting and rebuilding factories hand their function an IdentityHashMap")
    void factories() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();
      Traversal<IdentityHashMap<String, Integer>, Integer> collecting =
          Traversals.forMapValuesCollecting(IdentityHashMap::new);
      Traversal<IdentityHashMap<String, Integer>, Integer> viewed =
          Traversals.forMapValuesCollecting(map -> map, IdentityHashMap::new);
      Traversal<IdentityHashMap<String, Integer>, Integer> rebuilding =
          Traversals.forMapValuesRebuilding((original, map) -> new IdentityHashMap<>(map));
      Traversal<IdentityHashMap<String, Integer>, Integer> eachCollecting =
          EachInstances.<IdentityHashMap<String, Integer>, String, Integer>mapValuesEachCollecting(
                  IdentityHashMap::new)
              .each();

      for (Traversal<IdentityHashMap<String, Integer>, Integer> traversal :
          List.of(collecting, viewed, rebuilding, eachCollecting)) {
        assertThat(Traversals.modify(traversal, n -> n * 10, source)).hasSize(6);
      }
      Traversals.modify(
          Traversals.<IdentityHashMap<String, Integer>, String, Integer>forMapValuesRebuilding(
              (original, map) -> {
                assertEveryEntry(map, original.entrySet(), n -> n * 10);
                return original;
              }),
          n -> n * 10,
          source);
    }

    @Test
    @DisplayName("IndexedTraversals.forMap() and the indexed mapValuesEach()")
    void indexed() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();

      assertEveryEntry(
          IndexedTraversals.imodify(
              IndexedTraversals.<String, Integer>forMap(), (key, n) -> n * 10, source),
          source.entrySet(),
          n -> n * 10);
      assertEveryEntry(
          IndexedTraversals.imodify(
              EachInstances.<String, Integer>mapValuesEach().indexedTraversal(),
              (key, n) -> n * 10,
              source),
          source.entrySet(),
          n -> n * 10);
    }

    @Test
    @DisplayName("Setter.forMapValues(), through modify and modifyF")
    void setter() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();
      Setter<Map<String, Integer>, Integer> values = Setter.forMapValues();

      assertEveryEntry(values.modify(n -> n * 10, source), source.entrySet(), n -> n * 10);
      assertEveryEntry(
          IdKindHelper.ID
              .narrow(values.modifyF(n -> Id.of(n * 10), source, IdMonad.instance()))
              .value(),
          source.entrySet(),
          n -> n * 10);
    }

    @Test
    @DisplayName("Traversals.forMap(key), AtInstances.mapAt(), mapIx() and FocusPaths.mapAt()")
    void keyOptics() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();
      String third = source.keySet().stream().skip(2).findFirst().orElseThrow();
      int thirdValue = source.get(third);
      Function<Integer, Integer> onlyThird = n -> n == thirdValue ? n * 10 : n;

      assertEveryEntry(
          Traversals.modify(Traversals.<String, Integer>forMap(third), n -> n * 10, source),
          source.entrySet(),
          onlyThird);
      assertEveryEntry(
          AtInstances.<String, Integer>mapAt().at(third).modify(n -> n.map(v -> v * 10), source),
          source.entrySet(),
          onlyThird);
      assertEveryEntry(
          Traversals.modify(IxedInstances.<String, Integer>mapIx().ix(third), n -> n * 10, source),
          source.entrySet(),
          onlyThird);
      assertEveryEntry(
          FocusPaths.<String, Integer>mapAt(third).modify(n -> n * 10, source),
          source.entrySet(),
          onlyThird);
    }

    @Test
    @DisplayName("adding or removing a key through At and FocusPaths.mapAt() keeps the rest")
    void addAndRemove() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();
      String added = new String("k");
      String first = source.keySet().iterator().next();
      At<Map<String, Integer>, String, Integer> at = AtInstances.mapAt();

      Map<String, Integer> withAdded = at.at(added).set(Optional.of(99), source);
      Map<String, Integer> withoutFirst = at.at(first).set(Optional.empty(), source);

      assertThat(withAdded).isInstanceOf(IdentityHashMap.class).hasSize(7).containsValue(99);
      assertThat(withoutFirst).isInstanceOf(IdentityHashMap.class).hasSize(5);
      assertThat(withoutFirst.containsKey(first)).isFalse();
      assertThat(FocusPaths.<String, Integer>mapAt(added).set(99, source)).hasSize(7);
      assertThat(FocusPaths.<String, Integer>mapAt(first).remove(source)).hasSize(5);
      assertThat(source).hasSize(6);
    }

    @Test
    @DisplayName("the traversal laws hold for forMapValues()")
    void laws() {
      // An IdentityHashMap compares values by reference too; these stay within the Integer cache.
      TraversalLaws.assertTraversalLaws(
          Traversals.<String, Integer>forMapValues(), sixEqualKeys(), n -> n + 1, n -> n * 2);
    }

    @Test
    @DisplayName("each run of a deferred effect builds its own map, of the keys read at the call")
    void deferredEffect() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();
      List<Map.Entry<String, Integer>> asCalled =
          source.entrySet().stream().map(e -> Map.entry(e.getKey(), e.getValue())).toList();
      Kind<IOKind.Witness, Map<String, Integer>> result =
          Traversals.traverseMapValues(
              source, n -> IO_OP.widen(IO.delay(() -> n * 10)), Instances.monad(io()));
      IO<Map<String, Integer>> io = IO_OP.narrow(result);

      source.clear();
      Map<String, Integer> first = io.unsafeRunSync();
      Map<String, Integer> second = io.unsafeRunSync();

      assertThat(second).isNotSameAs(first);
      assertEveryEntry(first, asCalled, n -> n * 10);
      assertEveryEntry(second, asCalled, n -> n * 10);
    }

    @Test
    @DisplayName("each branch of a List effect builds its own map, with every key")
    void listBranches() {
      IdentityHashMap<String, Integer> source = new IdentityHashMap<>();
      source.put(new String("k"), 1);
      source.put(new String("k"), 2);
      List<Integer> read = List.copyOf(source.values());

      Kind<ListKind.Witness, Map<String, Integer>> result =
          Traversals.traverseMapValues(
              source, n -> LIST.widen(List.of(n, n + 100)), Instances.monad(list()));
      List<Map<String, Integer>> branches = LIST.narrow(result);

      assertThat(branches).hasSize(4);
      for (int i = 0; i < branches.size(); i++) {
        assertThat(branches.get(i)).isInstanceOf(IdentityHashMap.class).hasSize(2);
        for (int j = i + 1; j < branches.size(); j++) {
          assertThat(branches.get(i)).isNotSameAs(branches.get(j));
        }
      }
      assertThat(branches)
          .extracting(branch -> List.copyOf(branch.values()))
          .containsExactlyInAnyOrder(
              List.of(read.get(0), read.get(1)),
              List.of(read.get(0), read.get(1) + 100),
              List.of(read.get(0) + 100, read.get(1)),
              List.of(read.get(0) + 100, read.get(1) + 100));
    }
  }

  @Nested
  @DisplayName("A subclass of IdentityHashMap comes back a plain IdentityHashMap with every key")
  class IdentitySubclass {

    /** A subclass that fixes its type arguments, so it cannot hold the new values' type. */
    @SuppressWarnings("serial") // never serialised
    private static final class Names extends IdentityHashMap<String, String> {}

    @Test
    @DisplayName("a value-changing traversal never puts its new values into the subclass")
    void typeChangingTraversal() {
      Names source = new Names();
      source.put(new String("k"), "one");
      source.put(new String("k"), "three");

      Map<String, Integer> lengths =
          IdKindHelper.ID
              .narrow(
                  Traversals.traverseMapValues(
                      source, name -> Id.of(name.length()), IdMonad.instance()))
              .value();

      assertThat(lengths).isExactlyInstanceOf(IdentityHashMap.class).hasSize(2);
      assertThat(lengths.values()).containsExactlyInAnyOrder(3, 5);
      assertThat(source.values()).containsExactlyInAnyOrder("one", "three");
    }

    @Test
    @DisplayName("Setter.forMapValues() and AtInstances.mapAt() copy it into a plain one too")
    void setterAndAt() {
      Names source = new Names();
      String first = new String("k");
      source.put(first, "one");
      source.put(new String("k"), "two");

      assertThat(Setter.<String, String>forMapValues().modify(String::toUpperCase, source))
          .isExactlyInstanceOf(IdentityHashMap.class)
          .hasSize(2);
      assertThat(AtInstances.<String, String>mapAt().at(first).set(Optional.of("ONE"), source))
          .isExactlyInstanceOf(IdentityHashMap.class)
          .hasSize(2);
    }
  }

  @Nested
  @DisplayName("A source holding two keys that equals merges is refused, not silently merged")
  class MergingSource {

    @Test
    @DisplayName("by the value traversals, the factories and the Setter")
    void valueOptics() {
      assertRefused(m -> Traversals.modify(Traversals.forMapValues(), n -> n, m), mergingSource());
      assertRefused(
          m -> Traversals.modify(Traversals.forMapValuesRebuilding((s, map) -> s), n -> n, m),
          mergingSource());
      assertRefused(
          m -> IndexedTraversals.imodify(IndexedTraversals.forMap(), (key, n) -> n, m),
          mergingSource());
      assertRefused(m -> Setter.<Tag, Integer>forMapValues().modify(n -> n, m), mergingSource());
      assertRefused(
          m ->
              IdKindHelper.ID
                  .narrow(
                      Setter.<Tag, Integer>forMapValues().modifyF(Id::of, m, IdMonad.instance()))
                  .value(),
          mergingSource());
    }

    @Test
    @DisplayName("by the key optics")
    void keyOptics() {
      Tag first = new Tag("k", 1);

      assertRefused(m -> Traversals.modify(Traversals.forMap(first), n -> n, m), mergingSource());
      assertRefused(
          m -> AtInstances.<Tag, Integer>mapAt().at(first).set(Optional.of(5), m), mergingSource());
      assertRefused(m -> FocusPaths.<Tag, Integer>mapAt(first).set(5, m), mergingSource());
      assertRefused(m -> FocusPaths.<Tag, Integer>mapAt(first).remove(m), mergingSource());
    }

    @Test
    @DisplayName("the remedy the refusal offers keeps every entry")
    void remedy() {
      TreeMap<Tag, Integer> source = mergingSource();
      Traversal<TreeMap<Tag, Integer>, Integer> viaIdentity =
          Traversals.forMapValuesCollecting(
              IdentityHashMap::new,
              map -> {
                TreeMap<Tag, Integer> rebuilt = new TreeMap<>(source.comparator());
                rebuilt.putAll(map);
                return rebuilt;
              });

      TreeMap<Tag, Integer> result = Traversals.modify(viaIdentity, n -> n * 10, source);

      assertThat(result.keySet()).extracting(Tag::rank).containsExactly(1, 2);
      assertThat(result.values()).containsExactly(10, 20);
    }

    @Test
    @DisplayName("but a read loses nothing, so it still succeeds")
    void readsSucceed() {
      assertThat(Traversals.getAll(Traversals.<Tag, Integer>forMapValues(), mergingSource()))
          .containsExactly(1, 2);
    }
  }
}
