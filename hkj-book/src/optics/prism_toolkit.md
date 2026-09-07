# Prism Toolkit

## _Convenience methods and common patterns with the `Prisms` utility_

~~~admonish info title="What You'll Learn"
- The full set of prism convenience methods on `Prism<S, A>`: direct composition, conditional updates, predicate-based queries, and bulk extraction.
- The factory methods on the `Prisms` utility class for the standard JDK and Higher-Kinded-J types: `Optional`, `Either`, `Maybe`, `Try`, list head/tail decomposition, and more.
~~~

This page is the lookup catalogue for the prism API. The conceptual introduction, mental models, and worked examples live in [Prisms](prisms.md); use this page when you already know what a prism is and need to find the right method.

---

## Prism Convenience Methods

### _Streamlined Operations for Common Patterns_

Whilst `getOptional()` and `build()` are the core operations, the `Prism` interface provides several convenience methods that make everyday tasks more ergonomic and expressive.

**Quick Reference:**

| Method | Purpose | Returns |
|--------|---------|---------|
| `matches(S source)` | Check if prism matches without extraction | `boolean` |
| `getOrElse(A default, S source)` | Extract value or return default | `A` |
| `mapOptional(Function<A, B> f, S source)` | Transform matched value | `Optional<B>` |
| `modify(Function<A, A> f, S source)` | Modify if matches, else return original | `S` |
| `modifyWhen(Predicate<A> p, Function<A, A> f, S source)` | Modify only when predicate satisfied | `S` |
| `setWhen(Predicate<A> p, A value, S source)` | Set only when predicate satisfied | `S` |
| `orElse(Prism<S, A> other)` | Try this prism, then fallback | `Prism<S, A>` |

### Type Checking with `matches()`

The `matches()` method provides a clean alternative to `getOptional(source).isPresent()`:

<!-- verify -->
```java
Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

// Clear, declarative type checking
if (stringPrism.matches(value)) {
    // Process as string
}

// Useful in streams and filters
List<JsonValue> onlyStrings = values.stream()
    .filter(stringPrism::matches)
    .collect(Collectors.toList());
```

**Real-World Example**: Filtering polymorphic domain events:

<!-- verify -->
```java
@GeneratePrisms
sealed interface DomainEvent permits UserEvent, OrderEvent, PaymentEvent, OrderCompleted {}

// Business logic: process only payment events
public void processPayments(List<DomainEvent> events) {
    Prism<DomainEvent, PaymentEvent> paymentPrism =
        DomainEventPrisms.paymentEvent();

    long paymentCount = events.stream()
        .filter(paymentPrism::matches)
        .count();

    logger.info("Processing {} payment events", paymentCount);

    events.stream()
        .filter(paymentPrism::matches)
        .map(paymentPrism::getOptional)
        .flatMap(Optional::stream)
        .forEach(this::processPayment);
}
```

### Default Values with `getOrElse()`

When you need fallback values, `getOrElse()` is more concise than `getOptional().orElse()`:

<!-- verify -->
```java
Prism<ApiResponse, SuccessResponse> successPrism =
    ApiResponsePrisms.successResponse();

// Extract success data or use default
String data = successPrism.getOrElse(
    new SuccessResponse("fallback", 200),
    response
).data();

// Particularly useful for configuration
Config config = Prisms.<Config>some()
    .getOrElse(Config.DEFAULT, optionalConfig);
```

**Real-World Example**: Parsing user input with graceful degradation:

<!-- verify -->
```java
@GeneratePrisms
sealed interface ParsedValue permits IntValue, StringValue, InvalidValue {}

public int parseUserQuantity(String input, int defaultQty) {
    ParsedValue parsed = parseInput(input);

    Prism<ParsedValue, IntValue> intPrism = ParsedValuePrisms.intValue();

    // Extract integer or use sensible default
    return intPrism.getOrElse(
        new IntValue(defaultQty),
        parsed
    ).value();
}

// Application settings with fallback
public DatabaseConfig getDatabaseConfig(ApplicationConfig config) {
    Prism<ConfigSource, DatabaseConfig> dbConfigPrism =
        ConfigSourcePrisms.databaseConfig();

    return dbConfigPrism.getOrElse(
        DatabaseConfig.DEFAULT_POSTGRES,
        config.source()
    );
}
```

### Transforming Matches with `mapOptional()`

The `mapOptional()` method transforms matched values without building them back into the source type:

<!-- verify -->
```java
Prism<JsonValue, JsonNumber> numberPrism = JsonValuePrisms.jsonNumber();

// Extract and transform in one operation
Optional<String> formatted = numberPrism.mapOptional(
    num -> String.format("%.2f", num.value()),
    jsonValue
);

// Compose transformations
Optional<Boolean> isLarge = numberPrism.mapOptional(
    num -> num.value() > 1000,
    jsonValue
);
```

**Real-World Example**: ETL data transformation pipeline:

<!-- verify -->
```java
@GeneratePrisms
sealed interface SourceData permits CsvRow, JsonObject, XmlNode {}

public List<CustomerRecord> extractCustomers(List<SourceData> sources) {
    Prism<SourceData, CsvRow> csvPrism = SourceDataPrisms.csvRow();

    return sources.stream()
        .map(source -> csvPrism.mapOptional(
            csv -> new CustomerRecord(
                csv.column("customer_id"),
                csv.column("name"),
                csv.column("email")
            ),
            source
        ))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
}

// Extract business metrics from polymorphic events
public Optional<BigDecimal> extractRevenue(DomainEvent event) {
    Prism<DomainEvent, OrderCompleted> orderPrism =
        DomainEventPrisms.orderCompleted();

    return orderPrism.mapOptional(
        order -> order.lineItems().stream()
            .map(LineItem::totalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        event
    );
}
```

### Simple Modifications with `modify()`

Instead of manually calling `getOptional().map(f).map(build)`, use `modify()`:

<!-- verify -->
```java
Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

// Clean modification
JsonValue uppercased = stringPrism.modify(
    str -> new JsonString(str.value().toUpperCase()),
    jsonValue
);

// Verbose alternative
JsonValue verboseResult = stringPrism.getOptional(jsonValue)
    .map(str -> new JsonString(str.value().toUpperCase()))
    .map(stringPrism::build)
    .orElse(jsonValue);
```

If the prism doesn't match, `modify()` safely returns the original structure unchanged.

### Conditional Operations with `modifyWhen()` and `setWhen()`

These methods combine matching with predicate-based filtering:

<!-- verify -->
```java
Prism<ConfigValue, StringConfig> stringConfig =
    ConfigValuePrisms.stringConfig();

// Only modify non-empty strings
ConfigValue sanitised = stringConfig.modifyWhen(
    str -> !str.value().isEmpty(),
    str -> new StringConfig(str.value().trim()),
    configValue
);

// Only update if validation passes
ConfigValue validated = stringConfig.setWhen(
    str -> str.value().length() <= 255,
    new StringConfig("validated"),
    configValue
);
```

**Real-World Example**: Business rule enforcement in order processing:

<!-- verify -->
```java
@GeneratePrisms
sealed interface OrderStatus permits Draft, Submitted, Approved, Rejected {}

public class OrderProcessor {
    private static final BigDecimal VIP_THRESHOLD = new BigDecimal("1000");
    private static final double VIP_DISCOUNT_RATE = 0.1;

    private static final Prism<OrderStatus, Submitted> SUBMITTED =
        OrderStatusPrisms.submitted();

    // Only approve orders above minimum value. `setWhen` replaces the focus with
    // another value of the SAME variant, so a move to a different one asks the
    // prism whether it matches and builds the new status itself.
    public OrderStatus approveIfEligible(
        OrderStatus status,
        BigDecimal orderValue,
        BigDecimal minValue
    ) {
        return SUBMITTED.matches(status) && orderValue.compareTo(minValue) >= 0
            ? new Approved(Instant.now(), "AUTO_APPROVED")
            : status;
    }

    // Apply discount only to high-value draft orders
    public OrderStatus applyVipDiscount(OrderStatus status, Order order) {
        Prism<OrderStatus, Draft> draftPrism = OrderStatusPrisms.draft();

        return draftPrism.modifyWhen(
            draft -> order.totalValue().compareTo(VIP_THRESHOLD) > 0,
            draft -> draft.withDiscount(VIP_DISCOUNT_RATE),
            status
        );
    }
}
```

**Use Cases:**
- **Conditional validation**: Update only if current value meets criteria
- **Guarded transformations**: Apply changes only to valid states
- **Business rules**: Enforce constraints during updates
- **Workflow automation**: Apply state transitions based on business logic

### Fallback Matching with `orElse()`

The `orElse()` method chains prisms to try multiple matches:

Both prisms must share the same target type:

<!-- verify -->
```java
// Accept two spellings of the same flag
Prism<String, Unit> affirmative = Prisms.only("yes").orElse(Prisms.only("y"));

boolean saidYes = affirmative.matches(input);

// Building always uses the first prism's constructor
String built = affirmative.build(Unit.INSTANCE); // "yes"
```

**Real-World Example**: Handling multiple error types in API responses:

When the variants hold *different* target types, `orElse` cannot chain them (and a Prism-then-Lens composition is an `Affine`, which has no `orElse`); extract per prism and fall through on the `Optional`s instead:

<!-- verify -->
```java
Optional<String> message =
    ApiResponsePrisms.validationError()
        .mapOptional(ValidationError::message, response)
        .or(() -> ApiResponsePrisms.serverError()
            .mapOptional(ServerError::message, response));
```

~~~admonish tip title="When to Use Convenience Methods"
- **matches()**: Type guards, stream filters, conditional logic
- **getOrElse()**: Configuration, default values, fallback data
- **mapOptional()**: Projections, transformations without reconstruction
- **modify()**: Simple transformations of matching cases
- **modifyWhen()**: Conditional updates based on current state
- **setWhen()**: Guarded updates with validation
- **orElse()**: Handling multiple variants, fallback strategies
~~~

---

## Common Prism Patterns with the Prisms Utility

### _Ready-Made Prisms for Standard Types_

The `Prisms` utility class (in `org.higherkindedj.optics.util`) provides factory methods for common prism patterns, saving you from writing boilerplate for standard Java types.

**Quick Reference:**

| Factory Method | Type Signature | Use Case |
|----------------|----------------|----------|
| `some()` | `Prism<Optional<A>, A>` | Extract present Optional values |
| `left()` | `Prism<Either<L, R>, L>` | Focus on Left case |
| `right()` | `Prism<Either<L, R>, R>` | Focus on Right case |
| `only(A value)` | `Prism<A, Unit>` | Match specific value |
| `notNull()` | `Prism<@Nullable A, A>` | Filter null values |
| `instanceOf(Class<A>)` | `Prism<S, A>` | Safe type-based casting |
| `listHead()` | `Prism<List<A>, A>` | First element (if exists) |
| `listLast()` | `Prism<List<A>, A>` | Last element (if exists) |
| `listAt(int)` | `Prism<List<A>, A>` | Element at index (read-only) |

### Working with Optional: `Prisms.some()`

<!-- verify -->
```java
import org.higherkindedj.optics.util.Prisms;

Prism<Optional<String>, String> somePrism = Prisms.some();

Optional<String> present = Optional.of("hello");
Optional<String> value = somePrism.getOptional(present); // Optional.of("hello")

Optional<String> empty = Optional.empty();
Optional<String> noMatch = somePrism.getOptional(empty); // Optional.empty()

// Useful for nested Optionals - the witness names the layer being unwrapped
Optional<Optional<Config>> nestedConfig = loadConfig();
Optional<Config> flattened = Prisms.<Optional<Config>>some().getOptional(nestedConfig)
    .flatMap(Function.identity());
```

### Either Case Handling: `Prisms.left()` and `Prisms.right()`

<!-- verify -->
```java
Prism<Either<String, Integer>, String> leftPrism = Prisms.left();
Prism<Either<String, Integer>, Integer> rightPrism = Prisms.right();

Either<String, Integer> error = Either.left("Failed");
Optional<String> errorMsg = leftPrism.getOptional(error); // Optional.of("Failed")
Optional<Integer> noValue = rightPrism.getOptional(error); // Optional.empty()

// Compose with lenses for deep access (Prism >>> Lens = Affine)
Lens<ValidationError, String> messageLens = ValidationErrorLenses.message();

Affine<Either<ValidationError, Data>, String> errorMessage =
    Prisms.<ValidationError, Data>left()
        .andThen(messageLens);

Either<ValidationError, Data> result = validate(data);
Optional<String> msg = errorMessage.getOptional(result);
```

### Sentinel Values: `Prisms.only()`

Perfect for matching specific constant values:

<!-- verify -->
```java
Prism<String, Unit> httpOkPrism = Prisms.only("200 OK");

// Check for specific status
if (httpOkPrism.matches(statusCode)) {
    // Handle success case
}

// Filter for specific values
List<String> onlyErrors = statusCodes.stream()
    .filter(Prisms.only("500 ERROR")::matches)
    .collect(Collectors.toList());

// Null sentinel handling
Prism<String, Unit> nullPrism = Prisms.only(null);
boolean isNull = nullPrism.matches(statusCode);
```

### Null Safety: `Prisms.notNull()`

<!-- verify -->
```java
Prism<String, String> notNullPrism = Prisms.notNull();

// Safe extraction
@Nullable String nullable = getDatabaseValue();
Optional<String> safe = notNullPrism.getOptional(nullable);

// Compose to filter null values in pipelines
Traversal<List<String>, String> nonNullStrings =
    Traversals.<String>forList()
        .andThen(Prisms.<String>notNull().asTraversal());

List<@Nullable String> mixedList = List.of("hello", null, "world", null);
List<String> filtered = Traversals.getAll(nonNullStrings, mixedList);
// Result: ["hello", "world"]
```

### Type-Safe Casting: `Prisms.instanceOf()`

Elegant alternative to `instanceof` checks in type hierarchies:

<!-- verify -->
```java
sealed interface Animal permits Dog, Cat, Bird {}
@GenerateLenses record Dog(String name, String breed) implements Animal {}
record Cat(String name, int lives) implements Animal {}
record Bird(String name, boolean canFly) implements Animal {}

Prism<Animal, Dog> dogPrism = Prisms.instanceOf(Dog.class);

Animal animal = new Dog("Buddy", "Labrador");
Optional<Dog> maybeDog = dogPrism.getOptional(animal); // Optional.of(Dog(...))

// Compose with lenses for deep access
Lens<Dog, String> breedLens = DogLenses.breed();
Traversal<Animal, String> dogBreed =
    dogPrism.asTraversal().andThen(breedLens.asTraversal());

List<Animal> animals = List.of(
    new Dog("Rex", "German Shepherd"),
    new Cat("Whiskers", 9),
    new Dog("Max", "Beagle")
);

List<String> breeds = Traversals.getAll(
    Traversals.<Animal>forList().andThen(dogBreed),
    animals
);
// Result: ["German Shepherd", "Beagle"]
```

### Collection Element Access

<!-- verify -->
```java
// First element (if list is non-empty)
Prism<List<String>, String> headPrism = Prisms.listHead();
List<String> names = List.of("Alice", "Bob", "Charlie");
Optional<String> first = headPrism.getOptional(names); // Optional.of("Alice")

// Last element
Prism<List<String>, String> lastPrism = Prisms.listLast();
Optional<String> last = lastPrism.getOptional(names); // Optional.of("Charlie")

// Element at specific index (read-only for queries)
Prism<List<String>, String> secondPrism = Prisms.listAt(1);
Optional<String> second = secondPrism.getOptional(names); // Optional.of("Bob")

// Safe access patterns
String firstOrDefault = headPrism.getOrElse("Unknown", names);
boolean hasList = headPrism.matches(names);
```

~~~admonish warning title="List Prism Limitations"
The `listHead()` and `listLast()` prisms have limited `build()` operations: they create singleton lists. The `listAt(int)` prism throws `UnsupportedOperationException` on `build()` since there's no meaningful way to construct a complete list from a single indexed element.

**Use these prisms for:**
- Safe element extraction
- Conditional checks (with `matches()`)
- Query operations (with `getOptional()`)

**For list modification**, use `Traversal` or `Lens` instead:
<!-- verify -->
```java
// ✅ For modifications, use proper traversals
Lens<List<String>, String> firstLens = listFirstElementLens();
List<String> updated = firstLens.modify(String::toUpperCase, names);
```
~~~

~~~admonish tip title="Advanced List Decomposition"
For more comprehensive list manipulation, including cons/snoc patterns, head/tail and init/last decomposition, and stack-safe operations for large lists, see **[List Decomposition](list_decomposition.md)** and the `ListPrisms` utility class.

<!-- verify -->
```java
import org.higherkindedj.optics.util.ListPrisms;

// Cons pattern: decompose as (head, tail)
Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();
Optional<Pair<String, List<String>>> decomposed = cons.getOptional(names);
// decomposed = Optional.of(Pair.of("Alice", ["Bob", "Charlie"]))

// Snoc pattern: decompose as (init, last)
Prism<List<String>, Pair<List<String>, String>> snoc = ListPrisms.snoc();
```
~~~

### Composing Utility Prisms

The real power emerges when composing these utility prisms with your domain optics:

<!-- verify -->
```java
@GenerateLenses record Config(Optional<Either<String, DatabaseSettings>> database) {}
@GenerateLenses record DatabaseSettings(String host, int port) {}

// Build a path through Optional -> Either -> Settings -> host
// (a chain through asTraversal is a Traversal, not a Prism)
Traversal<Config, String> databaseHost =
    ConfigLenses.database()                    // Lens<Config, Optional<Either<...>>>
        .asTraversal()
        // explicit witnesses: chained receivers lose the target types
        .andThen(Prisms.<Either<String, DatabaseSettings>>some().asTraversal())
        .andThen(Prisms.<String, DatabaseSettings>right().asTraversal())
        .andThen(DatabaseSettingsLenses.host().asTraversal()); // -> String

Config config = new Config(Optional.of(Either.right(new DatabaseSettings("localhost", 5432))));
Optional<String> host = Traversals.getAll(databaseHost, config)
    .stream().findFirst();
```

~~~admonish tip title="Performance Considerations"
Utility prisms are lightweight and stateless; they're safe to create on-demand or cache as constants:

<!-- verify -->
```java
public class AppPrisms {
    public static final Prism<Optional<User>, User> SOME_USER = Prisms.some();
    public static final Prism<ApiResponse, SuccessResponse> SUCCESS =
        Prisms.instanceOf(SuccessResponse.class);
}
```
~~~


---

~~~admonish info title="Key Takeaways"
* **The convenience methods close the gaps**: `matches`, `getOrElse`, `mapOptional`, `modify`, `modifyWhen`, `setWhen`, and `orElse` cover the everyday patterns without manual `Optional` plumbing
* **`Prisms` ships the standard library**: `some()`, `left()`/`right()`, `only()`, `notNull()`, `instanceOf()`, and the list accessors mean the common cases are one call, not boilerplate
* **The list prisms are for reading**: `listHead()`/`listLast()` build singleton lists and `listAt` cannot build at all; modify lists through a `Traversal` or `Lens`
* **Utility prisms are stateless**: cache them as constants or create them on demand, whichever reads better
~~~

~~~admonish tip title="See Also"
- [Prisms](prisms.md): the concepts these conveniences are built on
- [Validated Prisms](validated_prism.md): when a failed match needs to say why
- [List Decomposition](list_decomposition.md): cons/snoc patterns and stack-safe list optics
~~~

---

**Previous:** [Prisms](prisms.md)
**Next:** [Validated Prisms](validated_prism.md)
