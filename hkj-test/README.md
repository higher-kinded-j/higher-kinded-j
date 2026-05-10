# Higher-Kinded-J Test Assertions

This module ships fluent AssertJ assertion helpers for every public Higher-Kinded-J type. It is a test-scope dependency: drop it in alongside JUnit and AssertJ and your tests start reading like the business intent.

```java
assertThatEither(result).isRight().hasRight(42);
assertThatMaybe(value).isJust().hasValue("hello");
assertThatTry(computation).isFailure().hasExceptionOfType(IOException.class);
assertThatIO(effect).whenExecuted().hasValue(expected);
```

## Quick Start

### 1. Add the dependency

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

AssertJ and `hkj-core` are pulled in transitively, so no further declarations are required.

### 2. Use the assertions

```java
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.assertions.IOAssert.assertThatIO;

@Test
void rights_carry_the_business_value() {
    Either<DomainError, Order> result = orderService.process(request);
    assertThatEither(result).isRight().hasRight(expectedOrder);
}

@Test
void io_is_lazy_until_executed() {
    IO<Integer> effect = IO.delay(() -> 1 + 1);
    assertThatIO(effect).isNotExecutedYet();
    assertThatIO(effect).whenExecuted().hasValue(2);
}
```

## What's Covered

Every assertion lives in package `org.higherkindedj.hkt.assertions`.

| Category | Helpers |
|----------|---------|
| Discriminated unions | `EitherAssert`, `MaybeAssert`, `TryAssert`, `ValidatedAssert`, `LazyAssert` |
| Reader / Writer / State | `ReaderAssert` (with `ReaderResultAssert`), `WriterAssert`, `StateAssert` |
| Effect types | `IOAssert`, `VTaskAssert`, `VStreamAssert` |
| Monad transformers | `EitherTAssert`, `MaybeTAssert`, `OptionalTAssert`, `ReaderTAssert`, `StateTAssert`, `WriterTAssert` |
| Free algebra | `FreeAssert`, `EitherFAssert` |

Each entry point follows the AssertJ convention `assertThatXxx(actual)`. Every chain returns the assertion for further composition, so the dialect and ergonomics match AssertJ's own helpers.

## Java 25 Module Import

`hkj-test` is published as a JPMS module named `org.higherkindedj.test`. On Java 25 with `--enable-preview`, JEP 511's module-import syntax replaces a long list of static imports with a single line:

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

## Coverage Guarantees

Every public assertion method is covered by a contract test in `src/test/java`. The `AssertContract<S, A>` base class enumerates rows of `(label, passingInput, failingInput, chain)` and dispatches each row as two dynamic tests: one verifying the chain succeeds on the passing input, another verifying it throws `AssertionError` on the failing input.

The module enforces 100% line and 100% instruction coverage as a `check`-task gate; adding a new assertion method without a contract row will fail CI.

## Documentation

For the complete reference, including detailed examples for every helper and idioms for the transformer assertions, see [Testing With hkj-test](https://higher-kinded-j.github.io/latest/tooling/test_assertions.html) in the project documentation.

## Licence

MIT. See [LICENSE.md](../LICENSE.md) in the project root.
