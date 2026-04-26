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

Compared to a hand-written `with*` cascade for the same nested update, generated optics typically incur the same allocation count. The difference at the byte-code level is the two or three lambda objects that the composition captures, which the JIT inlines after the path becomes hot.

For a single update on a small record, the cost is below noise. For tight inner loops, see the caching note below.

### `modifyF` and effect handlers

`modifyF(f, source, applicative)` runs `f` once per focused element and threads the results through the supplied `Applicative`. The cost is one call to `f` plus whatever the applicative's `ap` and `pure` do. Validation accumulates errors at the cost of not short-circuiting; `Either` short-circuits on the first failure.

### Traversal allocation

`Traversal.modify(f, source)` over a `List<A>` allocates one new list. If the function returns the same value for every element (a no-op modify), the list is still rebuilt; optics do not currently exploit reference equality to skip rebuilding.

---

## Caching optics

A lens or focus path is a value, not a function. Building the path has a one-off allocation cost; using it has none. For paths used repeatedly, store them as `static final`:

```java
private static final Lens<Company, String> COMPANY_NAME =
    CompanyLenses.name();

private static final TraversalPath<Order, BigDecimal> ALL_PRICES =
    OrderFocus.items().each().price();
```

This matters most for paths constructed by `andThen` chains; the work of composing the path is paid once instead of per call. For a single annotation-generated lens like `CompanyLenses.name()`, the path object is already a singleton-like static method result and additional caching gains nothing.

---

## Build-time impact

The annotation processor adds a single round of code generation to compilation. On a codebase with around a hundred annotated records the additional time is in the low single digits of seconds; large codebases scale roughly linearly with the number of annotated types.

Generated sources land under `build/generated/sources/annotationProcessor/java/main` (Gradle) or `target/generated-sources/annotations` (Maven). Most IDEs index these automatically after the first build. If autocomplete cannot see `XLenses` or `XFocus` types, a rebuild and project refresh resolves it.

Incremental compilation works as you would expect: changing a record's component triggers regeneration of just that record's companion classes.

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

These are not mandates, just patterns observed in production codebases that adopted optics without regret.

- **Annotate aggressively.** Add `@GenerateLenses` and `@GenerateFocus` to every record you own at the time you write it, even if you are not yet sure which optics you will use. The cost is one annotation; the benefit is that future code can reach for an optic without a refactor.
- **Place optic constants near the domain type.** A static `Optics` utility class next to the record carries the well-known paths.
- **Name paths after the field they end at.** `companyName`, not `companyToName` or `getCompanyName`. The receiver-style naming reads naturally at call sites: `Optics.companyName.set("...", company)`.
- **Use `Fold` when you only read.** Even when a `Lens` would work, expressing read-only intent makes reviews easier and prevents accidental mutations.
- **Reach for the Focus DSL first.** Manual `andThen` composition is fine and sometimes clearer, but the DSL gives you better IDE support and shorter call sites for nested updates.
- **Reserve the Free Monad DSL for problems that demand it.** If you do not have an audit, dry-run, or multi-mode requirement, the everyday APIs are simpler.

---

**Previous:** [Common Compiler Errors](compiler_errors.md)
**Next:** [Decision Trees](decision_trees.md)
