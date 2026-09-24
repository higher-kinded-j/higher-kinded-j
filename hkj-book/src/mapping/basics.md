# Record Mapping Basics

_Declare one interface; get a `build` that cannot fail and a `parse` that reports every bad field at once._

Most mappings are boring, and the mapper treats them that way: same-named, same-typed components match automatically, and one empty interface is the whole declaration. This page walks the happy path first: declare, build, parse, read the errors, convert a field, and meet a `null`. Renames and computed fields follow, for when you need them. The precise rules live on a page of their own, [Rules and Limits](rules.md).

~~~admonish info title="What You'll Learn"
- Declare a mapping as a `MappingSpec<Domain, Wire>` interface, and bind its generated Impl in the calling code
- Read a `parse` failure: every bad field at once, each located by its path
- Convert a type-differing field with a `ValidatedPrism` leaf
- Predict what `parse` does with a `null`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [BasicsBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java) and its [BasicsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/BasicsBookTest.java)** - the page includes them directly, so they are compiled and run by the build.

[GenerateMappingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/GenerateMappingExample.java)
~~~

## Your first mapping {#your-first-mapping}

The whole declaration is an empty interface naming the pair:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:basics_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:basics_usage}}
```

That is all of it: no mapper class, no configuration. The processor derives both directions from the two records at compile time, without reflection. It re-derives them on every compile, so the mapping cannot drift away from the records it maps.

The two directions have different shapes, and that asymmetry runs through the whole chapter:

```
   build : Domain ──▶ DTO      total, always succeeds
   parse : DTO ──▶ Domain      fallible, reports every bad field at once
                               Validated<NonEmptyList<FieldError>, Domain>
```

The generated class is `<Spec>Impl`, beside the spec, and a concrete spec like this one is used through its `INSTANCE` constant. [Generic specs](generics.md#one-rule-three-access-shapes) use `instance()` or `of(...)` instead. A spec nested in an outer class joins the enclosing simple names: `Shop.CustomerMapping` generates `ShopCustomerMappingImpl`.

### Bind in the caller, not on the spec {#bind-in-the-caller}

Code that calls a mapping more than once binds it once and reuses it, as `personMapping` does. Reading `INSTANCE` costs nothing, so this is for readability: shorter calls, and one name to change. Type the binding as the Impl, since the spec interface declares no methods of its own. A local suits a few calls in one method, and a `private static final` field suits the class that owns the boundary. In Spring, inject a `ValidatedPrism` when callers should depend on the mapping rather than on the generated class ([Injecting and testing generated mappings](testing.md#injecting-and-testing-generated-mappings)).

~~~admonish warning title="Not checked for you: never bind the Impl on the spec"
MapStruct's idiom puts the mapper's instance on its own interface: `CustomerMappingImpl MAPPER = CustomerMappingImpl.INSTANCE;` declared on `CustomerMapping`. Here it compiles, and it can read `null`. A program that uses `CustomerMappingImpl.INSTANCE` before it first reads `CustomerMapping.MAPPER` leaves the constant `null` for good, so its first `MAPPER.parse(...)` throws a `NullPointerException`. Which class a program reaches first depends on its code paths, so the failure comes and goes. Bind the Impl in the calling code instead. [A constant on the spec can read null](rules.md#spec-constant-reads-null) explains the class-initialisation cycle behind it.
~~~

---

## Validated leaves {#validated-leaves}

Real boundaries convert: the wire sends a `String`, and the domain wants an email that has already been checked. A **leaf** is the conversion at a single field, where the mapping stops copying and one wire value becomes one domain value. The leaf itself is a [`ValidatedPrism`](../optics/validated_prism.md), two functions: a parse that may reject, and a render that cannot:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:email_leaf}}
```

Attach it to the spec as a zero-parameter `default` method named after the domain component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:leaf_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:leaf_usage}}
```

The two type-argument orders are easy to remember. `ValidatedPrism<Wire, Domain>` reads in the direction `parse` runs, from the wire to the domain. `MappingSpec<Domain, Wire>` names what you own first.

The failure is a value, not an exception, and it names the field. With several bad fields, `parse` reports them all at once, so the client fixes everything in one round trip. Keep a type-differing wire field a `String`, so a bad value reaches `parse` rather than failing in the JSON binder: the Quickstart's [Read the 422](quickstart.md#4-read-the-422) step shows why.

Most leaves come ready-made: [Standard Codecs](codecs.md), the next page, covers identifiers, dates, enums and money. Coming from Bean Validation? [From Bean Validation](from_mapstruct.md#from-bean-validation) translates each annotation.

~~~admonish tip title="A leaf beats an identity match"
An explicit leaf wins even when both types are identical, so a `ValidatedPrism<String, String>` can validate a field that would otherwise be copied. Validate, do not normalise: a parse that trims accepts a spelling `build` cannot reproduce, which breaks the [section law](../optics/validated_prism.md#laws).
~~~

---

## Null has an address, not a stack trace {#null-doctrine}

A JSON binder leaves a missing property `null`, so a boundary meets nulls constantly. The rule is one sentence: **every value `parse` reads from the wire is null-guarded, and a `null` becomes a located `FieldError` (`must not be null`), accumulated with every other bad field, never an exception.** It locates through nesting (`customer.name: must not be null`) and inside containers (`emails.1: must not be null`), and a `null` never reaches a leaf's conversion logic.

~~~admonish tip title="Why this matters"
Compare the alternatives you have debugged before: an NPE with a stack trace pointing into generated code, or Jackson's `MismatchedInputException` naming a Java class. A located error names *the client's own field*, sits beside every other defect in the same response, and costs the client one round trip instead of one per null.
~~~

~~~admonish tip title="At the Spring boundary"
Returned as-is from a controller, this result becomes the single 422 response the introduction showed: [the 422 leg](../spring/spring_boot_integration.md#the-422-leg).
~~~

The exact contract, including what happens inside containers and which nulls remain the caller's bug, is in [The null contract, precisely](rules.md#the-null-contract-precisely). A field whose `null` *means* something can opt out of this rule, one field at a time, as [Absent Fields and Record Invariants](absence.md#optional-bridge) shows.

~~~admonish tip title="You can ship now"
You can now declare a mapping, bind its Impl, convert a field through a leaf, and read every bad field back at once, `null`s included. Renames and computed wire fields follow, for when a pair needs them.
~~~

~~~admonish question title="Checkpoint: does the mapping notice?" id="check-basics-drift"
A teammate adds `String phone` to `PersonDto`, and nothing else. `Person` stays as it is. Does the build notice? If it does, what does it say, and what are your options?

<!-- verify:rejects "'PersonDto' has more components than 'Person'. build must fill every wire component from a domain source or a derived field, and the extras have neither." -->
```java
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;

record Person(String name, int age) {}

record PersonDto(String name, int age, String phone) {}

@GenerateMapping
interface PersonMapping extends MappingSpec<Person, PersonDto> {}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-basics-drift-answer"
**It refuses to compile.** `build` has to fill every wire component, and `phone` has no source:

```
'PersonDto' has more components than 'Person'. build must fill every wire component from a domain
source or a derived field, and the extras have neither.
```

The message goes on to list the fixes: remove the extra component, add a matching domain component, or declare a [derived field](#derived-wire-fields) that computes it. This is the drift a hand-written mapper never reports. Where this lives: [Your first mapping](#your-first-mapping).
~~~

~~~admonish question title="Checkpoint: predict the result" id="check-basics-null"
`CustomerMapping` converts `email` through its leaf. What does `CustomerMappingImpl.INSTANCE.parse(new CustomerDto(null, "not-an-email"))` return?

1. It throws a `NullPointerException`
2. Invalid, with `name: must not be null` only
3. Invalid, with `email: not an email address` only
4. Invalid, with `name: must not be null` and `email: not an email address`
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-basics-null-answer"
**4.** The `null` name becomes a located error, not an exception, and it accumulates beside the bad email. Nothing stops at the first failure:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/BasicsBookTest.java:null_and_leaf}}
```

Where this lives: [Null has an address, not a stack trace](#null-doctrine) and [Validated leaves](#validated-leaves).
~~~

---

## Renames: `@MapField` {#renames-mapfield}

When the wire calls it `fullName` and the domain calls it `name`, declare an abstract method named after the *domain* component, with `to` naming the *wire* component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:rename_spec}}
```

Each wire component takes exactly one domain source, so colliding renames are compile errors, not surprises. A rename declares a concrete return type, which the generated stub only names and never calls.

~~~admonish note title="Error paths use domain names"
A wire sending `fullName` gets its errors at `name`, renames included. Every path in the system is domain-named, so paths stay consistent, and stable when the wire is refactored. A client that maps errors back onto its own payload keys applies the rename in reverse.
~~~

---

## Derived wire fields {#derived-wire-fields}

A wire component with **no domain counterpart** can be computed from the whole domain value: a `displayName` the domain does not store, because it is derivable. Declare a zero-parameter `default` method named after the *wire* component, returning `Getter<Domain, WireComponentType>`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:derived_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:derived_usage}}
```

The two directions are asymmetric: `build` computes the derived component, and `parse` throws it away, since the data is derivable and nothing is lost.

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

The optic is a `Getter` because a derived field is single-valued: exactly one focus, computed from the whole domain value. [Spec members](rules.md#spec-members) says how the processor tells a leaf from a derived field, and how a derived field meets the [emission tiers](tiers.md).

---

~~~admonish info title="Key Takeaways"
* **A mapping is an interface you own**: `@GenerateMapping` on a `MappingSpec<Domain, Wire>` generates `<Spec>Impl` with `build` and `parse`; bind it once in the calling code, never as a constant on the spec
* **Two directions, two shapes**: `build` is total, and `parse` reports every bad field at once, each located by a domain-named path
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
