# Record Mapping Basics

_Declare one interface; get a `build` that cannot fail and a `parse` that reports every bad field at once._

Most mappings are boring, and the mapper treats them that way: same-named, same-typed components match automatically, and one empty interface is the whole declaration. This page walks the happy path first: declare, build, parse, read the errors, convert a field, and meet a `null`. Renames and computed fields follow, for when you need them. The mapper needs Java 25 and the hkj Gradle plugin, and the [Quickstart](quickstart.md) sets both up. The precise rules live on a page of their own, [Rules and Limits](rules.md).

~~~admonish info title="What You'll Learn"
- Declare a mapping as a `MappingSpec<Domain, Wire>` interface, and keep its generated Impl in the calling code, never on the spec
- Use a `parse` result: the value, or every bad field at once, each located by its path
- Convert a field whose type differs with a `ValidatedPrism` leaf
- Predict what `parse` does with a `null`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [BasicsBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java) and its [BasicsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/BasicsBookTest.java)** - the page includes them directly, so they are compiled and run by the build.

For a larger worked example, see [GenerateMappingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/GenerateMappingExample.java).
~~~

## Your first mapping {#your-first-mapping}

The whole declaration is an empty interface naming the pair, domain first and wire second. The wire is the DTO: the shape that crosses the network.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:basics_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:basics_usage}}
```

That is all of it: no mapper class, no configuration. The processor derives both directions from the two records at compile time, without reflection. It re-derives them on every compile, so the mapping cannot drift away from the records it maps. Add a wire component that nothing fills, and the build stops: [`has more components than`](compiler_errors.md#wire-has-more-components).

The two directions have different shapes, and that asymmetry runs through the whole chapter:

```
   build : Domain ──▶ DTO      total, always succeeds
   parse : DTO ──▶ Domain      fallible, reports every bad field at once
                               Validated<NonEmptyList<FieldError>, Domain>
```

A [`Validated`](../monads/validated_monad.md) holds either the parsed value (`Valid`) or every error (`Invalid`), and a [`NonEmptyList`](../monads/nonemptylist_monad.md) is a list with at least one element.

The processor generates `PersonMappingImpl` in the spec's package, and you reach it through its `INSTANCE` constant. A spec nested in an outer class joins the enclosing simple names: `Shop.CustomerMapping` generates `ShopCustomerMappingImpl`. A [generic spec](generics.md#one-rule-three-access-shapes) is reached through `instance()` or `of(...)` instead.

### Bind in the caller, not on the spec {#bind-in-the-caller}

Code that calls a mapping more than once keeps the Impl in a variable, as `personMapping` does, and reuses it. Reading `INSTANCE` costs nothing, so the variable is only for readability. Type it as the Impl, since `build` and `parse` live there, not on the spec. A local suits a few calls in one method, and a `private static final` field suits a class that maps in several methods. In Spring, register the mapping as a `ValidatedPrism` bean and inject that, as the example app does:

``` java
{{#include ../../../hkj-spring/example/src/main/java/org/higherkindedj/spring/example/config/MappingConfiguration.java:mapping_configuration}}
```

[Injecting and testing generated mappings](testing.md#injecting-and-testing-generated-mappings) lists the surface to register for each kind of mapping.

~~~admonish warning title="Not checked for you: never bind the Impl on the spec"
MapStruct's idiom puts the mapper's instance on its own interface. Here that would be `CustomerMappingImpl MAPPER = CustomerMappingImpl.INSTANCE;`, declared on `CustomerMapping`, the spec with an email leaf in [Validated leaves](#validated-leaves). It compiles, and it can read `null`. If any code uses `CustomerMappingImpl.INSTANCE` before the first read of `CustomerMapping.MAPPER`, the constant stays `null` for good, and the next `MAPPER.parse(...)` throws a `NullPointerException`. Which class a program reaches first depends on its code paths, so the failure comes and goes. A spec with no leaf or derived field is safe, so the constant can work for months and break the day someone adds one. Keep the Impl in the calling code instead.
~~~

~~~admonish note title="Why the constant reads `null`" collapsible=true
The Impl implements the spec. Initialising a class first initialises every interface it implements that declares an instance method with a body: every `default` leaf and derived field, and any `private` instance helper. Using `CustomerMappingImpl.INSTANCE` first starts the Impl's initialisation, which initialises `CustomerMapping` before `INSTANCE` is assigned. `MAPPER` is evaluated then, and keeps the `null` it read. Two threads making those first uses at the same moment can deadlock instead. A constant on a [mix-in](codecs.md#shared-vocabulary-mix-in-interfaces) that declares a leaf fails the same way. The processor sees a field's type but not its initialiser, so it cannot tell this constant from a harmless one, and does not refuse it.
~~~

---

## Validated leaves {#validated-leaves}

Real boundaries convert: the wire sends a `String`, and the domain wants an email that has already been checked. A **leaf** is the conversion at a single field, where the mapping stops copying and one wire value becomes one domain value. Think of it as a Jackson serialiser and deserialiser for one field, except that a bad value comes back as an error naming the field, not an exception. The leaf itself is a [`ValidatedPrism`](../optics/validated_prism.md), two functions: a parse that may reject, and a render back to the wire that cannot fail:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:email_leaf}}
```

`validNel` and `invalidNel` build the two outcomes. The leaf's error carries only a message, and `parse` adds the field's path.

Attach it to the spec as a zero-parameter `default` method named after the domain component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:leaf_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:leaf_usage}}
```

The two type-argument orders are opposite. `ValidatedPrism<Wire, Domain>` reads the way `parse` runs, from wire to domain. `MappingSpec<Domain, Wire>` puts your domain type first.

The failure is a value, not an exception. It names the field by the domain component's name, whatever key the JSON used, as [Renames](#renames-mapfield) explains. With several bad fields, `parse` reports them all at once, so the client fixes everything in one round trip. Outside a controller, `fold` the result, with one function for the errors and one for the value. `Valid` and `Invalid` are records, so a `switch` works too:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:fold_usage}}
```

~~~admonish tip title="At the Spring boundary"
Returned as-is from a controller, the result becomes the single 422 response the Quickstart showed: [the 422 leg](../spring/spring_boot_integration.md#the-422-leg).
~~~

~~~admonish warning title="Not checked for you: keep a converted wire field a `String`"
Jackson binds the request before `parse` runs. Type a wire field that a leaf converts as a `String`. Typed as a `LocalDate` or an enum, a bad value fails inside Jackson, and the client gets Jackson's 400 instead of a located error. The Quickstart's [Read the 422](quickstart.md#4-read-the-422) step shows the difference.
~~~

Most leaves come ready-made: [Standard Codecs](codecs.md), the next page, covers identifiers, dates, enums and money. Coming from Bean Validation? [From Bean Validation](from_mapstruct.md#from-bean-validation) translates the common annotations.

~~~admonish note title="A leaf can check a field that needs no conversion"
An explicit leaf wins even when both types are identical, so a `ValidatedPrism<String, String>` can validate a field that would otherwise be copied. Check, but do not clean up: a parse that trims `" ada@example.com"` accepts a spelling `build` never writes back. That breaks the [section law](../optics/validated_prism.md#laws): an accepted wire value must rebuild to exactly itself. Reject the spaces instead.
~~~

---

## Null has an address, not a stack trace {#null-doctrine}

Jackson leaves a missing property `null`, so a boundary meets nulls constantly. The rule: **`parse` checks every value it reads from the wire, and a `null` becomes a `must not be null` error at that field's path, reported beside every other bad field, never thrown.**

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:null_usage}}
```

The path reaches into nested records (`customer.name`) and lists (`emails.1`, the second email). A `null` never reaches a leaf, so a leaf needs no null check of its own.

A primitive wire component such as `int age` never holds a `null`, so the rule does not reach it: what a missing or malformed one becomes is Jackson's decision.

~~~admonish tip title="Why this matters"
Compare the alternatives you have debugged before: an NPE with a stack trace pointing into generated code, or Jackson's `MismatchedInputException` naming a Java class. A located error names the field by its path, sits beside every other defect in the same response, and costs the client one round trip instead of one per `null`.
~~~

The exact contract, including what happens inside containers, is in [The null contract, precisely](rules.md#the-null-contract-precisely). A `null` DTO passed to `parse` still throws, since that one is the caller's bug. A field whose `null` *means* something can opt out of this rule, one field at a time, as [Absent Fields and Record Invariants](absence.md#optional-bridge) shows.

~~~admonish tip title="You can ship now"
You can now map a record DTO to a domain record and back, check each field as it arrives, and answer with every bad field at once. Every field is required: to let one be left out, see [Absent Fields](absence.md#optional-bridge). The rest of this page, [renames](#renames-mapfield) and [computed wire fields](#derived-wire-fields), is for when a pair needs them.
~~~

~~~admonish question title="Checkpoint: which `MAPPER` can read `null`?" id="check-basics-constant"
Both of these specs carry the MapStruct-style constant. In each program, some code uses the Impl's `INSTANCE` before anything reads the spec's `MAPPER`. Afterwards, which `MAPPER` can read `null`?

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:mapper_constants}}
```

1. Neither: an interface constant is set before any code can use it
2. `TicketMapping.MAPPER` only
3. `PassMapping.MAPPER` only
4. Both
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-basics-constant-answer"
**3.** `PassMapping` declares a leaf, so using `PassMappingImpl.INSTANCE` first leaves its `MAPPER` `null` for good. `TicketMapping` has no leaf, so its constant is safe, for now:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/BasicsBookTest.java:constant_proof}}
```

The day someone adds a leaf to `TicketMapping`, its constant breaks too. Delete both constants, and keep each Impl in the calling code.

Where this lives: [Bind in the caller, not on the spec](#bind-in-the-caller).
~~~

~~~admonish question title="Checkpoint: does a `null` reach the leaf?" id="check-basics-null"
`CustomerMapping` converts `email` through the [email leaf](#validated-leaves), which calls `raw.contains("@")`. What does `CustomerMappingImpl.INSTANCE.parse(new CustomerDto(null, null))` return?

1. It throws a `NullPointerException` from the leaf's `raw.contains("@")`
2. Invalid, with `name: must not be null` only
3. Invalid, with `name: must not be null` and `email: must not be null`
4. Invalid, with `name: must not be null` and `email: not an email address`
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-basics-null-answer"
**3.** Each `null` becomes a located error, and neither stops the other. The null check runs before the leaf, so the leaf never sees the `null` email:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/BasicsBookTest.java:null_before_leaf}}
```

Where this lives: [Null has an address, not a stack trace](#null-doctrine).
~~~

---

## Renames: `@MapField` {#renames-mapfield}

When the wire calls it `fullName` and the domain calls it `name`, declare an abstract method named after the *domain* component, with `to` naming the *wire* component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:rename_spec}}
```

Each wire component takes exactly one domain source, so the processor refuses two renames onto one name. Give the method the domain component's type, as `String name()` does. The Impl only names the method, and never calls it.

~~~admonish note title="Error paths use domain names, not JSON keys"
A client that sent `fullName` gets its errors at `name`. The same holds for a Jackson rename: under `@JsonProperty("first_name")` on a `firstName` component, or a snake_case naming strategy, the path stays `firstName`. Every path in the system is domain-named, so paths stay consistent, and stable when the wire is refactored. A client that maps errors back onto its own payload keys applies the renames in reverse.
~~~

---

## Derived wire fields {#derived-wire-fields}

A wire component with **no domain counterpart** can be computed from the whole domain value: a `displayName` the domain does not store, because it is derivable. Declare a zero-parameter `default` method named after the *wire* component, returning `Getter<Domain, WireComponentType>`. Read the `Getter` as a `Function<Domain, WireComponentType>`: `Getter.of` takes a plain lambda.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:derived_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:derived_usage}}
```

The two directions are asymmetric: `build` computes the derived component, and `parse` throws it away, since the data is derivable and nothing is lost. A client that sends `displayName` has it ignored, not checked.

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

[Spec members](rules.md#spec-members) says how the processor tells a leaf from a derived field, and [What Your Spec Generates](tiers.md) what a derived field changes in the generated methods.

---

~~~admonish info title="Key Takeaways"
* **A mapping is an interface you own**: `@GenerateMapping` on a `MappingSpec<Domain, Wire>` generates `<Spec>Impl` with `build` and `parse`, which the calling code keeps, never a constant on the spec
* **Two directions, two shapes**: `build` is total, and `parse` returns the value or every bad field at once, each located by a domain-named path
* **Leaves convert, renames rename, getters derive**: `ValidatedPrism` leaves for fields whose types differ, `@MapField` for names, `Getter` defaults for wire-only fields
* **Null is located, never thrown**: a `null` becomes `must not be null` at its path, inside containers too, and never reaches a leaf
~~~

~~~admonish tip title="See Also"
- [Validated Prisms](../optics/validated_prism.md): The type behind every leaf, and how to write your own
- [Standard Codecs and Shared Vocabulary](codecs.md): The stock leaf vocabulary and how to share it
- [Absent Fields and Record Invariants](absence.md): A field whose `null` means *absent*, and a constructor's refusal reported as an error
- [The 422 leg](../spring/spring_boot_integration.md#the-422-leg): The parse result as one HTTP response
~~~

---

**Previous:** [Quickstart: Your First 422](quickstart.md)
**Next:** [Standard Codecs and Shared Vocabulary](codecs.md)
