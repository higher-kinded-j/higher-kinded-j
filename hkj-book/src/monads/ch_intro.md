<!-- description: Find the Higher-Kinded-J type for a task, such as Either, Validated, Maybe or IO, and see which type classes each one supports. -->

# Core Types

<!-- source: Rudyard Kipling, "The Elephant's Child", in Just So Stories (1902), the closing verse; text checked against Project Gutenberg eBook #2781 -->
> _"I keep six honest serving-men: / (They taught me all I knew) / Their names are What and Where and When / And How and Why and Who."_
>
> — Rudyard Kipling, *Just So Stories* (The Elephant's Child)

---

Each family asks one of them of a value: what, if anything, it holds; why it failed; when it runs; where its settings come from; how many there are; or who runs it.

A core type is a container for a value, as Java's `Optional` is, and the library adds its own, such as `Maybe`, `Either` and `IO`. Application code reaches most of them through an [Effect Path](../effect/ch_intro.md) such as `EitherPath`, and generic code through their type class instances. The [Foundations introduction](../hkts/foundations_intro.md) says which you are writing.

Read a page when a task calls for its type, such as returning a typed error. [Choosing a Type](supported-types.md#choosing-a-type) helps you pick one, and until then you can skip the group.

---

## Pages in this group {#pages-in-this-group}

- **Failure**
  - [Either](either_monad.md): A value, or the first typed error
  - [Validated](validated_monad.md): A value, or every error found, and [Accumulating Assembly](validated_assembly.md) for records of validated fields
  - [Try](try_monad.md): A value, or a caught exception
  - [EitherOrBoth](either_or_both_monad.md): A value, an error, or a value with warnings
  - [NonEmptyList](nonemptylist_monad.md): A list with at least one element, such as errors
- **Absence**
  - [Maybe](maybe_monad.md): A value, or nothing, and never `null`
  - [Optional](optional_monad.md): Java's `Optional`, with the same instances as `Maybe`
- **Deferred**
  - [IO](io_monad.md): A side effect described now and run later
  - [Lazy](lazy_monad.md): A value worked out when first asked for, then kept
  - [CompletableFuture](cf_monad.md): Java's asynchronous result, already running
- **Virtual threads**
  - [VTask](vtask_monad.md): A deferred task, which `runAsync`, `Par` and `Scope` run on virtual threads, with [Structured Concurrency](vtask_scope.md) and [Resource Management](vtask_resource.md)
  - [VStream](vstream.md): A stream that produces each element when pulled, with [HKT and Type Classes](vstream_hkt.md), [Parallel Operations](vstream_parallel.md), [Performance](vstream_performance.md), [Resource-Safe Streaming](vstream_resources.md) and [Advanced Features](vstream_advanced.md)
- **Environment and state**
  - [Reader](reader_monad.md): A value worked out from settings
  - [Context](context_scoped.md): Settings read from a Java `ScopedValue`
  - [State](state_monad.md): A step that reads a state and gives a new one
  - [Writer](writer_monad.md): A value with a log beside it
- **Collections**
  - [List](list_monad.md): Java's `List`, read as many possible results
  - [Stream](stream_monad.md): Java's `Stream`, each element worked out when needed
- **Programs as data**
  - [Identity](identity.md): A value and nothing else, for generic code with no effect
  - [Trampoline](trampoline_monad.md): A recursion that runs without growing the stack
  - [Free](free_monad.md): A program written as data, for an interpreter to run
  - [Free Applicative](free_applicative.md): Independent steps an interpreter can inspect before running them
  - [EitherF](eitherf.md): Two instruction sets joined, so one program can use both
  - [Coyoneda](coyoneda.md): A `Functor` for any type, which joins its `map` calls into one
  - [Const](const_type.md): A value of its first type and none of its second, for folds and getters

---

## What each type supports {#what-each-type-supports}

{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:matrix}}

A type class, such as `Functor` or `Traverse`, names operations that work on any type with an instance of it, so a row tells you which generic code accepts that type. Every row lists the classes in the same order, so the differences show towards the end, and includes each class a listed one extends, as [the hierarchy](../functional/ch_intro.md#the-hierarchy) shows. `MonadError<…>` names the error its instance raises and recovers from: the type's own `L` or `E`, a `Throwable`, or `Unit`, the bare absence of `Maybe` and `Optional`. A test reads the table from the library's classes, so it lists exactly the instances of these type classes the library has. [`Semigroup` and `Monoid`](../functional/semigroup_and_monoid.md), which take a plain type rather than a container, are left out. Some instances take an argument: `Validated`'s and `EitherOrBoth`'s need a `Semigroup` to join two errors, and `Writer`'s and `Const`'s a `Monoid` to join two logs or two values.

~~~admonish info title="Hands-On Learning"
Practise the core types together in [Tutorial 07: Real World](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial07_RealWorld.java) (6 exercises).
~~~

---

**Previous:** [ForState Comprehension](../functional/forstate_comprehension.md)
**Next:** [Either](either_monad.md)
