# Feature Request: Support SPI-registered types in `@Focus` static method generation

## Summary

When a `@Focus`-annotated record has a field of an SPI-registered type (e.g., `Either<L,R>`,
`Map<K,V>`, `int[]`), the generated static method returns `FocusPath` and provides no unwrapping.
The path type is incorrect and the user cannot traverse into the container's elements without manual
optic composition.

This is the companion issue to the Navigator path-widening fix, which resolves the same symptom for
`@Navigate`-generated classes but does not address `@Focus` static methods.

## Background

### What Navigator fix covers

The Navigator fix adds a `Cardinality` enum to the `TraversableGenerator` SPI and consults SPI
implementations in `NavigatorClassGenerator.getFieldPathKind()` to select the correct path type
(`AffinePath` or `TraversalPath`) for SPI-registered container types. This is purely a return-type
decision — no optic composition code needs to be generated.

### Why `@Focus` is harder

`FocusProcessor` generates static methods that return composed optics. Each widening type produces
a specific optic composition call:

| Widening type | Generated code | Result |
|---|---|---|
| `OPTIONAL` | `.some()` | `AffinePath` — unwraps `Optional<A>` to `A` |
| `COLLECTION` | `.each()` | `TraversalPath` — traverses `List<A>` elements |
| `NULLABLE` | `.nullable()` | `AffinePath` — handles null as absent |
| `KIND_ZERO_OR_ONE` | `.traverseOver(expr).headOption()` | `AffinePath` via HKT bridge |
| `KIND_ZERO_OR_MORE` | `.traverseOver(expr)` | `TraversalPath` via HKT bridge |
| `NONE` | (no suffix) | `FocusPath` — direct lens |

SPI-registered types like `Either`, `Map`, and arrays:
- Are **not** `Kind<F,A>` types, so the `traverseOver()` path does not apply
- Have **no** dedicated `.some()` or `.each()` equivalent on `FocusPath`
- Require a new code generation strategy to compose the correct optic

## Current code structure

### `FocusProcessor.analyseFieldType()` (lines 439-488)

Determines widening by checking, in order:

1. Primitive types (no widening unless `@Nullable`)
2. `OPTIONAL_TYPES` — hardcoded set: `java.util.Optional`, `org.higherkindedj.hkt.maybe.Maybe`
3. `COLLECTION_TYPES` — hardcoded set: `java.util.List`, `java.util.Set`, `java.util.Collection`
4. `Kind<F,A>` — via `KindFieldAnalyser`
5. `@Nullable` annotation
6. Default: `NONE` (returns `FocusPath`)

SPI types fall through to step 6.

### Code generation switch (lines 312-373)

Each `WideningType` variant has a dedicated code generation branch in `generateFocusMethod()`.
Adding SPI support requires both a new `WideningType` variant and a corresponding code generation
branch that knows how to compose the correct optic.

## Proposed solutions

### Option A: Overloaded `.some(Affine)` and `.each(Each)` methods

Add overloads to `FocusPath` that accept an optic instance parameter, then have the SPI provide the
expression to generate.

#### API additions to FocusPath / AffinePath / TraversalPath

```java
// On FocusPath<S, A>:
<B> AffinePath<S, B> some(Affine<A, B> affine);
<B> TraversalPath<S, B> each(Each<A, B> each);

// On AffinePath<S, A>:
<B> AffinePath<S, B> some(Affine<A, B> affine);
<B> TraversalPath<S, B> each(Each<A, B> each);

// On TraversalPath<S, A>:
<B> TraversalPath<S, B> some(Affine<A, B> affine);
<B> TraversalPath<S, B> each(Each<A, B> each);
```

#### SPI extension on `TraversableGenerator`

```java
/**
 * Returns the Java expression that creates the optic instance for composing
 * into a FocusPath chain.
 *
 * For ZERO_OR_ONE types, this should return an Affine expression.
 * For ZERO_OR_MORE types, this should return an Each/Traversal expression.
 *
 * @return a valid Java source expression, e.g. "Affines.eitherRight()"
 */
String generateOpticExpression();

/**
 * Returns the fully qualified class names that need to be imported for the
 * optic expression to compile.
 *
 * @return set of fully qualified class names
 */
Set<String> getRequiredImports();
```

#### Generated code example

```java
// For a field: Either<String, Integer> result
public static <...> AffinePath<MyRecord, Integer> result() {
    return FocusPath.of(Lens.of(MyRecord::result, (source, newValue) -> new MyRecord(newValue)))
        .some(Affines.eitherRight());
}

// For a field: Map<String, Integer> scores
public static <...> TraversalPath<MyRecord, Integer> scores() {
    return FocusPath.of(Lens.of(MyRecord::scores, (source, newValue) -> new MyRecord(newValue)))
        .each(EachInstances.mapValues());
}
```

#### Pros

- Does not require types to participate in the HKT system
- Works for any type that can provide an `Affine` or `Each`/`Traversal` instance
- Clean separation: SPI provides the optic, processor generates the composition

#### Cons

- Larger API surface on `FocusPath`/`AffinePath`/`TraversalPath`
- Each SPI plugin must implement two new methods
- Need to handle import generation for the optic expressions

### Option B: Require SPI types to be liftable into HKT and use `traverseOver()`

Piggyback on the existing `Kind<F,A>` + `Traverse` mechanism by requiring SPI types to provide a
way to lift into the HKT encoding.

#### Generated code example

```java
// For a field: Either<String, Integer> result
return FocusPath.of(Lens.of(...))
    .<EitherWitness, Integer>traverseOver(EitherTraverse.instance())
    .headOption();
```

#### Pros

- Reuses existing `traverseOver()` infrastructure
- No new API surface on path classes

#### Cons

- Not all SPI types have HKT encodings (e.g., `int[]`, `Map<K,V>`, Guava collections)
- Requires wrapping/unwrapping overhead for types that aren't natively `Kind`-encoded
- Limits which types can participate in `@Focus` widening

### Recommendation

**Option A** is the more general solution. It decouples the optic composition from the HKT system,
meaning any type that can provide an `Affine` or `Traversal` instance can participate, regardless
of whether it has a `Kind` encoding.

Option B could work as a complementary mechanism for types that already have HKT encodings, but
should not be the only path.

## Affected types

The following SPI-registered types would benefit from this feature:

### ZERO_OR_ONE (should generate `AffinePath`)

| Type | Plugin module | Optic expression (Option A) |
|---|---|---|
| `Either<L, R>` | hkj-processor-plugins | `Affines.eitherRight()` |
| `Try<A>` | hkj-processor-plugins | `Affines.trySuccess()` |
| `Validated<E, A>` | hkj-processor-plugins | `Affines.validatedValid()` |
| `Maybe<A>` | hkj-processor-plugins | `Affines.maybeToOptional()` or `.some()` equivalent |
| `java.util.Optional<A>` | basejdk-processor-plugins | Already handled by hardcoded `.some()` |

### ZERO_OR_MORE (should generate `TraversalPath`)

| Type | Plugin module | Optic expression (Option A) |
|---|---|---|
| `Map<K, V>` | basejdk-processor-plugins | `EachInstances.mapValues()` |
| `T[]` (arrays) | basejdk-processor-plugins | `EachInstances.array()` |
| `java.util.List<A>` | basejdk-processor-plugins | Already handled by hardcoded `.each()` |
| `java.util.Set<A>` | basejdk-processor-plugins | Already handled by hardcoded `.each()` |
| `java.util.Collection<A>` | basejdk-processor-plugins | Already handled by hardcoded `.each()` |
| Eclipse Collections types | eclipse-processor-plugins | TBD |
| Guava Collections types | guava-processor-plugins | TBD |
| Vavr types | vavr-processor-plugins | TBD |
| Apache Commons types | apache-processor-plugins | TBD |

## Scope and tasks

1. **Add `.some(Affine)` and `.each(Each)` overloads** to `FocusPath`, `AffinePath`, and
   `TraversalPath`
2. **Extend `TraversableGenerator` SPI** with `generateOpticExpression()` and
   `getRequiredImports()`
3. **Add new `WideningType` variant** (e.g., `SPI_ZERO_OR_ONE`, `SPI_ZERO_OR_MORE`)
4. **Update `FocusProcessor.analyseFieldType()`** to consult loaded SPI generators as a fallback
   after the hardcoded checks (same pattern as the Navigator fix)
5. **Add code generation branch** in `generateFocusMethod()` for the new widening types
6. **Implement `generateOpticExpression()`** in each plugin generator
7. **Determine required `Affine`/`Each` utility methods** — some may need to be created (e.g.,
   `Affines.eitherRight()`, `Affines.trySuccess()`)
8. **Tests** — compile-testing integration tests for representative types from each category

## Dependencies

- Navigator SPI fix (adds `Cardinality` enum and SPI loading in `FocusProcessor`) should land first,
  as this feature builds on the same infrastructure
- Some optic utility methods (`Affines.eitherRight()`, etc.) may not exist yet and would need to be
  added to `hkj-core`

## Open questions

1. Should the hardcoded `OPTIONAL_TYPES` and `COLLECTION_TYPES` checks be removed in favour of
   the SPI, or kept as a fast path?
2. For `Either<L,R>` and `Validated<E,A>`, the focus element is the second type argument (index 1).
   The SPI already handles this via `getFocusTypeArgumentIndex()`. Should
   `generateOpticExpression()` be parameterised by the field's type arguments, or should the optic
   expression be fixed per generator?
3. Should `Map<K,V>` traversal focus on values (most common), keys, or entries? This may need to be
   configurable via an annotation parameter.
4. For array types, the `Each` instance needs the component type at runtime. How should this be
   communicated through the SPI?
