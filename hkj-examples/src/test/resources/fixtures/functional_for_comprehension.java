// Fixture for hkj-book/src/functional/for_comprehension.md
//
// The page's code is included from compiled examples. Its verified snippets are the import list
// those examples use, and chains started from a plain Monad that javac refuses a `when` on, so
// this fixture declares the orders and the found order those chains start from.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.MaybeKind;

class Fixture {

  static final Kind<ListKind.Witness, Order> orders = LIST.widen(List.of());

  static final Kind<MaybeKind.Witness, Order> found = MAYBE.nothing();
}
