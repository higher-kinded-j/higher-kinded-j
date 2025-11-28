// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import module java.base;
import module org.higherkindedj.core;

/**
 * A runnable example demonstrating the At type class for indexed CRUD operations on collections.
 *
 * <p>At provides a Lens to an Optional value at a given index, enabling:
 *
 * <ul>
 *   <li>Setting to Optional.empty() deletes the entry
 *   <li>Setting to Optional.of(value) inserts or updates the entry
 *   <li>Seamless composition with other optics for deep access
 * </ul>
 */
public class AtUsageExample {

  // Domain model for a user profile with nested collections
  // @GenerateLenses creates AUUserProfileLenses with lens accessors for each field
  @GenerateLenses
  public record AUUserProfile(
      String username,
      Map<String, String> settings,
      Map<String, Integer> scores,
      List<String> tags) {}

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("=== At Type Class Usage Examples ===\n");

    demonstrateMapOperations();
    demonstrateListOperations();
    demonstrateLensComposition();
    demonstrateDeepComposition();
  }

  private static void demonstrateMapOperations() {
    System.out.println("--- Map CRUD Operations ---");

    // Create At instance for Map<String, Integer>
    At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();

    // Initial data
    Map<String, Integer> scores = new HashMap<>();
    scores.put("math", 95);
    scores.put("english", 88);

    System.out.println("Initial scores: " + scores);

    // INSERT: Add a new subject score
    Map<String, Integer> afterInsert = mapAt.insertOrUpdate("science", 92, scores);
    System.out.println("After insert 'science': " + afterInsert);

    // UPDATE: Modify an existing score
    Map<String, Integer> afterUpdate = mapAt.insertOrUpdate("math", 98, afterInsert);
    System.out.println("After update 'math': " + afterUpdate);

    // READ: Get a score (returns Optional)
    Optional<Integer> physicsScore = mapAt.get("physics", afterUpdate);
    System.out.println("Physics score (absent): " + physicsScore);

    Optional<Integer> mathScore = mapAt.get("math", afterUpdate);
    System.out.println("Math score (present): " + mathScore);

    // DELETE: Remove a subject
    Map<String, Integer> afterRemove = mapAt.remove("english", afterUpdate);
    System.out.println("After remove 'english': " + afterRemove);

    // MODIFY: Apply function to existing value
    Map<String, Integer> afterModify = mapAt.modify("math", x -> x + 5, afterRemove);
    System.out.println("After modify 'math' (+5): " + afterModify);

    // CHECK: Contains operation
    System.out.println("Contains 'science': " + mapAt.contains("science", afterModify));
    System.out.println("Contains 'english': " + mapAt.contains("english", afterModify));

    // Original unchanged (immutability)
    System.out.println("Original scores (unchanged): " + scores);
    System.out.println();
  }

  private static void demonstrateListOperations() {
    System.out.println("--- List CRUD Operations ---");

    // Create At instance for List<String>
    At<List<String>, Integer, String> listAt = AtInstances.listAt();

    // Initial data
    List<String> tags = new ArrayList<>(List.of("java", "functional", "optics"));

    System.out.println("Initial tags: " + tags);

    // UPDATE: Modify element at index
    List<String> afterUpdate = listAt.insertOrUpdate(1, "FUNCTIONAL", tags);
    System.out.println("After update index 1: " + afterUpdate);

    // READ: Get element at index
    Optional<String> first = listAt.get(0, afterUpdate);
    System.out.println("Element at index 0: " + first);

    Optional<String> outOfBounds = listAt.get(10, afterUpdate);
    System.out.println("Element at index 10 (out of bounds): " + outOfBounds);

    // DELETE: Remove element (shifts indices)
    List<String> afterRemove = listAt.remove(1, afterUpdate);
    System.out.println("After remove index 1 (shifts): " + afterRemove);

    // MODIFY: Apply function to element
    List<String> afterModify = listAt.modify(0, String::toUpperCase, afterRemove);
    System.out.println("After modify index 0 (uppercase): " + afterModify);

    // Original unchanged
    System.out.println("Original tags (unchanged): " + tags);
    System.out.println();
  }

  private static void demonstrateLensComposition() {
    System.out.println("--- Lens Composition with At ---");

    // Use generated lenses from @GenerateLenses annotation
    Lens<AUUserProfile, Map<String, String>> settingsLens = AUUserProfileLenses.settings();
    Lens<AUUserProfile, Map<String, Integer>> scoresLens = AUUserProfileLenses.scores();

    // Create At instances
    At<Map<String, String>, String, String> settingsAt = AtInstances.mapAt();
    At<Map<String, Integer>, String, Integer> scoresAt = AtInstances.mapAt();

    // Initial profile
    AUUserProfile profile =
        new AUUserProfile(
            "alice",
            new HashMap<>(Map.of("theme", "dark", "language", "en")),
            new HashMap<>(Map.of("math", 95)),
            new ArrayList<>(List.of("developer")));

    System.out.println("Initial profile: " + profile);

    // Compose: AUUserProfile -> settings Map -> Optional<String> at "theme"
    Lens<AUUserProfile, Optional<String>> themeLens = settingsLens.andThen(settingsAt.at("theme"));

    // Read setting
    Optional<String> theme = themeLens.get(profile);
    System.out.println("Current theme: " + theme);

    // Update setting through composed lens
    AUUserProfile lightProfile = themeLens.set(Optional.of("light"), profile);
    System.out.println("After setting theme to 'light': " + lightProfile);

    // Add new setting through composed lens
    Lens<AUUserProfile, Optional<String>> notificationsLens =
        settingsLens.andThen(settingsAt.at("notifications"));
    AUUserProfile withNotifications = notificationsLens.set(Optional.of("enabled"), lightProfile);
    System.out.println("After adding 'notifications': " + withNotifications);

    // Remove setting through composed lens
    Lens<AUUserProfile, Optional<String>> languageLens =
        settingsLens.andThen(settingsAt.at("language"));
    AUUserProfile withoutLanguage = languageLens.set(Optional.empty(), withNotifications);
    System.out.println("After removing 'language': " + withoutLanguage);

    // Original unchanged
    System.out.println("Original profile (unchanged): " + profile);
    System.out.println();
  }

  private static void demonstrateDeepComposition() {
    System.out.println("--- Deep Composition: At + Prism ---");

    // For truly deep access, compose At's Lens<S, Optional<A>> with a Prism to unwrap Optional
    Lens<AUUserProfile, Map<String, Integer>> scoresLens = AUUserProfileLenses.scores();

    At<Map<String, Integer>, String, Integer> scoresAt = AtInstances.mapAt();
    Prism<Optional<Integer>, Integer> somePrism = Prisms.some();

    // Compose into a Traversal (0-or-1 focus)
    Lens<AUUserProfile, Optional<Integer>> mathScoreLens = scoresLens.andThen(scoresAt.at("math"));
    Traversal<AUUserProfile, Integer> mathScoreTraversal =
        mathScoreLens.asTraversal().andThen(somePrism.asTraversal());

    AUUserProfile profile =
        new AUUserProfile(
            "bob",
            new HashMap<>(),
            new HashMap<>(Map.of("math", 85, "science", 90)),
            new ArrayList<>());

    System.out.println("Initial profile: " + profile);

    // Get all focused values (0 or 1)
    List<Integer> mathScores = Traversals.getAll(mathScoreTraversal, profile);
    System.out.println("Math score via traversal: " + mathScores);

    // Modify through traversal
    AUUserProfile boosted = Traversals.modify(mathScoreTraversal, x -> x + 10, profile);
    System.out.println("After boosting math by 10: " + boosted);

    // Missing key results in no modification
    Traversal<AUUserProfile, Integer> historyScoreTraversal =
        scoresLens.andThen(scoresAt.at("history")).asTraversal().andThen(somePrism.asTraversal());

    List<Integer> historyScores = Traversals.getAll(historyScoreTraversal, profile);
    System.out.println("History score (absent): " + historyScores);

    AUUserProfile unchanged = Traversals.modify(historyScoreTraversal, x -> x + 10, profile);
    System.out.println("After trying to boost absent history: " + unchanged);
    System.out.println("Profiles equal (no change): " + profile.equals(unchanged));

    System.out.println();
  }
}
