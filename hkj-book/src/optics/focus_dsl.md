# Focus DSL: Path-Based Optic Syntax

_Navigate and update nested records through generated, compile-checked paths that mirror the shape of your data._

~~~admonish info title="What You'll Learn"
- Read, set and modify a field anywhere in your records through a generated path
- Predict the path type a field gives you, and spell the next hop
- Compose paths with `.via()` where a navigator does not reach
- Hand a path's optic to an API that takes a raw optic
~~~

~~~admonish example title="See Example Code"
**The code on this page is [FocusDslBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/optics/focus/FocusDslBook.java) and its [FocusDslBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/optics/focus/FocusDslBookTest.java)**: the page includes them, so the build compiles and runs them.

For longer programs in the same style, see [NavigatorExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/NavigatorExample.java) and [ContainerNavigationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ContainerNavigationExample.java).
~~~

A Focus path does for your records what the JPA metamodel or QueryDSL's Q-types do for a query, and it writes as well as reads. `OrderFocus.customer().name()` names a field the way `QOrder.order.customer.name` does, and the compiler checks every step. Instead of composing lenses, prisms and traversals by hand, you navigate your data with method chains that mirror its shape.

---

## The whole feature on one screen {#the-whole-feature-on-one-screen}

**Step 1.** Annotate the records. These are the chapter's cast, which the Quickstart introduced: an `Order` placed by a `Customer`.

~~~admonish example title="The cast these examples use" collapsible=true
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Order.java:order}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Customer.java:customer}}
```
~~~

**Step 2.** Use the generated `OrderFocus` companion class:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/focus/FocusDslBook.java:first_use}}
```

**Step 3.** That is it. The path you typed (`OrderFocus.customer().name()`) is a typed value: store it, pass it around, reuse it. The processor generated `OrderFocus` and `CustomerFocus` at compile time; nothing reflective happens at runtime.

`.name()` chains straight off `.customer()` because of `generateNavigators = true`. A *navigator* is the small class the processor generates for a field whose type is another annotated record, with one method per field of that record. Without it, `OrderFocus.customer()` is still a perfectly good `FocusPath<Order, Customer>`, and you spell the next hop `.via(CustomerFocus.name())`. Navigators are sugar over composition, so nothing is lost by leaving them off and nothing is locked in by turning them on.

Collections, optionals and sealed types extend the same pattern, and [Find your field](#find-your-field) gives the spelling for each. You almost never have to compose lenses by hand to get useful work done.

~~~admonish tip title="Why this matters"
The field's declared type decides the path type at compile time, so the path says how many values it can reach. An `Optional`, a `Maybe` or a field with a recognised `@Nullable` gives an `AffinePath`, never a `FocusPath` whose `get` quietly returns null. A `List`, `Set` or `Collection` gives a `TraversalPath`, which has no singular `get`. Mistakes of that shape are compilation errors, not production ones. The guarantee covers the shapes the processor recognises. A field that holds null without saying so gets a plain `FocusPath`, and so does a `Map` or an array unless the annotation sets `widenCollections = true`; [Find your field](#find-your-field) marks each.
~~~

---

## Find your field {#find-your-field}

You arrive holding a field, not an optic type. Find its declaration in the table: the row gives what the generated method returns and what you write next. Every row but the last is a component of one `Basket`: the basket a customer pays for before it becomes an `Order`, given a field of every kind around the cast's own records. The collapsed proof compiles each one. Each field links to the section that explains it.

| Your field | The generated method returns | What you write next |
|---|---|---|
| [`Customer customer`](focus_navigation.md#fluent-navigation-with-generated-navigators), a record with `@GenerateFocus` | a navigator, since `Basket` has `generateNavigators = true` | `.email()` |
| `String reference` | `FocusPath<Basket, String>` | `.get(basket)`, `.set(value, basket)`, `.modify(f, basket)` |
| [`List<LineItem> lines`](focus_navigation.md#collection-navigation), or a `Set` or `Collection` | `TraversalPath<Basket, LineItem>`, already on the elements | `.via(LineItemFocus.quantity())` |
| [`Optional<String> giftMessage`](focus_navigation.md#some-unwrap-optional) | `AffinePath<Basket, String>` | `.getOptional(basket)` |
| [`@Nullable String couponCode`](focus_navigation.md#nullable-handle-null-values), with a recognised `@Nullable` | `AffinePath<Basket, String>` | `.getOptional(basket)` |
| [`String legacyNote`](focus_navigation.md#nullable-handle-null-values), which may hold null but is not annotated | `FocusPath<Basket, String>` | `.nullable()` |
| [`Map<String, String> attributes`](focus_navigation.md#access-by-index) | `FocusPath<Basket, Map<String, String>>`, by default | `.atKey("channel")` |
| [`String[] tags`](focus_navigation.md#eacheach-traverse-with-a-custom-each-instance) | `FocusPath<Basket, String[]>`, by default | `.each(EachInstances.arrayEach())` |
| [`Either<String, String> approvedBy`](focus_navigation.md#someaffine-navigate-spi-container-types), or a `Maybe`, `Try` or `Validated` | `AffinePath<Basket, String>`, on the success side | `.getOptional(basket)` |
| [`Payment payment`](focus_navigation.md#working-with-sum-types-using-instanceof), a sealed interface | `FocusPath<Basket, Payment>` | `.via(PaymentPrisms.card())`, or `.via(AffinePath.instanceOf(Card.class))` |
| [`JsonNode payload`](importing_optics.md), a type you cannot annotate | `FocusPath<Basket, JsonNode>` | `.via(JsonNodeOptics.object())`, from `@ImportOptics` |
| [`Kind<F, A> items`](kind_field_support.md) | a path chosen by the witness | depends on the witness |

The two rows marked "by default" step into the elements instead when the annotation sets `widenCollections = true`, which [the fine print](focus_navigation.md#path-widening) covers.

~~~admonish example title="Proof: every row, compiled" collapsible=true
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/focus/FocusDslBook.java:basket}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/focus/FocusDslBook.java:find_your_field}}
```
~~~

[Collections, Optionals and Sealed Types](focus_navigation.md) explains each row in depth: which fields get a navigator, and how a path widens as it goes.

---

## What the processor gives you {#what-the-processor-gives-you}

### Annotate the records {#annotate-the-records}

Add `@GenerateFocus` to generate path builders. The Focus class builds its own lenses, so `@GenerateFocus` alone compiles. Add `@GenerateLenses` as well in practice: several idioms in this chapter start from `FocusPath.of(TheseLenses.field())`, which needs the generated lens class. Indexing a list is one, and decomposing it into its head and tail with `ListPrisms` another.

The examples that follow use the cast's `Order` and `LineItem`, and a `CustomerProfile`: a customer's account profile, whose nickname and second email are optional.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/CustomerProfile.java:customer_profile}}
```

### One method per component, and the field type picks the path {#one-method-per-component-and-the-field-type-picks-the-path}

The generated companion has one method per record component, and the field's *type* decides the path type you get back:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/focus/FocusDslBook.java:per_component}}
```

~~~admonish warning title="A collection field is already element-level"
`OrderFocus.lines()` focuses each `LineItem`, not the `List<LineItem>`. That is what you want for bulk reads and updates, but it means an operation on the list as a whole, such as indexing into it or taking its head, does not compose onto it. When you need the list itself, start from the lens instead: `FocusPath.of(OrderLenses.lines())`. See [Collections, Optionals and Sealed Types](focus_navigation.md#access-by-index) for the indexing forms.
~~~

### Chain with `.via()` {#chain-with-via}

Paths compose with `.via()`. Composing a path with a wider one widens the result, so one focus joined to many focuses is many focuses, and anything joined to a traversal is a traversal:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/focus/FocusDslBook.java:chained}}
```

---

## The Three Path Types

Focus DSL provides three path types, one for each answer to "how many values does this path reach?". Each navigation hop keeps the count or widens it, and `headOption()` is the one step back, from many to at most one:

| Path type | Reaches | After an optional step | After a collection step |
|---|---|---|---|
| `FocusPath<S, A>` | exactly one value | `AffinePath` | `TraversalPath` |
| `AffinePath<S, A>` | zero or one value | `AffinePath` | `TraversalPath` |
| `TraversalPath<S, A>` | zero or more values | `TraversalPath` | `TraversalPath` |

### FocusPath: Exactly One Element

`FocusPath<S, A>` wraps a `Lens<S, A>` and guarantees exactly one focused element:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/focus/FocusDslBook.java:focus_ops}}
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `get(S)` | `A` | Extract the focused value |
| `set(A, S)` | `S` | Replace the focused value |
| `modify(Function<A,A>, S)` | `S` | Transform the focused value |
| `toLens()` | `Lens<S, A>` | Extract the underlying optic |

### AffinePath: Zero or One Element

`AffinePath<S, A>` wraps an `Affine<S, A>` for optional access:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/focus/FocusDslBook.java:affine_ops}}
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getOptional(S)` | `Optional<A>` | Extract if present |
| `set(A, S)` | `S` | Write the value, creating the focus when the last step can build it and every earlier step is present |
| `modify(Function<A,A>, S)` | `S` | Transform if present |
| `matches(S)` | `boolean` | Check whether a value is in focus |
| `getOrElse(A, S)` | `A` | Extract, or the given default |
| `toAffine()` | `Affine<S, A>` | Extract the underlying optic |

~~~admonish warning title="Set on an absent focus writes anyway"
`set` through an `AffinePath` is not conditional. `CustomerProfileFocus.nickname().set(x, profile)` on a profile with no nickname returns a profile *with* that nickname, because the last step's setter rebuilds the present case unconditionally. As `Affine.set`'s javadoc puts it, an affine makes an absent focus present when its last step can build the value and every step before that is present. `modify` is the operation that no-ops on an absent focus.

The rule is positional. A miss at the *last* step writes through and creates the focus when that step can build the value (a prism, `.some()`, `.nullable()`). A miss at an *earlier* step of a multi-step path skips the whole set, because `Affine.andThen(Affine)` does guard. When absence must be preserved, reach for `modify`, or test with `matches` first.
~~~

### TraversalPath: Zero or More Elements

`TraversalPath<S, A>` wraps a `Traversal<S, A>` for collection access:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/focus/FocusDslBook.java:traversal_ops}}
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getAll(S)` | `List<A>` | Extract all focused values |
| `setAll(A, S)` | `S` | Replace all focused values |
| `modifyAll(Function<A,A>, S)` | `S` | Transform all focused values |
| `filter(Predicate<A>)` | `TraversalPath<S, A>` | Narrow to the matching elements (on a raw `Traversal` the same narrowing is spelled `filtered`) |
| `preview(S)` | `Optional<A>` | The first focused value, if there is one and it is not null |
| `count(S)`, `isEmpty(S)` | `int`, `boolean` | Query the number in focus |
| `exists(Predicate<A>, S)`, `all(Predicate<A>, S)` | `boolean` | Does any, or every, focused value match |
| `find(Predicate<A>, S)` | `Optional<A>` | The first focused value that matches and is not null |
| `fold(Monoid<A>, S)` | `A` | Combine every focused value through a monoid |
| `headOption()` | `AffinePath<S, A>` | Narrow to the first focused element (reads the first, writes to all) |
| `toTraversal()` | `Traversal<S, A>` | Extract the underlying optic |

~~~admonish tip title="You can ship now"
You can now find the path for any field you hold, read and write through it, and chain it into the records it reaches. The next page covers the hops you spell yourself, into collections, optional values and sealed types.
~~~

~~~admonish question title="Checkpoint: `set` two steps down" id="check-focus-absent"
A `CustomerProfile` has no second email. What does `CustomerProfileFocus.altEmail().via(EmailAddressFocus.value()).set("ada@work.example", profile)` return?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-focus-absent-answer"
**The profile unchanged.** The rule is positional: the miss is at the `Optional`, an earlier step, and the last step, the address's `value`, cannot build an `EmailAddress` from nothing, so the whole set is skipped. `CustomerProfileFocus.altEmail().set(...)` alone would write, because its last step can build the value.

Where this lives: [AffinePath: Zero or One Element](#affinepath-zero-or-one-element).
~~~

~~~admonish question title="Checkpoint: predict the path type" id="check-focus-types"
`Consignment` carries `@GenerateFocus(generateNavigators = true)` and has two components: `Address to`, where `Address` carries `@GenerateFocus`, and a sealed `ConsignmentState state`. What do `ConsignmentFocus.to()` and `ConsignmentFocus.state()` return? And what does `CustomerProfileFocus.altEmail()` return for an `Optional<EmailAddress>`?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-focus-types-answer"
**A navigator for `Address`, so `.city()` chains straight on; a `FocusPath<Consignment, ConsignmentState>`, because a sealed field is not narrowed until a prism picks a variant; and an `AffinePath<CustomerProfile, EmailAddress>`.**

Where this lives: [Find your field](#find-your-field).
~~~

---

~~~admonish info title="Key Takeaways"
* **The field type picks the path type.** A plain field gives `FocusPath`, an `Optional` field gives `AffinePath`, a `List` or `Set` field gives `TraversalPath` already stepped into the elements.
* **`.via()` is the universal join.** Navigators are generated sugar for a subset of fields; everything else composes with `.via()`, and the two mix freely. [Collections, Optionals and Sealed Types](focus_navigation.md) sets out which fields qualify.
* **Composing widens.** Composing a path with a wider one widens the result: one focus plus zero-or-one is zero-or-one, and anything joined to a traversal is a traversal. `headOption()` is the one step back.
* **A path is a value, not a call.** Build it once, store it in a static field, pass it around. `toLens()`, `toAffine()` and `toTraversal()` hand the underlying optic to any API that wants a raw optic.
~~~

~~~admonish info title="Hands-On Learning"
- [Tutorial12_FocusDSL.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial12_FocusDSL.java) (10 exercises)
- [Tutorial13_AdvancedFocusDSL.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial13_AdvancedFocusDSL.java) (8 exercises)
- [Tutorial19_NavigatorGeneration.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial19_NavigatorGeneration.java) (8 exercises)
~~~

~~~admonish tip title="See Also"
- [Collections, Optionals and Sealed Types](focus_navigation.md): collection navigation, `.via()` composition, and generated navigators
- [Type Class and Effect Integration](focus_effects.md): `modifyF()`, `foldMap()`, `traverseOver()`, and Effect path bridging
- [Custom Containers and Code Generation](focus_containers.md): generated class structure, SPI container types, and registration
- [Focus DSL Reference](focus_reference.md): decision guide, common patterns, performance, pitfalls, and FAQ
~~~

---

**Previous:** [Quickstart](quickstart.md)
**Next:** [Collections, Optionals and Sealed Types](focus_navigation.md)
