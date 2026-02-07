# Documentation Restructure Plan: Monad Transformers for the Java Ecosystem

Based on the pedagogical report analyzing how to restructure Monad Transformer documentation for Java developers, this plan maps the report's 6 strategic proposals against the current state of the HKJ book and identifies concrete, actionable work items.

---

## Executive Summary

The HKJ documentation is already well-structured in several important ways:
- The Effect Path API is the first chapter (Path-First ordering)
- The Railway model is introduced with the Pyramid of Doom motivator
- Transformers are under "Advanced Topics" (not front and centre)
- HKT foundations are at the bottom under "Foundations"

The main gaps are: no quickstart for impatient developers, missing named Stack Archetypes, no Production Readiness section, incomplete ROP terminology mapping, no Spring-specific refactoring recipes, no cheat sheet, and insufficient before/after code comparisons as a consistent pattern throughout the docs.

---

## Gap Analysis

| Report Proposal | Current Status | Work Needed |
|----------------|---------------|-------------|
| 1. Path-First Strategy | **Done** - SUMMARY.md already leads with Effect Path API | Minor: add a bridging paragraph from transformers back to Paths |
| 2. ROP Core Metaphor | **Mostly Done** - Railway diagrams with green/red colouring in overview; operator-to-ROP mapping table added | Enhance per-operator diagrams (Task 1.2) |
| 3. Focus-Effect Capstone | **Partial** - focus_integration.md + OrderWorkflow exist separately | Create unified before/after capstone threading both chapters |
| 4. Spring Boot Recipes | **Partial** - Spring integration docs exist | Add "Functional Controller" and "Transactional Reader" recipes |
| 5. Stack Archetypes | **Missing** - No named archetypes | Create archetype table and per-archetype guidance |
| 6. Performance & Debugging | **Missing** - No production readiness section | Create dedicated section on stack traces, allocation, trampolining |

**Additional gaps identified beyond the report:**

| Gap | Current Status | Work Needed |
|-----|---------------|-------------|
| Quickstart | **Done** - `quickstart.md` created with Gradle/Maven setup, preview flags, 4 examples | None |
| Cheat Sheet | **Done** - `cheatsheet.md` created with 19 path types, operators, escape hatches, type conversions | None |
| Decision Flowchart | **Done** - ASCII flowchart added to `path_types.md` | None |
| Migration Cookbook | **Missing** - No imperative-to-FP pattern mapping | "I have try/catch → here's TryPath" recipes |
| Escape Hatches | **Done** - Dedicated section in `effect_path_overview.md` + table in `cheatsheet.md` | None |
| Compiler Error Guide | **Missing** - No help for cryptic generic errors | Top 5 compiler errors and what they mean |

---

## Actionable Work Items

### Phase 0: Quickstart and Developer Onboarding ✓ COMPLETE

> **Goal:** Give impatient developers working code in under 5 minutes, and give returning developers a one-page reference they bookmark.
>
> **Status:** All 7 tasks complete. Quickstart and Cheat Sheet created, SUMMARY.md updated, home.md and README.md slimmed down, decision flowchart and escape hatches added.

#### Style Guide Compliance

All new pages in this phase must follow `docs/STYLE-GUIDE.md`:
- British English spelling throughout (e.g., "colour", "behaviour", "optimisation")
- No em dashes; use commas or semicolons instead
- No emojis
- `# Title` as level-1 heading
- `~~~admonish info title="What You'll Learn"` section near the top
- Horizontal rules (`---`) between major sections
- Navigation links (`**Previous:** / **Next:**`) at the bottom
- Example code links via `~~~admonish example title="See Example Code"` where applicable
- Code blocks with language specifier (` ```java `, ` ```gradle `, ` ```xml `)

---

#### Task 0.1: Create Quickstart Page ✓

**File:** New `hkj-book/src/quickstart.md` — CREATED

**Structure:**

```
# Quickstart

~~~admonish info title="What You'll Learn"
- How to add Higher-Kinded-J to a Gradle or Maven project
- Java 25 preview mode configuration (required)
- Your first Effect Path in under 5 minutes
~~~

---

## Prerequisites

> Java 25+ with preview features enabled. Higher-Kinded-J uses
> Java preview features (stable values, flexible constructor bodies).

---

## Gradle Setup

```gradle
// build.gradle.kts
plugins { java }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:LATEST_VERSION")
}

// Required: enable Java preview features
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}
```

## Maven Setup

```xml
<properties>
    <maven.compiler.release>25</maven.compiler.release>
    <maven.compiler.enablePreview>true</maven.compiler.enablePreview>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.higher-kinded-j</groupId>
        <artifactId>hkj-core</artifactId>
        <version>LATEST_VERSION</version>
    </dependency>
</dependencies>

<!-- Required for test execution with preview features -->
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <argLine>--enable-preview</argLine>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Example 1: Handle Absence (MaybePath)

(6 lines: Path.maybe → map → run → orElse)

## Example 2: Handle Errors (EitherPath)

(8 lines: Path.right → via → map → run → fold)

## Example 3: Validate Input (ValidationPath)

(10 lines: Path.valid → zipWith → map → run)

## Example 4: Chain It Together

(12 lines: combining MaybePath → toEitherPath → via → map)

## Getting Back to Standard Java

(4 lines showing .run(), .fold(), .toOptional(), .getOrElse())

~~~admonish tip title="See Also"
- [Effect Path Overview](effect/effect_path_overview.md) - The railway model explained
- [Path Types](effect/path_types.md) - Choosing the right path for your problem
- [Cheat Sheet](cheatsheet.md) - One-page operator reference
~~~

---

**Next:** [Cheat Sheet](cheatsheet.md)
```

**Key requirements:**
- Java 25 preview mode must be prominently flagged with both Gradle and Maven configurations
- The Gradle config must show `--enable-preview` for compile, test, and run tasks (matching the project's own `build.gradle.kts`)
- The Maven config must show `maven.compiler.enablePreview` property and surefire `--enable-preview` arg
- Examples must use only `Path.*` factory methods; no raw transformers, no Kind types, no witness types
- One-line comments only; no prose between code lines
- The entire page should fit on two screens (aim for ~120 lines of markdown)

**Why:** A large segment of developers learn by running code first and reading docs second. The current home.md is well-written but is a 300+ line narrative. The quickstart is the "show me the code" alternative. The `--enable-preview` requirement is currently absent from all user-facing docs; this is the first place to fix it.

---

#### Task 0.2: Create Cheat Sheet ✓

**File:** New `hkj-book/src/cheatsheet.md` — CREATED

**Structure:**

```
# Cheat Sheet

~~~admonish info title="What You'll Learn"
- Quick reference for all Path types, operators, and escape hatches
- At-a-glance lookup for daily use
~~~
```

Three reference tables (as previously specified in the plan):

**Section 1: Path Types at a Glance**

| Type | For | Create with | Run with |
|------|-----|-------------|----------|
| `MaybePath<A>` | Absence | `Path.maybe(v)` / `Path.just(v)` | `.run()` |
| `EitherPath<E, A>` | Typed errors | `Path.right(v)` / `Path.left(e)` | `.run()` |
| `TryPath<A>` | Exceptions | `Path.tryOf(() -> ...)` | `.run()` |
| `ValidationPath<E, A>` | Accumulating errors | `Path.valid(v, sg)` | `.run()` |
| `IOPath<A>` | Side effects | `Path.io(() -> ...)` | `.unsafeRun()` |
| `VTaskPath<A>` | Concurrency | `Path.vtask(() -> ...)` | `.run()` |
| `ReaderPath<R, A>` | Dependency injection | `Path.reader(fn)` | `.run(env)` |
| `WriterPath<W, A>` | Logging/audit | `Path.writer(v, log)` | `.run()` |
| `WithStatePath<S, A>` | Stateful computation | `Path.state(fn)` | `.run(initial)` |
| `TrampolinePath<A>` | Stack-safe recursion | `Path.trampoline(...)` | `.run()` |
| `LazyPath<A>` | Deferred + memoised | `Path.lazy(() -> ...)` | `.run()` |

**Section 2: Common Operators**

(as previously specified, plus `zipWith`, `zipWith3`, `parZipWith`, `fold`)

**Section 3: Escape Hatches**

(as previously specified, expanded with all path types)

**Section 4: Type Conversions**

| From | To | How |
|------|----|-----|
| `MaybePath` | `EitherPath` | `.toEitherPath(errorSupplier)` |
| `TryPath` | `EitherPath` | `.toEitherPath(exMapper)` |
| `EitherPath` | `MaybePath` | `.toMaybePath()` |
| `FocusPath` | `MaybePath` | `.toMaybePath()` |
| `FocusPath` | `EitherPath` | `.toEitherPath(errorFn)` |

**Style guide compliance:**
- "What You'll Learn" admonishment at top
- Horizontal rules between sections
- Navigation links: `**Previous:** [Quickstart](quickstart.md)` / `**Next:** [Effect Path API](effect/ch_intro.md)`
- No em dashes; semicolons where needed
- British English throughout

**Why:** Developers bookmark cheat sheets. This becomes the page they return to daily until the API is muscle memory.

---

#### Task 0.3: Add "Which Path?" Visual Flowchart ✓

**File:** `hkj-book/src/effect/path_types.md` — UPDATED (ASCII flowchart added above existing prose)

**What:** Add an ASCII decision tree **above** the existing prose-based guide. The flowchart should cover the 6 most common path types. The existing prose sections remain below as the detailed explanation.

```
                         START HERE
                             │
                  Can the operation fail?
                       /          \
                     No            Yes
                     │              │
              Is the value      Do you need ALL
              optional?          errors at once?
               /     \            /          \
             Yes      No        Yes           No
              │        │         │             │
          MaybePath  IdPath  ValidationPath    │
                                          Is the error a
                                          typed domain error
                                          or an exception?
                                           /            \
                                        Typed        Exception
                                         │              │
                                     EitherPath     TryPath
```

Add a brief note below: *"For deferred side effects, see `IOPath`. For virtual-thread concurrency, see `VTaskPath`. For stack-safe recursion, see `TrampolinePath`."*

**Why:** Faster to scan than prose. Developers in a hurry can follow the arrows in 5 seconds.

---

#### Task 0.4: Add Escape Hatches Section to Effect Path Overview ✓

**File:** `hkj-book/src/effect/effect_path_overview.md` — UPDATED (escape hatches section added before Summary)

**What:** Add a section titled **"Getting Back to Standard Java"** near the end of the overview, before any "What's Next" or navigation links. Include a brief intro paragraph and code block:

```java
// Every path has a clean exit
Maybe<User> maybe = maybePath.run();                       // Maybe
Either<AppError, User> either = eitherPath.run();          // Either
Optional<User> opt = maybePath.run().toOptional();         // java.util.Optional
User user = eitherPath.run().getOrElse(User.anonymous());  // raw value
String msg = tryPath.run().fold(Throwable::getMessage, Object::toString);
```

One-sentence framing: *"You are never locked in. Every Path type unwraps to a standard Java value with `.run()`, and `.fold()` gives you full control over both tracks."*

**Why:** Adoption anxiety is real. Developers worry about committing to a paradigm they cannot escape. Showing the exit reduces the perceived risk of trying the library.

---

#### Task 0.5: Update SUMMARY.md ✓

**File:** `hkj-book/src/SUMMARY.md` — UPDATED (Quickstart and Cheat Sheet added as first entries)

**What:** Add Quickstart and Cheat Sheet as the first entries after the home page:

```markdown
[Introduction to Higher-Kinded-J](home.md)

- [Quickstart](quickstart.md)
- [Cheat Sheet](cheatsheet.md)

- [Effect Path API](effect/ch_intro.md)
  ...
```

These must be top-level entries (not nested under a chapter) so they appear as the first sidebar items after the landing page.

---

#### Task 0.6: Slim Down home.md (Content Migration) ✓

**File:** `hkj-book/src/home.md` — UPDATED (Getting Started replaced with quickstart link; Documentation Guide updated)

**What to change:**

1. **Replace the "Getting Started" section** (lines ~250-284 in current home.md) with a short paragraph and link:
   > Ready to start? See the **[Quickstart](quickstart.md)** for setup instructions and your first Effect Path in 5 minutes.

   The full Gradle/Maven dependency blocks and Java 25 note currently in home.md move to quickstart.md. This eliminates duplication and ensures there is a single source of truth for setup instructions (the quickstart).

2. **Keep the "Path Types at a Glance" table** in home.md. It serves a different purpose here (selling the breadth of the library) than in the cheatsheet (daily reference with create/run columns). No change needed.

3. **Keep the before/after code examples**. The home.md examples are narrative-driven (demonstrating the pyramid problem). The quickstart examples are copy-paste-driven (minimal, self-contained). They serve different audiences.

4. **Add a link to the Cheat Sheet** in the "Documentation Guide" section, under "Effect Path API (Start Here)":
   > 0. **[Quickstart](quickstart.md):** Setup and first examples in 5 minutes
   > 0b. **[Cheat Sheet](cheatsheet.md):** One-page operator reference

**Why:** home.md should be the "why" page (narrative, vision, selling points). quickstart.md should be the "how" page (setup, copy-paste, go). Currently home.md tries to be both.

---

#### Task 0.7: Slim Down README.md (Content Migration) ✓

**File:** `README.md` (project root) — UPDATED (--enable-preview added to Requirements and Gradle snippet; quickstart link added)

**What to change:**

1. **Replace the "How to Use This Library" section** with a shorter version that links to the quickstart:
   ```markdown
   ## Getting Started

   > **Requires Java 25** with preview features enabled.

   ```gradle
   dependencies {
       implementation("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")
       annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:LATEST_VERSION")
   }
   ```

   See the **[Quickstart Guide](https://higher-kinded-j.github.io/latest/quickstart.html)** for full setup including Maven, preview flags, and first examples.
   ```

2. **Add `--enable-preview` to the Requirements section.** The current README says "Java Development Kit (JDK): Version 25 or later" but does not mention preview features. Update to:
   > **Java Development Kit (JDK): Version 25** or later, with `--enable-preview` enabled. Higher-Kinded-J uses Java preview features. See the [Quickstart Guide](https://higher-kinded-j.github.io/latest/quickstart.html) for Gradle and Maven configuration.

3. **Keep all other content** (feature comparison table, Spring Boot section, project structure, etc.). The README serves GitHub visitors who may never visit the docs site; it needs to remain self-contained for its audience.

**Why:** The README currently duplicates the dependency block without the critical `--enable-preview` flags. Linking to the quickstart creates a single source of truth and fixes the missing preview requirement.

---

### Phase 1: Enhance the Railway Metaphor (Effect Path Chapter) — PARTIALLY COMPLETE

#### Task 1.1: Add ROP Operator Mapping Table ✓
**File:** `hkj-book/src/effect/effect_path_overview.md` — UPDATED (operator mapping table added with green/red colour coding)
**What:** Add a reference table mapping every Path operator to its ROP concept:

| Operator | ROP Concept | Description |
|----------|-------------|-------------|
| `map` | Green Track transform | Transforms data on the success track |
| `mapError` | Red Track transform | Transforms data on the error track |
| `via` / `flatMap` | Track Switch | Chains an operation that may divert success to failure |
| `recover` | Red-to-Green Switch | Recovers from failure back to success |
| `ensure` | Green-to-Red Switch | Validates, diverting success to failure |
| `peek` | Siding | Side-effect observation without changing tracks |
| `orElse` | Fallback Junction | Tries an alternative path if the first fails |

**Why:** Makes the metaphor concrete and gives developers a shared vocabulary.

#### Task 1.2: Enhance Railway Diagrams Per Operator
**File:** `hkj-book/src/effect/effect_path_overview.md`
**What:** For each operator in the mapping table, add a small ASCII diagram showing the track behaviour. Example for `via`:

```
  Success ═══●══╗
                ╠═══ via(fn) ═══●═══▶ continues on success
  Failure ═══●══╝                 ╲
                                   ╲══▶ diverts to failure
```

**Why:** Visual learners need to see the switch points.

---

### Phase 2: Stack Archetypes (Transformers Chapter)

#### Task 2.1: Create Stack Archetypes Reference
**File:** `hkj-book/src/transformers/ch_intro.md` (or new `transformers/archetypes.md`)
**What:** Define 3-4 named archetype stacks that cover common use cases:

| Archetype | Path Type | Raw Stack | Use Case |
|-----------|-----------|-----------|----------|
| **The Service Stack** | `EitherPath<AppError, A>` | `EitherT<IO, AppError, A>` | Standard business logic (async + typed failure) |
| **The Lookup Stack** | `MaybePath<A>` | `MaybeT<IO, A>` | Optional lookups (async + absence) |
| **The Validation Stack** | `ValidationPath<List<String>, A>` | N/A (direct) | Form/input validation (accumulating errors) |
| **The Context Stack** | `ReaderPath<Config, A>` | `ReaderT<IO, Config, Either<E, A>>` | Systems with shared context (tracing, auth, config) |

For each archetype include:
- A 3-sentence description of when to use it
- A minimal code example (5-10 lines)
- The Path API equivalent (showing that users don't need the raw stack)

**Why:** Reduces cognitive load by giving developers named patterns rather than requiring them to design transformer stacks from scratch. Aligns with Cognitive Load Theory (the report's "chunks" argument).

#### Task 2.2: Add "Path First, Stack Later" Bridge
**File:** `hkj-book/src/transformers/transformers.md`
**What:** Add a prominent note at the top of the transformers chapter:

> **Most users don't need to read this chapter.** The Effect Path API (Chapter 1) wraps these transformers into a fluent interface. Start there. Come here when you need to build custom transformer stacks or understand what happens under the hood.

Include a cross-reference table showing which Path type wraps which transformer.

**Why:** Prevents intermediate developers from diving into raw transformers when the Path API would serve them better.

---

### Phase 3: Capstone Example (Focus-Effect Unification)

#### Task 3.1: Create Unified Before/After Capstone
**File:** New section in `hkj-book/src/effect/focus_integration.md` or new `hkj-book/src/examples/capstone_order.md`
**What:** A single end-to-end example that demonstrates Effects + Optics working together. The report's suggested scenario is good:

**Scenario:** Update the shipping address postcode of an order.
- Load order asynchronously (Async)
- Order might not exist (Optional)
- Validate the new postcode (Failure)
- Shipping address is nested 3 levels deep (Optics)

**Before (Standard Java):** 20+ lines of nested if/else, try/catch, manual field access.

**After (HKJ):**
```java
return Path.from(repo.findOrder(id))         // MaybePath
    .toEitherPath(OrderNotFound::new)         // → EitherPath
    .focus(OrderFocus.shippingAddress())       // Lens into nested data
    .focus(AddressFocus.postcode())            // Deeper lens
    .via(this::validatePostcode)               // Validation
    .map(Order::save);                         // Persist
```

**Why:** Demonstrates the unique value of HKJ (no other Java library combines transformers + optics this cleanly). This should be the "aha moment" in the documentation.

#### Task 3.2: Link Capstone from Multiple Chapters
**Files:** `effect/ch_intro.md`, `optics/ch5_intro.md`, `transformers/ch_intro.md`
**What:** Add cross-references to the capstone from each relevant chapter's intro, so readers discover it from wherever they enter the documentation.

---

### Phase 4: Spring Boot Refactoring Recipes

#### Task 4.1: "Functional Controller" Recipe
**File:** `hkj-book/src/spring/spring_boot_integration.md` or new `spring/recipes.md`
**What:** Show how to convert `EitherPath<AppError, T>` directly into a Spring `ResponseEntity`:

```java
@GetMapping("/users/{id}")
public ResponseEntity<?> getUser(@PathVariable String id) {
    return userService.findUser(id)              // EitherPath<AppError, User>
        .run()
        .fold(
            error -> switch (error) {
                case NotFound e -> ResponseEntity.notFound().build();
                case ValidationError e -> ResponseEntity.badRequest().body(e.messages());
                case Unauthorized e -> ResponseEntity.status(403).build();
            },
            user -> ResponseEntity.ok(user)
        );
}
```

Include an explanation of why this replaces scattered `try/catch` + `@ExceptionHandler` patterns.

#### Task 4.2: "ReaderT vs @Autowired" Bridge
**File:** `hkj-book/src/effect/advanced_effects.md` or `spring/spring_boot_integration.md`
**What:** Add a section explicitly comparing Spring's DI with ReaderT/ReaderPath:

| Concept | Spring DI | ReaderPath |
|---------|-----------|------------|
| Provide dependency | `@Autowired` on field | `.run(context)` at the edge |
| Access dependency | Direct field access | `.ask()` / `Path.reader(ctx -> ...)` |
| Scope | Container-managed (singleton/request) | Explicit, passed through composition |
| Testing | MockBean / TestConfiguration | Just pass a test context |

**Why:** Java developers think in Spring. Meeting them where they are reduces the conceptual leap.

---

### Phase 5: Production Readiness Section

#### Task 5.1: Create Production Readiness Page
**File:** New `hkj-book/src/effect/production_readiness.md`
**What:** Three subsections:

**Stack Traces:**
- Acknowledge that Path chains create deeper stack traces than imperative code
- Show an annotated example of a typical stack trace and how to read it
- Explain that `peek` can be used to add debug logging at specific points in the chain

**Allocation Overhead:**
- Explain that each step in a Path chain creates a small wrapper object
- Provide a cost comparison: wrapper allocation (nanoseconds) vs I/O operations (milliseconds)
- Conclusion: "The overhead is negligible compared to any database call, HTTP request, or file operation"

**Stack Safety (Trampolining):**
- Explain that the JVM lacks Tail Call Optimization
- Show when recursive Path chains can overflow the stack
- Introduce `TrampolinePath` as the solution
- Provide a simple before/after example

#### Task 5.2: Link from Transformers and Effect Chapters
**Files:** `effect/ch_intro.md`, `transformers/ch_intro.md`
**What:** Add Production Readiness to the chapter contents and SUMMARY.md.

---

### Phase 6: Minor Enhancements

#### Task 6.1: Consistent Before/After Pattern
**Files:** Multiple effect and transformer docs
**What:** Audit existing documentation and ensure that wherever a concept is introduced, there's a clear before/after comparison:
- **Before:** The imperative Java code with the problem
- **After:** The HKJ code solving it
- Each pair should have brief annotations explaining what changed

#### Task 6.2: Add Transformer Railway Diagrams
**File:** `hkj-book/src/transformers/transformers.md`
**What:** Add ROP-style diagrams to the transformer chapter showing how `EitherT`, `MaybeT`, etc. provide unified track management across two effect layers. Currently the transformer docs have good ASCII art for stacking but don't use the railway visual language established in the Effect Path chapter.

#### Task 6.3: Update SUMMARY.md — PARTIALLY COMPLETE
**File:** `hkj-book/src/SUMMARY.md`
**What:** Add new pages created by this plan to the book's table of contents:
- ~~Quickstart and Cheat Sheet at the top (Phase 0)~~ ✓ Done
- Stack Archetypes under Advanced Topics (transformers)
- Production Readiness under Effect Path API
- Migration Cookbook and Compiler Errors under Effect Path or a new Troubleshooting section
- Any new recipe pages under Integration Guides (Spring)

---

### Phase 7: Migration Cookbook and Compiler Error Guide (New)

#### Task 7.1: Create Migration Cookbook
**File:** New `hkj-book/src/effect/migration_cookbook.md`
**What:** A pattern-by-pattern translation guide from imperative Java to HKJ. Each recipe is a two-column before/after with a one-sentence explanation:

**Recipe: try/catch → TryPath**
```java
// Before                              // After
try {                                  Path.tryOf(() -> riskyOperation())
    Result r = riskyOperation();           .map(this::transform)
    return transform(r);                   .recover(ex -> fallback())
} catch (Exception e) {                    .run();
    return fallback();
}
```

**Recipe: Optional chains → MaybePath**
```java
// Before                              // After
return findUser(id)                    Path.maybe(findUser(id))
    .flatMap(u -> findAddress(u))          .via(u -> Path.maybe(findAddress(u)))
    .map(Address::city)                    .map(Address::city)
    .orElse("Unknown");                    .run().orElse("Unknown");
```

**Recipe: CompletableFuture nesting → EitherPath**
**Recipe: Nested if/null checks → MaybePath**
**Recipe: Accumulating validation errors → ValidationPath**
**Recipe: Deeply nested record updates → FocusPath**

**Why:** Developers don't think "I need a MaybePath." They think "I have this ugly Optional chain." Meeting them at their current code pattern is the fastest path to adoption.

#### Task 7.2: Create Common Compiler Errors Guide
**File:** New `hkj-book/src/effect/compiler_errors.md` or `tutorials/troubleshooting.md` (enhance existing)
**What:** Document the 5 most common compiler errors when using the Effect Path API, with the actual error message, what it means, and how to fix it:

1. **"Incompatible types: EitherPath<X, Y> cannot be converted to MaybePath<Y>"**
   - You're mixing path types in a chain. Use `.toEitherPath()` or `.toMaybePath()` to convert.

2. **"Cannot infer type arguments for Path.right(...)"**
   - Java can't figure out the error type. Add explicit type: `Path.<AppError, User>right(user)`.

3. **"Method via in type X is not applicable for the arguments"**
   - The function passed to `via` returns the wrong path type. Check that it returns the same path kind.

4. **"No suitable method found for map(...)"**
   - Lambda type inference failed. Add explicit parameter types to the lambda.

5. **"Type argument X is not within bounds of type-variable E"**
   - Error type mismatch in a chain. All steps must use the same error type, or use `mapError` to convert.

Each entry should include the full compiler message (as developers would see it), a minimal code snippet that triggers it, and the fix.

**Why:** Nothing kills adoption faster than a cryptic compiler error with no Google results. This page becomes the first search result.

---

## Implementation Order

The tasks are ordered by impact (highest value first):

1. **Phase 0: Quickstart & Onboarding** ✓ COMPLETE
2. **Phase 2: Stack Archetypes** - Highest bang for buck; gives developers named patterns
3. **Phase 5: Production Readiness** - Builds trust with senior engineers (the decision makers)
4. **Phase 7: Migration Cookbook** - Meets developers at their existing code
5. **Phase 1: ROP Enhancement** - PARTIALLY COMPLETE (Task 1.1 done; Task 1.2 remaining)
6. **Phase 3: Capstone Example** - The "aha moment" that sells the library
7. **Phase 4: Spring Recipes** - Meets developers where they are
8. **Phase 6: Minor Enhancements** - Polish and consistency (Task 6.3 partially done for Phase 0 items)

Each phase is independent and can be worked in parallel or reordered based on priority.

---

## Bonus Work Completed

The following enhancements were completed during Phase 0 as additional improvements:

- **Green/red colour coding for railway diagrams** — Applied HTML `<span>` colour styling (green #4CAF50 for success, red #F44336 for failure) to diagrams in:
  - `effect/effect_path_overview.md` — Main railway diagram and operator mapping table
  - `transformers/ch_intro.md` — Before/after stacking concept (red=problem, green=solution)
  - `transformers/eithert_transformer.md` — Left(error) red, Right(value) green
  - `transformers/maybet_transformer.md` — Nothing red, Just(value) green
  - `transformers/optionalt_transformer.md` — empty() red, of(value) green
- **Operator-to-ROP mapping table** — Added to `effect_path_overview.md` (originally drafted for cheat sheet, moved to overview where the railway metaphor is introduced with visual context)

---

## What NOT to Change

The report's analysis is thorough, but several of its recommendations are already implemented. To avoid unnecessary churn:

- **Do NOT restructure SUMMARY.md ordering** - It's already Path-First
- **Do NOT rename the transformers chapter** - "Advanced Topics" is appropriate; the report's suggestion to rename to "Railway Oriented Java" would conflict with the Effect Path chapter which already owns that metaphor
- **Do NOT remove or downplay HKT documentation** - It's correctly placed at the bottom under "Foundations" for advanced users
- **Do NOT add the comparative analysis table (HKJ vs Vavr vs Cats)** to the book - This belongs in the README or marketing materials, not the learning path
