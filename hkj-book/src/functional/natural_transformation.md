# Natural Transformation
## _Polymorphic Functions Between Type Constructors_

~~~admonish info title="What We'll Learn"
- What a natural transformation is, in two sentences
- How to convert between containers (`Maybe`, `List`, `Either`, `Try`) without peeking inside
- The naturality law and why a transformation that breaks it stops being trustworthy
- How natural transformations compose and where the identity transformation fits
- Why every Free monad interpreter is a natural transformation in disguise
- Where this shows up inside the Foundations one-liner
~~~

~~~admonish example title="Hands-On Practice"
[Tutorial08_NaturalTransformation.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial08_NaturalTransformation.java)
~~~

~~~admonish example title="See Example Code"
- [NaturalTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/NaturalTest.java) - Comprehensive test suite
- [NaturalTransformationExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/natural/NaturalTransformationExample.java) - Practical examples
~~~

## Two Sentences

A natural transformation is a function that converts every `F<A>` into a `G<A>`, for *any* `A`, without ever looking at the value inside. It changes the container, never the cargo.

```
F<A>  ────  Natural<F, G>  ────▶  G<A>
```

If we have used Higher-Kinded-J for more than a few hours, we have already called several. `MaybePath::toEitherPath` is one. `Either::toMaybe` is another. The interface in this section is just the formalisation, plus the laws that keep these transformations honest.

---

## Everyday Examples

| From | To | Transformation |
|------|----|----------------|
| `Maybe<A>` | `List<A>` | `Nothing` becomes `[]`, `Just(x)` becomes `[x]` |
| `List<A>` | `Maybe<A>` | Take the head element, if any |
| `Either<E, A>` | `Maybe<A>` | `Left` becomes `Nothing`, `Right(x)` becomes `Just(x)` |
| `Try<A>` | `Either<Throwable, A>` | `Success`/`Failure` map to `Right`/`Left` |
| `MaybePath<A>` | `EitherPath<E, A>` | absence becomes a typed `Left` carrying the supplied error |

The pattern is the same in every row: a rule about the *shape* of the input that knows nothing about what the input contains.

---

## The Interface

```java
@FunctionalInterface
public interface Natural<F, G> {

    <A> Kind<G, A> apply(Kind<F, A> fa);

    default <H> Natural<F, H> andThen(Natural<G, H> after);

    default <E> Natural<E, G> compose(Natural<E, F> before);

    static <F> Natural<F, F> identity();
}
```

Three things to notice:

1. The `<A>` is on `apply`, not on the interface. A single `Natural<F, G>` works for *every* element type. That polymorphism is the entire point.
2. It is a `@FunctionalInterface`, so most transformations are written as a one-line lambda.
3. `andThen` and `compose` exist because we want to chain transformations, and `identity()` exists because chains need a sensible starting point.

---

## Three Worked Transformations

### `Maybe ~> List`

```java
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

Natural<MaybeKind.Witness, ListKind.Witness> maybeToList = new Natural<>() {
    @Override
    public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
        Maybe<A> maybe = MAYBE.narrow(fa);
        List<A> list = maybe.fold(
            () -> List.of(),
            value -> List.of(value));
        return LIST.widen(list);
    }
};

Kind<MaybeKind.Witness, String> hello   = MAYBE.widen(Maybe.just("hello"));
Kind<ListKind.Witness, String>  asList  = maybeToList.apply(hello);
// LIST.narrow(asList) -> ["hello"]
```

### `Either ~> Maybe` (discarding the error)

```java
Natural<EitherKind.Witness<String>, MaybeKind.Witness> eitherToMaybe = new Natural<>() {
    @Override
    public <A> Kind<MaybeKind.Witness, A> apply(Kind<EitherKind.Witness<String>, A> fa) {
        Either<String, A> either = EITHER.<String, A>narrow(fa);
        return MAYBE.widen(
            either.fold(left -> Maybe.nothing(), Maybe::just));
    }
};
```

### `List ~> Maybe` (head)

```java
Natural<ListKind.Witness, MaybeKind.Witness> listHead = new Natural<>() {
    @Override
    public <A> Kind<MaybeKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
        List<A> list = LIST.narrow(fa);
        return MAYBE.widen(
            list.isEmpty() ? Maybe.nothing() : Maybe.just(list.get(0)));
    }
};
```

In all three cases, the body never inspects the element type. That is the discipline `Natural` enforces.

---

## The Naturality Law

A transformation that calls itself "natural" must obey one rule:

> For any function `f: A -> B` and any value `fa: F<A>`,
> `nat.apply(map(f, fa))` equals `map(f, nat.apply(fa))`.

In a diagram:

```
            map(f)
   F<A>  ──────────▶  F<B>
    │                   │
   nat                 nat
    ▼                   ▼
   G<A>  ──────────▶  G<B>
            map(f)
```

Either route to the bottom-right corner gives the same answer. Concretely: it does not matter whether we map and then transform, or transform and then map. The container changes; the value does not.

### Why This Matters

If a transformation peeks at `A` and behaves differently for `Integer` than for `String`, it is no longer structure-preserving and the naturality square stops commuting. The moment that happens, every reasoning we want to do about composition stops holding. We can no longer freely refactor `nat.apply(map(f, x))` into `map(f, nat.apply(x))`, and the abstraction has cost us nothing while delivering nothing.

The discipline is not pedantic; it is what makes natural transformations *useful*.

~~~admonish warning title="Violating Naturality"
A "transformation" that converts a `List<Integer>` differently from a `List<String>`, or that uses `instanceof` to inspect element types, is not a natural transformation. It might still be a useful function; it just is not one of these.
~~~

---

## Composition

Natural transformations chain like ordinary functions, with `andThen` and `compose`:

```java
Natural<F, G> fg = ...;
Natural<G, H> gh = ...;

Natural<F, H> fh = fg.andThen(gh);     // F ~> G ~> H
```

Composition is associative. Identity is `Natural.identity()`:

```java
Natural<F, F> id = Natural.identity();   // id.apply(fa) == fa
Natural.identity().andThen(nat).equals(nat);
nat.andThen(Natural.identity()).equals(nat);
```

These laws are the same laws categories obey. We do not need the category-theory background to use them; we just need to know that chaining is well-behaved and we can trust it.

---

## Where This Shows Up

### Free Monad Interpreters

The most common use is interpreting Free monads. A Free program is built from an instruction set `F`. To run it, we provide a natural transformation `F ~> M`, where `M` is the target effect (often `IO`).

```java
sealed interface ConsoleOp<A> {
    record PrintLine(String text) implements ConsoleOp<Unit> {}
    record ReadLine() implements ConsoleOp<String> {}
}

Natural<ConsoleOpKind.Witness, IOKind.Witness> interpreter = new Natural<>() {
    @Override
    public <A> Kind<IOKind.Witness, A> apply(Kind<ConsoleOpKind.Witness, A> fa) {
        ConsoleOp<A> op = CONSOLE_OP.narrow(fa);
        return switch (op) {
            case ConsoleOp.PrintLine p -> IO_OP.widen(IO.delay(() -> {
                System.out.println(p.text());
                return (A) Unit.INSTANCE;
            }));
            case ConsoleOp.ReadLine r -> IO_OP.widen(IO.delay(() -> (A) scanner.nextLine()));
        };
    }
};

Free<ConsoleOpKind.Witness, String> program = ...;
Kind<IOKind.Witness, String> executable = program.foldMap(interpreter, ioMonad);
```

The `interpreter` is *the* place where the program meets the world. Swapping it for a test interpreter (one that returns canned input and records prints) is how we test Free programs without mocking. The interpreter is a `Natural`, full stop.

### Monad Transformer Lifting

Every `lift` on a monad transformer (`IO ~> EitherT<IO, E, ?>`, for example) is a natural transformation. The same shape applies; the cargo is preserved; the container deepens by one layer.

### Coyoneda Lowering

`Coyoneda.lower` runs internally as a natural transformation. We rarely need to know that, but it explains why the lowering is law-abiding without extra hand-waving.

---

## Back to the One-Liner

Once again the line:

```java
repo.find(id)
    .toEitherPath()           // <-- natural transformation: MaybePath ~> EitherPath
    .focus().attributes().at(key)
    .modify(spec::validateAndCoerce)
    .flatMap(repo::save);
```

`.toEitherPath()` is the everyday face of a natural transformation. It takes whatever was inside the `MaybePath` (a found node, or absence) and produces the equivalent `EitherPath` (a `Right` with the node, or a typed `Left` carrying the not-found error). The element type `A` does not matter; the rule converts the *shape*. That is precisely what `Natural` is for.

The reason this composes with everything downstream is that `Natural` is law-abiding: anything we could have done to the value inside the `MaybePath` we can equally do to the value inside the `EitherPath` after the transformation, and the answer agrees. That is naturality, doing useful work without us thinking about it.

---

## When to Reach for `Natural`

Good use cases:

1. **Free monad and Free applicative interpretation.** The primary reason the type exists.
2. **Converting between similar containers in generic code.** Hand-rolling each conversion is fine for one-offs; defining a `Natural` is the move when the conversion is reused across the codebase.
3. **Swapping interpreters at runtime.** Production interpreter, test interpreter, dry-run interpreter; same program, three different `Natural`s.
4. **Lifting through transformer stacks.** Moving a value up a stack of transformers is naturality at work.

When we do *not* need a `Natural`: if we only need to convert one specific `Maybe<String>` to a `List<String>` once, write a method. `Natural` earns its keep when the polymorphism does.

---

## Things People Get Wrong

~~~admonish warning title="Common Misunderstandings"
- **"Naturality is a category-theory thing I can ignore."** It is the law that lets us refactor freely between "transform then map" and "map then transform". Without it, we lose the ability to reason about composition. We do not need the category theory to honour the law.
- **"I can branch on `A`."** Not in a `Natural`. The moment the body of `apply` looks at the element type, we have a function with the same Java signature but none of the guarantees `Natural` is supposed to provide.
- **"`Natural` and `Functor.map` are the same."** `map` rewrites the value `A`, leaving the container `F` alone. `Natural` rewrites the container `F`, leaving the value `A` alone. They are dual.
- **"It is just a method reference like any other."** It can be expressed that way, but only if the method preserves the value across all `A`. The `@FunctionalInterface` keeps the type signature honest; the law keeps the *meaning* honest.
~~~

---

## Identity, Composition, Laws

```java
// Left identity
Natural.identity().andThen(nat).equals(nat);

// Right identity
nat.andThen(Natural.identity()).equals(nat);

// Associativity
fg.andThen(gh).andThen(hi).equals(fg.andThen(gh.andThen(hi)));
```

These are the same laws functions obey. `Natural` is, deliberately, just a function with extra discipline.

---

## Relationship to Other Concepts

| Concept | Relationship |
|---------|--------------|
| **Functor** | Natural transformations operate on Functors; the naturality law involves `map` |
| **Free Monad** | Interpretation via `foldMap` uses a natural transformation |
| **Free Applicative** | Interpretation via `foldMap` also uses a natural transformation |
| **Monad Transformers** | `lift` operations are natural transformations |
| **Coyoneda** | Lowering from Coyoneda uses a natural transformation internally |

---

~~~admonish info title="Key Takeaways"
* A natural transformation converts `F<A>` to `G<A>` without inspecting `A`
* The naturality law makes the conversion structure-preserving and composable
* `andThen`, `compose`, and `identity` make `Natural` chain like ordinary functions
* Every Free monad interpreter is a `Natural`; swap interpreters to swap behaviour
* Inside the Foundations one-liner, `.toEitherPath()` is a `Natural` doing exactly this job
~~~

~~~admonish tip title="See Also"
- [Functor](functor.md) - the type class natural transformations interact with
- [Free Monad](../monads/free_monad.md) - the primary client of natural transformations
- [Bifunctor](bifunctor.md) - mapping over two parameters; a different abstraction along the same axis
- [One Line, Six Layers](../hkts/one_line_six_layers.md) - where this fits in the wider Foundations picture
~~~

~~~admonish tip title="Further Reading"
- **Bartosz Milewski**: [Natural Transformations](https://bartoszmilewski.com/2015/04/07/natural-transformations/) - Accessible introduction with diagrams
- **Mojang/DataFixerUpper**: [DFU on GitHub](https://github.com/Mojang/DataFixerUpper) - Minecraft's Java library where natural transformations drive data version migration
~~~

~~~admonish info title="Hands-On Learning"
Practice natural transformations in [Tutorial 08: Natural Transformation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial08_NaturalTransformation.java) (5 exercises, ~10 minutes).
~~~

---

**Previous:** [Bifunctor](bifunctor.md)
**Next:** [For Comprehension](for_comprehension.md)
