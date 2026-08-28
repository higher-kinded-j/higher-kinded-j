# Generic Specs {#generic-records-concrete-threaded-and-element-mapped}

_Concrete, threaded, and element-mapped: three ways to map a generic record, one rule for how you reach the Impl._

A generic record (`Page<T>`, `Result<E, A>`) raises a question a non-generic pair never does: is the mapping *for one instantiation*, *for all of them*, or *parameterised by codecs the spec cannot know*? All three are supported, and which one you have determines how the generated Impl is accessed. (No generic records at your boundary? Skip ahead to [Merge and Error Envelopes](merge_envelopes.md) and return when a `Page<T>` appears.)

~~~admonish info title="What You'll Learn"
- Mapping a concrete instantiation, where the whole toolkit applies under the substitution
- Threading a spec's own type parameters so one mapping serves every instantiation
- Element-mapped specs: abstract leaves deferred to a constructor-supplied `of(...)` factory
- The one rule behind `INSTANCE`, `instance()`, and `of(...)`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [RecordMappingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java)** - the page includes it directly, so it is compiled and run by the build.
~~~

## Concrete instantiations

As a **concrete instantiation**, name the type arguments in the spec and every component classifies under that substitution, so the whole toolkit (leaves, nesting, containers, the null doctrine, index location) applies unchanged:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:generic_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:generic_usage}}
```

An instantiated mapping registers like any other, so `Report(Page<Customer> results)` nests it automatically.

---

## Threaded specs

As a **threaded spec**, declare the spec generic in its own type parameters and one mapping serves every instantiation. Same-variable elements copy by identity under the null-element scan (a `null` element is `items.1: must not be null`, never a smuggled null), and the whole surface (`build`, `parse`, `asIso` on a lossless pair) is generic:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:threaded_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:threaded_usage}}
```

A generic Impl cannot carry a typed static `INSTANCE`, so it follows the library's generic-singleton convention (`EitherMonad.instance()`): one stateless cached instance behind `PageMappingImpl.instance()`. Multi-parameter and bounded specs thread too (`ResultMapping<E, A>`, `RankedMapping<T extends Number>`), and a same-typed `default` leaf (`ValidatedPrism<T, T>`) still routes elements.

In assignment context the witness is inferred, so plain `instance()` reads naturally; the explicit `PageMappingImpl.<String>instance()` form is only needed where Java cannot infer:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:threaded_inferred}}
```

A threaded spec nests too: a use site's type arguments unify against the spec's declared pair, so `Report(Page<String> results)` resolves `PageMapping<T>` as `PageMappingImpl.<String>instance()`, and a generic outer spec may thread its own variable straight through.

---

## One rule, three access shapes

The three access shapes are one rule, not three conventions: *how much state does the Impl carry?*

| Spec shape | Access | Why |
|---|---|---|
| Concrete | `XImpl.INSTANCE` | stateless, monomorphic: a plain constant |
| Threaded generic | `XImpl.<T>instance()` | stateless but generic: a typed constant is impossible, so the cached singleton sits behind a generic accessor (the `EitherMonad.instance()` convention) |
| Element-mapped | `XImpl.of(prisms)` | carries its leaf prisms as state: every call is a fresh, immutable instance |

---

## Generic mix-ins

A mix-in may declare type parameters of its own. Its members are read under the spec's instantiation, so a shared vocabulary interface parameterised by the type it speaks about contributes at the type the spec gives it:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:generic_mixin_spec}}
```

`name()` says `T` where it is declared and `String` where the spec has it, and that is what the generated Impl carries. It holds however many interfaces separate the two, and a spec's own parameters survive as themselves, because the Impl declares them.

The one shape this cannot answer for is a **raw** supertype anywhere on the route. Raw erases every member of the type below it, whatever that member declares, so `extends Renames` would contribute `Object name()` rather than the `String` it was written with. A raw ancestor that contributes nothing is left alone, since nothing of its is read; one that contributes a rename, a leaf or a derived field is refused at the declaration:

```
@GenerateMapping: mix-in 'Renames' is written raw. Its members are read under the spec's
instantiation, and a raw supertype erases every one of them whatever they declare: a
'ValidatedPrism<String, Email>' arrives bare, and a 'T' arrives as Object. Name the type
arguments where 'Renames' is extended, as 'extends Renames<...>'.
```

Erasure travels downwards, so the raw clause is not always the interface whose members went missing: with `TextRenames extends Renames<String>` and a spec saying `extends TextRenames` raw, it is `TextRenames` that has to be given its argument. The message names the raw clause in both cases, because that is the line to edit.

---

## Element-mapped specs

The third form is **element-mapped**: thread the two sides under *different* variables (`Page<T> ↔ PageDto<TDto>`) and declare the element mapping as an **abstract leaf**. Nothing on the spec can parse a `TDto` into a `T`, so the generated Impl defers it: each abstract leaf becomes a constructor-supplied field behind a public `of(...)` factory, one `ValidatedPrism` per leaf in declaration order:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:element_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:element_usage}}
```

The Impl carries the prisms as state, so there is no singleton in either spelling: every `of(...)` call is a fresh, immutable instance.

Element-mapped mappings nest as **compositions**. A use site whose pair unifies against one resolves each element pair in turn:

- through a leaf on the using spec named after the component (single-leaf specs; a spec with several abstract leaves resolves each pair against the other specs in the same compilation),
- or recursively through another registered mapping,

and emits `CodecPageMappingImpl.of(entries()).asValidatedPrism()` in place. Failures locate through the whole composed path (`entries.items.1: not an email address`); an unresolvable element pair is a compile error naming the pair and both ways to supply it (a leaf on the using spec, or another registered mapping).

~~~admonish note title="Boundaries"
Generic mappings are **record-to-record only** (bean-shaped wires and `UpdateSpec` mappings stay concrete); raw uses (including raw *nested* arguments) and wildcards are diagnosed, while array arguments (`Page<String[]>`) are concrete, map fine, and unify structurally at nested use sites. An abstract leaf belongs to a generic spec: on a concrete or sealed one it is diagnosed, since nothing defers its parser.
~~~

---

~~~admonish info title="Key Takeaways"
* **Three generic forms**: concrete instantiations, threaded specs, and element-mapped specs, all three nestable
* **Access follows state**: `INSTANCE` (monomorphic), `instance()` (generic singleton), `of(...)` (carries its element prisms)
* **Element-mapped specs defer what they cannot know**: each abstract leaf becomes a constructor-supplied `ValidatedPrism`
* **The boundaries are diagnosed**: record-to-record only, no raw types or wildcards, and abstract leaves only where something defers them
~~~

~~~admonish tip title="See Also"
- [Nesting, Containers, and Sealed Hierarchies](structure.md) - How generic mappings register and nest
- [Record Mapping Basics](basics.md#null-doctrine) - The null-element scan same-variable elements copy under
- [Injecting, Testing, and Diagnostics](testing.md) - Registering an element-mapped Impl as a bean
~~~

---

**Previous:** [Beans and Sparse PATCH](beans_patch.md)
**Next:** [Merge and Error Envelopes](merge_envelopes.md)
