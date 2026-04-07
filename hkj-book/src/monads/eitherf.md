# EitherF: _Either for Effects_

`EitherF<F, G, A>` is `Either` lifted to the type constructor level.

---

~~~admonish info title="What You'll Learn"
- What EitherF is and when to use it
- How Inject embeds effects into composed types
- How Interpreters.combine merges interpreters
~~~

## Core Concept

Just as `Either<L, R>` represents "a value that is either an L or an R," `EitherF<F, G, A>` represents "an instruction that is either from effect-set F or effect-set G."

```java
public sealed interface EitherF<F, G, A>
    permits EitherF.Left, EitherF.Right {

  record Left<F, G, A>(Kind<F, A> value) implements EitherF<F, G, A> {}
  record Right<F, G, A>(Kind<G, A> value) implements EitherF<F, G, A> {}
}
```

The `F` suffix follows the established `modifyF` convention where it means "lifted to the functor/effect level."

## When to Use

Use EitherF when composing multiple effect algebras for Free monad programs. A program that needs both Console and Database operations uses `EitherF<ConsoleOpKind.Witness, DbOpKind.Witness>` as its effect type.

For most users, EitherF is an implementation detail; the `@ComposeEffects` annotation generates the composition automatically.

## Inject

`Inject<F, G>` witnesses that effect type F can be embedded into a larger effect type G:

```java
public interface Inject<F, G> {
  <A> Kind<G, A> inject(Kind<F, A> fa);
}
```

Standard instances (provided by `InjectInstances`):

`injectLeft()`
: Inject into the left of an EitherF

`injectRight()`
: Inject into the right of an EitherF

`injectRightThen(Inject)`
: Transitive injection for 3+ effects

## Interpreters.combine

Merges individual interpreters into one for the composed type:

```java
Natural<EitherFKind.Witness<F, G>, M> combined =
    Interpreters.combine(interpreterF, interpreterG);
```

Overloads support 2, 3, and 4 effects. For 3+ effects, nesting is internal: `EitherF<F, EitherF<G, H>>`. Users never construct this manually.

## Free.translate

Transforms `Free<F, A>` to `Free<G, A>` using a natural transformation:

```java
Free<G, A> translated = Free.translate(program, inject::inject, functorG);
```

This is how `Bound` instances lift single-effect operations into the composed effect type.

---

## See Also

- [Free Monad](free_monad.md): the Free monad that EitherF compositions target
- [Payment Processing](../examples/payment_processing.md): worked example using EitherF composition
