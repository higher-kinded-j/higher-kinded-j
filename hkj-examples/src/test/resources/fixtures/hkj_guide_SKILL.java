// Fixture for .claude/skills/hkj-guide/SKILL.md
//
// The skill's snippets are about the Path API, so they elide the domain they operate on: the order
// records, the sealed error hierarchy, the repository and the service calls. Supplying them here
// means every chain the skill teaches is compiled against the REAL library, so a renamed combinator
// or a changed signature fails the build instead of quietly misleading a reader.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.effect.ValidationPath;

/** The reader's own domain. */
record Address(String line1) {}

record Receipt(String id) {}

record Money(long pence) {}

record Payment(String id) {}

record Order(String id) {}

record OrderRequest(String customerId) {}

record ValidatedOrder(Money amount) {}

record ValidationError(String message) {}

record Config(int port) {}

record Item(String sku) {}

record Inventory(List<Item> reserved) {}

record Result(String id) {}

record ValidatedUser(String email) {}

/** The customer whose preferred payment method the VResultPath chain reads. */
record Customer(String id) {
  Receipt preferredPayment() {
    return new Receipt("r-" + id);
  }
}

/**
 * The record the assembly examples build. The two-argument constructor is what the *unlabelled*
 * `accumulate()` example applies; the canonical one is what the labelled `fields()` example applies.
 */
record User(String name, String email, int age) {
  User(String name, String email) {
    this(name, email, 0);
  }
}

/** The wire shape the assembly examples parse. */
record UserDto(String name, String email, String age) {}

/** The reader's own error type, as the ForPath and mapError examples write it. */
record Error(String message) {}

/** The reader's own sealed failure hierarchy. */
sealed interface OrderError {
  record UserNotFound(String userId) implements OrderError {}

  record ValidationFailed(ValidationError cause) implements OrderError {}

  record PaymentFailed(Throwable cause) implements OrderError {}

  /** Layer-boundary enrichment, as `.mapError(OrderError::enrich)` uses it. */
  static OrderError enrich(OrderError error) {
    return error;
  }
}

/** Smart constructors: parse, don't validate. */
final class Name {
  static Validated<NonEmptyList<FieldError>, String> parse(String raw) {
    return raw == null || raw.isBlank()
        ? Validated.invalidNel(FieldError.of("must not be blank"))
        : Validated.valid(raw);
  }
}

final class Email {
  static Validated<NonEmptyList<FieldError>, String> parse(String raw) {
    return raw != null && raw.contains("@")
        ? Validated.valid(raw)
        : Validated.invalidNel(FieldError.of("must contain @"));
  }
}

final class Age {
  static Validated<NonEmptyList<FieldError>, Integer> parse(String raw) {
    try {
      return Validated.valid(Integer.parseInt(raw));
    } catch (NumberFormatException e) {
      return Validated.invalidNel(FieldError.of("must be a number"));
    }
  }
}

/** The repository the railway pipeline reads from: absence is normal, so it returns `Maybe`. */
final class UserRepository {
  Maybe<User> findById(String id) {
    return Maybe.just(new User("Ada", "ada@example.com", 36));
  }
}

final class OrderValidator {
  Either<ValidationError, ValidatedOrder> validate(OrderRequest request) {
    return Either.right(new ValidatedOrder(new Money(1_000)));
  }
}

final class PaymentService {
  Payment charge(User user, Money amount) {
    return new Payment("p-1");
  }
}

class Fixture {

  static final OrderRequest request = new OrderRequest("c-1");
  static final UserDto dto = new UserDto("Ada", "ada@example.com", "36");
  static final Config config = new Config(8080);
  static final String id = "u-1";
  static final List<Item> items = List.of(new Item("sku-1"));

  static final UserRepository userRepository = new UserRepository();
  static final OrderValidator validator = new OrderValidator();
  static final PaymentService paymentService = new PaymentService();

  // ----- the VResultPath order pipeline -----

  static VResultPath<OrderError, Address> validateAddress(OrderRequest request) {
    return Path.vresultRight(new Address("1 High Street"));
  }

  static VResultPath<OrderError, Customer> lookupCustomer(OrderRequest request) {
    return Path.vresultRight(new Customer(request.customerId()));
  }

  static VResultPath<OrderError, Receipt> retryOnce(OrderRequest request) {
    return Path.vresultRight(new Receipt("r-1"));
  }

  // ----- the unlabelled accumulate() validators -----

  static Validated<NonEmptyList<String>, String> parseName(String raw) {
    return raw == null || raw.isBlank() ? Validated.invalidNel("blank name") : Validated.valid(raw);
  }

  static Validated<NonEmptyList<String>, String> parseEmail(String raw) {
    return raw != null && raw.contains("@") ? Validated.valid(raw) : Validated.invalidNel("bad email");
  }

  // ----- the ForPath comprehension -----

  static EitherPath<Error, User> fetchUser(String id) {
    return Path.right(new User("Ada", "ada@example.com", 36));
  }

  static EitherPath<Error, ValidatedUser> validateUser(User user) {
    return Path.right(new ValidatedUser(user.email()));
  }

  static EitherPath<Error, Inventory> checkInventory(List<Item> items) {
    return Path.right(new Inventory(items));
  }

  static Result createOrder(User user, ValidatedUser validated, Inventory inventory) {
    return new Result("o-1");
  }

  // ----- the railway pipeline -----

  static Order createOrder(User user, OrderRequest request, Payment payment) {
    return new Order("o-1");
  }
}
