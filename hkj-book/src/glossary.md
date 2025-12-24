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

### Monad Transformer

**Definition:** A type constructor that adds capabilities to an existing monad, allowing multiple effects to be combined in a single computation. Transformers "stack" effects, enabling you to work with combinations like "async computation that may fail" or "IO that produces optional values".

**Common Transformers in Higher-Kinded-J:**
| Transformer | Adds | Example Stack |
|-------------|------|---------------|
| `EitherT<F, E, A>` | Error handling | `EitherT<IO, Error, A>` - IO that may fail |
| `MaybeT<F, A>` | Optionality | `MaybeT<CompletableFuture, A>` - async optional |
| `StateT<F, S, A>` | State threading | `StateT<Either, State, A>` - stateful with errors |

**Example:**
```java
// Problem: combining async + error handling manually is verbose
CompletableFuture<Either<Error, User>> fetchUser(String id);
CompletableFuture<Either<Error, Profile>> fetchProfile(User user);

// Without transformer: nested flatMaps
CompletableFuture<Either<Error, Profile>> result =
    fetchUser(id).thenCompose(eitherUser ->
        eitherUser.fold(
            error -> CompletableFuture.completedFuture(Either.left(error)),
            user -> fetchProfile(user)
        )
    );

// With EitherT: flat composition
EitherT<CompletableFutureKind.Witness, Error, Profile> result =
    EitherT.fromKind(CF.widen(fetchUser(id)), cfMonad)
        .flatMap(user -> EitherT.fromKind(CF.widen(fetchProfile(user)), cfMonad));

// Run to get the nested type back
CompletableFuture<Either<Error, Profile>> unwrapped =
    CF.narrow(result.value());
```

**How It Works:**
1. Wrap your nested type in the transformer: `EitherT.fromKind(...)`
2. Use standard monad operations (`map`, `flatMap`) on the transformer
3. The transformer handles the effect interleaving automatically
4. Unwrap when done using `.value()` or `.run()`

**Lift Operations:**
```java
// Lift the outer monad into the transformer
EitherT<IOKind.Witness, Error, String> lifted =
    EitherT.liftF(IO.delay(() -> "hello"), ioMonad);

// Lift an Either into the transformer
EitherT<IOKind.Witness, Error, Integer> fromEither =
    EitherT.fromEither(Either.right(42), ioMonad);
```

**When To Use:**
- Combining async operations with error handling
- Threading state through effectful computations
- When you need multiple effects but want flat composition
- Building effect stacks for complex workflows

**Trade-offs:**
- Adds complexity and potential performance overhead
- Stack order matters: `EitherT<IO, E, A>` ≠ `IOT<Either<E, _>, A>`
- Consider [Effect Paths](#effect-path) for simpler use cases

**Related:** [Monad](#monad), [MonadError](#monaderror), [EitherT Documentation](monads/eithert_monad.md)

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

## Core Effect Types

### Either

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

**Effect Path Equivalent:** Use [EitherPath](#effect-path) for fluent composition.

**Related:** [Either Documentation](monads/either_monad.md), [EitherPath](effect/path_either.md)

---

### Maybe

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

**Effect Path Equivalent:** Use [MaybePath](#effect-path) for fluent composition.

**Related:** [Maybe Documentation](monads/maybe_monad.md), [MaybePath](effect/path_maybe.md)

---

### Try

**Definition:** A type that captures the result of a computation that may throw an exception. Converts exception-based code into value-based error handling, making exceptions composable.

**Structure:** `Try<A>` is either `Success<A>` (computation succeeded) or `Failure` (exception was thrown).

**Example:**
```java
// Wrapping exception-throwing code
Try<Integer> parsed = Try.of(() -> Integer.parseInt("42"));     // Success(42)
Try<Integer> failed = Try.of(() -> Integer.parseInt("abc"));    // Failure(NumberFormatException)

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

**Effect Path Equivalent:** Use [TryPath](#effect-path) for fluent composition.

**Related:** [Try Documentation](monads/try_monad.md), [TryPath](effect/path_try.md)

---

### IO

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

**Effect Path Equivalent:** Use [IOPath](#effect-path) for fluent composition.

**Related:** [IO Documentation](monads/io_monad.md), [IOPath](effect/path_io.md)

---

### Validated

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

**When To Use:**
- Form validation where all errors should be shown
- Batch processing where you want all failures reported
- Configuration validation
- Any scenario where fail-fast behaviour loses important information

**Effect Path Equivalent:** Use [ValidationPath](#effect-path) for fluent composition.

**Related:** [Validated Documentation](monads/validated_monad.md), [ValidationPath](effect/path_validation.md)

---

## Effect Path API

### Effect

**Definition:** A computational context that represents a value alongside some additional behaviour or outcome. Effects model computations that may fail, produce optional results, perform side effects, or require asynchronous execution. Rather than throwing exceptions or returning null, effects make these behaviours explicit in the type system.

**Common Effect Types in Higher-Kinded-J:**
- **Maybe** - Computation that may produce no result
- **Either** - Computation that may fail with typed error information
- **Try** - Computation that may throw an exception
- **IO** - Computation that performs side effects
- **Validated** - Computation that accumulates multiple errors

**Example:**
```java
// Without effects: hidden failure modes
User getUser(String id) {
    // Might return null? Throw exception? Which exceptions?
}

// With effects: explicit about what can happen
Either<UserError, User> getUser(String id) {
    // Returns Right(user) on success, Left(error) on failure
}

Maybe<User> findUser(String id) {
    // Returns Just(user) if found, Nothing if not found
}

IO<User> loadUser(String id) {
    // Describes a side effect that will load the user when run
}
```

**Why Effects Matter:**
- Make failure modes visible in type signatures
- Enable composition of operations that may fail
- Replace scattered try-catch blocks with unified error handling
- Allow reasoning about code behaviour from types alone

**Related:** [Effect Path](#effect-path), [Railway-Oriented Programming](#railway-oriented-programming)

---

### Railway-Oriented Programming

**Definition:** A programming model where computations flow along two parallel tracks: a success track and a failure track. Operations automatically route values to the appropriate track, and failures propagate without manual checking at each step.

**The Railway Metaphor:**
```
SUCCESS TRACK  ═══════╦═══════╦═══════╦═══════► Result
                      ║       ║       ║
                   step 1  step 2  step 3
                      ║       ║       ║
FAILURE TRACK  ───────╨───────╨───────╨───────► Error
```

When a step succeeds, the value continues on the success track. When a step fails, execution switches to the failure track and subsequent steps are bypassed.

**Example:**
```java
// Traditional approach: manual error checking at each step
User user = findUser(id);
if (user == null) return error("User not found");
Account account = getAccount(user);
if (account == null) return error("Account not found");
if (!account.isActive()) return error("Account inactive");
return success(account.getBalance());

// Railway-oriented: automatic track switching
EitherPath<Error, BigDecimal> balance =
    Path.of(findUser(id))
        .via(user -> getAccount(user))
        .via(account -> validateActive(account))
        .map(account -> account.getBalance());
// Failures propagate automatically; no manual checks needed
```

**Key Operations:**
- `map` - Transform value on success track (stays on same track)
- `via` (flatMap) - Chain to next operation that may switch tracks
- `recover` - Switch from failure track back to success track

**Benefits:**
- Eliminates nested if-else and try-catch pyramids
- Business logic reads top-to-bottom
- Error handling is consistent and composable
- Type system ensures all failure cases are addressed

**Related:** [Effect Path](#effect-path), [via](#via), [recover](#recover)

---

### Effect Path

**Definition:** The primary API in Higher-Kinded-J for working with effects. Effect Paths wrap core effect types (Either, Maybe, Try, IO, Validated) and provide a fluent, railway-oriented interface for composition. Each Path type offers consistent operations (`map`, `via`, `recover`, `run`) regardless of the underlying effect.

**Available Path Types:**
| Path Type | Underlying Effect | Purpose |
|-----------|-------------------|---------|
| `EitherPath<E, A>` | `Either<E, A>` | Typed error handling |
| `MaybePath<A>` | `Maybe<A>` | Optional values |
| `TryPath<A>` | `Try<A>` | Exception handling |
| `IOPath<A>` | `IO<A>` | Deferred side effects |
| `ValidationPath<E, A>` | `Validated<E, A>` | Error accumulation |
| `TrampolinePath<A>` | `Trampoline<A>` | Stack-safe recursion |
| `CompletableFuturePath<A>` | `CompletableFuture<A>` | Async operations |

**Example:**
```java
// Create paths using the Path factory
EitherPath<Error, User> userPath = Path.either(findUser(id));
MaybePath<Config> configPath = Path.maybe(loadConfig());
TryPath<Data> dataPath = Path.tryOf(() -> parseJson(input));
IOPath<String> ioPath = Path.io(() -> readFile(path));

// All paths share the same fluent API
EitherPath<Error, String> result = userPath
    .map(User::name)                    // Transform success value
    .via(name -> validateName(name))    // Chain dependent operation
    .recover(err -> "Anonymous");       // Handle errors

// Execute and get result
String name = result.run().orElse("Unknown");
```

**Related:** [Path](#path), [via](#via), [recover](#recover), [Effect Path Documentation](effect/ch_intro.md)

---

### Path

**Definition:** The unified factory class for creating Effect Paths. Provides static methods to wrap values and computations in the appropriate Path type, serving as the main entry point to the Effect Path API.

**Factory Methods:**
```java
// Maybe paths
Path.maybe(nullableValue)           // Wrap nullable, null becomes Nothing
Path.just(nonNullValue)             // Wrap known-present value
Path.nothing()                      // Empty MaybePath

// Either paths
Path.either(eitherValue)            // Wrap existing Either
Path.right(value)                   // Success EitherPath
Path.left(error)                    // Failure EitherPath
Path.of(nullableValue)              // Wrap nullable as EitherPath

// Try paths
Path.tryOf(() -> riskyOperation())  // Wrap exception-throwing code
Path.success(value)                 // Successful TryPath
Path.failure(exception)             // Failed TryPath

// IO paths
Path.io(() -> sideEffect())         // Wrap side-effecting code
Path.ioOf(value)                    // Pure value in IO context

// Validation paths
Path.valid(value)                   // Valid result
Path.invalid(error)                 // Invalid with error
```

**Example:**
```java
// Building a complete workflow using Path factory
public EitherPath<OrderError, Receipt> processOrder(OrderRequest request) {
    return Path.maybe(customerRepository.find(request.customerId()))
        .toEitherPath(() -> new OrderError.CustomerNotFound())
        .via(customer -> Path.either(validateOrder(request, customer)))
        .via(validated -> Path.tryOf(() -> paymentService.charge(validated))
            .toEitherPath(OrderError.PaymentFailed::new))
        .map(payment -> createReceipt(request, payment));
}
```

**Related:** [Effect Path](#effect-path), [via](#via), [recover](#recover), [Effect Path Documentation](effect/ch_intro.md)

---

### via

**Definition:** The chaining operation on Effect Paths, equivalent to `flatMap` on monads. Applies a function that returns a new Path, allowing dependent operations to be sequenced. If the current Path is on the failure track, `via` is bypassed and the failure propagates.

**Signature:** `Path<E, A>.via(Function<A, Path<E, B>> f) → Path<E, B>`

**Example:**
```java
// Each step depends on the previous result
EitherPath<Error, Order> orderPath =
    Path.of(userId)
        .via(id -> findUser(id))           // Returns EitherPath<Error, User>
        .via(user -> getCart(user))        // Returns EitherPath<Error, Cart>
        .via(cart -> validateCart(cart))   // Returns EitherPath<Error, ValidatedCart>
        .via(valid -> createOrder(valid)); // Returns EitherPath<Error, Order>

// Compare to map (which doesn't chain Paths):
EitherPath<Error, String> mapped = userPath.map(user -> user.name());
// map: A -> B (simple transformation)
// via: A -> Path<E, B> (operation that may fail)
```

**When To Use:**
- Chaining operations where each step may fail
- Sequencing dependent computations
- Building pipelines of effectful operations

**Contrast with map:**
- `map(A -> B)` - Transform the value, stay on same track
- `via(A -> Path<E, B>)` - Chain to operation that may switch tracks

**Related:** [Effect Path](#effect-path), [Railway-Oriented Programming](#railway-oriented-programming), [recover](#recover)

---

### recover

**Definition:** The error recovery operation on Effect Paths, equivalent to `handleErrorWith` on MonadError. Allows switching from the failure track back to the success track by providing an alternative value or computation.

**Signature:** `Path<E, A>.recover(Function<E, A> handler) → Path<E, A>`

**Example:**
```java
// Simple recovery with default value
EitherPath<Error, Config> config = loadConfig()
    .recover(error -> Config.defaults());

// Recovery that inspects the error
EitherPath<ApiError, User> user = fetchUser(id)
    .recover(error -> switch (error) {
        case NotFound _ -> User.guest();
        case RateLimited _ -> User.cached(id);
        default -> throw new RuntimeException(error);  // Re-throw unrecoverable
    });

// Recovery with a new Path (recoverWith)
EitherPath<Error, Data> data = primarySource()
    .recoverWith(error -> fallbackSource());  // Try alternative on failure

// Partial recovery - only handle specific errors
EitherPath<Error, Value> result = operation()
    .recover(error -> {
        if (error instanceof Retryable) {
            return retryOperation();
        }
        throw error;  // Propagate non-retryable errors
    });
```

**When To Use:**
- Providing default values when operations fail
- Implementing fallback strategies
- Converting errors to success values
- Selective error recovery based on error type

**Related:** [Effect Path](#effect-path), [Railway-Oriented Programming](#railway-oriented-programming), [via](#via)

---

### Effect-Optics Bridge

**Definition:** The integration layer that connects Effect Paths with the Focus DSL, allowing seamless composition of effectful computations and immutable data navigation. The bridge enables focusing into data structures retrieved from effects and lifting optic operations into effectful contexts.

**How It Works:**
```
EFFECTS DOMAIN                    OPTICS DOMAIN
══════════════                    ═════════════
EitherPath<E, User>  ──┐    ┌──  FocusPath<User, Address>
TryPath<Config>      ──┤    ├──  Lens<Config, Settings>
IOPath<Data>         ──┘    └──  Traversal<Data, Item>
                        │  │
                        ▼  ▼
                   .focus(optic)
                        │
                        ▼
              UNIFIED COMPOSITION
```

**Example:**
```java
// Fetch user (effect) then navigate to nested data (optics)
EitherPath<Error, String> city = userService.findById(userId)  // Effect: fetch
    .focus(UserFocus.address())                                 // Optics: navigate
    .focus(AddressFocus.city())                                 // Optics: deeper
    .map(String::toUpperCase);                                  // Transform

// Modify nested data within an effectful context
EitherPath<Error, User> updated = userService.findById(userId)
    .focusAndModify(
        UserFocus.address().andThen(AddressFocus.postcode()),
        postcode -> postcode.toUpperCase()
    );

// Combine multiple effect sources with optic navigation
EitherPath<Error, Report> report =
    Path.of(loadCompany(id))
        .focus(CompanyFocus.departments())     // Traverse to departments
        .via(dept -> loadMetrics(dept.id()))   // Effect for each
        .map(metrics -> generateReport(metrics));
```

**Benefits:**
- No boilerplate for null checks during navigation
- Optic failures (missing optional values) integrate with effect failures
- Single vocabulary (`map`, `via`, `focus`) for both domains
- Type-safe composition across effect and structure boundaries

**Related:** [Effect Path](#effect-path), [Focus DSL](#focus-dsl), [FocusPath](#focuspath)

---

## Optics Terminology

### Affine

**Definition:** An optic that focuses on zero or one values within a structure. Affine sits between Lens (exactly one) and Prism (zero or one for sum types) in the optic hierarchy. It combines the "might not be there" aspect of Prism with the "focus on part of a product" aspect of Lens.

**Core Operations:**
- `preview(S source)` - Try to extract the value (returns Optional)
- `set(A value, S source)` - Set the value if the focus exists

**Example:**
```java
// Affine for the first element of a list (might be empty)
Affine<List<String>, String> firstElement = Affine.affine(
    list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)),
    (newFirst, list) -> list.isEmpty() ? list :
        Stream.concat(Stream.of(newFirst), list.stream().skip(1)).toList()
);

List<String> items = List.of("a", "b", "c");
Optional<String> first = firstElement.preview(items);  // Optional["a"]
List<String> updated = firstElement.set("X", items);   // ["X", "b", "c"]

List<String> empty = List.of();
Optional<String> noFirst = firstElement.preview(empty);  // Optional.empty()
List<String> stillEmpty = firstElement.set("X", empty);  // [] (unchanged)
```

**When To Use:**
- Accessing elements that may not exist (first element, element at index)
- Optional fields in product types
- Composing Lens with Prism (result is Affine)

**Hierarchy Position:** `Iso → Lens → Affine → Traversal`
                        `Iso → Prism → Affine → Traversal`

**Related:** [Lens](#lens), [Prism](#prism), [Affine Documentation](optics/affine.md)

---

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

### Fold

**Definition:** A read-only optic that extracts zero or more values from a structure. Folds are like Traversals but without the ability to modify. They generalise the concept of "folding" or "reducing" over a structure.

**Core Operations:**
- `foldMap(Monoid<M> monoid, Function<A, M> f, S source)` - Map and combine all values
- `toList(S source)` - Extract all focused values as a list
- `headOption(S source)` - Get the first value if any
- `exists(Predicate<A> p, S source)` - Check if any value satisfies predicate
- `all(Predicate<A> p, S source)` - Check if all values satisfy predicate

**Example:**
```java
// Fold over all players in a league
Fold<League, Player> allPlayers = LeagueFolds.teams()
    .andThen(TeamFolds.players());

// Extract all players
List<Player> players = allPlayers.toList(league);

// Sum all scores using a Monoid
Integer totalScore = allPlayers.foldMap(
    Monoids.integerAddition(),
    Player::score,
    league
);

// Check conditions across all values
boolean anyInactive = allPlayers.exists(p -> !p.isActive(), league);
boolean allQualified = allPlayers.all(p -> p.score() >= 100, league);
```

**When To Use:**
- Extracting multiple values without modification
- Aggregating data from nested structures
- Querying collections within complex types
- When you need read-only access to multiple elements

**Related:** [Traversal](#traversal), [Getter (Fold of one)](#lens)

---

### Setter

**Definition:** A write-only optic that can modify zero or more values within a structure. Setters are the dual of Folds: where Folds can only read, Setters can only write. They cannot extract values, only transform them.

**Core Operations:**
- `modify(Function<A, A> f, S source)` - Apply function to all focused values
- `set(A value, S source)` - Set all focused values to same value

**Example:**
```java
// Setter for all prices in an order
Setter<Order, BigDecimal> allPrices = OrderSetters.items()
    .andThen(LineItemSetters.price());

// Apply discount to all prices
Order discounted = allPrices.modify(
    price -> price.multiply(new BigDecimal("0.9")),
    order
);

// Set all prices to zero (for testing)
Order zeroed = allPrices.set(BigDecimal.ZERO, order);

// Compose with other optics
Setter<Company, String> allEmployeeEmails =
    CompanySetters.departments()
        .andThen(DepartmentSetters.employees())
        .andThen(EmployeeSetters.email());

Company normalised = allEmployeeEmails.modify(String::toLowerCase, company);
```

**When To Use:**
- Bulk modifications without needing to read values
- Applying transformations across nested structures
- When modification logic doesn't depend on current values
- Composing write-only operations

**Related:** [Traversal](#traversal), [Fold](#fold)

---

### Focus DSL

**Definition:** A domain-specific language for fluent, type-safe navigation and manipulation of immutable data structures. The Focus DSL provides a composable way to build paths through nested records without manual lens composition.

**Core Concept:** Instead of composing optics manually, the Focus DSL lets you chain `.focus()` calls to navigate through data structures, with the optic types inferred automatically.

**Example:**
```java
// Without Focus DSL: manual lens composition
Lens<Employee, String> streetLens =
    EmployeeLenses.company()
        .andThen(CompanyLenses.address())
        .andThen(AddressLenses.street());
String street = streetLens.get(employee);

// With Focus DSL: fluent navigation
String street = Focus.on(employee)
    .focus(EmployeeFocus.company())
    .focus(CompanyFocus.address())
    .focus(AddressFocus.street())
    .get();

// Modification is equally fluent
Employee updated = Focus.on(employee)
    .focus(EmployeeFocus.company())
    .focus(CompanyFocus.address())
    .focus(AddressFocus.city())
    .modify(String::toUpperCase);

// Mix with Effect Paths for effectful navigation
EitherPath<Error, String> city = userService.findById(id)
    .focus(UserFocus.address())
    .focus(AddressFocus.city());
```

**Key Features:**
- Type-safe: Compiler catches invalid paths
- Composable: Chain any optic types together
- Generated: `@GenerateLenses` creates Focus helpers automatically
- Effect integration: Seamlessly works with Effect Paths

**Related:** [FocusPath](#focuspath), [Lens](#lens), [Effect-Optics Bridge](#effect-optics-bridge), [Focus DSL Documentation](optics/focus_dsl.md)

---

### FocusPath

**Definition:** A generated helper class that provides pre-composed optic paths for navigating into record types. The annotation processor creates FocusPath classes for each annotated record, offering a fluent API for accessing fields and nested structures.

**Generation:** Add `@GenerateLenses` to your record to generate the corresponding Focus class.

**Example:**
```java
@GenerateLenses
public record User(String name, Address address, List<Order> orders) {}

@GenerateLenses
public record Address(String street, String city, String postcode) {}

// Generated: UserFocus class with methods:
// - UserFocus.name()     → Lens<User, String>
// - UserFocus.address()  → Lens<User, Address>
// - UserFocus.orders()   → Lens<User, List<Order>>

// Use in Focus DSL
String city = Focus.on(user)
    .focus(UserFocus.address())
    .focus(AddressFocus.city())
    .get();

// Compose for reusable paths
Lens<User, String> userCity = UserFocus.address()
    .andThen(AddressFocus.city());

// Use with Effect Paths
EitherPath<Error, String> cityPath = loadUser(id)
    .focus(UserFocus.address())
    .focus(AddressFocus.city());
```

**Naming Convention:**
- Record `Foo` generates `FooFocus` class
- Each field `bar` generates static method `FooFocus.bar()`

**Related:** [Focus DSL](#focus-dsl), [Lens](#lens), [Code Generation](optics/code_generation.md)

---

