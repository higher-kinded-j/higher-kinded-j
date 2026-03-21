# Focus DSL: Custom Containers and Code Generation

~~~admonish info title="What You'll Learn"
- What the annotation processor generates for each field type
- How container cardinality (`ZERO_OR_ONE` vs `ZERO_OR_MORE`) determines the generated path type
- The full table of supported container types across HKJ, JDK, Eclipse Collections, Guava, Vavr, and Apache Commons
- How to register your own container types via the `TraversableGenerator` SPI
~~~

~~~admonish title="Hands On Practice"
[Tutorial20_ContainerNavigation.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial20_ContainerNavigation.java) (4 exercises, ~10 minutes)
~~~

---

## Generated Class Structure

For a record like:

```java
@GenerateLenses
@GenerateFocus
record Employee(
    String name,
    int age,
    Optional<String> email,
    @Nullable String nickname,
    List<Skill> skills
) {}
```

The processor generates:

```java
@Generated
public final class EmployeeFocus {
    private EmployeeFocus() {}

    // Required fields -> FocusPath
    public static FocusPath<Employee, String> name() {
        return FocusPath.of(EmployeeLenses.name());
    }

    public static FocusPath<Employee, Integer> age() {
        return FocusPath.of(EmployeeLenses.age());
    }

    // Optional<T> field -> AffinePath (automatically unwraps with .some())
    public static AffinePath<Employee, String> email() {
        return FocusPath.of(EmployeeLenses.email()).some();
    }

    // @Nullable field -> AffinePath (automatically handles null with .nullable())
    public static AffinePath<Employee, String> nickname() {
        return FocusPath.of(EmployeeLenses.nickname()).nullable();
    }

    // List<T> field -> TraversalPath (traverses elements)
    public static TraversalPath<Employee, Skill> skills() {
        return FocusPath.of(EmployeeLenses.skills()).each();
    }

    // Indexed access to List<T> -> AffinePath
    public static AffinePath<Employee, Skill> skill(int index) {
        return FocusPath.of(EmployeeLenses.skills()).at(index);
    }

    // Either<String, Integer> field -> AffinePath (SPI widening with .some(Affine))
    public static AffinePath<Employee, Integer> timeout() {
        return FocusPath.of(EmployeeLenses.timeout()).some(Affines.eitherRight());
    }

    // Map<String, Integer> field -> TraversalPath (SPI widening with .each(Each))
    public static TraversalPath<Employee, Integer> scores() {
        return FocusPath.of(EmployeeLenses.scores()).each(EachInstances.mapValuesEach());
    }
}
```

---

## Custom Container Types

The Focus DSL automatically recognises `Optional`, `List`, and `Set` fields. But what about `Either`, `Try`, `Map`, or your own container types?

Every container type holds its values in one of two ways: it either wraps *at most one* value (like `Either`, which holds a success *or* a failure), or it holds *zero or more* values (like `Map`, which holds a collection of entries). The Focus DSL calls this the container's **cardinality**, and it determines the generated path type:

- **Zero or one** (e.g., `Either<L, R>`, `Try<A>`, `Validated<E, A>`) produces an `AffinePath`; the value may or may not be present.
- **Zero or more** (e.g., `Map<K, V>`, `T[]`) produces a `TraversalPath`; there may be many values to iterate over.

The `TraversableGenerator` SPI lets any container type participate in this path widening. When `@GenerateFocus` encounters a registered container field, it generates the correct `AffinePath` or `TraversalPath` automatically, with no manual composition needed.

Nested container patterns such as `Optional<List<String>>` or `Either<E, Map<K, V>>` are also detected automatically. The processor generates composed widening chains (e.g., `.some().each()`) and selects the correct return type using the widening lattice. See [Nested Container Widening](focus_navigation.md#nested-container-widening) for details.

### How It Works

For a record with an `Either` field:

```java
@GenerateFocus
record ApiResponse(int status, Either<String, UserData> body) {}
```

The processor generates:

```java
// body() returns AffinePath, not FocusPath, because Either is a ZERO_OR_ONE container
public static AffinePath<ApiResponse, UserData> body() {
    return FocusPath.of(Lens.of(ApiResponse::body, ...)).some(Affines.eitherRight());
}
```

The `.some(Affines.eitherRight())` call composes an `Affine` that focuses on the `Right` value, widening the path from `FocusPath` to `AffinePath`. For `ZERO_OR_MORE` SPI types (like `Map`), the static Focus method returns `FocusPath` for backwards compatibility; users call `.each(EachInstances.mapValuesEach())` manually to widen to `TraversalPath`. Navigator methods, however, widen automatically.

### Supported Container Types

The tables below show both the **navigator path** (the return type when navigating through a navigator chain) and the **static Focus method** return type (the type returned by a top-level `XxxFocus.field()` call). These differ for `ZERO_OR_MORE` SPI types; see the note after the tables.

#### HKJ and JDK types

| Container | Cardinality | Navigator path | Static Focus method | Optic used |
|-----------|-------------|---------------|---------------------|------------|
| `Either<L, R>` | Zero or one | `AffinePath` | `AffinePath` | `Affines.eitherRight()` |
| `Try<A>` | Zero or one | `AffinePath` | `AffinePath` | `Affines.trySuccess()` |
| `Validated<E, A>` | Zero or one | `AffinePath` | `AffinePath` | `Affines.validatedValid()` |
| `Maybe<A>` | Zero or one | `AffinePath` | `AffinePath` | `Affines.just()` |
| `Optional<A>` | Zero or one | `AffinePath` | `AffinePath` | `.some()` (built-in) |
| `Map<K, V>` | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `EachInstances.mapValuesEach()` |
| `T[]` (arrays) | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `EachInstances.arrayEach()` |
| `List<A>` | Zero or more | `TraversalPath` | `TraversalPath` | `.each()` (built-in) |
| `Set<A>` | Zero or more | `TraversalPath` | `TraversalPath` | `.each()` (built-in) |

¹ SPI `ZERO_OR_MORE` types return `FocusPath` from static Focus methods for backwards compatibility. Call `.each(eachInstance)` to widen manually.

#### Eclipse Collections

| Container | Cardinality | Navigator path | Static Focus method | Optic used |
|-----------|-------------|---------------|---------------------|------------|
| `ImmutableList<A>` | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> Lists.immutable.ofAll(list))` |
| `MutableList<A>` | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> Lists.mutable.ofAll(list))` |
| `ImmutableSet<A>` | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> Sets.immutable.ofAll(list))` |
| `MutableSet<A>` | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> Sets.mutable.ofAll(list))` |
| `ImmutableBag<A>` | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> Bags.immutable.ofAll(list))` |
| `MutableBag<A>` | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> Bags.mutable.ofAll(list))` |
| `ImmutableSortedSet<A>` | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> SortedSets.immutable.ofAll(list))` |
| `MutableSortedSet<A>` | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> SortedSets.mutable.ofAll(list))` |

#### Guava, Vavr, and Apache Commons

| Container | Library | Cardinality | Navigator path | Static Focus method | Optic used |
|-----------|---------|-------------|---------------|---------------------|------------|
| `ImmutableList<A>` | Guava | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(ImmutableList::copyOf)` |
| `ImmutableSet<A>` | Guava | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(ImmutableSet::copyOf)` |
| `io.vavr.collection.List<A>` | Vavr | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> List.ofAll(list))` |
| `io.vavr.collection.Set<A>` | Vavr | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(list -> HashSet.ofAll(list))` |
| `HashBag<A>` | Apache Commons | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(HashBag::new)` |
| `UnmodifiableList<A>` | Apache Commons | Zero or more | `TraversalPath` | **`FocusPath`** ¹ | `fromIterableCollecting(UnmodifiableList::new)` |

All third-party generators use `EachInstances.fromIterableCollecting(collector)`, a generic factory that iterates the container, traverses elements with the applicative functor, and reconstructs the container via the provided collector function. No additional modules are needed; the user's project already has the third-party library on the classpath since it declared the container type in its record.

~~~admonish note title="ZERO_OR_MORE static method behaviour"
For `ZERO_OR_MORE` SPI types (all collection-like containers above), the static Focus method returns `FocusPath`, not `TraversalPath`. This preserves backwards compatibility. To traverse manually, call `.each(eachInstance)`:

```java
// Static method returns FocusPath<AssetClass, ImmutableList<Position>>
var positions = AssetClassFocus.positions();

// Manually widen to TraversalPath
TraversalPath<AssetClass, Position> traversal = positions.each(
    EachInstances.fromIterableCollecting(list -> Lists.immutable.ofAll(list)));
```

Navigator generation handles `ZERO_OR_MORE` automatically; navigator methods for third-party collection fields return `TraversalPath` without manual widening.
~~~

### Cross-Ecosystem Navigation

Real-world Java projects often mix collection libraries: JDK collections for standard code, Eclipse Collections for high-performance immutable data, HKJ types (`Either`, `Try`, `Validated`) for typed error handling. The Focus DSL navigates across all of these with a single annotation, composing navigator chains that cross ecosystem boundaries transparently.

For a detailed walkthrough of cross-ecosystem navigation with a financial portfolio domain model, see the [Portfolio Risk Analysis](../examples/examples_portfolio_risk.md) example in the Examples Gallery.

### Registering Your Own Container Types

Third-party libraries can register their own container types by implementing `TraversableGenerator` and registering it via `META-INF/services`.

**Step 1: Implement the SPI interface**

```java
package com.example.optics;

import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import java.util.Set;

public class ResultGenerator extends BaseTraversableGenerator {

    @Override
    public String supportedTypeName() {
        return "com.example.Result";
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_ONE;  // Result holds zero or one success value
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

    // ... implement remaining methods from BaseTraversableGenerator
}
```

**Step 2: Register via `META-INF/services`**

Create the file `src/main/resources/META-INF/services/org.higherkindedj.optics.processing.spi.TraversableGenerator`:

```
com.example.optics.ResultGenerator
```

**Step 3: Module system configuration** (if using JPMS)

```java
module com.example.optics {
    requires org.higherkindedj.processor;
    provides org.higherkindedj.optics.processing.spi.TraversableGenerator
        with com.example.optics.ResultGenerator;
}
```

Once registered, any `@GenerateFocus` record with a `Result<E, A>` field will automatically generate an `AffinePath` that calls `.some(ResultAffines.success())`.

~~~admonish info title="Hands-On Learning"
Practice container type navigation in [Tutorial 20: Custom Container Navigation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial20_ContainerNavigation.java) (4 exercises, ~10 minutes).
~~~

~~~admonish tip title="See Also"
- [Traversal Generator Plugins](../tooling/generator_plugins.md) - Full SPI implementation guide
- [Portfolio Risk Analysis](../examples/examples_portfolio_risk.md) - Cross-ecosystem navigation example
~~~

---

**Previous:** [Type Class and Effect Integration](focus_effects.md)
**Next:** [Focus DSL Reference](focus_reference.md)
