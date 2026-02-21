# Expression: ForState Journey

~~~admonish info title="What You'll Learn"
- Why ForState is the recommended comprehension pattern for complex workflows
- How named record fields replace fragile tuple positions
- Threading state through monadic workflows using lenses
- Using guards (`when()`) to short-circuit workflows
- Pattern matching with prisms via `matchThen()`
- Bulk collection operations with `traverse()`
- Narrowing state scope with `zoom()` / `endZoom()`
- Bridging from `For` comprehensions to `ForState` with `toState()`
~~~

**Duration**: ~25 minutes | **Tutorials**: 1 | **Exercises**: 11

## Journey Overview

ForState is the recommended for-comprehension pattern when workflows involve more than two or three steps. Where the standard `For` builder accumulates values in a tuple (`t._1()`, `t._2()`, ...), ForState uses a **named record** with lenses, giving you `ctx.user()`, `ctx.address()`, and so on. This eliminates positional errors and makes workflows readable at any scale.

```
withState → update/modify/fromThen → when/matchThen → traverse → zoom → yield
For.from() → toState() → update/modify → when → yield
```

By the end, you'll be able to build complex workflows that combine effectful computation with named state management, guards, pattern matching, nested scope narrowing, and seamless bridging from `For` comprehensions.

~~~admonish tip title="Read First"
Before starting this tutorial, read the [ForState: Named State Comprehensions](../../functional/forstate_comprehension.md) chapter for a full comparison of `For` vs `ForState` and a complete API reference.
~~~

---

## Tutorial 01: ForState Basics (~25 minutes)
**File**: `Tutorial01_ForStateBasics.java` | **Exercises**: 11

Master the complete ForState API through progressive exercises, including the `toState()` bridge from `For` comprehensions.

**What you'll learn**:
- Basic state threading with `update()`, `modify()`, and `fromThen()`
- Guard-based short-circuiting with `when()` and MonadZero
- Prism-based pattern matching with `matchThen()`
- Bulk operations over collection fields with `traverse()`
- Narrowing state scope with `zoom()` / `endZoom()`
- Combining all features in a complete workflow
- Bridging from `For` to `ForState` with `toState()` (single-value, multi-value, and filterable)

**Key insight**: ForState separates *what* to compute (the monadic operation) from *where* to store it (the lens target), making workflows both declarative and type-safe. Every intermediate value has a name, not a tuple position.

**Links to documentation**: [ForState: Named State Comprehensions](../../functional/forstate_comprehension.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/expression/Tutorial01_ForStateBasics.java)

---

### Exercise Progression

| Exercise | Concept | Difficulty |
|----------|---------|------------|
| 1 | Basic `update()` | Beginner |
| 2 | Effectful `fromThen()` | Beginner |
| 3 | Pure `modify()` | Beginner |
| 4a,b | Guards with `when()` | Intermediate |
| 5a,b | Pattern matching with `matchThen()` | Intermediate |
| 6 | Bulk ops with `traverse()` | Intermediate |
| 7 | State zooming with `zoom()` | Intermediate |
| 8 | Combined workflow | Advanced |
| 9a | Basic `toState()` bridge from For | Intermediate |
| 9b | Multi-value `toState()` with spread-style | Intermediate |
| 9c | Filterable `toState()` with MonadZero guards | Advanced |
