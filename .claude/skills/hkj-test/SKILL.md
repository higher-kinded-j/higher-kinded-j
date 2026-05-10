---
name: hkj-test
description: "Write tests against Higher-Kinded-J types using hkj-test: assertThatEither / assertThatMaybe / assertThatTry / assertThatIO / assertThatVTask / assertThatVStream / assertThatLazy / assertThatValidated / assertThatReader / assertThatWriter / assertThatStateTuple / assertThatEitherT / assertThatMaybeT / assertThatOptionalT / assertThatReaderT / assertThatStateT / assertThatWriterT / assertThatFree / assertThatEitherF, AssertJ fluent assertions for HKJ types, transformer assertion unwrappers, JEP 511 module imports, AssertContract pattern for testing custom AssertJ assertions"
---

# Higher-Kinded-J Test Assertions

You are helping a developer write tests against Higher-Kinded-J types. The `hkj-test` module ships fluent AssertJ assertion helpers for every HKJ type, so test code stays in the AssertJ idiom and reads like the business intent.

## When to load supporting files

- If the user wants to write **their own AssertJ extensions** with the same coverage guarantees, load `reference/contract-pattern.md`
- If the user is asking about **the underlying type semantics** (when does Right vs Left apply, how does VTask differ from IO), suggest `/hkj-guide`
- If the user wants **transformer-stack guidance** beyond just asserting on results, suggest `/hkj-guide`

---

## Adding the Dependency

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("io.github.higher-kinded-j:hkj-test:LATEST_VERSION")
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.github.higher-kinded-j</groupId>
    <artifactId>hkj-test</artifactId>
    <version>LATEST_VERSION</version>
    <scope>test</scope>
</dependency>
```

AssertJ and `hkj-core` are pulled in transitively. No further declarations are required.

---

## Picking the Right Assertion Class

Every assertion lives in package `org.higherkindedj.hkt.assertions`. Match the subject under test to its assertion class:

| Subject | Assertion class | Entry point |
|---------|-----------------|-------------|
| `Either<L, R>` | `EitherAssert` | `assertThatEither(actual)` |
| `Maybe<T>` | `MaybeAssert` | `assertThatMaybe(actual)` |
| `Try<T>` | `TryAssert` | `assertThatTry(actual)` |
| `Validated<E, A>` | `ValidatedAssert` | `assertThatValidated(actual)` |
| `Lazy<T>` | `LazyAssert` | `assertThatLazy(actual)` |
| `Reader<R, A>` | `ReaderAssert` | `assertThatReader(actual)` |
| `Writer<W, A>` | `WriterAssert` | `assertThatWriter(actual)` |
| `StateTuple<S, A>` | `StateAssert` | `assertThatStateTuple(actual)` |
| `IO<T>` | `IOAssert` | `assertThatIO(actual)` |
| `VTask<T>` | `VTaskAssert` | `assertThatVTask(actual)` |
| `VStream<A>` | `VStreamAssert` | `assertThatVStream(actual)` |
| `Kind<EitherTKind.Witness<F, E>, A>` | `EitherTAssert` | `assertThatEitherT(actual, unwrap)` |
| `Kind<MaybeTKind.Witness<F>, A>` | `MaybeTAssert` | `assertThatMaybeT(actual, unwrap)` |
| `Kind<OptionalTKind.Witness<F>, A>` | `OptionalTAssert` | `assertThatOptionalT(actual, unwrap)` |
| `Kind<ReaderTKind.Witness<F, R>, A>` | `ReaderTAssert` | `assertThatReaderT(actual, unwrap)` |
| `Kind<StateTKind.Witness<S, F>, A>` | `StateTAssert` | `assertThatStateT(actual, unwrap)` |
| `Kind<WriterTKind.Witness<F, W>, A>` | `WriterTAssert` | `assertThatWriterT(actual, unwrap)` |
| `Free<F, A>` | `FreeAssert` | `assertThatFree(actual)` |
| `EitherF<F, G, A>` | `EitherFAssert` | `assertThatEitherF(actual)` |

Every assertion class extends AssertJ's `AbstractAssert`, so `.as("description")`, `.describedAs(...)`, and `.overridingErrorMessage(...)` are available everywhere.

---

## Idioms by Subject Shape

### Discriminated unions (Either, Maybe, Try, Validated)

```java
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

// Both branches: state predicate, value-equality, value-satisfies
assertThatEither(result).isRight().hasRight(42);
assertThatEither(failure).isLeft().hasLeft(expectedError);
assertThatEither(result).isRight().hasRightSatisfying(v ->
    assertThat(v).isPositive().isLessThan(100));
```

`MaybeAssert` mirrors the shape with `isJust()` / `isNothing()`. `TryAssert` uses `isSuccess()` / `isFailure()`, plus `hasExceptionOfType(...)` and `hasExceptionSatisfying(...)`. `ValidatedAssert` uses `isValid()` / `isInvalid()`, plus `hasValueOfType` / `hasErrorOfType`.

### Lazy

`Lazy` carries an evaluation lifecycle. The assertions check it via the public `isEvaluated()` / `hasFailed()` accessors:

```java
Lazy<Integer> deferred = Lazy.defer(() -> compute());

assertThatLazy(deferred).isNotEvaluated();
assertThatLazy(deferred).whenForcedHasValue(42).isEvaluated();
assertThatLazy(failing).whenForcedThrows(IllegalStateException.class);
assertThatLazy(failedLazy).hasFailed();
```

### Reader / Writer / State

`ReaderAssert` is two-phase: supply the environment with `whenRunWith(env)`, then assert on the result. The result step extends `AbstractAssert<ReaderResultAssert<R, A>, A>`.

```java
assertThatReader(reader)
    .whenRunWith(config)
    .produces("expected")
    .matches(value -> value.startsWith("expected"));

assertThatWriter(writer)
    .hasValue(42)
    .hasLog("computed: ");

assertThatStateTuple(result)
    .hasValue("processed")
    .hasState(5);
```

### Effect types (IO, VTask, VStream)

IO and VTask are lazy. Drive them through `whenExecuted()` / `whenRun()` first:

```java
assertThatIO(effect).whenExecuted().hasValue(2);
assertThatIO(effect).isNotExecutedYet();
assertThatIO(effect).isRepeatable();
assertThatIO(failing).throwsException(IllegalStateException.class)
    .withMessageContaining("kaboom");

assertThatVTask(task).whenRun().succeeds().hasValue(expected)
    .completesWithin(Duration.ofSeconds(1));
assertThatVTask(task).runSafeSucceeds();   // delegates to TryAssert
assertThatVTask(failing).runSafeFails();   // cached: chain reuses the runSafe result

assertThatVStream(stream).producesElements(1, 2, 3);
assertThatVStream(stream).hasCount(3);
assertThatVStream(failingStream).failsWithExceptionType(IllegalStateException.class);
```

### Monad transformers

Transformer assertions need an extra `unwrap` function: pull the outer monad's contents out into a plain `Optional` so the assertion can introspect both layers. The pattern repeats across every transformer.

```java
import static org.higherkindedj.hkt.assertions.EitherTAssert.assertThatEitherT;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

private <E, A> Optional<Either<E, A>> unwrap(
        Kind<OptionalKind.Witness, Either<E, A>> kind) {
    return OPTIONAL.narrow(kind);
}

Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> kind =
    EITHER_T.widen(EitherT.right(outerMonad, 42));

assertThatEitherT(kind, this::unwrap)
    .isPresentRight()
    .hasRightValue(42);
```

The same shape applies to `MaybeTAssert`, `OptionalTAssert`, `ReaderTAssert`, `StateTAssert`, `WriterTAssert`. The `Reader`/`State`/`Writer` variants additionally provide `whenRunWith(env)` / `whenRunWith(initialState)` to drive the underlying computation before asserting.

### Free / EitherF

```java
Free<MaybeKind.Witness, String> program = Free.pure("hello");
assertThatFree(program).isPure().hasPureValue("hello");

Free<MaybeKind.Witness, Integer> chained = Free.<MaybeKind.Witness, Integer>pure(1)
    .flatMap(i -> Free.pure(i + 1));
assertThatFree(chained).isFlatMapped();

assertThatEitherF(EitherF.left(maybeKind)).isLeft();
```

---

## Java 25 Module Import

`hkj-test` is a JPMS module (`org.higherkindedj.test`) with `requires transitive` on `org.higherkindedj.core` and `org.assertj.core`. On Java 25 with `--enable-preview`, JEP 511 module-imports replace a long list of static imports with two lines:

```java
import module org.higherkindedj.test;
import module org.higherkindedj.core;   // brings in Either, Maybe, Try, IO, ...

class UserServiceTest {
    @Test void returns_user() {
        Either<DomainError, User> result = userService.findById("u1");
        EitherAssert.assertThatEither(result).isRight();
    }
}
```

Both modules are now in scope; no further imports are required.

---

## Common Mistakes

- **Forgetting `whenExecuted()` / `whenRun()` on IO/VTask.** `assertThatIO(io).hasValue(x)` works (it calls `whenExecuted()` for you), but `withMessage(...)` and `withMessageContaining(...)` assume an exception has already been captured — chain after `throwsException(...)` or after an explicit `whenExecuted()`.
- **Reusing a `VTaskAssert` after assertions.** The assert caches `whenRun()` and `runSafe()` results across calls. That makes chains efficient; it also means a single instance only ever runs the task once via each path.
- **Forgetting the `unwrap` function on transformer asserts.** The transformer entry-points take *two* arguments: the Kind itself and an unwrapper of type `Function<Kind<F, X>, Optional<X>>`. For the common Optional-as-outer-monad case, the unwrapper is just `OPTIONAL::narrow`.
- **Asserting on a Right value when the Either might be Left** (or any other state-mismatch). The assertions throw with a clear message; do not pre-call `.getRight()` outside the assertion chain.
- **Using `ValidatedAssert.assertThatValidated(v, "description")` then trying `.as(...)` again.** The two-arg factory already calls `as(description)`. Subsequent `.as(...)` calls overwrite it.

---

## When to Suggest the Contract Pattern

If the user is writing their own AssertJ extension class (for a domain type that is not part of HKJ), point them at `reference/contract-pattern.md`. The `AssertContract<S, A>` base class used inside hkj-test is general-purpose: it dispatches each row of `(label, passingInput, failingInput, chain)` as two dynamic tests, giving 100% line/instruction coverage with declarative spec rows rather than mechanical positive/negative test pairs.
