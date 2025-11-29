// Copyright (c) 2025 Magnus Smith
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
}
