# Practical Guide: Data Equivalence with Isos

In the previous guides, we explored two essential optics: the **`Lens`**, for targeting data that *must* exist (a "has-a" relationship), and the **`Prism`**, for safely targeting data that *might* exist in a specific shape (an "is-a" relationship).

This leaves one final, fundamental question: what if you have two data types that are different in structure but hold the exact same information? How do you switch between them losslessly? For this, we need our final core optic: the **`Iso`**.

---

## The Scenario: Translating Between Equivalent Types

An **`Iso`** (Isomorphism) is a "two-way street." It's an optic that represents a perfectly reversible, lossless conversion between two equivalent types. Think of it as a **universal translator** 🔄 or a type-safe adapter that you can compose with other optics.

An `Iso` is the right tool when you need to:

* Convert a wrapper type to its raw value (e.g., `UserId(long id)` <-> `long`).
* Handle data encoding and decoding (e.g., `byte[]` <-> `Base64 String`).
* Bridge two data structures that are informationally identical (e.g., a custom record and a generic tuple).

Let's explore that last case. Imagine we have a `Point` record and want to convert it to a generic `Tuple2` to use with a library that operates on tuples.

**The Data Model:**


```java
public record Point(int x, int y) {}
// A generic Tuple2 from a library
public record Tuple2<A, B>(A _1, B _2) {}
```

These two records can hold the same information. An `Iso` is the perfect way to formalize this relationship.

---

## A Step-by-Step Walkthrough

### Step 1: Defining an Iso

Unlike Lenses and Prisms, which are often generated from annotations, Isos are almost always defined manually. This is because the logic for converting between two types is unique to your specific domain.

You create an `Iso` using the static `Iso.of(get, reverseGet)` constructor.


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

### Step 2: The Core Iso Operations

An `Iso` provides two fundamental, lossless operations:

* **`get(source)`**: The "forward" conversion (e.g., from `Point` to `Tuple2`).
* **`reverseGet(target)`**: The "backward" conversion (e.g., from `Tuple2` back to `Point`).

Furthermore, every `Iso` is trivially reversible using the **`.reverse()`** method, which returns a new `Iso` with the "get" and "reverseGet" functions swapped.


```java
var pointToTupleIso = Converters.pointToTuple();
var myPoint = new Point(10, 20);

// Forward conversion
Tuple2<Integer, Integer> myTuple = pointToTupleIso.get(myPoint); // -> Tuple2[10, 20]

// Backward conversion using the reversed Iso
Point convertedBack = pointToTupleIso.reverse().get(myTuple); // -> Point[10, 20]
```

### Step 3: Composing Isos as a Bridge

The most powerful feature of an `Iso` is its ability to act as an adapter or "glue" between other optics. Because the conversion is lossless, an `Iso` preserves the "shape" of the optic it's composed with.

* `Iso + Iso = Iso`
* **`Iso + Lens = Lens`**

This second rule is incredibly useful. We can compose our `Iso<Point, Tuple2>` with a `Lens` that operates on a `Tuple2` to create a brand new `Lens` that operates directly on our `Point`!


``` java
// A standard Lens that gets the first element of any Tuple2
Lens<Tuple2<Integer, Integer>, Integer> tupleFirstElementLens = ...;

// The composition: Iso<Point, Tuple2> + Lens<Tuple2, Integer> = Lens<Point, Integer>
Lens<Point, Integer> pointToX = pointToTupleIso.andThen(tupleFirstElementLens);

// We can now use this new Lens to modify the 'x' coordinate of our Point
Point movedPoint = pointToX.modify(x -> x + 5, myPoint); // -> Point[15, 20]
```

The `Iso` acted as a bridge, allowing a generic `Lens` for tuples to work on our specific `Point` record.

---

## Complete, Runnable Example

This example puts all the steps together to show both direct conversion and composition.


``` java
package org.higherkindedj.example.iso;

import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Lenses; // Assuming generated lenses for Tuple2
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;

public class IsoUsageExample {

    public record Point(int x, int y) {}

    public static class Converters {
        public static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
            return Iso.of(
                point -> Tuple.of(point.x(), point.y()),
                tuple -> new Point(tuple._1(), tuple._2()));
        }
    }

    public static void main(String[] args) {
        var myPoint = new Point(10, 20);
        System.out.println("Original Point: " + myPoint);
        System.out.println("------------------------------------------");

        // 1. Get the Iso and use it for direct conversion
        var pointToTupleIso = Converters.pointToTuple();
        Tuple2<Integer, Integer> myTuple = pointToTupleIso.get(myPoint);
        Point convertedBackPoint = pointToTupleIso.reverse().get(myTuple);

        System.out.println("After `get`:       " + myTuple);
        System.out.println("After `reverse`:   " + convertedBackPoint);
        System.out.println("------------------------------------------");


        // 2. Compose the Iso with a Lens to create a new Lens
        Lens<Tuple2<Integer, Integer>, Integer> tupleFirstElementLens = Tuple2Lenses._1();
        Lens<Point, Integer> pointToX = pointToTupleIso.andThen(tupleFirstElementLens);

        // 3. Use the new composed Lens to perform an update
        Point movedPoint = pointToX.modify(x -> x + 5, myPoint);
        System.out.println("After composing with a Lens to modify 'x': " + movedPoint);
    }
}
```

### Why Isos are a Powerful Bridge

`Lens`, `Prism`, and `Iso` form a powerful trio for modeling any data operation. An `Iso` is the essential glue that holds them together. It allows you to:

* **Decouple Your Domain**: Represent data in the most convenient form for a given task, and use Isos to translate between representations.
* **Refactor with Confidence**: Change an underlying data structure and provide an `Iso` to the old structure, ensuring consumers of your API don't break.
* **Enhance Composability**: Bridge optics that operate on different types, enabling you to build powerful, reusable tools from smaller, generic components.
