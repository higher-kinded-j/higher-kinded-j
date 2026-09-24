# Standard Codecs and Shared Vocabulary

_The stock conversions for the standard families, and the mix-in pattern that shares them across an API._

A typical DTO boundary converts the same handful of families every time: identifiers, dates, enums, money. Writing a `ValidatedPrism` by hand for each would be busywork, and writing it *lawfully*, accepting exactly the spelling it renders, is subtle. `StandardCodecs` ships that vocabulary ready-made. This page walks the stock codecs and the one rule they all keep first. Your own canon and a vocabulary shared across specs follow, for when you need them.

~~~admonish info title="What You'll Learn"
- Map identifiers, dates, enums, numbers and money with one factory call each, and predict which spellings each accepts
- Take a browser's or Python's timestamps without loosening a codec
~~~

~~~admonish example title="See Example Code"
**The code on this page is [StandardCodecsBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java) and its [StandardCodecsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/StandardCodecsBookTest.java)** - the page includes them directly, so they are compiled and run by the build.
~~~

## Standard codecs {#standard-codecs}

The common conversion families need no hand-written leaves. `StandardCodecs` ships one factory per family, so a typical DTO boundary maps out of the box:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:codecs_spec}}
```

| Factory | Wire ↔ domain |
|---|---|
| `uuid()` | `String` ↔ `UUID` |
| `uri()` | `String` ↔ `URI` |
| `localDate()` / `localDate(DateTimeFormatter)` | `String` ↔ `LocalDate` |
| `instant()` | `String` ↔ `Instant` (UTC, `Z`) |
| `offsetDateTime()` / `offsetDateTime(DateTimeFormatter)` | `String` ↔ `OffsetDateTime` |
| `enumByName(Class)` | `String` ↔ any enum, by exact constant name |
| `bigDecimal()` | `String` ↔ `BigDecimal`, plain notation, scale preserved |
| `intFromString()` / `longFromString()` / `doubleFromString()` | `String` ↔ boxed number, canonical spellings only (`"2"` is not a canonical double; `"2.0"` is) |
| `booleanStrict()` | `String` ↔ `Boolean`, exactly `true`/`false` |
| `currency()` | `String` ↔ `Currency` (ISO 4217) |
| `locale()` | `String` ↔ `Locale` (BCP 47 tag) |

Every parse failure is a located `FieldError` with a message a client can act on, so the codecs feed [the 422 leg](../spring/spring_boot_integration.md#the-422-leg) unchanged. The enum message names the permitted constants:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/StandardCodecsBookTest.java:codecs_errors}}
```

The number and boolean codecs produce box types, so declare such a component `Integer` or `Boolean`, not `int`: a `ValidatedPrism` cannot name a primitive, and the processor refuses the mismatch.

~~~admonish warning title="Not checked for you: qualify a factory named like its component"
A leaf for a component called `currency`, `locale` or `uuid` shares its factory's name. Inside `default ValidatedPrism<String, Currency> currency()`, an unqualified `currency()` calls the leaf itself, not the statically imported factory, so the first `parse` throws a `StackOverflowError`. It compiles without a warning. Write `return StandardCodecs.currency();`.
~~~

---

## Canonical forms only {#canonical-forms-only}

Each codec accepts exactly the form it renders, honouring the [section law](../optics/validated_prism.md#laws): an accepted wire value must rebuild to exactly itself. A case-folded UUID, a leading zero, scientific notation or a lowercase language tag is a located rejection, never a silent normalisation.

~~~admonish tip title="Why this matters"
Silent normalisation is data mutation nobody asked for. A mapper that quietly lowercases a UUID or reformats a timestamp makes an echo endpoint return different bytes than it received. It breaks cache keys and payload signatures, and bakes a client's spelling bug into the contract without anyone deciding to. The strictness is what makes round trips *provable*: every codec is law-checked to accept exactly what it renders. When a producer speaks a different canon, you do not weaken the law; you declare that canon, and keep the same guarantee on their spelling.
~~~

The two date-time codecs render differently, so they accept different spellings of the same moment. `instant()` writes as `Instant.toString()` does, with fractions in three-digit groups. `offsetDateTime()` writes the fraction with its trailing zeros dropped:

| Spelling on the wire | `instant()` | `offsetDateTime()` |
|---|---|---|
| `2026-07-28T12:34:56Z` | ✅ | ✅ |
| `2026-07-28T12:34:56.500Z` | ✅ | ❌ |
| `2026-07-28T12:34:56.5Z` | ❌ | ✅ |
| `2026-07-28T12:34:56.123Z` | ✅ | ✅ |
| `2026-07-28T12:34:56.000Z` | ❌ | ❌ |
| `2026-07-28T12:34:56+00:00` | ❌ | ❌ |

~~~admonish warning title="Not checked for you: a browser's timestamps fail some of the time"
A browser's `toISOString()` always writes three fraction digits and `Z`. `instant()` accepts that, except when the milliseconds are zero: `.000Z` is a spelling `Instant.toString()` never writes, so about one request in a thousand is rejected. Python's `isoformat()` writes `+00:00` for UTC, which neither codec accepts at all. Nothing fails at compile time, and a test with a fixed clock can pass every time. Declare the producer's canon instead, with the leaves that follow.
~~~

A formatter overload makes a producer's canon the codec's canon. Where no single pattern describes the producer, [`ValidatedPrism.canonical`](../optics/validated_prism.md#laws) wraps a parser and a render, and rejects every spelling the render cannot reproduce:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:codecs_formatters}}
```

Name the leaf after its component, as usual: `default ValidatedPrism<String, Instant> placedAt() { return WireFormats.BROWSER_INSTANT; }`. Each of these leaves accepts exactly what its producer sends, and renders it back the same way.

~~~admonish tip title="You can ship now"
You can now map the standard families with one factory call each, predict which spellings a codec accepts, and take a browser's or Python's timestamps without loosening anything. The rest of this page, [your own canon](#your-own-canon) and [a vocabulary shared across specs](#shared-vocabulary-mix-in-interfaces), is for when you need them.
~~~

~~~admonish question title="Checkpoint: which quantities parse?" id="check-codecs-int"
A `quantity` component is mapped with `StandardCodecs.intFromString()`. Clients send `"42"`, `"042"`, `"+42"`, `" 42"` and `"42.0"`. Which of them parse?

1. All five: each names the number 42
2. `"42"`, `"042"` and `"+42"`, the ones `Integer.parseInt` accepts
3. `"42"` only
4. `"42"` and `"42.0"`
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-codecs-int-answer"
**3.** A codec accepts exactly the spelling it renders, and `Integer.toString` writes `42` and nothing else. `Integer.parseInt` would take `"042"` and `"+42"`, but `build` could never reproduce either, so both are located rejections:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/StandardCodecsBookTest.java:check_int_canon}}
```

Where this lives: [Canonical forms only](#canonical-forms-only).
~~~

~~~admonish question title="Checkpoint: a browser, and a plain `offsetDateTime()`" id="check-codecs-instant"
A browser client sends `toISOString()` timestamps to an `OffsetDateTime` component mapped with the plain `StandardCodecs.offsetDateTime()`. Which of these requests does `parse` reject?

1. `2026-07-28T12:34:56.123Z`
2. `2026-07-28T12:34:56.120Z`
3. `2026-07-28T12:34:56.100Z`
4. `2026-07-28T12:34:56.000Z`
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-codecs-instant-answer"
**2, 3 and 4.** `offsetDateTime()` drops trailing zeros when it renders, so it writes `.12Z`, `.1Z` and no fraction for those three moments, and accepts only those spellings. Every millisecond value that ends in a zero is rejected, about one request in ten:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/StandardCodecsBookTest.java:check_browser_offset}}
```

`WireFormats.BROWSER_OFFSET` takes all four.

Where this lives: [Canonical forms only](#canonical-forms-only).
~~~

---

## Your own canon {#your-own-canon}

The same move covers any differently-canonical wire. An uppercase-UUID producer (SQL Server) is not forbidden by the law; only accepting *both* cases through one leaf is. The lenient, throwing `UUID.fromString` is fine inside `ValidatedPrism.canonical`, because the render defines the canon:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:canonical_leaf}}
```

A formatter canon is the *pattern's*, not the producer's output set, so `BROWSER_OFFSET` also accepts a `+01:00` offset a browser never sends, lawfully. The pattern fixes the precision too. A domain value finer than the pattern is truncated on `build`, a [non-injective render](../optics/validated_prism.md#laws), so pick a pattern that matches what the domain stores, and check a custom canon with the laws.

Conversions the vocabulary does not cover stay hand-written leaves: `ValidatedPrism.canonical(...)` where a throwing parser and a render exist, `ValidatedPrism.of(...)` for full control. The processor never applies a codec implicitly: a conversion exists only where a spec declares it.

---

## Shared vocabulary: mix-in interfaces {#shared-vocabulary-mix-in-interfaces}

The same rename or leaf tends to recur across an API's specs: every wire calls it `fullName`, and every email parses the same way. Move the shared members onto a **plain interface**, and extend it alongside `MappingSpec`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:mixin_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:mixin_usage}}
```

An inherited member counts exactly as if it were declared on the spec. One that binds to nothing, like `phone()` on `ClientMapping`, stays **inert**, so one vocabulary serves specs whose records differ. The same member declared on the spec itself is an error, which is what catches a typo. [What an inherited member binds against](rules.md#what-an-inherited-member-binds-against) says what "nothing" means for each kind of member, and [How a spec collects its vocabulary](rules.md#how-a-spec-collects-its-vocabulary) covers precedence, generic and threaded specs, and PATCH siblings.

A vocabulary also crosses a **module boundary**. It is a plain interface rather than a spec, so the module publishing it needs no annotation processor, only the library its members name. A downstream spec extends it from the jar exactly as from a sibling source file, since the annotations that give its members meaning are kept in the class file. One API module can therefore own the house vocabulary that every service module's specs extend.

---

~~~admonish info title="Key Takeaways"
* **The standard families are one factory call each**: `StandardCodecs` covers identifiers, dates, enums, numbers and money with lawful, located codecs
* **Canonical forms only**: each codec accepts exactly the spelling it renders, so a producer with its own canon gets a leaf that declares it
* **Browser and Python timestamps need their own leaves**: the stock date-time codecs reject some of what they send
* **Mix-ins share the vocabulary**: one plain interface serves every spec, and a member a spec cannot use stays inert
~~~

~~~admonish tip title="See Also"
- [Validated Prisms](../optics/validated_prism.md#laws): The section law the codecs are built to honour
- [Record Mapping Basics](basics.md#validated-leaves): How leaves attach to a spec
- [Sparse PATCH](beans_patch.md): The PATCH sibling that lifts the same element leaves
~~~

---

**Previous:** [Record Mapping Basics](basics.md)
**Next:** [Absent Fields and Record Invariants](absence.md)
