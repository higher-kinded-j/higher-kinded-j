// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Optic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IndexedOptic Tests")
class IndexedOpticTest {

  // Test data structures
  record Person(String name, int age) {}

  record Container(List<Person> people) {}

  // A simple indexed lens for testing
  private IndexedOptic<Integer, Person, String> indexedNameOptic;

  @BeforeEach
  void setUp() {
    // Create an indexed optic that provides index 0 for person name
    indexedNameOptic =
        new IndexedOptic<>() {
          @Override
          public <F> Kind<F, Person> imodifyF(
              BiFunction<Integer, String, Kind<F, String>> f, Person source, Applicative<F> app) {
            return app.map(newName -> new Person(newName, source.age()), f.apply(0, source.name()));
          }
        };
  }

  @Nested
  @DisplayName("unindexed Operation Tests")
  class UnindexedOperationTests {

    @Test
    @DisplayName("unindexed should create a regular optic that ignores index")
    void unindexedCreatesRegularOptic() {
      Optic<Person, Person, String, String> regularOptic = indexedNameOptic.unindexed();

      Person person = new Person("Alice", 30);
      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));

      Kind<OptionalKind.Witness, Person> result =
          regularOptic.modifyF(modifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result))
          .isPresent()
          .hasValueSatisfying(p -> assertThat(p.name()).isEqualTo("ALICE"));
    }

    @Test
    @DisplayName("unindexed should handle empty result from modifier")
    void unindexedHandlesEmptyResult() {
      Optic<Person, Person, String, String> regularOptic = indexedNameOptic.unindexed();

      Person person = new Person("Bob", 25);
      Function<String, Kind<OptionalKind.Witness, String>> emptyModifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.empty());

      Kind<OptionalKind.Witness, Person> result =
          regularOptic.modifyF(emptyModifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("iandThen Composition Tests")
  class IandThenCompositionTests {

    @Test
    @DisplayName("iandThen should compose two indexed optics with paired indices")
    void iandThenComposesTwoOptics() {
      // Create an indexed optic that focuses on individual characters of a string
      IndexedOptic<String, String, Character> innerOptic =
          new IndexedOptic<>() {
            @Override
            public <F> Kind<F, String> imodifyF(
                BiFunction<String, Character, Kind<F, Character>> f,
                String source,
                Applicative<F> app) {
              if (source.isEmpty()) {
                return app.of(source);
              }
              return app.map(c -> "" + c, f.apply("char-key", source.charAt(0)));
            }
          };

      // Compose: Person -[Integer]-> String -[String]-> Character
      IndexedOptic<Pair<Integer, String>, Person, Character> composed =
          indexedNameOptic.iandThen(innerOptic);

      Person person = new Person("Charlie", 35);

      // Track indices received
      List<Pair<Integer, String>> receivedIndices = new ArrayList<>();

      BiFunction<Pair<Integer, String>, Character, Kind<OptionalKind.Witness, Character>>
          capturingModifier =
              (pair, value) -> {
                receivedIndices.add(pair);
                return OptionalKindHelper.OPTIONAL.widen(Optional.of(Character.toUpperCase(value)));
              };

      Kind<OptionalKind.Witness, Person> result =
          composed.imodifyF(capturingModifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result))
          .isPresent()
          .hasValueSatisfying(p -> assertThat(p.name()).isEqualTo("C")); // First char uppercased

      assertThat(receivedIndices).hasSize(1);
      assertThat(receivedIndices.get(0).first()).isEqualTo(0);
      assertThat(receivedIndices.get(0).second()).isEqualTo("char-key");
    }

    @Test
    @DisplayName("iandThen should propagate empty through composition")
    void iandThenPropagatesEmpty() {
      IndexedOptic<String, String, Character> charOptic =
          new IndexedOptic<>() {
            @Override
            public <F> Kind<F, String> imodifyF(
                BiFunction<String, Character, Kind<F, Character>> f,
                String source,
                Applicative<F> app) {
              if (source.isEmpty()) {
                return app.of(source);
              }
              return app.map(c -> "" + c, f.apply("char", source.charAt(0)));
            }
          };

      IndexedOptic<Pair<Integer, String>, Person, Character> composed =
          indexedNameOptic.iandThen(charOptic);

      Person person = new Person("Dave", 40);

      BiFunction<Pair<Integer, String>, Character, Kind<OptionalKind.Witness, Character>>
          emptyModifier = (pair, c) -> OptionalKindHelper.OPTIONAL.widen(Optional.empty());

      Kind<OptionalKind.Witness, Person> result =
          composed.imodifyF(emptyModifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("andThen Composition Tests")
  class AndThenCompositionTests {

    @Test
    @DisplayName("andThen should compose indexed optic with regular optic preserving index")
    void andThenPreservesIndex() {
      // Create a regular lens for string length
      Lens<String, Integer> stringLengthLens = Lens.of(String::length, (s, len) -> "x".repeat(len));

      // Compose indexed optic with regular optic
      IndexedOptic<Integer, Person, Integer> composed = indexedNameOptic.andThen(stringLengthLens);

      Person person = new Person("Eve", 28);

      List<Integer> receivedIndices = new ArrayList<>();

      BiFunction<Integer, Integer, Kind<OptionalKind.Witness, Integer>> capturingModifier =
          (idx, length) -> {
            receivedIndices.add(idx);
            return OptionalKindHelper.OPTIONAL.widen(Optional.of(length * 2));
          };

      Kind<OptionalKind.Witness, Person> result =
          composed.imodifyF(capturingModifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result))
          .isPresent()
          .hasValueSatisfying(
              p -> assertThat(p.name()).isEqualTo("xxxxxx")); // "Eve".length() * 2 = 6

      assertThat(receivedIndices).containsExactly(0);
    }

    @Test
    @DisplayName("andThen should propagate empty from regular optic")
    void andThenPropagatesEmptyFromRegular() {
      // Create an optic that always returns empty
      Optic<String, String, Integer, Integer> emptyOptic =
          new Optic<>() {
            @Override
            public <F> Kind<F, String> modifyF(
                Function<Integer, Kind<F, Integer>> f, String s, Applicative<F> app) {
              return app.map(i -> "x".repeat(i), f.apply(s.length()));
            }
          };

      IndexedOptic<Integer, Person, Integer> composed = indexedNameOptic.andThen(emptyOptic);

      Person person = new Person("Frank", 45);

      BiFunction<Integer, Integer, Kind<OptionalKind.Witness, Integer>> emptyModifier =
          (idx, val) -> OptionalKindHelper.OPTIONAL.widen(Optional.empty());

      Kind<OptionalKind.Witness, Person> result =
          composed.imodifyF(emptyModifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Pair Tests")
  class PairTests {

    @Test
    @DisplayName("Pair.of should create a pair with given values")
    void pairOfCreatesCorrectPair() {
      Pair<Integer, String> pair = Pair.of(42, "hello");

      assertThat(pair.first()).isEqualTo(42);
      assertThat(pair.second()).isEqualTo("hello");
    }

    @Test
    @DisplayName("withFirst should create new pair with modified first element")
    void withFirstModifiesFirst() {
      Pair<Integer, String> original = new Pair<>(1, "a");
      Pair<String, String> modified = original.withFirst("one");

      assertThat(modified.first()).isEqualTo("one");
      assertThat(modified.second()).isEqualTo("a");
      // Original unchanged
      assertThat(original.first()).isEqualTo(1);
    }

    @Test
    @DisplayName("withSecond should create new pair with modified second element")
    void withSecondModifiesSecond() {
      Pair<Integer, String> original = new Pair<>(1, "a");
      Pair<Integer, Integer> modified = original.withSecond(100);

      assertThat(modified.first()).isEqualTo(1);
      assertThat(modified.second()).isEqualTo(100);
      // Original unchanged
      assertThat(original.second()).isEqualTo("a");
    }

    @Test
    @DisplayName("swap should swap first and second elements")
    void swapSwapsElements() {
      Pair<Integer, String> original = new Pair<>(42, "hello");
      Pair<String, Integer> swapped = original.swap();

      assertThat(swapped.first()).isEqualTo("hello");
      assertThat(swapped.second()).isEqualTo(42);
    }

    @Test
    @DisplayName("swap twice should return to original")
    void swapTwiceReturnsOriginal() {
      Pair<Integer, String> original = new Pair<>(42, "hello");
      Pair<Integer, String> swappedTwice = original.swap().swap();

      assertThat(swappedTwice).isEqualTo(original);
    }

    @Test
    @DisplayName("Pair with null values should be allowed")
    void pairWithNullValues() {
      Pair<String, String> pair = new Pair<>(null, null);

      assertThat(pair.first()).isNull();
      assertThat(pair.second()).isNull();
    }

    @Test
    @DisplayName("Pair equality should work correctly")
    void pairEquality() {
      Pair<Integer, String> pair1 = new Pair<>(1, "a");
      Pair<Integer, String> pair2 = new Pair<>(1, "a");
      Pair<Integer, String> pair3 = new Pair<>(2, "a");

      assertThat(pair1).isEqualTo(pair2);
      assertThat(pair1).isNotEqualTo(pair3);
    }
  }
}
