// Fixture for hkj-book/src/optics/indexed_access.md
//
// The page contrasts `At`, which inserts and deletes, with `Ixed`, which only reaches what is
// already there, and reaches for a settings record to show them composing. The record is declared
// here; a snippet that shows it shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.Ixed;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.ixed.IxedInstances;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

record Config(Map<String, String> settings) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Config config = sample();

  static final Lens<Config, Map<String, String>> settingsLens = sample();

  static final At<Map<String, String>, String, String> settingsAt = AtInstances.mapAt();

  static final At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();

  static final Map<String, Integer> scores = new HashMap<>();

  static final Map<String, Integer> withBob = scores;

  static final Map<String, Integer> updatedScores = scores;

  static final Map<String, Integer> bonusScores = scores;

  static final Map<String, Integer> afterRemove = scores;

  static final Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

  static final Map<String, Integer> ports = new HashMap<>();

  static final At<Map<String, Integer>, String, Integer> at = AtInstances.mapAt();

  static final Map<String, Integer> map = new HashMap<>();

  static final Map<String, Integer> original = map;

  static final List<String> items = new ArrayList<>(List.of("a", "b", "c", "d"));
}
