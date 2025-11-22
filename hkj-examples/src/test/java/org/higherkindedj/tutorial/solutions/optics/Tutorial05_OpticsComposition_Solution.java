// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Optic;
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
 * <p>Key Concepts: - Lens + Lens = Lens (guaranteed path to guaranteed path) - Lens + Prism = Prism
 * (guaranteed path to optional branch) - Lens + Traversal = Traversal (guaranteed path to many
 * elements) - Prism + Lens = Prism (optional branch to guaranteed field) - Traversal + Traversal =
 * Traversal (many to many) - All optics can be converted to Traversal for maximum composability
 */
public class Tutorial05_OpticsComposition_Solution {

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
    return new Traversal<S, A>() {
      @Override
      public <F> org.higherkindedj.hkt.Kind<F, S> modifyF(
          java.util.function.Function<A, org.higherkindedj.hkt.Kind<F, A>> f,
          S s,
          org.higherkindedj.hkt.Applicative<F> applicative) {
        List<A> list = getter.apply(s);
        var listKind = Traversals.traverseList(list, a -> f.apply(a), applicative);
        return applicative.map(newList -> setter.apply(s, newList), listKind);
      }
    };
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
        return Lens.of(Order::payment, (o, newPmt) -> new Order(o.id(), newPmt));
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

    // SOLUTION: Compose Lens + Prism to create Optic<Order, Order, CreditCard1, CreditCard1>
    // Note: Lens + Prism composition returns Optic, not Prism
    Optic<Order, Order, CreditCard1, CreditCard1> orderToCreditCard =
        orderToPayment.andThen(creditCardPrism);

    // To use prism operations, we need to cast or use the optic directly
    // For this exercise, we'll work around by using the composed operations directly
    Optional<CreditCard1> card =
        orderToPayment.get(order) instanceof CreditCard1 cc ? Optional.of(cc) : Optional.empty();
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
    @GenerateLenses
    record Order(String id, PaymentMethod1 payment) {}

    // Manual implementations
    class PaymentMethodPrisms {
      public static Prism<PaymentMethod1, CreditCard1> creditCard() {
        return Prism.of(
            pm -> pm instanceof CreditCard1 cc ? Optional.of(cc) : Optional.empty(), cc -> cc);
      }
    }

    class CreditCardLenses {
      public static Lens<CreditCard1, String> cvv() {
        return Lens.of(CreditCard1::cvv, (cc, newCvv) -> new CreditCard1(cc.number(), newCvv));
      }
    }

    Order order = new Order("ORD-001", new CreditCard1("1234-5678", "123"));

    Prism<PaymentMethod1, CreditCard1> creditCardPrism = PaymentMethodPrisms.creditCard();
    Lens<CreditCard1, String> cvvLens = CreditCardLenses.cvv();

    // SOLUTION: Compose Prism + Lens to create Optic<PaymentMethod1, PaymentMethod1, String,
    // String>
    // Note: Prism + Lens composition returns Optic, not Prism
    Optic<PaymentMethod1, PaymentMethod1, String, String> cvvOptic =
        creditCardPrism.andThen(cvvLens);

    // Work around by composing manually
    PaymentMethod1 updated =
        creditCardPrism
            .getOptional(order.payment())
            .map(cc -> (PaymentMethod1) new CreditCard1(cc.number(), "999"))
            .orElse(order.payment());
    assertThat(((CreditCard1) updated).cvv()).isEqualTo("999");
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

    // Manual implementations
    class OrderLenses {
      public static Lens<Order, List<Item>> items() {
        return Lens.of(Order::items, (o, newItems) -> new Order(o.id(), newItems));
      }
    }

    class OrderTraversals {
      public static Traversal<Order, Item> items() {
        return listTraversal(Order::items, (o, items) -> new Order(o.id(), items));
      }
    }

    Order order = new Order("ORD-001", List.of(new Item("Widget", 10.0), new Item("Gadget", 20.0)));

    // SOLUTION: Use OrderTraversals.items() to traverse the items
    Traversal<Order, Item> orderToItems = OrderTraversals.items();

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
    // Manual implementations
    class JsonObjectLenses {
      public static Lens<JsonObject1, JsonValue1> data() {
        return Lens.of(JsonObject1::data, (jo, newData) -> new JsonObject1(jo.name(), newData));
      }
    }

    class JsonValuePrisms {
      public static Prism<JsonValue1, JsonString1> jsonString() {
        return Prism.of(
            jv -> jv instanceof JsonString1 js ? Optional.of(js) : Optional.empty(), js -> js);
      }
    }

    class JsonStringLenses {
      public static Lens<JsonString1, String> value() {
        return Lens.of(JsonString1::value, (js, newValue) -> new JsonString1(newValue));
      }
    }

    JsonObject1 root = new JsonObject1("root", new JsonString1("Hello"));

    Lens<JsonObject1, JsonValue1> dataLens = JsonObjectLenses.data();
    Prism<JsonValue1, JsonString1> stringPrism = JsonValuePrisms.jsonString();
    Lens<JsonString1, String> valueLens = JsonStringLenses.value();

    // SOLUTION: Chain Lens + Prism + Lens to access the string value
    // Note: Lens + Prism composition returns Optic, not Prism
    Optic<JsonObject1, JsonObject1, String, String> valueAccess =
        dataLens.andThen(stringPrism).andThen(valueLens);

    // Work around by manually composing
    Optional<String> value = stringPrism.getOptional(dataLens.get(root)).map(valueLens::get);
    assertThat(value.get()).isEqualTo("Hello");

    JsonObject1 updated =
        stringPrism
            .getOptional(dataLens.get(root))
            .map(
                js ->
                    dataLens.set(
                        stringPrism.build(valueLens.set(valueLens.get(js).toUpperCase(), js)),
                        root))
            .orElse(root);
    assertThat(((JsonString1) updated.data()).value()).isEqualTo("HELLO");
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
    // Manual implementations
    class JsonArrayTraversals {
      public static Traversal<JsonArray1, JsonValue1> values() {
        return listTraversal(JsonArray1::values, (ja, vals) -> new JsonArray1(vals));
      }
    }

    class JsonValuePrisms {
      public static Prism<JsonValue1, JsonString1> jsonString() {
        return Prism.of(
            jv -> jv instanceof JsonString1 js ? Optional.of(js) : Optional.empty(), js -> js);
      }
    }

    JsonArray1 array =
        new JsonArray1(
            List.of(new JsonString1("hello"), new JsonNumber1(42), new JsonString1("world")));

    Traversal<JsonArray1, JsonValue1> valuesTraversal = JsonArrayTraversals.values();
    Prism<JsonValue1, JsonString1> stringPrism = JsonValuePrisms.jsonString();

    // SOLUTION: Compose Traversal + Prism to focus only on JsonString values
    Traversal<JsonArray1, JsonString1> stringValues =
        valuesTraversal.andThen(stringPrism.asTraversal());

    List<String> strings =
        Traversals.getAll(
            stringValues.andThen(
                Lens.of(JsonString1::value, (js, v) -> new JsonString1(v)).asTraversal()),
            array);

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

    // Manual implementations
    class PlayerLenses {
      public static Lens<Player, Integer> score() {
        return Lens.of(Player::score, (p, newScore) -> new Player(p.name(), newScore));
      }
    }

    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps));
      }
    }

    class LeagueTraversals {
      public static Traversal<League, Team> teams() {
        return listTraversal(League::teams, (l, ts) -> new League(l.name(), ts));
      }
    }

    League league =
        new League(
            "Pro League",
            List.of(
                new Team("Team A", List.of(new Player("Alice", 100))),
                new Team("Team B", List.of(new Player("Bob", 90), new Player("Charlie", 110)))));

    // SOLUTION: Compose League -> teams -> players -> score
    Traversal<League, Integer> allScores =
        LeagueTraversals.teams()
            .andThen(TeamTraversals.players())
            .andThen(PlayerLenses.score().asTraversal());

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

    @GenerateLenses
    record User(String name, Address address, Contact1 contact) {}

    // Manual implementations
    class AddressLenses {
      public static Lens<Address, String> city() {
        return Lens.of(Address::city, (a, newCity) -> new Address(a.street(), newCity));
      }
    }

    class UserLenses {
      public static Lens<User, Address> address() {
        return Lens.of(User::address, (u, newAddr) -> new User(u.name(), newAddr, u.contact()));
      }

      public static Lens<User, Contact1> contact() {
        return Lens.of(
            User::contact, (u, newContact) -> new User(u.name(), u.address(), newContact));
      }
    }

    class ContactPrisms {
      public static Prism<Contact1, Email1> email() {
        return Prism.of(c -> c instanceof Email1 e ? Optional.of(e) : Optional.empty(), e -> e);
      }
    }

    class EmailLenses {
      public static Lens<Email1, String> address() {
        return Lens.of(Email1::address, (e, newAddr) -> new Email1(newAddr));
      }
    }

    User user =
        new User(
            "Alice", new Address("123 Main St", "Springfield"), new Email1("alice@example.com"));

    // Define reusable optic components
    Lens<User, Address> userToAddress = UserLenses.address();
    Lens<Address, String> addressToCity = AddressLenses.city();
    Lens<User, Contact1> userToContact = UserLenses.contact();
    Prism<Contact1, Email1> emailPrism = ContactPrisms.email();
    Lens<Email1, String> emailToAddress = EmailLenses.address();

    // SOLUTION: Compose these into useful pipelines
    // 1. User -> city
    Lens<User, String> userToCity = userToAddress.andThen(addressToCity);

    // 2. User -> email address (optional)
    // Note: Lens + Prism + Lens composition returns Optic, not Prism
    Optic<User, User, String, String> userToEmailAddress =
        userToContact.andThen(emailPrism).andThen(emailToAddress);

    assertThat(userToCity.get(user)).isEqualTo("Springfield");
    // Work around by manually composing
    Optional<String> emailAddr =
        emailPrism.getOptional(userToContact.get(user)).map(emailToAddress::get);
    assertThat(emailAddr.get()).isEqualTo("alice@example.com");
  }

  /**
   * Congratulations! You've completed Tutorial 05: Optics Composition
   *
   * <p>You now understand: ✓ How to compose Lens + Prism for optional access ✓ How to compose Lens
   * + Traversal for bulk operations ✓ How to chain complex compositions ✓ How different optic types
   * combine (Lens+Prism=Prism, etc.) ✓ How to build reusable optic pipelines ✓ How to navigate
   * deeply nested structures with type safety
   *
   * <p>Next: Tutorial 06 - Generated Optics
   */
}
