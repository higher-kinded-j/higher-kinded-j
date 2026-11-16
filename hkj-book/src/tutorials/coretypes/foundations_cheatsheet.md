# Foundations Journey: Cheatsheet

A single-page reference for the Foundations journey's tutorials. Its tables set the Java we wrote before beside the Higher-Kinded-J we write now.

~~~admonish info title="Scope"
- Kind, Functor, Applicative, Monad
- Either, List, Maybe, Validated
- The widen, call, narrow pattern at the type class level
~~~

---

## Kind, widen, narrow (Tutorial 01)

| Pattern | Imperative Java | Higher-Kinded-J |
|---------|------------------|-----------------|
| Reach for a generic over containers | One method per container, forever | `Kind<F, A>` and a `Functor`/`Monad` instance |
| Lift a concrete type to `Kind` | n/a | `EITHER.widen(either)`, `LIST.widen(list)`, and so on |
| Drop a `Kind` back to the concrete type | n/a | `EITHER.narrow(kind)`, `LIST.narrow(kind)`, and so on |
| Talk about "the shape F" at the type level | not expressible | the witness type, e.g. `EitherKind.Witness<L>` |

Common stumble: narrowing with the wrong helper. The witness type is exactly what catches this; it is a compile error, not a runtime surprise.

---

## Functor: `map` (Tutorial 02)

| Pattern | Imperative Java | Higher-Kinded-J |
|---------|------------------|-----------------|
| Transform every element of a list | `xs.stream().map(f).toList()` | `monad.map(f, LIST.widen(xs))` |
| Transform a possibly-absent value | `opt.map(f)` | `monad.map(f, MAYBE.widen(maybe))` |
| Transform the success side of a result | `result.map(f)` (custom Result) | `either.map(f)` or `monad.map(f, EITHER.widen(either))` |
| Transform a future | `future.thenApply(f)` | `monad.map(f, FUTURE.widen(future))` |
| Method reference inside a transform | `String::toUpperCase` | unchanged (works exactly the same) |

Common stumble: using `map` with a function that returns a wrapped value. The result is `F<F<B>>` (nested). The fix is `flatMap` (see Tutorial 04).

---

## Applicative: `map2` … `map5` (Tutorial 03)

| Pattern | Imperative Java | Higher-Kinded-J |
|---------|------------------|-----------------|
| Combine N independent results | manual conditional ladder | `app.map2(...)` to `app.map5(...)` on the `Applicative` instance |
| Lift a plain value into a container | `Either.right(x)`, `Optional.of(x)` | `app.of(x)` or the concrete factory |
| Run N futures and combine their outputs | `CompletableFuture.allOf(...)` then unpack | `app.map2(widen(f1), widen(f2), combiner)` |
| Validate a form, fail-fast | series of early returns | `Either`'s instance and `map2` to `map5`, which stop at the first `Left` |
| Validate a form, accumulate errors | `Set<ConstraintViolation>` from Bean Validation | `Validated`'s instance and `map2` to `map5`; the `Semigroup` decides how errors combine |

Common stumble: calling `value1.map2(value2, ...)` directly on the concrete type. `Either` does not carry `map2` as an instance method. Combinators across several inputs live on the `Applicative` instance: get the instance, widen, call, narrow.

---

## Monad: `flatMap` (Tutorial 04)

| Pattern | Imperative Java | Higher-Kinded-J |
|---------|------------------|-----------------|
| Chain a step that depends on the previous one | `if (e.isError()) return e;` ladders | `.flatMap(...)` |
| Parse → validate → compute | nested `try`/`catch` | `.flatMap(parse)`, then `.flatMap(validate)`, then `.flatMap(compute)` |
| Look up X then look up Y(X) on `Optional` | `opt.flatMap(...)` | `.flatMap(...)` (same shape) |
| Compose async steps | `future.thenCompose(...)` | `.flatMap(...)` (same shape, on `CompletableFuture`) |
| Cartesian product of two lists | nested `for` loops + builder | `monad.flatMap(x -> monad.map(y -> ..., ys), xs)` |

Common stumble: using `flatMap` when steps are independent. `flatMap` says "this depends on that"; using it for independent inputs forces a sequential mental model and (on `Validated`) loses the accumulating behaviour. Reach for `map2` to `map5` instead.

---

## The `widen`, call, `narrow` pattern {#the-widen--call--narrow-pattern}

Whenever we move from a concrete type into code written for any container, we follow three steps:

| Step | Call | What changes |
|---|---|---|
| 1. Widen | `EITHER.widen(either)` | `Either<L, A>` becomes `Kind<EitherKind.Witness<L>, A>` |
| 2. Call | `monad.map(f, kind)` | the type class works on the `Kind` |
| 3. Narrow | `EITHER.narrow(result)` | the `Kind` becomes an `Either<L, B>` again |

Same shape for `LIST`, `MAYBE`, `VALIDATED`, `OPTIONAL`, `FUTURE` and the rest. Once we know it for one container, we know it for every container.

---

## Decision table

| Are the steps independent? | Can a step decide the next one? | Reach for |
|---|---|---|
| Yes | n/a | **Applicative** (`map2` to `map5`) |
| No | Yes | **Monad** (`flatMap`) |
| Yes, but want all errors back | n/a | **Applicative** on `Validated` |
| Yes, fail fast on first error | n/a | **Applicative** on `Either` |
| Single transformation | n/a | **Functor** (`map`) |
| Just need to package up a value | n/a | **Applicative.of** (or the concrete factory) |

---

## Where this lands in [One Line, Six Layers](../../hkts/one_line_six_layers.md)

The line that page takes apart changes the email on an order, and each step of it belongs to a layer these tutorials practise:

| Step of the line | Layer | Tutorial |
|---|---|---|
| `orders.find(id)` | An effect type: a `MaybePath<Order>` | 00 |
| `.toEitherPath(OrderError.notFound(id))` | A natural transformation, from `MaybePath` to `EitherPath` | 00 |
| `OrderFocus.customer().email().value()` | An optic, three records deep, whose `set` gives a new order | 00 |
| `.map(order -> ...)` | Functor | 02 |
| `.via(orders::save)` | Monad: `via` is `flatMap` on a Path | 04 |
| The whole expression | The algebra: the same `map` and `flatMap` as type classes over `Kind` | 00 (Exercise 6) and 01 |

Tutorial 00 writes the whole line, one layer at a time; Tutorials 02 and 04 practise the type classes behind `map` and `via`.

---

**See also:** [Functor](../../functional/functor.md) · [Applicative](../../functional/applicative.md) · [Monad](../../functional/monad.md) · [Lifting the Hood](../../hkts/lifting_the_hood.md) · [Foundations FAQ](../../hkts/faq.md)

---

**Previous:** [Foundations](foundations_journey.md)
**Next:** [Error Handling](error_handling_journey.md)
