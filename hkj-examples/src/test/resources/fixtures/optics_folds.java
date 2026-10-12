// Fixture for hkj-book/src/optics/folds.md
//
// The page queries the chapter cast's Order and its lines, and a customer's order history beside
// them, then reaches for a project team and a connection's settings; its catalogue search
// declares the product it reads. The cast comes from its package, which hkj-examples' main sources
// put on the gate's classpath, with sample values from CastFixtures in its test sources. The rest
// is declared here with its generators, so the page's snippets name genuinely generated folds; a
// snippet that shows a model shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.optics.extensions.FoldExtensions.findMaybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.getAllMaybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.previewMaybe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.*;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

// A customer's past orders, beside the chapter's Order
@GenerateFolds
record OrderHistory(List<Order> orders) {}

record ProjectTeam(String name, TeamMember lead, List<TeamMember> members) {}

record TeamMember(String name, String email) {}

record ConnectionConfig(String host, Optional<String> port, Optional<String> database) {}

class Fixture {

  static final LineItem laptop = new LineItem("LAPTOP", 1, new BigDecimal("999.99"));

  static final LineItem mouse = new LineItem("MOUSE", 2, new BigDecimal("12.50"));

  static final LineItem desk = new LineItem("DESK", 1, new BigDecimal("350.00"));

  static final Order order = CastFixtures.order(List.of(laptop, mouse, desk));

  static final List<Order> orders =
      List.of(order, CastFixtures.order(List.of(mouse)), CastFixtures.order(List.of(desk)));

  static final OrderHistory history = new OrderHistory(orders);

  static final Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

  // The BigDecimal sum the page writes in Step 4, which later snippets reuse by name.
  static final Monoid<BigDecimal> sumMonoid =
      new Monoid<>() {
        @Override
        public BigDecimal empty() {
          return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal combine(BigDecimal a, BigDecimal b) {
          return a.add(b);
        }
      };

  // The line total the page writes in Step 4, which later snippets reuse by name.
  static final Function<LineItem, BigDecimal> lineTotal =
      line -> line.price().multiply(BigDecimal.valueOf(line.quantity()));

  static final List<Double> discounts = List.of(0.9, 0.95, 0.85);

  static final Fold<List<Double>, Double> discountsFold = Fold.of(d -> d);

  static final TeamMember member = new TeamMember("Alice", "alice@example.com");

  static final ProjectTeam team =
      new ProjectTeam("Core", member, List.of(new TeamMember("Bob", "bob@example.com")));

  static final Lens<TeamMember, String> nameLens =
      Lens.of(TeamMember::name, (e, v) -> new TeamMember(v, e.email()));

  static final Lens<TeamMember, String> emailLens =
      Lens.of(TeamMember::email, (e, v) -> new TeamMember(e.name(), v));

  static final Fold<ProjectTeam, String> teamNameFold = Fold.of(t -> List.of(t.name()));

  static final Fold<ProjectTeam, String> leadNameFold = Fold.of(t -> List.of(t.lead().name()));

  static final Fold<ProjectTeam, String> memberNamesFold =
      Fold.of(t -> t.members().stream().map(TeamMember::name).toList());

  static final Fold<ProjectTeam, String> leadEmail =
      Fold.of(t -> List.of(t.lead().email()));

  static final Fold<ProjectTeam, String> memberEmails =
      Fold.of(t -> t.members().stream().map(TeamMember::email).toList());

  static final ConnectionConfig config =
      new ConnectionConfig("localhost", Optional.of("8080"), Optional.empty());

  static final Lens<ConnectionConfig, String> hostLens =
      Lens.of(ConnectionConfig::host, (c, v) -> new ConnectionConfig(v, c.port(), c.database()));

  static final Prism<ConnectionConfig, String> portPrism =
      Prism.of(
          ConnectionConfig::port,
          p -> new ConnectionConfig("localhost", Optional.of(p), Optional.empty()));

  static final Affine<ConnectionConfig, String> dbAffine =
      Affine.of(
          ConnectionConfig::database,
          (c, v) -> new ConnectionConfig(c.host(), c.port(), Optional.of(v)));

  // A stand-in for the reporting the page hands its extracted lines to: snippets are compiled, not
  // run.
  static String generateReport(List<LineItem> lines) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
