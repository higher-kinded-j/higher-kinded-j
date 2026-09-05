// Fixture for hkj-book/src/effect/context_vs_config.md
//
// The page contrasts ConfigContext (explicit, passed at the edge) with Context over a ScopedValue
// (implicit, inherited by forked virtual threads) on one database lookup and one fan-out. The
// domain behind both lives here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

record DatabaseConfig(String connectionString) {}

record User(String id, String name) {}

record RequestInfo(String traceId) {}

record PartialResult(String value) {}

record Result(String value) {

  static Result combine(PartialResult first, PartialResult second) {
    return new Result(first.value() + second.value());
  }
}

class Fixture {

  static final String userId = "u-1";

  static final DatabaseConfig productionConfig = new DatabaseConfig("jdbc:postgresql://prod/app");

  static final UserRepository userRepository = new UserRepository();

  static PartialResult fetchData() {
    return new PartialResult("data");
  }

  static PartialResult fetchMoreData() {
    return new PartialResult("more");
  }

  static PartialResult fetchData(RequestInfo info) {
    return new PartialResult("data for " + info.traceId());
  }

  static PartialResult fetchMoreData(RequestInfo info) {
    return new PartialResult("more for " + info.traceId());
  }

  static final class UserRepository {

    User findById(String id, String connectionString) {
      return new User(id, "Ada");
    }
  }
}
