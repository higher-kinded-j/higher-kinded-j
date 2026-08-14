# Capstone: One 422, Every Bad Field

> _"Make illegal states unrepresentable."_
> — Yaron Minsky

~~~admonish info title="What You'll Learn"
- One order-intake boundary built end to end: codecs, a custom leaf, a rename, nesting, a list, and a derived field
- The payoff: a five-defect request answered by a single response naming every bad field by path
- The encores: a sparse PATCH, a multi-source merge, and a typed error envelope on the same boundary
- The laws test that proves all of it, copied from a green build
~~~

~~~admonish example title="See Example Code"
**The code on this page is [BoundaryCapstoneBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java)** and its **[BoundaryCapstoneBookLawsTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBookLawsTest.java)** - the page includes both directly, so everything shown is compiled, run, and asserted by the build.
~~~

---

## The Scenario

An order-intake API. The domain is what the business logic trusts: typed identifiers, a real `Instant`, a real `Currency`, an email that has already been checked.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_domain}}
```

The wire is what clients actually send: strings, a rename (`fullName`), and one field the domain does not store because it can be computed:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_wire}}
```

**The task:** accept an `OrderDto`, return a trusted `Order`, and when the request is bad, tell the client *everything* that is wrong with it, in one round trip, with every problem located.

---

## The Imperative Approach

The mapper most codebases carry:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_before}}
```

Count the ways the five-defect request below defeats it. It reports **one** problem (whichever throws first), the message carries **no field path**, four exception types need catching (`NullPointerException`, `IllegalArgumentException`, `DateTimeParseException`, `NumberFormatException`), and when `Order` grows a component next quarter, nothing warns that this method no longer covers it.

---

## The HKJ Approach

One hand-written leaf for the email (everything else is stock), shared through a vocabulary interface:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_vocab}}
```

Three specs declare the whole boundary. Every conversion is named after its component; the processor derives both directions and rejects anything it cannot honour:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_spec}}
```

That is the entire declaration: no mapper class, no Bean Validation annotations, no exception handler. `build` is total (the derived `displayTotal` is computed, not copied):

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_build}}
```

---

## The Payoff

Now the five-defect request. A bad id, a bad email inside the nested customer, a bad price on the *second* line item, a non-canonical timestamp, and an unknown status:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_payoff}}
```

Five defects, one value, every error located, in declaration order. In a Spring controller this result needs no wrapping: return it as-is and [the 422 leg](../spring/spring_boot_integration.md#the-422-leg) renders it as one response:

```json
{
  "valid": false,
  "errors": [
    { "path": "id",             "segments": ["id"],                    "message": "not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)" },
    { "path": "customer.email", "segments": ["customer", "email"],     "message": "not an email address" },
    { "path": "lines.1.price",  "segments": ["lines", "1", "price"],   "message": "not a number in plain notation (expected e.g. 123.45)" },
    { "path": "placedAt",       "segments": ["placedAt"],              "message": "not an ISO-8601 instant (expected e.g. 2026-07-28T12:34:56Z)" },
    { "path": "status",         "segments": ["status"],                "message": "unknown OrderStatus (expected one of NEW, PAID, SHIPPED)" }
  ],
  "errorCount": 5
}
```

The client fixes all five and resubmits once. Compare that with the imperative version's five round trips of `400 Bad Request: bad email`.

### What happened, error by error

| Located error | Machinery that produced it |
|---|---|
| `id: not a UUID (...)` | the stock [`uuid()` codec](codecs.md#standard-codecs) |
| `customer.email: not an email address` | the hand-written leaf, located **through the nested spec** |
| `lines.1.price: not a number in plain notation (...)` | `bigDecimal()`, lifted over the list, located **by element index** |
| `placedAt: not an ISO-8601 instant (...)` | `instant()`, rejecting the non-canonical spelling |
| `status: unknown OrderStatus (...)` | `enumByName`, naming the permitted constants |

Every piece of the chapter fired at once, and none of it was written by hand.

---

## The Encores

The same boundary, three more tiers, almost no new code.

**A sparse PATCH.** The email leaf is already in the vocabulary, so the PATCH sibling is one bean and one empty spec. Absent means keep; a present bad value still fails, located:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_patch}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_patch_usage}}
```

**A receipt, merged.** One target from two sources, filled by component name, no class literals:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_merge_spec}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBook.java:capstone_merge_usage}}
```

**A typed error envelope.** When the service behind this boundary fails, its sealed `OrderError` carries a generated envelope and a typed context instead of a copy-pasted `code`/`message`/`timestamp` and a `Map<String, Object>`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/OrderErrorBook.java:error_envelope}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/OrderErrorBook.java:edit_context}}
```

---

## The Proof

"Lawful" is a passing test, not an adjective. The payoff above is asserted, message for message:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBookLawsTest.java:capstone_errors}}
```

And the mappings obey their tiers' laws, through the same `MappingLaws` harness the library's own build runs:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBookLawsTest.java:capstone_laws}}
```

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/capstone/BoundaryCapstoneBookLawsTest.java:capstone_patch_laws}}
```

---

~~~admonish info title="Key Takeaways"
* **The whole boundary is three small interfaces**: one custom leaf, stock codecs for the rest, a rename, a derived field; the processor derives both directions and keeps them covering the records
* **One response, every bad field**: nesting, list indices, and codec messages compose into located errors a client can map straight onto its form
* **The encores are almost free**: the PATCH sibling reuses the vocabulary, the merge is a method signature, the envelope is one component
* **All of it is proven**: the page's payoff and laws are includes from a green test
~~~

~~~admonish tip title="See Also"
- [The 422 leg](../spring/spring_boot_integration.md#the-422-leg) - This result as an HTTP response, unmodified
- [Sparse PATCH at the Spring boundary](../spring/spring_boot_integration.md#sparse-patch) - The PATCH encore behind a controller
- [Record Mapping Basics](basics.md) - Back to the start of the chapter
- [Capstone: Effects Meet Optics](../effect/capstone_focus_effect.md) - The effect-side sibling capstone
~~~

---

**Previous:** [Injecting, Testing, and Diagnostics](testing.md)
