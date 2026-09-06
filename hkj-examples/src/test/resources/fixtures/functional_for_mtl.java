// Fixture for hkj-book/src/functional/for_mtl.md
//
// The page writes three capability-polymorphic comprehensions, then bridges For into ForState. The
// state records, the monads and the lenses are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.Lens;

record AppConfig(String dbUrl, int maxRetries) {}

record Counter(int count, int total) {}

record OrderContext(
    String user, String orderId, boolean validated, String confirmationId, Address address,
    int totalCents) {}

record Address(String city) {}

record Dashboard(String user, int count, boolean ready) {}

class Fixture {

  static final Lens<Dashboard, Boolean> readyLens =
      Lens.of(Dashboard::ready, (d, v) -> new Dashboard(d.user(), d.count(), v));

  static final Lens<Dashboard, Integer> countLens =
      Lens.of(Dashboard::count, (d, v) -> new Dashboard(d.user(), v, d.ready()));


  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  // A MonadZero, because the toState example relies on `when()` being available.
  static final MonadZero<MaybeKind.Witness> maybeMonad = Instances.monadZero(maybe());

  static final MonadZero<MaybeKind.Witness> monad = maybeMonad;

  static final OrderContext initialContext =
      new OrderContext("Alice", "ORD-1", false, "", new Address("Springfield"), 1000);

  static final Lens<OrderContext, Boolean> validatedLens =
      Lens.of(
          OrderContext::validated,
          (c, v) ->
              new OrderContext(
                  c.user(), c.orderId(), v, c.confirmationId(), c.address(), c.totalCents()));

  static final Lens<OrderContext, String> confirmationLens =
      Lens.of(
          OrderContext::confirmationId,
          (c, v) ->
              new OrderContext(
                  c.user(), c.orderId(), c.validated(), v, c.address(), c.totalCents()));

  static final Lens<OrderContext, Address> addressLens =
      Lens.of(
          OrderContext::address,
          (c, v) ->
              new OrderContext(
                  c.user(), c.orderId(), c.validated(), c.confirmationId(), v, c.totalCents()));

  static final Lens<Address, String> cityLens =
      Lens.of(Address::city, (_, v) -> new Address(v));

  static Kind<MaybeKind.Witness, Boolean> validateOrder(String orderId) {
    return MAYBE.just(true);
  }

  static Kind<MaybeKind.Witness, String> processPayment(OrderContext ctx) {
    return MAYBE.just("CONF-123");
  }

  static String buildReceipt(String user, String confirmationId) {
    return user + ":" + confirmationId;
  }
}
