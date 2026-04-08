# EitherF: Composing Effect Vocabularies
## _One program, many languages, one interpreter_

> *"Only connect! That was the whole of her sermon."*
>
> -- E.M. Forster, *Howards End*

Forster's imperative applies to effect algebras. A payment service speaks four separate languages: gateway operations, fraud checks, ledger entries, notifications. Each is a self-contained sealed interface with its own vocabulary. But a program orchestrating all four needs a single type that can hold any instruction from any vocabulary. `EitherF` is the connection: a union type at the type-constructor level that routes each instruction to the interpreter that speaks its language.

---

~~~admonish info title="What You'll Learn"
- Why programs using multiple effect algebras need a composed effect type
- How `EitherF` represents "this instruction or that instruction" at the type constructor level
- How `Inject` embeds single-effect instructions into the composed type
- How `Interpreters.combine` merges individual interpreters into one
- How `Free.translate` lifts programs between effect types
- When `@ComposeEffects` generates all of this automatically
~~~

## The Problem: When One Vocabulary Isn't Enough

The [Free monad](free_monad.md) lets you build a program as a data structure and interpret it in different ways. But a Free program is parameterised by a single instruction type:

```java
Free<ConsoleOpKind.Witness, String> program = ...
```

This program can only contain console instructions. What if your workflow also needs database operations?

```java
// Console operations
Free<ConsoleOpKind.Witness, Unit> greeting = printLine("Hello");

// Database operations
Free<DbOpKind.Witness, User> lookup = dbLookup("alice");

// How do you combine them into one program?
// Free<???, Unit> combined = greeting.flatMap(_ -> lookup);  // Type mismatch!
```

The type parameters `ConsoleOpKind.Witness` and `DbOpKind.Witness` are different. You cannot `flatMap` across them. You need a type that says "this instruction is *either* a console operation *or* a database operation."

That type is `EitherF`.

## The Solution: Either for Type Constructors

Just as `Either<L, R>` holds a value that is either an `L` or an `R`, `EitherF<F, G, A>` holds an instruction that is either from effect set `F` or effect set `G`:

```java
public sealed interface EitherF<F, G, A>
    permits EitherF.Left, EitherF.Right {

  record Left<F, G, A>(Kind<F, A> value) implements EitherF<F, G, A> {}
  record Right<F, G, A>(Kind<G, A> value) implements EitherF<F, G, A> {}
}
```

The `F` suffix follows the established `modifyF` convention where it means "lifted to the functor/effect level."

When an instruction arrives, dispatch is a simple pattern match:

```
Instruction arrives
    │
    ├── Left(consoleOp)  →  Console interpreter handles it
    │
    └── Right(dbOp)      →  Database interpreter handles it
```

Now both instruction types live in one combined type, and `Free<EitherFKind.Witness<ConsoleOp, DbOp>, A>` can hold instructions from either vocabulary.

## Scaling Up: Right-Nesting for 3+ Effects

Two effects use a flat `EitherF<F, G>`. Three or more effects nest on the right:

```
Two effects:    EitherF<F, G>
Three effects:  EitherF<F, EitherF<G, H>>
Four effects:   EitherF<F, EitherF<G, EitherF<H, I>>>
```

The [payment processing example](../examples/payment_processing.md) composes four effect algebras this way:

```
EitherF< PaymentGatewayOp,
         EitherF< FraudCheckOp,
                  EitherF< LedgerOp,
                           NotificationOp >>>

Dispatch:
  Left?  → gateway interpreter
  Right? → Left?  → fraud interpreter
           Right? → Left?  → ledger interpreter
                    Right? → notification interpreter
```

Each instruction is routed to exactly one interpreter based on its position in the nesting. Users never construct this nesting manually; the [`@ComposeEffects`](../effect/effect_handlers.md#composing-effects) annotation generates it automatically.

## Inject: Embedding Instructions

Without `Inject`, every instruction would need manual wrapping:

```java
// Without Inject: verbose and error-prone
Free<Composed, RiskScore> checkFraud = Free.liftF(
    EitherFKindHelper.widen(
        EitherF.right(EitherFKindHelper.widen(
            EitherF.left(fraudOp)))),
    composedFunctor);

// With Inject: clean and type-safe
Free<Composed, RiskScore> checkFraud = fraud.checkTransaction(amount, customer, Function.identity());
```

`Inject<F, G>` witnesses that effect type `F` can be embedded into a larger composed type `G`:

```java
public interface Inject<F, G> {
  <A> Kind<G, A> inject(Kind<F, A> fa);
}
```

Standard instances (provided by `InjectInstances`):

`injectLeft()`
: Embeds into the left position of an `EitherF`

`injectRight()`
: Embeds into the right position of an `EitherF`

`injectRightThen(Inject)`
: Transitive injection for 3+ effects, chaining through nested right positions

## Interpreters.combine: One Dispatcher for Everything

Individual interpreters each handle one effect algebra. `Interpreters.combine` merges them into a single natural transformation that dispatches to the right handler:

```java
var interpreter = Interpreters.combine(
    gatewayInterpreter,
    fraudInterpreter,
    ledgerInterpreter,
    notificationInterpreter);

// One call interprets the entire composed program
IO<PaymentResult> result = IOKindHelper.IO_OP.narrow(
    program.foldMap(interpreter, IOMonad.INSTANCE));
```

Overloads support 2, 3, and 4 effects. Internally, `combine` pattern-matches on `Left`/`Right` at each nesting level and delegates to the corresponding interpreter.

## Free.translate: Lifting Between Effect Types

`Free.translate` transforms `Free<F, A>` to `Free<G, A>` using a natural transformation and a target functor:

```java
Free<G, A> translated = Free.translate(program, inject::inject, functorG);
```

This is how `Bound` instances work internally: when you call `console.readLine(Function.identity())`, the `Bound` class lifts the single-effect instruction into the composed `EitherF` type via `Free.translate` and the appropriate `Inject` instance.

## When @ComposeEffects Does It For You

For most users, `EitherF` is an implementation detail. The [`@ComposeEffects`](../effect/effect_handlers.md#composing-effects) annotation generates the entire composition infrastructure:

```java
@ComposeEffects
public record AppEffects(
    Class<ConsoleOp<?>> console,
    Class<DbOp<?>> db) {}
// Generates: AppEffectsWiring with Inject instances, composed Functor, BoundSet
```

The generated `BoundSet` provides smart constructors that handle injection automatically. You write programs using `bounds.console().readLine(...)` and `bounds.db().save(...)` without thinking about `EitherF`, `Inject`, or nesting.

Understanding EitherF matters when you need to debug type errors, write custom composition, or understand what the generated code does under the hood.

---

~~~admonish tip title="See Also"
- **[Free Monad](free_monad.md)** - The program-as-data foundation that EitherF compositions target
- **[Effect Handlers Introduction](../effect/effect_handlers_intro.md)** - Motivation and terminology for the effect handler system
- **[Effect Handler Reference](../effect/effect_handlers.md)** - Technical reference for `@EffectAlgebra` and `@ComposeEffects`
- **[Payment Processing](../examples/payment_processing.md)** - Complete worked example composing four effect algebras
~~~

---

**Previous:** [Free Applicative](free_applicative.md)
**Next:** [Coyoneda](coyoneda.md)
