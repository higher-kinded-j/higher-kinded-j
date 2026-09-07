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

<!-- verify -->
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

<!-- verify -->
```java
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateFocus;

@GenerateLenses @GenerateFocus(generateNavigators = true)
public record Street(String name, int number) {}

@GenerateLenses @GenerateFocus(generateNavigators = true)
public record Address(Street street, String city) {}

@GenerateLenses @GenerateFocus(generateNavigators = true)
public record User(String name, Address address) {}
```

<!-- verify -->
```java
User updated = UserFocus.address().street().name().set("New Street", user);
```

The annotation processor runs at compile time and produces `StreetLenses`, `StreetFocus`, `AddressLenses`, `AddressFocus`, `UserLenses`, and `UserFocus` for you. There is no reflection at runtime; the path you wrote is just a chain of typed method calls.

~~~admonish tip title="Why two annotations?"
`@GenerateLenses` produces classic lenses (`UserLenses.address()`) plus `withFoo` helpers for shallow updates. `@GenerateFocus` adds the path-based DSL (`UserFocus.address()`) for deep navigation, and `generateNavigators = true` is what lets the next hop chain off it as `.street()` rather than `.via(AddressFocus.street())`. Most records benefit from both annotations.
~~~

---

## 2. Sum types and collections, the same way

Sealed interfaces and collection fields use the same annotation-driven pattern.

<!-- verify -->
```java
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.higherkindedj.optics.annotations.*;

@GeneratePrisms
public sealed interface Status permits Status.Pending, Status.Shipped, Status.Cancelled {
    record Pending() implements Status {}
    record Shipped(Instant at) implements Status {}
    record Cancelled(String reason) implements Status {}
}

@GenerateLenses @GenerateFocus
public record LineItem(String sku, BigDecimal price) {}

@GenerateLenses @GenerateFocus @GenerateTraversals
public record Order(String id, Status status, List<LineItem> items) {}
```

**Apply a 10% discount to every line item:**

<!-- verify -->
```java
Order discounted = OrderFocus.items().via(LineItemFocus.price())
    .modifyAll(p -> p.multiply(new BigDecimal("0.9")), order);
```

`OrderFocus.items()` already walks every element of the `List<LineItem>`: the generated accessor ends in `.each()` for you, so it hands back a path focused on a `LineItem`, not on the list. `.via(LineItemFocus.price())` zooms each element down to the price field, and `modifyAll` applies the function in one pass to return a new `Order`.

A `List` field widens through the built-in traversal, which returns a plain path, so the next hop is `.via(...)`. `generateNavigators = true` shortens that to `.price()` for a field whose type is itself navigable, such as the `Address` in section 1.

~~~admonish warning title="Do not add your own `.each()`"
`OrderFocus.items().each()` compiles and then fails at run time. The accessor is already element-level, so the extra `each()` tries to read a `LineItem` as a `List`; its type parameter is inferred from whatever you assign it to, which is why the compiler lets it through. `TraversalPath.each()` is for a focus that is *itself* a list, and its javadoc documents the `ClassCastException`.
~~~

**Match only `Pending` orders:**

<!-- verify -->
```java
boolean isPending = StatusPrisms.pending().matches(order.status());

// modify rebuilds the variant it narrowed to, so the function is Cancelled -> Cancelled.
Status tidied = StatusPrisms.cancelled()
    .modify(c -> new Status.Cancelled(c.reason().strip()), order.status());

// Moving to a different variant is not a modify. Read through the prism, then build.
Status fulfilled = StatusPrisms.pending()
    .getOptional(order.status())
    .<Status>map(pending -> new Status.Shipped(Instant.now()))
    .orElse(order.status());
```

A `Prism` is the sum-type counterpart of a lens: it succeeds when the variant matches and is a no-op otherwise. Note what `modify` will and will not do. Its function is `A -> A`, so it rebuilds the *same* variant; changing `Pending` into `Shipped` is a read followed by a build, not a modification.

~~~admonish note title="Two views of the same record"
We added three annotations to `Order`. They don't conflict; each generates its own companion class (`OrderLenses`, `OrderFocus`, `OrderTraversals`) and you pick the entry point that matches your task.
~~~

---

## 3. Annotating types you don't own

External types like JDK classes, Jackson nodes, JOOQ records, and Protobuf messages can't be annotated directly. Higher-Kinded-J solves this with `OpticsSpec`: you declare the optics you want as an interface, and the processor generates them.

Here's a real example for Jackson 3.x's `JsonNode`:

<!-- verify -->
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

<!-- verify -->
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

~~~admonish info title="Key Takeaways"
* **The annotations generate plain Java at compile time.** `XLenses`, `XFocus`, `XPrisms` and `XTraversals` are ordinary classes you call with ordinary method chains. Nothing here uses runtime reflection.
* **`@GenerateLenses` and `@GenerateFocus` are the pair to reach for.** Lenses give you `withFoo` and the classic optics; Focus gives you the path DSL. `generateNavigators = true` is what lets the next hop read as `.street()` rather than `.via(AddressFocus.street())`.
* **A collection accessor is already element-level.** `OrderFocus.items()` focuses a `LineItem`, because the generated method ends in `.each()` for you. Adding another `.each()` compiles and then fails at run time.
* **A prism rebuilds the variant it narrowed to.** `modify` is `A -> A`, so it cannot turn a `Pending` into a `Shipped`; that is a read through the prism followed by building the new variant.
* **Types you do not own join the same vocabulary.** An `OpticsSpec` interface plus `@ImportOptics` generates optics for `JsonNode`, JOOQ records or JDK types, and they compose with everything else.
~~~

---

~~~admonish tip title="See Also"
- [Annotations at a Glance](annotations_at_a_glance.md): every `@Generate*` and spec hint, with its target and what it produces
- [Focus DSL](focus_dsl.md): the path-based API this page previews, in full
- [Java-Friendly APIs](ch4_intro.md): choosing between the Focus DSL, the Fluent API and the Free Monad DSL
- [What Are Optics?](optics_intro.md): the conceptual introduction, if you would rather start with the idea
- [Record Mapping](../mapping/ch_intro.md): the domain to wire boundary, which needs none of this chapter first
~~~

~~~admonish tip title="Ready for hands-on?"
The [Optics Tutorial Track](../tutorials/optics/ch_intro.md) is exercise-driven. Six journeys (~225 minutes total, 134 exercises) run from Lens & Prism through the Focus DSL to batching, coupled updates, and the generated DTO boundary. Recommended once you've finished this Quickstart.
~~~

---

**Previous:** [Optics](ch_intro.md)
**Next:** [Annotations at a Glance](annotations_at_a_glance.md)
