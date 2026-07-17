# Glossary: Data & Core Effect Types

~~~admonish info title="What This Page Covers"
- Core data structures and the effect types built on them.
- Part of the [Glossary](../glossary.md); see it for the other categories.
~~~

## Choice

**Definition:** A type representing a choice between two alternatives, similar to `Either` but used specifically in the context of Selective functors. Can be `Left<A>` (needs processing) or `Right<B>` (already processed).

**Example:**
```java
// Helper methods in Selective interface
Choice<String, Integer> needsParsing = Selective.left("42");
Choice<String, Integer> alreadyParsed = Selective.right(42);

// In selective operations
Kind<F, Choice<String, Integer>> input = ...;
Kind<F, Function<String, Integer>> parser = ...;
Kind<F, Integer> result = selective.select(input, parser);
// Parser only applied if Choice is Left
```

**Related:** [Selective Documentation](../functional/selective.md)

---

## Cons

**Definition:** A list decomposition pattern that views a non-empty list as a pair of its first element (head) and the remaining elements (tail). The name comes from Lisp's `cons` function (construct), which builds a list by prepending an element to another list.

**Structure:** A list `[a, b, c, d, e]` is decomposed as `Pair.of(a, [b, c, d, e])`.

**Example:**
```java
import org.higherkindedj.optics.util.ListPrisms;

// Cons prism decomposes list as (head, tail)
Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

List<String> names = List.of("Alice", "Bob", "Charlie");
Optional<Pair<String, List<String>>> decomposed = cons.getOptional(names);
// decomposed = Optional.of(Pair.of("Alice", ["Bob", "Charlie"]))

// Empty lists return Optional.empty()
Optional<Pair<String, List<String>>> empty = cons.getOptional(List.of());
// empty = Optional.empty()

// Build a list by prepending
List<String> built = cons.build(Pair.of("New", List.of("List")));
// built = ["New", "List"]
```

**When To Use:**
- Processing lists from front to back
- Implementing recursive algorithms that peel off the first element
- Pattern matching on list structure
- Building lists by prepending elements

**Relationship to Snoc:** Cons and Snoc are dual patterns. Cons works from the front (head/tail), Snoc works from the back (init/last).

**Related:** [Snoc](#snoc), [List Decomposition](../optics/list_decomposition.md)

---

## Const

**Definition:** A constant functor that wraps a value of type `C` whilst ignoring a phantom type parameter `A`. The second type parameter exists purely for type-level information and has no runtime representation.

**Structure:** `Const<C, A>` where `C` is the concrete value type and `A` is phantom.

**Example:**
```java
// Store a String, phantom type is Integer
Const<String, Integer> stringConst = new Const<>("hello");

String value = stringConst.value(); // "hello"

// Mapping over the phantom type changes the signature but not the value
Const<String, Double> doubleConst = stringConst.mapSecond(i -> i * 2.0);
System.out.println(doubleConst.value()); // Still "hello" (unchanged!)

// Bifunctor allows transforming the actual value
Bifunctor<ConstKind2.Witness> bifunctor = ConstBifunctor.INSTANCE;
Const<Integer, Double> intConst = CONST.narrow2(bifunctor.bimap(
    String::length,
    i -> i * 2.0,
    CONST.widen2(stringConst)
));
System.out.println(intConst.value()); // 5
```

**When To Use:**
- Implementing van Laarhoven lenses and folds
- Accumulating values whilst traversing structures
- Teaching phantom types and their practical applications
- Building optics that extract rather than modify data

**Related:** [Phantom Type](type-system.md#phantom-type), [Bifunctor](type-classes.md#bifunctor), [Const Type Documentation](../monads/const_type.md)

---

## Either

**Definition:** A sum type representing one of two possible values: `Left<L>` (typically an error or alternative) or `Right<R>` (typically the success value). Either is right-biased, meaning operations like `map` and `flatMap` work on the `Right` value.

**Structure:** `Either<L, R>` where `L` is the left type (often error) and `R` is the right type (often success).

**Example:**
```java
// Creating Either values
Either<String, Integer> success = Either.right(42);
Either<String, Integer> failure = Either.left("Not found");

// Pattern matching with fold
String message = success.fold(
    error -> "Error: " + error,
    value -> "Got: " + value
);  // "Got: 42"

// Chaining operations (right-biased)
Either<String, String> result = success
    .map(n -> n * 2)           // Right(84)
    .map(Object::toString);    // Right("84")

// Error recovery
Either<String, Integer> recovered = failure
    .orElse(Either.right(0));  // Right(0)
```

**When To Use:**
- Operations that can fail with typed, structured error information
- Domain errors that need to be handled explicitly
- When you need to preserve error details for later handling

**Effect Path Equivalent:** Use [EitherPath](effect-paths.md#effect-path) for fluent composition.

**Related:** [Either Documentation](../monads/either_monad.md), [EitherPath](../effect/path_either.md)

---

## EitherOrBoth

**Definition:** An *inclusive*-or (known elsewhere as `Ior` or `These`): a sealed type that is a `Left<L>`, a `Right<R>`, or `Both<L, R>` at once. Unlike `Either` and `Validated` (which are exclusive), it models a success that also carries accumulated, non-fatal warnings.

```java
EitherOrBoth<String, Integer> ok      = EitherOrBoth.right(42);
EitherOrBoth<String, Integer> warned  = EitherOrBoth.both("deprecated key", 42);
EitherOrBoth<String, Integer> failed  = EitherOrBoth.left("fatal");
```

**Right-biased with total accessors:** `map`/`flatMap` operate on the right; `getLeft()`/`getRight()` return `Maybe` and never throw. `flatMap` short-circuits on `Left` and accumulates a `Both`'s warnings via a `Semigroup<L>` (default `NonEmptyList.semigroup()`). The monadic `ap` short-circuits; full `Validated`-style accumulation lives on `EitherOrBothPath` (`zipWithAccum`).

**Related:** [EitherOrBoth](../monads/either_or_both_monad.md), [EitherOrBothPath](../effect/path_either_or_both.md), [Either](#either), [Validated](#validated), [NonEmptyList](#nonemptylist)

---

## IO

**Definition:** A type representing a deferred side-effecting computation. The computation is described but not executed until explicitly run, enabling referential transparency and controlled effect execution.

**Structure:** `IO<A>` wraps a `Supplier<A>` that produces the side effect when executed.

**Example:**
```java
// Describing side effects (nothing executes yet)
IO<String> readLine = IO.delay(() -> scanner.nextLine());
IO<Unit> printHello = IO.fromRunnable(() -> System.out.println("Hello"));

// Composing effects
IO<String> program = printHello
    .flatMap(_ -> readLine)
    .map(String::toUpperCase);

// Nothing has happened yet! Execute when ready:
String result = program.run();  // NOW side effects occur

// Sequencing multiple effects
IO<List<String>> readThreeLines = IO.sequence(List.of(
    readLine, readLine, readLine
));
```

**When To Use:**
- Deferring side effects for controlled execution
- Building pure descriptions of effectful programs
- Testing side-effecting code (run different interpreters)
- Ensuring effects happen in a specific order

**Effect Path Equivalent:** Use [IOPath](effect-paths.md#effect-path) for fluent composition.

**Related:** [IO Documentation](../monads/io_monad.md), [IOPath](../effect/path_io.md), [TimeSource](#timesource)

---

## Maybe

**Definition:** A type representing an optional value that may or may not be present. Unlike Java's `Optional`, `Maybe` is designed for functional composition and integrates with Higher-Kinded-J's type class hierarchy.

**Structure:** `Maybe<A>` is either `Just<A>` (contains a value) or `Nothing` (empty).

**Example:**
```java
// Creating Maybe values
Maybe<String> present = Maybe.just("hello");
Maybe<String> absent = Maybe.nothing();

// Safe operations
String upper = present
    .map(String::toUpperCase)
    .orElse("default");  // "HELLO"

// Chaining with flatMap
Maybe<Integer> result = Maybe.just("42")
    .flatMap(s -> {
        try {
            return Maybe.just(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return Maybe.nothing();
        }
    });  // Just(42)

// Pattern matching
String output = absent.fold(
    () -> "Nothing here",
    value -> "Found: " + value
);  // "Nothing here"
```

**When To Use:**
- Values that may legitimately be absent (no error, just missing)
- Null-safe programming without null checks
- When absence is a normal case, not an error

**Effect Path Equivalent:** Use [MaybePath](effect-paths.md#effect-path) for fluent composition.

**Related:** [Maybe Documentation](../monads/maybe_monad.md), [MaybePath](../effect/path_maybe.md)

---

## NonEmptyList

**Definition:** An immutable list guaranteed by its type to contain at least one element: a `head` plus a (possibly empty) `tail`. Because non-emptiness is encoded in the type, `head`, `last`, `reduce`, `min`, and `max` are **total** (they never throw and return no `Optional`). It is the natural carrier for an accumulating validation error channel, where an *invalid* result always has one or more errors.

**Example:**
```java
NonEmptyList<Integer> nel = NonEmptyList.of(1, 2, 3);
int head = nel.head();                       // 1 (total, never throws)
int sum  = nel.reduce((a, b) -> a + b);      // 6 (reduce without an identity)

// Checked construction from possibly-empty data returns Maybe (never throws):
Maybe<NonEmptyList<Integer>> maybe = NonEmptyList.fromList(List.of());   // Nothing

// As the validation error channel, no Semigroup argument, no List.of wrapping:
Validated<NonEmptyList<String>, Integer> bad = Validated.invalidNel("must be positive");
```

**No empty, by design:** there is no empty `NonEmptyList`, and therefore no `Monoid` instance and no `MonadZero`/`Alternative`; the absence of an empty value is the point. It is a `Functor`, `Applicative`, `Monad`, `Foldable`, `Traverse`, and `Semigroup` (concatenation via `NonEmptyList.semigroup()`).

**Related:** [NonEmptyList](../monads/nonemptylist_monad.md), [Validated](#validated), [Semigroup](type-classes.md#semigroup)

---

## Snoc

**Definition:** A list decomposition pattern that views a non-empty list as a pair of all elements except the last (init) and the last element. The name "snoc" is "cons" spelled backwards, reflecting that it works from the opposite end of the list.

**Structure:** A list `[a, b, c, d, e]` is decomposed as `Pair.of([a, b, c, d], e)`.

**Example:**
```java
import org.higherkindedj.optics.util.ListPrisms;

// Snoc prism decomposes list as (init, last)
Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();

List<Integer> numbers = List.of(1, 2, 3, 4, 5);
Optional<Pair<List<Integer>, Integer>> decomposed = snoc.getOptional(numbers);
// decomposed = Optional.of(Pair.of([1, 2, 3, 4], 5))

// Empty lists return Optional.empty()
Optional<Pair<List<Integer>, Integer>> empty = snoc.getOptional(List.of());
// empty = Optional.empty()

// Build a list by appending
List<Integer> built = snoc.build(Pair.of(List.of(1, 2, 3), 4));
// built = [1, 2, 3, 4]
```

**When To Use:**
- Processing lists from back to front
- Algorithms that need the final element
- Pattern matching on "everything before last" structure
- Building lists by appending elements

**Relationship to Cons:** Cons and Snoc are dual patterns. Cons works from the front (head/tail), Snoc works from the back (init/last).

**Related:** [Cons](#cons), [List Decomposition](../optics/list_decomposition.md)

---

## TimeSource

**Definition:** `java.time.Clock` lifted into the effect world, so reading the time is a lazy, composable effect rather than a scattered `Instant.now()` that makes every timestamp untestable. `TimeSource.now()` returns an `IO<Instant>` (with `nowAsync()` for the deferred variant); nothing is read until the effect runs, and each run reads afresh. It is deliberately named `TimeSource`, not `Clock`, so it never clashes with `java.time.Clock`.

**Example:**
```java
import org.higherkindedj.hkt.time.TimeSource;

TimeSource time  = TimeSource.system();         // production
TimeSource fixed = TimeSource.fixed(instant);   // deterministic in tests

IO<Reservation> reserve =
    time.now().map(t -> new Reservation(order.id(), t.plus(hold)));
```

**Testing:** inject `TimeSource.fixed(...)`, or `TimeSource.of(steppableClock)` with `hkj-test`'s `SteppableClock`, and time-dependent code becomes deterministic by moving the clock rather than sleeping.

**Related:** [TimeSource](../monads/io_monad.md), [IO](#io)

---

## Try

**Definition:** A type that captures the result of a computation that may throw an exception. Converts exception-based code into value-based error handling, making exceptions composable.

**Structure:** `Try<A>` is either `Success<A>` (computation succeeded) or `Failure` (exception was thrown).

**Example:**
```java
// Wrapping exception-throwing code
Try<Integer> parsed = Try.of(() -> Integer.parseInt("42"));     // Success(42)
Try<Integer> failed = Try.of(() -> Integer.parseInt("abc"));    // Failure(NumberFormatException)

// For checked-throwing APIs, use Try.attempt with a CheckedSupplier
Try<String> contents = Try.attempt(() -> Files.readString(Path.of("data.txt")));

// Safe chaining - exceptions don't propagate
Try<String> result = parsed
    .map(n -> n * 2)
    .map(Object::toString);  // Success("84")

// Recovery from failure
Integer value = failed
    .recover(ex -> 0)        // Provide default on any exception
    .get();                  // 0

// Conditional recovery
Try<Integer> recovered = failed.recoverWith(ex -> {
    if (ex instanceof NumberFormatException) {
        return Try.success(0);
    }
    return Try.failure(ex);  // Re-throw other exceptions
});
```

**When To Use:**
- Wrapping legacy code that throws exceptions
- Making exception-based APIs composable
- When you want to defer exception handling

**Effect Path Equivalent:** Use [TryPath](effect-paths.md#effect-path) for fluent composition.

**Related:** [Try Documentation](../monads/try_monad.md), [TryPath](../effect/path_try.md)

---

## Tuple

**Definition:** An immutable, fixed-size container that groups multiple values of potentially different types into a single value. Higher-Kinded-J provides `Tuple2` through `Tuple12`, where the number indicates how many elements the tuple holds.

**Structure:** Each tuple is a Java `record` with typed accessors `_1()` through `_N()`.

**Example:**
```java
// Create tuples using the factory method
Tuple2<String, Integer> pair = Tuple.of("Alice", 30);
Tuple3<String, Integer, Boolean> triple = Tuple.of("Alice", 30, true);

// Accessors
String name = pair._1();    // "Alice"
Integer age = pair._2();    // 30

// Mapping individual elements
Tuple2<String, String> mapped = pair.mapSecond(Object::toString);
// ("Alice", "30")

// Full map across all elements
Tuple3<Integer, String, String> transformed = triple.map(
    String::length, Object::toString, b -> b ? "yes" : "no"
);
// (5, "30", "yes")
```

**Available Arities:**
- `Tuple2<A, B>` through `Tuple5<A, B, C, D, E>` are hand-written
- `Tuple6` through `Tuple12` are generated by the `hkj-processor` annotation processor

**When To Use:**
- Accumulating values in for-comprehensions (tuples are used internally to carry bound values between steps)
- Returning multiple values from a function
- Grouping related values without defining a dedicated record

**Related:** [For Comprehension](../functional/for_comprehension.md), [Bifunctor](type-classes.md#bifunctor) (for `Tuple2`)

---

## Unit

**Definition:** A type with exactly one value (`Unit.INSTANCE`), representing the completion of an operation that doesn't produce a meaningful result. The functional equivalent of `void`, but usable as a type parameter.

**Example:**
```java
// IO action that performs a side effect
Kind<IOKind.Witness, Unit> printAction =
    IO_KIND.widen(IO.fromRunnable(() -> System.out.println("Hello")));

// Optional as MonadError<..., Unit>
MonadError<OptionalKind.Witness, Unit> optionalMonad = Instances.monadError(optional());
Kind<OptionalKind.Witness, String> empty =
    optionalMonad.raiseError(Unit.INSTANCE);  // Creates Optional.empty()
```

**When To Use:**
- Effects that don't return a value (logging, printing, etc.)
- Error types for contexts where absence is the only error (Optional, Maybe)

**Related:** [Core Concepts](../hkts/core-concepts.md)

---

## Validated

**Definition:** A type for accumulating multiple errors instead of failing fast on the first error. Unlike `Either`, which short-circuits on the first `Left`, `Validated` collects all errors using a `Semigroup`.

**Structure:** `Validated<E, A>` is either `Valid<A>` (success) or `Invalid<E>` (accumulated errors).

**Example:**
```java
// Individual validations
Validated<List<String>, String> validName = Validated.valid("Alice");
Validated<List<String>, Integer> invalidAge = Validated.invalid(List.of("Age must be positive"));
Validated<List<String>, String> invalidEmail = Validated.invalid(List.of("Invalid email format"));

// Combine with Applicative - ALL errors accumulated
Semigroup<List<String>> listSemigroup = Semigroups.list();
Applicative<Validated.Witness<List<String>>> app = ValidatedApplicative.instance(listSemigroup);

Validated<List<String>, User> result = app.map3(
    validName,
    invalidAge,
    invalidEmail,
    User::new
);
// Invalid(["Age must be positive", "Invalid email format"])

// Convert from Either for fail-fast then accumulate pattern
Either<String, Integer> eitherResult = Either.left("First error");
Validated<String, Integer> validated = Validated.fromEither(eitherResult);
```

**Modern accumulating idiom:** the canonical error channel is now [NonEmptyList](#nonemptylist), so `Validated.invalidNel("...")` and `Validated.validNel(value)` bake in the `Semigroup` and drop the manual `Semigroups.list()` argument. To build a record from several validated fields with every error located, use [Validated Assembly](optics.md#validated-assembly) (`Validated.fields()`), which reports each failure as a [FieldError](optics.md#fielderror).

**When To Use:**
- Form validation where all errors should be shown
- Batch processing where you want all failures reported
- Configuration validation
- Any scenario where fail-fast behaviour loses important information

**Effect Path Equivalent:** Use [ValidationPath](effect-paths.md#effect-path) for fluent composition.

**Related:** [Validated Documentation](../monads/validated_monad.md), [ValidationPath](../effect/path_validation.md), [NonEmptyList](#nonemptylist), [Validated Assembly](optics.md#validated-assembly), [FieldError](optics.md#fielderror)

