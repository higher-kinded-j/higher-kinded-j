# Tutorial Style Guide

This document defines the house style for Higher-Kinded-J tutorials. Tutorials are hands-on exercises that teach users how to use the library through practical coding challenges. Follow these guidelines to ensure consistency across all tutorial files.

## General Principles

### Language and Spelling

- Use **British English** spelling (e.g., "colour", "behaviour", "optimisation", "capitalise")
- Do not use em dashes (—); use commas or semicolons instead
- Do not use emojis in documentation or code comments (exception: a single 🎉 is permitted in the final congratulations message of a tutorial series)
- Target **Java 25** and embrace modern Java features

### Tone and Voice

- Be encouraging but not patronising
- Use second person ("You'll learn...") when addressing the reader
- Be concise and practical; avoid academic jargon
- Explain the "why" not just the "what"
- Assume the reader is a competent Java developer new to functional programming

### Modern Java Style

- Use Java records for domain modelling instead of classes with getters/setters
- Use sealed interfaces for sum types
- Use pattern matching where appropriate
- Prefer immutability throughout
- Use `var` sparingly; explicit types aid learning
- Use method references where they improve clarity (e.g., `String::toUpperCase`)

### Annotations as the Canonical Approach

Higher-Kinded-J provides annotation processors that generate boilerplate code. **Annotations are the canonical, recommended approach** for production code:

| Annotation | Generates | Use Case |
|------------|-----------|----------|
| `@GenerateLenses` | Lens accessors for record fields | Immutable field access |
| `@GeneratePrisms` | Prism accessors for sealed interface variants | Sum type handling |
| `@GenerateTraversals` | Traversal accessors for collection fields | Bulk operations |
| `@GenerateFocus` | Focus DSL entry points | Fluent navigation |

**Manual implementations should only appear in tutorials where understanding the fundamentals is the learning objective.** For example, Tutorial 01: Lens Basics manually creates lenses so students understand how `get` and `set` work under the hood. All subsequent tutorials should use generated optics.

When manual implementations are necessary for educational purposes, always include a prominent callout:

```java
/*
 * ========================================================================
 * IMPORTANT: Manual Implementation (For Educational Purposes Only)
 * ========================================================================
 *
 * This tutorial manually creates lenses to help you understand how they
 * work at a fundamental level. In real projects, ALWAYS use annotations:
 *
 *   @GenerateLenses
 *   record Person(String name, int age) {}
 *
 *   // Then use: PersonLenses.name(), PersonLenses.age()
 *
 * Manual implementations are error-prone and verbose. The annotation
 * processor generates optimised, tested code automatically.
 */
```

---

## Tutorial Structure

### File Organisation

Tutorials are organised into tracks and placed in the test source tree:

```
hkj-examples/src/test/java/org/higherkindedj/tutorial/
├── README.md                      # Overview of all tutorials
├── SOLUTIONS_REFERENCE.md         # Quick reference for all solutions
├── coretypes/                     # Core Types track
│   ├── Tutorial01_KindBasics.java
│   ├── Tutorial02_FunctorMapping.java
│   └── ...
├── optics/                        # Optics track
│   ├── Tutorial01_LensBasics.java
│   └── ...
└── solutions/                     # Complete solutions
    ├── SOLUTIONS.md
    ├── coretypes/
    │   └── Tutorial01_KindBasics_Solution.java
    └── optics/
        └── Tutorial01_LensBasics_Solution.java
```

### Naming Conventions

- Tutorial files: `TutorialNN_TopicName.java` (e.g., `Tutorial01_LensBasics.java`)
- Solution files: `TutorialNN_TopicName_Solution.java`
- Use PascalCase for topic names with underscores as separators
- Number tutorials sequentially within each track (01, 02, ..., 10, 11, ...)

---

## Tutorial File Structure

### Copyright Header

Every tutorial file must begin with the copyright and licence header:

```java
// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
```

### Package Declaration and Imports

Group imports logically:
1. Static imports (test assertions, helpers)
2. Java standard library
3. Higher-Kinded-J library classes
4. Test framework (JUnit)

```java
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.Test;
```

### Class-Level Javadoc

Every tutorial class must have comprehensive Javadoc that includes:
1. **Title line**: `Tutorial NN: Topic Name - Brief Description`
2. **Introduction**: 1-2 paragraphs explaining what the tutorial covers
3. **Key Concepts**: Bulleted list of concepts with brief explanations
4. **Prerequisites** (if applicable): Reference to prior tutorials
5. **Instruction**: Tell users to replace placeholders with working code

```java
/**
 * Tutorial 04: Monad - Chaining Dependent Computations
 *
 * <p>A Monad extends Applicative and provides flatMap (also called bind or >>=),
 * which allows you to chain computations where each step depends on the result
 * of the previous step.
 *
 * <p>Key Concepts:
 * <ul>
 *   <li>flatMap: chains dependent computations</li>
 *   <li>Each step can decide what to do based on previous results</li>
 *   <li>Automatically handles the context (no manual unwrapping needed)</li>
 * </ul>
 *
 * <p>Prerequisites: Complete Tutorials 1-3 before this one.
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
```

For advanced tutorials, use HTML lists (`<ul>`, `<li>`) for better formatting. For simpler tutorials, plain text with dashes is acceptable.

### The answerRequired() Helper

Every tutorial must include this helper method immediately after the class declaration:

```java
/** Helper method for incomplete exercises that throws a clear exception. */
private static <T> T answerRequired() {
  throw new RuntimeException("Answer required");
}
```

This method is used as a placeholder in exercises. Students replace `answerRequired()` calls with their solutions.

---

## Exercise Structure

### Exercise Format

Each exercise is a JUnit test method following this structure:

```java
/**
 * Exercise N: Descriptive Title
 *
 * <p>Explanation of the concept being taught. Use 1-2 paragraphs
 * to explain the context and why this matters.
 *
 * <p>Task: Clear, actionable instruction for what the student should do
 */
@Test
void exerciseN_descriptiveMethodName() {
    // Setup code (given)
    SomeType input = setupCode();

    // TODO: Replace null/answerRequired() with code that...
    // Hint: Specific hint about the solution approach
    SomeType result = answerRequired();

    // Assertions to verify the solution
    assertThat(result).isEqualTo(expectedValue);
}
```

### Exercise Naming

- Method names: `exerciseN_descriptiveMethodName` using camelCase
- Use verbs that describe the action: `widenEitherToKind`, `chainingDependentOperations`
- Keep names concise but meaningful

### Comments Within Exercises

Use `// TODO:` comments to mark where students need to write code:

```java
// TODO: Replace null with code that uses nameLens.get() to extract the name
String name = answerRequired();
```

Use `// Hint:` comments to provide guidance:

```java
// TODO: Replace null with chained flatMap operations
// Hint: input.flatMap(parse).flatMap(validatePositive).flatMap(divideHundredBy)
Either<String, Double> result = answerRequired();
```

### Important Callout Boxes

For important information that students should not miss, use block comments with clear headers:

```java
/*
 * ========================================================================
 * IMPORTANT: Section Title Here
 * ========================================================================
 *
 * Explanation of the important concept or warning.
 *
 * Key points:
 * ────────────────────────────────────────────────────────────────────────
 * 1. First important point
 * 2. Second important point
 * 3. Third important point
 */
```

See "Annotations as the Canonical Approach" above for the specific callout required when using manual optic implementations.

### Assertions

- Use AssertJ for all assertions (`assertThat()`)
- Test both positive cases and edge cases
- Verify that original values remain unchanged for immutable operations
- Include comments explaining what each assertion verifies when not obvious

```java
assertThat(updated.name()).isEqualTo("Bob");
assertThat(updated.age()).isEqualTo(30); // Other fields unchanged
assertThat(person.name()).isEqualTo("Alice"); // Original unchanged
```

---

## Congratulations Section

Every tutorial must end with a congratulations comment block:

```java
/**
 * Congratulations! You've completed Tutorial 04: Monad Chaining
 *
 * <p>You now understand:
 * <ul>
 *   <li>✓ How to use flatMap to chain dependent computations</li>
 *   <li>✓ The difference between map (plain values) and flatMap (wrapped values)</li>
 *   <li>✓ That flatMap short-circuits on errors</li>
 *   <li>✓ How flatMap works with different types (Either, Maybe, List)</li>
 * </ul>
 *
 * <p>Next: Tutorial 05 - Monad Error Handling
 */
```

For advanced tutorials, you may also include:

```java
 * <p>Key Takeaways:
 * <ul>
 *   <li>FocusPath wraps Lens - always focuses on exactly one element</li>
 *   <li>AffinePath wraps Affine - may focus on zero or one element</li>
 * </ul>
 *
 * <p>Next Steps:
 * <ul>
 *   <li>Use @GenerateFocus annotation to generate Focus classes</li>
 *   <li>Combine Focus DSL with Free Monad DSL (Tutorial 11)</li>
 * </ul>
```

---

## Solution Files

### Solution File Structure

Solution files mirror the tutorial files exactly, with these differences:

1. Class name ends with `_Solution`
2. Placeholder comments become solution comments
3. `answerRequired()` calls are replaced with actual code

```java
// Tutorial file:
// TODO: Replace null with code that uses EITHER.widen()
Kind<EitherKind.Witness<String>, Integer> kind = answerRequired();

// Solution file:
// SOLUTION: Use EITHER.widen() to convert Either to Kind
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);
```

### Solution Comments

Use `// SOLUTION:` prefix for solution explanations:

```java
// SOLUTION: Use EITHER.widen() to convert Either to Kind
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);
```

---

## Domain Modelling in Tutorials

### Record Definitions

Define records inline within test classes for simple examples:

```java
record Person(String name, int age, String email) {}
```

For more complex domain models, define them as static nested types or before the exercises:

```java
// --- Domain models for exercises ---

/** API response sum type for cross-optic composition exercises. */
sealed interface ApiResponse permits Success, ClientError, ServerError {}

record Success(ResponseData data, String timestamp) implements ApiResponse {}
record ClientError(String message, int code) implements ApiResponse {}
record ServerError(String message, String stackTrace) implements ApiResponse {}
```

### Naming Conventions for Domain Types

- Use realistic, domain-appropriate names: `Person`, `Order`, `Config`, `User`
- Use descriptive field names: `username`, `emailAddress`, `createdAt`
- For validation examples, use clear error types: `ValidationError`, `ParseError`

---

## Progressive Learning Design

### Tutorial Progression

Tutorials should follow a logical progression:

1. **Basics** (01-02): Introduce core concepts with simple examples
2. **Intermediate** (03-05): Build on basics with more complex scenarios
3. **Applied** (06-07): Real-world applications combining multiple concepts
4. **Advanced** (08+): Deep dives into specific patterns and DSLs

### Exercise Progression Within a Tutorial

Within a single tutorial, exercises should progress from simple to complex:

1. **Introduction**: Basic usage of the concept
2. **Variation**: Different scenarios or types
3. **Composition**: Combining with other concepts
4. **Real-world**: Practical application
5. **Edge cases**: Error handling, empty cases

### Time Estimates

Include time estimates in the README.md for each tutorial:

```markdown
### Tutorial 01: Kind Basics (~8 minutes)
Learn the foundation of higher-kinded types in Java:
- Understanding `Kind<F, A>`
- Widening and narrowing
- Witness types
```

Typical estimates:
- Basic tutorials: 8-10 minutes
- Intermediate tutorials: 10-12 minutes
- Advanced tutorials: 12-15 minutes

---

## Writing Style Examples

### Good: Clear, Practical Explanation

```java
/**
 * Exercise 2: Setting a value with a Lens
 *
 * <p>The set method creates a NEW structure with the field updated.
 * The original is unchanged.
 *
 * <p>Task: Use a lens to update the name
 */
```

### Avoid: Academic or Vague Explanation

```java
/**
 * Exercise 2: Lens Set Operation
 *
 * <p>The set operation is a morphism in the category of lenses that
 * produces a modified copy of the source structure.
 *
 * <p>Task: Apply the set combinator
 */
```

### Good: Helpful Hint

```java
// Hint: input.flatMap(parse).flatMap(validatePositive).flatMap(divideHundredBy)
```

### Avoid: Giving Away the Answer or Being Too Vague

```java
// Hint: Use flatMap  (too vague)
// Hint: The answer is input.flatMap(parse)...  (gives it away)
```

---

## Common Patterns

### Validation Functions

```java
Function<String, Either<String, Integer>> parse = s -> {
    try {
        return Either.right(Integer.parseInt(s));
    } catch (NumberFormatException e) {
        return Either.left("Not a number");
    }
};
```

### Database/Lookup Simulations

```java
Function<String, Maybe<User>> findUser = id -> {
    if (id.equals("user1")) {
        return Maybe.just(new User("user1", "Alice", 25));
    }
    return Maybe.nothing();
};
```

### Configuration-Based Operations

```java
record Config(String appName, String version, boolean debugMode) {}

Function<Config, Function<Order, ProcessedOrder>> processOrder = config -> order -> {
    double subtotal = order.quantity() * order.price();
    double tax = subtotal * config.taxRate();
    return new ProcessedOrder(order.id(), subtotal, subtotal + tax);
};
```

---

## Linking Tutorials to Documentation Pages

Each tutorial should be linked from its corresponding documentation page in hkj-book. This creates a bidirectional relationship: readers can find hands-on practice from the docs, and tutorial users can find deeper explanations in the docs.

### Documentation Page Requirements

When a documentation page has an associated tutorial, add **two** admonishments:

**1. Quick link near the top** (after "What You'll Learn"):

```markdown
~~~admonish title="Hands On Practice"
[Tutorial14_FocusEffectBridge.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial14_FocusEffectBridge.java)
~~~
```

**2. Detailed link near the bottom** (before "See Also"):

```markdown
~~~admonish info title="Hands-On Learning"
Practice Focus-Effect bridging in [Tutorial 14: Focus-Effect Bridge](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial14_FocusEffectBridge.java) (13 exercises, ~15 minutes).
~~~
```

### Admonishment Placement

The two admonishments serve different purposes:

| Position | Admonishment | Purpose |
|----------|--------------|---------|
| After "What You'll Learn" | `~~~admonish title="Hands On Practice"` | Quick access for readers who want to jump straight to exercises |
| Before "See Also" | `~~~admonish info title="Hands-On Learning"` | Contextual reminder with exercise count and time estimate |

### Link Format

Always use the full GitHub URL to the tutorial file on the `main` branch:

```
https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/{track}/TutorialNN_TopicName.java
```

Include in the detailed link:
- Tutorial number and name
- Exercise count
- Time estimate (in minutes)

### Tutorial-to-Doc References

In the tutorial's class-level Javadoc, reference the corresponding documentation page:

```java
/**
 * Tutorial 14: Focus-Effect Bridge
 *
 * <p>This tutorial covers bridging between the Focus DSL and Effect Paths.
 *
 * <p>See the documentation: Focus-Effect Integration in hkj-book
 *
 * <p>Key Concepts:
 * ...
 */
```

---

## Checklist for New Tutorials

When creating a new tutorial, ensure:

- [ ] Copyright header is present
- [ ] Package declaration matches directory structure
- [ ] Imports are organised (static, java, library, test)
- [ ] Class-level Javadoc includes title, introduction, and key concepts
- [ ] `answerRequired()` helper method is defined
- [ ] Each exercise has descriptive Javadoc with Task
- [ ] TODO and Hint comments are provided
- [ ] Assertions use AssertJ
- [ ] Congratulations block with checkmarks is present
- [ ] Solution file exists with matching structure
- [ ] README.md is updated with the new tutorial
- [ ] Time estimate is included
- [ ] British English spelling throughout
- [ ] No emojis (except final 🎉 if appropriate)
- [ ] Records used for domain modelling
- [ ] Modern Java features utilised where appropriate
- [ ] Annotations used for optics (except in fundamental-concept tutorials)
- [ ] Manual implementations have prominent educational callouts
- [ ] Corresponding doc page has "Hands On Practice" admonishment (top)
- [ ] Corresponding doc page has "Hands-On Learning" admonishment (bottom)

## Checklist for Tutorial Series

When creating a new tutorial track, ensure:

- [ ] Track README.md exists with overview
- [ ] Tutorials are numbered sequentially (01, 02, ...)
- [ ] Progressive difficulty from basics to advanced
- [ ] Solutions directory with all solution files
- [ ] SOLUTIONS_REFERENCE.md updated with quick reference
- [ ] Consistent naming across the track
- [ ] Cross-references to prerequisite tutorials
- [ ] Real-world application tutorial near the end

---

## Relationship to STYLE-GUIDE.md

This tutorial style guide complements the main [STYLE-GUIDE.md](STYLE-GUIDE.md). Key alignments:

| Aspect | STYLE-GUIDE.md | TUTORIAL-STYLE-GUIDE.md |
|--------|----------------|-------------------------|
| Language | British English | British English |
| Emojis | Not permitted | Not permitted (except final 🎉) |
| Java version | Modern Java | Java 25, modern features |
| Tone | Practical, accessible | Encouraging, hands-on |
| Structure | Documentation pages | Exercise-based test files |
| Code format | Triple backticks | Inline Java code |

When in doubt about general style questions, defer to STYLE-GUIDE.md.
