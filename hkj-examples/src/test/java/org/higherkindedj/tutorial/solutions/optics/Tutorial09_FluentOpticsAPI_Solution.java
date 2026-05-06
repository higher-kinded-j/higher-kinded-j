// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial09 FluentOpticsAPI — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial09_FluentOpticsAPI_Solution {

  // Manual traversal/lens implementations (annotation processor generates these in real projects)

  /** Helper to create a Traversal for List fields */
  static <S, A> Traversal<S, A> listTraversal(
      Function<S, List<A>> getter, BiFunction<S, List<A>, S> setter) {
    return new Traversal<S, A>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S s, Applicative<F> applicative) {
        List<A> list = getter.apply(s);
        var listKind = Traversals.traverseList(list, f, applicative);
        return applicative.map(newList -> setter.apply(s, newList), listKind);
      }
    };
  }

  /**
   * Why this is idiomatic: {@code OpticOps.get(source, lens)} reads source-first, matching the way
   * Java developers think about access ("from this person, the name"). The lens still does the
   * work; the call shape just feels native.
   *
   * <p>Alternative: {@code lens.get(source)} — the curried form. Both produce the same value; pick
   * the source-first form when the surrounding code is plain Java and the curried form when
   * chaining other optic combinators.
   *
   * <p>Common wrong attempt: mix the two orderings in the same file. Readers cannot remember which
   * side the source is on; pick one convention per module and stick with it.
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

    // Use OpticOps.get() with source-first parameter ordering
    String name = OpticOps.get(person, nameLens);

    assertThat(name).isEqualTo("Alice");

    // Use OpticOps.set() with source-first parameter ordering
    Person updated = OpticOps.set(person, ageLens, 31);

    assertThat(updated.age()).isEqualTo(31);
    assertThat(updated.name()).isEqualTo("Alice"); // Other fields unchanged
  }

  /**
   * Why this is idiomatic: {@code OpticOps.modify(source, lens, fn)} captures "transform this field
   * of this source by this function" in one call, source-first. The lens does the read, the
   * function transforms, the lens writes back.
   *
   * <p>Alternative: {@code OpticOps.set(source, lens, fn.apply(OpticOps.get(source, lens)))}. Three
   * calls, two reads, easy to typo. Use {@code modify} whenever the new value depends on the old.
   *
   * <p>Common wrong attempt: pass the lens-curried form {@code lens.modify(fn, source)} into an
   * {@code OpticOps.modify(...)} call. The {@code OpticOps} API expects a fresh source-first
   * invocation; do not nest the two conventions.
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

    // Use OpticOps.modify() to increment age by 1
    Person older = OpticOps.modify(person, ageLens, age -> age + 1);

    assertThat(older.age()).isEqualTo(31);

    // Use OpticOps.modify() to uppercase the name
    Person capitalised = OpticOps.modify(person, nameLens, String::toUpperCase);

    assertThat(capitalised.name()).isEqualTo("ALICE");
  }

  /**
   * Why this is idiomatic: {@code OpticOps.getAll(source, traversal)} reads every focused element
   * into a list; {@code OpticOps.modifyAll(source, traversal, fn)} rewrites them in one pass. The
   * shape mirrors the read/write pair on lenses.
   *
   * <p>Alternative: call {@code Traversals.getAll}/{@code Traversals.modify} from the static
   * helpers. Same answer; the {@code OpticOps} surface keeps every operation source-first for
   * consistency with the rest of the API.
   *
   * <p>Common wrong attempt: hand-roll the iteration with a stream and try to write back via a
   * constructor. Works for one schema; the traversal-based call survives schema growth.
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

    // Use OpticOps.getAll() to extract all player names
    List<String> names = OpticOps.getAll(team, playerNames);

    assertThat(names).containsExactly("Alice", "Bob");

    Traversal<Team, Player> playersTraversal = TeamTraversals.players();

    // Use OpticOps.modifyAll() to give everyone +10 bonus points
    Team bonusApplied =
        OpticOps.modifyAll(team, playersTraversal, p -> new Player(p.name(), p.score() + 10));

    assertThat(bonusApplied.players().get(0).score()).isEqualTo(110);
    assertThat(bonusApplied.players().get(1).score()).isEqualTo(100);
  }

  /**
   * Why this is idiomatic: {@code exists}, {@code count}, and {@code find} answer the three most
   * common questions about a focused set of elements without materialising a list. They read like
   * assertions and return at the first match where they can.
   *
   * <p>Alternative: call {@code getAll(...)} and run streams over the result. Equivalent for small
   * inputs; the dedicated query methods short-circuit and avoid the intermediate list for large
   * traversals.
   *
   * <p>Common wrong attempt: write {@code getAll(...).contains(predicate)} — {@code List.contains}
   * checks equality, not a predicate. Use {@code anyMatch} or the dedicated {@code exists}.
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

    // Use OpticOps.exists() to check if any score >= 100
    boolean hasHighScorers = OpticOps.exists(team, scores, score -> score >= 100);

    assertThat(hasHighScorers).isTrue();

    // Use OpticOps.count() to count total players
    int playerCount = OpticOps.count(team, TeamTraversals.players());

    assertThat(playerCount).isEqualTo(3);

    Traversal<Team, Player> players = TeamTraversals.players();

    // Use OpticOps.find() to find first player with score > 100
    Optional<Player> topPlayer = OpticOps.find(team, players, p -> p.score() > 100);

    assertThat(topPlayer).isPresent();
    assertThat(topPlayer.get().name()).isEqualTo("Alice");
  }

  /**
   * Why this is idiomatic: {@code OpticOps.modifyEither(source, lens, fn)} runs an {@code
   * Either}-returning validator on the focused field. A {@code Left} aborts the write; a {@code
   * Right} replaces the field. The error type is exactly what the validator returns.
   *
   * <p>Alternative: validate before modifying ({@code if (!isValid(...)) return Left(...)}). Same
   * outcome; the optic-driven version keeps the path and the validator paired in one call site.
   *
   * <p>Common wrong attempt: throw an {@code IllegalArgumentException} from the validator. The
   * optic call now propagates a runtime exception instead of a typed {@code Left}; reach for {@code
   * modifyEither} when typed errors are the contract.
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

    // Use OpticOps.modifyEither() to validate and normalise email
    Either<String, User> validResult = OpticOps.modifyEither(user, emailLens, validateEmail);

    assertThat(validResult.isRight()).isTrue();
    assertThat(validResult.getRight().email()).isEqualTo("alice@example.com");

    // Test with invalid email
    User invalidUser = new User("user2", "notanemail");

    // Use OpticOps.modifyEither() on invalid email
    Either<String, User> invalidResult =
        OpticOps.modifyEither(invalidUser, emailLens, validateEmail);

    assertThat(invalidResult.isLeft()).isTrue();
    assertThat(invalidResult.getLeft()).contains("missing @");
  }

  /**
   * Why this is idiomatic: {@code OpticOps.modifyMaybe(source, lens, fn)} replaces the {@code
   * Either} channel with a {@code Maybe} — when the only meaningful answer to "did this work?" is
   * yes/no, the simpler wrapper saves the caller from inventing an error type.
   *
   * <p>Alternative: keep using {@code modifyEither} with {@code Either<Unit, A>}. Equivalent;
   * {@code modifyMaybe} is shorter when the failure carries no extra information.
   *
   * <p>Common wrong attempt: return {@code null} for invalid inputs. {@code Maybe.nothing()} is the
   * typed equivalent and never NPEs; never reach for {@code null} when the API offers a wrapper.
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

    // Use OpticOps.modifyMaybe() to validate age
    Maybe<Person> validResult = OpticOps.modifyMaybe(person, ageLens, validateAge);

    assertThat(validResult.isJust()).isTrue();

    // Test with invalid age
    Person invalidPerson = new Person("Bob", 200);

    // Use OpticOps.modifyMaybe() on invalid age
    Maybe<Person> invalidResult = OpticOps.modifyMaybe(invalidPerson, ageLens, validateAge);

    assertThat(invalidResult.isNothing()).isTrue();
  }

  /**
   * Why this is idiomatic: {@code modifyAllValidated} runs every element through the validator and
   * combines the errors via a {@code Semigroup}. The user sees every reason the order is rejected,
   * not just the first one.
   *
   * <p>Alternative: {@code modifyAllEither} with fail-fast semantics. Pick that when one error is
   * enough; pick the {@code Validated} form when the user wants to fix everything at once.
   *
   * <p>Common wrong attempt: feed validation errors into a list and {@code throw} at the end. The
   * errors are now a side channel; the {@code Validated} return type carries them in the result and
   * stays composable.
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

    // Use OpticOps.modifyAllValidated() to validate all prices
    Validated<List<String>, Order> result =
        OpticOps.modifyAllValidated(order, prices, validatePrice);

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

    // Use OpticOps.modifyAllValidated() on valid order
    Validated<List<String>, Order> validResult =
        OpticOps.modifyAllValidated(validOrder, validPrices, validatePrice);

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
