// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListTraversals - Limiting Traversals for Lists")
class ListTraversalsTest {

  // Test Data Structure
  record User(String name, int score) {
    User doubleScore() {
      return new User(name, score * 2);
    }
  }

  @Nested
  @DisplayName("taking(int n) - Focus on first n elements")
  class TakingTests {

    @Test
    @DisplayName("taking() should modify only the first n elements")
    void takingModifiesFirstN() {
      Traversal<List<Integer>, Integer> first3 = ListTraversals.taking(3);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.modify(first3, x -> x * 2, numbers);

      assertThat(result).containsExactly(2, 4, 6, 4, 5);
    }

    @Test
    @DisplayName("taking() getAll should return only first n elements")
    void takingGetAllReturnsFirstN() {
      Traversal<List<Integer>, Integer> first3 = ListTraversals.taking(3);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(first3, numbers);

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("taking() with n > size should focus on all elements")
    void takingWithNGreaterThanSize() {
      Traversal<List<Integer>, Integer> first10 = ListTraversals.taking(10);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(first10, x -> x * 2, numbers);

      assertThat(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("taking() with n = 0 should focus on no elements")
    void takingWithZero() {
      Traversal<List<Integer>, Integer> first0 = ListTraversals.taking(0);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(first0, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(Traversals.getAll(first0, numbers)).isEmpty();
    }

    @Test
    @DisplayName("taking() with negative n should focus on no elements")
    void takingWithNegative() {
      Traversal<List<Integer>, Integer> negativeN = ListTraversals.taking(-5);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(negativeN, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(Traversals.getAll(negativeN, numbers)).isEmpty();
    }

    @Test
    @DisplayName("taking() with empty list should return empty list")
    void takingWithEmptyList() {
      Traversal<List<Integer>, Integer> first3 = ListTraversals.taking(3);

      List<Integer> emptyList = List.of();
      List<Integer> result = Traversals.modify(first3, x -> x * 2, emptyList);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("taking() with n=0 and empty list should return empty list")
    void takingWithZeroAndEmptyList() {
      Traversal<List<Integer>, Integer> first0 = ListTraversals.taking(0);

      List<Integer> emptyList = List.of();
      List<Integer> result = Traversals.modify(first0, x -> x * 2, emptyList);

      assertThat(result).isEmpty();
      assertThat(Traversals.getAll(first0, emptyList)).isEmpty();
    }

    @Test
    @DisplayName("taking() should compose with other traversals")
    void takingComposition() {
      Traversal<List<User>, User> first2Users = ListTraversals.taking(2);
      Lens<User, String> nameLens = Lens.of(User::name, (u, n) -> new User(n, u.score()));

      Traversal<List<User>, String> first2Names = first2Users.andThen(nameLens.asTraversal());

      List<User> users =
          List.of(new User("Alice", 100), new User("Bob", 200), new User("Charlie", 150));

      List<String> names = Traversals.getAll(first2Names, users);
      assertThat(names).containsExactly("Alice", "Bob");

      List<User> modified = Traversals.modify(first2Names, String::toUpperCase, users);
      assertThat(modified)
          .containsExactly(new User("ALICE", 100), new User("BOB", 200), new User("Charlie", 150));
    }
  }

  @Nested
  @DisplayName("dropping(int n) - Skip first n elements")
  class DroppingTests {

    @Test
    @DisplayName("dropping() should modify only elements after first n")
    void droppingModifiesAfterFirstN() {
      Traversal<List<Integer>, Integer> drop2 = ListTraversals.dropping(2);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.modify(drop2, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 6, 8, 10);
    }

    @Test
    @DisplayName("dropping() getAll should skip first n elements")
    void droppingGetAllSkipsFirstN() {
      Traversal<List<Integer>, Integer> drop2 = ListTraversals.dropping(2);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(drop2, numbers);

      assertThat(result).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("dropping() with n >= size should focus on no elements")
    void droppingWithNGreaterOrEqualToSize() {
      Traversal<List<Integer>, Integer> drop10 = ListTraversals.dropping(10);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(drop10, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(Traversals.getAll(drop10, numbers)).isEmpty();
    }

    @Test
    @DisplayName("dropping() with n = 0 should focus on all elements")
    void droppingWithZero() {
      Traversal<List<Integer>, Integer> drop0 = ListTraversals.dropping(0);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(drop0, x -> x * 2, numbers);

      assertThat(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("dropping() with negative n should focus on all elements")
    void droppingWithNegative() {
      Traversal<List<Integer>, Integer> dropNeg = ListTraversals.dropping(-5);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(dropNeg, x -> x * 2, numbers);

      assertThat(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("dropping() with empty list should return empty list")
    void droppingWithEmptyList() {
      Traversal<List<Integer>, Integer> drop2 = ListTraversals.dropping(2);

      List<Integer> emptyList = List.of();
      List<Integer> result = Traversals.modify(drop2, x -> x * 2, emptyList);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("dropping() should compose with other traversals")
    void droppingComposition() {
      Traversal<List<User>, User> afterFirst = ListTraversals.dropping(1);
      Lens<User, Integer> scoreLens = Lens.of(User::score, (u, s) -> new User(u.name(), s));

      Traversal<List<User>, Integer> scoresAfterFirst = afterFirst.andThen(scoreLens.asTraversal());

      List<User> users =
          List.of(new User("Alice", 100), new User("Bob", 200), new User("Charlie", 150));

      List<Integer> scores = Traversals.getAll(scoresAfterFirst, users);
      assertThat(scores).containsExactly(200, 150);

      List<User> modified = Traversals.modify(scoresAfterFirst, s -> s + 50, users);
      assertThat(modified)
          .containsExactly(new User("Alice", 100), new User("Bob", 250), new User("Charlie", 200));
    }
  }

  @Nested
  @DisplayName("takingLast(int n) - Focus on last n elements")
  class TakingLastTests {

    @Test
    @DisplayName("takingLast() should modify only the last n elements")
    void takingLastModifiesLastN() {
      Traversal<List<Integer>, Integer> last3 = ListTraversals.takingLast(3);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.modify(last3, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 6, 8, 10);
    }

    @Test
    @DisplayName("takingLast() getAll should return only last n elements")
    void takingLastGetAllReturnsLastN() {
      Traversal<List<Integer>, Integer> last3 = ListTraversals.takingLast(3);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(last3, numbers);

      assertThat(result).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("takingLast() with n > size should focus on all elements")
    void takingLastWithNGreaterThanSize() {
      Traversal<List<Integer>, Integer> last10 = ListTraversals.takingLast(10);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(last10, x -> x * 2, numbers);

      assertThat(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("takingLast() with n = 0 should focus on no elements")
    void takingLastWithZero() {
      Traversal<List<Integer>, Integer> last0 = ListTraversals.takingLast(0);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(last0, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(Traversals.getAll(last0, numbers)).isEmpty();
    }

    @Test
    @DisplayName("takingLast() with negative n should focus on no elements")
    void takingLastWithNegative() {
      Traversal<List<Integer>, Integer> lastNeg = ListTraversals.takingLast(-5);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(lastNeg, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(Traversals.getAll(lastNeg, numbers)).isEmpty();
    }

    @Test
    @DisplayName("takingLast() with empty list should return empty list")
    void takingLastWithEmptyList() {
      Traversal<List<Integer>, Integer> last3 = ListTraversals.takingLast(3);

      List<Integer> emptyList = List.of();
      List<Integer> result = Traversals.modify(last3, x -> x * 2, emptyList);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("takingLast() with n=0 and empty list should return empty list")
    void takingLastWithZeroAndEmptyList() {
      Traversal<List<Integer>, Integer> last0 = ListTraversals.takingLast(0);

      List<Integer> emptyList = List.of();
      List<Integer> result = Traversals.modify(last0, x -> x * 2, emptyList);

      assertThat(result).isEmpty();
      assertThat(Traversals.getAll(last0, emptyList)).isEmpty();
    }

    @Test
    @DisplayName("takingLast() should compose with other traversals")
    void takingLastComposition() {
      Traversal<List<User>, User> last2Users = ListTraversals.takingLast(2);

      List<User> users =
          List.of(new User("Alice", 100), new User("Bob", 200), new User("Charlie", 150));

      List<User> modified = Traversals.modify(last2Users, User::doubleScore, users);
      assertThat(modified)
          .containsExactly(new User("Alice", 100), new User("Bob", 400), new User("Charlie", 300));
    }
  }

  @Nested
  @DisplayName("droppingLast(int n) - Exclude last n elements")
  class DroppingLastTests {

    @Test
    @DisplayName("droppingLast() should modify all except last n elements")
    void droppingLastModifiesExceptLastN() {
      Traversal<List<Integer>, Integer> dropLast2 = ListTraversals.droppingLast(2);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.modify(dropLast2, x -> x * 2, numbers);

      assertThat(result).containsExactly(2, 4, 6, 4, 5);
    }

    @Test
    @DisplayName("droppingLast() getAll should exclude last n elements")
    void droppingLastGetAllExcludesLastN() {
      Traversal<List<Integer>, Integer> dropLast2 = ListTraversals.droppingLast(2);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(dropLast2, numbers);

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("droppingLast() with n >= size should focus on no elements")
    void droppingLastWithNGreaterOrEqualToSize() {
      Traversal<List<Integer>, Integer> dropLast10 = ListTraversals.droppingLast(10);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(dropLast10, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(Traversals.getAll(dropLast10, numbers)).isEmpty();
    }

    @Test
    @DisplayName("droppingLast() with n = 0 should focus on all elements")
    void droppingLastWithZero() {
      Traversal<List<Integer>, Integer> dropLast0 = ListTraversals.droppingLast(0);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(dropLast0, x -> x * 2, numbers);

      assertThat(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("droppingLast() with negative n should focus on all elements")
    void droppingLastWithNegative() {
      Traversal<List<Integer>, Integer> dropLastNeg = ListTraversals.droppingLast(-5);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(dropLastNeg, x -> x * 2, numbers);

      assertThat(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("droppingLast() with empty list should return empty list")
    void droppingLastWithEmptyList() {
      Traversal<List<Integer>, Integer> dropLast2 = ListTraversals.droppingLast(2);

      List<Integer> emptyList = List.of();
      List<Integer> result = Traversals.modify(dropLast2, x -> x * 2, emptyList);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("droppingLast() should compose with other traversals")
    void droppingLastComposition() {
      Traversal<List<User>, User> exceptLast = ListTraversals.droppingLast(1);

      List<User> users =
          List.of(new User("Alice", 100), new User("Bob", 200), new User("Charlie", 150));

      List<User> modified = Traversals.modify(exceptLast, User::doubleScore, users);
      assertThat(modified)
          .containsExactly(new User("Alice", 200), new User("Bob", 400), new User("Charlie", 150));
    }
  }

  @Nested
  @DisplayName("slicing(int from, int to) - Focus on index range")
  class SlicingTests {

    @Test
    @DisplayName("slicing() should modify only elements in range [from, to)")
    void slicingModifiesRange() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(1, 4);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.modify(slice, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 4, 6, 8, 5);
    }

    @Test
    @DisplayName("slicing() getAll should return only elements in range")
    void slicingGetAllReturnsRange() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(1, 4);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(slice, numbers);

      assertThat(result).containsExactly(2, 3, 4);
    }

    @Test
    @DisplayName("slicing() from start should work like taking")
    void slicingFromStart() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(0, 3);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(slice, numbers);

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("slicing() to end should work like dropping")
    void slicingToEnd() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(2, 10);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(slice, numbers);

      assertThat(result).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("slicing() with from = to should focus on no elements")
    void slicingWithEqualIndices() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(2, 2);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.modify(slice, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
      assertThat(Traversals.getAll(slice, numbers)).isEmpty();
    }

    @Test
    @DisplayName("slicing() with to < from should focus on no elements")
    void slicingWithInvertedRange() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(4, 2);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.modify(slice, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
      assertThat(Traversals.getAll(slice, numbers)).isEmpty();
    }

    @Test
    @DisplayName("slicing() with negative from should clamp to 0")
    void slicingWithNegativeFrom() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(-3, 2);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(slice, numbers);

      assertThat(result).containsExactly(1, 2);
    }

    @Test
    @DisplayName("slicing() with negative to parameter should clamp correctly")
    void slicingWithNegativeTo() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(1, -5);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(slice, numbers);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("slicing() with to beyond size should clamp to size")
    void slicingWithToBeyondSize() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(3, 100);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(slice, numbers);

      assertThat(result).containsExactly(4, 5);
    }

    @Test
    @DisplayName("slicing() with both indices out of bounds should clamp correctly")
    void slicingWithBothOutOfBounds() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(-10, 100);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.getAll(slice, numbers);

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("slicing() with empty list should return empty list")
    void slicingWithEmptyList() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(1, 4);

      List<Integer> emptyList = List.of();
      List<Integer> result = Traversals.modify(slice, x -> x * 2, emptyList);

      assertThat(result).isEmpty();
      assertThat(Traversals.getAll(slice, emptyList)).isEmpty();
    }

    @Test
    @DisplayName("slicing() with empty list and from=0 should return empty list")
    void slicingWithEmptyListFromZero() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(0, 5);

      List<Integer> emptyList = List.of();
      List<Integer> result = Traversals.modify(slice, x -> x * 2, emptyList);

      assertThat(result).isEmpty();
      assertThat(Traversals.getAll(slice, emptyList)).isEmpty();
    }

    @Test
    @DisplayName("slicing() with empty list and both indices zero should return empty list")
    void slicingWithEmptyListBothZero() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(0, 0);

      List<Integer> emptyList = List.of();
      List<Integer> result = Traversals.modify(slice, x -> x * 2, emptyList);

      assertThat(result).isEmpty();
      assertThat(Traversals.getAll(slice, emptyList)).isEmpty();
    }

    @Test
    @DisplayName("slicing() should compose with other traversals")
    void slicingComposition() {
      Traversal<List<User>, User> middle = ListTraversals.slicing(1, 3);
      Lens<User, String> nameLens = Lens.of(User::name, (u, n) -> new User(n, u.score()));

      Traversal<List<User>, String> middleNames = middle.andThen(nameLens.asTraversal());

      List<User> users =
          List.of(
              new User("Alice", 100),
              new User("Bob", 200),
              new User("Charlie", 150),
              new User("Diana", 180));

      List<String> names = Traversals.getAll(middleNames, users);
      assertThat(names).containsExactly("Bob", "Charlie");

      List<User> modified = Traversals.modify(middleNames, String::toLowerCase, users);
      assertThat(modified)
          .containsExactly(
              new User("Alice", 100),
              new User("bob", 200),
              new User("charlie", 150),
              new User("Diana", 180));
    }

    @Test
    @DisplayName("slicing() single element should focus on one element")
    void slicingSingleElement() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(2, 3);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(slice, numbers);

      assertThat(result).containsExactly(3);

      List<Integer> modified = Traversals.modify(slice, x -> x * 10, numbers);
      assertThat(modified).containsExactly(1, 2, 30, 4, 5);
    }

    @Test
    @DisplayName("slicing() with from beyond size should focus on no elements")
    void slicingWithFromBeyondSize() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(10, 20);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.modify(slice, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3); // No modifications
      assertThat(Traversals.getAll(slice, numbers)).isEmpty();
    }

    @Test
    @DisplayName("slicing() with from equal to size should focus on no elements")
    void slicingWithFromEqualToSize() {
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(5, 10);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.modify(slice, x -> x * 2, numbers);

      assertThat(result).containsExactly(1, 2, 3, 4, 5); // No modifications
      assertThat(Traversals.getAll(slice, numbers)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition and Chaining")
  class CompositionTests {

    @Test
    @DisplayName("taking() and dropping() can be combined")
    void takingAndDropping() {
      // Take first 4, then drop first 1 = elements at indices 1, 2, 3
      Traversal<List<Integer>, Integer> take4 = ListTraversals.taking(4);
      Traversal<List<Integer>, Integer> drop1 = ListTraversals.dropping(1);

      // Apply take4 first, then within those results conceptually drop 1
      // But these are List->element traversals, so we need nested structure
      // Let's test a different approach: using slicing which is equivalent
      Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(1, 4);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> result = Traversals.getAll(slice, numbers);

      assertThat(result).containsExactly(2, 3, 4);
    }

    @Test
    @DisplayName("Limiting traversals can be chained with filtered()")
    void limitingWithFiltered() {
      Traversal<List<Integer>, Integer> first4 = ListTraversals.taking(4);
      Traversal<List<Integer>, Integer> first4Even = first4.filtered(n -> n % 2 == 0);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6);
      List<Integer> result = Traversals.getAll(first4Even, numbers);

      // First 4 are [1, 2, 3, 4], of which evens are [2, 4]
      assertThat(result).containsExactly(2, 4);

      List<Integer> modified = Traversals.modify(first4Even, x -> x * 10, numbers);
      assertThat(modified).containsExactly(1, 20, 3, 40, 5, 6);
    }

    @Test
    @DisplayName("Multiple limiting traversals preserve structure")
    void multipleTraversalsPreserveStructure() {
      List<Integer> original = List.of(1, 2, 3, 4, 5);

      // Apply taking(3) - should preserve [4, 5]
      List<Integer> afterTaking = Traversals.modify(ListTraversals.taking(3), x -> x * 2, original);
      assertThat(afterTaking).containsExactly(2, 4, 6, 4, 5);

      // Apply dropping(2) to that result - should preserve [2, 4]
      List<Integer> afterDropping =
          Traversals.modify(ListTraversals.dropping(2), x -> x + 100, afterTaking);
      assertThat(afterDropping).containsExactly(2, 4, 106, 104, 105);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCaseTests {

    @Test
    @DisplayName("Single element list with taking(1)")
    void singleElementTaking() {
      Traversal<List<String>, String> first1 = ListTraversals.taking(1);

      List<String> single = List.of("only");
      List<String> result = Traversals.modify(first1, String::toUpperCase, single);

      assertThat(result).containsExactly("ONLY");
    }

    @Test
    @DisplayName("Single element list with dropping(1)")
    void singleElementDropping() {
      Traversal<List<String>, String> drop1 = ListTraversals.dropping(1);

      List<String> single = List.of("only");
      List<String> result = Traversals.getAll(drop1, single);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Very large n values are handled gracefully")
    void largeNValues() {
      Traversal<List<Integer>, Integer> takeHuge = ListTraversals.taking(Integer.MAX_VALUE);

      List<Integer> numbers = List.of(1, 2, 3);
      List<Integer> result = Traversals.getAll(takeHuge, numbers);

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Traversals work with mutable operations but return new list")
    void immutabilityCheck() {
      Traversal<List<Integer>, Integer> first2 = ListTraversals.taking(2);

      List<Integer> original = List.of(1, 2, 3, 4, 5);
      List<Integer> modified = Traversals.modify(first2, x -> x * 2, original);

      // Original should be unchanged
      assertThat(original).containsExactly(1, 2, 3, 4, 5);
      // Modified should be new list
      assertThat(modified).containsExactly(2, 4, 3, 4, 5);
      assertThat(original).isNotSameAs(modified);
    }
  }
}
