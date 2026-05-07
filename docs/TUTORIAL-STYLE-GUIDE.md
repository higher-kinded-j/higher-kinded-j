# Tutorial Style Guide

This document defines the house style for Higher-Kinded-J tutorials. Tutorials are hands-on exercises that teach users how to use the library through practical coding challenges. Follow these guidelines to ensure consistency across all tutorial files.

## General Principles

### Language and Spelling

- Use **British English** spelling (e.g., "colour", "behaviour", "optimisation", "capitalise")
- Em dashes (`—`) are acceptable in `@DisplayName` values, Javadoc titles, and parenthetical asides where they improve scanning (for example, "Tutorial 00: One Line, Six Layers — The Whole Stack in a Single Expression"). Avoid using them as a replacement for commas or semicolons in long body prose
- Do not use emojis in documentation or code comments (exception: a single 🎉 is permitted in the final congratulations message of a tutorial series)
- Target **Java 25** and embrace modern Java features

### Tone and Voice

- Be encouraging but not patronising
- Prefer the inclusive first-person plural ("we will widen this to a `Kind`...") in narrative prose. Second person ("you") remains appropriate in tasks and instructions ("Task: extract the value...")
- Be concise and practical; avoid academic jargon
- Explain the "why" not just the "what"
- Assume the reader is a competent Java developer new to functional programming
- Anchor each abstraction to a familiar Java idiom whenever possible (see "Java Idiom Anchors" below)

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
- **Chapter anchors** (Tutorial 00): `Tutorial00_TopicName.java`. A chapter anchor is the optional opening file that touches every concept the rest of the chapter unpacks (for example, `Tutorial00_OneLineSixLayers.java`). It also doubles as a setup check — if it compiles and passes, the rest of the chapter will work
- **Capstones**: `TutorialCapstone_TopicName.java`. A capstone takes the chapter anchor expression (or another small motif from the chapter) and grows it into a realistic workflow that combines several capabilities. Capstones do not have a number; they always come last in the track

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
1. **Title line**: `Tutorial NN: Topic Name — Brief Description`
2. **Pain → Promise** (recommended): A short imperative-Java snippet showing the pain, followed by the Higher-Kinded-J version of the same workflow. This is the single most effective way to motivate the tutorial
3. **Java idiom anchor** (recommended): Two or three lines linking the new abstraction to a familiar Java type the reader has already used (`Stream`, `Optional`, `CompletableFuture`, `try`/`catch`, ...)
4. **Introduction**: 1-2 paragraphs explaining what the tutorial covers
5. **Key Concepts**: Bulleted list of concepts with brief explanations
6. **Prerequisites** (if applicable): Reference to prior tutorials
7. **Estimated time**: When the README time band is not obvious from the file alone (`<p>Estimated time: 25-35 minutes`)
8. **Tiered hints note**: If the file uses tiered hints (Nudge / Strategy / Spoiler), explain the convention once at class level
9. **Instruction**: Tell users to replace placeholders with working code

```java
/**
 * Tutorial 04: Monad — Chaining Dependent Computations
 *
 * <p>Pain → Promise. The imperative version of "first parse, then validate,
 * then divide" turns into a try/catch ladder with three exits. The HKJ version
 * is one fluent chain where the first failure short-circuits the rest:
 *
 * <pre>
 *   parse(input)
 *     .flatMap(this::validatePositive)
 *     .flatMap(this::divideHundredBy);
 * </pre>
 *
 * <p>Java idiom anchor. {@code flatMap} on {@link Either} is the same idea as
 * {@link java.util.Optional#flatMap Optional.flatMap}: chain a step that itself
 * returns the same effect, and let the framework propagate the empty / error case.
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

### @DisplayName

Every tutorial class and every exercise method should carry a JUnit `@DisplayName` so the IDE Test Runner and surefire/Gradle reports show the prose form of the title rather than the raw method name. The display name should match the title in the Javadoc (sans the leading "Exercise N:" prefix when desirable):

```java
@DisplayName("Tutorial 01: Kind Basics")
public class Tutorial01_KindBasics {

  @Test
  @DisplayName("Exercise 1: widen Either to Kind")
  void exercise1_widenEitherToKind() { ... }
}
```

For tutorials with `@Nested` groupings, give the nested class its own `@DisplayName` (for example, `"Part 1: Bridging the gap"`).

### Section Dividers

For visual separation between exercises within a long tutorial file, use ASCII box-drawing dividers as comment lines. Two conventions are in use:

```java
// ═════════════════════════════════════════════════════════════════════════
// Exercise 1: Widening a concrete type to Kind
// ═════════════════════════════════════════════════════════════════════════
```

```java
// ─── Exercise 1 ──────────────────────────────────────────────────────────
```

Pick one style per file and stay consistent. Heavy double-line dividers (`═`) are conventional for top-level exercise headings; light single-line dividers (`─`) are conventional for sub-sections such as domain models or fixtures.

### @Nested Groupings

When a tutorial naturally splits into parts (for example, "Bridging", "Composing", "Recovery"), use `@Nested` inner classes to group exercises rather than long flat files of unrelated tests:

```java
@Nested
@DisplayName("Part 1: Bridging the gap")
class BridgingExercises {

  @Test
  @DisplayName("Exercise 1: fromKind lifts a Future<Either> into EitherT")
  void exercise1_fromKindLiftsFutureEither() { ... }
}
```

Nested groupings are optional. Use them when:
- The tutorial has more than ~6 exercises
- The exercises split into logical phases that share fixtures
- The IDE Test Runner reads better with the grouping

### Java Idiom Anchors

Most readers come from a "regular Java" background. When introducing an abstraction, name the closest familiar Java idiom in two or three lines and explain what the new shape adds. This is more effective than a definition.

| Abstraction | Familiar Java idiom |
|---|---|
| `Kind<F, A>` | `Stream<Order>` is a shape (Stream) holding an element type (Order) |
| `flatMap` on Either | `Optional.flatMap` |
| `MaybePath` | a value that may or may not be present (no `null`) |
| `EitherPath` | typed errors instead of unchecked exceptions |
| `Lens.modify` | a "with" copy method on a record |
| `EitherT` | the shape `CompletableFuture<Either<L, R>>` collapsed into one composable layer |

Anchors should appear in the class-level Javadoc, ideally between the Pain → Promise block and the Key Concepts list.

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

### Tiered Hints (Nudge / Strategy / Spoiler)

The canonical hint format is a three-line ladder placed in the exercise's Javadoc inside a `<pre>` block, in increasing order of how much it gives away. Readers stop reading as soon as they have what they need:

```java
/**
 * Exercise 1: Widening to Kind.
 *
 * <p>Every higher-kinded type in this library can be widened to its
 * {@code Kind} representation.
 *
 * <p>Task: widen an {@code Either<String, Integer>} to
 * {@code Kind<EitherKind.Witness<String>, Integer>}.
 *
 * <pre>
 *   // Nudge:    There is a single helper called EITHER that knows how to widen.
 *   // Strategy: EITHER exposes a widen() method.
 *   // Spoiler:  EITHER.widen(either)
 * </pre>
 */
@Test
@DisplayName("Exercise 1: widen Either to Kind")
void exercise1_widenEitherToKind() { ... }
```

Conventions:

- **Nudge** — restates the concept that should fire. No method names, no values
- **Strategy** — names the method, helper, or value involved
- **Spoiler** — the literal expression the student would type

Align the three labels so the file reads as a table. When a tutorial uses tiered hints, mention the convention once at class level so readers know they can stop early.

The older single-line `// Hint:` comment style is still acceptable for short utility tutorials, but tiered hints are preferred for any tutorial whose exercises have more than one plausible approach:

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

### Diagnostic Exercises (Things People Get Wrong)

Towards the end of a tutorial, include a **diagnostic exercise** that shows a plausible-looking but wrong attempt and asks the reader to fix it. This converts a "common mistake" panel from passive reading into active practice.

A diagnostic exercise:

- Has a method name beginning with `diagnostic_`, not `exerciseN_`
- Carries a `@DisplayName` that names both the wrong shape and the lesson (for example, `"Diagnostic: focus narrows; modify inside map preserves the outer record"`)
- Includes the wrong code as a commented-out block with a short note explaining why it is wrong
- Asks the student to write the correct shape next to it
- Optionally demonstrates that the "wrong" tool is actually right for a different goal (so the lesson is "different tools for different jobs", not "this is bad")

```java
/**
 * Diagnostic exercise: a wrong attempt and its fix.
 *
 * <p>The code below tries to call {@code .focus(FocusPath.of(attributesLens))}
 * on an {@link EitherPath} hoping to "modify the inner map" — but
 * {@code EitherPath.focus(FocusPath)} narrows the path to the focused value,
 * so the outer {@link Item} is no longer in scope.
 *
 * <p>The lesson: when we want to update an inner field while keeping the outer
 * record, we use {@code lens.modify(...)} inside a {@code .map}.
 */
@Test
@DisplayName("Diagnostic: focus narrows; modify inside map preserves the outer record")
void diagnostic_focusVsModify() { ... }
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
4. Each `@Test` carries a teaching block above it (see "Teaching Solution Format" below)

```java
// Tutorial file:
// TODO: Replace null with code that uses EITHER.widen()
Kind<EitherKind.Witness<String>, Integer> kind = answerRequired();

// Solution file:
// SOLUTION: Use EITHER.widen() to convert Either to Kind
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);
```

### Teaching Solution Format

Solution files are not just answer keys; they are the second half of the tutorial. Every `@Test` in a solution file carries a Javadoc block above it in the **Why this is idiomatic / Alternative / Common wrong attempt** format:

```java
/**
 * Why this is idiomatic: {@code getOrElse} expresses intent — "give me the
 * value, or this default if absent" — without ever exposing a {@code null}
 * or forcing a downstream {@code if}.
 *
 * <p>Alternative: {@code path.run().getOrElse(...)} (calling through the
 * underlying Maybe). Same answer; the {@code MaybePath} method is just sugar.
 *
 * <p>Common wrong attempt: {@code path.run().get()}. Calling {@code get()}
 * on a {@code Nothing} throws — and silently masks the absent case, which
 * is exactly the problem we are trying to fix.
 */
@Test
@DisplayName("Exercise 1: Layer 1 (Effect)")
void exercise1_effectType() { ... }
```

Conventions:

- **Why this is idiomatic** — one or two sentences naming the principle the answer demonstrates. Not "this works"; rather, "this is the shape we want and here is why"
- **Alternative** — one shape that also passes the test, with a one-line note on the trade-off (usually: "more code, no benefit")
- **Common wrong attempt** — one plausible mistake and the symptom it produces. This is the same material as a "Things People Get Wrong" panel in prose, but tied to the actual exercise

When the exercise is purely a setup check or there is no meaningful alternative or wrong attempt, a single short paragraph is acceptable.

### Solution Comments

Use `// SOLUTION:` prefix for solution explanations within the method body:

```java
// SOLUTION: Use EITHER.widen() to convert Either to Kind
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);
```

The Javadoc teaching block above the test method does the heavier lifting; in-line `// SOLUTION:` comments should be reserved for non-obvious lines within longer answers.

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

### Track Shape

A complete tutorial track has three optional bookends around the numbered exercises:

| Position | File | Role |
|---|---|---|
| Opening | `Tutorial00_*` | Chapter anchor and setup check. One small expression that touches every layer the chapter unpacks. Doubles as the "your build is configured correctly" smoke test |
| Body | `Tutorial01_*` ... `TutorialNN_*` | The numbered exercises, in order of increasing complexity |
| Closing | `TutorialCapstone_*` | The chapter anchor grown up to a realistic workflow that combines several capabilities. No exercise number; always last |

Tracks need not include all three. Use Tutorial 00 when the chapter benefits from a single recurring anchor expression; use a Capstone when there is a meaningful "everything together" workflow worth practising.

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
- Transformer / capstone tutorials: 25-35 minutes

### Tracking Progress

The `tutorialProgress` Gradle task counts unanswered `answerRequired()` placeholders across every track and prints a per-journey progress bar. Authors should ensure that:

- Every placeholder in an in-progress tutorial uses `answerRequired()` (not `null`, not a thrown exception, not a magic constant). The task counts these and only these
- The default `test` task excludes in-progress tutorials and runs only solutions; `tutorialTest` includes the in-progress files for readers working through them with predictable failures
- New tutorial files are picked up automatically (the task scans the `tutorial/` source tree)

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
- [ ] Class-level Javadoc opens with a Pain → Promise block where one applies
- [ ] Class-level Javadoc names a familiar Java idiom anchor
- [ ] `@DisplayName` on the class and on every `@Test` method
- [ ] `@Nested` groupings used when the file has more than ~6 exercises that split into phases
- [ ] `answerRequired()` helper method is defined and used for every placeholder
- [ ] Each exercise has descriptive Javadoc with Task
- [ ] Tiered hints (Nudge / Strategy / Spoiler) supplied in a `<pre>` block, or short single-line `// Hint:` for trivial exercises
- [ ] Assertions use AssertJ
- [ ] At least one diagnostic exercise (`diagnostic_*`) where the tutorial covers an easily-confused pair of operations
- [ ] Section dividers use a single consistent style (heavy `═` or light `─`)
- [ ] Congratulations block with checkmarks is present
- [ ] Solution file exists with matching structure
- [ ] Solution file's every `@Test` has a "Why this is idiomatic / Alternative / Common wrong attempt" teaching block
- [ ] README.md is updated with the new tutorial
- [ ] Time estimate is included
- [ ] British English spelling throughout
- [ ] No emojis (except final 🎉 if appropriate)
- [ ] Records used for domain modelling
- [ ] Modern Java features utilised where appropriate
- [ ] `var` used sparingly; explicit types in declarations where the type aids learning
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
- [ ] Tutorial 00 chapter anchor (optional but recommended where the chapter has a recurring motif)
- [ ] Capstone tutorial (optional but recommended for chapters whose pieces compose)
- [ ] `tutorialProgress` continues to count placeholders correctly (every in-progress slot is `answerRequired()`)

---

## Relationship to STYLE-GUIDE.md

This tutorial style guide complements the main [STYLE-GUIDE.md](STYLE-GUIDE.md). Key alignments:

| Aspect | STYLE-GUIDE.md | TUTORIAL-STYLE-GUIDE.md |
|--------|----------------|-------------------------|
| Language | British English | British English |
| Em dashes | Avoid as comma replacement | Acceptable in titles, `@DisplayName`, and parenthetical asides |
| Emojis | Not permitted | Not permitted (except final 🎉) |
| Java version | Modern Java | Java 25, modern features |
| Tone | Practical, accessible, "we" voice | Encouraging, hands-on, "we" voice |
| Structure | Documentation pages | Exercise-based test files |
| Code format | Triple backticks | Inline Java code |
| Per-exercise teaching | n/a | "Why this is idiomatic / Alternative / Common wrong attempt" in solution files |

When in doubt about general style questions, defer to STYLE-GUIDE.md.
