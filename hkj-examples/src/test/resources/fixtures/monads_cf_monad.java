// Fixture for hkj-book/src/monads/cf_monad.md
//
// One asynchronous user lookup, and the subscription and discount that follow it.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;

record User(String id, String subscriptionId) {}

record Subscription(String id) {}

record Order(String id) {}

record Discount(double amount) {

  static Discount none() {
    return new Discount(0);
  }
}

final class UserService {

  CompletableFuture<User> findUser(String id) {
    return CompletableFuture.completedFuture(new User(id, "s-1"));
  }
}

final class SubscriptionService {

  CompletableFuture<Subscription> getSubscription(String id) {
    return CompletableFuture.completedFuture(new Subscription(id));
  }
}

final class PricingService {

  CompletableFuture<Discount> calculateDiscount(User user, Subscription subscription) {
    return CompletableFuture.completedFuture(Discount.none());
  }
}

class Fixture {

  static final String id = "u-1";

  static final String userId = "u-1";

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  static final UserService userService = new UserService();

  static final SubscriptionService subscriptionService = new SubscriptionService();

  static final PricingService pricingService = new PricingService();

  static CompletableFuture<User> findUser(String id) {
    return CompletableFuture.completedFuture(new User(id, "s-1"));
  }

  static CompletableFuture<Order> createOrder(User user) {
    return CompletableFuture.completedFuture(new Order("o-1"));
  }
}
