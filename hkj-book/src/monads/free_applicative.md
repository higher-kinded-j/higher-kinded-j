<!-- description: Hold a program of independent steps as data in Java with Free Applicative: read an order's prices before they run, fetch them in one call, report every unknown SKU. -->

# Free Applicative: Programs You Can Read Whole

_No step waits on another, so the whole program is known before any of it runs._

~~~admonish abstract title="Free Applicative at a glance"
| | |
|---|---|
| **Models** | A program of instructions that do not depend on one another, held as data and known whole before it runs |
| **Replaces** | Independent calls made one at a time, when you want to count, check or batch them first |
| **Effect Path** | [`FreeApPath`](../glossary/effect-paths.md#freeappath) |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:free_applicative}}
| **Choose it when** | No step needs the result of another; otherwise [Free](free_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Build an order's prices as one program whose instructions are all known before it runs
- Count a program's instructions and list what it will ask for, without running it
- Price every line with one catalogue call, by reading the program first
- Report every unknown SKU at once, by interpreting into `Validated`
- Choose between Free Applicative and Free, and hold one inside the other
~~~

~~~admonish example title="See Example Code"
**The code on this page is [FreeApplicativeBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java)**: the page includes it, so the build compiles and runs it. [FreeApplicativeBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBookTest.java) holds what the page says each method does.
~~~

~~~admonish tip title="Where Free Applicative fits"
[`@EffectAlgebra`](../effect/effect_handlers.md#defining-effects) generates an instruction set's encoding, its `Functor` and its interpreter base class, and those serve a Free Applicative program as they serve a [Free](free_monad.md) one. Its generated constructors build `Free` programs only, so a `FreeAp` program lifts each instruction itself, as this page's `priceOf` does. [FreeApPath](../effect/path_freeap.md) documents the Path's operations; this page shows what independent steps buy you.
~~~

---

## Steps that do not depend on one another {#steps-that-do-not-depend-on-one-another}

Before checkout, the order service prices each line of a basket again from today's catalogue. In a [Free](free_monad.md) program each step may wait on the result of the one before, so an analysis [sees only as far as the first step that waits](free_monad.md#what-can-be-seen-before-it-runs). No line's price waits on another's, so the basket can be held as a Free Applicative program instead. Its steps are combined with `zipWith`, which gives neither step the other's result, and so every instruction is known before the program runs.

The instructions are the `OrderOp` records the [Free](free_monad.md#instructions-held-as-data) page uses. Each carries a continuation `k`, from the instruction's result to what the program does next, and `@EffectAlgebra` generates `OrderOpKind`, `OrderOpFunctor` and `OrderOpInterpreter` from them:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/OrderOp.java:order_op}}
```

The code on this page uses these imports:

<!-- verify -->
```java
import static org.higherkindedj.example.book.foundations.OrderOpKindHelper.ORDER_OP;
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.OrderOp;
import org.higherkindedj.example.book.foundations.OrderOpFunctor;
import org.higherkindedj.example.book.foundations.OrderOpInterpreter;
import org.higherkindedj.example.book.foundations.OrderOpKind;
import org.higherkindedj.example.book.foundations.OrderOpOps;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.effect.FreeApPath;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
```

`Path.freeApLift` puts one instruction on a `FreeApPath`. `Function.identity()` is its continuation, which hands the price straight back as the step's result:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:steps}}
```

A line's total is its price times its quantity, and the basket's total adds the lines together with `zipWith`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:program}}
```

Nothing has been priced. `BASKET` holds one lamp and four bulbs, added last week at 38.00 and 2.40 each. The catalogue now prices a `LAMP` at 40.00 and a `BULB` at 2.50, so the basket comes to 50.00.

## Reading a program before it runs {#reading-a-program-before-it-runs}

An analysis is an interpreter too. This one turns each instruction into the SKU it would price, held in a [Const](const_type.md), which carries a value through the program and ignores what each step would give:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:skus}}
```

`FreeApAnalyzer` counts the instructions, and `analyseWith` runs the analysis, joining the lists with `Monoids.list()`, a [Monoid](../functional/semigroup_and_monoid.md) that appends:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:read}}
```

Nothing has been priced yet. A program of any length can be read: a basket of ten thousand lines is counted, analysed and run without overflowing the stack.

## One catalogue call for the whole basket {#one-catalogue-call-for-the-whole-basket}

Priced one line at a time, a basket of ten lines makes ten calls to the catalogue service: the N+1 problem, as when a loop of `findById` calls could have been one `IN (...)` query. Read first, it makes one call. `priceInOneCall` lists the SKUs, asks the catalogue for all of them at once, and then runs the program with an interpreter that prices from what came back:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:catalogue}}
```

A stand-in catalogue records each call it is given, so the run can count them:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:one_call}}
```

`foldMap` hands the instructions to the interpreter in the order `zipWith` and `map2` wrote them, here the lamp and then the bulbs.

## Every unknown SKU at once {#every-unknown-sku-at-once}

An interpreter into [`Validated`](validated_monad.md) gives each price, or a failure naming the SKU the catalogue does not know:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:checked}}
```

This basket holds two SKUs the catalogue does not know. `Validated`'s `ap` keeps the failures of both sides, so the run reports both:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:every_unknown}}
```

The same lines as a `Free` program, interpreted the same way, report only the rug. Each of its steps waits on the one before, and `Validated`'s `flatMap` has no result to hand on after a failure.

## Free or Free Applicative {#free-or-free-applicative}

Ask whether a step needs the result of one before it. If it does, the program is a [Free](free_monad.md) one. If none does, it can be a Free Applicative one, and it gains what this page has shown:

| | `Free` | Free Applicative |
|---|---|---|
| A step can | Wait on the result before it, and choose what comes next | Not see any other step's result |
| Known before it runs | Up to the first step that waits on a result | Every instruction |
| Built with | `via` | `zipWith` and `map` |
| Run in | A `Monad` | An `Applicative`, such as `Validated`, which keeps every failure, or `Const`, which reads the program |

Independent instructions can also run at the same time. Interpreted into `CompletableFuture`, whose `ap` combines futures already running, an interpreter that starts each price with `supplyAsync` prices the lamp and the bulbs at once.

## Inside a Free program {#inside-a-free-program}

A real program often has both kinds of step: the basket's prices, which are independent, then a decision that needs their total. `Free.Ap` holds a Free Applicative program as one step of a `Free` one:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:inside_free}}
```

`LIMIT` is 30.00, and the basket comes to 50.00:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:run_inside_free}}
```

`ProgramAnalyser` counts the basket as one step, a parallel scope, and does not look inside it. Read the basket with `FreeApAnalyzer` before you wrap it.

## A program always gives a value {#a-program-always-gives-a-value}

`Path.freeApPure(null, functor)` and `FreeAp.pure(null)` throw at the call, and a `map` or `zipWith` function that returns `null` fails the program when it is interpreted. [A Task Always Gives a Value](vtask_monad.md#a-task-always-gives-a-value) sets out the rule every lazy type follows.

## Creating instances {#creating-instances}

`Path.freeApLift(fa, functor)` lifts one instruction onto a `FreeApPath`, `Path.freeApPure(value, functor)` holds a result with no instruction, and `Path.freeAp(freeAp, functor)` puts a `FreeAp` on a Path. Without a Path, `FreeAp.lift(fa)` and `FreeAp.pure(value)` build a `FreeAp` directly, and need no `Functor`.

## Methods {#methods}

On a `FreeApPath`:

| Method | What it does |
|---|---|
| `map(f)` | Change the result |
| `zipWith(other, f)`, `zipWith3(b, c, f)` | Combine programs whose steps do not see one another's results |
| `foldMap(interpreter, monad)` | Run the program, giving a `GenericPath` in the target monad |
| `foldMapKind(interpreter, applicative)` | Run the program in any `Applicative`, giving a `Kind` |
| `toFreeAp()` | Get the `FreeAp` |

On a `FreeAp`, and with `FreeApAnalyzer`:

| Method | What it does |
|---|---|
| `map2(other, f)`, `ap(ff)` | Combine two programs |
| `foldMap(interpreter, applicative)` | Run the program, giving a `Kind`; `analyse(...)` is the same call, named for an analysis |
| `retract(applicative)` | Run a program whose instructions are already an `Applicative`, such as `IO`, with no interpreter |
| `FreeApAnalyzer.countOperations(program)` | Count the instructions |
| `FreeApAnalyzer.collectOperations(program)`, `groupByType(program, narrow)` | List the instructions, or count them by record type |
| `FreeApAnalyzer.containsOperation(program, test)` | Ask whether any instruction passes a test |
| `FreeApAnalyzer.analyseWith(program, analysis, monoid)` | Run an analysis into `Const`, joining its values with the monoid |

## Working with Kind {#working-with-kind}

`FREE_AP.widen` and `FREE_AP.narrow`, from `FreeApKindHelper`, convert between a `FreeAp` and its `Kind`. Generic code takes it through `FreeApApplicative.instance()`, whose `map2` builds the program as `FreeAp`'s own does:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/freeap/FreeApplicativeBook.java:kind}}
```

`FreeApApplicative` has no `flatMap`: a Free Applicative program is an `Applicative` and never a `Monad`, which is what keeps every instruction in view.

## When to use Free Applicative {#when-to-use-free-applicative}

| You need | Use |
|---|---|
| Independent lookups counted, checked or batched before they run | Free Applicative |
| Every failure among independent checks | Free Applicative run in `Validated`, or [Validated](validated_monad.md) directly |
| A step that needs another's result | [Free](free_monad.md), holding a Free Applicative step where the steps are independent |
| Several instruction sets in one program | [EitherF](eitherf.md) |
| Independent work run once, one way | [VTask](vtask_monad.md)'s `Par`, or a direct call |

~~~admonish info title="Hands-On Learning"
Practise building programs in [Tutorial 10: Free Applicative](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial10_FreeApplicative.java) (6 exercises), and reading them in [Tutorial 11: Static Analysis](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial11_StaticAnalysis.java) (10 exercises), from the [Advanced Patterns](../tutorials/coretypes/advanced_journey.md) journey.
~~~

---

**Previous:** [Free](free_monad.md)
**Next:** [EitherF](eitherf.md)
