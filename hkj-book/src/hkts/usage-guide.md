<!-- description: Write one Java method for any container with Higher-Kinded-J: pick a witness, ask Instances for its type class, then widen, call and narrow. -->

# Working with Kind: Widen, Call, Narrow

_Five steps take a value into `Kind<F, A>`, through a type class, and back out._

![A man in a glass-walled room holds his hands together in a diamond shape in front of him](../images/oa-movements.png)

~~~admonish info title="What You'll Learn"
- Pick the witness for a container, and ask `Instances` for its type class
- Widen a value, call the type class on it, and narrow the result
- Write one method over `MonadError` that runs unchanged on a `Maybe` and an `Either`
- Recover from a failure inside the encoding, in code written for any container
~~~

~~~admonish example title="See Example Code"
**The code on this page is [WorkingWithKindBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/workflow/WorkingWithKindBook.java)**: the page includes it, so the build compiles and runs it. [WorkingWithKindBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/workflow/WorkingWithKindBookTest.java) holds each claim the page makes.
~~~

---

This page is for code written once for any container. Code for one container, such as a service method that returns an `EitherPath`, stays with [Effect Paths](../effect/ch_intro.md), and the [Foundations](foundations_intro.md) introduction's "Which level am I working at?" tip says how to tell which you need.

## The five steps {#the-five-steps}

1. **Pick the witness.** It names the container in your types: `MaybeKind.Witness` for `Maybe`, `EitherKind.Witness<L>` for an `Either` whose left type is `L`, `ListKind.Witness` for `List`, and `OptionalKind.Witness` for `Optional`. Every other type follows the pattern, such as `TryKind.Witness` for `Try`.
2. **Ask `Instances` for the type class.** `Instances.monadError(maybe())` gives `Maybe`'s `MonadError`, where `maybe()` names the witness. [Obtaining Instances](../functional/instances_facade.md) covers every lookup.
3. **Widen the value** with its type's helper, such as `MAYBE.widen(...)`.
4. **Call a method on the instance, passing the `Kind`**: `map`, `flatMap`, `raiseError` or `handleErrorWith`. A `Kind` has no methods of its own.
5. **Narrow the result** with the same helper, such as `MAYBE.narrow(...)`.

The code on this page uses these imports, with `Order` and `OrderStatus` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
```

## Write the method once {#write-the-method-once}

`totalUnlessShipped` totals an order, and refuses one that has already shipped. It never names `Maybe`: it is written over a `MonadError` for any `F`.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/workflow/WorkingWithKindBook.java:generic}}
```

- **It asks for a `MonadError`, because refusing an order is a failure.** The introduction's `totals` needed only a `Functor`, since nothing there could fail. A [`MonadError`](../functional/monad_error.md) is a `Monad` that can also raise an error and recover from one.
- **The bound on `F` says it names a container with one type argument missing.** [The `WitnessArity` bound](core-concepts.md#the-witnessarity-bound) explains it.
- **`of` and `raiseError` come from the instance**, so the method builds results without knowing the container.
- **`flatMap` runs its function only when there is an order.** On a `Nothing`, the function never runs, and the `Nothing` comes back.
- **The caller passes the error.** The method knows nothing about `E`, so it cannot build one: the caller passes `Unit.INSTANCE` for `Maybe`, and a message for an `Either` whose left type is `String`.

## Take it through the steps {#take-it-through-the-steps}

Here the five steps total an order held in a `Maybe`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/workflow/WorkingWithKindBook.java:steps}}
```

The same method runs unchanged on an `Either`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/workflow/WorkingWithKindBook.java:either}}
```

~~~admonish warning title="The compiler does not check `E`"
`Instances.monadError` takes `E` from the variable you assign it to, and nothing checks it against the instance. `MonadError<MaybeKind.Witness, String>` compiles, then throws `ClassCastException` when you raise a `String`. Declare the error type the instance has, `Unit` for `Maybe`, from the table in [Obtaining Instances](../functional/instances_facade.md#zero-argument-lookups).
~~~

## Recover in code for any container {#recover-in-code-for-any-container}

Code written for any `F` cannot narrow, because it does not know which helper to use. So it recovers through the instance: `handleErrorWith` takes the failed `Kind` and a function from the error to a replacement.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/workflow/WorkingWithKindBook.java:recover}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/workflow/WorkingWithKindBook.java:recovered}}
```

For a chain of several dependent steps, a [for comprehension](../functional/for_comprehension.md) reads more easily than nested `flatMap` calls.

~~~admonish tip title="You can ship now"
You can now write one method over a type class and run it on any container that has an instance: widen, call, narrow. The rest of this page is for when `narrow` throws, and what widening allocates.
~~~

---

## The fine print {#the-fine-print}

- **`narrow` throws `KindUnwrapException`** for a `null`, or for another container's `Kind`. It is a bug to fix, not a failure to recover. See [When `narrow` throws](core-concepts.md#when-narrow-throws).
- **Widening a JDK type allocates a holder.** Widening `Optional` or `List` allocates one, and widening `Maybe` or `Either` does not. See [The pieces each type provides](core-concepts.md#the-pieces-each-type-provides).

---

**Previous:** [How the Encoding Works](core-concepts.md)
**Next:** [Obtaining Instances](../functional/instances_facade.md)
