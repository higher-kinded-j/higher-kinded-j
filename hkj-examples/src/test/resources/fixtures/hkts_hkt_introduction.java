// Fixture for hkj-book/src/hkts/hkt_introduction.md
//
// The page argues, with javac, why Java needs an encoding for higher kinds. Each of its three
// fences is something javac refuses: a type parameter given type arguments, a shared interface
// that loses the container, and an instance whose container differs from the Kind's. The last
// calls the chapter's own totals on the order below.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook.totals;
import static org.higherkindedj.hkt.instances.Instances.functor;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;

class Fixture {

  static final Order order =
      FoundationsIntroBook.order(
          "00000000-0000-0000-0000-000000000001",
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))));
}
