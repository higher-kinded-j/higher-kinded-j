// Fixture for hkj-book/src/resilience/ch_intro.md
//
// The page's payoff snippet guards one order-pipeline step on the typed railway; the
// domain types and the step itself are elided there, so they live here.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is
// spliced into. That is why spotless excludes src/test/resources (see build.gradle.kts).

import java.time.Duration;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.resilience.RetryPolicy;

record Order(String id) {}

record Reservation(String id) {}

sealed interface OrderError {
  record SystemError(String why) implements OrderError {
    static OrderError timeout(String what, Duration budget) {
      return new SystemError(what + " timed out after " + budget);
    }
  }
}

class Fixture {
  static final Order order = new Order("o-1");

  static VResultPath<OrderError, Reservation> reserveInventory(Order order) {
    return null;
  }
}
