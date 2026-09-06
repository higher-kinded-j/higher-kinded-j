// Fixture for hkj-book/src/functional/monad.md
//
// The page chains user -> account -> balance, then combines independent lookups. The lookups the
// snippets declare with elided bodies are declared here for real, so a snippet that shows one
// signature still has the rest to call.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;

record User(int id, String name) {}

record Account(int userId, String accountId) {}

record Order(int userId, String item) {}

record UserOrder(User user, Order order) {}

record Product(int id, String name, double price) {}

record Inventory(int productId, int quantity) {}

class Fixture {

  static final Monad<OptionalKind.Witness> monad = Instances.monadError(optional());

  static Optional<User> findUserPlain(int id) {
    return id == 1 ? Optional.of(new User(1, "Alice")) : Optional.empty();
  }

  static Optional<Account> findAccountPlain(User user) {
    return Optional.of(new Account(user.id(), "acc-123"));
  }

  static Optional<Double> getBalancePlain(Account account) {
    return Optional.of(100.0);
  }

  Kind<OptionalKind.Witness, User> findUser(int id) {
    return OPTIONAL.widen(findUserPlain(id));
  }

  Kind<OptionalKind.Witness, Account> findAccount(User user) {
    return OPTIONAL.widen(findAccountPlain(user));
  }

  Kind<OptionalKind.Witness, Double> getBalance(Account account) {
    return OPTIONAL.widen(getBalancePlain(account));
  }

  Kind<OptionalKind.Witness, Order> findOrder(int orderId) {
    return OPTIONAL.widen(Optional.of(new Order(1, "widget")));
  }

  Kind<OptionalKind.Witness, Product> findProduct(int id) {
    return OPTIONAL.widen(Optional.of(new Product(id, "Widget", 9.99)));
  }

  Kind<OptionalKind.Witness, Inventory> checkInventory(int productId) {
    return OPTIONAL.widen(Optional.of(new Inventory(productId, 5)));
  }

  Kind<OptionalKind.Witness, UserOrder> validateAndCombine(User user, Order order) {
    return OPTIONAL.widen(Optional.of(new UserOrder(user, order)));
  }

  Kind<OptionalKind.Witness, String> validateAndProcess(User user, Order order) {
    return OPTIONAL.widen(Optional.of(user.name() + " ordered " + order.item()));
  }
}
