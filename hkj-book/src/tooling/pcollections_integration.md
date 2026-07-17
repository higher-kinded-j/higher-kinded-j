# PCollections Integration

~~~admonish info title="What You'll Learn"
- How PCollections persistent collections plug into Higher-Kinded-J via the existing `ListKind`
- The performance characteristics of `PVector` versus `ArrayList` through the HKT pipeline
- Which operations amortise PCollections' iteration cost and which expose it
- How to add PCollections to your own project as an opt-in dependency
~~~

[PCollections](https://pcollections.org/) is a small, lightweight library of persistent immutable
data structures whose types implement the standard `java.util` interfaces. `PVector` implements
`List`, `PStack` implements `List`, `PSet` implements `Set`, and so on. That `java.util`
compatibility makes PCollections a particularly easy fit for Higher-Kinded-J: any `PVector` or
`PStack` can be widened directly into the existing `ListKind` and processed through `ListMonad`,
`ListTraverse`, and `ListSelective` with no production code changes.

This page describes the core integration: a purely additive set of tests, benchmarks, and
documentation that validate the compatibility hypothesis. No new HKT types or instances are
introduced. Optics support goes further, with [generator plugins](pcollections_optics.md) for
`PVector`, `PStack`, `PSet`, `PBag`, and `PMap`. One caveat remains, covered below: transformations
do not preserve the persistent type through the entire HKT pipeline, because there are no dedicated
`Kind`/`Witness` types for it.

---

## What's Included

| Artefact | Location |
|----------|----------|
| Version pin | `gradle/libs.versions.toml` (`pcollections = "5.0.0"`) |
| Integration test | `hkj-core/src/test/.../list/pcollections/PCollectionsListIntegrationTest.java` |
| jQwik property test | `hkj-core/src/test/.../list/pcollections/PCollectionsListPropertyTest.java` |
| JMH benchmarks | `hkj-benchmarks/src/jmh/.../PCollectionsHktBenchmark.java` |
| Runnable example | `hkj-examples/.../basic/pcollections/PCollectionsExample.java` |

---

## Adding PCollections to a Project That Already Uses Higher-Kinded-J

PCollections is not a transitive dependency of `hkj-core`. It is wired in only as a
`testImplementation`. Application code that wants to use the integration must add it explicitly:

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.pcollections:pcollections:5.0.0")
    implementation("io.github.higher-kinded-j:hkj-core:<version>")
}
```

If you also use the Java module system, add `requires org.pcollections;` to your `module-info.java`.

---

## Using PCollections Through `ListKind`

Because `PVector` and `PStack` are `java.util.List` instances, the existing `ListKindHelper.LIST`
helper widens them with no special API:

```java
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

PVector<Integer> source = TreePVector.from(List.of(1, 2, 3));
Kind<ListKind.Witness, Integer> kind = LIST.widen(source);

Kind<ListKind.Witness, Integer> doubled = Instances.monadZero(list()).map(x -> x * 2, kind);
List<Integer> result = LIST.narrow(doubled); // java.util.List, not PVector
```

The same code accepts a `PStack`, an `ArrayList`, or any other `List` implementation. The
widen/narrow boundary is structural rather than nominal.

---

## Caveat: Persistent Type Is Not Preserved End-to-End

`ListMonad.of` and the standard `map`/`flatMap` implementations produce `java.util.List`
instances internally, typically `Collections.singletonList` or a fresh `ArrayList`. That means a
pipeline of the form:

```java
PVector<Integer> in = TreePVector.from(...);
Kind<ListKind.Witness, Integer> out = Instances.monadZero(list()).map(x -> x + 1, LIST.widen(in));
List<Integer> narrowed = LIST.narrow(out); // not a PVector anymore
```

returns a JDK list, not a `PVector`. Round-tripping a `PVector` without any operation preserves
the underlying instance (`narrowed == in`); only transformations widen back to `java.util.List`.
Preserving the persistent type through the entire HKT pipeline would require dedicated
`Kind`/`Witness` types, which the integration does not currently provide.

---

## Performance Characteristics

The headline finding from `PCollectionsHktBenchmark` is that **the widen/narrow boundary is free
for both libraries**, and PCollections pays a structural iteration tax that **shrinks as the
operation does more per-element work**.

Throughput at size 1000, in operations per microsecond (higher is better):

| Operation | `ArrayList` | `PVector` | `PVector / ArrayList` |
|-----------|------------|-----------|-----------------------|
| `widen` + `narrow` | 2.57 K | 2.57 K | 1.00 |
| `traverse` (Optional applicative) | 0.10 | 0.07 | 0.70 |
| `map` | 0.34 | 0.13 | 0.38 |
| `foldMap` (sum monoid) | 0.44 | 0.14 | 0.32 |
| `flatMap` | 0.07 | 0.02 | 0.29 |

Two observations are worth flagging:

- **The boundary is genuinely free.** Both `widen + narrow` benchmarks hit the same throughput
  ceiling regardless of whether the underlying list is an `ArrayList` or a `TreePVector`,
  confirming that `ListHolder` does not defensive-copy.
- **`traverse` is the narrowest gap, not the widest.** Counterintuitively, the more "monadic" the
  operation, the smaller the PCollections overhead becomes. `traverse` dominates the per-element
  cost with applicative `map2` and `Kind` boxing, so the underlying iteration delta becomes a
  small fraction of total time. This is good news: the operation shapes Higher-Kinded-J users
  reach for most often are precisely the ones where PCollections costs least.

`flatMap` shows the widest gap because each call also allocates a fresh `TreePVector` inside the
lambda. That reconstruction cost is part of the measurement, not pure iteration overhead.

To reproduce:

```sh
./gradlew :hkj-benchmarks:jmh -Pincludes=".*PCollectionsHktBenchmark.*"
./gradlew :hkj-benchmarks:benchmarkReport
```

The fast-feedback configuration (one fork, one warmup, one measurement iteration) leaves error
bars as `NaN`. For numbers worth citing externally, use `-Pjmh.warmupIterations=2
-Pjmh.iterations=3`.

---

## What the Tests Cover

`PCollectionsListIntegrationTest` exercises:

- `widen`/`narrow` round-trips for `PVector` and `PStack`, including instance preservation
- Functor `map` over `PVector` and `PStack`
- Monad `flatMap` and `ap`, mixing `PVector` and `PStack` inputs
- `Traverse` with the `Optional` applicative, covering both success and short-circuit cases
- `Foldable.foldMap` with the sum and string monoids
- `Selective.select` over a `PVector` of `Choice` values
- `Alternative` (`empty`, `orElse`) composing PCollections lists with each other

`PCollectionsListPropertyTest` uses jQwik to verify, across randomised inputs, that the standard
laws hold for `PVector` widened through `ListKind`:

- Functor identity and composition
- Monad left identity, right identity, and associativity
- Foldable: `foldMap` matches `stream().sum()`, and is a monoid homomorphism over concatenation

---

~~~admonish example title="See Example Code"
[PCollectionsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/pcollections/PCollectionsExample.java)
~~~

~~~admonish info title="Key Takeaways"
* **`PVector` and `PStack` work through `ListKind` with no production code changes**, because they implement `java.util.List`
* **The widen/narrow boundary is free**, with no defensive copying in either direction
* **PCollections pays a 2 to 3 times iteration tax** on `map`, `flatMap`, and `foldMap` compared to `ArrayList`
* **The tax shrinks for richer operations**: `traverse` is only 30% slower because per-element applicative work dominates
* **Operations return `java.util.List`**, not the original persistent type; preserving it end-to-end is not currently supported
~~~

~~~admonish tip title="See Also"
- [Benchmarks and Performance](../benchmarks.md) - The full benchmark suite and how to read its output
- [Traversal Generator Plugins](generator_plugins.md) - PCollections support for `@GenerateTraversals`
- [List Type Class Instances](../monads/list_monad.md) - The instances PCollections piggy-backs on
~~~

---

**Previous:** [Traversal Generator Plugins](generator_plugins.md)
**Next:** [PCollections Optics](pcollections_optics.md)
