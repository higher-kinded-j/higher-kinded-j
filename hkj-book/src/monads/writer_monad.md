# The WriterMonad:
## _Accumulating Output Alongside Computations_

~~~admonish info title="What You'll Learn"
- Why threading a mutable log through every function is painful -- and how Writer eliminates it
- How `Monoid` controls the way accumulated output combines
- Building a complete audit trail that travels with your computation
- Using `tell`, `flatMap`, and `map` to construct a step-by-step receipt
- Extracting the result, the log, or both with `run()`, `exec()`, and `runWriter()`
~~~

~~~ admonish example title="See Example Code:"
[WriterExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/writer/WriterExample.java)
~~~

## The Problem: Logs That Leak Everywhere

Imagine a pricing function that computes a final price through several steps -- add tax, apply a discount, add shipping. You need a complete audit log of every step. You cannot use `System.out.println` (not composable, not testable). So you reach for a shared mutable list:

```java
// The ugly way: threading a mutable log through every function
List<String> log = new ArrayList<>();
double price = addTax(subtotal, log);          // log.add("Tax added: ...")
double total  = applyDiscount(price, log);     // log.add("Discount applied: ...")
double finalP = addShipping(total, log);       // log.add("Shipping added: ...")
// Every function needs a log parameter. Leaky. Messy. Untestable.
```

Every function must accept *and* mutate that list. The log is invisible in the return type, impossible to compose, and a magnet for bugs. What you actually want is a return value that carries both the result *and* the log -- automatically, invisibly, composably.

That is exactly what `Writer` does.

## The Fix: Writer Carries the Log For You

A `Writer<W, A>` pairs a computed value `A` with an accumulated log `W`. When you sequence steps with `flatMap`, the logs combine automatically through a `Monoid<W>` -- no manual bookkeeping, no mutable state.

The `Writer<W, A>` record is minimal by design:

```java
public record Writer<W, A>(W log, A value) implements WriterKind<W, A> {
    // Factory methods
    static <W, A> Writer<W, A> value(Monoid<W> monoidW, A value); // empty log + value
    static <W>    Writer<W, Unit> tell(W log);                     // log + Unit value

    // Accessors
    A run();   // extract value, discard log
    W exec();  // extract log, discard value
}
```

Two fields. Two factory methods. Two accessors. The complexity lives in how steps *compose* -- and that is where `Monoid` and `flatMap` come in.

### Monoid Made Tangible

The `Monoid<W>` tells Writer *how* to combine logs. Three concrete examples:

```
String:  ""  + "step1; " + "step2; "   -->  "step1; step2; "
List:    []  ++ ["step1"] ++ ["step2"]  -->  ["step1", "step2"]
Sum:      0  +  1         +  1          -->  2  (counting operations)
```

A Monoid needs two things: an `empty()` value (the starting point) and a `combine()` operation (how two logs merge). Writer handles the rest.

In Java, a String monoid looks like this:

```java
class StringMonoid implements Monoid<String> {
    @Override public String empty() { return ""; }
    @Override public String combine(String x, String y) { return x + y; }
}
```

Swap in a different Monoid and Writer accumulates a completely different kind of output -- no other code changes needed.

## Core Components

![writer.svg](../images/puml/writer.svg)

| Component | Role |
|-----------|------|
| `Writer<W, A>` | Record holding a `log` of type `W` and a `value` of type `A` |
| `Monoid<W>` | Defines `empty()` and `combine()` for the log type |
| `WriterMonad<W>` | Provides `of`, `map`, `flatMap`, `ap` -- all log-aware |
| `WriterKind<W, A>` | HKT interface; `Writer` implements it via holder pattern |
| `WriterKindHelper.WRITER` | Enum singleton for `widen`, `narrow`, `tell`, `value`, `run`, `exec`, `runWriter` |

~~~admonish note title="How Log Accumulation Works"
```
flatMap step 1          flatMap step 2          flatMap step 3
┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│ log: "A"     │        │ log: "B"     │        │ log: "C"     │
│ value: 10    │───────>│ value: 20    │───────>│ value: 30    │
└──────────────┘        └──────────────┘        └──────────────┘
                  combine("A","B")         combine("AB","C")
                  via Monoid               via Monoid

Final: Writer(log: "ABC", value: 30)
```

Each `flatMap` step produces a new `(log, value)` pair. The logs from both the input and the step are combined using the `Monoid<W>.combine()` operation, accumulating output across the entire chain.
~~~

~~~admonish example title="Example: Building a Calculation Receipt"

A pricing calculation that produces both a final price and a step-by-step receipt.

**Step 1 -- Set up the Monoid and Monad**

```java
import static org.higherkindedj.hkt.writer.WriterKindHelper.*;

// Monoid that combines log strings by concatenation
Monoid<String> logMonoid = new Monoid<>() {
    public String empty() { return ""; }
    public String combine(String x, String y) { return x + y; }
};

var monad = new WriterMonad<>(logMonoid);
```

**Step 2 -- Define pricing steps as functions**

Each step returns a Writer: the result *and* a log entry. No log parameter needed.

```java
// Each function: takes a price, returns Writer(log, newPrice)
Function<Double, Kind<WriterKind.Witness<String>, Double>> addTax = price -> {
    var taxed = price * 1.08;
    return WRITER.widen(new Writer<>(
        "Tax 8%%: $%.2f -> $%.2f; ".formatted(price, taxed), taxed));
};

Function<Double, Kind<WriterKind.Witness<String>, Double>> applyDiscount = price -> {
    var discounted = price * 0.90;
    return WRITER.widen(new Writer<>(
        "Discount 10%%: $%.2f -> $%.2f; ".formatted(price, discounted), discounted));
};

Function<Double, Kind<WriterKind.Witness<String>, Double>> addShipping = price -> {
    var shipped = price + 5.00;
    return WRITER.widen(new Writer<>(
        "Shipping: +$5.00 -> $%.2f; ".formatted(shipped), shipped));
};
```

**Step 3 -- Compose the pipeline**

`flatMap` threads the value forward and accumulates the log at each step.

```java
// Start with subtotal $100, log the starting point
var start = monad.flatMap(
    ignored -> WRITER.value(logMonoid, 100.0),
    WRITER.tell("Subtotal: $100.00; ")
);

// Chain: tax -> discount -> shipping
var afterTax      = monad.flatMap(addTax, start);
var afterDiscount = monad.flatMap(applyDiscount, afterTax);
var finalPrice    = monad.flatMap(addShipping, afterDiscount);
```

**Step 4 -- Extract the results**

```java
// Get just the final price
Double price = WRITER.run(finalPrice);
// --> 102.06

// Get just the receipt log
String receipt = WRITER.exec(finalPrice);
// --> "Subtotal: $100.00; Tax 8%: ... Discount 10%: ... Shipping: ..."

// Get both as a Writer record
var result = WRITER.runWriter(finalPrice);
System.out.println("Receipt: " + result.log());
System.out.println("Total:   $" + result.value());
```

Every step is a pure function. The log is never passed as a parameter -- Writer carries it invisibly. The receipt and the price arrive together at the end.
~~~

~~~admonish note title="Before vs After"

Compare the mutable-log approach from the opening with the Writer version:

| | Mutable Log | Writer |
|---|---|---|
| **Log location** | Separate `List<String>` parameter | Inside the return value |
| **Function signature** | `double addTax(double price, List<String> log)` | `Function<Double, Kind<..., Double>>` -- no log param |
| **Composability** | Must manually pass the log through every call | `flatMap` chains compose automatically |
| **Testability** | Hard to test without mocking the list | Pure functions -- assert on `run()` and `exec()` |
| **Thread safety** | Shared mutable list is not thread-safe | Immutable records, no shared state |
~~~

~~~admonish tip title="tell vs map vs flatMap"

These three operations serve distinct roles. Understanding when to use each is key to working with Writer effectively.

| Operation | What it does | Touches the log? | Touches the value? |
|-----------|-------------|-------------------|--------------------|
| `tell(msg)` | Appends to the log; value is `Unit` | Yes -- sets the log | No -- value is `Unit` |
| `map(f)` | Transforms the value; log passes through unchanged | No | Yes |
| `flatMap(f)` | Runs a function that returns a new Writer; **combines** both logs via Monoid | Yes -- combines | Yes |

**`tell`** is for inserting a log entry without affecting the computation:

```java
var logged = WRITER.tell("Checkpoint reached; ");
// Writer(log: "Checkpoint reached; ", value: Unit)
```

**`map`** is for transforming the value while leaving the log untouched:

```java
var doubled = monad.map(x -> x * 2, WRITER.value(logMonoid, 50.0));
// Writer(log: "", value: 100.0)  -- log unchanged
```

**`flatMap`** is for chaining steps that each produce their own log:

```java
var chained = monad.flatMap(addTax, WRITER.value(logMonoid, 100.0));
// Writer(log: "Tax 8%: $100.00 -> $108.00; ", value: 108.0)  -- logs merged
```
~~~

## When to Use Writer

| Scenario | Writer? |
|----------|---------|
| Accumulating logs, metrics, or audit trails alongside a computation | Yes -- this is Writer's sweet spot |
| Tracing steps in a calculation for debugging | Yes -- use `tell` for step-by-step entries |
| Building up a list of results or messages | Yes -- use `Writer<List<T>, A>` with a list-concat Monoid |
| Side effects that hit the outside world (console, network, DB) | No -- use [IO](./io_monad.md) instead |
| Combining Writer with other effects (async, errors) | Use [WriterT transformer](../transformers/transformers.md) |

~~~admonish important title="Key Points"
- `Writer<W, A>` pairs a computation result (`A`) with accumulated output (`W`).
- The `Monoid<W>` defines how outputs combine -- concatenation for strings, appending for lists, addition for numbers.
- `flatMap` automatically combines logs from both steps using the Monoid -- no manual bookkeeping.
- `tell(log)` creates a Writer that only logs (value is `Unit`) -- useful for inserting log entries into a chain.
- `map(f)` transforms only the value -- the log passes through unchanged.
- `run()` extracts just the value; `exec()` extracts just the log; `runWriter()` gives you both as a `Writer` record.
- `Writer<W, A>` integrates with HKT via `WriterKind`, so `widen`/`narrow` are zero-cost casts.
~~~

---

~~~admonish example title="Benchmarks"
Writer has dedicated JMH benchmarks measuring log accumulation overhead, Monoid combination cost, and chain depth. Key expectations:

- **Pure value operations** (`map`, `of`) are fast -- they don't invoke the Monoid
- **`flatMap` chains** incur Monoid combination cost at each step -- use an efficient Monoid (e.g., `StringBuilder` or list append rather than string concatenation for long logs)
- **Deep chains** scale linearly with the Monoid's `combine` cost

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*WriterBenchmark.*"
```
See [Benchmarks & Performance](../benchmarks.md) for full details and how to interpret results.
~~~

---

**Previous:** [Performance](vstream_performance.md)
**Next:** [Const](const_type.md)
