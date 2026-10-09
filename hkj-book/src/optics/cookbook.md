# Optics Cookbook

_Find the problem you hold, then copy its recipe: a Focus path first, raw optics collapsed._

## Find your recipe {#find-your-recipe}

| You want to | Recipe |
|---|---|
| Update a field nested several records deep, such as an order's customer email | [1: A field several records deep](#deep-field) |
| Change a value inside an `Optional`, only when one is present | [2: A field inside an `Optional`](#optional-field) |
| Change every element of a list inside a list, such as every price in an order history | [3: Every element, at any depth](#every-element) |
| Change only the list elements that match a condition | [4: Only the elements that match](#matching-elements) |
| Change one case of a sealed interface (a sum type), and leave the others | [5: One variant of a sealed type](#one-variant) |
| Read or change one subtype in a list of mixed types, as `instanceof` would | [6: One variant in a list of mixed types](#variants-in-a-list) |
| Read a `Map` value with a default for a missing key, or set one key | [7: A map entry, with a fallback](#map-entry) |
| Change the `Right` of an `Either` field, and leave a `Left` alone | [8: The value inside an `Either` field](#either-field) |
| Validate a PATCH and report every bad field by name | [9: A PATCH that reports every bad field](#patch) |
| Validate every element, collecting every error or stopping at the first | [10: Every value a path reaches, checked](#check-values) |
| Sum, count or test the values in a list | [11: Totals, counts and tests](#totals) |
| Read values from several fields as one list | [12: Values from several places](#several-places) |
| Sort or reverse a list's elements in place | [13: Sorting and reversing](#sort) |
| Log what a path reads, to debug it | [14: Tracing a path](#trace) |

The recipes use the chapter's order-service cast, and declare any supporting type they need. Most also show their raw-optics form in a collapsed block, for a codebase that composes generated lenses by hand.

~~~admonish example title="The cast these recipes use" collapsible=true
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Order.java:order}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Customer.java:customer}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/EmailAddress.java:email_address}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/LineItem.java:line_item}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/CustomerProfile.java:customer_profile}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Consignment.java:consignment}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/ConsignmentState.java:consignment_state}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Payment.java:payment}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Card.java:card}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Bank.java:bank}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Address.java:address}}
```
~~~

---

## Recipe 1: A field several records deep {#deep-field}

You want to replace one value three records down, such as the email on an order's customer, without rebuilding each record by hand.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:deep_field}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:deep_field_raw}}
```
~~~

`set` returns a new `Order`: it rebuilds the records on the path and reuses everything off it, the lines included. Chaining `.email().value()` straight on needs generated navigators, which `@GenerateFocus(generateNavigators = true)` turns on, here on `Order` and `Customer`. Without them, each hop is a `.via(...)`.

---

## Recipe 2: A field inside an `Optional` {#optional-field}

A customer profile's alternative email is an `Optional<EmailAddress>`, and you want to change the address when there is one.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:optional_field}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:optional_field_raw}}
```
~~~

An `Optional` component makes the generated path an `AffinePath`, which focuses zero or one value. `modify` leaves an empty `Optional` alone, and so does `set` here, because the path ends in a lens. A path whose last step is the `Optional`'s prism, such as `CustomerProfileFocus.altEmail()`, writes even when the `Optional` is empty: see [When the focus is absent](affine.md#when-the-focus-is-absent).

---

## Recipe 3: Every element, at any depth {#every-element}

An order history holds orders, and each order holds lines. You want to round every line's price to pence, across all of them.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:order_history}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:every_element}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:every_element_raw}}
```
~~~

A `List` component's generated method already steps into the elements, so `OrderHistoryFocus.orders()` focuses each `Order`. Each `.via(...)` goes one level deeper, and `modifyAll` returns a new history with every price rounded. In plain Java the same change is two nested streams, each ending in a record's constructor.

---

## Recipe 4: Only the elements that match {#matching-elements}

You want to discount only the order lines of four or more items, and leave the others as they are.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:matching}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:matching_raw}}
```
~~~

`filter` narrows the path to the matching lines, so the hops after it reach only those, and the other lines come back unchanged. `modifyWhen(condition, f, order)` is `filter` followed by `modifyAll`, for a change to the whole element.

~~~admonish tip title="Plain Java wins here"
For a condition on one record rather than on the elements of a list, an `if` around a single `set` reads best. A filter earns its place when the condition picks elements out of a list.
~~~

---

## Recipe 5: One variant of a sealed type {#one-variant}

A consignment's state is the sealed `ConsignmentState`: `Pending`, `Dispatched` or `Returned`. You want to tidy the reason on a returned consignment, and leave every other state alone.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:one_variant}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:one_variant_raw}}
```
~~~

The generated prism `ConsignmentStatePrisms.returned()` matches one variant, so the path is an `AffinePath` that is empty for any other state. `modify` keeps the variant it matched. Moving a consignment to another state is a read followed by a build, as [the Quickstart's sealed type](quickstart.md#2-sum-types-and-collections-the-same-way) shows.

---

## Recipe 6: One variant in a list of mixed types {#variants-in-a-list}

A payment history holds a list of `Payment`, each a `Card` or a `Bank`. You want every card number, or every card number masked, and the bank payments skipped.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:payment_history}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:variants_in_list}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:variants_in_list_raw}}
```
~~~

After `payments()` steps into the list, the prism keeps the cards and passes over the rest. The read skips the bank payments, and the write leaves them as they were. For a sealed type you cannot annotate, `AffinePath.instanceOf(Card.class)` takes the prism's place.

---

## Recipe 7: A map entry, with a fallback {#map-entry}

A stockroom keeps its stock by SKU in a `Map<String, Integer>`. You want a count that is zero for a SKU it has never held, and to change one entry.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:stockroom}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:map_entry}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:map_entry_raw}}
```
~~~

A `Map` component's path focuses the whole map, and `.atKey(key)` narrows it to one value that may be missing. `getOrElse` supplies the fallback for a missing key.

---

## Recipe 8: The value inside an `Either` field {#either-field}

A stockroom's `verifiedName` is an `Either<String, String>`: a `Right` once a check has passed, and a `Left` holding the reason when it has not. You want to tidy the name only when it was verified.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:either_field}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:either_field_raw}}
```
~~~

The processor generates an `Either` component's path on its `Right` side, as an `AffinePath`, so a `Left` comes back unchanged.

~~~admonish tip title="Plain Java wins here"
For an `Either` on its own rather than a field inside a record, `either.map(String::strip)` does what the path's `modify` does. The path earns its place when the `Either` sits inside a record.
~~~

---

## Recipe 9: A PATCH that reports every bad field {#patch}

A PATCH request may carry a new name, a new email, both or neither. You want to apply what it carries, and to report every bad field at once, each by its name.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:customer_patch}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:patch}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:patch_raw}}
```
~~~

For a patch with a blank name and an email that has no `@`, `patched` is `Invalid(NonEmptyList[name: must not be blank, email: not an address])`. A patch that supplies only a good email changes only the email.

`parseIfPresent` does nothing for a `null` field. `Edits.accumulate` collects every failure in edit order, and writes only when every edit has parsed. A generated path carries its component's name, which is where `name:` and `email:` come from. A navigator hop such as `CustomerFocus.email()` hands over its path with `toPath()`. [Many Edits at Once](multi_edit.md) covers the rest of the builder.

---

## Recipe 10: Every value a path reaches, checked {#check-values}

Every line of an order must hold at least one item. You want to check every quantity, and report either each bad one or only the first.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:check_quantity}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:check_values}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:check_values_raw}}
```
~~~

For an order whose lines hold 0 and -1 items, `checked` is `Invalid([No items: 0, No items: -1])` and `firstFailure` is `Left(No items: 0)`. A good order comes back valid and unchanged.

`OpticOps` takes a `Traversal`, which a `TraversalPath` hands over with `toTraversal()`. `modifyAllEither` keeps only the first error, but it still runs the check on every value. [Updates That Can Fail](fluent_api.md) has the forms for one field, and `modifyF` for an effect such as a remote price lookup.

---

## Recipe 11: Totals, counts and tests {#totals}

You want the number of items on an order, whether any line is a bulk line, and the order's total.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:totals}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:totals_raw}}
```
~~~

For Ada's order of one lamp at 40.00 and four bulbs at 2.50, `items` is 5, `lineCount` is 2 and `total` is 50.00. A `TraversalPath` answers `exists`, `all`, `count` and `find` itself, and `foldMap` combines the values with a monoid such as `Monoids.integerAddition()`.

~~~admonish tip title="Plain Java wins here"
For a read of one list field, a stream over `order.lines()` is as short. The path earns its place when the values sit deeper, or when the same path also writes.
~~~

---

## Recipe 12: Values from several places {#several-places}

A customer profile always has a name, and sometimes a nickname. You want them as one list, or to ask one question of all of them.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:several_places}}
```

~~~admonish example collapsible=true title="The same with raw optics"
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:several_places_raw}}
```
~~~

For a profile named Ada Lovelace with the nickname Countess, `all` is `[Ada Lovelace, Countess]`. `asFold()` turns a path into a read-only `Fold`, and `plus` joins two folds, the first one's values coming first. `Fold.sum` joins three or more, so `searchTerms` adds the alternative email when there is one.

~~~admonish tip title="Plain Java wins here"
For one read, `Stream.concat(Stream.of(profile.name()), profile.nickname().stream()).toList()` is as short. A `Fold` earns its place as a value you hand to other code, which can ask it `exists`, `all` or `foldMap`.
~~~

---

## Recipe 13: Sorting and reversing {#sort}

You want an order's lines in price order, or in reverse order.

No Focus path sorts. `Traversals.sorted` and `Traversals.reversed` take a `Traversal`, such as the generated `OrderTraversals.lines()`, or a `TraversalPath`'s `toTraversal()`.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:sort}}
```

Both read every focused value into a list, reorder the list, and write the values back to the places the traversal visits, in turn. A filtered traversal therefore sorts only the elements it keeps, and the others stay where they were. Sorting a traversal of prices, rather than of lines, moves the prices between lines.

---

## Recipe 14: Tracing a path {#trace}

A path reads fewer values than you expected, and you want to see what it finds.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java:trace}}
```

On a `TraversalPath`, the observer runs on `getAll` and on the queries, such as `count` and `exists`, which on a traced path read through `getAll`. A write such as `modifyAll` does not call it, and nor does `foldMap`, which reads through the traversal. A `via` or `filter` after `traced` returns a path without the observer, so trace last. Tracing belongs to the Focus paths, and has no raw-optics form.

---

The recipes are [CookbookBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cookbook/CookbookBook.java), and [CookbookBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/optics/cookbook/CookbookBookTest.java) holds every result this page states.

~~~admonish tip title="See Also"
- [Collections, Optionals and Sealed Types](focus_navigation.md): every navigation step these recipes take
- [Many Edits at Once](multi_edit.md): the PATCH recipe in full, with combined and sparse edits
- [Updates That Can Fail](fluent_api.md): checks on one field, and effects with `modifyF`
- [Composition Rules](composition_rules.md): the type each raw `andThen` returns
- [Production Readiness](production_readiness.md): caching a path, and the team conventions that keep paths readable
~~~

~~~admonish info title="Hands-On Learning"
Practise real-world optics patterns in [Tutorial 08: Real World Optics](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial08_RealWorldOptics.java) (6 exercises).
~~~

---

**Previous:** [Decision Trees](decision_trees.md)
**Next:** [Optic Capabilities](optic_capabilities.md)
