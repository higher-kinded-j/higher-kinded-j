// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 08: Fluent Optics API - Ergonomic Data Access
 *
 * <p>The OpticOps class provides a more fluent, Java-friendly API for working with optics. It
 * offers two complementary styles:
 *
 * <ul>
 *   <li><b>Static methods</b>: Concise, direct operations (source-first parameter ordering)
 *   <li><b>Fluent builders</b>: Method chaining for explicit workflows
 * </ul>
 *
 * <p>Key Benefits: - More intuitive parameter ordering (source comes first) - Validation-aware
 * operations with Either, Maybe, and Validated - Query operations (exists, count, find) - Cleaner
 * syntax for common patterns
 *
 * <p>This tutorial covers the fluent API. For the Free Monad DSL approach, see Tutorial 09.
 */
public class Tutorial08_FluentOpticsAPI {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // Manual traversal/lens implementations (annotation processor generates these in real projects)

  /** Helper to create a Traversal for List fields */
  static <S, A> Traversal<S, A> listTraversal(
      Function<S, List<A>> getter, BiFunction<S, List<A>, S> setter) {
    return new Traversal<S, A>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> applicative) {
        List<A> list = getter.apply(s);
        var listKind =
            org.higherkindedj.optics.util.Traversals.traverseList(
                list, f, applicative);
        return applicative.map(newList -> setter.apply(s, newList), listKind);
      }
    };
  }

  /**
   * Exercise 1: Basic get and set operations
   *
   * <p>OpticOps.get() and OpticOps.set() provide source-first parameter ordering, which is more
   * natural in Java.
   *
   * <p>Task: Use OpticOps to get and set values
   */
  @Test
  void exercise1_basicGetAndSet() {
    @GenerateLenses
    record Person(String name, int age, String email) {}

    // Manual implementation (annotation processor would generate this)
    class PersonLenses {
      public static Lens<Person, String> name() {
        return Lens.of(Person::name, (p, newName) -> new Person(newName, p.age(), p.email()));
      }

      public static Lens<Person, Integer> age() {
        return Lens.of(Person::age, (p, newAge) -> new Person(p.name(), newAge, p.email()));
      }

      public static Lens<Person, String> email() {
        return Lens.of(Person::email, (p, newEmail) -> new Person(p.name(), p.age(), newEmail));
      }
    }

    Person person = new Person("Alice", 30, "alice@example.com");

    Lens<Person, String> nameLens = PersonLenses.name();
    Lens<Person, Integer> ageLens = PersonLenses.age();

    // TODO: Replace null with OpticOps.get() to extract the name
    // Hint: OpticOps.get(person, nameLens)
    String name = answerRequired();

    assertThat(name).isEqualTo("Alice");

    // TODO: Replace null with OpticOps.set() to update the age to 31
    // Hint: OpticOps.set(person, ageLens, 31)
    Person updated = answerRequired();

    assertThat(updated.age()).isEqualTo(31);
    assertThat(updated.name()).isEqualTo("Alice"); // Other fields unchanged
  }

  /**
   * Exercise 2: Modify operations
   *
   * <p>OpticOps.modify() applies a transformation function to the focused value.
   *
   * <p>Task: Use OpticOps.modify() to increment age and uppercase name
   */
  @Test
  void exercise2_modifyOperations() {
    @GenerateLenses
    record Person(String name, int age) {}

    // Manual implementation (annotation processor would generate this)
    class PersonLenses {
      public static Lens<Person, String> name() {
        return Lens.of(Person::name, (p, newName) -> new Person(newName, p.age()));
      }

      public static Lens<Person, Integer> age() {
        return Lens.of(Person::age, (p, newAge) -> new Person(p.name(), newAge));
      }
    }

    Person person = new Person("alice", 30);

    Lens<Person, String> nameLens = PersonLenses.name();
    Lens<Person, Integer> ageLens = PersonLenses.age();

    // TODO: Replace null with OpticOps.modify() to increment age by 1
    // Hint: OpticOps.modify(person, ageLens, age -> age + 1)
    Person older = answerRequired();

    assertThat(older.age()).isEqualTo(31);

    // TODO: Replace null with OpticOps.modify() to uppercase the name
    // Hint: OpticOps.modify(person, nameLens, String::toUpperCase)
    Person capitalized = answerRequired();

    assertThat(capitalized.name()).isEqualTo("ALICE");
  }

  /**
   * Exercise 3: Working with collections using getAll and modifyAll
   *
   * <p>OpticOps provides dedicated methods for traversals over collections.
   *
   * <p>Task: Extract and modify all elements using OpticOps
   */
  @Test
  void exercise3_collectionOperations() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String teamName, List<Player> players) {}

    // Manual implementations (annotation processor would generate these)
    class PlayerLenses {
      public static Lens<Player, String> name() {
        return Lens.of(Player::name, (p, newName) -> new Player(newName, p.score()));
      }

      public static Lens<Player, Integer> score() {
        return Lens.of(Player::score, (p, newScore) -> new Player(p.name(), newScore));
      }
    }

    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.teamName(), ps));
      }
    }

    Team team = new Team("Team Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90)));

    Traversal<Team, String> playerNames =
        TeamTraversals.players().andThen(PlayerLenses.name().asTraversal());

    // TODO: Replace null with OpticOps.getAll() to extract all player names
    // Hint: OpticOps.getAll(team, playerNames)
    List<String> names = answerRequired();

    assertThat(names).containsExactly("Alice", "Bob");

    Traversal<Team, Player> playersTraversal = TeamTraversals.players();

    // TODO: Replace null with OpticOps.modifyAll() to give everyone +10 bonus points
    // Hint: OpticOps.modifyAll(team, playersTraversal, p -> new Player(p.name(), p.score() + 10))
    Team bonusApplied = answerRequired();

    assertThat(bonusApplied.players().get(0).score()).isEqualTo(110);
    assertThat(bonusApplied.players().get(1).score()).isEqualTo(100);
  }

  /**
   * Exercise 4: Query operations - exists, count, find
   *
   * <p>OpticOps provides query methods for checking predicates and finding elements.
   *
   * <p>Task: Use query operations to inspect data
   */
  @Test
  void exercise4_queryOperations() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(List<Player> players) {}

    // Manual implementations (annotation processor would generate these)
    class PlayerLenses {
      public static Lens<Player, String> name() {
        return Lens.of(Player::name, (p, newName) -> new Player(newName, p.score()));
      }

      public static Lens<Player, Integer> score() {
        return Lens.of(Player::score, (p, newScore) -> new Player(p.name(), newScore));
      }
    }

    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(ps));
      }
    }

    Team team =
        new Team(
            List.of(new Player("Alice", 120), new Player("Bob", 85), new Player("Charlie", 95)));

    Traversal<Team, Integer> scores =
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal());

    // TODO: Replace false with OpticOps.exists() to check if any score >= 100
    // Hint: OpticOps.exists(team, scores, score -> score >= 100)
    boolean hasHighScorers = answerRequired();

    assertThat(hasHighScorers).isTrue();

    // TODO: Replace 0 with OpticOps.count() to count total players
    // Hint: OpticOps.count(team, TeamTraversals.players())
    int playerCount = answerRequired();

    assertThat(playerCount).isEqualTo(3);

    Traversal<Team, Player> players = TeamTraversals.players();

    // TODO: Replace null with OpticOps.find() to find first player with score > 100
    // Hint: OpticOps.find(team, players, p -> p.score() > 100)
    Optional<Player> topPlayer = answerRequired();

    assertThat(topPlayer).isPresent();
    assertThat(topPlayer.get().name()).isEqualTo("Alice");
  }

  /**
   * Exercise 5: Validation with Either (fail-fast)
   *
   * <p>OpticOps.modifyEither() validates a value and short-circuits on the first error.
   *
   * <p>Task: Validate and update an email field
   */
  @Test
  void exercise5_validationWithEither() {
    @GenerateLenses
    record User(String id, String email) {}

    // Manual implementation (annotation processor would generate this)
    class UserLenses {
      public static Lens<User, String> id() {
        return Lens.of(User::id, (u, newId) -> new User(newId, u.email()));
      }

      public static Lens<User, String> email() {
        return Lens.of(User::email, (u, newEmail) -> new User(u.id(), newEmail));
      }
    }

    User user = new User("user1", "alice@example.com");

    Lens<User, String> emailLens = UserLenses.email();

    // Email validator function
    Function<String, Either<String, String>> validateEmail =
        email -> {
          if (!email.contains("@")) {
            return Either.left("Invalid email: missing @");
          }
          if (!email.contains(".")) {
            return Either.left("Invalid email: missing domain");
          }
          return Either.right(email.toLowerCase());
        };

    // TODO: Replace null with OpticOps.modifyEither() to validate and normalize email
    // Hint: OpticOps.modifyEither(user, emailLens, validateEmail)
    Either<String, User> validResult = answerRequired();

    assertThat(validResult.isRight()).isTrue();
    assertThat(validResult.getRight().email()).isEqualTo("alice@example.com");

    // Test with invalid email
    User invalidUser = new User("user2", "notanemail");

    // TODO: Replace null with OpticOps.modifyEither() on invalid email
    Either<String, User> invalidResult = answerRequired();

    assertThat(invalidResult.isLeft()).isTrue();
    assertThat(invalidResult.getLeft()).contains("missing @");
  }

  /**
   * Exercise 6: Validation with Maybe (optional)
   *
   * <p>OpticOps.modifyMaybe() validates a value, returning Maybe.nothing() on failure.
   *
   * <p>Task: Validate age is within reasonable bounds
   */
  @Test
  void exercise6_validationWithMaybe() {
    @GenerateLenses
    record Person(String name, int age) {}

    // Manual implementation (annotation processor would generate this)
    class PersonLenses {
      public static Lens<Person, String> name() {
        return Lens.of(Person::name, (p, newName) -> new Person(newName, p.age()));
      }

      public static Lens<Person, Integer> age() {
        return Lens.of(Person::age, (p, newAge) -> new Person(p.name(), newAge));
      }
    }

    Person person = new Person("Alice", 30);

    Lens<Person, Integer> ageLens = PersonLenses.age();

    // Age validator: must be between 0 and 150
    Function<Integer, Maybe<Integer>> validateAge =
        age -> (age >= 0 && age <= 150) ? Maybe.just(age) : Maybe.nothing();

    // TODO: Replace null with OpticOps.modifyMaybe() to validate age
    // Hint: OpticOps.modifyMaybe(person, ageLens, validateAge)
    Maybe<Person> validResult = answerRequired();

    assertThat(validResult.isJust()).isTrue();

    // Test with invalid age
    Person invalidPerson = new Person("Bob", 200);

    // TODO: Replace null with OpticOps.modifyMaybe() on invalid age
    Maybe<Person> invalidResult = answerRequired();

    assertThat(invalidResult.isNothing()).isTrue();
  }

  /**
   * Exercise 7: Multi-field validation with Validated (error accumulation)
   *
   * <p>OpticOps.modifyAllValidated() validates multiple values and accumulates ALL errors, not just
   * the first one.
   *
   * <p>Task: Validate all item prices in an order
   */
  @Test
  void exercise7_validationWithValidated() {
    @GenerateLenses
    record Item(String name, double price) {}

    @GenerateLenses
    @GenerateTraversals
    record Order(String id, List<Item> items) {}

    // Manual implementations (annotation processor would generate these)
    class ItemLenses {
      public static Lens<Item, String> name() {
        return Lens.of(Item::name, (i, newName) -> new Item(newName, i.price()));
      }

      public static Lens<Item, Double> price() {
        return Lens.of(Item::price, (i, newPrice) -> new Item(i.name(), newPrice));
      }
    }

    class OrderTraversals {
      public static Traversal<Order, Item> items() {
        return listTraversal(Order::items, (o, items) -> new Order(o.id(), items));
      }
    }

    Order order =
        new Order(
            "ORD-001",
            List.of(new Item("Widget", 10.0), new Item("Gadget", -5.0), new Item("Gizmo", 0.0)));

    Traversal<Order, Double> prices =
        OrderTraversals.items().andThen(ItemLenses.price().asTraversal());

    // Price validator: must be positive
    Function<Double, Validated<String, Double>> validatePrice =
        price ->
            price > 0
                ? Validated.valid(price)
                : Validated.invalid("Price must be positive: " + price);

    // TODO: Replace null with OpticOps.modifyAllValidated() to validate all prices
    // Hint: OpticOps.modifyAllValidated(order, prices, validatePrice)
    Validated<List<String>, Order> result = answerRequired();

    // Should be invalid because we have negative and zero prices
    assertThat(result.isInvalid()).isTrue();
    // Validated accumulates errors (at least 2 invalid prices)
    List<String> errors = result.fold(errorList -> errorList, valid -> List.of());
    assertThat(errors.size()).isGreaterThanOrEqualTo(1);

    // Test with all valid prices
    Order validOrder =
        new Order("ORD-002", List.of(new Item("Widget", 10.0), new Item("Gadget", 20.0)));

    Traversal<Order, Double> validPrices =
        OrderTraversals.items().andThen(ItemLenses.price().asTraversal());

    // TODO: Replace null with OpticOps.modifyAllValidated() on valid order
    Validated<List<String>, Order> validResult = answerRequired();

    assertThat(validResult.isValid()).isTrue();
  }

  /**
   * Congratulations! You've completed Tutorial 08: Fluent Optics API
   *
   * <p>You now understand: ✓ How to use OpticOps static methods for concise operations ✓ Source-
   * first parameter ordering (more natural in Java) ✓ Collection operations: getAll, modifyAll,
   * setAll ✓ Query operations: exists, count, find ✓ Validation with Either (fail-fast) ✓
   * Validation with Maybe (optional) ✓ Multi-field validation with Validated (error accumulation)
   *
   * <p>Key Takeaways: - OpticOps provides a more ergonomic API than direct optic methods - Use
   * modifyEither/modifyMaybe for single-field validation - Use modifyAllValidated for multi-field
   * validation with error accumulation - Query operations make data inspection cleaner
   *
   * <p>Next: Tutorial 09 - Advanced Optics DSL (Free Monad)
   */
}
