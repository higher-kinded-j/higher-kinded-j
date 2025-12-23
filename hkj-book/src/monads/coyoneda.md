# Coyoneda
## _The Free Functor and Map Fusion_

~~~admonish info title="What You'll Learn"
- What Coyoneda is and why it's called the "free functor"
- How Coyoneda provides automatic Functor instances for any type constructor
- The map fusion optimisation and how it works
- Using Coyoneda with Free monads to simplify DSL definitions
- The lift and lower pattern
- When Coyoneda improves performance
~~~

~~~admonish title="Hands On Practice"
[Tutorial09_Coyoneda.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial09_Coyoneda.java)
~~~

~~~admonish example title="See Example Code"
- [CoyonedaTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/coyoneda/CoyonedaTest.java) - Comprehensive test suite
- [CoyonedaFunctorTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/coyoneda/CoyonedaFunctorTest.java) - Functor law verification
- [CoyonedaExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/coyoneda/CoyonedaExample.java) - Practical examples
~~~

## Purpose

Coyoneda solves two practical problems:

1. **Automatic Functor instances**: Any type constructor `F` can be wrapped in Coyoneda, which gives it a Functor instance for free, even if `F` itself doesn't have one.

2. **Map fusion**: Multiple consecutive `map` operations are automatically fused into a single function composition, reducing overhead.

### The Problem Coyoneda Solves

When building Free monads, each instruction type needs a Functor instance. This can be tedious:

```java
// Without Coyoneda: must implement Functor for every DSL
sealed interface DatabaseOp<A> {
    record Query(String sql) implements DatabaseOp<ResultSet> {}
    record Update(String sql) implements DatabaseOp<Integer> {}
}

// Tedious: implement Functor<DatabaseOp> manually
// Even more tedious if DatabaseOp has many variants
```

With Coyoneda, you can skip the Functor implementation entirely:

```java
// With Coyoneda: wrap your DSL and get Functor for free
Coyoneda<DatabaseOp, ResultSet> coyo = Coyoneda.lift(new Query("SELECT * FROM users"));
// Now you can map over it without implementing Functor<DatabaseOp>!
```

## How Coyoneda Works

Coyoneda stores two things:
1. A value of type `Kind<F, X>` (the original wrapped value)
2. A function `X -> A` (the accumulated transformations)

When you call `map(f)`, instead of applying `f` immediately, Coyoneda just composes `f` with the existing transformation:

```
Coyoneda(fx, transform).map(f) = Coyoneda(fx, f.compose(transform))
```

The actual mapping only happens when you "lower" back to `F` using a real Functor instance.

```
lift: F[A] ──────────────> Coyoneda[F, A]    (wrap with identity function)
                               │
                          map(f).map(g).map(h)
                               │
                               ▼
                          Coyoneda[F, D]    (functions composed, not applied)
                               │
lower: Coyoneda[F, D] ─────────> F[D]       (apply composed function once)
```

## Core Interface

```java
public sealed interface Coyoneda<F, A> {

    /**
     * Lifts a Kind<F, A> into Coyoneda with the identity transformation.
     */
    static <F, A> Coyoneda<F, A> lift(Kind<F, A> fa);

    /**
     * Creates a Coyoneda with a value and transformation function.
     */
    static <F, X, A> Coyoneda<F, A> apply(Kind<F, X> fx, Function<? super X, ? extends A> transform);

    /**
     * Maps a function, composing it with existing transformations.
     * No Functor instance required!
     */
    <B> Coyoneda<F, B> map(Function<? super A, ? extends B> f);

    /**
     * Lowers back to Kind<F, A> by applying the accumulated transformation.
     * This is where the actual mapping happens.
     */
    Kind<F, A> lower(Functor<F> functor);

    /**
     * Returns the underlying Kind value (before transformations).
     */
    Kind<F, ?> underlying();
}
```

## Basic Usage

### Lifting and Mapping

```java
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

// Lift a Maybe into Coyoneda
Kind<MaybeKind.Witness, Integer> maybe = MAYBE.widen(Maybe.just(42));
Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(maybe);

// Map without needing a Functor instance
Coyoneda<MaybeKind.Witness, String> mapped = coyo
    .map(x -> x * 2)           // 42 -> 84
    .map(x -> x + 1)           // 84 -> 85
    .map(Object::toString);    // 85 -> "85"

// All three maps are fused into ONE composed function!
// The function (x -> ((x * 2) + 1).toString()) is stored but not yet applied
```

### Lowering with a Functor

```java
// When ready, lower back using a Functor instance
MaybeFunctor functor = MaybeFunctor.INSTANCE;
Kind<MaybeKind.Witness, String> result = mapped.lower(functor);

// Only NOW does the actual mapping happen - just once!
Maybe<String> finalResult = MAYBE.narrow(result);
// Result: Just("85")
```

### Using CoyonedaFunctor

The `CoyonedaFunctor<F>` class provides a Functor instance for any `Coyoneda<F, _>`:

```java
CoyonedaFunctor<MaybeKind.Witness> coyoFunctor = new CoyonedaFunctor<>();

Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> kindCoyo =
    COYONEDA.widen(Coyoneda.lift(maybe));

Kind<CoyonedaKind.Witness<MaybeKind.Witness>, String> mapped =
    coyoFunctor.map(x -> x.toString(), kindCoyo);
```

## Map Fusion

Map fusion is the key performance benefit. Consider:

```java
// Without Coyoneda: three separate traversals
List<String> result = list.stream()
    .map(x -> x * 2)
    .map(x -> x + 1)
    .map(Object::toString)
    .toList();
// Each map creates intermediate results
```

```java
// With Coyoneda: functions are composed, single traversal
Coyoneda<ListKind.Witness, Integer> coyo = Coyoneda.lift(LIST.widen(list));
Coyoneda<ListKind.Witness, String> mapped = coyo
    .map(x -> x * 2)
    .map(x -> x + 1)
    .map(Object::toString);
// No mapping yet - just function composition

Kind<ListKind.Witness, String> result = mapped.lower(listFunctor);
// NOW the composed function is applied in ONE traversal
```

The benefit is most noticeable when:
- The underlying structure is expensive to traverse
- You have many consecutive map operations
- The intermediate types are complex

## Use with Free Monads

Coyoneda is particularly useful with Free monads. Without Coyoneda, your instruction set must be a Functor:

```java
// Without Coyoneda: must implement Functor<ConsoleOp>
Free<ConsoleOpKind.Witness, String> program = Free.liftF(readLine, consoleOpFunctor);
```

With Coyoneda, you can wrap any instruction set:

```java
// With Coyoneda: no Functor needed for ConsoleOp
// The Free monad operates on Coyoneda<ConsoleOp, _> instead

// Lift instruction into Coyoneda, then into Free
Coyoneda<ConsoleOpKind.Witness, String> coyoOp = Coyoneda.lift(readLineKind);
Free<CoyonedaKind.Witness<ConsoleOpKind.Witness>, String> program =
    Free.liftF(COYONEDA.widen(coyoOp), new CoyonedaFunctor<>());
```

This eliminates boilerplate when you have many instruction types.

## The Yoneda Lemma

Coyoneda is based on the **covariant Yoneda lemma** from category theory. In simplified terms:

```
Coyoneda[F, A] ≅ F[A]
```

This isomorphism is witnessed by:
- `lift`: `F[A] -> Coyoneda[F, A]` (wraps with identity function)
- `lower`: `Coyoneda[F, A] -> F[A]` (applies accumulated function via Functor)

The isomorphism holds for any Functor `F`. Coyoneda essentially "delays" the functor operations until lowering.

## Functor Laws

`CoyonedaFunctor` satisfies the Functor laws automatically:

**Identity Law:**
```java
coyo.map(x -> x) == coyo
// Composing with identity doesn't change the accumulated function
```

**Composition Law:**
```java
coyo.map(f).map(g) == coyo.map(x -> g.apply(f.apply(x)))
// Multiple maps compose into one - this is map fusion!
```

These laws hold by construction because `map` simply composes functions.

## When to Use Coyoneda

### Good Use Cases

1. **Free monad DSLs**: Avoid implementing Functor for each instruction type
2. **Map fusion**: Optimise chains of map operations on expensive structures
3. **Deferred computation**: Delay mapping until you have a Functor available
4. **Generic programming**: Work with type constructors that don't have Functor instances

### When You Might Not Need It

1. **Single map operations**: No fusion benefit with just one map
2. **Already have a Functor**: If implementing Functor is easy, Coyoneda adds indirection
3. **Simple types**: For types like `Optional` or `List`, the built-in map is already efficient

## Performance Considerations

Coyoneda trades:
- **Pros**: Reduced traversals, function composition is cheap, deferred execution
- **Cons**: Object allocation for Coyoneda wrapper, slightly more complex types

The benefit is most significant when:
- The underlying `F` is expensive to map over (e.g., large trees, IO operations)
- You have many consecutive map operations
- You want to defer all mapping to a single point

For simple cases with one or two maps on efficient structures, the overhead may not be worth it.

## Summary

Coyoneda provides:

- **Automatic Functor instances**: Any type constructor gains a Functor via wrapping
- **Map fusion**: Multiple maps compose into a single function application
- **Deferred execution**: Actual mapping happens only at lowering
- **DSL simplification**: Free monad instruction sets don't need Functor instances

It's a powerful tool for optimising functional pipelines and simplifying Free monad definitions, based on the elegant mathematics of the Yoneda lemma.

---

~~~admonish tip title="Further Reading"
- **Why it helps**: [Introduction to Yoneda and Coyoneda](https://gist.github.com/gregberns/ede18190d5117eea6fb51815e2eab9b2) - Explains Coyoneda as a _deferred map_
- **Bartosz Milewski**: [The Yoneda Lemma](https://bartoszmilewski.com/2015/09/01/the-yoneda-lemma/) - The mathematical foundation (more theoretical)
~~~

~~~admonish info title="Hands-On Learning"
Practice Coyoneda in [Tutorial 09: Coyoneda](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial09_Coyoneda.java) (5 exercises, ~10 minutes).
~~~

---

**Previous:** [Free Applicative](free_applicative.md)
**Next:** [Try](try_monad.md)
