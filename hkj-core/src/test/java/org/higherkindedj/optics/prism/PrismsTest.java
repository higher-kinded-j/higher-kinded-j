// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.prism;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Prisms Utility Tests")
class PrismsTest {

  @Nested
  @DisplayName("some() Prism for Optional")
  class SomePrismTests {

    private final Prism<Optional<String>, String> somePrism = Prisms.some();

    @Test
    @DisplayName("getOptional() should return value when Optional is present")
    void getOptionalWhenPresent() {
      Optional<String> source = Optional.of("hello");

      Optional<String> result = somePrism.getOptional(source);

      assertThat(result).hasValue("hello");
    }

    @Test
    @DisplayName("getOptional() should return empty when Optional is empty")
    void getOptionalWhenEmpty() {
      Optional<String> source = Optional.empty();

      Optional<String> result = somePrism.getOptional(source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("build() should wrap value in Optional.of()")
    void buildWrapsInOptional() {
      Optional<String> result = somePrism.build("world");

      assertThat(result).hasValue("world");
    }

    @Test
    @DisplayName("should satisfy Prism laws - review (getOptional ∘ build = Some)")
    void prismReviewLaw() {
      String value = "test";

      Optional<String> result = somePrism.getOptional(somePrism.build(value));

      assertThat(result).hasValue(value);
    }

    @Test
    @DisplayName(
        "should satisfy Prism laws - preview (getOptional.flatMap(build) = identity when present)")
    void prismPreviewLaw() {
      Optional<String> source = Optional.of("hello");

      Optional<Optional<String>> rebuilt = somePrism.getOptional(source).map(somePrism::build);

      assertThat(rebuilt).hasValue(source);
    }

    @Test
    @DisplayName("asTraversal() should convert to working Traversal")
    void asTraversalConversion() {
      Traversal<Optional<String>, String> traversal = somePrism.asTraversal();

      Optional<String> source = Optional.of("hello");
      Optional<String> result = Traversals.modify(traversal, String::toUpperCase, source);

      assertThat(result).hasValue("HELLO");
    }

    @Test
    @DisplayName("asTraversal() should be no-op when Optional is empty")
    void asTraversalNoOpWhenEmpty() {
      Traversal<Optional<String>, String> traversal = somePrism.asTraversal();

      Optional<String> source = Optional.empty();
      Optional<String> result = Traversals.modify(traversal, String::toUpperCase, source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should compose with At lens for deep access")
    void composeWithAtLens() {
      record User(String name, Map<String, Integer> scores) {}

      Lens<User, Map<String, Integer>> scoresLens =
          Lens.of(User::scores, (u, s) -> new User(u.name(), s));

      var mapAt = AtInstances.<String, Integer>mapAt();
      Prism<Optional<Integer>, Integer> intSomePrism = Prisms.some();

      // Compose: User -> Map -> Optional<Integer> -> Integer
      Lens<User, Optional<Integer>> mathScoreLens = scoresLens.andThen(mapAt.at("math"));
      Traversal<User, Integer> mathScoreTraversal =
          mathScoreLens.asTraversal().andThen(intSomePrism.asTraversal());

      User user = new User("Alice", new HashMap<>(Map.of("math", 95, "english", 88)));

      // Get all values (should be single element)
      List<Integer> scores = Traversals.getAll(mathScoreTraversal, user);
      assertThat(scores).containsExactly(95);

      // Modify value
      User updatedUser = Traversals.modify(mathScoreTraversal, x -> x + 5, user);
      assertThat(updatedUser.scores()).containsEntry("math", 100);
    }

    @Test
    @DisplayName("should handle missing key gracefully when composed with At")
    void composeWithAtMissingKey() {
      record User(Map<String, Integer> scores) {}

      Lens<User, Map<String, Integer>> scoresLens = Lens.of(User::scores, (u, s) -> new User(s));

      var mapAt = AtInstances.<String, Integer>mapAt();
      Prism<Optional<Integer>, Integer> intSomePrism = Prisms.some();

      Lens<User, Optional<Integer>> historyScoreLens = scoresLens.andThen(mapAt.at("history"));
      Traversal<User, Integer> historyScoreTraversal =
          historyScoreLens.asTraversal().andThen(intSomePrism.asTraversal());

      User user = new User(new HashMap<>(Map.of("math", 95)));

      // Should return empty list when key is missing
      List<Integer> scores = Traversals.getAll(historyScoreTraversal, user);
      assertThat(scores).isEmpty();

      // Modify should be no-op when missing
      User updatedUser = Traversals.modify(historyScoreTraversal, x -> x + 5, user);
      assertThat(updatedUser.scores()).containsExactlyEntriesOf(user.scores());
    }
  }

  @Nested
  @DisplayName("none() Prism for Optional")
  class NonePrismTests {

    private final Prism<Optional<String>, Unit> nonePrism = Prisms.none();

    @Test
    @DisplayName("getOptional() should match when Optional is empty")
    void getOptionalWhenEmpty() {
      Optional<String> source = Optional.empty();

      Optional<Unit> result = nonePrism.getOptional(source);

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("getOptional() should not match when Optional is present")
    void getOptionalWhenPresent() {
      Optional<String> source = Optional.of("hello");

      Optional<Unit> result = nonePrism.getOptional(source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("build() should create empty Optional")
    void buildCreatesEmpty() {
      Optional<String> result = nonePrism.build(Unit.INSTANCE);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition Patterns")
  class CompositionPatterns {

    @Test
    @DisplayName("should enable insert through composed lens")
    void insertThroughComposedLens() {
      record Config(Map<String, String> settings) {}

      Lens<Config, Map<String, String>> settingsLens =
          Lens.of(Config::settings, (c, s) -> new Config(s));

      var mapAt = AtInstances.<String, String>mapAt();

      Config config = new Config(new HashMap<>());

      // Insert a new setting
      Lens<Config, Optional<String>> debugLens = settingsLens.andThen(mapAt.at("debug"));
      Config updated = debugLens.set(Optional.of("true"), config);

      assertThat(updated.settings()).containsEntry("debug", "true");
    }

    @Test
    @DisplayName("should enable delete through composed lens")
    void deleteThroughComposedLens() {
      record Config(Map<String, String> settings) {}

      Lens<Config, Map<String, String>> settingsLens =
          Lens.of(Config::settings, (c, s) -> new Config(s));

      var mapAt = AtInstances.<String, String>mapAt();

      Config config = new Config(new HashMap<>(Map.of("debug", "true", "verbose", "false")));

      // Delete a setting
      Lens<Config, Optional<String>> debugLens = settingsLens.andThen(mapAt.at("debug"));
      Config updated = debugLens.set(Optional.empty(), config);

      assertThat(updated.settings()).doesNotContainKey("debug").containsEntry("verbose", "false");
    }

    @Test
    @DisplayName("should chain multiple At operations")
    void chainMultipleAtOperations() {
      record NestedConfig(Map<String, Map<String, Integer>> nested) {}

      Lens<NestedConfig, Map<String, Map<String, Integer>>> nestedLens =
          Lens.of(NestedConfig::nested, (c, n) -> new NestedConfig(n));

      var outerAt = AtInstances.<String, Map<String, Integer>>mapAt();
      var innerAt = AtInstances.<String, Integer>mapAt();

      NestedConfig config =
          new NestedConfig(new HashMap<>(Map.of("database", new HashMap<>(Map.of("port", 5432)))));

      // Navigate: NestedConfig -> Map -> Optional<Map> -> Map -> Optional<Integer>
      Lens<NestedConfig, Optional<Map<String, Integer>>> dbSettingsLens =
          nestedLens.andThen(outerAt.at("database"));

      // Get the database settings
      Optional<Map<String, Integer>> dbSettings = dbSettingsLens.get(config);
      assertThat(dbSettings).isPresent();

      // Get port from database settings
      Integer port = innerAt.get("port", dbSettings.get()).orElse(0);
      assertThat(port).isEqualTo(5432);

      // Update port
      Map<String, Integer> updatedDbSettings =
          innerAt.insertOrUpdate("port", 5433, dbSettings.get());
      NestedConfig updatedConfig = dbSettingsLens.set(Optional.of(updatedDbSettings), config);

      assertThat(updatedConfig.nested().get("database")).containsEntry("port", 5433);
    }
  }
}
