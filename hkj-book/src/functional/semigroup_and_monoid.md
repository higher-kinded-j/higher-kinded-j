# Semigroup and Monoid: 
## Foundational Type Classes 🧮

~~~admonish info title="What You'll Learn"
- The fundamental building blocks for combining data: Semigroup and Monoid
- How associative operations enable parallel and sequential data processing
- Using Monoids for error accumulation in validation scenarios
- Practical applications with String concatenation, integer addition, and boolean operations
- Advanced Monoid operations: combining collections, repeated application, and identity testing
- Working with numeric types: Long and Double monoid instances
- Optional-based monoids for data aggregation: first, last, maximum, and minimum
- How these abstractions power Foldable operations and validation workflows
~~~

In functional programming, we often use **type classes** to define common behaviours that can be applied to a wide range of data types. These act as interfaces that allow us to write more abstract and reusable code. In `higher-kinded-j`, we provide a number of these type classes to enable powerful functional patterns.

Here we will cover two foundational type classes: `Semigroup` and `Monoid`. Understanding these will give you a solid foundation for many of the more advanced concepts in the library.

---

## **`Semigroup<A>`**

A **`Semigroup`** is one of the simplest and most fundamental type classes. It provides a blueprint for types that have a single, associative way of being combined.

### What is it?

A `Semigroup` is a type class for any data type that has a `combine` operation. This operation takes two values of the same type and merges them into a single value of that type. The only rule is that this operation must be **associative**.

This means that for any values `a`, `b`, and `c`:

`(a.combine(b)).combine(c)` must be equal to `a.combine(b.combine(c))`

The interface for `Semigroup` in `hkj-api` is as follows:


``` java 
public interface Semigroup<A> {
    A combine(A a1, A a2);
}
```

### Common Instances: The `Semigroups` Utility

To make working with `Semigroup` easier, `higher-kinded-j` provides a `Semigroups` utility interface with static factory methods for common instances.


``` java
// Get a Semigroup for concatenating Strings
Semigroup<String> stringConcat = Semigroups.string();

// Get a Semigroup for concatenating Strings with a delimiter
Semigroup<String> stringConcatDelimited = Semigroups.string(", ");

// Get a Semigroup for concatenating Lists
Semigroup<List<Integer>> listConcat = Semigroups.list();
```

### Where is it used in `higher-kinded-j`?

The primary and most powerful use case for `Semigroup` in this library is to enable **error accumulation** with the **`Validated`** data type.

When you use the `Applicative` instance for `Validated`, you must provide a `Semigroup` for the error type. This tells the applicative how to combine errors when multiple invalid computations occur.

**Example: Accumulating Validation Errors**


``` java
// Create an applicative for Validated that accumulates String errors by joining them.
Applicative<Validated.Witness<String>> applicative =
    ValidatedMonad.instance(Semigroups.string("; "));

// Two invalid results
Validated<String, Integer> invalid1 = Validated.invalid("Field A is empty");
Validated<String, Integer> invalid2 = Validated.invalid("Field B is not a number");

// Combine them using the applicative's map2 method
Kind<Validated.Witness<String>, Integer> result =
    applicative.map2(
        VALIDATED.widen(invalid1),
        VALIDATED.widen(invalid2),
        (val1, val2) -> val1 + val2
    );

// The errors are combined using our Semigroup
// Result: Invalid("Field A is empty; Field B is not a number")
System.out.println(VALIDATED.narrow(result));
```

---

## **`Monoid<A>`**

A **`Monoid`** is a `Semigroup` with a special "identity" or "empty" element. This makes it even more powerful, as it provides a way to have a "starting" or "default" value.

### What is it?

A `Monoid` is a type class for any data type that has an associative `combine` operation (from `Semigroup`) and an `empty` value. This `empty` value is a special element that, when combined with any other value, returns that other value.

This is known as the **identity law**. For any value `a`:

`a.combine(empty())` must be equal to `a``empty().combine(a)` must be equal to `a`

The interface for `Monoid` in `hkj-api` extends `Semigroup`:


``` java 
public interface Monoid<A> extends Semigroup<A> {
    A empty();
}
```

### Common Instances: The `Monoids` Utility

Similar to `Semigroups`, the library provides a `Monoids` utility interface for creating common instances.


``` java
// Get a Monoid for integer addition (empty = 0)
Monoid<Integer> intAddition = Monoids.integerAddition();

// Get a Monoid for String concatenation (empty = "")
Monoid<String> stringMonoid = Monoids.string();

// Get a Monoid for boolean AND (empty = true)
Monoid<Boolean> booleanAnd = Monoids.booleanAnd();
```

### Where it is used in `higher-kinded-j`

A `Monoid` is essential for **folding** (or reducing) a data structure. The `empty` element provides a safe starting value, which means you can correctly fold a collection that might be empty.

This is formalised in the **`Foldable`** typeclass, which has a `foldMap` method. This method maps every element in a structure to a monoidal type and then combines all the results.

**Example: Using `foldMap` with different Monoids**


``` java 
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
Kind<ListKind.Witness, Integer> numbersKind = LIST.widen(numbers);

// 1. Sum the list using the integer addition monoid
Integer sum = ListTraverse.INSTANCE.foldMap(
    Monoids.integerAddition(),
    Function.identity(),
    numbersKind
); // Result: 15

// 2. Concatenate the numbers as strings
String concatenated = ListTraverse.INSTANCE.foldMap(
    Monoids.string(),
    String::valueOf,
    numbersKind
); // Result: "12345"
```

---

## **Advanced Monoid Operations**

The `Monoid` interface provides several powerful default methods that build upon the basic `combine` and `empty` operations. These methods handle common aggregation patterns and make working with collections much more convenient.

### `combineAll`: Aggregating Collections

The `combineAll` method takes an iterable collection and combines all its elements using the monoid's operation. If the collection is empty, it returns the identity element.


``` java
Monoid<Integer> sum = Monoids.integerAddition();
List<Integer> salesData = List.of(120, 450, 380, 290);

Integer totalSales = sum.combineAll(salesData);
// Result: 1240

// Works safely with empty collections
Integer emptyTotal = sum.combineAll(Collections.emptyList());
// Result: 0 (the empty value)
```

This is particularly useful for batch processing scenarios where you need to aggregate data from multiple sources:


``` java
// Combining log messages
Monoid<String> logMonoid = Monoids.string();
List<String> logMessages = loadLogMessages();
String combinedLog = logMonoid.combineAll(logMessages);

// Merging configuration sets
Monoid<Set<String>> configMonoid = Monoids.set();
List<Set<String>> featureFlags = List.of(
    Set.of("feature-a", "feature-b"),
    Set.of("feature-b", "feature-c"),
    Set.of("feature-d")
);
Set<String> allFlags = configMonoid.combineAll(featureFlags);
// Result: ["feature-a", "feature-b", "feature-c", "feature-d"]
```

### `combineN`: Repeated Application

The `combineN` method combines a value with itself `n` times. This is useful for scenarios where you need to apply the same value repeatedly:


``` java
Monoid<Integer> product = Monoids.integerMultiplication();

// Calculate 2^5 using multiplication monoid
Integer result = product.combineN(2, 5);
// Result: 32 (2 * 2 * 2 * 2 * 2)

// Repeat a string pattern
Monoid<String> stringMonoid = Monoids.string();
String border = stringMonoid.combineN("=", 50);
// Result: "=================================================="

// Build a list with repeated elements
Monoid<List<String>> listMonoid = Monoids.list();
List<String> repeated = listMonoid.combineN(List.of("item"), 3);
// Result: ["item", "item", "item"]
```

**Special cases:**
- When `n = 0`, returns the empty value
- When `n = 1`, returns the value unchanged
- When `n < 0`, throws `IllegalArgumentException`

### `isEmpty`: Identity Testing

The `isEmpty` method tests whether a given value equals the identity element of the monoid:


``` java
Monoid<Integer> sum = Monoids.integerAddition();
Monoid<Integer> product = Monoids.integerMultiplication();

sum.isEmpty(0);      // true (0 is the identity for addition)
sum.isEmpty(5);      // false

product.isEmpty(1);  // true (1 is the identity for multiplication)
product.isEmpty(0);  // false

Monoid<String> stringMonoid = Monoids.string();
stringMonoid.isEmpty("");     // true
stringMonoid.isEmpty("text"); // false
```

This is particularly useful for optimisation and conditional logic:


``` java
public void processIfNotEmpty(Monoid<String> monoid, String value) {
    if (!monoid.isEmpty(value)) {
        // Only process non-empty values
        performExpensiveOperation(value);
    }
}
```

---

## **Working with Numeric Types**

The `Monoids` utility provides comprehensive support for numeric operations beyond just `Integer`. This is particularly valuable for financial calculations, statistical operations, and scientific computing.

### Long Monoids

For working with large numeric values or high-precision calculations:


``` java
// Long addition for counting large quantities
Monoid<Long> longSum = Monoids.longAddition();
List<Long> userCounts = List.of(1_500_000L, 2_300_000L, 890_000L);
Long totalUsers = longSum.combineAll(userCounts);
// Result: 4,690,000

// Long multiplication for compound calculations
Monoid<Long> longProduct = Monoids.longMultiplication();
Long compound = longProduct.combineN(2L, 20);
// Result: 1,048,576 (2^20)
```

### Double Monoids

For floating-point arithmetic and statistical computations:


``` java
// Double addition for financial calculations
Monoid<Double> dollarSum = Monoids.doubleAddition();
List<Double> expenses = List.of(49.99, 129.50, 89.99);
Double totalExpenses = dollarSum.combineAll(expenses);
// Result: 269.48

// Double multiplication for compound interest
Monoid<Double> growth = Monoids.doubleMultiplication();
Double interestRate = 1.05; // 5% per year
Double compoundGrowth = growth.combineN(interestRate, 10);
// Result: ≈1.629 (after 10 years)
```

**Practical Example: Statistical Calculations**


``` java
public class Statistics {

    public static double calculateMean(List<Double> values) {
        if (values.isEmpty()) return 0.0;

        Monoid<Double> sum = Monoids.doubleAddition();
        Double total = sum.combineAll(values);
        return total / values.size();
    }

    public static double calculateProduct(List<Double> factors) {
        Monoid<Double> product = Monoids.doubleMultiplication();
        return product.combineAll(factors);
    }
}

// Usage
List<Double> measurements = List.of(23.5, 24.1, 23.8, 24.3);
double average = Statistics.calculateMean(measurements);
// Result: 23.925
```

---

## **Optional Monoids for Data Aggregation**

One of the most powerful features of the `Monoids` utility is its support for `Optional`-based aggregation. These monoids elegantly handle the common pattern of finding the "best" value from a collection of optional results.

### `firstOptional` and `lastOptional`

These monoids select the first or last non-empty optional value, making them perfect for fallback chains and priority-based selection:


``` java
Monoid<Optional<String>> first = Monoids.firstOptional();
Monoid<Optional<String>> last = Monoids.lastOptional();

List<Optional<String>> configs = List.of(
    Optional.empty(),           // Missing config
    Optional.of("default.conf"), // Found!
    Optional.of("user.conf")    // Also found
);

// Get first available configuration
Optional<String> primaryConfig = first.combineAll(configs);
// Result: Optional["default.conf"]

// Get last available configuration
Optional<String> latestConfig = last.combineAll(configs);
// Result: Optional["user.conf"]
```

**Practical Example: Configuration Fallback Chain**


``` java
public class ConfigLoader {

    public Optional<Config> loadConfig() {
        Monoid<Optional<Config>> firstAvailable = Monoids.firstOptional();

        return firstAvailable.combineAll(List.of(
            loadFromEnvironment(),     // Try environment variables first
            loadFromUserHome(),        // Then user's home directory
            loadFromWorkingDir(),      // Then current directory
            loadDefaultConfig()        // Finally, use defaults
        ));
    }

    private Optional<Config> loadFromEnvironment() {
        return Optional.ofNullable(System.getenv("APP_CONFIG"))
            .map(this::parseConfig);
    }

    private Optional<Config> loadFromUserHome() {
        Path userConfig = Paths.get(System.getProperty("user.home"), ".apprc");
        return Files.exists(userConfig)
            ? Optional.of(parseConfigFile(userConfig))
            : Optional.empty();
    }

    // ... other loaders
}
```

### `maximum` and `minimum`

These monoids find the maximum or minimum value from a collection of optional values. They work with any `Comparable` type or accept a custom `Comparator`:


``` java
Monoid<Optional<Integer>> max = Monoids.maximum();
Monoid<Optional<Integer>> min = Monoids.minimum();

List<Optional<Integer>> scores = List.of(
    Optional.of(85),
    Optional.empty(),      // Missing data
    Optional.of(92),
    Optional.of(78),
    Optional.empty()
);

Optional<Integer> highestScore = max.combineAll(scores);
// Result: Optional[92]

Optional<Integer> lowestScore = min.combineAll(scores);
// Result: Optional[78]
```

**Using Custom Comparators**

For more complex types, you can provide a custom comparator:


``` java
public record Product(String name, double price) {}

// Find most expensive product
Monoid<Optional<Product>> mostExpensive =
    Monoids.maximum(Comparator.comparing(Product::price));

List<Optional<Product>> products = List.of(
    Optional.of(new Product("Widget", 29.99)),
    Optional.empty(),
    Optional.of(new Product("Gadget", 49.99)),
    Optional.of(new Product("Gizmo", 19.99))
);

Optional<Product> priciest = mostExpensive.combineAll(products);
// Result: Optional[Product("Gadget", 49.99)]

// Find product with shortest name
Monoid<Optional<Product>> shortestName =
    Monoids.minimum(Comparator.comparing(p -> p.name().length()));

Optional<Product> shortest = shortestName.combineAll(products);
// Result: Optional[Product("Gizmo", 19.99)]
```

**Practical Example: Finding Best Offers**


``` java
public class PriceComparison {

    public record Offer(String vendor, BigDecimal price, boolean inStock)
        implements Comparable<Offer> {

        @Override
        public int compareTo(Offer other) {
            return this.price.compareTo(other.price);
        }
    }

    public Optional<Offer> findBestOffer(List<String> vendors, String productId) {
        Monoid<Optional<Offer>> cheapest = Monoids.minimum();

        List<Optional<Offer>> offers = vendors.stream()
            .map(vendor -> fetchOffer(vendor, productId))
            .filter(opt -> opt.map(Offer::inStock).orElse(false)) // Only in-stock items
            .collect(Collectors.toList());

        return cheapest.combineAll(offers);
    }

    private Optional<Offer> fetchOffer(String vendor, String productId) {
        // API call to get offer from vendor
        // Returns Optional.empty() if unavailable
    }
}
```

**When Both Optionals are Empty**

It's worth noting that these monoids handle empty collections gracefully:


``` java
Monoid<Optional<Integer>> max = Monoids.maximum();

List<Optional<Integer>> allEmpty = List.of(
    Optional.empty(),
    Optional.empty()
);

Optional<Integer> result = max.combineAll(allEmpty);
// Result: Optional.empty()

// Also works with empty list
Optional<Integer> emptyResult = max.combineAll(Collections.emptyList());
// Result: Optional.empty()
```

This makes them perfect for aggregation pipelines where you're not certain data will be present, but you want to find the best available value if any exists.

---

## **Conclusion**

Semigroups and Monoids are deceptively simple abstractions that unlock powerful patterns for data combination and aggregation. By understanding these type classes, you gain:

- **Composability**: Build complex aggregations from simple, reusable pieces
- **Type Safety**: Let the compiler ensure your combinations are valid
- **Flexibility**: Swap monoids to get different behaviours from the same code
- **Elegance**: Express data aggregation intent clearly and concisely

The new utility methods (`combineAll`, `combineN`, `isEmpty`) and expanded instance library (numeric types, Optional-based aggregations) make these abstractions even more practical for everyday Java development.

**Further Reading:**
- [Foldable and Traverse](foldable_and_traverse.md) - See how Monoids power folding operations
- [Applicative](applicative.md) - Learn how Semigroups enable error accumulation with Validated
- [Java Optional Documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html)
