# Core Types: Error Handling Journey

~~~admonish info title="What You'll Learn"
- Making failures explicit and recoverable using MonadError
- When to use `Either`, `Maybe`, `Validated`, and `Try`
- Building real-world workflows that combine multiple patterns
~~~

**Duration**: ~30 minutes | **Tutorials**: 3 | **Exercises**: 20

**Prerequisites**: [Core Types: Foundations Journey](foundations_journey.md)

## Journey Overview

Now that you understand Functor, Applicative, and Monad, it's time to handle the real world: errors happen. This journey teaches you to make errors explicit in your types and choose the right type for each situation.

```
MonadError (explicit failures) → Concrete Types (right tool for the job) → Real World (combine everything)
```

---

## Tutorial 05: MonadError Handling (~8 minutes)
**File**: `Tutorial05_MonadErrorHandling.java` | **Exercises**: 7

Learn to make failures explicit and recoverable using MonadError.

**What you'll learn**:
- Creating errors explicitly with `raiseError`
- Recovering from errors with `handleErrorWith` and `recover`
- Using `Try` to wrap exception-throwing code safely
- When to use `Either`, `Try`, or `Validated`

**Key insight**: MonadError makes error handling a first-class part of your type signatures. No more surprise exceptions!

**Real-world application**: API error handling, database failure recovery, validation with custom error types.

**Links to documentation**: [MonadError Guide](../../functional/monad_error.md) | [Either Monad](../../monads/either_monad.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial05_MonadErrorHandling.java)

---

## Tutorial 06: Concrete Types (~10 minutes)
**File**: `Tutorial06_ConcreteTypes.java` | **Exercises**: 7

Learn when to use each concrete type that implements the typeclasses you've learned.

**What you'll learn**:
- `Either<L, R>` for explicit, fail-fast error handling
- `Maybe<A>` for optional values without error details
- `List<A>` for working with multiple values
- `Validated<E, A>` for accumulating all errors
- How to convert between these types

**Key insight**: Each type makes different trade-offs. `Either` fails fast, `Validated` accumulates errors, `Maybe` discards error details.

**Decision tree**:
```
Need error message?
├── Yes → Need ALL errors?
│         ├── Yes → Validated
│         └── No  → Either
└── No  → Just optional?
          ├── Yes → Maybe
          └── Multiple values? → List
```

**Links to documentation**: [Either](../../monads/either_monad.md) | [Maybe](../../monads/maybe_monad.md) | [Validated](../../monads/validated_monad.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial06_ConcreteTypes.java)

---

## Tutorial 07: Real World (~12 minutes)
**File**: `Tutorial07_RealWorld.java` | **Exercises**: 6

Bring everything together by building realistic workflows that combine multiple patterns.

**What you'll learn**:
- Building validation pipelines with Applicative
- Processing data streams with Functor and Monad
- Using `Reader` monad for dependency injection
- Combining effects: validation + transformation + error handling

**Key insight**: Real applications rarely use just one pattern. They compose Functor, Applicative, and Monad to build robust systems.

**Real-world scenarios**:
- User registration with validation, database checks, and email sending
- Data import pipeline with parsing, validation, and transformation
- Configuration-driven workflow using Reader monad

**Links to documentation**: [Reader Monad](../../monads/reader_monad.md) | [For Comprehensions](../../functional/for_comprehension.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial07_RealWorld.java)

---

## Running the Tutorials

```bash
./gradlew :hkj-examples:test --tests "*Tutorial05_MonadErrorHandling*"
./gradlew :hkj-examples:test --tests "*Tutorial06_ConcreteTypes*"
./gradlew :hkj-examples:test --tests "*Tutorial07_RealWorld*"
```

---

## Common Pitfalls

### 1. Forgetting to Handle Errors
**Problem**: Assuming `Either.right()` everywhere without planning for `Either.left()`.

**Solution**: Always think about both paths. Tests check both success and failure cases!

### 2. Using Either When You Want All Errors
**Problem**: Validation stops at the first error when you want to show all errors.

**Solution**: Use `Validated` with Applicative (`map2`, etc.) to accumulate errors:
```java
Validated<List<String>, User> result = ValidatedApplicative.instance().map3(
    validateName(name),
    validateEmail(email),
    validateAge(age),
    User::new
);
```

### 3. Throwing Exceptions Instead of Returning Errors
**Problem**: Old habits die hard. You throw instead of returning `Either.left()`.

**Solution**: Wrap exception-throwing code with `Try`:
```java
Try<Integer> result = Try.of(() -> Integer.parseInt(input));
```

---

## What's Next?

After completing this journey:

1. **Continue to Advanced Patterns**: Natural Transformations, Coyoneda, Free Applicative
2. **Jump to Effect API**: Start using the user-friendly Effect Path API (recommended for practical use)
3. **Explore Optics**: Apply your knowledge to immutable data manipulation

---

**Previous**: [Core Types: Foundations](foundations_journey.md)
**Next Journey**: [Core Types: Advanced Patterns](advanced_journey.md)
