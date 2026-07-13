# Interactive Tutorials: Learn Higher-Kinded-J by Building

The best way to understand Higher-Kinded Types and Optics is not to read about them but to write them: run a test, watch it fail, change one line, and watch it pass. This chapter is built around that loop.

## What Makes These Tutorials Different?

Rather than passive reading, we will:
- **Write real code** by replacing `answerRequired()` placeholders with working implementations
- **Get immediate feedback** because each test fails until our solution is correct
- **Build progressively**: earlier concepts become tools for later challenges
- **See practical applications**: every exercise solves a problem Java developers face routinely

Think of the chapter as a guided laboratory for functional programming patterns in Java.

## Thirteen Focused Journeys

Each journey is designed for a single sitting (20-65 minutes). Short enough to stay focused; long enough to build real understanding.

### Effect API Journey (Recommended)

~~~admonish tip title="Start Here for Practical Use"
After completing **Core: Foundations** the **Effect API journey** is the recommended next step. It teaches the primary user-facing API of Higher-Kinded-J.
~~~

| Journey | Duration | Exercises | Focus |
|---------|----------|-----------|-------|
| [Effect API](effect/effect_journey.md) | ~65 min | 15 | Effect paths, ForPath, Contexts |

### Expression Journey

| Journey | Duration | Exercises | Focus |
|---------|----------|-----------|-------|
| [Expression: ForState](expression/forstate_journey.md) | ~25 min | 11 | Named fields, guards, pattern matching, zoom |

### Concurrency Journeys

| Journey | Duration | Exercises | Focus |
|---------|----------|-----------|-------|
| [Concurrency: VTask](concurrency/vtask_journey.md) | ~45 min | 16 | Virtual threads, VTask, VTaskPath, Par combinators |
| [Concurrency: Scope & Resource](concurrency/scope_resource_journey.md) | ~30 min | 12 | Structured concurrency, resource management |

### Resilience Journey

| Journey | Duration | Exercises | Focus |
|---------|----------|-----------|-------|
| [Resilience Patterns](resilience/resilience_journey.md) | ~45 min | 22 | Circuit breaker, saga, retry, bulkhead |

### Core Types Journeys (Foundation)

| Journey | Duration | Exercises | Focus |
|---------|----------|-----------|-------|
| [Foundations](coretypes/foundations_journey.md) | ~40 min | 24 | Kind, Functor, Applicative, Monad |
| [Error Handling](coretypes/error_handling_journey.md) | ~30 min | 20 | MonadError, Either, Maybe, Validated |
| [Advanced Patterns](coretypes/advanced_journey.md) | ~40 min | 26 | Natural Transformations, Coyoneda, Free Ap, Static Analysis |

### Optics Journeys

| Journey | Duration | Exercises | Focus |
|---------|----------|-----------|-------|
| [Lens & Prism](optics/lens_prism_journey.md) | ~40 min | 30 | Lens, Prism, Affine fundamentals |
| [Traversals & Practice](optics/traversals_journey.md) | ~40 min | 27 | Traversals, composition, real-world use |
| [Fluent & Free DSL](optics/fluent_free_journey.md) | ~35 min | 22 | Fluent API, Free Monad DSL |
| [Focus DSL](optics/focus_dsl_journey.md) | ~35 min | 29 | Type-safe path navigation, container widening |

## How the Tutorials Work

### The Exercise Pattern

Each tutorial contains multiple exercises following this pattern:

```java
@Test
void exercise1_yourFirstChallenge() {
    // 1. Context: what we are working with
    Either<String, Integer> value = Either.right(42);

    // 2. Task: what we need to implement
    // TODO: Transform the value by doubling it
    Either<String, Integer> result = answerRequired();

    // 3. Verification: the test checks our solution
    assertThat(result.getRight()).isEqualTo(84);
}
```

We replace `answerRequired()` with working code. The test fails with a clear error message until we get it right.

### Tiered Hints

Newer exercise files use a three-tier hint structure so we can read just enough to get unstuck without seeing the answer:

```java
// Nudge:    What concept applies here?
// Strategy: Which method on Either turns A into B?
// Spoiler:  value.map(n -> n * 2)
```

Read top-to-bottom and stop as soon as we have what we need.

### The Learning Loop

1. **Read** the exercise description and the Nudge
2. **Write** our solution in place of `answerRequired()`
3. **Run** the test (Ctrl+Shift+F10 in IntelliJ, Cmd+Shift+T in Eclipse)
4. **Observe** the result:
   - ✅ Green: correct, move on
   - ❌ Red: read the error message and the Strategy hint
5. **Iterate** until we understand the pattern, not just until the test passes

### Tracking Our Progress

```bash
./gradlew :hkj-examples:tutorialProgress
```

This task scans the tutorial test files, counts the remaining `answerRequired()` calls per journey, and prints a per-journey progress bar. Useful for finding our place after a break.

### Getting Unstuck

If we are struggling with an exercise:

1. **Read the Javadoc carefully**: comments contain hints and links to relevant documentation
2. **Check the type signatures**: what type does the method expect? what does it return?
3. **Look at earlier exercises**: we may already have used a similar pattern
4. **Consult the documentation**: links are provided throughout the tutorials
5. **Peek at the solution**: solutions live in `solutions/<journey>/` directories. Each `@Test` method in a solution carries a Javadoc block in the **Why this is idiomatic / Alternative / Common wrong attempt** format; reading that prose first is usually more useful than reading the working code on its own. See the [Solutions Guide](solutions_guide.md) for the format and how to use it.

> **Resist the temptation to copy-paste.** We will learn far more from struggling for five minutes than from reading the answer immediately. The struggle is where the learning happens.

## Prerequisites

### Required Knowledge
- **Java fundamentals**: records, generics, lambdas, method references
- **IDE proficiency**: running tests, navigating code, using auto-completion
- **Basic functional concepts**: helpful but not required; we introduce them as needed

### Technical Setup
- **Java 25 or later**: the library uses modern Java features
- **Build tool**: Gradle or Maven with the Higher-Kinded-J dependencies configured
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions

### Verify Our Setup

The fastest way is the **`Tutorial00_OneLineSixLayers`** exercise, which doubles as the chapter's anchor:

```bash
./gradlew :hkj-examples:tutorialTest --tests "*Tutorial00_OneLineSixLayers*"
```

If we see a test failure with "Answer required", everything is wired up correctly and we are ready to go.

### Running Tutorials

Tutorial exercises are run using a dedicated Gradle task:

```bash
# Run all tutorial exercises
./gradlew :hkj-examples:tutorialTest

# Run a specific tutorial
./gradlew :hkj-examples:tutorialTest --tests "*Tutorial01_KindBasics*"

# Run VTask concurrency tutorials
./gradlew :hkj-examples:tutorialTest --tests "*TutorialVTask*"

# See progress across journeys
./gradlew :hkj-examples:tutorialProgress
```

~~~admonish note title="Test Configuration"
Tutorial tests are **excluded** from `./gradlew test` because they are incomplete by design. The solution tests are included and must pass to ensure each tutorial is correctly designed.

| Command | Description |
|---------|-------------|
| `./gradlew test` | Runs solution tests only (must pass) |
| `./gradlew :hkj-examples:tutorialTest` | Runs tutorial exercises (expected to fail until we complete them) |
| `./gradlew :hkj-examples:tutorialProgress` | Prints how many `answerRequired()` calls remain per journey |
~~~

## Recommended Learning Paths

See the full [Learning Paths](learning_paths.md) guide for detailed sequences. A quick overview:

### Quickstart (2 sessions)
[Core: Foundations](coretypes/foundations_journey.md) → [Effect API](effect/effect_journey.md)

### Practical FP (4 sessions)
[Core: Foundations](coretypes/foundations_journey.md) → [Error Handling](coretypes/error_handling_journey.md) → [Effect API](effect/effect_journey.md) → [Optics: Lens & Prism](optics/lens_prism_journey.md)

### Optics Specialist (4 sessions)
[Lens & Prism](optics/lens_prism_journey.md) → [Traversals](optics/traversals_journey.md) → [Fluent & Free](optics/fluent_free_journey.md) → [Focus DSL](optics/focus_dsl_journey.md)

### Full Curriculum (13 sessions)
All journeys in recommended order. See [Learning Paths](learning_paths.md).

## What We Will Build

By the end of these tutorials, we will have hands-on experience building:

### From Core Types Journeys
- A **form validation system** using Applicative to combine independent checks
- A **data processing pipeline** using Monad to chain dependent operations
- An **error handling workflow** using `Either` and `Validated` for robust failure management
- A **configuration system** using `Reader` for dependency injection

### From Optics Journeys
- A **user profile editor** with deep nested updates using Lens composition
- An **e-commerce order processor** using Traversals for bulk operations
- A **data validation pipeline** combining Lens, Prism, and Traversal
- A **multi-step workflow builder** using the Free Monad DSL with logging and validation

## Tips for Success

1. **One journey per sitting.** Each journey builds internal momentum. Splitting them reduces learning.
2. **Read hints in order.** Nudge first, then Strategy, then Spoiler. Stop the moment we have enough.
3. **Run tests frequently.** Get one green before moving on; don't write all the exercises in one go.
4. **Experiment fearlessly.** Tests provide a safety net; we cannot break anything.
5. **Don't rush.** Understanding matters more than speed. Take breaks between journeys.
6. **Ask questions.** Use [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions) if a concept is unclear.

## Beyond the Tutorials

After completing the tutorials, continue with:

- **[Example Code](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example)** - production-quality examples in `hkj-examples`, including the order, market, payment, and draughts domains
- **[API Documentation](https://higher-kinded-j.github.io/home.html)** - deep dives into every optic and typeclass
- **[Complete Walkthroughs](../hkts/order-walkthrough.md)** - how the patterns combine in real applications
- **[One Line, Six Layers](../hkts/one_line_six_layers.md)** - the chapter-wide anchor that ties everything in this book to a single expression
- **Our own projects** - apply these patterns to a real codebase

## Ready to Begin?

Choose a starting point:

**Recommended Path:**
1. [Tutorial00: One Line, Six Layers](../hkts/one_line_six_layers.md) (anchor + setup check)
2. [Foundations Journey](coretypes/foundations_journey.md) - core concepts
3. [Effect API Journey](effect/effect_journey.md) - the primary user-facing API

**Core Types Track (Foundation):**
- [Foundations Journey](coretypes/foundations_journey.md) - HKT basics
- [Error Handling Journey](coretypes/error_handling_journey.md) - error handling
- [Advanced Journey](coretypes/advanced_journey.md) - advanced patterns

**Expression Track:**
- [ForState Journey](expression/forstate_journey.md) - named fields, guards, pattern matching, zoom

**Concurrency & Resilience Track:**
- [VTask Journey](concurrency/vtask_journey.md) - virtual threads and Par combinators
- [Scope & Resource Journey](concurrency/scope_resource_journey.md) - structured concurrency
- [Resilience Patterns Journey](resilience/resilience_journey.md) - circuit breaker, saga, retry, bulkhead

**Optics Track:**
- [Lens & Prism Journey](optics/lens_prism_journey.md) - start here for optics
- [Traversals Journey](optics/traversals_journey.md) - collections and composition
- [Fluent & Free Journey](optics/fluent_free_journey.md) - advanced APIs
- [Focus DSL Journey](optics/focus_dsl_journey.md) - type-safe paths

Or see [Learning Paths](learning_paths.md) for detailed sequences.

Remember: the goal isn't to memorise every detail. It's to develop an intuition for when and how to apply these patterns. That only comes through practice.

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Core Types: Foundations Journey](coretypes/foundations_journey.md)
