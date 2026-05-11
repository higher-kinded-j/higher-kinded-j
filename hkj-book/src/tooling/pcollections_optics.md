# PCollections Optics

~~~admonish info title="What You'll Learn"
- Which PCollections types `@GenerateTraversals` recognises automatically
- How generated traversals reconstruct persistent collections after a focus change
- The Focus DSL navigation patterns for PCollections fields
- Where natural-ordering caveats apply for sorted variants
~~~

[PCollections](https://pcollections.org/) persistent collections are first-class citizens of the optics generator system. Annotating a record whose components are `PVector`, `PStack`, `PSet`, `PSortedSet`, `PBag`, `PMap`, or `PSortedMap` produces traversal code that round-trips through the persistent type, with no production-code changes required.

This page documents the seven PCollections generator plugins added in Phase 2 of the integration. For the underlying compatibility hypothesis and benchmark numbers, see [PCollections Integration](pcollections_integration.md).

---

## Supported Types

| Field type | Generator | Reconstruction | Cardinality |
|-----------|-----------|----------------|-------------|
| `PVector<A>` | `PVectorGenerator` | `TreePVector.from(Collection)` | ZERO_OR_MORE |
| `PStack<A>` | `PStackGenerator` | `ConsPStack.from(Collection)` | ZERO_OR_MORE |
| `PSet<A>` | `PSetGenerator` | `HashTreePSet.from(Collection)` | ZERO_OR_MORE |
| `PSortedSet<A>` | `PSortedSetGenerator` | `TreePSet.from(Collection)` | ZERO_OR_MORE |
| `PBag<A>` | `PBagGenerator` | `HashTreePBag.from(Collection)` | ZERO_OR_MORE |
| `PMap<K, V>` | `PMapValueGenerator` | `HashTreePMap.from(Map)` (value focus) | ZERO_OR_MORE |
| `PSortedMap<K, V>` | `PSortedMapValueGenerator` | `TreePMap.from(Map)` (value focus) | ZERO_OR_MORE |

Map generators focus on the value type (index 1). Keys are passed through unchanged.

All seven generators run at the default priority and activate automatically once `org.pcollections:pcollections` is on your annotation processor's classpath.

> All seven generators participate in `@GenerateFocus` navigator generation. The five collection-like generators (`PVector` / `PStack` / `PSet` / `PSortedSet` / `PBag`) widen through [`EachInstances.fromIterableCollecting`](../optics/common_data_structure_traversals.md); the two map-value generators widen through [`EachInstances.mapValuesEachCollecting`](../optics/common_data_structure_traversals.md), the map-shaped companion to `fromIterableCollecting`. So `@GenerateFocus` on a record with a `PMap` or `PSortedMap` field produces a navigator returning a `TraversalPath` over the values.

---

## Worked Example

Annotate a record whose components include PCollections types:

```java
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.pcollections.PVector;
import org.pcollections.PMap;

@GenerateFocus
@GenerateTraversals
public record Portfolio(
    String owner,
    PVector<Position> positions,
    PMap<String, Double> exposureByCurrency) {}
```

The processor emits a `PortfolioTraversals` companion class with two traversals:

- `Portfolio_positions()` traverses each `Position` in the `PVector`. After a focus change, `TreePVector.from(...)` rebuilds the persistent vector around the new elements.
- `Portfolio_exposureByCurrency()` traverses each `Double` value in the `PMap`. Keys are preserved, values are replaced; `HashTreePMap.from(...)` rebuilds the persistent map.

The Focus DSL composes these into navigators with the correct path types automatically:

```java
import static com.example.PortfolioFocus.portfolio;

TraversalPath<Portfolio, Position> allPositions = portfolio().positions();
TraversalPath<Portfolio, Double> allExposures = portfolio().exposureByCurrency();
```

`TraversalPath` is selected because each generator declares `Cardinality.ZERO_OR_MORE`. No explicit widening call is needed.

---

## Reconstruction Cost

Generated traversals copy the persistent collection into a JDK `ArrayList` for traversal, then hand the resulting list back to the persistent factory. That gives O(n) iteration plus the construction cost of the persistent type, which for `TreePVector` is O(n) and for tree-backed sets and maps is O(n log n). For most domain models this is invisible alongside the work the focus function does.

If you are operating on a hot path and the persistent type matters more than the optic structure, prefer hand-written code that uses PCollections' native `with`/`plus` operations rather than going through `@GenerateTraversals`. The generated traversal is correct, not minimal.

---

## Sorted Variants and Comparators

`PSortedSet` and `PSortedMap` are reconstructed using `TreePSet.from(Collection)` and `TreePMap.from(Map)`, which apply natural ordering. **Custom comparators are not preserved** through a generated traversal.

Two practical implications:

- If your `PSortedSet<MyType>` relies on a custom `Comparator<MyType>`, write the optic by hand rather than relying on the generator
- The natural-ordering reconstruction will throw at runtime if your element type is not `Comparable`. Generators do not detect this statically; a missing `Comparable` bound will surface as a `ClassCastException` the first time the traversal runs

For the common case where elements implement `Comparable` and the natural ordering is the desired one, the generator is the right tool.

---

## Hand-Written Optics

If you need an optic outside `@GenerateTraversals` (for instance, on a third-party type you cannot annotate), the same building blocks are available:

```java
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.each.Each;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import org.pcollections.PMap;
import org.pcollections.HashTreePMap;

Each<PVector<String>, String> pvectorEach =
    EachInstances.fromIterableCollecting(TreePVector::from);

// Persistent maps use the map-shaped companion helper.
Each<PMap<String, Integer>, Integer> pmapValues =
    EachInstances.mapValuesEachCollecting(HashTreePMap::from);
```

`fromIterableCollecting` accepts any function that builds a persistent collection from a `List`; `mapValuesEachCollecting` does the same for any `java.util.Map` subtype, rebuilding the persistent map from a JDK `Map`. The same forms cover all the PCollections types — there is no library-specific factory class; the generic helpers are the public API.

---

## When PCollections Is the Right Choice

PCollections excels when you want persistent immutable collections through the standard `java.util` interfaces, with no custom typeclass machinery. The optics generators slot it into Higher-Kinded-J's traversal pipeline so domain records using `PVector`, `PMap`, and friends compose with the rest of the optics ecosystem without any glue code.

For projects already on Eclipse Collections or Vavr, those existing generators remain the better fit. PCollections is most attractive for greenfield projects that want a small dependency footprint and `java.util` compatibility.

---

~~~admonish info title="Key Takeaways"
* **Seven generator plugins** cover every PCollections collection and map type
* **Activation is automatic** once PCollections is on the annotation processor classpath
* **Reconstruction goes through `from(Collection)` / `from(Map)`**, so traversals round-trip cleanly through the persistent type
* **Sorted variants assume natural ordering**; custom comparators are not preserved by generated code
* **Use `EachInstances.fromIterableCollecting(TreePVector::from)`** (collections) or **`EachInstances.mapValuesEachCollecting(HashTreePMap::from)`** (maps) for hand-written optics outside `@GenerateTraversals`
~~~

~~~admonish tip title="See Also"
- [Traversal Generator Plugins](generator_plugins.md) - The full SPI catalogue and architecture
- [PCollections Integration](pcollections_integration.md) - Phase 1 compatibility tests, benchmarks, and the underlying `ListKind` story
- [Focus DSL](../optics/focus_dsl.md) - Composing generated traversals into navigators
~~~

---

**Previous:** [PCollections Integration](pcollections_integration.md)
**Next:** [Claude Code Skills](claude_code_skills.md)
