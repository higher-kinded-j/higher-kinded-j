// Fixture for hkj-book/src/optics/each_typeclass.md
//
// The page catalogues the Each instances and then walks a depot's bays, a list of orders, products
// and a user's projects through them. The orders are the chapter cast's Order, imported from its
// package; the other models are declared here, and a snippet that shows one shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static java.util.stream.Collectors.toMap;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.EachIndexed;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.extensions.EachExtensions;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;

// The page's stand-ins for "whatever the left and right of an Either are here". Declared so the
// catalogue snippet reads as it is written.
record AppError(String message) {}

record Value(String value) {}

// A depot's bays, each holding its stock by SKU, for the Focus DSL section.
record Depot(String name, List<Bay> bays) {}

record Bay(String label, Map<String, Integer> stock) {}

record Product(String name, BigDecimal price) {

  Product withPrice(BigDecimal newPrice) {
    return new Product(name, newPrice);
  }
}

// Containers of the reader's own, for the sections that wrap a traversal they already have.
record MyContainer<A>(List<A> items) {}

record MyList<A>(List<A> items) {}

record Task(String title, boolean reviewed) {

  Task markReviewed() {
    return new Task(title, true);
  }
}

record Project(String name, Map<String, Task> tasks) {}

record User(String name, List<Project> projects) {}

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

  static final Depot depot = sample();

  static final List<Order> orders = List.of();

  static final List<Product> products = List.of();

  static final Lens<User, List<Project>> userProjectsLens = sample();

  static final Lens<Project, Map<String, Task>> projectTasksLens = sample();

  static Kind<org.higherkindedj.hkt.validated.ValidatedKind.Witness<List<String>>, Order>
      validateOrder(Order order) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
