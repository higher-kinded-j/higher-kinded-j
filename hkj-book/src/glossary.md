# Glossary of Functional Programming Terms 📖

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

**Java Analogy:** Think of `? super T` in Java generics—this is contravariant. Also, function parameters are contravariant.

**Example:**
```java
// Contravariant behaviour in Java
Consumer<Object> objectConsumer = obj -> System.out.println(obj);
Consumer<String> stringConsumer = objectConsumer; // ✅ Can accept more general consumer

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

**Java Analogy:** Think of `? extends T` in Java generics—this is covariant.

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

**Java Analogy:** Most mutable collections in Java are invariant—`List<Integer>` is not a subtype of `List<Number>`.

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
// The String error type is fixed—you can't substitute it with Object or CharSequence
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

**Example:**
```java
Monad<OptionalKind.Witness> monad = OptionalMonad.INSTANCE;

Kind<OptionalKind.Witness, String> result =
    monad.flatMap(
        userId -> monad.flatMap(
            profile -> findAccount(profile.accountId()),
            findProfile(userId)
        ),
        findUser("user123")
    );
// Each step depends on the previous result
```

**Laws:**
- Left Identity: `flatMap(f, of(a)) == f(a)`
- Right Identity: `flatMap(of, m) == m`
- Associativity: `flatMap(g, flatMap(f, m)) == flatMap(x -> flatMap(g, f(x)), m)`

**When To Use:** Sequential operations where each step depends on the previous result (database queries, async workflows, error handling pipelines).

**Related:** [Monad Documentation](functional/monad.md)

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

## Optics Terminology

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

## Memory Aids

### CCIA Mnemonic
**"C-C-I-A: Covariant reads Correctly, Invariant is Always exact, Contravariant accepts input"**

### Hierarchy Mnemonic
**"Every MONAD is APplicative, every APplicative is FUnCtor"**
- Monad ⊃ Applicative ⊃ Functor

### Selective Position Mnemonic
**"Selective Sits Between: More than Applicative, Less than Monad"**
- Functor ⊂ Applicative ⊂ Selective ⊂ Monad

### Optics Mnemonic
**"LenS for FieldS, PriSm for SumS, TraverSal for CollectionS"**

### Variance Direction Mnemonic
**"COvariant = COrect direction (same), CONTRAvariant = CONTRAry direction (opposite)"**

---

**This glossary is intentionally structured for easy expansion. To add a new term:**
1. Place it in the appropriate category (or create a new category if needed)
2. Follow the format: Definition → Example → "Think Of It As" (if helpful) → Where You'll See It
3. Include code examples for concrete concepts
4. Cross-reference related documentation pages
5. Maintain alphabetical ordering within each category
