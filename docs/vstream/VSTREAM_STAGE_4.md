# Stage 4: Each Integration and FocusDSL

## Overview

This stage integrates VStream with the optics ecosystem by providing an `Each<VStream<A>, A>`
instance, enabling TraversalPath composition through `.each()`, and bridging VStream into the
FocusDSL for fluent navigation through structures containing VStream fields. This is where
VStream becomes a first-class participant in the optics world, allowing queries and
modifications to flow through VStream fields using the same vocabulary as lists, sets, and
other traversable containers.

**Module**: `hkj-core`
**Packages**: `org.higherkindedj.optics.each`, `org.higherkindedj.optics.focus`

**Prerequisites**: Stages 1-3 (Core VStream, HKT Encoding, VStreamPath)

---

## Detailed Tasks

### 4.1 VStream Traversal

**File**: `hkj-core/src/main/java/org/higherkindedj/optics/each/VStreamTraversals.java`

Create a Traversal for VStream elements following the pattern of `Traversals.forList()`:

```java
public final class VStreamTraversals {

    private VStreamTraversals() {}

    public static <A> Traversal<VStream<A>, A> forVStream() {
        return new Traversal<>() {
            @Override
            public <F extends WitnessArity<TypeArity.Unary>> Kind<F, VStream<A>> modifyF(
                    Function<A, Kind<F, A>> f,
                    VStream<A> source,
                    Applicative<F> applicative) {
                // Materialise stream, traverse as list, reconstruct as VStream
                List<A> elements = source.toList().run();
                Kind<F, List<A>> traversed =
                    TrampolineUtils.traverseListStackSafe(elements, f, applicative);
                return applicative.map(VStream::fromList, traversed);
            }
        };
    }
}
```

**Requirements**:
- `modifyF` materialises the stream (finite streams only)
- Uses `TrampolineUtils.traverseListStackSafe` for stack safety with large streams
- Reconstructs result via `VStream.fromList()`
- Document clearly: materialisation occurs; not suitable for infinite streams
- Comprehensive javadoc with usage examples

**Design note**: This materialisation approach is consistent with how `StreamEach` works
today (via `TraverseTraversals.forStream()`). The alternative (preserving laziness through
the traversal) is explored in Stage 6 as `StreamTraversal`.

### 4.2 VStream Each Instance

**File**: `hkj-core/src/main/java/org/higherkindedj/optics/each/EachInstances.java`

Add VStream Each to the existing EachInstances class:

```java
public static <A> Each<VStream<A>, A> vstreamEach() {
    return Each.fromTraversal(VStreamTraversals.forVStream());
}
```

**Requirements**:
- Non-indexed (VStream does not naturally expose positional indices)
- `supportsIndexed()` returns `false`
- Follows the pattern of `streamEach()` in EachInstances
- Javadoc explaining finite-stream requirement

### 4.3 Optional: Indexed VStream Traversal

Consider providing an optional indexed traversal that tracks element position:

```java
public static <A> Each<VStream<A>, A> vstreamIndexedEach() {
    return Each.fromIndexedTraversal(
        new IndexedTraversal<Integer, VStream<A>, A>() {
            @Override
            public <F extends WitnessArity<TypeArity.Unary>>
            Kind<F, VStream<A>> imodifyF(
                    BiFunction<Integer, A, Kind<F, A>> f,
                    VStream<A> source,
                    Applicative<F> applicative) {
                List<A> elements = source.toList().run();
                // Apply f with index to each element
                // Use TrampolineUtils for stack safety
                // Reconstruct as VStream
            }
        }
    );
}
```

**Decision**: Implement if the indexed variant provides clear value (e.g., for position-based
filtering or transformation). Otherwise defer to a future stage. Document the decision.

### 4.4 FocusDSL Integration

The existing `.each()` method on `TraversalPath` already supports explicit `Each` instances:

```java
// Already exists in TraversalPath
default <E> TraversalPath<S, E> each(Each<A, E> eachInstance) {
    return via(eachInstance.each());
}
```

With the VStream Each instance, this works immediately:

```java
// Navigate through a VStream field
FocusPath<Config, VStream<Rule>> rulesLens = /* generated lens */;

TraversalPath<Config, Rule> allRules =
    FocusPath.of(rulesLens)
        .each(EachInstances.vstreamEach());
```

**However**, the parameterless `.each()` method defaults to list elements:

```java
default <E> TraversalPath<S, E> each() {
    return via((Traversal<A, E>) FocusPaths.listElements());
}
```

**Task**: Evaluate whether the parameterless `.each()` should be updated to detect VStream
types automatically, or whether explicit `Each` instance passing is sufficient.

**Recommendation**: Keep parameterless `.each()` as list-only (avoids runtime type checking
overhead) and rely on explicit `each(vstreamEach())` for VStream fields. Document this
clearly.

### 4.5 TraversalPath toVStreamPath Bridge

**File**: `hkj-core/src/main/java/org/higherkindedj/optics/focus/TraversalPath.java`

Add a `toVStreamPath` method alongside existing `toStreamPath`, `toListPath`, etc.:

```java
default VStreamPath<A> toVStreamPath(S source) {
    List<A> elements = getAll(source);
    return Path.vstreamFromList(elements);
}
```

**Requirements**:
- Materialises traversal results to a list, then wraps as VStreamPath
- Follow the pattern of existing `toStreamPath()`, `toListPath()`, `toNonDetPath()`
- Javadoc with code examples

### 4.6 VStreamPath fromEach Factory

**File**: Update `VStreamPath` interface and `DefaultVStreamPath`

Add a static factory that creates a VStreamPath from a structure and its Each instance:

```java
static <S, A> VStreamPath<A> fromEach(S source, Each<S, A> each) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(each, "each must not be null");
    List<A> elements = Traversals.getAll(each.each(), source);
    return Path.vstreamFromList(elements);
}
```

**Requirements**:
- Extracts all elements via the Each traversal
- Wraps as VStream for lazy downstream processing
- Null parameter validation
- Javadoc with usage examples

### 4.7 Optics Composition Examples

Verify that VStream composes naturally with the full optics hierarchy:

1. **Lens then VStream Each**:
   ```java
   FocusPath<Company, VStream<Department>> depts = ...;
   TraversalPath<Company, Department> allDepts = FocusPath.of(depts).each(vstreamEach());
   ```

2. **Nested VStream Each**:
   ```java
   TraversalPath<Company, Employee> allEmps =
       FocusPath.of(deptsLens)
           .each(vstreamEach())           // TraversalPath<Company, Department>
           .via(deptEmployeesLens)         // TraversalPath<Company, VStream<Employee>>
           .each(vstreamEach());           // TraversalPath<Company, Employee>
   ```

3. **VStream Each then Prism**:
   ```java
   TraversalPath<Config, String> activeValues =
       FocusPath.of(entriesLens)
           .each(vstreamEach())           // TraversalPath<Config, Entry>
           .via(entryValuePrism);         // TraversalPath<Config, String>
   ```

4. **modifyF with VTask Applicative through VStream**:
   ```java
   TraversalPath<Config, Rule> allRules =
       FocusPath.of(rulesLens).each(vstreamEach());

   Kind<VTaskKind.Witness, Config> enriched = allRules.modifyF(
       rule -> VTASK.widen(VTask.of(() -> enrichRule(rule))),
       config,
       new VTaskApplicative()
   );
   ```

5. **Selective branch through VStream**:
   ```java
   allRules.toTraversal().branch(
       Rule::isEnabled,
       rule -> VTASK.widen(VTask.of(() -> processEnabled(rule))),
       rule -> VTASK.widen(VTask.of(() -> archiveDisabled(rule))),
       config,
       vtaskSelective
   );
   ```

---

## Testing

### 4.8 VStream Each Instance Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/optics/each/VStreamEachTest.java`

Follow the established pattern from EachInstancesTest (1085 lines):

1. **Basic Traversal** (`@Nested class BasicTraversal`)
   - `getAll()` extracts all elements from VStream
   - `getAll()` on empty VStream returns empty list
   - `modify()` transforms all elements
   - `modify()` on empty VStream returns empty VStream
   - Element order preserved

2. **With Different Applicatives** (`@Nested class WithApplicatives`)
   - `modifyF` with IdApplicative (pure transformation)
   - `modifyF` with MaybeApplicative (fail-fast on Nothing)
   - `modifyF` with ValidatedApplicative (error accumulation)
   - `modifyF` with VTaskApplicative (effectful per-element processing)
   - `modifyF` with OptionalApplicative

3. **Indexing** (`@Nested class Indexing`)
   - `supportsIndexed()` returns false for vstreamEach
   - `eachWithIndex()` returns empty Optional
   - If indexed variant implemented: verify index correctness

4. **Composition with Optics** (`@Nested class OpticsComposition`)
   - Lens then VStream Each
   - Nested VStream Each (two levels deep)
   - VStream Each then Prism
   - VStream Each then Affine
   - Full pipeline: Lens -> Each -> Lens -> modify

5. **Edge Cases** (`@Nested class EdgeCases`)
   - Single-element VStream
   - VStream with null elements (if permitted)
   - VStream with duplicate elements
   - Large VStream (1000+ elements for stack safety)

6. **Integration with FocusDSL** (`@Nested class FocusDSLIntegration`)
   - `.each(vstreamEach())` on FocusPath produces correct TraversalPath
   - `getAll()` through composed path
   - `modifyAll()` through composed path
   - `setAll()` through composed path
   - `filter()` on TraversalPath after `.each()`

### 4.9 TraversalPath toVStreamPath Tests

**File**: Extend `TraversalPathTest` or create dedicated test:

- `toVStreamPath()` collects all elements
- `toVStreamPath()` on empty traversal returns empty VStreamPath
- `toVStreamPath()` preserves element order
- VStreamPath from traversal supports further stream operations

### 4.10 VStreamPath fromEach Tests

- `fromEach()` with list Each extracts all list elements
- `fromEach()` with VStream Each extracts all stream elements
- `fromEach()` with empty structure returns empty VStreamPath
- Null parameter handling

### 4.11 Optics Law Tests for VStream

**File**: `hkj-core/src/test/java/org/higherkindedj/optics/each/VStreamTraversalLawsTest.java`

Verify traversal laws for VStream:

1. **Identity Law**: `modifyF(Id::of, stream, idApplicative)` equals the original stream
2. **Composition**: Traversing twice is equivalent to traversing with composed function
3. **Structure Preservation**: Traversal preserves VStream structure (length, order)

---

## Documentation

### 4.12 Javadoc

All public types and methods require comprehensive javadoc:
- VStreamTraversals: Explain traversal semantics, materialisation, finite-stream requirement
- EachInstances.vstreamEach(): Usage with FocusDSL, comparison with streamEach
- TraversalPath.toVStreamPath(): Materialisation behaviour
- VStreamPath.fromEach(): Factory usage
- British English throughout

### 4.13 Documentation Page

**File**: `docs/vstream/vstream_optics.md`

Following STYLE-GUIDE.md:
- "What You'll Learn" admonishment
- Problem statement: navigating through structures with VStream fields
- Solution: Each instance + FocusDSL composition
- Diagram showing optics composition with VStream
- Code examples for each composition pattern (lens -> each, nested each, each -> prism)
- modifyF with VTask for effectful traversal through VStream
- Comparison: VStream Each vs List Each vs Stream Each
- "Key Takeaways" admonishment
- "See Also" linking to Each, Traversal, FocusDSL, VStreamPath

---

## Examples

### 4.14 Example Code

**File**: `hkj-examples/src/main/java/org/higherkindedj/examples/vstream/VStreamOpticsExample.java`

Demonstrate with realistic domain model:

```java
// Domain model using @GenerateLenses annotations
@GenerateLenses
record Company(String name, VStream<Department> departments) {}

@GenerateLenses
record Department(String name, VStream<Employee> employees) {}

@GenerateLenses
record Employee(String name, double salary, boolean active) {}
```

Scenarios:
1. Get all employee names across all departments
2. Give all active employees a 10% raise
3. Count employees per department
4. Use modifyF with VTask to enrich employee data from an external service
5. Filter departments with more than 5 employees
6. Selective branch: different salary calculations for active vs inactive

---

## Tutorials

### 4.15 Tutorial

**File**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/vstream/Tutorial04_VStreamOptics.java`

Following TUTORIAL-STYLE-GUIDE.md:
- 10-12 exercises:
  1. Create a VStream Each instance using EachInstances.vstreamEach()
  2. Use getAll to extract all elements from a VStream via Each
  3. Use modify to transform all VStream elements
  4. Navigate into a VStream field using FocusPath and .each()
  5. Compose lens then each to reach nested elements
  6. Nest two levels of VStream each
  7. Use modifyAll on a TraversalPath through VStream
  8. Filter elements via TraversalPath after .each()
  9. Use modifyF with Optional applicative (fail-fast validation)
  10. Use modifyF with VTask applicative (effectful enrichment)
  11. Convert a TraversalPath result to VStreamPath using toVStreamPath
  12. Create VStreamPath from a structure using fromEach
- Solution file: `Tutorial04_VStreamOptics_Solution.java`
- Time estimate: 15 minutes

---

## Acceptance Criteria

- [ ] VStreamTraversals.forVStream() creates valid Traversal
- [ ] EachInstances.vstreamEach() returns correct Each instance
- [ ] VStream Each integrates with `.each(vstreamEach())` on FocusDSL paths
- [ ] TraversalPath.toVStreamPath() materialises and wraps correctly
- [ ] VStreamPath.fromEach() creates VStreamPath from structure + Each
- [ ] modifyF works with Id, Maybe, Optional, Validated, VTask applicatives
- [ ] Nested VStream Each composition works (two+ levels deep)
- [ ] Traversal laws verified for VStream
- [ ] All Each instance tests pass following EachInstancesTest pattern
- [ ] Optics composition tests pass
- [ ] FocusDSL integration tests pass
- [ ] Javadoc complete on all public API
- [ ] Documentation page written
- [ ] Example code with realistic domain model
- [ ] Tutorial and solution file complete
- [ ] All existing tests continue to pass

---

## GitHub Issue Summary

**Title**: VStream Stage 4: Each Typeclass Integration and FocusDSL Composition

Integrate VStream into the optics ecosystem with an `Each<VStream<A>, A>` instance, enabling
TraversalPath composition through `.each()` and full FocusDSL support for navigating through
structures containing VStream fields.

**Key deliverables**:
- `VStreamTraversals.forVStream()` - Traversal for VStream elements (materialising,
  stack-safe via TrampolineUtils)
- `EachInstances.vstreamEach()` - Each instance for VStream
- FocusDSL integration: `.each(vstreamEach())` on FocusPath/AffinePath/TraversalPath
- `TraversalPath.toVStreamPath()` - bridge from TraversalPath to VStreamPath
- `VStreamPath.fromEach()` - factory creating VStreamPath from structure + Each instance
- Verified composition: Lens -> VStream Each, nested VStream Each, Each -> Prism/Affine
- `modifyF` verified with Id, Maybe, Optional, Validated, and VTask applicatives
- Traversal law tests for VStream
- Each instance tests following EachInstancesTest pattern
- Documentation, realistic domain example, and tutorial with 12 exercises

**Packages**: `org.higherkindedj.optics.each`, `org.higherkindedj.optics.focus` in `hkj-core`

**Dependencies**: Stages 1-3 (Core VStream, HKT Encoding, VStreamPath)
