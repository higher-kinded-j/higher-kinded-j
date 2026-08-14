# Merge and Error Envelopes

_The forward-only sibling that assembles one target from several sources, and the generator that types your error context._

Two more generators complete the family. `@GenerateMerge` covers the assembly a boundary often needs just after parsing: one domain value built from several inputs. `@GenerateErrorEnvelope` covers the other end of the boundary: the typed domain error a fallible mapping produces, without the copy-pasted envelope fields and the untyped `Map<String, Object>` context.

~~~admonish info title="What You'll Learn"
- Declaring a merge entirely by a spec method's signature, with truthful return types
- How merge legs resolve: identity, leaves, or sibling mapping specs, with located failures
- Replacing repeated `code`/`message`/`timestamp`/`context` components with one `ErrorEnvelope<C>`
- The generated factories, the fluent `context()` builder, and `editContext` enrichment
~~~

~~~admonish example title="See Example Code"
**The code on this page is [RecordMappingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java) and [OrderErrorBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/OrderErrorBook.java)** - the page includes them directly, so they are compiled and run by the build.
~~~

## Merging several sources: `@GenerateMerge`

The forward-only sibling: assemble one target from **several** sources, declared entirely by the spec method's signature, no class literals, no inverse (truthful types):

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:merge_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:merge_usage}}
```

Each target component fills from the one source with a same-named component: identity when the types match, through a `ValidatedPrism` leaf when they differ, or through a sibling `@GenerateMapping` spec (the `customer` below parses through `CustomerMappingImpl`, and failures locate as dotted paths):

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:nested_merge_spec}}
```

Ambiguity (two sources carrying the component) and unfilled components are compile errors, and the return type must tell the truth: fallible fills demand the `Validated` return; an identity-only merge must declare the plain target.

The fallible path carries the [same null doctrine as `parse`](basics.md#null-doctrine): a null source-component read is a located, accumulated `FieldError`, never an exception, while a null source *argument* stays the caller's `NullPointerException`. A plain-return merge is total *by its declaration*: nulls flow through to the target constructor exactly as `build` copies them. (The return type follows the fills, so the guard cannot be bought by declaration alone: an identity-only merge that wants it should add a normalising `ValidatedPrism<X, X>` leaf, which makes the merge fallible and brings the `Validated` return with it.)

---

## Generating error envelopes: `@GenerateErrorEnvelope`

The third generator in the family targets the other end of the boundary: the typed domain error a fallible mapping produces. A sealed error hierarchy re-declares the same envelope (`code`, `message`, `timestamp`, `context`) on every variant, and `context` is usually an untyped `Map<String, Object>`. `@GenerateErrorEnvelope` supplies the envelope and types the context, so each variant declares only its domain-specific components plus one `ErrorEnvelope<C>` component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/OrderErrorBook.java:error_envelope}}
```

~~~admonish note title="Two senses of 'context'"
The *typed context* here is diagnostic metadata attached to an error value: a records-as-schema type such as `OrderErrorContext`. It is unrelated to the [`ErrorContext`](../effect/effect_contexts_error.md) effect type, which is a composable IO-plus-`Either` computation. This page's context is data carried on an error; that one is a way of running effects.
~~~

For `OrderError` the processor generates a companion named `OrderErrors` with three pieces:

- **A factory per variant.** `code` is the UPPER_SNAKE variant name and `message` its humanised form; the timestamp is read from a [`TimeSource`](../monads/io_monad.md), so an overload takes one explicitly and the convenience uses `TimeSource.system()`.
- **A fluent `context()` builder** over the context record's components.
- **An `editContext(error, edit)` wither** that rebuilds the concrete variant through an exhaustive switch.

Add a one-line `default` so the wither reads as an instance method, and construction plus enrichment matches the shape you would hand-write:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/OrderErrorBook.java:edit_context}}
```

The context type is discovered **structurally** from the `ErrorEnvelope` component's type argument, never a class literal, and every variant must agree on it. Three rules apply, each a what/why/fix diagnostic:

- the hierarchy, its variants, and the context record must be non-generic;
- permitted variants must be records; a nested sealed sub-hierarchy is rejected with a flatten-it fix, not recursed into;
- the context record's components must be nullable reference types. The all-absent context holds `null`, so primitives are rejected at compile time; and because a null-rejecting compact constructor cannot be detected by the processor, keep the context a plain nullable data carrier.

~~~admonish note title="Fine-grained or coarse variants?"
The design choice is about the *hierarchy*, not the annotation.

- **Fine-grained** (one variant per failure mode, each with its own typed fields, as in `MarketError`'s `FeedDisconnected` / `RiskLimitBreached` / `StaleData`): the generated `MarketErrors` factories carry everything, and no hand-written construction remains.
- **Coarse** (a variant grouping several codes, as in `OrderError`'s `CustomerError` covering `CUSTOMER_NOT_FOUND` and `CUSTOMER_SUSPENDED`): suits a boundary whose downstream `switch` presents failures by category. One generated factory per variant derives only one code, so these variants keep a hand-written factory per code, each calling the canonical constructor with `ErrorEnvelope.of(...)` and the generated builder.

Either way the repeated envelope and the untyped `Map<String, Object>` are gone. Reach for fine-grained variants when each failure mode is genuinely distinct, and group them when a boundary treats a whole category uniformly.
~~~

Two verbs keep the two operations distinct: `ErrorEnvelope.withContext(D)` is the record wither that **replaces** the context (and may change its type), while the generated `editContext(error, edit)` **transforms** the existing context through the builder, seeded from the current value. Reach for `withContext` to set a context, `editContext` to enrich one.

---

~~~admonish info title="Key Takeaways"
* **A merge is a method signature**: sources in, target out, each component filled from exactly one source, ambiguity and gaps diagnosed
* **Return types tell the truth**: fallible fills force the `Validated` return; identity-only merges declare the plain target
* **`@GenerateErrorEnvelope` retires the copy-pasted envelope**: one `ErrorEnvelope<C>` component, generated factories, a typed context instead of `Map<String, Object>`
* **`withContext` replaces, `editContext` enriches**
~~~

~~~admonish tip title="See Also"
- [Accumulating Assembly](../monads/validated_assembly.md) - The `fields()` ladders behind fallible merges
- [Testing With hkj-test](../tooling/test_assertions.md) - `assertThatErrorEnvelope` for envelope assertions
- [Record Mapping Basics](basics.md) - The `parse` whose errors these envelopes type
~~~

---

**Previous:** [Generic Specs](generics.md)
**Next:** [Injecting, Testing, and Diagnostics](testing.md)
