# Effect Handlers: Programs as Data

> *"All moments, past, present, and future, always have existed, always will exist."*
>
> -- Kurt Vonnegut, *Slaughterhouse-Five*

Billy Pilgrim experiences every moment of his life simultaneously; nothing is executed in sequence, everything simply *is*. A Free monad program has the same quality: every instruction, from charging a card to sending a receipt, exists simultaneously as data in a tree. No side effect has fired. No network call has been made. The entire workflow is present, inspectable, transformable. Only when an interpreter walks the tree does a single timeline, production, test, audit, or dry-run, materialise from the structure. Traditional dependency injection chooses the timeline at compile time; effect handlers let the program contain all of them at once.

---

~~~admonish info title="What You'll Learn"
- Why dependency injection falls short for multi-interpretation workflows
- How effect handlers relate to Higher-Kinded-J's existing capabilities
- The core idea: programs as data structures built from records and sealed interfaces
- How this connects to Data Oriented Programming in modern Java
- A terminology bridge mapping FP concepts to familiar Java equivalents
- When to use effect handlers and when to stick with simpler approaches
~~~

## The Problem: When Dependency Injection Is Not Enough

Consider a familiar Spring service. A `PaymentService` depends on four external systems: a payment gateway, a fraud detector, an accounting ledger, and a notification sender.

```java
@Service
public class PaymentService {

    @Autowired private PaymentGateway gateway;
    @Autowired private FraudDetector fraud;
    @Autowired private AccountingLedger ledger;
    @Autowired private NotificationSender notifications;

    public PaymentResult processPayment(Customer customer, Money amount) {
        RiskScore risk = fraud.checkTransaction(amount, customer);
        if (risk.exceeds(THRESHOLD)) {
            notifications.alertFraudTeam(customer, risk);
            return PaymentResult.declined("High risk");
        }
        Money balance = ledger.getBalance(customer.accountId());
        if (balance.lessThan(amount)) {
            return PaymentResult.declined("Insufficient funds");
        }
        ChargeResult charge = gateway.charge(amount, customer.paymentMethod());
        ledger.recordTransaction(customer.accountId(), amount);
        notifications.sendReceipt(customer, charge);
        return PaymentResult.success(charge.transactionId());
    }
}
```

Spring's `@Autowired` lets you swap implementations for testing. But the business needs more than implementation swapping:

| Requirement | Spring DI | Effect Handlers |
|---|---|---|
| Swap to test doubles | Yes | Yes |
| Inspect the workflow before execution (count calls, estimate cost) | No | Yes |
| Guarantee every operation is handled at compile time | No | Yes |
| Run the same logic as a fee estimate without charging | Manual second service | Same program, different interpreter |
| Add audit logging without modifying business logic | AOP proxy (runtime) | Interpreter wrapping (compile-time) |

The root cause is that the Spring service **executes immediately**. Each method call fires a side effect the moment it runs. You cannot step back and ask "what would this program do?" because it has already done it.

Effect handlers solve this by separating *description* from *execution*. The business logic becomes a data structure, a tree of instructions. Interpreters walk the tree and decide what each instruction means. Different interpreters produce different behaviours from the same program.

---

## Where This Fits: Effects, Optics, and Handlers

Higher-Kinded-J provides two core capabilities for modern Java:

- **[Effect Path API](effect_path_overview.md)**: Composable error handling with railway-oriented pipelines. Success travels one track, failure travels another. Operations like `map`, `via`, and `recover` work identically across all effect types.

- **[Focus DSL / Optics](../optics/focus_dsl.md)**: Type-safe immutable data navigation. Lenses, prisms, and traversals treat data access as first-class values, eliminating the verbose copy-and-update boilerplate that Java records require.

Effect Handlers open a third dimension. Where Effect Paths handle *how computations succeed or fail* and Optics handle *how data is accessed and transformed*, Effect Handlers address a different question: *how domain operations are defined, composed, and interpreted*.

```
        Effect Path API                Focus DSL / Optics
        ───────────────                ──────────────────
        "How computations              "How data is accessed
         succeed or fail"               and transformed"
              │                              │
              │     ┌─────────────────┐      │
              └────▶│    Shared       │◀─────┘
                    │   Foundations   │
                    │                 │
                    │  Records        │
                    │  Sealed types   │
                    │  Composition    │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ Effect Handlers │
                    │                 │
                    │ "How domain     │
                    │  operations are │
                    │  interpreted"   │
                    └─────────────────┘
```

~~~admonish note title="A Complementary Capability"
Effect Paths and Optics remain the primary entry points for most Higher-Kinded-J users. Effect Handlers are for teams that need multi-interpretation workflows: services where the same business logic must run in production, test, dry-run, and audit modes without code duplication. If your service has a single execution mode and straightforward testing needs, you may not need effect handlers at all.
~~~

All three capabilities share the same building blocks: records, sealed interfaces, and composition. They also work together: an effect handler program can use optics to navigate nested data structures and effect paths to propagate errors at the boundary.

---

## The Idea: Programs as Data Structures

If you have used sealed interfaces and records in Java 21+, you already know the foundation.

An *effect algebra* is a sealed interface where each permitted record represents an operation. This is standard Data Oriented Programming:

```java
@EffectAlgebra
public sealed interface PaymentGatewayOp<A>
    permits PaymentGatewayOp.Authorise,
            PaymentGatewayOp.Charge,
            PaymentGatewayOp.Refund {

  record Authorise<A>(Money amount, PaymentMethod method,
      Function<AuthorisationToken, A> k) implements PaymentGatewayOp<A> { /* ... */ }

  record Charge<A>(Money amount, PaymentMethod method,
      Function<ChargeResult, A> k) implements PaymentGatewayOp<A> { /* ... */ }

  record Refund<A>(TransactionId txId, Money amount,
      Function<RefundResult, A> k) implements PaymentGatewayOp<A> { /* ... */ }
}
```

Each record carries its parameters (the data the operation needs) and a *continuation function* `k` that transforms the result. When you chain these operations together using `flatMap`, you build a tree:

```
       FlatMapped
       ╱        ╲
  Suspend       λ(risk) → FlatMapped
  [FraudCheck]              ╱        ╲
                       Suspend       λ(balance) → ...
                       [GetBalance]
                                         ╲
                                     Suspend     → Pure(result)
                                     [Charge]
```

This tree is the program. It is an ordinary Java data structure. No side effect has occurred. The tree records *what* the program intends to do, not *how* to do it.

Because the program is data, you can:

- **Inspect it** before execution: count the number of external calls, identify error recovery points, estimate cost
- **Transform it** by composing interpreters: wrap every operation with audit logging, add retry logic
- **Interpret it** in different modes: production (real services), testing (pure values), quoting (fee estimation), auditing (record every step)

Just as optics represent *data access* as composable values, effect handlers represent *domain operations* as composable data.

---

## The DOP Connection: Where Modern Java Is Heading

Java's evolution toward Data Oriented Programming provides exactly the building blocks that effect handlers need:

| Java Feature | DOP Usage | Effect Handler Usage |
|---|---|---|
| **Records** | Immutable data carriers | Operation definitions with parameters |
| **Sealed interfaces** | Exhaustive type hierarchies | Closed sets of operations (effect algebras) |
| **Pattern matching** | Deconstructing data | Interpreting operations in handlers |

This is not exotic functional programming imported from Haskell. It is standard Java 21+ patterns applied to domain workflows. A `switch` expression over a sealed interface is how you write an interpreter:

```java
// This is just pattern matching, standard Java DOP
public <A> Kind<IOKind.Witness, A> apply(PaymentGatewayOp<A> op) {
    return switch (op) {
        case Authorise<A> auth -> handleAuthorise(auth);
        case Charge<A> charge -> handleCharge(charge);
        case Refund<A> refund -> handleRefund(refund);
    };
}
```

If you add a new operation to the sealed interface, every interpreter must handle it or the code will not compile. This is the same exhaustiveness guarantee that `switch` expressions provide for any sealed type.

Higher-Kinded-J's `@EffectAlgebra` annotation processor generates the boilerplate (the Kind marker, Functor instance, smart constructors, and interpreter skeleton) so you can focus on defining operations and writing interpreters.

---

## Terminology Bridge: FP Concepts in Java Terms

Effect handler documentation uses terms from functional programming that may be unfamiliar. Each maps directly to a Java concept you already know.

| FP Term | Java Equivalent | One-Liner |
|---|---|---|
| **Effect algebra** | Sealed interface + records | A closed set of operations |
| **Continuation (CPS)** | `Function` parameter on each record | Like `thenApply` on `CompletableFuture` |
| **`mapK`** | Method on each record | Like `Stream.map` but for an instruction |
| **Natural transformation** | An interpreter | Converts DSL instructions to real actions |
| **`EitherF`** | Union type for composing algebras | Like `Either` but for type constructors |
| **Free monad** | The program tree | A data structure of sequenced instructions |
| **`foldMap`** | Tree-walking interpreter runner | Like `Stream.reduce` for programs |

### Effect Algebra

A sealed interface annotated with `@EffectAlgebra` where each permitted record represents a domain operation. The sealed modifier guarantees that the set of operations is closed: the compiler knows every possible instruction. This is the same pattern that Effect Path uses for error types.

### Continuation-Passing Style (CPS)

Each operation record includes a `Function` parameter (conventionally named `k`) that transforms the operation's natural result type to the generic type parameter `A`. If `Charge` naturally produces a `ChargeResult`, the continuation `Function<ChargeResult, A>` lets callers transform that result inline. This is the same idea as `CompletableFuture.thenApply`: chain a transformation onto a value that does not exist yet.

### `mapK`

A method on each record that composes the continuation function with a new transformation. The generated Functor delegates to `mapK` rather than using unsafe casts. Think of it as `Stream.map` applied to a single instruction rather than a collection.

### Natural Transformation (Interpreter)

A function that converts each instruction in your DSL into an action in a target monad. A production interpreter converts `Charge` into an `IO` action that calls Stripe. A test interpreter converts `Charge` into an `Id` value with a canned result. The abstract skeleton is generated by `@EffectAlgebra`; you fill in the handler methods.

### `EitherF`

A union type lifted to the type constructor level. When your program uses multiple effect algebras (payments, fraud, ledger, notifications), `EitherF` composes them into a single type via right-nesting. The `@ComposeEffects` annotation generates this composition automatically.

### Free Monad

The data structure (`Free<F, A>`) that represents a program as a tree of instructions. Three node types: `Pure` (return a value), `Suspend` (an instruction to execute), and `FlatMapped` (sequence two programs). Because the program is data, it can be inspected, transformed, and interpreted in different ways.

### `foldMap`

The method that interprets a Free monad program. It traverses the instruction tree, applies a natural transformation (interpreter) to each `Suspend` node, and combines results using the target monad's `flatMap`. Stack-safe via internal trampolining. This is how you "run" the program: `program.foldMap(interpreter, monad)`.

~~~admonish tip title="Glossary"
For quick-reference definitions, see the [Effect Handlers glossary entries](../glossary.md#effect-handlers).
~~~

---

## Where Effect Handlers Shine (and Where They Don't)

### When to Use Effect Handlers

| Scenario | Why Handlers Help |
|---|---|
| Services with multiple external dependencies | Each dependency becomes an effect algebra; interpreters handle them uniformly |
| Multiple execution modes (production, test, audit, dry-run) | Same program, different interpreters |
| Audit and replay requirements | Program tree can be logged, serialised, and replayed |
| Mock-free testing | `Id` monad interpreters return pure values; no mocking framework needed |
| Compile-time exhaustiveness | Sealed interfaces guarantee every operation is handled |

### When Not to Use Effect Handlers

| Scenario | Why Simpler Approaches Suffice |
|---|---|
| Simple CRUD services | Spring DI works well; the overhead of effect algebras is not justified |
| Performance-critical hot paths | Free monad allocates intermediate objects; measure before committing |
| Single-interpretation services | If you only run in production mode, the abstraction adds complexity without benefit |
| Teams new to the pattern | Introduce gradually; start with Effect Paths and Optics first |

### How It Fits Spring

The *functional core / imperative shell* pattern applies directly:

```
┌───────────────────────────────────────────────────┐
│              Spring Controller                    │
│              (imperative shell)                   │
│                                                   │
│   Chooses interpreter based on context:           │
│   - Production: IO interpreters                   │
│   - Quote mode: Id interpreters with fee calc     │
│   - Testing:    Id interpreters with fixed values │
│                                                   │
│   ┌───────────────────────────────────────────┐   │
│   │         Free Monad Program                │   │
│   │         (functional core)                 │   │
│   │                                           │   │
│   │   Pure business logic                     │   │
│   │   No side effects                         │   │
│   │   Uses Optics for data navigation         │   │
│   │   Uses Effect Paths for error handling    │   │
│   └───────────────────────────────────────────┘   │
└───────────────────────────────────────────────────┘
```

The Free program is the functional core: pure, testable, inspectable. The Spring controller is the imperative shell that selects the interpreter and executes the result. Optics handle data navigation within the program; Effect Paths handle error propagation at the edges.

| Feature | Spring DI | Effect Handlers |
|---|---|---|
| Program inspection | No | Yes (`ProgramAnalyser`) |
| Exhaustive checking | No | Yes (sealed interfaces) |
| Multiple interpretations | Manual wiring | Built-in (`foldMap`) |
| Compositional decoration | Limited (AOP) | Yes (interpreter wrapping) |
| Mock-free testing | No (needs Mockito) | Yes (`Id` monad) |

---

## What's Next

~~~admonish tip title="See Also"
- **[Effect Handler Reference](effect_handlers.md)**: Technical reference for `@EffectAlgebra`, `@ComposeEffects`, Free monad programs, and interpreter patterns
- **[Payment Processing Example](../examples/payment_processing.md)**: Complete worked example with four interpretation modes (production, testing, quote, high-risk decline)
- **[FreePath](path_free.md)**: Fluent Effect Path API wrapper for Free monad programs
~~~

---

**Previous:** [Production Readiness](production_readiness.md) | **Next:** [Effect Handler Reference](effect_handlers.md)
