// Fixture for hkj-book/src/optics/filtered_optics.md
//
// The page narrows one platform of users and customers with `filtered` and `filterBy`. The model
// is declared here with the generators the snippets' companions come from; the snippet that shows
// it shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.toList;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
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
record Invoice(String id, double amount, boolean overdue) {}

@GenerateLenses
@GenerateFolds
record Customer(String name, List<Invoice> invoices, SubscriptionTier tier) {}

@GenerateLenses
@GenerateFolds
@GenerateTraversals
record Platform(List<User> users, List<Customer> customers) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final User user = sample();

  static final List<User> users = List.of();

  static final Customer customer = sample();

  static final List<Customer> customers = List.of();

  static final Platform platform = sample();

  static final List<Platform> platforms = List.of();

  static final Traversal<List<User>, User> userTraversal = Traversals.forList();
}
