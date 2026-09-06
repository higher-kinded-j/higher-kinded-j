// Fixture for hkj-book/src/transformers/migration_cookbook.md
//
// Five migrations, each shown three ways: the nested original, the Path, and the transformer. The
// three ways need the same call in three shapes, so each is named for its shape: `*Future` for the
// plain future the original threads by hand, the bare name for the resolved value a Path composes,
// and `*Async` for the Kind a transformer wraps.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.effect.WriterPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.higherkindedj.hkt.writer_t.WriterT;

record OrderData(String id) {}

record ValidatedOrder(String id) {}

record Inventory(String orderId) {}

record Receipt(String orderId) {}

sealed interface DomainError {
  record Rejected(String reason) implements DomainError {}
}

record User(String id) {}

record Profile(String userId) {}

record UserPreferences(String theme) {}

record AppConfig(String apiKey) {}

record ServiceData(String rawData) {}

record ProcessedData(String info) {}

record AuditEntry(String detail) {}

class Fixture {

  static final OrderData data = new OrderData("order-1");

  static final String userId = "user-1";

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  static final Monoid<List<AuditEntry>> auditMonoid = Monoids.list();

  // --- 1. Async + typed error ---

  static CompletableFuture<Either<DomainError, ValidatedOrder>> validateOrderFuture(
      OrderData data) {
    return CompletableFuture.completedFuture(Either.right(new ValidatedOrder(data.id())));
  }

  static CompletableFuture<Either<DomainError, Inventory>> checkInventoryFuture(
      ValidatedOrder order) {
    return CompletableFuture.completedFuture(Either.right(new Inventory(order.id())));
  }

  static CompletableFuture<Either<DomainError, Receipt>> processPaymentFuture(
      Inventory inventory) {
    return CompletableFuture.completedFuture(Either.right(new Receipt(inventory.orderId())));
  }

  static Either<DomainError, ValidatedOrder> validateOrder(OrderData data) {
    return Either.right(new ValidatedOrder(data.id()));
  }

  static Either<DomainError, Inventory> checkInventory(ValidatedOrder order) {
    return Either.right(new Inventory(order.id()));
  }

  static Either<DomainError, Receipt> processPayment(Inventory inventory) {
    return Either.right(new Receipt(inventory.orderId()));
  }

  static Kind<CompletableFutureKind.Witness, Either<DomainError, ValidatedOrder>>
      validateOrderAsync(OrderData data) {
    return FUTURE.widen(validateOrderFuture(data));
  }

  static Kind<CompletableFutureKind.Witness, Either<DomainError, Inventory>> checkInventoryAsync(
      ValidatedOrder order) {
    return FUTURE.widen(checkInventoryFuture(order));
  }

  static Kind<CompletableFutureKind.Witness, Either<DomainError, Receipt>> processPaymentAsync(
      Inventory inventory) {
    return FUTURE.widen(processPaymentFuture(inventory));
  }

  // --- 2. Async + absence ---

  static CompletableFuture<Optional<User>> fetchUserFuture(String userId) {
    return CompletableFuture.completedFuture(Optional.of(new User(userId)));
  }

  static CompletableFuture<Optional<Profile>> fetchProfileFuture(String userId) {
    return CompletableFuture.completedFuture(Optional.of(new Profile(userId)));
  }

  static CompletableFuture<Optional<UserPreferences>> fetchPrefsFuture(String userId) {
    return CompletableFuture.completedFuture(Optional.of(new UserPreferences("dark")));
  }

  static Optional<User> lookupUser(String userId) {
    return Optional.of(new User(userId));
  }

  static Optional<Profile> lookupProfile(String userId) {
    return Optional.of(new Profile(userId));
  }

  static Optional<UserPreferences> lookupPrefs(String userId) {
    return Optional.of(new UserPreferences("dark"));
  }

  static Kind<CompletableFutureKind.Witness, Optional<User>> fetchUserAsync(String userId) {
    return FUTURE.widen(fetchUserFuture(userId));
  }

  static Kind<CompletableFutureKind.Witness, Optional<Profile>> fetchProfileAsync(String userId) {
    return FUTURE.widen(fetchProfileFuture(userId));
  }

  static Kind<CompletableFutureKind.Witness, Optional<UserPreferences>> fetchPrefsAsync(
      String userId) {
    return FUTURE.widen(fetchPrefsFuture(userId));
  }

  // --- 3. Async + configuration ---

  static ServiceData callApi(String apiKey, String itemId) {
    return new ServiceData("raw:" + itemId);
  }

  static ProcessedData transform(ServiceData data, AppConfig config) {
    return new ProcessedData("processed:" + data.rawData());
  }

  // --- 5. Stateful pipeline ---

  static List<Integer> prepend(List<Integer> stack, Integer value) {
    var newStack = new LinkedList<>(stack);
    newStack.add(0, value);
    return newStack;
  }

  static StateTuple<List<Integer>, Unit> push(List<Integer> stack, Integer value) {
    return StateTuple.of(prepend(stack, value), Unit.INSTANCE);
  }

  static Optional<StateTuple<List<Integer>, Integer>> pop(List<Integer> stack) {
    if (stack.isEmpty()) {
      return Optional.empty();
    }
    var newStack = new LinkedList<>(stack);
    Integer value = newStack.remove(0);
    return Optional.of(StateTuple.of(newStack, value));
  }

  static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Unit> push(Integer value) {
    return STATE_T.stateT(
        stack -> OPTIONAL.widen(Optional.of(StateTuple.of(prepend(stack, value), Unit.INSTANCE))),
        Instances.monadError(optional()));
  }

  static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Integer> pop() {
    return STATE_T.stateT(
        stack ->
            stack.isEmpty()
                ? OPTIONAL.widen(Optional.empty())
                : OPTIONAL.widen(
                    Optional.of(
                        StateTuple.of(
                            new LinkedList<>(stack.subList(1, stack.size())), stack.get(0)))),
        Instances.monadError(optional()));
  }
}
