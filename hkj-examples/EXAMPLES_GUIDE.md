# Higher-Kinded-J Examples Guide

This guide provides an overview of all runnable examples in the `hkj-examples` module, organised by functionality. Each example demonstrates specific concepts from the Higher-Kinded-J library.

## Table of Contents

- [Getting Started](#getting-started)
- [Running Examples](#running-examples)
- [Basic HKT Examples](#basic-hkt-examples)
  - [Core Types](#core-types)
  - [Monad Transformers](#monad-transformers)
  - [Type Classes](#type-classes)
- [Optics Examples](#optics-examples)
  - [Core Optics](#core-optics)
  - [Traversals](#traversals)
  - [Extensions and Fluent API](#extensions-and-fluent-api)
  - [Cookbook Recipes](#cookbook-recipes)
- [Complete Application Examples](#complete-application-examples)
- [Tutorials](#tutorials)

---

## Getting Started

Before running examples, verify your setup is correctly configured:

```bash
./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.tutorials.TutorialGettingStarted
```

| Example | Description | Documentation |
|---------|-------------|---------------|
| [TutorialGettingStarted.java](src/main/java/org/higherkindedj/example/tutorials/TutorialGettingStarted.java) | Verifies your Higher-Kinded-J setup is configured correctly | [Tutorials Introduction](https://higher-kinded-j.github.io/latest/tutorials/tutorials_intro.html) |

---

## Running Examples

All examples can be run using Gradle with the following command pattern:

```bash
./gradlew :hkj-examples:run -PmainClass=<fully.qualified.ClassName>
```

For example:

```bash
./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.maybe.MaybeExample
```

---

## Basic HKT Examples

### Core Types

Examples demonstrating fundamental monadic types and their operations.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [MaybeExample.java](src/main/java/org/higherkindedj/example/basic/maybe/MaybeExample.java) | Demonstrates the Maybe monad for optional value handling | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.maybe.MaybeExample` | [Maybe Monad](https://higher-kinded-j.github.io/latest/monads/maybe_monad.html) |
| [EitherExample.java](src/main/java/org/higherkindedj/example/basic/either/EitherExample.java) | Shows Either for error handling with typed errors | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.either.EitherExample` | [Either Monad](https://higher-kinded-j.github.io/latest/monads/either_monad.html) |
| [OptionalExample.java](src/main/java/org/higherkindedj/example/basic/optional/OptionalExample.java) | Demonstrates Optional monad integration | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.optional.OptionalExample` | [Optional Monad](https://higher-kinded-j.github.io/latest/monads/optional_monad.html) |
| [TryExample.java](src/main/java/org/higherkindedj/example/basic/trymonad/TryExample.java) | Shows Try monad for exception handling | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.trymonad.TryExample` | [Try Monad](https://higher-kinded-j.github.io/latest/monads/try_monad.html) |
| [ValidatedMonadExample.java](src/main/java/org/higherkindedj/example/basic/validated/ValidatedMonadExample.java) | Demonstrates Validated for accumulating errors | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.validated.ValidatedMonadExample` | [Validated Monad](https://higher-kinded-j.github.io/latest/monads/validated_monad.html) |
| [IdExample.java](src/main/java/org/higherkindedj/example/basic/id/IdExample.java) | Shows the Identity monad | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.id.IdExample` | [Identity](https://higher-kinded-j.github.io/latest/monads/identity.html) |
| [IOExample.java](src/main/java/org/higherkindedj/example/basic/io/IOExample.java) | Demonstrates IO monad for side effect management | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.io.IOExample` | [IO Monad](https://higher-kinded-j.github.io/latest/monads/io_monad.html) |
| [LazyExample.java](src/main/java/org/higherkindedj/example/basic/lazy/LazyExample.java) | Shows deferred computation with Lazy | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.lazy.LazyExample` | [Lazy Monad](https://higher-kinded-j.github.io/latest/monads/lazy_monad.html) |
| [ListMonadExample.java](src/main/java/org/higherkindedj/example/basic/list/ListMonadExample.java) | Demonstrates List monad operations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.list.ListMonadExample` | [List Monad](https://higher-kinded-j.github.io/latest/monads/list_monad.html) |
| [StreamExample.java](src/main/java/org/higherkindedj/example/basic/StreamExample.java) | Shows Stream monad operations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.StreamExample` | [Stream Monad](https://higher-kinded-j.github.io/latest/monads/stream_monad.html) |
| [ReaderExample.java](src/main/java/org/higherkindedj/example/basic/reader/ReaderExample.java) | Demonstrates Reader monad for dependency injection | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.reader.ReaderExample` | [Reader Monad](https://higher-kinded-j.github.io/latest/monads/reader_monad.html) |
| [WriterExample.java](src/main/java/org/higherkindedj/example/basic/writer/WriterExample.java) | Shows Writer monad for logging | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.writer.WriterExample` | [Writer Monad](https://higher-kinded-j.github.io/latest/monads/writer_monad.html) |
| [StateExample.java](src/main/java/org/higherkindedj/example/basic/state/StateExample.java) | Demonstrates State monad for stateful computations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.state.StateExample` | [State Monad](https://higher-kinded-j.github.io/latest/monads/state_monad.html) |
| [CompletableFutureExample.java](src/main/java/org/higherkindedj/example/basic/future/CompletableFutureExample.java) | Shows asynchronous operations with CompletableFuture | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.future.CompletableFutureExample` | [CompletableFuture Monad](https://higher-kinded-j.github.io/latest/monads/cf_monad.html) |
| [TrampolineExample.java](src/main/java/org/higherkindedj/example/basic/trampoline/TrampolineExample.java) | Demonstrates stack-safe recursion | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.trampoline.TrampolineExample` | [Trampoline Monad](https://higher-kinded-j.github.io/latest/monads/trampoline_monad.html) |
| [ConstExample.java](src/main/java/org/higherkindedj/example/basic/constant/ConstExample.java) | Shows the Const type for phantom type patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.constant.ConstExample` | [Const Type](https://higher-kinded-j.github.io/latest/monads/const_type.html) |

### Monad Transformers

Examples demonstrating monad transformer stacks for combining effects.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [EitherTExample.java](src/main/java/org/higherkindedj/example/basic/either_t/EitherTExample.java) | Demonstrates EitherT for async error handling | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.either_t.EitherTExample` | [EitherT Transformer](https://higher-kinded-j.github.io/latest/transformers/eithert_transformer.html) |
| [MaybeTExample.java](src/main/java/org/higherkindedj/example/basic/maybe_t/MaybeTExample.java) | Shows MaybeT for optional values in effects | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.maybe_t.MaybeTExample` | [MaybeT Transformer](https://higher-kinded-j.github.io/latest/transformers/maybet_transformer.html) |
| [OptionalTExample.java](src/main/java/org/higherkindedj/example/basic/optional_t/OptionalTExample.java) | Demonstrates OptionalT transformer | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.optional_t.OptionalTExample` | [OptionalT Transformer](https://higher-kinded-j.github.io/latest/transformers/optionalt_transformer.html) |
| [ReaderTExample.java](src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTExample.java) | Shows ReaderT for dependency injection with effects | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.reader_t.ReaderTExample` | [ReaderT Transformer](https://higher-kinded-j.github.io/latest/transformers/readert_transformer.html) |
| [ReaderTAsyncExample.java](src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncExample.java) | Demonstrates ReaderT with async operations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.reader_t.ReaderTAsyncExample` | [ReaderT Transformer](https://higher-kinded-j.github.io/latest/transformers/readert_transformer.html) |
| [ReaderTAsyncUnitExample.java](src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncUnitExample.java) | Shows ReaderT with Unit return type | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.reader_t.ReaderTAsyncUnitExample` | [ReaderT Transformer](https://higher-kinded-j.github.io/latest/transformers/readert_transformer.html) |
| [StateTExample.java](src/main/java/org/higherkindedj/example/basic/state_t/StateTExample.java) | Demonstrates StateT for stateful effect combinations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.state_t.StateTExample` | [StateT Transformer](https://higher-kinded-j.github.io/latest/transformers/statet_transformer.html) |
| [StateTStackExample.java](src/main/java/org/higherkindedj/example/basic/state_t/StateTStackExample.java) | Shows complex StateT stacks | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.state_t.StateTStackExample` | [StateT Transformer](https://higher-kinded-j.github.io/latest/transformers/statet_transformer.html) |

### Type Classes

Examples demonstrating type class abstractions.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [GenericExample.java](src/main/java/org/higherkindedj/example/basic/GenericExample.java) | Shows generic programming with type classes | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.GenericExample` | [Core Concepts](https://higher-kinded-j.github.io/latest/hkts/core-concepts.html) |
| [BifunctorExample.java](src/main/java/org/higherkindedj/example/basic/bifunctor/BifunctorExample.java) | Demonstrates Bifunctor operations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.bifunctor.BifunctorExample` | [Bifunctor](https://higher-kinded-j.github.io/latest/functional/bifunctor.html) |
| [ProfunctorExample.java](src/main/java/org/higherkindedj/example/basic/profunctor/ProfunctorExample.java) | Shows Profunctor operations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.profunctor.ProfunctorExample` | [Profunctor](https://higher-kinded-j.github.io/latest/functional/profunctor.html) |
| [FoldableExample.java](src/main/java/org/higherkindedj/example/basic/foldable/FoldableExample.java) | Demonstrates Foldable and Traverse | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.foldable.FoldableExample` | [Foldable and Traverse](https://higher-kinded-j.github.io/latest/functional/foldable_and_traverse.html) |
| [ForComprehensionExample.java](src/main/java/org/higherkindedj/example/basic/expression/ForComprehensionExample.java) | Shows For comprehension syntax | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.expression.ForComprehensionExample` | [For Comprehension](https://higher-kinded-j.github.io/latest/functional/for_comprehension.html) |
| [AlternativeConfigExample.java](src/main/java/org/higherkindedj/example/basic/alternative/AlternativeConfigExample.java) | Demonstrates Alternative type class | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.alternative.AlternativeConfigExample` | [Alternative](https://higher-kinded-j.github.io/latest/functional/alternative.html) |
| [OptionalMonoidExample.java](src/main/java/org/higherkindedj/example/basic/monoid/OptionalMonoidExample.java) | Shows Monoid operations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.monoid.OptionalMonoidExample` | [Semigroup and Monoid](https://higher-kinded-j.github.io/latest/functional/semigroup_and_monoid.html) |

### Free Monad

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [ConsoleProgram.java](src/main/java/org/higherkindedj/example/free/ConsoleProgram.java) | Demonstrates Free monad with a Console DSL | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.free.ConsoleProgram` | [Free Monad](https://higher-kinded-j.github.io/latest/monads/free_monad.html) |

---

## Optics Examples

### Core Optics

Examples demonstrating fundamental optic types: Lens, Prism, Iso, and Traversal.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [LensUsageExample.java](src/main/java/org/higherkindedj/example/optics/LensUsageExample.java) | Demonstrates Lens composition and generated helpers | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.LensUsageExample` | [Lenses](https://higher-kinded-j.github.io/latest/optics/lenses.html) |
| [PrismUsageExample.java](src/main/java/org/higherkindedj/example/optics/PrismUsageExample.java) | Shows Prism for sum type handling | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.PrismUsageExample` | [Prisms](https://higher-kinded-j.github.io/latest/optics/prisms.html) |
| [IsoUsageExample.java](src/main/java/org/higherkindedj/example/optics/IsoUsageExample.java) | Demonstrates Iso for isomorphic transformations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.IsoUsageExample` | [Iso](https://higher-kinded-j.github.io/latest/optics/iso.html) |
| [TraversalUsageExample.java](src/main/java/org/higherkindedj/example/optics/TraversalUsageExample.java) | Shows Traversal for multiple focus points | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.TraversalUsageExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |
| [GetterUsageExample.java](src/main/java/org/higherkindedj/example/optics/GetterUsageExample.java) | Demonstrates read-only Getter optic | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.GetterUsageExample` | [Getters](https://higher-kinded-j.github.io/latest/optics/getters.html) |
| [SetterUsageExample.java](src/main/java/org/higherkindedj/example/optics/SetterUsageExample.java) | Shows write-only Setter optic | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.SetterUsageExample` | [Setters](https://higher-kinded-j.github.io/latest/optics/setters.html) |
| [FoldUsageExample.java](src/main/java/org/higherkindedj/example/optics/FoldUsageExample.java) | Demonstrates Fold for read-only aggregation | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.FoldUsageExample` | [Folds](https://higher-kinded-j.github.io/latest/optics/folds.html) |
| [CrossOpticCompositionExample.java](src/main/java/org/higherkindedj/example/optics/CrossOpticCompositionExample.java) | Shows cross-optic composition patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.CrossOpticCompositionExample` | [Composing Optics](https://higher-kinded-j.github.io/latest/optics/composing_optics.html) |
| [NearlyPrismExample.java](src/main/java/org/higherkindedj/example/optics/NearlyPrismExample.java) | Demonstrates the `nearly` Prism constructor | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.NearlyPrismExample` | [Advanced Prism Patterns](https://higher-kinded-j.github.io/latest/optics/advanced_prism_patterns.html) |
| [DoesNotMatchExample.java](src/main/java/org/higherkindedj/example/optics/DoesNotMatchExample.java) | Shows the `doesNotMatch` Prism method | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.DoesNotMatchExample` | [Advanced Prism Patterns](https://higher-kinded-j.github.io/latest/optics/advanced_prism_patterns.html) |
| [PrismConvenienceMethodsExample.java](src/main/java/org/higherkindedj/example/optics/PrismConvenienceMethodsExample.java) | Demonstrates Prism convenience methods | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.PrismConvenienceMethodsExample` | [Prisms](https://higher-kinded-j.github.io/latest/optics/prisms.html) |
| [PrismsUtilityExample.java](src/main/java/org/higherkindedj/example/optics/PrismsUtilityExample.java) | Shows Prisms utility class usage | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.PrismsUtilityExample` | [Prisms](https://higher-kinded-j.github.io/latest/optics/prisms.html) |
| [CoreTypePrismsExample.java](src/main/java/org/higherkindedj/example/optics/CoreTypePrismsExample.java) | Demonstrates core type Prisms (Either, Maybe, etc.) | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.CoreTypePrismsExample` | [Core Type Prisms](https://higher-kinded-j.github.io/latest/optics/core_type_prisms.html) |
| [AtUsageExample.java](src/main/java/org/higherkindedj/example/optics/AtUsageExample.java) | Shows At type class for indexed access | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.AtUsageExample` | [At](https://higher-kinded-j.github.io/latest/optics/at.html) |
| [IxedUsageExample.java](src/main/java/org/higherkindedj/example/optics/IxedUsageExample.java) | Demonstrates Ixed type class | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.IxedUsageExample` | [Ixed](https://higher-kinded-j.github.io/latest/optics/ixed.html) |
| [IndexedOpticsExample.java](src/main/java/org/higherkindedj/example/optics/IndexedOpticsExample.java) | Shows indexed optics usage | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.IndexedOpticsExample` | [Indexed Optics](https://higher-kinded-j.github.io/latest/optics/indexed_optics.html) |

### Traversals

Examples demonstrating various traversal patterns over collections and data structures.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [ListTraversalsExample.java](src/main/java/org/higherkindedj/example/optics/ListTraversalsExample.java) | Demonstrates List traversals | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.ListTraversalsExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |
| [StringTraversalsExample.java](src/main/java/org/higherkindedj/example/optics/StringTraversalsExample.java) | Shows String character traversals | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.StringTraversalsExample` | [String Traversals](https://higher-kinded-j.github.io/latest/optics/string_traversals.html) |
| [TupleTraversalsExample.java](src/main/java/org/higherkindedj/example/optics/TupleTraversalsExample.java) | Demonstrates Tuple traversals | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.TupleTraversalsExample` | [Common Data Structure Traversals](https://higher-kinded-j.github.io/latest/optics/common_data_structure_traversals.html) |
| [OptionalMapTraversalsExample.java](src/main/java/org/higherkindedj/example/optics/OptionalMapTraversalsExample.java) | Shows Optional and Map traversals | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.OptionalMapTraversalsExample` | [Common Data Structure Traversals](https://higher-kinded-j.github.io/latest/optics/common_data_structure_traversals.html) |
| [FilteredTraversalExample.java](src/main/java/org/higherkindedj/example/optics/FilteredTraversalExample.java) | Demonstrates filtered traversals | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.FilteredTraversalExample` | [Filtered Optics](https://higher-kinded-j.github.io/latest/optics/filtered_optics.html) |
| [PredicateListTraversalsExample.java](src/main/java/org/higherkindedj/example/optics/PredicateListTraversalsExample.java) | Shows predicate-based list traversals | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.PredicateListTraversalsExample` | [Limiting Traversals](https://higher-kinded-j.github.io/latest/optics/limiting_traversals.html) |
| [PartsOfTraversalExample.java](src/main/java/org/higherkindedj/example/optics/PartsOfTraversalExample.java) | Demonstrates partsOf for list rewriting | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.PartsOfTraversalExample` | [Traversal Extensions](https://higher-kinded-j.github.io/latest/optics/traversal_extensions.html) |
| [ValidatedTraversalExample.java](src/main/java/org/higherkindedj/example/optics/ValidatedTraversalExample.java) | Shows validated traversals with error accumulation | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.ValidatedTraversalExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |

#### Collection-Specific Traversals

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [ListTraversalExample.java](src/main/java/org/higherkindedj/example/optics/traversal/list/ListTraversalExample.java) | List traversal patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.traversal.list.ListTraversalExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |
| [SetTraversalExample.java](src/main/java/org/higherkindedj/example/optics/traversal/set/SetTraversalExample.java) | Set traversal patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.traversal.set.SetTraversalExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |
| [MapValueTraversalExample.java](src/main/java/org/higherkindedj/example/optics/traversal/map/MapValueTraversalExample.java) | Map value traversal patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.traversal.map.MapValueTraversalExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |
| [ArrayTraversalExample.java](src/main/java/org/higherkindedj/example/optics/traversal/array/ArrayTraversalExample.java) | Array traversal patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.traversal.array.ArrayTraversalExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |
| [OptionalTraversalExample.java](src/main/java/org/higherkindedj/example/optics/traversal/optional/OptionalTraversalExample.java) | Optional traversal patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.traversal.optional.OptionalTraversalExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |
| [MaybeTraversalExample.java](src/main/java/org/higherkindedj/example/optics/traversal/maybe/MaybeTraversalExample.java) | Maybe traversal patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.traversal.maybe.MaybeTraversalExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |
| [EitherTraversalExample.java](src/main/java/org/higherkindedj/example/optics/traversal/either/EitherTraversalExample.java) | Either traversal patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.traversal.either.EitherTraversalExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |
| [TryTraversalExample.java](src/main/java/org/higherkindedj/example/optics/traversal/trymonad/TryTraversalExample.java) | Try traversal patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.traversal.trymonad.TryTraversalExample` | [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html) |

### Extensions and Fluent API

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [LensExtensionsExample.java](src/main/java/org/higherkindedj/example/optics/LensExtensionsExample.java) | Demonstrates Lens extension methods | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.LensExtensionsExample` | [Lens Extensions](https://higher-kinded-j.github.io/latest/optics/lens_extensions.html) |
| [TraversalExtensionsExample.java](src/main/java/org/higherkindedj/example/optics/TraversalExtensionsExample.java) | Shows Traversal extension methods | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.TraversalExtensionsExample` | [Traversal Extensions](https://higher-kinded-j.github.io/latest/optics/traversal_extensions.html) |
| [GetterExtensionsExample.java](src/main/java/org/higherkindedj/example/optics/extensions/GetterExtensionsExample.java) | Demonstrates Getter extensions | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.extensions.GetterExtensionsExample` | [Getters](https://higher-kinded-j.github.io/latest/optics/getters.html) |
| [FoldExtensionsExample.java](src/main/java/org/higherkindedj/example/optics/extensions/FoldExtensionsExample.java) | Shows Fold extension methods | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.extensions.FoldExtensionsExample` | [Folds](https://higher-kinded-j.github.io/latest/optics/folds.html) |
| [FluentApiExample.java](src/main/java/org/higherkindedj/example/optics/fluent/FluentApiExample.java) | Demonstrates fluent optic API | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.fluent.FluentApiExample` | [Fluent API](https://higher-kinded-j.github.io/latest/optics/fluent_api.html) |
| [FluentOpticOpsExample.java](src/main/java/org/higherkindedj/example/optics/fluent/FluentOpticOpsExample.java) | Shows fluent optic operations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.fluent.FluentOpticOpsExample` | [Fluent API](https://higher-kinded-j.github.io/latest/optics/fluent_api.html) |
| [AdvancedFluentPatternsExample.java](src/main/java/org/higherkindedj/example/optics/fluent/AdvancedFluentPatternsExample.java) | Advanced fluent API patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.fluent.AdvancedFluentPatternsExample` | [Fluent API](https://higher-kinded-j.github.io/latest/optics/fluent_api.html) |
| [FluentValidationExample.java](src/main/java/org/higherkindedj/example/optics/fluent/FluentValidationExample.java) | Shows fluent validation patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.fluent.FluentValidationExample` | [Fluent API](https://higher-kinded-j.github.io/latest/optics/fluent_api.html) |
| [FreeDslExample.java](src/main/java/org/higherkindedj/example/optics/fluent/FreeDslExample.java) | Demonstrates Free monad DSL for optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.fluent.FreeDslExample` | [Free Monad DSL](https://higher-kinded-j.github.io/latest/optics/free_monad_dsl.html) |
| [FreeMonadOpticDSLExample.java](src/main/java/org/higherkindedj/example/optics/fluent/FreeMonadOpticDSLExample.java) | Free monad optic DSL patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.fluent.FreeMonadOpticDSLExample` | [Free Monad DSL](https://higher-kinded-j.github.io/latest/optics/free_monad_dsl.html) |
| [OpticInterpretersExample.java](src/main/java/org/higherkindedj/example/optics/fluent/OpticInterpretersExample.java) | Shows optic interpreter patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.fluent.OpticInterpretersExample` | [Interpreters](https://higher-kinded-j.github.io/latest/optics/interpreters.html) |
| [OpticProfunctorExample.java](src/main/java/org/higherkindedj/example/optics/profunctor/OpticProfunctorExample.java) | Demonstrates profunctor optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.profunctor.OpticProfunctorExample` | [Profunctor Optics](https://higher-kinded-j.github.io/latest/optics/profunctor_optics.html) |

### Cookbook Recipes

Ready-to-use patterns for common optics use cases.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [DeepUpdateRecipes.java](src/main/java/org/higherkindedj/example/optics/cookbook/DeepUpdateRecipes.java) | Recipes for deep immutable updates | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.cookbook.DeepUpdateRecipes` | [Cookbook](https://higher-kinded-j.github.io/latest/optics/cookbook.html) |
| [CollectionRecipes.java](src/main/java/org/higherkindedj/example/optics/cookbook/CollectionRecipes.java) | Recipes for collection operations | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.cookbook.CollectionRecipes` | [Cookbook](https://higher-kinded-j.github.io/latest/optics/cookbook.html) |
| [CompositionRecipes.java](src/main/java/org/higherkindedj/example/optics/cookbook/CompositionRecipes.java) | Recipes for optic composition | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.cookbook.CompositionRecipes` | [Cookbook](https://higher-kinded-j.github.io/latest/optics/cookbook.html) |
| [ConditionalUpdateRecipes.java](src/main/java/org/higherkindedj/example/optics/cookbook/ConditionalUpdateRecipes.java) | Recipes for conditional updates | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.cookbook.ConditionalUpdateRecipes` | [Cookbook](https://higher-kinded-j.github.io/latest/optics/cookbook.html) |

### Real-World Optics Scenarios

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [ApiResponseHandlingExample.java](src/main/java/org/higherkindedj/example/optics/ApiResponseHandlingExample.java) | Handling API responses with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.ApiResponseHandlingExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [BatchProcessingExample.java](src/main/java/org/higherkindedj/example/optics/BatchProcessingExample.java) | Batch data processing with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.BatchProcessingExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [ConfigurationManagementExample.java](src/main/java/org/higherkindedj/example/optics/ConfigurationManagementExample.java) | Configuration management with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.ConfigurationManagementExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [CustomerAnalyticsExample.java](src/main/java/org/higherkindedj/example/optics/CustomerAnalyticsExample.java) | Customer analytics with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.CustomerAnalyticsExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [DataValidationPipelineExample.java](src/main/java/org/higherkindedj/example/optics/DataValidationPipelineExample.java) | Data validation pipelines | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.DataValidationPipelineExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [EventProcessingExample.java](src/main/java/org/higherkindedj/example/optics/EventProcessingExample.java) | Event processing with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.EventProcessingExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [IntegrationPatternsExample.java](src/main/java/org/higherkindedj/example/optics/IntegrationPatternsExample.java) | Integration patterns with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.IntegrationPatternsExample` | [Core Type Integration](https://higher-kinded-j.github.io/latest/optics/core_type_integration.html) |
| [PaginationExample.java](src/main/java/org/higherkindedj/example/optics/PaginationExample.java) | Pagination handling with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.PaginationExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [PluginSystemExample.java](src/main/java/org/higherkindedj/example/optics/PluginSystemExample.java) | Plugin system with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.PluginSystemExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [SelectiveOpticsExample.java](src/main/java/org/higherkindedj/example/optics/SelectiveOpticsExample.java) | Selective optics patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.SelectiveOpticsExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [SelectivePerformanceExample.java](src/main/java/org/higherkindedj/example/optics/SelectivePerformanceExample.java) | Performance optimisation with selective | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.SelectivePerformanceExample` | [Selective](https://higher-kinded-j.github.io/latest/functional/selective.html) |
| [StateMachineExample.java](src/main/java/org/higherkindedj/example/optics/StateMachineExample.java) | State machines with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.StateMachineExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |
| [TimeSeriesWindowingExample.java](src/main/java/org/higherkindedj/example/optics/TimeSeriesWindowingExample.java) | Time series windowing with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.TimeSeriesWindowingExample` | [Optics Examples](https://higher-kinded-j.github.io/latest/optics/optics_examples.html) |

---

## Complete Application Examples

### Order Processing Workflow

A comprehensive example demonstrating EitherT monad transformer for async error handling in a business workflow.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [OrderWorkflowRunner.java](src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflowRunner.java) | Complete order processing workflow | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.order.workflow.OrderWorkflowRunner` | [Order Walkthrough](https://higher-kinded-j.github.io/latest/hkts/order-walkthrough.html) |
| [Workflow1.java](src/main/java/org/higherkindedj/example/order/workflow/Workflow1.java) | Workflow variant 1 | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.order.workflow.Workflow1` | [Order Walkthrough](https://higher-kinded-j.github.io/latest/hkts/order-walkthrough.html) |
| [Workflow2.java](src/main/java/org/higherkindedj/example/order/workflow/Workflow2.java) | Workflow variant 2 | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.order.workflow.Workflow2` | [Order Walkthrough](https://higher-kinded-j.github.io/latest/hkts/order-walkthrough.html) |
| [WorkflowTraverse.java](src/main/java/org/higherkindedj/example/order/workflow/WorkflowTraverse.java) | Workflow with traverse patterns | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.order.workflow.WorkflowTraverse` | [Foldable and Traverse](https://higher-kinded-j.github.io/latest/functional/foldable_and_traverse.html) |
| [WorkflowLensAndPrism.java](src/main/java/org/higherkindedj/example/order/workflow/WorkflowLensAndPrism.java) | Workflow with lens and prism | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.order.workflow.WorkflowLensAndPrism` | [Optics Introduction](https://higher-kinded-j.github.io/latest/optics/optics_intro.html) |
| [LensUsageExample.java](src/main/java/org/higherkindedj/example/order/lens/LensUsageExample.java) | Lens usage in order domain | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.order.lens.LensUsageExample` | [Lenses](https://higher-kinded-j.github.io/latest/optics/lenses.html) |

### Draughts (Checkers) Game

An interactive draughts game demonstrating IO monad, State monad, and For comprehensions.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [Draughts.java](src/main/java/org/higherkindedj/example/draughts/Draughts.java) | Interactive draughts game | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.draughts.Draughts` | [Draughts Example](https://higher-kinded-j.github.io/latest/hkts/draughts.html) |

### Configuration Audit

Demonstrates optics for auditing and transforming complex configuration structures.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [ConfigAuditExample.java](src/main/java/org/higherkindedj/example/configaudit/ConfigAuditExample.java) | Configuration auditing with optics | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.configaudit.ConfigAuditExample` | [Auditing Complex Data Example](https://higher-kinded-j.github.io/latest/optics/auditing_complex_data_example.html) |

---

## Tutorials

Interactive tutorial examples for learning Higher-Kinded-J concepts.

| Example | Description | Run Command | Documentation |
|---------|-------------|-------------|---------------|
| [TutorialGettingStarted.java](src/main/java/org/higherkindedj/example/tutorials/TutorialGettingStarted.java) | Setup verification | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.tutorials.TutorialGettingStarted` | [Tutorials Introduction](https://higher-kinded-j.github.io/latest/tutorials/tutorials_intro.html) |
| [ApplicativeValidation.java](src/main/java/org/higherkindedj/example/tutorials/ApplicativeValidation.java) | Applicative validation tutorial | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.tutorials.ApplicativeValidation` | [Applicative](https://higher-kinded-j.github.io/latest/functional/applicative.html) |
| [LensDeepUpdate.java](src/main/java/org/higherkindedj/example/tutorials/LensDeepUpdate.java) | Deep update with lenses tutorial | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.tutorials.LensDeepUpdate` | [Lenses](https://higher-kinded-j.github.io/latest/optics/lenses.html) |

---

## Additional Resources

- [Higher-Kinded-J Documentation](https://higher-kinded-j.github.io/latest/)
- [Core Concepts](https://higher-kinded-j.github.io/latest/hkts/core-concepts.html)
- [Quick Reference](https://higher-kinded-j.github.io/latest/hkts/quick_reference.html)
- [Troubleshooting](https://higher-kinded-j.github.io/latest/tutorials/troubleshooting.html)
