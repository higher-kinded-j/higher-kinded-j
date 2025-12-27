# Embedded Kind System Implementation Plan

## Overview

This plan details the implementation of an embedded kind system for higher-kinded-j 2.0. The change introduces compile-time arity checking for witness types, catching errors earlier and making the type system self-documenting.

## Breaking Change Summary

| Component | Change |
|-----------|--------|
| `Kind<F, A>` | Add bound: `Kind<F extends WitnessArity<?>, A>` |
| `Kind2<F, A, B>` | Add bound: `Kind2<F extends WitnessArity<TypeArity.Binary>, A, B>` |
| Type classes | Add arity bounds (e.g., `Functor<F extends WitnessArity<TypeArity.Unary>>`) |
| All witnesses | Must implement `WitnessArity<TypeArity.X>` |

## Scope Analysis

| Category | Count | Files |
|----------|-------|-------|
| Type class interfaces | 11 | hkj-api |
| Witness classes | 33 | hkj-core |
| Type class implementations | 72 | hkj-core |
| Test files | 335 | All modules |
| Documentation files | 133 | hkj-book |
| Example files | 273 | hkj-examples |
| Processor files | 18 | hkj-processor |
| Plugin files | 11 | hkj-processor-plugins |
| Spring module files | 57 | hkj-spring |

---

## Phase 1: Infrastructure (hkj-api)

### 1.1 Create TypeArity Interface

**File:** `hkj-api/src/main/java/org/higherkindedj/hkt/TypeArity.java`

```java
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * Represents the arity of a type constructor witness in the HKT encoding.
 *
 * <p>In type theory terms, this encodes the "kind" of a type:
 * <ul>
 *   <li>{@link Unary} - A type constructor taking one parameter (kind: * -> *)
 *   <li>{@link Binary} - A type constructor taking two parameters (kind: * -> * -> *)
 * </ul>
 *
 * <p>This uses familiar Java terminology:
 * <ul>
 *   <li>"Arity" - as in function arity, how many arguments something takes
 *   <li>"Unary/Binary" - as in unary/binary operators
 * </ul>
 *
 * @see WitnessArity
 * @see Kind
 * @see Kind2
 */
@NullMarked
public sealed interface TypeArity permits TypeArity.Unary, TypeArity.Binary {

    /**
     * Represents a type constructor that takes exactly one type parameter.
     * Examples: List<_>, Optional<_>, Maybe<_>, IO<_>
     * Kind notation: * -> *
     */
    final class Unary implements TypeArity {
        private Unary() {}
    }

    /**
     * Represents a type constructor that takes exactly two type parameters.
     * Examples: Either<_, _>, Map<_, _>, Function<_, _>
     * Kind notation: * -> * -> *
     */
    final class Binary implements TypeArity {
        private Binary() {}
    }
}
```

### 1.2 Create WitnessArity Interface

**File:** `hkj-api/src/main/java/org/higherkindedj/hkt/WitnessArity.java`

```java
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * Marker interface declaring a witness type's arity in the HKT encoding.
 *
 * <p>Witnesses implementing this interface declare what "shape" of type
 * constructor they represent. This enables compile-time verification
 * that witnesses are used correctly with appropriate type classes.
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * // Unary witness (for Functor, Monad, etc.)
 * public interface MaybeKind<A> extends Kind<MaybeKind.Witness, A> {
 *     final class Witness implements WitnessArity<TypeArity.Unary> {
 *         private Witness() {}
 *     }
 * }
 *
 * // Parameterized unary witness (partial application)
 * public interface EitherKind<L, R> extends Kind<EitherKind.Witness<L>, R> {
 *     final class Witness<TYPE_L> implements WitnessArity<TypeArity.Unary> {
 *         private Witness() {}
 *     }
 * }
 *
 * // Binary witness (for Bifunctor, Profunctor)
 * public interface EitherKind2<L, R> extends Kind2<EitherKind2.Witness, L, R> {
 *     final class Witness implements WitnessArity<TypeArity.Binary> {
 *         private Witness() {}
 *     }
 * }
 * }</pre>
 *
 * @param <A> The type arity of this witness (Unary or Binary)
 * @see TypeArity
 * @see Kind
 * @see Kind2
 */
@NullMarked
public interface WitnessArity<A extends TypeArity> {}
```

---

## Phase 2: Update Core Interfaces (hkj-api)

### 2.1 Update Kind Interface

**File:** `hkj-api/src/main/java/org/higherkindedj/hkt/Kind.java`

```java
// Change from:
public interface Kind<F, A> {}

// To:
public interface Kind<F extends WitnessArity<?>, A> {}
```

### 2.2 Update Kind2 Interface

**File:** `hkj-api/src/main/java/org/higherkindedj/hkt/Kind2.java`

```java
// Change from:
public interface Kind2<F, A, B> {}

// To:
public interface Kind2<F extends WitnessArity<TypeArity.Binary>, A, B> {}
```

---

## Phase 3: Update Type Class Interfaces (hkj-api)

### 3.1 Unary Type Classes

| Interface | New Signature |
|-----------|---------------|
| `Functor` | `Functor<F extends WitnessArity<TypeArity.Unary>>` |
| `Applicative` | `Applicative<F extends WitnessArity<TypeArity.Unary>>` |
| `Monad` | `Monad<M extends WitnessArity<TypeArity.Unary>>` |
| `Traverse` | `Traverse<T extends WitnessArity<TypeArity.Unary>>` |
| `Foldable` | `Foldable<F extends WitnessArity<TypeArity.Unary>>` |
| `Alternative` | `Alternative<F extends WitnessArity<TypeArity.Unary>>` |
| `MonadZero` | `MonadZero<M extends WitnessArity<TypeArity.Unary>>` |
| `Selective` | `Selective<S extends WitnessArity<TypeArity.Unary>>` |

### 3.2 MonadError (Special Case)

```java
// MonadError has error type parameter
public interface MonadError<M extends WitnessArity<TypeArity.Unary>, E>
    extends Monad<M> { ... }
```

### 3.3 Binary Type Classes

| Interface | New Signature |
|-----------|---------------|
| `Bifunctor` | `Bifunctor<F extends WitnessArity<TypeArity.Binary>>` |
| `Profunctor` | `Profunctor<P extends WitnessArity<TypeArity.Binary>>` |

### 3.4 Natural Transformation

```java
// Natural works across different witnesses
public interface Natural<F extends WitnessArity<?>, G extends WitnessArity<?>> {
    <A> Kind<G, A> apply(Kind<F, A> fa);
}
```

---

## Phase 4: Annotate All Witnesses (hkj-core)

### 4.1 Simple Unary Witnesses (14)

| Type | File | Change |
|------|------|--------|
| Maybe | `maybe/MaybeKind.java` | `Witness implements WitnessArity<Unary>` |
| Optional | `optional/OptionalKind.java` | `Witness implements WitnessArity<Unary>` |
| List | `list/ListKind.java` | `Witness implements WitnessArity<Unary>` |
| Stream | `stream/StreamKind.java` | `Witness implements WitnessArity<Unary>` |
| Id | `id/IdKind.java` | `Witness implements WitnessArity<Unary>` |
| IO | `io/IOKind.java` | `Witness implements WitnessArity<Unary>` |
| Lazy | `lazy/LazyKind.java` | `Witness implements WitnessArity<Unary>` |
| Try | `exception/TryKind.java` | `Witness implements WitnessArity<Unary>` |
| Trampoline | `trampoline/TrampolineKind.java` | `Witness implements WitnessArity<Unary>` |
| CompletableFuture | `future/CompletableFutureKind.java` | `Witness implements WitnessArity<Unary>` |

### 4.2 Parameterized Unary Witnesses (13)

| Type | File | Change |
|------|------|--------|
| Either | `either/EitherKind.java` | `Witness<L> implements WitnessArity<Unary>` |
| Reader | `reader/ReaderKind.java` | `Witness<R> implements WitnessArity<Unary>` |
| State | `state/StateKind.java` | `Witness<S> implements WitnessArity<Unary>` |
| Writer | `writer/WriterKind.java` | `Witness<W> implements WitnessArity<Unary>` |
| Const | `const_/ConstKind.java` | `Witness<C> implements WitnessArity<Unary>` |
| Validated | `validated/ValidatedKind.java` | `Witness<E> implements WitnessArity<Unary>` |
| Coyoneda | `coyoneda/CoyonedaKind.java` | `Witness<F> implements WitnessArity<Unary>` |
| Free | `free/FreeKind.java` | `Witness<F> implements WitnessArity<Unary>` |
| FreeAp | `free_ap/FreeApKind.java` | `Witness<F> implements WitnessArity<Unary>` |

### 4.3 Transformer Witnesses (5)

| Type | File | Change |
|------|------|--------|
| MaybeT | `maybe_t/MaybeTKind.java` | `Witness<F> implements WitnessArity<Unary>` |
| OptionalT | `optional_t/OptionalTKind.java` | `Witness<F> implements WitnessArity<Unary>` |
| EitherT | `either_t/EitherTKind.java` | `Witness<F, L> implements WitnessArity<Unary>` |
| ReaderT | `reader_t/ReaderTKind.java` | `Witness<F, R> implements WitnessArity<Unary>` |
| StateT | `state_t/StateTKind.java` | `Witness<F, S> implements WitnessArity<Unary>` |

### 4.4 Binary Witnesses (6)

| Type | File | Change |
|------|------|--------|
| Either2 | `either/EitherKind2.java` | `Witness implements WitnessArity<Binary>` |
| Tuple2 | `tuple/Tuple2Kind2.java` | `Witness implements WitnessArity<Binary>` |
| Const2 | `const_/ConstKind2.java` | `Witness implements WitnessArity<Binary>` |
| Function | `func/FunctionKind.java` | `Witness implements WitnessArity<Binary>` |
| Writer2 | `writer/WriterKind2.java` | `Witness implements WitnessArity<Binary>` |
| Validated2 | `validated/ValidatedKind2.java` | `Witness implements WitnessArity<Binary>` |

---

## Phase 5: Update Type Class Implementations (hkj-core)

### 5.1 Functor Implementations (17 files)

Once witnesses are annotated, implementations should compile. Verify each:

- [ ] `coyoneda/CoyonedaFunctor.java`
- [ ] `either/EitherFunctor.java`
- [ ] `free/FreeFunctor.java`
- [ ] `free_ap/FreeApFunctor.java`
- [ ] `future/CompletableFutureFunctor.java`
- [ ] `io/IOFunctor.java`
- [ ] `list/ListFunctor.java`
- [ ] `maybe/MaybeFunctor.java`
- [ ] `optional/OptionalFunctor.java`
- [ ] `reader/ReaderFunctor.java`
- [ ] `state/StateFunctor.java`
- [ ] `stream/StreamFunctor.java`
- [ ] `trampoline/TrampolineFunctor.java`
- [ ] `exception/TryFunctor.java`
- [ ] `writer/WriterFunctor.java`
- [ ] `const_/ConstFunctor.java`
- [ ] `validated/ValidatedFunctor.java`

### 5.2 Monad Implementations (22 files)

- [ ] All Monad implementations (each extends Applicative which extends Functor)
- [ ] Transformer monads (MaybeTMonad, EitherTMonad, etc.)

### 5.3 Bifunctor Implementations (5 files)

- [ ] `either/EitherBifunctor.java`
- [ ] `tuple/Tuple2Bifunctor.java`
- [ ] `const_/ConstBifunctor.java`
- [ ] `validated/ValidatedBifunctor.java`
- [ ] `writer/WriterBifunctor.java`

### 5.4 Profunctor Implementation (1 file)

- [ ] `func/FunctionProfunctor.java`

### 5.5 Other Implementations

- [ ] All Traverse implementations (8)
- [ ] All Selective implementations (8)
- [ ] All MonadError implementations (10)
- [ ] Alternative, MonadZero implementations

---

## Phase 6: Update Annotation Processors (hkj-processor)

### 6.1 Analysis Required

Review each processor for references to:
- `Kind<F, A>` types
- Type class interfaces (Functor, Traverse, etc.)
- Witness type generation

### 6.2 Files to Review

| File | Purpose | Likely Impact |
|------|---------|---------------|
| `LensProcessor.java` | Lens generation | Low - uses optics, not type classes |
| `PrismProcessor.java` | Prism generation | Low |
| `TraversalProcessor.java` | Traversal generation | Medium - may reference Traverse |
| `PathProcessor.java` | Effect Path API | Medium - uses Kind types |
| `PathSourceProcessor.java` | Path source gen | Medium |
| `KindFieldAnalyser.java` | Kind field analysis | High - directly works with Kind |
| `KindRegistry.java` | Kind type registry | High - may need updates |

### 6.3 Processor Tests

- [ ] Review all processor tests
- [ ] Ensure generated code compiles with new bounds

---

## Phase 7: Update Processor Plugins (hkj-processor-plugins)

### 7.1 Generator Plugins

| Plugin | Purpose | Impact |
|--------|---------|--------|
| `ArrayGenerator.java` | Array traversals | Medium |
| `ListGenerator.java` | List traversals | Medium |
| `OptionalGenerator.java` | Optional traversals | Medium |
| `MaybeGenerator.java` | Maybe traversals | Medium |
| `EitherGenerator.java` | Either traversals | Medium |
| `SetGenerator.java` | Set traversals | Medium |
| `MapValueGenerator.java` | Map traversals | Medium |
| `TryGenerator.java` | Try traversals | Medium |
| `ValidatedGenerator.java` | Validated traversals | Medium |
| `BaseTraversableGenerator.java` | Base class | High |

### 7.2 SPI Interface

- [ ] Check `TraversableGenerator` SPI for Kind references

---

## Phase 8: Update Spring Module (hkj-spring)

### 8.1 Autoconfiguration

- [ ] Review Spring Boot autoconfiguration classes
- [ ] Check Jackson serializers for Kind types
- [ ] Verify actuator endpoints

### 8.2 Integration Tests

- [ ] Run full Spring Boot integration test suite
- [ ] Verify example application compiles and runs

---

## Phase 9: Update Tests (335 files)

### 9.1 Core Tests (hkj-core ~150 files)

Priority order:
1. [ ] Type class interface tests
2. [ ] Witness type tests
3. [ ] Functor/Monad implementation tests
4. [ ] Validation tests
5. [ ] Integration tests

### 9.2 Architecture Tests

**File:** `hkj-core/src/test/java/.../WitnessTypeRules.java`

Update architecture rules to verify:
- All witnesses implement `WitnessArity`
- Unary witnesses use `WitnessArity<Unary>`
- Binary witnesses use `WitnessArity<Binary>`

### 9.3 Processor Tests (~30 files)

- [ ] Verify processor test fixtures compile with new bounds
- [ ] Update expected generated code

### 9.4 Example Tests (~35 files)

- [ ] Run all example tests
- [ ] Fix any compilation errors

---

## Phase 10: Update Documentation (hkj-book, 133 files)

### 10.1 Core Concept Updates

| File | Updates Needed |
|------|----------------|
| `hkts/core-concepts.md` | Explain TypeArity and WitnessArity |
| `hkts/basic-examples.md` | Update witness examples |
| `hkts/quick-reference.md` | Add arity reference |
| `glossary.md` | Add TypeArity, WitnessArity, Unary, Binary terms |

### 10.2 Type Class Documentation

| File | Updates Needed |
|------|----------------|
| `functional/functor.md` | Show bounded signature, explain Unary requirement |
| `functional/applicative.md` | Show bounded signature |
| `functional/monad.md` | Show bounded signature |
| `functional/bifunctor.md` | Show Binary requirement |
| `functional/profunctor.md` | Show Binary requirement |
| `functional/foldable_and_traverse.md` | Update signatures |
| `functional/alternative.md` | Update signature |
| `functional/monad_error.md` | Update signature |

### 10.3 Monad Documentation (21 files)

Each monad page needs:
- Updated witness example with `WitnessArity`
- Code examples reflecting new signatures

### 10.4 Transformer Documentation (6 files)

- Show parameterized witness with WitnessArity
- Explain transformer witnesses are still Unary

### 10.5 New Documentation

Create new page: `hkts/type-arity.md`
- Explain the kind system
- Migration guide
- FAQ

---

## Phase 11: Update Examples (hkj-examples, 273 files)

### 11.1 Basic Examples (~43 files)

- [ ] Update all monad examples
- [ ] Update bifunctor examples
- [ ] Update profunctor examples

### 11.2 Tutorial Solutions (~299 test files)

- [ ] Verify all tutorial solutions compile
- [ ] Update expected outputs if needed

### 11.3 Complex Examples

| Example Set | Files | Notes |
|-------------|-------|-------|
| Order workflow | ~120 | Heavy use of monads |
| Effect API | ~30 | Uses Kind types |
| Free monad | ~15 | Free/FreeAp witnesses |
| Optics | ~25 | May reference Kind |
| Draughts game | ~20 | State/IO monads |

---

## Phase 12: Create Migration Guide

### 12.1 Guide Contents

1. **What Changed**
   - Kind now bounded
   - Type classes now bounded
   - Witnesses must implement WitnessArity

2. **Migration Steps**
   - Add `implements WitnessArity<TypeArity.Unary>` to unary witnesses
   - Add `implements WitnessArity<TypeArity.Binary>` to binary witnesses
   - Recompile

3. **Before/After Examples**

4. **Common Errors and Fixes**

5. **FAQ**

### 12.2 Tooling

Consider providing:
- [ ] OpenRewrite recipe for automated migration
- [ ] IntelliJ inspection for finding unbounded witnesses
- [ ] Compiler plugin for helpful error messages

---

## Phase 13: OpenRewrite Automation

### 13.1 Overview

OpenRewrite recipes can automate the majority of migration work for both internal code and downstream users. Create a recipe JAR that users can apply to their projects.

**Module:** `hkj-openrewrite` (new module)

### 13.2 Recipe: Add WitnessArity to Unary Witnesses

Detects witness classes inside `*Kind` interfaces that extend `Kind<X, Y>` and adds `implements WitnessArity<TypeArity.Unary>`.

```java
package org.higherkindedj.openrewrite;

import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

public class AddUnaryWitnessArity extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add WitnessArity<TypeArity.Unary> to unary witness classes";
    }

    @Override
    public String getDescription() {
        return "Adds `implements WitnessArity<TypeArity.Unary>` to witness classes " +
               "nested in interfaces that extend Kind<F, A>.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(
                    J.ClassDeclaration classDecl, ExecutionContext ctx) {

                // Check if this is a Witness class
                if (!"Witness".equals(classDecl.getSimpleName())) {
                    return classDecl;
                }

                // Check if parent is a Kind interface (not Kind2)
                J.ClassDeclaration parent = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (parent == null || !extendsKind(parent)) {
                    return classDecl;
                }

                // Skip if already implements WitnessArity
                if (alreadyImplementsWitnessArity(classDecl)) {
                    return classDecl;
                }

                // Add the implements clause
                return addWitnessArityImplements(classDecl, "Unary");
            }
        };
    }
}
```

### 13.3 Recipe: Add WitnessArity to Binary Witnesses

Similar recipe for `Kind2` witnesses:

```java
public class AddBinaryWitnessArity extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add WitnessArity<TypeArity.Binary> to binary witness classes";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(
                    J.ClassDeclaration classDecl, ExecutionContext ctx) {

                if (!"Witness".equals(classDecl.getSimpleName())) {
                    return classDecl;
                }

                J.ClassDeclaration parent = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (parent == null || !extendsKind2(parent)) {
                    return classDecl;
                }

                if (alreadyImplementsWitnessArity(classDecl)) {
                    return classDecl;
                }

                return addWitnessArityImplements(classDecl, "Binary");
            }
        };
    }
}
```

### 13.4 Recipe: Add Required Imports

Automatically adds imports when WitnessArity is added:

```java
public class AddWitnessArityImports extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add TypeArity and WitnessArity imports";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(
                    J.CompilationUnit cu, ExecutionContext ctx) {

                // Check if file contains WitnessArity reference
                if (!containsWitnessArity(cu)) {
                    return cu;
                }

                // Add imports if missing
                cu = addImportIfMissing(cu, "org.higherkindedj.hkt.TypeArity");
                cu = addImportIfMissing(cu, "org.higherkindedj.hkt.WitnessArity");

                return cu;
            }
        };
    }
}
```

### 13.5 Composite Recipe for Full Migration

```yaml
# rewrite.yml
---
type: specs.openrewrite.org/v1beta/recipe
name: org.higherkindedj.openrewrite.MigrateToKindSystem2
displayName: Migrate to Higher-Kinded-J 2.0 Kind System
description: >
  Adds WitnessArity bounds to all witness classes for compatibility
  with Higher-Kinded-J 2.0's embedded kind system.
recipeList:
  - org.higherkindedj.openrewrite.AddUnaryWitnessArity
  - org.higherkindedj.openrewrite.AddBinaryWitnessArity
  - org.higherkindedj.openrewrite.AddWitnessArityImports
  - org.openrewrite.java.OrderImports
```

### 13.6 Detection Recipes (Diagnostics)

Recipe to find witnesses that need updating (dry-run mode):

```java
public class FindUnboundedWitnesses extends Recipe {

    @Override
    public String getDisplayName() {
        return "Find witness classes without WitnessArity";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(
                    J.ClassDeclaration classDecl, ExecutionContext ctx) {

                if (!"Witness".equals(classDecl.getSimpleName())) {
                    return classDecl;
                }

                if (!alreadyImplementsWitnessArity(classDecl)) {
                    // Report as a finding
                    ctx.insertMessage("unbounded-witness",
                        classDecl.getSimpleName() + " at " + getCursor().getPath());
                }

                return classDecl;
            }
        };
    }
}
```

### 13.7 Usage Instructions for Downstream Users

**Maven:**

```xml
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>5.37.0</version>
  <configuration>
    <activeRecipes>
      <recipe>org.higherkindedj.openrewrite.MigrateToKindSystem2</recipe>
    </activeRecipes>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>org.higherkindedj</groupId>
      <artifactId>hkj-openrewrite</artifactId>
      <version>2.0.0</version>
    </dependency>
  </dependencies>
</plugin>
```

Run with:
```bash
mvn rewrite:run
```

**Gradle:**

```kotlin
plugins {
    id("org.openrewrite.rewrite") version "6.16.0"
}

dependencies {
    rewrite("org.higherkindedj:hkj-openrewrite:2.0.0")
}

rewrite {
    activeRecipe("org.higherkindedj.openrewrite.MigrateToKindSystem2")
}
```

Run with:
```bash
./gradlew rewriteRun
```

### 13.8 Testing the Recipes

Create test fixtures:

```java
@Test
void addsUnaryWitnessArity() {
    rewriteRun(
        spec -> spec.recipe(new AddUnaryWitnessArity()),
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;

            public interface MyTypeKind<A> extends Kind<MyTypeKind.Witness, A> {
                final class Witness {
                    private Witness() {}
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public interface MyTypeKind<A> extends Kind<MyTypeKind.Witness, A> {
                final class Witness implements WitnessArity<TypeArity.Unary> {
                    private Witness() {}
                }
            }
            """
        )
    );
}
```

### 13.9 Recipe Coverage Matrix

| Pattern | Recipe | Automated |
|---------|--------|-----------|
| Simple unary witness | `AddUnaryWitnessArity` | ✅ |
| Parameterized unary witness `Witness<L>` | `AddUnaryWitnessArity` | ✅ |
| Binary witness (Kind2) | `AddBinaryWitnessArity` | ✅ |
| Add imports | `AddWitnessArityImports` | ✅ |
| Order imports | Built-in OpenRewrite | ✅ |
| Custom type class bounds | Manual | ❌ |
| Complex inheritance | Manual review | ❌ |

### 13.10 Implementation Priority

1. **High:** `AddUnaryWitnessArity` - covers 27 of 33 witnesses
2. **High:** `AddBinaryWitnessArity` - covers 6 of 33 witnesses
3. **High:** `AddWitnessArityImports` - required for all changes
4. **Medium:** `FindUnboundedWitnesses` - diagnostics for validation
5. **Low:** Custom type class migration - rare in user code

---

## Implementation Order

### Week 1: Foundation
1. Create TypeArity and WitnessArity (Phase 1)
2. Update Kind and Kind2 (Phase 2)
3. Update type class interfaces (Phase 3)

### Week 2: Core Implementation
4. Annotate all witnesses (Phase 4)
5. Fix type class implementations (Phase 5)
6. Run and fix core tests

### Week 3: Ecosystem
7. Update processors (Phase 6)
8. Update plugins (Phase 7)
9. Update Spring module (Phase 8)

### Week 4: Polish & Tooling
10. Fix all remaining tests (Phase 9)
11. Create hkj-openrewrite module (Phase 13)
12. Update documentation (Phase 10)
13. Update examples (Phase 11)
14. Write migration guide (Phase 12)

### OpenRewrite Development (can parallel with Week 2-3)
- Create hkj-openrewrite module structure
- Implement AddUnaryWitnessArity recipe
- Implement AddBinaryWitnessArity recipe
- Implement AddWitnessArityImports recipe
- Create composite MigrateToKindSystem2 recipe
- Write recipe tests
- Use recipes to migrate internal codebase (dogfooding)

---

## Validation Checklist

- [ ] All modules compile
- [ ] All tests pass
- [ ] Javadoc generates without errors
- [ ] Examples run correctly
- [ ] Spring Boot example starts
- [ ] Documentation builds
- [ ] Migration guide is complete
- [ ] OpenRewrite recipes tested on external sample project
- [ ] OpenRewrite recipes successfully migrate internal codebase

---

## Rollback Plan

If critical issues are discovered:
1. Revert to 1.x branch
2. Continue 1.x maintenance releases
3. Address issues in 2.0-RC2

---

## Version Strategy

| Version | Content |
|---------|---------|
| 1.x | Current, maintained for bug fixes |
| 2.0-RC1 | Full implementation for testing |
| 2.0-RC2 | Address feedback |
| 2.0.0 | Final release |
