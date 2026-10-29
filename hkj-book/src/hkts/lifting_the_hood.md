<!-- description: Follow one widen, flatMap and narrow through Higher-Kinded-J: how the call reaches its instance, what runs inside it, and what each step allocates. -->

# Lifting the Hood

_One round trip through the encoding, call by call, with what each step costs._

<!-- source: Leo Tolstoy, Anna Karenina (1878), Part One, Chapter 1, trans. Richard Pevear and Larissa Volokhonsky (2000) -->
> _"All happy families are alike; each unhappy family is unhappy in its own way."_
>
> — Leo Tolstoy, *Anna Karenina* (Part One, Chapter 1, trans. Richard Pevear and Larissa Volokhonsky)

---

Most library types widen alike: each is already a `Kind`, so `widen` hands back the same object. Each JDK type is wrapped in a holder of its own. This page follows one round trip through the encoding, call by call, and shows where that difference costs something.

~~~admonish info title="What You'll Learn"
- Trace what runs when you call `flatMap` on a `Kind`
- Say how the call reaches `EitherMonad`: through a value you pass, not a lookup at compile time
- Count what each step allocates, for a library type and for a JDK type
- Choose a library type when a holder's allocation matters
~~~

~~~admonish example title="See Example Code"
**The round trip is the `roundtrip` region of [EncodingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/encoding/EncodingBook.java)**, the code How the Encoding Works shows. [LiftingTheHoodBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/hood/LiftingTheHoodBookTest.java) holds the claims a test can hold, and reads the compiled class for the claim about the bytecode.
~~~

---

## The round trip {#the-expression-we-are-tracing}

The round trip from [How the Encoding Works](core-concepts.md#the-witness-and-kind) widens an `Either`, calls `flatMap` on it, and narrows the result:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/encoding/EncodingBook.java:roundtrip}}
```

Four calls matter: `Instances.monad`, `widen`, `flatMap` and `narrow`. The next three sections take them in turn.

## How the call finds `EitherMonad` {#how-the-call-finds-eithermonad}

The instance is a value. `either()` returns a token that holds the one `EitherMonad`, and `Instances.monad(either())` hands that object back, typed as `Monad<EitherKind.Witness<String>>`. The witness type does not choose the instance. It makes javac check that `monad` and `kind` name the same container. [Obtaining Instances](../functional/instances_facade.md) covers the lookup.

`monad.flatMap(...)` is then an ordinary interface call. javac emits `invokeinterface Monad.flatMap`, and the JVM reaches `EitherMonad.flatMap` at run time, as it would for any interface method. While only one instance class passes through a call site, the JIT usually inlines the call, so the interface costs little in a hot loop. The test reads the compiled class and finds that `invokeinterface`.

## `widen` and `narrow` {#widen-and-narrow}

```mermaid
%%{init: {"sequence": {"actorMargin": 20, "diagramMarginX": 10}}}%%
sequenceDiagram
    accTitle: widen and narrow on an Either
    accDescr: Your code passes Right(10) to the EITHER helper's widen, which checks it is not null and returns the same object, typed as a Kind. Later your code passes the result to narrow, which checks it is not null and is an Either, and returns the same object, typed as an Either.
    participant Y as Your code
    participant H as EITHER helper
    Y->>H: widen(Right(10))
    Note over H: not null?
    H-->>Y: the same Right(10), as a Kind
    Y->>H: narrow(result)
    Note over H: not null? an Either?
    H-->>Y: the same object, as an Either
```

In words: `widen` checks for `null` and returns its argument, because an `Either` already implements `Kind`. `narrow` checks for `null` and for an `Either`, then casts. Neither allocates. Given another container's `Kind`, `narrow` throws `KindUnwrapException`, as [When `narrow` throws](core-concepts.md#when-narrow-throws) explains.

Most of the library's own types work this way. The JDK's types widen into a holder instead, since the library cannot change them, and so does `Lazy`, which memoises its value in fields that change, where every `Kind` must be immutable. [The pieces each type provides](core-concepts.md#the-pieces-each-type-provides) lists both kinds. An `Either` is a `Kind`, so you could skip `widen`. Keep it, so that every type reads the same and every value is checked.

## Inside `flatMap` {#inside-flatmap}

`EitherMonad.flatMap` narrows the `Kind` it is given, and hands `Either.flatMap` a lambda that calls your function:

```mermaid
%%{init: {"sequence": {"actorMargin": 20, "diagramMarginX": 10}}}%%
sequenceDiagram
    accTitle: Inside EitherMonad.flatMap on a Right
    accDescr: Your code calls flatMap on EitherMonad. It checks its arguments, narrows the Kind to an Either, and calls Right(10)'s flatMap with a lambda. The lambda calls your function f with 10, gets back Right("v=10") as a Kind, and narrows it. EitherMonad widens the result, which is the same Right("v=10"), and returns it.
    participant Y as Your code
    participant M as EitherMonad
    participant E as Right(10)
    participant F as f
    Y->>M: flatMap(f, kind)
    Note over M: checks, then narrow(kind)
    M->>E: flatMap(lambda)
    E->>F: apply(10)
    F-->>E: Right("v=10")
    Note over E: lambda narrows it
    E-->>M: Right("v=10")
    M-->>Y: widen: the same object
```

The diagram shows two narrows: one for the `Kind` you pass in, and one for the `Kind` your function returns. On a `Left`, `Either.flatMap` returns the same `Left` without calling the lambda. So your function never runs, and the `Kind` that comes back is the very `Left` that went in.

## What each step allocates {#what-each-step-allocates}

The same round trip, on a library type and on a JDK type:

| Step | `Either`, a library type | `Optional`, a JDK type |
|---|---|---|
| `Instances.monad(either())`, or `optional()` | One small token, from `either()` | One small token, from `optional()` |
| `widen` | Nothing: the same object comes back | One holder |
| `flatMap`'s own work | One lambda that captures your function | One lambda, and one holder for the result |
| Inside your function | What it builds: here a `String` and a `Right` | What it builds, and one holder when it widens its result |
| `narrow` | Nothing: a type check and a cast | Nothing: a type check and a field read |

So for a library type the machinery allocates one short-lived lambda per `flatMap`, and a JDK type adds a holder at each `widen`. The token comes once, when you look the instance up. The JIT can often remove short-lived objects that never leave the method, by escape analysis, but it cannot always. [Benchmarks & Performance](../benchmarks.md) has measured numbers.

That asymmetry is the tax on a type that cannot be its own `Kind`. The fix is not technical; it is to use library types (`Maybe`, `Either`) when the cost matters.

## What this means in practice {#what-this-means-in-practice}

- **`widen` and `narrow` on a library type cost a check, not an object.** Write them wherever they make the code clearer.
- **On a JDK type, each `widen` allocates a holder.** In a tight loop, prefer `Maybe`, `Either` or `Try`. In a method that runs once per request, the holder does not show.
- **The instance is a value you pass.** Look it up once, outside a loop. A call site that sees one instance class is usually inlined. One that sees many, as code generic over `F` can, pays for an ordinary interface call.

An `EitherPath` skips the `Kind`, the instance and the lambda: it holds an `Either` and calls it directly, and allocates a new path at each step instead. [Two encodings, one algebra](one_line_six_layers.md#two-encodings-one-algebra) sets the two side by side, and the [chapter introduction](foundations_intro.md) says which level fits your code.

---

~~~admonish tip title="See Also"
- [How the Encoding Works](core-concepts.md): the pieces this page traces
- [Foundations FAQ](faq.md): the design questions this trace raises
- [Benchmarks & Performance](../benchmarks.md): measured numbers
~~~

---

**Previous:** [Type Arity](type-arity.md)
**Next:** [Extending the Simulation](extending-simulation.md)
