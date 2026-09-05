// Fixture for hkj-book/src/effect/effect_contexts_error.md
//
// The ErrorContext page walks one order/user/profile pipeline through construction, chaining,
// recovery, error translation and execution. The domain, its four error types and the services
// behind them live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.io.IOKind;

record User(String id, String profileId) {

  static User anonymous() {
    return new User("anonymous", "");
  }

  static User guest() {
    return new User("guest", "");
  }
}

record Profile(String id) {

  static Profile empty() {
    return new Profile("");
  }
}

record Cart(String id) {}

record Order(String id) {

  static Order placeholder(String reason) {
    return new Order("placeholder: " + reason);
  }
}

record OrderRequest(String sku) {}

record Invoice(String id) {}

record Customer(String id) {}

record Orders(String customerId) {}

record Data(String value) {}

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

record HttpResponse(int status, String body) {}

/** The four error types the page translates between as it climbs the layers. */
record OrderError(String message) {

  static OrderError fromException(Throwable cause) {
    return new OrderError(String.valueOf(cause.getMessage()));
  }
}

record ApiError(int status, String message) {

  ApiError(String message) {
    this(500, message);
  }

  static ApiError fromException(Throwable cause) {
    return new ApiError(500, String.valueOf(cause.getMessage()));
  }

  static ApiError fromRepoError(RepoError error) {
    return new ApiError(500, error.message());
  }
}

record ValidationError(String message) {}

record DbError(String message) {

  static DbError fromException(Throwable cause) {
    return new DbError(String.valueOf(cause.getMessage()));
  }

  static DbError fromSql(Throwable cause) {
    return new DbError(String.valueOf(cause.getMessage()));
  }
}

record RepoError(String message) {

  static RepoError fromDbError(DbError error) {
    return new RepoError(error.message());
  }
}

final class NotFoundException extends RuntimeException {

  NotFoundException(String message) {
    super(message);
  }
}

class Fixture {

  static final String id = "u-1";

  static final String userId = "u-1";

  static final String customerId = "c-1";

  static final OrderRequest request = new OrderRequest("sku-1");

  static final Logger log = new Logger();

  static final HttpClient httpClient = new HttpClient();

  static final Validator validator = new Validator();

  static final UserService userService = new UserService();

  static final OrderService orderService = new OrderService();

  static final CustomerRepo customerRepo = new CustomerRepo();

  static final OrderRepo orderRepo = new OrderRepo();

  static final InvoiceService invoiceService = new InvoiceService();

  static final FetchService primaryService = new FetchService();

  static final FetchService backupService = new FetchService();

  static final Db db = new Db();

  static final UserClientLike client = new UserClientLike();

  static final ErrorContext<IOKind.Witness, ApiError, User> userContext =
      ErrorContext.success(new User("u-1", "p-1"));

  static Either<ApiError, User> lookupUser(String id) {
    return Either.right(new User(id, "p-1"));
  }

  static ErrorContext<IOKind.Witness, ApiError, User> fetchUser(String id) {
    return ErrorContext.success(new User(id, "p-1"));
  }

  static ErrorContext<IOKind.Witness, ApiError, Profile> fetchProfile(String profileId) {
    return ErrorContext.success(new Profile(profileId));
  }

  static ErrorContext<IOKind.Witness, ApiError, Profile> enrichProfile(Profile profile) {
    return ErrorContext.success(profile);
  }

  static ErrorContext<IOKind.Witness, String, User> fetchFromCache(String id) {
    return ErrorContext.success(new User(id, "p-1"));
  }

  static ErrorContext<IOKind.Witness, String, User> fetchFromDatabase(String id) {
    return ErrorContext.success(new User(id, "p-1"));
  }

  static ErrorContext<IOKind.Witness, String, Unit> logAction(String what) {
    return ErrorContext.success(Unit.INSTANCE);
  }

  static ErrorContext<IOKind.Witness, String, Unit> performAction() {
    return ErrorContext.success(Unit.INSTANCE);
  }

  static Config loadConfigFromServer() {
    return new Config("server");
  }

  static User parseUser(String body) {
    return new User("u-1", "p-1");
  }

  static Profile parseProfile(String body) {
    return new Profile("p-1");
  }

  static final class HttpClient {

    HttpResponse get(String path) {
      return new HttpResponse(200, "{}");
    }
  }

  static final class Validator {

    Either<ValidationError, Order> validate(OrderRequest request) {
      return Either.right(new Order("o-1"));
    }
  }

  static final class UserService {

    User fetch(String id) {
      return new User(id, "p-1");
    }
  }

  static final class OrderService {

    Order validate(User user, OrderRequest request) {
      return new Order("o-1");
    }

    Order create(Order validated) {
      return validated;
    }
  }

  static final class CustomerRepo {

    Customer find(String id) {
      return new Customer(id);
    }
  }

  static final class OrderRepo {

    Orders findByCustomer(String customerId) {
      return new Orders(customerId);
    }
  }

  static final class InvoiceService {

    Invoice generate(Orders orders) {
      return new Invoice("i-1");
    }
  }

  static final class FetchService {

    Data fetch() {
      return new Data("data");
    }
  }

  static final class Db {

    User query(String sql) {
      return new User("u-1", "p-1");
    }
  }

  /** Stands in for the client the page defines further down, for the usage snippet beside it. */
  static final class UserClientLike {

    ErrorContext<IOKind.Witness, ApiError, User> fetchUser(String id) {
      return ErrorContext.success(new User(id, "p-1"));
    }

    ErrorContext<IOKind.Witness, ApiError, Profile> fetchProfile(User user) {
      return ErrorContext.success(new Profile(user.profileId()));
    }
  }

  /** Stands in for whatever logger the reader has. */
  static final class Logger {

    void warn(String format, Object... arguments) {}

    void error(String format, Object... arguments) {}
  }
}
