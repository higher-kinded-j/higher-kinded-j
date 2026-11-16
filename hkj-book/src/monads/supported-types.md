<!-- description: Find the Higher-Kinded-J type for the Java you write today, such as Maybe for a method that may return null, and see which type classes each type supports. -->

# Supported Types

_Find the type for the code you write today, and see which type classes each type supports._

## Choosing a Type {#choosing-a-type}

Find the code you write today, or the Java type it already gives you, in the first column. Application code holds the type beside it through the [Effect Path](../effect/ch_intro.md) in the last column, where it has one.

{{#include ../../../hkj-examples/src/test/resources/golden/foundations-triage.md.golden:triage}}

Each type's page opens with a card that names a neighbouring type for when that type does not fit.

An Effect Path can hold two effects at once: [`VResultPath`](../effect/path_vresult.md), for one, is a deferred task that fails with a typed error. Where no Path holds the pair you need, a monad transformer adds one effect to another, as [Other types with instances](#other-types-with-instances) shows.

## What each type supports {#what-each-type-supports}

{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:matrix}}

A type class, such as `Functor` or `Traverse`, names operations that work on any type with an instance of it, so a row tells you which generic code accepts that type. [Type Classes](../functional/ch_intro.md) says what each one lets you call. Every row lists the classes in the same order, and includes each class a listed one extends, as [the hierarchy](../functional/ch_intro.md#the-hierarchy) shows. `MonadError<…>` names the error its instance raises and recovers from: the type's own `L` or `E`, a `Throwable`, or `Unit`, the bare absence of `Maybe` and `Optional`.

Two rows that match can still behave differently, because behaviour belongs to the instance: `Either`'s `ap` stops at the first error, and `Validated`'s keeps every one. A test reads the table from the library's classes, so it lists exactly the instances of these type classes the library has. [`Semigroup` and `Monoid`](../functional/semigroup_and_monoid.md), which take a plain type rather than a container, are left out. Some instances take arguments. `Validated`'s and `EitherOrBoth`'s take a `Semigroup` to join two errors, `Writer`'s and `Const`'s a `Monoid` to join two logs or two values, and `EitherF`'s a `Functor` for each side.

## Other types with instances {#other-types-with-instances}

A monad transformer, such as `EitherT`, adds one effect to a monad you choose, so its instance takes that monad's own, and `WriterT`'s a `Monoid` as well. `ReaderT`'s `MonadReader<…>`, `StateT`'s `MonadState<…>` and `WriterT`'s `MonadWriter<…>` name the environment, state or output each gives access to, and [MTL Capabilities](../transformers/mtl_capabilities.md) shows code written against them. `Tuple2` is a pair whose two sides [Bifunctor](../functional/bifunctor.md) maps, and `Function` a function whose input and output [Profunctor](../functional/profunctor.md) adapts. None of them has a Core Types page, so each row links the page that shows it.

{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:others}}

Java has no higher-kinded types, so generic code reaches every type on this page through the same encoding, which [How the Encoding Works](../hkts/core-concepts.md#the-pieces-each-type-provides) shows.

![Two cartoon toys, one sweeping an arm towards the horizon while the other looks on, worried, captioned "Monads, monads, monads everywhere"](../images/monads_everywhere.webp)

~~~admonish info title="Hands-On Learning"
Practise with concrete types in [Tutorial 06: Concrete Types](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial06_ConcreteTypes.java) (7 exercises).
~~~

---

**Previous:** [Const](const_type.md)
**Next:** [Quick Reference](../hkts/quick_reference.md)
