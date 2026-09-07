// Fixture for hkj-book/src/tutorials/optics/focus_dsl_journey.md
//
// The journey builds one path through a company and then shows the same DSL over an Either field,
// a Kind field and a map. The models and the optics it names are declared here; a snippet that
// shows a model shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

record Error(String message) {}

record Manager(String name, String email, int salary) {}

record Dept(String name, Manager manager) {}

record Company(String name, List<Dept> departments) {}

record Root(Company company) {}

record Team(String name, List<Manager> members) {}

record Role(String name) {}

record User(String name, Kind<ListKind.Witness, Role> roles) {}

record PricingError(String reason) {}

record MarketPrice(double amount) {}

@GenerateFocus(generateNavigators = true)
record Position(String ticker, Either<PricingError, MarketPrice> livePrice) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Root root = sample();

  static final Company company = sample();

  static final Team team = sample();

  static final User user = sample();

  static final Lens<Root, Company> companyLens = sample();

  static final Lens<Company, List<Dept>> departmentsLens = sample();

  static final Lens<Dept, Manager> managerLens = sample();

  static final Lens<Manager, String> emailLens = sample();

  static final Lens<User, Kind<ListKind.Witness, Role>> userRolesLens = sample();

  static final TraversalPath<User, String> path = sample();

  static final TraversalPath<Company, Integer> salaryPath = sample();

  static final TraversalPath<Team, String> namePath = sample();

  static final AffinePath<Root, String> affinePath = sample();

  static final Root source = root;

  static final Position position = sample();

  static Kind<EitherKind.Witness<Error>, String> validateAndTransform(String value) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Kind<CompletableFutureKind.Witness, String> fetchAndUpdate(String value) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
