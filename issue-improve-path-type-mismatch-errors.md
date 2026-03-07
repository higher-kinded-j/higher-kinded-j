# Improve runtime error messages for Path type mismatches in `via()`, `then()`, and `zipWith()`

**Labels:** `good first issue`, `enhancement`, `effect-path-api`

## Problem

When users accidentally mix different Path types in chaining operations, the runtime error messages are not helpful enough. For example:

```java
// Compiles fine, but crashes at runtime
Path.id(0).flatMap(_ -> Path.absent());
Path.right("hello").via(_ -> Path.io(() -> 42));
```

The current `IllegalArgumentException` messages don't clearly explain *what went wrong*, *why it went wrong*, or *how to fix it*.

## Goal

Improve the error messages thrown when a Path type mismatch is detected at runtime in `via()`, `then()`, and `zipWith()` implementations. The new messages should:

1. **Name the expected and actual types** — e.g. "expected `MaybePath` but received `IOPath`"
2. **Explain the constraint** — each Path type can only chain with the same Path type
3. **Suggest a fix** — point the user toward conversion methods like `toEitherPath()`, `toMaybePath()`, etc.

## Example

**Before (current):**
```
IllegalArgumentException: <varies by implementation, often generic>
```

**After (desired):**
```
IllegalArgumentException: Type mismatch in via(): expected MaybePath but mapper returned IOPath.
Each Path type can only chain with the same type.
Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.
```

## Where to Look

The `via()` (also called `flatMap()`), `then()`, and `zipWith()` methods are defined in the capability interfaces and implemented in each concrete Path class.

**Capability interfaces** (define the method contracts):
- `Chainable.java` — declares `via()` / `then()`
- `Combinable.java` — declares `zipWith()`

Use these as your starting point to understand the method signatures. Then look at the concrete implementations.

**Concrete Path implementations** (where the `instanceof` checks and error throws live):

All Path implementations are under:
```
src/main/java/org/higherkindedj/hkj/effect/
```

Each concrete Path class (e.g. `MaybePath`, `EitherPath`, `IdPath`, `IOPath`, `TryPath`, etc.) implements `via()` and/or `zipWith()` with a runtime type check. Search for `instanceof` checks and `IllegalArgumentException` throws within these files to find all the sites that need updating.

## How to Implement

1. **Find all throw sites** — Search the Path implementation files for `IllegalArgumentException` throws related to type mismatches in `via()`, `then()`, and `zipWith()`. You can search for `instanceof` or `IllegalArgumentException` across the `effect/` directory.

2. **Update each message** — Replace the existing message with a descriptive one following this pattern:
   ```java
   throw new IllegalArgumentException(
       "Type mismatch in via(): expected " + this.getClass().getSimpleName()
       + " but mapper returned " + result.getClass().getSimpleName()
       + ". Each Path type can only chain with the same type."
       + " Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.");
   ```
   Adjust the method name (`via()`, `then()`, `zipWith()`) and parameter name (`mapper returned`, `other was`, etc.) to match the context.

3. **Consider a shared helper** — If you find the message pattern is repeated in many places, you *may* extract a small private static helper method to format the message consistently. This is optional — inline strings in each throw site are perfectly fine if the count is small.

4. **Verify with tests** — The existing test suite should continue to pass. Additionally, write or update tests that assert the improved error messages are produced. Look for existing tests that exercise the type-mismatch scenario (search for `assertThrows` or `IllegalArgumentException` in the test files under `src/test/`).

## Acceptance Criteria

- [ ] All `via()` / `flatMap()` type-mismatch throws include the expected and actual type names
- [ ] All `then()` type-mismatch throws include the expected and actual type names
- [ ] All `zipWith()` type-mismatch throws include the expected and actual type names
- [ ] Error messages suggest using conversion methods
- [ ] Existing tests pass
- [ ] At least one test per method (`via`, `then`, `zipWith`) asserts the improved error message content
