# VStream\<A\>: Lazy Pull-Based Streaming on Virtual Threads

## Master Plan

### Vision

VStream\<A\> is a lazy, pull-based streaming abstraction that executes on virtual threads,
providing efficient streaming operations with full type safety and seamless integration
with the higher-kinded-j ecosystem. It fills the gap between VTask (single-value effect on
virtual threads) and StreamPath (reusable wrapper over `java.util.Stream` with no virtual
thread integration), enabling composable, effectful streaming pipelines that leverage Java 25
virtual threads for scalable concurrent processing.

---

### Problem Statement

Higher-kinded-j currently has:

- **VTask\<A\>**: A lazy effect type for single values on virtual threads, with full monad
  hierarchy and MonadError support.
- **StreamPath**: A reusable wrapper over `java.util.Stream` that materialises internally
  to a list, defeating true laziness for large or infinite sequences.
- **Each\<S, A\>**: A typeclass for canonical element-wise traversal with instances for List,
  Set, Map, Optional, Array, Stream, and String.
- **Optics**: Full hierarchy (Lens, Prism, Affine, Traversal, Fold) with `modifyF` as the
  core polymorphic operation.
- **FocusDSL**: FocusPath/AffinePath/TraversalPath for fluent, type-safe navigation with
  `.each()`, `.at()`, `.via()` composition.

**The gap**: There is no streaming abstraction that combines laziness, pull-based semantics,
virtual thread execution, and integration with the HKT/optics ecosystem. Java's `Stream<A>`
is single-use, eagerly consumed by traversal, and lacks effect integration. VTask is
single-value. StreamPath materialises internally. None of these support effectful,
element-by-element production on virtual threads.

---

### Design Choice: Pull/Step-Based Stream (Approach B)

After evaluating four approaches (Cons-Cell, Pull/Step, Channel-Based, Resource-Safe), the
**Pull/Step-based** design was selected as the recommended approach.

#### Core Representation

```java
@FunctionalInterface
public interface VStream<A> extends VStreamKind<A> {
    VTask<Step<A>> pull();

    sealed interface Step<A> {
        record Emit<A>(A value, VStream<A> tail) implements Step<A> {}
        record Done<A>()                          implements Step<A> {}
        record Skip<A>(VStream<A> tail)           implements Step<A> {}
    }
}
```

Each `pull()` returns a `VTask<Step<A>>`: the consumer calls `pull()`, which executes on a
virtual thread and returns either an element plus continuation (`Emit`), a skip plus
continuation (`Skip`, enabling efficient filtering), or completion (`Done`).

#### Why This Design

| Criterion               | Assessment |
|--------------------------|------------|
| VTask alignment          | Each pull is a VTask; natural fit with existing execution model |
| Laziness                 | Full; elements produced on demand only |
| Virtual thread fit       | Excellent; each pull can block on a virtual thread cheaply |
| Memory efficiency        | High; no persistent cons-cell structure |
| Filter optimisation      | Skip step avoids element allocation |
| Fusion potential         | Consecutive maps/filters composable before pulling |
| HKT integration          | Single functional interface; straightforward Kind witness |
| Optics compatibility     | Works with modifyF, Each, and FocusDSL composition |
| Incremental extensibility | Resource safety (bracket) addable later without breaking changes |

#### Alternatives Considered

| Approach | Summary | Reason Not Selected |
|----------|---------|---------------------|
| A: Cons-Cell (Haskell-style) | `Cons(head, VTask<tail>)` | Higher memory overhead per element; stack safety concerns |
| C: Channel-Based (CSP/Go-style) | Producer/consumer via blocking queue | Fundamentally imperative; poor functional composition; architectural outlier |
| D: Resource-Safe (fs2/ZIO-style) | Built-in bracket/scope tracking | Over-engineered for v1; addable as extension to Approach B later |

---

### Capability Hierarchy Placement

```
Composable (Functor)
    |
Combinable (Applicative)
    |
Chainable (Monad)
    +-- Effectful (IOPath, VTaskPath)
    +-- Recoverable (MaybePath, EitherPath, TryPath, ValidationPath)
    +-- VStreamPath  <-- new
```

VStreamPath implements `Chainable<A>` because:
- `map` transforms each element (Functor)
- `zipWith` pairs elements from two streams positionally (Applicative)
- `via`/`flatMap` substitutes each element with a sub-stream and flattens (Monad)

It does **not** implement `Effectful<A>` (that interface is for single-value effects) or
`Recoverable` (streams do not have a single error channel).

---

### Implementation Stages

The implementation is divided into six stages, each independently deliverable and releasable.
Each stage builds on the previous but produces a usable, tested, documented increment.

| Stage | Title | Dependencies | Key Deliverables |
|-------|-------|--------------|------------------|
| 1 | Core VStream Type | None | VStream, Step, factory methods, combinators, tests |
| 2 | HKT Encoding and Type Classes | Stage 1 | VStreamKind, Functor, Monad, Foldable, Alternative, law tests |
| 3 | VStreamPath and Effect Path API | Stages 1-2 | VStreamPath, Path factories, PathOps, conversions, focus bridge |
| 4 | Each Integration and FocusDSL | Stages 1-3 | Each instance, TraversalPath integration, optics composition |
| 5 | Parallel Operations and Chunking | Stages 1-3 | parEvalMap, chunking, VStreamPar combinators, benchmarks |
| 6 | Advanced Features | Stages 1-5 | Resource safety, reactive interop, StreamTraversal optic |

Each stage has a dedicated implementation document with detailed tasks, testing requirements,
documentation, tutorials, and a GitHub issue summary.

---

### Integration Points

#### With Optics (Traversal.modifyF)

VStream composes with existing optics through standard Traversal composition. Using
`modifyF` with `VTaskApplicative`, effectful transformations apply to all stream elements:

```java
Traversal<Company, Employee> allEmployees = /* composed traversal */;

Kind<VTaskKind.Witness, Company> enriched = allEmployees.modifyF(
    emp -> VTASK.widen(VTask.of(() -> enrichFromAPI(emp))),
    company,
    new VTaskApplicative()
);
```

#### With FocusDSL (.each())

The `.each()` method on TraversalPath dispatches to Each instances. With a VStream Each
instance, navigation flows naturally through VStream fields:

```java
TraversalPath<Config, Rule> allRules =
    FocusPath.of(configRulesLens)   // FocusPath<Config, VStream<Rule>>
        .each(vstreamEach);          // TraversalPath<Config, Rule>

List<Rule> rules = allRules.getAll(config);
Config updated = allRules.modifyAll(Rule::enable, config);
```

#### With EffectPath API

VStreamPath bridges into the Effect Path ecosystem with materialisation to VTaskPath for
terminal operations:

```java
VStreamPath<User> users = Path.vstream(userStream);
VTaskPath<List<User>> collected = users.filter(User::isActive).toList();
List<User> result = collected.unsafeRun();
```

#### With Selective (Traversal.branch)

VStream elements can be processed with branched strategies using the Selective typeclass:

```java
Kind<VTaskKind.Witness, VStream<Account>> processed =
    vStreamTraversal.branch(
        Account::isPremium,
        acc -> VTASK.widen(VTask.of(() -> premiumProcess(acc))),
        acc -> VTASK.widen(VTask.of(() -> standardProcess(acc))),
        accountStream,
        vtaskSelective
    );
```

---

### Further Opportunities

VStream enables several additional capabilities for higher-kinded-j:

1. **Streaming Foldable with Monoid**: Lazy monoid-based aggregation without holding the
   entire stream in memory.

2. **Streaming Validation**: `VStream<Validated<E, A>>` for streaming validation with
   early termination or error accumulation.

3. **Natural Transformations**: Type-safe conversion between StreamPath and VStreamPath via
   `GenericPath.mapK`.

4. **Chunked/Batched Processing**: `VStream.chunk(n)` for efficient batch I/O operations
   (bulk database inserts, batch API calls).

5. **StreamTraversal Optic**: A new optic type that preserves laziness during traversal,
   unlike standard Traversal which materialises via Applicative.

6. **Reactive Bridge**: `VStream` to/from `java.util.concurrent.Flow.Publisher` for
   reactive streams interop.

7. **PathProvider SPI**: VStream registered via ServiceLoader for dynamic `Path.from()`
   integration.

8. **Backpressure-Free Processing**: Virtual threads naturally handle backpressure; if the
   consumer is slow, the producer blocks on a virtual thread cheaply.

9. **For-Comprehension Support**: The existing annotation processor could be extended to
   support VStream in generated for-comprehension syntax.

---

### Risk Summary

| Risk | Severity | Mitigation |
|------|----------|------------|
| Infinite stream misuse (OOM on materialisation) | Medium | Document clearly; provide `take`/`takeWhile`; materialisation methods warn in javadoc |
| Stack overflow in deep flatMap chains | Medium | Iterative evaluation loop in `pull()` (trampoline-style unfolding of Suspend) |
| API surface complexity | Medium | Start with core combinators; extend incrementally per stage |
| Each/Traversal materialisation for infinite streams | High | Document finite-only constraint on Each; provide streaming-specific alternatives |
| Testing complexity (lazy + effectful + concurrent) | Medium | Property-based testing with stream laws; deterministic test helpers |
| Performance overhead vs raw Java streams | Low | Benchmark in Stage 5; provide `toStreamPath()` escape hatch |
| Sealed hierarchy permits | Low | VStreamPath extends Chainable; verify permit compatibility |

---

### Implementation Documents

- [Stage 1: Core VStream Type](./VSTREAM_STAGE_1.md)
- [Stage 2: HKT Encoding and Type Classes](./VSTREAM_STAGE_2.md)
- [Stage 3: VStreamPath and Effect Path API](./VSTREAM_STAGE_3.md)
- [Stage 4: Each Integration and FocusDSL](./VSTREAM_STAGE_4.md)
- [Stage 5: Parallel Operations and Chunking](./VSTREAM_STAGE_5.md)
- [Stage 6: Advanced Features](./VSTREAM_STAGE_6.md)
