# EitherOrBothPath

`EitherOrBothPath<L, A>` wraps [`EitherOrBoth<L, A>`](../monads/either_or_both_monad.md) for
computations that succeed but may also carry **non-fatal warnings**. It is the railway companion to
`ValidationPath`: where `ValidationPath` is success *or* accumulated errors, `EitherOrBothPath` adds a
third outcome: a value *and* warnings.

~~~admonish info title="What You'll Learn"
- Creating EitherOrBothPath instances, including the `rightNel` / `leftNel` / `bothNel` shortcuts
- Short-circuit sequencing with `via` (Left stops; Both carries warnings forward)
- Parallel, collect-everything accumulation with `zipWithAccum` / `andAlso`
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
EitherOrBothPath<NonEmptyList<String>, Config> ok      = Path.rightNel(config);
EitherOrBothPath<NonEmptyList<String>, Config> warned  = Path.bothNel("deprecated key", config);
EitherOrBothPath<NonEmptyList<String>, Config> failed  = Path.leftNel("config missing");
```

When you need a custom warning channel, supply the `Semigroup` explicitly:

```java
EitherOrBothPath<String, Integer> p = Path.both("warn", 42, Semigroups.string("; "));
```

---

## Two Modes of Composition

Like `ValidationPath`, `EitherOrBothPath` offers both short-circuit and accumulating composition.

### Short-circuit (`via`)

`via` sequences dependent steps. A `Left` stops the chain; a `Both` carries its warnings forward and
accumulates any the next step produces:

```java
EitherOrBothPath<NonEmptyList<String>, Integer> result =
    Path.<String, Integer>bothNel("uses deprecated key", 8)
        .via(value -> value < 10
            ? Path.bothNel("value is low", value)
            : Path.rightNel(value));

result.run();        // Both([uses deprecated key, value is low], 8)
result.warnings();   // Just([uses deprecated key, value is low])
result.getOrElse(0); // 8
```

### Accumulating (`zipWithAccum`)

`zipWithAccum` combines *independent* results and collects **every** warning, even across a fatal
`Left` (the `Validated`-style accumulation that the monadic operations deliberately do not do):

```java
EitherOrBothPath<NonEmptyList<String>, String> name = Path.bothNel("name was trimmed", "Ada");
EitherOrBothPath<NonEmptyList<String>, Integer> age = Path.bothNel("age defaulted", 30);

EitherOrBothPath<NonEmptyList<String>, String> reg =
    name.zipWithAccum(age, (n, a) -> n + " (" + a + ")");
// Both([name was trimmed, age defaulted], "Ada (30)")
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

## Recovery

`recover` / `recoverWith` / `orElse` act on a fatal `Left` only; a `Both` is already a success and is
passed through unchanged:

```java
Path.<String, Integer>leftNel("config missing").recover(errors -> 0).run();   // Right(0)
Path.<String, Integer>bothNel("deprecated", 42).recover(errors -> 0).run();    // Both([deprecated], 42)
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
