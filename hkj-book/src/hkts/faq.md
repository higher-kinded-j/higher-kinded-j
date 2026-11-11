<!-- description: Design answers on Higher-Kinded-J's Kind encoding: widen and narrow cost, Scala and Haskell names, and how it compares with Vavr, Cyclops and Arrow. -->

# Foundations FAQ

<!-- source: Pierre-Simon Laplace, A Philosophical Essay on Probabilities, Part I, Chapter I, "Introduction", p. 1, trans. Frederick Wilson Truscott and Frederick Lincoln Emory from the sixth French edition (New York: John Wiley & Sons, 1902); checked against Project Gutenberg #58881 -->
> _"…the most important questions of life, which are indeed for the most part only problems of probability."_
>
> — Pierre-Simon Laplace, *A Philosophical Essay on Probabilities* (Chapter I, trans. Frederick Wilson Truscott and Frederick Lincoln Emory)

---

For Laplace, most of life's important questions were problems of probability. Most questions on this page turn on one missing feature: in Java, a type parameter cannot take type parameters of its own.

| Question | Short answer |
|---|---|
| [Why can't this just be an interface `Mappable<T>`?](#why-cant-this-just-be-an-interface-mappablet) | Java cannot say "the same container" in a method's type |
| [Why two type parameters on `Kind` instead of one?](#why-two-type-parameters-on-kind-instead-of-one) | Generic code changes one and keeps the other |
| [Is the instance found for me, like a Scala implicit?](#is-the-instance-found-for-me-like-a-scala-implicit) | No: you pass it, as a value |
| [Is the witness type ever instantiated? Does it allocate?](#is-the-witness-type-ever-instantiated-does-it-allocate) | No, and no |
| [What's the runtime cost of `widen` and `narrow`?](#whats-the-runtime-cost-of-widen-and-narrow) | A check and a cast, or one small holder |
| [Why a `Holder` for some types but direct implementation for others?](#why-a-holder-for-some-types-but-direct-implementation-for-others) | A `Kind` must be a type the library owns, and immutable |
| [Why does `narrow` throw rather than return an `Either`?](#why-does-narrow-throw-rather-than-return-an-either) | A wrong `Kind` is a bug in the code, not bad data |
| [Why `WitnessArity`, as well as `Kind` and `Kind2`?](#why-are-arities-encoded-with-witnessaritytypearityunary-instead-of-separate-kindkind2-interfaces-only) | So a witness of the wrong arity does not compile |
| [`OptionalKind.Witness` or `Optional.Witness`?](#i-see-optionalkindwitness-in-some-examples-and-optionalwitness-in-others-which-is-correct) | `OptionalKind.Witness` |
| [Do the instances obey the laws?](#do-the-instances-obey-the-laws) | Yes, for values that are never `null`, bar the few listed |
| [I know Scala, Haskell or Vavr: what are the names here?](#i-know-scala-haskell-or-vavr-what-are-the-names-here) | Mostly the names Cats uses; `pure` is `of` |
| [How does this compare with Vavr, Cyclops or Arrow-Kt?](#how-does-this-compare-with-vavr-cyclops-or-arrow-kt) | Vavr has the types without type classes; Arrow dropped its `Kind` |
| [Will Project Valhalla or pattern matching make this obsolete?](#will-project-valhalla-or-pattern-matching-make-this-obsolete) | No: neither adds the missing feature |
| [What is the elevator pitch?](#what-is-the-elevator-pitch-for-someone-who-has-not-read-the-chapter) | Write a combinator once, for every container |
| [Where do I go from here?](#where-do-i-go-from-here) | The issue tracker, or Lifting the Hood |

## Why can't this just be an interface `Mappable<T>`? {#why-cant-this-just-be-an-interface-mappablet}

It can, for one container at a time. You can write `interface Mappable<T> { <U> Mappable<U> map(Function<T, U> f); }` and have your own containers implement it, though never the JDK's `Optional` or `List`, which you cannot change.

What you cannot write is a method that says "give me any `Mappable<T>` and I will return another `Mappable` of the same shape". Java has no way to spell "of the same shape", because it has no way to talk about the type constructor itself. The signature `<F<?>, A, B> F<B> map(F<A> fa, Function<A, B> f)` is not legal Java.

`Kind<F, A>` is the workaround. It moves the shape into an ordinary type parameter, the witness type `F`, so you can write one generic method that keeps the container the same across the call. Without it, every generic combinator, such as `traverse`, `sequence` or `For`, would be written again for each container the library supports.

## Why two type parameters on `Kind` instead of one? {#why-two-type-parameters-on-kind-instead-of-one}

Because generic code changes one half and keeps the other. `Kind<F, A>` reads as "container `F` holding an `A`". `map` changes the `A` and keeps the `F`; a [natural transformation](../functional/natural_transformation.md) changes the `F` and keeps the `A`. Keeping the two apart in the type lets the compiler check that each call keeps the half it should.

`F` names the container; it never chooses the instance that works on it, as the next answer says. `Kind2<F, A, B>` is the form for a type with two sides, such as `Either<L, R>` or `Function<A, B>`, and `Bifunctor` and `Profunctor` work on it.

## Is the instance found for me, like a Scala implicit? {#is-the-instance-found-for-me-like-a-scala-implicit}

No: you pass the instance, as a value. Scala finds a `Monad` for a type through an implicit or a `given`, and Haskell through instance resolution. Java has neither, so a method written once for any container takes a `Monad<F>` parameter, and its caller supplies one, such as `Instances.monad(either())`.

`either()` returns a token, a small object that carries `Either`'s instance, and `Instances.monad` hands that instance back. The token is not the witness type `EitherKind.Witness`, which never exists at run time, as the next answer says. The compiler checks only that the instance and the values share the witness. [The Instances facade](../functional/instances_facade.md) shows each call.

## Is the witness type ever instantiated? Does it allocate? {#is-the-witness-type-ever-instantiated-does-it-allocate}

No, and no. A witness, such as `MaybeKind.Witness`, is a `final` class with a private constructor, and nothing in the library creates one. It exists only as a name the compiler can write down, never as a value at run time.

## What's the runtime cost of `widen` and `narrow`? {#whats-the-runtime-cost-of-widen-and-narrow}

A check and a cast for most of the library's own types, and one small holder per `widen` for the rest:

| Types | `widen` | `narrow` |
|---|---|---|
| `Maybe`, `Either`, `Try`, `Validated`, `IO` and most of the library's own types | Checks for `null`, and returns the same object | Checks the type, and casts |
| `Lazy`, and JDK types such as `Optional`, `List`, `Stream`, `CompletableFuture` and `Function` | Allocates a small holder record | Reads the value out of the holder |

Most [Effect Paths](../effect/ch_intro.md), such as `EitherPath`, are not a `Kind` at all: each holds its value and calls it directly, so code on one pays for neither.

The type class call around them usually costs more: `EitherMonad.flatMap` allocates a lambda on every call. [What each step allocates](lifting_the_hood.md#what-each-step-allocates) follows one call and counts its allocations, and [Benchmarks](../benchmarks.md) has the measurements. If the cost matters in a hot loop, prefer the library type, such as `Maybe` over `Optional`.

## Why a `Holder` for some types but direct implementation for others? {#why-a-holder-for-some-types-but-direct-implementation-for-others}

Because a type can implement `Kind` only where the library owns it, and a `Kind` must be immutable. `java.util.Optional` is a final class in the JDK, so no library can add an interface to it. `Lazy` is the library's own, but it remembers its value in fields that change once it is worked out, so it uses a holder too.

The instances never see the difference. They call `widen` and `narrow` on the type's helper, and the helper knows which kind of type it holds.

## Why does `narrow` throw rather than return an `Either`? {#why-does-narrow-throw-rather-than-return-an-either}

Because a wrong `Kind` is a bug in the code, not bad data. `narrow` checks a structural invariant, not a domain condition. If a `Kind<OptionalKind.Witness, String>` reaches `OPTIONAL.narrow`, the `Optional` helper's `narrow`, and is not an `Optional` underneath, the code that built it is wrong.

A returned `Either` would push that bug into the value layer and force every caller to handle it. An exception keeps it off the success path, and fails loudly where the invariant broke. `KindUnwrapException` is unchecked on purpose: catching it in application code almost always hides a deeper problem. [When `narrow` throws](core-concepts.md#when-narrow-throws) lists the cases.

## Why `WitnessArity`, as well as `Kind` and `Kind2`? {#why-are-arities-encoded-with-witnessaritytypearityunary-instead-of-separate-kindkind2-interfaces-only}

So that a witness of the wrong arity does not compile. `Kind` and `Kind2` are separate interfaces, but `Kind` accepts a witness of any arity. The type classes bound theirs: `Functor<F extends WitnessArity<TypeArity.Unary>>` takes only a one-parameter witness, and `Bifunctor<F extends WitnessArity<TypeArity.Binary>>` only a two-parameter one.

Give `Functor` a binary witness, such as `EitherKind2.Witness`, and javac stops at the bound:

<!-- verify:rejects "is not within bounds of type-variable" -->
```java
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.either.EitherKind2;

Functor<EitherKind2.Witness> functor;
```

The type argument `is not within bounds of type-variable F`, javac says. Nothing at run time looks at a witness's arity, as [What the compiler refuses](type-arity.md#what-the-compiler-refuses) says.

## `OptionalKind.Witness` or `Optional.Witness`? {#i-see-optionalkindwitness-in-some-examples-and-optionalwitness-in-others-which-is-correct}

`OptionalKind.Witness`. The witness is a nested final class on the `OptionalKind` interface, since the library cannot add one to `java.util.Optional`. `Optional.Witness` does not exist; read it as a typo wherever you meet it. Every type follows the same rule: the witness lives on `XxxKind`, never on the value type.

## Do the instances obey the laws? {#do-the-instances-obey-the-laws}

Yes, for values that are never `null`, bar the few instances listed. [Laws and Where HKJ Bends Them](laws.md) names each instance that bends a law, such as `Validated`'s `ap` and `Try`'s `flatMap`, with what to do instead. It also says which laws `hkj-test` checks on an instance of your own.

## I know Scala, Haskell or Vavr: what are the names here? {#i-know-scala-haskell-or-vavr-what-are-the-names-here}

Mostly the names Cats uses, as methods of a type class you pass as a value; `pure` is `of`, and a natural transformation is a `Natural`. The last column names each method with the type, or the type class, it belongs to. "None" means that library or language has no such name.

| Scala (Cats) | Haskell | Vavr | Higher-Kinded-J |
|---|---|---|---|
| `F[_]` | kind `Type -> Type` | None | `Kind<F, A>`, with a witness `F` |
| An `implicit` or `given` | Instance resolution | None | A value you pass, from `Instances` |
| `pure` | `pure`, `return` | `Option.of` and other factories | `Applicative`: `of` |
| `map` | `fmap`, `<$>` | `map` on each type | `Functor`: `map` |
| `flatMap` | `>>=` | `flatMap` on each type | `Monad.flatMap`, or `EitherPath.via` on a Path |
| `ap`, `mapN` | `<*>`, `liftA2` | `combine` on `Validation` | `Applicative`: `ap`, `map2` to `map5` |
| `traverse`, `sequence` | `traverse`, `sequenceA` | Static `traverse`, `sequence` | `Traverse`: `traverse`, `sequenceA` |
| `FunctionK`, `~>` | A function from `f x` to `g x`, for every `x` | None | [`Natural<F, G>`](../functional/natural_transformation.md) |
| `for` … `yield` | `do` | `API.For` | `For.from`, or `ForPath` |
| `MonadError`: `raiseError`, `handleErrorWith` | `MonadError` (mtl): `throwError`, `catchError` | `Try`: `recover`, `recoverWith` | `MonadError`: `raiseError`, `handleErrorWith` |
| `<+>`, `empty` | `<\|>`, `empty` | `orElse` on each type | `Alternative`: `orElse`, `empty` |
| `Bifunctor`: `bimap` | `bimap` | `Either`: `bimap` | [`Bifunctor`](../functional/bifunctor.md): `bimap` |
| `Stateful` (cats-mtl) | `MonadState` (mtl) | None | [`MonadState`](../transformers/mtl_state.md) |
| `Either` | `Either` | `Either` | [`Either`](../monads/either_monad.md), or `EitherPath` |
| `Option` | `Maybe` | `Option` | [`Maybe`](../monads/maybe_monad.md), or `MaybePath` |
| `Try` | None; `try` gives an `Either` | `Try` | [`Try`](../monads/try_monad.md), or `TryPath` |
| `Validated`, `ValidatedNel` | `Validation` (validation) | `Validation` | [`Validated`](../monads/validated_monad.md), and `validNel` for the `NonEmptyList` form |
| `Ior` | `These` (these) | None | [`EitherOrBoth`](../monads/either_or_both_monad.md) |
| `NonEmptyList` | `NonEmpty` | None | [`NonEmptyList`](../monads/nonemptylist_monad.md) |
| `Id` | `Identity` | None | [`Id`](../monads/identity.md) |
| `Eval` | None: evaluation is lazy already | `Lazy` | [`Lazy`](../monads/lazy_monad.md), or [`Trampoline`](../monads/trampoline_monad.md) for deep recursion |
| `IO` (Cats Effect) | `IO` | None | [`IO`](../monads/io_monad.md), or [`VTask`](../monads/vtask_monad.md) on virtual threads |
| `EitherT` | `ExceptT` | None | [`EitherT`](../transformers/eithert_transformer.md) |
| `OptionT` | `MaybeT` | None | [`MaybeT`](../transformers/maybet_transformer.md), [`OptionalT`](../transformers/optionalt_transformer.md) |
| `Kleisli`, `ReaderT` | `Reader`, `ReaderT` | None | [`Reader`](../monads/reader_monad.md), [`ReaderT`](../transformers/readert_transformer.md) |
| `State`, `StateT` | `State`, `StateT` | None | [`State`](../monads/state_monad.md), [`StateT`](../transformers/statet_transformer.md) |
| `Writer`, `WriterT` | `Writer`, `WriterT` | None | [`Writer`](../monads/writer_monad.md), [`WriterT`](../transformers/writert_transformer.md) |
| `Free` (cats-free) | `Free` (free) | None | [`Free`](../monads/free_monad.md), run with `Free.foldMap` |
| `FreeApplicative` (cats-free) | `Ap` (free) | None | [`FreeAp`](../monads/free_applicative.md) |
| `EitherK` | `Sum` | None | [`EitherF`](../monads/eitherf.md) |
| `Coyoneda` (cats-free) | `Coyoneda` (kan-extensions) | None | [`Coyoneda`](../monads/coyoneda.md) |
| `Semigroup`, `Monoid` | `Semigroup`, `Monoid` | None | [`Semigroup`, `Monoid`](../functional/semigroup_and_monoid.md) |
| `Const` | `Const` | None | [`Const`](../monads/const_type.md) |

The names were checked against Cats 2.13 and Cats Effect 3, the Haskell packages the rows name (`base` where none is named), and Vavr 1.1. A test checks every name in the last column against the library.

## How does this compare with Vavr, Cyclops or Arrow-Kt? {#how-does-this-compare-with-vavr-cyclops-or-arrow-kt}

Vavr has the types without the type classes, Cyclops has type classes but no release since 2022, and Arrow removed its `Kind` encoding:

| Library | What it offers | Where it stands |
|---|---|---|
| Vavr | Concrete types, such as `Option`, `Either`, `Try`, `Validation` and `Lazy`, and persistent collections, with no type classes: code is written against each type separately | For one or two functional types in an otherwise ordinary codebase, often the simpler choice |
| Cyclops | Higher-kinded type classes, in its `cyclops-pure` module | Released 10.4.1 in May 2022 |
| Arrow, for Kotlin | Concrete types, after a `Kind` encoding much like this one | Deprecated the encoding in 0.12 and removed it in 0.13, both in March 2021, since "most relevant APIs are now concrete over the data types", as its notice said |

Higher-Kinded-J draws the same lesson. Application code holds an Effect Path, a concrete type that never names `Kind`, and the encoding serves code written once for any container. It is not a port of any of these libraries.

## Will Project Valhalla or pattern matching make this obsolete? {#will-project-valhalla-or-pattern-matching-make-this-obsolete}

No: neither adds the feature the encoding stands in for. Project Valhalla's value objects, in [JEP 401](https://openjdk.org/jeps/401), have no identity. A holder declared as one might be flattened rather than allocated, so `widen` of a JDK type could cost less, but the encoding stays.

Pattern matching over sealed types makes `fold`-style code shorter, and it leaves the missing feature where it was. No JEP yet proposes higher-kinded types. If Java ever gains them, code that names `Kind`, `widen` and `narrow` would change. Code that holds an Effect Path never names them, so it would change least.

## What is the elevator pitch? {#what-is-the-elevator-pitch-for-someone-who-has-not-read-the-chapter}

> Java cannot write a method over "any container `F` of `A`", since it cannot name a container apart from what it holds. Higher-Kinded-J stands in with a marker type `F`, an interface `Kind<F, A>` that ties it to the value type, and small helpers that convert between `Kind` and the real Java type. You write a generic combinator once, and it works across `Optional`, `Either`, `IO` and every other type the library supports, while application code holds an Effect Path and never sees `Kind`.

If your listener asks why bother, [One Line, Six Layers](one_line_six_layers.md) makes the case in one expression.

## Where do I go from here? {#where-do-i-go-from-here}

If your question is not on this page, ask it on the [GitHub issue tracker](https://github.com/higher-kinded-j/higher-kinded-j/issues).

For mechanism rather than design, [Lifting the Hood](lifting_the_hood.md) follows one `flatMap` call from your code down to `widen` and `narrow` and back.

---

**Previous:** [Quick Reference](quick_reference.md)
**Next:** [Example Index](hkt_basic_examples.md)
