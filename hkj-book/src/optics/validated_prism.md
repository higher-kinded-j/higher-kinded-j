# Validated Prisms

_The smart-constructor optic: a `Prism` whose match says **why not** — and all the reasons at once._

A `Prism<S, A>`'s match answers yes/no (`Optional`). That is the wrong shape for a **validated boundary** — the "parse, don't validate" pattern where an unvalidated wire value becomes an always-valid domain value. The forward direction wants *reasons* (`"not an email"`), located and accumulated; the backward direction is *total* (a valid domain value always renders). `ValidatedPrism` encodes exactly that asymmetry:

``` java
import org.higherkindedj.optics.validated.ValidatedPrism;

ValidatedPrism<String, EmailAddress> email = ValidatedPrism.of(
    EmailAddress::parse,      // String -> Validated<NonEmptyList<FieldError>, EmailAddress>
    EmailAddress::value);     // EmailAddress -> String   (total)

Validated<NonEmptyList<FieldError>, EmailAddress> parsed = email.parse("  NOPE ");

EmailAddress addr = /* a valid domain value */;
String rendered = email.build(addr);              // always succeeds

ValidationPath<NonEmptyList<FieldError>, EmailAddress> railway =
    email.parsePath("ada@corp.example");
```

## Composition: nesting short-circuits, siblings accumulate

`andThen` goes *deeper into structure*, so it **short-circuits** — you cannot parse the inner value if the outer parse failed. To report every bad field of a record at once, parse each field with its own prism and assemble the *siblings* with [`fields()` / `accumulate()`](../monads/validated_assembly.md) or the [`Edits` builder](multi_edit.md). This is the same split `ValidationPath` draws between `via` and sibling accumulation.

Only compositions that preserve the **total build** yield a `ValidatedPrism`:

| Compose with | Result | Notes |
|---|---|---|
| `ValidatedPrism<A, B>` | `ValidatedPrism<S, B>` | parse short-circuits; build composes |
| `Iso<A, B>` | `ValidatedPrism<S, B>` | parse maps through; build round-trips |
| `Prism<A, B>` + a `FieldError` reason | `ValidatedPrism<S, B>` | the reason speaks for the prism's empty case |
| `Lens<A, B>` | — deliberately absent | a lens needs a base to write into, so no total `B -> S` build exists |

## Bridging the lattice

- `ValidatedPrism.fromIso(iso)` — a parse that never fails.
- `ValidatedPrism.fromPrism(prism, reason)` — lift a plain prism by supplying the reason its `Optional.empty` cannot express.
- `toPrism()` / `toAffine()` — forget the reasons (the affine's `set` leaves non-parsing sources unchanged, preserving the affine laws).

## Laws

A lawful validated boundary satisfies both round trips, verified with [`ValidatedPrismLaws`](../tooling/test_assertions.md) from `hkj-test`:

``` java
ValidatedPrismLaws.assertValidatedPrismLaws(email, "ada@corp.example", "not-an-email");
// parse-build: parse(build(a)) == Valid(a)
// build-parse: parse(s) == Valid(a)  =>  build(a) == s   (no lossy parse-normalise)
```

The section law is the sharp one: a prism whose `build` normalises (zero-padding, trimming) breaks it — normalise in `parse`, render faithfully in `build`.

---

~~~admonish info title="Key Takeaways"
* **`parse` is fallible and accumulating** (`Validated<NonEmptyList<FieldError>, A>`); **`build` is total** — the parse-don't-validate asymmetry as an optic
* **Nesting short-circuits; siblings accumulate** via the assembly builders or `Edits`
* **Only build-preserving compositions exist**: `ValidatedPrism`, `Iso`, and `Prism`-with-a-reason; `Lens` deliberately not
* **Both round-trip laws are published** in `hkj-test`; the section law forbids lossy build-normalisation
* **`parsePath` lands on the railway** (`ValidationPath`) directly
~~~

~~~admonish info title="Hands-On Learning"
Practice the boundary in [Tutorial 25: ValidatedPrism](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial25_ValidatedPrism.java) (3 exercises, ~10 minutes), and see the runnable [`ValidatedPrismExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ValidatedPrismExample.java).
~~~

~~~admonish tip title="See Also"
- [Prisms](prisms.md) - The yes/no match this type upgrades
- [Accumulating Assembly](../monads/validated_assembly.md) - Sibling-field accumulation for multi-field parses
- [Multi-Edit and Sparse Updates](multi_edit.md) - The update-side counterpart
~~~

---

**Previous:** [Prism Toolkit](prism_toolkit.md)
**Next:** [Affines](affine.md)
