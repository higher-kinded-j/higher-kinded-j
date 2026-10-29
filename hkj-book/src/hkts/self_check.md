# Check Your Understanding

_Twelve questions on the Ship pages, each answer proved by the build._

These questions cover the pages from [One Line, Six Layers](one_line_six_layers.md) to [For Comprehensions](../functional/for_comprehension.md). They start with recall and end with writing code of your own, and they do not follow page order, on purpose. Answer each question before you open its answer, in your head or on paper. Each answer ends with a link to the section that teaches it, and [Where to go next](#where-to-go-next) turns your score into a plan.

~~~admonish question title="Checkpoint 1: changing the value inside a `Kind`" id="check-self-kind"
You hold a `Kind<MaybeKind.Witness, Order>`. What do you call `map` on to work out the order's total, and how do you get a plain `Maybe` back?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-kind-answer"
**On `Maybe`'s instance, such as `Instances.functor(maybe())`, and then `MAYBE.narrow`.** Any of the `functor`, `applicative` or `monad` lookups gives that instance. A `Kind` has no methods of its own, so calling `map` on it does not compile: javac cannot find a "method map" on it.

<!-- verify:rejects "method map" -->
```java
Kind<MaybeKind.Witness, Order> found = MAYBE.widen(Maybe.just(order));
found.map(placed -> placed.customer());
```

Where this lives: [The five steps](usage-guide.md#the-five-steps).
~~~

~~~admonish question title="Checkpoint 2: a step that can fail, on a Path" id="check-self-via"
On an `EitherPath`, which method runs a step that can itself fail? Does that method go through `Either`'s `Monad` instance?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-via-answer"
**`via`, and no.** An `EitherPath` holds an `Either` and calls it directly, with no `Kind` and no instance between. `Monad`'s `flatMap` is the same operation over `Kind`, and the two give the same result.

Where this lives: [Two encodings, one algebra](one_line_six_layers.md#two-encodings-one-algebra).
~~~

~~~admonish question title="Checkpoint 3: nothing to give back" id="check-self-unit"
A step runs only for its effect, and has no value to give back. What goes in the `A` of its `Kind<F, A>`, and why not `Void`?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-unit-answer"
**`Unit`, whose one value is `Unit.INSTANCE`.** `Void`'s only value is `null`, and what `of(null)` does depends on the container: `Either`'s throws, and `Maybe`'s gives `Nothing`. `Unit.INSTANCE` is a value that every container holds.

Where this lives: [`Unit`: a value for "nothing to return"](core-concepts.md#unit).
~~~

~~~admonish question title="Checkpoint 4: what a future's handler receives" id="check-self-future"
A `CompletableFuture` fails inside `supplyAsync`, and a later `thenApply` stage passes the failure on. What does `handleErrorWith`'s handler receive: a `CompletionException`, or the exception the supplier threw?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-future-answer"
**The exception the supplier threw.** `CompletableFuture`'s instance unwraps the cause from any `CompletionException` before it calls the handler.

Where this lives: [What each instance does](../functional/monad_error.md#what-each-instance-does).
~~~

~~~admonish question title="Checkpoint 5: three lookups on `Try`" id="check-self-one-object"
How many different objects do `Instances.monad(try_())`, `Instances.applicative(try_())` and `Instances.functor(try_())` give back?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-one-object-answer"
**One.** A token's instance implements the whole `Functor`, `Applicative`, `Monad` chain, so the three lookups return that same object, for `Try` as for `Maybe`.

Where this lives: [Zero-argument lookups](../functional/instances_facade.md#zero-argument-lookups).
~~~

~~~admonish question title="Checkpoint 6: a lookup inside `map`" id="check-self-nest"
`found` is a `Kind<EitherKind.Witness<String>, Order>`, `monad` is `Either`'s `Monad`, and `findCustomer` gives back a `Kind<EitherKind.Witness<String>, Customer>`. What does `monad.map(findCustomer, found)` give back, and which method gives a `Kind<EitherKind.Witness<String>, Customer>` instead?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-nest-answer"
**A `Kind` inside a `Kind`, and `flatMap`.** `map` puts whatever its function returns inside the container, a container included. `flatMap` runs a step that gives back a container, and flattens the result into one. The compiler agrees with both types:

<!-- verify -->
```java
Kind<EitherKind.Witness<String>, Kind<EitherKind.Witness<String>, Customer>> nested =
    monad.map(findCustomer, found);
Kind<EitherKind.Witness<String>, Customer> flat = monad.flatMap(findCustomer, found);
```

Where this lives: [What a `Monad` adds](../functional/monad.md#what-a-monad-adds).
~~~

~~~admonish question title="Checkpoint 7: every bad line, or the first" id="check-self-traverse"
You `traverse` three line items with a check that fails on two of them, once through `Instances.validated(Semigroups.list())` and once through `Either`'s instance. How many errors come back from each?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-traverse-answer"
**Two from `Validated`, and one from `Either`.** `traverse` combines the results with the instance it is given, so it keeps what that instance's `map2` keeps. `Validated`'s joins the errors with its semigroup, here one that keeps them all, and `Either`'s keeps the first.

Where this lives: [What each instance does](../functional/applicative.md#what-each-instance-does), and [What builds on `Applicative`](../functional/applicative.md#what-builds-on-applicative).
~~~

~~~admonish question title="Checkpoint 8: widening twice" id="check-self-widen"
You widen the same `Maybe` twice, and the same `Optional` twice. Is each pair the same object?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-widen-answer"
**The `Maybe` pair is, and the `Optional` pair is not.** A `Maybe` is its own `Kind`, so widening returns it. An `Optional` cannot implement `Kind`, so each widen wraps it in a new holder.

Where this lives: [The pieces each type provides](core-concepts.md#the-pieces-each-type-provides).
~~~

~~~admonish question title="Checkpoint 9: `par` or `from`, on a list" id="check-self-par"
With `List`'s instance, `For.par` starts a chain from the SKUs `["LAMP", "DESK"]` and the quantities `[1, 2]`. How many values does `yield` give? Does a chain that binds the same two lists with `from` give a different count?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-par-answer"
**Four, and no.** `par` combines with `List`'s `map2`, which gives every combination. `from` uses `List`'s `flatMap`, which runs the next step on every element, so it reaches the same four.

Where this lives: [Independent steps with `par`](../functional/for_comprehension.md#independent-steps-with-par), and [What each instance does](../functional/applicative.md#what-each-instance-does).
~~~

~~~admonish question title="Checkpoint 10: write `countLines`" id="check-self-count"
Write `countLines`, which gives the number of lines of the order in any container. Ask for the least capability it needs.
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-count-answer"
**A `Functor` is enough**, because counting needs only `map`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selfcheck/SelfCheckBook.java:count_lines}}
```

Where this lives: [One method for any container](../functional/functor.md#one-method-for-any-container).
~~~

~~~admonish question title="Checkpoint 11: write `receiptLine`" id="check-self-receipt"
Write `receiptLine`, which builds `"<order id> to <email>"` from an order and an email address looked up independently, in any container. Ask for the least capability it needs, and say what it gives with `Instances.validated(Semigroups.list())` when neither was found.
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-receipt-answer"
**An `Applicative`**, because the two values do not depend on each other, so `map2` combines them:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selfcheck/SelfCheckBook.java:receipt_line}}
```

With `Validated`'s instance and a semigroup that joins lists, it gives both errors.

Where this lives: [One method for any container](../functional/applicative.md#one-method-for-any-container).
~~~

~~~admonish question title="Checkpoint 12: write `fromCacheOr`" id="check-self-cache"
Write `fromCacheOr`, which gives the order from a cache lookup, or calls the order service when the cache missed, in any container with an error type. The service must not be called when the cache has the order.
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-cache-answer"
**A `MonadError`, with the service call in a handler**, so it runs only when the cache missed. `recoverWith` would take the service's result as a value, so Java would call the service first, whatever the cache gave:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selfcheck/SelfCheckBook.java:from_cache_or}}
```

Where this lives: [Should you use `recoverWith` or `handleErrorWith`?](../functional/monad_error.md#constant-fallbacks-recover-and-recoverwith)
~~~

~~~admonish example title="See Example Code"
**The answers' code is [SelfCheckBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selfcheck/SelfCheckBook.java), and [SelfCheckBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/selfcheck/SelfCheckBookTest.java) holds every result an answer predicts.** The build also checks the refusal in Checkpoint 1 against javac's own words, and compiles the two types in Checkpoint 6. Open them after the twelve, since they hold the answers.
~~~

---

## Where to go next {#where-to-go-next}

A question counts when every part of your answer was right before you opened it, and code you wrote counts when it asks for the same capability as the answer's and gives the same results. Whatever your score, open the *Where this lives* link of any answer you missed.

| Your score | Next step |
|---|---|
| 10 to 12, with Checkpoints 10 to 12 among them | You can ship code written once for any container. Read the rest of the chapter as a task calls for it: [Higher-Kinded Types](ch_intro.md) for the encoding in depth, [Type Classes](../functional/ch_intro.md) for `Traverse`, `Selective` and the rest, [Core Types](../monads/ch_intro.md) for each type, and the [Quick Reference](quick_reference.md) or [FAQ](faq.md) when you hold a question |
| Any other score from 6 to 11 | Reread the sections you missed, try those questions again, then carry on as for 10 to 12 |
| 5 or fewer | Go back to [One Line, Six Layers](one_line_six_layers.md) and read each Ship page through to [For Comprehensions](../functional/for_comprehension.md), working the [Foundations journey](../tutorials/coretypes/foundations_journey.md) alongside, then take the questions again |

---

**Previous:** [For Comprehension](../functional/for_comprehension.md)
**Next:** [Higher-Kinded Types](ch_intro.md)
