// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code parseValues} and {@code buildValues} keep every entry of their source, telling keys apart
 * as the source does.
 *
 * <p>The identity source holds six keys that are equal but not the same object, so a result rebuilt
 * as a {@code LinkedHashMap} would keep one entry. The merging source is a {@code TreeMap} whose
 * comparator tells apart two keys that {@code equals} merges, which no map either method can build
 * keeps apart, so both refuse it.
 */
@DisplayName("parseValues and buildValues keep every entry of their source")
class ValidatedMapKeysTest {

  record Code(String value) {}

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

    @Override
    public String toString() {
      return name + "#" + rank;
    }
  }

  private static final ValidatedPrism<String, Code> CODE =
      ValidatedPrism.of(
          raw ->
              raw.isBlank()
                  ? Validated.invalidNel(FieldError.of("must not be blank"))
                  : Validated.validNel(new Code(raw)),
          Code::value);

  /** Six keys that are equal but not the same, in an identity map at its default capacity. */
  private static <V> IdentityHashMap<String, V> sixEqualKeys(V value) {
    IdentityHashMap<String, V> map = new IdentityHashMap<>();
    for (int i = 0; i < 6; i++) {
      map.put(new String("k"), value);
    }
    return map;
  }

  private static <V> Map<Tag, V> mergingSource(V first, V second) {
    Map<Tag, V> map = new TreeMap<>(Comparator.comparingInt(Tag::rank));
    map.put(new Tag("k", 1), first);
    map.put(new Tag("k", 2), second);
    return map;
  }

  private static String refusal(String subject) {
    return subject
        + " this java.util.TreeMap without losing an entry: its keys k#1 and k#2 are equal by"
        + " equals(), and the rebuilt map looks keys up by equals(). If this is a view of an"
        + " IdentityHashMap, pass the map itself; otherwise pass an IdentityHashMap copy of it. A"
        + " key changed after it went into the map also causes this.";
  }

  /**
   * The result is an identity map of its own holding the source's own keys, the same objects, in
   * the source's order, and looks them up by identity.
   */
  private static void assertSameKeysInOrder(Map<String, ?> result, Map<String, ?> source) {
    assertThat(result)
        .isExactlyInstanceOf(IdentityHashMap.class)
        .isNotSameAs(source)
        .hasSameSizeAs(source);
    Iterator<String> resultKeys = result.keySet().iterator();
    for (String key : source.keySet()) {
      assertThat(resultKeys.next()).isSameAs(key);
    }
    assertThat(result.containsKey(new String("k"))).isFalse();
  }

  @Nested
  @DisplayName("An IdentityHashMap comes back an IdentityHashMap of its own, with every key")
  class Identity {

    @Test
    @DisplayName("parseValues keeps every key, the same objects in the source's order")
    void parseValuesKeepsEveryKey() {
      IdentityHashMap<String, String> wire = sixEqualKeys("A1");

      Validated<NonEmptyList<FieldError>, Map<String, Code>> parsed = CODE.parseValues(wire);

      assertThatValidated(parsed).isValid();
      assertSameKeysInOrder(parsed.get(), wire);
      assertThat(parsed.get().values()).hasSize(6).containsOnly(new Code("A1"));
    }

    @Test
    @DisplayName("parseValues locates a failing value under each of the keys that holds it")
    void parseValuesLocatesEachKey() {
      IdentityHashMap<String, String> wire = sixEqualKeys(" ");

      Validated<NonEmptyList<FieldError>, Map<String, Code>> parsed = CODE.parseValues(wire);

      assertThatValidated(parsed).isInvalid();
      assertThat(parsed.getError().toJavaList())
          .hasSize(6)
          .containsOnly(new FieldError(List.of("k"), "must not be blank"));
    }

    @Test
    @DisplayName("buildValues keeps every key, the same objects in the source's order")
    void buildValuesKeepsEveryKey() {
      IdentityHashMap<String, Code> domain = sixEqualKeys(new Code("A1"));

      Map<String, String> built = CODE.buildValues(domain);

      assertSameKeysInOrder(built, domain);
      assertThat(built.values()).hasSize(6).containsOnly("A1");
    }

    @Test
    @DisplayName("a round trip keeps every key, so a result can be passed back in")
    void roundTripKeepsEveryKey() {
      IdentityHashMap<String, Code> domain = sixEqualKeys(new Code("A1"));

      Map<String, Code> back = CODE.parseValues(CODE.buildValues(domain)).get();
      Map<String, String> again = CODE.buildValues(back);

      assertSameKeysInOrder(back, domain);
      assertSameKeysInOrder(again, domain);
    }

    @Test
    @DisplayName("a result compares as an IdentityHashMap does, by the same value objects too")
    void comparesByTheSameValues() {
      IdentityHashMap<String, String> wire = sixEqualKeys("A1");

      assertThat(CODE.parseValues(wire).get()).isNotEqualTo(CODE.parseValues(wire).get());
    }
  }

  @Nested
  @DisplayName("A map holding two keys that equals cannot tell apart is refused")
  class Merging {

    @Test
    @DisplayName("parseValues refuses it, naming both keys")
    void parseValuesRefuses() {
      Map<Tag, String> wire = mergingSource("A1", "B2");

      assertThatThrownBy(() -> CODE.parseValues(wire))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(refusal("parseValues cannot parse"));
    }

    @Test
    @DisplayName("parseValues refuses it even where a value fails, so the refusal is never hidden")
    void parseValuesRefusesBesideAFailure() {
      Map<Tag, String> wire = mergingSource("A1", " ");

      assertThatThrownBy(() -> CODE.parseValues(wire))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(refusal("parseValues cannot parse"));
    }

    @Test
    @DisplayName("buildValues refuses it, naming both keys")
    void buildValuesRefuses() {
      Map<Tag, Code> domain = mergingSource(new Code("A1"), new Code("B2"));

      assertThatThrownBy(() -> CODE.buildValues(domain))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(refusal("buildValues cannot build"));
    }

    @Test
    @DisplayName("an IdentityHashMap copy of it, as the message offers, keeps both entries")
    void theRemedyKeepsBothEntries() {
      Map<Tag, String> wire = new IdentityHashMap<>(mergingSource("A1", "B2"));
      Map<Tag, Code> domain = new IdentityHashMap<>(mergingSource(new Code("A1"), new Code("B2")));

      // An IdentityHashMap looks values up by reference too, so compare the values themselves.
      assertThat(CODE.parseValues(wire).get().values())
          .containsExactlyInAnyOrder(new Code("A1"), new Code("B2"));
      assertThat(CODE.buildValues(domain).values()).containsExactlyInAnyOrder("A1", "B2");
    }
  }
}
