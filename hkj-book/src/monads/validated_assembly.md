<!-- description: Build a Java record from validated fields with Validated.fields(), and report every bad field at once, each error naming its field. -->

# Accumulating Assembly

_Building a record from N validated fields: every error reported at once._

~~~admonish info title="What You'll Learn"
- Build a record from independently validated fields with `Validated.fields()`, and report every bad field by name
- Read where each error belongs from its path, nested records included (`address.zip`)
- Guard a record whose constructor can refuse its fields, with `construct`
- Run the same assembly on `Validated`, `ValidationPath` or `EitherOrBoth`, and say what each does with a failed field
- Choose between the builder, `@GenerateAssembly`, `zipWithAccum` and the `mapN` family
~~~

~~~admonish example title="See Example Code"
**The code on this page is [ValidatedAssemblyBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java)**: the page includes it, so the build compiles and runs it. [ValidatedAssemblyBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBookTest.java) holds the errors it reports.

[ValidatedAssemblyExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/validated/ValidatedAssemblyExample.java) is a longer, standalone example.
~~~

## Which field was wrong? {#which-field-was-wrong}

[Validated](validated_monad.md#every-error-in-one-reply) checks fields independently and keeps every error. Unless a message spells out its field, as `street is required` does, a list such as `[not an email address, not a number]` names no field, and no code can read one back out. Whoever sent the request usually needs to know: a form shows each error beside its field, and an HTTP 422 body names the field it rejects. A request DTO becoming a domain record, or raw configuration becoming a settings object, needs every error *and* its field.

`Validated.fields()` gives each field a label as it joins the assembly, and every error that field reports carries the label.

---

## The front door: `fields()`

`Validated.fields()` opens a labelled assembly over `NonEmptyList<FieldError>`. A `FieldError` is a small record, `path` segments plus a `message`, with a `pathString()` such as `"address.zip"`. Each `field(label, value)` adds one validated field; `apply(...)` completes the assembly with a constructor reference or lambda of exactly the accumulated arity:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:fields}}
```

Three guarantees, all by construction:

- **Every bad field is reported at once.** Nothing short-circuits; accumulation is `NonEmptyList` concatenation.
- **Errors are located.** `field("email", ...)` prepends `"email"` onto each error's path via `FieldError.at`, so consumers can render `"email: not an email address"` or map errors onto form fields.
- **Errors emerge in field-declaration order.** The order of `field(...)` calls is the order of the errors, which makes downstream output (an HTTP 422 body, a CLI report) deterministic.

A leaf validator creates unlabelled errors with `FieldError.of("message")`; the assembly attaches the location.

### When the record refuses: `construct` {#construct}

A record often guards itself: a compact constructor that throws when its fields disagree. `apply` runs whatever function you hand it, so that exception escapes the assembly. It takes with it whatever an enclosing assembly had collected around it, and, on `EitherOrBoth`, the [tolerant carrier](#one-shape-three-carriers), the warnings this one had. `construct` completes the assembly the same way but guards the call. Once every field is valid, a `RuntimeException` the function throws becomes an unlabelled `FieldError` carrying the exception's message, or the fallback you pass when the message is missing or blank:

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

### Nesting composes

Because `at()` *prepends*, assembling a sub-record under an outer label prefixes all its inner paths:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:nesting}}
```

Nesting is also the way past the builder's limit. `apply` takes a function of at most 16 fields, matching the shipped `Function3` to `Function16`, so a record with more fields nests a sub-record per group. That usually improves the domain model anyway, and [`@GenerateAssembly`](#generating-the-companion-generateassembly) has no limit of its own.

---

## The generic flavour: `accumulate()`

When your error type is not `FieldError`, `Validated.accumulate()` assembles the same way over `NonEmptyList<X>` for any payload `X`, joining fields with `and(value)`. [Validated](validated_monad.md#every-error-in-one-reply) shows it. It takes no `Semigroup`, the object that says how to join two errors, because the errors are always a `NonEmptyList`, joined by appending. The first of these trips up both builders, and the second only `accumulate()`:

~~~admonish note title="Two inference notes"
- **Inline literals need explicit type arguments.** A chained stage receives no target typing, so an inline factory call such as `.field("age", Validated.invalidNel(FieldError.of("bad")))` infers `Object` and the compile error surfaces later, at `apply`. Write `Validated.<FieldError, Integer>invalidNel(...)` for inline literals; values with declared types (the results of leaf validators) need none.
- **Error payloads are invariant.** Suppose an assembly collects a sealed `ConfigError`, and a leaf returns `Validated<NonEmptyList<PortError>, A>` for one of its subtypes, `PortError`. The leaf does not widen to the assembly's payload. Widen at the leaf: `parsePort(raw).mapError(nel -> nel.map(e -> (ConfigError) e))`, or have leaf validators return the hierarchy's root type directly.
~~~

---

## One shape, three carriers

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

## Generating the companion: `@GenerateAssembly` {#generating-the-companion-generateassembly}

For a record you own, the annotation processor generates a companion that removes three ways the hand-written chain can go wrong:

- **Labels come from the component names**, so a typo or a rename cannot break them.
- **Field order cannot be wrong**: the stage methods are named, and must be called in order.
- **The generator sets no limit on the number of fields** of its own: it emits the exact arity, even past 16.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:generated_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/assembly/ValidatedAssemblyBook.java:generated_usage}}
```

A component whose type is itself annotated accepts its sub-companion's result directly, and the outer component name prefixes the inner paths (`address.zip`). The companion lives in the record's package, named `<Record>Assembly`; for a nested record the enclosing simple names are joined (`Outer.Inner` gives `OuterInnerAssembly`). Under the hood the companion merges through the same `Validated.ap` / `NonEmptyList.semigroup()` primitives as the builder, so the two agree on every input the record's constructor accepts. Where the constructor throws, they part. The builder's `apply` lets the exception propagate, while `assemble()` reports it as an unlabelled `FieldError` carrying the exception's message. That is the same [invariant guard](../mapping/absence.md#constructor-invariants) the generated mappings use. End the builder with [`construct`](#construct) instead of `apply` and the two agree again. Generic records are not supported yet; use the hand-written `fields()` builder for records you cannot annotate. The companion names the record and each component's type from the record's package, so the processor refuses one that is `private`, or nested in a `private` class, naming the class to change ([Compiler Messages](../optics/compiler_errors.md#companion-cannot-be-reached)).

---

## Choosing the right tool

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
- No `Semigroup` argument and no `Kind`. `apply` takes up to 16 fields; a wider record nests sub-records, or uses `@GenerateAssembly`.
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
- [Many Edits at Once](../optics/multi_edit.md): the same all-errors-at-once model for *updating* existing values
~~~

---

**Previous:** [Validated](validated_monad.md)
**Next:** [Try](try_monad.md)
