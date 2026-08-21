# Standard Codecs and Shared Vocabulary

_The stock conversions for the standard families, and the mix-in pattern that shares them across an API._

A typical DTO boundary converts the same handful of families every time: identifiers, dates, enums, money. Writing a `ValidatedPrism` by hand for each would be busywork, and writing it *lawfully* (accepting exactly the spelling it renders) is subtle. `StandardCodecs` ships that vocabulary ready-made, and a plain mix-in interface shares it, together with your own leaves and renames, across every spec in an API.

~~~admonish info title="What You'll Learn"
- Mapping the standard conversion families (identifiers, dates, enums, money) with one factory call each
- Why the codecs accept canonical forms only, and how the formatter overloads serve differently-canonical wires
- Wrapping a lenient, throwing JDK parser lawfully with `ValidatedPrism.canonical`
- Sharing leaves and renames across specs with plain mix-in interfaces
~~~

~~~admonish example title="See Example Code"
**The code on this page is [StandardCodecsBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java) and [RecordMappingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java)** - the page includes them directly, so they are compiled and run by the build.
~~~

## Standard codecs

The common conversion families need no hand-written leaves: `StandardCodecs` ships one factory per family, so a typical DTO boundary maps out of the box:

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

Every parse failure is a located `FieldError` with a copy-worthy message, so the codecs feed [the 422 leg](../spring/spring_boot_integration.md#the-422-leg) unchanged, and the enum message names the permitted constants:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/StandardCodecsBookTest.java:codecs_errors}}
```

### Canonical forms only

Each codec accepts exactly the form it renders, honouring the [`ValidatedPrism` section law](../optics/validated_prism.md#laws). A case-folded UUID, a leading zero, scientific notation or a lowercase language tag is a located rejection, never a silent normalisation, so whatever `parse` accepts, `build` reproduces byte-for-byte (`build(parse(s).get()) == s` whenever `s` parses).

~~~admonish tip title="Why this matters"
Silent normalisation is data mutation nobody asked for. A mapper that quietly lowercases a UUID or reformats a timestamp makes an echo endpoint return different bytes than it received, breaks cache keys and payload signatures, and bakes a client's spelling bug into the contract without anyone deciding to. The strictness here is not pedantry: it is the property that makes round trips *provable*, and every codec is law-checked to accept exactly what it renders. When a producer legitimately speaks a different canon, you do not weaken the law; you declare that canon (below) and keep the same guarantee on their spelling.
~~~

The date-time canons collide with two very common producers, and the fix is the same for both:

| Producer | Sends | Default canon says | Fix |
|---|---|---|---|
| Python `isoformat()`, PostgreSQL JSON | `2026-07-28T12:34:56+00:00` | rejected: a zero offset must be spelled `Z` | formatter overload |
| JavaScript `toISOString()` | `2026-07-28T12:34:56.000Z` | rejected: a zero fraction renders as no fraction at all, so `.000Z` never round-trips | formatter overload |

Non-zero fractions expose that the two date-time codecs have *different* canons, each honestly its own render: `instant()` follows `Instant.toString()`'s three-digit groups (`.500Z` parses, `.5Z` is rejected), while `offsetDateTime()` renders without trailing zeros (`.5Z` parses, `.500Z` is rejected). The rule never changes, only the render: each codec accepts exactly the spelling it produces.

The formatter overload makes the canonical form *theirs*:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:codecs_formatters}}
```

Two properties of a formatter canon are worth knowing. The canon is the *pattern's*, not the producer's output set: any spelling the pattern round-trips is accepted, so `JS_WIRE` admits a `+01:00` offset a real `toISOString()` would never emit, lawfully. And the pattern fixes the canon's *precision*: extra fractional digits on the wire are rejected (they do not fit the pattern), while a domain value carrying finer precision than the pattern renders truncated on `build`, which is a [non-injective render](../optics/validated_prism.md#laws), the obligation the laws page leaves with you. Pick a pattern whose precision matches what the domain actually stores, and check a custom canon with the laws.

### Your own canon: `ValidatedPrism.canonical`

The same move covers any differently-canonical wire. An uppercase-UUID producer (SQL Server) is not forbidden by the law; only accepting *both* cases through one leaf is. [`ValidatedPrism.canonical`](../optics/validated_prism.md#laws) supplies the guard such a leaf needs: the lenient, throwing `UUID.fromString` is fine, because the render defines the canon and the per-value guard rejects every spelling it cannot reproduce:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/StandardCodecsBook.java:canonical_leaf}}
```

Conversions the vocabulary does not cover stay hand-written leaves: `ValidatedPrism.canonical(...)` where a throwing parser and a render exist, `ValidatedPrism.of(...)` for full control. The processor never applies a codec implicitly; a conversion exists only where a spec declares it.

~~~admonish note title="Two mechanical notes"
- The number and boolean codecs focus the **box types**: a `ValidatedPrism<String, int>` cannot exist, so an `int` component cannot take a leaf; declare it `Integer` (the mapper rejects the mismatch at compile time either way).
- Under the star import, a leaf whose component shares a factory's name (`currency`, `locale`, `uuid`) must qualify the call (`return StandardCodecs.currency();`) because the leaf method itself is the nearer `currency()` and an unqualified call recurses.
~~~

---

## Shared vocabulary: mix-in interfaces

The same rename or the same leaf tends to recur across an API's specs: every wire calls it `fullName`, every email parses the same way. Move the shared members onto a **plain interface** and extend it alongside `MappingSpec`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:mixin_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:mixin_usage}}
```

An inherited member counts exactly as if it were declared on the spec: renames, leaves *and* derived fields, collected across the whole hierarchy (a mix-in may extend further mix-ins, and a diamond counts once). Precedence is **Java's own**: a member re-declared on the spec (or on a nearer mix-in) hides the one it overrides.

Two shapes are rejected, each naming the offender:

- a mix-in that **is itself a mapping spec** (directly or transitively extends `MappingSpec`/`UpdateSpec`): a mix-in shares vocabulary, a spec generates an Impl, and inheriting one spec from another would conflate the two;
- a **generic** mix-in: inherited member types are read as declared, and substituting them under an instantiation is not supported yet.

Diagnostics about an inherited member name its declaring interface, `abstract method 'bogus' (inherited from 'BrokenVocabulary') is neither a rename nor a leaf`, so the fix points at the right file.

~~~admonish note title="The inheritance edge cases, precisely"
Conflicting inherited `default` methods are already a javac error before the processor runs. The one case javac leaves open, unrelated mix-ins both declaring the same *abstract* rename (override-equivalent abstracts may coexist, JLS 9.4.1), folds into a single rename when the targets agree and is rejected with a diagnostic naming both interfaces when they conflict. Interface `static` helpers are not inherited (JLS 8.4.8), so factory methods on a mix-in stay inert.
~~~

Mix-ins compose with the rest of the feature: [threaded generic specs](generics.md) can extend (non-generic) mix-ins, and [`UpdateSpec`](beans_patch.md#sparse-patch-write-back-updatespec) mappings inherit vocabulary the same way, element leaves included, so the leaf a full spec lifts over a `List` serves its PATCH sibling unchanged. [`@GenerateMerge`](merge_envelopes.md) specs still declare everything directly.

---

~~~admonish info title="Key Takeaways"
* **The standard families are one factory call each**: `StandardCodecs` covers identifiers, dates, enums, numbers, and money with lawful, located codecs
* **Canonical forms only**: each codec accepts exactly the spelling it renders; a differently-canonical wire takes the formatter overload or a `ValidatedPrism.canonical` leaf
* **`canonical` makes lenient parsers lawful**: the render defines the canon and the per-value guard rejects every spelling it cannot reproduce
* **Mix-ins share the vocabulary**: one plain interface serves every spec, PATCH siblings included; nothing is ever applied implicitly
~~~

~~~admonish tip title="See Also"
- [Validated Prisms](../optics/validated_prism.md#laws) - The section law the codecs are built to honour
- [Record Mapping Basics](basics.md#validated-leaves) - How leaves attach to a spec
- [Beans and Sparse PATCH](beans_patch.md) - The PATCH sibling that lifts the same element leaves
~~~

---

**Previous:** [Record Mapping Basics](basics.md)
**Next:** [Nesting, Containers, and Sealed Hierarchies](structure.md)
