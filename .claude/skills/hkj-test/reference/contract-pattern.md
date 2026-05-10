# Contract Pattern: Reaching 100% Coverage on AssertJ Extensions

The `hkj-test` module enforces 100% line and instruction coverage on its 19 assertion classes via a contract-test framework. The same pattern is reusable for any AssertJ extension a user writes for their own domain types.

---

## The Problem

A typical AssertJ extension class has methods of the shape:

```java
public DomainAssert isInState(...) {
    isNotNull();
    if (cond) failWithMessage(...);
    return this;
}
```

To reach 100% coverage you need:

1. A test where the chain succeeds (covers the happy path).
2. A test where the chain fails (covers the failure-message branch).
3. Multiplied by every public method.

Hand-writing two `@Test`s per method scales linearly with the API surface and bloats the test base.

---

## The `AssertContract<S, A>` Base Class

`AssertContract` is a small declarative base. Each subclass enumerates rows describing one method (or chain). Every row carries:

- a `label` for diagnostic reporting,
- an optional `passingInput`: a subject value that should satisfy the chain,
- an optional `failingInput`: a subject value that should make the chain throw `AssertionError`,
- the `chain`: a `Consumer<A>` driving the assertion methods.

Two `@TestFactory` methods dispatch each row as up to two dynamic tests:

- happy path — invoking the chain on the passing subject must not throw,
- failure path — invoking the chain on the failing subject must throw `AssertionError`.

Asymmetric rows (chains whose closure always throws regardless of input, or chains for which a meaningful failing input does not exist) use `passOnly` / `failOnly`.

```java
public abstract class AssertContract<S, A> {

  @FunctionalInterface
  protected interface ThrowingChain<A> {
    void accept(A assertion) throws Throwable;
  }

  protected record Row<S, A>(
      String label,
      Optional<S> passingInput,
      Optional<S> failingInput,
      ThrowingChain<A> chain) {}

  protected abstract Function<S, A> entry();
  protected abstract Stream<Row<S, A>> rows();

  protected static <S, A> Row<S, A> row(String label, S pass, S fail, ThrowingChain<A> chain) { ... }
  protected static <S, A> Row<S, A> failOnly(String label, S fail, ThrowingChain<A> chain) { ... }
  protected static <S, A> Row<S, A> passOnly(String label, S pass, ThrowingChain<A> chain) { ... }

  @TestFactory
  Stream<DynamicTest> happy_path_passes_for_valid_inputs() { ... }

  @TestFactory
  Stream<DynamicTest> failure_path_throws_for_invalid_inputs() { ... }
}
```

---

## Example: A Contract for `EitherAssert`

```java
class EitherAssertContractTest
    extends AssertContract<Either<String, Integer>, EitherAssert<String, Integer>> {

  private static final Either<String, Integer> RIGHT_42 = Either.right(42);
  private static final Either<String, Integer> RIGHT_99 = Either.right(99);
  private static final Either<String, Integer> LEFT_ERR = Either.left("err");

  @Override
  protected Function<Either<String, Integer>, EitherAssert<String, Integer>> entry() {
    return EitherAssert::assertThatEither;
  }

  @Override
  protected Stream<Row<Either<String, Integer>, EitherAssert<String, Integer>>> rows() {
    return Stream.of(
        row("isRight", RIGHT_42, LEFT_ERR, EitherAssert::isRight),
        row("isLeft",  LEFT_ERR, RIGHT_42, EitherAssert::isLeft),
        row("hasRight value match", RIGHT_42, RIGHT_99, a -> a.hasRight(42)),
        row("hasRight wrong state", RIGHT_42, LEFT_ERR, a -> a.hasRight(42)),
        failOnly("hasRightSatisfying inner fails", RIGHT_42,
            a -> a.hasRightSatisfying(v -> { throw new AssertionError("inner"); })));
  }
}
```

A row covers two failure modes when the same chain has more than one branch (here: wrong-state and wrong-value for `hasRight`). Add a row per method and per failure branch.

---

## Tips for Reaching 100%

1. **Replace `if + failWithMessage` with AssertJ-fluent chains** (`assertThat(x).withFailMessage(...).isTrue()` / `isEqualTo` / `isNull` / `isNotNull` / `isInstanceOf`). JaCoCo's bytecode-to-source mapping credits multi-call chains reliably; single-line `failWithMessage(...)` calls are credited inconsistently.

2. **Use the `Supplier` overload of `withFailMessage` for state-dependent error messages.** It defers message construction to the failure path, which avoids dereferencing fields that are only valid in the failing branch (e.g. calling `.getLeft()` on a `Right`).

3. **Cast directly to record components for sealed sums.** Replace `fold`-with-throw and `try`/`catch` dispatch with `((Failure<T>) actual).cause()` or `((Right<L, R>) actual).value()` once the state predicate has been asserted. This removes dead-code branches that JaCoCo cannot credit.

4. **Make outer holder classes `final` with private no-arg constructors.** Cover the synthetic constructor with one reflection-based row:

   ```java
   passOnly("private constructor accessible via reflection", anyInput, a -> {
     try {
       var c = MyOuterAssert.class.getDeclaredConstructor();
       c.setAccessible(true);
       c.newInstance();
     } catch (Exception e) { throw new AssertionError(e); }
   })
   ```

5. **For chains whose failure depends on the inner closure** (e.g. `hasValueSatisfying(consumer)` where the consumer always throws), use `failOnly`. Their happy-path counterpart is impossible because the chain throws regardless of input.

---

## When to Use This Pattern

- You are writing AssertJ extensions for your own domain types and want them coverage-gated.
- You are extending hkj-test (e.g. with assertions for an external library that builds on HKJ).
- You want a declarative, table-driven alternative to mechanical positive/negative test pairs.

When you do *not* need this pattern: small assertion classes with three or four methods are simpler to test with plain `@Test` methods. The contract approach pays off when the API surface is large enough that the row table reads more clearly than a wall of test methods.
