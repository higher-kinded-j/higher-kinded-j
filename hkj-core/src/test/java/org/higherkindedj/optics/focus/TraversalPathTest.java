// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TraversalPath<S, A> Tests")
class TraversalPathTest {

  // Test Data Structures
  record Team(String name, List<Player> players) {}

  record Player(String name, int score, List<String> achievements) {}

  sealed interface Shape permits Circle, Rectangle {}

  record Circle(double radius) implements Shape {}

  record Rectangle(double width, double height) implements Shape {}

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {

    @Test
    @DisplayName("of should create a TraversalPath from a Traversal")
    void ofShouldCreateTraversalPath() {
      Traversal<List<String>, String> traversal = Traversals.forList();
      TraversalPath<List<String>, String> path = TraversalPath.of(traversal);

      assertThat(path).isNotNull();
      assertThat(path.toTraversal()).isSameAs(traversal);
    }

    @Test
    @DisplayName("getAll should return all focused values")
    void getAllShouldReturnAllValues() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());

      assertThat(path.getAll(List.of("a", "b", "c"))).containsExactly("a", "b", "c");
      assertThat(path.getAll(List.of())).isEmpty();
    }

    @Test
    @DisplayName("preview should return first value if present")
    void previewShouldReturnFirstValue() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());

      assertThat(path.preview(List.of("a", "b", "c"))).contains("a");
      assertThat(path.preview(List.of())).isEmpty();
    }

    @Test
    @DisplayName("setAll should replace all values")
    void setAllShouldReplaceAllValues() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());

      List<String> result = path.setAll("X", List.of("a", "b", "c"));
      assertThat(result).containsExactly("X", "X", "X");
    }

    @Test
    @DisplayName("modifyAll should transform all values")
    void modifyAllShouldTransformAllValues() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());

      List<String> result = path.modifyAll(String::toUpperCase, List.of("a", "b", "c"));
      assertThat(result).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("count should return number of focused elements")
    void countShouldReturnNumberOfElements() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());

      assertThat(path.count(List.of("a", "b", "c"))).isEqualTo(3);
      assertThat(path.count(List.of())).isEqualTo(0);
    }

    @Test
    @DisplayName("isEmpty should check if no elements focused")
    void isEmptyShouldCheckIfNoElements() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());

      assertThat(path.isEmpty(List.of("a"))).isFalse();
      assertThat(path.isEmpty(List.of())).isTrue();
    }

    @Test
    @DisplayName("exists should check if any element matches predicate")
    void existsShouldCheckIfAnyMatches() {
      TraversalPath<List<Integer>, Integer> path = TraversalPath.of(Traversals.forList());

      assertThat(path.exists(n -> n > 5, List.of(1, 2, 10))).isTrue();
      assertThat(path.exists(n -> n > 5, List.of(1, 2, 3))).isFalse();
      assertThat(path.exists(n -> n > 5, List.of())).isFalse();
    }

    @Test
    @DisplayName("all should check if all elements match predicate")
    void allShouldCheckIfAllMatch() {
      TraversalPath<List<Integer>, Integer> path = TraversalPath.of(Traversals.forList());

      assertThat(path.all(n -> n > 0, List.of(1, 2, 3))).isTrue();
      assertThat(path.all(n -> n > 0, List.of(1, -1, 3))).isFalse();
      assertThat(path.all(n -> n > 0, List.of())).isTrue(); // Vacuously true
    }

    @Test
    @DisplayName("find should return first matching element")
    void findShouldReturnFirstMatching() {
      TraversalPath<List<Integer>, Integer> path = TraversalPath.of(Traversals.forList());

      assertThat(path.find(n -> n > 5, List.of(1, 7, 10))).contains(7);
      assertThat(path.find(n -> n > 100, List.of(1, 7, 10))).isEmpty();
    }
  }

  @Nested
  @DisplayName("Filtering")
  class Filtering {

    @Test
    @DisplayName("filter should only affect matching elements")
    void filterShouldOnlyAffectMatchingElements() {
      TraversalPath<List<Integer>, Integer> path = TraversalPath.of(Traversals.forList());
      TraversalPath<List<Integer>, Integer> evenPath = path.filter(n -> n % 2 == 0);

      List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6);

      assertThat(evenPath.getAll(numbers)).containsExactly(2, 4, 6);

      List<Integer> doubled = evenPath.modifyAll(n -> n * 2, numbers);
      assertThat(doubled).containsExactly(1, 4, 3, 8, 5, 12);
    }

    @Test
    @DisplayName("filter can be chained")
    void filterCanBeChained() {
      TraversalPath<List<Integer>, Integer> path = TraversalPath.of(Traversals.forList());
      TraversalPath<List<Integer>, Integer> filteredPath =
          path.filter(n -> n > 0).filter(n -> n < 10);

      assertThat(filteredPath.getAll(List.of(-5, 1, 5, 15, 8))).containsExactly(1, 5, 8);
    }
  }

  @Nested
  @DisplayName("Composition with Lens")
  class CompositionWithLens {

    @Test
    @DisplayName("via(Lens) should compose with all traversed elements")
    void viaLensShouldComposeWithAllElements() {
      Lens<Team, List<Player>> playersLens =
          Lens.of(Team::players, (t, p) -> new Team(t.name(), p));
      Traversal<List<Player>, Player> playerTraversal = Traversals.forList();
      Lens<Player, String> nameLens =
          Lens.of(Player::name, (p, n) -> new Player(n, p.score(), p.achievements()));

      TraversalPath<Team, Player> playersPath = FocusPath.of(playersLens).via(playerTraversal);
      TraversalPath<Team, String> namesPath = playersPath.via(nameLens);

      Team team =
          new Team(
              "Red",
              List.of(new Player("Alice", 100, List.of()), new Player("Bob", 85, List.of())));

      assertThat(namesPath.getAll(team)).containsExactly("Alice", "Bob");

      Team modified = namesPath.modifyAll(String::toUpperCase, team);
      assertThat(modified.players().get(0).name()).isEqualTo("ALICE");
      assertThat(modified.players().get(1).name()).isEqualTo("BOB");
    }
  }

  @Nested
  @DisplayName("Composition with Prism")
  class CompositionWithPrism {

    @Test
    @DisplayName("via(Prism) should filter to matching elements")
    void viaPrismShouldFilterToMatchingElements() {
      Prism<Shape, Circle> circlePrism =
          Prism.of(s -> s instanceof Circle c ? Optional.of(c) : Optional.empty(), c -> c);

      TraversalPath<List<Shape>, Shape> shapesPath = TraversalPath.of(Traversals.forList());
      TraversalPath<List<Shape>, Circle> circlesPath = shapesPath.via(circlePrism);

      List<Shape> shapes = List.of(new Circle(5), new Rectangle(3, 4), new Circle(10));

      assertThat(circlesPath.getAll(shapes)).hasSize(2);
      assertThat(circlesPath.getAll(shapes).get(0).radius()).isEqualTo(5);
      assertThat(circlesPath.getAll(shapes).get(1).radius()).isEqualTo(10);

      // Modify only circles
      List<Shape> modified = circlesPath.modifyAll(c -> new Circle(c.radius() * 2), shapes);
      assertThat(((Circle) modified.get(0)).radius()).isEqualTo(10);
      assertThat(((Rectangle) modified.get(1)).width()).isEqualTo(3); // Unchanged
      assertThat(((Circle) modified.get(2)).radius()).isEqualTo(20);
    }
  }

  @Nested
  @DisplayName("Composition with Affine")
  class CompositionWithAffine {

    @Test
    @DisplayName("via(Affine) should compose and filter to present elements")
    void viaAffineShouldComposeAndFilterToPresent() {
      record Item(String name, Optional<String> description) {}

      Lens<Item, Optional<String>> descLens =
          Lens.of(Item::description, (item, desc) -> new Item(item.name(), desc));
      Affine<Optional<String>, String> someAffine = FocusPaths.optionalSome();

      TraversalPath<List<Item>, Item> itemsPath = TraversalPath.of(Traversals.forList());
      TraversalPath<List<Item>, Optional<String>> descPath = itemsPath.via(descLens);
      TraversalPath<List<Item>, String> presentDescPath = descPath.via(someAffine);

      List<Item> items =
          List.of(
              new Item("Widget", Optional.of("A useful widget")),
              new Item("Gadget", Optional.empty()),
              new Item("Gizmo", Optional.of("A cool gizmo")));

      // Only items with descriptions are focused
      assertThat(presentDescPath.getAll(items)).containsExactly("A useful widget", "A cool gizmo");

      // Modify only present descriptions
      List<Item> modified = presentDescPath.modifyAll(String::toUpperCase, items);
      assertThat(modified.get(0).description()).contains("A USEFUL WIDGET");
      assertThat(modified.get(1).description()).isEmpty(); // Unchanged
      assertThat(modified.get(2).description()).contains("A COOL GIZMO");
    }
  }

  @Nested
  @DisplayName("Composition with Traversal")
  class CompositionWithTraversal {

    @Test
    @DisplayName("via(Traversal) should compose for nested collections")
    void viaTraversalShouldComposeForNestedCollections() {
      Lens<Team, List<Player>> playersLens =
          Lens.of(Team::players, (t, p) -> new Team(t.name(), p));
      Traversal<List<Player>, Player> playerTraversal = Traversals.forList();
      Lens<Player, List<String>> achievementsLens =
          Lens.of(Player::achievements, (p, a) -> new Player(p.name(), p.score(), a));
      Traversal<List<String>, String> achievementTraversal = Traversals.forList();

      TraversalPath<Team, String> allAchievementsPath =
          FocusPath.of(playersLens)
              .via(playerTraversal)
              .via(achievementsLens)
              .via(achievementTraversal);

      Team team =
          new Team(
              "Champions",
              List.of(
                  new Player("Alice", 100, List.of("MVP", "First Blood")),
                  new Player("Bob", 85, List.of("Ace"))));

      assertThat(allAchievementsPath.getAll(team)).containsExactly("MVP", "First Blood", "Ace");

      Team modified = allAchievementsPath.modifyAll(String::toUpperCase, team);
      assertThat(modified.players().get(0).achievements()).containsExactly("MVP", "FIRST BLOOD");
      assertThat(modified.players().get(1).achievements()).containsExactly("ACE");
    }
  }

  @Nested
  @DisplayName("Composition with Iso")
  class CompositionWithIso {

    @Test
    @DisplayName("via(Iso) should transform all elements")
    void viaIsoShouldTransformAllElements() {
      record Wrapper(String value) {}

      Iso<String, Wrapper> wrapperIso = Iso.of(Wrapper::new, Wrapper::value);

      TraversalPath<List<String>, String> stringsPath = TraversalPath.of(Traversals.forList());
      TraversalPath<List<String>, Wrapper> wrappersPath = stringsPath.via(wrapperIso);

      List<String> strings = List.of("a", "b");

      assertThat(wrappersPath.getAll(strings)).extracting(Wrapper::value).containsExactly("a", "b");
    }
  }

  @Nested
  @DisplayName("Collection Navigation")
  class CollectionNavigation {

    @Test
    @DisplayName("each() should flatten nested lists")
    void eachShouldFlattenNestedLists() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, String> innerPath = outerPath.each();

      List<List<String>> nested = List.of(List.of("a", "b"), List.of("c"), List.of("d", "e"));

      assertThat(innerPath.getAll(nested)).containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    @DisplayName("each(Each) should traverse using provided Each instance")
    void eachWithInstanceShouldFlattenNestedMaps() {
      TraversalPath<List<Map<String, Integer>>, Map<String, Integer>> outerPath =
          TraversalPath.of(Traversals.forList());

      Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
      TraversalPath<List<Map<String, Integer>>, Integer> valuesPath = outerPath.each(mapEach);

      List<Map<String, Integer>> maps = List.of(Map.of("a", 1, "b", 2), Map.of("c", 3));

      assertThat(valuesPath.getAll(maps)).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("at(index) should access element at index in all sublists")
    void atShouldAccessElementInAllSublists() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, String> firstPath = outerPath.at(0);

      List<List<String>> nested = List.of(List.of("a", "b"), List.of("c"), List.of("d", "e"));

      // Gets first element from each sublist that has one
      assertThat(firstPath.getAll(nested)).containsExactly("a", "c", "d");
    }

    @Test
    @DisplayName("some() should unwrap Optional values in list")
    void someShouldUnwrapOptionalValuesInList() {
      TraversalPath<List<Optional<String>>, Optional<String>> optPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<Optional<String>>, String> valuesPath = optPath.some();

      List<Optional<String>> opts = List.of(Optional.of("a"), Optional.empty(), Optional.of("c"));

      assertThat(valuesPath.getAll(opts)).containsExactly("a", "c");
    }
  }

  @Nested
  @DisplayName("List Decomposition Navigation")
  class ListDecompositionNavigation {

    @Test
    @DisplayName("cons() should decompose lists into head and tail pairs")
    void consShouldDecomposeListsIntoHeadTailPairs() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, Pair<String, List<String>>> consPath = outerPath.cons();

      List<List<String>> nested = List.of(List.of("a", "b", "c"), List.of("x", "y"), List.of());

      List<Pair<String, List<String>>> results = consPath.getAll(nested);

      // Empty list is skipped
      assertThat(results).hasSize(2);
      assertThat(results.get(0).first()).isEqualTo("a");
      assertThat(results.get(0).second()).containsExactly("b", "c");
      assertThat(results.get(1).first()).isEqualTo("x");
      assertThat(results.get(1).second()).containsExactly("y");
    }

    @Test
    @DisplayName("headTail() should be alias for cons()")
    void headTailShouldBeAliasForCons() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, Pair<String, List<String>>> headTailPath =
          outerPath.headTail();

      List<List<String>> nested = List.of(List.of("a", "b", "c"));

      List<Pair<String, List<String>>> results = headTailPath.getAll(nested);
      assertThat(results).hasSize(1);
      assertThat(results.get(0).first()).isEqualTo("a");
    }

    @Test
    @DisplayName("snoc() should decompose lists into init and last pairs")
    void snocShouldDecomposeListsIntoInitLastPairs() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, Pair<List<String>, String>> snocPath = outerPath.snoc();

      List<List<String>> nested = List.of(List.of("a", "b", "c"), List.of("x", "y"), List.of());

      List<Pair<List<String>, String>> results = snocPath.getAll(nested);

      // Empty list is skipped
      assertThat(results).hasSize(2);
      assertThat(results.get(0).first()).containsExactly("a", "b");
      assertThat(results.get(0).second()).isEqualTo("c");
      assertThat(results.get(1).first()).containsExactly("x");
      assertThat(results.get(1).second()).isEqualTo("y");
    }

    @Test
    @DisplayName("initLast() should be alias for snoc()")
    void initLastShouldBeAliasForSnoc() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, Pair<List<String>, String>> initLastPath =
          outerPath.initLast();

      List<List<String>> nested = List.of(List.of("a", "b", "c"));

      List<Pair<List<String>, String>> results = initLastPath.getAll(nested);
      assertThat(results).hasSize(1);
      assertThat(results.get(0).second()).isEqualTo("c");
    }

    @Test
    @DisplayName("head() should focus on head elements of all sublists")
    void headShouldFocusOnHeadElements() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, String> headPath = outerPath.head();

      List<List<String>> nested = List.of(List.of("a", "b"), List.of("x", "y", "z"), List.of());

      // Empty list is skipped
      assertThat(headPath.getAll(nested)).containsExactly("a", "x");
    }

    @Test
    @DisplayName("last() should focus on last elements of all sublists")
    void lastShouldFocusOnLastElements() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, String> lastPath = outerPath.last();

      List<List<String>> nested = List.of(List.of("a", "b"), List.of("x", "y", "z"), List.of());

      // Empty list is skipped
      assertThat(lastPath.getAll(nested)).containsExactly("b", "z");
    }

    @Test
    @DisplayName("tail() should focus on tails of all sublists")
    void tailShouldFocusOnTails() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, List<String>> tailPath = outerPath.tail();

      List<List<String>> nested = List.of(List.of("a", "b", "c"), List.of("x"), List.of());

      // Empty list is skipped, single element list has empty tail
      List<List<String>> tails = tailPath.getAll(nested);
      assertThat(tails).hasSize(2);
      assertThat(tails.get(0)).containsExactly("b", "c");
      assertThat(tails.get(1)).isEmpty();
    }

    @Test
    @DisplayName("init() should focus on inits of all sublists")
    void initShouldFocusOnInits() {
      TraversalPath<List<List<String>>, List<String>> outerPath =
          TraversalPath.of(Traversals.forList());
      TraversalPath<List<List<String>>, List<String>> initPath = outerPath.init();

      List<List<String>> nested = List.of(List.of("a", "b", "c"), List.of("x"), List.of());

      // Empty list is skipped, single element list has empty init
      List<List<String>> inits = initPath.getAll(nested);
      assertThat(inits).hasSize(2);
      assertThat(inits.get(0)).containsExactly("a", "b");
      assertThat(inits.get(1)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethods {

    @Test
    @DisplayName("toTraversal should return the underlying Traversal")
    void toTraversalShouldReturnUnderlyingTraversal() {
      Traversal<List<String>, String> traversal = Traversals.forList();
      TraversalPath<List<String>, String> path = TraversalPath.of(traversal);

      assertThat(path.toTraversal()).isSameAs(traversal);
    }

    @Test
    @DisplayName("asFold should return a Fold view")
    void asFoldShouldReturnFoldView() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());
      var fold = path.asFold();

      List<String> list = List.of("hello", "world");

      // Fold can query but not modify
      assertThat(fold.exists(s -> s.length() > 4, list)).isTrue();
      assertThat(fold.all(s -> s.length() > 3, list)).isTrue();
    }
  }

  @Nested
  @DisplayName("Narrowing Methods")
  class NarrowingMethods {

    @Test
    @DisplayName("headOption should return AffinePath focusing on first element")
    void headOptionShouldReturnAffinePathFocusingOnFirst() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());
      AffinePath<List<String>, String> affinePath = path.headOption();

      assertThat(affinePath).isNotNull();
      assertThat(affinePath.getOptional(List.of("a", "b", "c"))).contains("a");
    }

    @Test
    @DisplayName("headOption should return empty Optional when no elements")
    void headOptionShouldReturnEmptyWhenNoElements() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());
      AffinePath<List<String>, String> affinePath = path.headOption();

      assertThat(affinePath.getOptional(List.of())).isEmpty();
    }

    @Test
    @DisplayName("headOption AffinePath set should update all elements")
    void headOptionSetShouldUpdateAllElements() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());
      AffinePath<List<String>, String> affinePath = path.headOption();

      List<String> result = affinePath.set("X", List.of("a", "b", "c"));

      // headOption's set uses setAll, updating all focused elements
      assertThat(result).containsExactly("X", "X", "X");
    }

    @Test
    @DisplayName("headOption AffinePath set on empty list should return empty list")
    void headOptionSetOnEmptyShouldReturnEmpty() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());
      AffinePath<List<String>, String> affinePath = path.headOption();

      List<String> result = affinePath.set("X", List.of());

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("headOption AffinePath modify should transform all elements")
    void headOptionModifyShouldTransformAllElements() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());
      AffinePath<List<String>, String> affinePath = path.headOption();

      List<String> result = affinePath.modify(String::toUpperCase, List.of("a", "b", "c"));

      // Modify only modifies if getOptional returns a value
      // Since we have elements, all get updated via setAll
      assertThat(result).containsExactly("A", "A", "A");
    }

    @Test
    @DisplayName("headOption AffinePath modify on empty should return unchanged")
    void headOptionModifyOnEmptyShouldReturnUnchanged() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());
      AffinePath<List<String>, String> affinePath = path.headOption();

      List<String> empty = List.of();
      List<String> result = affinePath.modify(String::toUpperCase, empty);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("headOption matches should return true when elements exist")
    void headOptionMatchesShouldReturnTrueWhenElementsExist() {
      TraversalPath<List<String>, String> path = TraversalPath.of(Traversals.forList());
      AffinePath<List<String>, String> affinePath = path.headOption();

      assertThat(affinePath.matches(List.of("a", "b"))).isTrue();
      assertThat(affinePath.matches(List.of())).isFalse();
    }
  }
}
