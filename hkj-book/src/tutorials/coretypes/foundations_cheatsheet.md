# Foundations Journey: Cheatsheet

A single-page reference for the four tutorials in the Foundations journey. Two columns: how we wrote it before, how we write it now.

~~~admonish info title="Scope"
- Kind, Functor, Applicative, Monad
- Either, List, Maybe, Validated
- The widen / call / narrow pattern at the typeclass layer
~~~

---

## Kind, widen, narrow (Tutorial 01)

| Pattern | Imperative Java | Higher-Kinded-J |
|---------|------------------|-----------------|
| Reach for a generic over containers | One method per container, forever | `Kind<F, A>` and a `Functor`/`Monad` instance |
| Lift a concrete type to `Kind` | n/a | `EITHER.widen(either)` / `LIST.widen(list)` / etc. |
| Drop a `Kind` back to the concrete type | n/a | `EITHER.narrow(kind)` / `LIST.narrow(kind)` / etc. |
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
| Combine N independent results | manual conditional ladder | `app.map2(...)` ... `app.map5(...)` on the typeclass instance |
| Lift a plain value into a container | `Either.right(x)`, `Optional.of(x)` | `app.of(x)` or the concrete factory |
| Run N futures and combine their outputs | `CompletableFuture.allOf(...)` then unpack | `app.map2(widen(f1), widen(f2), combiner)` |
| Validate a form, fail-fast | series of early returns | `EitherMonad` + `mapN` (short-circuits on first Left) |
| Validate a form, accumulate errors | `Set<ConstraintViolation>` from Bean Validation | `ValidatedMonad` + `mapN` (the `Semigroup` decides how to combine) |

Common stumble: calling `value1.map2(value2, ...)` directly on the concrete type. `Either` does not carry `map2` as an instance method. Combinators across multiple inputs live on the `Applicative` typeclass instance: get the instance, widen, call, narrow.

---

## Monad: `flatMap` (Tutorial 04)

| Pattern | Imperative Java | Higher-Kinded-J |
|---------|------------------|-----------------|
| Chain a step that depends on the previous one | `if (e.isError()) return e;` ladders | `.flatMap(...)` |
| Parse → validate → compute | nested `try`/`catch` | `.flatMap(parse).flatMap(validate).flatMap(compute)` |
| Look up X then look up Y(X) on `Optional` | `opt.flatMap(...)` | `.flatMap(...)` (same shape) |
| Compose async steps | `future.thenCompose(...)` | `.flatMap(...)` (same shape, on `CompletableFuture`) |
| Cartesian product of two lists | nested `for` loops + builder | `monad.flatMap(x -> monad.map(y -> ..., ys), xs)` |

Common stumble: using `flatMap` when steps are independent. `flatMap` says "this depends on that"; using it for independent inputs forces a sequential mental model and (on `Validated`) loses the accumulating semantics. Reach for `mapN` instead.

---

## The `widen` / call / `narrow` pattern

Whenever we move from a concrete type into typeclass-generic code, we follow three steps:

```
   1. widen     EITHER.widen(either)            // Either<L, A>     -> Kind<EitherKind.Witness<L>, A>
   2. operate   monad.map(f, kind)              // operate at the Kind level
   3. narrow    EITHER.narrow(result)           // Kind<...>        -> Either<L, B>
```

Same shape for `LIST`/`MAYBE`/`VALIDATED`/`OPTIONAL`/`FUTURE`/etc. Once we know it for one container, we know it for every container.

---

## Decision table

| Are the steps independent? | Can a step decide the next one? | Reach for |
|---|---|---|
| Yes | n/a | **Applicative** (`map2` / `mapN`) |
| No | Yes | **Monad** (`flatMap`) |
| Yes, but want all errors back | n/a | **Applicative** on `Validated` |
| Yes, fail fast on first error | n/a | **Applicative** on `Either` |
| Single transformation | n/a | **Functor** (`map`) |
| Just need to package up a value | n/a | **Applicative.of** (or the concrete factory) |

---

## Where this lands in [One Line, Six Layers](../../hkts/one_line_six_layers.md)

```
   repo.find(id)              .toEitherPath()      .focus().attributes().at(key)
   └── Effect Path ───────────┤                    └── Optic ──────────────────┐
       absence as MaybePath   │                        traversal into a record │
                              │                                                │
                              └── Natural transformation                       │
                                  MaybePath ~> EitherPath                      │
                                                                               │
   .modify(spec::validateAndCoerce)             .flatMap(repo::save);          │
   └── Functor (under the optic) ───┐           └── Monad ─────────────────────┘
       Tutorial 02                   │               Tutorial 04
                                    │
                                    └── Type class instance dispatched at compile time
                                        EitherFunctor / EitherMonad
                                        Tutorials 02-04
```

Tutorials 02-04 cover the lower three layers of the One Line, Six Layers diagram. Tutorial 00 walks through every layer end-to-end as a single working expression.

---

**See also:** [Functor](../../functional/functor.md) · [Applicative](../../functional/applicative.md) · [Monad](../../functional/monad.md) · [Lifting the Hood](../../hkts/lifting_the_hood.md) · [Foundations FAQ](../../hkts/faq.md)
