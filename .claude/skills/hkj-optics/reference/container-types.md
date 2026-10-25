# Container Type Support

## Cardinality Model

Every container type has a **cardinality** that determines the generated Focus path type:

| Cardinality      | Meaning               | Generated Path   | Example Types                        |
|------------------|-----------------------|------------------|--------------------------------------|
| `ZERO_OR_ONE`    | At most one value     | `AffinePath`     | `Either`, `Try`, `Validated`, `Maybe`, `Optional` |
| `ZERO_OR_MORE`   | Collection of values  | `TraversalPath`  | `List`, `Set`, `Map`, arrays, Eclipse/Guava/Vavr collections |

## HKJ and JDK Types

| Container         | Cardinality    | Static Focus Method | Optic Used                       |
|-------------------|----------------|---------------------|----------------------------------|
| `Either<L, R>`    | Zero or one    | `AffinePath`        | `Affines.eitherRight()`          |
| `Try<A>`          | Zero or one    | `AffinePath`        | `Affines.trySuccess()`           |
| `Validated<E, A>` | Zero or one    | `AffinePath`        | `Affines.validatedValid()`       |
| `Maybe<A>`        | Zero or one    | `AffinePath`        | `Affines.just()` (built-in)      |
| `Optional<A>`     | Zero or one    | `AffinePath`        | `FocusPaths.optionalSome()` (built-in) |
| `Map<K, V>`       | Zero or more   | **`FocusPath`** ^1  | `EachInstances.mapValuesEach()`  |
| `T[]` (arrays) ^2 | Zero or more   | **`FocusPath`** ^1  | `EachInstances.arrayEach()`      |
| `List<A>`         | Zero or more   | `TraversalPath`     | `EachInstances.listEach()` (built-in) |
| `Set<A>`          | Zero or more   | `TraversalPath`     | `EachInstances.setEach()` (built-in) |
| `Collection<A>`   | Zero or more   | `TraversalPath`     | `EachInstances.collectionEach()` (built-in) |

^1 SPI `ZERO_OR_MORE` types return `FocusPath` from static Focus methods for backwards compatibility. Set `@GenerateFocus(widenCollections = true)` to widen them all, name one in `widenFields` to widen just that field, or call `.each(eachInstance)` to widen manually.

A navigator's method returns the same path type as the static method for the same component: a navigator only adds navigation methods to that path. So a container of `@GenerateFocus` records gets a navigator over its elements wherever the method steps into them. An `Optional`, `Maybe`, `List`, `Set`, `Collection` or `Either` of one always does, nested or not, and so does a `Kind` the processor traverses; a `Map`, an array or a third-party collection does where the record widens it.

^2 An array of a reference type. A primitive array such as `int[]` stays a `FocusPath` under every setting: `arrayEach()` is `Each<A[], A>`, and no primitive is an `A`.

## Eclipse Collections

| Container                | Static Focus Method | Optic Used                                                      |
|--------------------------|---------------------|-----------------------------------------------------------------|
| `ImmutableList<A>`       | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Lists.immutable.ofAll(list))`   |
| `MutableList<A>`         | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Lists.mutable.ofAll(list))`     |
| `ImmutableSet<A>`        | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Sets.immutable.ofAll(list))`    |
| `MutableSet<A>`          | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Sets.mutable.ofAll(list))`      |
| `ImmutableBag<A>`        | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Bags.immutable.ofAll(list))`    |
| `MutableBag<A>`          | **`FocusPath`** ^1  | `fromIterableCollecting(list -> Bags.mutable.ofAll(list))`      |
| `ImmutableSortedSet<A>`  | **`FocusPath`** ^1  | `fromIterableCollecting(list -> SortedSets.immutable.ofAll(list))` |
| `MutableSortedSet<A>`    | **`FocusPath`** ^1  | `fromIterableCollecting(list -> SortedSets.mutable.ofAll(list))` |

All Eclipse Collections types have cardinality `ZERO_OR_MORE`.

## Guava, Vavr, and Apache Commons

| Container                          | Library        | Static Focus Method | Optic Used                                              |
|------------------------------------|----------------|---------------------|---------------------------------------------------------|
| `ImmutableList<A>`                 | Guava          | **`FocusPath`** ^1  | `fromIterableCollecting(ImmutableList::copyOf)`          |
| `ImmutableSet<A>`                  | Guava          | **`FocusPath`** ^1  | `fromIterableCollecting(ImmutableSet::copyOf)`           |
| `io.vavr.collection.List<A>`      | Vavr           | **`FocusPath`** ^1  | `fromIterableCollecting(list -> List.ofAll(list))`       |
| `io.vavr.collection.Set<A>`       | Vavr           | **`FocusPath`** ^1  | `fromIterableCollecting(list -> HashSet.ofAll(list))`    |
| `HashBag<A>`                       | Apache Commons | **`FocusPath`** ^1  | `fromIterableCollecting(HashBag::new)`                   |
| `UnmodifiableList<A>`              | Apache Commons | **`FocusPath`** ^1  | `fromIterableCollecting(UnmodifiableList::new)`           |

All third-party types have cardinality `ZERO_OR_MORE` and use `EachInstances.fromIterableCollecting(collector)`.

## Generated Code Examples

### Required field -> FocusPath
<!-- verify -->
```java
public static FocusPath<Contractor, String> name() {
    return FocusPath.of(ContractorLenses.name());
}
```

### Optional<T> -> AffinePath (auto-unwrap)
<!-- verify -->
```java
public static AffinePath<Contractor, String> email() {
    return FocusPath.of(ContractorLenses.email()).some(FocusPaths.optionalSome());
}
```

### @Nullable -> AffinePath (null handling)
<!-- verify -->
```java
public static AffinePath<Contractor, String> nickname() {
    return FocusPath.of(ContractorLenses.nickname()).nullable();
}
```

Any of the six recognised `@Nullable` annotations does this, wherever that annotation's own `@Target` puts it (JSpecify's `TYPE_USE` on the component's type, JetBrains', AndroidX's and SpotBugs' on the accessor, JSR-305's and Jakarta's on the component itself). A nullable container widens through `.nullable()` and then its own step, where it has one, so `@Nullable List<T>` is `.nullable().each(EachInstances.listEach())` and `@Nullable Optional<T>` is `.nullable().some(FocusPaths.optionalSome())`, and a null container has no focus. A nullable container inside another, as in `List<@Nullable List<T>>`, is ruled out the same way, and a navigator passes over a null element it would read. Position counts as Java defines it, so `String @Nullable []` is a nullable array while `@Nullable String[]` and `List<@Nullable String>` annotate the elements.

### List<T> -> TraversalPath (element traversal)
<!-- verify -->
```java
public static TraversalPath<Contractor, Skill> skills() {
    return FocusPath.of(ContractorLenses.skills()).each(EachInstances.listEach());
}
```

### List<T> indexed access -> AffinePath
<!-- verify -->
```java
public static AffinePath<Contractor, Skill> skill(int index) {
    return FocusPath.of(ContractorLenses.skills()).via(FocusPaths.listAt(index));
}
```

### Either<L, R> -> AffinePath (SPI widening)
<!-- verify -->
```java
public static AffinePath<Contractor, Integer> timeout() {
    return FocusPath.of(ContractorLenses.timeout()).some(Affines.eitherRight());
}
```

### Map<K, V> -> TraversalPath (SPI widening)
<!-- verify -->
```java
public static TraversalPath<Contractor, Integer> scores() {
    return FocusPath.of(ContractorLenses.scores()).each(EachInstances.mapValuesEach());
}
```

## Nested Container Widening

Patterns like `Optional<List<String>>` or `Either<E, Map<K, V>>` are detected automatically. The processor generates composed widening chains (e.g., `.some(FocusPaths.optionalSome()).each(EachInstances.listEach())`).

## Raw and Wildcard Container Type Arguments

An SPI container widens by receiving an optic instance — `.some(Affines.eitherRight())`, `.each(EachInstances.mapValuesEach())` — whose own type arguments javac infers from the field type. A raw container offers none to infer from, and a wildcard has no ground instantiation, so `@GenerateFocus` rejects the component rather than emitting a call that cannot compile.

```java
// Rejected: no Affine can be denoted for a wildcard type argument
@GenerateFocus
record Holder(Either<String, ? extends Widget> boundedEither) {}

// Accepted
@GenerateFocus
record Holder(Either<String, Widget> either) {}
```

- Both of the container's own type arguments count, focused or not: `Either<?, Widget>` is rejected too.
- A wildcard nested *inside* an argument is fine: `Either<String, List<? extends Widget>>` has a ground instantiation and widens to `.some(Affines.eitherRight()).each(EachInstances.listEach())`, the `Each` cast to one over `List<? extends Widget>`.
- The five built-in widenings, `Optional`, `Maybe`, `List`, `Set` and `Collection`, take a raw or wildcard argument without complaint: the processor names `FocusPaths.optionalSome()`, `Affines.just()` or the container's own `Each` for the element type and casts it to the type the field declares. So `Maybe<?>` widens to an `AffinePath` onto `Object`, `Set<? extends Widget>` to a `TraversalPath` onto `Widget`, and a raw `Set` to one onto `Object`.
- A `ZERO_OR_MORE` SPI container is rejected only when something actually widens it: `widenCollections = true`, or `widenFields` naming the field. At the default settings it stays a `FocusPath`, and so does everything beneath it: `Map<String, Either<String, ? extends Widget>>` compiles by default, because the un-widened `Map` means the `Either` is never asked for an optic.
- A custom generator that names no optic expression is exempt, because it widens nothing: the path stops at its container, as if no generator supported it. Every generator HKJ ships for a type the SPI widens names one.
- This is a rule about composing an optic instance, so it is `@GenerateFocus`'s alone. `@GenerateTraversals` reads the same component and emits a `Traversal` over the type the wildcard stands for: `? extends T` is `T`, and `?` or `? super T` is `Object`.

## ZERO_OR_MORE Manual Widening

For SPI `ZERO_OR_MORE` types, static Focus methods return `FocusPath`. Widen manually:

<!-- verify -->
```java
// Static method returns FocusPath<Allocation, ImmutableList<Holding>>
var holdings = AllocationFocus.holdings();

// Manually widen to TraversalPath
TraversalPath<Allocation, Holding> traversal = holdings.each(
    EachInstances.fromIterableCollecting(list -> Lists.immutable.ofAll(list)));
```

A navigator method reports the same path type as the static method for the same component, so
widening it is the same decision either way. To navigate into the elements of a container of
`@GenerateFocus` records, widen it, with `widenFields = "holdings"` for that one field; the method
then returns a navigator over the elements. A generic element gets a navigator when the container
names a type for each of its parameters, so a widened `Map<String, Box<String>>` gets one and
`Map<String, Box<?>>` does not.

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
