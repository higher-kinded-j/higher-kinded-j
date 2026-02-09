# Type Arity: Classifying Type Constructors

> _"To define a thing is to say what kind of thing it is."_
> -- Aristotle, Categories

~~~admonish info title="What You'll Learn"
- What type arity means and why it matters
- The difference between Witness and WitnessArity
- How `TypeArity` and `WitnessArity` enforce compile-time safety
- The difference between unary and binary type constructors
- How the arity system prevents common type errors
~~~

Higher-Kinded-J uses an *arity system* to classify type constructors by the number of type parameters they accept. This classification is enforced at compile time, preventing incorrect usage of witness types with incompatible type classes.

---

## Witness vs WitnessArity: Understanding the Distinction

Before diving into arity, it's important to understand the relationship between these two concepts:

```
┌─────────────────────────────────────────────────────────────────┐
│  WitnessArity<TypeArity.Unary>        (interface)               │
│  "I am a unary type constructor"                                │
│                                                                 │
│         ▲                                                       │
│         │ implements                                            │
│         │                                                       │
│  ┌──────┴──────┐  ┌──────────────┐  ┌──────────────┐           │
│  │ ListKind    │  │ OptionalKind │  │ IOKind       │           │
│  │ .Witness    │  │ .Witness     │  │ .Witness     │           │
│  └─────────────┘  └──────────────┘  └──────────────┘           │
│  "I am List"      "I am Optional"   "I am IO"                  │
│  (concrete class) (concrete class)  (concrete class)           │
└─────────────────────────────────────────────────────────────────┘
```

**Witness** is a concrete type (usually a `final class`) that acts as a *compile-time tag* to identify a specific type constructor. Each type constructor has its own unique Witness:

```java
ListKind.Witness      // Identifies the List type constructor
OptionalKind.Witness  // Identifies the Optional type constructor
IOKind.Witness        // Identifies the IO type constructor
```

**WitnessArity** is an *interface* that Witness types implement to declare how many type parameters their type constructor accepts:

```java
// WitnessArity is the interface - it classifies witnesses by arity
public interface WitnessArity<A extends TypeArity> {}

// Each Witness implements it to declare its arity
final class Witness implements WitnessArity<TypeArity.Unary> {}   // "I take one type parameter"
final class Witness implements WitnessArity<TypeArity.Binary> {}  // "I take two type parameters"
```

**In short:**
- **Witness** answers: *"Which type constructor?"* (identity)
- **WitnessArity** answers: *"What kind of type constructor?"* (classification)

This separation allows `Kind<F extends WitnessArity<?>, A>` to require only valid witnesses, while type classes can further constrain to specific arities.

---

## What is Type Arity?

In type theory, the *arity* of a type constructor refers to how many type parameters it accepts before producing a concrete type:

```
                     TypeArity
                         │
          ┌──────────────┴──────────────┐
          ▼                             ▼
    ┌───────────┐                 ┌───────────┐
    │   Unary   │                 │  Binary   │
    │   * → *   │                 │ * → * → * │
    └───────────┘                 └───────────┘
          │                             │
          ▼                             ▼
    List<_>                       Either<_,_>
    Maybe<_>                      Tuple<_,_>
    IO<_>                         Function<_,_>
    Optional<_>                   Validated<_,_>
```

- **Unary (`* → *`)**: Takes one type parameter. `List<_>` becomes `List<String>` when applied to `String`.
- **Binary (`* → * → *`)**: Takes two type parameters. `Either<_,_>` becomes `Either<String, Integer>` when applied to `String` and `Integer`.

---

## The Arity Interfaces

Higher-Kinded-J provides two interfaces to encode arity information:

### TypeArity

A sealed interface that defines the possible arities:

```java
public sealed interface TypeArity {
    final class Unary implements TypeArity {}
    final class Binary implements TypeArity {}
}
```

### WitnessArity

A marker interface that witness types implement to declare their arity:

```java
public interface WitnessArity<A extends TypeArity> {}
```

Every witness type must implement `WitnessArity` with the appropriate arity:

```java
// Unary witness (one type parameter)
public interface ListKind<A> extends Kind<ListKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}

// Binary witness (two type parameters)
public interface EitherKind2<L, R> extends Kind2<EitherKind2.Witness, L, R> {
    final class Witness implements WitnessArity<TypeArity.Binary> {
        private Witness() {}
    }
}
```

---

## How Arity Enforces Type Safety

The `Kind` interface requires its witness type to implement `WitnessArity`:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Kind<F, A>                               │
│              where F extends WitnessArity<?>                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                   ┌──────────┴──────────┐
                   ▼                     ▼
         ┌─────────────────┐   ┌─────────────────┐
         │  WitnessArity   │   │  WitnessArity   │
         │ <TypeArity      │   │ <TypeArity      │
         │     .Unary>     │   │     .Binary>    │
         └─────────────────┘   └─────────────────┘
                   │                     │
         ┌─────────┴─────────┐   ┌───────┴───────┐
         ▼                   ▼   ▼               ▼
    ListKind.Witness   MaybeKind  EitherKind2   FunctionKind
                        .Witness   .Witness      .Witness
```

Type classes then specify which arity they require:

```java
// Unary type classes
public interface Functor<F extends WitnessArity<TypeArity.Unary>> { ... }
public interface Applicative<F extends WitnessArity<TypeArity.Unary>> { ... }
public interface Monad<F extends WitnessArity<TypeArity.Unary>> { ... }

// Binary type classes
public interface Bifunctor<F extends WitnessArity<TypeArity.Binary>> { ... }
public interface Profunctor<F extends WitnessArity<TypeArity.Binary>> { ... }
```

This creates a compile-time guarantee:

```java
// ✓ Compiles: ListKind.Witness is Unary
Functor<ListKind.Witness> listFunctor = ListFunctor.INSTANCE;

// ✗ Does not compile: EitherKind2.Witness is Binary, not Unary
Functor<EitherKind2.Witness> invalid;  // Compilation error!
```

---

## Unary vs Binary: When to Use Each

### Unary Type Constructors

Use unary witnesses when you want to work with a type as a single-parameter container:

| Type | Witness | Use Case |
|------|---------|----------|
| `List<A>` | `ListKind.Witness` | Collections with Functor/Monad |
| `Optional<A>` | `OptionalKind.Witness` | Optional values |
| `IO<A>` | `IOKind.Witness` | Deferred effects |
| `Either<L, A>` | `EitherKind.Witness<L>` | Error handling (right-biased) |

Note that `Either` has *two* witness types:
- `EitherKind.Witness<L>` - Unary, with `L` fixed. Used with `Functor`, `Monad`, `MonadError`.
- `EitherKind2.Witness` - Binary. Used with `Bifunctor`.

### Binary Type Constructors

Use binary witnesses when you need to transform both type parameters:

| Type | Witness | Use Case |
|------|---------|----------|
| `Either<L, R>` | `EitherKind2.Witness` | Bifunctor (transform both sides) |
| `Function<A, B>` | `FunctionKind.Witness` | Profunctor |
| `Tuple<A, B>` | `TupleKind2.Witness` | Pairs with Bifunctor |

---

## Diagram: Type Class Hierarchy by Arity

```
┌────────────────────────────────────────────────────┐
│           Functor<F extends WitnessArity           │
│                    <TypeArity.Unary>>              │
│                         │                          │
│    ┌────────────────────┼────────────────────┐    │
│    ▼                    ▼                    ▼    │
│ Applicative          Monad              Traverse   │
│                                                    │
│    └────────────────────┬────────────────────┘    │
│                         ▼                          │
│                    MonadError                      │
└────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│         Bifunctor<F extends WitnessArity           │
│                    <TypeArity.Binary>>             │
│                                                    │
│         Profunctor<F extends WitnessArity          │
│                    <TypeArity.Binary>>             │
└────────────────────────────────────────────────────┘
```

---

## Common Patterns

### Defining a New Unary Type

```java
// 1. Define the Kind interface with witness
public interface MyTypeKind<A> extends Kind<MyTypeKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}

// 2. Your type implements the Kind interface
public record MyType<A>(A value) implements MyTypeKind<A> {}

// 3. Create type class instances
public class MyTypeFunctor implements Functor<MyTypeKind.Witness> {
    @Override
    public <A, B> Kind<MyTypeKind.Witness, B> map(
            Function<A, B> f, Kind<MyTypeKind.Witness, A> fa) {
        MyType<A> myType = (MyType<A>) fa;
        return new MyType<>(f.apply(myType.value()));
    }
}
```

### Using Either with Different Arities

```java
// As Monad (unary, right-biased)
Monad<EitherKind.Witness<String>> eitherMonad = EitherMonadError.instance();
Kind<EitherKind.Witness<String>, Integer> result =
    eitherMonad.flatMap(x -> eitherMonad.of(x + 1), eitherMonad.of(42));

// As Bifunctor (binary, transform both sides)
Bifunctor<EitherKind2.Witness> bifunctor = EitherBifunctor.INSTANCE;
Kind2<EitherKind2.Witness, Integer, String> mapped =
    bifunctor.bimap(String::length, Object::toString, EITHER2.widen(either));
```

---

## Why Arity Matters

Without the arity system, you could accidentally:

1. **Use a binary witness with a unary type class**: Attempting to use `Either<L,R>` as a simple Functor without fixing `L` would be a type error.

2. **Mix incompatible witnesses**: Different type classes have different requirements, and the arity system makes these explicit.

3. **Create invalid witness types**: A witness that doesn't implement `WitnessArity` cannot be used with `Kind` at all.

The arity system catches these errors at compile time rather than runtime.

---

~~~admonish info title="Key Takeaways"
- **TypeArity** classifies type constructors as Unary (`* → *`) or Binary (`* → * → *`)
- **WitnessArity** is implemented by witness types to declare their arity
- **Compile-time safety**: The Java compiler prevents using witnesses with incompatible type classes
- **Either has two witnesses**: `EitherKind.Witness<L>` (unary) and `EitherKind2.Witness` (binary)
~~~

---

**Previous:** [Core Concepts](core-concepts.md)
**Next:** [Usage Guide](usage-guide.md)
