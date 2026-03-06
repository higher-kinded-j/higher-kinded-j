# Detailed Analysis: Approach 3 + Approach 6 (Combined)

## Type-Specific Method Overloads + Compile-Time Annotation Checking

---

## Executive Summary

After thorough codebase analysis, a **critical finding** reshapes this entire discussion:

**All 18 Path implementations already return concrete types** (e.g., `MaybePath<B> via(...)` not `Chainable<B> via(...)`). The type safety gap is **not in the return types** — it's in the **parameter types**. The interface signatures accept `Chainable<B>` and `Combinable<B>` as parameters, allowing any Path type to be passed where only the same-kind Path is valid.

This means Approach 3 becomes a **parameter-tightening** exercise, and Approach 6's annotation processor becomes a **call-site validator**. The two approaches are naturally complementary and can be combined.

---

## Part 1: The Actual Type Safety Gap

### What compiles but shouldn't:

```java
// via() accepts Function<..., ? extends Chainable<B>> — any Chainable is accepted
MaybePath<String> result = Path.just(42)
    .via(n -> Path.right("hello"));  // EitherPath passed where MaybePath needed
                                      // COMPILES — fails at RUNTIME

// zipWith() accepts Combinable<B> — any Combinable is accepted
MaybePath<Integer> result = Path.just(1)
    .zipWith(Path.right(2), Integer::sum);  // EitherPath passed
                                             // COMPILES — fails at RUNTIME

// then() accepts Supplier<? extends Chainable<B>> — any Chainable is accepted
MaybePath<String> result = Path.just(42)
    .then(() -> Path.tryOf(() -> "hello"));  // TryPath passed
                                              // COMPILES — fails at RUNTIME
```

### What already works correctly:

```java
// Return types ARE concrete — these give proper type inference
MaybePath<String> result = Path.just(42)
    .via(n -> Path.just("hello"))  // ✅ Correctly inferred as MaybePath<String>
    .map(String::toUpperCase);      // ✅ Correctly inferred as MaybePath<String>
```

### Scope of runtime instanceof checks:

- **~50 instanceof checks** across 18 Path implementations
- Every `via()`, `then()`, and `zipWith()` method in every Path type has one
- Additionally: `recoverWith()`, `orElse()` in Recoverable implementations (~10 more)

---

## Part 2: Approach 3 — Type-Specific Method Overloads

### 2.1 What Would Change

**The interface signatures stay the same.** Each concrete Path type adds **overloaded methods** with tighter parameter types:

```java
// EXISTING (unchanged):
public <B> MaybePath<B> via(Function<? super A, ? extends Chainable<B>> mapper)

// NEW OVERLOAD (added):
public <B> MaybePath<B> via(Function<? super A, ? extends MaybePath<B>> mapper)
```

### 2.2 Critical Problem: Java Method Overload Resolution

**This approach has a fundamental flaw in Java's type system.**

When the compiler sees:

```java
Path.just(42).via(n -> Path.just("hello"));
```

It must resolve between:
1. `via(Function<? super A, ? extends Chainable<B>>)` — the interface method
2. `via(Function<? super A, ? extends MaybePath<B>>)` — the new overload

**The lambda `n -> Path.just("hello")` has target-type-dependent inference.** Java uses the target type to infer the lambda's return type. With two applicable methods, this creates **ambiguity**:

```
error: reference to via is ambiguous
  both method via(Function<? super Integer, ? extends Chainable<String>>) in Chainable
  and method via(Function<? super Integer, ? extends MaybePath<String>>) in MaybePath
  match
```

This is because `Function<A, MaybePath<B>>` is a subtype of `Function<A, Chainable<B>>`, making both methods applicable. Java's overload resolution with generics and lambdas has known edge cases here (JLS §15.12.2.5).

### 2.3 Workaround: Different Method Names

Instead of overloads, use **differently-named methods**:

```java
// Option A: "Typed" suffix
public <B> MaybePath<B> viaTyped(Function<? super A, ? extends MaybePath<B>> mapper)
public <B, C> MaybePath<C> zipWithTyped(MaybePath<B> other, BiFunction<...> combiner)

// Option B: Descriptive names
public <B> MaybePath<B> viaMaybe(Function<? super A, ? extends MaybePath<B>> mapper)
public <B, C> MaybePath<C> zipWithMaybe(MaybePath<B> other, BiFunction<...> combiner)
```

### 2.4 Trade-off Analysis for Approach 3

| Dimension | Assessment |
|-----------|------------|
| **Simplicity** | ❌ NEGATIVE — Doubles the method surface area per Path type |
| **Complexity** | ❌ NEGATIVE — 18 types × 3 methods = 54 new methods minimum |
| **Flexibility** | ⚠️ NEUTRAL — Doesn't restrict, just adds options |
| **Correctness** | ⚠️ PARTIAL — Helps if users choose the typed method; doesn't prevent using the untyped one |
| **Type Safety** | ⚠️ PARTIAL — Opt-in, not enforced |
| **API Surface** | ❌ NEGATIVE — Every Path type's Javadoc doubles in size |
| **Discoverability** | ❌ NEGATIVE — Users must know to use the typed variant |
| **Breaking Changes** | ✅ NONE — Purely additive |

### 2.5 Scope of Changes for Approach 3

| Area | Files | Changes |
|------|-------|---------|
| **Core interfaces** | 0 | None — interfaces unchanged |
| **Path implementations** | 18 | Add 3 overloaded methods each (54 methods total) |
| **Tests** | 18+ | New tests for each typed method |
| **ForPath/ForPathSteps** | 1 + generated | Could use typed methods internally but not required |
| **Examples** | 7+ | Update to demonstrate typed variants |
| **hkj-book** | 5+ files | capabilities.md, composition.md, path_types.md, tutorials |
| **Spring integration** | 0 | No changes needed |
| **Annotation processor** | 0 | No changes needed |

### 2.6 Verdict on Approach 3 Standalone

**Not recommended.** The overload ambiguity problem makes it awkward. The differently-named-method workaround works but creates a confusing dual API ("Do I use `via` or `viaMaybe`?"). The partial type safety it offers doesn't justify the API complexity.

---

## Part 3: Approach 6 — Status Quo + @CheckPathType Annotation Processor

### 3.1 How @CheckPathType Would Work

#### 3.1.1 The Fundamental Limitation

**Standard `javax.annotation.processing.Processor` cannot do this.**

Annotation processors operate on **declarations** (classes, methods, fields with annotations), not on **method call sites**. They process elements annotated with specific annotations and generate new source files or report errors on annotated elements.

What we need is call-site analysis: "at line 42 in UserService.java, `maybePath.via(...)` is called with a lambda that returns `EitherPath` — warn the user." This requires **analyzing method invocations within method bodies**, which annotation processors cannot do.

#### 3.1.2 What CAN Be Done With Standard Annotation Processing

**Option A: Annotate the interface methods, generate type-safe wrapper methods**

```java
// In Chainable.java:
@CheckPathType  // Marker annotation
<B> Chainable<B> via(Function<? super A, ? extends Chainable<B>> mapper);
```

The processor would generate **companion classes** with typed signatures:

```java
// Generated: MaybePathOps.java
public final class MaybePathOps {
    public static <A, B> MaybePath<B> via(
            MaybePath<A> self,
            Function<? super A, ? extends MaybePath<B>> mapper) {
        return self.via(mapper);
    }
}
```

**Problem:** Users must opt-in to use `MaybePathOps.via(path, fn)` instead of `path.via(fn)`. This is worse than Approach 3's named methods.

**Option B: Annotate user code methods to trigger checking**

```java
@CheckPathTypes  // "Check all Path operations in this method"
public MaybePath<User> findUser(int id) {
    return Path.just(id).via(n -> lookupUser(n));
}
```

**Problem:** Still can't inspect method body contents. The processor sees the method declaration but cannot analyze the AST of the method body.

#### 3.1.3 What WOULD Work: Error Prone or Checker Framework

**Error Prone** (Google's compile-time bug checker):

Error Prone operates as a **javac plugin** with full access to the AST. A custom `BugChecker` can:
1. Visit every method invocation node
2. Check if the receiver is a concrete Path type
3. Check if the argument returns a different Path type
4. Emit a warning/error

```java
// Hypothetical Error Prone check:
@BugPattern(
    name = "PathTypeMismatch",
    summary = "Path.via()/zipWith()/then() called with incompatible Path type",
    severity = WARNING)
public class PathTypeMismatchChecker extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher {

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // 1. Is this a call to via(), zipWith(), or then()?
        // 2. What's the receiver's concrete type? (e.g., MaybePath)
        // 3. Does the argument's return type match?
        // 4. If mismatch, emit warning
    }
}
```

**Pros:**
- Full AST access — can analyze any expression
- Runs during normal compilation — no extra build step
- Can auto-suggest fixes
- Google maintains it, widely adopted

**Cons:**
- Requires javac (not compatible with Eclipse's compiler)
- New dependency: `com.google.errorprone`
- Requires `--add-exports` JVM flags for javac plugin loading
- Not a standard annotation processor — different tooling

**Checker Framework:**

Similar capabilities but heavier-weight. More suited for type-system extensions (nullness, tainting) than single-method checks. Overkill for this use case.

#### 3.1.4 A Pragmatic Middle Ground: Javac Plugin (Lightweight)

Java's `com.sun.source.util.Plugin` interface provides AST access without the Error Prone dependency:

```java
public class PathTypeCheckPlugin implements Plugin {
    @Override
    public String getName() { return "PathTypeCheck"; }

    @Override
    public void init(JavacTask task, String... args) {
        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                    // Walk the AST, find via/zipWith/then calls
                    // Check receiver type vs argument return type
                    // Report warnings via Trees API
                }
            }
        });
    }
}
```

**Pros:**
- No external dependencies beyond the JDK
- Full AST access
- Runs during compilation
- Can be packaged as a standard jar

**Cons:**
- Uses `com.sun.source` internal APIs (but they're stable)
- Requires `-Xplugin:PathTypeCheck` compiler flag
- More complex to implement than Error Prone's `BugChecker`
- IDE support varies

### 3.2 Trade-off Analysis for Approach 6

| Dimension | Assessment |
|-----------|------------|
| **Simplicity** | ✅ POSITIVE — Zero changes to existing code |
| **Complexity** | ⚠️ MODERATE — New module for the checker, but isolated |
| **Flexibility** | ✅ POSITIVE — Opt-in, doesn't constrain the API |
| **Correctness** | ✅ POSITIVE — Catches mismatches at compile time when enabled |
| **Type Safety** | ⚠️ PARTIAL — Only when the plugin/checker is on the classpath |
| **API Surface** | ✅ POSITIVE — No changes |
| **Discoverability** | ⚠️ MODERATE — Users must configure the checker |
| **Breaking Changes** | ✅ NONE |

### 3.3 Scope of Changes for Approach 6

| Area | Files | Changes |
|------|-------|---------|
| **Core interfaces** | 0 | None |
| **Path implementations** | 0 | None |
| **New module** | 1 | `hkj-checker` — contains the javac plugin or Error Prone check |
| **Build config** | 1-2 | Add module to settings.gradle, configure compiler flags |
| **Tests** | 5-10 | Compile-time test cases (files that should warn, files that shouldn't) |
| **Examples** | 0 | No changes needed |
| **hkj-book** | 1-2 | New chapter on compile-time checking; update troubleshooting |
| **Spring integration** | 0 | No changes |

---

## Part 4: Combined Approach (3 + 6)

### 4.1 The Best of Both

The approaches are complementary because they address different audiences:

- **Approach 6 (checker):** Catches mistakes in existing code using `via()/zipWith()/then()` as they are today
- **Approach 3 (typed methods):** Gives power users who want explicit type constraints a way to express them

### 4.2 Recommended Combined Strategy

#### Phase 1: Compile-Time Checker (Approach 6)

Build a javac plugin (or Error Prone check) in a new `hkj-checker` module:

1. **Create `hkj-checker` module** with a `PathTypeMismatchChecker`
2. **Detection rules:**
   - When `X.via(fn)` is called and `X` is a concrete Path type (e.g., `MaybePath`), check that `fn`'s return type is compatible with `X`
   - When `X.zipWith(other, fn)` is called, check that `other` is the same Path type as `X`
   - When `X.then(supplier)` is called, check the supplier's return type
   - When `X.recoverWith(fn)` is called, check `fn`'s return type
3. **Severity:** WARNING by default (not error), so it's non-breaking
4. **Configuration:** Users opt-in by adding `hkj-checker` to their annotation processor path or compiler plugin path

**Zero impact on existing code. Zero breaking changes. Immediate value.**

#### Phase 2: Type-Safe zipWith Only (Targeted Approach 3)

Of the three methods (`via`, `zipWith`, `then`), only `zipWith` can benefit from a type-safe overload **without ambiguity issues**:

```java
// zipWith takes a Combinable<B> parameter (not a Function), so no lambda ambiguity
// EXISTING:
public <B, C> MaybePath<C> zipWith(Combinable<B> other, BiFunction<...> combiner)

// NEW: different parameter type, not ambiguous
// This would need a different name since Java erases the generic type
```

Actually, even `zipWith` has the same problem: `MaybePath<B>` is a subtype of `Combinable<B>`, so both methods would be applicable. **The overload approach fundamentally doesn't work in Java for subtype-related overloads.**

**Revised Phase 2:** Instead, consider adding **convenience methods** that already exist in some Path types:

```java
// Already exists for some Path types:
public <B, C, D> MaybePath<D> zipWith3(
    MaybePath<B> second, MaybePath<C> third, Function3<...> combiner)
public <B, C, D, E> MaybePath<E> zipWith4(
    MaybePath<B> second, MaybePath<C> third, MaybePath<D> fourth, Function4<...> combiner)
```

The `zipWith3`, `zipWith4` methods already take concrete types! The only gap is the binary `zipWith` which takes the interface type. This is an inherent design constraint of implementing `Combinable<A>`.

#### Phase 3: Better Error Messages (Approach 6 enhancement)

Improve the existing runtime error messages to be more actionable:

```java
// CURRENT:
throw new IllegalArgumentException("Cannot zipWith non-MaybePath: " + other.getClass());

// IMPROVED:
throw new IllegalArgumentException(
    "MaybePath.zipWith() requires another MaybePath, but got " + other.getClass().getSimpleName()
    + ". Use explicit conversion (e.g., .toMaybePath()) to convert between Path types."
    + " Enable the hkj-checker compiler plugin to catch this at compile time.");
```

---

## Part 5: Wider Consequences

### 5.1 Positive Consequences

| Consequence | Details |
|-------------|---------|
| **Compile-time safety** | The checker catches the most common mistake (mixing Path types) before runtime |
| **Zero API churn** | No interface changes, no method additions, no breaking changes |
| **Educational value** | The checker's warnings teach users about Path type discipline |
| **Gradual adoption** | Teams can add the checker when ready; existing code continues to work |
| **IDE integration** | Error Prone integrates with IntelliJ; javac plugins show warnings in build output |
| **Documentation opportunity** | A "Type Safety" chapter in hkj-book explaining the design and the checker |

### 5.2 Negative Consequences

| Consequence | Details |
|-------------|---------|
| **New module to maintain** | `hkj-checker` needs updates when new Path types are added |
| **Build complexity** | Users must configure compiler plugins (non-trivial for Gradle/Maven) |
| **False positives** | Polymorphic code that intentionally uses `Chainable<A>` would trigger warnings |
| **Internal API dependency** | Javac plugin uses `com.sun.source` APIs; Error Prone adds a dependency |
| **Incomplete coverage** | Only catches issues when the checker is enabled |

### 5.3 Impact on ForPath Comprehensions

ForPath internally uses concrete types throughout (e.g., `MaybePath.via()` with `MaybePath`-returning functions). The checker would **not** flag any ForPath code because it's already type-correct. No changes needed.

### 5.4 Impact on GenericPath

GenericPath is special — its witness type `F` is only known at runtime. The checker would need special handling:

```java
// This should NOT warn — GenericPath.via() legitimately accepts GenericPath returns
GenericPath<MaybeKind.Witness, String> gp = GenericPath.pure("hello", monad);
gp.via(s -> GenericPath.pure(s.length(), monad));  // OK

// This SHOULD warn — mixing GenericPath with MaybePath
gp.via(s -> Path.just(s.length()));  // ⚠️ Warning
```

The checker would treat GenericPath as: "result must also be GenericPath" (matching the runtime check).

### 5.5 Impact on Cross-Path Conversions

Explicit conversions (`toEitherPath()`, `toMaybePath()`) are unaffected — they return the target type directly, not through `via()`.

---

## Part 6: @CheckPathType Annotation Processor — Detailed Design

### 6.1 Recommended Implementation: Error Prone BugChecker

Error Prone is the most practical choice because:
- It has a well-documented API for writing custom checks
- It integrates with Gradle (`net.ltgt.errorprone` plugin)
- IntelliJ has built-in Error Prone support
- The `BugChecker` API handles all the AST walking boilerplate

### 6.2 Detection Algorithm

```
For each method invocation in the compilation unit:
  1. Is the method named "via", "zipWith", "then", "flatMap", "recoverWith", or "orElse"?
  2. Is the receiver type a concrete Path type? (not Chainable<A> or Combinable<A>)
     - If receiver is abstract (Chainable<A>), skip — polymorphic use is intentional
  3. Determine the "expected" Path type from the receiver
  4. Analyze the argument:
     - For via/flatMap/then: resolve the return type of the Function/Supplier
     - For zipWith: resolve the concrete type of the 'other' parameter
     - For recoverWith/orElse: resolve the return type of the recovery function
  5. If the resolved type is:
     - Same concrete Path type → OK, no warning
     - A different concrete Path type → WARNING
     - Chainable<B> (abstract) → OK, can't determine at compile time
     - Unknown/unresolvable → OK, skip (avoid false positives)
```

### 6.3 Handling Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Lambda return type is inferred | Use javac's type attribution to resolve inferred types |
| Method reference (e.g., `this::lookupUser`) | Resolve the method's return type |
| Variable passed as argument | Check the variable's declared type |
| Conditional expression in lambda | Check all branches; warn if any branch mismatches |
| Generic methods returning Chainable | Skip — can't determine concrete type |
| `var` type declarations | Use the inferred type |

### 6.4 Module Structure

```
hkj-checker/
├── build.gradle
├── src/main/java/
│   └── org/higherkindedj/checker/
│       ├── PathTypeMismatchChecker.java    // Main BugChecker
│       ├── PathTypeRegistry.java           // Known Path types and their relationships
│       └── package-info.java
├── src/main/resources/
│   └── META-INF/services/
│       └── com.google.errorprone.bugpatterns.BugChecker
└── src/test/java/
    └── org/higherkindedj/checker/
        ├── PathTypeMismatchCheckerTest.java
        ├── testdata/
        │   ├── MismatchVia.java             // Should trigger warning
        │   ├── MismatchZipWith.java          // Should trigger warning
        │   ├── CorrectUsage.java             // Should NOT trigger warning
        │   ├── PolymorphicUsage.java          // Should NOT trigger warning
        │   └── CrossPathConversion.java       // Should NOT trigger warning
        └── ...
```

### 6.5 User Configuration

**Gradle:**
```groovy
dependencies {
    annotationProcessor 'org.higherkindedj:hkj-checker:1.0'
    errorprone 'com.google.errorprone:error_prone_core:2.28.0'
}

tasks.withType(JavaCompile).configureEach {
    options.errorprone {
        check('PathTypeMismatch', CheckSeverity.WARN)
    }
}
```

**Maven:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.higherkindedj</groupId>
                <artifactId>hkj-checker</artifactId>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

---

## Part 7: Documentation Impact

### 7.1 hkj-book Changes

| File | Change |
|------|--------|
| `effect/capabilities.md` | Add "Type Safety" section explaining the design choice and the checker |
| `effect/composition.md` | Add note about common mistakes section referencing the checker |
| New: `effect/type_checking.md` | Dedicated chapter on compile-time checking setup and usage |
| `tutorials/troubleshooting.md` | Add checker setup instructions |
| `home.md` | Mention checker in feature list |

### 7.2 API Documentation

- Add `@implNote` to `Chainable.via()`, `Combinable.zipWith()`, `Chainable.then()` noting that implementations require same-type arguments
- This is purely documentary — no code changes

---

## Part 8: Recommendation

### Do This (Combined Approach):

1. **Phase 1 — Better error messages** (immediate, low effort)
   - Improve all ~50 runtime error messages to suggest conversions and mention the checker
   - 0 risk, high value

2. **Phase 2 — Error Prone checker** (medium effort, high value)
   - New `hkj-checker` module with `PathTypeMismatchChecker`
   - Catches the #1 developer mistake at compile time
   - Completely opt-in, zero impact on existing users

3. **Phase 3 — Documentation** (medium effort)
   - New hkj-book chapter on type safety
   - API documentation improvements

### Don't Do This:

- **Don't add method overloads** (Approach 3 pure) — Java's overload resolution makes this impractical
- **Don't add witness types to Chainable** — redundant with GenericPath, adds complexity everywhere
- **Don't use Checker Framework** — too heavy for this use case
- **Don't make the checker mandatory** — it should be opt-in to avoid build friction

### Why This Is The Right Answer:

The current design with runtime checks is **correct by construction** — it never produces wrong results, it just fails late. The checker moves that failure earlier (to compile time) without touching the API. This is the minimum-change, maximum-value approach.
