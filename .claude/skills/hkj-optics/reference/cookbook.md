# Optics Cookbook

Practical recipes for common optics problems. Each recipe: problem, solution, explanation.

---

## Recipe 1: Updating Nested Optional Fields

```java
record User(String name, Optional<Profile> profile) {}
record Profile(String bio, Optional<Settings> settings) {}
record Settings(boolean darkMode, int fontSize) {}

Traversal<User, Integer> userFontSize =
    UserLenses.profile()                                    // Lens<User, Optional<Profile>>
        .andThen(Prisms.some())                             // Prism -> Profile
        .andThen(ProfileLenses.settings().asTraversal())    // Lens -> Optional<Settings>
        .andThen(Prisms.some().asTraversal())               // Prism -> Settings
        .andThen(SettingsLenses.fontSize().asTraversal());  // Lens -> Integer

// Increase font size if it exists; otherwise unchanged
User updated = Traversals.modify(userFontSize, size -> size + 2, user);
```

Each `Prisms.some()` safely handles Optional -- if any is empty, modification is skipped.

---

## Recipe 2: Modifying a Specific Variant of a Sum Type

```java
sealed interface ApiResponse permits Success, Error, Loading {}
record Success(Data data, String timestamp) implements ApiResponse {}
record Error(String message, int code) implements ApiResponse {}

Prism<ApiResponse, Success> successPrism = Prism.of(
    resp -> resp instanceof Success s ? Optional.of(s) : Optional.empty(),
    s -> s
);

Traversal<ApiResponse, Data> successData =
    successPrism.andThen(SuccessLenses.data());

// Transform data only for Success responses; Error/Loading pass through unchanged
ApiResponse modified = Traversals.modify(
    successData, data -> new Data(data.value().toUpperCase()), response);
```

---

## Recipe 3: Bulk Updates Across Collections

```java
record Order(String id, List<LineItem> items) {}
record LineItem(String productId, int quantity, Money price) {}

Traversal<Order, LineItem> allItems =
    OrderLenses.items().asTraversal().andThen(Traversals.forList());

// Filter to high-quantity items only
Traversal<Order, LineItem> bulkItems =
    allItems.andThen(Traversals.filtered(item -> item.quantity() > 10));

// Apply 10% discount to bulk items
Order discounted = Traversals.modify(bulkItems,
    item -> new LineItem(item.productId(), item.quantity(), item.price().multiply(0.9)),
    order);
```

---

## Recipe 4: Extracting Values from Polymorphic Structures

```java
sealed interface Event permits UserEvent, SystemEvent {}
record UserEvent(String userId, String action) implements Event {}
record SystemEvent(String level, String message) implements Event {}

Prism<Event, UserEvent> userEventPrism = Prism.of(
    e -> e instanceof UserEvent u ? Optional.of(u) : Optional.empty(),
    u -> u
);

Traversal<List<Event>, String> userActions =
    Traversals.<Event>forList()
        .andThen(userEventPrism.asTraversal())
        .andThen(UserEventLenses.action().asTraversal());

List<String> actions = Traversals.getAll(userActions, events);
// Only UserEvent actions extracted; SystemEvents skipped
```

---

## Recipe 5: Safe Map Access

```java
record Config(Map<String, String> settings) {}

Traversal<Config, String> databaseUrl =
    ConfigLenses.settings().asTraversal()
        .andThen(Traversals.forMap("database.url"));

List<String> urls = Traversals.getAll(databaseUrl, config);
String url = urls.isEmpty() ? "jdbc:postgresql://localhost/default" : urls.get(0);
```

---

## Recipe 6: Transforming Nested Collections

```java
record Company(List<Department> departments) {}
record Department(String name, List<Employee> employees) {}
record Employee(String name, int salary) {}

Traversal<Company, Integer> allSalaries =
    CompanyLenses.departments().asTraversal()
        .andThen(Traversals.forList())
        .andThen(DepartmentLenses.employees().asTraversal())
        .andThen(Traversals.forList())
        .andThen(EmployeeLenses.salary().asTraversal());

// Give everyone a 5% raise
Company afterRaise = Traversals.modify(allSalaries, s -> (int)(s * 1.05), company);

// Get total payroll
List<Integer> salaries = Traversals.getAll(allSalaries, company);
int totalPayroll = salaries.stream().mapToInt(Integer::intValue).sum();
```

---

## Recipe 7: Conditional Updates Based on Related Data

```java
record Product(String name, Money price, boolean onSale) {}

Traversal<List<Product>, Money> salePrices =
    Traversals.<Product>forList()
        .andThen(Traversals.filtered(Product::onSale))
        .andThen(ProductLenses.price().asTraversal());

List<Product> discounted = Traversals.modify(
    salePrices, price -> price.multiply(0.8), products);
// Only on-sale products get 20% discount
```

---

## Recipe 8: Working with Either for Error Handling

```java
Prism<Either<ValidationError, UserData>, UserData> rightPrism = Prisms.right();

// Transform user data only on success; errors pass through unchanged
Either<ValidationError, UserData> transformed = rightPrism.modify(
    user -> new UserData(user.name().toUpperCase(), user.email()), result);
```

---

## Recipe 9: Extracting Values from Multiple Paths (Fold.plus)

```java
record Team(String name, Employee lead, List<Employee> members) {}

Fold<Team, String> leadEmail = teamLeadLens.asFold()
    .andThen(employeeEmailLens.asFold());
Fold<Team, String> memberEmails = Fold.<Team, Employee>of(Team::members)
    .andThen(employeeEmailLens.asFold());

// Combine with plus
Fold<Team, String> allEmails = leadEmail.plus(memberEmails);

// Or use sum for 3+ paths
Fold<Team, String> allNames = Fold.sum(
    Fold.of(t -> List.of(t.name())),
    teamLeadLens.asFold().andThen(employeeNameLens.asFold()),
    Fold.<Team, Employee>of(Team::members).andThen(employeeNameLens.asFold())
);

List<String> emails = allEmails.getAll(team); // ["alice@co", "bob@co"]
```

---

## Recipe 10: Traversal-Derived Folds for Read-Only Queries

```java
Traversal<Order, Double> allPrices = /* existing traversal */;
Fold<Order, Double> pricesFold = allPrices.asFold();

double total = pricesFold.foldMap(Monoids.doubleAddition(), p -> p, order);
boolean hasExpensive = pricesFold.exists(p -> p > 100.0, order);
boolean allAffordable = pricesFold.all(p -> p < 200.0, order);
int itemCount = pricesFold.length(order);
```

`Traversal.asFold()` reuses the traversal's `modifyF` with a `Const` applicative for monoidal accumulation.

---

## Recipe 11: Focus DSL -- Nested Record Updates

```java
// Using Focus DSL instead of manual lens composition
TraversalPath<Company, Integer> allSalaries =
    FocusPath.of(companyDeptsLens).each().via(deptEmployeesLens).each().via(employeeSalaryLens);

Company updated = allSalaries.modifyAll(s -> (int)(s * 1.05), company);

// Or use generated Focus classes (@GenerateFocus)
Company updated = CompanyFocus.departments().employees().salary()
    .modifyAll(s -> (int)(s * 1.05), company);
```

---

## Recipe 12: Sum Type Handling with instanceOf()

```java
sealed interface Notification permits Email, SMS, Push {}
record Email(String address, String subject, String body) implements Notification {}

TraversalPath<User, Notification> allNotifications =
    FocusPath.of(userNotificationsLens).each();

TraversalPath<User, Email> emailsOnly =
    allNotifications.via(AffinePath.instanceOf(Email.class));

// Get all email addresses
List<String> emailAddresses = emailsOnly.via(emailAddressLens).getAll(user);

// Update all email subjects
User updated = emailsOnly.via(emailSubjectLens)
    .modifyAll(subject -> "[URGENT] " + subject, user);
```

---

## Recipe 13: Conditional Updates with modifyWhen()

```java
record Inventory(List<Product> products) {}
record Product(String name, int stock, BigDecimal price, Category category) {}

TraversalPath<Inventory, Product> allProducts =
    FocusPath.of(inventoryProductsLens).each();

// Apply 20% discount to electronics with low stock
Inventory updated = allProducts.modifyWhen(
    p -> p.category() == Category.ELECTRONICS && p.stock() < 10,
    p -> new Product(p.name(), p.stock(),
        p.price().multiply(new BigDecimal("0.80")), p.category()),
    inventory);
```

---

## Recipe 14: Aggregation with foldMap()

```java
TraversalPath<Order, LineItem> allItems = FocusPath.of(orderItemsLens).each();

int totalQuantity = allItems.via(lineItemQuantityLens)
    .foldMap(intSumMonoid, q -> q, order);

BigDecimal totalPrice = allItems.via(lineItemPriceLens)
    .foldMap(decimalSumMonoid, p -> p, order);
```

---

## Recipe 15: Validation with modifyF()

```java
FocusPath<Config, String> apiKeyPath = FocusPath.of(configApiKeyLens);

Function<String, Kind<MaybeKind.Witness, String>> validateApiKey = key -> {
    if (key != null && key.length() >= 10) {
        return MaybeKindHelper.MAYBE.widen(Maybe.just(key.toUpperCase()));
    }
    return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
};

Kind<MaybeKind.Witness, Config> result =
    apiKeyPath.modifyF(validateApiKey, config, MaybeMonad.INSTANCE);
```

---

## Best Practices

**1. Create reusable optic constants:**
```java
public final class OrderOptics {
    public static final Traversal<Order, Money> ALL_PRICES =
        OrderLenses.items().asTraversal()
            .andThen(Traversals.forList())
            .andThen(LineItemLenses.price().asTraversal());
}
```

**2. Use direct composition when possible:**
```java
// Preferred: type-safe
Traversal<Config, Settings> direct = configLens.andThen(settingsPrism);

// Fallback: maximum flexibility
Traversal<Config, Settings> manual =
    configLens.asTraversal().andThen(settingsPrism.asTraversal());
```

**3. Document complex compositions:**
```java
/**
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

**4. Prefer specific types when available:**
```java
// If always present, use Lens directly
Lens<User, String> name = UserLenses.name();
String userName = name.get(user);

// Only use Traversal when you need flexibility
Traversal<User, String> optionalNickname = /* ... */;
List<String> nicknames = Traversals.getAll(optionalNickname, user);
```
