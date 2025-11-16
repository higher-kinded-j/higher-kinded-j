// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;
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
}
