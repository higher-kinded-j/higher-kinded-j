// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 10: Free Applicative - SOLUTIONS
 *
 * <p>This file contains the solutions for all exercises in Tutorial 10.
 */
public class Tutorial10_FreeApplicative_Solution {

  // Helper for interpreting with Id
  private static final IdMonad ID_APP = IdMonad.instance();
  private static final Natural<IdKind.Witness, IdKind.Witness> IDENTITY = Natural.identity();

  @Test
  void exercise1_pure() {
    // SOLUTION: Create a FreeAp containing the value 42
    FreeAp<IdKind.Witness, Integer> pureValue = FreeAp.pure(42);

    // Interpret to get the value
    Kind<IdKind.Witness, Integer> result = pureValue.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo(42);
  }

  @Test
  void exercise2_map() {
    FreeAp<IdKind.Witness, Integer> pureValue = FreeAp.pure(10);

    // SOLUTION: Map the value to multiply by 5
    FreeAp<IdKind.Witness, Integer> mapped = pureValue.map(x -> x * 5);

    Kind<IdKind.Witness, Integer> result = mapped.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo(50);
  }

  @Test
  void exercise3_map2() {
    FreeAp<IdKind.Witness, String> name = FreeAp.pure("Alice");
    FreeAp<IdKind.Witness, Integer> age = FreeAp.pure(30);

    // SOLUTION: Combine name and age into a greeting
    FreeAp<IdKind.Witness, String> combined =
        name.map2(age, (n, a) -> n + " is " + a + " years old");

    Kind<IdKind.Witness, String> result = combined.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo("Alice is 30 years old");
  }

  @Test
  void exercise4_multipleMap2() {
    FreeAp<IdKind.Witness, Integer> a = FreeAp.pure(10);
    FreeAp<IdKind.Witness, Integer> b = FreeAp.pure(20);
    FreeAp<IdKind.Witness, Integer> c = FreeAp.pure(30);

    // SOLUTION: Sum all three values using map2 chains
    FreeAp<IdKind.Witness, Integer> sum = a.map2(b, Integer::sum).map2(c, Integer::sum);

    Kind<IdKind.Witness, Integer> result = sum.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo(60);
  }

  @Test
  void exercise5_independenceUnderstanding() {
    // These represent "fetching" operations
    FreeAp<IdKind.Witness, String> fetchUser = FreeAp.pure("User{id=1}");
    FreeAp<IdKind.Witness, String> fetchPosts = FreeAp.pure("Posts{count=5}");

    // SOLUTION: Combine them - fetchPosts does NOT depend on fetchUser's result
    FreeAp<IdKind.Witness, String> dashboard =
        fetchUser.map2(fetchPosts, (user, posts) -> user + " has " + posts);

    Kind<IdKind.Witness, String> result = dashboard.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo("User{id=1} has Posts{count=5}");
  }

  @Test
  void exercise6_buildRecord() {
    record Person(String name, int age, String city) {}

    FreeAp<IdKind.Witness, String> fetchName = FreeAp.pure("Bob");
    FreeAp<IdKind.Witness, Integer> fetchAge = FreeAp.pure(25);
    FreeAp<IdKind.Witness, String> fetchCity = FreeAp.pure("London");

    // SOLUTION: Combine all three into a Person using map3
    FreeApApplicative<IdKind.Witness> applicative = FreeApApplicative.instance();
    Kind<FreeApKind.Witness<IdKind.Witness>, Person> personKind =
        applicative.map3(
            FREE_AP.widen(fetchName),
            FREE_AP.widen(fetchAge),
            FREE_AP.widen(fetchCity),
            Person::new);
    FreeAp<IdKind.Witness, Person> person = FREE_AP.narrow(personKind);

    Kind<IdKind.Witness, Person> result = person.foldMap(IDENTITY, ID_APP);
    Person p = ID.narrow(result).value();

    assertThat(p.name()).isEqualTo("Bob");
    assertThat(p.age()).isEqualTo(25);
    assertThat(p.city()).isEqualTo("London");
  }
}
