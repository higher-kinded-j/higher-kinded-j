// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for composing {@link Lens} with {@link Prism} to produce an {@link Affine}.
 *
 * <p>This composition follows the standard optic composition rule: Lens >>> Prism = Affine.
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
  @DisplayName("Lens.andThen(Prism) produces Affine")
  class LensAndThenPrism {

    @Test
    @DisplayName("should compose to an Affine")
    void composesToAffine() {
      Affine<Container, Inner> affine = innerLens.andThen(somePrism);

      assertThat(affine).isNotNull();
    }

    @Test
    @DisplayName("getOptional should return value when prism matches")
    void affine_getOptional_returnsValueWhenPresent() {
      Affine<Container, Inner> affine = innerLens.andThen(somePrism);
      Container container = new Container(Optional.of(new Inner("hello", 42)));

      Optional<Inner> result = affine.getOptional(container);

      assertThat(result).contains(new Inner("hello", 42));
    }

    @Test
    @DisplayName("getOptional should return empty when prism does not match")
    void affine_getOptional_returnsEmptyWhenAbsent() {
      Affine<Container, Inner> affine = innerLens.andThen(somePrism);
      Container container = new Container(Optional.empty());

      Optional<Inner> result = affine.getOptional(container);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("modify should apply function when prism matches")
    void affine_modify_appliesWhenPresent() {
      Affine<Container, Inner> affine = innerLens.andThen(somePrism);
      Container container = new Container(Optional.of(new Inner("hello", 42)));

      Container result =
          affine.modify(inner -> new Inner(inner.value().toUpperCase(), inner.count()), container);

      assertThat(result.inner()).contains(new Inner("HELLO", 42));
    }

    @Test
    @DisplayName("modify should return unchanged when prism does not match")
    void affine_modify_returnsUnchangedWhenAbsent() {
      Affine<Container, Inner> affine = innerLens.andThen(somePrism);
      Container container = new Container(Optional.empty());

      Container result =
          affine.modify(inner -> new Inner(inner.value().toUpperCase(), inner.count()), container);

      assertThat(result).isEqualTo(container);
    }

    @Test
    @DisplayName("should chain with a lens to produce another affine")
    void affine_canChainWithLens() {
      Affine<Container, Inner> innerAffine = innerLens.andThen(somePrism);
      Affine<Container, String> valueAffine = innerAffine.andThen(valueLens);

      Container container = new Container(Optional.of(new Inner("hello", 42)));

      Optional<String> value = valueAffine.getOptional(container);
      assertThat(value).contains("hello");

      Container modified = valueAffine.modify(String::toUpperCase, container);
      assertThat(modified.inner()).contains(new Inner("HELLO", 42));
    }

    @Test
    @DisplayName("should preserve other fields when modifying nested value")
    void affine_preservesOtherFields() {
      Affine<Container, Inner> affine = innerLens.andThen(somePrism);
      Container container = new Container(Optional.of(new Inner("hello", 42)));

      Container result =
          affine.modify(inner -> new Inner(inner.value().toUpperCase(), inner.count()), container);

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
      Affine<Wrapper, JsonString> affine = dataLens.andThen(jsonStringPrism);
      Wrapper wrapper = new Wrapper(new JsonString("hello"));

      Optional<JsonString> result = affine.getOptional(wrapper);

      assertThat(result).contains(new JsonString("hello"));
    }

    @Test
    @DisplayName("should not match when sealed interface case does not match")
    void doesNotMatchDifferentSealedInterfaceCase() {
      Affine<Wrapper, JsonString> affine = dataLens.andThen(jsonStringPrism);
      Wrapper wrapper = new Wrapper(new JsonNumber(42));

      Optional<JsonString> result = affine.getOptional(wrapper);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("modify should only affect matching case")
    void modifyOnlyAffectsMatchingCase() {
      Affine<Wrapper, JsonString> affine = dataLens.andThen(jsonStringPrism);

      Wrapper stringWrapper = new Wrapper(new JsonString("hello"));
      Wrapper numberWrapper = new Wrapper(new JsonNumber(42));

      Wrapper modifiedString =
          affine.modify(s -> new JsonString(s.value().toUpperCase()), stringWrapper);
      Wrapper modifiedNumber =
          affine.modify(s -> new JsonString(s.value().toUpperCase()), numberWrapper);

      assertThat(modifiedString.data()).isEqualTo(new JsonString("HELLO"));
      assertThat(modifiedNumber.data()).isEqualTo(new JsonNumber(42));
    }
  }

  @Nested
  @DisplayName("Comparison with asTraversal approach")
  class ComparisonTests {

    @Test
    @DisplayName(
        "andThen(Prism) Affine should behave same as asTraversal().andThen(prism.asTraversal())")
    void behavesLikeAsTraversalComposition() {
      Container containerWithValue = new Container(Optional.of(new Inner("test", 100)));
      Container containerEmpty = new Container(Optional.empty());

      // Direct composition returns Affine
      Affine<Container, Inner> direct = innerLens.andThen(somePrism);

      // Manual composition via asTraversal returns Traversal
      Traversal<Container, Inner> manual = innerLens.asTraversal().andThen(somePrism.asTraversal());

      // Both should produce same results for getOptional/getAll
      assertThat(direct.getOptional(containerWithValue).stream().toList())
          .isEqualTo(Traversals.getAll(manual, containerWithValue));
      assertThat(direct.getOptional(containerEmpty).stream().toList())
          .isEqualTo(Traversals.getAll(manual, containerEmpty));

      // Both should produce same results for modify
      assertThat(
              direct.modify(
                  inner -> new Inner(inner.value().toUpperCase(), inner.count()),
                  containerWithValue))
          .isEqualTo(
              Traversals.modify(
                  manual,
                  inner -> new Inner(inner.value().toUpperCase(), inner.count()),
                  containerWithValue));
      assertThat(
              direct.modify(
                  inner -> new Inner(inner.value().toUpperCase(), inner.count()), containerEmpty))
          .isEqualTo(
              Traversals.modify(
                  manual,
                  inner -> new Inner(inner.value().toUpperCase(), inner.count()),
                  containerEmpty));
    }
  }
}
