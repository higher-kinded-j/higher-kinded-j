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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

@GenerateLenses
@GenerateFocus
record Skill(String name, int proficiency) {}

@GenerateLenses
@GenerateFocus
record Candidate(
    String name,
    int age,
    Optional<String> email,
    List<Skill> skills,
    Either<String, Integer> timeout,
    Map<String, Integer> scores) {}

@GenerateLenses
@GenerateFocus
record Vacancy(String name, Set<Skill> skills, Collection<String> tags) {}

@GenerateLenses
@GenerateFocus(widenCollections = true)
record WidenedCandidate(String name, Map<String, Integer> scores, String[] tags, int[] levels) {}

record Widget(String name) {}

// A generic element, and a list of it whose element carries a wildcard, for A wildcard inside the
// element
@GenerateFocus
record Box<T>(T value) {}

@GenerateFocus
record Crate(List<Box<? extends Widget>> boxes) {}

@GenerateLenses
@GenerateFocus
record Holding(String ticker, double weight) {}

@GenerateLenses
@GenerateFocus
record Allocation(String className, ImmutableList<Holding> holdings) {}

class Fixture {
  static final Candidate candidate =
      new Candidate(
          "Alice",
          41,
          Optional.of("alice@acme.test"),
          List.of(new Skill("Java", 9)),
          Either.right(30),
          Map.of("q1", 90));

  static final Vacancy vacancy =
      new Vacancy("Platform", Set.of(new Skill("Java", 9)), Set.of("backend", "jvm"));

  static final Allocation allocation =
      new Allocation("Equities", Lists.immutable.of(new Holding("ACME", 0.4)));
}
