# Interactive Tutorials: Learn Higher-Kinded-J by Building

The best way to understand Higher-Kinded Types and Optics isn't just reading about them—it's writing code, running tests, and seeing the patterns come alive in your IDE.

## What Makes These Tutorials Different?

Rather than passive reading, you'll:
- **Write Real Code**: Replace `answerRequired()` placeholders with working implementations
- **Get Immediate Feedback**: Each test fails until your solution is correct
- **Build Progressively**: Earlier concepts become tools for later challenges
- **See Practical Applications**: Every exercise solves a real problem Java developers face

Think of these as a guided laboratory for functional programming patterns in Java.

## Two Learning Tracks

The tutorials are organised into two complementary tracks. You can follow them in order or jump to whichever interests you most.

### [Core Types Track](coretypes_track.md) 🎯
**Duration**: ~60 minutes | **Tutorials**: 7 | **Exercises**: 45

Master the Higher-Kinded Types simulation that powers the library. Learn to work with `Kind<F, A>`, understand Functors, Applicatives, and Monads, and see how to build robust, composable workflows with types like `Either`, `Maybe`, and `Validated`.

**Perfect for**: Developers who want to understand the theoretical foundation, write generic code that works across multiple types, or use monads directly in their applications.

### [Optics Track](optics_track.md) 🔍
**Duration**: ~90 minutes | **Tutorials**: 9 | **Exercises**: 64

Master immutable data manipulation with Lenses, Prisms, and Traversals. Learn to perform deep updates on nested structures, work with collections elegantly, and build sophisticated data transformation pipelines using the Free Monad DSL.

**Perfect for**: Developers working with complex domain models, nested JSON structures, or anyone tired of verbose "copy-and-update" code in Java.

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
   - ❌ **Red**: Read the error message carefully—it contains clues
5. **Iterate** until you understand the pattern

### Getting Unstuck

If you're struggling with an exercise:

1. **Read the Javadoc carefully**: The comments contain hints and links to relevant documentation
2. **Check the type signatures**: What types does the method expect? What does it return?
3. **Look at earlier exercises**: You might have already used a similar pattern
4. **Consult the documentation**: Links are provided throughout the tutorials
5. **Peek at the solution**: Solutions are in `solutions/coretypes/` and `solutions/optics/` directories

> ⚠️ **Resist the temptation to copy-paste solutions!** You'll learn far more from struggling for 5 minutes than from reading the answer immediately. The struggle is where the learning happens.

## Prerequisites

### Required Knowledge
- **Java Fundamentals**: Records, generics, lambda expressions, method references
- **IDE Proficiency**: Running tests, navigating code, using auto-completion
- **Basic Functional Concepts**: Helpful but not required—we'll introduce them as needed

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

### Path 1: Foundation First (Recommended for Beginners)
1. Start with **Core Types Track** (Tutorials 01-04)
2. Switch to **Optics Track** (Tutorials 01-03)
3. Return to **Core Types** (Tutorials 05-07)
4. Finish with **Optics** (Tutorials 04-09)

**Why this path?** You'll understand the `Kind<F, A>` mechanism before seeing how it powers `modifyF` in optics. The interleaving keeps things fresh.

### Path 2: Optics Focused (For Practical Developers)
1. Complete **Optics Track** (All 9 tutorials)
2. Circle back to **Core Types Track** as needed

**Why this path?** If you just want to write better code with immutable data, optics give immediate practical value. You can learn the HKT theory later.

### Path 3: Theory to Practice (For Functional Enthusiasts)
1. Complete **Core Types Track** (All 7 tutorials)
2. Complete **Optics Track** (All 9 tutorials)

**Why this path?** A linear progression from foundational concepts to advanced applications. Perfect if you enjoy building up from first principles.

## What You'll Build

By the end of these tutorials, you'll have hands-on experience building:

### From Core Types Track:
- A **form validation system** using Applicative to combine independent checks
- A **data processing pipeline** using Monad to chain dependent operations
- An **error handling workflow** using `Either` and `Validated` for robust failure management
- A **configuration system** using `Reader` monad for dependency injection

### From Optics Track:
- A **user profile editor** with deep nested updates using Lens composition
- An **e-commerce order processor** using Traversals for bulk operations
- A **data validation pipeline** combining Lens, Prism, and Traversal
- A **multi-step workflow builder** using the Free Monad DSL with logging and validation

## Tips for Success

1. **Start from the Beginning**: Each tutorial builds on previous concepts. Skipping ahead leads to confusion.

2. **Read the Hints**: They're there to guide you, not to slow you down. The Javadoc comments often contain the answer.

3. **Run Tests Frequently**: Don't write all exercises at once. Get one green, then move to the next.

4. **Experiment Fearlessly**: Try different approaches. Tests provide a safety net—you can't break anything.

5. **Don't Rush**: Understanding matters more than speed. Take breaks if you're stuck.

6. **Ask Questions**: Use [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions) if you're confused about a concept.

## Beyond the Tutorials

After completing the tutorials, continue your learning journey with:

- **[Example Code](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example)**: Production-quality examples in `hkj-examples`
- **[API Documentation](https://higher-kinded-j.github.io/home.html)**: Deep dives into every optic and typeclass
- **[Complete Walkthroughs](../hkts/order-walkthrough.md)**: See how the patterns combine in real applications
- **Your Own Projects**: Apply these patterns to your actual codebase

## Ready to Begin?

Choose your track and start coding:

→ **[Core Types Track](coretypes_track.md)** - Understand the foundation
→ **[Optics Track](optics_track.md)** - Master immutable data manipulation

Remember: The goal isn't to memorise every detail. It's to develop an intuition for when and how to apply these patterns. That only comes through practice.

Happy learning! 🚀
