# Natural Transformation
## _Polymorphic Functions Between Type Constructors_

~~~admonish info title="What You'll Learn"
- What natural transformations are and why they matter
- How to transform between different computational contexts
- The naturality law and why it ensures correctness
- Composing natural transformations
- Using natural transformations with Free monads
- Converting between types like Maybe, List, and Either
~~~

~~~admonish title="Hands On Practice"
[Tutorial08_NaturalTransformation.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial08_NaturalTransformation.java)
~~~

~~~admonish example title="See Example Code"
- [NaturalTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/NaturalTest.java) - Comprehensive test suite
- [NaturalTransformationExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/natural/NaturalTransformationExample.java) - Practical examples
~~~

## Purpose

In functional programming, we often work with values wrapped in different contexts: `Maybe<A>`, `List<A>`, `Either<E, A>`, `IO<A>`, and so on. Sometimes we need to convert between these contexts whilst preserving the value inside.

A **natural transformation** is a polymorphic function that converts from one type constructor to another:

```
F[A] ────Natural<F, G>────> G[A]
```

The key insight is that this transformation works for **any** type `A`. It transforms the "container" without knowing or caring about what's inside.

![natural_transformation.svg](../images/puml/natural_transformation.svg)



### Everyday Examples

You've likely encountered natural transformations without realising it:

| From | To | Transformation |
|------|----|----------------|
| `Maybe<A>` | `List<A>` | Nothing becomes `[]`, Just(x) becomes `[x]` |
| `List<A>` | `Maybe<A>` | Take the head element (if any) |
| `Either<E, A>` | `Maybe<A>` | Left becomes Nothing, Right(x) becomes Just(x) |
| `Try<A>` | `Either<Throwable, A>` | Success/Failure maps to Right/Left |

## Core Interface

The `Natural<F, G>` interface in Higher-Kinded-J is straightforward:

```java
@FunctionalInterface
public interface Natural<F, G> {

    /**
     * Applies this natural transformation to convert a value in context F to context G.
     */
    <A> Kind<G, A> apply(Kind<F, A> fa);

    /**
     * Composes this transformation with another: F ~> G ~> H becomes F ~> H
     */
    default <H> Natural<F, H> andThen(Natural<G, H> after);

    /**
     * Composes another transformation before this one: E ~> F ~> G becomes E ~> G
     */
    default <E> Natural<E, G> compose(Natural<E, F> before);

    /**
     * Returns the identity transformation: F ~> F
     */
    static <F> Natural<F, F> identity();
}
```

The `@FunctionalInterface` annotation means you can implement natural transformations using lambda expressions.

## Basic Usage

### Example 1: Maybe to List

Converting a `Maybe` to a `List` is a classic natural transformation:

```java
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

Natural<MaybeKind.Witness, ListKind.Witness> maybeToList = new Natural<>() {
    @Override
    public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
        Maybe<A> maybe = MAYBE.narrow(fa);
        List<A> list = maybe.fold(
            () -> List.of(),           // Nothing -> empty list
            value -> List.of(value)    // Just(x) -> singleton list
        );
        return LIST.widen(list);
    }
};

// Usage
Kind<MaybeKind.Witness, String> maybeValue = MAYBE.widen(Maybe.just("hello"));
Kind<ListKind.Witness, String> listValue = maybeToList.apply(maybeValue);
// Result: List containing "hello"
```

### Example 2: Either to Maybe

Discarding the error information from an `Either`:

```java
Natural<EitherKind.Witness<String>, MaybeKind.Witness> eitherToMaybe = new Natural<>() {
    @Override
    public <A> Kind<MaybeKind.Witness, A> apply(Kind<EitherKind.Witness<String>, A> fa) {
        Either<String, A> either = EITHER.<String, A>narrow(fa);
        return MAYBE.widen(
            either.fold(
                left -> Maybe.nothing(),
                Maybe::just
            )
        );
    }
};
```

### Example 3: List Head

Getting the first element of a list (if any):

```java
Natural<ListKind.Witness, MaybeKind.Witness> listHead = new Natural<>() {
    @Override
    public <A> Kind<MaybeKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
        List<A> list = LIST.narrow(fa);
        return MAYBE.widen(
            list.isEmpty()
                ? Maybe.nothing()
                : Maybe.just(list.get(0))
        );
    }
};
```

## The Naturality Law

For a transformation to be truly "natural", it must satisfy the **naturality law**:

```
For any function f: A -> B and any value fa: F[A]
nat.apply(functor.map(f, fa)) == functor.map(f, nat.apply(fa))
```

In plain terms: it doesn't matter whether you map first then transform, or transform first then map. The diagram commutes:

```
         map(f)
  F[A] ─────────> F[B]
    │               │
nat │               │ nat
    ↓               ↓
  G[A] ─────────> G[B]
         map(f)
```

### Why This Matters

The naturality law ensures that your transformation is **structure-preserving**. It doesn't peek inside the values or behave differently based on what type `A` is. This makes natural transformations predictable and composable.

~~~admonish warning title="Violating the Naturality Law"
A transformation that inspects the type of `A` or behaves inconsistently would violate naturality. For example, a "transformation" that converts `List<Integer>` differently from `List<String>` is not a natural transformation.
~~~

## Composition

Natural transformations compose beautifully. Given:
- `nat1: F ~> G`
- `nat2: G ~> H`

You can create `F ~> H`:

```java
Natural<F, G> maybeToEither = ...;
Natural<G, H> eitherToIO = ...;

// Compose to get Maybe ~> IO
Natural<F, H> maybeToIO = maybeToEither.andThen(eitherToIO);
```

Composition is **associative**:

```java
// These are equivalent:
(f.andThen(g)).andThen(h)
f.andThen(g.andThen(h))
```

## Use with Free Monads

The most common use case for natural transformations is **interpreting Free monads**. A Free monad program is built from an instruction set `F`, and a natural transformation `F ~> M` interprets those instructions into a target monad `M`.

```java
// Define DSL instructions
sealed interface ConsoleOp<A> {
    record PrintLine(String text) implements ConsoleOp<Unit> {}
    record ReadLine() implements ConsoleOp<String> {}
}

// Interpreter as natural transformation: ConsoleOp ~> IO
Natural<ConsoleOpKind.Witness, IOKind.Witness> interpreter = new Natural<>() {
    @Override
    public <A> Kind<IOKind.Witness, A> apply(Kind<ConsoleOpKind.Witness, A> fa) {
        ConsoleOp<A> op = CONSOLE_OP.narrow(fa);
        return switch (op) {
            case ConsoleOp.PrintLine p -> IO.widen(IO.of(() -> {
                System.out.println(p.text());
                return (A) Unit.INSTANCE;
            }));
            case ConsoleOp.ReadLine r -> IO.widen(IO.of(() -> {
                return (A) scanner.nextLine();
            }));
        };
    }
};

// Use with Free monad's foldMap
Free<ConsoleOpKind.Witness, String> program = ...;
Kind<IOKind.Witness, String> executable = program.foldMap(interpreter, ioMonad);
```

~~~admonish tip title="See Also"
For a complete guide to building DSLs with Free monads and natural transformations, see the [Free Monad](../monads/free_monad.md) documentation.
~~~

## Identity Transformation

The identity natural transformation returns its input unchanged:

```java
Natural<F, F> id = Natural.identity();
// id.apply(fa) == fa for all fa
```

This satisfies the identity laws for composition:

```java
// Left identity
Natural.identity().andThen(nat) == nat

// Right identity
nat.andThen(Natural.identity()) == nat
```

## Relationship to Other Concepts

| Concept | Relationship |
|---------|--------------|
| **Functor** | Natural transformations operate on Functors; the naturality law involves `map` |
| **Free Monad** | Interpretation via `foldMap` uses natural transformations |
| **Free Applicative** | Interpretation via `foldMap` also uses natural transformations |
| **Monad Transformers** | `lift` operations are natural transformations |
| **Coyoneda** | Lowering from Coyoneda uses a natural transformation internally |

## When to Use Natural Transformations

### Good Use Cases

1. **Free monad/applicative interpretation** - The primary use case
2. **Type conversions** - Converting between similar container types
3. **Abstracting over interpreters** - Swapping implementations at runtime
4. **Monad transformer lifting** - Moving values up the transformer stack

### When You Might Not Need Them

If you're simply converting a single concrete value (e.g., one specific `Maybe<String>` to `List<String>`), you can use regular methods. Natural transformations shine when you need the **polymorphic** behaviour, working uniformly across all types `A`.

## Summary

Natural transformations are polymorphic functions between type constructors that:

- Transform `F[A]` to `G[A]` for **any** type `A`
- Preserve structure (satisfy the naturality law)
- Compose associatively
- Form the basis for Free monad interpretation
- Enable clean separation between program description and execution

They are a fundamental building block in functional programming, particularly when working with effect systems and domain-specific languages.

---

~~~admonish tip title="Further Reading"
- **Bartosz Milewski**: [Natural Transformations](https://bartoszmilewski.com/2015/04/07/natural-transformations/) - Accessible introduction with diagrams
- **Cats Documentation**: [FunctionK](https://typelevel.org/cats/datatypes/functionk.html) - Scala's equivalent (called `~>` or `FunctionK`)
- **Functional Programming in Scala**: Chapter 11 covers natural transformations in the context of Free monads
~~~

~~~admonish info title="Hands-On Learning"
Practice natural transformations in [Tutorial 08: Natural Transformation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial08_NaturalTransformation.java) (5 exercises, ~10 minutes).
~~~

---

**Previous:** [Bifunctor](bifunctor.md)
**Next:** [For Comprehension](for_comprehension.md)
