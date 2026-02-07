// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/**
 * Stack Archetype examples demonstrating the seven named patterns for common enterprise problems.
 *
 * <p>Each archetype demonstrates a different Effect Path type applied to a realistic enterprise
 * scenario:
 *
 * <ul>
 *   <li><b>Service Stack</b> ({@link EitherPath}) - Payment processing with typed domain errors and
 *       circuit-breaker retry
 *   <li><b>Lookup Stack</b> ({@link MaybePath}) - Multi-source configuration resolution with
 *       fallback chains
 *   <li><b>Validation Stack</b> ({@link ValidationPath}) - API request validation with error
 *       accumulation
 *   <li><b>Context Stack</b> ({@link ReaderPath}) - Multi-tenant SaaS with threaded tenant context
 *   <li><b>Audit Stack</b> ({@link WriterPath}) - Financial transfer with compliance audit trail
 *   <li><b>Workflow Stack</b> ({@link WithStatePath}) - Order fulfilment state machine
 *   <li><b>Safe Recursion Stack</b> ({@link TrampolinePath}) - Paginated API aggregation without
 *       stack overflow
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ArchetypeExamples}
 *
 * @see org.higherkindedj.hkt.effect.Path
 */
public class ArchetypeExamples {

  public static void main(String[] args) {
    System.out.println("=== Stack Archetype Examples ===\n");

    serviceStack();
    lookupStack();
    validationStack();
    contextStack();
    auditStack();
    workflowStack();
    safeRecursionStack();
  }

  // ---------------------------------------------------------------------------
  // 1. THE SERVICE STACK (EitherPath)
  //    Enterprise scenario: Payment processing with typed domain errors
  //    and circuit-breaker retry for transient failures.
  // ---------------------------------------------------------------------------

  // Imperative sketch:
  //   try {
  //       Account account = lookupAccount(id);
  //       if (account.balance() < amount) throw new InsufficientFundsException(...);
  //       Charge charge = chargeAccount(account, amount);
  //       return new Confirmation(charge.txId());
  //   } catch (GatewayTimeoutException e) { return processPayment(request); }

  sealed interface PaymentError {
    record InsufficientFunds(String accountId, double shortfall) implements PaymentError {}

    record AccountNotFound(String accountId) implements PaymentError {}

    record AccountSuspended(String accountId) implements PaymentError {}

    record GatewayTimeout(String provider) implements PaymentError {}
  }

  record Account(String id, double balance, boolean active) {}

  record PaymentConfirmation(String transactionId) {}

  // Simulated account database
  private static final Map<String, Account> ACCOUNTS =
      Map.of(
          "acc-1", new Account("acc-1", 1000.0, true),
          "acc-2", new Account("acc-2", 50.0, true),
          "acc-3", new Account("acc-3", 5000.0, false));

  private static EitherPath<PaymentError, Account> lookupAccount(String accountId) {
    Account account = ACCOUNTS.get(accountId);
    if (account == null) {
      return Path.left(new PaymentError.AccountNotFound(accountId));
    }
    return Path.right(account);
  }

  private static EitherPath<PaymentError, Account> validateBalance(Account account, double amount) {
    if (!account.active()) {
      return Path.left(new PaymentError.AccountSuspended(account.id()));
    }
    if (account.balance() < amount) {
      return Path.left(
          new PaymentError.InsufficientFunds(account.id(), amount - account.balance()));
    }
    return Path.right(account);
  }

  private static EitherPath<PaymentError, PaymentConfirmation> processPayment(
      String accountId, double amount) {
    return lookupAccount(accountId)
        .via(account -> validateBalance(account, amount))
        .map(account -> new PaymentConfirmation("tx-" + account.id() + "-" + (int) amount));
  }

  private static void serviceStack() {
    System.out.println("--- 1. Service Stack (EitherPath): Payment Processing ---");

    // Successful payment
    var success = processPayment("acc-1", 200.0);
    System.out.println("  Payment (acc-1, 200): " + success.run());

    // Insufficient funds
    var insufficient = processPayment("acc-2", 100.0);
    System.out.println("  Payment (acc-2, 100): " + insufficient.run());

    // Account suspended
    var suspended = processPayment("acc-3", 10.0);
    System.out.println("  Payment (acc-3, 10):  " + suspended.run());

    // Circuit-breaker: recover from transient failures, propagate others
    EitherPath<PaymentError, PaymentConfirmation> resilient =
        Path.<PaymentError, PaymentConfirmation>left(new PaymentError.GatewayTimeout("stripe"))
            .recoverWith(
                error ->
                    switch (error) {
                      case PaymentError.GatewayTimeout t -> processPayment("acc-1", 200.0);
                      default -> Path.left(error);
                    });
    System.out.println("  Retry after timeout:  " + resilient.run());
    System.out.println();
  }

  // ---------------------------------------------------------------------------
  // 2. THE LOOKUP STACK (MaybePath)
  //    Enterprise scenario: Multi-source configuration resolution.
  //    Database -> environment -> defaults, with clean fallback.
  // ---------------------------------------------------------------------------

  // Imperative sketch:
  //   Config config = database.find(key);
  //   if (config == null) config = envLookup(key);
  //   if (config == null) config = Config.defaultFor(key);

  record Config(String key, String value, String source) {}

  private static final Map<String, Config> CONFIG_DB =
      Map.of("db.url", new Config("db.url", "jdbc:postgresql://prod:5432/app", "database"));

  private static MaybePath<Config> lookupFromDatabase(String key) {
    return Path.maybe(CONFIG_DB.get(key));
  }

  private static MaybePath<Config> lookupFromEnvironment(String key) {
    // Simulate: only "log.level" exists in environment
    if ("log.level".equals(key)) {
      return Path.just(new Config(key, "DEBUG", "environment"));
    }
    return Path.nothing();
  }

  private static MaybePath<Config> resolveConfig(String key) {
    return lookupFromDatabase(key)
        .orElse(() -> lookupFromEnvironment(key))
        .orElse(() -> Path.just(new Config(key, "default", "fallback")));
  }

  private static void lookupStack() {
    System.out.println("--- 2. Lookup Stack (MaybePath): Configuration Resolution ---");

    System.out.println("  db.url:       " + resolveConfig("db.url").run());
    System.out.println("  log.level:    " + resolveConfig("log.level").run());
    System.out.println("  unknown.key:  " + resolveConfig("unknown.key").run());
    System.out.println();
  }

  // ---------------------------------------------------------------------------
  // 3. THE VALIDATION STACK (ValidationPath)
  //    Enterprise scenario: REST API request validation.
  //    Collect ALL errors, not just the first.
  // ---------------------------------------------------------------------------

  // Imperative sketch:
  //   List<String> errors = new ArrayList<>();
  //   if (name == null || name.length() < 2) errors.add("Name too short");
  //   if (!email.contains("@")) errors.add("Invalid email");
  //   if (!errors.isEmpty()) return badRequest(errors);

  record Registration(String name, String email, int age) {}

  private static final Semigroup<List<String>> LIST_SEMIGROUP = Semigroups.list();

  private static ValidationPath<List<String>, String> validateName(String name) {
    return name != null && name.length() >= 2
        ? Path.valid(name, LIST_SEMIGROUP)
        : Path.invalid(List.of("Name must be at least 2 characters"), LIST_SEMIGROUP);
  }

  private static ValidationPath<List<String>, String> validateEmail(String email) {
    return email != null && email.contains("@")
        ? Path.valid(email, LIST_SEMIGROUP)
        : Path.invalid(List.of("Invalid email format"), LIST_SEMIGROUP);
  }

  private static ValidationPath<List<String>, Integer> validateAge(int age) {
    return age >= 0 && age <= 150
        ? Path.valid(age, LIST_SEMIGROUP)
        : Path.invalid(List.of("Age must be between 0 and 150"), LIST_SEMIGROUP);
  }

  private static void validationStack() {
    System.out.println("--- 3. Validation Stack (ValidationPath): API Request Validation ---");

    // Valid request
    var valid =
        validateName("Alice")
            .zipWith3Accum(validateEmail("alice@example.com"), validateAge(30), Registration::new);
    System.out.println("  Valid:   " + valid.run());

    // Multiple failures accumulated
    var invalid =
        validateName("A")
            .zipWith3Accum(validateEmail("not-an-email"), validateAge(-5), Registration::new);
    System.out.println("  Invalid: " + invalid.run());

    System.out.println();
  }

  // ---------------------------------------------------------------------------
  // 4. THE CONTEXT STACK (ReaderPath)
  //    Enterprise scenario: Multi-tenant SaaS with threaded context.
  //    Tenant ID, feature flags, and rate limits flow through the pipeline.
  // ---------------------------------------------------------------------------

  // Imperative sketch:
  //   PricingPlan plan = resolvePricing(tenantCtx);
  //   List<Product> products = listProducts(tenantCtx);
  //   return new ProductPage(plan, products);
  //   // tenantCtx pollutes every method signature

  record TenantContext(String tenantId, Set<String> featureFlags) {}

  record Product(String name, double price) {}

  record ProductPage(String pricingTier, List<Product> products) {}

  private static ReaderPath<TenantContext, String> resolvePricing() {
    return Path.<TenantContext>ask()
        .map(ctx -> ctx.featureFlags().contains("premium-pricing") ? "Premium" : "Standard");
  }

  private static ReaderPath<TenantContext, List<Product>> listProducts() {
    return Path.<TenantContext>ask()
        .map(
            ctx -> {
              // Simulated per-tenant product catalogue
              if ("tenant-a".equals(ctx.tenantId())) {
                return List.of(new Product("Widget", 9.99), new Product("Gadget", 19.99));
              }
              return List.of(new Product("Basic Widget", 4.99));
            });
  }

  private static ReaderPath<TenantContext, ProductPage> buildProductPage() {
    return resolvePricing().zipWith(listProducts(), ProductPage::new);
  }

  private static void contextStack() {
    System.out.println("--- 4. Context Stack (ReaderPath): Multi-Tenant SaaS ---");

    TenantContext premium =
        new TenantContext("tenant-a", Set.of("premium-pricing", "beta-features"));
    TenantContext standard = new TenantContext("tenant-b", Set.of());

    System.out.println("  Premium tenant: " + buildProductPage().run(premium));
    System.out.println("  Standard tenant: " + buildProductPage().run(standard));
    System.out.println();
  }

  // ---------------------------------------------------------------------------
  // 5. THE AUDIT STACK (WriterPath)
  //    Enterprise scenario: Financial transfer with compliance audit trail.
  //    Every step produces an audit entry alongside its result.
  // ---------------------------------------------------------------------------

  // Imperative sketch:
  //   logger.info("DEBIT {} from {}", amount, from.id());  // easy to forget
  //   Account debited = from.withBalance(from.balance() - amount);
  //   logger.info("CREDIT {} to {}", amount, to.id());     // easy to forget
  //   // Audit trail scattered across log files, not attached to result

  record AuditEntry(String action, String detail) {
    @Override
    public String toString() {
      return "[" + action + "] " + detail;
    }
  }

  record BankAccount(String id, double balance) {
    BankAccount withBalance(double newBalance) {
      return new BankAccount(id, newBalance);
    }
  }

  record TransferResult(String fromId, String toId, double amount) {}

  private static final Monoid<List<AuditEntry>> AUDIT_MONOID = Monoids.list();

  private static WriterPath<List<AuditEntry>, BankAccount> debitAccount(
      BankAccount account, double amount) {
    BankAccount updated = account.withBalance(account.balance() - amount);
    return WriterPath.writer(
        updated, List.of(new AuditEntry("DEBIT", amount + " from " + account.id())), AUDIT_MONOID);
  }

  private static WriterPath<List<AuditEntry>, BankAccount> creditAccount(
      BankAccount account, double amount) {
    BankAccount updated = account.withBalance(account.balance() + amount);
    return WriterPath.writer(
        updated, List.of(new AuditEntry("CREDIT", amount + " to " + account.id())), AUDIT_MONOID);
  }

  private static void auditStack() {
    System.out.println("--- 5. Audit Stack (WriterPath): Financial Transfer ---");

    BankAccount from = new BankAccount("acc-100", 1000.0);
    BankAccount to = new BankAccount("acc-200", 500.0);

    WriterPath<List<AuditEntry>, TransferResult> transfer =
        debitAccount(from, 250.0)
            .via(debited -> creditAccount(to, 250.0))
            .map(credited -> new TransferResult(from.id(), to.id(), 250.0));

    var result = transfer.run();
    System.out.println("  Result: " + result.value());
    System.out.println("  Audit trail:");
    for (AuditEntry entry : result.log()) {
      System.out.println("    " + entry);
    }
    System.out.println();
  }

  // ---------------------------------------------------------------------------
  // 6. THE WORKFLOW STACK (WithStatePath)
  //    Enterprise scenario: Order fulfilment state machine.
  //    Pending -> Validated -> Paid -> Shipped, with typed transitions.
  // ---------------------------------------------------------------------------

  // Imperative sketch:
  //   class OrderProcessor {
  //       private OrderStage stage = PENDING;
  //       private List<String> events = new ArrayList<>();
  //       void validate() { stage = VALIDATED; events.add("..."); }
  //       void pay()      { stage = PAID;      events.add("..."); }
  //       // Mutable state, implicit transitions, hard to test
  //   }

  enum OrderStage {
    PENDING,
    VALIDATED,
    PAID,
    SHIPPED
  }

  record OrderState(OrderStage stage, List<String> events) {
    OrderState advance(OrderStage next, String event) {
      var updated = new ArrayList<>(events);
      updated.add(event);
      return new OrderState(next, List.copyOf(updated));
    }
  }

  private static WithStatePath<OrderState, Unit> validateOrder() {
    return WithStatePath.modify(s -> s.advance(OrderStage.VALIDATED, "Order validated"));
  }

  private static WithStatePath<OrderState, Unit> processOrderPayment() {
    return WithStatePath.modify(s -> s.advance(OrderStage.PAID, "Payment processed"));
  }

  private static WithStatePath<OrderState, Unit> shipOrder() {
    return WithStatePath.modify(s -> s.advance(OrderStage.SHIPPED, "Order shipped"));
  }

  private static void workflowStack() {
    System.out.println("--- 6. Workflow Stack (WithStatePath): Order Fulfilment ---");

    WithStatePath<OrderState, OrderState> fulfilOrder =
        validateOrder()
            .then(ArchetypeExamples::processOrderPayment)
            .then(ArchetypeExamples::shipOrder)
            .then(WithStatePath::get);

    OrderState initial = new OrderState(OrderStage.PENDING, List.of());
    OrderState finalState = fulfilOrder.evalState(initial);

    System.out.println("  Final stage:  " + finalState.stage());
    System.out.println("  Event log:    " + finalState.events());
    System.out.println();
  }

  // ---------------------------------------------------------------------------
  // 7. THE SAFE RECURSION STACK (TrampolinePath)
  //    Enterprise scenario: Paginated API aggregation.
  //    Recursively fetch all pages without stack overflow.
  // ---------------------------------------------------------------------------

  // Imperative sketch:
  //   List<String> all = new ArrayList<>();
  //   int cursor = 0;
  //   while (cursor >= 0) {
  //       Page page = api.fetch(cursor);
  //       all.addAll(page.records());
  //       cursor = page.nextCursor();
  //   }
  //   // Works, but less composable with downstream transformations

  record Page(List<String> records, int nextCursor) {}

  // Simulated paginated API: 5 pages of 3 records each
  private static Page fetchPage(int cursor) {
    List<String> records =
        List.of(
            "record-" + (cursor * 3 + 1),
            "record-" + (cursor * 3 + 2),
            "record-" + (cursor * 3 + 3));
    int next = cursor < 4 ? cursor + 1 : -1; // -1 signals end
    return new Page(records, next);
  }

  private static TrampolinePath<List<String>> fetchAllPages(int cursor, List<String> accumulated) {
    Page page = fetchPage(cursor);
    var all = new ArrayList<>(accumulated);
    all.addAll(page.records());

    if (page.nextCursor() < 0) {
      return TrampolinePath.done(List.copyOf(all));
    }
    return TrampolinePath.defer(() -> fetchAllPages(page.nextCursor(), all));
  }

  private static void safeRecursionStack() {
    System.out.println("--- 7. Safe Recursion Stack (TrampolinePath): Paginated API ---");

    List<String> allRecords = fetchAllPages(0, List.of()).run();
    System.out.println("  Total records: " + allRecords.size());
    System.out.println("  First: " + allRecords.getFirst());
    System.out.println("  Last:  " + allRecords.getLast());
    System.out.println();
  }
}
