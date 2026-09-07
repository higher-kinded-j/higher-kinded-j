// Fixture for hkj-book/src/glossary/effect-paths.md
//
// `ConsoleOp`, `DbOp` and the `@ComposeEffects` record are REAL declarations here, so the processor
// generates ConsoleOpKind / ConsoleOpOps / ConsoleOpInterpreter and AppEffectsSupport, and the
// page's snippets name the genuine article. A snippet that declares one of them for itself shadows
// this copy, and the processor generates from that.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.example.payment.effect.FraudCheckOp;
import org.higherkindedj.example.payment.effect.LedgerOp;
import org.higherkindedj.example.payment.effect.NotificationOp;
import org.higherkindedj.example.payment.effect.PaymentGatewayOp;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.annotations.GenerateFocus;

record Error(String message) {}

record UserError(String message) {}

@GenerateFocus
record Address(String city, String postcode) {}

@GenerateFocus
record User(String id, String name, Address address) {

  static User guest() {
    return new User("guest", "Guest", new Address("", ""));
  }

  static User cached(String id) {
    return new User(id, "cached", new Address("", ""));
  }
}

record Config(String name) {

  static Config defaults() {
    return new Config("default");
  }
}

record Metrics(String value) {}

record Report(List<Metrics> metrics) {}

record Department(String id) {}

@GenerateFocus
record Company(String name, List<Department> departments) {}

record Data(String body) {}

record Cart(String id) {}

record ValidatedCart(String id) {}

record Order(String id) {}

record Receipt(String id) {}

record OrderRequest(String customerId, String address) {}

record OrderResult(String status) {

  static OrderResult rejected(OrderError error) {
    return new OrderResult("rejected");
  }
}

record Customer(String id) {}

record Payment(String id) {}

record Account(boolean active, BigDecimal balance) {

  boolean isActive() {
    return active;
  }

  BigDecimal getBalance() {
    return balance;
  }
}

sealed interface OrderError {
  record CustomerNotFound() implements OrderError {}

  record PaymentFailed(Throwable cause) implements OrderError {}

  record AddressInvalid(String detail) implements OrderError {}
}

sealed interface ApiError {
  record NotFound() implements ApiError {}

  record RateLimited() implements ApiError {}

  record Unavailable() implements ApiError {}
}

interface CustomerRepository {
  Maybe<Customer> find(String customerId);
}

interface PaymentService {
  Payment charge(Order order);
}

interface UserService {
  EitherPath<Error, User> findById(String id);
}

/** The reader's own algebras, declared for real so the processor generates their support. */
@EffectAlgebra
sealed interface ConsoleOp<A> permits ConsoleOp.ReadLine, ConsoleOp.PrintLine {

  <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f);

  record ReadLine<A>(Function<String, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new ReadLine<>(k.andThen(f));
    }
  }

  record PrintLine<A>(String message, Function<Unit, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new PrintLine<>(message, k.andThen(f));
    }
  }
}

@EffectAlgebra
sealed interface DbOp<A> permits DbOp.Save {

  <B> DbOp<B> mapK(Function<? super A, ? extends B> f);

  record Save<A>(String value, Function<String, A> k) implements DbOp<A> {
    @Override
    public <B> DbOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Save<>(value, k.andThen(f));
    }
  }
}

@ComposeEffects
record AppEffects(Class<ConsoleOp<?>> console, Class<DbOp<?>> db) {}

class Fixture {

  static final Scanner scanner = new Scanner(System.in);

  static final UserService userService = sample();

  static final String userId = "u-1";

  static final String id = "u-1";

  static final String input = "{}";

  // The composed console+db witness, spelled out because Java has no alias for it.
  static final Free<
          EitherFKind.Witness<ConsoleOpKind.Witness, DbOpKind.Witness>, String>
      program = sample();

  static final ConsoleOpOps.Bound<
          EitherFKind.Witness<ConsoleOpKind.Witness, DbOpKind.Witness>>
      console = sample();

  static final String path = "/tmp/data";

  static final Natural<ConsoleOpKind.Witness, IOKind.Witness> consoleInterp = sample();

  static final Natural<DbOpKind.Witness, IOKind.Witness> dbInterp = sample();

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Either<Error, User> findUser(String id) {
    return Either.left(new Error("not found"));
  }

  static Maybe<Config> findConfig() {
    return Maybe.just(new Config("app"));
  }

  static EitherPath<Error, Config> loadConfig() {
    return Path.right(new Config("app"));
  }

  static Data parseJson(String input) {
    return new Data(input);
  }

  static String readFile(String path) {
    return "";
  }

  static EitherPath<Error, String> validateName(String name) {
    return Path.right(name);
  }

  static final CustomerRepository customerRepository = sample();

  static final PaymentService paymentService = sample();

  static final OrderRequest req = new OrderRequest("c-1", "221B Baker Street");

  static Either<Error, Order> validateOrder(OrderRequest request, Customer customer) {
    return Either.left(new Error("invalid"));
  }

  static Receipt createReceipt(OrderRequest request, Payment payment) {
    return new Receipt(payment.id());
  }

  static Either<Error, User> lookupUser(String id) {
    return findUser(id);
  }

  static EitherPath<Error, Account> getAccount(User user) {
    return Path.left(new Error("no account"));
  }

  static EitherPath<Error, Account> validateActive(Account account) {
    return Path.left(new Error("inactive"));
  }

  static EitherPath<ApiError, User> fetchUser(String id) {
    return Path.left(new ApiError.NotFound());
  }

  static EitherPath<Error, Data> primarySource() {
    return Path.left(new Error("down"));
  }

  static EitherPath<Error, Data> fallbackSource() {
    return Path.right(new Data("cached"));
  }

  static EitherPath<Error, Cart> getCart(User user) {
    return Path.left(new Error("no cart"));
  }

  static EitherPath<Error, ValidatedCart> validateCart(Cart cart) {
    return Path.left(new Error("invalid cart"));
  }

  static EitherPath<Error, Order> createOrder(ValidatedCart cart) {
    return Path.left(new Error("not created"));
  }

  static final EitherPath<Error, User> userPath = sample();

  static User findUserOrNull(String id) {
    return null;
  }

  static Account getAccountOrNull(User user) {
    return null;
  }

  static VResultPath<OrderError, Address> validateAddress(
      String address) {
    return sample();
  }

  static VResultPath<OrderError, OrderResult> reserveStock(
      Address address) {
    return sample();
  }

  static Either<Error, Company> loadCompany(String id) {
    return Either.left(new Error("not found"));
  }

  static EitherPath<Error, List<Metrics>> loadMetrics(List<Department> departments) {
    return Path.left(new Error("not found"));
  }

  static Report generateReport(List<Metrics> metrics) {
    return new Report(metrics);
  }

  static EitherPath<Error, User> loadUser(String id) {
    return userService.findById(id);
  }
}
