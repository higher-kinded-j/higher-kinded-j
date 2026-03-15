// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Fold<S, A> Tests")
class FoldTest {

  // Test Data Structures
  record Item(String name, int price) {}

  record Order(List<Item> items) {}

  record Customer(String name, List<Order> orders) {}

  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

  // Simple Monoids for testing
  private static final Monoid<Integer> SUM_MONOID =
      new Monoid<>() {
        @Override
        public Integer empty() {
          return 0;
        }

        @Override
        public Integer combine(Integer a, Integer b) {
          return a + b;
        }
      };

  private static final Monoid<Integer> PRODUCT_MONOID =
      new Monoid<>() {
        @Override
        public Integer empty() {
          return 1;
        }

        @Override
        public Integer combine(Integer a, Integer b) {
          return a * b;
        }
      };

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {

    @Test
    @DisplayName("of() should create a Fold from a getter function")
    void ofCreation() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      List<Item> allItems = itemsFold.getAll(order);
      assertThat(allItems)
          .hasSize(3)
          .extracting(Item::name)
          .containsExactly("Apple", "Banana", "Cherry");
    }

    @Test
    @DisplayName("Custom Fold implementation should work correctly")
    void customFoldImplementation() {
      // Create a Fold that extracts all prices from an order
      Fold<Order, Integer> pricesFold =
          new Fold<>() {
            @Override
            public <M> M foldMap(
                Monoid<M> monoid, Function<? super Integer, ? extends M> f, Order source) {
              M result = monoid.empty();
              for (Item item : source.items()) {
                result = monoid.combine(result, f.apply(item.price()));
              }
              return result;
            }
          };

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      int totalPrice = pricesFold.foldMap(SUM_MONOID, price -> price, order);
      assertThat(totalPrice).isEqualTo(300);
    }

    @Test
    @DisplayName("foldMap() should aggregate values using a Monoid")
    void foldMap() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      int totalPrice = itemsFold.foldMap(SUM_MONOID, Item::price, order);
      assertThat(totalPrice).isEqualTo(300);

      int productOfPrices = itemsFold.foldMap(PRODUCT_MONOID, Item::price, order);
      assertThat(productOfPrices).isEqualTo(750000); // 100 * 50 * 150
    }

    @Test
    @DisplayName("foldMap() on empty structure should return monoid empty")
    void foldMapEmpty() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order emptyOrder = new Order(List.of());

      int totalPrice = itemsFold.foldMap(SUM_MONOID, Item::price, emptyOrder);
      assertThat(totalPrice).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("Query Operations")
  class QueryOperations {

    @Test
    @DisplayName("getAll() should extract all focused parts")
    void getAll() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      List<Item> allItems = itemsFold.getAll(order);
      assertThat(allItems)
          .hasSize(3)
          .extracting(Item::name)
          .containsExactly("Apple", "Banana", "Cherry");
    }

    @Test
    @DisplayName("preview() should return the first focused part")
    void preview() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      Optional<Item> firstItem = itemsFold.preview(order);
      assertThat(firstItem).isPresent().get().extracting(Item::name).isEqualTo("Apple");
    }

    @Test
    @DisplayName("preview() on empty structure should return Optional.empty()")
    void previewEmpty() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order emptyOrder = new Order(List.of());

      Optional<Item> firstItem = itemsFold.preview(emptyOrder);
      assertThat(firstItem).isEmpty();
    }

    @Test
    @DisplayName("find() should return the first matching element")
    void find() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      Optional<Item> expensiveItem = itemsFold.find(item -> item.price() > 100, order);
      assertThat(expensiveItem).isPresent().get().extracting(Item::name).isEqualTo("Cherry");

      Optional<Item> veryExpensiveItem = itemsFold.find(item -> item.price() > 200, order);
      assertThat(veryExpensiveItem).isEmpty();
    }

    @Test
    @DisplayName("isEmpty() should check if there are no focused parts")
    void isEmpty() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order emptyOrder = new Order(List.of());
      assertThat(itemsFold.isEmpty(emptyOrder)).isTrue();

      Order order = new Order(List.of(new Item("Apple", 100)));
      assertThat(itemsFold.isEmpty(order)).isFalse();
    }

    @Test
    @DisplayName("length() should count the number of focused parts")
    void length() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      assertThat(itemsFold.length(order)).isEqualTo(3);

      Order emptyOrder = new Order(List.of());
      assertThat(itemsFold.length(emptyOrder)).isEqualTo(0);
    }

    @Test
    @DisplayName("exists() should check if any element matches a predicate")
    void exists() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      assertThat(itemsFold.exists(item -> item.price() > 100, order)).isTrue();
      assertThat(itemsFold.exists(item -> item.price() > 200, order)).isFalse();

      Order emptyOrder = new Order(List.of());
      assertThat(itemsFold.exists(item -> true, emptyOrder)).isFalse();
    }

    @Test
    @DisplayName("all() should check if all elements match a predicate")
    void all() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      assertThat(itemsFold.all(item -> item.price() > 0, order)).isTrue();
      assertThat(itemsFold.all(item -> item.price() > 100, order)).isFalse();

      Order emptyOrder = new Order(List.of());
      assertThat(itemsFold.all(item -> false, emptyOrder)).isTrue(); // vacuously true
    }
  }

  @Nested
  @DisplayName("Composition")
  class Composition {

    @Test
    @DisplayName("andThen() should compose two Folds")
    void andThenFold() {
      // Fold from Customer to all Orders
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);

      // Fold from Order to all Items
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      // Composed Fold from Customer to all Items
      Fold<Customer, Item> customerItemsFold = ordersFold.andThen(itemsFold);

      Customer customer =
          new Customer(
              "John",
              List.of(
                  new Order(List.of(new Item("Apple", 100), new Item("Banana", 50))),
                  new Order(List.of(new Item("Cherry", 150), new Item("Date", 75)))));

      List<Item> allItems = customerItemsFold.getAll(customer);
      assertThat(allItems)
          .hasSize(4)
          .extracting(Item::name)
          .containsExactly("Apple", "Banana", "Cherry", "Date");

      int totalPrice = customerItemsFold.foldMap(SUM_MONOID, Item::price, customer);
      assertThat(totalPrice).isEqualTo(375); // 100 + 50 + 150 + 75
    }
  }

  @Nested
  @DisplayName("Effectful Operations (modifyF)")
  class EffectfulOperations {

    @Test
    @DisplayName("modifyF should sequence effects and return original structure")
    void modifyFSequencesEffects() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      // Apply an effectful function to each item
      Function<Item, Kind<OptionalKind.Witness, Item>> effectfulTransform =
          item -> OptionalKindHelper.OPTIONAL.widen(Optional.of(item));

      Kind<OptionalKind.Witness, Order> result =
          itemsFold.modifyF(effectfulTransform, order, OptionalMonad.INSTANCE);

      Optional<Order> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      // Fold returns original structure (read-only)
      assertThat(optResult.get()).isEqualTo(order);
    }

    @Test
    @DisplayName("modifyF should fail if any effect fails")
    void modifyFFailsOnAnyFailure() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 50), new Item("Cherry", 150)));

      // Fail if item price is less than 75
      Function<Item, Kind<OptionalKind.Witness, Item>> failOnCheapItems =
          item -> {
            if (item.price() >= 75) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(item));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, Order> result =
          itemsFold.modifyF(failOnCheapItems, order, OptionalMonad.INSTANCE);

      Optional<Order> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isEmpty();
    }

    @Test
    @DisplayName("modifyF on empty fold should return original structure wrapped in effect")
    void modifyFEmptyFold() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order emptyOrder = new Order(List.of());

      Function<Item, Kind<OptionalKind.Witness, Item>> effectfulTransform =
          item -> OptionalKindHelper.OPTIONAL.widen(Optional.of(item));

      Kind<OptionalKind.Witness, Order> result =
          itemsFold.modifyF(effectfulTransform, emptyOrder, OptionalMonad.INSTANCE);

      Optional<Order> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get()).isEqualTo(emptyOrder);
    }

    @Test
    @DisplayName("modifyF should combine multiple effects")
    void modifyFCombinesMultipleEffects() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Order order =
          new Order(
              List.of(new Item("Apple", 100), new Item("Banana", 200), new Item("Cherry", 300)));

      // All items pass validation
      Function<Item, Kind<OptionalKind.Witness, Item>> validatePrice =
          item -> {
            if (item.price() > 0) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(item));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, Order> result =
          itemsFold.modifyF(validatePrice, order, OptionalMonad.INSTANCE);

      Optional<Order> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get()).isEqualTo(order);
    }
  }

  @Nested
  @DisplayName("Integration with Other Optics")
  class IntegrationWithOtherOptics {

    @Test
    @DisplayName("Lens.asFold() should convert Lens to Fold")
    void lensAsFold() {
      record Person(String name, int age) {}

      Lens<Person, String> nameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.age()));

      Fold<Person, String> nameFold = nameLens.asFold();

      Person person = new Person("Alice", 30);

      List<String> names = nameFold.getAll(person);
      assertThat(names).containsExactly("Alice");

      Optional<String> firstName = nameFold.preview(person);
      assertThat(firstName).isPresent().contains("Alice");
    }

    @Test
    @DisplayName("Prism.asFold() should convert Prism to Fold")
    void prismAsFold() {
      Prism<Json, String> jsonStringPrism =
          Prism.of(
              json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
              JsonString::new);

      Fold<Json, String> jsonStringFold = jsonStringPrism.asFold();

      Json stringJson = new JsonString("hello");
      Json numberJson = new JsonNumber(42);

      assertThat(jsonStringFold.getAll(stringJson)).containsExactly("hello");
      assertThat(jsonStringFold.getAll(numberJson)).isEmpty();

      assertThat(jsonStringFold.length(stringJson)).isEqualTo(1);
      assertThat(jsonStringFold.length(numberJson)).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("filtered() - Predicate-based filtering")
  class FilteredTests {

    @Test
    @DisplayName("filtered() should only include elements matching predicate in getAll")
    void filteredGetAll() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Fold<Order, Item> expensiveItems = itemsFold.filtered(item -> item.price() > 100);

      Order order =
          new Order(
              List.of(
                  new Item("Apple", 50),
                  new Item("Laptop", 1000),
                  new Item("Banana", 30),
                  new Item("Phone", 800)));

      List<Item> result = expensiveItems.getAll(order);
      assertThat(result).hasSize(2).extracting(Item::name).containsExactly("Laptop", "Phone");
    }

    @Test
    @DisplayName("filtered() should only aggregate matching elements in foldMap")
    void filteredFoldMap() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Fold<Order, Item> expensiveItems = itemsFold.filtered(item -> item.price() > 100);

      Order order =
          new Order(
              List.of(
                  new Item("Apple", 50),
                  new Item("Laptop", 1000),
                  new Item("Banana", 30),
                  new Item("Phone", 800)));

      // Sum only expensive items
      int total = expensiveItems.foldMap(SUM_MONOID, Item::price, order);
      assertThat(total).isEqualTo(1800); // 1000 + 800
    }

    @Test
    @DisplayName("filtered() should respect predicate in exists()")
    void filteredExists() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Fold<Order, Item> expensiveItems = itemsFold.filtered(item -> item.price() > 100);

      Order order = new Order(List.of(new Item("Apple", 50), new Item("Laptop", 1000)));

      // Check if any expensive item costs more than 500
      assertThat(expensiveItems.exists(item -> item.price() > 500, order)).isTrue();

      // Check if any expensive item is named "Apple" (Apple isn't expensive, so not included)
      assertThat(expensiveItems.exists(item -> item.name().equals("Apple"), order)).isFalse();
    }

    @Test
    @DisplayName("filtered() should respect predicate in all()")
    void filteredAll() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Fold<Order, Item> expensiveItems = itemsFold.filtered(item -> item.price() > 100);

      Order order =
          new Order(
              List.of(new Item("Apple", 50), new Item("Laptop", 1000), new Item("Phone", 800)));

      // All expensive items cost more than 500
      assertThat(expensiveItems.all(item -> item.price() > 500, order)).isTrue();

      // Not all expensive items cost more than 900
      assertThat(expensiveItems.all(item -> item.price() > 900, order)).isFalse();
    }

    @Test
    @DisplayName("filtered() should count only matching elements")
    void filteredLength() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Fold<Order, Item> expensiveItems = itemsFold.filtered(item -> item.price() > 100);

      Order order =
          new Order(
              List.of(
                  new Item("Apple", 50),
                  new Item("Laptop", 1000),
                  new Item("Banana", 30),
                  new Item("Phone", 800)));

      assertThat(expensiveItems.length(order)).isEqualTo(2);
    }

    @Test
    @DisplayName("filtered() with no matches should return empty results")
    void filteredNoMatches() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Fold<Order, Item> veryExpensiveItems = itemsFold.filtered(item -> item.price() > 10000);

      Order order = new Order(List.of(new Item("Laptop", 1000), new Item("Phone", 800)));

      assertThat(veryExpensiveItems.getAll(order)).isEmpty();
      assertThat(veryExpensiveItems.length(order)).isEqualTo(0);
      assertThat(veryExpensiveItems.foldMap(SUM_MONOID, Item::price, order)).isEqualTo(0);
    }

    @Test
    @DisplayName("filtered() with empty source should handle gracefully")
    void filteredEmptySource() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Fold<Order, Item> expensiveItems = itemsFold.filtered(item -> item.price() > 100);

      Order emptyOrder = new Order(List.of());

      assertThat(expensiveItems.getAll(emptyOrder)).isEmpty();
      assertThat(expensiveItems.length(emptyOrder)).isEqualTo(0);
      assertThat(expensiveItems.isEmpty(emptyOrder)).isTrue();
    }

    @Test
    @DisplayName("filtered() can be chained")
    void filteredChaining() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      // Items that are expensive AND have name starting with 'P'
      Fold<Order, Item> expensivePItems =
          itemsFold
              .filtered(item -> item.price() > 100)
              .filtered(item -> item.name().startsWith("P"));

      Order order =
          new Order(
              List.of(
                  new Item("Apple", 50),
                  new Item("Laptop", 1000),
                  new Item("Phone", 800),
                  new Item("Pencil", 2)));

      List<Item> result = expensivePItems.getAll(order);
      assertThat(result).hasSize(1).extracting(Item::name).containsExactly("Phone");
    }

    @Test
    @DisplayName("filtered() composes with other folds")
    void filteredComposition() {
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      // All items from all orders, then filter to expensive ones
      Fold<Customer, Item> expensiveCustomerItems =
          ordersFold.andThen(itemsFold).filtered(item -> item.price() > 100);

      Customer customer =
          new Customer(
              "Alice",
              List.of(
                  new Order(List.of(new Item("Apple", 50), new Item("Laptop", 1000))),
                  new Order(List.of(new Item("Phone", 800), new Item("Banana", 30)))));

      List<Item> expensive = expensiveCustomerItems.getAll(customer);
      assertThat(expensive).hasSize(2).extracting(Item::name).containsExactly("Laptop", "Phone");
    }
  }

  @Nested
  @DisplayName("filterBy() - Query-based filtering")
  class FilterByTests {

    @Test
    @DisplayName("filterBy() should filter based on nested fold query")
    void filterByNestedQuery() {
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      // Filter orders that have any expensive item
      Fold<Customer, Order> ordersWithExpensiveItems =
          ordersFold.filterBy(itemsFold, item -> item.price() > 100);

      Customer customer =
          new Customer(
              "Alice",
              List.of(
                  new Order(List.of(new Item("Apple", 50), new Item("Banana", 30))),
                  new Order(List.of(new Item("Laptop", 1000))),
                  new Order(List.of(new Item("Pen", 5)))));

      List<Order> result = ordersWithExpensiveItems.getAll(customer);
      assertThat(result).hasSize(1);
      assertThat(result.get(0).items()).extracting(Item::name).containsExactly("Laptop");
    }

    @Test
    @DisplayName("filterBy() should aggregate only matching elements")
    void filterByFoldMap() {
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      // Orders with any item over $500
      Fold<Customer, Order> highValueOrders =
          ordersFold.filterBy(itemsFold, item -> item.price() > 500);

      Customer customer =
          new Customer(
              "Alice",
              List.of(
                  new Order(List.of(new Item("Apple", 50))), // total: 50
                  new Order(List.of(new Item("Laptop", 1000))), // total: 1000 (matches)
                  new Order(List.of(new Item("Phone", 800))))); // total: 800 (matches)

      // Count high-value orders
      int count = highValueOrders.length(customer);
      assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("filterBy() with empty nested should exclude element")
    void filterByEmptyNested() {
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Fold<Customer, Order> ordersWithCheapItems =
          ordersFold.filterBy(itemsFold, item -> item.price() < 10);

      Customer customer =
          new Customer(
              "Alice",
              List.of(
                  new Order(List.of()), // empty order - no items
                  new Order(List.of(new Item("Pen", 5))), // has cheap item
                  new Order(List.of(new Item("Laptop", 1000))))); // no cheap items

      List<Order> result = ordersWithCheapItems.getAll(customer);
      assertThat(result).hasSize(1);
      assertThat(result.get(0).items()).extracting(Item::name).containsExactly("Pen");
    }

    @Test
    @DisplayName("filterBy() can use composed folds")
    void filterByComposedFold() {
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);

      // Create a fold that gets order total
      Getter<Order, Integer> orderTotalGetter =
          Getter.of(source -> source.items().stream().mapToInt(Item::price).sum());
      Fold<Order, Integer> orderTotalFold = orderTotalGetter.asFold();

      // Orders with total over $500
      Fold<Customer, Order> bigOrders = ordersFold.filterBy(orderTotalFold, total -> total > 500);

      Customer customer =
          new Customer(
              "Alice",
              List.of(
                  new Order(List.of(new Item("A", 100), new Item("B", 100))), // 200
                  new Order(List.of(new Item("C", 300), new Item("D", 300))), // 600
                  new Order(List.of(new Item("E", 50))))); // 50

      int bigOrderCount = bigOrders.length(customer);
      assertThat(bigOrderCount).isEqualTo(1);
    }

    @Test
    @DisplayName("filterBy() should work with preview()")
    void filterByPreview() {
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Fold<Customer, Order> ordersWithExpensiveItems =
          ordersFold.filterBy(itemsFold, item -> item.price() > 100);

      Customer customer =
          new Customer(
              "Alice",
              List.of(
                  new Order(List.of(new Item("Apple", 50))),
                  new Order(List.of(new Item("Laptop", 1000)))));

      Optional<Order> firstExpensive = ordersWithExpensiveItems.preview(customer);
      assertThat(firstExpensive).isPresent();
      assertThat(firstExpensive.get().items()).extracting(Item::name).containsExactly("Laptop");
    }

    @Test
    @DisplayName("filterBy() should work with find()")
    void filterByFind() {
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      Fold<Customer, Order> ordersWithExpensiveItems =
          ordersFold.filterBy(itemsFold, item -> item.price() > 100);

      Customer customer =
          new Customer(
              "Alice",
              List.of(
                  new Order(List.of(new Item("Apple", 50))),
                  new Order(List.of(new Item("Laptop", 1000))),
                  new Order(List.of(new Item("Phone", 800), new Item("Case", 150)))));

      // Find first order with expensive items that has more than 1 item
      Optional<Order> result =
          ordersWithExpensiveItems.find(order -> order.items().size() > 1, customer);

      assertThat(result).isPresent();
      assertThat(result.get().items()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("plus() - Combining Folds")
  class PlusCombination {

    @Test
    @DisplayName("plus() should combine two folds over different fields")
    void basicCombination() {
      record Person(String firstName, String lastName) {}
      Lens<Person, String> firstLens =
          Lens.of(Person::firstName, (p, n) -> new Person(n, p.lastName()));
      Lens<Person, String> lastLens =
          Lens.of(Person::lastName, (p, n) -> new Person(p.firstName(), n));

      Fold<Person, String> combined = firstLens.asFold().plus(lastLens.asFold());
      Person person = new Person("Alice", "Smith");

      assertThat(combined.getAll(person)).containsExactly("Alice", "Smith");
    }

    @Test
    @DisplayName("fold.plus(Fold.empty()) should behave like fold (right identity)")
    void rightIdentity() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order order = new Order(List.of(new Item("Apple", 100), new Item("Banana", 50)));

      Fold<Order, Item> combined = itemsFold.plus(Fold.empty());
      assertThat(combined.getAll(order)).isEqualTo(itemsFold.getAll(order));
    }

    @Test
    @DisplayName("Fold.empty().plus(fold) should behave like fold (left identity)")
    void leftIdentity() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order order = new Order(List.of(new Item("Apple", 100), new Item("Banana", 50)));

      Fold<Order, Item> combined = Fold.<Order, Item>empty().plus(itemsFold);
      assertThat(combined.getAll(order)).isEqualTo(itemsFold.getAll(order));
    }

    @Test
    @DisplayName("plus() should be associative")
    void associativity() {
      record Triple(String a, String b, String c) {}
      Fold<Triple, String> fa =
          new Fold<>() {
            @Override
            public <M> M foldMap(Monoid<M> m, Function<? super String, ? extends M> f, Triple s) {
              return f.apply(s.a());
            }
          };
      Fold<Triple, String> fb =
          new Fold<>() {
            @Override
            public <M> M foldMap(Monoid<M> m, Function<? super String, ? extends M> f, Triple s) {
              return f.apply(s.b());
            }
          };
      Fold<Triple, String> fc =
          new Fold<>() {
            @Override
            public <M> M foldMap(Monoid<M> m, Function<? super String, ? extends M> f, Triple s) {
              return f.apply(s.c());
            }
          };

      Triple t = new Triple("x", "y", "z");
      assertThat(fa.plus(fb).plus(fc).getAll(t)).isEqualTo(fa.plus(fb.plus(fc)).getAll(t));
    }

    @Test
    @DisplayName("plus() should preserve ordering (first fold before second)")
    void preservesOrdering() {
      Fold<Order, Item> fold1 = Fold.of(order -> order.items().subList(0, 1));
      Fold<Order, Item> fold2 = Fold.of(order -> order.items().subList(1, 2));

      Order order = new Order(List.of(new Item("First", 1), new Item("Second", 2)));
      Fold<Order, Item> combined = fold1.plus(fold2);

      assertThat(combined.getAll(order)).extracting(Item::name).containsExactly("First", "Second");
    }

    @Test
    @DisplayName("preview() on combined fold should return the first element from the first fold")
    void previewOnCombined() {
      record Person(String firstName, String lastName) {}
      Lens<Person, String> firstLens =
          Lens.of(Person::firstName, (p, n) -> new Person(n, p.lastName()));
      Lens<Person, String> lastLens =
          Lens.of(Person::lastName, (p, n) -> new Person(p.firstName(), n));

      Fold<Person, String> combined = firstLens.asFold().plus(lastLens.asFold());
      assertThat(combined.preview(new Person("Alice", "Smith"))).contains("Alice");
    }

    @Test
    @DisplayName("foldMap() with sum monoid should work across combined folds")
    void foldMapWithSumMonoid() {
      Fold<Order, Item> fold1 = Fold.of(order -> List.of(order.items().get(0)));
      Fold<Order, Item> fold2 = Fold.of(order -> List.of(order.items().get(1)));

      Fold<Order, Item> combined = fold1.plus(fold2);
      Order order = new Order(List.of(new Item("A", 100), new Item("B", 200)));

      int total = combined.foldMap(SUM_MONOID, Item::price, order);
      assertThat(total).isEqualTo(300);
    }

    @Test
    @DisplayName("length() on combined fold should return sum of lengths")
    void lengthOnCombined() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order order = new Order(List.of(new Item("A", 1), new Item("B", 2)));

      Fold<Order, Item> combined = itemsFold.plus(itemsFold);
      assertThat(combined.length(order)).isEqualTo(4);
    }

    @Test
    @DisplayName("exists() on combined fold should check both folds")
    void existsOnCombined() {
      record Pair(int left, int right) {}
      Fold<Pair, Integer> leftFold =
          new Fold<>() {
            @Override
            public <M> M foldMap(Monoid<M> m, Function<? super Integer, ? extends M> f, Pair s) {
              return f.apply(s.left());
            }
          };
      Fold<Pair, Integer> rightFold =
          new Fold<>() {
            @Override
            public <M> M foldMap(Monoid<M> m, Function<? super Integer, ? extends M> f, Pair s) {
              return f.apply(s.right());
            }
          };

      Fold<Pair, Integer> combined = leftFold.plus(rightFold);
      Pair pair = new Pair(5, 15);

      assertThat(combined.exists(x -> x > 10, pair)).isTrue();
      assertThat(combined.exists(x -> x > 20, pair)).isFalse();
    }

    @Test
    @DisplayName("all() on combined fold should check both folds")
    void allOnCombined() {
      record Pair(int left, int right) {}
      Fold<Pair, Integer> leftFold =
          new Fold<>() {
            @Override
            public <M> M foldMap(Monoid<M> m, Function<? super Integer, ? extends M> f, Pair s) {
              return f.apply(s.left());
            }
          };
      Fold<Pair, Integer> rightFold =
          new Fold<>() {
            @Override
            public <M> M foldMap(Monoid<M> m, Function<? super Integer, ? extends M> f, Pair s) {
              return f.apply(s.right());
            }
          };

      Fold<Pair, Integer> combined = leftFold.plus(rightFold);
      Pair pair = new Pair(5, 15);

      assertThat(combined.all(x -> x > 0, pair)).isTrue();
      assertThat(combined.all(x -> x > 10, pair)).isFalse();
    }

    @Test
    @DisplayName("find() on combined fold should find from first fold before second")
    void findOnCombined() {
      record Pair(int left, int right) {}
      Fold<Pair, Integer> leftFold =
          new Fold<>() {
            @Override
            public <M> M foldMap(Monoid<M> m, Function<? super Integer, ? extends M> f, Pair s) {
              return f.apply(s.left());
            }
          };
      Fold<Pair, Integer> rightFold =
          new Fold<>() {
            @Override
            public <M> M foldMap(Monoid<M> m, Function<? super Integer, ? extends M> f, Pair s) {
              return f.apply(s.right());
            }
          };

      Fold<Pair, Integer> combined = leftFold.plus(rightFold);
      Pair pair = new Pair(5, 15);

      assertThat(combined.find(x -> x > 0, pair)).contains(5);
    }

    @Test
    @DisplayName("filtered() should work after combination")
    void filteredOnCombined() {
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Fold<Order, Item> doubled = itemsFold.plus(itemsFold);

      Order order = new Order(List.of(new Item("Apple", 50), new Item("Laptop", 1000)));

      Fold<Order, Item> expensive = doubled.filtered(item -> item.price() > 100);
      assertThat(expensive.getAll(order)).hasSize(2).allMatch(item -> item.name().equals("Laptop"));
    }

    @Test
    @DisplayName("isEmpty() on combined fold should be true only when both are empty")
    void isEmptyOnCombined() {
      Fold<Order, Item> emptyFold = Fold.empty();
      Fold<Order, Item> itemsFold = Fold.of(Order::items);
      Order order = new Order(List.of(new Item("A", 1)));

      assertThat(emptyFold.plus(emptyFold).isEmpty(order)).isTrue();
      assertThat(emptyFold.plus(itemsFold).isEmpty(order)).isFalse();
      assertThat(itemsFold.plus(emptyFold).isEmpty(order)).isFalse();
    }

    @Test
    @DisplayName("Combining Lens.asFold() with Fold should work")
    void lensAsFoldPlusFold() {
      record Container(String label, List<String> tags) {}
      Lens<Container, String> labelLens =
          Lens.of(Container::label, (c, l) -> new Container(l, c.tags()));
      Fold<Container, String> tagsFold = Fold.of(Container::tags);

      Fold<Container, String> combined = labelLens.asFold().plus(tagsFold);
      Container c = new Container("box", List.of("fragile", "heavy"));

      assertThat(combined.getAll(c)).containsExactly("box", "fragile", "heavy");
    }

    @Test
    @DisplayName("Combining Prism.asFold() with Fold should work")
    void prismAsFoldPlusFold() {
      Prism<Json, String> stringPrism =
          Prism.of(
              j -> j instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
              JsonString::new);
      Prism<Json, Integer> numberPrism =
          Prism.of(
              j -> j instanceof JsonNumber n ? Optional.of(n.value()) : Optional.empty(),
              JsonNumber::new);

      // Combine string values and number values mapped to string
      Fold<Json, String> stringFold = stringPrism.asFold();
      Fold<Json, String> numberAsStringFold =
          numberPrism
              .asFold()
              .andThen(
                  new Fold<>() {
                    @Override
                    public <M> M foldMap(
                        Monoid<M> monoid, Function<? super String, ? extends M> f, Integer source) {
                      return f.apply(source.toString());
                    }
                  });

      Fold<Json, String> combined = stringFold.plus(numberAsStringFold);
      assertThat(combined.getAll(new JsonString("hello"))).containsExactly("hello");
      assertThat(combined.getAll(new JsonNumber(42))).containsExactly("42");
    }

    @Test
    @DisplayName("Combining Affine.asFold() with Fold should work")
    void affineAsFoldPlusFold() {
      record Config(Optional<String> host, Optional<String> port) {}
      Affine<Config, String> hostAffine =
          Affine.of(c -> c.host(), (c, h) -> new Config(Optional.of(h), c.port()));
      Affine<Config, String> portAffine =
          Affine.of(c -> c.port(), (c, p) -> new Config(c.host(), Optional.of(p)));

      Fold<Config, String> combined = hostAffine.asFold().plus(portAffine.asFold());

      Config both = new Config(Optional.of("localhost"), Optional.of("8080"));
      assertThat(combined.getAll(both)).containsExactly("localhost", "8080");

      Config hostOnly = new Config(Optional.of("localhost"), Optional.empty());
      assertThat(combined.getAll(hostOnly)).containsExactly("localhost");
    }

    @Test
    @DisplayName("Multiple chained plus calls should work")
    void multipleChainedPlus() {
      record Quad(String a, String b, String c, String d) {}
      Function<Function<Quad, String>, Fold<Quad, String>> single =
          getter ->
              new Fold<>() {
                @Override
                public <M> M foldMap(Monoid<M> m, Function<? super String, ? extends M> f, Quad s) {
                  return f.apply(getter.apply(s));
                }
              };

      Fold<Quad, String> all =
          single
              .apply(Quad::a)
              .plus(single.apply(Quad::b))
              .plus(single.apply(Quad::c))
              .plus(single.apply(Quad::d));

      Quad q = new Quad("w", "x", "y", "z");
      assertThat(all.getAll(q)).containsExactly("w", "x", "y", "z");
    }

    @Test
    @DisplayName("Fold.empty() getAll should return empty list")
    void emptyFoldGetAll() {
      Fold<Order, Item> emptyFold = Fold.empty();
      Order order = new Order(List.of(new Item("A", 1)));

      assertThat(emptyFold.getAll(order)).isEmpty();
      assertThat(emptyFold.length(order)).isEqualTo(0);
      assertThat(emptyFold.isEmpty(order)).isTrue();
      assertThat(emptyFold.preview(order)).isEmpty();
    }

    @Test
    @DisplayName("Fold.sum() should combine multiple folds like chained plus")
    void sumVarargs() {
      record Triple(String a, String b, String c) {}
      Function<Function<Triple, String>, Fold<Triple, String>> single =
          getter ->
              new Fold<>() {
                @Override
                public <M> M foldMap(
                    Monoid<M> m, Function<? super String, ? extends M> f, Triple s) {
                  return f.apply(getter.apply(s));
                }
              };

      Fold<Triple, String> fa = single.apply(Triple::a);
      Fold<Triple, String> fb = single.apply(Triple::b);
      Fold<Triple, String> fc = single.apply(Triple::c);

      Fold<Triple, String> summed = Fold.sum(fa, fb, fc);
      Fold<Triple, String> chained = fa.plus(fb).plus(fc);

      Triple t = new Triple("x", "y", "z");
      assertThat(summed.getAll(t)).isEqualTo(chained.getAll(t));
    }

    @Test
    @DisplayName("plus() with andThen should compose first then combine")
    void plusWithAndThen() {
      Fold<Customer, Order> ordersFold = Fold.of(Customer::orders);
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      // Two different customers' orders via different paths
      Fold<Customer, Item> customerItems = ordersFold.andThen(itemsFold);

      // Combine the names fold from items with prices fold from items
      Fold<Customer, String> namesFold =
          customerItems.andThen(
              new Fold<>() {
                @Override
                public <M> M foldMap(Monoid<M> m, Function<? super String, ? extends M> f, Item s) {
                  return f.apply(s.name());
                }
              });
      Fold<Customer, Integer> pricesFold =
          customerItems.andThen(
              new Fold<>() {
                @Override
                public <M> M foldMap(
                    Monoid<M> m, Function<? super Integer, ? extends M> f, Item s) {
                  return f.apply(s.price());
                }
              });

      Customer customer = new Customer("John", List.of(new Order(List.of(new Item("Apple", 100)))));

      assertThat(namesFold.getAll(customer)).containsExactly("Apple");
      assertThat(pricesFold.foldMap(SUM_MONOID, p -> p, customer)).isEqualTo(100);
    }

    @Test
    @DisplayName("modifyF on combined fold should sequence effects from both folds")
    void modifyFOnCombined() {
      Fold<Order, Item> fold1 = Fold.of(order -> List.of(order.items().get(0)));
      Fold<Order, Item> fold2 = Fold.of(order -> List.of(order.items().get(1)));
      Fold<Order, Item> combined = fold1.plus(fold2);

      Order order = new Order(List.of(new Item("Apple", 100), new Item("Banana", 200)));

      // All items pass validation
      Function<Item, Kind<OptionalKind.Witness, Item>> validate =
          item -> OptionalKindHelper.OPTIONAL.widen(Optional.of(item));

      Kind<OptionalKind.Witness, Order> result =
          combined.modifyF(validate, order, OptionalMonad.INSTANCE);

      Optional<Order> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get()).isEqualTo(order);
    }
  }
}
