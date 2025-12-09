# Glossary of Functional Programming Terms

~~~admonish info title="What This Section Covers"
- Key terminology used throughout Higher-Kinded-J documentation
- Explanations tailored for mid-level Java developers
- Practical examples to reinforce understanding
- Quick reference for concepts you encounter whilst coding
~~~

This glossary provides clear, practical explanations of functional programming and Higher-Kinded-J concepts. Each term includes Java-friendly explanations and examples where helpful.

---

## Type System Concepts

### Contravariant

**Definition:** A type parameter is contravariant when it appears in an "input" or "consumer" position. If `A` is a subtype of `B`, then `F<B>` can be treated as a subtype of `F<A>` when accepting values (note the direction reversal!).

**Java Analogy:** Think of `? super T` in Java generics: this is contravariant. Also, function parameters are contravariant.

**Example:**
```java
// Contravariant behaviour in Java (function parameters)
// A function accepting Object can be used where one accepting String is expected
Comparator<Object> objectComparator = (a, b) -> a.toString().compareTo(b.toString());
Comparator<String> stringComparator = objectComparator; // ✅ Valid - contravariance in action

// Note: Java's Consumer<T> is invariant, so Consumer<Object> ≠ Consumer<String>
// But function *parameters* are naturally contravariant

// In Higher-Kinded-J: Profunctor's first parameter is contravariant
Profunctor<FunctionKind.Witness> prof = FunctionProfunctor.INSTANCE;

Function<String, Integer> stringLength = String::length;
// lmap is contravariant - we pre-process the INPUT
Kind2<FunctionKind.Witness, Integer, Integer> intLength =
    prof.lmap(Object::toString, FUNCTION.widen(stringLength));
// Now accepts Integer input by converting it to String first
```

**Think Of It As:** "Values flow INTO the container" - you're consuming/accepting data.

**Important:** The direction is reversed! A function that accepts `Object` is more flexible than one that accepts only `String`, so `Function<Object, R>` is a "subtype" of `Function<String, R>` in terms of what it can handle.

**Where You'll See It:**
- The first parameter of Profunctor (input side)
- Function parameters
- Consumer types

---

### Covariant

**Definition:** A type parameter is covariant when it appears in an "output" or "producer" position. If `A` is a subtype of `B`, then `F<A>` can be treated as a subtype of `F<B>` when reading values.

**Java Analogy:** Think of `? extends T` in Java generics: this is covariant.

**Example:**
```java
// Covariant behaviour in Java collections (read-only)
List<? extends Number> numbers = new ArrayList<Integer>();
Number n = numbers.get(0); // ✅ Safe to read out as Number

// In Higher-Kinded-J: Functor is covariant in its type parameter
Functor<ListKind.Witness> functor = ListFunctor.INSTANCE;
Kind<ListKind.Witness, Integer> ints = LIST.widen(List.of(1, 2, 3));
Kind<ListKind.Witness, String> strings = functor.map(Object::toString, ints);
// Integer -> String transformation (output direction)
```

**Think Of It As:** "Values flow OUT of the container" - you're producing/reading data.

**Where You'll See It:**
- Functor's type parameter (transforms outputs)
- Bifunctor's both parameters (both are outputs)
- The second parameter of Profunctor (output side)
- Return types of functions

---

### Invariant

**Definition:** A type parameter is invariant when it appears in both input and output positions, or when the type doesn't allow any subtype substitution.

**Java Analogy:** Most mutable collections in Java are invariant: `List<Integer>` is not a subtype of `List<Number>`.

**Example:**
```java
// Invariant behaviour in Java
List<Integer> ints = new ArrayList<>();
List<Number> nums = ints; // ❌ Compilation error!
// Not allowed because:
// - You could read Number (covariant)
// - You could write Number (contravariant)
// Both directions would violate type safety with mutable collections

// In Higher-Kinded-J: MonadError's error type is typically invariant
MonadError<EitherKind.Witness<String>, String> monadError = EitherMonadError.instance();
// The String error type is fixed; you can't substitute it with Object or CharSequence
```

**Think Of It As:** "Locked to exactly this type" - no flexibility in either direction.

**Where You'll See It:**
- Mutable collections
- Types used in both input and output positions
- Type parameters that don't participate in transformation operations

---

### Variance Summary Table

| Variance | Direction | Java Analogy | Example Type Class | Intuition |
|----------|-----------|--------------|-------------------|-----------|
| **Covariant** | Output/Producer | `? extends T` | Functor, Applicative, Monad | "Reading out" |
| **Contravariant** | Input/Consumer | `? super T` | Profunctor (first param) | "Writing in" (reversed) |
| **Invariant** | Neither/Both | No wildcards | Monad error type | "Exact match required" |

---

## Higher-Kinded Type Simulation

### Defunctionalisation

**Definition:** A technique for simulating higher-kinded types in languages that don't natively support them. Instead of passing type constructors as parameters, we represent them with marker types (witnesses) and use these as ordinary type parameters.

**The Problem It Solves:** Java's type system cannot parametrise over type constructors. You cannot write `<F<_>>` in Java to mean "any container type F". Defunctionalisation works around this by using witness types to represent type constructors.

**Example:**
```java
// ❌ What we'd like to write but can't in Java:
public <F<_>, A, B> F<B> map(Function<A, B> f, F<A> fa) { ... }

// ✅ What we write using defunctionalisation:
public <F, A, B> Kind<F, B> map(Function<A, B> f, Kind<F, A> fa) { ... }
// Where F is a witness type like OptionalKind.Witness or ListKind.Witness
```

**How It Works:**
1. Define a marker interface (witness type) for each type constructor (e.g., `ListKind.Witness` for `List`)
2. Use `Kind<F, A>` where `F` is the witness and `A` is the type parameter
3. Provide helper methods to convert between concrete types and their `Kind` representations

**Where You'll See It:** Throughout the Higher-Kinded-J library - it's the foundation of the entire HKT simulation.

**Related:** [Core Concepts](hkts/core-concepts.md)

---

### Higher-Kinded Type (HKT)

**Definition:** A type that abstracts over type constructors. In languages with HKT support, you can write generic code that works with any "container" type like `List`, `Optional`, or `CompletableFuture` without knowing which one at compile time.

**Java Analogy:** Regular generics let you abstract over types (`<T>`). Higher-kinded types let you abstract over type constructors (`<F<_>>`).

**Example:**
```java
// Regular generics (abstracting over types):
public <T> T identity(T value) { return value; }

// Higher-kinded types (abstracting over type constructors):
public <F> Kind<F, Integer> increment(Functor<F> functor, Kind<F, Integer> fa) {
    return functor.map(x -> x + 1, fa);
}

// Works with any Functor:
increment(OptionalFunctor.INSTANCE, OPTIONAL.widen(Optional.of(5)));  // Optional[6]
increment(ListFunctor.INSTANCE, LIST.widen(List.of(1, 2, 3)));        // [2, 3, 4]
```

**Why It Matters:** Enables writing truly generic, reusable functional code that works across different container types.

**Related:** [HKT Introduction](hkts/hkt_introduction.md)

---

### Kind

**Definition:** The core interface in Higher-Kinded-J that simulates higher-kinded types. `Kind<F, A>` represents a type constructor `F` applied to a type `A`.

**Structure:**
- `Kind<F, A>` - Single type parameter (e.g., `List<A>`, `Optional<A>`)
- `Kind2<F, A, B>` - Two type parameters (e.g., `Either<A, B>`, `Function<A, B>`)

**Example:**
```java
// Standard Java types and their Kind representations:
Optional<String>           ≈ Kind<OptionalKind.Witness, String>
List<Integer>              ≈ Kind<ListKind.Witness, Integer>
Either<String, Integer>    ≈ Kind2<EitherKind2.Witness, String, Integer>
Function<String, Integer>  ≈ Kind2<FunctionKind.Witness, String, Integer>

// Converting between representations:
Optional<String> opt = Optional.of("hello");
Kind<OptionalKind.Witness, String> kindOpt = OPTIONAL.widen(opt);
Optional<String> backToOpt = OPTIONAL.narrow(kindOpt);
```

**Think Of It As:** A wrapper that allows Java's type system to work with type constructors generically.

**Note on Either:** Either has two witness types depending on usage:
- `EitherKind.Witness<L>` for `Kind<EitherKind.Witness<L>, R>` - used with Functor/Monad (right-biased)
- `EitherKind2.Witness` for `Kind2<EitherKind2.Witness, L, R>` - used with Bifunctor (both sides)

**Related:** [Core Concepts](hkts/core-concepts.md)

---

### Type Constructor

**Definition:** A type that takes one or more type parameters to produce a concrete type. In other words, it's a "type function" that constructs types.

**Examples:**
```java
// List is a type constructor
List      // Not a complete type (needs a parameter)
List<T>   // Type constructor applied to parameter T
List<String>  // Concrete type

// Either is a type constructor with two parameters
Either           // Not a complete type
Either<L, R>     // Type constructor applied to parameters L and R
Either<String, Integer>  // Concrete type

// Optional is a type constructor
Optional         // Not a complete type
Optional<T>      // Type constructor applied to parameter T
Optional<String> // Concrete type
```

**Notation:** Often written with an underscore to show the "hole": `List<_>`, `Either<String, _>`, `Optional<_>`

**Why It Matters:** Type constructors are what we abstract over with higher-kinded types. Understanding them is key to understanding HKTs.

---

### Witness Type

**Definition:** A marker type used to represent a type constructor in the defunctionalisation pattern. Each type constructor has a corresponding witness type.

**Examples:**
```java
// List type constructor → ListKind.Witness
public interface ListKind<A> extends Kind<ListKind.Witness, A> {
    final class Witness { private Witness() {} }
}

// Optional type constructor → OptionalKind.Witness
public interface OptionalKind<A> extends Kind<OptionalKind.Witness, A> {
    final class Witness { private Witness() {} }
}

// Either type constructor → EitherKind.Witness<L>
public interface EitherKind<L, R> extends Kind2<EitherKind.Witness<L>, L, R> {
    final class Witness<L> { private Witness() {} }
}
```

**Usage:**
```java
// The Witness type is used as the F parameter:
Functor<ListKind.Witness> listFunctor = ListFunctor.INSTANCE;
Functor<OptionalKind.Witness> optionalFunctor = OptionalFunctor.INSTANCE;
MonadError<EitherKind.Witness<String>, String> eitherMonad = EitherMonadError.instance();
```

**Think Of It As:** A compile-time tag that identifies which type constructor we're working with.

**Related:** [Core Concepts](hkts/core-concepts.md)

---

### Phantom Type

**Definition:** A type parameter that appears in a type signature but has no corresponding runtime representation. It exists purely for compile-time type safety and doesn't store any actual data of that type.

**Key Characteristics:**
- Present in the type signature for type-level information
- Never instantiated or stored at runtime
- Used for type-safe APIs without runtime overhead
- Enables compile-time guarantees whilst maintaining efficiency

**Example:**
```java
// Const<C, A> uses A as a phantom type
Const<String, Integer> stringConst = new Const<>("hello");
// The Integer type parameter is phantom - no Integer is stored!

String value = stringConst.value(); // "hello"

// Mapping over the phantom type changes the signature but not the value
Const<String, Double> doubleConst = stringConst.mapSecond(i -> i * 2.0);
System.out.println(doubleConst.value()); // Still "hello" (unchanged!)
```

**Common Use Cases:**
- **State tracking at compile time**: Phantom types in state machines (e.g., `DatabaseConnection<Closed>` vs `DatabaseConnection<Open>`)
- **Units of measure**: Tracking units without runtime overhead (e.g., `Measurement<Metres>` vs `Measurement<Feet>`)
- **Const type**: The second type parameter in `Const<C, A>` is phantom, enabling fold and getter patterns
- **Type-safe builders**: Ensuring build steps are called in the correct order

**Real-World Example:**
```java
// State machine with phantom types
class FileHandle<State> {
    private File file;

    // Only available when Closed
    FileHandle<Open> open() { ... }
}

class Open {}
class Closed {}

// Type-safe at compile time:
FileHandle<Closed> closed = new FileHandle<>();
FileHandle<Open> opened = closed.open();  // ✅ Allowed
// opened.open();  // ❌ Compile error - already open!
```

**Benefits:**
- Zero runtime cost - no additional memory or processing
- Compile-time safety - prevents incorrect API usage
- Self-documenting APIs - type signature conveys intent
- Enables advanced patterns like GADTs (Generalised Algebraic Data Types)

**Where You'll See It:**
- `Const<C, A>` - the `A` parameter is phantom
- Witness types in HKT encoding (though serving a different purpose)
- State machines and protocol enforcement
- Type-level programming patterns

**Related:** [Const Type Documentation](monads/const_type.md), [Witness Type](#witness-type)

---

## Functional Type Classes

### Applicative

**Definition:** A type class that extends Functor with the ability to lift pure values into a context and combine multiple independent computations.

**Core Operations:**
- `of(A value)` - Lift a pure value into the context
- `ap(Kind<F, Function<A,B>> ff, Kind<F, A> fa)` - Apply a wrapped function to a wrapped value
- `map2`, `map3`, etc. - Combine multiple wrapped values

**Example:**
```java
Applicative<OptionalKind.Witness> app = OptionalApplicative.INSTANCE;

// Lift pure values
Kind<OptionalKind.Witness, Integer> five = app.of(5);  // Optional[5]

// Combine independent values
Kind<OptionalKind.Witness, String> result = app.map2(
    app.of("Hello"),
    app.of("World"),
    (a, b) -> a + " " + b
);  // Optional["Hello World"]
```

**When To Use:** Combining multiple independent effects (form validation, parallel computations, configuration assembly).

**Related:** [Applicative Documentation](functional/applicative.md)

---

### Bifunctor

**Definition:** A type class for types with two covariant parameters, allowing transformation of both sides independently or simultaneously.

**Core Operations:**
- `bimap(Function<A,C> f, Function<B,D> g, Kind2<F,A,B> fab)` - Transform both parameters
- `first(Function<A,C> f, Kind2<F,A,B> fab)` - Transform only the first parameter
- `second(Function<B,D> g, Kind2<F,A,B> fab)` - Transform only the second parameter

**Example:**
```java
Bifunctor<EitherKind.Witness> bifunctor = EitherBifunctor.INSTANCE;

Either<String, Integer> either = Either.right(42);
Kind2<EitherKind.Witness, String, Integer> kindEither = EITHER.widen(either);

// Transform both sides
Kind2<EitherKind.Witness, Integer, String> transformed =
    bifunctor.bimap(String::length, Object::toString, kindEither);
// Right("42")
```

**When To Use:** Transforming error and success channels, working with pairs/tuples, API format conversion.

**Related:** [Bifunctor Documentation](functional/bifunctor.md)

---

### Functor

**Definition:** The most basic type class for types that can be "mapped over". Allows transforming values inside a context without changing the context structure.

**Core Operation:**
- `map(Function<A,B> f, Kind<F,A> fa)` - Apply a function to the wrapped value

**Example:**
```java
Functor<ListKind.Witness> functor = ListFunctor.INSTANCE;

Kind<ListKind.Witness, String> strings = LIST.widen(List.of("one", "two"));
Kind<ListKind.Witness, Integer> lengths = functor.map(String::length, strings);
// [3, 3]
```

**Laws:**
- Identity: `map(x -> x, fa) == fa`
- Composition: `map(g.compose(f), fa) == map(g, map(f, fa))`

**When To Use:** Simple transformations where the context (container structure) stays the same.

**Related:** [Functor Documentation](functional/functor.md)

---

### Monad

**Definition:** A type class that extends Applicative with the ability to chain dependent computations (flatMap/bind).

**Core Operation:**
- `flatMap(Function<A, Kind<F,B>> f, Kind<F,A> ma)` - Chain computations where each depends on the previous result

**Additional Operations:**
- `flatMap2/3/4/5(...)` - Combine multiple monadic values with a function that returns a monadic value (similar to `map2/3/4/5` but with effectful combining function)
- `as(B value, Kind<F,A> ma)` - Replace the result while preserving the effect
- `peek(Consumer<A> action, Kind<F,A> ma)` - Perform side effect without changing the value

**Example:**
```java
Monad<OptionalKind.Witness> monad = OptionalMonad.INSTANCE;

// Chain dependent operations
Kind<OptionalKind.Witness, String> result =
    monad.flatMap(
        userId -> monad.flatMap(
            profile -> findAccount(profile.accountId()),
            findProfile(userId)
        ),
        findUser("user123")
    );

// Combine multiple monadic values with effectful result
Kind<OptionalKind.Witness, Order> order =
    monad.flatMap2(
        findUser("user123"),
        findProduct("prod456"),
        (user, product) -> validateAndCreateOrder(user, product)
    );
```

**Laws:**
- Left Identity: `flatMap(f, of(a)) == f(a)`
- Right Identity: `flatMap(of, m) == m`
- Associativity: `flatMap(g, flatMap(f, m)) == flatMap(x -> flatMap(g, f(x)), m)`

**When To Use:** Sequential operations where each step depends on the previous result (database queries, async workflows, error handling pipelines).

**Related:** [Monad Documentation](functional/monad.md)

---

### Monoid

**Definition:** A type class for types that have an associative binary operation (`combine`) and an identity element (`empty`). Extends Semigroup by adding the identity element, making it safe for reducing empty collections.

**Core Operations:**
- `empty()` - The identity element
- `combine(A a1, A a2)` - Associative binary operation (from Semigroup)
- `combineAll(Iterable<A> elements)` - Combine all elements in a collection
- `combineN(A value, int n)` - Combine a value with itself n times
- `isEmpty(A value)` - Test if a value equals the empty element

**Example:**
```java
Monoid<Integer> intAddition = Monoids.integerAddition();

// Identity law: empty is the neutral element
intAddition.combine(5, intAddition.empty());  // 5
intAddition.combine(intAddition.empty(), 5);  // 5

// Combine a collection
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
Integer sum = intAddition.combineAll(numbers);  // 15

// Repeated application
Integer result = intAddition.combineN(3, 4);  // 12 (3+3+3+3)

// Working with Optional values
Monoid<Optional<Integer>> maxMonoid = Monoids.maximum();
Optional<Integer> max = maxMonoid.combineAll(
    List.of(Optional.of(5), Optional.empty(), Optional.of(10))
);  // Optional[10]
```

**Common Instances in `Monoids` utility:**
- `integerAddition()`, `longAddition()`, `doubleAddition()` - Numeric addition
- `integerMultiplication()`, `longMultiplication()`, `doubleMultiplication()` - Numeric multiplication
- `string()` - String concatenation
- `list()`, `set()` - Collection concatenation/union
- `booleanAnd()`, `booleanOr()` - Boolean operations
- `firstOptional()`, `lastOptional()` - First/last non-empty Optional
- `maximum()`, `minimum()` - Max/min value aggregation with Optional

**Laws:**
- Left Identity: `combine(empty(), a) == a`
- Right Identity: `combine(a, empty()) == a`
- Associativity: `combine(a, combine(b, c)) == combine(combine(a, b), c)` (from Semigroup)

**When To Use:** Aggregating data (summing values, concatenating strings), reducing collections, folding data structures, accumulating results in parallel computations.

**Related:** [Semigroup and Monoid Documentation](functional/semigroup_and_monoid.md)

---

### Semigroup

**Definition:** A type class for types that have an associative binary operation. The most fundamental algebraic structure for combining values.

**Core Operation:**
- `combine(A a1, A a2)` - Associative binary operation

**Example:**
```java
Semigroup<String> stringConcat = Semigroups.string();
String result = stringConcat.combine("Hello", " World");  // "Hello World"

// With custom delimiter
Semigroup<String> csvConcat = Semigroups.string(", ");
String csv = csvConcat.combine("apple", "banana");  // "apple, banana"

// For error accumulation in Validated
Semigroup<String> errorAccumulator = Semigroups.string("; ");
Applicative<Validated.Witness<String>> validator =
    ValidatedMonad.instance(errorAccumulator);
// Errors are combined: "Field A is invalid; Field B is required"
```

**Common Instances in `Semigroups` utility:**
- `string()` - Basic string concatenation
- `string(String delimiter)` - String concatenation with delimiter
- `list()` - List concatenation
- `set()` - Set union
- `first()` - Always takes the first value
- `last()` - Always takes the last value

**Laws:**
- Associativity: `combine(a, combine(b, c)) == combine(combine(a, b), c)`

**When To Use:** Error accumulation (especially with Validated), combining partial results, building aggregators where an empty/identity value doesn't make sense.

**Related:** [Semigroup and Monoid Documentation](functional/semigroup_and_monoid.md)

---

### MonadError

**Definition:** A type class that extends Monad with explicit error handling capabilities for a specific error type.

**Core Operations:**
- `raiseError(E error)` - Create an error state
- `handleErrorWith(Kind<F,A> ma, Function<E, Kind<F,A>> handler)` - Recover from errors

**Example:**
```java
MonadError<EitherKind.Witness<String>, String> monadError = EitherMonadError.instance();

Kind<EitherKind.Witness<String>, Double> result =
    monadError.handleErrorWith(
        divideOperation,
        error -> monadError.of(0.0)  // Provide default on error
    );
```

**When To Use:** Workflows that need explicit error handling and recovery (validation, I/O operations, API calls).

**Related:** [MonadError Documentation](functional/monad_error.md)

---

### Profunctor

**Definition:** A type class for types that are contravariant in their first parameter (input) and covariant in their second parameter (output). The canonical example is `Function<A, B>`.

**Core Operations:**
- `lmap(Function<C,A> f, Kind2<P,A,B> pab)` - Pre-process the input (contravariant)
- `rmap(Function<B,D> g, Kind2<P,A,B> pab)` - Post-process the output (covariant)
- `dimap(Function<C,A> f, Function<B,D> g, Kind2<P,A,B> pab)` - Transform both simultaneously

**Example:**
```java
Profunctor<FunctionKind.Witness> prof = FunctionProfunctor.INSTANCE;

Function<String, Integer> stringLength = String::length;
Kind2<FunctionKind.Witness, String, Integer> kindFunc = FUNCTION.widen(stringLength);

// Adapt to work with integers (converting to string first)
Kind2<FunctionKind.Witness, Integer, Integer> intLength =
    prof.lmap(Object::toString, kindFunc);
```

**When To Use:** Building adaptable pipelines, API adapters, validation frameworks that need to work with different input/output formats.

**Related:** [Profunctor Documentation](functional/profunctor.md)

---

### Selective

**Definition:** A type class that sits between Applicative and Monad, providing conditional effects with static structure. All branches must be known upfront, enabling static analysis.

**Core Operations:**
- `select(Kind<F, Choice<A,B>> fab, Kind<F, Function<A,B>> ff)` - Conditionally apply a function
- `whenS(Kind<F, Boolean> cond, Kind<F, Unit> effect)` - Execute effect only if condition is true
- `ifS(Kind<F, Boolean> cond, Kind<F, A> then, Kind<F, A> else)` - If-then-else with visible branches

**Example:**
```java
Selective<IOKind.Witness> selective = IOSelective.INSTANCE;

// Only log if debug is enabled
Kind<IOKind.Witness, Boolean> debugEnabled =
    IO_KIND.widen(IO.delay(() -> config.isDebug()));
Kind<IOKind.Witness, Unit> logEffect =
    IO_KIND.widen(IO.fromRunnable(() -> log.debug("Debug info")));

Kind<IOKind.Witness, Unit> conditionalLog = selective.whenS(debugEnabled, logEffect);
```

**When To Use:** Feature flags, conditional logging, configuration-based behaviour, multi-source fallback strategies.

**Related:** [Selective Documentation](functional/selective.md)

---

### Natural Transformation

**Definition:** A polymorphic function between type constructors. Given type constructors `F` and `G`, a natural transformation is a function that converts `F[A]` to `G[A]` for any type `A`, without knowing or inspecting what `A` is.

**Core Operation:**
- `apply(Kind<F, A> fa)` - Transform from context F to context G

**Example:**
```java
// Natural transformation from Maybe to List
Natural<MaybeKind.Witness, ListKind.Witness> maybeToList = new Natural<>() {
    @Override
    public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
        Maybe<A> maybe = MAYBE.narrow(fa);
        List<A> list = maybe.map(List::of).orElse(List.of());
        return LIST.widen(list);
    }
};

// Use with Free monad interpretation
Free<ConsoleOpKind.Witness, String> program = ...;
Kind<IOKind.Witness, String> executable = program.foldMap(interpreter, ioMonad);
```

**The Naturality Law:** For any function `f: A -> B`:
```
nat.apply(functor.map(f, fa)) == functor.map(f, nat.apply(fa))
```

**When To Use:** Free monad/applicative interpretation, type conversions between containers, monad transformer lifting.

**Related:** [Natural Transformation Documentation](functional/natural_transformation.md)

---

### Coyoneda

**Definition:** The "free functor" that provides automatic Functor instances for any type constructor. Coyoneda stores a value and an accumulated transformation function, deferring actual mapping until lowering.

**Core Operations:**
- `lift(Kind<F, A> fa)` - Wrap a value in Coyoneda
- `map(Function<A, B> f)` - Compose functions (no Functor needed)
- `lower(Functor<F> functor)` - Apply accumulated function using provided Functor

**Example:**
```java
// Lift into Coyoneda - no Functor required for mapping!
Coyoneda<MyDSL, Integer> coyo = Coyoneda.lift(myInstruction);

// Chain maps - functions are composed, not applied
Coyoneda<MyDSL, String> mapped = coyo
    .map(x -> x * 2)
    .map(x -> x + 1)
    .map(Object::toString);

// Only when lowering is the Functor used (and functions applied once)
Kind<MyDSL, String> result = mapped.lower(myDslFunctor);
```

**Key Benefit:** Enables map fusion (multiple maps become one function composition) and eliminates the need for Functor instances on Free monad instruction sets.

**When To Use:** With Free monads to avoid implementing Functor for every DSL instruction type; optimising chains of map operations.

**Related:** [Coyoneda Documentation](monads/coyoneda.md), [Map Fusion](#map-fusion)

---

### Free Applicative

**Definition:** The applicative counterpart to Free Monad. Whilst Free Monad captures sequential, dependent computations, Free Applicative captures independent computations that can potentially run in parallel.

**Core Operations:**
- `pure(A value)` - Lift a pure value
- `lift(Kind<F, A> fa)` - Lift a single instruction
- `map2(FreeAp<F, B> fb, BiFunction<A, B, C> f)` - Combine independent computations
- `foldMap(Natural<F, G> nat, Applicative<G> app)` - Interpret using a natural transformation

**Example:**
```java
// Independent computations - can run in parallel
FreeAp<DbOp, User> userFetch = FreeAp.lift(new GetUser(userId));
FreeAp<DbOp, List<Post>> postsFetch = FreeAp.lift(new GetPosts(userId));

// Combine them - neither depends on the other's result
FreeAp<DbOp, UserProfile> profile = userFetch.map2(
    postsFetch,
    UserProfile::new
);

// Smart interpreter can execute both in parallel or batch them
Kind<CompletableFutureKind.Witness, UserProfile> result =
    profile.foldMap(parallelInterpreter, cfApplicative);
```

**When To Use:** Parallel data fetching, validation that accumulates all errors, batching independent operations, static analysis of programs before execution.

**Related:** [Free Applicative Documentation](monads/free_applicative.md), [Free Monad](monads/free_monad.md)

---

### Map Fusion

**Definition:** An optimisation where multiple consecutive `map` operations are combined into a single function composition, reducing the number of traversals over a data structure.

**Example:**
```java
// Without fusion: three separate traversals
list.map(x -> x * 2)      // Traversal 1
    .map(x -> x + 1)      // Traversal 2
    .map(Object::toString); // Traversal 3

// With Coyoneda: one traversal
Coyoneda.lift(list)
    .map(x -> x * 2)       // Just composes functions
    .map(x -> x + 1)       // Just composes functions
    .map(Object::toString) // Just composes functions
    .lower(listFunctor);   // ONE traversal with composed function
```

**How It Works:** Coyoneda stores the accumulated function `f.andThen(g).andThen(h)` instead of applying each map immediately. The composed function is applied once during `lower()`.

**When To Use:** Chains of transformations on expensive-to-traverse structures; automatically enabled when using Coyoneda.

**Related:** [Coyoneda](#coyoneda), [Coyoneda Documentation](monads/coyoneda.md)

---

## Data Types and Structures

### Choice

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

**Related:** [Selective Documentation](functional/selective.md)

---

### Unit

**Definition:** A type with exactly one value (`Unit.INSTANCE`), representing the completion of an operation that doesn't produce a meaningful result. The functional equivalent of `void`, but usable as a type parameter.

**Example:**
```java
// IO action that performs a side effect
Kind<IOKind.Witness, Unit> printAction =
    IO_KIND.widen(IO.fromRunnable(() -> System.out.println("Hello")));

// Optional as MonadError<..., Unit>
MonadError<OptionalKind.Witness, Unit> optionalMonad = OptionalMonad.INSTANCE;
Kind<OptionalKind.Witness, String> empty =
    optionalMonad.raiseError(Unit.INSTANCE);  // Creates Optional.empty()
```

**When To Use:**
- Effects that don't return a value (logging, printing, etc.)
- Error types for contexts where absence is the only error (Optional, Maybe)

**Related:** [Core Concepts](hkts/core-concepts.md)

---

### Const

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

**Related:** [Phantom Type](#phantom-type), [Bifunctor](#bifunctor), [Const Type Documentation](monads/const_type.md)

---

## Optics Terminology

### At

**Definition:** A type class for structures that support indexed access with insertion and deletion semantics. Provides a `Lens<S, Optional<A>>` where setting to `Optional.empty()` deletes the entry and setting to `Optional.of(value)` inserts or updates it.

**Core Operations:**
- `at(I index)` - Returns `Lens<S, Optional<A>>` for the index
- `get(I index, S source)` - Read value at index (returns Optional)
- `insertOrUpdate(I index, A value, S source)` - Insert or update entry
- `remove(I index, S source)` - Delete entry at index
- `modify(I index, Function<A,A> f, S source)` - Update value if present

**Example:**
```java
At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();

Map<String, Integer> scores = new HashMap<>(Map.of("alice", 100));

// Insert new entry
Map<String, Integer> withBob = mapAt.insertOrUpdate("bob", 85, scores);
// Result: {alice=100, bob=85}

// Remove entry
Map<String, Integer> noAlice = mapAt.remove("alice", withBob);
// Result: {bob=85}

// Compose with Lens for deep access
Lens<UserProfile, Optional<String>> themeLens =
    settingsLens.andThen(mapAt.at("theme"));
```

**When To Use:** CRUD operations on maps or lists where you need to insert new entries or delete existing ones whilst maintaining immutability and optics composability.

**Related:** [Indexed Access: At and Ixed Type Classes](optics/indexed_access.md)

---

### Iso (Isomorphism)

**Definition:** An optic representing a lossless, bidirectional conversion between two types. If you can convert `A` to `B` and back to `A` without losing information, you have an isomorphism.

**Core Operations:**
- `get(S source)` - Convert from S to A
- `reverseGet(A value)` - Convert from A to S

**Example:**
```java
// String and List<Character> are isomorphic
Iso<String, List<Character>> stringToChars = Iso.iso(
    s -> s.chars().mapToObj(c -> (char) c).collect(Collectors.toList()),
    chars -> chars.stream().map(String::valueOf).collect(Collectors.joining())
);

List<Character> chars = stringToChars.get("Hello");  // ['H', 'e', 'l', 'l', 'o']
String back = stringToChars.reverseGet(chars);       // "Hello"
```

**When To Use:** Converting between equivalent representations (e.g., Celsius/Fahrenheit, String/ByteArray, domain models and DTOs with no information loss).

**Related:** [Iso Documentation](optics/iso.md)

---

### Lens

**Definition:** An optic for working with product types (records with fields). Provides a composable way to get and set fields in immutable data structures.

**Core Operations:**
- `get(S source)` - Extract a field value
- `set(A newValue, S source)` - Create a new copy with updated field
- `modify(Function<A,A> f, S source)` - Update field using a function

**Example:**
```java
@GenerateLenses
public record Address(String street, String city) {}

@GenerateLenses
public record Company(String name, Address address) {}

@GenerateLenses
public record Employee(String name, Company company) {}

// Compose lenses for deep updates
Lens<Employee, String> employeeToStreet =
    EmployeeLenses.company()
        .andThen(CompanyLenses.address())
        .andThen(AddressLenses.street());

// Update nested field in one line
Employee updated = employeeToStreet.set("456 New St", originalEmployee);
```

**Related:** [Lenses Documentation](optics/lenses.md)

---

### Prism

**Definition:** An optic for working with sum types (sealed interfaces, Optional, Either). Provides safe access to specific variants within a discriminated union.

**Core Operations:**
- `preview(S source)` - Try to extract a variant (returns Optional)
- `review(A value)` - Construct the sum type from a variant
- `modify(Function<A,A> f, S source)` - Update if variant matches

**Example:**
```java
@GeneratePrisms
public sealed interface PaymentMethod {
    record CreditCard(String number) implements PaymentMethod {}
    record BankTransfer(String iban) implements PaymentMethod {}
}

Prism<PaymentMethod, String> creditCardPrism =
    PaymentMethodPrisms.creditCard().andThen(CreditCardLenses.number());

// Safe extraction
Optional<String> cardNumber = creditCardPrism.preview(payment);

// Conditional update
PaymentMethod masked = creditCardPrism.modify(num -> "****" + num.substring(12), payment);
```

**Related:** [Prisms Documentation](optics/prisms.md)

---

### Traversal

**Definition:** An optic for working with multiple values within a structure (lists, sets, trees). Allows bulk operations on all elements.

**Core Operations:**
- `modifyF(Applicative<F> app, Function<A, Kind<F,A>> f, S source)` - Effectful modification of all elements
- `toList(S source)` - Extract all focused values as a list

**Example:**
```java
@GenerateLenses
public record Order(String id, List<LineItem> items) {}

Traversal<Order, LineItem> orderItems =
    OrderLenses.items().asTraversal();

// Apply bulk update
Order discounted = orderItems.modify(
    item -> item.withPrice(item.price() * 0.9),
    order
);
```

**Related:** [Traversals Documentation](optics/traversals.md)

---

