# Coming from Lombok, Streams and Switch

_The Java you write today, the optic that replaces it, and when to keep the Java._

Most of what an optic does, you already do with a wither, a stream or a `switch`. This page sets each habit beside its path or optic, marks where the analogy breaks, and says when plain Java stays the better choice.

---

## From Lombok and withers {#from-lombok-and-withers}

A wither copies one record with one component replaced. A [lens](lenses.md) is the accessor and that wither as one value, and it composes, and [Why a lens, when you have `@With`?](lenses.md#lens-or-wither) makes the full case.

| You write today | With optics | Note |
|---|---|---|
| `order.withStatus(PAID)`, one level deep | `OrderFocus.status().set(PAID, order)`, or the lens `OrderLenses.status()` | a wither is enough for one shallow change; a lens earns its place once it is reused or composed |
| `x.withA(f.apply(x.a()))`, a read and then a write | `path.modify(f, x)` | one call reads and writes |
| A wither cascade three records deep | `OrderFocus.customer().email().value().modify(f, order)` | a chained hop needs [navigators](focus_navigation.md#fluent-navigation-with-generated-navigators), `generateNavigators = true`; without them, each hop is `.via(...)` |
| `withLo(5).withHi(10)` on a record whose constructor checks both | `Lens.paired`, which constructs once | two lens writes fail at the first, just as two withers do: [Coupled Fields](coupled_fields.md) |
| `toBuilder().sku(s).price(p).build()`, several fields at once | `Edits.combine(set(LineItemFocus.sku(), s), set(LineItemFocus.price(), p)).apply(line)` | `combine` builds a record per edit; for a constructor that checks fields together, [accumulate onto a focus](multi_edit.md#fields-a-constructor-checks-together) |
| `@Builder`, to create a record | keep it | a lens writes into a value you already have, so it replaces `@With`, never `@Builder` |
| A Lombok class with a builder or withers, rather than a record | an `@ImportOptics` spec with [`@ViaBuilder` or `@Wither`](copy_strategies.md) | the generated lenses call your builder or your withers |
| Lombok's annotation processor in the build | Lombok's processor first, then `hkj-processor` | [Build-time impact](production_readiness.md#build-time-impact) says why |

Here is the cascade, normalising the email on an order, with the withers Lombok's `@With` generates:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromJavaBook.java:lombok_before}}
```

And the path that replaces it:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromJavaBook.java:lombok_after}}
```

For an address sent as `Ada@Example.COM`, with spaces round it, both give `ada@example.com`. The path rebuilds the order, the customer and the email address, and the order's lines are the same list as before.

---

## From streams {#from-streams}

A stream over a list field reads the elements, and a wither puts the new list back. A [traversal](traversals.md) does both, at any depth, and a [fold](folds.md) is the read-only half.

| You write today | With optics | Note |
|---|---|---|
| `stream().map(f).toList()` over a list field, put back with a wither | `OrderFocus.lines().via(LineItemFocus.price()).modifyAll(f, order)` | the path does the rebuild, however deep the list |
| `stream().filter(p).map(f).toList()` | `.filter(p)` on the path, then `modifyAll(f, order)` | the stream drops what `p` rejects; the filtered path keeps it, unchanged |
| `stream().map(f).toList()`, to read the values | `path.getAll(order)` | |
| `reduce`, `anyMatch`, `count` | `foldMap(monoid, f, order)`, `exists(p, order)` and `count(order)` on the path | only reads; `exists` stops at its first match, as [What reads cost](production_readiness.md#read-cost) explains |
| `Collectors.toMap` over a map's entries, to change every value | `CatalogueFocus.prices().each(EachInstances.mapValuesEach()).modifyAll(f, catalogue)` | `toMap` promises no map type and no order; the path keeps the source's iteration order |
| `limit(n)` or `skip(n)`, then `map(f)` | `ListTraversals.taking(n)` or `dropping(n)`, reached from the list's lens | the stream drops the rest; the [limited traversal](limiting_traversals.md) keeps it, unchanged |
| A loop that checks every element and collects the failures | `OpticOps.modifyAllValidated(order, path.toTraversal(), check)` | every bad value reported at once: [Updates That Can Fail](fluent_api.md#every-element-every-error) |

Discounting the bulk lines, those of four or more, shows the difference that matters. A stream's `filter` would drop the lamp from the order, so the careful version tests inside `map`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromJavaBook.java:streams_before}}
```

A path's `filter` leaves the lamp in place, so the condition can stay a filter:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromJavaBook.java:streams_after}}
```

For Ada's order of a £40.00 lamp and four £2.50 bulbs, both keep the lamp at 40.00 and price the bulbs at 2.250.

---

## From `switch` and `instanceof` {#from-switch-and-instanceof}

A [prism](prisms.md) is an `instanceof` pattern and the variant's constructor in one value. An [affine](affine.md) is an accessor that returns an `Optional`, with a copy that writes the value back.

| You write today | With optics | Note |
|---|---|---|
| `if (state instanceof Returned returned)`, then `returned.reason()` | `ConsignmentStatePrisms.returned().getOptional(state)`, or `matches(state)` for the bare test | `@GeneratePrisms` on the sealed interface writes one prism per variant |
| `new Returned(reason)`, the variant's constructor | `returned().build(new Returned(reason))`, the variant as a `ConsignmentState` | for a sealed variant, `build` only widens the type; `Prisms.some().build(x)` wraps, as `Optional.of(x)` does |
| A sealed `switch` that changes one case and passes the rest through | `ConsignmentFocus.state().via(returned()).via(ReturnedFocus.reason()).modify(f, consignment)` | the `switch` stops compiling when a variant is added; the prism passes the new one through |
| A `switch` that moves the state to another variant, `Pending` to `Dispatched` | `ConsignmentFocus.state().via(pending()).matches(consignment)`, then `ConsignmentFocus.state().set(new Dispatched(at), consignment)` | `modify` through a prism keeps the variant, so a move is a check and a write, as [Send goods out](capstone.md#send-goods-out) shows |
| `profile.altEmail().map(EmailAddress::value)`, and a copy to write it back | `CustomerProfileFocus.altEmail().via(EmailAddressFocus.value())` | reads as the chain does, and writes too; `modify` leaves an empty `Optional` alone |

A sealed `switch` that tidies a returned consignment's reason, and passes every other state through:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromJavaBook.java:switch_before}}
```

The same change through the prism:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromJavaBook.java:switch_after}}
```

A consignment returned with the reason `damaged`, padded with spaces, comes back with `damaged` from both. A pending one comes back from both as the very object it was.

---

## The three that do not carry over {#what-does-not-carry-over}

1. **A path never creates a record.** It writes into one you already hold, so `@Builder` and your constructors stay.
2. **A filtered path keeps what it skips.** A stream's `filter` leaves the rejected elements out of the result. A path's `filter` leaves them in the structure, unchanged, and only `getAll` and the other reads leave them out.
3. **A prism is not exhaustive.** A sealed `switch` fails to compile when a variant is added, and a prism passes the new variant through. Keep the `switch` where every case needs an answer.

---

## When plain Java wins {#when-plain-java-wins}

~~~admonish tip title="Plain Java wins here"
- **One shallow change needs no optic.** `order.withStatus(PAID)` says everything, and a path adds nothing until it is reused or composed.
- **A change of shape is a stream's job.** A traversal keeps the collection and its element type, so grouping, flattening or mapping to another type stays a stream.
~~~

---

## Migrating one method {#migrating-one-method}

1. Add `@GenerateFocus(generateNavigators = true)` beside `@With` on the records the method changes. The two sit side by side.
2. Replace the deepest cascade first, and assert that the old and new versions agree on a few fixtures.
3. Once a second method uses a path, name it as a `static final` constant, as [Caching optics](production_readiness.md#caching-optics) suggests.
4. Keep `@With` for one-level changes, and `@Builder` for creating records.

**The code on this page is [FromJavaBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromJavaBook.java) and its [FromJavaBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/optics/comingfrom/FromJavaBookTest.java)**: the page includes the first, and the test holds the before-and-after pairs and the rows on streams and `switch`.

---

~~~admonish info title="Where next"
- The idiom each optic type stands in for, in one table: [Choosing an optic](optics_intro.md#choosing-an-optic)
- The names a Haskell or Scala reader knows: [Coming from Monocle or Haskell lens](from_monocle.md)
- The same translation for a DTO mapper: [Coming from MapStruct and Bean Validation](../mapping/from_mapstruct.md)
~~~

---

**Previous:** [Composition Rules](composition_rules.md)
**Next:** [Coming from Monocle or Haskell lens](from_monocle.md)
