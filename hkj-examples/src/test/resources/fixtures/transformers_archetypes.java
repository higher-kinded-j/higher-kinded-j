// Fixture for hkj-book/src/transformers/archetypes.md
//
// The page walks seven archetypes - service, fallback, validation, context, audit, workflow and
// recursion - each with a Path showcase and an imperative contrast. Each archetype's domain is
// declared here; the snippets declare the types the archetype is *about*.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.effect.TrampolinePath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.effect.WriterPath;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.writer.Writer;

// --- Service stack ---

record Account(String id, double balance) {

  Account withBalance(double newBalance) {
    return new Account(id, newBalance);
  }
}

record PaymentRequest(String accountId, double amount) {}

record Charge(String transactionId) {}

record PaymentConfirmation(String transactionId) {}

final class InsufficientFundsException extends RuntimeException {

  InsufficientFundsException(String accountId, double shortfall) {
    super("Account " + accountId + " is short by " + shortfall);
  }
}

final class GatewayTimeoutException extends RuntimeException {}

// --- Fallback stack ---

record Config(String key, String value) {

  static Config defaultFor(String key) {
    return new Config(key, "default");
  }

  static Config parse(String raw) {
    return new Config("parsed", raw);
  }
}

final class ConfigDatabase {

  Maybe<Config> find(String key) {
    return Maybe.just(new Config(key, "from-db"));
  }
}

// --- Validation stack ---

record RegistrationRequest(String name, String email, int age) {}

record Registration(String name, String email, int age) {}

// --- Context stack ---

enum PricingPlan {
  STANDARD,
  PREMIUM
}

record Product(String sku) {}

record ProductPage(PricingPlan plan, List<Product> products) {}

final class Catalog {

  List<Product> findByTenant(String tenantId) {
    return List.of(new Product("sku-1"));
  }
}

// --- Audit stack ---

record AuditEntry(String kind, String detail) {}

record TransferResult(String from, String to, double amount) {}

record TenantContext(String tenantId, Set<String> featureFlags) {}

enum OrderStage {
  PENDING,
  VALIDATED,
  PAID,
  SHIPPED
}

// --- Recursion stack ---

record PageRecord(String id) {}

record Page(List<PageRecord> records, String nextCursor) {}

final class PagedApi {

  Page fetch(String cursor) {
    return new Page(List.of(new PageRecord(cursor)), null);
  }
}

// --- Combining archetypes ---

record ValidatedOrder(String id) {}

record OrderResult(String id) {}

record WorkflowConfig(String region) {}

sealed interface OrderError {
  record Invalid(List<String> errors) implements OrderError {}
}

final class SimpleLogger {

  void info(String message, Object... args) {}
}

class Fixture {

  static final Semigroup<List<String>> errors = Semigroups.list();

  static final Monoid<List<AuditEntry>> auditMonoid = Monoids.list();

  static final ConfigDatabase database = new ConfigDatabase();

  static final Catalog catalog = new Catalog();

  static final PagedApi api = new PagedApi();

  static final SimpleLogger logger = new SimpleLogger();

  static final String initialCursor = "cursor-0";

  static final String key = "feature.flag";

  static final String name = "Alice";

  static final String email = "alice@example.com";

  static final int age = 36;

  static final Account from = new Account("acc-1", 1000.0);

  static final Account to = new Account("acc-2", 500.0);

  static final PaymentRequest request = new PaymentRequest("acc-1", 100.0);

  static final RegistrationRequest registration =
      new RegistrationRequest("Alice", "alice@example.com", 36);

  // --- Service stack ---

  static final TenantContext currentTenantContext =
      new TenantContext("tenant-1", Set.of("premium"));

  static Charge chargeDirectly(Account account, double amount) {
    return new Charge("txn-" + account.id());
  }

  static Account lookupAccount(String accountId) {
    return new Account(accountId, 1000.0);
  }

  // --- Fallback stack ---

  static MaybePath<Config> lookupFromEnvironment(String key) {
    return Path.maybe(Maybe.fromNullable(System.getenv(key)).map(Config::parse));
  }

  // --- Validation stack ---

  static ValidationPath<List<String>, Integer> validateAge(int age) {
    return age >= 0 && age <= 150
        ? Path.valid(age, errors)
        : Path.invalid(List.of("Age must be between 0 and 150"), errors);
  }

  // --- Audit stack ---

  static WriterPath<List<AuditEntry>, Account> creditAccount(Account account, double amount) {
    Account updated = account.withBalance(account.balance() + amount);
    return WriterPath.writer(
        updated,
        List.of(new AuditEntry("CREDIT", amount + " to " + account.id())),
        auditMonoid);
  }

  // --- Combining archetypes ---

  static ValidationPath<List<String>, ValidatedOrder> validateRequest(PaymentRequest request) {
    return Path.valid(new ValidatedOrder(request.accountId()), errors);
  }

  static EitherPath<OrderError, OrderResult> processOrder(
      ValidatedOrder order, WorkflowConfig config) {
    return Path.right(new OrderResult(order.id()));
  }
}
