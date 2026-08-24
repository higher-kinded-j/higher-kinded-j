# Production Readiness

## _Honest answers about runtime cost, build impact, and team conventions_

~~~admonish info title="What You'll Learn"
- The runtime cost profile of generated optics: what `modify` allocates, where the lambdas live, and whether `static final` caching matters.
- Build-time impact of the annotation processor and how it scales with codebase size.
- When to extract optics into reusable values versus inlining at call sites.
- Versioning expectations: which surfaces are stable, where to expect change, and how generated code interacts with library upgrades.
- Team conventions that have proven valuable in production codebases.
~~~

This page does not offer a marketing case for using optics in production; it offers honest answers to the questions a senior engineer asks before adopting a new abstraction in a codebase others must maintain.

---

## Runtime cost

### What `set` and `modify` allocate

Every `set` or `modify` call on a `Lens` over a record allocates one new record per layer of nesting touched. A composed lens through three layers allocates three new records, plus any intermediate captures. There is no in-place mutation; that is the cost of immutability and not specific to optics.

Compared to a hand-written `with*` cascade for the same nested update, generated optics typically incur the same allocation count. The difference is the two or three anonymous `Lens` and `FocusPath` objects the composition allocates.

For a single update on a small record, the cost is unlikely to matter. For tight inner loops, see the caching note below.

These are engineering estimates from the shape of the generated code, not benchmark output: the [JMH suite](../benchmarks.md) covers `Fold.plus` but not lens or traversal allocation.

### `modifyF` and effect handlers

`modifyF(f, source, applicative)` runs `f` once per focused element and threads the results through the supplied `Applicative`. The cost is one call to `f` plus whatever the applicative's `ap` and `pure` do. `Validated` accumulates every error and `Either` keeps only the first, but neither skips work: the traversal applies `f` to every focused element before the applicative combines the results, so the choice shapes the answer rather than the cost.

### Traversal allocation

`Traversals.modify(traversal, f, source)` over a `List<A>` allocates one new list, plus a small constant number of short-lived objects *per element*: the traversal threads each result through the `Id` applicative and an immutable cons-list before flattening. Budget O(n) allocations, not O(1). (Reads and writes on a bare `Traversal` go through the `Traversals` utility; the interface itself declares no plain read or write.) If the function returns the same value for every element (a no-op modify), the list is still rebuilt; optics do not compare references to skip rebuilding.

---

## Caching optics

A lens or focus path is a value, not a function. Building the path has a one-off allocation cost, which caching removes; applying it still allocates the rebuilt structure, which nothing removes. For paths used repeatedly, store them as `static final`:

```java
private static final Lens<Company, String> COMPANY_NAME =
    CompanyLenses.name();

private static final TraversalPath<Order, BigDecimal> ALL_PRICES =
    OrderFocus.items().via(ItemFocus.price());
```

This matters most for paths constructed by `andThen` chains, where the whole composition is rebuilt on every call. It is worth doing even for a single accessor: a generated accessor is a factory, not a constant. `CompanyLenses.name()` calls `Lens.of(...)` and allocates a fresh `Lens` every time, and `CompanyFocus.name()` allocates a `Lens` and a `FocusPath`. Caching removes that per-call allocation.

---

## Build-time impact

The annotation processor adds one code-generation pass to compilation. On a codebase with around a hundred annotated records the additional time is in the low single digits of seconds; large codebases scale roughly linearly with the number of annotated types.

Generated sources land under `build/generated/sources/annotationProcessor/java/main` (Gradle) or `target/generated-sources/annotations` (Maven). Most IDEs index these automatically after the first build. If autocomplete cannot see `XLenses` or `XFocus` types, a rebuild and project refresh resolves it.

Incremental compilation is supported, conservatively: every processor is registered with Gradle as *aggregating*, so a change to any annotated type re-runs generation across the source set rather than regenerating one companion class. That is deliberate, an isolating claim that turned out wrong would produce silently stale output, and consuming source sets stay incremental regardless.

---

## When to extract optics

| Situation | Recommendation |
|---|---|
| Path used once in a method body | Inline at call site (`UserFocus.address().city().get(user)`) |
| Path used multiple times in the same class | Extract to a `private static final` field |
| Path used across packages | Extract to a `public static final` field on a domain-optics utility class |
| Path constructed dynamically from runtime input | Build inside the method; do not cache |
| Path inside a tight loop | Extract to a local variable above the loop |

The optic value's type carries useful documentation. A `Lens<Company, String>` field named `companyName` reads more cleanly than a method that recomputes the path.

---

## Versioning and stability

The annotation surface (`@GenerateLenses`, `@GenerateFocus`, `@GeneratePrisms`, `@GenerateTraversals`, `@GenerateFolds`, `@GenerateGetters`, `@GenerateSetters`, `@GenerateIsos`, `@ImportOptics`, `OpticsSpec`) is the stable contract you depend on. Changes to method names on these annotations follow semantic-versioning expectations.

The shapes of generated classes (`XLenses`, `XFocus`, etc.) are also stable; existing fields and methods do not disappear without a deprecation cycle. New fields and methods may be added to support new annotation parameters; this is additive and source-compatible.

The Focus DSL surface (`FocusPath`, `AffinePath`, `TraversalPath`, methods like `.each()`, `.via()`, `.modifyAll()`) is stable. The Free Monad DSL APIs (`OpticPrograms`, `OpticInterpreters`) are also stable but used by fewer projects; if you adopt them, weigh the smaller adoption surface accordingly.

`Profunctor` adaptations (`contramap`, `map`, `dimap`) are stable.

When upgrading across minor versions, regenerate by rebuilding. Generated code is compatible with the runtime library version that produced it; mixing differently-versioned generated code and runtime jar can produce subtle runtime errors and is not supported.

---

## Team conventions that work

These are the conventions the library's own examples and tests follow. Treat them as defaults, not mandates.

- **Annotate the records you own as you write them.** Adding `@GenerateLenses` later is mechanical, but discovering mid-task that the optic does not exist is not. Weigh it against the build-time note above: generation scales with the number of annotated types, so "every record in the monorepo" is a different proposition from "every record in this module".
- **Place optic constants near the domain type.** A static `Optics` utility class next to the record carries the well-known paths.
- **Name paths after the field they end at.** `companyName`, not `companyToName` or `getCompanyName`. The receiver-style naming reads naturally at call sites: `Optics.companyName.set("...", company)`.
- **Use `Fold` when you only read.** Even when a `Lens` would work, expressing read-only intent makes reviews easier and prevents accidental mutations.
- **Reach for the Focus DSL first.** Manual `andThen` composition is fine and sometimes clearer, but the DSL gives you better IDE support and shorter call sites for nested updates.
- **Reserve the Free Monad DSL for problems that demand it.** If you do not have an audit, dry-run, or multi-mode requirement, the everyday APIs are simpler.

~~~admonish info title="Key Takeaways"
* **Immutability is the cost, not optics.** A composed lens allocates one record per touched layer, the same count a hand-written `with*` cascade would.
* **A path is a value: build it once.** `static final` removes the construction cost; it cannot remove the cost of rebuilding the structure you update.
* **A no-op modify still rebuilds.** Optics do not compare references to skip work.
* **Neither error strategy saves work.** `Validated` accumulates and `Either` keeps the first, but every element is visited either way.
* **Annotation processing is a build-time cost, paid once per compile**, and it buys compile-time errors instead of runtime ones.
~~~

~~~admonish tip title="See Also"
- [Optic Capabilities](optic_capabilities.md): what each optic can do before you tune how it does it
- [Optic-Driven Batching](optic_batching.md): the one place where an optic's cost is I/O rather than allocation
- [Decision Trees](decision_trees.md): choosing the API whose cost profile suits the task
~~~

---

**Previous:** [Common Compiler Errors](compiler_errors.md)
**Next:** [Decision Trees](decision_trees.md)
