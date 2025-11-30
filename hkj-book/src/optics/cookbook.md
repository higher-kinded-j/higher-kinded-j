# Optics Cookbook

## _Practical Recipes for Common Problems_

~~~admonish info title="What You'll Learn"
- Ready-to-use patterns for common optics scenarios
- Copy-paste recipes with explanations
- Best practices for production code
~~~

This cookbook provides practical recipes for common optics problems. Each recipe includes the problem statement, solution, and explanation.

---

## Recipe 1: Updating Nested Optional Fields

### Problem

You have a deeply nested structure with optional fields and need to update a value that may or may not exist.

### Solution

```java
record User(String name, Optional<Profile> profile) {}
record Profile(String bio, Optional<Settings> settings) {}
record Settings(boolean darkMode, int fontSize) {}

// Build the traversal path
Traversal<User, Integer> userFontSize =
    UserLenses.profile()              // Lens<User, Optional<Profile>>
        .andThen(Prisms.some())       // Prism<Optional<Profile>, Profile>
        .andThen(ProfileLenses.settings().asTraversal())  // Lens<Profile, Optional<Settings>>
        .andThen(Prisms.some().asTraversal())             // Prism<Optional<Settings>, Settings>
        .andThen(SettingsLenses.fontSize().asTraversal()); // Lens<Settings, Integer>

// Usage
User user = new User("Alice", Optional.of(
    new Profile("Developer", Optional.of(new Settings(true, 14)))
));

// Increase font size if it exists, otherwise leave unchanged
User updated = Traversals.modify(userFontSize, size -> size + 2, user);
```

### Why It Works

Each `Prisms.some()` safely handles the Optional - if any Optional is empty, the modification is skipped and the original structure is returned unchanged.

---

## Recipe 2: Modifying a Specific Variant of a Sum Type

### Problem

You have a sealed interface and want to modify only one specific variant whilst leaving others unchanged.

### Solution

```java
sealed interface ApiResponse permits Success, Error, Loading {}
record Success(Data data, String timestamp) implements ApiResponse {}
record Error(String message, int code) implements ApiResponse {}
record Loading(int progress) implements ApiResponse {}

// Create prism for the Success case
Prism<ApiResponse, Success> successPrism = Prism.of(
    resp -> resp instanceof Success s ? Optional.of(s) : Optional.empty(),
    s -> s
);

// Compose with lens to access data
Traversal<ApiResponse, Data> successData =
    successPrism.andThen(SuccessLenses.data());

// Usage: transform data only for Success responses
ApiResponse response = new Success(new Data("original"), "2024-01-01");
ApiResponse modified = Traversals.modify(
    successData,
    data -> new Data(data.value().toUpperCase()),
    response
);
// Result: Success[data=Data[value=ORIGINAL], timestamp=2024-01-01]

// Error responses pass through unchanged
ApiResponse error = new Error("Not found", 404);
ApiResponse unchanged = Traversals.modify(
    successData,
    data -> new Data(data.value().toUpperCase()),
    error
);
// Result: Error[message=Not found, code=404] (unchanged)
```

---

## Recipe 3: Bulk Updates Across Collections

### Problem

You need to update all items in a collection that match certain criteria.

### Solution

```java
record Order(String id, List<LineItem> items) {}
record LineItem(String productId, int quantity, Money price) {}

// Traversal to all line items
Traversal<Order, LineItem> allItems =
    OrderLenses.items().asTraversal()
        .andThen(Traversals.forList());

// Traversal to high-quantity items only
Traversal<Order, LineItem> bulkItems =
    allItems.andThen(Traversals.filtered(item -> item.quantity() > 10));

// Apply 10% discount to bulk items
Order order = new Order("ORD-001", List.of(
    new LineItem("PROD-1", 5, new Money(100)),
    new LineItem("PROD-2", 15, new Money(200)),
    new LineItem("PROD-3", 20, new Money(150))
));

Order discounted = Traversals.modify(
    bulkItems,
    item -> new LineItem(
        item.productId(),
        item.quantity(),
        item.price().multiply(0.9)
    ),
    order
);
// Only items with quantity > 10 get the discount
```

---

## Recipe 4: Extracting Values from Polymorphic Structures

### Problem

You have a list of mixed types and need to extract values from specific types only.

### Solution

```java
sealed interface Event permits UserEvent, SystemEvent {}
record UserEvent(String userId, String action) implements Event {}
record SystemEvent(String level, String message) implements Event {}

// Prism to focus on UserEvents
Prism<Event, UserEvent> userEventPrism = Prism.of(
    e -> e instanceof UserEvent u ? Optional.of(u) : Optional.empty(),
    u -> u
);

// Traversal from list of events to user actions
Traversal<List<Event>, String> userActions =
    Traversals.<Event>forList()
        .andThen(userEventPrism.asTraversal())
        .andThen(UserEventLenses.action().asTraversal());

// Usage
List<Event> events = List.of(
    new UserEvent("user-1", "LOGIN"),
    new SystemEvent("INFO", "Server started"),
    new UserEvent("user-2", "LOGOUT"),
    new SystemEvent("WARN", "High memory usage")
);

List<String> actions = Traversals.getAll(userActions, events);
// Result: ["LOGIN", "LOGOUT"]
```

---

## Recipe 5: Safe Map Access with Fallback

### Problem

You need to access a value in a Map that may not exist, with a sensible default.

### Solution

```java
record Config(Map<String, String> settings) {}

// Traversal to a specific key
Traversal<Config, String> databaseUrl =
    ConfigLenses.settings().asTraversal()
        .andThen(Traversals.forMap("database.url"));

// Get with default
Config config = new Config(Map.of("app.name", "MyApp"));

List<String> urls = Traversals.getAll(databaseUrl, config);
String url = urls.isEmpty() ? "jdbc:postgresql://localhost/default" : urls.get(0);

// Or use Optional pattern
Optional<String> maybeUrl = urls.stream().findFirst();
```

---

## Recipe 6: Composing Multiple Validations

### Problem

You need to validate multiple fields and accumulate all errors.

### Solution

```java
import static org.higherkindedj.optics.fluent.OpticOps.modifyAllValidated;

record Registration(String email, String password, int age) {}

// Traversals for each field
Traversal<Registration, String> emailTraversal =
    RegistrationLenses.email().asTraversal();
Traversal<Registration, String> passwordTraversal =
    RegistrationLenses.password().asTraversal();
Traversal<Registration, Integer> ageTraversal =
    RegistrationLenses.age().asTraversal();

// Validation functions
Function<String, Validated<List<String>, String>> validateEmail = email ->
    email.contains("@")
        ? Validated.valid(email)
        : Validated.invalid(List.of("Invalid email format"));

Function<String, Validated<List<String>, String>> validatePassword = password ->
    password.length() >= 8
        ? Validated.valid(password)
        : Validated.invalid(List.of("Password must be at least 8 characters"));

Function<Integer, Validated<List<String>, Integer>> validateAge = age ->
    age >= 18
        ? Validated.valid(age)
        : Validated.invalid(List.of("Must be 18 or older"));

// Combine validations
public Validated<List<String>, Registration> validateRegistration(Registration reg) {
    Validated<List<String>, Registration> emailResult =
        modifyAllValidated(emailTraversal, validateEmail, reg);
    Validated<List<String>, Registration> passwordResult =
        modifyAllValidated(passwordTraversal, validatePassword, reg);
    Validated<List<String>, Registration> ageResult =
        modifyAllValidated(ageTraversal, validateAge, reg);

    // Combine all validations
    return emailResult.flatMap(r1 ->
        passwordResult.flatMap(r2 ->
            ageResult
        )
    );
}
```

---

## Recipe 7: Transforming Nested Collections

### Problem

You have nested collections and need to transform items at the innermost level.

### Solution

```java
record Company(List<Department> departments) {}
record Department(String name, List<Employee> employees) {}
record Employee(String name, int salary) {}

// Traversal to all employee salaries across all departments
Traversal<Company, Integer> allSalaries =
    CompanyLenses.departments().asTraversal()
        .andThen(Traversals.forList())
        .andThen(DepartmentLenses.employees().asTraversal())
        .andThen(Traversals.forList())
        .andThen(EmployeeLenses.salary().asTraversal());

// Give everyone a 5% raise
Company company = /* ... */;
Company afterRaise = Traversals.modify(
    allSalaries,
    salary -> (int) (salary * 1.05),
    company
);

// Get total payroll
List<Integer> salaries = Traversals.getAll(allSalaries, company);
int totalPayroll = salaries.stream().mapToInt(Integer::intValue).sum();
```

---

## Recipe 8: Conditional Updates Based on Related Data

### Problem

You need to update a field based on the value of another field in the same structure.

### Solution

```java
record Product(String name, Money price, boolean onSale) {}

// Create a lens for the price
Lens<Product, Money> priceLens = ProductLenses.price();

// Conditional discount based on onSale flag
public Product applyDiscount(Product product, double discountRate) {
    if (product.onSale()) {
        return priceLens.modify(
            price -> price.multiply(1 - discountRate),
            product
        );
    }
    return product;
}

// Or using Traversal with filter
Traversal<List<Product>, Money> salePrices =
    Traversals.<Product>forList()
        .andThen(Traversals.filtered(Product::onSale))
        .andThen(priceLens.asTraversal());

List<Product> products = /* ... */;
List<Product> discounted = Traversals.modify(
    salePrices,
    price -> price.multiply(0.8),
    products
);
```

---

## Recipe 9: Working with Either for Error Handling

### Problem

You have an `Either<Error, Success>` and need to transform the success case whilst preserving errors.

### Solution

```java
record ValidationError(String field, String message) {}
record UserData(String name, String email) {}

// Prism to focus on the Right (success) case
Prism<Either<ValidationError, UserData>, UserData> rightPrism = Prisms.right();

// Transform user data only on success
Either<ValidationError, UserData> result =
    Either.right(new UserData("alice", "alice@example.com"));

Either<ValidationError, UserData> transformed = rightPrism.modify(
    user -> new UserData(user.name().toUpperCase(), user.email()),
    result
);
// Result: Right(UserData[name=ALICE, email=alice@example.com])

// Errors pass through unchanged
Either<ValidationError, UserData> errorResult =
    Either.left(new ValidationError("email", "Invalid format"));

Either<ValidationError, UserData> stillError = rightPrism.modify(
    user -> new UserData(user.name().toUpperCase(), user.email()),
    errorResult
);
// Result: Left(ValidationError[field=email, message=Invalid format])
```

---

## Recipe 10: Sorting or Reversing Traversed Elements

### Problem

You need to sort or reorder the elements focused by a Traversal.

### Solution

```java
record Scoreboard(List<Player> players) {}
record Player(String name, int score) {}

// Traversal to all scores
Traversal<Scoreboard, Integer> scores =
    ScoreboardLenses.players().asTraversal()
        .andThen(Traversals.forList())
        .andThen(PlayerLenses.score().asTraversal());

// Sort scores (highest first)
Scoreboard board = new Scoreboard(List.of(
    new Player("Alice", 100),
    new Player("Bob", 150),
    new Player("Charlie", 75)
));

Scoreboard sorted = Traversals.sorted(
    scores,
    Comparator.reverseOrder(),
    board
);
// Result: Players now have scores [150, 100, 75] respectively

// Reverse the order
Scoreboard reversed = Traversals.reversed(scores, board);
// Result: Players now have scores [75, 150, 100]
```

---

## Best Practices

### 1. Create Reusable Optic Constants

```java
public final class OrderOptics {
    public static final Traversal<Order, Money> ALL_PRICES =
        OrderLenses.items().asTraversal()
            .andThen(Traversals.forList())
            .andThen(LineItemLenses.price().asTraversal());

    public static final Traversal<Order, String> CUSTOMER_EMAIL =
        OrderLenses.customer()
            .andThen(CustomerPrisms.verified())
            .andThen(CustomerLenses.email().asTraversal());
}
```

### 2. Use Direct Composition Methods

```java
// Preferred: type-safe, clearer intent
Traversal<Config, Settings> direct = configLens.andThen(settingsPrism);

// Fallback: when you need maximum flexibility
Traversal<Config, Settings> manual =
    configLens.asTraversal().andThen(settingsPrism.asTraversal());
```

### 3. Document Complex Compositions

```java
/**
 * Traverses from an Order to all active promotion codes.
 *
 * Path: Order -> Customer -> Loyalty (if exists) -> Promotions list -> Active only
 */
public static final Traversal<Order, String> ACTIVE_PROMO_CODES =
    OrderLenses.customer()
        .andThen(CustomerPrisms.loyaltyMember())
        .andThen(LoyaltyLenses.promotions().asTraversal())
        .andThen(Traversals.forList())
        .andThen(Traversals.filtered(Promotion::isActive))
        .andThen(PromotionLenses.code().asTraversal());
```

### 4. Prefer Specific Types When Available

```java
// If you know it's always present, use Lens directly
Lens<User, String> name = UserLenses.name();
String userName = name.get(user);

// Only use Traversal when you need the flexibility
Traversal<User, String> optionalNickname = /* ... */;
List<String> nicknames = Traversals.getAll(optionalNickname, user);
```

---

**Previous:** [Composition Rules](composition_rules.md)
**Next:** [Practical Examples](optics_examples.md)
