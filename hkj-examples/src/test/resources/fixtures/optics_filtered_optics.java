// Fixture for hkj-book/src/optics/filtered_optics.md
//
// The page narrows one platform of users and billing accounts with `filtered` and `filterBy`. The
// model is declared here with the generators the snippets' companions come from; the snippet that
// shows it shadows this copy. An account holds the chapter cast's own Customer, which hkj-examples'
// main sources put on the gate's classpath.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.higherkindedj.example.book.optics.cast.*;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

enum SubscriptionTier {
  FREE,
  BASIC,
  PREMIUM,
  ENTERPRISE
}

@GenerateLenses
record User(String name, boolean active, int score, SubscriptionTier tier) {

  User grantBonus() {
    return new User(name, active, score + 100, tier);
  }
}

@GenerateLenses
@GenerateFolds
record Invoice(String id, BigDecimal amount, boolean overdue) {}

// A customer's billing account, beside the cast's Customer
@GenerateLenses
@GenerateFolds
record BillingAccount(Customer customer, List<Invoice> invoices, SubscriptionTier tier) {}

@GenerateLenses
@GenerateFolds
@GenerateTraversals
record Platform(List<User> users, List<BillingAccount> accounts) {}

class Fixture {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, and a snippet
   * that shows a model shadows the one above, so naming a constructor here would tie the fixture
   * to one shape of it.
   */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final User user = sample();

  static final List<User> users = List.of();

  static final BillingAccount account = sample();

  static final List<BillingAccount> accounts = List.of();

  static final Platform platform = sample();

  static final List<Platform> platforms = List.of();

  static final Traversal<List<User>, User> userTraversal = Traversals.forList();
}
