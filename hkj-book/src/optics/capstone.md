# Capstone: An Order Desk

_Three order-desk operations, built from named paths, a checked PATCH, a filtered discount and a sealed state._

~~~admonish info title="What You'll Learn"
- Name each path once, as a constant, and reuse it across methods
- Accept an order only when every price passes, then discount its bulk lines
- Amend an order from a sparse request, and report every bad field it sent
- Move a sealed state to another variant only when it is the one you expect
~~~

~~~admonish example title="See Example Code"
**The code on this page is [OrderDesk.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/optics/capstone/OrderDesk.java) and its [OrderDeskTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/optics/capstone/OrderDeskTest.java)**: the page includes them, so the build compiles and runs them.
~~~

The order service has a desk that takes orders in, amends them when a customer asks, and sends goods out. Each operation is a few lines, because the paths do the navigating. The records are the chapter's cast: an `Order` placed by a `Customer`, with `LineItem`s, and a `Consignment` whose state is a sealed `ConsignmentState`.

---

## Name the paths once {#name-the-paths-once}

A path is a value, so the desk builds each one once and names it for what it reaches:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/capstone/OrderDesk.java:paths}}
```

`EMAIL` and `STATUS` are `FocusPath`s, so each reaches exactly one value. `PRICES` reaches every line's price, and `BULK_PRICES` the price of every line with four or more of an item. `PENDING` reaches a consignment's state only when it is `Pending`. Each type says how many values its path can reach, so the code that uses it cannot forget.

---

## Take an order in {#take-an-order-in}

An order is accepted when every price passes the check, and only then are its bulk lines discounted by 10%:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/capstone/OrderDesk.java:check_price}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/capstone/OrderDesk.java:accept}}
```

`map` on a `Validated` changes the value inside a `Valid` and passes an `Invalid` through untouched, so the discount runs only on an accepted order. Ada's order of a £40.00 lamp and four £2.50 bulbs comes back `Valid`, with the bulbs at 2.250 and the lamp as it was. An order priced at -1.00, 2.50 and -3.00 comes back `Invalid` with both bad prices named, and nothing is discounted.

---

## Amend an order {#amend-an-order}

A customer can ask to change the email on an order, its status, or both. The request is sparse, each component `null` when it was not sent, and each value it sends is parsed before anything is written:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/capstone/OrderDesk.java:amendment}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/capstone/OrderDesk.java:amend}}
```

~~~admonish question title="Checkpoint: count the errors first" id="check-capstone-amend"
A request sends the email `ada.example.org`, which has no `@`, and the status `LOST`, which `OrderStatus` does not have. Before you read on: how many errors does `amend` report, in what order, and where is each located?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-capstone-amend-answer"
**Two, in edit order: `Invalid(NonEmptyList[customer.email.value: not an email, status: not a status])`.** `accumulate` checks every edit before it writes anything, and each generated path labels its own errors, so the email's error carries the whole route, three records down.

Where this lives: [Validated PATCH](multi_edit.md#validated-patch-editsaccumulate).
~~~

A good request, `  Ada@Example.ORG ` and `paid`, gives an order whose customer's email is `ada@example.org` and whose status is `PAID`, with its lines the same list as before. A request that sends nothing leaves the order as it was.

~~~admonish example title="The same amendment by hand" collapsible=true
Without paths, each field rebuilds every record that holds it, and the first bad field throws, so a caller never hears about the second:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/capstone/OrderDesk.java:amend_by_hand}}
```

Lombok's withers would shorten the rebuilds, but not the early throw.
~~~

---

## Send goods out {#send-goods-out}

A consignment is dispatched only from `Pending`. Moving to `Dispatched` is a different variant, so it is a check through the prism and then a write of the new state, not a `modify`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/capstone/OrderDesk.java:dispatch}}
```

A pending consignment comes back `Dispatched` at the given instant. A returned one comes back as the very object it was.

---

## What the desk used {#what-the-desk-used}

| The desk | What it rests on | Where it was taught |
|---|---|---|
| `EMAIL`, three records down | a generated path with navigators | [Quickstart](quickstart.md#1-annotate-then-update) |
| `PRICES`, every line's price | a `List` field is already element-level | [Collections, Optionals and Sealed Types](focus_navigation.md#collection-navigation) |
| `BULK_PRICES` | `filter` narrows a traversal path | [Focus DSL](focus_dsl.md#traversalpath-zero-or-more-elements) |
| `PRICES.toTraversal()` | a path hands over its optic | [What a Path Is Made Of](optics_intro.md#each-path-type-wraps-an-optic) |
| `modifyAllValidated` | every bad value reported at once | [Updates That Can Fail](fluent_api.md#every-element-every-error) |
| `Edits.accumulate` and `parseIfPresent` | a sparse request, every bad field located | [Many Edits at Once](multi_edit.md#validated-patch-editsaccumulate) |
| `PENDING.matches`, then a write | moving to another variant is not a `modify` | [Quickstart](quickstart.md#2-sum-types-and-collections-the-same-way) |

~~~admonish tip title="You can ship now"
You have built an order desk from the chapter's first pages, and each operation is compiled and tested. The next page checks what stayed with you, and its score says where to go next.
~~~

---

~~~admonish info title="Key Takeaways"
* **Name a path once.** A path is a value; build it as a constant and every method reuses it.
* **Check, then change.** `modifyAllValidated` decides whether an order is accepted, and `map` changes it only when it is.
* **A filter belongs on the path.** The discount never sees a line the filter leaves out.
* **A sparse request is one `accumulate`.** Every field it sent is checked, and every bad one is reported where it lives.
* **A move between variants is a check and a write.** `matches` asks, and the state's own path writes the new variant.
~~~

~~~admonish tip title="See Also"
- [Many Edits at Once](multi_edit.md): the PATCH model the amendment rests on
- [Mapping at the Boundary](../mapping/ch_intro.md): the same order service, parsed from a request and built into a response
~~~

---

**Previous:** [Many Edits at Once](multi_edit.md)
**Next:** [Check Your Understanding](self_check.md)
