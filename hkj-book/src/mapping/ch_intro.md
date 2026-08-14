# Mapping at the Boundary

> _"A parser is just a function that consumes less-structured input and produces more-structured output."_
> — Alexis King, *Parse, Don't Validate*

---

Every service has the same three files. A DTO the framework binds. A domain record the business logic trusts. And, between them, a mapper: hand-written, reflection-driven, or generated, but always *there*, because the shape the wire speaks is never quite the shape the domain thinks in.

Here is the version most codebases carry, in one form or another:

```java
public static User toDomain(UserDto dto) {
    Objects.requireNonNull(dto.email(), "email required");   // throws on the FIRST problem
    if (!dto.email().contains("@")) {
        throw new IllegalArgumentException("bad email");     // no field name, no path
    }
    return new User(
        UUID.fromString(dto.id()),                           // throws its own exception
        dto.email(),
        LocalDate.parse(dto.joined()));                      // and so does this one
}
```

It works, until it doesn't, and it fails three ways at once:

1. **It drifts.** Add a component to `User` and nothing tells you the mapper no longer covers it. The compiler is not watching this file.
2. **It stops at the first error.** The client fixes the email, resubmits, and only then learns the date was bad too. One round trip per defect.
3. **Its errors have no address.** `IllegalArgumentException: bad email` says nothing a client can map onto a form field, so a handler somewhere turns it into a vague 400.

The usual patch is a pipeline: bind with Jackson, validate with Bean Validation annotations, translate with a mapper, and catch what leaks in a `@ControllerAdvice`. Four tools, the format rules written twice, and the domain type still constructed from data that was checked *somewhere else*.

This chapter replaces that pipeline with one step, derived at compile time from an interface you own:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_usage}}
```

Outbound, `build` is a total function: it cannot fail. Inbound, `parse` is a *parser* in Alexis King's sense: it returns your typed domain value, or `Validated<NonEmptyList<FieldError>, Domain>` carrying **every** bad field at once, each located by a dotted path (`customer.email: not a valid email address`). Nothing drifts, because the processor re-derives the mapping from the records on every compile and rejects what it cannot honour. And in a Spring controller the parse result is already a response: one 422 listing every defect, [the 422 leg](../spring/spring_boot_integration.md#the-422-leg).

---

## What the mapper will (and will not) generate

The generated surface follows the shape of the pair. That decision is the map of this chapter:

```
                     How do the two records correspond?
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
  every component            some fields differ           wire has FEWER
  matches by name            in type: leaves,             components than
  and type                   codecs, nested specs         the domain
        │                           │                           │
        ▼                           ▼                 ┌─────────┴─────────┐
  build + guarded parse      build + accumulating     ▼                   ▼
  + asIso()                  parse                all identity        any leaf
  (lossless round trip)      (no asIso: the       asLens():          validated
                             round trip can       lawful             patch(d, w):
                             reject)              write-back         write-back
                                                                     that can fail

  ...and a PATCH request bean, where null means "leave unchanged"?
                    extend UpdateSpec ──▶ updateFrom only
```

Nothing here is fabricated: a mapping only offers the operations its field correspondences can lawfully support, and every tier is verified against a published law harness, in the library's build and in yours. [The Emission Tiers](tiers.md) is the full story.

~~~admonish note title="If you know MapStruct"
This is not a MapStruct competitor on breadth, and does not try to be: MapStruct keeps its ground for mutable JPA entities, nested-path flattening, and Bean-Validation-centric shops. What this generator does differently is **boundary correctness for record domains**: the inbound direction is a validating parser with located, accumulated errors (where MapStruct throws on the first bad conversion, or silently maps an invalid value), the outbound direction is provably total, and no operation is generated whose laws the pair cannot satisfy. Adopt it where the boundary is the product; keep MapStruct where its breadth pays.
~~~

---

~~~admonish info title="In This Chapter"
- **Record Mapping Basics** – The `MappingSpec` interface, the total `build` / accumulating `parse` asymmetry, the null doctrine that turns every wire `null` into a located error, `ValidatedPrism` leaves, `@MapField` renames, and derived wire-only fields.
- **Standard Codecs and Shared Vocabulary** – The stock leaf vocabulary (`uuid()`, `localDate()`, `enumByName(...)`, money and more), why it accepts canonical forms only, `ValidatedPrism.canonical` for your own canons, and mix-in interfaces that share leaves across an API.
- **Nesting, Containers, and Sealed Hierarchies** – Specs nest automatically and failures compose into dotted paths; `List`/`Optional`/`Map` lift their elements; sealed pairs dispatch exhaustively in both directions.
- **The Emission Tiers** – The truthful-types table: which shapes earn `asIso()`, `asLens()`, the validated `patch`, or `asValidatedPrism()`, and the `MappingLaws` call that proves each tier lawful in your own tests.
- **Beans and Sparse PATCH** – Getter/setter and builder wires with the full feature set, and the `UpdateSpec` opt-in that gives a PATCH bean null-as-absent semantics without weakening validation of what was sent.
- **Generic Specs** – Concrete instantiations, threaded type parameters, and element-mapped specs whose codecs arrive at construction time.
- **Merge and Error Envelopes** – `@GenerateMerge` assembles one target from several sources with truthful return types; `@GenerateErrorEnvelope` retires the copy-pasted `code`/`message`/`timestamp` and types the error context.
- **Injecting, Testing, and Diagnostics** – Register the surface you consume, fake codecs as two-line values, and lean on what/why/fix diagnostics; there is no component ceiling.
- **Capstone: One 422, Every Bad Field** – The whole chapter on one order-intake boundary: a five-defect request answered by a single located-errors response, with PATCH, merge, and envelope encores, all proven by a green test.
~~~

## Chapter Contents

1. [Record Mapping Basics](basics.md) - The spec interface, build/parse asymmetry, leaves, renames
2. [Standard Codecs and Shared Vocabulary](codecs.md) - Stock lawful codecs and mix-in sharing
3. [Nesting, Containers, and Sealed Hierarchies](structure.md) - Composition and dotted error paths
4. [The Emission Tiers](tiers.md) - Truthful types, projections, the validated patch, laws
5. [Beans and Sparse PATCH](beans_patch.md) - Bean wires and the UpdateSpec tier
6. [Generic Specs](generics.md) - Concrete, threaded, and element-mapped generics
7. [Merge and Error Envelopes](merge_envelopes.md) - Multi-source assembly and typed error context
8. [Injecting, Testing, and Diagnostics](testing.md) - Beans, fakes, and limits
9. [Capstone: One 422, Every Bad Field](capstone.md) - The whole chapter on one boundary, proven

---

**Next:** [Record Mapping Basics](basics.md)
