// Fixture for hkj-book/src/optics/list_decomposition.md
//
// The page decomposes lists with cons and snoc, and then reaches for a container and a team to
// show the optics composing. Those records are declared here; the snippet that shows the container
// shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.ListPrisms;

record Item(String sku) {}

@GenerateLenses
@GenerateFocus
record Container(String name, List<Item> items) {}

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
record Team(String name, List<Player> players) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final List<String> names = List.of("Alice", "Bob", "Charlie");

  static final List<Integer> numbers = List.of(1, 2, 3, 4, 5);

  static final Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

  static final Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();

  static final Container container = sample();

  static final Team team = sample();
}
