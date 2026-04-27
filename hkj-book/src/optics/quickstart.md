# Optics Quickstart

~~~admonish info title="What You'll Learn"
- How `@GenerateLenses` and `@GenerateFocus` collapse a deep nested update into one line
- How `@GeneratePrisms` and `@GenerateTraversals` extend the same pattern to sum types and collections
- How `@ImportOptics` brings external library types (Jackson `JsonNode`) into the same world
- Where to read next depending on what you want to do
~~~

This page assumes you have Higher-Kinded-J on your classpath. If not, the [book-level Quickstart](../quickstart.md) covers setup; the [HKJ Gradle/Maven plugin](../tooling/gradle_plugin.md) wires the annotation processor in automatically.

You do **not** need to understand higher-kinded types, profunctors, or `Applicative` to use any of the code on this page. The annotations generate plain Java classes you call with familiar method chains.

---

## 1. From cascade to one-liner

The conventional Java approach to updating a field three layers deep:

```java
public User updateStreetName(User user, String newStreetName) {
    var address = user.address();
    var street  = address.street();
    var newStreet  = new Street(newStreetName, street.number());
    var newAddress = new Address(newStreet, address.city());
    return new User(user.name(), newAddress);
}
```

With `@GenerateLenses` and `@GenerateFocus` on each record, the same operation becomes one line:

```java
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateFocus;

@GenerateLenses @GenerateFocus
public record Street(String name, int number) {}

@GenerateLenses @GenerateFocus
public record Address(Street street, String city) {}

@GenerateLenses @GenerateFocus
public record User(String name, Address address) {}
```

```java
User updated = UserFocus.address().street().name().set("New Street", user);
```

The annotation processor runs at compile time and produces `StreetLenses`, `StreetFocus`, `AddressLenses`, `AddressFocus`, `UserLenses`, and `UserFocus` for you. There is no reflection at runtime; the path you wrote is just a chain of typed method calls.

~~~admonish tip title="Why two annotations?"
`@GenerateLenses` produces classic lenses (`UserLenses.address()`) plus `withFoo` helpers for shallow updates. `@GenerateFocus` adds the path-based DSL (`UserFocus.address().street().name()`) for deep navigation. Most records benefit from both.
~~~

---

## 2. Sum types and collections, the same way

Sealed interfaces and collection fields use the same annotation-driven pattern.

```java
import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.optics.annotations.*;

@GeneratePrisms
public sealed interface Status permits Pending, Shipped, Cancelled {
    record Pending() implements Status {}
    record Shipped(java.time.Instant at) implements Status {}
    record Cancelled(String reason) implements Status {}
}

@GenerateLenses @GenerateFocus
public record LineItem(String sku, BigDecimal price) {}

@GenerateLenses @GenerateFocus @GenerateTraversals
public record Order(String id, Status status, List<LineItem> items) {}
```

**Apply a 10% discount to every line item:**

```java
Order discounted = OrderFocus.items().each().price()
    .modifyAll(p -> p.multiply(new BigDecimal("0.9")), order);
```

`OrderFocus.items().each()` walks every element of the `List<LineItem>`. Continuing with `.price()` zooms each element down to the price field. `modifyAll` applies the function in one pass and returns a new `Order`.

**Match only `Pending` orders:**

```java
boolean isPending = StatusPrisms.pending().matches(order.status());

Status fulfilled = StatusPrisms.pending()
    .modify(p -> new Status.Shipped(java.time.Instant.now()), order.status());
```

A `Prism` is a `Lens` for sum types: it succeeds when the variant matches and is a no-op otherwise.

~~~admonish note title="Two views of the same record"
We added three annotations to `Order`. They don't conflict; each generates its own companion class (`OrderLenses`, `OrderFocus`, `OrderTraversals`) and you pick the entry point that matches your task.
~~~

---

## 3. Annotating types you don't own

External types like JDK classes, Jackson nodes, JOOQ records, and Protobuf messages can't be annotated directly. Higher-Kinded-J solves this with `OpticsSpec`: you declare the optics you want as an interface, and the processor generates them.

Here's a real example for Jackson 3.x's `JsonNode`:

```java
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.InstanceOf;
import org.higherkindedj.optics.annotations.OpticsSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

@ImportOptics
public interface JsonNodeOpticsSpec extends OpticsSpec<JsonNode> {

    @InstanceOf(ObjectNode.class) Prism<JsonNode, ObjectNode> object();
    @InstanceOf(ArrayNode.class)  Prism<JsonNode, ArrayNode>  array();
    @InstanceOf(StringNode.class) Prism<JsonNode, StringNode> text();
}
```

The processor reads the spec and generates a `JsonNodeOptics` class (the `Spec` suffix is dropped) with three prisms backed by `instanceof` pattern matching:

```java
import java.util.Optional;

JsonNode response = mapper.readTree(json);

Optional<ArrayNode> items = JsonNodeOptics.array()
    .getOptional(response.get("items"));

Optional<StringNode> firstName = items
    .flatMap(arr -> JsonNodeOptics.object().getOptional(arr.get(0)))
    .flatMap(obj -> JsonNodeOptics.text().getOptional(obj.get("name")));
```

Same composition, same vocabulary, applied to a type you can't modify. See [Taming JSON with Jackson](optics_spec_interfaces.md) for the full pattern, including how to handle Jackson's predicate-based type-checking APIs with `@MatchWhen`.

---

## Where next?

- **Looking up an annotation?** [Annotations at a Glance](annotations_at_a_glance.md) lists every `@Generate*` and `@OpticsSpec` hint with its target and what it produces.
- **Just want to update a nested record?** Continue with the [Focus DSL](focus_dsl.md).
- **Choosing between Focus DSL, Fluent API, or Free Monad DSL?** The [Java-Friendly APIs](ch4_intro.md) chapter has a decision tree.
- **Choosing which optic for your data shape?** [Integration and Recipes](ch5_intro.md) carries a flowchart and a complete pipeline example.
- **Theory first?** [What Are Optics?](optics_intro.md) is the conceptual introduction.

~~~admonish tip title="Ready for hands-on?"
The [Optics Tutorial Track](../tutorials/optics/ch_intro.md) is exercise-driven. Four journeys (~150 minutes total, 108 exercises) cover Lens & Prism, Traversals, Fluent & Free DSL, and the Focus DSL. Recommended once you've finished this Quickstart.
~~~

---

**Previous:** [Optics](ch_intro.md)
**Next:** [Annotations at a Glance](annotations_at_a_glance.md)
