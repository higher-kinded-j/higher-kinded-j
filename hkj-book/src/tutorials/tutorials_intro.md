# Interactive Tutorials: Learn Higher-Kinded-J by Building

The best way to understand Higher-Kinded Types and Optics isn't just reading about them: it's writing code, running tests, and seeing the patterns come alive in your IDE.

## What Makes These Tutorials Different?

Rather than passive reading, you'll:
- **Write Real Code**: Replace `answerRequired()` placeholders with working implementations
- **Get Immediate Feedback**: Each test fails until your solution is correct
- **Build Progressively**: Earlier concepts become tools for later challenges
- **See Practical Applications**: Every exercise solves a real problem Java developers face

Think of these as a guided laboratory for functional programming patterns in Java.

## Eight Focused Journeys

Each journey is designed for a single sitting (22-40 minutes). Short enough to stay focused. Long enough to build real understanding.

### Effect API Journey (Recommended)

~~~admonish tip title="Start Here for Practical Use"
After completing Core: Foundations, the **Effect API journey** is the recommended next step. It teaches the primary user-facing API of Higher-Kinded-J.
~~~

| Journey | Duration | Exercises | Focus |
|---------|----------|-----------|-------|
| [Effect API](effect/effect_journey.md) | ~65 min | 15 | Effect paths, ForPath, Contexts |

### Core Types Journeys (Foundation)

| Journey | Duration | Exercises | Focus |
|---------|----------|-----------|-------|
| [Foundations](coretypes/foundations_journey.md) | ~38 min | 24 | Kind, Functor, Applicative, Monad |
| [Error Handling](coretypes/error_handling_journey.md) | ~30 min | 20 | MonadError, Either, Maybe, Validated |
| [Advanced Patterns](coretypes/advanced_journey.md) | ~26 min | 16 | Natural Transformations, Coyoneda, Free Ap |

### Optics Journeys

| Journey | Duration | Exercises | Focus |
|---------|----------|-----------|-------|
| [Lens & Prism](optics/lens_prism_journey.md) | ~40 min | 30 | Lens, Prism, Affine fundamentals |
| [Traversals & Practice](optics/traversals_journey.md) | ~40 min | 27 | Traversals, composition, real-world use |
| [Fluent & Free DSL](optics/fluent_free_journey.md) | ~37 min | 22 | Fluent API, Free Monad DSL |
| [Focus DSL](optics/focus_dsl_journey.md) | ~22 min | 18 | Type-safe path navigation |

## How the Tutorials Work

### The Exercise Pattern

Each tutorial contains multiple exercises following this pattern:

```java
@Test
void exercise1_yourFirstChallenge() {
    // 1. Context: What you're working with
    Either<String, Integer> value = Either.right(42);

    // 2. Task: What you need to implement
    // TODO: Transform the value by doubling it
    Either<String, Integer> result = answerRequired();

    // 3. Verification: The test checks your solution
    assertThat(result.getRight()).isEqualTo(84);
}
```

Your job is to replace `answerRequired()` with working code. The test will fail with a clear error message until you get it right.

### The Learning Loop

1. **Read** the exercise description and hints
2. **Write** your solution in place of `answerRequired()`
3. **Run** the test (Ctrl+Shift+F10 in IntelliJ, Cmd+Shift+T in Eclipse)
4. **Observe** the result:
   - ✅ **Green**: Correct! Move to the next exercise
   - ❌ **Red**: Read the error message carefully; it contains clues
5. **Iterate** until you understand the pattern

### Getting Unstuck

If you're struggling with an exercise:

1. **Read the Javadoc carefully**: The comments contain hints and links to relevant documentation
2. **Check the type signatures**: What types does the method expect? What does it return?
3. **Look at earlier exercises**: You might have already used a similar pattern
4. **Consult the documentation**: Links are provided throughout the tutorials
5. **Peek at the solution**: Solutions are in `solutions/coretypes/` and `solutions/optics/` directories

> **Resist the temptation to copy-paste solutions!** You'll learn far more from struggling for 5 minutes than from reading the answer immediately. The struggle is where the learning happens.

## Prerequisites

### Required Knowledge
- **Java Fundamentals**: Records, generics, lambda expressions, method references
- **IDE Proficiency**: Running tests, navigating code, using auto-completion
- **Basic Functional Concepts**: Helpful but not required; we'll introduce them as needed

### Technical Setup
- **Java 25 or later**: The library uses modern Java features
- **Build Tool**: Gradle or Maven with the Higher-Kinded-J dependencies configured
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions

### Verify Your Setup

Run this simple test to ensure everything is configured correctly:

```bash
./gradlew :hkj-examples:test --tests "*Tutorial01_KindBasics.exercise1*"
```

If you see a test failure with "Answer required", you're ready to go!

## Recommended Learning Paths

See the full [Learning Paths](learning_paths.md) guide for detailed sequences. Here's a quick overview:

### Quick Start (2 sessions)
[Core: Foundations](coretypes/foundations_journey.md) → [Effect API](effect/effect_journey.md)

### Practical FP (4 sessions)
[Core: Foundations](coretypes/foundations_journey.md) → [Error Handling](coretypes/error_handling_journey.md) → [Effect API](effect/effect_journey.md) → [Optics: Lens & Prism](optics/lens_prism_journey.md)

### Optics Specialist (4 sessions)
[Lens & Prism](optics/lens_prism_journey.md) → [Traversals](optics/traversals_journey.md) → [Fluent & Free](optics/fluent_free_journey.md) → [Focus DSL](optics/focus_dsl_journey.md)

### Full Curriculum (8 sessions)
All journeys in recommended order. See [Learning Paths](learning_paths.md).

## What You'll Build

By the end of these tutorials, you'll have hands-on experience building:

### From Core Types Journeys:
- A **form validation system** using Applicative to combine independent checks
- A **data processing pipeline** using Monad to chain dependent operations
- An **error handling workflow** using `Either` and `Validated` for robust failure management
- A **configuration system** using `Reader` monad for dependency injection

### From Optics Journeys:
- A **user profile editor** with deep nested updates using Lens composition
- An **e-commerce order processor** using Traversals for bulk operations
- A **data validation pipeline** combining Lens, Prism, and Traversal
- A **multi-step workflow builder** using the Free Monad DSL with logging and validation

## Tips for Success

1. **One journey per sitting**: Each journey builds internal momentum. Splitting them reduces learning.

2. **Read the Hints**: They're there to guide you, not to slow you down. The Javadoc comments often contain the answer.

3. **Run Tests Frequently**: Don't write all exercises at once. Get one green, then move to the next.

4. **Experiment Fearlessly**: Try different approaches. Tests provide a safety net; you can't break anything.

5. **Don't Rush**: Understanding matters more than speed. Take breaks between journeys.

6. **Ask Questions**: Use [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions) if you're confused about a concept.

## Beyond the Tutorials

After completing the tutorials, continue your learning journey with:

- **[Example Code](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example)**: Production-quality examples in `hkj-examples`
- **[API Documentation](https://higher-kinded-j.github.io/home.html)**: Deep dives into every optic and typeclass
- **[Complete Walkthroughs](../hkts/order-walkthrough.md)**: See how the patterns combine in real applications
- **Your Own Projects**: Apply these patterns to your actual codebase

## Ready to Begin?

Choose your starting point:

**Recommended Path:**
1. [Foundations Journey](coretypes/foundations_journey.md) - Start here for core concepts
2. [Effect API Journey](effect/effect_journey.md) - The primary user-facing API (recommended next)

**Core Types Track (Foundation):**
- [Foundations Journey](coretypes/foundations_journey.md) - Start here for HKT basics
- [Error Handling Journey](coretypes/error_handling_journey.md) - Continue with error handling
- [Advanced Journey](coretypes/advanced_journey.md) - Master advanced patterns

**Optics Track:**
- [Lens & Prism Journey](optics/lens_prism_journey.md) - Start here for optics
- [Traversals Journey](optics/traversals_journey.md) - Collections and composition
- [Fluent & Free Journey](optics/fluent_free_journey.md) - Advanced APIs
- [Focus DSL Journey](optics/focus_dsl_journey.md) - Type-safe paths

Or see [Learning Paths](learning_paths.md) for detailed sequences.

Remember: The goal isn't to memorise every detail. It's to develop an intuition for when and how to apply these patterns. That only comes through practice.

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Core Types: Foundations Journey](coretypes/foundations_journey.md)
