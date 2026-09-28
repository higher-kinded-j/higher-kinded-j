# Mapper at a Glance

_What it generates, what it costs, and the decisions to know before adopting it._

`@GenerateMapping` turns one interface you own into a mapper the compiler writes. Outbound, a
`build` that cannot fail. Inbound, a `parse` that reports every bad field at once, each located by
a dotted path. For the partial cases, a write-back. It runs at compile time and produces plain
Java, so there is no reflection, no registry, and nothing to configure at startup.

This page is for judging the fit. If you would rather see it working first, the
[Quickstart](quickstart.md) is five steps to a real 422.

---

## Install

```kotlin
plugins {
    id("io.github.higher-kinded-j.hkj") version "LATEST_VERSION"
}
```

The plugin wires `hkj-core`, the annotation processor, `-parameters` and the preview flags. The
build runs on **Java 25** today, with preview features enabled: `javac` accepts `--enable-preview`
only for the release it is running on, so a later JDK waits on the library moving to it rather than
on a version bump. Hand-rolled Gradle and Maven builds are in
[Manual setup](../tooling/manual_setup.md), which also covers **Lombok** (list it before
`hkj-processor`) and [types another processor generates](../tooling/manual_setup.md). For HTTP
responses, add `hkj-spring-boot-starter`.

---

## What it writes

A lossless pair, declared as an empty interface:

``` java
@GenerateMapping
interface PersonMapping extends MappingSpec<Person, PersonDto> {}
```

The processor writes what follows. It is a golden file in the processor's own tests, pinned byte
for byte, so it is exactly what your build produces:

~~~admonish example title="PersonMappingImpl.java, as generated" collapsible=true
``` java
{{#include ../../../hkj-processor/src/test/resources/golden/mapping/PersonMappingImpl.java.golden}}
```
~~~

Plain static code: no reflection, no proxies, nothing to warm up, and a stack trace that lands in a
file you can open. `build` copies; `parse` assembles the record through a `fields()` ladder that
names each component, which is where the located errors come from; `asValidatedPrism()` is how one
mapping nests inside another.

---

## Decisions to know before adopting

| The decision | Why it is that way | What you do about it |
|---|---|---|
| A conversion is declared per component, never inferred from the types | An implicit `String` to `UUID` conversion is a guess about a boundary the library cannot see | Declare a leaf, and share the common ones through one [mix-in vocabulary](codecs.md#shared-vocabulary-mix-in-interfaces) |
| Error paths use **domain** component names | Paths stay stable when the wire is renamed | Where a client needs wire names, map them back through the rename, or read the structured `segments` |
| A wire `null` is a located error unless you say otherwise | On most wires a `null` really is a defect | [`@OptionalBridge`](absence.md#optional-bridge) per component; automatic on bean wires |
| The domain must be a record | `parse` builds through the canonical constructor | Keep entities at the far side of the boundary, and map to them from the record |
| The annotation goes on your spec interface, never on the mapped types | Records, sealed hierarchies and beans from a compiled library cannot be edited | Declare the spec in your own code: `@GenerateMapping interface VendorOrderMapping extends MappingSpec<VendorOrder, OrderDto> {}` |
| Sparse PATCH is opt-in, bean-only and wrapper-typed | A primitive can never be absent, and a record component is always present | [`UpdateSpec`](beans_patch.md#sparse-patch-write-back-updatespec) with `Integer`, `Boolean` and friends |
| A PATCH replaces a nested object wholesale | Deep merge is out of scope | Patch the nested record through its own spec |
| A spec-carrying module on the **module path** neither writes nor reads the cross-module index | The index is one package, and a package belongs to one module | Delegate with a leaf calling the other Impl, or pass `-Ahkj.mapping.index=false` |
| The generated Impl is bound in the caller, never as a constant on the spec | Class initialisation can leave that constant `null`, intermittently; the processor warns at one typed as the Impl | `XImpl.INSTANCE` at the call site, or an injected surface: [why](rules.md#impl-constant-on-a-spec) |

Everything the processor refuses says so at compile time, with what is wrong, why it matters and
the code to write. The complete set is in [Rules and Limits](rules.md), and the messages you are most likely to meet
are in [Compiler Messages](compiler_errors.md).

---

## Which wires map

| Wire shape | `build` | `parse` | Projection write-back | `updateFrom` |
|---|:--:|:--:|:--:|:--:|
| Record | ✅ | ✅ | `asLens()`, or `patch` where a field validates | ❌ (records cannot express absence) |
| Getter/setter bean | ✅ | ✅ | `patch` (any reference property), `asLens()` if all primitive | ✅ |
| Builder bean (`builder()`/`newBuilder()`) | ✅ | ✅ | as above | ✅ |
| Getter-only bean (nothing writes it) | ❌ | ✅ | ❌ | ❌ |
| Write-only bean (setters or builder only) | ✅ | ❌ | ❌ | ❌ |
| A two-way bean's [`@ReadOnly`](beans.md#read-only-properties) property (a getter, no setter) | ✅ (left out) | ✅ (read) | ❌ | ❌ |
| JAXB getter-only `List` | ✅ (through `addAll`) | ✅ | ✅ | ❌ (it can never read `null`) |
| Sealed hierarchy against sealed hierarchy | ✅ | ✅ | ❌ | ❌ |
| Generic record (`Page<T>`) | ✅ | ✅ | ✅ | ❌ (record-to-record only) |
| `JsonNullable` property (a `_JsonNullable` companion is [left out](rules.md#jsonnullable-companions)) | through a leaf | through a leaf | through a leaf | ❌, not supported yet |

A lossless pair also earns `asIso()`; a bean pair with a reference property does not, because an
unset property is an ordinary state. [What Your Spec Generates](tiers.md) explains which surface each spec gets, and why.

---

## What it costs

- **Generated at compile time**: the Impl is written once per build.
- **No reflection and no startup scan.** A concrete Impl is a singleton that keeps each leaf after
  its first use; an element-mapped one carries its leaf prisms and is built by `of(...)`.
- **One `Validated` per field read** on the parse side, which is the price of accumulating errors
  rather than throwing on the first.
- **Lines of spec per field**: [counted field by field](#what-you-write-field-by-field), against
  MapStruct and Bean Validation.
- **No component ceiling**: a flat 30-field wire maps like a narrow one.

### What you write, field by field {#what-you-write-field-by-field}

The project's mapping benchmark maps two pairs, a nested order and a flat record, four ways, so each
row is counted from code that compiles. The generated mapper's specs, a MapStruct mapper and the
same wire annotated for Bean Validation sit side by side in
[`OrderMappingModel`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/mapping/OrderMappingModel.java)
and
[`FlatMappingModel`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/mapping/FlatMappingModel.java),
and the benchmark checks each claim the last row makes. The last column is what the wire record
carries for Bean Validation.

| A field that | `@GenerateMapping` | MapStruct 1.6 | Bean Validation, on the wire |
|---|---|---|---|
| keeps its name and type | nothing | nothing | `@NotNull` |
| is renamed on the wire | `@MapField(to = "fullName") String name();` | `@Mapping(target = "fullName", source = "name")`, and its inverse or `@InheritInverseConfiguration` | `@NotNull` |
| needs a stock conversion: a `UUID`, an enum, an `Instant` | a [leaf](basics.md#validated-leaves) returning `StandardCodecs.uuid()` and friends | nothing: built in | `@NotNull @Pattern(regexp = …)` |
| is a `String` on the wire and a number in the domain | a leaf, with the domain component `Integer`, not `int`: a codec cannot name a primitive | nothing: built in | `@NotNull @Pattern(regexp = …)` |
| has a type of your own, such as an email address | one leaf, a parse and a render | a method each way | `@NotNull @Pattern(regexp = …)` |
| is a nested record, or a list of them | a spec for the nested pair | nothing | `@NotNull @Valid`, or `@NotNull List<@NotNull @Valid …>` |
| is derived on the wire | a `default` method returning a `Getter` | `@Mapping(target = …, expression = "java(…)")` | nothing |
| must be checked, every failure located | nothing more: the leaf is the check, and a `null` is refused, located | not available: a bad value throws or passes silently, and a `null` passes on | a check of each field's shape: a well-shaped bad value still fails in the conversion, located nowhere |

A leaf declared once on a [mix-in](codecs.md#shared-vocabulary-mix-in-interfaces) serves every
spec that extends it, as a MapStruct `uses =` class serves every mapper that names it. What each
approach asks of a whole mapper, from `Mappers.getMapper` to a `@ControllerAdvice` for violations,
is in [Coming from MapStruct and Bean Validation](from_mapstruct.md).

### What a call costs {#what-a-call-costs}

The order pair is a customer, a list of two line items, a UUID, an `Instant`, a currency and a
status. Its bad wire has five bad fields: a malformed id, email, timestamp and status, and a price
in exponent form, `1E+3`.

`build` checks nothing in any approach. On `parse`, only the generated mapper and Bean Validation
check the wire, so each is the other's comparison. The hand-written mapper and plain MapStruct stop
at the first problem with an exception that locates nothing, and let other bad values through, so
their figures are a floor rather than a rival.

| Order pair | `@GenerateMapping` | Hand-written | MapStruct | MapStruct and Bean Validation |
|---|---|---|---|---|
| `build` | 112 ns | 100 ns | 88 ns | 88 ns, as MapStruct |
| `parse`, a valid wire | 2,610 ns | 394 ns | 386 ns | 2,110 ns |
| memory that valid `parse` allocates | 13.6 KB | 1.8 KB | 1.8 KB | 6.7 KB |
| `parse`, five bad fields | 4,670 ns, all five located | 477 ns, the first thrown | 534 ns, the first thrown | 5,600 ns, all five located |
| the same, 100 frames deeper | 4,810 ns | 2,240 ns | 2,250 ns | 5,710 ns |

Against Bean Validation, a valid `parse` costs 24 per cent more, and a wire with five bad fields
16 to 17 per cent less. Where nothing validates today, a valid `parse` adds about 2,200 ns a
request, the price of checking and locating every field. A thrown exception's stack trace grows
with the caller's depth. So the two mappers that throw cost more than four times as much 100 frames
down, the benchmark's stand-in for a web framework's stack. The generated mapper rejects these five
fields without an exception: its stock codecs check a value's shape before a parser can throw, and
[your own leaf](codecs.md#your-own-canon) can too. The ten-field flat pair tells the same story,
with a valid `parse` 29 per cent over Bean Validation's and five bad fields 21 to 23 per cent under.

Two things favour Bean Validation here. It formats its messages with `ParameterMessageInterpolator`,
which evaluates no expression language, and its patterns check only a field's shape, so a value
that passes can still fail in the conversion after it.

Measured on 0.4.11 with MapStruct 1.6.3 and Hibernate Validator 9.1.0.Final, on an AMD Ryzen 9
7950X under Temurin 25.0.1. Each figure is the mean of four forks of five one-second iterations
after three of warm-up, and JMH's error margin is under 4 per cent of each. [The Mapping
Benchmark](../benchmarks.md#the-mapping-benchmark) says how to run it on your own hardware.

---

## Is it a fit for your estate?

~~~admonish note title="Twelve questions about your services" id="check-fit-test"
Each ✅ is work the mapper already does. Each ⚠ is a decision to make before adopting, not a
blocker. Each ❌ means keep what you have for that case.

| Your boundary | Verdict |
|---|:--:|
| Domain types are records | ✅ |
| Domain types are JPA entities or other mutable beans | ❌ keep MapStruct here |
| Wire types are records | ✅ |
| Wire types come from a generated client (getter/setter beans, builders) | ✅, except protobuf-java messages, not supported yet |
| Lombok `@Data`, `@Value`, `@Builder` or `@SuperBuilder` wires, `@Singular` included | ✅ (order Lombok before the processor; drop `@Singular` on a PATCH request) |
| Lombok `@Accessors(fluent = true)` wires | ⚠ the accessors are not `getX`/`isX`, so they do not pair |
| PATCH endpoints where an omitted field means *leave unchanged* | ✅ `UpdateSpec` |
| PATCH DTOs from openapi-generator with `default:` values in the schema | ⚠ the defaults read as sent: see [A PATCH getter must answer `null` until set](beans_patch.md#patch-getters-answer-null) |
| PATCH bodies that must distinguish *clear* from *absent* | ⚠ an `Optional`-typed property; `JsonNullable` is not supported yet |
| Nested objects patched field by field | ❌ replacement is wholesale |
| Clients that need error paths in **wire** names | ⚠ paths are domain-named |
| Spec-carrying libraries on the module path | ⚠ no cross-module index there |

The [estate capstone](estate.md) works a generated-client bean, a Lombok `@Data` bean and a
clearing PATCH across three modules.
~~~

---

~~~admonish info title="Where next"
- Five steps to a working endpoint: [Quickstart](quickstart.md)
- Coming from another mapper: [Coming from MapStruct and Bean Validation](from_mapstruct.md)
- What each spec shape generates: [What Your Spec Generates](tiers.md)
- The complete set of limits: [Rules and Limits](rules.md)
~~~

---

**Previous:** [Capstone: An Estate in Three Modules](estate.md)
**Next:** [Coming from MapStruct and Bean Validation](from_mapstruct.md)
