# Foundations FAQ

> _"The most important questions of life are, for the most part, really only problems of probability."_
>
> – Pierre-Simon Laplace

---

This page answers the questions that experienced Java developers ask once they have read the rest of Foundations and are sceptical, in roughly the order they tend to ask them.

~~~admonish info title="What We'll Learn"
- Honest answers to the design and cost questions about HKT simulation
- Where Higher-Kinded-J sits relative to Vavr, Arrow-Kt, and friends
- What Project Valhalla and pattern matching are likely to change
- Which questions have settled answers and which are still open
~~~

---

## Why can't this just be an interface `Mappable<T>`?

It can, for one container at a time. We can write `interface Mappable<T> { <U> Mappable<U> map(Function<T, U>); }` and have `Optional`, `List`, and `Try` all implement it.

What we cannot write is a method that says *"give me any `Mappable<T>` and I will return another `Mappable<T>` of the same shape"*. Java has no way to spell "of the same shape" because it has no way to talk about the type constructor itself. The signature `<F<?>, A, B> F<B> map(F<A>, Function<A, B>)` is not legal Java; the `F<?>` parameter cannot be quantified at the method level.

`Kind<F, A>` is the workaround. It moves the "shape" into a regular type parameter `F` (the witness type), so we can write *one* generic method that legitimately keeps the container constant across the call. Without that move, every generic combinator (`traverse`, `sequence`, `flatMapN`, `For`) would have to be duplicated for every container type the library supports.

---

## Why two type parameters on `Kind` instead of one?

Because we routinely want to talk about both halves separately.

`Kind<F, A>` reads as "container `F` holding an `A`". `F` decides which `Functor`, `Applicative`, or `Monad` instance gets dispatched. `A` is the value the user actually cares about. A combinator like `map` rewrites `A` and leaves `F` alone; a natural transformation rewrites `F` and leaves `A` alone. Splitting them at the type level is what lets the type system enforce that distinction.

`Kind2<F, A, B>` exists for the binary case (`Either<L, R>`, `Function<A, B>`) and is what `Bifunctor` and `Profunctor` operate on.

---

## Is the witness type ever instantiated? Does it allocate?

No. Witness types are declared as `final class Witness implements WitnessArity<TypeArity.Unary> {}` with a private constructor. Nothing in the library ever calls `new Witness()`. The witness exists purely as a marker at the type level: a name the type system can write down, not a value the runtime ever sees. Allocation cost is precisely zero.

---

## What's the runtime cost of `widen` and `narrow`?

It depends on whether the underlying type is a *library type* or a *JDK type*.

**Library types** (`Maybe`, `Either`, `Try`, `Validated`, `IO`, `Lazy`, `Id`, the monad transformers, all the `*Path` types) directly implement their `Kind` interface. `widen` is a null check followed by a typed reference (no allocation, no copy). `narrow` is an `instanceof` check followed by a cast. Both calls inline aggressively under JIT and disappear in hot paths. Allocation cost: zero.

**JDK types** (`java.util.Optional`, `java.util.List`, `java.util.stream.Stream`, `java.util.concurrent.CompletableFuture`) cannot implement `Kind` because we do not own their source. For these, `widen` allocates one small `Holder` record per call and `narrow` reads the field back. Allocation cost: one record per `widen`. The JIT can often elide these via escape analysis when the `Kind` does not leave the current method.

If the cost matters in a hot loop, prefer the library type: `Maybe` over `Optional`, `EitherPath` over a hand-rolled `Either<Throwable, A>` over JDK types.

The benchmarks chapter has the numbers. See [Benchmarks & Performance](../benchmarks.md).

---

## Why does `narrow` throw rather than return an `Either`?

`narrow` is checking a structural invariant, not a domain condition. If a `Kind<OptionalKind.Witness, String>` arrives at `OPTIONAL.narrow` and is not an `Optional`-shaped `Kind`, something is wrong with the *code*, not with the data. A returned `Either` would push the bug into the value layer and force every caller to handle it; an exception keeps the noise out of the success path and crashes loudly when an invariant is violated.

`KindUnwrapException` is unchecked deliberately. Catching it in application code almost always indicates a deeper problem.

---

## Why a `Holder` for some types but direct implementation for others?

We can only make a type implement `Kind` if we own the source. `java.util.Optional` is final-by-policy and lives in the JDK; we cannot retrofit it. `Maybe`, `Either`, `IO`, and the rest of the library types are ours, so we wire `Kind` directly into them and avoid the wrapper.

The asymmetry is unfortunate but not material. The type-class instances do not care which approach the underlying type takes; they call `widen` and `narrow` on the helper and the helper knows what to do.

---

## Why are arities encoded with `WitnessArity<TypeArity.Unary>` instead of separate `Kind`/`Kind2` interfaces only?

We do have separate `Kind` and `Kind2` interfaces. The `WitnessArity` bound is an additional safety check that ties a witness type to its arity at the type level. It prevents someone declaring `class MyWitness implements WitnessArity<TypeArity.Unary> {}` and then accidentally using it as a binary witness, or vice versa. The compiler refuses, with a message that points at the arity rather than at a deeper inference failure ten layers down.

Without it, mistakes still compile but break at runtime in unhelpful ways. We learned that the hard way and added the bound.

---

## How does this compare with Vavr, Cyclops, or Arrow-Kt?

Honestly:

- **Vavr** ships excellent concrete monads (`Try`, `Either`, `Option`, `Validation`) and a broad collection library, but does not expose generic type classes. Code written against Vavr has to talk to each container type individually. If the goal is "use one or two functional types in an otherwise normal Java codebase", Vavr is often the simpler choice.
- **Cyclops** is closer to Higher-Kinded-J in ambition: it does provide HKT-style abstractions, and many of the patterns will look familiar. Higher-Kinded-J differs in modelling, in the strictness of its witness/arity story, and in shipping the Effect Path / Optics / Transformers layers as first-class concerns.
- **Arrow-Kt** is the most direct analogue, but in Kotlin, where context receivers and inline classes make the encoding lighter. If we are on Kotlin, Arrow is genuinely a better experience. On Java, Higher-Kinded-J is the closest equivalent that does not require a language change.

We are not trying to be a one-for-one port of any of them. Higher-Kinded-J leans into Java records, sealed types, and JIT-friendly encodings, and treats generic type classes as a tool that earns its keep when paired with the Effect Path layer.

---

## Will Project Valhalla or pattern matching make this obsolete?

Not in any near-term sense.

Valhalla brings value classes, which will let some `Holder` allocations vanish at the source level rather than just at the JIT level. That is a quiet win for performance, not a structural change.

Pattern matching for sealed types makes `fold`-style code more pleasant. It does not address the underlying limitation: Java still cannot quantify a method over a type constructor.

Higher-kinded types proper would obviate the witness-type machinery, but they are not on any current JEP. If they ever ship, the migration story is mechanical: the public API of Higher-Kinded-J is shaped to be replaceable with native HKTs without changing user code.

---

## I see `OptionalKind.Witness` in some examples and `Optional.Witness` in others. Which is correct?

`OptionalKind.Witness`. The `Witness` is declared as a nested final class on the `OptionalKind` interface, not on `java.util.Optional` itself. If we encounter `Optional.Witness` in older docs, treat it as a typo. The same convention applies to every type in the library: the witness lives on `XxxKind`, never on the underlying value type.

---

## What is the elevator pitch for someone who has not read the chapter?

Three sentences:

> Java cannot write a method that takes "any container `F` of `A`" because `F<?>` is not a legal type variable. Higher-Kinded-J fakes it by introducing a marker type `F`, an interface `Kind<F, A>` that ties it to the value type, and tiny conversion helpers to bridge between `Kind` and the real Java type. Once we accept that detour, we can write generic combinators that work unchanged across `Optional`, `Either`, `IO`, and everything else the library supports.

If the answer is "yes, but why bother?", [One Line, Six Layers](one_line_six_layers.md) makes the case better than any prose can.

---

## Where do I go from here?

If a question we have is not on this page, it is probably worth asking. The library has a [GitHub issue tracker](https://github.com/higher-kinded-j/higher-kinded-j/issues), and FAQ entries grow there before they grow here.

For mechanism rather than philosophy, [Lifting the Hood](lifting_the_hood.md) walks through one `flatMap` call from user code all the way down to `widen`/`narrow` and back up.

---

**Previous:** [Extending the Simulation](extending-simulation.md)
**Next:** [Type Classes](../functional/ch_intro.md)
