# Stack Archetypes: Named Patterns for Common Problems

Every enterprise application juggles multiple concerns: typed errors, optional lookups, validation, shared context, audit trails, stateful workflows, and safe recursion. Rather than designing a composition strategy from scratch each time, these **named archetypes** give you a ready-made pattern for the most common situations.

~~~admonish info title="What You'll Learn"
- Seven named stack archetypes that cover the vast majority of enterprise use cases
- Which Path type to reach for when you recognise a familiar problem
- How each archetype maps to its underlying monad transformer (for when you need the raw machinery)
- How archetypes compose together in real-world workflows
~~~

~~~admonish example title="See Example Code"
[ArchetypeExamples.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ArchetypeExamples.java)
~~~

---

## Archetypes at a Glance

| Archetype | Path Type | Underlying Type | Enterprise Use Case |
|-----------|-----------|-----------------|---------------------|
| [Service Stack](#the-service-stack) | `EitherPath<E, A>` | `Either<E, A>` | Typed domain errors with short-circuit |
| [Lookup Stack](#the-lookup-stack) | `MaybePath<A>` | `Maybe<A>` | Optional lookups with fallback chains |
| [Validation Stack](#the-validation-stack) | `ValidationPath<E, A>` | `Validated<E, A>` | Collecting all errors, not just the first |
| [Context Stack](#the-context-stack) | `ReaderPath<R, A>` | `Reader<R, A>` | Dependency injection; multi-tenant context |
| [Audit Stack](#the-audit-stack) | `WriterPath<W, A>` | `Writer<W, A>` | Compliance audit trails; structured logging |
| [Workflow Stack](#the-workflow-stack) | `WithStatePath<S, A>` | `State<S, A>` | State machine transitions; stateful pipelines |
| [Safe Recursion Stack](#the-safe-recursion-stack) | `TrampolinePath<A>` | `Trampoline<A>` | Stack-safe recursion; paginated aggregation |

---

## The Service Stack

**Path type:** `EitherPath<E, A>`
**Underlying type:** `Either<E, A>`
**Raw transformer:** `EitherT<F, E, A>`

### The problem

Your service method can fail in multiple, domain-specific ways: insufficient funds, account suspended, gateway timeout. Traditional Java forces a choice between checked exceptions (verbose, uncompilable in lambdas) and unchecked exceptions (invisible, unsafe). Neither option composes cleanly across a multi-step pipeline.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●══════════●══════════●══════════●═══▶  Confirmation</span>
    <span style="color:#4CAF50">          right      via        via       map</span>
    <span style="color:#4CAF50">        (account)  (validate)  (charge)</span>
                        ╲            ╲
                         ╲            ╲  error: switch tracks
                          ╲            ╲
    <span style="color:#F44336"><b>Failure</b> ────────────●────────────●──────────────▶  PaymentError</span>
    <span style="color:#F44336">              InsufficientFunds  GatewayTimeout</span>
                                        │
                                   <span style="color:#4CAF50">recoverWith</span>    circuit-breaker: retry once
                                        │
    <span style="color:#4CAF50">                                    ●═══▶  retried Confirmation</span>
</pre>

### The solution

```java
// Domain error hierarchy
sealed interface PaymentError {
    record InsufficientFunds(String accountId, double shortfall) implements PaymentError {}
    record AccountNotFound(String accountId) implements PaymentError {}
    record AccountSuspended(String accountId) implements PaymentError {}
    record GatewayTimeout(String provider) implements PaymentError {}
}

// Service method returning a typed error channel
EitherPath<PaymentError, PaymentConfirmation> processPayment(PaymentRequest request) {
    return Path.<PaymentError, Account>right(lookupAccount(request.accountId()))
        .via(account -> validateBalance(account, request.amount()))
        .via(account -> chargeAccount(account, request.amount()))
        .map(charge -> new PaymentConfirmation(charge.transactionId()));
}

// Circuit-breaker with retry: recover from transient failures
EitherPath<PaymentError, PaymentConfirmation> resilientPayment(PaymentRequest request) {
    return processPayment(request)
        .recoverWith(error -> switch (error) {
            case PaymentError.GatewayTimeout t -> processPayment(request);  // retry once
            default -> Path.left(error);                                    // propagate
        });
}
```

### When to use

This is the default archetype. Most service-layer methods that can fail with domain-specific errors should start here. The typed error channel makes every failure mode visible in the method signature, and `recoverWith` gives you a natural place for circuit-breaker and retry logic.

### The imperative alternative

```java
// Without EitherPath: scattered try/catch, invisible error modes
try {
    Account account = lookupAccount(request.accountId());
    if (account.balance() < request.amount()) {
        throw new InsufficientFundsException(account.id(), ...);
    }
    Charge charge = chargeAccount(account, request.amount());
    return new PaymentConfirmation(charge.transactionId());
} catch (GatewayTimeoutException e) {
    // Retry logic mixed with business logic
    return processPayment(request);  // but what about the other exceptions?
}
```

---

## The Lookup Stack

**Path type:** `MaybePath<A>`
**Underlying type:** `Maybe<A>`
**Raw transformer:** `MaybeT<F, A>`

### The problem

You need to resolve a value from multiple sources with a defined priority: first check the database, then the environment, then fall back to defaults. Each source might return nothing. With `Optional`, you end up with nested `flatMap` chains that obscure the fallback logic.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Present</b>  ═══●═══════════════════════════════════════▶  Config</span>
    <span style="color:#4CAF50">         database</span>
                ╲
                 ╲  nothing: try next source
                  ╲
    <span style="color:#F44336"><b>Absent</b></span>   ──────●
                  │ <span style="color:#4CAF50">orElse</span>
    <span style="color:#4CAF50"><b>Present</b>  ═════●═════════════════════════════════════▶  Config</span>
    <span style="color:#4CAF50">         environment</span>
                  ╲
                   ╲  nothing: try next source
                    ╲
    <span style="color:#F44336"><b>Absent</b></span>   ────────●
                    │ <span style="color:#4CAF50">orElse</span>
    <span style="color:#4CAF50"><b>Present</b>  ═══════●═══════════════════════════════════▶  Config</span>
    <span style="color:#4CAF50">          defaults (guaranteed)</span>
</pre>

### The solution

```java
MaybePath<Config> resolveConfig(String key) {
    return lookupFromDatabase(key)                          // MaybePath<Config>
        .orElse(() -> lookupFromEnvironment(key))           // try next source
        .orElse(() -> Path.just(Config.defaultFor(key)));   // guaranteed fallback
}

// Each lookup returns MaybePath
MaybePath<Config> lookupFromDatabase(String key) {
    return Path.maybe(database.find(key));   // Nothing if absent
}
```

### When to use

Whenever absence is normal and expected, not an error. Cache lookups, configuration resolution, optional user preferences, feature flag checks. If the caller needs to know *why* the value is missing, use the Service Stack instead.

### The imperative alternative

```java
// Without MaybePath: null-check chains
Config config = database.find(key);
if (config == null) {
    config = System.getenv(key) != null ? Config.parse(System.getenv(key)) : null;
}
if (config == null) {
    config = Config.defaultFor(key);
}
```

---

## The Validation Stack

**Path type:** `ValidationPath<E, A>`
**Underlying type:** `Validated<E, A>`
**No direct transformer equivalent**

### The problem

A REST API receives a request body with multiple fields. Each field has its own validation rules. The caller expects *all* validation failures in a single response, not one at a time. The Service Stack's short-circuit behaviour is wrong here; you need error accumulation.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50">validateName ════●═══╗</span>
                        ║
    <span style="color:#4CAF50">validateEmail ═══●═══╬═══ zipWith3Accum ═══●═══▶  Registration</span>
                        ║
    <span style="color:#4CAF50">validateAge ════●════╝</span>

    If any fail, errors <b>accumulate</b> (not short-circuit):

    <span style="color:#F44336">validateName ────●───╗</span>
    <span style="color:#F44336">  "Name too short"   ║</span>
                        <span style="color:#F44336">╠═══ zipWith3Accum ═══●═══▶  List[err1, err2]</span>
    <span style="color:#F44336">validateEmail ───●───╝</span>
    <span style="color:#F44336">  "Invalid email"</span>
                        ║
    <span style="color:#4CAF50">validateAge ════●════╝</span>
    <span style="color:#4CAF50">  (valid, but still</span>
    <span style="color:#4CAF50">   collected with errors)</span>
</pre>

### The solution

```java
Semigroup<List<String>> errors = Semigroups.list();

ValidationPath<List<String>, String> validateName(String name) {
    return name != null && name.length() >= 2
        ? Path.valid(name, errors)
        : Path.invalid(List.of("Name must be at least 2 characters"), errors);
}

ValidationPath<List<String>, String> validateEmail(String email) {
    return email != null && email.contains("@")
        ? Path.valid(email, errors)
        : Path.invalid(List.of("Invalid email format"), errors);
}

// Accumulate ALL errors, not just the first
ValidationPath<List<String>, Registration> validateRequest(RegistrationRequest req) {
    return validateName(req.name())
        .zipWith3Accum(
            validateEmail(req.email()),
            validateAge(req.age()),
            Registration::new
        );
}
// Invalid case returns: List["Name must be at least 2 characters", "Invalid email format"]
```

### When to use

API request validation, form input checking, configuration file parsing; anywhere the user benefits from seeing all problems at once. If you only need the first error, the Service Stack is simpler.

### The imperative alternative

```java
// Without ValidationPath: manual error list accumulation
List<String> errors = new ArrayList<>();
if (name == null || name.length() < 2) errors.add("Name must be at least 2 characters");
if (email == null || !email.contains("@")) errors.add("Invalid email format");
if (age < 0 || age > 150) errors.add("Age must be between 0 and 150");
if (!errors.isEmpty()) return ResponseEntity.badRequest().body(errors);
// ... proceed with validated data (but the types don't prove it's valid)
```

---

## The Context Stack

**Path type:** `ReaderPath<R, A>`
**Underlying type:** `Reader<R, A>`
**Raw transformer:** `ReaderT<F, R, A>`

### The problem

In a multi-tenant SaaS application, every service call needs access to tenant context: the tenant ID, feature flags, rate limits, and the current user's permissions. Passing this context through every method parameter is tedious, error-prone, and clutters every signature. Spring's `@Autowired` solves this for singletons, but not for request-scoped data.

### The solution

```java
record TenantContext(String tenantId, Set<String> featureFlags) {}

// Service methods declare their dependency on context without receiving it yet
ReaderPath<TenantContext, PricingPlan> resolvePricing() {
    return Path.<TenantContext>ask()
        .map(ctx -> ctx.featureFlags().contains("premium")
            ? PricingPlan.PREMIUM
            : PricingPlan.STANDARD);
}

ReaderPath<TenantContext, List<Product>> listProducts() {
    return Path.<TenantContext>ask()
        .map(ctx -> catalog.findByTenant(ctx.tenantId()));
}

// Compose without mentioning context at intermediate steps
ReaderPath<TenantContext, ProductPage> buildProductPage() {
    return resolvePricing()
        .zipWith(listProducts(), ProductPage::new);
}

// Provide context once, at the edge
ProductPage page = buildProductPage().run(currentTenantContext);
```

### When to use

Multi-tenant systems, distributed tracing (threading trace IDs), security contexts (current user, permissions), or any situation where multiple service methods need shared request-scoped data. Particularly valuable in non-Spring contexts or when you need explicit, testable context management.

### The imperative alternative

```java
// Without ReaderPath: context pollutes every signature
PricingPlan resolvePricing(TenantContext ctx) { ... }
List<Product> listProducts(TenantContext ctx) { ... }
ProductPage buildProductPage(TenantContext ctx) {
    return new ProductPage(resolvePricing(ctx), listProducts(ctx));
}
```

---

## The Audit Stack

**Path type:** `WriterPath<W, A>`
**Underlying type:** `Writer<W, A>`
**No direct transformer in Higher-Kinded-J** (use `WriterPath` directly)

### The problem

Financial regulations require every step in a transaction to produce an audit entry. The audit log must be accumulated alongside the computation, not scattered across side-effecting logger calls that are invisible in the type system and easily forgotten.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Value</b>   ═══●══════════════●══════════════●═══▶  TransferResult</span>
    <span style="color:#4CAF50">         debit          credit         map</span>
              │                │
              ▼ <i>siding</i>        ▼ <i>siding</i>
    <span style="color:#FFB300"><b>Log</b>     ──●──────────────●──────────────────▶  [DEBIT, CREDIT]</span>
    <span style="color:#FFB300">       [DEBIT ...]    [CREDIT ...]</span>
    <span style="color:#FFB300">                 accumulated via Monoid</span>
</pre>

### The solution

```java
Monoid<List<AuditEntry>> auditMonoid = Monoids.list();

WriterPath<List<AuditEntry>, Account> debitAccount(Account account, double amount) {
    Account updated = account.withBalance(account.balance() - amount);
    return WriterPath.writer(
        updated,
        List.of(new AuditEntry("DEBIT", amount + " from " + account.id())),
        auditMonoid
    );
}

WriterPath<List<AuditEntry>, TransferResult> transfer(Account from, Account to, double amount) {
    return debitAccount(from, amount)
        .via(debited -> creditAccount(to, amount))
        .map(credited -> new TransferResult(from.id(), to.id(), amount));
}

// Extract both result and accumulated audit trail
Writer<List<AuditEntry>, TransferResult> result = transfer(from, to, 500.0).run();
TransferResult outcome = result.value();
List<AuditEntry> auditTrail = result.log();      // every step's audit entries, in order
```

### When to use

Compliance-sensitive operations (financial transactions, healthcare record access, permission changes), structured logging where the log must travel with the computation, or any pipeline where you need a provable record of every step.

### The imperative alternative

```java
// Without WriterPath: side-effecting logger calls, easy to forget
Account debitAccount(Account account, double amount) {
    logger.info("DEBIT {} from {}", amount, account.id());  // easy to forget this line
    return account.withBalance(account.balance() - amount);
}
// The audit trail is scattered across log files, not attached to the result
```

---

## The Workflow Stack

**Path type:** `WithStatePath<S, A>`
**Underlying type:** `State<S, A>`
**Raw transformer:** `StateT<S, F, A>`

### The problem

An order fulfilment pipeline must track its current state as it progresses through stages: `Pending`, `Validated`, `Paid`, `Shipped`. Each step can inspect and update the state. With mutable fields, the state transitions are implicit and hard to test. With immutable records, you end up threading the state through every method return value manually.

### The solution

```java
enum OrderStage { PENDING, VALIDATED, PAID, SHIPPED }

record OrderState(OrderStage stage, List<String> events) {
    OrderState advance(OrderStage next, String event) {
        var updated = new java.util.ArrayList<>(events);
        updated.add(event);
        return new OrderState(next, List.copyOf(updated));
    }
}

WithStatePath<OrderState, Unit> validateOrder() {
    return WithStatePath.<OrderState>modify(
        s -> s.advance(OrderStage.VALIDATED, "Order validated")
    );
}

WithStatePath<OrderState, Unit> processPayment() {
    return WithStatePath.<OrderState>modify(
        s -> s.advance(OrderStage.PAID, "Payment processed")
    );
}

// Compose steps; state threads through automatically
WithStatePath<OrderState, OrderState> fulfil() {
    return validateOrder()
        .then(() -> processPayment())
        .then(() -> shipOrder())
        .then(WithStatePath::get);  // return final state
}

// Run with initial state
OrderState initial = new OrderState(OrderStage.PENDING, List.of());
OrderState finalState = fulfil().evalState(initial);
// OrderState[stage=SHIPPED, events=[Order validated, Payment processed, Order shipped]]
```

### When to use

Order processing pipelines, approval workflows, multi-step wizards, game state, or any computation where the state evolves through a defined sequence of transitions. Particularly powerful when combined with sealed types for the state, ensuring only valid transitions compile.

### The imperative alternative

```java
// Without WithStatePath: mutable state, implicit transitions
class OrderProcessor {
    private OrderStage stage = OrderStage.PENDING;
    private final List<String> events = new ArrayList<>();

    void validate() { stage = OrderStage.VALIDATED; events.add("..."); }
    void pay()      { stage = OrderStage.PAID;      events.add("..."); }
    // State is mutable, transitions are unchecked, testing requires reset
}
```

---

## The Safe Recursion Stack

**Path type:** `TrampolinePath<A>`
**Underlying type:** `Trampoline<A>`
**No transformer equivalent** (base effect for stack safety)

### The problem

You are aggregating data from a paginated external API. Each page returns a "next cursor" if more data exists. A naive recursive implementation overflows the stack after a few thousand pages. The JVM does not support tail-call optimisation.

### The solution

```java
TrampolinePath<List<Record>> fetchAllPages(String cursor, List<Record> accumulated) {
    Page page = api.fetch(cursor);
    List<Record> all = new ArrayList<>(accumulated);
    all.addAll(page.records());

    if (page.nextCursor() == null) {
        return TrampolinePath.done(List.copyOf(all));           // base case
    }
    return TrampolinePath.defer(                                // recursive case
        () -> fetchAllPages(page.nextCursor(), all)             // no stack growth
    );
}

// Safe even for millions of pages
List<Record> allRecords = fetchAllPages(initialCursor, List.of()).run();
```

### When to use

Paginated API aggregation, tree traversals, graph algorithms, recursive data transformations; any recursive algorithm that might exceed the JVM's stack depth. If your recursion depth is bounded to a small number (under a few hundred), plain recursion is fine.

### The imperative alternative

```java
// Without TrampolinePath: manual loop (works, but less composable)
List<Record> all = new ArrayList<>();
String cursor = initialCursor;
while (cursor != null) {
    Page page = api.fetch(cursor);
    all.addAll(page.records());
    cursor = page.nextCursor();
}
```

---

## Combining Archetypes

Real applications rarely use a single archetype in isolation. The Order Processing example in the Examples Gallery demonstrates multiple archetypes working together:

- **Service Stack** (`EitherPath`) for the main workflow with typed `OrderError`
- **Validation Stack** (`ValidationPath`) for input validation before processing
- **Context Stack** (`ReaderPath`) for threading `WorkflowConfig` through the pipeline
- **Audit Stack** (`WriterPath`) for compliance logging via `AuditLogWriter`

The Path API's conversion methods (`toEitherPath`, `toMaybePath`, `toValidationPath`) make it straightforward to transition between archetypes at natural boundaries in your pipeline.

```java
// Validate first (accumulate all errors), then switch to service stack (short-circuit)
EitherPath<OrderError, ValidatedOrder> validated =
    validateRequest(request)                          // ValidationPath (accumulate)
        .toEitherPath()                               // switch to EitherPath
        .mapError(errors -> new OrderError.Invalid(errors));

// Thread context through the validated pipeline
ReaderPath<WorkflowConfig, EitherPath<OrderError, OrderResult>> pipeline =
    Path.<WorkflowConfig>ask()
        .map(config -> validated.via(order -> processOrder(order, config)));
```

---

## Archetypes vs Raw Transformers

Each archetype's Path type provides a fluent, concrete API. For most use cases, you never need the raw transformer. The table below shows the correspondence for when you do need to drop down:

| Path Type | Corresponding Transformer | When to Use the Transformer |
|-----------|--------------------------|----------------------------|
| `EitherPath<E, A>` | `EitherT<F, E, A>` | Combining typed errors with a *different* outer monad (e.g. `CompletableFuture`) |
| `MaybePath<A>` | `MaybeT<F, A>` | Combining absence with a different outer monad |
| `OptionalPath<A>` | `OptionalT<F, A>` | Same as MaybeT, for `java.util.Optional` users |
| `ReaderPath<R, A>` | `ReaderT<F, R, A>` | Combining environment reading with a different outer monad |
| `WithStatePath<S, A>` | `StateT<S, F, A>` | Combining state with a different outer monad |
| `ValidationPath<E, A>` | *(no transformer)* | Always use the Path type directly |
| `WriterPath<W, A>` | *(no transformer)* | Always use the Path type directly |
| `TrampolinePath<A>` | *(no transformer)* | Always use the Path type directly |

~~~admonish tip title="See Also"
- [Effect Path Overview](../effect/effect_path_overview.md) - The railway model and how Path types work
- [Path Types](../effect/path_types.md) - Detailed reference for every Path type
- [Order Processing Workflow](../examples/examples_order.md) - Full-scale example combining multiple archetypes
- [Monad Transformers](transformers.md) - The raw transformer machinery underneath
~~~

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Monad Transformers](transformers.md)
