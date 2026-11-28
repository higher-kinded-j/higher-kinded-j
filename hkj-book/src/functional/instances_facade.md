<!-- description: Get any Higher-Kinded-J type class instance in one shape, Instances.monad(maybe()), with the compiler checking each capability you ask for. -->

# Obtaining Instances: The `Instances` Facade

_Ask one class for every type class instance, in one shape: `Instances.monad(maybe())`._

Each type class in Higher-Kinded-J has a standard instance for each type, and `org.higherkindedj.hkt.instances.Instances` is where you ask for it. Every lookup has the same shape, `Instances.x(...)`, so typing `Instances.` in an IDE lists every capability on offer.

~~~admonish info title="What You'll Learn"
- Ask `Instances` for a `Functor`, `Applicative`, `Monad` or `MonadError` with a token from `Witnesses`
- Pass the dependency an instance needs, such as a `Semigroup`, a `Monoid` or an outer `Monad`
- Read what javac reports when you ask a type for a capability its instance lacks
- Name the error type a `MonadError` really has
~~~

~~~admonish example title="See Example Code"
[InstancesFacadeBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/instances/InstancesFacadeBookTest.java) holds each claim this page makes, including that its tables match every token and every method.
~~~

---

## Zero-argument lookups

For a type whose instance needs nothing, pass its token: a method in `Witnesses`, such as `maybe()`, that names the type's witness.

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.maybe;

import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.MaybeKind;

Monad<MaybeKind.Witness>       monad       = Instances.monad(maybe());
Applicative<MaybeKind.Witness> applicative = Instances.applicative(maybe());
Functor<MaybeKind.Witness>     functor     = Instances.functor(maybe());
```

The instance implements the whole `Functor`, `Applicative`, `Monad` chain in one object, so all three calls return that same object.

Three more lookups give a richer capability, for the types whose instance has it. `MonadError` adds raising and recovering from an error; `MonadZero` adds an empty value and `filter`; `Alternative` adds an empty value and `orElse`. For `Maybe` and `Optional`, `orElse` falls back to a second value when the first is empty; for `List`, `Stream` and `VStream`, it joins the two.

<!-- verify -->
```java
MonadError<MaybeKind.Witness, Unit> me  = Instances.monadError(maybe());
MonadZero<ListKind.Witness>         mz  = Instances.monadZero(list());
Alternative<OptionalKind.Witness>   alt = Instances.alternative(optional());
```

| Lookup | Type | Error type |
|---|---|---|
| `monadError` | `Maybe`, `Optional` | `Unit` |
| `monadError` | `Try`, `CompletableFuture`, `VTask` | `Throwable` |
| `monadError` | `Either<L, _>` | `L` |
| `monadZero` | `Maybe`, `Optional`, `List`, `Stream` | |
| `alternative` | `Maybe`, `Optional`, `List`, `Stream`, `VStream` | |

The compiler checks each of these lookups against the type's instance. A type outside the table does not compile, and nor does a `MonadError` with an error type the table does not give, as [what the compiler checks](#what-the-compiler-checks) shows.

### Type parameters come from your variable {#type-inference-for-phantom-typed-types}

Each token has a generic type, `Of<F, M>`, rather than a `Class`: `F` is the type's witness, and `M` the class of its instance. Java has no generic class literals, so a `Class` would lose `Either`'s left type or `Reader`'s environment. The four tokens for types with such a parameter, `either()`, `reader()`, `context()` and `state()`, are generic methods, so the parameter comes from the variable you assign to:

<!-- verify -->
```java
// OrderError flows from the assignment target
Monad<EitherKind.Witness<OrderError>> m = Instances.monad(either());
```

### Available tokens

| Family | Tokens |
|--------|--------|
| No type parameter | `maybe()`, `io()`, `list()`, `nonEmptyList()`, `optional()`, `try_()`, `vtask()`, `vstream()`, `lazy()`, `stream()`, `completableFuture()`, `trampoline()`, `id()` |
| A type parameter | `either()`, `reader()`, `context()`, `state()` |

`try_()` ends in an underscore because `try` is a reserved word in Java.

---

## Argument-carrying instances

`Validated`, `EitherOrBoth`, `Writer` and the monad transformers each need a dependency: a `Semigroup`, which combines two values, a `Monoid`, which is a `Semigroup` with an empty value, or an outer `Monad`. None has a sensible default, so the facade asks for it in the method's parameters, where the compiler checks it and the IDE shows it:

<!-- verify -->
```java
MonadError<ValidatedKind.Witness<List<String>>, List<String>> v = Instances.validated(Semigroups.list());
Monad<EitherOrBothKind.Witness<List<String>>> eob = Instances.eitherOrBoth(Semigroups.list());
Monad<WriterKind.Witness<String>> w = Instances.writer(Monoids.string());

MonadError<EitherTKind.Witness<OptionalKind.Witness, String>, String> et =
    Instances.eitherT(Instances.monad(optional()));
MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Unit> mt =
    Instances.maybeT(Instances.monad(optional()));
MonadError<OptionalTKind.Witness<MaybeKind.Witness>, Unit> ot =
    Instances.optionalT(Instances.monad(maybe()));
Monad<ReaderTKind.Witness<OptionalKind.Witness, String>> rt =
    Instances.readerT(Instances.monad(optional()));
Monad<StateTKind.Witness<Integer, OptionalKind.Witness>> st =
    Instances.stateT(Instances.monad(optional()));
MonadWriter<WriterTKind.Witness<OptionalKind.Witness, String>, String> wt =
    Instances.writerT(Instances.monad(optional()), Monoids.string());
```

A dependency in the signature cannot be missed: `Instances.validated()` with no `Semigroup` does not compile.

~~~admonish tip title="You can ship now"
You can now get any built-in instance in one shape: a token for a type that needs nothing, an argument for one that needs a dependency. The rest of this page is for what the compiler checks, what the facade covers, and the accessors behind it.
~~~

~~~admonish question title="Checkpoint: two lookups on `IO`" id="check-facade-cast"
One of these lines compiles. Which one, and what does javac report for the other?

<!-- verify:rejects "cannot be applied to given types" -->
```java
Monad<IOKind.Witness> monad = Instances.monad(io());
Alternative<IOKind.Witness> alternative = Instances.alternative(io());
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-facade-cast-answer"
**`Instances.monad(io())` compiles. For `Instances.alternative(io())`, javac reports "method alternative in class Instances cannot be applied to given types".** Every token gives a `Monad`, and `IO`'s instance, an `IOMonad`, has no `Alternative`.

Where this lives: [Zero-argument lookups](#zero-argument-lookups).
~~~

~~~admonish question title="Checkpoint: a token for `Validated`" id="check-facade-argument"
`maybe()` is a token for `Instances.monad(maybe())`. Is there a `validated()` token, for `Instances.monad(validated())`?

<!-- verify:rejects "cannot find symbol" -->
```java
var monad = Instances.monad(validated());
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-facade-argument-answer"
**No: javac reports "cannot find symbol".** `Validated` needs a `Semigroup` to join its errors, so it has no token. The facade asks for the `Semigroup` in the method's parameters instead: `Instances.validated(Semigroups.list())`.

Where this lives: [Argument-carrying instances](#argument-carrying-instances).
~~~

---

## The fine print {#the-fine-print}

### What the compiler checks {#what-the-compiler-checks}

`Instances` is a class of static methods, with no registry, no `ServiceLoader` and nothing to configure. A token lookup returns the instance its token carries, and a lookup that takes an argument builds a new instance on each call.

- **A token's type names its instance's class.** `maybe()` is an `Of<MaybeKind.Witness, MaybeMonad>`, and `either()` an `Of<EitherKind.Witness<L>, EitherMonad<L>>`.
- **`monad`, `applicative` and `functor` accept every token.** Every instance is a `Monad`.
- **The three richer lookups accept a token whose instance has the capability.** `monadZero` takes an `Of<F, ? extends MonadZero<F>>`.
- **`monadError` reads the error type from the instance's class.** `MaybeMonad` is a `MonadError<MaybeKind.Witness, Unit>`, so `E` is `Unit`, even with `var`.

For a type whose instance lacks the capability, javac reports "method monadZero in class Instances cannot be applied to given types":

<!-- verify:rejects "cannot be applied to given types" -->
```java
MonadZero<EitherKind.Witness<String>> zero = Instances.monadZero(either());
```

For a variable declared with another error type, javac reports "inference variable E has incompatible equality constraints String,Unit", because `Maybe`'s error type is `Unit`:

<!-- verify:rejects "inference variable E has incompatible equality constraints" -->
```java
MonadError<MaybeKind.Witness, String> wrong = Instances.monadError(maybe());
```

To keep a token in a local variable, declare it with `var`, which keeps the instance's class. A field or parameter names the capability its code asks for, or `?` where only `monad`, `applicative` or `functor` reads it. Either survives a token's class changing to one with more capabilities, which naming the class would not:

<!-- verify -->
```java
var token = maybe();
MonadZero<MaybeKind.Witness> zero = Instances.monadZero(token);

Witnesses.Of<MaybeKind.Witness, ? extends MonadZero<MaybeKind.Witness>> zeroToken = maybe();
Alternative<MaybeKind.Witness> alternative = Instances.alternative(zeroToken);

Witnesses.Of<MaybeKind.Witness, ?> anyInstance = maybe();
Monad<MaybeKind.Witness> monad = Instances.monad(anyInstance);

var errors = Instances.monadError(maybe());
MonadError<MaybeKind.Witness, Unit> sameType = errors;
```

A generic method that passes a token on to a richer lookup bounds the instance the same way:

<!-- verify -->
```java
<F extends WitnessArity<TypeArity.Unary>> MonadZero<F> zeroFor(
        Witnesses.Of<F, ? extends MonadZero<F>> token) {
    return Instances.monadZero(token);
}
```

### The accessors behind the facade {#the-accessors-behind-the-facade}

Each class behind the facade has an accessor of its own, in one of four shapes. `Instances` is the one shape to use in new code:

<!-- verify -->
```java
Semigroup<String>            semi  = Semigroups.string();
Monad<OptionalKind.Witness>  outer = OptionalMonad.INSTANCE;

Monad<MaybeKind.Witness>             m1 = MaybeMonad.INSTANCE;            // a static field
Monad<EitherKind.Witness<String>>    m2 = EitherMonad.instance();         // a generic static method
MonadError<ValidatedKind.Witness<String>, String> v =
    ValidatedMonad.instance(semi);                                        // a factory with an argument
MonadError<EitherTKind.Witness<OptionalKind.Witness, String>, String> et =
    new EitherTMonad<>(outer);                                            // a constructor with an argument
```

### Scope

| Type class | Through `Instances` |
|------------|----------|
| `Functor`, `Applicative`, `Monad` | Yes, for every token |
| `MonadError`, `MonadZero`, `Alternative` | Yes, for the types in the table in [Zero-argument lookups](#zero-argument-lookups) |
| The instances that need an argument: `validated`, `eitherOrBoth`, `writer`, `eitherT`, `maybeT`, `optionalT`, `readerT`, `stateT`, `writerT` | Yes |
| `MonadWriter` | Only through `writerT` |
| `MonadReader`, `MonadState` | Not offered: `ReaderTMonadReader` and `StateTMonadState` are separate classes |
| `Traverse`, `Selective`, `Foldable` | Not offered: each is a separate instance class, such as `MaybeTraverse.INSTANCE` |
| `Free`, `FreeApplicative` | Not offered: use `new FreeMonad<>()` or `FreeApApplicative.instance()` |
| `Const` | Not offered: it has an `Applicative` and a `Bifunctor`, and a token carries a `Monad` |
| `Semigroup`, `Monoid` | Not offered: they are keyed by a value type, not a witness; use `Semigroups` and `Monoids` |
| `Bifunctor`, `Profunctor` | Not offered: they take a binary witness |

---

## See Also

- [Functor](functor.md), [Applicative](applicative.md), [Monad](monad.md), [MonadError](monad_error.md)
- [Semigroup and Monoid](semigroup_and_monoid.md)
- [Monad Transformers](../transformers/transformers.md)

---

**Previous:** [Working with Kind](../hkts/usage-guide.md)
**Next:** [Functor](functor.md)
