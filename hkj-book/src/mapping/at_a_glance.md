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
| Sparse PATCH is opt-in, bean-only and wrapper-typed | A primitive can never be absent, and a record component is always present | [`UpdateSpec`](beans_patch.md#sparse-patch-write-back-updatespec) with `Integer`, `Boolean` and friends |
| A PATCH replaces a nested object wholesale | Deep merge is out of scope | Patch the nested record through its own spec |
| A spec-carrying module on the **module path** neither writes nor reads the cross-module index | The index is one package, and a package belongs to one module | Delegate with a leaf calling the other Impl, or pass `-Ahkj.mapping.index=false` |
| The generated Impl is bound in the caller, never as a constant on the spec | Class initialisation can leave that constant `null`, intermittently | `XImpl.INSTANCE` at the call site, or an injected surface: [why](basics.md#bind-in-the-caller) |

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
| Read-only bean (getters only) | ❌ | ✅ | ❌ | ❌ |
| Write-only bean (setters or builder only) | ✅ | ❌ | ❌ | ❌ |
| JAXB getter-only `List` | ✅ (through `addAll`) | ✅ | ✅ | ❌ (it can never read `null`) |
| Sealed hierarchy against sealed hierarchy | ✅ | ✅ | ❌ | ❌ |
| Generic record (`Page<T>`) | ✅ | ✅ | ✅ | ❌ (record-to-record only) |
| `JsonNullable` property | through a leaf | through a leaf | through a leaf | ❌, not supported yet |

A lossless pair also earns `asIso()`; a bean pair with a reference property does not, because an
unset property is an ordinary state. [What Your Spec Generates](tiers.md) explains which surface each spec gets, and why.

---

## What it costs

- **Compile time**, not run time: the Impl is generated once, per build.
- **No reflection and no startup scan.** A concrete Impl is a stateless singleton; an
  element-mapped one carries its leaf prisms and is built by `of(...)`.
- **One `Validated` per field read** on the parse side, which is the price of accumulating errors
  rather than throwing on the first.
- **Lines of spec per field**: nothing for an identical field, one for a rename, one for a stock
  codec (none, where a mix-in already carries it), one for a derived field.
- **No component ceiling**: a flat 30-field wire maps like a narrow one.

---

## Is it a fit for your estate?

~~~admonish question title="Checkpoint: twelve questions about your services" id="check-fit-test"
Each ✅ is work the mapper already does. Each ⚠ is a decision to make before adopting, not a
blocker. Each ❌ means keep what you have for that case.

| Your boundary | Verdict |
|---|:--:|
| Domain types are records | ✅ |
| Domain types are JPA entities or other mutable beans | ❌ keep MapStruct here |
| Wire types are records | ✅ |
| Wire types come from a generated client (getter/setter beans, builders) | ✅ |
| Lombok `@Data`, `@Value` or `@Builder` wires | ✅ (order Lombok before the processor) |
| Lombok `@Accessors(fluent = true)` wires | ⚠ the accessors are not `getX`/`isX`, so they do not pair |
| PATCH endpoints where an omitted field means *leave unchanged* | ✅ `UpdateSpec` |
| PATCH DTOs from openapi-generator with `default:` values in the schema | ⚠ the defaults read as sent: see [A PATCH getter must answer `null` until set](beans_patch.md#patch-getters-answer-null) |
| PATCH bodies that must distinguish *clear* from *absent* | ⚠ an `Optional`-typed property; `JsonNullable` is not supported yet |
| Nested objects patched field by field | ❌ replacement is wholesale |
| Clients that need error paths in **wire** names | ⚠ paths are domain-named |
| Spec-carrying libraries on the module path | ⚠ no cross-module index there |
~~~

---

~~~admonish info title="Where next"
- Five steps to a working endpoint: [Quickstart](quickstart.md)
- Coming from another mapper: [Coming from MapStruct and Bean Validation](from_mapstruct.md)
- What each spec shape generates: [What Your Spec Generates](tiers.md)
- The complete set of limits: [Rules and Limits](rules.md)
~~~

---

**Previous:** [Injecting, Testing, and Diagnostics](testing.md)
**Next:** [Coming from MapStruct and Bean Validation](from_mapstruct.md)
