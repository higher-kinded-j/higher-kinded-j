# Accumulating Assembly

_Building a record from N validated fields: every error reported at once._

~~~admonish info title="What You'll Learn"
- Assembling a record from independently validated fields with `Validated.fields()` and `Validated.accumulate()`: every error reported at once, no `Semigroup` argument, no arity wall, no `Kind`
- How `field(label, value)` attaches a path to each error, and how nesting composes paths (`address.zip`)
- The guarantee that errors emerge in field-declaration order
- The same assembly shape on all three carriers: `Validated` (strict), `ValidationPath` (railway), and `EitherOrBoth` (tolerant, value keeps flowing)
- When to reach for the builder versus `zipWithAccum` or the `mapN` family
- Guarding a constructor that may refuse the fields, with `construct`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [ValidatedAssemblyBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java)** - the page includes it directly, so it is compiled and run by the build.

[ValidatedAssemblyExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/validated/ValidatedAssemblyExample.java)
~~~

## The Problem: Assembling N Fields Was the Hard Part

[Validated](validated_monad.md) accumulates errors, and [NonEmptyList](nonemptylist_monad.md) gives the errors an honest carrier. But the everyday task (a request DTO becomes a domain aggregate; a raw config becomes a settings object) is assembling a record from N validated fields, and until now that meant choosing between:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:hkt_generic}}
```

- **An arity wall.** `map2..map5` stop at five fields; `zipWithAccum` is binary and `zipWith3Accum` ternary.
- **Ceremony.** The `Kind` wrappers and the explicit `Semigroup` argument surface exactly when you want the least friction.
- **Unlocated errors.** An accumulated failure is a flat list; nothing says *which field* each error belongs to.

The staged assembly builder removes all three at once.

---

## The Front Door: `fields()`

`Validated.fields()` opens a labelled assembly over `NonEmptyList<FieldError>`. Each `field(label, value)` adds one validated field; `apply(...)` completes the assembly with a constructor reference or lambda of exactly the accumulated arity:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:fields}}
```

Three guarantees, all by construction:

- **Every bad field is reported at once.** Nothing short-circuits; accumulation is `NonEmptyList` concatenation.
- **Errors are located.** `field("email", ...)` prepends `"email"` onto each error's path via `FieldError.at`, so consumers can render `"email: not an email address"` or map errors onto form fields.
- **Errors emerge in field-declaration order.** The order of `field(...)` calls is the order of the errors, which makes downstream output (an HTTP 422 body, a CLI report) deterministic.

A leaf validator creates unlabelled errors with `FieldError.of("message")`; the assembly attaches the location. `FieldError` is a small record (`path` segments plus a `message`) with a `pathString()` such as `"address.zip"`.

### When the Record Refuses: `construct` {#construct}

A record often guards itself: a compact constructor that throws when its fields disagree. `apply` runs whatever function you hand it, so that exception escapes the assembly, taking with it whatever an enclosing assembly had collected around it, and, on the tolerant carrier, the warnings this one had. `construct` completes the assembly the same way but guards the call: once every field is valid, a `RuntimeException` the function throws becomes an unlabelled `FieldError` carrying the exception's message, or the fallback you pass when the message is missing or blank:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:construct_record}}
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:construct}}
```

The rules are the generated mappings' own ([a record's own invariants](../mapping/absence.md#constructor-invariants)):

- **The function runs last.** It needs every field, so it runs only once all of them are valid, and a record reports either its fields' errors or its refusal, never both.
- **The refusal is unlabelled**, so the `field(label, ...)` that nests the assembly locates it: `window: closes must be after opens`.
- **Any `RuntimeException` counts**, so a bug in the function reaches the caller as its message, with its stack trace dropped. Keep the function to checks on its arguments, with messages written for whoever reads the errors.
- **Only the function is guarded.** A `null` it returns still throws, as it does from `apply`; only what the function itself throws is a refusal.
- **Every labelled builder has it**: `Validated.fields()`, `Path.fields()`, and `EitherOrBoth.fields()`. On the tolerant carrier a *failed* field still short-circuits the function, while a `Both` field's warning does not, so a refusal there comes back as a `Left` carrying those warnings and the refusal. The generic `accumulate()` builders have no `FieldError` to build, so they end in `apply` only.

### Nesting Composes

Because `at()` *prepends*, assembling a sub-record under an outer label prefixes all its inner paths:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:nesting}}
```

This also doubles as the escape hatch for very wide records: `apply` overloads exist up to arity 16 (matching the shipped `Function3..Function16`); a record with more fields nests a sub-record per group, which usually improves the domain model anyway.

---

## The Generic Flavour: `accumulate()`

When your error type is not `FieldError`, `accumulate()` gives the same open-arity assembly for any payload `X`, carried as `NonEmptyList<X>`. Fields join with `and(value)`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:accumulate}}
```

There is still no `Semigroup` argument: the carrier is fixed to `NonEmptyList`, and accumulation is concatenation.

~~~admonish note title="Two inference notes"
- **Inline literals need a type witness.** A chained stage receives no target typing, so an inline factory call such as `.field("age", Validated.invalidNel(FieldError.of("bad")))` infers `Object` and the compile error surfaces later, at `apply`. Write `Validated.<FieldError, Integer>invalidNel(...)` for inline literals; values with declared types (the results of leaf validators) need no witness.
- **Error payloads are invariant.** Leaves typed with a subtype of a sealed error hierarchy (`Validated<NonEmptyList<PortError>, A>`) do not widen automatically to the assembly's payload (`ConfigError`). Widen at the leaf: `parsePort(raw).mapError(nel -> nel.map(e -> (ConfigError) e))`, or have leaf validators return the hierarchy's root type directly.
~~~

---

## One Shape, Three Carriers

The same two entry points exist on each carrier, so the assembly reads identically whichever error strategy the surrounding code uses:

| Carrier | Entries | Result | Behaviour |
|---|---|---|---|
| `Validated` | `Validated.fields()` / `Validated.accumulate()` | `Validated<NonEmptyList<E>, R>` | strict: any invalid field fails the assembly, all errors kept |
| `ValidationPath` | `Path.fields()` / `Path.accumulate()` | `ValidationPath<NonEmptyList<E>, R>` | the railway flavour; composes onward with `via`, `zipWithAccum`, `recover` |
| `EitherOrBoth` | `EitherOrBoth.fields()` / `EitherOrBoth.accumulate()` | `EitherOrBoth<NonEmptyList<E>, R>` | tolerant: warnings accumulate (`Both`) while the value keeps flowing; any `Left` dominates, still keeping every warning |

The tolerant flavour is the natural fit for lenient config parsing:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:eob_accumulate}}
```

Under the hood every stage transition delegates to the existing accumulation primitives: `Validated.ap` with `NonEmptyList.semigroup()`, `ValidationPath.zipWithAccum`, and `EitherOrBoth.zipWithAccum`. The builder introduces no second accumulation mechanism.

---

## Generating the Companion: `@GenerateAssembly`

For records you own, the annotation processor generates a per-record companion that removes the three remaining failure modes of the hand-written chain: labels come from the component names (typo-proof, rename-safe), field order cannot be wrong (named, order-enforcing stage methods), and there is no arity ceiling (the generator emits exact arity, even past 16).

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:generated_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:generated_usage}}
```

A component whose type is itself annotated accepts its sub-companion's result directly, and the outer component name prefixes the inner paths (`address.zip`). The companion lives in the record's package, named `<Record>Assembly`; for a nested record the enclosing simple names are joined (`Outer.Inner` gives `OuterInnerAssembly`). Under the hood the companion merges through the same `Validated.ap` / `NonEmptyList.semigroup()` primitives as the builder, so the two agree on every input the record's constructor accepts. Where the constructor throws, they part: the builder's `apply` runs whatever function you hand it, so the exception propagates, while `assemble()` reports it as an unlabelled `FieldError` carrying the exception's message, the same [invariant guard](../mapping/absence.md#constructor-invariants) the generated mappings use. End the builder with [`construct`](#construct) instead of `apply` and the two agree again. Generic records are not supported; use the hand-written `fields()` builder for records you cannot annotate. The companion names the record and each component's type from the record's package, so the processor refuses one that is `private`, or nested in a `private` class, naming the class to change ([Compiler Messages](../optics/compiler_errors.md#companion-cannot-be-reached)).

---

## Choosing the Right Tool

| You are combining... | Reach for |
|---|---|
| N independent fields into a record | `fields()` / `accumulate()` |
| N validated fields into an **existing** value (sparse update / `PATCH`) | [`Edits.accumulate`](../optics/multi_edit.md) |
| A record you own, assembled in many places | [`@GenerateAssembly`](#generating-the-companion-generateassembly) |
| Two or three values, inline, no labels needed | `zipWithAccum` / `zipWith3Accum` on the Path types, or `EitherOrBoth.zipWithAccum` |
| Values inside generic `Kind`-polymorphic code | the `Applicative` `mapN` family |
| Steps where later ones depend on earlier results | `flatMap` / `via` (short-circuiting, by design) |

~~~admonish tip title="Testing located errors"
`hkj-test` asserts a whole accumulation as rendered `"path: message"` lines with `assertThatValidated(result).hasFieldErrors(...)`, and takes a single error apart with `assertThatFieldError`:

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBookTest.java:field_error}}
```
~~~

~~~admonish info title="Key Takeaways"
- `fields()` assembles a record from N validated fields with located errors; `accumulate()` is the generic-payload twin.
- No `Semigroup` argument, no `Kind`, no arity wall up to 16; wider records nest sub-records.
- Errors always emerge in field-declaration order.
- Nesting prepends the outer label: `"address.zip"`.
- `construct(Record::new, "not a valid Record")` guards a constructor that may refuse the fields; `apply` does not.
- The same shape exists on `Validated`, `ValidationPath` (via `Path`), and `EitherOrBoth` (tolerant).
~~~

~~~admonish tip title="See Also"
- [Validated](validated_monad.md): the underlying strict accumulating type
- [NonEmptyList](nonemptylist_monad.md): the error carrier the builder is fixed to
- [EitherOrBoth](either_or_both_monad.md): the tolerant carrier
- [ValidationPath](../effect/path_validation.md): the railway validation API
- [Validated Prisms](../optics/validated_prism.md): a leaf parser with a faithful render-back *is* a `ValidatedPrism`: pass `vp.parse(raw)` to `field(label, ...)`. (`fields()` accumulates *siblings*; a prism's `andThen` short-circuits *nesting*.)
- [Applicative](../functional/applicative.md): the `mapN` family for `Kind`-generic code
- [Multi-Edit and Sparse Updates](../optics/multi_edit.md): the same all-errors-at-once model for *updating* existing values
~~~

---

**Previous:** [Validated](validated_monad.md)
**Next:** [VTask](vtask_monad.md)
