// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.each;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.extensions.EachExtensions;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EachInstances Tests")
class EachInstancesTest {

  @Nested
  @DisplayName("List Each Instance")
  class ListEachTests {

    private final Each<List<String>, String> listEach = EachInstances.listEach();

    @Test
    @DisplayName("each() should return traversal for list elements")
    void eachReturnsTraversal() {
      Traversal<List<String>, String> traversal = listEach.each();
      assertThat(traversal).isNotNull();
    }

    @Test
    @DisplayName("each() should traverse all elements")
    void eachTraversesAllElements() {
      List<String> list = List.of("a", "b", "c");
      Traversal<List<String>, String> traversal = listEach.each();

      List<String> elements = Traversals.getAll(traversal, list);

      assertThat(elements).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("each() should modify all elements")
    void eachModifiesAllElements() {
      List<String> list = List.of("hello", "world");
      Traversal<List<String>, String> traversal = listEach.each();

      List<String> modified = Traversals.modify(traversal, String::toUpperCase, list);

      assertThat(modified).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("each() should handle empty list")
    void eachHandlesEmptyList() {
      List<String> list = List.of();
      Traversal<List<String>, String> traversal = listEach.each();

      List<String> elements = Traversals.getAll(traversal, list);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("eachWithIndex() should return indexed traversal")
    void eachWithIndexReturnsIndexedTraversal() {
      Optional<IndexedTraversal<Integer, List<String>, String>> indexed = listEach.eachWithIndex();

      assertThat(indexed).isPresent();
    }

    @Test
    @DisplayName("eachWithIndex() should provide element indices")
    void eachWithIndexProvidesIndices() {
      List<String> list = List.of("a", "b", "c");
      IndexedTraversal<Integer, List<String>, String> indexed =
          listEach.<Integer>eachWithIndex().orElseThrow();

      List<String> modified = IndexedTraversals.imodify(indexed, (i, s) -> i + ":" + s, list);

      assertThat(modified).containsExactly("0:a", "1:b", "2:c");
    }

    @Test
    @DisplayName("supportsIndexed() should return true for List")
    void supportsIndexedReturnsTrue() {
      assertThat(listEach.supportsIndexed()).isTrue();
    }

    @Test
    @DisplayName("listEach() should return new instance each time")
    void listEachReturnsNewInstance() {
      Each<List<String>, String> first = EachInstances.listEach();
      Each<List<String>, String> second = EachInstances.listEach();

      // New instances are created each time due to Java's type erasure limitations
      assertThat(first).isNotSameAs(second);
    }
  }

  @Nested
  @DisplayName("Set Each Instance")
  class SetEachTests {

    private final Each<Set<String>, String> setEach = EachInstances.setEach();

    @Test
    @DisplayName("each() should traverse all elements")
    void eachTraversesAllElements() {
      Set<String> set = Set.of("a", "b", "c");
      Traversal<Set<String>, String> traversal = setEach.each();

      List<String> elements = Traversals.getAll(traversal, set);

      assertThat(elements).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    @DisplayName("each() should modify all elements")
    void eachModifiesAllElements() {
      Set<String> set = Set.of("hello", "world");
      Traversal<Set<String>, String> traversal = setEach.each();

      Set<String> modified = Traversals.modify(traversal, String::toUpperCase, set);

      assertThat(modified).containsExactlyInAnyOrder("HELLO", "WORLD");
    }

    @Test
    @DisplayName("supportsIndexed() should return false for Set")
    void supportsIndexedReturnsFalse() {
      assertThat(setEach.supportsIndexed()).isFalse();
    }

    @Test
    @DisplayName("eachWithIndex() should return empty for Set")
    void eachWithIndexReturnsEmpty() {
      Optional<IndexedTraversal<Object, Set<String>, String>> indexed = setEach.eachWithIndex();

      assertThat(indexed).isEmpty();
    }
  }

  @Nested
  @DisplayName("Map Values Each Instance")
  class MapValuesEachTests {

    private final Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();

    @Test
    @DisplayName("each() should traverse all values")
    void eachTraversesAllValues() {
      Map<String, Integer> map = Map.of("a", 1, "b", 2, "c", 3);
      Traversal<Map<String, Integer>, Integer> traversal = mapEach.each();

      List<Integer> values = Traversals.getAll(traversal, map);

      assertThat(values).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("each() should modify all values")
    void eachModifiesAllValues() {
      Map<String, Integer> map = new HashMap<>();
      map.put("a", 1);
      map.put("b", 2);
      Traversal<Map<String, Integer>, Integer> traversal = mapEach.each();

      Map<String, Integer> modified = Traversals.modify(traversal, x -> x * 10, map);

      assertThat(modified).containsEntry("a", 10).containsEntry("b", 20);
    }

    @Test
    @DisplayName("eachWithIndex() should provide key indices")
    void eachWithIndexProvidesKeyIndices() {
      Map<String, Integer> map = new LinkedHashMap<>();
      map.put("first", 1);
      map.put("second", 2);
      IndexedTraversal<String, Map<String, Integer>, Integer> indexed =
          mapEach.<String>eachWithIndex().orElseThrow();

      var pairs = IndexedTraversals.toIndexedList(indexed, map);

      assertThat(pairs).hasSize(2);
      assertThat(pairs.stream().map(p -> p.first() + ":" + p.second()))
          .containsExactlyInAnyOrder("first:1", "second:2");
    }

    @Test
    @DisplayName("supportsIndexed() should return true for Map")
    void supportsIndexedReturnsTrue() {
      assertThat(mapEach.supportsIndexed()).isTrue();
    }
  }

  @Nested
  @DisplayName("Optional Each Instance")
  class OptionalEachTests {

    private final Each<Optional<String>, String> optionalEach = EachInstances.optionalEach();

    @Test
    @DisplayName("each() should traverse present value")
    void eachTraversesPresentValue() {
      Optional<String> opt = Optional.of("hello");
      Traversal<Optional<String>, String> traversal = optionalEach.each();

      List<String> elements = Traversals.getAll(traversal, opt);

      assertThat(elements).containsExactly("hello");
    }

    @Test
    @DisplayName("each() should return empty for empty optional")
    void eachReturnsEmptyForEmptyOptional() {
      Optional<String> opt = Optional.empty();
      Traversal<Optional<String>, String> traversal = optionalEach.each();

      List<String> elements = Traversals.getAll(traversal, opt);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("each() should modify present value")
    void eachModifiesPresentValue() {
      Optional<String> opt = Optional.of("hello");
      Traversal<Optional<String>, String> traversal = optionalEach.each();

      Optional<String> modified = Traversals.modify(traversal, String::toUpperCase, opt);

      assertThat(modified).hasValue("HELLO");
    }

    @Test
    @DisplayName("supportsIndexed() should return false for Optional")
    void supportsIndexedReturnsFalse() {
      assertThat(optionalEach.supportsIndexed()).isFalse();
    }
  }

  @Nested
  @DisplayName("Maybe Each Instance")
  class MaybeEachTests {

    private final Each<Maybe<String>, String> maybeEach = EachExtensions.maybeEach();

    @Test
    @DisplayName("each() should traverse Just value")
    void eachTraversesJustValue() {
      Maybe<String> maybe = Maybe.just("hello");
      Traversal<Maybe<String>, String> traversal = maybeEach.each();

      List<String> elements = Traversals.getAll(traversal, maybe);

      assertThat(elements).containsExactly("hello");
    }

    @Test
    @DisplayName("each() should return empty for Nothing")
    void eachReturnsEmptyForNothing() {
      Maybe<String> maybe = Maybe.nothing();
      Traversal<Maybe<String>, String> traversal = maybeEach.each();

      List<String> elements = Traversals.getAll(traversal, maybe);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("each() should modify Just value")
    void eachModifiesJustValue() {
      Maybe<String> maybe = Maybe.just("hello");
      Traversal<Maybe<String>, String> traversal = maybeEach.each();

      Maybe<String> modified = Traversals.modify(traversal, String::toUpperCase, maybe);

      assertThat(modified.isJust()).isTrue();
      assertThat(modified.get()).isEqualTo("HELLO");
    }
  }

  @Nested
  @DisplayName("Either Right Each Instance")
  class EitherRightEachTests {

    private final Each<Either<String, Integer>, Integer> eitherEach =
        EachExtensions.eitherRightEach();

    @Test
    @DisplayName("each() should traverse Right value")
    void eachTraversesRightValue() {
      Either<String, Integer> either = Either.right(42);
      Traversal<Either<String, Integer>, Integer> traversal = eitherEach.each();

      List<Integer> elements = Traversals.getAll(traversal, either);

      assertThat(elements).containsExactly(42);
    }

    @Test
    @DisplayName("each() should return empty for Left")
    void eachReturnsEmptyForLeft() {
      Either<String, Integer> either = Either.left("error");
      Traversal<Either<String, Integer>, Integer> traversal = eitherEach.each();

      List<Integer> elements = Traversals.getAll(traversal, either);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("each() should modify Right value")
    void eachModifiesRightValue() {
      Either<String, Integer> either = Either.right(10);
      Traversal<Either<String, Integer>, Integer> traversal = eitherEach.each();

      Either<String, Integer> modified = Traversals.modify(traversal, x -> x * 2, either);

      assertThat(modified.isRight()).isTrue();
      assertThat(modified.getRight()).isEqualTo(20);
    }

    @Test
    @DisplayName("each() should not modify Left")
    void eachDoesNotModifyLeft() {
      Either<String, Integer> either = Either.left("error");
      Traversal<Either<String, Integer>, Integer> traversal = eitherEach.each();

      Either<String, Integer> modified = Traversals.modify(traversal, x -> x * 2, either);

      assertThat(modified.isLeft()).isTrue();
      assertThat(modified.getLeft()).isEqualTo("error");
    }
  }

  @Nested
  @DisplayName("Try Success Each Instance")
  class TrySuccessEachTests {

    private final Each<Try<Integer>, Integer> tryEach = EachExtensions.trySuccessEach();

    @Test
    @DisplayName("each() should traverse Success value")
    void eachTraversesSuccessValue() {
      Try<Integer> tryValue = Try.success(42);
      Traversal<Try<Integer>, Integer> traversal = tryEach.each();

      List<Integer> elements = Traversals.getAll(traversal, tryValue);

      assertThat(elements).containsExactly(42);
    }

    @Test
    @DisplayName("each() should return empty for Failure")
    void eachReturnsEmptyForFailure() {
      Try<Integer> tryValue = Try.failure(new RuntimeException("error"));
      Traversal<Try<Integer>, Integer> traversal = tryEach.each();

      List<Integer> elements = Traversals.getAll(traversal, tryValue);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("each() should modify Success value")
    void eachModifiesSuccessValue() throws Throwable {
      Try<Integer> tryValue = Try.success(10);
      Traversal<Try<Integer>, Integer> traversal = tryEach.each();

      Try<Integer> modified = Traversals.modify(traversal, x -> x * 2, tryValue);

      assertThat(modified.isSuccess()).isTrue();
      assertThat(modified.get()).isEqualTo(20);
    }
  }

  @Nested
  @DisplayName("Validated Each Instance")
  class ValidatedEachTests {

    private final Each<Validated<String, Integer>, Integer> validatedEach =
        EachExtensions.validatedEach();

    @Test
    @DisplayName("each() should traverse Valid value")
    void eachTraversesValidValue() {
      Validated<String, Integer> validated = Validated.valid(42);
      Traversal<Validated<String, Integer>, Integer> traversal = validatedEach.each();

      List<Integer> elements = Traversals.getAll(traversal, validated);

      assertThat(elements).containsExactly(42);
    }

    @Test
    @DisplayName("each() should return empty for Invalid")
    void eachReturnsEmptyForInvalid() {
      Validated<String, Integer> validated = Validated.invalid("error");
      Traversal<Validated<String, Integer>, Integer> traversal = validatedEach.each();

      List<Integer> elements = Traversals.getAll(traversal, validated);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("each() should modify Valid value")
    void eachModifiesValidValue() {
      Validated<String, Integer> validated = Validated.valid(10);
      Traversal<Validated<String, Integer>, Integer> traversal = validatedEach.each();

      Validated<String, Integer> modified = Traversals.modify(traversal, x -> x * 2, validated);

      assertThat(modified.isValid()).isTrue();
      assertThat(modified.get()).isEqualTo(20);
    }
  }

  @Nested
  @DisplayName("Array Each Instance")
  class ArrayEachTests {

    private final Each<String[], String> arrayEach = EachInstances.arrayEach();

    @Test
    @DisplayName("each() should traverse all elements")
    void eachTraversesAllElements() {
      String[] array = {"a", "b", "c"};
      Traversal<String[], String> traversal = arrayEach.each();

      List<String> elements = Traversals.getAll(traversal, array);

      assertThat(elements).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("each() should modify all elements")
    void eachModifiesAllElements() {
      String[] array = {"hello", "world"};
      Traversal<String[], String> traversal = arrayEach.each();

      String[] modified = Traversals.modify(traversal, String::toUpperCase, array);

      assertThat(modified).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("each() should handle empty array")
    void eachHandlesEmptyArray() {
      String[] array = {};
      Traversal<String[], String> traversal = arrayEach.each();

      List<String> elements = Traversals.getAll(traversal, array);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("eachWithIndex() should return indexed traversal")
    void eachWithIndexReturnsIndexedTraversal() {
      Optional<IndexedTraversal<Integer, String[], String>> indexed = arrayEach.eachWithIndex();

      assertThat(indexed).isPresent();
    }

    @Test
    @DisplayName("eachWithIndex() should provide element indices")
    void eachWithIndexProvidesIndices() {
      String[] array = {"a", "b", "c"};
      IndexedTraversal<Integer, String[], String> indexed =
          arrayEach.<Integer>eachWithIndex().orElseThrow();

      String[] modified = IndexedTraversals.imodify(indexed, (i, s) -> i + ":" + s, array);

      assertThat(modified).containsExactly("0:a", "1:b", "2:c");
    }

    @Test
    @DisplayName("eachWithIndex() should handle empty array")
    void eachWithIndexHandlesEmptyArray() {
      String[] array = {};
      IndexedTraversal<Integer, String[], String> indexed =
          arrayEach.<Integer>eachWithIndex().orElseThrow();

      String[] modified = IndexedTraversals.imodify(indexed, (i, s) -> i + ":" + s, array);

      assertThat(modified).isEmpty();
    }
  }

  @Nested
  @DisplayName("String Chars Each Instance")
  class StringCharsEachTests {

    private final Each<String, Character> stringEach = EachInstances.stringCharsEach();

    @Test
    @DisplayName("each() should traverse all characters")
    void eachTraversesAllCharacters() {
      String str = "hello";
      Traversal<String, Character> traversal = stringEach.each();

      List<Character> chars = Traversals.getAll(traversal, str);

      assertThat(chars).containsExactly('h', 'e', 'l', 'l', 'o');
    }

    @Test
    @DisplayName("each() should modify all characters")
    void eachModifiesAllCharacters() {
      String str = "abc";
      Traversal<String, Character> traversal = stringEach.each();

      String modified = Traversals.modify(traversal, Character::toUpperCase, str);

      assertThat(modified).isEqualTo("ABC");
    }

    @Test
    @DisplayName("each() should handle empty string")
    void eachHandlesEmptyString() {
      String str = "";
      Traversal<String, Character> traversal = stringEach.each();

      List<Character> chars = Traversals.getAll(traversal, str);

      assertThat(chars).isEmpty();
    }

    @Test
    @DisplayName("eachWithIndex() should provide character indices")
    void eachWithIndexProvidesIndices() {
      String str = "abc";
      IndexedTraversal<Integer, String, Character> indexed =
          stringEach.<Integer>eachWithIndex().orElseThrow();

      var pairs = IndexedTraversals.toIndexedList(indexed, str);

      assertThat(pairs).hasSize(3);
      assertThat(pairs.get(0).first()).isEqualTo(0);
      assertThat(pairs.get(0).second()).isEqualTo('a');
      assertThat(pairs.get(1).first()).isEqualTo(1);
      assertThat(pairs.get(1).second()).isEqualTo('b');
      assertThat(pairs.get(2).first()).isEqualTo(2);
      assertThat(pairs.get(2).second()).isEqualTo('c');
    }

    @Test
    @DisplayName("eachWithIndex() should handle empty string")
    void eachWithIndexHandlesEmptyString() {
      String str = "";
      IndexedTraversal<Integer, String, Character> indexed =
          stringEach.<Integer>eachWithIndex().orElseThrow();

      var pairs = IndexedTraversals.toIndexedList(indexed, str);

      assertThat(pairs).isEmpty();
    }
  }

  @Nested
  @DisplayName("Stream Each Instance")
  class StreamEachTests {

    @Test
    @DisplayName("each() should traverse stream elements")
    void eachTraversesStreamElements() {
      Each<Stream<String>, String> streamEach = EachInstances.streamEach();
      Stream<String> stream = Stream.of("a", "b", "c");
      Traversal<Stream<String>, String> traversal = streamEach.each();

      List<String> elements = Traversals.getAll(traversal, stream);

      assertThat(elements).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("each() should modify stream elements")
    void eachModifiesStreamElements() {
      Each<Stream<String>, String> streamEach = EachInstances.streamEach();
      Stream<String> stream = Stream.of("hello", "world");
      Traversal<Stream<String>, String> traversal = streamEach.each();

      Stream<String> modified = Traversals.modify(traversal, String::toUpperCase, stream);

      assertThat(modified.toList()).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("supportsIndexed() should return false for Stream")
    void supportsIndexedReturnsFalse() {
      Each<Stream<String>, String> streamEach = EachInstances.streamEach();
      assertThat(streamEach.supportsIndexed()).isFalse();
    }
  }

  @Nested
  @DisplayName("Each.fromTraversal() Factory")
  class FromTraversalTests {

    @Test
    @DisplayName("fromTraversal() should wrap traversal as Each")
    void fromTraversalWrapsTraversal() {
      Traversal<List<String>, String> listTraversal = Traversals.forList();
      Each<List<String>, String> each = Each.fromTraversal(listTraversal);

      List<String> list = List.of("a", "b", "c");
      List<String> elements = Traversals.getAll(each.each(), list);

      assertThat(elements).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("fromTraversal() should not support indexed by default")
    void fromTraversalDoesNotSupportIndexed() {
      Traversal<List<String>, String> listTraversal = Traversals.forList();
      Each<List<String>, String> each = Each.fromTraversal(listTraversal);

      assertThat(each.supportsIndexed()).isFalse();
    }
  }

  @Nested
  @DisplayName("Each.fromIndexedTraversal() Factory")
  class FromIndexedTraversalTests {

    @Test
    @DisplayName("fromIndexedTraversal() should wrap as Each with index support")
    void fromIndexedTraversalWrapsWithIndexSupport() {
      IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();
      Each<List<String>, String> each = Each.fromIndexedTraversal(indexed);

      assertThat(each.supportsIndexed()).isTrue();
    }

    @Test
    @DisplayName("fromIndexedTraversal() should provide both regular and indexed traversal")
    void fromIndexedTraversalProvidesBoth() {
      IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();
      Each<List<String>, String> each = Each.fromIndexedTraversal(indexed);

      List<String> list = List.of("a", "b", "c");

      // Regular traversal
      List<String> elements = Traversals.getAll(each.each(), list);
      assertThat(elements).containsExactly("a", "b", "c");

      // Indexed traversal
      IndexedTraversal<Integer, List<String>, String> retrievedIndexed =
          each.<Integer>eachWithIndex().orElseThrow();
      var pairs = IndexedTraversals.toIndexedList(retrievedIndexed, list);
      assertThat(pairs).hasSize(3);
    }

    @Test
    @DisplayName("fromIndexedTraversal() should preserve index type through generic casting")
    void fromIndexedTraversalPreservesIndexType() {
      IndexedTraversal<String, Map<String, Integer>, Integer> indexed = IndexedTraversals.forMap();
      Each<Map<String, Integer>, Integer> each = Each.fromIndexedTraversal(indexed);

      Map<String, Integer> map = new LinkedHashMap<>();
      map.put("a", 1);
      map.put("b", 2);

      IndexedTraversal<String, Map<String, Integer>, Integer> retrievedIndexed =
          each.<String>eachWithIndex().orElseThrow();
      var pairs = IndexedTraversals.toIndexedList(retrievedIndexed, map);

      assertThat(pairs).hasSize(2);
      assertThat(pairs.stream().anyMatch(p -> p.first().equals("a") && p.second() == 1)).isTrue();
      assertThat(pairs.stream().anyMatch(p -> p.first().equals("b") && p.second() == 2)).isTrue();
    }
  }

  @Nested
  @DisplayName("EachInstances.fromTraverse() Factory")
  class FromTraverseTests {

    @Test
    @DisplayName("fromTraverse() should create Each from Traverse instance")
    void fromTraverseCreatesEach() {
      Traverse<MaybeKind.Witness> maybeTraverse = MaybeTraverse.INSTANCE;
      Each<Kind<MaybeKind.Witness, String>, String> each =
          EachInstances.fromTraverse(maybeTraverse);

      assertThat(each).isNotNull();
      assertThat(each.each()).isNotNull();
    }

    @Test
    @DisplayName("fromTraverse() should traverse Just value")
    void fromTraverseTraversesJust() {
      Traverse<MaybeKind.Witness> maybeTraverse = MaybeTraverse.INSTANCE;
      Each<Kind<MaybeKind.Witness, String>, String> each =
          EachInstances.fromTraverse(maybeTraverse);

      Kind<MaybeKind.Witness, String> maybeKind = MaybeKindHelper.MAYBE.widen(Maybe.just("hello"));
      Traversal<Kind<MaybeKind.Witness, String>, String> traversal = each.each();

      List<String> elements = Traversals.getAll(traversal, maybeKind);

      assertThat(elements).containsExactly("hello");
    }

    @Test
    @DisplayName("fromTraverse() should traverse Nothing as empty")
    void fromTraverseTraversesNothing() {
      Traverse<MaybeKind.Witness> maybeTraverse = MaybeTraverse.INSTANCE;
      Each<Kind<MaybeKind.Witness, String>, String> each =
          EachInstances.fromTraverse(maybeTraverse);

      Kind<MaybeKind.Witness, String> maybeKind = MaybeKindHelper.MAYBE.widen(Maybe.nothing());
      Traversal<Kind<MaybeKind.Witness, String>, String> traversal = each.each();

      List<String> elements = Traversals.getAll(traversal, maybeKind);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("fromTraverse() should not support indexed by default")
    void fromTraverseDoesNotSupportIndexed() {
      Traverse<MaybeKind.Witness> maybeTraverse = MaybeTraverse.INSTANCE;
      Each<Kind<MaybeKind.Witness, String>, String> each =
          EachInstances.fromTraverse(maybeTraverse);

      assertThat(each.supportsIndexed()).isFalse();
      assertThat(each.eachWithIndex()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Instance Creation Verification")
  class InstanceCreationTests {

    // Note: Due to Java's type erasure limitations with parameterized types and wildcard types,
    // EachInstances factory methods create new instances on each call rather than caching.
    // This is because you cannot safely cast from Each<List<?>, ?> to Each<List<A>, A>.

    @Test
    @DisplayName("listEach() should return new instances")
    void listEachReturnsNewInstances() {
      Each<List<String>, String> first = EachInstances.listEach();
      Each<List<String>, String> second = EachInstances.listEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("setEach() should return new instances")
    void setEachReturnsNewInstances() {
      Each<Set<String>, String> first = EachInstances.setEach();
      Each<Set<String>, String> second = EachInstances.setEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("mapValuesEach() should return new instances")
    void mapValuesEachReturnsNewInstances() {
      Each<Map<String, Integer>, Integer> first = EachInstances.mapValuesEach();
      Each<Map<String, Integer>, Integer> second = EachInstances.mapValuesEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("optionalEach() should return new instances")
    void optionalEachReturnsNewInstances() {
      Each<Optional<String>, String> first = EachInstances.optionalEach();
      Each<Optional<String>, String> second = EachInstances.optionalEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("maybeEach() should return new instances")
    void maybeEachReturnsNewInstances() {
      Each<Maybe<String>, String> first = EachExtensions.maybeEach();
      Each<Maybe<String>, String> second = EachExtensions.maybeEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("eitherRightEach() should return new instances")
    void eitherRightEachReturnsNewInstances() {
      Each<Either<String, Integer>, Integer> first = EachExtensions.eitherRightEach();
      Each<Either<String, Integer>, Integer> second = EachExtensions.eitherRightEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("trySuccessEach() should return new instances")
    void trySuccessEachReturnsNewInstances() {
      Each<Try<Integer>, Integer> first = EachExtensions.trySuccessEach();
      Each<Try<Integer>, Integer> second = EachExtensions.trySuccessEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("validatedEach() should return new instances")
    void validatedEachReturnsNewInstances() {
      Each<Validated<String, Integer>, Integer> first = EachExtensions.validatedEach();
      Each<Validated<String, Integer>, Integer> second = EachExtensions.validatedEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("arrayEach() should return new instances")
    void arrayEachReturnsNewInstances() {
      Each<String[], String> first = EachInstances.arrayEach();
      Each<String[], String> second = EachInstances.arrayEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("streamEach() should return new instances")
    void streamEachReturnsNewInstances() {
      Each<Stream<String>, String> first = EachInstances.streamEach();
      Each<Stream<String>, String> second = EachInstances.streamEach();

      assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("stringCharsEach() should return new instances")
    void stringCharsEachReturnsNewInstances() {
      Each<String, Character> first = EachInstances.stringCharsEach();
      Each<String, Character> second = EachInstances.stringCharsEach();

      assertThat(first).isNotSameAs(second);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("List with null elements should handle nulls correctly")
    void listWithNullElements() {
      Each<List<String>, String> listEach = EachInstances.listEach();
      List<String> list = new ArrayList<>();
      list.add("a");
      list.add(null);
      list.add("c");

      List<String> elements = Traversals.getAll(listEach.each(), list);

      assertThat(elements).containsExactly("a", null, "c");
    }

    @Test
    @DisplayName("Set with single element should traverse correctly")
    void setSingleElement() {
      Each<Set<String>, String> setEach = EachInstances.setEach();
      Set<String> set = Set.of("only");

      List<String> elements = Traversals.getAll(setEach.each(), set);

      assertThat(elements).containsExactly("only");
    }

    @Test
    @DisplayName("Map with single entry should traverse correctly")
    void mapSingleEntry() {
      Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
      Map<String, Integer> map = Map.of("key", 42);

      List<Integer> values = Traversals.getAll(mapEach.each(), map);

      assertThat(values).containsExactly(42);
    }

    @Test
    @DisplayName("Empty map should traverse to empty list")
    void emptyMapTraversal() {
      Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
      Map<String, Integer> map = Map.of();

      List<Integer> values = Traversals.getAll(mapEach.each(), map);

      assertThat(values).isEmpty();
    }

    @Test
    @DisplayName("Empty set should traverse to empty list")
    void emptySetTraversal() {
      Each<Set<String>, String> setEach = EachInstances.setEach();
      Set<String> set = Set.of();

      List<String> elements = Traversals.getAll(setEach.each(), set);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("Try with RuntimeException should not traverse")
    void tryWithRuntimeException() {
      Each<Try<Integer>, Integer> tryEach = EachExtensions.trySuccessEach();
      Try<Integer> failure = Try.failure(new RuntimeException("test error"));

      List<Integer> elements = Traversals.getAll(tryEach.each(), failure);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("Validated invalid should not traverse")
    void validatedInvalidNotTraversed() {
      Each<Validated<String, Integer>, Integer> validatedEach = EachExtensions.validatedEach();
      Validated<String, Integer> invalid = Validated.invalid("error message");

      Validated<String, Integer> modified =
          Traversals.modify(validatedEach.each(), x -> x * 2, invalid);

      assertThat(modified.isValid()).isFalse();
      assertThat(modified.getError()).isEqualTo("error message");
    }

    @Test
    @DisplayName("Empty stream should traverse correctly")
    void emptyStreamTraversal() {
      Each<Stream<String>, String> streamEach = EachInstances.streamEach();
      Stream<String> emptyStream = Stream.empty();

      List<String> elements = Traversals.getAll(streamEach.each(), emptyStream);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("Array indexed traversal should handle single element")
    void arraySingleElementIndexed() {
      Each<String[], String> arrayEach = EachInstances.arrayEach();
      String[] array = {"single"};
      IndexedTraversal<Integer, String[], String> indexed =
          arrayEach.<Integer>eachWithIndex().orElseThrow();

      var pairs = IndexedTraversals.toIndexedList(indexed, array);

      assertThat(pairs).hasSize(1);
      assertThat(pairs.get(0).first()).isEqualTo(0);
      assertThat(pairs.get(0).second()).isEqualTo("single");
    }

    @Test
    @DisplayName("String with single character should traverse correctly")
    void singleCharacterString() {
      Each<String, Character> stringEach = EachInstances.stringCharsEach();
      String str = "X";

      List<Character> chars = Traversals.getAll(stringEach.each(), str);

      assertThat(chars).containsExactly('X');
    }

    @Test
    @DisplayName("String indexed traversal should work with special characters")
    void stringSpecialCharacters() {
      Each<String, Character> stringEach = EachInstances.stringCharsEach();
      String str = "a\tb\nc";
      IndexedTraversal<Integer, String, Character> indexed =
          stringEach.<Integer>eachWithIndex().orElseThrow();

      var pairs = IndexedTraversals.toIndexedList(indexed, str);

      assertThat(pairs).hasSize(5);
      assertThat(pairs.get(1).second()).isEqualTo('\t');
      assertThat(pairs.get(3).second()).isEqualTo('\n');
    }

    @Test
    @DisplayName("Modify on Nothing should return Nothing")
    void modifyNothingReturnsNothing() {
      Each<Maybe<String>, String> maybeEach = EachExtensions.maybeEach();
      Maybe<String> nothing = Maybe.nothing();

      Maybe<String> result = Traversals.modify(maybeEach.each(), String::toUpperCase, nothing);

      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Modify on Left should return Left unchanged")
    void modifyLeftReturnsLeft() {
      Each<Either<String, Integer>, Integer> eitherEach = EachExtensions.eitherRightEach();
      Either<String, Integer> left = Either.left("error");

      Either<String, Integer> result = Traversals.modify(eitherEach.each(), x -> x * 2, left);

      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("error");
    }

    @Test
    @DisplayName("Map indexed traversal with modify should update values with keys")
    void mapIndexedModify() {
      Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
      Map<String, Integer> map = new LinkedHashMap<>();
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      IndexedTraversal<String, Map<String, Integer>, Integer> indexed =
          mapEach.<String>eachWithIndex().orElseThrow();

      Map<String, Integer> modified =
          IndexedTraversals.imodify(
              indexed, (key, value) -> key.equals("b") ? value * 10 : value, map);

      assertThat(modified).containsEntry("a", 1).containsEntry("b", 20).containsEntry("c", 3);
    }

    @Test
    @DisplayName("List modify operation via traversal should update all elements")
    void listModifyAllElements() {
      Each<List<String>, String> listEach = EachInstances.listEach();
      List<String> list = List.of("a", "b", "c");

      List<String> result = Traversals.modify(listEach.each(), s -> "X", list);

      assertThat(result).containsExactly("X", "X", "X");
    }
  }

  @Nested
  @DisplayName("Each Interface Default Methods")
  class EachInterfaceTests {

    @Test
    @DisplayName("supportsIndexed() should delegate to eachWithIndex().isPresent()")
    void supportsIndexedDelegatesToEachWithIndex() {
      // Create Each with indexed support
      Each<List<String>, String> withIndexed = EachInstances.listEach();
      assertThat(withIndexed.supportsIndexed()).isEqualTo(withIndexed.eachWithIndex().isPresent());

      // Create Each without indexed support
      Each<Set<String>, String> withoutIndexed = EachInstances.setEach();
      assertThat(withoutIndexed.supportsIndexed())
          .isEqualTo(withoutIndexed.eachWithIndex().isPresent());
    }

    @Test
    @DisplayName("fromTraversal() creates functional interface implementation")
    void fromTraversalCreatesLambda() {
      Traversal<List<String>, String> traversal = Traversals.forList();
      Each<List<String>, String> each = Each.fromTraversal(traversal);

      // Verify functional interface implementation
      assertThat(each.each()).isEqualTo(traversal);
    }

    @Test
    @DisplayName("eachWithIndex() default returns empty Optional")
    void eachWithIndexDefaultReturnsEmpty() {
      // Create minimal Each implementation
      Each<List<String>, String> minimalEach = () -> Traversals.forList();

      assertThat(minimalEach.eachWithIndex()).isEmpty();
      assertThat(minimalEach.supportsIndexed()).isFalse();
    }
  }
}
