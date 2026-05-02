# Transformers at a Glance

A one-page reference card for every monad transformer in Higher-Kinded-J. Use this page to locate a factory, an operation, or the equivalent Path type without reading prose.

~~~admonish info title="What You'll Learn"
- The signature, witness, and primary use case for each transformer
- The equivalent Effect Path type for readers crossing between APIs
- The matching MTL capability when one exists
- The smallest meaningful example for each transformer
~~~

---

## The Family

| Transformer | Wraps | MonadError? | Path Type | MTL Capability |
|-------------|-------|-------------|-----------|----------------|
| `EitherT<F, L, R>`   | `Kind<F, Either<L, R>>` | yes (`L`)    | [`EitherPath<E, A>`](../effect/path_either.md) | -- |
| `OptionalT<F, A>`    | `Kind<F, Optional<A>>`  | yes (`Unit`) | [`OptionalPath<A>`](../effect/path_optional.md) | -- |
| `MaybeT<F, A>`       | `Kind<F, Maybe<A>>`     | yes (`Unit`) | [`MaybePath<A>`](../effect/path_maybe.md) | -- |
| `ReaderT<F, R, A>`   | `R -> Kind<F, A>`       | no           | [`ReaderPath<R, A>`](../effect/advanced_effects.md) | [`MonadReader`](mtl_reader.md) |
| `StateT<S, F, A>`    | `S -> Kind<F, (S, A)>`  | no           | [`WithStatePath<S, A>`](../effect/advanced_effects.md) | [`MonadState`](mtl_state.md) |
| `WriterT<F, W, A>`   | `Kind<F, Pair<A, W>>`   | no           | [`WriterPath<W, A>`](../effect/advanced_effects.md) | [`MonadWriter`](mtl_writer.md) |

`F` is always the outer monad witness. The transformer adds its own effect on top of whatever `F` provides (async, optional, error, identity).

---

## Factory Methods

Every transformer exposes a small set of static factories. The names follow a consistent shape: `lift` for outer-monad values, `fromKind` for already-nested values, and a primitive constructor for each effect-specific case.

### EitherT

| Factory | Produces |
|---------|----------|
| `EitherT.right(monad, value)`        | `F<Right(value)>` |
| `EitherT.left(monad, error)`         | `F<Left(error)>` |
| `EitherT.fromEither(monad, either)`  | `F<either>` |
| `EitherT.liftF(monad, fa)`           | `F<Right(a)>` from `F<A>` |
| `EitherT.fromKind(fEither)`          | wraps an existing `Kind<F, Either<L, R>>` |

### OptionalT

| Factory | Produces |
|---------|----------|
| `OptionalT.some(monad, value)`           | `F<Optional.of(value)>` |
| `OptionalT.none(monad)`                  | `F<Optional.empty()>` |
| `OptionalT.fromOptional(monad, opt)`     | `F<opt>` |
| `OptionalT.liftF(monad, fa)`             | `F<Optional.ofNullable(a)>` from `F<A>` |
| `OptionalT.fromKind(fOptional)`          | wraps an existing `Kind<F, Optional<A>>` |

### MaybeT

| Factory | Produces |
|---------|----------|
| `MaybeT.just(monad, value)`              | `F<Just(value)>` |
| `MaybeT.nothing(monad)`                  | `F<Nothing>` |
| `MaybeT.fromMaybe(monad, maybe)`         | `F<maybe>` |
| `MaybeT.liftF(monad, fa)`                | `F<Maybe.fromNullable(a)>` from `F<A>` |
| `MaybeT.fromKind(fMaybe)`                | wraps an existing `Kind<F, Maybe<A>>` |

### ReaderT

| Factory | Produces |
|---------|----------|
| `ReaderT.of(r -> fa)`                    | from `R -> Kind<F, A>` |
| `ReaderT.lift(monad, fa)`                | environment ignored, returns `F<A>` |
| `ReaderT.reader(monad, r -> a)`          | result lifted into `F` |
| `ReaderT.ask(monad)`                     | the environment as the value |

### StateT

| Factory | Produces |
|---------|----------|
| `StateT.create(s -> fStateTuple, monad)` | from a state-transition function |
| `StateTKindHelper.stateT(...)`           | helper that returns the `Kind<>` form directly |

### WriterT

| Factory | Produces |
|---------|----------|
| `WriterT.of(monad, monoid, value)`       | `F<Pair(value, empty)>` |
| `WriterT.tell(monad, w)`                 | `F<Pair(Unit, w)>` |
| `WriterT.writer(monad, value, w)`        | explicit value and output |
| `WriterT.liftF(monad, monoid, fa)`       | `F<Pair(a, empty)>` from `F<A>` |
| `WriterT.fromKind(fPair)`                | wraps an existing `Kind<F, Pair<A, W>>` |

---

## Key Operations

| Transformer | Operations beyond `of`/`map`/`flatMap` |
|-------------|-----------------------------------------|
| `EitherT`   | `raiseError(err)`, `handleErrorWith(handler)` |
| `OptionalT` | `raiseError(Unit.INSTANCE)`, `handleErrorWith(handler)` |
| `MaybeT`    | `raiseError(Unit.INSTANCE)`, `handleErrorWith(handler)` |
| `ReaderT`   | `ask`, `local(f)` |
| `StateT`    | `get`, `put(s)`, `modify(f)`, `gets(f)`, `inspect(f)` |
| `WriterT`   | `tell(w)`, `listen(ma)`, `pass(ma)`, `listens(f, ma)`, `censor(f, ma)` |

Every transformer supports `mapT(f)` to change the outer monad without touching the inner effect. Note that `StateT.mapT` requires an extra `Monad<G>` parameter because `StateT` stores its monad instance internally.

---

## Smallest Meaningful Example

Each example below is the shortest snippet that exercises the transformer's primary effect.

### EitherT

```java
var monad   = new EitherTMonad<CompletableFutureKind.Witness, String>(CompletableFutureMonad.INSTANCE);
var success = EitherT.right(CompletableFutureMonad.INSTANCE, 42);
var failed  = EitherT.left(CompletableFutureMonad.INSTANCE, "oops");
```

### OptionalT

```java
var monad   = new OptionalTMonad<CompletableFutureKind.Witness>(CompletableFutureMonad.INSTANCE);
var present = OptionalT.some(CompletableFutureMonad.INSTANCE, 42);
var absent  = OptionalT.none(CompletableFutureMonad.INSTANCE);
```

### MaybeT

```java
var monad = new MaybeTMonad<CompletableFutureKind.Witness>(CompletableFutureMonad.INSTANCE);
var just  = MaybeT.just(CompletableFutureMonad.INSTANCE, 42);
var none  = MaybeT.nothing(CompletableFutureMonad.INSTANCE);
```

### ReaderT

```java
var monad  = new ReaderTMonad<CompletableFutureKind.Witness, AppConfig>(CompletableFutureMonad.INSTANCE);
var reader = ReaderT.<CompletableFutureKind.Witness, AppConfig, String>reader(
    CompletableFutureMonad.INSTANCE,
    config -> config.dbUrl());
```

### StateT

```java
var optMonad = OptionalMonad.INSTANCE;
var stMonad  = StateTMonad.<Integer, OptionalKind.Witness>instance(optMonad);
var counter  = StateT.create(
    (Integer s) -> OPTIONAL.widen(Optional.of(StateTuple.of(s + 1, s))),
    optMonad);
```

### WriterT

```java
var listMonoid = Monoids.list();
var monad      = new WriterTMonad<IdKind.Witness, List<String>>(IdMonad.instance(), listMonoid);
var logged     = WriterT.tell(IdMonad.instance(), List.of("started"));
```

---

## When to Use the Transformer Instead of the Path Type

| Path Type | Reach for the Transformer When |
|-----------|--------------------------------|
| `EitherPath<E, A>`    | The outer monad must be `CompletableFuture`, `IO`, `VTask`, or a custom monad rather than the Path's default |
| `MaybePath<A>` / `OptionalPath<A>` | Same as above, plus you need to combine absence with another effect |
| `ReaderPath<R, A>`    | You need environment threading inside an explicit async or error monad |
| `WithStatePath<S, A>` | You need stateful threading combined with another effect |
| All of the above      | You are writing polymorphic library code that names an MTL capability |

If none of those apply, the corresponding Path type is simpler to read and write.

---

~~~admonish tip title="See Also"
- [Quickstart](quickstart.md) -- three runnable examples in 150 lines
- [Stack Archetypes](archetypes.md) -- named patterns for common composition problems
- [Migration Cookbook](migration_cookbook.md) -- imperative and Path translations
- [Monad Transformers](transformers.md) -- the underlying mechanics
~~~

---

**Previous:** [Quickstart](quickstart.md)
**Next:** [Migration Cookbook](migration_cookbook.md)
