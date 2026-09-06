// Fixture for hkj-book/src/optics/each_typeclass.md
//
// The page catalogues the Each instances and then walks a user's orders, products and projects
// through them. Those models are declared here; a snippet that shows one shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.toMap;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
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
// catalogue snippet reads as it is written; `Error` shadows java.lang.Error inside this unit.
record Error(String message) {}

record Value(String value) {}

record Order(String id, Map<String, Integer> items) {}

record Product(String name, double price) {

  Product withPrice(double newPrice) {
    return new Product(name, newPrice);
  }
}

record Task(String title, boolean reviewed) {

  Task markReviewed() {
    return new Task(title, true);
  }
}

record Project(String name, Map<String, Task> tasks) {}

record User(String name, List<Order> orders) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final User user = sample();

  static final List<Order> orders = List.of();

  static final List<Product> products = List.of();

  static final Lens<User, List<Project>> userProjectsLens = sample();

  static final Lens<Project, Map<String, Task>> projectTasksLens = sample();

  static Kind<org.higherkindedj.hkt.validated.ValidatedKind.Witness<List<String>>, Order>
      validateOrder(Order order) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
