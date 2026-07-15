---
name: hkj-test
description: "Write tests against Higher-Kinded-J types using hkj-test: assertThatEither / assertThatMaybe / assertThatTry / assertThatIO / assertThatVTask / assertThatVStream / assertThatVTaskPath / assertThatVStreamPath / assertThatVTaskContext / assertThatVResultPath / assertThatLazy / assertThatValidated / assertThatNonEmptyList / assertThatEitherOrBoth / assertThatFieldError / assertThatErrorEnvelope / assertThatReader / assertThatWriter / assertThatStateTuple / assertThatList / assertThatOptionalKind / assertThatStream / assertThatId / assertThatEitherT / assertThatMaybeT / assertThatOptionalT / assertThatReaderT / assertThatStateT / assertThatWriterT / assertThatFree / assertThatEitherF, AssertJ fluent assertions for HKJ types, transformer assertion unwrappers, Kind-narrowing wrappers (List, OptionalKind, Stream, Id), Path/context assertions (VTaskPath, VStreamPath, VTaskContext, VResultPath), validation-accumulation and FieldError path assertions, optic laws / law harness (IsoLaws, LensLaws, PrismLaws, AffineLaws, TraversalLaws, ValidatedPrismLaws, MappingLaws), SteppableClock for deterministic time with TimeSource, JEP 511 module imports, AssertContract pattern for testing custom AssertJ assertions"
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
| `NonEmptyList<T>` | `NonEmptyListAssert` | `assertThatNonEmptyList(actual)` |
| `EitherOrBoth<L, R>` | `EitherOrBothAssert` | `assertThatEitherOrBoth(actual)` |
| `FieldError` | `FieldErrorAssert` | `assertThatFieldError(actual)` |
| `ErrorEnvelope<C>` | `ErrorEnvelopeAssert` | `assertThatErrorEnvelope(actual)` |
| `Lazy<T>` | `LazyAssert` | `assertThatLazy(actual)` |
| `Reader<R, A>` | `ReaderAssert` | `assertThatReader(actual)` |
| `Writer<W, A>` | `WriterAssert` | `assertThatWriter(actual)` |
| `StateTuple<S, A>` | `StateAssert` | `assertThatStateTuple(actual)` |
| `IO<T>` | `IOAssert` | `assertThatIO(actual)` |
| `VTask<T>` | `VTaskAssert` | `assertThatVTask(actual)` |
| `VStream<A>` | `VStreamAssert` | `assertThatVStream(actual)` |
| `VTaskPath<T>` | `VTaskPathAssert` | `assertThatVTaskPath(actual)` |
| `VResultPath<E, A>` | `VResultPathAssert` | `assertThatVResultPath(actual)` |
| `VStreamPath<A>` | `VStreamPathAssert` | `assertThatVStreamPath(actual)` |
| `VTaskContext<T>` | `VTaskContextAssert` | `assertThatVTaskContext(actual)` |
| `Kind<ListKind.Witness, T>` | `ListAssert` | `assertThatList(actual)` |
| `Kind<OptionalKind.Witness, T>` | `OptionalKindAssert` | `assertThatOptionalKind(actual)` |
| `Kind<StreamKind.Witness, T>` | `StreamAssert` | `assertThatStream(actual)` |
| `Kind<IdKind.Witness, T>` | `IdAssert` | `assertThatId(actual)` |
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

### NonEmptyList

```java
import static org.higherkindedj.hkt.assertions.NonEmptyListAssert.assertThatNonEmptyList;

assertThatNonEmptyList(errors).hasSize(2).hasHead(first).hasLast(second);
assertThatNonEmptyList(errors).containsExactly(first, second);
assertThatNonEmptyList(errors).contains(first).doesNotContain(other);
assertThatNonEmptyList(errors).allMatch(e -> e.pathString().startsWith("order"));
assertThatNonEmptyList(errors).anyMatch(e -> e.pathString().equals("order.email"));
assertThatNonEmptyList(errors).satisfies(nel -> assertThat(nel.toJavaList()).isSorted());
```

Also `isNotEmpty()`, `containsOnly(...)`. `head()`/`last()` are total on `NonEmptyList`, so the assertions never need an emptiness guard.

### EitherOrBoth

Three states, so three predicates. `Both` is the one `Either` cannot express (success *plus* non-fatal warnings):

```java
import static org.higherkindedj.hkt.assertions.EitherOrBothAssert.assertThatEitherOrBoth;

assertThatEitherOrBoth(clean).isRight().hasRight(order);
assertThatEitherOrBoth(fatal).isLeft().hasLeft(error);
assertThatEitherOrBoth(warned).isBoth().hasBoth(warnings, order);

assertThatEitherOrBoth(warned).hasBothSatisfying((warns, value) -> {
    assertThat(warns).hasSize(1);
    assertThat(value.id()).isEqualTo("o1");
});
```

Also `hasLeftSatisfying(...)` / `hasRightSatisfying(...)`.

### Validation accumulation (FieldError)

There is **no** dedicated "accumulated validation" assertion; `ValidatedAssert` is unchanged. Assert accumulation by taking the `NonEmptyList<FieldError>` off the `Invalid` and asserting on the located errors:

```java
import static org.higherkindedj.hkt.assertions.FieldErrorAssert.assertThatFieldError;
import static org.higherkindedj.hkt.assertions.NonEmptyListAssert.assertThatNonEmptyList;

Validated<NonEmptyList<FieldError>, Customer> result = mapping.parse(dto);

assertThatValidated(result).isInvalid();
NonEmptyList<FieldError> errors = result.getError();

assertThatNonEmptyList(errors).hasSize(2);
assertThatFieldError(errors.head()).hasPath("customer.email").hasMessageContaining("not an email");
assertThatFieldError(errors.last()).hasSegments("customer", "age");

FieldError unlabelled = FieldError.of("must be positive");   // never located with .at(...)
assertThatFieldError(unlabelled).isUnlabelled();             // no path segments at all
```

`FieldErrorAssert` also carries `hasMessage(...)`. `hasPath` matches the dotted rendering (`FieldError.pathString()`); `hasSegments` matches the segments individually. Use it when a segment might itself contain a dot.

### ErrorEnvelope

```java
import static org.higherkindedj.hkt.assertions.ErrorEnvelopeAssert.assertThatErrorEnvelope;

assertThatErrorEnvelope(error.envelope())
    .hasCode("OUT_OF_STOCK")
    .hasMessageContaining("out of stock")
    .hasTimestamp(FROZEN_INSTANT)          // deterministic if the code takes a TimeSource
    .hasContextSatisfying(ctx -> assertThat(ctx.orderId()).isEqualTo(orderId));
```

Also `hasMessage(...)` and `hasContext(expected)` for whole-record equality. `hasTimestamp` is only worth asserting when the envelope was built from an injected `TimeSource` (see **Deterministic Time** below).

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

### VResultPath (async with a typed error channel)

`VResultPath<E, A>` wraps `VTask<Either<E, A>>`, so it has *three* outcomes: `Right` (success), `Left` (typed domain failure), and a **defect** (a thrown exception on the task channel). The assert runs the path on the first assertion and caches the outcome (no `whenRun()` step):

```java
import static org.higherkindedj.hkt.assertions.VResultPathAssert.assertThatVResultPath;

assertThatVResultPath(path).isRight().hasRight(order);
assertThatVResultPath(path).isLeft().hasLeft(OutOfStock.INSTANCE);
assertThatVResultPath(path).hasLeftSatisfying(e -> assertThat(e.code()).isEqualTo("OUT_OF_STOCK"));

assertThatVResultPath(defective)
    .hasDefect()
    .withDefectType(IllegalStateException.class)
    .withDefectMessageContaining("kaboom");

assertThatVResultPath(path).isRight().completesWithin(Duration.ofSeconds(1));
```

Also `hasRightSatisfying(...)`. Keep the distinction sharp: a typed failure is `isLeft()`, a thrown exception is `hasDefect()`. Asserting `isLeft()` on a defect fails.

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

## Optic Law Harness

Hand-written or generated optics are only useful if they are lawful. Package `org.higherkindedj.optics.laws` ships one final class of static asserts per optic. They are AssertJ-only (no JUnit dependency), and a failure names the violated law with the offending values.

| Optic | Class | Aggregate entry point |
|-------|-------|-----------------------|
| `Iso<S, A>` | `IsoLaws` | `assertIsoLaws(iso, s, a)` |
| `Lens<S, A>` | `LensLaws` | `assertLensLaws(lens, s, a1, a2)` |
| `Prism<S, A>` | `PrismLaws` | `assertPrismLaws(prism, matchingSource, nonMatchingSource)` |
| `Affine<S, A>` | `AffineLaws` | `assertAffineLaws(affine, presentSource, absentSource, a1, a2)` |
| `Traversal<S, A>` | `TraversalLaws` | `assertTraversalLaws(traversal, s, f, g)` |
| `ValidatedPrism<S, A>` | `ValidatedPrismLaws` | `assertValidatedPrismLaws(prism, parsingSource, nonParsingSource)` |
| `@GenerateMapping` spec | `MappingLaws` | `assertMappingLaws(...)` (see below) |

```java
import org.higherkindedj.optics.laws.LensLaws;
import org.higherkindedj.optics.laws.PrismLaws;
import org.higherkindedj.optics.laws.TraversalLaws;

class OrderOpticsLawsTest {

    private static final Order ORDER = new Order("o1", "ada@example.com", List.of(1, 2, 3));

    // A prism needs a source it matches and one it does not.
    private static final OrderError OUT_OF_STOCK = new OrderError.OutOfStock("sku-1");
    private static final OrderError NOT_FOUND = new OrderError.NotFound("o1");

    @Test
    void email_lens_is_lawful() {
        LensLaws.assertLensLaws(OrderLenses.email(), ORDER, "bob@example.com", "cy@example.com");
    }

    @Test
    void out_of_stock_prism_is_lawful() {
        PrismLaws.assertPrismLaws(OrderErrorPrisms.outOfStock(), OUT_OF_STOCK, NOT_FOUND);
    }

    @Test
    void quantities_traversal_is_lawful() {
        TraversalLaws.assertTraversalLaws(
            OrderTraversals.quantities(), ORDER, i -> i + 1, i -> i * 2);
    }
}
```

**Fixture preconditions.** The aggregate entry points guard these and fail with a diagnostic if you get them wrong:

- `assertLensLaws(lens, s, a1, a2)`: `a1` and `a2` must differ **from each other and from the current focus** `lens.get(s)`. A lens whose set-set law is broken cannot be detected with duplicate fixtures.
- `assertAffineLaws(...)`: same distinctness rule on `a1`/`a2`; `presentSource` must have a target and `absentSource` must not.
- `assertPrismLaws(...)` / `assertValidatedPrismLaws(...)`: the first source must match/parse, the second must not.

The individual laws are also exposed if you want them as separate test methods: `LensLaws.assertGetSet` / `assertSetGet` / `assertSetSet`, `IsoLaws.assertGetReverseGet` / `assertReverseGetGet`, `PrismLaws.assertBuildMatch` / `assertMatchBuild` / `assertNoMatch`, `AffineLaws.assertGetSetWhenPresent` / `assertSetGetWhenPresent` / `assertSetSetWhenPresent` / `assertSetNoOpWhenAbsent`, `TraversalLaws.assertIdentity` / `assertFusion`, `ValidatedPrismLaws.assertParseBuild` / `assertBuildParse` / `assertNoParse`.

### MappingLaws (`@GenerateMapping`)

A generated mapper emits a different optic per tier, so pick the overload matching the tier you asked for:

```java
// lossless tier -> asIso()
MappingLaws.assertMappingLaws(spec.asIso(), spec.asValidatedPrism(), domainSample, wireSample);

// lossy projection tier -> asLens() (delegates to the lens laws, distinct-values guard included)
MappingLaws.assertMappingLaws(spec.asLens(), domainSample, wireSample1, wireSample2);

// parse-capable tier -> asValidatedPrism()
MappingLaws.assertMappingLaws(spec.asValidatedPrism(), parseableWire, nonParseableWire);

// total-parse mapping (identity components / derived wire fields): round trip only
MappingLaws.assertMappingLaws(spec.asValidatedPrism(), domainSample);
```

Also `MappingLaws.assertBuildAgreesWithIso(iso, mapping, domainSample)` and `assertParseAgreesWithIso(iso, mapping, wireSample)` for the two halves of the lossless tier. The single-sample overload passes on a genuinely fallible mapping without ever exercising a failure path. If the spec has a fallible leaf, use the two-wire-sample overload with a non-parsing sample.

---

## Deterministic Time (SteppableClock)

Code that reads the wall clock should take a `TimeSource` (`org.higherkindedj.hkt.time.TimeSource`), not call `Instant.now()`. In tests, feed it a clock you control and **advance time instead of sleeping**. `SteppableClock` (in `org.higherkindedj.hkt.assertions`) is a mutable `java.time.Clock` that only moves when you move it:

```java
import java.time.Duration;
import java.time.Instant;
import org.higherkindedj.hkt.assertions.SteppableClock;
import org.higherkindedj.hkt.time.TimeSource;

var clock = SteppableClock.startingAt(Instant.parse("2026-07-07T00:00:00Z"));
var service = new InMemoryInventoryService(TimeSource.of(clock));

service.reserve(sku);
clock.advance(Duration.ofMinutes(16));    // no Thread.sleep, no flakiness

assertThatEither(service.checkout(sku)).isLeft().hasLeft(ReservationExpired.INSTANCE);
```

`clock.set(instant)` jumps to an absolute instant; `clock.instant()` / `getZone()` / `withZone(zone)` are the usual `Clock` accessors, so a `SteppableClock` drops into anything taking a JDK `Clock`.

For a clock that never moves at all, `TimeSource.fixed(instant)` is enough; no `SteppableClock` needed. Use `SteppableClock` when the test needs *elapsed* time: expiry, TTL, rate-limit windows, retry backoff, and `ErrorEnvelope` timestamps.

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

- **Forgetting `whenExecuted()` / `whenRun()` on IO/VTask.** `assertThatIO(io).hasValue(x)` works (it calls `whenExecuted()` for you), but `withMessage(...)` and `withMessageContaining(...)` assume an exception has already been captured; chain after `throwsException(...)` or after an explicit `whenExecuted()`.
- **Reusing a `VTaskAssert` after assertions.** The assert caches `whenRun()` and `runSafe()` results across calls. That makes chains efficient; it also means a single instance only ever runs the task once via each path.
- **Forgetting the `unwrap` function on transformer asserts.** The transformer entry-points take *two* arguments: the Kind itself and an unwrapper of type `Function<Kind<F, X>, Optional<X>>`. For the common Optional-as-outer-monad case, the unwrapper is just `OPTIONAL::narrow`.
- **Asserting on a Right value when the Either might be Left** (or any other state-mismatch). The assertions throw with a clear message; do not pre-call `.getRight()` outside the assertion chain.
- **Using `ValidatedAssert.assertThatValidated(v, "description")` then trying `.as(...)` again.** The two-arg factory already calls `as(description)`. Subsequent `.as(...)` calls overwrite it.
- **Looking for an "accumulated errors" assertion on `ValidatedAssert`.** There isn't one, and it is not an omission. Assert `isInvalid()`, take the `NonEmptyList<FieldError>` via `getError()`, then use `assertThatNonEmptyList` for the shape and `assertThatFieldError` (`hasPath` / `hasSegments` / `isUnlabelled`) for each located error.
- **Confusing a `Left` with a defect on `VResultPath`.** A typed domain failure is `isLeft()`; a thrown exception is `hasDefect()`. They are different channels.
- **Passing duplicate fixtures to `assertLensLaws` / `assertAffineLaws`.** `a1` and `a2` must differ from each other and from the current focus, otherwise the set-set law is untestable. The harness fails fast with that message rather than silently passing.

---

## When to Suggest the Contract Pattern

If the user is writing their own AssertJ extension class (for a domain type that is not part of HKJ), point them at `reference/contract-pattern.md`. The `AssertContract<S, A>` base class used inside hkj-test is general-purpose: it dispatches each row of `(label, passingInput, failingInput, chain)` as two dynamic tests, giving 100% line/instruction coverage with declarative spec rows rather than mechanical positive/negative test pairs.
