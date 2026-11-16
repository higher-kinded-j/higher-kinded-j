<!-- description: Find the Higher-Kinded-J core type for a task, such as Either for a typed error, Maybe for a missing value or IO for a side effect. -->

# Core Types

<!-- source: Rudyard Kipling, "The Elephant's Child", in Just So Stories (1902), the closing verse; text checked against Project Gutenberg eBook #2781 -->
> _"I keep six honest serving-men: / (They taught me all I knew) / Their names are What and Where and When / And How and Why and Who."_
>
> — Rudyard Kipling, *Just So Stories* (The Elephant's Child)

---

Each family asks one of them of a value: what, if anything, it holds; why it failed; when it runs; where its settings come from; how many there are; or who runs it.

A core type holds a value, as Java's `Optional` does, and the library adds its own. Application code reaches most of them through an [Effect Path](../effect/ch_intro.md), and generic code through type class instances; the [Foundations introduction](../hkts/foundations_intro.md) says which you are writing.

Read a page when a task needs its type, and skip the group until then. [Choosing a Type](supported-types.md#choosing-a-type) and [What each type supports](supported-types.md#what-each-type-supports) help you choose.

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
  - [Context](context_scoped.md): Request values bound to a `ScopedValue`
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

~~~admonish info title="Hands-On Learning"
Practise the core types together in [Tutorial 07: Real World](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial07_RealWorld.java) (6 exercises).
~~~

---

**Previous:** [ForState Comprehension](../functional/forstate_comprehension.md)
**Next:** [Either](either_monad.md)
