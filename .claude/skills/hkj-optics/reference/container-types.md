# Container Type Support

## Cardinality Model

Every container type has a **cardinality** that determines the generated Focus path type:

| Cardinality      | Meaning               | Generated Path   | Example Types                        |
|------------------|-----------------------|------------------|--------------------------------------|
| `ZERO_OR_ONE`    | At most one value     | `AffinePath`     | `Either`, `Try`, `Validated`, `Maybe`, `Optional` |
| `ZERO_OR_MORE`   | Collection of values  | `TraversalPath`  | `List`, `Set`, `Map`, arrays, Eclipse/Guava/Vavr collections |

## HKJ and JDK Types

| Container         | Cardinality    | Navigator Path   | Static Focus Method | Optic Used                       |
|-------------------|----------------|------------------|---------------------|----------------------------------|
| `Either<L, R>`    | Zero or one    | `AffinePath`     | `AffinePath`        | `Affines.eitherRight()`          |
| `Try<A>`          | Zero or one    | `AffinePath`     | `AffinePath`        | `Affines.trySuccess()`           |
| `Validated<E, A>` | Zero or one    | `AffinePath`     | `AffinePath`        | `Affines.validatedValid()`       |
| `Maybe<A>`        | Zero or one    | `AffinePath`     | `AffinePath`        | `Affines.just()`                 |
| `Optional<A>`     | Zero or one    | `AffinePath`     | `AffinePath`        | `.some()` (built-in)             |
| `Map<K, V>`       | Zero or more   | `TraversalPath`  | **`FocusPath`** ^1  | `EachInstances.mapValuesEach()`  |
| `T[]` (arrays)    | Zero or more   | `TraversalPath`  | **`FocusPath`** ^1  | `EachInstances.arrayEach()`      |
| `List<A>`         | Zero or more   | `TraversalPath`  | `TraversalPath`     | `.each()` (built-in)            |
| `Set<A>`          | Zero or more   | `TraversalPath`  | `TraversalPath`     | `.each()` (built-in)            |

^1 SPI `ZERO_OR_MORE` types return `FocusPath` from static Focus methods for backwards compatibility. Call `.each(eachInstance)` to widen manually.

## Eclipse Collections

| Container                | Navigator Path   | Static Focus Method | Optic Used                                                      |
|--------------------------|------------------|---------------------|-----------------------------------------------------------------|
| `ImmutableList<A>`       | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Lists.immutable.ofAll(list))`   |
| `MutableList<A>`         | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Lists.mutable.ofAll(list))`     |
| `ImmutableSet<A>`        | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Sets.immutable.ofAll(list))`    |
| `MutableSet<A>`          | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Sets.mutable.ofAll(list))`      |
| `ImmutableBag<A>`        | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Bags.immutable.ofAll(list))`    |
| `MutableBag<A>`          | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Bags.mutable.ofAll(list))`      |
| `ImmutableSortedSet<A>`  | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> SortedSets.immutable.ofAll(list))` |
| `MutableSortedSet<A>`    | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> SortedSets.mutable.ofAll(list))` |

All Eclipse Collections types have cardinality `ZERO_OR_MORE`.

## Guava, Vavr, and Apache Commons

| Container                          | Library        | Navigator Path   | Static Focus Method | Optic Used                                              |
|------------------------------------|----------------|------------------|---------------------|---------------------------------------------------------|
| `ImmutableList<A>`                 | Guava          | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(ImmutableList::copyOf)`          |
| `ImmutableSet<A>`                  | Guava          | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(ImmutableSet::copyOf)`           |
| `io.vavr.collection.List<A>`      | Vavr           | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> List.ofAll(list))`       |
| `io.vavr.collection.Set<A>`       | Vavr           | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> HashSet.ofAll(list))`    |
| `HashBag<A>`                       | Apache Commons | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(HashBag::new)`                   |
| `UnmodifiableList<A>`              | Apache Commons | `TraversalPath`  | **`FocusPath`** ^1  | `fromIterableCollecting(UnmodifiableList::new)`           |

All third-party types have cardinality `ZERO_OR_MORE` and use `EachInstances.fromIterableCollecting(collector)`.

## Generated Code Examples

### Required field -> FocusPath
```java
public static FocusPath<Employee, String> name() {
    return FocusPath.of(EmployeeLenses.name());
}
```

### Optional<T> -> AffinePath (auto-unwrap)
```java
public static AffinePath<Employee, String> email() {
    return FocusPath.of(EmployeeLenses.email()).some();
}
```

### @Nullable -> AffinePath (null handling)
```java
public static AffinePath<Employee, String> nickname() {
    return FocusPath.of(EmployeeLenses.nickname()).nullable();
}
```

### List<T> -> TraversalPath (element traversal)
```java
public static TraversalPath<Employee, Skill> skills() {
    return FocusPath.of(EmployeeLenses.skills()).each();
}
```

### List<T> indexed access -> AffinePath
```java
public static AffinePath<Employee, Skill> skill(int index) {
    return FocusPath.of(EmployeeLenses.skills()).at(index);
}
```

### Either<L, R> -> AffinePath (SPI widening)
```java
public static AffinePath<Employee, Integer> timeout() {
    return FocusPath.of(EmployeeLenses.timeout()).some(Affines.eitherRight());
}
```

### Map<K, V> -> TraversalPath (SPI widening)
```java
public static TraversalPath<Employee, Integer> scores() {
    return FocusPath.of(EmployeeLenses.scores()).each(EachInstances.mapValuesEach());
}
```

## Nested Container Widening

Patterns like `Optional<List<String>>` or `Either<E, Map<K, V>>` are detected automatically. The processor generates composed widening chains (e.g., `.some().each()`).

## ZERO_OR_MORE Manual Widening

For SPI `ZERO_OR_MORE` types, static Focus methods return `FocusPath`. Widen manually:

```java
// Static method returns FocusPath<AssetClass, ImmutableList<Position>>
var positions = AssetClassFocus.positions();

// Manually widen to TraversalPath
TraversalPath<AssetClass, Position> traversal = positions.each(
    EachInstances.fromIterableCollecting(list -> Lists.immutable.ofAll(list)));
```

Navigator generation handles `ZERO_OR_MORE` automatically -- navigator methods return `TraversalPath` without manual widening.

## Registering Custom Container Types (TraversableGenerator SPI)

### Step 1: Implement the SPI

```java
public class ResultGenerator extends BaseTraversableGenerator {

    @Override
    public String supportedTypeName() {
        return "com.example.Result";
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_ONE;  // holds zero or one success value
    }

    @Override
    public int getFocusTypeArgumentIndex() {
        return 1;  // Result<E, A> focuses on A (index 1)
    }

    @Override
    public String generateOpticExpression() {
        return "ResultAffines.success()";  // Java expression returning an Affine
    }

    @Override
    public Set<String> getRequiredImports() {
        return Set.of("com.example.optics.ResultAffines");
    }
}
```

### Step 2: Register via META-INF/services

File: `src/main/resources/META-INF/services/org.higherkindedj.optics.processing.spi.TraversableGenerator`

```
com.example.optics.ResultGenerator
```

### Step 3: Module system (if JPMS)

```java
module com.example.optics {
    requires org.higherkindedj.processor;
    provides org.higherkindedj.optics.processing.spi.TraversableGenerator
        with com.example.optics.ResultGenerator;
}
```

Once registered, `@GenerateFocus` records with `Result<E, A>` fields automatically generate an `AffinePath` calling `.some(ResultAffines.success())`.
