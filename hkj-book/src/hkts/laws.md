<!-- description: Which Higher-Kinded-J instances bend a type class law, such as Validated's ap, what equal means in a law, and which laws hkj-test checks. -->

# Laws and Where HKJ Bends Them

_The instances that bend a law, what equal means in a law, and which laws `hkj-test` checks._

## Where an instance bends a law {#where-an-instance-bends-a-law}

| Instance | Law it bends, and how | What to do |
|---|---|---|
| [`Validated`](../functional/applicative.md#where-validated-bends-a-rule) | **`ap` agrees with `flatMap`**, which `MonadLaws` does not check: `ap`, and `map2` to `map5`, keep every error, while `flatMap` stops at the first | Combine independent checks with `map2` to `map5` or `traverse`, and chain a check that needs the one before with `flatMap` |
| [`Try`](../monads/try_monad.md#exceptions-as-values) | **Left identity**: `flatMap` turns a step that throws into a `Failure`, where calling the step throws | Nothing: catching is what `Try` is for, and a step that returns keeps the law |
| [`CompletableFuture`](../monads/cf_monad.md#working-with-kind) | **Handling a raised error is the handler**: the handler receives a raised `CompletionException`'s cause, not the exception | Raise the cause itself |
| [`doubleAddition`, `doubleMultiplication`](../functional/semigroup_and_monoid.md#things-people-get-wrong) | **Associativity**: each `+` or `*` rounds, so `(0.1 + 0.2) + 0.3` is `0.6000000000000001`, and `0.1 + (0.2 + 0.3)` is `0.6` | Total money with `bigDecimalAddition`, and compare `double` results within a tolerance |
| [`doubleAddition`](../functional/semigroup_and_monoid.md#things-people-get-wrong) | **Left and right identity**: adding `0.0` turns `-0.0` into `0.0`, which `Double.equals` treats as unequal | Compare with `==`, or within a tolerance |
| [`bigDecimalAddition`](../functional/semigroup_and_monoid.md#three-laws) | **Left and right identity, by `equals`**: added to `ZERO`, `new BigDecimal("1E+3")` comes back as `1000`, equal to it by `compareTo` only | Compare with `compareTo`, or keep a price at its usual scale of two |
| [`Maybe`, `Optional`, `List`, `Stream`](core-concepts.md#values-never-null) | **Left identity, for a `null` value**: `of(null)` gives an empty value, so a step that turns `null` into a value fails it | Keep `null` out of `of`, and hold a value that may be absent in a `Maybe` |

An instance built from a `Semigroup` or `Monoid` you pass, such as a `Writer`'s, keeps its laws only when what you pass keeps its own. A `Writer` that logs with `doubleAddition` fails associativity and right identity. An instance not listed keeps its laws as far as the library's tests check them, which [Which laws `hkj-test` checks](#which-laws-hkj-test-checks) shows.

## What equal means in a law {#what-equal-means-in-a-law}

A law says that two expressions are equal. For a value, such as an `Either` or a `List`, that is `equals` once both are narrowed. A deferred type, such as `IO` or `VTask`, is equal when running both gives equal results, and a `Free` program when interpreting both does. A function-shaped type, such as `State` or `Reader`, is equal when running both from the same input gives equal results.

A `Stream` is equal to another when collecting both gives equal lists. A stream can be read only once, so a checker that uses one value on both sides of a law, as most of `hkj-test`'s do, throws `IllegalStateException` on a `Stream`. Check a `Stream` instance's laws by hand, building each side from a fresh stream. Comparing two deferred values runs each of them, so an effect that appears on both sides of a law runs once for each side. For a type with one parameter, `KindEquivalence.byEqualsAfter`, in `hkj-test`, builds each of these equalities from a function that narrows, runs or interprets.

A function, such as a `Natural`, is equal to another when both give equal results for every input. A test can try only a few inputs, as [the naturality law](../functional/natural_transformation.md#the-naturality-law) does.

## Which laws `hkj-test` checks {#which-laws-hkj-test-checks}

Each type class links the section that states its laws.

{{#include ../../../hkj-examples/src/test/resources/golden/foundations-laws.md.golden:checked}}

Each checker for a type class over one parameter takes the instance, a few values, and an equality from `KindEquivalence.byEqualsAfter`. `BifunctorLaws` takes an equality over `Kind2` values, and `KindHelperLaws` takes the type's `widen` and `narrow`. [Prove your instance](extending-simulation.md#prove-your-instance) runs them on a type of your own.

Where a type class reads "Not supported yet", check its laws in a test of your own, as the sections for [`MonadError`](../functional/monad_error.md#three-laws) and [`Alternative`](../functional/alternative.md#laws) do. `TraverseLaws` checks identity only, so a `Traverse` instance's naturality and composition need a test of your own too.

`SelectiveLaws` checks left pure and right pure, two properties that the laws of the [Selective paper](https://dl.acm.org/doi/10.1145/3341694) do not require, and it checks none of the paper's laws. The library's instances keep both properties. The paper does not require a `select` to skip the function for a `Right`, so an instance of your own can keep its laws and still fail right pure.

**The claims on this page are tested** in [LawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/laws/LawsBookTest.java) and in the tests of the pages each row links.

---

**Previous:** [Foundations FAQ](faq.md)
**Next:** [Example Index](hkt_basic_examples.md)
