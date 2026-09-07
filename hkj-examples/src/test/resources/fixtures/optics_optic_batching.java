// Fixture for hkj-book/src/optics/optic_batching.md
//
// The page runs an optic under the batching applicative and then routes, guards and partitions the
// result. The identifiers it fetches and the resolvers it hands the runner are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Optic;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fetch.BatchLoader;
import org.higherkindedj.optics.fetch.Fetch;
import org.higherkindedj.optics.fetch.FetchApplicative;
import org.higherkindedj.optics.fetch.FetchKind;
import org.higherkindedj.optics.fetch.FetchOptics;
import org.higherkindedj.optics.fetch.SafeFetch;
import org.higherkindedj.optics.fetch.SourceRouter;
import org.higherkindedj.optics.focus.FocusPaths;

record UserId(String value) {}

record User(UserId id, String name) {}

record Team(String name, List<UserId> memberIds) {}

record EnrichedTeam(String name, List<User> members) {}

class Backend {

  Map<Integer, Integer> loadAll(Set<Integer> keys) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Backend backend = new Backend();

  static final Team team = sample();

  static final Function<Set<UserId>, Map<UserId, User>> userResolver = sample();

  static final Function<Set<UserId>, Map<UserId, User>> failingResolver = sample();

  static final Fetch<UserId, User, User> program = sample();
}
