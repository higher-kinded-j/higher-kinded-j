// Fixture for hkj-book/src/optics/focus_containers.md
//
// The page shows what the processor emits for each field shape, so the fixture
// declares one record per shape and the snippets assert the generated path
// types by assigning them.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

@GenerateLenses
@GenerateFocus
record Skill(String name, int level) {}

@GenerateLenses
@GenerateFocus
record Employee(
    String name,
    int age,
    Optional<String> email,
    List<Skill> skills,
    Either<String, Integer> timeout,
    Map<String, Integer> scores) {}

@GenerateLenses
@GenerateFocus(widenCollections = true)
record WidenedEmployee(String name, Map<String, Integer> scores) {}

@GenerateLenses
@GenerateFocus
record Position(String ticker, double weight) {}

@GenerateLenses
@GenerateFocus
record AssetClass(String className, ImmutableList<Position> positions) {}

class Fixture {
  static final Employee employee =
      new Employee(
          "Alice",
          41,
          Optional.of("alice@acme.test"),
          List.of(new Skill("Java", 9)),
          Either.right(30),
          Map.of("q1", 90));

  static final AssetClass assetClass =
      new AssetClass("Equities", Lists.immutable.of(new Position("ACME", 0.4)));
}
