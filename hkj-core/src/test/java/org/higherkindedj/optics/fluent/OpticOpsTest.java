// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fluent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Fold;
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
    org.higherkindedj.optics.Getter<Person, String> nameGetter =
        org.higherkindedj.optics.Getter.of(Person::name);

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
    org.higherkindedj.hkt.Functor<org.higherkindedj.hkt.id.IdKind.Witness> idFunctor =
        org.higherkindedj.hkt.id.IdMonad.instance();

    org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.id.IdKind.Witness, Person> result =
        OpticOps.modifyF(
            person, AGE_LENS, age -> org.higherkindedj.hkt.id.Id.of(age + 1), idFunctor);

    Person updated = org.higherkindedj.hkt.id.IdKindHelper.ID.narrow(result).value();
    assertEquals(26, updated.age());
  }

  @Test
  void testModifyAllF() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    // Use Id as the effect type (simple applicative)
    org.higherkindedj.hkt.Applicative<org.higherkindedj.hkt.id.IdKind.Witness> idApplicative =
        org.higherkindedj.hkt.id.IdMonad.instance();

    org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.id.IdKind.Witness, Team> result =
        OpticOps.modifyAllF(
            team, ages, age -> org.higherkindedj.hkt.id.Id.of(age + 1), idApplicative);

    Team updated = org.higherkindedj.hkt.id.IdKindHelper.ID.narrow(result).value();
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
    org.higherkindedj.hkt.Functor<org.higherkindedj.hkt.id.IdKind.Witness> idFunctor =
        org.higherkindedj.hkt.id.IdMonad.instance();

    org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.id.IdKind.Witness, Person> result =
        OpticOps.modifying(person)
            .throughF(AGE_LENS, age -> org.higherkindedj.hkt.id.Id.of(age + 1), idFunctor);

    Person updated = org.higherkindedj.hkt.id.IdKindHelper.ID.narrow(result).value();
    assertEquals(26, updated.age());
  }

  @Test
  void testModifyingAllThroughF() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    // Use Id as the effect type (simple applicative)
    org.higherkindedj.hkt.Applicative<org.higherkindedj.hkt.id.IdKind.Witness> idApplicative =
        org.higherkindedj.hkt.id.IdMonad.instance();

    org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.id.IdKind.Witness, Team> result =
        OpticOps.modifying(team)
            .allThroughF(ages, age -> org.higherkindedj.hkt.id.Id.of(age + 1), idApplicative);

    Team updated = org.higherkindedj.hkt.id.IdKindHelper.ID.narrow(result).value();
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
    org.higherkindedj.optics.Getter<Person, String> nameGetter =
        org.higherkindedj.optics.Getter.of(Person::name);

    String name = OpticOps.getting(person).through(nameGetter);
    assertEquals("Alice", name);
  }

  // ============================================================================
  // Utility Class Constructor Test
  // ============================================================================

  @Test
  void testPrivateConstructor() throws Exception {
    // Test that the private constructor throws UnsupportedOperationException
    java.lang.reflect.Constructor<OpticOps> constructor = OpticOps.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    Exception exception =
        assertThrows(
            java.lang.reflect.InvocationTargetException.class, () -> constructor.newInstance());
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
}
