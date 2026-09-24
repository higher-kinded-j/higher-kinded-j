# Record Mapping Basics

_Declare one interface; get a `build` that cannot fail and a `parse` that reports every bad field at once._

Most mappings are boring, and the mapper treats them that way: same-named, same-typed components match automatically, and one empty interface is the whole declaration. This page walks the happy path first (declare, build, parse, read the errors), then adds the declarations you will actually reach for: a conversion, a rename, and a computed field. The precise rules live on a page of their own, [Rules and Limits](rules.md).

~~~admonish info title="What You'll Learn"
- Declaring a mapping as a `MappingSpec<Domain, Wire>` interface and calling the generated Impl
- Reading a `parse` failure: every bad field at once, each located by name
- Converting a type-differing field with a `ValidatedPrism` leaf
- Renaming components with `@MapField`, and computing wire-only fields with derived getters
- Why a wire `null` becomes a located error, never an exception
~~~

~~~admonish example title="See Example Code"
**The code on this page is [BasicsBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java)** - the page includes it directly, so it is compiled and run by the build.

[GenerateMappingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/GenerateMappingExample.java)
~~~

## Your first mapping {#your-first-mapping}

The whole declaration is an empty interface naming the pair:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:basics_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:basics_usage}}
```

That is all of it: no mapper class, no configuration. The processor derives both directions from the two records at compile time, reflection-free, and re-derives them on every compile, so the mapping cannot drift away from the records it maps.

The two directions have different shapes, and that asymmetry runs through the whole chapter:

```
   build : Domain ──▶ DTO      total, always succeeds
   parse : DTO ──▶ Domain      fallible, reports every bad field at once
                               Validated<NonEmptyList<FieldError>, Domain>
```

The generated class is `<Spec>Impl` beside the spec; a concrete spec like this one is used through its `INSTANCE` constant ([generic specs](generics.md#one-rule-three-access-shapes) use `instance()` or `of(...)` instead). A spec nested in an outer class joins the enclosing simple names: `Shop.CustomerMapping` generates `ShopCustomerMappingImpl`.

Code that calls a mapping more than once binds it once and reuses it, as `personMapping` does above. Reading `INSTANCE` costs nothing, so this is for readability: shorter calls, and one name to change. Type the binding as the Impl, since the spec interface declares no methods of its own. Where it lives depends on how widely it is used: a local for a few calls in one method, a `private static final` field in the class that owns the boundary, or, in Spring, an injected `ValidatedPrism` when callers should depend on the mapping rather than on the generated class ([Injecting and testing generated mappings](testing.md#injecting-and-testing-generated-mappings)). Bind it in the code that calls it, never as a constant on the spec itself; [the fine print](#bind-in-the-caller) says why.

---

## Validated leaves {#validated-leaves}

Real boundaries convert: the wire sends a `String`, the domain wants an email that has already been checked. A **leaf** is the conversion at a single field: the point where the mapping stops copying and one wire value becomes one domain value. The leaf itself is a [`ValidatedPrism`](../optics/validated_prism.md), two functions: a parse that may reject, and a render that cannot:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:email_leaf}}
```

Attach it to the spec as a zero-parameter `default` method named after the domain component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:leaf_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:leaf_usage}}
```

Note what the failure looks like: a value, not an exception, and the error knows *which field* it belongs to. With several bad fields, `parse` reports all of them at once; the client fixes everything in one round trip.

You will rarely write leaves like this one by hand. The standard conversion families (identifiers, dates, enums, money) ship ready-made; [Standard Codecs](codecs.md) covers them, and it is the natural next page.

~~~admonish tip title="A leaf beats an identity match"
An explicit leaf wins even when the two component types are identical, so a `ValidatedPrism<String, String>` can validate a field the types alone would copy verbatim. Validate, not normalise: a parse that trims or case-folds accepts a spelling its `build` cannot reproduce, which breaks the [section law](../optics/validated_prism.md#laws) (an accepted wire value must rebuild to exactly itself).
~~~

---

## Renames: `@MapField` {#renames-mapfield}

When the wire calls it `fullName` and the domain calls it `name`, declare an abstract method named after the *domain* component, with `to` naming the *wire* component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:rename_spec}}
```

Each wire component takes exactly one domain source; colliding renames are compile errors, not surprises. A rename declares a concrete return type (the generated stub only names it, never calls it), and agreeing renames inherited from [mix-ins](codecs.md#shared-vocabulary-mix-in-interfaces) fold into one stub returning the narrowest declared type.

Error paths use **domain** component names, renames included: a wire sending `fullName` gets its errors at `name`. Every path in the system is domain-named, so paths stay consistent and stable under wire refactors; a client mapping errors back onto its own payload keys applies the rename in reverse.

---

## Derived wire fields {#derived-wire-fields}

A wire component with **no domain counterpart** can be computed from the whole domain value: a `displayName` the domain does not store because it is derivable. Declare a zero-parameter `default` method named after the *wire* component, returning `Getter<Domain, WireComponentType>`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:derived_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:derived_usage}}
```

The two directions are asymmetric: `build` computes the derived component, `parse` throws it away (the data is derivable, so nothing is lost).

```
  build : fills the derived component from the whole domain value
  ────────────────────────────────────────────────────────────────
  Profile(first, last) ──▶ ProfileDto(first, last, displayName)
                                                   ▲
             displayName() : Getter<Profile,String>│  first + " " + last
                                                   └── computed, not copied

  parse : ignores the derived component (it is derivable)
  ────────────────────────────────────────────────────────────────
  ProfileDto(first, last, displayName) ──▶ Valid(Profile(first, last))
                          └── displayName dropped, never read
```

The optic is a `Getter` because a derived field is single-valued: exactly one focus computed from the whole domain value. How the processor distinguishes leaf methods from derived-field methods, and how a derived field interacts with the [emission tiers](tiers.md), are in [Rules and Limits](rules.md#spec-members).

---

## Null has an address, not a stack trace {#null-doctrine}

A JSON binder leaves a missing property `null`, so a boundary meets nulls constantly. The rule is one sentence: **every value `parse` reads from the wire is null-guarded, and a `null` read becomes a located `FieldError` (`must not be null`), accumulating with every other bad field, never an exception.** It locates through nesting (`customer.name: must not be null`) and inside containers (`emails.1: must not be null`), and a `null` never reaches a leaf's conversion logic.

~~~admonish tip title="Why this matters"
Compare the alternatives you have debugged before: an NPE with a stack trace pointing into generated code, or Jackson's `MismatchedInputException` naming a Java class. A located error names *the client's own field*, sits beside every other defect in the same response, and costs the client one round trip instead of one per null. Here, a null always gets an address and never a stack trace.
~~~

~~~admonish tip title="At the Spring boundary"
Returned as-is from a controller, this result becomes the single 422 response the introduction showed: [the 422 leg](../spring/spring_boot_integration.md#the-422-leg).
~~~

The exact contract (what happens inside containers, and which nulls remain the caller's bug) is in [The null contract, precisely](rules.md#the-null-contract-precisely). A field whose `null` *means* something can be opted out of this rule, one field at a time, as [Absent Fields and Record Invariants](absence.md#optional-bridge) shows.

---

## A rule nothing checks for you {#the-fine-print}

This one stays on the page it is about, because the processor cannot see it.

### Bind in the caller, not on the spec {#bind-in-the-caller}

MapStruct's idiom declares a mapper's instance on the mapper's own interface. The same move here, `CustomerMappingImpl MAPPER = CustomerMappingImpl.INSTANCE;` declared on `CustomerMapping` (the leaf spec above), compiles but can read `null`. The Impl implements the spec, and the JVM initialises an interface as part of initialising a class that implements it whenever the interface declares an instance method with a body: every leaf and derived field it declares is one, and so is a `private` helper. When a program uses `CustomerMappingImpl.INSTANCE` before it first reads `CustomerMapping.MAPPER`, the spec's constant is evaluated while the Impl's own `INSTANCE` is still unassigned, and it keeps that `null` for good: the first `MAPPER.parse(...)` throws a `NullPointerException`. Two threads making those first uses at the same moment can deadlock instead. A constant on a mix-in that declares a leaf fails the same way. Which class a program reaches first depends on its code paths, so the failure comes and goes. A local, a field in the calling class or an injected `ValidatedPrism` sits outside the cycle.

---

~~~admonish info title="Key Takeaways"
* **A mapping is an interface you own**: `@GenerateMapping` on a `MappingSpec<Domain, Wire>` generates `<Spec>Impl` with `build` and `parse`; bind it once in the calling code, never as a constant on the spec
* **Two directions, two shapes**: `build` is total; `parse` reports every bad field at once, each located by a domain-named path
* **Leaves convert, renames rename, getters derive**: `ValidatedPrism` leaves for type-differing fields, `@MapField` for names, `Getter` defaults for wire-only fields
* **Null is located, never thrown**: one rule across both wire shapes and inside containers; only a null wire itself stays the caller's error
~~~

~~~admonish tip title="See Also"
- [Validated Prisms](../optics/validated_prism.md): The leaf optic every fallible correspondence is built from
- [Standard Codecs and Shared Vocabulary](codecs.md): The stock leaf vocabulary and how to share it
- [Absent Fields and Record Invariants](absence.md): A field whose `null` means *absent*, and a constructor's refusal reported as an error
- [The 422 leg](../spring/spring_boot_integration.md#the-422-leg): The parse result as one HTTP response
~~~

---

**Previous:** [Quickstart: Your First 422](quickstart.md)
**Next:** [Standard Codecs and Shared Vocabulary](codecs.md)
