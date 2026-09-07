// Fixture for hkj-book/src/functional/semigroup_and_monoid.md
//
// The page works through every Monoids factory with a small example each. The domain those
// examples reach for - a config loader, an order pipeline, a log source - is declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

record Config(String source) {}

record Order(String id, String email, BigDecimal total) {

  Order withEmail(String email) {
    return new Order(id, email, total);
  }

  Order withTotal(BigDecimal total) {
    return new Order(id, email, total);
  }

  Order withWarehouse(String warehouse) {
    return this;
  }
}

class Fixture {

  static final BigDecimal DISCOUNT = new BigDecimal("0.9");

  static final Order order = new Order("order-1", "Alice@Example.COM", new BigDecimal("100.00"));

  static final Order orderA = order;

  static final Order orderB = order;

  static final Update<Order> normalise = o -> o.withEmail(o.email().toLowerCase());

  static final Update<Order> applyDiscount = o -> o.withTotal(o.total().multiply(DISCOUNT));

  static final Update<Order> assignWarehouse = o -> o.withWarehouse("LDN");

  static List<String> loadLogMessages() {
    return List.of("started", "finished");
  }

  static void performExpensiveOperation(String value) {}

  static Optional<Config> loadFromEnvironment() {
    return Optional.empty();
  }

  static Optional<Config> loadFromUserHome() {
    return Optional.of(new Config("user"));
  }

  static Optional<Config> loadFromWorkingDir() {
    return Optional.empty();
  }

  static Optional<Config> loadDefaultConfig() {
    return Optional.of(new Config("default"));
  }
}
