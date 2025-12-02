// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fluent;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/** Tests for the fluent API {@link OpticOps}. */
class OpticOpsTest {

  // Test data
  record Person(String name, int age) {}

  static final Lens<Person, String> NAME_LENS =
      Lens.of(Person::name, (person, name) -> new Person(name, person.age()));

  static final Lens<Person, Integer> AGE_LENS =
      Lens.of(Person::age, (person, age) -> new Person(person.name(), age));

  record Team(String name, List<Person> members) {}

  static final Lens<Team, String> TEAM_NAME_LENS =
      Lens.of(Team::name, (team, name) -> new Team(name, team.members()));

  static final Lens<Team, List<Person>> TEAM_MEMBERS_LENS =
      Lens.of(Team::members, (team, members) -> new Team(team.name(), members));

  static final Traversal<Team, Person> TEAM_MEMBERS_TRAVERSAL =
      TEAM_MEMBERS_LENS.asTraversal().andThen(Traversals.forList());

  // ============================================================================
  // Static Method Tests
  // ============================================================================

  @Test
  void testGet() {
    Person person = new Person("Alice", 25);
    String name = OpticOps.get(person, NAME_LENS);
    assertEquals("Alice", name);
  }

  @Test
  void testPreview() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Optional<Person> first = OpticOps.preview(team, TEAM_MEMBERS_TRAVERSAL);
    assertTrue(first.isPresent());
    assertEquals("Alice", first.get().name());
  }

  @Test
  void testGetAll() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    List<Person> members = OpticOps.getAll(team, TEAM_MEMBERS_TRAVERSAL);
    assertEquals(2, members.size());
  }

  @Test
  void testSet() {
    Person person = new Person("Alice", 25);
    Person updated = OpticOps.set(person, NAME_LENS, "Bob");
    assertEquals("Bob", updated.name());
    assertEquals(25, updated.age());
  }

  @Test
  void testSetAll() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Team updated = OpticOps.setAll(team, ages, 21);

    List<Integer> updatedAges = OpticOps.getAll(updated, ages);
    assertEquals(List.of(21, 21), updatedAges);
  }

  @Test
  void testModify() {
    Person person = new Person("Alice", 25);
    Person updated = OpticOps.modify(person, AGE_LENS, age -> age + 1);
    assertEquals("Alice", updated.name());
    assertEquals(26, updated.age());
  }

  @Test
  void testModifyAll() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Team updated = OpticOps.modifyAll(team, ages, age -> age + 1);

    List<Integer> updatedAges = OpticOps.getAll(updated, ages);
    assertEquals(List.of(26, 31), updatedAges);
  }

  @Test
  void testExists() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    assertTrue(OpticOps.exists(team, ages, age -> age > 28));
    assertFalse(OpticOps.exists(team, ages, age -> age > 40));
  }

  @Test
  void testAll() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    assertTrue(OpticOps.all(team, ages, age -> age >= 18));
    assertFalse(OpticOps.all(team, ages, age -> age >= 30));
  }

  @Test
  void testFind() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));

    Optional<Person> found = OpticOps.find(team, TEAM_MEMBERS_TRAVERSAL, p -> p.age() >= 30);
    assertTrue(found.isPresent());
    assertEquals("Bob", found.get().name());
  }

  @Test
  void testCount() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));

    int count = OpticOps.count(team, TEAM_MEMBERS_TRAVERSAL);
    assertEquals(2, count);
  }

  @Test
  void testIsEmpty() {
    Team emptyTeam = new Team("Empty", List.of());
    Team fullTeam = new Team("Full", List.of(new Person("Alice", 25)));

    assertTrue(OpticOps.isEmpty(emptyTeam, TEAM_MEMBERS_TRAVERSAL));
    assertFalse(OpticOps.isEmpty(fullTeam, TEAM_MEMBERS_TRAVERSAL));
  }

  // ============================================================================
  // Static Method Tests with Traversal/Getter Parameters
  // ============================================================================

  @Test
  void testGetWithGetter() {
    Person person = new Person("Alice", 25);
    Getter<Person, String> nameGetter = Getter.of(Person::name);

    String name = OpticOps.get(person, nameGetter);
    assertEquals("Alice", name);
  }

  @Test
  void testPreviewWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    Optional<Person> first = OpticOps.preview(team, membersFold);
    assertTrue(first.isPresent());
    assertEquals("Alice", first.get().name());
  }

  @Test
  void testGetAllWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    List<Person> members = OpticOps.getAll(team, membersFold);
    assertEquals(2, members.size());
  }

  @Test
  void testExistsWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Fold<Team, Integer> agesFold = Fold.of(t -> Traversals.getAll(ages, t));

    assertTrue(OpticOps.exists(team, agesFold, age -> age > 28));
    assertFalse(OpticOps.exists(team, agesFold, age -> age > 40));
  }

  @Test
  void testAllWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Fold<Team, Integer> agesFold = Fold.of(t -> Traversals.getAll(ages, t));

    assertTrue(OpticOps.all(team, agesFold, age -> age >= 18));
    assertFalse(OpticOps.all(team, agesFold, age -> age >= 30));
  }

  @Test
  void testFindWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    Optional<Person> found = OpticOps.find(team, membersFold, p -> p.age() >= 30);
    assertTrue(found.isPresent());
    assertEquals("Bob", found.get().name());
  }

  @Test
  void testCountWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    int count = OpticOps.count(team, membersFold);
    assertEquals(2, count);
  }

  @Test
  void testIsEmptyWithFold() {
    Team emptyTeam = new Team("Empty", List.of());
    Team fullTeam = new Team("Full", List.of(new Person("Alice", 25)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    assertTrue(OpticOps.isEmpty(emptyTeam, membersFold));
    assertFalse(OpticOps.isEmpty(fullTeam, membersFold));
  }

  @Test
  void testModifyF() {
    Person person = new Person("Alice", 25);

    // Use Id as the effect type (simple functor)
    Functor<IdKind.Witness> idFunctor = IdMonad.instance();

    Kind<IdKind.Witness, Person> result =
        OpticOps.modifyF(person, AGE_LENS, age -> Id.of(age + 1), idFunctor);

    Person updated = IdKindHelper.ID.narrow(result).value();
    assertEquals(26, updated.age());
  }

  @Test
  void testModifyAllF() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    // Use Id as the effect type (simple applicative)
    Applicative<IdKind.Witness> idApplicative = IdMonad.instance();

    Kind<IdKind.Witness, Team> result =
        OpticOps.modifyAllF(team, ages, age -> Id.of(age + 1), idApplicative);

    Team updated = IdKindHelper.ID.narrow(result).value();
    assertEquals(26, updated.members().get(0).age());
    assertEquals(31, updated.members().get(1).age());
  }

  // ============================================================================
  // Fluent Builder Tests
  // ============================================================================

  @Test
  void testGettingThrough() {
    Person person = new Person("Alice", 25);
    String name = OpticOps.getting(person).through(NAME_LENS);
    assertEquals("Alice", name);
  }

  @Test
  void testGettingMaybeThrough() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25)));
    Optional<Person> first = OpticOps.getting(team).maybeThrough(TEAM_MEMBERS_TRAVERSAL);
    assertTrue(first.isPresent());
    assertEquals("Alice", first.get().name());
  }

  @Test
  void testGettingAllThrough() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    List<Person> members = OpticOps.getting(team).allThrough(TEAM_MEMBERS_TRAVERSAL);
    assertEquals(2, members.size());
  }

  @Test
  void testSettingThrough() {
    Person person = new Person("Alice", 25);
    Person updated = OpticOps.setting(person).through(NAME_LENS, "Bob");
    assertEquals("Bob", updated.name());
  }

  @Test
  void testSettingAllThrough() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Team updated = OpticOps.setting(team).allThrough(ages, 21);

    List<Integer> updatedAges = OpticOps.getAll(updated, ages);
    assertEquals(List.of(21, 21), updatedAges);
  }

  @Test
  void testModifyingThrough() {
    Person person = new Person("Alice", 25);
    Person updated = OpticOps.modifying(person).through(AGE_LENS, age -> age + 1);
    assertEquals(26, updated.age());
  }

  @Test
  void testModifyingAllThrough() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Team updated = OpticOps.modifying(team).allThrough(ages, age -> age + 1);

    List<Integer> updatedAges = OpticOps.getAll(updated, ages);
    assertEquals(List.of(26, 31), updatedAges);
  }

  @Test
  void testQueryingAnyMatch() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    assertTrue(OpticOps.querying(team).anyMatch(ages, age -> age > 28));
  }

  @Test
  void testQueryingAllMatch() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    assertTrue(OpticOps.querying(team).allMatch(ages, age -> age >= 18));
  }

  @Test
  void testQueryingFindFirst() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));

    Optional<Person> found =
        OpticOps.querying(team).findFirst(TEAM_MEMBERS_TRAVERSAL, p -> p.age() >= 30);
    assertTrue(found.isPresent());
    assertEquals("Bob", found.get().name());
  }

  @Test
  void testQueryingCount() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));

    int count = OpticOps.querying(team).count(TEAM_MEMBERS_TRAVERSAL);
    assertEquals(2, count);
  }

  @Test
  void testQueryingIsEmpty() {
    Team emptyTeam = new Team("Empty", List.of());

    assertTrue(OpticOps.querying(emptyTeam).isEmpty(TEAM_MEMBERS_TRAVERSAL));
  }

  // ============================================================================
  // Fluent Builder Tests with Fold Parameters
  // ============================================================================

  @Test
  void testQueryingAnyMatchWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Fold<Team, Integer> agesFold = Fold.of(t -> Traversals.getAll(ages, t));

    assertTrue(OpticOps.querying(team).anyMatch(agesFold, age -> age > 28));
  }

  @Test
  void testQueryingAllMatchWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Fold<Team, Integer> agesFold = Fold.of(t -> Traversals.getAll(ages, t));

    assertTrue(OpticOps.querying(team).allMatch(agesFold, age -> age >= 18));
  }

  @Test
  void testQueryingFindFirstWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    Optional<Person> found = OpticOps.querying(team).findFirst(membersFold, p -> p.age() >= 30);
    assertTrue(found.isPresent());
    assertEquals("Bob", found.get().name());
  }

  @Test
  void testQueryingCountWithFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    int count = OpticOps.querying(team).count(membersFold);
    assertEquals(2, count);
  }

  @Test
  void testQueryingIsEmptyWithFold() {
    Team emptyTeam = new Team("Empty", List.of());
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    assertTrue(OpticOps.querying(emptyTeam).isEmpty(membersFold));
  }

  @Test
  void testModifyingThroughF() {
    Person person = new Person("Alice", 25);

    // Use Id as the effect type (simple functor)
    Functor<IdKind.Witness> idFunctor = IdMonad.instance();

    Kind<IdKind.Witness, Person> result =
        OpticOps.modifying(person).throughF(AGE_LENS, age -> Id.of(age + 1), idFunctor);

    Person updated = IdKindHelper.ID.narrow(result).value();
    assertEquals(26, updated.age());
  }

  @Test
  void testModifyingAllThroughF() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    // Use Id as the effect type (simple applicative)
    Applicative<IdKind.Witness> idApplicative = IdMonad.instance();

    Kind<IdKind.Witness, Team> result =
        OpticOps.modifying(team).allThroughF(ages, age -> Id.of(age + 1), idApplicative);

    Team updated = IdKindHelper.ID.narrow(result).value();
    assertEquals(26, updated.members().get(0).age());
    assertEquals(31, updated.members().get(1).age());
  }

  @Test
  void testGettingMaybeThroughFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    Optional<Person> first = OpticOps.getting(team).maybeThrough(membersFold);
    assertTrue(first.isPresent());
    assertEquals("Alice", first.get().name());
  }

  @Test
  void testGettingAllThroughFold() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);

    List<Person> members = OpticOps.getting(team).allThrough(membersFold);
    assertEquals(2, members.size());
  }

  // ============================================================================
  // Fluent Builder Tests with Getter Parameters
  // ============================================================================

  @Test
  void testGettingThroughGetter() {
    Person person = new Person("Alice", 25);
    Getter<Person, String> nameGetter = Getter.of(Person::name);

    String name = OpticOps.getting(person).through(nameGetter);
    assertEquals("Alice", name);
  }

  // ============================================================================
  // Utility Class Constructor Test
  // ============================================================================

  @Test
  void testPrivateConstructor() throws Exception {
    // Test that the private constructor throws UnsupportedOperationException
    Constructor<OpticOps> constructor = OpticOps.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    Exception exception =
        assertThrows(InvocationTargetException.class, () -> constructor.newInstance());
    assertTrue(exception.getCause() instanceof UnsupportedOperationException);
    assertEquals("Utility class", exception.getCause().getMessage());
  }

  // ============================================================================
  // Composition Tests
  // ============================================================================

  @Test
  void testComposedOptics() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));

    // Compose to get all ages
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    // Test through static method
    List<Integer> ageList = OpticOps.getAll(team, ages);
    assertEquals(List.of(25, 30), ageList);

    // Test through fluent builder
    List<Integer> ageList2 = OpticOps.getting(team).allThrough(ages);
    assertEquals(List.of(25, 30), ageList2);

    // Modify through composition
    Team updated = OpticOps.modifyAll(team, ages, age -> age + 1);
    assertEquals(List.of(26, 31), OpticOps.getAll(updated, ages));
  }

  // ============================================================================
  // Validation-Aware Operations Tests
  // ============================================================================

  // Test data for validation
  record User(String username, String email, int age) {}

  static final Lens<User, String> USERNAME_LENS =
      Lens.of(User::username, (user, username) -> new User(username, user.email(), user.age()));

  static final Lens<User, String> EMAIL_LENS =
      Lens.of(User::email, (user, email) -> new User(user.username(), email, user.age()));

  static final Lens<User, Integer> USER_AGE_LENS =
      Lens.of(User::age, (user, age) -> new User(user.username(), user.email(), age));

  record Order(String orderId, List<Double> itemPrices) {}

  static final Lens<Order, List<Double>> ITEM_PRICES_LENS =
      Lens.of(Order::itemPrices, (order, prices) -> new Order(order.orderId(), prices));

  static final Traversal<Order, Double> ITEM_PRICES_TRAVERSAL =
      ITEM_PRICES_LENS.asTraversal().andThen(Traversals.forList());

  // ===== modifyEither Tests =====

  @Test
  void testModifyEitherSuccess() {
    User user = new User("alice", "ALICE@EXAMPLE.COM", 30);

    Either<String, User> result =
        OpticOps.modifyEither(
            user,
            EMAIL_LENS,
            email ->
                email.contains("@")
                    ? Either.right(email.toLowerCase())
                    : Either.left("Invalid email format"));

    assertTrue(result.isRight());
    assertEquals("alice@example.com", result.getRight().email());
  }

  @Test
  void testModifyEitherFailure() {
    User user = new User("alice", "not-an-email", 30);

    Either<String, User> result =
        OpticOps.modifyEither(
            user,
            EMAIL_LENS,
            email ->
                email.contains("@")
                    ? Either.right(email.toLowerCase())
                    : Either.left("Invalid email format"));

    assertTrue(result.isLeft());
    assertEquals("Invalid email format", result.getLeft());
  }

  @Test
  void testModifyEitherPreservesOtherFields() {
    User user = new User("alice", "alice@example.com", 30);

    Either<String, User> result =
        OpticOps.modifyEither(user, EMAIL_LENS, email -> Either.right(email.toUpperCase()));

    assertTrue(result.isRight());
    User updated = result.getRight();
    assertEquals("alice", updated.username()); // Preserved
    assertEquals("ALICE@EXAMPLE.COM", updated.email()); // Modified
    assertEquals(30, updated.age()); // Preserved
  }

  // ===== modifyMaybe Tests =====

  @Test
  void testModifyMaybeSuccess() {
    User user = new User("alice", "alice@example.com", 30);

    Maybe<User> result =
        OpticOps.modifyMaybe(
            user,
            USER_AGE_LENS,
            age -> (age >= 0 && age <= 150) ? Maybe.just(age) : Maybe.nothing());

    assertTrue(result.isJust());
    assertEquals(30, result.get().age());
  }

  @Test
  void testModifyMaybeFailure() {
    User user = new User("alice", "alice@example.com", 200);

    Maybe<User> result =
        OpticOps.modifyMaybe(
            user,
            USER_AGE_LENS,
            age -> (age >= 0 && age <= 150) ? Maybe.just(age) : Maybe.nothing());

    assertTrue(result.isNothing());
  }

  @Test
  void testModifyMaybeWithTransformation() {
    User user = new User("alice", "alice@example.com", 29);

    Maybe<User> result =
        OpticOps.modifyMaybe(
            user,
            USER_AGE_LENS,
            age -> {
              int newAge = age + 1;
              return (newAge >= 0 && newAge <= 150) ? Maybe.just(newAge) : Maybe.nothing();
            });

    assertTrue(result.isJust());
    assertEquals(30, result.get().age());
  }

  // ===== modifyAllValidated Tests =====

  @Test
  void testModifyAllValidatedAllSuccess() {
    Order order = new Order("ORD-001", List.of(10.0, 50.0, 100.0));

    Validated<List<String>, Order> result =
        OpticOps.modifyAllValidated(
            order,
            ITEM_PRICES_TRAVERSAL,
            price -> {
              if (price < 0) {
                return Validated.invalid("Price cannot be negative");
              } else if (price > 10000) {
                return Validated.invalid("Price exceeds maximum");
              } else {
                return Validated.valid(price);
              }
            });

    assertTrue(result.isValid());
    assertEquals(List.of(10.0, 50.0, 100.0), result.get().itemPrices());
  }

  @Test
  void testModifyAllValidatedSingleError() {
    Order order = new Order("ORD-002", List.of(10.0, -50.0, 100.0));

    Validated<List<String>, Order> result =
        OpticOps.modifyAllValidated(
            order,
            ITEM_PRICES_TRAVERSAL,
            price -> {
              if (price < 0) {
                return Validated.invalid("Price cannot be negative");
              } else if (price > 10000) {
                return Validated.invalid("Price exceeds maximum");
              } else {
                return Validated.valid(price);
              }
            });

    assertTrue(result.isInvalid());
    List<String> errors = result.getError();
    assertEquals(1, errors.size());
    assertTrue(errors.get(0).contains("negative"));
  }

  @Test
  void testModifyAllValidatedMultipleErrors() {
    Order order = new Order("ORD-003", List.of(-10.0, -50.0, 15000.0));

    Validated<List<String>, Order> result =
        OpticOps.modifyAllValidated(
            order,
            ITEM_PRICES_TRAVERSAL,
            price -> {
              if (price < 0) {
                return Validated.invalid("Price cannot be negative");
              } else if (price > 10000) {
                return Validated.invalid("Price exceeds maximum");
              } else {
                return Validated.valid(price);
              }
            });

    assertTrue(result.isInvalid());
    List<String> errors = result.getError();
    assertEquals(3, errors.size()); // All errors accumulated
    assertTrue(errors.get(0).contains("negative"));
    assertTrue(errors.get(1).contains("negative"));
    assertTrue(errors.get(2).contains("maximum"));
  }

  @Test
  void testModifyAllValidatedEmptyTraversal() {
    Order emptyOrder = new Order("ORD-004", List.of());

    Validated<List<String>, Order> result =
        OpticOps.modifyAllValidated(
            emptyOrder, ITEM_PRICES_TRAVERSAL, price -> Validated.invalid("Should not be called"));

    assertTrue(result.isValid()); // No errors because no items to validate
    assertEquals(0, result.get().itemPrices().size());
  }

  // ===== modifyAllEither Tests =====

  @Test
  void testModifyAllEitherAllSuccess() {
    Order order = new Order("ORD-005", List.of(10.0, 50.0, 100.0));

    Either<String, Order> result =
        OpticOps.modifyAllEither(
            order,
            ITEM_PRICES_TRAVERSAL,
            price -> price >= 0 ? Either.right(price) : Either.left("Negative price: " + price));

    assertTrue(result.isRight());
    assertEquals(List.of(10.0, 50.0, 100.0), result.getRight().itemPrices());
  }

  @Test
  void testModifyAllEitherShortCircuits() {
    Order order = new Order("ORD-006", List.of(-10.0, -50.0, -100.0));

    Either<String, Order> result =
        OpticOps.modifyAllEither(
            order,
            ITEM_PRICES_TRAVERSAL,
            price -> price >= 0 ? Either.right(price) : Either.left("Negative price: " + price));

    assertTrue(result.isLeft());
    // Should contain first error only (short-circuit)
    String error = result.getLeft();
    assertEquals("Negative price: -10.0", error);
  }

  @Test
  void testModifyAllEitherEmptyTraversal() {
    Order emptyOrder = new Order("ORD-007", List.of());

    Either<String, Order> result =
        OpticOps.modifyAllEither(
            emptyOrder, ITEM_PRICES_TRAVERSAL, price -> Either.left("Should not be called"));

    assertTrue(result.isRight()); // Success because no items to validate
    assertEquals(0, result.getRight().itemPrices().size());
  }

  // ===== ModifyingWithValidation Builder Tests =====

  @Test
  void testModifyingWithValidationThroughEitherSuccess() {
    User user = new User("alice", "ALICE@EXAMPLE.COM", 30);

    Either<String, User> result =
        OpticOps.modifyingWithValidation(user)
            .throughEither(
                EMAIL_LENS,
                email ->
                    email.contains("@")
                        ? Either.right(email.toLowerCase())
                        : Either.left("Invalid email"));

    assertTrue(result.isRight());
    assertEquals("alice@example.com", result.getRight().email());
  }

  @Test
  void testModifyingWithValidationThroughEitherFailure() {
    User user = new User("alice", "not-an-email", 30);

    Either<String, User> result =
        OpticOps.modifyingWithValidation(user)
            .throughEither(
                EMAIL_LENS,
                email ->
                    email.contains("@")
                        ? Either.right(email.toLowerCase())
                        : Either.left("Invalid email"));

    assertTrue(result.isLeft());
    assertEquals("Invalid email", result.getLeft());
  }

  @Test
  void testModifyingWithValidationThroughMaybeSuccess() {
    User user = new User("alice", "alice@example.com", 30);

    Maybe<User> result =
        OpticOps.modifyingWithValidation(user)
            .throughMaybe(
                USER_AGE_LENS, age -> (age >= 0 && age <= 150) ? Maybe.just(age) : Maybe.nothing());

    assertTrue(result.isJust());
    assertEquals(30, result.get().age());
  }

  @Test
  void testModifyingWithValidationThroughMaybeFailure() {
    User user = new User("alice", "alice@example.com", 200);

    Maybe<User> result =
        OpticOps.modifyingWithValidation(user)
            .throughMaybe(
                USER_AGE_LENS, age -> (age >= 0 && age <= 150) ? Maybe.just(age) : Maybe.nothing());

    assertTrue(result.isNothing());
  }

  @Test
  void testModifyingWithValidationAllThroughValidatedSuccess() {
    Order order = new Order("ORD-008", List.of(10.0, 50.0, 100.0));

    Validated<List<String>, Order> result =
        OpticOps.modifyingWithValidation(order)
            .allThroughValidated(
                ITEM_PRICES_TRAVERSAL,
                price -> price >= 0 ? Validated.valid(price) : Validated.invalid("Negative price"));

    assertTrue(result.isValid());
    assertEquals(List.of(10.0, 50.0, 100.0), result.get().itemPrices());
  }

  @Test
  void testModifyingWithValidationAllThroughValidatedMultipleErrors() {
    Order order = new Order("ORD-009", List.of(-10.0, -50.0, 100.0));

    Validated<List<String>, Order> result =
        OpticOps.modifyingWithValidation(order)
            .allThroughValidated(
                ITEM_PRICES_TRAVERSAL,
                price -> price >= 0 ? Validated.valid(price) : Validated.invalid("Negative price"));

    assertTrue(result.isInvalid());
    assertEquals(2, result.getError().size()); // Two errors accumulated
  }

  @Test
  void testModifyingWithValidationAllThroughEitherSuccess() {
    Order order = new Order("ORD-010", List.of(10.0, 50.0, 100.0));

    Either<String, Order> result =
        OpticOps.modifyingWithValidation(order)
            .allThroughEither(
                ITEM_PRICES_TRAVERSAL,
                price -> price >= 0 ? Either.right(price) : Either.left("Negative price"));

    assertTrue(result.isRight());
    assertEquals(List.of(10.0, 50.0, 100.0), result.getRight().itemPrices());
  }

  @Test
  void testModifyingWithValidationAllThroughEitherShortCircuits() {
    Order order = new Order("ORD-011", List.of(-10.0, -50.0, -100.0));

    Either<String, Order> result =
        OpticOps.modifyingWithValidation(order)
            .allThroughEither(
                ITEM_PRICES_TRAVERSAL,
                price -> price >= 0 ? Either.right(price) : Either.left("Negative price"));

    assertTrue(result.isLeft());
    assertEquals("Negative price", result.getLeft()); // Only first error
  }

  // ===== Integration Tests =====

  @Test
  void testValidationChainingWithFold() {
    User user = new User("alice", "alice@example.com", 30);

    // First validate email
    Either<String, User> emailValidated =
        OpticOps.modifyEither(
            user,
            EMAIL_LENS,
            email ->
                email.contains("@")
                    ? Either.right(email.toLowerCase())
                    : Either.left("Invalid email"));

    // Then validate age if email was valid
    Either<String, User> fullyValidated =
        emailValidated.flatMap(
            validUser ->
                OpticOps.modifyEither(
                    validUser,
                    USER_AGE_LENS,
                    age -> (age >= 18) ? Either.right(age) : Either.left("Must be 18 or older")));

    assertTrue(fullyValidated.isRight());
    assertEquals("alice@example.com", fullyValidated.getRight().email());
    assertEquals(30, fullyValidated.getRight().age());
  }

  @Test
  void testValidationWithTransformation() {
    User user = new User("ALICE", "alice@example.com", 30);

    // Validate and normalize username
    Either<String, User> result =
        OpticOps.modifyEither(
            user,
            USERNAME_LENS,
            username -> {
              if (username.length() < 3) {
                return Either.left("Username too short");
              }
              if (username.length() > 20) {
                return Either.left("Username too long");
              }
              // Normalize to lowercase
              return Either.right(username.toLowerCase());
            });

    assertTrue(result.isRight());
    assertEquals("alice", result.getRight().username()); // Normalized to lowercase
  }

  // ============================================================================
  // Affine Static Method Tests
  // ============================================================================

  record Profile(String name, Optional<String> bio) {}

  static final Affine<Profile, String> PROFILE_BIO_AFFINE =
      Affine.of(Profile::bio, (profile, bio) -> new Profile(profile.name(), Optional.of(bio)));

  @Test
  void testGetOptionalWithAffineWhenPresent() {
    Profile profile = new Profile("Alice", Optional.of("Software Engineer"));

    Optional<String> bio = OpticOps.getOptional(profile, PROFILE_BIO_AFFINE);

    assertTrue(bio.isPresent());
    assertEquals("Software Engineer", bio.get());
  }

  @Test
  void testGetOptionalWithAffineWhenAbsent() {
    Profile profile = new Profile("Alice", Optional.empty());

    Optional<String> bio = OpticOps.getOptional(profile, PROFILE_BIO_AFFINE);

    assertTrue(bio.isEmpty());
  }

  @Test
  void testSetWithAffine() {
    Profile profile = new Profile("Alice", Optional.of("Old bio"));

    Profile updated = OpticOps.set(profile, PROFILE_BIO_AFFINE, "New bio");

    assertTrue(updated.bio().isPresent());
    assertEquals("New bio", updated.bio().get());
  }

  @Test
  void testModifyWithAffine() {
    Profile profile = new Profile("Alice", Optional.of("software engineer"));

    Profile updated = OpticOps.modify(profile, PROFILE_BIO_AFFINE, String::toUpperCase);

    assertTrue(updated.bio().isPresent());
    assertEquals("SOFTWARE ENGINEER", updated.bio().get());
  }

  @Test
  void testMatchesWithAffineWhenPresent() {
    Profile profile = new Profile("Alice", Optional.of("Software Engineer"));

    boolean matches = OpticOps.matches(profile, PROFILE_BIO_AFFINE);

    assertTrue(matches);
  }

  @Test
  void testMatchesWithAffineWhenAbsent() {
    Profile profile = new Profile("Alice", Optional.empty());

    boolean matches = OpticOps.matches(profile, PROFILE_BIO_AFFINE);

    assertFalse(matches);
  }

  // ============================================================================
  // Affine Fluent Builder Tests
  // ============================================================================

  @Test
  void testGettingMaybeThroughAffine() {
    Profile profile = new Profile("Alice", Optional.of("Software Engineer"));

    Optional<String> bio = OpticOps.getting(profile).maybeThrough(PROFILE_BIO_AFFINE);

    assertTrue(bio.isPresent());
    assertEquals("Software Engineer", bio.get());
  }

  @Test
  void testSettingThroughAffine() {
    Profile profile = new Profile("Alice", Optional.of("Old bio"));

    Profile updated = OpticOps.setting(profile).through(PROFILE_BIO_AFFINE, "New bio");

    assertTrue(updated.bio().isPresent());
    assertEquals("New bio", updated.bio().get());
  }

  @Test
  void testModifyingThroughAffine() {
    Profile profile = new Profile("Alice", Optional.of("software engineer"));

    Profile updated = OpticOps.modifying(profile).through(PROFILE_BIO_AFFINE, String::toUpperCase);

    assertTrue(updated.bio().isPresent());
    assertEquals("SOFTWARE ENGINEER", updated.bio().get());
  }
}
