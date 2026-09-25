# Standard Codecs and Shared Vocabulary

_The stock conversions for the standard families, and the mix-in pattern that shares them across an API._

A typical DTO boundary converts the same handful of families every time: identifiers, dates, enums, money. Writing a `ValidatedPrism` by hand for each would be busywork, and writing it *lawfully*, accepting exactly the spelling it renders, is subtle. `StandardCodecs` ships that vocabulary ready-made. This page first walks the stock codecs and the one rule they all keep. Your own canon and a vocabulary shared across specs follow, for when you need them.

~~~admonish info title="What You'll Learn"
- Map identifiers, dates, enums, numbers and money with one factory call each, and predict which spellings each accepts
- Predict which of a browser's or Python's timestamps a stock codec rejects, and declare the producer's canon instead
~~~

~~~admonish example title="See Example Code"
**The code on this page is [StandardCodecsBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java) and its [StandardCodecsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/StandardCodecsBookTest.java)** - the page includes them directly, so they are compiled and run by the build.
~~~

## Standard codecs {#standard-codecs}

The common conversion families need no conversion code of your own. `StandardCodecs`, in `org.higherkindedj.optics.validated`, ships one factory per family. The processor never applies one on its own, so each converting component declares a one-line [leaf](basics.md#validated-leaves) that returns it:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:codecs_imports}}

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

A codec's own failure is a `FieldError` with a message and no path. Under a spec, the generated `parse` locates it at the component, so the codecs feed [the 422 leg](../spring/spring_boot_integration.md#the-422-leg) unchanged. Each message ends with a sample of the spelling the codec wants, and the enum message names the permitted constants:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/StandardCodecsBookTest.java:codecs_errors}}
```

The number and boolean codecs are for values that arrive as strings: a query parameter, a CSV cell, a quoted JSON value. They produce boxed types, so the domain component is `Integer` or `Boolean`, not `int`: a `ValidatedPrism` cannot name a primitive, and the processor [refuses the mismatch](compiler_errors.md#no-usable-source). A quantity that JSON sends as a number can take either of two shapes. Declared `Integer` on both sides, it needs no codec, and a missing one is still a located `must not be null`. But Jackson answers a malformed one before `parse` runs, with its own 400 for `"two"`, and it quietly truncates `2.5` to `2`. Declared `String` on the wire with `intFromString()`, as in the [Capstone](capstone.md), every bad quantity is located, and a JSON number `2` still binds, as the string `"2"`.

~~~admonish warning title="Not checked for you: qualify a factory named like its component"
A leaf for a component called `currency`, `locale`, `uri` or `uuid` shares its factory's name. Inside `default ValidatedPrism<String, Currency> currency()`, an unqualified `currency()` calls the leaf itself, not the statically imported factory, so the first `parse` or `build` throws a `StackOverflowError`. It compiles without a warning. Write `return StandardCodecs.currency();`.
~~~

---

## Canonical forms only {#canonical-forms-only}

A codec's **canon** is the spelling its `build` writes. `parse` accepts that spelling and rejects every other spelling of the same value with an error, honouring the [section law](../optics/validated_prism.md#laws): an accepted wire value must rebuild to exactly itself. So `parse` rejects an uppercase UUID, `042`, `1E+3` or the language tag `en-gb`, and never quietly normalises it. The rejection reads like a malformed value, so its `expected e.g.` sample is what shows the client the spelling the codec wants.

~~~admonish tip title="Why this matters"
Silent normalisation is data mutation nobody asked for. A mapper that quietly lowercases a UUID or reformats a timestamp makes an echo endpoint return different bytes than it received. It breaks cache keys and payload signatures, and bakes a client's spelling bug into the contract without anyone deciding to. Strictness is what guarantees a round trip: every stock codec is tested to accept exactly what it writes. When a producer speaks a different canon, you do not weaken the rule; you declare that canon, and keep the same guarantee on their spelling.
~~~

The two date-time codecs render differently, so they accept different spellings of the same moment. Both write UTC as `Z`, and leave out a fraction that is zero. `instant()` writes as `Instant.toString()` does: always UTC, and any other fraction in groups of three digits. `offsetDateTime()` keeps the offset it was given, and drops the fraction's trailing zeros:

| Spelling on the wire | Accepted by `instant()` | Accepted by `offsetDateTime()` |
|---|---|---|
| `2026-07-28T12:34:56Z` | ✅ | ✅ |
| `2026-07-28T12:34:56.500Z` | ✅ | ❌ |
| `2026-07-28T12:34:56.5Z` | ❌ | ✅ |
| `2026-07-28T12:34:56.123Z` | ✅ | ✅ |
| `2026-07-28T12:34:56.000Z` | ❌ | ❌ |
| `2026-07-28T12:34:56+00:00` | ❌ | ❌ |
| `2026-07-28T12:34:56+01:00` | ❌ | ✅ |

~~~admonish warning title="Not checked for you: browser and Python timestamps are rejected"
A browser's `toISOString()` always writes three fraction digits and `Z`. `instant()` accepts that except when the milliseconds are zero, since `Instant.toString()` never writes `.000Z`. For a timestamp read from the clock, that is about one request in a thousand. For a time the user picked in a date or time input, it is every request, because those are whole seconds. An aware Python `datetime`'s `isoformat()` writes `+00:00` for UTC, which both codecs reject every time. Nothing fails at compile time, and a test whose fixture has non-zero milliseconds passes every time. Declare the producer's canon instead, and check what your producer really sends: some Python frameworks rewrite `+00:00` as `Z`.
~~~

For a `LocalDate` or `OffsetDateTime` component, the formatter overload is enough: pass the producer's pattern, and the codec accepts exactly what that pattern writes. `instant()` has no formatter overload, and no single pattern describes Python's output. For those, [`ValidatedPrism.canonical`](../optics/validated_prism.md#laws) wraps a parser and a render, and rejects every spelling the render would not write back identically:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:codecs_formatters}}
```

Pick the leaf by producer and component type, and name it after the component, as usual:

| Producer | `Instant` component | `OffsetDateTime` component |
|---|---|---|
| a browser's `toISOString()` | `BROWSER_INSTANT` | `BROWSER_OFFSET` |
| Python's `isoformat()`, on an aware `datetime` | `PYTHON_INSTANT` | `PYTHON_OFFSET` |

`default ValidatedPrism<String, Instant> placedAt() { return WireFormats.BROWSER_INSTANT; }` accepts every spelling a browser sends, and renders a parsed value back the same way. One leaf has one canon, since `build` writes one spelling. When a browser and a Python job send the same record, declare a spec per producer over the same pair, each with its own timestamp leaf, and share the rest through a [vocabulary](#shared-vocabulary-mix-in-interfaces).

~~~admonish warning title="Not checked for you: a pattern cuts what it builds to its precision"
`BROWSER_INSTANT` writes milliseconds, and `PYTHON_INSTANT` microseconds. An `Instant.now()` carries finer digits on current JDKs, so `build` cuts them off, and a client that echoes the value back sends a different `Instant`. Truncate at creation where a value must round-trip: `Instant.now().truncatedTo(ChronoUnit.MILLIS)`. A law check from a wire sample never sees the finer value, but `ValidatedPrismLaws.assertParseBuild(WireFormats.BROWSER_INSTANT, Instant.now())` does, and fails.
~~~

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
**3.** A codec accepts exactly the spelling it renders, and `Integer.toString` writes `42` and nothing else. `Integer.parseInt` would take `"042"` and `"+42"`, but `build` could never reproduce either, so `parse` rejects both with a located error:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/StandardCodecsBookTest.java:check_int_canon}}
```

Where this lives: [Canonical forms only](#canonical-forms-only).
~~~

~~~admonish question title="Checkpoint: how often does `offsetDateTime()` reject a browser?" id="check-codecs-instant"
A browser stamps each request with `new Date().toISOString()`, and an `OffsetDateTime` component maps it with the plain `StandardCodecs.offsetDateTime()`. Roughly how often does `parse` reject a request?

1. Never: both write UTC as `Z`
2. About one request in a thousand, as for `instant()`
3. About one request in ten
4. Every time: `offsetDateTime()` expects an offset such as `+01:00`
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-codecs-instant-answer"
**3.** `offsetDateTime()` drops the fraction's trailing zeros when it renders, so it writes `.12Z` where a browser sends `.120Z`. It rejects every millisecond value that ends in a zero, a hundred of the thousand, while `BROWSER_OFFSET` takes them all:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/StandardCodecsBookTest.java:check_browser_offset}}
```

Where this lives: [Canonical forms only](#canonical-forms-only).
~~~

---

## Your own canon {#your-own-canon}

Declaring the producer's canon, as the browser and Python leaves do, covers any wire with a canon of its own. An uppercase-UUID producer (SQL Server) is not forbidden by the law; only accepting *both* cases through one leaf is. The lenient, throwing `UUID.fromString` is fine inside `ValidatedPrism.canonical`, because the render defines the canon:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:canonical_leaf}}
```

A pattern's canon is everything the pattern writes, not just what one producer sends. So `BROWSER_OFFSET` also accepts a `+01:00` offset a browser never sends, lawfully, and rejects extra fraction digits, which the pattern has no room for.

Conversions the vocabulary does not cover stay hand-written leaves: `ValidatedPrism.canonical(...)` where a throwing parser and a render exist, `ValidatedPrism.of(...)` for full control.

---

## Shared vocabulary: mix-in interfaces {#shared-vocabulary-mix-in-interfaces}

The same rename or leaf tends to recur across an API's specs: every wire calls it `fullName`, and every email parses the same way. Move the shared members onto a **plain interface**, and extend it alongside `MappingSpec`. Like a Jackson mix-in, it holds mapping declarations apart from the types they describe. Unlike one, a spec extends it, so Java's inheritance decides which declaration wins:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:mixin_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:mixin_usage}}
```

An inherited member binds exactly as if it were declared on the spec. One that binds to nothing, like `phone()` on `ClientMapping`, stays **inert**, so one vocabulary serves specs whose records differ. The processor refuses the same member declared on the spec itself, which is what catches a typo there. The flip side: a misspelt leaf in the mix-in stays inert everywhere, so test each spec's rejections once. [What an inherited member binds against](rules.md#what-an-inherited-member-binds-against) says what "nothing" means for each kind of member. [How a spec collects its vocabulary](rules.md#how-a-spec-collects-its-vocabulary) covers precedence, generic specs and PATCH specs, and [Mix-in shapes the processor refuses](rules.md#refused-mix-in-shapes) names the two it will not take.

A vocabulary also crosses a **module boundary**. The API module that publishes it needs the HKJ libraries its members use, not the annotation processor, and a service module's spec extends it from the jar as if it were local. One API module can therefore own the house vocabulary every service's specs extend, as long as it is on each consumer's compile classpath ([Multi-module builds](../tooling/manual_setup.md#multi-module-builds)).

---

~~~admonish info title="Key Takeaways"
* **The standard families are one factory call each**: `StandardCodecs` covers identifiers, dates, enums, numbers and money with lawful codecs, whose failures a spec locates at the component
* **Canonical forms only**: each codec accepts exactly the spelling it renders, so a producer with its own canon gets a leaf that declares it
* **Browser and Python timestamps need their own leaves**: the stock date-time codecs reject some or all of what they send, and a pattern cuts what it builds to its precision
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
