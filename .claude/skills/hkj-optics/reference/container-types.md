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
<!-- verify -->
```java
public static FocusPath<Employee, String> name() {
    return FocusPath.of(EmployeeLenses.name());
}
```

### Optional<T> -> AffinePath (auto-unwrap)
<!-- verify -->
```java
public static AffinePath<Employee, String> email() {
    return FocusPath.of(EmployeeLenses.email()).some();
}
```

### @Nullable -> AffinePath (null handling)
<!-- verify -->
```java
public static AffinePath<Employee, String> nickname() {
    return FocusPath.of(EmployeeLenses.nickname()).nullable();
}
```

Any of the six recognised `@Nullable` annotations does this, wherever that annotation's own `@Target` puts it (JSpecify's `TYPE_USE` on the component's type, JetBrains', AndroidX's and SpotBugs' on the accessor, JSR-305's and Jakarta's on the component itself). A container decides its own widening, so `@Nullable List<T>` is `.each()` and `@Nullable Optional<T>` is `.some()`; and position counts as Java defines it, so `String @Nullable []` is a nullable array while `@Nullable String[]` and `List<@Nullable String>` annotate the elements.

### List<T> -> TraversalPath (element traversal)
<!-- verify -->
```java
public static TraversalPath<Employee, Skill> skills() {
    return FocusPath.of(EmployeeLenses.skills()).each();
}
```

### List<T> indexed access -> AffinePath
<!-- verify -->
```java
public static AffinePath<Employee, Skill> skill(int index) {
    return FocusPath.of(EmployeeLenses.skills()).at(index);
}
```

### Either<L, R> -> AffinePath (SPI widening)
<!-- verify -->
```java
public static AffinePath<Employee, Integer> timeout() {
    return FocusPath.of(EmployeeLenses.timeout()).some(Affines.eitherRight());
}
```

### Map<K, V> -> TraversalPath (SPI widening)
<!-- verify -->
```java
public static TraversalPath<Employee, Integer> scores() {
    return FocusPath.of(EmployeeLenses.scores()).each(EachInstances.mapValuesEach());
}
```

## Nested Container Widening

Patterns like `Optional<List<String>>` or `Either<E, Map<K, V>>` are detected automatically. The processor generates composed widening chains (e.g., `.some().each()`).

## Raw and Wildcard Container Type Arguments

An SPI container widens by receiving an optic instance — `.some(Affines.eitherRight())`, `.each(EachInstances.mapValuesEach())` — whose own type arguments javac infers from the field type. A raw container offers none to infer from, and a wildcard has no ground instantiation, so `@GenerateFocus` rejects the component rather than emitting a call that cannot compile.

```java
// Rejected: no Affine can be denoted for a wildcard type argument
@GenerateFocus
record Holder(Either<String, ? extends Leaf> boundedEither) {}

// Accepted
@GenerateFocus
record Holder(Either<String, Leaf> either) {}
```

- Both of the container's own type arguments count, focused or not: `Either<?, Leaf>` is rejected too.
- A wildcard nested *inside* an argument is fine: `Either<String, List<? extends Leaf>>` has a ground instantiation and widens to `.some(Affines.eitherRight()).each()`.
- The built-in `Optional`, `List` and `Set` widenings take a wildcard without complaint, because `.some()` and `.each()` are methods with a free type variable and no optic argument to unify.
- A `ZERO_OR_MORE` SPI container is rejected only when something actually widens it: `widenCollections = true`, or a navigator reaching a navigable element inside it. At the default settings it stays a `FocusPath`, and so does everything beneath it — `Map<String, Either<String, ? extends Leaf>>` compiles by default, because the un-widened `Map` means the `Either` is never asked for an optic.
- A custom generator that names no optic expression is exempt: it widens through `.nullable()` or `.each()`, whose free type variable takes a raw or wildcard argument without complaint. Every generator shipped with HKJ names one.
- This is a rule about composing an optic instance, so it is `@GenerateFocus`'s alone. `@GenerateTraversals` reads the same component and emits a `Traversal` over the type the wildcard stands for: `? extends T` is `T`, and `?` or `? super T` is `Object`.

## ZERO_OR_MORE Manual Widening

For SPI `ZERO_OR_MORE` types, static Focus methods return `FocusPath`. Widen manually:

<!-- verify -->
```java
// Static method returns FocusPath<AssetClass, ImmutableList<Position>>
var positions = AssetClassFocus.positions();

// Manually widen to TraversalPath
TraversalPath<AssetClass, Position> traversal = positions.each(
    EachInstances.fromIterableCollecting(list -> Lists.immutable.ofAll(list)));
```

A navigator method reports the same path type as the static method for the same component, so
widening it is the same decision either way. The exception is a container whose element is
itself a `@GenerateFocus` record: that one is always stepped into, because the navigator it
hands back has to reach the element.

## Registering Custom Container Types (TraversableGenerator SPI)

### Step 1: Implement the SPI

<!-- verify -->
```java
public class ResultGenerator extends BaseTraversableGenerator {

    // Which type this generator claims. The SPI matches on the TypeMirror, not on a name.
    @Override
    public boolean supports(TypeMirror type) {
        return type instanceof DeclaredType declared
            && declared.asElement().toString().equals("com.example.Result");
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

    // The ONE method you must implement: everything above has a default. It emits the body of the
    // effectful modify, so this is where the container is actually traversed.
    @Override
    public CodeBlock generateModifyF(
            RecordComponentElement component,
            ClassName recordClassName,
            List<? extends RecordComponentElement> allComponents) {
        String name = component.getSimpleName().toString();
        return CodeBlock.builder()
            .addStatement("final var result = source.$L()", name)
            .addStatement("return applicative.map(v -> $L, f.apply(result.get()))",
                generateConstructorArgs(name, "com.example.Result.ok(v)", allComponents))
            .build();
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
