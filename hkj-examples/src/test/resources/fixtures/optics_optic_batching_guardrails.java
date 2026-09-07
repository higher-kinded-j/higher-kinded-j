// Fixture for hkj-book/src/optics/optic_batching_guardrails.md
//
// The page inspects a batching program's plan and then puts guards around the run. The program,
// the backend it dispatches to and the identifiers it fetches are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.fetch.Fetch;
import org.higherkindedj.optics.fetch.Guard;
import org.higherkindedj.optics.fetch.Guards;
import org.higherkindedj.optics.fetch.Plan;
import org.higherkindedj.optics.fetch.Plans;
import org.higherkindedj.optics.fetch.SafeFetch;

record UserId(String value) {}

record User(UserId id, String name) {}

record Team(String name, List<UserId> memberIds) {}

class Backend {

  Map<UserId, User> loadAll(Set<UserId> keys) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

// The reader's own logger, and the HTTP responses the guarded run is folded into.
class Log {

  void info(String message, Object... arguments) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

record Response(int status, Object body) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Log log = new Log();

  static final Backend backend = new Backend();

  static final Fetch<UserId, User, Team> program = sample();

  static final Guard<UserId> composed = sample();

  static Response badRequest(String message) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Response ok(Team value) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
