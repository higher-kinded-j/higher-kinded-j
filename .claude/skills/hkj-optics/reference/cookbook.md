# Optics Cookbook

Practical recipes for common optics problems. Each recipe: problem, solution, explanation.

---

## Recipe 1: Updating Nested Optional Fields

<!-- verify -->
```java
record Patron(String name, Optional<PatronProfile> profile) {}
record PatronProfile(String bio, Optional<DisplaySettings> settings) {}
record DisplaySettings(boolean darkMode, int fontSize) {}

// Zero or one font size, so andThen gives an Affine: no asTraversal() needed
Affine<Patron, Integer> patronFontSize =
    PatronLenses.profile()                          // Lens<Patron, Optional<PatronProfile>>
        .andThen(Prisms.some())                     // Prism -> PatronProfile
        .andThen(PatronProfileLenses.settings())    // Lens -> Optional<DisplaySettings>
        .andThen(Prisms.<DisplaySettings>some())    // Prism -> DisplaySettings
        .andThen(DisplaySettingsLenses.fontSize()); // Lens -> Integer

// Increase font size if it exists; otherwise unchanged
Patron updated = patronFontSize.modify(size -> size + 2, patron);
```

Each `Prisms.some()` safely handles Optional: if any is empty, modification is skipped.

---

## Recipe 2: Modifying a Specific Variant of a Sum Type

<!-- verify -->
```java
sealed interface FetchState permits Loaded, LoadFailed, Loading {}
record Loaded(Data data, String timestamp) implements FetchState {}
record LoadFailed(String message, int code) implements FetchState {}

Prism<FetchState, Loaded> loadedPrism = Prism.of(
    fetch -> fetch instanceof Loaded loaded ? Optional.of(loaded) : Optional.empty(),
    loaded -> loaded
);

// Prism + Lens = Affine: zero or one Data
Affine<FetchState, Data> loadedData = loadedPrism.andThen(LoadedLenses.data());

// Transform data only for Loaded states; LoadFailed/Loading pass through unchanged
FetchState modified = loadedData.modify(data -> new Data(data.value().toUpperCase()), state);
```

---

## Recipe 3: Bulk Updates Across Collections

<!-- verify -->
```java
record Order(UUID id, Customer customer, List<LineItem> lines,
             Instant placedAt, Currency currency, OrderStatus status) {}
record LineItem(String sku, Integer quantity, BigDecimal price) {}

Traversal<Order, LineItem> allLines =
    OrderLenses.lines().andThen(Traversals.forList());

// Filter to high-quantity lines only
Traversal<Order, LineItem> bulkLines =
    allLines.andThen(Traversals.filtered(line -> line.quantity() > 10));

// Apply 10% discount to bulk lines
Order discounted = Traversals.modify(bulkLines,
    line -> new LineItem(line.sku(), line.quantity(), line.price().multiply(new BigDecimal("0.9"))),
    order);
```

---

## Recipe 4: Extracting Values from Polymorphic Structures

<!-- verify -->
```java
sealed interface Activity permits UserAction, SystemNotice {}
record UserAction(String userId, String action) implements Activity {}
record SystemNotice(String level, String message) implements Activity {}

Prism<Activity, UserAction> userActionPrism = Prism.of(
    a -> a instanceof UserAction u ? Optional.of(u) : Optional.empty(),
    u -> u
);

Traversal<List<Activity>, String> userActions =
    Traversals.<Activity>forList()
        .andThen(userActionPrism)
        .andThen(UserActionLenses.action());

List<String> actions = Traversals.getAll(userActions, activities);
// Only UserAction actions extracted; SystemNotices skipped
```

---

## Recipe 5: Safe Map Access

<!-- verify -->
```java
record ConfigMap(Map<String, String> settings) {}

Traversal<ConfigMap, String> databaseUrl =
    ConfigMapLenses.settings().andThen(Traversals.forMap("database.url"));

List<String> urls = Traversals.getAll(databaseUrl, config);
String url = urls.isEmpty() ? "jdbc:postgresql://localhost/default" : urls.get(0);
```

---

## Recipe 6: Transforming Nested Collections

<!-- verify -->
```java
record OrgChart(String name, List<OrgUnit> units) {}
record OrgUnit(String name, List<OrgMember> members) {}
record OrgMember(String name, int age, Optional<String> email, Integer salary) {}

Traversal<OrgChart, Integer> allSalaries =
    OrgChartLenses.units()
        .andThen(Traversals.forList())
        .andThen(OrgUnitLenses.members())
        .andThen(Traversals.forList())
        .andThen(OrgMemberLenses.salary());

// Give everyone a 5% raise
OrgChart afterRaise = Traversals.modify(allSalaries, s -> (int)(s * 1.05), company);

// Get total payroll
List<Integer> salaries = Traversals.getAll(allSalaries, company);
int totalPayroll = salaries.stream().mapToInt(Integer::intValue).sum();
```

---

## Recipe 7: Conditional Updates Based on Related Data

<!-- verify -->
```java
record Product(String sku, String name, BigDecimal price, int stock) {}

Traversal<List<Product>, BigDecimal> overstockPrices =
    Traversals.<Product>forList()
        .andThen(Traversals.filtered(product -> product.stock() > 100))
        .andThen(ProductLenses.price());

List<Product> discounted = Traversals.modify(
    overstockPrices, price -> price.multiply(new BigDecimal("0.8")), products);
// Only overstocked products get a 20% discount
```

---

## Recipe 8: Working with Either for Error Handling

<!-- verify -->
```java
Prism<Either<FormError, UserData>, UserData> rightPrism = Prisms.right();

// Transform user data only on success; errors pass through unchanged
Either<FormError, UserData> transformed = rightPrism.modify(
    user -> new UserData(user.name().toUpperCase(), user.email()), result);
```

---

## Recipe 9: Extracting Values from Multiple Paths (Fold.plus)

<!-- verify -->
```java
record Guild(String name, Engineer lead, List<Engineer> members) {}
record Engineer(String name, String email) {}

Fold<Guild, String> leadEmail = guildLeadLens.asFold()
    .andThen(engineerEmailLens.asFold());
Fold<Guild, String> memberEmails = Fold.<Guild, Engineer>of(Guild::members)
    .andThen(engineerEmailLens.asFold());

// Combine with plus
Fold<Guild, String> allEmails = leadEmail.plus(memberEmails);

// Or use sum for 3+ paths
Fold<Guild, String> allNames = Fold.sum(
    Fold.of(g -> List.of(g.name())),
    guildLeadLens.asFold().andThen(engineerNameLens.asFold()),
    Fold.<Guild, Engineer>of(Guild::members).andThen(engineerNameLens.asFold())
);

List<String> emails = allEmails.getAll(guild); // ["alice@co", "bob@co"]
```

---

## Recipe 10: Traversal-Derived Folds for Read-Only Queries

```java
Traversal<Order, Integer> allQuantities = /* existing traversal */;
Fold<Order, Integer> quantitiesFold = allQuantities.asFold();

int totalUnits = quantitiesFold.foldMap(Monoids.integerAddition(), q -> q, order);
boolean hasBulkLine = quantitiesFold.exists(q -> q > 10, order);
boolean allWithinLimit = quantitiesFold.all(q -> q <= 100, order);
int lineCount = quantitiesFold.length(order);
```

`Traversal.asFold()` reuses the traversal's `modifyF` with a `Const` applicative for monoidal accumulation.

---

## Recipe 11: Focus DSL for Nested Record Updates

<!-- verify -->
```java
// Using Focus DSL instead of manual lens composition. Each each(listEach()) takes its element
// type from the List the path focuses on, so the chain needs no witnesses.
TraversalPath<OrgChart, Integer> allSalaries =
    FocusPath.of(chartUnitsLens)
        .each(EachInstances.listEach())
        .via(unitMembersLens)
        .each(EachInstances.listEach())
        .via(memberSalaryLens);

OrgChart updated = allSalaries.modifyAll(s -> (int)(s * 1.05), company);

// Or use generated Focus classes (@GenerateFocus): a List of records gets a navigator over its
// elements, so each hop chains
OrgChart updatedViaFocus =
    OrgChartFocus.units()
        .members()
        .salary()
        .modifyAll(s -> (int)(s * 1.05), company);
```

---

## Recipe 12: Sum Type Handling with instanceOf()

<!-- verify -->
```java
sealed interface Notification permits EmailMessage, SMS, Push {}
record EmailMessage(String address, String subject, String body) implements Notification {}

TraversalPath<Outbox, Notification> allNotifications =
    FocusPath.of(outboxNotificationsLens).each(EachInstances.listEach());

TraversalPath<Outbox, EmailMessage> emailsOnly =
    allNotifications.via(AffinePath.instanceOf(EmailMessage.class));

// Get all email addresses
List<String> emailAddresses = emailsOnly.via(messageAddressLens).getAll(outbox);

// Update all email subjects
Outbox updated = emailsOnly.via(emailSubjectLens)
    .modifyAll(subject -> "[URGENT] " + subject, outbox);
```

---

## Recipe 13: Conditional Updates with modifyWhen()

<!-- verify -->
```java
record Inventory(List<Product> products, String warehouseId) {}
record Product(String sku, String name, BigDecimal price, int stock) {}

TraversalPath<Inventory, Product> allProducts =
    FocusPath.of(inventoryProductsLens).each(EachInstances.listEach());

// Apply 20% discount to premium products with low stock
Inventory updated = allProducts.modifyWhen(
    p -> p.price().compareTo(new BigDecimal("100")) > 0 && p.stock() < 10,
    p -> new Product(p.sku(), p.name(),
        p.price().multiply(new BigDecimal("0.80")), p.stock()),
    inventory);
```

---

## Recipe 14: Aggregation with foldMap()

<!-- verify -->
```java
TraversalPath<Order, LineItem> allLines =
    FocusPath.of(orderLinesLens).each(EachInstances.listEach());

int totalQuantity = allLines.via(lineItemQuantityLens)
    .foldMap(intSumMonoid, q -> q, order);

BigDecimal totalPrice = allLines.via(lineItemPriceLens)
    .foldMap(decimalSumMonoid, p -> p, order);
```

---

## Recipe 15: Validation with modifyF()

<!-- verify -->
```java
FocusPath<ConfigMap, String> apiKeyPath = FocusPath.of(configApiKeyLens);

Function<String, Kind<MaybeKind.Witness, String>> validateApiKey = key -> {
    if (key != null && key.length() >= 10) {
        return MaybeKindHelper.MAYBE.widen(Maybe.just(key.toUpperCase()));
    }
    return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
};

Kind<MaybeKind.Witness, ConfigMap> result =
    apiKeyPath.modifyF(validateApiKey, config, MaybeMonad.INSTANCE);
```

---

## Best Practices

**1. Create reusable optic constants:**
<!-- verify -->
```java
public final class OrderOptics {
    public static final Traversal<Order, BigDecimal> ALL_PRICES =
        OrderLenses.lines()
            .andThen(Traversals.forList())
            .andThen(LineItemLenses.price());
}
```

**2. Use direct composition when possible:**
<!-- verify -->
```java
// Preferred: type-safe (Lens + Prism = Affine, the most precise type available)
Affine<PatronProfile, DisplaySettings> direct = settingsLens.andThen(settingsPrism);

// Fallback: maximum flexibility (everything widens to Traversal)
Traversal<PatronProfile, DisplaySettings> manual =
    settingsLens.asTraversal().andThen(settingsPrism.asTraversal());
```

**3. Document complex compositions:**
```java
/**
 * Path: Order -> lines list -> bulk lines only (more than 10) -> SKU
 */
public static final Traversal<Order, String> BULK_LINE_SKUS =
    OrderLenses.lines()
        .andThen(Traversals.forList())
        .andThen(Traversals.filtered(line -> line.quantity() > 10))
        .andThen(LineItemLenses.sku());
```

**4. Prefer specific types when available:**
```java
// If always present, use Lens directly
Lens<Patron, String> name = PatronLenses.name();
String patronName = name.get(patron);

// Only use Traversal when you need flexibility
Traversal<Patron, String> optionalBio = /* ... */;
List<String> bios = Traversals.getAll(optionalBio, patron);
```
