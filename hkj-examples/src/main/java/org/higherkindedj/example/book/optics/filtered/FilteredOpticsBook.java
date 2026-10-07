// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.filtered;

import java.util.List;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/filtered_optics.html">Filtered Optics</a>
 * page, where it composes a filtered traversal and shows what {@code modify} keeps and what {@code
 * getAll} leaves out. The page {@code {{#include}}}s the anchored regions, and {@code
 * FilteredOpticsBookTest} holds the claims the page makes about this code.
 */
public final class FilteredOpticsBook {

  private FilteredOpticsBook() {}

  /** What the page's Step 2 returns: the active names, and the list with only those changed. */
  record Composed(List<String> names, List<User> result) {}

  /** What the page's semantics example returns: the modified list, and the matching users. */
  record Semantics(List<User> modified, List<User> gotten) {}

  static Composed composeWithALens() {
    // ANCHOR: compose
    // Compose: list → filtered users → user name
    Traversal<List<User>, String> activeUserNames =
        Traversals.<User>forList().filtered(User::active).andThen(UserLenses.name());

    List<User> users =
        List.of(
            new User("alice", true, 100, SubscriptionTier.PREMIUM),
            new User("bob", false, 200, SubscriptionTier.FREE),
            new User("charlie", true, 150, SubscriptionTier.BASIC));

    // Get only active user names
    List<String> names = Traversals.getAll(activeUserNames, users);
    // [alice, charlie]

    // Uppercase only active user names
    List<User> result = Traversals.modify(activeUserNames, String::toUpperCase, users);
    // [User[name=ALICE, active=true, score=100, tier=PREMIUM],
    //  User[name=bob, active=false, score=200, tier=FREE],
    //  User[name=CHARLIE, active=true, score=150, tier=BASIC]]
    // bob is unchanged because he is inactive
    // ANCHOR_END: compose
    return new Composed(names, result);
  }

  static Semantics preservedAndExcluded() {
    // ANCHOR: semantics
    List<User> users =
        List.of(
            new User("Alice", true, 100, SubscriptionTier.PREMIUM),
            new User("Bob", false, 200, SubscriptionTier.FREE),
            new User("Charlie", true, 150, SubscriptionTier.BASIC));

    Traversal<List<User>, User> activeUsers = Traversals.<User>forList().filtered(User::active);

    // MODIFY: the structure is preserved, and only the matching users change
    List<User> modified = Traversals.modify(activeUsers, User::grantBonus, users);
    // [User[name=Alice, active=true, score=200, tier=PREMIUM],
    //  User[name=Bob, active=false, score=200, tier=FREE],
    //  User[name=Charlie, active=true, score=250, tier=BASIC]]
    // Alice and Charlie gain 100 points; Bob, inactive, keeps his place unchanged

    // QUERY: only the matching users are returned
    List<User> gotten = Traversals.getAll(activeUsers, users);
    // [User[name=Alice, active=true, score=100, tier=PREMIUM],
    //  User[name=Charlie, active=true, score=150, tier=BASIC]]
    // Bob is left out entirely
    // ANCHOR_END: semantics
    return new Semantics(modified, gotten);
  }
}

enum SubscriptionTier {
  FREE,
  BASIC,
  PREMIUM,
  ENTERPRISE
}

@GenerateLenses
record User(String name, boolean active, int score, SubscriptionTier tier) {
  User grantBonus() {
    return new User(name, active, score + 100, tier);
  }
}
