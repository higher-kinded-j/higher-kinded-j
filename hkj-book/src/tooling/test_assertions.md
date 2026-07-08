# Testing With hkj-test: Fluent Assertions for HKJ Types

~~~admonish info title="What You'll Learn"
- How to add `hkj-test` to a project as a test-scope dependency
- The shape of the assertion API for the simple types, the effect types, and the monad transformers
- How to assert on chained, lazy, or stateful HKJ values without unwrapping by hand
- How to drop into Java 25's `import module` syntax to bring every helper into scope in one line
~~~

`hkj-test` is a small, focused companion to `hkj-core`: an AssertJ-flavoured assertion module covering every HKJ type a test is likely to touch. It is independent of `hkj-checker` and the Gradle/Maven plugins; you can adopt it without changing anything else in your build.

---

## Adding the Dependency

`hkj-test` is published alongside the rest of Higher-Kinded-J on Maven Central.

### Gradle

```kotlin
dependencies {
    testImplementation("io.github.higher-kinded-j:hkj-test:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.higher-kinded-j</groupId>
    <artifactId>hkj-test</artifactId>
    <version>LATEST_VERSION</version>
    <scope>test</scope>
</dependency>
```

The artifact pulls AssertJ in transitively via `requires transitive`, so consumers do not need to add it again. It also `requires transitive org.higherkindedj.core`, which means a single dependency declaration is enough whether you intend to test against `Either`, `IO`, or any of the transformers.

---

## What Is Covered

Every public HKJ type a user is likely to assert on has a dedicated assertion class. All assertions live in `org.higherkindedj.hkt.assertions`.

| Category | Helpers |
|----------|---------|
| Discriminated unions | `EitherAssert`, `MaybeAssert`, `TryAssert`, `ValidatedAssert`, `LazyAssert` |
| Reader / Writer / State | `ReaderAssert` (with `ReaderResultAssert`), `WriterAssert`, `StateAssert` |
| Effect types | `IOAssert`, `VTaskAssert`, `VStreamAssert` |
| Effect paths | `VTaskPathAssert`, `VStreamPathAssert`, `VResultPathAssert`, `VTaskContextAssert` |
| Monad transformers | `EitherTAssert`, `MaybeTAssert`, `OptionalTAssert`, `ReaderTAssert`, `StateTAssert`, `WriterTAssert` |
| Free algebra | `FreeAssert`, `EitherFAssert` |

Each entry point follows the AssertJ convention `assertThatXxx(actual)`:

```java
assertThatEither(result);
assertThatMaybe(value);
assertThatTry(computation);
```

---

## Assertions for the Simple Types

The discriminated-union and value-bearing types share a common shape: a state predicate, a value-equality check, a value-satisfies-consumer escape hatch, and the usual null variants.

### `EitherAssert`

```java
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

Either<DomainError, Order> result = orderService.process(request);

assertThatEither(result)
    .isRight()
    .hasRight(expectedOrder);

assertThatEither(failure)
    .isLeft()
    .hasLeftSatisfying(error ->
        assertThat(error).isInstanceOf(ValidationFailure.class));
```

### `MaybeAssert` and `ValidatedAssert`

```java
assertThatMaybe(lookup).isJust().hasValue("alice");
assertThatMaybe(lookup).isNothing();

assertThatValidated(form)
    .isInvalid()
    .hasErrorSatisfying(errors -> errors.size() == 2, "two errors collected");
```

### `LazyAssert`

`Lazy` carries its own evaluation lifecycle, so the assertions track it explicitly:

```java
Lazy<Integer> deferred = Lazy.defer(() -> compute());

assertThatLazy(deferred).isNotEvaluated();
assertThatLazy(deferred).whenForcedHasValue(42).isEvaluated();
assertThatLazy(failing).whenForcedThrows(IllegalStateException.class);
```

### `WriterAssert` and `StateAssert`

These cover both halves of the wrapped pair:

```java
assertThatWriter(writer)
    .hasValue(42)
    .hasLog("computed: ");

assertThatStateTuple(result)
    .hasValue("processed")
    .hasState(5);
```

---

## Assertions for the Effect Types

Effect types are lazy, so the assertions take care of running them. `IOAssert` and `VTaskAssert` follow a `whenExecuted()` / `whenRun()` chain; `VStreamAssert` materialises the stream once and lets you make repeated claims about it.

### `IOAssert`

```java
import static org.higherkindedj.hkt.assertions.IOAssert.assertThatIO;

IO<Integer> effect = IO.delay(() -> 1 + 1);

assertThatIO(effect).whenExecuted().hasValue(2);
assertThatIO(effect).isNotExecutedYet();
assertThatIO(effect).isRepeatable();

IO<String> failing = IO.delay(() -> { throw new IllegalStateException("kaboom"); });

assertThatIO(failing)
    .throwsException(IllegalStateException.class)
    .withMessageContaining("kaboom");
```

### `VTaskAssert`

```java
VTask<Integer> task = VTask.delay(() -> heavyComputation());

assertThatVTask(task)
    .whenRun()
    .succeeds()
    .hasValue(expected)
    .completesWithin(Duration.ofSeconds(1));
```

### `VResultPathAssert`

A `VResultPath<E, A>` run has three possible outcomes, and the assertion covers all of them: a typed success (`Right`), a typed domain error (`Left`), or a defect (an exception outside the typed channel). The path is executed once and the outcome cached for the rest of the chain:

```java
VResultPath<DomainError, Order> path = Path.vresultDefer(() -> service.load(orderId));

assertThatVResultPath(path).isRight().hasRight(expectedOrder);

assertThatVResultPath(failing)
    .isLeft()
    .hasLeftSatisfying(error ->
        assertThat(error).isInstanceOf(NotFound.class));

assertThatVResultPath(defective)
    .hasDefect()
    .withDefectType(IllegalStateException.class);
```

### `VStreamAssert`

```java
VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3));

assertThatVStream(stream).producesElements(1, 2, 3);
assertThatVStream(stream).hasCount(3);
assertThatVStream(stream).isEmpty();          // for empty streams

assertThatVStream(failingStream)
    .failsWithExceptionType(IllegalStateException.class);
```

---

## Assertions for Monad Transformers

Transformer assertions take an extra argument: an `unwrapper` function that pulls the transformer's outer monad back into a plain `Optional` so the assertion can introspect it. The pattern mirrors how transformer code typically composes outer and inner monads.

```java
import static org.higherkindedj.hkt.assertions.EitherTAssert.assertThatEitherT;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

MonadError<OptionalKind.Witness, Unit> outerMonad = Instances.monadError(optional());

private <E, A> Optional<Either<E, A>> unwrap(Kind<OptionalKind.Witness, Either<E, A>> kind) {
    return OPTIONAL.narrow(kind);
}

Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> kind =
    EITHER_T.widen(EitherT.right(outerMonad, 42));

assertThatEitherT(kind, this::unwrap)
    .isPresentRight()
    .hasRightValue(42);
```

The same shape applies to `MaybeTAssert`, `OptionalTAssert`, `ReaderTAssert`, `StateTAssert`, and `WriterTAssert`. The `Reader`, `State`, and `Writer` variants additionally provide `whenRunWith(env)` / `whenRunWith(initialState)` to drive the underlying computation before asserting.

---

## Test Fixtures

### `SteppableClock`

A clock that only moves when told to — pair it with `TimeSource.of(clock)` and time-dependent code is exercised by stepping the clock, not sleeping:

``` java
import org.higherkindedj.hkt.assertions.SteppableClock;
import org.higherkindedj.hkt.time.TimeSource;

SteppableClock clock = SteppableClock.startingAt(Instant.parse("2026-07-07T00:00:00Z"));
var service = new InMemoryInventoryService(TimeSource.of(clock));

service.reserve(order);                    // hold expires 15 minutes from "now"
clock.advance(Duration.ofMinutes(16));     // time passes - instantly
service.reserve(other);                    // the expired hold is reclaimed
```

Stepping is atomic (safe to advance from the test thread while virtual threads read), and `withZone` honours the `java.time.Clock` contract — the zoned view shares the same steppable timeline.

---

## Java 25: One-line Module Import

`hkj-test` is published as a proper JPMS module named `org.higherkindedj.test`. On Java 25 with `--enable-preview` (already enabled across the HKJ project), JEP 511's module-import syntax reduces the per-class import boilerplate to a single line:

```java
import module org.higherkindedj.test;
import module org.higherkindedj.core;   // brings in Either, Maybe, Try, IO, ...

class UserServiceTest {
    @Test
    void returns_user() {
        Either<DomainError, User> result = userService.findById("u1");
        EitherAssert.assertThatEither(result).isRight();
    }
}
```

Both modules are now in scope; no further imports are required.

---

## Coverage Guarantees

Every public assertion method on every assertion class is covered by a dedicated `*AssertContractTest` in `hkj-test/src/test/java`. Each contract spec enumerates rows of `(label, passingInput, failingInput, chain)` and the framework dispatches each row as two dynamic tests: one verifying the chain succeeds on the passing input, another verifying it throws `AssertionError` on the failing input.

Coverage is enforced at 100% line and 100% instruction on the `hkj-test` bundle. Adding a new assertion method without a corresponding contract row will fail the project's `check` task, so the test surface stays exhaustive over time.

---

## Optic Laws

`org.higherkindedj.optics.laws` publishes law-verification helpers for every optic family — the properties that *define* a lawful `Iso`, `Lens`, `Prism`, `Affine`, or `Traversal` — in the same flat `assert…` style as the `hkt.laws` type-class helpers:

``` java
import org.higherkindedj.optics.laws.LensLaws;
import org.higherkindedj.optics.laws.PrismLaws;

@Test
void nameLensIsLawful() {
    LensLaws.assertLensLaws(UserLenses.name(), new User("Ada", 36), "Grace", "Alan");
    // get-set, set-get (both values) and set-set, with counterexample-bearing messages
}

@Test
void statusPrismIsLawful() {
    PrismLaws.assertPrismLaws(ShapePrisms.circle(), new Circle(1.0), new Square(2.0));
}
```

`ValidatedPrismLaws` joins the family for the validated-boundary optic (parse-build and the section law build-parse). Each family also exposes the individual laws (`assertGetSet`, `assertBuildMatch`, `assertSetNoOpWhenAbsent`, …) for targeted checks, and failures name the violated law with the offending values — `"Lens set-get: get(set(Grace, …)) == the value set; got Ada"`. Guard rails reject vacuous fixtures (equal set-set values, non-matching prism sources). Drive broader coverage with `@ParameterizedTest` or property fixtures at the call site.

~~~admonish tip title="See Also"
- [Manual Gradle and Maven Setup](manual_setup.md) - Adding hkj-test to projects that do not use the HKJ build plugin
- [Build Plugins](gradle_plugin.md) - Other test-time tooling
- [What Are Optics?](../optics/optics_intro.md) - The optic families these laws define
~~~

---

**Previous:** [Claude Code Skills](claude_code_skills.md)
