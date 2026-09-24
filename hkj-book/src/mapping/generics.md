# Generic Specs {#generic-records-concrete-threaded-and-element-mapped}

_Concrete, threaded, and element-mapped: three ways to map a generic record, one rule for how you reach the Impl._

A generic record (`Page<T>`, `Result<E, A>`) raises a question a non-generic pair never does. Is the mapping for one instantiation, for every instantiation, or for element types only the caller can convert? All three are supported, and the answer decides how you reach the generated Impl. (No generic records at your boundary? Skip ahead to [Merge and Error Envelopes](merge_envelopes.md), and return when a `Page<T>` appears.)

~~~admonish info title="What You'll Learn"
- Pick the form a generic record needs, and reach its Impl
- Pass an element-mapped spec its prisms in the order it declares them
~~~

~~~admonish example title="See Example Code"
**The code on this page is [GenericsBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java) and its [GenericsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/GenericsBookTest.java)** - the page includes them directly, so they are compiled and run by the build.
~~~

## One rule, three access shapes {#one-rule-three-access-shapes}

The form a spec takes decides how you reach its Impl, and one rule covers all three: how much state does the Impl carry?

| Form | Declared as | Access | Why |
|---|---|---|---|
| Concrete | `CustomerPageMapping extends MappingSpec<Page<Customer>, PageDto<CustomerDto>>` | `XImpl.INSTANCE` | stateless and monomorphic: a plain constant |
| Threaded | `PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>>` | `XImpl.instance()` | stateless but generic: a typed constant is impossible, so a cached singleton sits behind a generic accessor, as `EitherMonad.instance()` does |
| Element-mapped | `CodecPageMapping<T, TDto>`, with an abstract leaf | `XImpl.of(prisms)` | carries its leaf prisms as state: each call is a fresh, immutable instance |

---

## Concrete instantiations {#concrete-instantiations}

A **concrete** spec names the type arguments, and every component classifies under that substitution. The whole toolkit applies unchanged: leaves, nesting, containers, the null rule and index locations:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:generic_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:generic_usage}}
```

An instantiated mapping registers like any other, so `Report(Page<Customer> results)` nests it automatically.

---

## Threaded specs {#threaded-specs}

A **threaded** spec declares its own type parameters, and one mapping serves every instantiation. Same-variable elements copy by identity under the null-element scan, so a `null` element is `items.1: must not be null`, never passed through. The whole surface is generic: `build`, `parse`, and `asIso()` on a lossless pair:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:threaded_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:threaded_usage}}
```

In assignment context Java infers the type argument, so plain `instance()` reads naturally. The explicit `PageMappingImpl.<String>instance()` form is needed only where Java cannot infer it:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:threaded_inferred}}
```

Multi-parameter and bounded specs thread too (`ResultMapping<E, A>`, `RankedMapping<T extends Number>`), and a same-typed `default` leaf (`ValidatedPrism<T, T>`) still routes elements. A threaded spec nests as well: a use site's type arguments unify against the spec's declared pair, so `Report(Page<String> results)` resolves `PageMapping<T>` as `PageMappingImpl.<String>instance()`.

---

## Element-mapped specs {#element-mapped-specs}

An **element-mapped** spec threads the two sides under *different* variables (`Page<T>` against `PageDto<TDto>`), and declares the element mapping as an **abstract leaf**. Nothing on the spec can parse a `TDto` into a `T`, so the Impl leaves it to the caller: each abstract leaf becomes a field that a public `of(...)` factory fills, one `ValidatedPrism` per leaf:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:element_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:element_usage}}
```

The Impl carries the prisms as state, so there is no singleton: every `of(...)` call is a fresh, immutable instance. Build one where it is used and reuse it, rather than calling `of(...)` for every parse.

~~~admonish warning title="Not checked for you: pass the prisms in declaration order"
`of(...)` takes one prism per abstract leaf, in the order the spec declares the leaves. Two leaves of the same type swap without a compile error, and each value then parses through the other's leaf. [Leaf order in `of(...)`](rules.md#leaf-order-in-of) says where a leaf inherited from a mix-in falls.
~~~

An element-mapped mapping nests as a composition, so a failure locates through the whole path, `entries.items.1: not an email address`: [How an element-mapped spec nests](rules.md#element-mapped-nesting). Generic mappings are record-to-record only, and [the boundaries of a generic spec](rules.md#generic-boundaries) lists what else the processor diagnoses.

~~~admonish tip title="You can ship now"
You can now map a generic record for one instantiation, for every instantiation, or with element prisms the caller supplies, and reach each Impl. The rest of this page, [generic mix-ins](#generic-mix-ins), is for when you need it.
~~~

~~~admonish question title="Checkpoint: how do you reach this Impl?" id="check-generics-access"
`TagPageMapping` maps generic records:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:tag_page_spec}}
```

How does code reach its generated Impl?

1. `TagPageMappingImpl.INSTANCE`
2. `TagPageMappingImpl.<String>instance()`
3. `TagPageMappingImpl.of()`
4. Either of the first two
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-generics-access-answer"
**1.** The spec declares no type parameters of its own. It names `String`, so it is concrete, whatever the records declare. Its Impl is stateless and monomorphic, a plain `INSTANCE` constant, with no `instance()`:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/GenericsBookTest.java:check_access}}
```

Where this lives: [One rule, three access shapes](#one-rule-three-access-shapes).
~~~

~~~admonish question title="Checkpoint: what does a swapped `of(...)` do?" id="check-generics-swap"
A booking window's two dates arrive in different formats, so each takes its own codec:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:window_spec}}
```

The caller wants `opens` parsed as ISO (`2026-07-28`) and `closes` as a UK date (`31/07/2026`), and writes `WindowMappingImpl.of(uk, iso)`. What happens?

1. A compile error: the arguments are in the wrong order
2. It parses correctly: `of(...)` matches each prism to its leaf by type
3. It compiles, and a valid request fails on both dates, each parsed through the other's codec
4. `of(...)` throws, since the prisms disagree
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-generics-swap-answer"
**3.** `of(...)` takes the prisms in declaration order, `opens` then `closes`, and both have the same type, so the swap compiles. Each date then parses through the other's codec, and both fail, located:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/GenericsBookTest.java:check_swap}}
```

Where this lives: [Element-mapped specs](#element-mapped-specs).
~~~

---

## Generic mix-ins {#generic-mix-ins}

A [mix-in](codecs.md#shared-vocabulary-mix-in-interfaces) may declare type parameters of its own. Its members are read under the spec's instantiation, so a shared vocabulary parameterised by the type it speaks about contributes at the type the spec gives it:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/GenericsBook.java:generic_mixin_spec}}
```

`name()` says `T` where it is declared, and `String` where the spec has it, and that is what the generated Impl carries. It holds however many interfaces separate the two, and a spec's own type parameters survive as themselves, because the Impl declares them. A leaf of an element-mapped spec can come from a generic mix-in too.

The one shape this cannot answer for is a **raw** supertype anywhere on the route. The processor refuses one that contributes a member, and [a generic mix-in reached raw](rules.md#a-generic-mix-in-reached-raw) says which clause to edit.

---

~~~admonish info title="Key Takeaways"
* **Three generic forms**: a concrete instantiation, a threaded spec and an element-mapped spec, all three nestable
* **Access follows state**: `INSTANCE` for a monomorphic Impl, `instance()` for a generic singleton, and `of(...)` for one carrying its element prisms
* **An element-mapped spec leaves the element conversion to the caller**: each abstract leaf is a prism passed to `of(...)`, in declaration order
* **The boundaries are diagnosed**: record-to-record only, no raw types or wildcards, and abstract leaves only where something supplies them
~~~

~~~admonish tip title="See Also"
- [Nesting, Containers, and Sealed Hierarchies](structure.md): How generic mappings register and nest
- [The null rule](basics.md#null-doctrine): The null-element scan same-variable elements copy under
- [Injecting, Testing, and Diagnostics](testing.md): Registering an element-mapped Impl as a bean
~~~

---

**Previous:** [Sparse PATCH](beans_patch.md)
**Next:** [Merge and Error Envelopes](merge_envelopes.md)
