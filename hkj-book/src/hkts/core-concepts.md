<!-- description: How Higher-Kinded-J writes "any container F" in Java: witness types, Kind<F, A>, widen and narrow, WitnessArity, and what each core type does with null. -->

# How the Encoding Works

_Write "any container `F`" in Java: a witness names the container, and `Kind<F, A>` carries the name._

<!-- source: Jonathan Swift, Gulliver's Travels (1726), Part III, Chapter V; text checked against Project Gutenberg eBook #829 -->
> _"since words are only names for things, it would be more convenient for all men to carry about them such things as were necessary to express a particular business they are to discourse on."_
>
> — Jonathan Swift, *Gulliver's Travels* (Part III, Chapter V)

The sages of Lagado gave up words and carried the things themselves. Java cannot carry a type constructor about, so Higher-Kinded-J carries a name for one instead: the witness.

![A head in profile, made of jigsaw pieces printed with gears and sketches, its top pieces lifting away](../images/concepts.png)

~~~admonish info title="What You'll Learn"
- Read a `Kind<F, A>` and name the container its witness stands for
- Move a value into the encoding with `widen` and back with `narrow`, and say what each costs
- Declare the `WitnessArity` bound on a generic method
- Predict what a core type does with `null`, and return `Unit` or a `Maybe` instead
- Tell an encoding failure from a domain failure
~~~

~~~admonish example title="See Example Code"
**The code on this page is [EncodingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/encoding/EncodingBook.java)**: the page includes it, so the build compiles and runs it. [EncodingBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/encoding/EncodingBookTest.java) holds each claim the page makes.
~~~

---

## Why Java needs an encoding {#why-java-needs-an-encoding}

Java can abstract over a type, as `List<T>` does over `T`, but not over a type constructor such as `List` itself. A method for "any container of integers" does not compile:

<!-- verify:rejects "type parameter F" -->
```java
<F> F<Integer> doubleAll(F<Integer> numbers) {
    return numbers;
}
```

javac answers `unexpected type`, with `found: type parameter F`: a type parameter cannot take type arguments of its own. A language with higher-kinded types can name such a parameter, written `F<_>` in this book: "a container still waiting for its element type". It is not Java's `?` wildcard, which stands for an unknown type rather than a missing one. [Introduction to Higher-Kinded Types](hkt_introduction.md) says what this buys. Higher-Kinded-J gets there with ordinary interfaces. Each container gets a name, and generic code passes the name about in place of the container. The technique is called [defunctionalisation](../glossary/type-system.md#defunctionalisation).

## The witness and `Kind<F, A>` {#the-witness-and-kind}

A **witness** is an empty class that stands for one container. `ListKind.Witness` stands for `List`, `MaybeKind.Witness` for `Maybe`, and `EitherKind.Witness<L>` for `Either` with its left type `L` fixed. Its constructor is private, so nothing ever creates one: a witness appears only as a type argument.

**`Kind<F, A>`** is the interface that generic code works with. It reads "a container named by `F`, holding `A`", so `Kind<ListKind.Witness, String>` is the `Kind` view of a `List<String>`. A type class such as `Monad<F>` is written over `Kind<F, A>`, so one `Monad` interface serves every container that has a witness.

Two helpers cross between a value and its `Kind` view: `widen` takes the value into the encoding, and `narrow` brings it back. Here `flatMap` takes a `Kind` and gives one back, and only the `Either` instance behind `monad` looks inside:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/encoding/EncodingBook.java:roundtrip}}
```

`EITHER` is the helper that `EitherKindHelper` provides for `widen` and `narrow`, and each type has one, such as `MAYBE` or `LIST`. `Instances.monad(either())` is the value that knows how to `flatMap` an `Either`, and [Obtaining Instances](../functional/instances_facade.md) is the page on asking for one. [Lifting the Hood](lifting_the_hood.md) traces the same round trip call by call, with what each step allocates. The [type system glossary](../glossary/type-system.md) defines each of these terms in a line.

### The `WitnessArity` bound {#the-witnessarity-bound}

A witness also declares how many type arguments its container still needs: its *arity*. `Kind` asks for that declaration in its bound, `Kind<F extends WitnessArity<?>, A>`, so a class that is not a witness cannot stand in for `F`:

```java
// ListKind.Witness: one type argument still missing, like List<_>
final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
}

// EitherKind2.Witness: two type arguments still missing, like Either<_, _>
final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() {}
}
```

Most type classes, `Functor` and `Monad` among them, ask for a unary witness. `Either` has two type arguments, so its `Monad` fixes the left one, and `EitherKind.Witness<L>` is unary. `Bifunctor` and `Profunctor` take a binary witness, over `Kind2`. A generic method puts the same bound on its own `F`, as `totals` does in the [Foundations](foundations_intro.md) introduction: `<F extends WitnessArity<TypeArity.Unary>>`. [Type Arity](type-arity.md) covers the rest.

## The pieces each type provides {#the-pieces-each-type-provides}

Every container that joins the encoding brings the same pieces. They differ in one respect: whether the container is its own `Kind`, or is held in a small record that is.

| Piece | A type that is its own `Kind`: `Maybe` | A type held in a record: `Optional` |
|---|---|---|
| Witness | `MaybeKind.Witness` | `OptionalKind.Witness` |
| `Kind` interface | `MaybeKind<A>`, which `Maybe` itself extends | `OptionalKind<A>`, which a holder record implements |
| Helper | `MAYBE`, on `MaybeKindHelper` | `OPTIONAL`, on `OptionalKindHelper` |
| `widen` | Returns the same object: a `Maybe` already is its `Kind` | Wraps the `Optional` in the holder, one allocation |
| `narrow` | Checks the type, then casts | Checks for the holder, then returns the `Optional` it holds |
| Type class instances | `MaybeMonad`, `MaybeTraverse` and others | `OptionalMonad`, `OptionalTraverse` and others |

The JDK's types cannot implement `Kind`, so they are held, and so are two of the library's own: `Lazy` and `Tuple2`. A type that is its own `Kind` crosses into the encoding without an allocation, and a debugger shows the `Maybe` itself, not a holder. [Extending the Simulation](extending-simulation.md) shows how to give a type of your own these pieces.

## Values, never null {#values-never-null}

Higher-Kinded-J's own types hold a value, never `null`. They refuse it at the factory, and a `map` function that returns `null` fails them, so an absent value shows in the type. `Maybe` and `Id` treat `null` differently:

| Type | A `null` value | A `map` function that returns `null` |
|---|---|---|
| `Either`, `Try`, `Validated`, `EitherOrBoth`, `NonEmptyList`, `IO`, `VTask`, `Lazy`, `Reader`, `State`, `Writer`, `Free` and `Trampoline` | Refused: `Either.right(null)` and `Instances.monad(either()).of(null)` throw `NullPointerException` | Fails: `Either` throws at once, `Try` gives a `Failure`, and `IO` fails when it runs |
| `Maybe` | Read as `Nothing` by `Maybe.fromNullable` and by the instance's `of`, though `Maybe.just(null)` throws | Read as `Nothing` |
| `Id` | Held | Held |

When a step has nothing to return, return [`Unit`](#unit). When a value may be absent, hold it in a `Maybe`. The JDK containers, the transformers and the tuples have rules of their own, in [Where null is read or held](#where-null-is-read-or-held).

## `Unit`: a value for "nothing to return" {#unit}

`void` is not a type, so it cannot fill the `A` in `Kind<F, A>`. Java's own stand-in, `Void`, has only `null` as a value, which the types on this page refuse. `org.higherkindedj.hkt.Unit` has exactly one value, `Unit.INSTANCE`. An action run for its effect gives `Unit`, and a type whose failure carries no detail takes `Unit` as its error type, as `Maybe`'s [`MonadError`](../functional/monad_error.md) does:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/encoding/EncodingBook.java:unit}}
```

The source is [`Unit.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-api/src/main/java/org/higherkindedj/hkt/Unit.java).

~~~admonish tip title="You can ship now"
You can now run any type class over a value you hold: widen it, call the instance, and narrow the result, returning `Unit` or a `Maybe` where you might have returned `null`. The rest of this page is for when a `null` turns up or `narrow` throws.
~~~

---

## The fine print {#the-fine-print}

### Where null is read or held {#where-null-is-read-or-held}

Some types read `null` as empty, or hold it, by design:

- **The JDK containers' instances read `null` as empty.** `of(null)` gives an empty `Optional`, `List` or `Stream`, and a `CompletableFuture` completed with `null`.
- **`MaybeT` and `OptionalT` read `null` as `Maybe` and `Optional` do.** Their `of(null)` gives an empty inner value.
- **The tuples hold whatever they are given.** `Tuple.of(null, null)` builds a `Tuple2` of two nulls.
- **`Const` ignores the value.** It never holds one, so `of(null)` gives the monoid's empty value.

So generic code never hands `null` to `of`: what comes back depends on `F`. A `flatMap` function must return a `Kind` in every type, so one that returns `null` throws `KindUnwrapException`.

### When `narrow` throws {#when-narrow-throws}

Two kinds of failure meet in this encoding, and they are handled differently:

- **A domain failure is a value.** A `Left`, a `Nothing`, a `Try.Failure` or a failed `CompletableFuture` travels inside its container, and the type's own methods or `MonadError`'s `handleErrorWith` recover from it.
- **An encoding failure is a bug.** `narrow` throws `KindUnwrapException`, an unchecked `IllegalArgumentException`, for `null` or another container's `Kind`, as does a `flatMap` function that returns `null`. Fix the mismatch rather than catch it.

---

~~~admonish info title="Hands-On Learning"
Practise widening and narrowing in [Tutorial 01: Kind Basics](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial01_KindBasics.java) (5 exercises).
~~~

---

**Previous:** [One Line, Six Layers](one_line_six_layers.md)
**Next:** [Working with Kind](usage-guide.md)
