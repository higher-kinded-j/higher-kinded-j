// Fixture for hkj-book/src/monads/lazy_monad.md
//
// The page builds one dashboard out of three slow lookups, and measures when each of them runs.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.lazy;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.lazy.LazyKind;

record Profile(String name) {}

record Recommendations(String body) {}

record Analytics(String body) {}

class Fixture {

  static final String userId = "u-1";

  static String fetchUserProfile(String userId) {
    return "profile";
  }

  static String fetchRecommendations(String userId) {
    return "recommendations";
  }

  static String fetchAnalytics(String userId) {
    return "analytics";
  }

  static String summarize(String profile, String recommendations, String analytics) {
    return profile + recommendations + analytics;
  }
}
