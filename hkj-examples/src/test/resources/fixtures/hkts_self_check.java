// Fixture for hkj-book/src/hkts/self_check.md
//
// The page's answers include their code from SelfCheckBook. What is gated here is Checkpoint 1's
// call to map on a Kind, which javac refuses, and Checkpoint 6's two declared types, which it
// accepts. This fixture imports what they name, and declares the values they use.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

class Fixture {

  static final Order order = null;

  static final Monad<EitherKind.Witness<String>> monad = Instances.monad(either());

  static final Kind<EitherKind.Witness<String>, Order> found = null;

  static final Function<Order, Kind<EitherKind.Witness<String>, Customer>> findCustomer =
      order -> null;
}
