<!-- description: Make a type of your own work with Higher-Kinded-J's type classes: a witness, a helper and an instance, the law tests that prove them, and a Path. -->

# Adding Your Own Type

_A witness, a helper and an instance, the laws they must keep, and a Path at the end._

<!-- source: Sophocles, Antigone, ll. 453-455, trans. F. Storr (Loeb Classical Library, 1912); checked against Project Gutenberg eBook #31 -->
> _"Nor did I deem that thou, a mortal man, / Could'st by a breath annul and override / The immutable unwritten laws of Heaven."_
>
> — Sophocles, *Antigone* (trans. F. Storr)

No compiler reads the functor and monad laws, yet every caller of your instance relies on them. `hkj-test` writes them down, so your build can check them.

![A red tower crane against a blue sky, its hook lowered](../images/extending-crane.png)

Read this page when your codebase has a container type of its own, and you want `Functor`, `Monad`, `For` and the rest to work with it. The worked example is `Result`: a value, or the reason there is none, of the kind many codebases write for themselves. If `Either` or an `EitherPath` would serve, use it instead, and skip this page.

~~~admonish info title="What You'll Learn"
- Give a type of your own a witness, a helper and a `Monad`
- Prove that the instance keeps its laws, with `hkj-test`
- Pass the instance to code written for any container, such as `For`
- Hold a type you cannot change, such as the JDK's `Set`, in a holder
- Turn your type into a Path with `GenericPath`
~~~

~~~admonish example title="See Example Code"
**The code on this page is in the [owntype](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype) package**: the page includes it, so the build compiles and runs it. [OwnTypeBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/owntype/OwnTypeBookTest.java) runs the law tests and holds each claim the page makes.
~~~

---

## What you write {#what-you-write}

| Piece | For `Result` | What it does |
|---|---|---|
| A witness, inside a `Kind` interface | `ResultKind.Witness` | Names the container, so code generic over `F` can mention it |
| The type itself | `Result`, which extends `ResultKind` | Is its own `Kind`, so `widen` allocates nothing |
| A helper | `ResultKindHelper.RESULT` | Moves a `Result` into the encoding with `widen`, and back with `narrow` |
| An instance | `ResultMonad.INSTANCE` | Gives `of`, `map`, `ap` and `flatMap` for `Result` |

[The pieces each type provides](core-concepts.md#the-pieces-each-type-provides) shows the same pieces for the library's own types. Most of those also have a token in `Witnesses`, for `Instances` to look up. Those tokens can only be built inside the library, so your code passes `ResultMonad.INSTANCE` wherever a `Monad` is asked for.

## Your own type: `Result` {#your-own-type}

`Result` extends its own `Kind` interface, which declares the witness:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype/ResultKind.java:kind}}
```

`Result` itself is a sealed interface with two records:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype/Result.java:result}}
```

A `Result` is a `ResultKind`, so it is already a `Kind<ResultKind.Witness, A>`. The helper's `widen` therefore returns its argument, and `narrow` needs only a type pattern:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype/ResultKindHelper.java:helper}}
```

`narrow` throws `KindUnwrapException` for `null`, and for any other class that claims the witness, as the library's own helpers do. [Type Arity](type-arity.md) explains the witness's `WitnessArity<TypeArity.Unary>` bound.

If your type carries its error type as a parameter, as a `Result<E, A>` would, the witness takes it too, as `EitherKind.Witness<L>` does. The instance then becomes a class with a generic `instance()` method, as `EitherMonad` has, since an enum cannot take a type parameter.

## The instance {#the-instance}

A `Monad` asks for four methods: `of`, `map`, `ap` and `flatMap`. `map` and `flatMap` narrow their argument and switch over the two records, `of` widens an `Ok`, and `ap` is built from `flatMap` and `map`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype/ResultMonad.java:monad}}
```

A `Failed` passes through `map` and `flatMap` untouched, so a chain stops at its first failure, as an `Either` chain does. `ap` stops at the first failure too: when both arguments have failed, the function's reason wins. The instance holds no state, so one shared instance is enough, and a single-element `enum` gives you that.

The instance never builds or passes on `null`. `Ok` and `Failed` refuse it, so `of(null)` throws, and `flatMap` narrows what a step returns, so a step that returns `null` throws too. [Values, never null](core-concepts.md#values-never-null) gives the rule.

## Prove your instance {#prove-your-instance}

No compiler checks how `map` and `flatMap` behave. A `map` that turned an `Ok` into a `Failed` would compile, and so would a `flatMap` that lost a `Failed`'s reason. The laws say what callers rely on, and `hkj-test`'s law checkers turn them into assertions:

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/owntype/OwnTypeBookTest.java:laws}}
```

- **`KindHelperLaws.assertRoundTrip`** checks that `narrow(widen(r))` is the very `r` you started with.
- **`FunctorLaws`** checks identity, that mapping with `x -> x` changes nothing, and composition, that two maps equal one map of the composed function. [Functor's laws](../functional/functor.md#two-laws) states them.
- **`MonadLaws`** checks left identity, right identity and associativity, as [Monad's laws](../functional/monad.md#three-laws) states them.
- **`KindEquivalence.byEqualsAfter(RESULT::narrow)`** says when two `Kind`s count as the same: narrow both, then compare with `equals`, which a record already has.

Every law that takes a `Result` runs on an `Ok` and on a `Failed`, since each record has its own branch. `verifyEmail` always fails, so left identity is checked with a failing step too. `ApplicativeLaws` checks `ap` the same way, in the same test file. A law checker for `MonadError` is not supported yet.

The law checkers ship in [`hkj-test`](../tooling/test_assertions.md#adding-the-dependency). `ORDER` is any `Order`, and the test uses these imports, with JUnit's:

<!-- verify -->
```java
import static org.higherkindedj.example.book.foundations.owntype.ResultKindHelper.RESULT;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.example.book.foundations.owntype.Result;
import org.higherkindedj.example.book.foundations.owntype.ResultKind;
import org.higherkindedj.example.book.foundations.owntype.ResultMonad;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.KindHelperLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
```

## Use it with generic code {#use-it-with-generic-code}

`ResultMonad.INSTANCE` is a `Monad<ResultKind.Witness>`, so code written for any container takes it. The chapter introduction's `totals`, and a `For` comprehension, both run on a `Result`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype/OwnTypeBook.java:generic}}
```

Neither knows about `Result`; the instance does. A `Failed` order would give a `Failed` from both, with its reason.

## A type you cannot change {#a-type-you-cannot-change}

The JDK's `Set` cannot extend your `Kind` interface, so the helper wraps it in a holder record that does:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype/SetKind.java:set_kind}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype/SetKindHelper.java:set_helper}}
```

`widen` now allocates a holder, and `narrow` reads the `Set` back out with a record pattern. The round-trip law still holds, because `narrow` returns the very `Set` that went in. The holder shares that `Set` with the caller rather than copying it, so widen an unmodifiable one, such as `Set.of` or `Set.copyOf` gives. [Lifting the Hood](lifting_the_hood.md) counts what a holder costs. Write an instance for `Set` as `ResultMonad` is written, through `SET.narrow` and `SET.widen`.

## Your type gets a Path {#your-type-gets-a-path}

Once the instance exists, `GenericPath.of` turns a `Kind` of your type into a Path. Application code can then use `map` and `via`, with no `widen` or `narrow` between the steps:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype/OwnTypeBook.java:path_of}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/owntype/OwnTypeBook.java:path}}
```

`via` takes a step that returns another Path, here built from the `Result` that `verified` returns. `runKind` hands back the `Kind`, and `RESULT.narrow` the `Result`. The Path's `recover` and `recoverWith` need a `MonadError`: built from a `Monad` alone, as here, they throw `UnsupportedOperationException`. Give `GenericPath.of` a `MonadError` instance, and they work. [Generic Paths](../effect/path_generic.md) covers the rest. [Recovery with `errorType`](../effect/path_source.md#recovery-with-errortype) shows a `MonadError` for a type shaped like `Result`, and how `@PathSource` generates a named Path for it, with no `runKind` or `narrow`.

---

**Previous:** [Lifting the Hood](lifting_the_hood.md)
**Next:** [Type Classes](../functional/ch_intro.md)
