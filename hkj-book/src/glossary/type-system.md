# Glossary: Type System & Higher-Kinded Types

~~~admonish info title="What This Page Covers"
- Variance, higher-kinded type simulation, witnesses, and phantom types.
- Part of the [Glossary](../glossary.md); see it for the other categories.
~~~

## Contravariant

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

## Covariant

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

## Defunctionalisation

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

**Related:** [Core Concepts](../hkts/core-concepts.md)

---

## Higher-Kinded Type (HKT)

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

**Related:** [HKT Introduction](../hkts/hkt_introduction.md)

---

## Invariant

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

## Kind

**Definition:** The core interface in Higher-Kinded-J that simulates higher-kinded types. `Kind<F, A>` represents a type constructor `F` applied to a type `A`. The `F` parameter must implement `WitnessArity` to ensure type safety.

**Structure:**
- `Kind<F extends WitnessArity<?>, A>` - Single type parameter (e.g., `List<A>`, `Optional<A>`)
- `Kind2<F extends WitnessArity<?>, A, B>` - Two type parameters (e.g., `Either<A, B>`, `Function<A, B>`)

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

**Related:** [Core Concepts](../hkts/core-concepts.md)

---

## Phantom Type

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

**Related:** [Const Type Documentation](../monads/const_type.md), [Witness Type](#witness-type)

---

## Type Constructor

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

## TypeArity

**Definition:** A sealed interface that classifies type constructors by the number of type parameters they accept. Used with `WitnessArity` to provide compile-time safety for the Kind system.

**Structure:**
```java
public sealed interface TypeArity {
    // Unary: one type parameter (* -> *)
    final class Unary implements TypeArity {}

    // Binary: two type parameters (* -> * -> *)
    final class Binary implements TypeArity {}
}
```

**Arities:**
- `TypeArity.Unary` - For type constructors with one parameter: `List<_>`, `Optional<_>`, `IO<_>`
- `TypeArity.Binary` - For type constructors with two parameters: `Either<_,_>`, `Function<_,_>`, `Tuple<_,_>`

**Example:**
```java
// List is unary: * -> *
final class Witness implements WitnessArity<TypeArity.Unary> {}

// Either (when used with Bifunctor) is binary: * -> * -> *
final class Witness implements WitnessArity<TypeArity.Binary> {}
```

**Why It Matters:** TypeArity enables the compiler to verify that witness types are used correctly with the appropriate type classes. Unary witnesses work with `Functor`, `Monad`, etc., whilst binary witnesses work with `Bifunctor` and `Profunctor`.

**Related:** [WitnessArity](#witnessarity), [Witness Type](#witness-type), [Type Arity](../hkts/type-arity.md)

---

## Variance Summary Table

| Variance | Direction | Java Analogy | Example Type Class | Intuition |
|----------|-----------|--------------|-------------------|-----------|
| **Covariant** | Output/Producer | `? extends T` | Functor, Applicative, Monad | "Reading out" |
| **Contravariant** | Input/Consumer | `? super T` | Profunctor (first param) | "Writing in" (reversed) |
| **Invariant** | Neither/Both | No wildcards | Monad error type | "Exact match required" |

---

## Witness Type

**Definition:** A marker type used to represent a type constructor in the defunctionalisation pattern. Each type constructor has a corresponding witness type that implements `WitnessArity` to declare its arity.

**Examples:**
```java
// List type constructor → ListKind.Witness (unary)
public interface ListKind<A> extends Kind<ListKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}

// Optional type constructor → OptionalKind.Witness (unary)
public interface OptionalKind<A> extends Kind<OptionalKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}

// Either type constructor → EitherKind.Witness<L> (unary, with fixed L)
public interface EitherKind<L, R> extends Kind<EitherKind.Witness<L>, R> {
    final class Witness<L> implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}

// Either for Bifunctor → EitherKind2.Witness (binary)
public interface EitherKind2<L, R> extends Kind2<EitherKind2.Witness, L, R> {
    final class Witness implements WitnessArity<TypeArity.Binary> {
        private Witness() {}
    }
}
```

**Usage:**
```java
// The Witness type is used as the F parameter:
Functor<ListKind.Witness> listFunctor = ListFunctor.INSTANCE;
Functor<OptionalKind.Witness> optionalFunctor = OptionalFunctor.INSTANCE;
MonadError<EitherKind.Witness<String>, String> eitherMonad = EitherMonadError.instance();

// Binary witnesses for Bifunctor/Profunctor:
Bifunctor<EitherKind2.Witness> eitherBifunctor = EitherBifunctor.INSTANCE;
```

**Think Of It As:** A compile-time tag that identifies which type constructor we're working with and its arity.

**Related:** [WitnessArity](#witnessarity), [TypeArity](#typearity), [Core Concepts](../hkts/core-concepts.md)

---

## WitnessArity

**Definition:** A marker interface that witness types implement to declare their arity (number of type parameters). This creates a compile-time bound on the `Kind` interface, preventing misuse of witness types.

**Structure:**
```java
public interface WitnessArity<A extends TypeArity> {}
```

**Usage:**
```java
// Witness for a unary type constructor (List, Optional, IO)
final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
}

// Witness for a binary type constructor (Either, Function)
final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() {}
}
```

**How It Enforces Safety:**
```java
// Kind requires F to implement WitnessArity
public interface Kind<F extends WitnessArity<?>, A> {}

// Type classes specify the required arity
public interface Functor<F extends WitnessArity<TypeArity.Unary>> {}
public interface Bifunctor<F extends WitnessArity<TypeArity.Binary>> {}
```

**Example:**
```java
// This compiles: ListKind.Witness implements WitnessArity<Unary>
Functor<ListKind.Witness> listFunctor = ListFunctor.INSTANCE;

// This would NOT compile: EitherKind2.Witness implements WitnessArity<Binary>
// Functor<EitherKind2.Witness> invalid;  // Error: Binary not compatible with Unary
```

**Why It Matters:** WitnessArity provides compile-time guarantees that witness types are used with compatible type classes. You cannot accidentally use a binary witness with a unary type class.

**Related:** [TypeArity](#typearity), [Witness Type](#witness-type), [Type Arity](../hkts/type-arity.md)

