// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.optics.extensions.FoldExtensions.*;

import java.util.List;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FoldExtensions Tests")
class FoldExtensionsTest {

  // Test Data Structures
  record Item(String name, int price) {}

  record Order(List<Item> items) {}

  record Team(List<User> members) {}

  record User(String name, int age) {}

  @Nested
  @DisplayName("Constructor")
  class ConstructorTests {

    @Test
    @DisplayName("should throw UnsupportedOperationException when attempting to instantiate")
    void cannotInstantiate() {
      assertThatThrownBy(
              () -> {
                var constructor = FoldExtensions.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                constructor.newInstance();
              })
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .getCause()
          .hasMessage("Utility class - do not instantiate");
    }
  }

  @Nested
  @DisplayName("previewMaybe()")
  class PreviewMaybeTests {

    @Test
    @DisplayName("should return Just when fold has a first element")
    void previewMaybeWithElement() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      Maybe<Item> firstItem = previewMaybe(itemsFold, order);

      assertThat(firstItem.isJust()).isTrue();
      assertThat(firstItem.get()).extracting(Item::name).isEqualTo("Apple");
    }

    @Test
    @DisplayName("should return Nothing when fold has no elements")
    void previewMaybeEmpty() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order emptyOrder = new Order(List.of());

      Maybe<Item> firstItem = previewMaybe(itemsFold, emptyOrder);

      assertThat(firstItem.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should allow chaining with map")
    void previewMaybeWithMap() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order = new Order(List.of(new Item("Apple", 100)));

      Maybe<String> itemName = previewMaybe(itemsFold, order).map(Item::name);

      assertThat(itemName.isJust()).isTrue();
      assertThat(itemName.get()).isEqualTo("Apple");
    }

    @Test
    @DisplayName("should allow chaining with flatMap")
    void previewMaybeWithFlatMap() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order = new Order(List.of(new Item("Apple", 100)));

      Maybe<Integer> doublePrice =
          previewMaybe(itemsFold, order).map(Item::price).flatMap(price -> Maybe.just(price * 2));

      assertThat(doublePrice.isJust()).isTrue();
      assertThat(doublePrice.get()).isEqualTo(200);
    }

    @Test
    @DisplayName("should provide default value with orElse")
    void previewMaybeWithOrElse() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order emptyOrder = new Order(List.of());

      Item defaultItem = new Item("Default", 0);
      Item result = previewMaybe(itemsFold, emptyOrder).orElse(defaultItem);

      assertThat(result).isEqualTo(defaultItem);
    }
  }

  @Nested
  @DisplayName("findMaybe()")
  class FindMaybeTests {

    @Test
    @DisplayName("should return Just when an element matches the predicate")
    void findMaybeWithMatch() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      Maybe<Item> expensiveItem = findMaybe(itemsFold, item -> item.price() > 100, order);

      assertThat(expensiveItem.isJust()).isTrue();
      assertThat(expensiveItem.get()).extracting(Item::name).isEqualTo("Cherry");
    }

    @Test
    @DisplayName("should return Nothing when no element matches the predicate")
    void findMaybeNoMatch() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      Maybe<Item> veryExpensiveItem = findMaybe(itemsFold, item -> item.price() > 200, order);

      assertThat(veryExpensiveItem.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should return Nothing when fold is empty")
    void findMaybeOnEmpty() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order emptyOrder = new Order(List.of());

      Maybe<Item> result = findMaybe(itemsFold, item -> item.price() > 0, emptyOrder);

      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should return first match when multiple elements match")
    void findMaybeFirstMatch() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 120), new Item("Cherry", 150)));

      Maybe<Item> over100 = findMaybe(itemsFold, item -> item.price() >= 100, order);

      assertThat(over100.isJust()).isTrue();
      assertThat(over100.get())
          .extracting(Item::name)
          .isEqualTo("Apple"); // First match, not Cherry
    }

    @Test
    @DisplayName("should allow chaining with map")
    void findMaybeWithMap() {
      Fold<Team, User> membersFold = Fold.of(Team::members);

      Team team =
          new Team(List.of(new User("Alice", 25), new User("Bob", 30), new User("Charlie", 35)));

      Maybe<String> adultName =
          findMaybe(membersFold, user -> user.age() >= 30, team).map(User::name);

      assertThat(adultName.isJust()).isTrue();
      assertThat(adultName.get()).isEqualTo("Bob");
    }
  }

  @Nested
  @DisplayName("getAllMaybe()")
  class GetAllMaybeTests {

    @Test
    @DisplayName("should return Just when list is non-empty")
    void getAllMaybeNonEmpty() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      Maybe<List<Item>> allItems = getAllMaybe(itemsFold, order);

      assertThat(allItems.isJust()).isTrue();
      assertThat(allItems.get())
          .hasSize(3)
          .extracting(Item::name)
          .containsExactly("Apple", "Banana", "Cherry");
    }

    @Test
    @DisplayName("should return Nothing when list is empty")
    void getAllMaybeEmpty() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order emptyOrder = new Order(List.of());

      Maybe<List<Item>> allItems = getAllMaybe(itemsFold, emptyOrder);

      assertThat(allItems.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should allow chaining with map to process the list")
    void getAllMaybeWithMap() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order = new Order(List.of(new Item("Apple", 100), new Item("Banana", 50)));

      Maybe<Integer> totalPrice =
          getAllMaybe(itemsFold, order).map(items -> items.stream().mapToInt(Item::price).sum());

      assertThat(totalPrice.isJust()).isTrue();
      assertThat(totalPrice.get()).isEqualTo(150);
    }

    @Test
    @DisplayName("should allow chaining with flatMap")
    void getAllMaybeWithFlatMap() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order = new Order(List.of(new Item("Apple", 100)));

      Maybe<Item> firstItem =
          getAllMaybe(itemsFold, order)
              .flatMap(items -> items.isEmpty() ? Maybe.nothing() : Maybe.just(items.getFirst()));

      assertThat(firstItem.isJust()).isTrue();
      assertThat(firstItem.get()).extracting(Item::name).isEqualTo("Apple");
    }

    @Test
    @DisplayName("should provide default value with orElse")
    void getAllMaybeWithOrElse() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order emptyOrder = new Order(List.of());

      List<Item> defaultList = List.of(new Item("Default", 0));
      List<Item> result = getAllMaybe(itemsFold, emptyOrder).orElse(defaultList);

      assertThat(result).isEqualTo(defaultList);
    }

    @Test
    @DisplayName("should distinguish empty list from nothing using conditional")
    void getAllMaybeDistinguishEmptyFromNothing() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order emptyOrder = new Order(List.of());
      Order orderWithItems = new Order(List.of(new Item("Apple", 100)));

      // Empty case
      Maybe<List<Item>> emptyResult = getAllMaybe(itemsFold, emptyOrder);
      String emptyMessage =
          emptyResult.isNothing()
              ? "No items found"
              : "Found " + emptyResult.get().size() + " items";
      assertThat(emptyMessage).isEqualTo("No items found");

      // Non-empty case
      Maybe<List<Item>> nonEmptyResult = getAllMaybe(itemsFold, orderWithItems);
      String nonEmptyMessage =
          nonEmptyResult.isNothing()
              ? "No items found"
              : "Found " + nonEmptyResult.get().size() + " items";
      assertThat(nonEmptyMessage).isEqualTo("Found 1 items");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("should work with complex chaining across all methods")
    void complexChaining() {
      Fold<Team, User> membersFold = Fold.of(Team::members);

      Team team =
          new Team(
              List.of(
                  new User("Alice", 25),
                  new User("Bob", 30),
                  new User("Charlie", 35),
                  new User("Diana", 28)));

      // Find first adult, get their name, convert to uppercase
      Maybe<String> adultNameUpper =
          findMaybe(membersFold, user -> user.age() >= 30, team)
              .map(User::name)
              .map(String::toUpperCase);

      assertThat(adultNameUpper.get()).isEqualTo("BOB");

      // Get all members, count those over 30
      Maybe<Long> adultCount =
          getAllMaybe(membersFold, team)
              .map(members -> members.stream().filter(u -> u.age() >= 30).count());

      assertThat(adultCount.get()).isEqualTo(2L);
    }

    @Test
    @DisplayName("should compose with standard Fold operations")
    void compositionWithStandardFold() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order = new Order(List.of(new Item("Apple", 100), new Item("Banana", 50)));

      // Use standard fold operation
      boolean hasExpensiveItems = itemsFold.exists(item -> item.price() > 75, order);
      assertThat(hasExpensiveItems).isTrue();

      // Use extension method
      Maybe<Item> expensiveItem = findMaybe(itemsFold, item -> item.price() > 75, order);
      assertThat(expensiveItem.isJust()).isTrue();
      assertThat(expensiveItem.get()).extracting(Item::name).isEqualTo("Apple");
    }

    @Test
    @DisplayName("should handle nested structures correctly")
    void nestedStructures() {
      record Customer(String name, List<Order> orders) {}

      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);

      Customer customer =
          new Customer(
              "John", List.of(new Order(List.of(new Item("Apple", 100))), new Order(List.of())));

      Maybe<List<Order>> orders = getAllMaybe(ordersFold, customer);

      assertThat(orders.isJust()).isTrue();
      assertThat(orders.get()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("should handle single element list correctly")
    void singleElement() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order order = new Order(List.of(new Item("Apple", 100)));

      Maybe<Item> preview = previewMaybe(itemsFold, order);
      Maybe<Item> found = findMaybe(itemsFold, item -> true, order);
      Maybe<List<Item>> all = getAllMaybe(itemsFold, order);

      assertThat(preview.isJust()).isTrue();
      assertThat(found.isJust()).isTrue();
      assertThat(all.isJust()).isTrue();
      assertThat(all.get()).hasSize(1);
    }

    @Test
    @DisplayName("should handle predicates that match all elements")
    void predicateMatchesAll() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order order = new Order(List.of(new Item("Apple", 100), new Item("Banana", 50)));

      Maybe<Item> found = findMaybe(itemsFold, item -> item.price() > 0, order);

      assertThat(found.isJust()).isTrue();
      assertThat(found.get()).extracting(Item::name).isEqualTo("Apple"); // First element
    }

    @Test
    @DisplayName("should handle predicates that match no elements")
    void predicateMatchesNone() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order order = new Order(List.of(new Item("Apple", 100), new Item("Banana", 50)));

      Maybe<Item> found = findMaybe(itemsFold, item -> item.price() < 0, order);

      assertThat(found.isNothing()).isTrue();
    }
  }
}
