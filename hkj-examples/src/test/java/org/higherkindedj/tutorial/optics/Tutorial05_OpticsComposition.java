// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 05: Optics Composition - Combining Different Optic Types
 *
 * <p>The true power of optics comes from composing different types together. You can chain Lenses,
 * Prisms, and Traversals to navigate complex data structures.
 *
 * <p>Key Concepts: - Lens + Lens = Lens (guaranteed path to guaranteed path) - Lens + Prism =
 * Prism (guaranteed path to optional branch) - Lens + Traversal = Traversal (guaranteed path to
 * many elements) - Prism + Lens = Prism (optional branch to guaranteed field) - Traversal +
 * Traversal = Traversal (many to many) - All optics can be converted to Traversal for maximum
 * composability
 */
public class Tutorial05_OpticsComposition {

  // Shared sealed interfaces for exercises (must be at class level)

  // PaymentMethod for exercises 1-2
  @GeneratePrisms
  sealed interface PaymentMethod1 permits CreditCard1, BankAccount1, Cash1 {}

  @GenerateLenses
  record CreditCard1(String number, String cvv) implements PaymentMethod1 {}

  @GenerateLenses
  record BankAccount1(String accountNumber, String routingNumber) implements PaymentMethod1 {}

  record Cash1() implements PaymentMethod1 {}

  // JsonValue for exercises 4-5
  @GeneratePrisms
  sealed interface JsonValue1 permits JsonString1, JsonNumber1, JsonObject1, JsonArray1 {}

  @GenerateLenses
  record JsonString1(String value) implements JsonValue1 {}

  record JsonNumber1(double value) implements JsonValue1 {}

  @GenerateLenses
  record JsonObject1(String name, JsonValue1 data) implements JsonValue1 {}

  @GenerateLenses
  @GenerateTraversals
  record JsonArray1(List<JsonValue1> values) implements JsonValue1 {}

  // Contact for exercise 7
  @GeneratePrisms
  sealed interface Contact1 permits Email1, Phone1 {}

  @GenerateLenses
  record Email1(String address) implements Contact1 {}

  record Phone1(String number) implements Contact1 {}

  // Helper for creating list traversals
  static <S, A> Traversal<S, A> listTraversal(
      java.util.function.Function<S, List<A>> getter,
      java.util.function.BiFunction<S, List<A>, S> setter) {
    return Traversal.of(
        (applicative, f, s) -> {
          List<A> list = getter.apply(s);
          var listKind = Traversals.traverseList(list, a -> f.apply(a), applicative);
          return applicative.map(listKind, newList -> setter.apply(s, newList));
        });
  }

  /**
   * Exercise 1: Lens + Prism composition
   *
   * <p>Access an optional field through a guaranteed path.
   *
   * <p>Task: Navigate to a specific variant's field
   */
  @Test
  void exercise1_lensPlusPrism() {
    @GenerateLenses
    record Order(String id, PaymentMethod1 payment) {}

    // Manual implementations
    class OrderLenses {
      public static Lens<Order, PaymentMethod1> payment() {
        return Lens.of(Order::payment, newPmt -> o -> new Order(o.id(), newPmt));
      }
    }

    class PaymentMethodPrisms {
      public static Prism<PaymentMethod1, CreditCard1> creditCard() {
        return Prism.of(
            pm -> pm instanceof CreditCard1 cc ? Optional.of(cc) : Optional.empty(), cc -> cc);
      }
    }

    Order order = new Order("ORD-001", new CreditCard1("1234-5678", "123"));

    Lens<Order, PaymentMethod1> orderToPayment = OrderLenses.payment();
    Prism<PaymentMethod1, CreditCard1> creditCardPrism = PaymentMethodPrisms.creditCard();

    // TODO: Replace null with composed optic: Lens + Prism
    // This creates a Prism<Order, CreditCard>
    // Hint: orderToPayment.andThen(creditCardPrism)
    Prism<Order, CreditCard1> orderToCreditCard = null;

    Optional<CreditCard1> card = orderToCreditCard.getOptional(order);
    assertThat(card.isPresent()).isTrue();
    assertThat(card.get().number()).isEqualTo("1234-5678");
  }

  /**
   * Exercise 2: Prism + Lens composition
   *
   * <p>Access a field within a specific variant.
   *
   * <p>Task: Update just the CVV of a credit card payment
   */
  @Test
  void exercise2_prismPlusLens() {
    @GeneratePrisms
    sealed interface PaymentMethod {}

    @GenerateLenses
    record CreditCard(String number, String cvv) implements PaymentMethod {}

    record Cash() implements PaymentMethod {}

    @GenerateLenses
    record Order(String id, PaymentMethod payment) {}

    Order order = new Order("ORD-001", new CreditCard("1234-5678", "123"));

    Prism<PaymentMethod, CreditCard> creditCardPrism = PaymentMethodPrisms.creditCard();
    Lens<CreditCard, String> cvvLens = CreditCardLenses.cvv();

    // TODO: Replace null with composed optic: Prism + Lens
    // This creates a Prism<PaymentMethod, String> for the CVV
    // Hint: creditCardPrism.andThen(cvvLens)
    Prism<PaymentMethod, String> cvvPrism = null;

    PaymentMethod updated = cvvPrism.modify(cvv -> "999", order.payment());
    assertThat(((CreditCard) updated).cvv()).isEqualTo("999");
  }

  /**
   * Exercise 3: Lens + Traversal composition
   *
   * <p>Access multiple elements through a guaranteed path.
   *
   * <p>Task: Update all items in an order's item list
   */
  @Test
  void exercise3_lensPlusTraversal() {
    @GenerateLenses
    record Item(String name, double price) {}

    @GenerateLenses
    @GenerateTraversals
    record Order(String id, List<Item> items) {}

    Order order =
        new Order("ORD-001", List.of(new Item("Widget", 10.0), new Item("Gadget", 20.0)));

    Lens<Order, List<Item>> itemsLens = OrderLenses.items();
    Traversal<List<Item>, Item> listTraversal = Traversals.traverseList();

    // TODO: Replace null with composed optic: Lens + Traversal
    // This creates a Traversal<Order, Item>
    // Hint: itemsLens.asTraversal().andThen(listTraversal)
    Traversal<Order, Item> orderToItems = null;

    // Apply 10% discount to all items
    Order updated =
        Traversals.modify(orderToItems, item -> new Item(item.name(), item.price() * 0.9), order);

    assertThat(updated.items().get(0).price()).isCloseTo(9.0, within(0.01));
    assertThat(updated.items().get(1).price()).isCloseTo(18.0, within(0.01));
  }

  /**
   * Exercise 4: Complex composition (Lens + Prism + Lens)
   *
   * <p>Navigate through multiple levels with different optic types.
   *
   * <p>Task: Access a field deep within an optional branch
   */
  @Test
  void exercise4_complexComposition() {
    @GeneratePrisms
    sealed interface JsonValue {}

    @GenerateLenses
    record JsonString(String value) implements JsonValue {}

    record JsonNumber(double value) implements JsonValue {}

    @GenerateLenses
    record JsonObject(String name, JsonValue data) implements JsonValue {}

    JsonObject root = new JsonObject("root", new JsonString("Hello"));

    Lens<JsonObject, JsonValue> dataLens = JsonObjectLenses.data();
    Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();
    Lens<JsonString, String> valueLens = JsonStringLenses.value();

    // TODO: Replace null with a chain: Lens + Prism + Lens
    // to access the string value inside the JsonObject's data field
    Prism<JsonObject, String> valueAccess =
        dataLens.andThen(stringPrism).andThen(null);

    Maybe<String> value = valueAccess.getOptional(root);
    assertThat(value.get()).isEqualTo("Hello");

    JsonObject updated = valueAccess.modify(s -> s.toUpperCase(), root);
    assertThat(((JsonString) updated.data()).value()).isEqualTo("HELLO");
  }

  /**
   * Exercise 5: Traversal + Prism composition
   *
   * <p>Filter a collection to specific variants and access their fields.
   *
   * <p>Task: Find all string values in a mixed list
   */
  @Test
  void exercise5_traversalPlusPrism() {
    @GeneratePrisms
    sealed interface JsonValue {}

    record JsonString(String value) implements JsonValue {}

    record JsonNumber(double value) implements JsonValue {}

    @GenerateTraversals
    record JsonArray(List<JsonValue> values) {}

    JsonArray array =
        new JsonArray(
            List.of(
                new JsonString("hello"), new JsonNumber(42), new JsonString("world")));

    Traversal<JsonArray, JsonValue> valuesTraversal = JsonArrayTraversals.values();
    Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

    // TODO: Replace null with composed optic: Traversal + Prism
    // to focus only on JsonString values
    Traversal<JsonArray, JsonString> stringValues = null;

    List<String> strings =
        Traversals.getAll(stringValues.andThen(Lens.of(JsonString::value, v -> js -> new JsonString(v)).asTraversal()), array);

    assertThat(strings).containsExactly("hello", "world");
  }

  /**
   * Exercise 6: Deep nested composition
   *
   * <p>Combine multiple optics to navigate a complex structure.
   *
   * <p>Task: Access all player scores in all teams
   */
  @Test
  void exercise6_deepNestedComposition() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    @GenerateLenses
    @GenerateTraversals
    record League(String name, List<Team> teams) {}

    League league =
        new League(
            "Pro League",
            List.of(
                new Team("Team A", List.of(new Player("Alice", 100))),
                new Team("Team B", List.of(new Player("Bob", 90), new Player("Charlie", 110)))));

    // TODO: Replace null with a fully composed optic chain:
    // League -> teams (Traversal) -> players (Traversal) -> score (Lens)
    Traversal<League, Integer> allScores = null;

    List<Integer> scores = Traversals.getAll(allScores, league);
    assertThat(scores).containsExactly(100, 90, 110);

    // Give everyone a 5-point bonus
    League updated = Traversals.modify(allScores, score -> score + 5, league);
    List<Integer> updatedScores = Traversals.getAll(allScores, updated);
    assertThat(updatedScores).containsExactly(105, 95, 115);
  }

  /**
   * Exercise 7: Building reusable optic pipelines
   *
   * <p>Create composable building blocks for common access patterns.
   *
   * <p>Task: Build a library of reusable optics
   */
  @Test
  void exercise7_reusableOpticPipelines() {
    @GenerateLenses
    record Address(String street, String city) {}

    @GeneratePrisms
    sealed interface Contact {}

    @GenerateLenses
    record Email(String address) implements Contact {}

    @GenerateLenses
    record Phone(String number) implements Contact {}

    @GenerateLenses
    record User(String name, Address address, Contact contact) {}

    User user =
        new User("Alice", new Address("123 Main St", "Springfield"), new Email("alice@example.com"));

    // Define reusable optic components
    Lens<User, Address> userToAddress = UserLenses.address();
    Lens<Address, String> addressToCity = AddressLenses.city();
    Lens<User, Contact> userToContact = UserLenses.contact();
    Prism<Contact, Email> emailPrism = ContactPrisms.email();
    Lens<Email, String> emailToAddress = EmailLenses.address();

    // TODO: Compose these into useful pipelines
    // 1. User -> city
    Lens<User, String> userToCity = null;

    // 2. User -> email address (optional)
    Prism<User, String> userToEmailAddress = null;

    assertThat(userToCity.get(user)).isEqualTo("Springfield");
    assertThat(userToEmailAddress.getOptional(user).get()).isEqualTo("alice@example.com");
  }

  /**
   * Congratulations! You've completed Tutorial 05: Optics Composition
   *
   * <p>You now understand: ✓ How to compose Lens + Prism for optional access ✓ How to compose
   * Lens + Traversal for bulk operations ✓ How to chain complex compositions ✓ How different optic
   * types combine (Lens+Prism=Prism, etc.) ✓ How to build reusable optic pipelines ✓ How to
   * navigate deeply nested structures with type safety
   *
   * <p>Next: Tutorial 06 - Generated Optics
   */
}
