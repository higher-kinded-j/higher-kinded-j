// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdSelective;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Traversal<S, A> Tests")
class TraversalTest {

  // Test Data Structures from LensTest
  record Street(String name) {}

  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

  // Additional test data structures for filtering tests
  record User(String name, boolean active, int score) {
    User grantBonus() {
      return new User(name, active, score + 100);
    }
  }

  record Order(List<Item> items) {}

  record Item(String name, int price) {}

  record Customer(String name, List<Order> orders) {}

  // Helper to create a Traversal for any List.
  private <T> Traversal<List<T>, T> listElements() {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, List<T>> modifyF(
          Function<T, Kind<F, T>> f, List<T> source, Applicative<F> applicative) {
        Kind<F, Kind<ListKind.Witness, T>> traversed =
            ListTraverse.INSTANCE.traverse(applicative, f, ListKindHelper.LIST.widen(source));
        return applicative.map(ListKindHelper.LIST::narrow, traversed);
      }
    };
  }

  @Test
  @DisplayName("andThen(Lens) should compose Traversal and Lens")
  void andThen_withLens() {
    Traversal<List<Street>, Street> listStreetTraversal = listElements();
    Lens<Street, String> streetNameLens = Lens.of(Street::name, (s, n) -> new Street(n));

    Traversal<List<Street>, String> composed =
        listStreetTraversal.andThen(streetNameLens.asTraversal());

    List<Street> source = List.of(new Street("Elm"), new Street("Oak"));
    List<String> names = Traversals.getAll(composed, source);
    assertThat(names).containsExactly("Elm", "Oak");

    List<Street> modified = Traversals.modify(composed, String::toUpperCase, source);
    assertThat(modified).extracting(Street::name).containsExactly("ELM", "OAK");
  }

  @Test
  @DisplayName("andThen(Prism) should compose Traversal and Prism")
  void andThen_withPrism() {
    Traversal<List<Json>, Json> listJsonTraversal = listElements();
    Prism<Json, String> jsonStringPrism =
        Prism.of(
            json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
            JsonString::new);

    // FIX: Convert the Prism to a Traversal before composition
    Traversal<List<Json>, String> composed =
        listJsonTraversal.andThen(jsonStringPrism.asTraversal());

    List<Json> source =
        List.of(new JsonString("hello"), new JsonNumber(1), new JsonString("world"));
    List<String> strings = Traversals.getAll(composed, source);
    assertThat(strings).containsExactly("hello", "world");

    List<Json> modified = Traversals.modify(composed, String::toUpperCase, source);
    assertThat(modified)
        .containsExactly(new JsonString("HELLO"), new JsonNumber(1), new JsonString("WORLD"));
  }

  @Nested
  @DisplayName("filtered() - Predicate-based filtering")
  class FilteredTests {

    @Test
    @DisplayName("filtered() should only modify elements matching predicate")
    void filteredModifiesOnlyMatching() {
      Traversal<List<User>, User> allUsers = listElements();
      Traversal<List<User>, User> activeUsers = allUsers.filtered(User::active);

      List<User> users =
          List.of(
              new User("Alice", true, 100),
              new User("Bob", false, 200),
              new User("Charlie", true, 150));

      List<User> result = Traversals.modify(activeUsers, User::grantBonus, users);

      // Active users get bonus, inactive user preserved unchanged
      assertThat(result)
          .containsExactly(
              new User("Alice", true, 200), // bonus applied
              new User("Bob", false, 200), // unchanged
              new User("Charlie", true, 250) // bonus applied
              );
    }

    @Test
    @DisplayName("filtered() getAll should only return matching elements")
    void filteredGetAllReturnsOnlyMatching() {
      Traversal<List<User>, User> allUsers = listElements();
      Traversal<List<User>, User> activeUsers = allUsers.filtered(User::active);

      List<User> users =
          List.of(
              new User("Alice", true, 100),
              new User("Bob", false, 200),
              new User("Charlie", true, 150));

      List<User> actives = Traversals.getAll(activeUsers, users);

      assertThat(actives).hasSize(2).extracting(User::name).containsExactly("Alice", "Charlie");
    }

    @Test
    @DisplayName("filtered() should work with empty list")
    void filteredWithEmptyList() {
      Traversal<List<User>, User> allUsers = listElements();
      Traversal<List<User>, User> activeUsers = allUsers.filtered(User::active);

      List<User> emptyList = List.of();
      List<User> result = Traversals.modify(activeUsers, User::grantBonus, emptyList);
      assertThat(result).isEmpty();

      List<User> gotten = Traversals.getAll(activeUsers, emptyList);
      assertThat(gotten).isEmpty();
    }

    @Test
    @DisplayName("filtered() should handle when no elements match")
    void filteredNoMatches() {
      Traversal<List<User>, User> allUsers = listElements();
      Traversal<List<User>, User> activeUsers = allUsers.filtered(User::active);

      List<User> users = List.of(new User("Alice", false, 100), new User("Bob", false, 200));

      List<User> result = Traversals.modify(activeUsers, User::grantBonus, users);
      assertThat(result).containsExactly(users.toArray(new User[0]));

      List<User> gotten = Traversals.getAll(activeUsers, users);
      assertThat(gotten).isEmpty();
    }

    @Test
    @DisplayName("filtered() should handle when all elements match")
    void filteredAllMatch() {
      Traversal<List<User>, User> allUsers = listElements();
      Traversal<List<User>, User> activeUsers = allUsers.filtered(User::active);

      List<User> users =
          List.of(
              new User("Alice", true, 100),
              new User("Bob", true, 200),
              new User("Charlie", true, 150));

      List<User> result = Traversals.modify(activeUsers, User::grantBonus, users);
      assertThat(result).extracting(User::score).containsExactly(200, 300, 250);

      List<User> gotten = Traversals.getAll(activeUsers, users);
      assertThat(gotten).hasSize(3);
    }

    @Test
    @DisplayName("filtered() should compose with other traversals")
    void filteredComposition() {
      Traversal<List<User>, User> allUsers = listElements();
      Lens<User, String> nameLens =
          Lens.of(User::name, (u, n) -> new User(n, u.active(), u.score()));

      // Compose: list -> filtered users -> name
      Traversal<List<User>, String> activeUserNames =
          allUsers.filtered(User::active).andThen(nameLens.asTraversal());

      List<User> users =
          List.of(
              new User("alice", true, 100),
              new User("bob", false, 200),
              new User("charlie", true, 150));

      // Get only active user names
      List<String> names = Traversals.getAll(activeUserNames, users);
      assertThat(names).containsExactly("alice", "charlie");

      // Modify only active user names
      List<User> result = Traversals.modify(activeUserNames, String::toUpperCase, users);
      assertThat(result)
          .containsExactly(
              new User("ALICE", true, 100),
              new User("bob", false, 200), // unchanged
              new User("CHARLIE", true, 150));
    }

    @Test
    @DisplayName("filtered() can be chained multiple times")
    void filteredChaining() {
      Traversal<List<User>, User> allUsers = listElements();

      // Chain two filters: active AND high score
      Traversal<List<User>, User> activeHighScorers =
          allUsers.filtered(User::active).filtered(u -> u.score() > 120);

      List<User> users =
          List.of(
              new User("Alice", true, 100), // active but low score
              new User("Bob", false, 200), // high score but inactive
              new User("Charlie", true, 150), // active and high score
              new User("Diana", true, 180) // active and high score
              );

      List<User> result = Traversals.getAll(activeHighScorers, users);
      assertThat(result).hasSize(2).extracting(User::name).containsExactly("Charlie", "Diana");
    }
  }

  @Nested
  @DisplayName("filterBy() - Query-based filtering")
  class FilterByTests {

    @Test
    @DisplayName("filterBy() should filter based on nested fold query")
    void filterByNestedQuery() {
      Traversal<List<Order>, Order> allOrders = listElements();
      Fold<Order, Item> orderItems = Fold.of(Order::items);

      // Filter orders that have any expensive item (price > 100)
      Traversal<List<Order>, Order> ordersWithExpensiveItems =
          allOrders.filterBy(orderItems, item -> item.price() > 100);

      List<Order> orders =
          List.of(
              new Order(List.of(new Item("Apple", 50), new Item("Banana", 30))), // no expensive
              new Order(List.of(new Item("Laptop", 1000), new Item("Mouse", 25))), // has expensive
              new Order(List.of(new Item("Book", 20))), // no expensive
              new Order(List.of(new Item("Phone", 800), new Item("Case", 150))) // has expensive
              );

      List<Order> result = Traversals.getAll(ordersWithExpensiveItems, orders);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).items()).extracting(Item::name).contains("Laptop");
      assertThat(result.get(1).items()).extracting(Item::name).contains("Phone");
    }

    @Test
    @DisplayName("filterBy() should modify only matching elements based on query")
    void filterByModifiesCorrectly() {
      Traversal<List<Customer>, Customer> allCustomers = listElements();
      Fold<Customer, Order> customerOrders = Fold.of(Customer::orders);
      Fold<Order, Item> orderItems = Fold.of(Order::items);
      Fold<Customer, Item> customerItems = customerOrders.andThen(orderItems);

      // Filter customers who have any item over $500
      Traversal<List<Customer>, Customer> highValueCustomers =
          allCustomers.filterBy(customerItems, item -> item.price() > 500);

      List<Customer> customers =
          List.of(
              new Customer(
                  "Alice", List.of(new Order(List.of(new Item("Book", 20), new Item("Pen", 5))))),
              new Customer("Bob", List.of(new Order(List.of(new Item("Laptop", 1000))))),
              new Customer("Charlie", List.of(new Order(List.of(new Item("Coffee", 5))))));

      Lens<Customer, String> nameLens =
          Lens.of(Customer::name, (c, n) -> new Customer(n, c.orders()));

      // Mark high-value customers
      Traversal<List<Customer>, String> highValueNames =
          highValueCustomers.andThen(nameLens.asTraversal());

      List<Customer> result = Traversals.modify(highValueNames, name -> name + " [VIP]", customers);

      assertThat(result)
          .extracting(Customer::name)
          .containsExactly("Alice", "Bob [VIP]", "Charlie");
    }

    @Test
    @DisplayName("filterBy() with empty nested fold should exclude element")
    void filterByWithEmptyNested() {
      Traversal<List<Order>, Order> allOrders = listElements();
      Fold<Order, Item> orderItems = Fold.of(Order::items);

      Traversal<List<Order>, Order> ordersWithCheapItems =
          allOrders.filterBy(orderItems, item -> item.price() < 10);

      List<Order> orders =
          List.of(
              new Order(List.of()), // empty order - no items to match
              new Order(List.of(new Item("Pen", 5))), // has cheap item
              new Order(List.of(new Item("Laptop", 1000))) // no cheap items
              );

      List<Order> result = Traversals.getAll(ordersWithCheapItems, orders);
      assertThat(result).hasSize(1);
      assertThat(result.get(0).items()).extracting(Item::name).containsExactly("Pen");
    }

    @Test
    @DisplayName("filterBy() can use composed folds")
    void filterByWithComposedFold() {
      Traversal<List<Customer>, Customer> allCustomers = listElements();
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);
      Getter<Order, Integer> orderTotalGetter =
          Getter.of(source -> source.items().stream().mapToInt(Item::price).sum());
      Fold<Order, Integer> orderTotalFold = orderTotalGetter.asFold();

      // Customers with any order total over $500
      Fold<Customer, Integer> customerOrderTotals = ordersFold.andThen(orderTotalFold);
      Traversal<List<Customer>, Customer> bigSpenders =
          allCustomers.filterBy(customerOrderTotals, total -> total > 500);

      List<Customer> customers =
          List.of(
              new Customer(
                  "Alice",
                  List.of(new Order(List.of(new Item("A", 100), new Item("B", 100))))), // 200
              new Customer(
                  "Bob",
                  List.of(new Order(List.of(new Item("C", 300), new Item("D", 300))))), // 600
              new Customer(
                  "Charlie",
                  List.of(
                      new Order(List.of(new Item("E", 50))), // 50
                      new Order(List.of(new Item("F", 700)))))); // 700

      List<Customer> result = Traversals.getAll(bigSpenders, customers);
      assertThat(result).hasSize(2).extracting(Customer::name).containsExactly("Bob", "Charlie");
    }
  }

  @Nested
  @DisplayName("Traversals.filtered() - Static combinator")
  class StaticFilteredTests {

    @Test
    @DisplayName("static filtered() creates affine traversal")
    void staticFilteredCreatesAffineTraversal() {
      Traversal<User, User> activeFilter = Traversals.filtered(User::active);

      User activeUser = new User("Alice", true, 100);
      User inactiveUser = new User("Bob", false, 200);

      // Active user gets modified
      User result1 = Traversals.modify(activeFilter, User::grantBonus, activeUser);
      assertThat(result1.score()).isEqualTo(200);

      // Inactive user stays unchanged
      User result2 = Traversals.modify(activeFilter, User::grantBonus, inactiveUser);
      assertThat(result2.score()).isEqualTo(200);
      assertThat(result2).isEqualTo(inactiveUser);
    }

    @Test
    @DisplayName("static filtered() getAll returns element only if matches")
    void staticFilteredGetAll() {
      Traversal<User, User> activeFilter = Traversals.filtered(User::active);

      User activeUser = new User("Alice", true, 100);
      User inactiveUser = new User("Bob", false, 200);

      List<User> result1 = Traversals.getAll(activeFilter, activeUser);
      assertThat(result1).containsExactly(activeUser);

      List<User> result2 = Traversals.getAll(activeFilter, inactiveUser);
      assertThat(result2).isEmpty();
    }

    @Test
    @DisplayName("static filtered() composes with list traversal")
    void staticFilteredComposesWithList() {
      Traversal<List<User>, User> activeUsersInList =
          Traversals.<User>forList().andThen(Traversals.filtered(User::active));

      List<User> users =
          List.of(
              new User("Alice", true, 100),
              new User("Bob", false, 200),
              new User("Charlie", true, 150));

      List<User> actives = Traversals.getAll(activeUsersInList, users);
      assertThat(actives).hasSize(2).extracting(User::name).containsExactly("Alice", "Charlie");
    }

    @Test
    @DisplayName("static filtered() chains with further traversals")
    void staticFilteredChains() {
      Lens<User, String> nameLens =
          Lens.of(User::name, (u, n) -> new User(n, u.active(), u.score()));

      Traversal<List<User>, String> activeUserNames =
          Traversals.<User>forList()
              .andThen(Traversals.filtered(User::active))
              .andThen(nameLens.asTraversal());

      List<User> users =
          List.of(
              new User("alice", true, 100),
              new User("bob", false, 200),
              new User("charlie", true, 150));

      List<String> names = Traversals.getAll(activeUserNames, users);
      assertThat(names).containsExactly("alice", "charlie");

      List<User> modified = Traversals.modify(activeUserNames, String::toUpperCase, users);
      assertThat(modified).extracting(User::name).containsExactly("ALICE", "bob", "CHARLIE");
    }

    @Test
    @DisplayName("static filtered() is semantically equivalent to instance method")
    void staticFilteredEquivalentToInstanceMethod() {
      List<User> users =
          List.of(
              new User("Alice", true, 100),
              new User("Bob", false, 200),
              new User("Charlie", true, 150));

      // Instance method approach
      Traversal<List<User>, User> approach1 = Traversals.<User>forList().filtered(User::active);

      // Static combinator approach
      Traversal<List<User>, User> approach2 =
          Traversals.<User>forList().andThen(Traversals.filtered(User::active));

      List<User> result1 = Traversals.getAll(approach1, users);
      List<User> result2 = Traversals.getAll(approach2, users);

      assertThat(result1).containsExactlyElementsOf(result2);

      List<User> modified1 = Traversals.modify(approach1, User::grantBonus, users);
      List<User> modified2 = Traversals.modify(approach2, User::grantBonus, users);

      assertThat(modified1).containsExactlyElementsOf(modified2);
    }
  }

  @Nested
  @DisplayName("Selective Operations")
  class SelectiveOperations {

    private final IdSelective selective = IdSelective.instance();
    private final Traversal<List<User>, User> listTraversal = listElements();

    @Test
    @DisplayName("branch should apply different functions based on predicate")
    void branchAppliesDifferentFunctions() {
      List<User> users =
          List.of(
              new User("Alice", true, 100),
              new User("Bob", false, 200),
              new User("Charlie", true, 150));

      Kind<IdKind.Witness, List<User>> result =
          listTraversal.branch(
              User::active,
              u ->
                  IdKindHelper.ID.widen(
                      Id.of(new User(u.name().toUpperCase(), u.active(), u.score()))),
              u ->
                  IdKindHelper.ID.widen(
                      Id.of(new User(u.name() + "_inactive", u.active(), u.score()))),
              users,
              selective);

      List<User> resultList = IdKindHelper.ID.narrow(result).value();
      assertThat(resultList)
          .containsExactly(
              new User("ALICE", true, 100),
              new User("Bob_inactive", false, 200),
              new User("CHARLIE", true, 150));
    }

    @Test
    @DisplayName("modifyWhen should only modify when predicate is true")
    void modifyWhenAppliesConditionally() {
      List<User> users =
          List.of(
              new User("Alice", true, 100),
              new User("Bob", false, 200),
              new User("Charlie", true, 150));

      Kind<IdKind.Witness, List<User>> result =
          listTraversal.modifyWhen(
              User::active, u -> IdKindHelper.ID.widen(Id.of(u.grantBonus())), users, selective);

      List<User> resultList = IdKindHelper.ID.narrow(result).value();
      assertThat(resultList)
          .containsExactly(
              new User("Alice", true, 200), // bonus applied
              new User("Bob", false, 200), // unchanged
              new User("Charlie", true, 250)); // bonus applied
    }

    @Test
    @DisplayName("modifyWhen should leave all unchanged when no elements match")
    void modifyWhenNoMatches() {
      List<User> users = List.of(new User("Alice", false, 100), new User("Bob", false, 200));

      Kind<IdKind.Witness, List<User>> result =
          listTraversal.modifyWhen(
              User::active, u -> IdKindHelper.ID.widen(Id.of(u.grantBonus())), users, selective);

      List<User> resultList = IdKindHelper.ID.narrow(result).value();
      assertThat(resultList).containsExactlyElementsOf(users);
    }

    @Test
    @DisplayName("branch with empty list should return empty list")
    void branchWithEmptyList() {
      List<User> emptyList = List.of();

      Kind<IdKind.Witness, List<User>> result =
          listTraversal.branch(
              User::active,
              u -> IdKindHelper.ID.widen(Id.of(u.grantBonus())),
              u -> IdKindHelper.ID.widen(Id.of(u)),
              emptyList,
              selective);

      List<User> resultList = IdKindHelper.ID.narrow(result).value();
      assertThat(resultList).isEmpty();
    }
  }

  @Nested
  @DisplayName("andThen(Traversal) Composition")
  class AndThenTraversalTests {

    @Test
    @DisplayName("andThen(Traversal) should compose two traversals")
    void andThenComposesTraversals() {
      Traversal<List<Order>, Order> ordersTraversal = listElements();
      Traversal<Order, Item> itemsTraversal =
          new Traversal<>() {
            @Override
            public <F> Kind<F, Order> modifyF(
                Function<Item, Kind<F, Item>> f, Order source, Applicative<F> applicative) {
              Kind<F, Kind<ListKind.Witness, Item>> traversed =
                  ListTraverse.INSTANCE.traverse(
                      applicative, f, ListKindHelper.LIST.widen(source.items()));
              return applicative.map(
                  listKind -> new Order(ListKindHelper.LIST.narrow(listKind)), traversed);
            }
          };

      // Compose: List<Order> -> Order -> Item
      Traversal<List<Order>, Item> allItems = ordersTraversal.andThen(itemsTraversal);

      List<Order> orders =
          List.of(
              new Order(List.of(new Item("Apple", 50), new Item("Banana", 30))),
              new Order(List.of(new Item("Laptop", 1000))));

      // Get all items
      List<Item> items = Traversals.getAll(allItems, orders);
      assertThat(items).hasSize(3);
      assertThat(items).extracting(Item::name).containsExactly("Apple", "Banana", "Laptop");

      // Modify all items
      List<Order> modified =
          Traversals.modify(allItems, i -> new Item(i.name().toUpperCase(), i.price()), orders);
      assertThat(modified.get(0).items()).extracting(Item::name).containsExactly("APPLE", "BANANA");
      assertThat(modified.get(1).items()).extracting(Item::name).containsExactly("LAPTOP");
    }

    @Test
    @DisplayName("andThen(Traversal) with nested empty should preserve structure")
    void andThenWithEmptyNested() {
      Traversal<List<Order>, Order> ordersTraversal = listElements();
      Traversal<Order, Item> itemsTraversal =
          new Traversal<>() {
            @Override
            public <F> Kind<F, Order> modifyF(
                Function<Item, Kind<F, Item>> f, Order source, Applicative<F> applicative) {
              Kind<F, Kind<ListKind.Witness, Item>> traversed =
                  ListTraverse.INSTANCE.traverse(
                      applicative, f, ListKindHelper.LIST.widen(source.items()));
              return applicative.map(
                  listKind -> new Order(ListKindHelper.LIST.narrow(listKind)), traversed);
            }
          };

      Traversal<List<Order>, Item> allItems = ordersTraversal.andThen(itemsTraversal);

      List<Order> orders =
          List.of(new Order(List.of()), new Order(List.of(new Item("Widget", 100))));

      List<Item> items = Traversals.getAll(allItems, orders);
      assertThat(items).hasSize(1);
      assertThat(items.get(0).name()).isEqualTo("Widget");

      List<Order> modified =
          Traversals.modify(allItems, i -> new Item(i.name() + "!", i.price()), orders);
      assertThat(modified.get(0).items()).isEmpty();
      assertThat(modified.get(1).items()).extracting(Item::name).containsExactly("Widget!");
    }
  }
}
