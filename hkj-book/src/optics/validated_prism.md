# Validated Prisms

_The smart-constructor optic: a `Prism` whose match says **why not**, and all the reasons at once._

~~~admonish info title="What You'll Learn"
- Why a validated boundary needs a fallible, accumulating `parse` and a total `build`: the "parse, don't validate" asymmetry captured as an optic
- Constructing a `ValidatedPrism` with `ValidatedPrism.of`, and landing on the railway with `parsePath`
- How composition splits: `andThen` short-circuits into structure while sibling fields accumulate every reason
- Which compositions preserve the total build (`ValidatedPrism`, `Iso`, and `Prism`-with-a-reason) and why `Lens` cannot
- Bridging the optic lattice with `fromIso`, `fromPrism`, `toPrism`, and `toAffine`
- The two round-trip laws, and why the second forbids a lossy, normalising `build`
~~~

A `Prism<S, A>` answers one question about a value: does it match this shape, yes or no? Its match returns `Optional<A>`, present or empty. At a **validated boundary**, where a raw wire value (a `String` off the network) must become an always-valid domain value (an `EmailAddress`), yes/no is too blunt. A rejected value needs to say *why*, and ideally give *every* reason at once (`"not an email"`, `"too long"`), each located to the field it came from. The reverse direction is never in doubt: a domain value you already hold always renders back to a string.

`ValidatedPrism<S, A>` captures that asymmetry as two directions with different shapes. `parse` is fallible and accumulating; `build` is total:

```
                 parse  (fallible, accumulating)
   wire value  ───────────────────────────────▶  domain value
   String                                          EmailAddress
   (unvalidated)  ◀───────────────────────────────  (always valid)
                 build  (total, always succeeds)

   parse("  NOPE ")          =  Invalid[ "not an email" ]   (every reason at once)
   parse("ada@corp.example") =  Valid(EmailAddress)
   build(addr)               =  "ada@corp.example"          (never fails)
```

In code:

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

---

## Composition: nesting short-circuits, siblings accumulate

Prisms combine in two ways, and the two behave differently when a parse fails.

**Nesting with `andThen` goes deeper into a single value, so it short-circuits.** If the outer parse fails there is no inner value to look at, so the first reason wins and parsing stops. This is the same choice `ValidationPath` makes with `via`.

**Sibling fields accumulate.** To report every bad field of a record at once, parse each field with its own prism and combine the results with [`fields()` / `accumulate()`](../monads/validated_assembly.md) or the [`Edits` builder](multi_edit.md). Because the fields are independent, every reason is collected, not just the first.

```
   Nesting: andThen, deeper into one value       =>  short-circuit
     outer.parse ✗ ─────────────────────────▶  stop, the first reason wins
     outer.parse ✓ ──▶ inner.parse ─────────▶  keep going

   Siblings: fields() / accumulate(), one prism per field    =>  accumulate
     name   ✓
     email  ✗  "not an email"      ┐
     age    ✗  "must be positive"  ├──▶  Invalid[ all reasons at once ]
                                   ┘
```

Only compositions that preserve the **total build** yield a `ValidatedPrism`:

| Compose with | Result | Notes |
|---|---|---|
| `ValidatedPrism<A, B>` | `ValidatedPrism<S, B>` | parse short-circuits; build composes |
| `Iso<A, B>` | `ValidatedPrism<S, B>` | parse maps through; build round-trips |
| `Prism<A, B>` + a `FieldError` reason | `ValidatedPrism<S, B>` | the reason speaks for the prism's empty case |
| `Lens<A, B>` | Deliberately absent | a lens needs a base to write into, so no total `B -> S` build exists |

---

## Bridging the lattice

- `ValidatedPrism.fromIso(iso)`: a parse that never fails.
- `ValidatedPrism.fromPrism(prism, reason)`: lift a plain prism by supplying the reason its `Optional.empty` cannot express.
- `toPrism()` / `toAffine()`: forget the reasons (the affine's `set` leaves non-parsing sources unchanged, preserving the affine laws).

---

## Laws

A lawful validated boundary satisfies both round trips, verified with [`ValidatedPrismLaws`](../tooling/test_assertions.md) from `hkj-test`:

``` java
ValidatedPrismLaws.assertValidatedPrismLaws(email, "ada@corp.example", "not-an-email");
// parse-build: parse(build(a)) == Valid(a)
// build-parse: parse(s) == Valid(a)  =>  build(a) == s   (no lossy parse-normalise)
```

The second law is the subtle one. If `build` changes the value as it renders (zero-padding a code, trimming whitespace), the round trip no longer holds. Keep all normalising in `parse`, and let `build` render the value faithfully.

---

~~~admonish info title="Key Takeaways"
* **`parse` is fallible and accumulating** (`Validated<NonEmptyList<FieldError>, A>`); **`build` is total**: the parse-don't-validate asymmetry as an optic
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
- [Record Mapping](record_mapping.md) - `@GenerateMapping` derives whole-record `parse`/`build` from these leaves
~~~

---

**Previous:** [Prism Toolkit](prism_toolkit.md)
**Next:** [Affines](affine.md)
