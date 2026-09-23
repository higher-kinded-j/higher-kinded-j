# Record Mapping Basics

_Declare one interface; get a `build` that cannot fail and a `parse` that reports every bad field at once._

Most mappings are boring, and the mapper treats them that way: same-named, same-typed components match automatically, and one empty interface is the whole declaration. This page walks the happy path first (declare, build, parse, read the errors), then adds the declarations you will actually reach for: a conversion, a rename, a computed field, and an optional one. The precise rules live on a page of their own, [Rules and Limits](rules.md).

~~~admonish info title="What You'll Learn"
- Declaring a mapping as a `MappingSpec<Domain, Wire>` interface and calling the generated Impl
- Reading a `parse` failure: every bad field at once, each located by name
- Converting a type-differing field with a `ValidatedPrism` leaf
- Renaming components with `@MapField`, and computing wire-only fields with derived getters
- Why a wire `null` becomes a located error, never an exception
- Declaring the one field where a `null` means *absent* instead, with `@OptionalBridge`
- How an invariant the domain's own constructor enforces reports, located at the record
~~~

~~~admonish example title="See Example Code"
**The code on this page is [RecordMappingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java)** - the page includes it directly, so it is compiled and run by the build.

[GenerateMappingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/GenerateMappingExample.java)
~~~

## Your first mapping

The whole declaration is an empty interface naming the pair:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_usage}}
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
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:email_leaf}}
```

Attach it to the spec as a zero-parameter `default` method named after the domain component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:leaf_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:leaf_usage}}
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
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:rename_spec}}
```

Each wire component takes exactly one domain source; colliding renames are compile errors, not surprises. A rename declares a concrete return type (the generated stub only names it, never calls it), and agreeing renames inherited from [mix-ins](codecs.md#shared-vocabulary-mix-in-interfaces) fold into one stub returning the narrowest declared type.

Error paths use **domain** component names, renames included: a wire sending `fullName` gets its errors at `name`. Every path in the system is domain-named, so paths stay consistent and stable under wire refactors; a client mapping errors back onto its own payload keys applies the rename in reverse.

---

## Derived wire fields {#derived-wire-fields}

A wire component with **no domain counterpart** can be computed from the whole domain value: a `displayName` the domain does not store because it is derivable. Declare a zero-parameter `default` method named after the *wire* component, returning `Getter<Domain, WireComponentType>`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:derived_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:derived_usage}}
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

The exact contract (what happens inside containers, and which nulls remain the caller's bug) is in [The null contract, precisely](rules.md#the-null-contract-precisely). The next section covers the one deliberate exception: a field whose `null` *means* something.

---

## Optional fields: `@OptionalBridge` {#optional-bridge}

Sometimes a wire `null` is not a defect: it is how the client says *this field is absent*. A domain `Optional<String> nickname` against a wire `String nickname` is the shape, and it is the shape real record DTOs take, because a JSON binder writes absence as `null` and not as an `Optional`.

Say so per component with `@OptionalBridge`, and the pair maps in both directions:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:bridge_spec}}
```

```
  build : empty ──▶ null                parse : null ──▶ Optional.empty()
          present ──▶ the value                 value ──▶ Optional.of(value)
                                                          (through its leaf or spec, if any)
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:bridge_usage}}
```

The annotation has **two placements**, and which one a component takes is decided by one question: does the present value need a leaf?

| The present element | Where the annotation goes | What it declares |
| --- | --- | --- |
| Copies as-is, or is mapped by its own spec | An abstract marker method named after the domain component | `@OptionalBridge Optional<String> nickname();` (the return type restates the component) |
| Converts through a leaf | That component's own `default` leaf | `@OptionalBridge default ValidatedPrism<String, EmailAddress> altEmail()`, declared over the **element** types |

The two can never be combined, and not by choice: a marker and a same-named leaf are one method with two incompatible return types, which javac rejects before the processor sees it.

An element pair that already has a `@GenerateMapping` spec needs no leaf: the marker is enough, and a present value [nests through that spec](structure.md#optional-nested-objects) exactly as an unbridged component of that pair would. A bridged `List`, `Set`, array or `Map` lifts its elements the same way, through their spec or through a leaf over the element types.

~~~admonish warning title="Opt-in, never inferred"
The processor will not guess this. Without the annotation, `nickname = null` is a located `must not be null`, exactly as [the doctrine above](#null-doctrine) says, and that is the right default: on most record wires a `null` really is a defect. The bridge is the one place a spec overrides it, one component at a time, in writing.

A [bean wire](beans_patch.md) needs no annotation: bean conventions leave `Optional` off property types, so the bridge is automatic there. Declaring it on a bean spec is redundant, and the processor says so with a note rather than an error, because a [shared mix-in vocabulary](codecs.md#shared-vocabulary-mix-in-interfaces) may legitimately serve both wire shapes.
~~~

~~~admonish note title="Under `@NullMarked`"
The bridged wire component is nullable by construction: `build` writes `null` into it for an absent value, so it must be declared to take one. Declare it `@Nullable String nickname`; [A bridged component must take `null`](rules.md#bridged-component-nullable) says which declarations the processor refuses, and where the annotation goes on an array.
~~~

~~~admonish example title="The same pair without the annotation, refused"
<!-- verify:rejects "Add '@OptionalBridge java.util.Optional<java.lang.String> nickname();' to the spec" -->
```java
import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;

record Reader(String name, Optional<String> nickname) {}

record ReaderDto(String name, String nickname) {}

@GenerateMapping
interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {}
```

The processor says:

```
@GenerateMapping: target field 'ReaderDto.nickname' has no usable source. The types differ
(java.lang.String vs java.util.Optional<java.lang.String>) and no matching leaf method was
found. Found on Reader: [name, nickname]. Add '@OptionalBridge
java.util.Optional<java.lang.String> nickname();' to the spec, so an absent value reads as a
null wire component and back. Add 'default ValidatedPrism<java.lang.String,
java.util.Optional<java.lang.String>> nickname()' to the spec.
```

The refusal names the bridge first, and offers the whole-`Optional` leaf second. The two are not alternatives for the same job: a leaf maps the pair, but leaves `null` a located error, so only the bridge gives the field an absent state. Declaring the annotation *on* such a leaf is refused rather than silently ignored.
~~~

~~~admonish example title="The annotation on a bean wire, reported as redundant"
<!-- verify:reports "@OptionalBridge on 'nickname' is redundant on a bean wire" -->
```java
import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.OptionalBridge;

record Guest(String name, Optional<String> nickname) {}

class GuestBean {
  private String name;
  private String nickname;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getNickname() { return nickname; }
  public void setNickname(String nickname) { this.nickname = nickname; }
}

@GenerateMapping
interface GuestMapping extends MappingSpec<Guest, GuestBean> {
  @OptionalBridge
  Optional<String> nickname();
}
```

The processor says:

```
@GenerateMapping: @OptionalBridge on 'nickname' is redundant on a bean wire. A bean wire
bridges a domain Optional to its nullable property automatically, because bean conventions
leave Optional off property types; the annotation opts a RECORD wire into the same
correspondence. Remove the annotation, or keep it if the vocabulary is shared with a
record-wire spec.
```

A note, not an error: the mapping is generated exactly as it would be without the annotation.
~~~

A bridged component is a non-identity correspondence, so the mapping does not gain `asIso()`, and a [projection](tiers.md) carrying one takes the validated `patch(Domain, Wire)` rather than `asLens()`, which is the same tier arithmetic a bean bridge has always had.

---

## A record's own invariants {#constructor-invariants}

A domain record often guards itself: a compact constructor that throws when its components disagree. `parse` keeps that guard and still returns a value. Once every component of the record has parsed, the generated code calls its canonical constructor, and a `RuntimeException` the constructor throws becomes a `FieldError` at the record's own path, carrying the exception's message:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:invariant_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:invariant_usage}}
```

The second stay fails at `stays.1`, and the missing guest is still reported beside it. The rules:

- **The record is the address.** A cross-field invariant belongs to no single component, so it locates where the record does: under the component that holds it (`stays.1`), or unlabelled at the top level.
- **The constructor runs last.** It needs every component, so it runs only once all of them have parsed. A record therefore reports either its components' errors or its invariant, never both, while everything around it keeps accumulating as usual.
- **The message is what the client reads**, so write it for them. An exception without a message, or with a blank one, reads `not a valid Stay`.
- **Any `RuntimeException` counts, bugs included.** The null guard keeps a `null` out of the constructor, but a constructor that divides by zero or dereferences something of its own fails the same way: its message goes to the client and its stack trace is dropped. Keep the constructor to checks on its arguments, with messages written for a client.
- **A rule about one field alone belongs in a [leaf](#validated-leaves)**: it locates at the field and accumulates with the record's other errors.

The same guard covers every surface that builds the record whole from parsed parts. Only `asIso().reverseGet`, a projection's `asLens().set` and a sparse update's `toValidated()` let the exception propagate instead: [which surfaces a refusal reaches](rules.md#constructor-refusal-surfaces).

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
* **Absence is declared, never guessed**: `@OptionalBridge` opts one `Optional` component into reading `null` as absent, and on a record wire nothing else does
* **A record's own invariant is located too**: an exception from its constructor becomes a `FieldError` at the record's path, beside every other error
~~~

~~~admonish tip title="See Also"
- [Validated Prisms](../optics/validated_prism.md): The leaf optic every fallible correspondence is built from
- [Standard Codecs and Shared Vocabulary](codecs.md): The stock leaf vocabulary and how to share it
- [The 422 leg](../spring/spring_boot_integration.md#the-422-leg): The parse result as one HTTP response
~~~

---

**Previous:** [Quickstart: Your First 422](quickstart.md)
**Next:** [Standard Codecs and Shared Vocabulary](codecs.md)
