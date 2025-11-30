// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
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
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Lens + Lens = Lens (guaranteed path to guaranteed path)
 *   <li>Lens + Prism = Traversal (guaranteed path to optional branch - zero or one focus)
 *   <li>Lens + Traversal = Traversal (guaranteed path to many elements)
 *   <li>Prism + Lens = Traversal (optional branch to guaranteed field - zero or one focus)
 *   <li>Prism + Prism = Prism (optional to optional)
 *   <li>Traversal + Traversal = Traversal (many to many)
 *   <li>All optics can be converted to Traversal for maximum composability
 * </ul>
 *
 * <p><strong>Important:</strong> When composing a Lens with a Prism (or vice versa), the result is
 * a Traversal because the Prism might not match, resulting in zero-or-one focus.
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
      Function<S, List<A>> getter, BiFunction<S, List<A>, S> setter) {
    return new Traversal<S, A>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> applicative) {
        List<A> list = getter.apply(s);
        var listKind = Traversals.traverseList(list, a -> f.apply(a), applicative);
        return applicative.map(newList -> setter.apply(s, newList), listKind);
      }
    };
  }

  /**
   * Exercise 1: Lens + Prism composition = Traversal
   *
   * <p>Access an optional field through a guaranteed path. Because the Prism might not match, the
   * result is a Traversal with zero-or-one focus.
   *
   * <p>Task: Navigate to a specific variant's field using Lens.andThen(Prism)
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
    Order cashOrder = new Order("ORD-002", new Cash1());

    Lens<Order, PaymentMethod1> orderToPayment = OrderLenses.payment();
    Prism<PaymentMethod1, CreditCard1> creditCardPrism = PaymentMethodPrisms.creditCard();

    // SOLUTION: Compose Lens + Prism = Traversal
    Traversal<Order, CreditCard1> orderToCreditCard = orderToPayment.andThen(creditCardPrism);

    // Use Traversals.getAll to extract the credit card (returns list with 0 or 1 element)
    List<CreditCard1> cards = Traversals.getAll(orderToCreditCard, order);
    assertThat(cards).hasSize(1);
    assertThat(cards.get(0).number()).isEqualTo("1234-5678");

    // For cash orders, the traversal finds no credit cards
    List<CreditCard1> noCards = Traversals.getAll(orderToCreditCard, cashOrder);
    assertThat(noCards).isEmpty();
  }

  /**
   * Exercise 2: Prism + Lens composition = Traversal
   *
   * <p>Access a field within a specific variant. Because the Prism might not match, the result is a
   * Traversal with zero-or-one focus.
   *
   * <p>Task: Update just the CVV of a credit card payment using Prism.andThen(Lens)
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

    PaymentMethod1 creditCard = new CreditCard1("1234-5678", "123");
    PaymentMethod1 cash = new Cash1();

    Prism<PaymentMethod1, CreditCard1> creditCardPrism = PaymentMethodPrisms.creditCard();
    Lens<CreditCard1, String> cvvLens = CreditCardLenses.cvv();

    // SOLUTION: Compose Prism + Lens = Traversal
    Traversal<PaymentMethod1, String> cvvTraversal = creditCardPrism.andThen(cvvLens);

    // Modify the CVV using Traversals.modify
    PaymentMethod1 updated = Traversals.modify(cvvTraversal, cvv -> "999", creditCard);
    assertThat(((CreditCard1) updated).cvv()).isEqualTo("999");

    // Modifying cash payment does nothing (traversal doesn't match)
    PaymentMethod1 unchangedCash = Traversals.modify(cvvTraversal, cvv -> "999", cash);
    assertThat(unchangedCash).isSameAs(cash);
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
   * Exercise 4: Complex composition (Lens + Prism + Lens) = Traversal
   *
   * <p>Navigate through multiple levels with different optic types. The Prism in the middle means
   * the overall result is a Traversal.
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

    JsonObject1 stringRoot = new JsonObject1("root", new JsonString1("Hello"));
    JsonObject1 numberRoot = new JsonObject1("root", new JsonNumber1(42.0));

    Lens<JsonObject1, JsonValue1> dataLens = JsonObjectLenses.data();
    Prism<JsonValue1, JsonString1> stringPrism = JsonValuePrisms.jsonString();
    Lens<JsonString1, String> valueLens = JsonStringLenses.value();

    // SOLUTION: Chain Lens + Prism + Lens = Traversal
    // Note: After Lens.andThen(Prism) returns Traversal, we need .asTraversal() on the Lens
    Traversal<JsonObject1, String> valueAccess =
        dataLens.andThen(stringPrism).andThen(valueLens.asTraversal());

    // Use Traversals.getAll to get the value (returns list with 0 or 1 element)
    List<String> values = Traversals.getAll(valueAccess, stringRoot);
    assertThat(values).containsExactly("Hello");

    // When the prism doesn't match, getAll returns empty list
    List<String> noValues = Traversals.getAll(valueAccess, numberRoot);
    assertThat(noValues).isEmpty();

    // Modify works on matching structures
    JsonObject1 updated = Traversals.modify(valueAccess, String::toUpperCase, stringRoot);
    assertThat(((JsonString1) updated.data()).value()).isEqualTo("HELLO");

    // Modify leaves non-matching structures unchanged
    JsonObject1 unchanged = Traversals.modify(valueAccess, String::toUpperCase, numberRoot);
    assertThat(unchanged.data()).isInstanceOf(JsonNumber1.class);
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

    User phoneUser =
        new User("Bob", new Address("456 Oak Ave", "Shelbyville"), new Phone1("555-1234"));

    // Define reusable optic components
    Lens<User, Address> userToAddress = UserLenses.address();
    Lens<Address, String> addressToCity = AddressLenses.city();
    Lens<User, Contact1> userToContact = UserLenses.contact();
    Prism<Contact1, Email1> emailPrism = ContactPrisms.email();
    Lens<Email1, String> emailToAddress = EmailLenses.address();

    // SOLUTION: Compose these into useful pipelines
    // 1. User -> city (Lens + Lens = Lens)
    Lens<User, String> userToCity = userToAddress.andThen(addressToCity);

    // 2. User -> email address (Lens + Prism + Lens = Traversal)
    // Note: After Lens.andThen(Prism) returns Traversal, we need .asTraversal() on the Lens
    Traversal<User, String> userToEmailAddress =
        userToContact.andThen(emailPrism).andThen(emailToAddress.asTraversal());

    assertThat(userToCity.get(user)).isEqualTo("Springfield");

    // Use Traversals.getAll for the email traversal
    List<String> emails = Traversals.getAll(userToEmailAddress, user);
    assertThat(emails).containsExactly("alice@example.com");

    // Phone users have no email
    List<String> noEmails = Traversals.getAll(userToEmailAddress, phoneUser);
    assertThat(noEmails).isEmpty();
  }

  /**
   * Congratulations! You've completed Tutorial 05: Optics Composition
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How Lens + Lens = Lens (guaranteed paths compose to guaranteed path)
   *   <li>How Lens + Prism = Traversal (adding optionality gives zero-or-one focus)
   *   <li>How Prism + Lens = Traversal (same reasoning - Prism adds optionality)
   *   <li>How to compose Lens + Traversal for bulk operations
   *   <li>How to chain complex compositions
   *   <li>How to build reusable optic pipelines
   *   <li>How to navigate deeply nested structures with type safety
   * </ul>
   *
   * <p>Next: Tutorial 06 - Generated Optics
   */
}
