// Fixture for .claude/skills/hkj-arch/reference/architecture-examples.md
//
// The page is a pair of before/after refactorings, so it shows the code that *moves* and elides the
// domain it moves through. Supplying that domain here compiles the "after" halves -- the pure core
// and the shell -- against the real library. It already caught `Semigroup.listSemigroup()` (no such
// method; it is `Semigroups.list()`), a `Path.either(Validated)` call (Path.either takes an Either),
// and a `Path.maybe(Optional)` that silently produced a MaybePath<Optional<User>>.
//
// `validateForm` and `validateEmail` are static here because the page's `validateForm` snippet is a
// static method: a static method may hide a static one, but it may not hide an instance one.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** The reader's own money type. */
record Money(BigDecimal amount) {

  static final Money ZERO = Money.of(0);

  static Money of(double amount) {
    return new Money(BigDecimal.valueOf(amount));
  }

  Money add(Money other) {
    return new Money(amount.add(other.amount));
  }

  Money multiply(double factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)));
  }
}

record Item(String id, Money price, int quantity) {}

record OrderRequest(List<Item> items) {}

/** Example 1 asks whether the user is a gold member; Example 2 constructs the same record. */
record User(String id, String name, String email) {
  boolean isGoldMember() {
    return "gold".equals(id);
  }
}

record ValidatedOrder(User user, OrderRequest request, Money total) {}

record OrderResult(String transactionId, Money total) {}

/** The inventory snapshot the pure core is handed, rather than the service it would have called. */
record InventoryStatus(List<String> unavailableIds) {

  static InventoryStatus allAvailable() {
    return new InventoryStatus(List.of());
  }

  boolean isAvailable(String id, int quantity) {
    return !unavailableIds.contains(id);
  }
}

sealed interface OrderError {
  record NotFound(String userId) implements OrderError {}

  record InsufficientStock(List<Item> items) implements OrderError {}

  record PaymentFailed(Throwable cause) implements OrderError {}
}

record RegistrationForm(String name, String email) {}

sealed interface RegistrationError {
  record ValidationFailed(List<String> reasons) implements RegistrationError {}

  record DuplicateEmail(String email) implements RegistrationError {}
}

/** The "before" code throws these; they are the reader's own. */
class ValidationException extends RuntimeException {
  ValidationException(String message) {
    super(message);
  }
}

class DuplicateException extends RuntimeException {
  DuplicateException(String message) {
    super(message);
  }
}

interface UserRepository {
  Optional<User> findById(String id);

  boolean existsByEmail(String email);

  void save(User user);
}

interface EmailService {
  void sendWelcome(User user);
}

/** The collaborators the shell injects, and the pure core it delegates to. */
interface InventoryService {
  InventoryStatus checkStatus(List<Item> items);
}

record PaymentResult(String transactionId) {}

interface PaymentGateway {
  PaymentResult charge(User user, Money amount);
}

interface NotificationService {
  void sendConfirmation(OrderResult result);
}

/** Declared by the page's "Functional Core" snippet; the shell snippet calls it. */
class OrderRules {
  static EitherPath<OrderError, ValidatedOrder> validateOrder(
      User user, OrderRequest request, InventoryStatus inventory) {
    return Path.right(new ValidatedOrder(user, request, Money.ZERO));
  }
}

/** Declared by the page's "Imperative Shell" snippet; the controller snippet calls it. */
interface OrderService {
  Either<OrderError, OrderResult> processOrder(String userId, OrderRequest request);
}

class Fixture {

  static UserRepository userRepo;
  static EmailService emailService;
  static OrderService orderService;

  String currentUserId() {
    return "u-1";
  }

  /** The sibling validation `validateForm` accumulates with. Static: `validateForm` is static. */
  static ValidationPath<List<String>, String> validateEmail(String email) {
    Semigroup<List<String>> errors = Semigroups.list();
    return email != null && email.contains("@")
        ? Path.valid(email, errors)
        : Path.invalid(List.of("Invalid email"), errors);
  }

  /** Declared by the page's "Core" snippet; the "Shell" snippet calls it. */
  static Validated<List<String>, RegistrationForm> validateForm(RegistrationForm form) {
    return Validated.valid(form);
  }
}
