# Production Readiness

_What optics cost at run time and build time, and the conventions that keep a team consistent._

This page does not offer a marketing case for using optics in production; it offers honest answers to the questions a senior engineer asks before adopting a new abstraction in a codebase others must maintain.

Find your question, then follow its link:

| You are asking | Go to |
|---|---|
| Does a lens update allocate more than a hand-written `with*` cascade? | [What `set` and `modify` allocate](#what-set-and-modify-allocate) |
| Does a traversal rebuild its container on every call, even to read it? | [What reads cost](#read-cost) and [Traversal allocation](#traversal-allocation) |
| Which collection optic copies the whole map, and which hands back the source unchanged? | [What each collection optic builds](#collection-optics) |
| What does a prism or affine cost when it does not match? | [Prisms and affines on a miss](#prisms-and-affines) |
| Does validating every value cost more with `Validated` than with `Either`? | [`modifyF` and effect handlers](#modifyf-and-effect-handlers) |
| Does an adapter or a Focus path add work on every call? | [Adapters and Focus paths](#adapters-and-paths) |
| Should I store a composed optic in a constant, and where? | [Caching optics](#caching-optics) and [When to extract optics](#when-to-extract-optics) |
| What does the processor add to the build: time, incremental compilation, `-Werror`, coverage, Lombok beside it? | [Build-time impact](#build-time-impact) |
| Will generated classes and the Focus DSL stay stable across versions? | [Versioning and stability](#versioning-and-stability) |
| What should a team agree on before using optics widely? | [Team conventions that work](#team-conventions-that-work) |

---

## Runtime cost

In short, with the method that decides each named in its own subsection:

| You call | It costs |
|---|---|
| A lens `set` or `modify` | What a hand-written `with*` cascade allocates |
| A traversal `modify` | A rebuilt container, on every call |
| A read through `Traversals.getAll` or `asFold()` | No rebuild |
| A query such as `find`, `exists` or `preview` | Stops at the focus that answers it |
| An `At` edit | A copy of the whole map or list |
| A prism or affine `modify` that misses | Nothing: no allocation and no rebuild |

### What `set` and `modify` allocate

Every `set` or `modify` call on a `Lens` over a record allocates one new record per layer of nesting touched. A composed lens through three layers allocates three new records, plus any intermediate captures. There is no in-place mutation; that is the cost of immutability and not specific to optics. Everything off the path keeps its reference: a generated lens passes the record's other components to its constructor as they are.

Compared to a hand-written `with*` cascade for the same nested update, generated optics typically incur the same allocation count. The difference is the two or three anonymous `Lens` and `FocusPath` objects the composition allocates.

For a single update on a small record, the cost is unlikely to matter. For tight inner loops, see [Caching optics](#caching-optics).

These are engineering estimates from the shape of the generated code, not benchmark output: the [JMH suite](../benchmarks.md) covers `Fold.plus` but not lens or traversal allocation.

### `modifyF` and effect handlers

`modifyF(f, source, applicative)` runs `f` once per focused element and threads the results through the supplied `Applicative`. The cost is one call to `f` plus whatever the applicative's `ap` and `pure` do. `Validated` accumulates every error and `Either` keeps only the first, but neither skips work: the traversal applies `f` to every focused element before the applicative combines the results, so the choice shapes the answer rather than the cost.

### Traversal allocation

`Traversals.modify(traversal, f, source)` over a `List<A>` allocates one new list, plus a small constant number of short-lived objects *per element*: the traversal threads each result through the `Id` applicative and an immutable cons-list before flattening. Budget O(n) allocations, not O(1). (Reads and writes on a bare `Traversal` go through the `Traversals` utility; the interface itself declares no plain read or write.) If the function returns the same value for every element (a no-op modify), the list is still rebuilt; optics do not compare references to skip rebuilding.

### What each collection optic builds {#collection-optics}

Each collection optic rebuilds its container in its own way, and several return the source itself when there is nothing to change. The third column says which, and the last names the method that decides it.

| Optic | What it builds | When nothing changes | Implemented in |
|---|---|---|---|
| `Traversals.forMapValues()` | Copies the keys and values into two lists, traverses the values, then builds one new `LinkedHashMap` in the source's order. | An empty map still gets a new, empty map. | `Traversals.traverseMapValues` |
| `Traversals.forMap(key)` | Copies the whole map to replace one value. | An absent key returns the source itself. | `Traversals.forMap` |
| `filtered(p)`, `filterBy(query, p)` | Tests each element once, in the same pass as the update; `filterBy`'s test is a `Fold.exists`, which stops at the first queried focus that matches. | A rejected element passes through with `of`, keeping its reference. | `Traversal.filtered`, `Traversal.filterBy` |
| `ListTraversals.taking(n)`, `slicing(from, to)` and the other limits | Run the function on the slice only, then copy the elements outside it by reference into one new list; `takingWhile` and `droppingWhile` first scan for the split point. | An empty slice returns the source itself. | `ListTraversals.slicing` |
| `StringTraversals.chars()`, `worded()`, `lined()` | Split the string into a list, one boxed `Character` per character or a regular-expression split into words or lines, then join one new string. | A new string is joined anyway. | `StringTraversals.chars`, `StringTraversals.worded` |
| `Traversals.partsOf(t)` | `get` collects the foci into one list through `getAll`, and `set` walks the traversal again, so a `modify` walks it twice. `sorted` and `reversed` copy that list once more, and `distinct` twice. | The traversal is still walked twice. | `Traversals.partsOf` |
| `IndexedTraversals.forList()`, `forMap()` | Pass the index and the value to your function as two arguments, so no `Pair` is built per focus. | An empty list or map returns the source itself. | `IndexedTraversals.forList`, `IndexedTraversals.forMap` |
| `IndexedTraversals.toIndexedList`, `IndexedTraversals.length`, `asIndexedFold()`, `iandThen` | `toIndexedList` builds one `Pair` per focus to collect the foci, and `iandThen` one per focus because its index is a pair. `length` and `asIndexedFold()` build none: like `asFold()`, they run the traversal in a constant applicative, which skips every rebuild. | Not applicable: these read. | `IndexedTraversals.toIndexedList`, `IndexedTraversal.asIndexedFold`, `IndexedTraversal.iandThen` |
| `Setter.forList()`, `Setter.forMapValues()` | `modify` maps straight into a new list or `LinkedHashMap`, with no applicative. `modifyF` collects the effects, sequences them from the right onto an immutable cons list, and builds the result once. | The collection is rebuilt anyway. | `Setter.forList`, `ConsList.sequence` |
| `At`: `AtInstances.mapAt()`, `listAt()` | Every `set`, `insertOrUpdate`, `remove` and `modify` copies the whole map or list. | Copies anyway, even when the index is absent. | `AtInstances.mapAt`, `AtInstances.listAt` |
| `Ixed`: `IxedInstances.mapIx()`, `listIx()` | An edit copies only when the index is present, and a read through `IxedInstances.get` copies nothing. | An absent index returns the source itself. | `IxedInstances.fromAt` |

A run of `At` edits therefore copies the map once per edit. To make many edits at once, copy the map into a `LinkedHashMap` once, change that copy, and carry on with the result.

### Prisms and affines on a miss {#prisms-and-affines}

A generated prism tests its case with `instanceof` for a sealed subtype, `==` for an enum constant, or your predicate for `@MatchWhen`. It answers a miss with the JDK's shared `Optional.empty()`, so a miss allocates no `Optional`. On a miss, `Prism.modify` and `Affine.modify` return the source itself, so nothing is rebuilt. `Prism.modifyF` and `Affine.modifyF` wrap the source with `of` only on a miss, so a match allocates nothing for the branch it does not take. `Affine.andThen` chains the two reads with `Optional.flatMap`, or `map` for a lens step, so a composed affine's `getOptional` stops at the first absent step. Inside a traversal, `Traversal.andThen(Prism)` passes a non-matching element through with `of`, so the rest of the chain never runs for it.

### What reads cost {#read-cost}

A read through a traversal or a fold rebuilds nothing, and a query that looks for an answer stops at the focus that settles it.

| Read | What it visits and builds | Implemented in |
|---|---|---|
| `Traversals.getAll(t, s)`, `t.asFold().getAll(s)`, and a `TraversalPath`'s `getAll(s)` | Run `modifyF` in a constant applicative, which skips every rebuild, though a traversal's own set-up, such as a map's key and value lists or a split string, still runs. | `Traversal.asFold`, `Traversals.getAll` |
| A `Fold`'s `preview`, `find`, `exists`, `all` and `isEmpty` | Stop the fold at the focus that settles the answer, so no later focus is visited and the predicate is not called on one. `getAll` and `length` visit every focus. | `Fold.find`, `FoldSearch.first` |
| An `IndexedFold`'s `findWithIndex`, `existsWithIndex`, `allWithIndex`, their value-only forms, and `isEmpty` | Stop at the answer, as a `Fold`'s queries do. The forms that take an index, and `find`, build one `Pair` per focus they visit. | `IndexedFold.findWithIndex` |
| A `TraversalPath`'s `preview`, `count`, `exists`, `all`, `find` and `isEmpty` | Read through the path's `asFold()`: `preview`, `exists`, `all`, `find` and `isEmpty` stop at their answer as a `Fold`'s queries do, and `count` visits every focus. A path from `traced` reads them through `getAll`, so its observer sees every focus. | `TraversalPath.exists` |
| `IxedInstances.get`, `IxedInstances.contains` | Read through `Traversals.getAll`, so they copy nothing, and neither does `At.get`. | `IxedInstances.get` |
| A fold from `@GenerateFolds` | Loops over an iterable component directly, or applies the function to a single one, with no intermediate list. | `FoldProcessor` |
| `getMaybe`, `previewMaybe`, `findMaybe`, `getAllMaybe` | Make the plain read, then convert it: a new `Just` for a value, the shared `Nothing` for none. | `GetterExtensions.getMaybe`, `FoldExtensions` |

### Adapters and Focus paths {#adapters-and-paths}

`contramap`, `map` and `dimap` each return one optic that runs your conversion functions around the wrapped optic's `modifyF`, as `Optic.dimap` shows, and add nothing else. An adapter therefore costs what your functions cost, on every call.

A Focus path holds the optic it was built from, and its reads and writes delegate to that optic. Each `via` composes the two optics with `andThen` into a new path. A `via` with another path also joins the two label lists, and a `via` with a bare optic keeps this path's list, as `FocusPath.via` shows. That is a building cost, which [Caching optics](#caching-optics) removes.

---

## Caching optics

A lens or focus path is a value, not a function. Building the path has a one-off allocation cost, which caching removes; applying it still allocates the rebuilt structure, which nothing removes. For paths used repeatedly, store them as `static final`:

<!-- verify -->
```java
private static final Lens<Company, String> COMPANY_NAME =
    CompanyLenses.name();

private static final TraversalPath<Order, BigDecimal> ALL_PRICES =
    OrderFocus.lines().via(LineItemFocus.price());
```

This matters most for paths constructed by `andThen` chains, where the whole composition is rebuilt on every call. The saving is smaller but real for a single accessor too, because a generated accessor is a factory rather than a constant: `CompanyLenses.name()` calls `Lens.of(...)` and allocates a fresh `Lens` every time, and `CompanyFocus.name()` allocates a `Lens` and a `FocusPath`.

Both cases assume the path is used more than once. A path used once has no allocation to amortise, which is why [When to extract optics](#when-to-extract-optics) still says to inline it at the call site.

---

## Build-time impact

The annotation processor adds one code-generation pass to compilation. On a codebase with around a hundred annotated records the additional time is in the low single digits of seconds; large codebases scale roughly linearly with the number of annotated types.

Generated sources land under `build/generated/sources/annotationProcessor/java/main` (Gradle) or `target/generated-sources/annotations` (Maven). Most IDEs index these automatically after the first build. If autocomplete cannot see `XLenses` or `XFocus` types, a rebuild and project refresh resolves it.

Incremental compilation is supported, conservatively: every processor that generates code is registered with Gradle as *aggregating*, so a change to any annotated type re-runs generation across the source set rather than regenerating one companion class. That is deliberate, an isolating claim that turned out wrong would produce silently stale output, and consuming source sets stay incremental regardless. The one processor that generates nothing is registered as isolating: it marks hkj's annotations as handled, so that javac's `-Xlint:processing` does not report them (see below).

Every class file the processors write carries `@Generated`: the companion class, the navigator and stage classes nested inside it, and the classes that implement each traversal and fold. The marker has class retention, which is what JaCoCo's generated-code filter reads, so a coverage floor needs no exclude patterns for `XLenses`, `XFocus`, `XTraversals` or anything nested in them, and a build that carried such patterns can drop them. The same marker is what the [compile-time checks](../tooling/compile_checks.md#what-the-checker-detects) gate on.

Generated code is written to compile under `-Xlint:all -Werror`. javac reports every annotation that no processor claims, that is, declares it handles, so hkj's processors claim `@Generated` and every annotation they read alongside a generating one, such as `@MapField` or `@Wither`. Two warnings are outside hkj's reach: an annotation from another library that no processor claims, such as jspecify's `@NullMarked`, which `@EffectAlgebra` and `@ComposeEffects` write onto the classes they generate, or Spring's, which an `@HkjHttpClient` client carries; and, in a named module, the `exports` lint on a public signature that names a type from a module required without `transitive`. A build that keeps `-Werror` beside such annotations turns that one lint off with `-Xlint:all,-processing`; for the `exports` warning, declare the requirement `transitive`.

A type-use annotation you write on a component, a bound or a type argument is copied onto the types generated code writes out, so a `@Nullable` still reads as nullable in a lens's focus or a mapping Impl's leaf. An annotation the generated file cannot write cleanly is left off instead of breaking the build: one missing from the compile classpath, one declared private or inside a private class, one package-private to a package other than the one the file is written into, and one that is itself deprecated, which would draw a warning in a file you cannot put a `@SuppressWarnings` on. Inside a `@NullMarked` scope an unannotated type means non-null, so an annotation left off that way changes what the generated signature says: keep the nullness annotations generated code has to repeat public and current.

javac offers each round's annotations to the processors in processor-path order, and stops once every annotation in the round is claimed. A processor that supports every annotation, such as Lombok's, belongs ahead of `hkj-processor` on the processor path, or a round whose annotations are all hkj's never reaches it. A build that names its processors, with `-processor` or Maven's `<annotationProcessors>`, adds `org.higherkindedj.optics.processing.CompanionAnnotationProcessor` to the list; the HKJ Maven plugin adds it at its own release.

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
- **Reserve the Free Monad DSL for problems that demand it.** If you do not have an audit, structural-analysis, or multi-mode requirement, the everyday APIs are simpler.

~~~admonish tip title="See Also"
- [Optic Capabilities](optic_capabilities.md): what each optic can do before you tune how it does it
- [Optic-Driven Batching](optic_batching.md): the one place where an optic's cost is I/O rather than allocation
- [Decision Trees](decision_trees.md): choosing the API whose cost profile suits the task
~~~

---

**Previous:** [Look It Up](ch7_intro.md)
**Next:** [Annotations at a Glance](annotations_at_a_glance.md)
