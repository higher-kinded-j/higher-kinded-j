# Check Your Understanding

_Twelve questions on the Quickstart through the Capstone, each answer proved by the build._

These questions cover the pages from the [Quickstart](quickstart.md) to [the Capstone](capstone.md). They start with recall and end with writing code of your own, and they do not follow page order, on purpose. Answer each question before you open its answer, in your head or on paper. Each answer ends with a link to the section that teaches it, and [Where to go next](#where-to-go-next) turns your score into a plan.

~~~admonish question title="Checkpoint 1: a list field's path" id="check-self-list"
What does `OrderFocus.lines()` focus, the list or each line? What does `OrderFocus.lines().count(order)` return for an order of a lamp and four bulbs?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-list-answer"
**Each line, and 2.** A `List` field's generated method has already stepped into the elements, so the path is a `TraversalPath<Order, LineItem>`. There are two lines; the four bulbs are one line with a quantity of 4.

Where this lives: [Find your field](focus_dsl.md#find-your-field).
~~~

~~~admonish question title="Checkpoint 2: a type you cannot annotate" id="check-self-import"
Jackson's `JsonNode` is not your class, so you cannot put `@GeneratePrisms` on it. How do you get prisms for its object, array and text nodes?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-import-answer"
**Declare an `OpticsSpec` interface for `JsonNode`, annotated `@ImportOptics`.** The processor reads the spec and generates `JsonNodeOptics`, with one prism per method, which compose with the rest of your optics:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/JsonNodeOpticsSpec.java:spec}}
```

Where this lives: [Annotating types you don't own](quickstart.md#3-annotating-types-you-dont-own).
~~~

~~~admonish question title="Checkpoint 3: would you approve it?" id="check-self-combine"
A colleague tidies a line's SKU and parses a new price in one update. Would you approve this?

<!-- verify:rejects "no suitable method found for combine" -->
```java
Update<LineItem> tidy =
    Edits.combine(
        Edit.modify(LineItemFocus.sku(), String::strip),
        Edit.parseIfPresent(
            LineItemFocus.price(), "40.00", raw -> Validated.validNel(new BigDecimal(raw))));
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-combine-answer"
**No: it does not compile, with `no suitable method found for combine`.** `parseIfPresent` makes a `FallibleEdit`, and `Edits.combine` takes only edits that cannot fail, so a failure can never be dropped silently. Put both edits in `Edits.accumulate` instead.

Where this lives: [How the split stays compile-time safe](multi_edit.md#how-the-split-stays-compile-time-safe).
~~~

~~~admonish question title="Checkpoint 4: one value of a map" id="check-self-map"
A catalogue keeps its prices in a `Map`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/navigation/NavigationBook.java:catalogue}}
```

What does `CatalogueFocus.prices()` return, and how do you read the price of `LAMP`?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-map-answer"
**A `FocusPath` to the whole map; `.atKey("LAMP")` gives an `AffinePath` to one price.** A `Map` field is not stepped into by default, so its path still focuses the map, and `.atKey` picks one value, which may be absent.

Where this lives: [Access by index](focus_navigation.md#access-by-index).
~~~

~~~admonish question title="Checkpoint 5: a refused order" id="check-self-accept"
The Capstone's `accept` checks every price with `modifyAllValidated`, then discounts the bulk lines inside `map`. For an order with a negative price, does the discount run?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-accept-answer"
**No.** `map` on a `Validated` changes only a `Valid`, and an `Invalid` passes through untouched, so the result is `Invalid` with every bad price, and nothing is discounted.

Where this lives: [Take an order in](capstone.md#take-an-order-in).
~~~

~~~admonish question title="Checkpoint 6: a field that may be null" id="check-self-nullable"
Two components may hold null: `@Nullable String couponCode`, with JSpecify's annotation, and `String legacyNote`, with none. Which needs `.nullable()` after its generated method?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-nullable-answer"
**Only `legacyNote`.** A recognised `@Nullable` makes the generated method return an `AffinePath` already. An unannotated reference gives a `FocusPath`, and `.nullable()` reads a null as absent.

Where this lives: [`.nullable()`: read a null as absent](focus_navigation.md#nullable-handle-null-values).
~~~

~~~admonish question title="Checkpoint 7: two bad fields in one patch" id="check-self-patch"
A PATCH of an order line sends the SKU `lamp 2` and the price `forty`, and both fail their parsers. The edits are written SKU first, then quantity, then price. With `Edits.accumulate`, how many errors come back, and in what order?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-patch-answer"
**Two, in edit order: the SKU's, then the price's**, each located by its path. `accumulate` checks every edit on its own before it writes anything:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/MultiEditBook.java:accumulate}}
```

Where this lives: [Validated PATCH](multi_edit.md#validated-patch-editsaccumulate).
~~~

~~~admonish question title="Checkpoint 8: what `andThen` returns" id="check-self-and-then"
What is the type of `OrderTraversals.lines().andThen(LineItemLenses.price())`?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-and-then-answer"
**`Traversal<Order, BigDecimal>`.** Many followed by exactly one is still many:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/selfcheck/SelfCheckBook.java:and_then_type}}
```

Where this lives: [Composing optics with `andThen`](optics_intro.md#composing-optics-with-andthen).
~~~

~~~admonish question title="Checkpoint 9: a constructor that tidies" id="check-self-laws"
This record lowercases what it is given:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/selfcheck/SelfCheckBook.java:normalised_email}}
```

Is a lens to its `value` lawful? What does setting `ADA@EXAMPLE.COM` and reading it back give?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-laws-answer"
**No: reading back gives `ada@example.com`, not what was set.** A lens promises that you read back what you set, and the constructor changes the value on the way in, so hkj-test's `LensLaws` reports the broken law:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/selfcheck/SelfCheckBook.java:normalised_lens}}
```

Where this lives: [What a lens is](optics_intro.md#what-a-lens-is), and [Use Manual Lens Creation When](lenses.md#use-manual-lens-creation-when) on checking a hand-written lens.
~~~

~~~admonish question title="Checkpoint 10: write the path" id="check-self-one-of-each"
Write one expression that sets the quantity of every line of an `order` to 1.
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-one-of-each-answer"
**A traversal into the lines, then the quantity, then `setAll`:**

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/selfcheck/SelfCheckBook.java:one_of_each}}
```

Where this lives: [TraversalPath: Zero or More Elements](focus_dsl.md#traversalpath-zero-or-more-elements).
~~~

~~~admonish question title="Checkpoint 11: every bad quantity" id="check-self-quantities"
Write a method that accepts an order only if every line has at least one item, and otherwise reports every line that does not.
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-quantities-answer"
**A check that returns its verdict, run through the quantities with `modifyAllValidated`:**

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/selfcheck/SelfCheckBook.java:check_quantities}}
```

Quantities of 0, 4 and -1 give `Invalid(["No items: 0", "No items: -1"])`.

Where this lives: [Every element, every error](fluent_api.md#every-element-every-error).
~~~

~~~admonish question title="Checkpoint 12: one variant only" id="check-self-returned"
Write an update that upper-cases the reason of a `Returned` consignment and leaves a consignment in any other state as it is.
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-returned-answer"
**A path through the state's `returned()` prism, then `modify`**, which changes only the variant the prism matches:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/selfcheck/SelfCheckBook.java:returned_reason}}
```

Where this lives: [One variant of a sealed type](focus_navigation.md#working-with-sum-types-using-instanceof).
~~~

~~~admonish example title="See Example Code"
**The code on this page is [SelfCheckBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/optics/selfcheck/SelfCheckBook.java) and [SelfCheckBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/optics/selfcheck/SelfCheckBookTest.java)**: the page includes the first, and the test asserts every result an answer predicts. The answers drawn from the earlier pages are proved by those pages' own tests, and the build checks the refused snippet against the compiler's own words. Open them after the twelve, since they hold the answers.
~~~

---

## Where to go next {#where-to-go-next}

A question counts when every part of your answer was right before you opened it, and code you wrote counts when it compiles to the same result as the answer's. Whatever your score, open the *Where this lives* link of any answer you missed.

| Your score | Next step |
|---|---|
| 10 to 12, with Checkpoints 11 and 12 among them | You can ship. Read the rest of the chapter as a task calls for it: [The Optic Types](ch1_intro.md) for each optic in depth, and [Look It Up](ch7_intro.md) for an answer to a specific question |
| Any other score from 6 to 11 | Reread the sections you missed, try those questions again, then carry on as for 10 to 12 |
| 5 or fewer | Go back to the [Quickstart](quickstart.md) and read each page through to [the Capstone](capstone.md), working the [Optics Tutorial Track](../tutorials/optics/ch_intro.md) alongside, then take the questions again |

---

**Previous:** [Capstone: An Order Desk](capstone.md)
**Next:** [The Optic Types](ch1_intro.md)
