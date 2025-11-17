# Optics - Basic Usage Examples

~~~admonish info title="What You'll Learn"
- Practical application patterns for optics across diverse problem domains
- Building configuration processors, data validators, and API adapters with optics
- Creating reusable optic libraries tailored to your specific business needs
- Performance Optimisation techniques and benchmarking for complex optic compositions
- Testing strategies for optic-based data processing pipelines
- Decision frameworks for choosing the right optic combinations for real-world scenarios
- Common anti-patterns to avoid and best practices for maintainable optic code
~~~

~~~admonish title="Example Code"
This document provides a brief summary of the example classes found in the  `org.higherkindedj.example.optics` package in the [HKJ-Examples](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics).
~~~

These examples showcase how to use the code generation features (`@GenerateLenses`, `@GeneratePrisms`, `@GenerateTraversals`) and the resulting optics to work with immutable data structures in a clean and powerful way.

## [LensUsageExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/LensUsageExample.java)

This example is the primary introduction to **Lenses**. It demonstrates how to automatically generate `Lens` optics for immutable records and then compose them to read and update deeply nested fields.

* **Key Concept**: A `Lens` provides a focus on a single field within a product type (like a record or class).
* **Demonstrates**:
  * Defining a nested data model (`League`, `Team`, `Player`).
  * Using `@GenerateLenses` on records to trigger code generation.
  * Accessing generated Lenses (e.g., `LeagueLenses.teams()`).
  * Composing Lenses with `andThen()` to create a path to a deeply nested field.
  * Using `get()` to read a value and `set()` to perform an immutable update.

```java
// Composing lenses to focus from League -> Team -> name
Lens<League, String> leagueToTeamName = LeagueLenses.teams().andThen(TeamLenses.name());

// Use the composed lens to get and set a value
String teamName = leagueToTeamName.get(league);
League updatedLeague = leagueToTeamName.set("New Team Name").apply(league);
```

## [PrismUsageExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PrismUsageExample.java)

This example introduces **Prisms**. It shows how to generate optics for a sealed interface (a sum type) and use the resulting `Prism` to focus on a specific implementation of that interface.

* **Key Concept**: A `Prism` provides a focus on a specific case within a sum type (like a sealed interface or enum). It succeeds if the object is an instance of that case.
* **Demonstrates**:
  * Defining a `sealed interface` (`Shape`) with different implementations (`Rectangle`, `Circle`).
  * Using `@GeneratePrisms` on the sealed interface.
  * Using the generated `Prism` to safely "get" an instance of a specific subtype.
  * Using `modify()` to apply a function only if the object is of the target type.

```java
  // Get the generated prism for the Rectangle case
  Prism<Shape, Rectangle> rectanglePrism = ShapePrisms.rectangle();
  
  // Safely attempt to modify a shape, which only works if it's a Rectangle
  Optional<Shape> maybeUpdated = rectanglePrism.modify(r -> new Rectangle(r.width() + 10, r.height()))
                                    .apply(new Rectangle(5, 10)); // Returns Optional[Rectangle[width=15, height=10]]
  
  Optional<Shape> maybeNotUpdated = rectanglePrism.modify(...)
                                       .apply(new Circle(20.0)); // Returns Optional.empty
```

## [TraversalUsageExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TraversalUsageExample.java)

This example showcases the power of composing **Traversals** and **Lenses** to perform bulk updates on items within nested collections.

* **Key Concept**: A `Traversal` provides a focus on zero or more elements, such as all items in a `List` or all values in a `Map`.
* **Demonstrates**:
  * Using `@GenerateTraversals` to create optics for fields that are collections (`List<Team>`, `List<Player>`).
  * Composing a `Traversal` with another `Traversal` and a `Lens` to create a single optic that focuses on a field within every element of a nested collection.
  * Using `modifyF()` with the `Id` monad to perform a pure, bulk update (e.g., adding bonus points to every player's score).

```java
  // Compose a path from League -> each Team -> each Player -> score
  Traversal<League, Integer> leagueToAllPlayerScores =
      LeagueTraversals.teams()
          .andThen(TeamTraversals.players())
          .andThen(PlayerLenses.score());

  // Use the composed traversal to add 5 to every player's score
  var updatedLeague = IdKindHelper.ID.narrow(
      leagueToAllPlayerScores.modifyF(
          score -> Id.of(score + 5), league, IdMonad.instance()
      )
  ).value();
```

## [PartsOfTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PartsOfTraversalExample.java)

This example demonstrates the **partsOf** combinator for list-level manipulation of traversal focuses. It shows how to convert a `Traversal` into a `Lens` on a `List`, enabling powerful operations like sorting, reversing, and deduplicating focused elements whilst maintaining structure integrity.

* **Key Concept**: `partsOf` bridges element-wise traversal operations and collection-level algorithms by treating all focuses as a single list.
* **Demonstrates**:
  * Converting a `Traversal<S, A>` into a `Lens<S, List<A>>` with `Traversals.partsOf()`.
  * Extracting all focused elements as a list for group-level operations.
  * Using convenience methods: `Traversals.sorted()`, `Traversals.reversed()`, `Traversals.distinct()`.
  * Custom comparator sorting (case-insensitive, by length, reverse order).
  * Combining `partsOf` with filtered traversals for selective list operations.
  * Understanding size mismatch behaviour (graceful degradation).
  * Real-world use case: normalising prices across an e-commerce catalogue.

```java
  // Convert traversal to lens on list of all prices
  Traversal<Catalogue, Double> allPrices = CatalogueTraversals.categories()
      .andThen(CategoryTraversals.products())
      .andThen(ProductLenses.price().asTraversal());
  Lens<Catalogue, List<Double>> pricesLens = Traversals.partsOf(allPrices);

  // Sort all prices across the entire catalogue
  Catalogue sortedCatalogue = Traversals.sorted(allPrices, catalogue);

  // Reverse prices (highest to lowest)
  Catalogue reversedCatalogue = Traversals.reversed(allPrices, sortedCatalogue);

  // Remove duplicate product names
  List<Product> deduplicatedProducts = Traversals.distinct(nameTraversal, products);

  // Sort only in-stock product prices (combining with filtered traversals)
  Traversal<List<Product>, Double> inStockPrices = Traversals.<Product>forList()
      .filtered(p -> p.stockLevel() > 0)
      .andThen(ProductLenses.price().asTraversal());
  List<Product> result = Traversals.sorted(inStockPrices, products);
```

## [FoldUsageExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/FoldUsageExample.java)

This example demonstrates **Folds** for read-only querying and data extraction from complex structures.

* **Key Concept**: A `Fold` is a read-only optic that focuses on zero or more elements, perfect for queries, searches, and aggregations without modification.
* **Demonstrates**:
  * Using `@GenerateFolds` to create query optics automatically.
  * Using `getAll()`, `preview()`, `find()`, `exists()`, `all()`, `isEmpty()`, and `length()` operations for querying data.
  * Composing folds for deep queries across nested structures.
  * Using standard monoids from `Monoids` utility class (`Monoids.doubleAddition()`, `Monoids.booleanAnd()`, `Monoids.booleanOr()`).
  * Using `foldMap` with monoids for custom aggregations (sum, product, boolean operations).
  * Contrasting Fold (read-only) with Traversal (read-write) to express intent clearly.

```java
  // Get all products from an order
  Fold<Order, ProductItem> items = OrderFolds.items();
  List<ProductItem> allProducts = items.getAll(order);

  // Check if any product is out of stock
  boolean hasOutOfStock = items.exists(p -> !p.inStock(), order);

  // Calculate total price using standard monoid from Monoids utility class
  Monoid<Double> sumMonoid = Monoids.doubleAddition();
  double total = items.foldMap(sumMonoid, ProductItem::price, order);

  // Use boolean monoids for condition checking
  Monoid<Boolean> andMonoid = Monoids.booleanAnd();
  boolean allAffordable = items.foldMap(andMonoid, p -> p.price() < 1000, order);

  // Compose folds for deep queries
  Fold<OrderHistory, ProductItem> allProductsInHistory =
      OrderHistoryFolds.orders().andThen(OrderFolds.items());
  List<ProductItem> allProds = allProductsInHistory.getAll(history);
```

## [ValidatedTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ValidatedTraversalExample.java)

This example demonstrates a more advanced use case for **Traversals** where the goal is to validate multiple fields on a single object and accumulate all errors.

* **Key Concept**: A `Traversal` can focus on multiple fields *of the same type* within a single object.
* **Demonstrates**:
  * Defining a `RegistrationForm` with several `String` fields.
  * Using `@GenerateTraversals` with a custom `name` parameter to create a single `Traversal` that groups multiple fields (`name`, `email`, `password`).
  * Using this traversal with `Validated` to run a validation function on each field.
  * Because `Validated` has an `Applicative` that accumulates errors, the end result is a `Validated` object containing either the original form or a list of all validation failures.


## [OpticProfunctorExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/profunctor/OpticProfunctorExample.java)

This comprehensive example demonstrates the **profunctor** capabilities of optics, showing how to adapt existing optics to work with different data types and structures.

* **Key Concept**: Every optic is a profunctor, meaning it can be adapted using `contramap`, `map`, and `dimap` operations to work with different source and target types.
* **Demonstrates**:

  * **Contramap-style adaptation**: Using an existing `Person` lens with `Employee` objects by providing a conversion function.
  * **Map-style adaptation**: Transforming the target type of a lens (e.g., `LocalDate` to formatted `String`).
  * **Dimap-style adaptation**: Converting between completely different data representations (e.g., internal models vs external DTOs).
  * **API Integration**: Creating adapters for external API formats whilst reusing internal optics.
  * **Type-safe wrappers**: Working with strongly-typed wrapper classes efficiently.

  ```java
  // Adapt a Person lens to work with Employee objects
  Lens<Person, String> firstNameLens = PersonLenses.firstName();
  Lens<Employee, String> employeeFirstNameLens = 
      firstNameLens.contramap(employee -> employee.personalInfo());

  // Adapt a lens to work with different target types
  Lens<Person, LocalDate> birthDateLens = PersonLenses.birthDate();
  Lens<Person, String> birthDateStringLens = 
      birthDateLens.map(date -> date.format(DateTimeFormatter.ISO_LOCAL_DATE));
  ```

## Traversal Examples

These examples focus on using generated traversals for specific collection and container types, often demonstrating "effectful" traversals where each operation can succeed or fail.

### [ListTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/traversal/list/ListTraversalExample.java)

* **Demonstrates**: Traversing a `List<String>` field.
* **Scenario**: A `Project` has a list of team members. The traversal is used with a `lookupUser` function that returns a `Validated` type. This allows validating every member in the list. If any lookup fails, the entire operation results in an `Invalid`.

### [ArrayTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/traversal/array/ArrayTraversalExample.java)

* **Demonstrates**: Traversing an `Integer[]` field.
* **Scenario**: A `Survey` has an array of answers. The traversal is used with a validation function to ensure every answer is within a valid range (1-5), accumulating errors with `Validated`.

### [SetTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/traversal/set/SetTraversalExample.java)

* **Demonstrates**: Traversing a `Set<String>` field.
* **Scenario**: A `UserGroup` has a set of member emails. The traversal validates that every email in the set has a valid format (`contains "@"`).

### [MapValueTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/traversal/map/MapValueTraversalExample.java)

* **Demonstrates**: Traversing the *values* of a `Map<String, Boolean>` field.
* **Scenario**: A `FeatureToggles` record holds a map of flags. The traversal focuses on every `Boolean` value in the map, allowing for a bulk update to disable all features at once.

### [EitherTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/traversal/either/EitherTraversalExample.java)

* **Demonstrates**: Traversing an `Either<String, Integer>` field.
* **Scenario**: A `Computation` can result in a success (`Right`) or failure (`Left`). The traversal shows that `modifyF` only affects the value if the `Either` is a `Right`, leaving a `Left` untouched.

### [MaybeTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/traversal/maybe/MaybeTraversalExample.java)

* **Demonstrates**: Traversing a `Maybe<String>` field.
* **Scenario**: A `Configuration` has an optional `proxyHost`. The traversal shows that an operation is only applied if the `Maybe` is a `Just`, leaving a `Nothing` untouched, which is analogous to the `Either` example.

### [OptionalTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/traversal/optional/OptionalTraversalExample.java)

* **Demonstrates**: Traversing a `java.util.Optional<String>` field.
* **Scenario**: A `User` record has an optional `middleName`. The traversal is used to apply a function (like `toUpperCase`) to the middle name only if it is present. This shows how to work with standard Java types in a functional way.

### [TryTraversalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/traversal/trymonad/TryTraversalExample.java)

* **Demonstrates**: Traversing a `Try<Integer>` field.
* **Scenario**: A `NetworkRequest` record holds the result of an operation that could have thrown an exception, wrapped in a `Try`. The traversal allows modification of the value only if the `Try` is a `Success`, leaving a `Failure` (containing an exception) unchanged.

**Previous:**[Profunctor Optics: Advanced Data Transformation](composing_optics.md)
**Next:**[Auditing Complex Data: The Power of Optics](auditing_complex_data_example.md)