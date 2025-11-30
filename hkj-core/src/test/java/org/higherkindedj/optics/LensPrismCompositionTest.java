// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for composing {@link Lens} with {@link Prism} to produce a {@link Traversal}.
 *
 * <p>This composition follows the standard optic composition rule: Lens >>> Prism = Traversal.
 */
@DisplayName("Lens.andThen(Prism) Composition Tests")
class LensPrismCompositionTest {

  // Test domain
  record Container(Optional<Inner> inner) {}

  record Inner(String value, int count) {}

  // Optics
  static final Lens<Container, Optional<Inner>> innerLens =
      Lens.of(Container::inner, (container, inner) -> new Container(inner));

  static final Prism<Optional<Inner>, Inner> somePrism = Prisms.some();

  static final Lens<Inner, String> valueLens =
      Lens.of(Inner::value, (inner, value) -> new Inner(value, inner.count()));

  @Nested
  @DisplayName("Lens.andThen(Prism) produces Traversal")
  class LensAndThenPrism {

    @Test
    @DisplayName("should compose to a Traversal")
    void composesToTraversal() {
      Traversal<Container, Inner> traversal = innerLens.andThen(somePrism);

      assertThat(traversal).isNotNull();
    }

    @Test
    @DisplayName("toList should return value when prism matches")
    void traversal_toList_returnsValueWhenPresent() {
      Traversal<Container, Inner> traversal = innerLens.andThen(somePrism);
      Container container = new Container(Optional.of(new Inner("hello", 42)));

      List<Inner> result = Traversals.getAll(traversal, container);

      assertThat(result).containsExactly(new Inner("hello", 42));
    }

    @Test
    @DisplayName("toList should return empty when prism does not match")
    void traversal_toList_returnsEmptyWhenAbsent() {
      Traversal<Container, Inner> traversal = innerLens.andThen(somePrism);
      Container container = new Container(Optional.empty());

      List<Inner> result = Traversals.getAll(traversal, container);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("modify should apply function when prism matches")
    void traversal_modify_appliesWhenPresent() {
      Traversal<Container, Inner> traversal = innerLens.andThen(somePrism);
      Container container = new Container(Optional.of(new Inner("hello", 42)));

      Container result =
          Traversals.modify(
              traversal, inner -> new Inner(inner.value().toUpperCase(), inner.count()), container);

      assertThat(result.inner()).contains(new Inner("HELLO", 42));
    }

    @Test
    @DisplayName("modify should return unchanged when prism does not match")
    void traversal_modify_returnsUnchangedWhenAbsent() {
      Traversal<Container, Inner> traversal = innerLens.andThen(somePrism);
      Container container = new Container(Optional.empty());

      Container result =
          Traversals.modify(
              traversal, inner -> new Inner(inner.value().toUpperCase(), inner.count()), container);

      assertThat(result).isEqualTo(container);
    }

    @Test
    @DisplayName("should chain with another traversal")
    void traversal_canChainWithAnotherTraversal() {
      Traversal<Container, Inner> innerTraversal = innerLens.andThen(somePrism);
      Traversal<Container, String> valueTraversal = innerTraversal.andThen(valueLens.asTraversal());

      Container container = new Container(Optional.of(new Inner("hello", 42)));

      List<String> values = Traversals.getAll(valueTraversal, container);
      assertThat(values).containsExactly("hello");

      Container modified = Traversals.modify(valueTraversal, String::toUpperCase, container);
      assertThat(modified.inner()).contains(new Inner("HELLO", 42));
    }

    @Test
    @DisplayName("should preserve other fields when modifying nested value")
    void traversal_preservesOtherFields() {
      Traversal<Container, Inner> traversal = innerLens.andThen(somePrism);
      Container container = new Container(Optional.of(new Inner("hello", 42)));

      Container result =
          Traversals.modify(
              traversal, inner -> new Inner(inner.value().toUpperCase(), inner.count()), container);

      assertThat(result.inner().get().count()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Lens.andThen(Prism) with sealed interfaces")
  class SealedInterfaceTests {

    sealed interface JsonValue permits JsonString, JsonNumber {}

    record JsonString(String value) implements JsonValue {}

    record JsonNumber(int value) implements JsonValue {}

    record Wrapper(JsonValue data) {}

    static final Lens<Wrapper, JsonValue> dataLens =
        Lens.of(Wrapper::data, (wrapper, data) -> new Wrapper(data));

    static final Prism<JsonValue, JsonString> jsonStringPrism =
        Prism.of(json -> json instanceof JsonString s ? Optional.of(s) : Optional.empty(), s -> s);

    @Test
    @DisplayName("should match when sealed interface case matches")
    void matchesSealedInterfaceCase() {
      Traversal<Wrapper, JsonString> traversal = dataLens.andThen(jsonStringPrism);
      Wrapper wrapper = new Wrapper(new JsonString("hello"));

      List<JsonString> result = Traversals.getAll(traversal, wrapper);

      assertThat(result).containsExactly(new JsonString("hello"));
    }

    @Test
    @DisplayName("should not match when sealed interface case does not match")
    void doesNotMatchDifferentSealedInterfaceCase() {
      Traversal<Wrapper, JsonString> traversal = dataLens.andThen(jsonStringPrism);
      Wrapper wrapper = new Wrapper(new JsonNumber(42));

      List<JsonString> result = Traversals.getAll(traversal, wrapper);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("modify should only affect matching case")
    void modifyOnlyAffectsMatchingCase() {
      Traversal<Wrapper, JsonString> traversal = dataLens.andThen(jsonStringPrism);

      Wrapper stringWrapper = new Wrapper(new JsonString("hello"));
      Wrapper numberWrapper = new Wrapper(new JsonNumber(42));

      Wrapper modifiedString =
          Traversals.modify(traversal, s -> new JsonString(s.value().toUpperCase()), stringWrapper);
      Wrapper modifiedNumber =
          Traversals.modify(traversal, s -> new JsonString(s.value().toUpperCase()), numberWrapper);

      assertThat(modifiedString.data()).isEqualTo(new JsonString("HELLO"));
      assertThat(modifiedNumber.data()).isEqualTo(new JsonNumber(42));
    }
  }

  @Nested
  @DisplayName("Comparison with asTraversal approach")
  class ComparisonTests {

    @Test
    @DisplayName("andThen(Prism) should behave same as asTraversal().andThen(prism.asTraversal())")
    void behavesLikeAsTraversalComposition() {
      Container containerWithValue = new Container(Optional.of(new Inner("test", 100)));
      Container containerEmpty = new Container(Optional.empty());

      // Direct composition
      Traversal<Container, Inner> direct = innerLens.andThen(somePrism);

      // Manual composition via asTraversal
      Traversal<Container, Inner> manual = innerLens.asTraversal().andThen(somePrism.asTraversal());

      // Both should produce same results for getAll
      assertThat(Traversals.getAll(direct, containerWithValue))
          .isEqualTo(Traversals.getAll(manual, containerWithValue));
      assertThat(Traversals.getAll(direct, containerEmpty))
          .isEqualTo(Traversals.getAll(manual, containerEmpty));

      // Both should produce same results for modify
      assertThat(
              Traversals.modify(
                  direct,
                  inner -> new Inner(inner.value().toUpperCase(), inner.count()),
                  containerWithValue))
          .isEqualTo(
              Traversals.modify(
                  manual,
                  inner -> new Inner(inner.value().toUpperCase(), inner.count()),
                  containerWithValue));
      assertThat(
              Traversals.modify(
                  direct,
                  inner -> new Inner(inner.value().toUpperCase(), inner.count()),
                  containerEmpty))
          .isEqualTo(
              Traversals.modify(
                  manual,
                  inner -> new Inner(inner.value().toUpperCase(), inner.count()),
                  containerEmpty));
    }
  }
}
