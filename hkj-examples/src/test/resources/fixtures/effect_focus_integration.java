// Fixture for hkj-book/src/effect/focus_integration.md
//
// The page bridges the optics side and the effect side over one user/company graph: lifting a
// FocusPath into each Path type, and focusing a Path through an optic. The records and the
// ready-made paths live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.PathOps;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPaths;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

@GenerateLenses
@GenerateFocus
record User(String name, Optional<String> email) {}

@GenerateLenses
@GenerateFocus
record Company(String name, List<User> employees) {}

@GenerateLenses
@GenerateFocus
record Org(String name, List<Department> departments) {}

record Error(String message) {

  static Error missingEmail() {
    return new Error("missing email");
  }
}

record UserId(String value) {}

record OrderId(String value) {}

record UpdateRequest(String email) {}

record SaveResult(String id) {}

@GenerateLenses
@GenerateFocus
record Address(String city, String postcode) {}

@GenerateLenses
@GenerateFocus
record Customer(String name, Address address) {}

@GenerateLenses
@GenerateFocus
record Order(String id, Customer customer) {}

@GenerateLenses
@GenerateFocus
record Employee(String name, Optional<String> email) {}

@GenerateLenses
@GenerateFocus
record Department(String name, Optional<Employee> manager) {}

@GenerateLenses
@GenerateFocus
record Profile(String handle, Optional<String> email) {}

@GenerateLenses
@GenerateFocus
record Account(String id, Profile profile) {}

final class MissingEmailException extends RuntimeException {

  MissingEmailException() {
    super("No email");
  }
}

class Fixture {

  static final String userId = "u-1";

  static final OrderService orderService = new OrderService();

  static final AccountService userService = new AccountService();

  static EitherPath<Error, String> validateEmail(String email) {
    return Path.right(email);
  }

  static EitherPath<Error, Account> applyUpdate(UpdateRequest request, String email) {
    return Path.right(new Account("a-1", new Profile("ada", Optional.of(email))));
  }

  static final class OrderService {

    EitherPath<Error, Order> findById(OrderId id) {
      return Path.right(new Order(id.value(), new Customer("Ada", new Address("London", "N1"))));
    }
  }

  static final class AccountService {

    EitherPath<Error, Account> findById(UserId id) {
      return Path.right(new Account(id.value(), new Profile("ada", Optional.of("a@b.test"))));
    }

    EitherPath<Error, SaveResult> save(Account account) {
      return Path.right(new SaveResult(account.id()));
    }
  }

  static final User alice = new User("Alice", Optional.of("alice@example.com"));

  static final User bob = new User("Bob", Optional.empty());

  static final User charlie = new User("Charlie", Optional.of("charlie@example.com"));

  static final EitherPath<Error, User> userResult = Path.either(Either.right(alice));

  static final TryPath<User> userTryPath = Path.success(alice);

  static final MaybePath<User> userMaybePath = Path.just(alice);

  static EitherPath<Error, User> fetchUser(String id) {
    return Path.either(Either.right(alice));
  }
}
