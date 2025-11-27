# Isomorphisms: A Practical Guide

## _Data Equivalence with Isos_

~~~admonish info title="What You'll Learn"
- How to define lossless, reversible conversions between equivalent types
- Creating isomorphisms with `Iso.of(get, reverseGet)`
- Using `reverse()` to flip conversion directions
- Step-by-step transformation workflows for data format conversion
- Testing round-trip properties to ensure conversion correctness
- When to use isos vs direct conversion methods vs manual adapters
~~~

~~~admonish title="Example Code"
[IsoUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/IsoUsageExample.java)
~~~

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

public record Tuple2<A, B>(A _1, B _2) {}
```

These two records can hold the same information. An `Iso` is the perfect way to formalize this relationship.

---

## Think of Isos Like...

* **A universal translator**: Perfect two-way conversion between equivalent representations
* **A reversible adapter**: Converts between formats without losing information
* **A bridge**: Connects two different structures that represent the same data
* **A currency exchange**: Converts between equivalent values at a 1:1 rate

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

// Demonstrate perfect round-trip
assert myPoint.equals(convertedBack); // Always true for lawful Isos
```

### Step 3: Composing Isos as a Bridge

The most powerful feature of an `Iso` is its ability to act as an adapter or "glue" between other optics. Because the conversion is lossless, an `Iso` preserves the "shape" of the optic it's composed with.

* `Iso + Iso = Iso`
* **`Iso + Lens = Lens`**
* **`Iso + Prism = Prism`**
* **`Iso + Traversal = Traversal`**

This second rule is incredibly useful. We can compose our `Iso<Point, Tuple2>` with a `Lens` that operates on a `Tuple2` to create a brand new `Lens` that operates directly on our `Point`!

```java
// A standard Lens that gets the first element of any Tuple2
Lens<Tuple2<Integer, Integer>, Integer> tupleFirstElementLens = ...;

// The composition: Iso<Point, Tuple2> + Lens<Tuple2, Integer> = Lens<Point, Integer>
Lens<Point, Integer> pointToX = pointToTupleIso.andThen(tupleFirstElementLens);

// We can now use this new Lens to modify the 'x' coordinate of our Point
Point movedPoint = pointToX.modify(x -> x + 5, myPoint); // -> Point[15, 20]
```

The `Iso` acted as a bridge, allowing a generic `Lens` for tuples to work on our specific `Point` record.

---

## When to Use Isos vs Other Approaches

### Use Isos When:

* **Data format conversion** - Converting between equivalent representations
* **Legacy system integration** - Bridging old and new data formats
* **Library interoperability** - Adapting your types to work with external libraries
* **Composable adapters** - Building reusable conversion components

```java
// Perfect for format conversion
Iso<LocalDate, String> dateStringIso = Iso.of(
    date -> date.format(DateTimeFormatter.ISO_LOCAL_DATE),
    dateStr -> LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
);

// Use with any date-focused lens
Lens<Person, String> birthDateStringLens = 
    PersonLenses.birthDate().andThen(dateStringIso);
```

### Use Direct Conversion Methods When:

* **One-way conversion** - You don't need the reverse operation
* **Non-lossless conversion** - Information is lost in the conversion
* **Performance critical paths** - Minimal abstraction overhead needed

```java
// Simple one-way conversion
String pointDescription = point.x() + "," + point.y();
```

### Use Manual Adapters When:

* **Complex conversion logic** - Multi-step or conditional conversions
* **Validation required** - Conversion might fail
* **Side effects needed** - Logging, caching, etc.

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

### ❌ Don't Do This:

```java
// Lossy conversion - not a true isomorphism
Iso<Double, Integer> lossyIso = Iso.of(
    d -> d.intValue(),    // Loses decimal precision!
    i -> i.doubleValue()  // Can't recover original value
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

### ✅ Do This Instead:

```java
// True isomorphism - perfect round-trip
Iso<Point, String> goodPointIso = Iso.of(
    point -> point.x() + "," + point.y(),
    str -> {
        String[] parts = str.split(",");
        return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
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

## Performance Notes

Isos are designed for efficient, lossless conversion:

* **Zero overhead composition**: Multiple Iso compositions are fused into single operations
* **Lazy evaluation**: Conversions only happen when needed
* **Type safety**: All conversions are checked at compile time
* **Reusable**: Isos can be stored and reused across your application

**Best Practice**: For frequently used conversions, create Isos as constants and test them:

```java
public class DataIsos {
    public static final Iso<UserId, Long> USER_ID_LONG = 
        Iso.of(UserId::value, UserId::new);
    
    public static final Iso<Money, BigDecimal> MONEY_DECIMAL = 
        Iso.of(Money::amount, Money::new);
    
    // Test your isos
    static {
        testIsomorphism(USER_ID_LONG, new UserId(12345L));
        testIsomorphism(MONEY_DECIMAL, new Money(new BigDecimal("99.99")));
    }
  
    private static <A, B> void testIsomorphism(Iso<A, B> iso, A original) {
        B converted = iso.get(original);
        A roundTrip = iso.reverse().get(converted);
        if (!original.equals(roundTrip)) {
            throw new AssertionError("Iso failed round-trip test: " + original + " -> " + converted + " -> " + roundTrip);
        }
    }
}
```

---

## Real-World Examples

### 1. API Data Transformation

```java
// Internal model
public record Customer(String name, String email, LocalDate birthDate) {}

// External API model
public record CustomerDto(String fullName, String emailAddress, String birthDateString) {}

public class CustomerIsos {
    public static final Iso<Customer, CustomerDto> CUSTOMER_DTO = Iso.of(
        // Convert to DTO
        customer -> new CustomerDto(
            customer.name(),
            customer.email(),
            customer.birthDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        ),
        // Convert from DTO
        dto -> new Customer(
            dto.fullName(),
            dto.emailAddress(),
            LocalDate.parse(dto.birthDateString(), DateTimeFormatter.ISO_LOCAL_DATE)
        )
    );
  
    // Now any Customer lens can work with DTOs
    public static final Lens<CustomerDto, String> DTO_NAME = 
        CUSTOMER_DTO.reverse().andThen(CustomerLenses.name()).andThen(CUSTOMER_DTO);
}
```

### 2. Configuration Format Conversion

```java
// Different configuration representations
public record DatabaseConfig(String host, int port, String database) {}
public record ConnectionString(String value) {}

public class ConfigIsos {
    public static final Iso<DatabaseConfig, ConnectionString> DB_CONNECTION = Iso.of(
        // To connection string
        config -> new ConnectionString(
            "jdbc:postgresql://" + config.host() + ":" + config.port() + "/" + config.database()
        ),
        // From connection string
        conn -> {
            // Simple parser for this example
            String url = conn.value();
            String[] parts = url.replace("jdbc:postgresql://", "").split("[:/]");
            return new DatabaseConfig(parts[0], Integer.parseInt(parts[1]), parts[2]);
        }
    );
  
    // Use with existing configuration lenses
    public static final Lens<DatabaseConfig, String> CONNECTION_STRING_HOST = 
        DB_CONNECTION.andThen(
            Lens.of(
                cs -> cs.value().split("//")[1].split(":")[0],
                (cs, host) -> new ConnectionString(cs.value().replaceFirst("//[^:]+:", "//" + host + ":"))
            )
        ).andThen(DB_CONNECTION.reverse());
}
```

### 3. Wrapper Type Integration

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

## Complete, Runnable Example

This example puts all the steps together to show both direct conversion and composition.

```java
public class IsoUsageExample {

    @GenerateLenses
    public record Point(int x, int y) {}

    @GenerateLenses
    public record Circle(Point centre, int radius) {}

    public static class Converters {
        @GenerateIsos
        public static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
            return Iso.of(
                    point -> Tuple.of(point.x(), point.y()),
                    tuple -> new Point(tuple._1(), tuple._2()));
        }

        // Additional useful Isos
        public static final Iso<Point, String> POINT_STRING = Iso.of(
                point -> point.x() + "," + point.y(),
                str -> {
                    String[] parts = str.split(",");
                    return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
        );
    }

    // Test helper
    private static <A, B> void testRoundTrip(Iso<A, B> iso, A original, String description) {
        B converted = iso.get(original);
        A roundTrip = iso.reverse().get(converted);
        System.out.println(description + ":");
        System.out.println("  Original:  " + original);
        System.out.println("  Converted: " + converted);
        System.out.println("  Round-trip: " + roundTrip);
        System.out.println("  Success: " + original.equals(roundTrip));
        System.out.println();
    }

    public static void main(String[] args) {
        // 1. Define a point and circle.
        var myPoint = new Point(10, 20);
        var myCircle = new Circle(myPoint, 5);

        System.out.println("=== ISO USAGE EXAMPLE ===");
        System.out.println("Original Point: " + myPoint);
        System.out.println("Original Circle: " + myCircle);
        System.out.println("------------------------------------------");

        // 2. Get the generated Iso.
        var pointToTupleIso = ConvertersIsos.pointToTuple;

        // --- SCENARIO 1: Direct conversions and round-trip testing ---
        System.out.println("--- Scenario 1: Direct Conversions ---");
        testRoundTrip(pointToTupleIso, myPoint, "Point to Tuple conversion");
        testRoundTrip(Converters.POINT_STRING, myPoint, "Point to String conversion");

        // --- SCENARIO 2: Using reverse() ---
        System.out.println("--- Scenario 2: Reverse Operations ---");
        var tupleToPointIso = pointToTupleIso.reverse();
        var myTuple = Tuple.of(30, 40);
        Point pointFromTuple = tupleToPointIso.get(myTuple);
        System.out.println("Tuple: " + myTuple + " -> Point: " + pointFromTuple);
        System.out.println();

        // --- SCENARIO 3: Composition with lenses ---
        System.out.println("--- Scenario 3: Composition with Lenses ---");

        // Create a lens manually that works with Point directly
        Lens<Point, Integer> pointToXLens = Lens.of(
                Point::x,
                (point, newX) -> new Point(newX, point.y())
        );

        // Use the lens
        Point movedPoint = pointToXLens.modify(x -> x + 5, myPoint);
        System.out.println("Original point: " + myPoint);
        System.out.println("After moving X by 5: " + movedPoint);
        System.out.println();

        // --- SCENARIO 4: Demonstrating Iso composition ---
        System.out.println("--- Scenario 4: Iso Composition ---");

        // Show how the Iso can be used to convert and work with tuples
        Tuple2<Integer, Integer> tupleRepresentation = pointToTupleIso.get(myPoint);
        System.out.println("Point as tuple: " + tupleRepresentation);

        // Modify the tuple using tuple operations
        Lens<Tuple2<Integer, Integer>, Integer> tupleFirstLens = Tuple2Lenses._1();
        Tuple2<Integer, Integer> modifiedTuple = tupleFirstLens.modify(x -> x * 2, tupleRepresentation);

        // Convert back to Point
        Point modifiedPoint = pointToTupleIso.reverse().get(modifiedTuple);
        System.out.println("Modified tuple: " + modifiedTuple);
        System.out.println("Back to point: " + modifiedPoint);
        System.out.println();

        // --- SCENARIO 5: String format conversions ---
        System.out.println("--- Scenario 5: String Format Conversions ---");

        String pointAsString = Converters.POINT_STRING.get(myPoint);
        System.out.println("Point as string: " + pointAsString);

        Point recoveredFromString = Converters.POINT_STRING.reverse().get(pointAsString);
        System.out.println("Recovered from string: " + recoveredFromString);
        System.out.println("Perfect round-trip: " + myPoint.equals(recoveredFromString));

        // --- SCENARIO 6: Working with Circle centre through Iso ---
        System.out.println("--- Scenario 6: Circle Centre Manipulation ---");

        // Get the centre as a tuple, modify it, and put it back
        Point originalCentre = myCircle.centre();
        Tuple2<Integer, Integer> centreAsTuple = pointToTupleIso.get(originalCentre);
        Tuple2<Integer, Integer> shiftedCentre = Tuple.of(centreAsTuple._1() + 10, centreAsTuple._2() + 10);
        Point newCentre = pointToTupleIso.reverse().get(shiftedCentre);
        Circle newCircle = CircleLenses.centre().set(newCentre, myCircle);

        System.out.println("Original circle: " + myCircle);
        System.out.println("Centre as tuple: " + centreAsTuple);
        System.out.println("Shifted centre tuple: " + shiftedCentre);
        System.out.println("New circle: " + newCircle);
    }
```

**Expected Output:**

```
=== ISO USAGE EXAMPLE ===
Original Point: Point[x=10, y=20]
Original Circle: Circle[centre=Point[x=10, y=20], radius=5]
------------------------------------------
--- Scenario 1: Direct Conversions ---
Point to Tuple conversion:
  Original:  Point[x=10, y=20]
  Converted: Tuple2[_1=10, _2=20]
  Round-trip: Point[x=10, y=20]
  Success: true

Point to String conversion:
  Original:  Point[x=10, y=20]
  Converted: 10,20
  Round-trip: Point[x=10, y=20]
  Success: true

--- Scenario 2: Reverse Operations ---
Tuple: Tuple2[_1=30, _2=40] -> Point: Point[x=30, y=40]

--- Scenario 3: Working with Different Representations ---
Original point: Point[x=10, y=20]
After moving X by 5: Point[x=15, y=20]

--- Scenario 4: Conversion Workflows ---
Point as tuple: Tuple2[_1=10, _2=20]
Modified tuple: Tuple2[_1=20, _2=20]
Back to point: Point[x=20, y=20]

--- Scenario 5: String Format Conversions ---
Point as string: 10,20
Recovered from string: Point[x=10, y=20]
Perfect round-trip: true

--- Scenario 6: Circle Centre Manipulation ---
Original circle: Circle[centre=Point[x=10, y=20], radius=5]
Centre as tuple: Tuple2[_1=10, _2=20]
Shifted centre tuple: Tuple2[_1=20, _2=30]
New circle: Circle[centre=Point[x=20, y=30], radius=5]
```

---

## Why Isos are a Powerful Bridge

`Lens`, `Prism`, and `Iso` form a powerful trio for modelling any data operation. An `Iso` is the essential bridge that enables you to:

* **Work with the Best Representation**: Convert data to the most suitable format for each operation, then convert back when needed.
* **Enable Library Integration**: Adapt your internal data types to work seamlessly with external libraries without changing your core domain model.
* **Maintain Type Safety**: All conversions are checked at compile time, eliminating runtime conversion errors.
* **Build Reusable Converters**: Create tested, reusable conversion components that can be used throughout your application.

The step-by-step conversion approach shown in the examples is the most practical way to use Isos in real applications, providing clear, maintainable code that leverages the strengths of different data representations.

---

**Previous:** [Advanced Prism Patterns](advanced_prism_patterns.md)
**Next:** [Traversals: Handling Bulk Updates](traversals.md)

