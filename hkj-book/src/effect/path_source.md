# Custom Paths with `@PathSource`

_Give your effect a named Path class, generated when you build._

~~~admonish info title="What You'll Learn"
- Decide whether `GenericPath` is enough for your effect, or a generated Path is worth a build step
- Generate a Path for your effect's witness, then compose with `map`, `via` and `run`
- Predict which methods each `capability` level generates
- Recover from your effect's own error type with `errorType` and `RECOVERABLE`
- Name and place the generated class with `suffix`, `targetPackage` or a nested type
~~~

~~~admonish example title="See Example Code"
**The code on this page is [PathSourceBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/PathSourceBook.java)**, which the build compiles and runs. The two annotated effects sit beside it in the same package: [Traced.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/Traced.java) and [Outcome.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/Outcome.java), each with its witness, kind helper and `Monad`.
~~~

Say your library ships `Traced<A>`, a value together with the log of steps that produced it.
[`GenericPath`](path_generic.md) can wrap it, given a `Monad` you write for it, but every signature
that holds one then spells out the effect's witness: `GenericPath<TracedKind.Witness, Integer>`.
`@PathSource` has the annotation processor write a Path class for the effect instead,
`TracedPath<Integer>`, with `map`, `via` and the rest declared on it.

Think of `CompletableFuture`, which gives an asynchronous task a named type with `thenApply` and
`thenCompose`. A generated Path gives your effect the same: a named type with `map` and `via`, so
callers compose it without handling its [`Kind`](../glossary/type-system.md#kind). Unlike a
`CompletableFuture`, it runs nothing itself. It holds your effect's value and the `Monad` that
composes it.

---

## When `GenericPath` is enough {#when-genericpath-is-enough}

Stay with `GenericPath` when your code needs `ForPath` or `Path.from`, or when your witness takes
type arguments. Generate a Path when your users write the type in their own signatures, or when
recovery should be typed by your effect's error.

| You need | `GenericPath<F, A>` | A generated Path |
|---|---|---|
| No build step | ✓ | ✗: the annotation processor writes it |
| A witness with type arguments, as in `Result<E, A>` | ✓ | ✗: refused |
| `ForPath`, `Path.from`, a `PathProvider` | ✓ | ✗ |
| `toMaybePath`, `toEitherPath`, `mapK`, `zipWith3` | ✓ | ✗ |
| One type parameter in your users' signatures | ✗: `GenericPath<TracedKind.Witness, A>` | ✓: `TracedPath<A>` |
| A `via` that javac holds to the same Path | ✗: javac takes any Path, and only the [HKJ checker](../tooling/compile_checks.md) reports another | ✓ |
| A `recover` typed by your effect's error | ✗: each caller names the error type | ✓: at `RECOVERABLE` |

`ForPath` has an entry point for `GenericPath`, and a `PathProvider` returns a `Chainable`, which is
what `Path.from` hands back. A generated Path is neither: `Chainable` is sealed to the library's own
Paths. The choice is not all or nothing, though. `Path.generic` takes a generated Path's `Kind`
back into a `GenericPath`, with the same `Monad`, wherever one is needed:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/PathSourceBook.java:to_generic}}
```

---

## Generate your first Path

The running example is `Traced`. Here is what you write, and what the processor writes:

| You write | Why |
|---|---|
| A [witness](../glossary/type-system.md#witness-type), the marker class `Traced`'s `Kind` is indexed by | The generated Path wraps a `Kind` of it |
| A `Monad` over the witness, or a `MonadError` if the effect can fail | The Path composes through it |
| A kind helper, whose `narrow` turns the `Kind` back into a `Traced` | `run` hands back the `Kind` |
| `@PathSource` on the effect type | The processor writes `TracedPath` from it |

[Extending the HKT simulation](../hkts/extending-simulation.md#scenario-2-integrating-a-custom-library-type)
covers the first three in depth. The witness is a marker class inside the effect's `Kind` interface:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/TracedKind.java:traced_kind}}
```

`Traced` implements that interface, so a `Traced` is its own `Kind`, and it carries the annotation,
naming the witness:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/Traced.java:traced}}
```

The `Monad` keeps the log: `map` leaves it alone, and `flatMap` appends the next step's log to this
one's. The kind helper, `TracedKindHelper.TRACED`, narrows a `Kind` back to a `Traced`, and throws
`KindUnwrapException` for anything else, as each of the library's own helpers does.

~~~admonish example title="TracedMonad" collapsible=true
```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/TracedMonad.java:traced_monad}}
```
~~~

Then build. The HKJ Gradle and Maven plugins put the annotation processor on the processor path,
and add `hkj-annotations`, where `@PathSource` lives:

```gradle
// build.gradle.kts
plugins {
    id("io.github.higher-kinded-j.hkj") version "LATEST_VERSION"
}
```

The [Quickstart](../quickstart.md) shows the Maven plugin, and [Manual Setup](../tooling/manual_setup.md)
a build without either. The processor writes `TracedPath.java` into the package of `Traced`. Gradle
keeps it under `build/generated/sources/annotationProcessor/java/main`, and Maven under
`target/generated-sources/annotations`.

`TracedPath.of` takes the `Traced` itself and the `Monad` that composes it. `map` changes the value
and keeps the log. `via` runs a step that returns another `TracedPath`, and its log joins this
one's. `run` hands back the `Kind`, which `TRACED.narrow` turns into a `Traced`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/PathSourceBook.java:first_path}}
```

The Path keeps the `Monad` you pass to `of`, because a generated class has no way to look one up.
Your library can hide it from callers behind a factory of its own, such as a static method that
returns `TracedPath.of(traced, TracedMonad.INSTANCE)`.

`via` takes a function returning a `TracedPath`. `Path.just` returns a `MaybePath`, so javac
reports `bad return type in lambda expression`:

<!-- verify:rejects "bad return type in lambda expression" -->
```java
TracedPath<Integer> discounted = priced.via(pence -> Path.just(pence - 200));
```

~~~admonish tip title="You can ship now"
You can give your library's effect a Path of its own: annotate the type, build, and compose with
`map`, `via` and `run`. If your effect can fail, read [Recovery with `errorType`](#recovery-with-errortype)
before you ship: `RECOVERABLE` changes what `of` and `pure` take, and a factory of your own keeps
your callers clear of that. The rest of the page is for when you need more.
~~~

---

## What gets generated {#what-gets-generated}

The processor writes one class, `public final class TracedPath<A>`, marked `@Generated`. Its name
is the annotated type's name followed by `Path`, and it is written into the same package.

| Member | What it does |
|---|---|
| `of(kind, monad)` | Wraps a `Kind` of the witness, with the `Monad` that composes it |
| `pure(value, monad)` | Starts from a plain value, through the `Monad`'s `of` |
| `run()`, `runKind()` | Return the wrapped `Kind`; the two are the same |
| `map`, `peek`, and the capability's methods | Each returns a new `TracedPath`: see [Choosing a capability](#choosing-a-capability) |
| `equals`, `hashCode` | Compare the wrapped `Kind`, so two Paths over equal `Kind`s are equal |
| `toString` | Names the class, then shows the `Kind` |

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/PathSourceBook.java:object_methods}}
```

**The annotated type gives the Path its name, and its Javadoc link.** The processor reads none of
its methods, and does not check that the witness is the type's own.

---

## Choosing a capability {#choosing-a-capability}

`capability` decides which methods the class gets. Each level adds to the one before it, and the
default is `CHAINABLE`. `Chainable` and the [capability interfaces](capabilities.md) that extend it
are sealed to the library's own Paths, so from `COMBINABLE` up the class implements `Combinable`,
and declares the rest of its methods itself.

| `capability` | Implements | Adds | Choose it when |
|---|---|---|---|
| `COMPOSABLE` | `Composable<A>` | `map`, `peek` | Callers should only transform values |
| `COMBINABLE` | `Combinable<A>` | `zipWith` | Callers combine results that do not depend on each other |
| `CHAINABLE` (default) | `Combinable<A>` | `via`, `then`, `flatMap` | Callers run one step after another |
| `RECOVERABLE` | `Combinable<A>` | `recover`, `recoverWith`, `mapError` | Your effect has an error type: see [Recovery](#recovery-with-errortype) |

**Every level takes a `Monad` in `of` and `pure`.** A lower level hides methods from your callers,
but still needs the full `Monad`. The generated `zipWith` combines through the `Monad`'s
`flatMap`, so it runs this Path's step, then the other's:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/PathSourceBook.java:zip_with}}
```

~~~admonish warning title="zipWith combines only with another TracedPath"
`zipWith` takes any `Combinable`, as the interface declares it, so javac accepts another Path
there. The generated `zipWith` then throws `IllegalArgumentException` at the call, with a message
beginning `Cannot zipWith non-TracedPath`:

<!-- verify -->
```java
TracedPath<Integer> mixed = priced.zipWith(Path.just(350), Integer::sum);
```
~~~

`peek` maps its action over the value, so the action runs when the effect produces the value. For
an eager effect such as `Traced` that is at the call, and for a lazy one, each time the effect runs.

---

## Recovery with `errorType` {#recovery-with-errortype}

`Traced` cannot fail. For an effect that can, set `errorType` to its error type and `capability`
to `RECOVERABLE`. The example's `Outcome` holds either a value or a `Problem`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/Outcome.java:outcome}}
```

**At `RECOVERABLE`, `of` and `pure` take a `MonadError` as well as the `Monad`.** `MonadError`
extends `Monad`, so one instance serves both: `OutcomeMonad` implements
`MonadError<OutcomeKind.Witness, Problem>`, and the example passes it as each. `recover`,
`recoverWith` and `mapError` then take functions of a `Problem`, so javac checks what each does
with it:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/PathSourceBook.java:recover}}
```

A function written for another error type does not compile. The error reads
`Function<String,String> cannot be converted to Function<? super Problem,? extends String>`:

<!-- verify:rejects "String> cannot be converted to" -->
```java
OutcomePath<String> backOrdered =
    reservation.recover((String reason) -> "back-ordered: " + reason);
```

~~~admonish tip title="Why this matters"
`GenericPath` declares `recover` over an error type each caller names, and nothing checks that
name against the effect. Name it wrongly, and the code compiles, then throws `ClassCastException`
when the error reaches it:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/effect/pathsource/PathSourceBook.java:generic_recover}}
```

A generated Path types its recovery by `errorType`, so the same mistake fails the build instead.
~~~

**Set `errorType` without `RECOVERABLE`, or the reverse, and the processor writes a note.** The Path is generated
without recovery methods. A note stops nothing, even under `-Werror`, so read the
compiler output for it. Here `errorType` is set and `capability` left at its default:

<!-- verify:reports "has no effect on the generated 'OutcomePath'" -->
```java
@PathSource(witness = OutcomeKind.Witness.class, errorType = Problem.class)
public sealed interface Outcome<A> extends OutcomeKind<A> {

  record Ok<A>(A value) implements Outcome<A> {}

  record Failed<A>(Problem problem) implements Outcome<A> {}
}
```

```
Note: @PathSource: errorType 'Problem' has no effect on the generated 'OutcomePath'. The default
capability, CHAINABLE, generates no recovery methods; recover, recoverWith and mapError are
generated only for RECOVERABLE. For them, set capability = PathSource.Capability.RECOVERABLE: of
and pure then take a MonadError<OutcomeKind.Witness, Problem>, so existing calls must pass one.
Otherwise remove errorType.
```

The other half, `RECOVERABLE` with no `errorType`, draws a note that opens
`capability RECOVERABLE generates no recovery methods on 'OutcomePath' without an errorType`:

<!-- verify:reports "generates no recovery methods on 'OutcomePath' without an errorType" -->
```java
@PathSource(witness = OutcomeKind.Witness.class, capability = PathSource.Capability.RECOVERABLE)
public sealed interface Outcome<A> extends OutcomeKind<A> {

  record Ok<A>(A value) implements Outcome<A> {}

  record Failed<A>(Problem problem) implements Outcome<A> {}
}
```

---

## Naming and placement {#naming-and-placement}

The Path takes the annotated type's simple name, followed by `suffix`, and is written into the
type's package unless `targetPackage` names another. For `Traced` in `com.shop.trace`:

| You write | The processor writes |
|---|---|
| `@PathSource(witness = TracedKind.Witness.class)` | `com.shop.trace.TracedPath` |
| `suffix = "Steps"` | `com.shop.trace.TracedSteps` |
| `targetPackage = "com.shop.paths"` | `com.shop.paths.TracedPath` |
| `suffix = ""` and `targetPackage = "com.shop.paths"` | `com.shop.paths.Traced` |
| `@PathSource` on `Traced` nested in a class `Effects` | `com.shop.trace.TracedPath`, a top-level class |

**A `targetPackage` needs the types the Path names to be `public`.** The Path names the witness,
and the error type at `RECOVERABLE`; the processor refuses either when it cannot reach it, as
[What the processor refuses](#what-the-processor-refuses) quotes. The Path also imports the
annotated type's top-level class for its Javadoc link. A `targetPackage` on a type whose top-level
class is not `public` is not supported yet: the generated file does not compile.

---

## The fine print {#the-fine-print}

### What the processor refuses {#what-the-processor-refuses}

The processor reports each refusal as an error, in three sentences: what is wrong, why, and the
fix. The fix sentence is the one to act on; in short:

| The message says | What it means | Fix |
|---|---|---|
| `'Traced' is an enum` | `@PathSource` is on an enum or an annotation interface | Annotate the class, interface or record |
| `which is not a Java identifier` | The suffix leaves a name no class can have | Use letters, digits, `_` or `$` |
| `which Java does not allow as the name of a class` | The suffix makes a reserved word, such as `record` | Choose another suffix |
| `That is the type it is generated for` | An empty suffix lands the Path on the annotated type itself | Give a suffix, or a `targetPackage` |
| `A type of that name is already declared in the compilation` | Another type already has the Path's name | Change the suffix, or rename that type |
| `is not a package name` | `targetPackage` is not a package name | Give a name such as `com.shop.paths` |
| `witness 'int' is not a class` | The witness is a primitive, `void` or an array | Name the witness marker class |
| `is generic, and a class literal can name only its raw type` | The witness, or the error type at `RECOVERABLE`, takes type arguments | Use `GenericPath`, or a non-generic error type |
| `witness 'String' is not a witness of one type parameter` | The witness does not implement `WitnessArity<TypeArity.Unary>` | Name the witness marker class |
| `errorType 'int' is not a reference type` | The error type at `RECOVERABLE` is a primitive or `void` | Use a record describing the error |
| `witness 'TraceWitness' cannot be reached from 'com.shop.paths'` | The Path's package cannot see the witness or the error type | Make it `public`, or drop `targetPackage` |

A generic witness, such as `EitherKind.Witness<L>`, belongs with `GenericPath`, which takes its
`Kind` with the type arguments. The fix in each message says so.

~~~admonish example title="Declarations that produce them" collapsible=true
An enum:

<!-- verify:rejects "'Traced' is an enum" -->
```java
@PathSource(witness = TracedKind.Witness.class)
enum Traced { STARTED }
```

A suffix that is not part of a name:

<!-- verify:rejects "which is not a Java identifier" -->
```java
@PathSource(witness = TracedKind.Witness.class, suffix = "-steps")
record Traced<A>(A value, List<String> log) implements TracedKind<A> {}
```

A suffix that makes a reserved word, here `record`:

<!-- verify:rejects "which Java does not allow as the name of a class" -->
```java
@PathSource(witness = TracedKind.Witness.class, suffix = "ord")
record rec<A>(A value, List<String> log) implements TracedKind<A> {}
```

An empty suffix, with nothing to keep the Path apart from the type:

<!-- verify:rejects "That is the type it is generated for" -->
```java
@PathSource(witness = TracedKind.Witness.class, suffix = "")
record Traced<A>(A value, List<String> log) implements TracedKind<A> {}
```

A Path name another type already has:

<!-- verify:rejects "A type of that name is already declared in the compilation" -->
```java
record TracedPath(String note) {}

@PathSource(witness = TracedKind.Witness.class)
record Traced<A>(A value, List<String> log) implements TracedKind<A> {}
```

A target that is not a package name:

<!-- verify:rejects "is not a package name" -->
```java
@PathSource(witness = TracedKind.Witness.class, targetPackage = "com.shop.trace paths")
record Traced<A>(A value, List<String> log) implements TracedKind<A> {}
```

A witness that is not a class:

<!-- verify:rejects "witness 'int' is not a class" -->
```java
@PathSource(witness = int.class)
record Traced<A>(A value, List<String> log) implements TracedKind<A> {}
```

A generic witness:

<!-- verify:rejects "is generic, and a class literal can name only its raw type" -->
```java
@PathSource(witness = EitherKind.Witness.class)
record Traced<A>(A value, List<String> log) implements TracedKind<A> {}
```

A class that is not a witness:

<!-- verify:rejects "witness 'String' is not a witness of one type parameter" -->
```java
@PathSource(witness = String.class)
record Traced<A>(A value, List<String> log) implements TracedKind<A> {}
```

A primitive error type:

<!-- verify:rejects "errorType 'int' is not a reference type" -->
```java
@PathSource(
    witness = OutcomeKind.Witness.class,
    errorType = int.class,
    capability = PathSource.Capability.RECOVERABLE)
interface Outcome<A> extends OutcomeKind<A> {}
```

A witness the Path's package cannot see:

<!-- verify:rejects "cannot be reached from 'com.shop.paths'" -->
```java
final class TraceWitness implements WitnessArity<TypeArity.Unary> {}

@PathSource(witness = TraceWitness.class, targetPackage = "com.shop.paths")
record Traced<A>(A value, List<String> log) implements TracedKind<A> {}
```
~~~

### A witness another processor writes {#a-witness-another-processor-writes}

The witness may be a type another annotation processor generates in the same build. javac runs
the processors again over each batch of generated sources, a round at a time, so `@PathSource`
waits, and writes the Path in the round after the witness appears. A witness that never appears is
left for javac to report: `cannot find symbol`, or `package TracedKind does not exist` for a
`Witness` nested in a `Kind` interface that was never generated.

[`@EffectAlgebra`](effect_handlers_intro.md) writes such a witness, but an effect algebra comes with
a `Functor` and no `Monad`, so you have nothing to pass to `of`. Run an effect algebra through
[`FreePath`](path_free.md) instead.

### Deprecated capabilities {#deprecated-capabilities}

`EFFECTFUL` and `ACCUMULATING` are deprecated for removal in 0.5.0. Neither generates what its
name promises:

| Deprecated | Generates exactly what this does | So it lacks |
|---|---|---|
| `EFFECTFUL` | `CHAINABLE` | `unsafeRun`, `delay` and `async` |
| `ACCUMULATING` | `RECOVERABLE` | Error accumulation: `zipWith` stops at the first error |

<!-- verify:reports "has been deprecated and marked for removal" -->
```java
@PathSource(witness = TracedKind.Witness.class, capability = PathSource.Capability.EFFECTFUL)
record Traced<A>(A value, List<String> log) implements TracedKind<A> {}
```

javac warns at each use that `EFFECTFUL in Capability has been deprecated and marked for removal`,
which fails a build under `-Werror`. The
[`ReplaceDeprecatedPathSourceCapabilitiesRecipe`](../tooling/openrewrite.md#050-deprecation-migration)
replaces each with the level it generates.

---

~~~admonish info title="Key Takeaways"
* **Only `GenericPath` goes into `ForPath`**, or comes from `Path.from` and a `PathProvider`; `Path.generic` takes a generated Path's `Kind` back to one
* **A generated Path is a named type**, `TracedPath<A>`, whose `via` javac holds to itself
* **`of` and `pure` take your `Monad`**, at every level, and the `MonadError` too at `RECOVERABLE`
* **Recovery needs both `errorType` and `RECOVERABLE`**; either alone draws a note and no recovery
* **The annotated type names the Path**: the witness is what it wraps, and the `Monad` what composes it
~~~

~~~admonish tip title="See Also"
- [GenericPath](path_generic.md): the escape hatch every custom monad has without a build step
- [Capability Interfaces](capabilities.md): what `Composable`, `Combinable` and `Chainable` promise
- [PathProvider registration](../monads/vstream_advanced.md#pathprovider-spi-registration): registering a `PathProvider`, so `Path.from` finds a Path for your witness
- [Extending the HKT simulation](../hkts/extending-simulation.md): writing a witness, a helper and a `Monad`
- [Effect Handlers](effect_handlers_intro.md): `@EffectAlgebra`, for effects described as instructions
~~~

---

**Previous:** [GenericPath](path_generic.md)
**Next:** [TrampolinePath](path_trampoline.md)
