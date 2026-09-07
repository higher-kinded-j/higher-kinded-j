// Fixture for hkj-book/src/transformers/transformer_capstone.md
//
// One order workflow - validate, reserve, charge - written three ways: threaded by hand, against
// MTL capabilities, and through the Path API. The async services and the Path-shaped steps are
// declared here; the domain is declared by the page's own opening snippet.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.WriterPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;

record AppConfig(String inventoryUrl, String paymentUrl, String apiKey) {}

record AuditEntry(String step, String detail) {}

record Order(String id, String sku, int quantity, double amount) {}

record Receipt(String orderId, String confirmationCode) {}

sealed interface DomainError {
  record InvalidOrder(String reason) implements DomainError {}

  record OutOfStock(String sku) implements DomainError {}

  record PaymentDeclined(String reason) implements DomainError {}
}

class Fixture {

  static final Order order = new Order("order-1", "sku-1", 2, 49.99);

  static final AppConfig prodConfig =
      new AppConfig("https://inventory", "https://payments", "api-key");

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  static final Monoid<List<AuditEntry>> listMonoid = Monoids.list();

  // --- The async services the by-hand version threads together ---

  static CompletableFuture<Either<DomainError, Unit>> reserveAsync(
      String inventoryUrl, String sku, int quantity) {
    return CompletableFuture.completedFuture(Either.right(Unit.INSTANCE));
  }

  static CompletableFuture<Either<DomainError, String>> chargeAsync(
      String paymentUrl, String apiKey, double amount) {
    return CompletableFuture.completedFuture(Either.right("confirmation-1"));
  }

  // --- The capability-polymorphic steps the MTL version composes ---

  // Instance methods, because the page declares `validate` itself in the next snippet.
  <F extends WitnessArity<TypeArity.Unary>> Kind<F, Unit> validate(
      Order order,
      MonadError<F, DomainError> errors,
      MonadWriter<F, List<AuditEntry>> audit) {
    return audit.tell(List.of(new AuditEntry("validate", "ok: " + order.id())));
  }

  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, Unit> reserve(
      Order order,
      String inventoryUrl,
      MonadError<F, DomainError> errors,
      MonadWriter<F, List<AuditEntry>> audit) {
    return audit.tell(List.of(new AuditEntry("reserve", "ok: sku=" + order.sku())));
  }

  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> charge(
      Order order,
      AppConfig config,
      MonadError<F, DomainError> errors,
      MonadWriter<F, List<AuditEntry>> audit) {
    return audit.map(_ -> "confirmation-1", audit.tell(List.of(new AuditEntry("charge", "ok"))));
  }

  // --- The Path-shaped steps ---

  static EitherPath<DomainError, Unit> reserve(Order order, String inventoryUrl) {
    return Path.right(Unit.INSTANCE);
  }

  static EitherPath<DomainError, String> charge(Order order, AppConfig config) {
    return Path.right("confirmation-1");
  }
}
