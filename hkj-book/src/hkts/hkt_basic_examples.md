<!-- description: Every runnable Higher-Kinded-J basic example, from Either, Maybe and IO to State and the monad transformers, with what each shows and its page. -->

# Example Index

_Every runnable example in `org.higherkindedj.example.basic`, with what it shows and the page that explains it._

Each example is a class with a `main` method in the [`hkj-examples` module](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/basic). Open the class in your IDE and run it, or run it from the command line:

```bash
./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.either.EitherExample
```

A test checks that every runnable class in the package has a row. The build compiles these examples; the code on the book's own pages, in `org.higherkindedj.example.book`, is tested as well.

## Failure {#failure}

| Example | What it shows | Page |
|---|---|---|
| [`EitherExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either/EitherExample.java) | `Right` and `Left`, a chain of `flatMap`s that stops at the first `Left`, `fold`, and recovery with `handleErrorWith` | [Either](../monads/either_monad.md) |
| [`EitherOrBothExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/eitherorboth/EitherOrBothExample.java) | A `Left`, a `Right`, or `Both` at once: a result that carries warnings | [EitherOrBoth](../monads/either_or_both_monad.md) |
| [`NonEmptyListExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/nonemptylist/NonEmptyListExample.java) | A list that always holds at least one element | [NonEmptyList](../monads/nonemptylist_monad.md) |
| [`TryExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/trymonad/TryExample.java) | `Success` and `Failure`, `flatMap`, which passes a `Failure` through, and recovery with `recover` and `recoverWith` | [Try](../monads/try_monad.md) |
| [`TryAttemptExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/trymonad/TryAttemptExample.java) | `Try.attempt` around Java methods that throw checked exceptions | [Try](../monads/try_monad.md) |
| [`ValidatedMonadExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/validated/ValidatedMonadExample.java) | `Valid` and `Invalid`, `flatMap` stopping at the first `Invalid`, and recovery with `handleErrorWith` | [Validated](../monads/validated_monad.md) |
| [`ValidatedAssemblyExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/validated/ValidatedAssemblyExample.java) | A record built from fields validated one by one, with every error kept in field order | [Accumulating Assembly](../monads/validated_assembly.md) |
| [`GeneratedAssemblyExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/validated/GeneratedAssemblyExample.java) | The companion that `@GenerateAssembly` generates, with one method per component | [Accumulating Assembly](../monads/validated_assembly.md) |

## Absence {#absence}

| Example | What it shows | Page |
|---|---|---|
| [`MaybeExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/maybe/MaybeExample.java) | `Just` and `Nothing`, `map`, `flatMap`, and recovery from `Nothing` with `handleErrorWith` | [Maybe](../monads/maybe_monad.md) |
| [`OptionalExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/optional/OptionalExample.java) | Java's `Optional` through the `Kind` encoding: `map`, `flatMap`, and a default with `handleErrorWith` | [Optional](../monads/optional_monad.md) |

## Deferred {#deferred}

| Example | What it shows | Page |
|---|---|---|
| [`IOExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/io/IOExample.java) | Console effects described with `delay`, composed with `map` and `flatMap`, and run with `unsafeRunSync` | [IO](../monads/io_monad.md) |
| [`LazyExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/lazy/LazyExample.java) | A computation deferred until `force`, then kept, so that it runs once | [Lazy](../monads/lazy_monad.md) |
| [`CompletableFutureExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/future/CompletableFutureExample.java) | `map` and `flatMap` over futures, and a failed future recovered with `handleErrorWith` | [CompletableFuture](../monads/cf_monad.md) |

## Virtual threads {#virtual-threads}

| Example | What it shows | Page |
|---|---|---|
| [`VStreamBasicExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/vstream/VStreamBasicExample.java) | Building a stream, transforming it and running it, its laziness, an effectful `unfold`, and recovery | [VStream](../monads/vstream.md) |
| [`VStreamHKTExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/vstream/VStreamHKTExample.java) | VStream's instances: code written for any `Monad`, `Foldable`'s `foldMap`, and `Alternative`'s `orElse` and `guard` | [HKT and Type Classes](../monads/vstream_hkt.md) |
| [`VStreamAdvancedExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/vstream/VStreamAdvancedExample.java) | `bracket` and `onFinalize`, `StreamTraversal`, `Flow.Publisher`, and natural transformations between `List` and `VStream` | [Advanced Features](../monads/vstream_advanced.md) |

## Environment and state {#environment-and-state}

| Example | What it shows | Page |
|---|---|---|
| [`ReaderExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader/ReaderExample.java) | Values read from a configuration, chained with `flatMap` and run with `runReader` | [Reader](../monads/reader_monad.md) |
| [`StateExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state/StateExample.java) | A stack's `push` and `pop` as `State` steps | [State](../monads/state_monad.md) |
| [`BankAccountWorkflow`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state/BankAccountWorkflow.java) | Deposits and withdrawals threaded through `State` and composed with `For` | [State](../monads/state_monad.md) |
| [`WriterExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/writer/WriterExample.java) | A log kept beside the result with `tell`, and joined across `flatMap` | [Writer](../monads/writer_monad.md) |

## Collections {#collections}

| Example | What it shows | Page |
|---|---|---|
| [`ListMonadExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/list/ListMonadExample.java) | `map` and `flatMap` on a `List`, with a step that gives several results or none | [List](../monads/list_monad.md) |
| [`StreamExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/StreamExample.java) | Java's `Stream` through the type classes: `map`, `flatMap`, taking and dropping, and laziness | [Stream](../monads/stream_monad.md) |
| [`PCollectionsExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/pcollections/PCollectionsExample.java) | PCollections' persistent lists through `ListKind`, since each is a `java.util.List` | [PCollections Integration](../tooling/pcollections_integration.md) |

## Programs as data {#programs-as-data}

| Example | What it shows | Page |
|---|---|---|
| [`IdExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/id/IdExample.java) | `Id`, which adds nothing: `map`, `flatMap`, and `StateT` over `Id` | [Identity](../monads/identity.md) |
| [`TrampolineExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/trampoline/TrampolineExample.java) | Recursion that does not overflow the stack | [Trampoline](../monads/trampoline_monad.md) |
| [`FreeApplicativeExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/free_ap/FreeApplicativeExample.java) | A Free Applicative program built and then interpreted | [Free Applicative](../monads/free_applicative.md) |
| [`StaticAnalysisExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/free_ap/StaticAnalysisExample.java) | A Free Applicative program read before it runs | [Free Applicative](../monads/free_applicative.md) |
| [`PermissionCheckingExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/free_ap/PermissionCheckingExample.java) | The permissions a program of file operations needs, checked before it runs | [Free Applicative](../monads/free_applicative.md) |
| [`ParallelDataFetchExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/free_ap/ParallelDataFetchExample.java) | Independent fetches held as one program, which an interpreter could run in parallel | [Free Applicative](../monads/free_applicative.md) |
| [`CoyonedaExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/coyoneda/CoyonedaExample.java) | `map`s held by Coyoneda and applied together by `lower` | [Coyoneda](../monads/coyoneda.md) |
| [`ConstExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/constant/ConstExample.java) | `Const`, which holds one value that `second` leaves alone, and its `Bifunctor` | [Const](../monads/const_type.md) |

## Type classes {#type-classes}

| Example | What it shows | Page |
|---|---|---|
| [`GenericExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/GenericExample.java) | One function written for any `Functor`, called on a `List` and an `Optional` | [Working with Kind](../hkts/usage-guide.md) |
| [`InstancesFacadeExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/InstancesFacadeExample.java) | Instances fetched through the `Instances` facade | [Obtaining Instances](../functional/instances_facade.md) |
| [`AlternativeConfigExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/alternative/AlternativeConfigExample.java) | Configuration read from the first source that has it, with `Alternative` | [Alternative](../functional/alternative.md) |
| [`BifunctorExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/bifunctor/BifunctorExample.java) | `bimap`, `first` and `second` on two-sided types | [Bifunctor](../functional/bifunctor.md) |
| [`ProfunctorExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/profunctor/ProfunctorExample.java) | `lmap`, `rmap` and `dimap` adapting a function's input and output | [Profunctor](../functional/profunctor.md) |
| [`FoldableExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/foldable/FoldableExample.java) | One structure reduced several ways, with `foldMap` and different `Monoid`s | [Foldable and Traverse](../functional/foldable_and_traverse.md) |
| [`OptionalMonoidExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/monoid/OptionalMonoidExample.java) | Data aggregated with `firstOptional`, `maximum` and `minimum` | [Semigroup and Monoid](../functional/semigroup_and_monoid.md) |
| [`UpdateMonoidExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/monoid/UpdateMonoidExample.java) | Named `Update`s composed with `Monoids.update()` and folded | [Semigroup and Monoid](../functional/semigroup_and_monoid.md) |
| [`NaturalTransformationExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/natural/NaturalTransformationExample.java) | Natural transformations from one container to another | [Natural Transformation](../functional/natural_transformation.md) |

## For comprehensions {#for-comprehensions}

| Example | What it shows | Page |
|---|---|---|
| [`ForComprehensionExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/ForComprehensionExample.java) | `For` over several monads, among them `List`, `Maybe` and `StateT`: `from`, `when`, `let` and `yield` | [For Comprehension](../functional/for_comprehension.md) |
| [`ForTraverseComprehensionExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/ForTraverseComprehensionExample.java) | `traverse`, `sequence` and `flatTraverse` inside `For` and `ForPath` | [For Comprehension](../functional/for_comprehension.md) |
| [`ForStateExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/ForStateExample.java) | `ForState` workflows that update their state through lenses | [ForState Comprehension](../functional/forstate_comprehension.md) |
| [`MTLCapabilitiesExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/MTLCapabilitiesExample.java) | `MonadReader`, `MonadState` and `MonadWriter`, each in a function written against that capability alone | [MTL Capabilities](../transformers/mtl_capabilities.md) |
| [`EnhancedOpticsIntegrationExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/EnhancedOpticsIntegrationExample.java) | `ForState` steps through traversals and `Iso`s, and `For`'s `through`, on a department's records | [ForState Comprehension](../functional/forstate_comprehension.md) |

## Monad transformers {#monad-transformers}

| Example | What it shows | Page |
|---|---|---|
| [`EitherTExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either_t/EitherTExample.java) | Validation with `Either` over asynchronous `CompletableFuture` steps | [EitherT](../transformers/eithert_transformer.md) |
| [`MaybeTExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/maybe_t/MaybeTExample.java) | Asynchronous lookups that may find nothing: a user, then their preferences | [MaybeT](../transformers/maybet_transformer.md) |
| [`OptionalTExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/optional_t/OptionalTExample.java) | Asynchronous lookups with `Optional`: a user, their profile, then their preferences, with a default | [OptionalT](../transformers/optionalt_transformer.md) |
| [`ReaderTExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTExample.java) | The four ways to build a `ReaderT`: `of`, `liftF`, `reader` and `ask` | [ReaderT](../transformers/readert_transformer.md) |
| [`ReaderTAsyncExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncExample.java) | An asynchronous workflow that reads its configuration, run against two configurations | [ReaderT](../transformers/readert_transformer.md) |
| [`ReaderTAsyncUnitExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncUnitExample.java) | An initialisation step that reads its configuration and gives `Unit` | [ReaderT](../transformers/readert_transformer.md) |
| [`StateTExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state_t/StateTExample.java) | `StateT` over `Optional`: building one, `map` and `flatMap`, and a negative starting state that gives `Optional.empty()` | [StateT](../transformers/statet_transformer.md) |
| [`StateTStackExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state_t/StateTStackExample.java) | A stack over `Optional`, where popping an empty stack gives `Optional.empty()` | [StateT](../transformers/statet_transformer.md) |
| [`WriterTExample`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/writer_t/WriterTExample.java) | `WriterT` over `Id`: `tell`, `listen` and `censor` | [WriterT](../transformers/writert_transformer.md) |

For Effect Path, optics and whole-application examples, see the [Examples Gallery](../examples/ch_intro.md).

---

**Previous:** [Laws and Where HKJ Bends Them](laws.md)
