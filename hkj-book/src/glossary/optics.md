# Glossary: Optics, Validation & Mapping

~~~admonish info title="What This Page Covers"
- Lenses, prisms, traversals, the Focus DSL, and record mapping.
- Part of the [Glossary](../glossary.md); see it for the other categories.
~~~

## Affine

**Definition:** An optic that focuses on zero or one values within a structure. Affine sits between Lens (exactly one) and Prism (zero or one for sum types) in the optic hierarchy. It combines the "might not be there" aspect of Prism with the "focus on part of a product" aspect of Lens.

**Core Operations:**
- `preview(S source)` - Try to extract the value (returns Optional)
- `set(A value, S source)` - Set the value if the focus exists

**Example:**
```java
// Affine for the first element of a list (might be empty)
Affine<List<String>, String> firstElement = Affine.affine(
    list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)),
    (newFirst, list) -> list.isEmpty() ? list :
        Stream.concat(Stream.of(newFirst), list.stream().skip(1)).toList()
);

List<String> items = List.of("a", "b", "c");
Optional<String> first = firstElement.preview(items);  // Optional["a"]
List<String> updated = firstElement.set("X", items);   // ["X", "b", "c"]

List<String> empty = List.of();
Optional<String> noFirst = firstElement.preview(empty);  // Optional.empty()
List<String> stillEmpty = firstElement.set("X", empty);  // [] (unchanged)
```

**When To Use:**
- Accessing elements that may not exist (first element, element at index)
- Optional fields in product types
- Composing Lens with Prism (result is Affine)

**Hierarchy Position:** `Iso → Lens → Affine → Traversal`
                        `Iso → Prism → Affine → Traversal`

**Related:** [Lens](#lens), [Prism](#prism), [Affine Documentation](../optics/affine.md)

---

## At

**Definition:** A type class for structures that support indexed access with insertion and deletion semantics. Provides a `Lens<S, Optional<A>>` where setting to `Optional.empty()` deletes the entry and setting to `Optional.of(value)` inserts or updates it.

**Core Operations:**
- `at(I index)` - Returns `Lens<S, Optional<A>>` for the index
- `get(I index, S source)` - Read value at index (returns Optional)
- `insertOrUpdate(I index, A value, S source)` - Insert or update entry
- `remove(I index, S source)` - Delete entry at index
- `modify(I index, Function<A,A> f, S source)` - Update value if present

**Example:**
```java
At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();

Map<String, Integer> scores = new HashMap<>(Map.of("alice", 100));

// Insert new entry
Map<String, Integer> withBob = mapAt.insertOrUpdate("bob", 85, scores);
// Result: {alice=100, bob=85}

// Remove entry
Map<String, Integer> noAlice = mapAt.remove("alice", withBob);
// Result: {bob=85}

// Compose with Lens for deep access
Lens<UserProfile, Optional<String>> themeLens =
    settingsLens.andThen(mapAt.at("theme"));
```

**When To Use:** CRUD operations on maps or lists where you need to insert new entries or delete existing ones whilst maintaining immutability and optics composability.

**Related:** [Indexed Access: At and Ixed Type Classes](../optics/indexed_access.md)

---

## Edits

**Definition:** A sparse, accumulating multi-edit over optics. `Edits.combine(...)` folds several pure edits (`set`, `modify`) into one reusable [Update](type-classes.md#update) at compile time; a fallible edit is rejected there, so a validation failure can never be silently dropped. `Edits.accumulate(...)` adds the validated REST-`PATCH` shape: it mixes pure and fallible edits, reports every bad field at once (each located as a [FieldError](#fielderror)), and applies the writes only if all validated. The `…IfPresent` factories treat `null` as absent, which is what makes a patch sparse.

**Example:**
```java
import static org.higherkindedj.optics.edit.Edit.*;

// Pure fold: several edits into one reusable Update<Order>
Update<Order> normalise = Edits.combine(
    modify(EMAIL, String::toLowerCase),
    modify(SKU,   String::trim));

// Validated PATCH: every bad field reported at once, only present fields written
Validated<NonEmptyList<FieldError>, Order> patched =
    Edits.accumulate(
            setIfPresent(ORDER_NUMBER, req.orderNumber()),
            parseIfPresent(EMAIL, req.email(), Email::parse),
            modifyIfPresent(QUANTITY, req.qtyDelta(), (delta, qty) -> qty + delta))
        .apply(order);
```

**Related:** [Multi-Edit and Sparse Updates](../optics/multi_edit.md), [Update](type-classes.md#update), [FieldError](#fielderror), [ValidatedPrism](#validatedprism)

---

## FieldError

**Definition:** A single validation failure carrying a composable path to the offending field plus a message. It is a small record (path segments plus a message) with a `pathString()` such as `"address.zip"`. Accumulating validation collects `FieldError`s into a [NonEmptyList](data-effects.md#nonemptylist) in declaration order, so a failed parse reports *which* fields were wrong and *where* they sit in a nested structure, not merely that validation failed.

**Example:**
```java
FieldError bare    = FieldError.of("not a postcode");   // unlocated leaf
FieldError located = bare.at("zip").at("address");      // pathString() == "address.zip"
```

**Located automatically:** `Validated.fields()` and the `parseIfPresent` edits prepend the field label onto each error's path, so a leaf validator creates unlocated `FieldError.of(...)`s and the assembly attaches the location. `hkj-test` ships `assertThatFieldError`.

**Related:** [Open-Arity Assembly](../monads/validated_assembly.md), [NonEmptyList](data-effects.md#nonemptylist), [Validated Assembly](#validated-assembly), [ValidatedPrism](#validatedprism)

---

## Focus DSL

**Definition:** A domain-specific language for fluent, type-safe navigation and manipulation of immutable data structures. The Focus DSL provides a composable way to build paths through nested records without manual lens composition.

**Core Concept:** Instead of composing optics manually, the Focus DSL lets you chain `.focus()` calls to navigate through data structures, with the optic types inferred automatically.

**Example:**
```java
// Without Focus DSL: manual lens composition
Lens<Employee, String> streetLens =
    EmployeeLenses.company()
        .andThen(CompanyLenses.address())
        .andThen(AddressLenses.street());
String street = streetLens.get(employee);

// With Focus DSL: fluent navigation
String street = Focus.on(employee)
    .focus(EmployeeFocus.company())
    .focus(CompanyFocus.address())
    .focus(AddressFocus.street())
    .get();

// Modification is equally fluent
Employee updated = Focus.on(employee)
    .focus(EmployeeFocus.company())
    .focus(CompanyFocus.address())
    .focus(AddressFocus.city())
    .modify(String::toUpperCase);

// Mix with Effect Paths for effectful navigation
EitherPath<Error, String> city = userService.findById(id)
    .focus(UserFocus.address())
    .focus(AddressFocus.city());
```

**Key Features:**
- Type-safe: Compiler catches invalid paths
- Composable: Chain any optic types together
- Generated: `@GenerateLenses` creates Focus helpers automatically
- Effect integration: Seamlessly works with Effect Paths

**Related:** [FocusPath](#focuspath), [Lens](#lens), [Effect-Optics Bridge](effect-paths.md#effect-optics-bridge), [Focus DSL Documentation](../optics/focus_dsl.md)

---

## FocusPath

**Definition:** A generated helper class that provides pre-composed optic paths for navigating into record types. The annotation processor creates FocusPath classes for each annotated record, offering a fluent API for accessing fields and nested structures.

**Generation:** Add `@GenerateLenses` to your record to generate the corresponding Focus class.

**Example:**
```java
@GenerateLenses
public record User(String name, Address address, List<Order> orders) {}

@GenerateLenses
public record Address(String street, String city, String postcode) {}

// Generated: UserFocus class with methods:
// - UserFocus.name()     → Lens<User, String>
// - UserFocus.address()  → Lens<User, Address>
// - UserFocus.orders()   → Lens<User, List<Order>>

// Use in Focus DSL
String city = Focus.on(user)
    .focus(UserFocus.address())
    .focus(AddressFocus.city())
    .get();

// Compose for reusable paths
Lens<User, String> userCity = UserFocus.address()
    .andThen(AddressFocus.city());

// Use with Effect Paths
EitherPath<Error, String> cityPath = loadUser(id)
    .focus(UserFocus.address())
    .focus(AddressFocus.city());
```

**Naming Convention:**
- Record `Foo` generates `FooFocus` class
- Each field `bar` generates static method `FooFocus.bar()`

**Related:** [Focus DSL](#focus-dsl), [Lens](#lens), [Code Generation](../optics/annotations_at_a_glance.md)

---

## Fold

**Definition:** A read-only optic that extracts zero or more values from a structure. Folds are like Traversals but without the ability to modify. They generalise the concept of "folding" or "reducing" over a structure.

**Core Operations:**
- `foldMap(Monoid<M> monoid, Function<A, M> f, S source)` - Map and combine all values
- `toList(S source)` - Extract all focused values as a list
- `headOption(S source)` - Get the first value if any
- `exists(Predicate<A> p, S source)` - Check if any value satisfies predicate
- `all(Predicate<A> p, S source)` - Check if all values satisfy predicate

**Example:**
```java
// Fold over all players in a league
Fold<League, Player> allPlayers = LeagueFolds.teams()
    .andThen(TeamFolds.players());

// Extract all players
List<Player> players = allPlayers.toList(league);

// Sum all scores using a Monoid
Integer totalScore = allPlayers.foldMap(
    Monoids.integerAddition(),
    Player::score,
    league
);

// Check conditions across all values
boolean anyInactive = allPlayers.exists(p -> !p.isActive(), league);
boolean allQualified = allPlayers.all(p -> p.score() >= 100, league);
```

**When To Use:**
- Extracting multiple values without modification
- Aggregating data from nested structures
- Querying collections within complex types
- When you need read-only access to multiple elements

**Related:** [Traversal](#traversal), [Getter (Fold of one)](#lens)

---

## @GenerateAssembly

**Definition:** The codegen companion for [Validated Assembly](#validated-assembly). Annotate a record and the processor emits a same-package `…Assembly` companion with one order-enforcing method per component, so assembly is discovered by autocomplete and the canonical constructor is baked in. A component typed as another annotated record accepts its sub-companion's result directly.

**Example:**
```java
@GenerateAssembly
public record User(Name name, Email email) {}

Validated<NonEmptyList<FieldError>, User> user =
    UserAssembly.fields()
        .name(parseName(dto.name()))
        .email(parseEmail(dto.email()))
        .assemble();      // canonical constructor baked in
```

**Related:** [Open-Arity Assembly](../monads/validated_assembly.md), [Validated Assembly](#validated-assembly), [FieldError](#fielderror)

---

## @GenerateErrorEnvelope

**Definition:** The third record-mapping processor, targeting the typed domain error a fallible mapping produces. A sealed error hierarchy usually re-declares the same envelope (`code`, `message`, `timestamp`, `context`) on every variant, with `context` an untyped `Map<String, Object>`. `@GenerateErrorEnvelope` supplies the envelope and **types** the context: each variant declares only its domain fields plus one `ErrorEnvelope<C>` component, and the processor generates the `…s` companion (per-variant factories, a typed `context()` builder, and an `editContext` wither). Context is records-as-schema (`context.orderId()`, not `map.get(...)`); timestamps read from a [TimeSource](data-effects.md#timesource) for deterministic tests.

**Example:**
```java
record OrderErrorContext(@Nullable OrderId orderId, @Nullable TraceId traceId) {}

@GenerateErrorEnvelope
public sealed interface OrderError {
  ErrorEnvelope<OrderErrorContext> envelope();                  // declared once
  record OutOfStock(List<ProductId> products,
                    ErrorEnvelope<OrderErrorContext> envelope) implements OrderError {}
}

OrderError error = OrderErrors.outOfStock(products)
    .editContext(ctx -> ctx.orderId(orderId).traceId(traceId));  // typed context, not map.put
```

**Related:** [Record Mapping](../optics/record_mapping.md#generating-error-envelopes-generateerrorenvelope), [TimeSource](data-effects.md#timesource), [@GenerateMapping](#generatemapping)

---

## @GenerateMapping

**Definition:** An annotation processor for the record-to-DTO boundary. Annotate an interface extending `MappingSpec<Domain, Wire>` and the processor generates, reflection-free at compile time, a total `build` (domain to wire) plus an accumulating `parse` (wire to domain) returning `Validated<NonEmptyList<FieldError>, Domain>`, so a bad DTO reports every bad field at once. Components match by name and type; `@MapField` declares renames, and `List`/`Optional`/`Map` containers lift automatically. The annotation sits on *your* spec interface, so third-party records map without being annotatable.

**Example:**
```java
@GenerateMapping
public interface PersonMapping extends MappingSpec<Person, PersonDto> {}

PersonDto dto = PersonMappingImpl.INSTANCE.build(person);          // total
Validated<NonEmptyList<FieldError>, Person> back =
    PersonMappingImpl.INSTANCE.parse(dto);                        // accumulating, located
```

**Truthful emission tiers:** the generated mapper offers only what the shape supports: `asIso` when lossless, `asLens` when total one way, and the accumulating `parse` otherwise. Every tier is law-checked against the published `hkj-test` harness.

**Related:** [Record Mapping](../optics/record_mapping.md), [ValidatedPrism](#validatedprism), [FieldError](#fielderror), [@GenerateMerge](#generatemerge)

---

## @GenerateMerge

**Definition:** The forward-only sibling of [@GenerateMapping](#generatemapping): assemble one target record from **several** sources, declared entirely by the spec method's signature, with no class literals and no inverse. Each target component fills from the one source with a same-named component: identity when the types match, through a [ValidatedPrism](#validatedprism) leaf when they differ, or through a sibling `@GenerateMapping` spec (failures locating as dotted paths). Ambiguous or unfilled components are what/why/fix compile errors, and the return type must tell the truth: a fallible fill demands a `Validated` return.

**Example:**
```java
@GenerateMerge
public interface DashboardAssembly {
  Dashboard assemble(User user, Account account, Settings settings);
}

Dashboard dashboard =
    DashboardAssemblyImpl.INSTANCE.assemble(user, account, settings);
```

**Related:** [Record Mapping](../optics/record_mapping.md), [@GenerateMapping](#generatemapping), [ValidatedPrism](#validatedprism)

---

## Iso (Isomorphism)

**Definition:** An optic representing a lossless, bidirectional conversion between two types. If you can convert `A` to `B` and back to `A` without losing information, you have an isomorphism.

**Core Operations:**
- `get(S source)` - Convert from S to A
- `reverseGet(A value)` - Convert from A to S

**Example:**
```java
// String and List<Character> are isomorphic
Iso<String, List<Character>> stringToChars = Iso.iso(
    s -> s.chars().mapToObj(c -> (char) c).collect(Collectors.toList()),
    chars -> chars.stream().map(String::valueOf).collect(Collectors.joining())
);

List<Character> chars = stringToChars.get("Hello");  // ['H', 'e', 'l', 'l', 'o']
String back = stringToChars.reverseGet(chars);       // "Hello"
```

**When To Use:** Converting between equivalent representations (e.g., Celsius/Fahrenheit, String/ByteArray, domain models and DTOs with no information loss).

**Related:** [Iso Documentation](../optics/iso.md)

---

## Lens

**Definition:** An optic for working with product types (records with fields). Provides a composable way to get and set fields in immutable data structures.

**Core Operations:**
- `get(S source)` - Extract a field value
- `set(A newValue, S source)` - Create a new copy with updated field
- `modify(Function<A,A> f, S source)` - Update field using a function

**Example:**
```java
@GenerateLenses
public record Address(String street, String city) {}

@GenerateLenses
public record Company(String name, Address address) {}

@GenerateLenses
public record Employee(String name, Company company) {}

// Compose lenses for deep updates
Lens<Employee, String> employeeToStreet =
    EmployeeLenses.company()
        .andThen(CompanyLenses.address())
        .andThen(AddressLenses.street());

// Update nested field in one line
Employee updated = employeeToStreet.set("456 New St", originalEmployee);
```

**Related:** [Lenses Documentation](../optics/lenses.md)

---

## Parse, Don't Validate

**Definition:** The principle that a boundary should turn unstructured input into a typed value **once**, at the edge, and keep that guarantee in the type thereafter, rather than re-checking the same data repeatedly downstream. Higher-Kinded-J expresses it with types whose *parse* is fallible and accumulating and whose *build* is total: [ValidatedPrism](#validatedprism) for a single value, [Validated Assembly](#validated-assembly) for a whole record, and [@GenerateMapping](#generatemapping) for a record-to-DTO boundary. Failures are [FieldError](#fielderror)s, so a rejected input reports every bad field at once, each located.

**Related:** [Record Mapping](../optics/record_mapping.md), [ValidatedPrism](#validatedprism), [Validated](data-effects.md#validated)

---

## Prism

**Definition:** An optic for working with sum types (sealed interfaces, Optional, Either). Provides safe access to specific variants within a discriminated union.

**Core Operations:**
- `preview(S source)` - Try to extract a variant (returns Optional)
- `review(A value)` - Construct the sum type from a variant
- `modify(Function<A,A> f, S source)` - Update if variant matches

**Example:**
```java
@GeneratePrisms
public sealed interface PaymentMethod {
    record CreditCard(String number) implements PaymentMethod {}
    record BankTransfer(String iban) implements PaymentMethod {}
}

Prism<PaymentMethod, String> creditCardPrism =
    PaymentMethodPrisms.creditCard().andThen(CreditCardLenses.number());

// Safe extraction
Optional<String> cardNumber = creditCardPrism.preview(payment);

// Conditional update
PaymentMethod masked = creditCardPrism.modify(num -> "****" + num.substring(12), payment);
```

**Related:** [Prisms Documentation](../optics/prisms.md), [ValidatedPrism](#validatedprism)

---

## Setter

**Definition:** A write-only optic that can modify zero or more values within a structure. Setters are the dual of Folds: where Folds can only read, Setters can only write. They cannot extract values, only transform them.

**Core Operations:**
- `modify(Function<A, A> f, S source)` - Apply function to all focused values
- `set(A value, S source)` - Set all focused values to same value

**Example:**
```java
// Setter for all prices in an order
Setter<Order, BigDecimal> allPrices = OrderSetters.items()
    .andThen(LineItemSetters.price());

// Apply discount to all prices
Order discounted = allPrices.modify(
    price -> price.multiply(new BigDecimal("0.9")),
    order
);

// Set all prices to zero (for testing)
Order zeroed = allPrices.set(BigDecimal.ZERO, order);

// Compose with other optics
Setter<Company, String> allEmployeeEmails =
    CompanySetters.departments()
        .andThen(DepartmentSetters.employees())
        .andThen(EmployeeSetters.email());

Company normalised = allEmployeeEmails.modify(String::toLowerCase, company);
```

**When To Use:**
- Bulk modifications without needing to read values
- Applying transformations across nested structures
- When modification logic doesn't depend on current values
- Composing write-only operations

**Related:** [Traversal](#traversal), [Fold](#fold)

---

## Traversal

**Definition:** An optic for working with multiple values within a structure (lists, sets, trees). Allows bulk operations on all elements.

**Core Operations:**
- `modifyF(Applicative<F> app, Function<A, Kind<F,A>> f, S source)` - Effectful modification of all elements
- `toList(S source)` - Extract all focused values as a list

**Example:**
```java
@GenerateLenses
public record Order(String id, List<LineItem> items) {}

Traversal<Order, LineItem> orderItems =
    OrderLenses.items().asTraversal();

// Apply bulk update
Order discounted = orderItems.modify(
    item -> item.withPrice(item.price() * 0.9),
    order
);
```

**Related:** [Traversals Documentation](../optics/traversals.md)

---

## Validated Assembly

**Definition:** Open-arity assembly of a record from N independently validated fields, with every error collected and no `Semigroup` argument, no arity wall, and no `Kind` ceremony. `Validated.fields()` opens a labelled assembly over `NonEmptyList<FieldError>`; each `field(label, value)` adds one validated field, and `apply(...)` completes it with a constructor reference of exactly the accumulated arity. The same shape exists across three carriers: `Validated` (strict), `ValidationPath` (railway, via `Path.fields()`), and `EitherOrBoth` (tolerant).

**Example:**
```java
Validated<NonEmptyList<FieldError>, User> user =
    Validated.fields()
        .field("name",  parseName(dto.name()))
        .field("email", parseEmail(dto.email()))
        .apply(User::new);
// Invalid(NonEmptyList[email: not an email address]), or Valid(user)
```

**Related:** [Open-Arity Assembly](../monads/validated_assembly.md), [@GenerateAssembly](#generateassembly), [FieldError](#fielderror), [Validated](data-effects.md#validated), [EitherOrBoth](data-effects.md#eitherorboth)

---

## ValidatedPrism

**Definition:** The smart-constructor optic for *parse, don't validate* boundaries. Its `parse` returns `Validated<NonEmptyList<FieldError>, A>`, so every failure is located rather than only the first, whilst `build` is total and always succeeds. It is the accumulating counterpart to a [Prism](#prism), whose `preview` reports only presence or absence.

**Example:**
```java
ValidatedPrism<String, EmailAddress> email = ValidatedPrism.of(
    raw -> parseEmail(raw),        // String -> Validated<NonEmptyList<FieldError>, EmailAddress>
    EmailAddress::toString);       // total build

Validated<NonEmptyList<FieldError>, EmailAddress> parsed = email.parse("  NOPE ");
String rendered = email.build(addr);   // always succeeds
```

**Composition:** nested composition short-circuits whilst sibling fields accumulate, so a whole record parses in one pass with every bad field reported. `ValidatedPrism.fromIso(iso)` is a parse that never fails; `ValidatedPrism.fromPrism(prism, reason)` lifts a plain prism by supplying the reason its empty case cannot express. Both round-trip laws ship as `ValidatedPrismLaws` in `hkj-test`.

**Related:** [ValidatedPrism](../optics/validated_prism.md), [Prism](#prism), [FieldError](#fielderror), [Validated](data-effects.md#validated)

