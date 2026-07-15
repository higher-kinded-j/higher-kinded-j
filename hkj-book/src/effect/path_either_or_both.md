# EitherOrBothPath

~~~admonish example title="See Example Code"
**The code on this page is [EitherOrBothPathBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/effect/EitherOrBothPathBook.java)** - the page includes it directly, so it is compiled and run by the build.
~~~


`EitherOrBothPath<L, A>` wraps [`EitherOrBoth<L, A>`](../monads/either_or_both_monad.md) for
computations that succeed but may also carry **non-fatal warnings**. It is the railway companion to
`ValidationPath`: where `ValidationPath` is success *or* accumulated errors, `EitherOrBothPath` adds a
third outcome: a value *and* warnings.

~~~admonish info title="What You'll Learn"
- Creating EitherOrBothPath instances, including the `rightNel` / `leftNel` / `bothNel` shortcuts
- Short-circuit sequencing with `via` (Left stops; Both carries warnings forward)
- Parallel, collect-everything accumulation with `zipWithAccum` / `andAlso`
- Open-arity tolerant assembly with `EitherOrBoth.accumulate()` / `fields()`
- Recovering from a fatal `Left` at the boundary
- Converting to other paths, deciding what happens to warnings
~~~

~~~admonish example title="See Example Code:"
[EitherOrBothPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/EitherOrBothPathExample.java)
~~~

---

## Creation

The `rightNel` / `leftNel` / `bothNel` factories bake in `NonEmptyList.semigroup()`, so the common
case needs no `Semigroup` argument and no manual single-warning wrapping:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/EitherOrBothPathBook.java:create}}
```

When you need a custom warning channel, supply the `Semigroup` explicitly:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/EitherOrBothPathBook.java:custom_semigroup}}
```

---

## The Railway View

`via` runs a dependent step on the success track. A `Right` flows straight through; a `Both` carries its
warnings forward and accumulates any the next step adds; a `Left` derails and short-circuits the rest of
the chain. `recover` can switch a `Left` back onto the success track. (The accumulating `zipWithAccum`
mode, which collects every warning even across a `Left`, is covered below.)

<pre class="hkj-railway-diagram">
    <span style="color:#4CAF50"><b>Right</b>  ═══●═══════════════●═══▶  value</span>
    <span style="color:#FFB300"><b>Both</b>   ═══●═══════════════●═══▶  value + warnings  (via threads and accumulates them)</span>
    <span style="color:#FFB300">           via f           via g</span>
                ╲               ╲   via returns Left
                 ╲               ╲
    <span style="color:#F44336"><b>Left</b>   ────●───────────────●───▶  fatal, f never runs</span>
                                    │
                               <span style="color:#4CAF50">recover</span>   switch back to the success track
                                    │
    <span style="color:#4CAF50">                                ●═══▶  recovered Right</span>
</pre>

---

## Two Modes of Composition

Like `ValidationPath`, `EitherOrBothPath` offers both short-circuit and accumulating composition.

### Short-circuit (`via`)

`via` sequences dependent steps. A `Left` stops the chain; a `Both` carries its warnings forward and
accumulates any the next step produces:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/EitherOrBothPathBook.java:via}}
```

### Accumulating (`zipWithAccum`)

`zipWithAccum` combines *independent* results and collects **every** warning, even across a fatal
`Left` (the `Validated`-style accumulation that the monadic operations deliberately do not do):

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/EitherOrBothPathBook.java:zip_accum}}
```

`andAlso` / `andThen` are conveniences over `zipWithAccum` that keep one side's value while still
accumulating both sides' warnings.

~~~admonish warning title="`via`/`zipWith` short-circuit; `zipWithAccum` accumulates"
The Chainable combinators (`via`, `zipWith`) follow the monad: a `Left` short-circuits and the other
side's warnings are dropped. The Accumulating combinators (`zipWithAccum`, `andAlso`) collect every
warning regardless. Choose by intent: sequential dependency → `via`; independent validations →
`zipWithAccum`.
~~~

---

### Open-arity assembly (`EitherOrBoth.accumulate()`)

For N independent fields, the staged assembly on the core type extends `zipWithAccum` to open arity: warnings accumulate while the value keeps flowing, and the result wraps back into a path with `Path.eitherOrBoth` when railway composition should continue.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/EitherOrBothPathBook.java:accumulate}}
```

See [Accumulating Assembly](../monads/validated_assembly.md).

---

## Recovery

`recover` / `recoverWith` / `orElse` act on a fatal `Left` only; a `Both` is already a success and is
passed through unchanged:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/EitherOrBothPathBook.java:recover}}
```

To transform the warning type, use `mapErrorWith(mapper, newSemigroup)` (a new `Semigroup` is needed
for the new type).

---

## Conversions

| Method | Effect |
|--------|--------|
| `toEitherPathDroppingWarnings()` | `Both` becomes `Right` (warnings dropped) |
| `toEitherPathFailingOnWarnings()` | `Both` becomes `Left` (warnings fatal) |
| `toValidationPath()` | `Both` becomes `Valid` (warnings dropped) |
| `toMaybePath()` / `toOptionalPath()` | keep the success value, discard the left channel |
| `toTryPath(errorToException)` | `Left` becomes a failure; `Right`/`Both` a success |

---

## At the HTTP Boundary (hkj-spring)

When returned from a Spring controller, `EitherOrBothPath` maps to:

- `Right` → `200 OK` with the value as the body.
- `Both` → `200 OK` with the value as the body, and the warnings surfaced (never silently dropped) in
  the `X-Hkj-Warnings` response header.
- `Left` → a 4xx/5xx response whose status is resolved by the configured `ErrorStatusCodeStrategy`.

---

## When To Use It

| Need | Use |
|------|-----|
| Success that may carry non-fatal warnings | **`EitherOrBothPath`** |
| Pure error accumulation, no partial value | [ValidationPath](path_validation.md) |
| Clean success-or-failure, short-circuit | [EitherPath](path_either.md) |

~~~admonish tip title="See Also"
- [EitherOrBoth](../monads/either_or_both_monad.md) - the underlying type and its `flatMap` contract
- [ValidationPath](path_validation.md) - pure accumulating validation
- [EitherPath](path_either.md) - short-circuit success-or-failure
- [NonEmptyList](../monads/nonemptylist_monad.md) - the warning channel
~~~

---

**Previous:** [ValidationPath](path_validation.md)
**Next:** [IOPath](path_io.md)
