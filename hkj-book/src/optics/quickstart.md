# Optics Quickstart

_Change a field three records deep in one line, then a list, a sealed type and a Jackson tree._

~~~admonish info title="What You'll Learn"
- Change a field three records deep with one generated path
- Apply one change to every element of a list inside a record
- Match one variant of a sealed type, and move to another without a `modify`
- Generate optics for a type you cannot annotate, such as Jackson's `JsonNode`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [QuickstartBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/optics/quickstart/QuickstartBook.java) and its [QuickstartBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/optics/quickstart/QuickstartBookTest.java)**: the page includes them directly, so the build compiles and runs them.
~~~

Your project needs Java 25 and the annotation processor `hkj-processor`, which the HKJ build plugin wires in; the [chapter introduction](ch_intro.md) sets both out.

You do **not** need to understand higher-kinded types, profunctors, or `Applicative` to use any of the code on this page. The annotations generate plain Java classes you call with familiar method chains.

---

## 1. Annotate, then update

The chapter's running cast is an order service. Annotate each record:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Order.java:order}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Customer.java:customer}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/EmailAddress.java:email_address}}
```

Then a field three records deep, the customer's email address, is one line:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/quickstart/QuickstartBook.java:one_liner}}
```

The annotation processor runs at compile time and produces `OrderLenses`, `OrderFocus`, `CustomerLenses`, `CustomerFocus`, `EmailAddressLenses` and `EmailAddressFocus` for you, and `OrderTraversals` for the list of lines. There is no reflection at runtime; the path you wrote is just a chain of typed method calls.

Each of those methods returns a *lens*: a record's accessor paired with a copy that replaces that one component through the canonical constructor. A path joins lenses end to end, so a write three records down rebuilds each record on the way, and reuses everything off the path.

~~~admonish example title="What the processor wrote" collapsible=true
A generated lens class is plain Java. This one comes from the processor's own tests, for a four-field `Customer(id, name, email, loyaltyPoints)` rather than the cast's: it holds one static method per component and a `withX` helper per component. The processor's tests compare this file line for line against what it generates. It was generated through `@ImportOptics`, which is why it has a package of its own; `@GenerateLenses` writes the same class into the record's package.

``` java
{{#include ../../../hkj-processor/src/test/resources/golden/CustomerLenses.java.golden}}
```
~~~

~~~admonish tip title="Why two annotations?"
`@GenerateLenses` produces classic lenses (`OrderLenses.customer()`) plus `withFoo` helpers for shallow updates. `@GenerateFocus` adds the path-based DSL (`OrderFocus.customer()`) for deep navigation, and `generateNavigators = true` is what lets the next hop chain off it as `.email()` rather than `.via(CustomerFocus.email())`. Most records benefit from both annotations.
~~~

---

## 2. Sum types and collections, the same way

Collection fields and sealed interfaces use the same annotation-driven pattern. An order's lines are a `List<LineItem>`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/LineItem.java:line_item}}
```

**Round every price to pence:**

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/quickstart/QuickstartBook.java:round}}
```

`OrderFocus.lines()` already walks every element of the `List<LineItem>`: the generated accessor ends in `.each()` for you, so it hands back a path focused on a `LineItem`, not on the list. `.via(LineItemFocus.price())` zooms each element down to the price field, and `modifyAll` applies the function in one pass to return a new `Order`. The introduction's discount left the lines at 36.000 and 2.250; rounding gives 36.00 and 2.25.

A hop from a `List` field is always `.via(...)`, navigators or not: `generateNavigators = true` shortens a hop into a single record field, as `.email()` does in section 1.

~~~admonish warning title="Do not add your own `.each()`"
`OrderFocus.lines().each()` compiles and then fails at run time. The extra `each()` tries to read a `LineItem` as a `List`; its type parameter is inferred from whatever you assign it to, which is why the compiler lets it through. `TraversalPath.each()` is for a focus that is *itself* a list, and its javadoc documents the `ClassCastException`.
~~~

**Match a variant, and move to another.** A consignment's state is a sealed interface, and `@GeneratePrisms` writes one prism per variant:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/ConsignmentState.java:consignment_state}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Consignment.java:consignment}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/quickstart/QuickstartBook.java:prism}}
```

A `Prism` is the sum-type counterpart of a lens: it succeeds when the variant matches and is a no-op otherwise. Note what `modify` will and will not do. Its function is `A -> A`, so it rebuilds the *same* variant; changing `Pending` into `Dispatched` is a read followed by a build, not a modification.

~~~admonish note title="Two views of the same record"
We added three annotations to `Order`. They don't conflict; each generates its own companion class (`OrderLenses`, `OrderFocus`, `OrderTraversals`) and you pick the entry point that matches your task.
~~~

---

## 3. Annotating types you don't own

External types like JDK classes, Jackson nodes, JOOQ records, and Protobuf messages can't be annotated directly. Higher-Kinded-J solves this with `OpticsSpec`: you declare the optics you want as an interface, and the processor generates them. Here is one for Jackson 3's `JsonNode`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/JsonNodeOpticsSpec.java:spec}}
```

The processor reads the spec and generates a `JsonNodeOptics` class (the `Spec` suffix is dropped) with one prism per method, each backed by an `instanceof` pattern match:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/quickstart/QuickstartBook.java:json}}
```

~~~admonish tip title="Plain Java wins here"
For one read like this, Jackson's own `response.at("/items/0/name")` is shorter. The prisms earn their place when they compose with the rest of your optics, so that a JSON tree joins the same paths as your records. [Taming JSON with Jackson](optics_spec_interfaces.md) shows that, and how `@MatchWhen` handles Jackson's predicate-based type checks.
~~~

---

~~~admonish info title="Key Takeaways"
* **The annotations generate plain Java at compile time.** `XLenses`, `XFocus`, `XPrisms` and `XTraversals` are ordinary classes you call with ordinary method chains. Nothing here uses runtime reflection.
* **`@GenerateLenses` and `@GenerateFocus` are the pair to reach for.** Lenses give you `withFoo` and the classic optics; Focus gives you the path DSL, and `generateNavigators = true` lets a hop into another record chain as a method call.
* **A collection accessor is already element-level.** `OrderFocus.lines()` focuses a `LineItem`, because the generated method ends in `.each()` for you. Adding another `.each()` compiles and then fails at run time.
* **A prism rebuilds the variant it narrowed to.** `modify` is `A -> A`, so it cannot turn a `Pending` into a `Dispatched`; that is a read through the prism followed by building the new variant.
* **Types you do not own join the same vocabulary.** An `OpticsSpec` interface plus `@ImportOptics` generates optics for `JsonNode`, JOOQ records or JDK types, and they compose with everything else.
~~~

---

~~~admonish tip title="See Also"
- [Annotations at a Glance](annotations_at_a_glance.md): every `@Generate*` and spec hint, with its target and what it produces
- [Focus DSL](focus_dsl.md): the path-based API this page previews, in full
- [Decision Trees](decision_trees.md#tree-2-which-api-style): choosing between the Focus DSL, the Fluent API and the Free Monad DSL
- [What a Path Is Made Of](optics_intro.md): the optics a path wraps, and when you need one
- [Record Mapping](../mapping/ch_intro.md): the domain to wire boundary, which needs none of this chapter first
~~~

~~~admonish tip title="Ready for hands-on?"
The [Optics Tutorial Track](../tutorials/optics/ch_intro.md) (202 exercises) is exercise-driven. Six journeys run from Lens & Prism through the Focus DSL to batching, coupled updates, and the generated DTO boundary. Recommended once you've finished this Quickstart.
~~~

---

**Previous:** [Optics](ch_intro.md)
**Next:** [Focus DSL](focus_dsl.md)
