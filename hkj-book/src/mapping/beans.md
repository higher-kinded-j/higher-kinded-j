# Bean-Shaped Wires

_Map getter/setter and builder classes with the same features as records, including ones only read or written._

Generated client models, JAXB payloads and many legacy DTOs are beans: classes with getters and setters, or a builder, rather than records. They map with the same leaves, renames and derived fields that [Record Mapping Basics](basics.md) teaches for records. This page covers what changes, including a bean that is only ever read or only ever written. A bean used as a PATCH request, where `null` means *not sent*, has a page of its own, [Sparse PATCH](beans_patch.md).

~~~admonish info title="What You'll Learn"
- Mapping bean-shaped wire types (setters, builders, JAXB lists) with the same features as records
- Why a bean mapping withholds `asIso()`, and how `Optional` bridges through `null` here without a declaration
- Why a bean projection with a reference property takes the validated `patch` rather than `asLens()`
- Keeping an accessor out of the mapping on purpose, with `@Unmapped`
- Mapping a bean that can only be read, or only be written: `parse` alone or `build` alone, and where each nests
~~~

~~~admonish example title="See Example Code"
**The code on this page is [BeansBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BeansBook.java) and its [BeansBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/BeansBookTest.java)** - the page includes them directly, so they are compiled and run by the build.
~~~

## Bean-shaped wire targets {#bean-shaped-wire-targets}

The wire side need not be a record. A **bean** (a mutable class with a no-args constructor and getters/setters, or an immutable one with a builder) maps the same way, with the same features (renames, leaves, derived fields, container lifting, nesting). Only *how* the wire is read and written changes: `build` fills through setters or a builder, and `parse` reads through getters.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BeansBook.java:bean_spec}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BeansBook.java:bean_usage}}
```

The design decisions worth knowing:

- **Null is located, never thrown, like every wire.** The null guard is universal ([one rule](basics.md#null-doctrine), both shapes), so a `null` property read is a located `FieldError` exactly as on a record wire. What is bean-*specific* is why nulls are expected at all: an unset property is a representable, ordinary state of a mutable bean, not just a hostile binding.
- **Honest tiers.** Because an unset property is ordinary, a bean's guarded reference reads count as fallible and the mapping withholds `asIso()` automatically; an all-primitive bean (whose reads can never be null) still earns it. A record wire's guards exist for hostile bindings only, so a lossless record mapping keeps `asIso()`, with the parse-iso coherence law scoped to wires whose reference components are non-null and whose values the domain accepts. Nesting is unaffected: a bean mapping that builds and parses exposes `asValidatedPrism()` like any other, so record specs nest it and containers lift it, and a [one-directional](#one-directional-beans) one exposes the half it has, nesting wherever only that direction is used.
- **Construction strategy** is detected from the bean's shape, tried in order: a public no-args constructor with `setX` setters (and, for a getter-only `List`, the JAXB convention `getItems().addAll(...)`); then a static `builder()`/`newBuilder()` whose setters fill it and whose `build()` yields the wire. A bean with getters that fits neither is only ever read, so it maps [parse-only](#one-directional-beans); a bean with nothing to read or write gets a what/why/fix diagnostic.
- **A property is a getter and a writer that share a name.** Getters are `getX()`, and `isX()` returning `boolean` or `Boolean` (the shape JAXB declares for an optional boolean); where a bean declares both for one name, `getX()` reads it. A getter nothing writes, or a writer nothing reads, is left out of the mapping, which suits a computed getter such as `getSummary()` or a builder's singular adder. When an unpaired accessor is named after a domain component the bean carries under no name, the one the component maps under (its own, or the one a `@MapField` rename gives it), leaving it out would drop that component without a word, so it is refused. The diagnostic names the fix: [when an unpaired accessor is refused](rules.md#unpaired-accessors) lists what it offers, and [Accessors meant to stay out](#accessors-meant-to-stay-out) covers the marker for one left out on purpose.
- **Optionality bridges through `null`, automatically here.** On a full mapping, a domain `Optional<T>` maps to a nullable bean property `T` (bean conventions leave `Optional` off property types): empty bridges to absent (`build` writes `null`, replacing whatever the bean or its builder started with; `parse` reads `Optional.ofNullable(...)`), and a present value still validates through its leaf, or [nests through its own spec](structure.md#optional-nested-objects). This is the *only* wire shape where the bridge needs no declaration: a record wire opts in per component with [`@OptionalBridge`](absence.md#optional-bridge), which buys the same correspondence, and declaring it on a bean spec is redundant (a note, not an error, so one mix-in can serve both shapes). One property shape refuses the bridge, [a getter-only `List`](rules.md#getter-only-list-refuses-the-bridge), which has no unset state to carry absence. The [sparse tier](beans_patch.md#what-each-json-state-does) is the deliberate exception in the other direction: there `null` already means "leave unchanged", so a PATCH bean encodes "set to empty" with an `Optional`-typed property instead.
- **A bridged property's writer must take `null`.** `build` never skips a write, so a bean's own defaults (a field initialiser, a builder's default) cannot survive it and read back as present. The price is that an empty `Optional` reaches the setter or builder setter as `null`. A setter that copies defensively needs a guard (`v == null ? null : List.copyOf(v)`). A parameter declared non-null, by a non-null annotation or by a `@NullMarked` scope with no `@Nullable` on it, is refused, as a bridged record component is: mark it `@Nullable` (on a Lombok bean, on the field, which Lombok copies to the setter). A generated builder that refuses `null` (protobuf, Immutables) cannot be changed, so declare the component without the `Optional`, or give it a leaf over the whole `Optional` that encodes absence the builder's way (`ValidatedPrism<String, Optional<String>>` mapping empty to `""`), which wins over the bridge. A default the bean applies to a `null` it is given, in the setter, a builder's `build()` or the getter, still reads back as present; `MappingLaws` catches it.
- **The domain stays a record.** `parse` assembles the domain through its canonical constructor, so only the *wire* may be bean-shaped; a bean domain gets a diagnostic.

The precise rules for bean wires, from an unpaired accessor to what a getter-only `List` must declare, are in [Bean wires](rules.md#bean-wires).

### Bean projections {#bean-projections}

A bean with *fewer* properties than the domain is a projection, as a smaller record wire is, but the same shape can land on a different tier. A record is constructed whole, so a record projection that copies by identity keeps its lawful `asLens()`: a `null` component there is a hostile binding, not a state the type invites. A bean is constructed empty and filled by setters, which makes an unset reference property an ordinary state, and a lens's `set` cannot fail, so it has no honest answer for one. A bean projection with any reference property therefore takes the [validated `patch`](tiers.md#leaf-carrying-projections-the-validated-patch), even when every property copies by identity:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BeansBook.java:bean_projection_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BeansBook.java:bean_projection_usage}}
```

Everything else is the record-wire tier unchanged: every projected property is validated, every bad one is located and accumulated, and the unprojected components are read from the domain argument, so they survive by construction. Leaves, nested specs and container lifting all apply, and so does the automatic `Optional` bridge: a bridged property left unset reads as empty, so `patch` writes `Optional.empty()` rather than keeping the current value. The same `MappingLaws` patch overload law-checks it; the laws compare domain values only, so the bean needs no `equals`. An all-primitive bean projection, whose reads can never be null, keeps its lawful `asLens()`.

This is not the REST PATCH contract, even when the bean is a PATCH request: an unset property never means "keep the current value". For that, extend `UpdateSpec` ([Sparse PATCH](beans_patch.md#sparse-patch-write-back-updatespec)).

`patch` only reads the bean, through its getters, but the Impl also carries `build`, which writes one, so a bean projection still needs one of the construction strategies above. A getter-only bean narrower than the domain is not a projection at all: nothing writes it, so it maps [parse-only](#one-directional-beans), and every domain component then needs a getter.

### Accessors meant to stay out {#accessors-meant-to-stay-out}

Some beans leave an accessor unpaired on purpose. A response DTO reused as the PATCH body carries a server-assigned `getId()` the client must not change; a view computes `getStatus()` on the wire; a generated request has a setter the domain does not model. Pairing such an accessor is the wrong fix, and the bean is often not yours to edit, so the spec says the omission is deliberate with an abstract `@Unmapped` marker named after the *accessor's* property:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BeansBook.java:unmapped_spec}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BeansBook.java:unmapped_usage}}
```

The marker only withholds the refusal: the accessor was never a property, so the component it names stays unmapped, a wire narrower than the domain is still a projection, and nothing else about the generated Impl changes. It reaches a full mapping and a [sparse `UpdateSpec`](beans_patch.md#sparse-patch-write-back-updatespec) alike, and both refusals it answers: [an accessor named after a domain component](rules.md#unpaired-accessors), and [a `setX` setter a PATCH bean cannot read](rules.md#every-patch-setter-has-a-getter). The return type is not read, so it may restate the accessor's own type, and the marker is stubbed out by the Impl like a rename.

A marker the spec declares itself must name an accessor the bean leaves unpaired: one naming a property the mapping carries, or naming nothing at all, is refused as the misspelling it usually is. One inherited from a [mix-in](codecs.md#shared-vocabulary-mix-in-interfaces) binds where it can and is otherwise inert, like every other inherited vocabulary member, so one mix-in serves specs whose wires differ.

### One-directional beans {#one-directional-beans}

Some beans are only ever crossed one way. A generated client's response type, an immutable view built through its constructor, or a third-party result offers getters and nothing that writes it; an outbound request, or a write model behind a builder, is filled and never read back. Neither can support both directions, so the Impl carries the one it can and nothing for the other: the missing direction is absent, never a method that throws.

| The bean offers | It maps | The Impl carries |
|---|---|---|
| properties it can both read and write | both ways, as above | `build`, `parse`, `asValidatedPrism()` and the rest of its tier |
| getters, and no setters or builder that fill it | parse-only, unless every getter is a getter-only `List` | `parse` and `asValidatedParse()` |
| setters or a builder, and no getters | build-only | `build` and `asValidatedBuild()` |

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BeansBook.java:one_way_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BeansBook.java:one_way_usage}}
```

The bean's shape decides, and a note says which way it was read and why, so an unintended reading does not go unnoticed: a bean meant to be built whose no-args constructor the generated Impl cannot reach reads parse-only, and the note says the constructor is out of reach. The two-way reading wins whenever any property allows it, so a bean is one-directional only when nothing at all crosses the other way. [How a bean's direction is read](rules.md#how-a-beans-direction-is-read) covers the mixed cases, such as a bean that reads some names and writes others. One of them maps both ways: a bean whose every getter is a getter-only `List`, which `build` fills the JAXB way, through `getX().addAll(...)`.

The rules follow from which direction is missing:

- **Coverage belongs to the direction.** A parse produces the domain, so every domain component needs a getter, and a getter no component names is ignored. A build produces the bean, so every writer needs a source, a domain component or a [derived field](basics.md#derived-wire-fields), and a domain component the bean does not carry is not written. Neither is a projection, since nothing is written back.
- **The rest of the vocabulary is unchanged.** Renames, leaves, container lifting and the automatic `Optional` bridge work in whichever direction exists: a leaf parses on a parse-only bean and builds on a build-only one.
- **Nesting follows the direction.** A one-directional mapping nests wherever only its direction is used, lifted through containers like any other: a parse-only spec inside a parse-only mapping, a [sparse `UpdateSpec`](beans_patch.md#sparse-patch-write-back-updatespec) or a [`@GenerateMerge`](merge_envelopes.md) source, and a build-only spec inside a build-only mapping. A full mapping nests in all of them. Where the missing direction is needed, the failed lookup names the one-directional spec and what it lacks; sealed dispatch needs both directions of every subtype pair.
- **Law-check the surface it has.** `MappingLaws` takes `asValidatedParse()` with a parsing and a non-parsing wire, or `asValidatedBuild()` with a domain value ([What Your Spec Generates](tiers.md#law-checked-in-the-repo-and-in-your-tests)):

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/BeansBookTest.java:one_way_laws}}
```

---

~~~admonish info title="Key Takeaways"
* **Beans map with the full feature set**: only the read/write mechanics differ, and the tiers stay honest (`asIso` is withheld, and a projection takes `patch` rather than `asLens`, where unset properties make reads fallible)
* **A bean crossed one way maps that way**: a read model gets `parse` alone and a write model `build` alone, each nesting where its one direction is used
~~~

~~~admonish tip title="See Also"
- [Sparse PATCH](beans_patch.md): A bean as a PATCH request, where `null` means *leave unchanged*
- [Bean wires](rules.md#bean-wires): The precise rules, from an unpaired accessor to a getter-only `List`
- [What Your Spec Generates](tiers.md): Which methods each spec shape gets, and why a bean mapping withholds `asIso()`
~~~

---

**Previous:** [What Your Spec Generates](tiers.md)
**Next:** [Sparse PATCH](beans_patch.md)
