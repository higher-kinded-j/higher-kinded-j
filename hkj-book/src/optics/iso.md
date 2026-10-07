# Isomorphisms: A Practical Guide

_Convert between two types that hold the same information, losslessly in both directions._

~~~admonish info title="What You'll Learn"
- Define a lossless conversion with `Iso.of(get, reverseGet)`, and flip it with `reverse()`
- Compose an iso with a lens, prism, affine or traversal, and keep that optic's type
- Convert through an iso inside a `For` or `ForState` workflow with `through`, `modifyVia` and `updateVia`
- Check an iso's round trip, and choose a one-way method or a Validated Prism when a conversion loses data or can fail
- Generate a static iso field with `@GenerateIsos`, and fix a method it refuses
~~~

~~~admonish example title="See Example Code"
[IsoUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/IsoUsageExample.java)
~~~

In the previous guides, we explored two essential optics: the **`Lens`**, for targeting data that *must* exist (a "has-a" relationship), and the **`Prism`**, for safely targeting data that *might* exist in a specific shape (an "is-a" relationship).

This leaves one final, fundamental question: what if you have two data types that are different in structure but hold the exact same information? How do you switch between them losslessly? For this, we need our final core optic: the **`Iso`**.

---

## The Scenario: Translating Between Equivalent Types

An **`Iso`** (Isomorphism) is an optic that represents a perfectly reversible, lossless conversion between two equivalent types. It plays the part of a wrapper record's constructor and accessor, such as `new UserId(value)` and `id.value()`: two functions that undo each other. Unlike a loose pair of functions, it is one value that composes with other optics and turns around with `reverse()`, and `IsoLaws` checks that nothing is lost. [Choosing an optic](optics_intro.md#choosing-an-optic) sets it beside the other optic types.

An `Iso` is the right tool when you need to:

* Convert a wrapper type to its raw value (e.g., `UserId(long id)` <-> `long`).
* Change units or representation without losing anything (e.g., whole cents <-> the same amount written to two decimal places).
* Bridge two data structures that are informationally identical (e.g., a custom record and a generic tuple).

Let us explore that last case. Imagine we have a `Point` record and want to convert it to a generic `Tuple2` to use with a library that operates on tuples.

**The Data Model:**

```java
public record Point(int x, int y) {}

public record Tuple2<A, B>(A _1, B _2) {}
```

These two records can hold the same information. An `Iso` is the perfect way to formalise this relationship.

---

## A Step-by-Step Walkthrough

### Step 1: Defining an Iso

Unlike Lenses and Prisms, which are often generated from annotations, Isos are almost always defined manually. This is because the logic for converting between two types is unique to your specific domain.

You create an `Iso` using the static `Iso.of(get, reverseGet)` constructor.

<!-- verify -->
```java
import org.higherkindedj.optics.Iso;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;

public class Converters {
    public static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
      return Iso.of(
          // Function to get the Tuple from the Point
          point -> Tuple.of(point.x(), point.y()),
          // Function to get the Point from the Tuple
          tuple -> new Point(tuple._1(), tuple._2())
      );
    }
}
```

#### Using `@GenerateIsos` for Method-Based Isos

Whilst most Isos are defined manually, the `@GenerateIsos` annotation can be applied to methods that return Iso instances to generate a companion class with static fields. You can also customise the generated package:

```java
public class Converters {
    // Generated class will be placed in org.example.generated.optics
    @GenerateIsos(targetPackage = "org.example.generated.optics")
    public static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
        return Iso.of(
            point -> Tuple.of(point.x(), point.y()),
            tuple -> new Point(tuple._1(), tuple._2())
        );
    }
}
```

This is useful when you need to avoid name collisions or organise generated code separately.

The annotated method has to be `static`, take no arguments, be reachable from the generated package, and return an `Iso` whose two type arguments name no type variable. Each of those is a question about the *field*: `@GenerateIsos` publishes the iso as a `public static final` field, and a field has nowhere to declare a `<T>` and no receiver to call an instance method on. It is alone in this among the optics annotations: the rest generate static *methods*, which can declare type parameters and do.

So `<T> Iso<Box<T>, T> boxIso()` is refused where it is written rather than generating a field naming a `T` that nothing declares. Note the rule is about what the **iso** names, not what the method declares: `<T> Iso<Box, String> boxIso()` generates fine, because `T` is inferred at the call and never reaches the field's type. Give the iso concrete type arguments (`Iso<Box<String>, String>`), or call the method directly and skip the generated field.

### Step 2: The Core Iso Operations

An `Iso` provides two fundamental, lossless operations:

* **`get(source)`**: The "forward" conversion (e.g., from `Point` to `Tuple2`).
* **`reverseGet(target)`**: The "backward" conversion (e.g., from `Tuple2` back to `Point`).

Furthermore, every `Iso` is trivially reversible using the **`.reverse()`** method, which returns a new `Iso` with the "get" and "reverseGet" functions swapped.

<!-- verify -->
```java
var pointToTupleIso = Converters.pointToTuple();
var myPoint = new Point(10, 20);

// Forward conversion
Tuple2<Integer, Integer> myTuple = pointToTupleIso.get(myPoint); // -> Tuple2[10, 20]

// Backward conversion using the reversed Iso
Point convertedBack = pointToTupleIso.reverse().get(myTuple); // -> Point[10, 20]

// Demonstrate perfect round-trip
assert myPoint.equals(convertedBack); // Always true for lawful Isos
```

### Step 3: Composing Isos as a Bridge

The most powerful feature of an `Iso` is its ability to act as an adapter or "glue" between other optics. Because the conversion is lossless, an `Iso` preserves the "shape" of the optic it is composed with.

* `Iso + Iso = Iso`
* **`Iso + Lens = Lens`**
* **`Iso + Prism = Prism`**
* **`Iso + Affine = Affine`**
* **`Iso + Traversal = Traversal`**

This second rule is incredibly useful. We can compose our `Iso<Point, Tuple2>` with a `Lens` that operates on a `Tuple2` to create a brand new `Lens` that operates directly on our `Point`:

<!-- verify -->
```java
// A standard Lens that gets the first element of any Tuple2
Lens<Tuple2<Integer, Integer>, Integer> tupleFirstElementLens =
    Lens.of(Tuple2::_1, (t, v) -> Tuple.of(v, t._2()));

// The composition: Iso<Point, Tuple2> + Lens<Tuple2, Integer> = Lens<Point, Integer>
Lens<Point, Integer> pointToX = pointToTupleIso.andThen(tupleFirstElementLens);

// We can now use this new Lens to modify the 'x' coordinate of our Point
Point movedPoint = pointToX.modify(x -> x + 5, myPoint); // -> Point[15, 20]
```

The `Iso` acted as a bridge, allowing a generic `Lens` for tuples to work on our specific `Point` record.

### Step 4: Isos in Comprehension Workflows

Composition with individual lenses is useful, but Isos truly come into their own when they are woven into [comprehension](../functional/for_comprehension.md) workflows. Instead of scattering `iso.get()` and `iso.reverseGet()` calls throughout your code, you can let the comprehension handle the conversion for you, keeping both representations in scope at once.

The `For` comprehension's `through()` method does exactly this. It converts the currently bound value via an Iso and accumulates both the original and the converted value, so you can reason in whichever representation suits each step:

<!-- verify -->
```java
// Whole cents, and the same amount in dollars to two decimal places
Iso<Integer, BigDecimal> centsToDollars =
    Iso.of(cents -> BigDecimal.valueOf(cents, 2),
           dollars -> dollars.movePointRight(2).intValueExact());

Kind<IdKind.Witness, String> result =
    For.from(idMonad, Id.of(50000))
        .through(centsToDollars)
        .yield((cents, dollars) ->
            "Budget: " + cents + " cents = $" + dollars);
// Result: "Budget: 50000 cents = $500.00"
```

This Iso is lossless only for amounts in whole cents: `intValueExact` refuses a value with more than two decimal places. That is why the `ForState` example in this section rounds back to whole cents before it stores the result.

Both values are available in `yield()` without any manual conversion. When used with a `MonadZero` such as `Maybe` or `List`, `through()` preserves the ability to apply `when()` guards on the converted values:

<!-- verify -->
```java
Kind<ListKind.Witness, String> result =
    For.from(listMonad, LIST.widen(temperatures))
        .through(celsiusToFahrenheitIso)
        .when(t -> t._2().value() > 50.0)  // filter on the Fahrenheit value
        .yield((celsius, fahrenheit) -> celsius.value() + "C");
```

Within a [ForState](../functional/forstate_comprehension.md) workflow, two additional operations let you modify or set a field through an Iso without ever touching the internal representation directly:

<!-- verify -->
```java
// Increase budget by 10%, reasoning in dollars, rounding back to whole cents
ForState.withState(idMonad, Id.of(department))
    .modifyVia(budgetLens, centsToDollars,
        dollars -> dollars.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_EVEN))
    .yield();

// Set budget to exactly $750.00 (stored internally as 75000 cents)
ForState.withState(idMonad, Id.of(department))
    .updateVia(budgetLens, centsToDollars, new BigDecimal("750.00"))
    .yield();
```

The `modifyVia` flow is: `lens.get` &#8594; `iso.get` &#8594; `modifier` &#8594; `iso.reverseGet` &#8594; `lens.set`. The `updateVia` flow is simpler: `iso.reverseGet(value)` &#8594; `lens.set`. Both honour the Iso's round-trip property.

~~~admonish tip title="See Also"
For the full range of optics operations within comprehensions (including traversals, pattern matching, and bulk transforms), see [Optics Integration](../functional/for_optics.md).
~~~

---

## When to Use Isos vs Other Approaches

### Use Isos When

* **Data format conversion** - Converting between equivalent representations
* **Legacy system integration** - Bridging old and new data formats
* **Library interoperability** - Adapting your types to work with external libraries
* **Composable adapters** - Building reusable conversion components

<!-- verify -->
```java
// A wrapper and the value it wraps: the same information, convertible both ways for every value
Iso<UserId, Long> userIdIso = Iso.of(UserId::value, UserId::new);

// Use with any lens that focuses a UserId
@GenerateLenses record Account(UserId id, String owner) {}
Lens<Account, Long> rawAccountId = AccountLenses.id().andThen(userIdIso);
```

### Use Direct Conversion Methods When

* **One-way conversion** - You do not need the reverse operation
* **Non-lossless conversion** - Information is lost in the conversion
* **A hot loop you have measured** - [Production Readiness](production_readiness.md#runtime-cost) says what each call allocates

<!-- verify -->
```java
// Simple one-way conversion
String pointDescription = point.x() + "," + point.y();
```

### Use Manual Adapters When

* **Complex conversion logic** - Multi-step or conditional conversions
* **Validation required** - Conversion might fail
* **Side effects needed** - Logging, caching, etc.

<!-- verify -->
```java
// Complex conversion that might fail
public Optional<Point> parsePoint(String input) {
    try {
        String[] parts = input.split(",");
        return Optional.of(new Point(
            Integer.parseInt(parts[0].trim()),
            Integer.parseInt(parts[1].trim())
        ));
    } catch (Exception e) {
        return Optional.empty();
    }
}
```

---

## Common Pitfalls

### Do Not Do This

<!-- verify -->
```java
// Lossy conversion - not a true isomorphism
Iso<Double, Integer> lossyIso = Iso.of(
    d -> d.intValue(),    // Loses decimal precision!
    i -> i.doubleValue()  // Cannot recover original value
);

// One-way thinking - forgetting about reverseGet
Iso<Point, String> badPointIso = Iso.of(
    point -> point.x() + "," + point.y(),
    str -> new Point(0, 0)  // Ignores the input!
);

// Creating Isos repeatedly instead of reusing
var iso1 = Iso.of(Point::x, x -> new Point(x, 0));
var iso2 = Iso.of(Point::x, x -> new Point(x, 0));
var iso3 = Iso.of(Point::x, x -> new Point(x, 0));
```

### Do This Instead

<!-- verify -->
```java
// True isomorphism: every Point has exactly one Tuple2, and back
Iso<Point, Tuple2<Integer, Integer>> goodPointIso = Iso.of(
    point -> Tuple.of(point.x(), point.y()),
    tuple -> new Point(tuple._1(), tuple._2())
);

// Test your isomorphisms
public static <A, B> void testIsomorphism(Iso<A, B> iso, A original) {
    B converted = iso.get(original);
    A roundTrip = iso.reverse().get(converted);
    assert original.equals(roundTrip) : "Iso failed round-trip test";
}

// Reuse Isos as constants
public static final Iso<Point, Tuple2<Integer, Integer>> POINT_TO_TUPLE =
    Iso.of(
        point -> Tuple.of(point.x(), point.y()),
        tuple -> new Point(tuple._1(), tuple._2())
    );
```

---

## Real-World Example: Wrapper Type Integration

<!-- verify -->
```java
// Strongly-typed wrappers
public record ProductId(UUID value) {}
public record CategoryId(UUID value) {}

public class WrapperIsos {
    public static final Iso<ProductId, UUID> PRODUCT_ID_UUID =
        Iso.of(ProductId::value, ProductId::new);

    public static final Iso<CategoryId, UUID> CATEGORY_ID_UUID =
        Iso.of(CategoryId::value, CategoryId::new);

    // Use with any UUID-based operations
    public static String formatProductId(ProductId id) {
        return PRODUCT_ID_UUID
            .andThen(Iso.of(UUID::toString, UUID::fromString))
            .get(id);
    }
}
```

~~~admonish warning title="When it is not an Iso"
A tempting use of an Iso is a bridge between a domain record and its wire DTO (`Customer` <-> `CustomerDto` with a date rendered as a string). It is **not** one: parsing the string back can fail, and a conversion that can fail in either direction has no lawful `reverseGet`. That boundary belongs to the fallible optic built for it, the [Validated Prism](validated_prism.md), and to [`@GenerateMapping`](../mapping/ch_intro.md), which derives the whole record conversion. Where a pair really is lossless, the generated mapping exposes it as an Iso, so the two worlds meet exactly where the laws allow.
~~~

## Complete, Runnable Example

This example puts all the steps together to show both direct conversion and composition. Its records pass `targetPackage` to `@GenerateLenses`, so `CircleLenses` is generated into `org.higherkindedj.example.optics.iso` and imported from there; your own records can leave the attribute out.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/optics/IsoUsageExample.java:complete_example}}
```

**Expected Output:**

```
{{#include ../../../hkj-examples/src/test/resources/golden/optics-iso-example-output.txt.golden}}
```

---

~~~admonish info title="Key Takeaways"
* **An Iso is a lossless two-way street**: `get` and `reverseGet` convert between equivalent representations, and `reverse()` flips the direction for free
* **Isos are the composition bridge**: composed with a Lens, Prism, Affine or Traversal they preserve that optic's shape, so a generic optic works on your domain type
* **Losslessness is the law**: a conversion that drops information or can fail in either direction is not an Iso; test the round trip, and reach for [Validated Prisms](validated_prism.md) when parsing can fail
* **Reuse as constants**: define each Iso once, test it, and compose it everywhere the representation boundary appears
~~~

~~~admonish tip title="See Also"
- [Composition Rules](composition_rules.md): why `Iso.andThen(X)` is an `X` for each of the five optics in its table
- [Validated Prisms](validated_prism.md): the fallible sibling for conversions that can reject
- [What Your Spec Generates](../mapping/tiers.md): where a lossless generated record mapping earns its `asIso()`
- [Production Readiness](production_readiness.md#runtime-cost): what each optic allocates, and when to cache a composed optic
~~~

---

**Previous:** [Affines](affine.md)
**Next:** [Profunctor Optics](profunctor_optics.md)
