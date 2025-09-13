# Best Practices for Standardized Error Handling in Higher-Kinded-J

## Overview

This document outlines best practices for using the standardized error handling utilities provided by `ErrorHandling.java` to ensure consistency, maintainability, and robustness across the Higher-Kinded-J library.

## Core Principles

### 1. Fail-Fast with Clear Messages
Always validate inputs immediately and provide descriptive error messages that help developers understand what went wrong and how to fix it.

### 2. Use Standardized Templates
Prefer standardized error message templates over custom messages to ensure consistency across the library.

### 3. Centralize Common Patterns
Use the utility methods in `ErrorHandling` rather than implementing validation logic inline.

## Common Patterns and Their Standardized Equivalents

### Pattern 1: Null Parameter Validation

**❌ Don't do this:**
```java
public <A, B> Kind<F, B> map(Function<A, B> f, Kind<F, A> fa) {
    Objects.requireNonNull(f, "Function f cannot be null for map");
    Objects.requireNonNull(fa, "Kind fa cannot be null for map");
    // ... rest of implementation
}
```

**✅ Do this instead:**
```java
public <A, B> Kind<F, B> map(Function<A, B> f, Kind<F, A> fa) {
    requireNonNullFunction(f, "function f");
    requireNonNullKind(fa, "Kind fa");
    // ... rest of implementation
}
```

### Pattern 2: Kind Narrowing

**❌ Don't do this:**
```java
@Override
public <A> ConcreteType<A> narrow(Kind<Witness, A> kind) {
    if (kind == null) {
        throw new KindUnwrapException("Cannot narrow null Kind for ConcreteType");
    }
    if (!(kind instanceof ConcreteType)) {
        throw new KindUnwrapException("Kind instance is not a ConcreteType: " + kind.getClass().getName());
    }
    return (ConcreteType<A>) kind;
}
```

**✅ Do this instead:**
```java
@Override
public <A> ConcreteType<A> narrow(Kind<Witness, A> kind) {
    return narrowKindWithTypeCheck(kind, ConcreteType.class, "ConcreteType");
}
```

### Pattern 3: Complex Narrowing with Switch

**❌ Don't do this:**
```java
private <A> ConcreteType<A> extractFromKind(Kind<Witness, A> kind) {
    return switch (kind) {
        case ConcreteTypeHolder<A> holder -> holder.value();
        default -> throw new ClassCastException("Unexpected Kind type: " + kind.getClass());
    };
}
```

**✅ Do this instead:**
```java
// Simple type check and cast
return narrowKindWithTypeCheck(kind, ConcreteType.class, TYPE_NAME);

// Complex extraction with switch/pattern matching
return narrowKind(kind, TYPE_NAME, this::extractFromKind);

private <A> ConcreteType<A> extractFromKind(Kind<Witness, A> kind) {
    return switch (kind) {
        case ConcreteTypeHolder<A> holder -> holder.value();
        default -> throw new ClassCastException(); // Caught and wrapped
    };
}
```

### Pattern 4: Holder Record Validation

**❌ Don't do this:**
```java
record ConcreteTypeHolder<A>(ConcreteType<A> value) implements ConcreteTypeKind<A> {
    ConcreteTypeHolder {
        Objects.requireNonNull(value, "ConcreteType value cannot be null in holder");
    }
}
```

**✅ Do this instead:**
```java
record ConcreteTypeHolder<A>(ConcreteType<A> value) implements ConcreteTypeKind<A> {
    ConcreteTypeHolder {
        requireNonNullForHolder(value, "ConcreteType");
    }
}
```

### Pattern 5: Widen Operation Validation

**❌ Don't do this:**
```java
@Override
public <A> Kind<Witness, A> widen(ConcreteType<A> value) {
    Objects.requireNonNull(value, "Input ConcreteType cannot be null for widen");
    return new ConcreteTypeHolder<>(value);
}
```

**✅ Do this instead:**
```java
@Override
public <A> Kind<Witness, A> widen(ConcreteType<A> value) {
    requireNonNullForWiden(value, TYPE_NAME);
    return new ConcreteTypeHolder<>(value);
}
```

## Advanced Patterns

### Multiple Type Matching

For complex narrowing scenarios where multiple concrete types might be valid:

```java
@Override
public <A> ConcreteType<A> narrow(Kind<Witness, A> kind) {
    return narrowKindWithMatchers(kind, "ConcreteType",
        TypeMatchers.forClassWithCast(ConcreteTypeV1.class),
        TypeMatchers.forClassWithCast(ConcreteTypeV2.class),
        TypeMatchers.forClass(LegacyType.class, this::convertFromLegacy)
    );
}
```

### Batch Validation

For methods with multiple validation requirements:

```java
public <A, B, C> Result<A, B, C> complexOperation(A a, B b, C c) {
    validateAll(
        Validation.requireNonNull(a, "Parameter a cannot be null"),
        Validation.requireNonNull(b, "Parameter b cannot be null"),
        Validation.require(c != null && isValid(c), "Parameter c must be non-null and valid")
    );
    // ... rest of implementation
}
```

### Exception Wrapping with Context

When catching exceptions and providing additional context:

```java
private <A> ConcreteType<A> performComplexOperation(Kind<Witness, A> kind) {
    try {
        return doComplexWork(kind);
    } catch (Exception e) {
        throw wrapAsKindUnwrapException(e, "Failed to perform complex operation on Kind");
    }
}
```

## Error Message Guidelines

### Be Specific and Actionable

**❌ Vague:**
```java
throw new IllegalArgumentException("Invalid input");
```

**✅ Specific:**
```java
requireNonNullFunction(mapper, "mapper function for flatMap");
```

### Use Consistent Terminology

- "Kind" not "kind" or "wrapper"
- "function" not "fn" or "func"
- "parameter" not "param" or "arg"

### Include Context When Helpful

**❌ No context:**
```java
requireNonNullKind(ma);
```

**✅ With context:**
```java
requireNonNullKind(ma, "source Kind for flatMap");
```

## Performance Considerations

### Use Lazy Message Evaluation for Expensive Messages

**❌ Eager evaluation:**
```java
throw new KindUnwrapException("Complex error: " + expensiveDebugInfo());
```

**✅ Lazy evaluation:**
```java
throwKindUnwrapException(lazyMessage("Complex error: %s", expensiveDebugInfo()));
```

### Avoid Repeated Validation

**❌ Redundant validation:**
```java
public void method1(Kind<F, A> kind) {
    requireNonNullKind(kind);
    method2(kind);
}

public void method2(Kind<F, A> kind) {
    requireNonNullKind(kind); // Redundant if called from method1
    // ... implementation
}
```

**✅ Single point of validation:**
```java
public void method1(Kind<F, A> kind) {
    requireNonNullKind(kind);
    method2Internal(kind); // Internal method assumes valid input
}

private void method2Internal(Kind<F, A> kind) {
    // No validation needed - input guaranteed valid
    // ... implementation
}
```

## Testing Error Handling

### Test Null Parameters
```java
@Test
void testNullFunction() {
    assertThrows(NullPointerException.class, 
        () -> monad.map(null, validKind));
}

@Test
void testNullKind() {
    assertThrows(NullPointerException.class, 
        () -> monad.map(validFunction, null));
}
```

### Test Invalid Kind Types
```java
@Test
void testInvalidKindType() {
    Kind<WrongWitness, String> wrongKind = // ... create invalid kind
    assertThrows(KindUnwrapException.class,
        () -> helper.narrow(wrongKind));
}
```

### Verify Error Messages
```java
@Test
void testErrorMessage() {
    var exception = assertThrows(KindUnwrapException.class,
        () -> helper.narrow(null));
    assertTrue(exception.getMessage().contains("Cannot narrow null Kind"));
}
```

## Migration Strategy

### Phase 1: High-Impact Classes
1. Update `KindHelper` and similar implementations
2. Update `MonadError` and `Monad` implementations with inconsistent validation

### Phase 2: Consistency Pass
3. Ensure all KindHelper classes use standardized patterns
4. Update error message formatting to use templates

### Phase 3: Enhancement
5. Add domain-specific validation utilities as needed
6. Consider performance optimizations for hot paths
7. Add comprehensive documentation and examples

## Implementation Checklist

### For KindHelper Classes
- [ ] Use `requireNonNullForWiden` in `widen()` methods
- [ ] Use `narrowKind` or `narrowKindWithTypeCheck` in `narrow()` methods
- [ ] Use `requireNonNullForHolder` in record constructors
- [ ] Replace custom error messages with standardized templates
- [ ] Remove manual null checking in favor of utility methods

### For Monad/Functor/Applicative Classes
- [ ] Use `requireNonNullFunction` for function parameters
- [ ] Use `requireNonNullKind` for Kind parameters
- [ ] Replace `Objects.requireNonNull` with appropriate utility methods
- [ ] Ensure consistent parameter naming in error messages

### For Error Messages
- [ ] Use standardized message templates from `ErrorHandling`
- [ ] Ensure consistent terminology throughout
- [ ] Include helpful context information
- [ ] Use lazy evaluation for expensive debug information

## Common Anti-Patterns to Avoid

### 1. Inconsistent Null Checking
**❌ Mixing validation styles:**
```java
public void method(Function<A, B> f, Kind<F, A> fa, String name) {
    Objects.requireNonNull(f, "Function cannot be null");
    requireNonNullKind(fa);
    if (name == null) throw new IllegalArgumentException("Name required");
}
```

**✅ Consistent validation:**
```java
public void method(Function<A, B> f, Kind<F, A> fa, String name) {
    requireNonNullFunction(f, "function f");
    requireNonNullKind(fa, "Kind fa");
    Objects.requireNonNull(name, "name parameter");
}
```

### 2. Reimplementing Existing Utilities
**❌ Manual narrowing:**
```java
if (kind == null) throw new KindUnwrapException("Null kind");
if (!(kind instanceof MyType)) throw new KindUnwrapException("Wrong type");
return (MyType) kind;
```

**✅ Using utilities:**
```java
return narrowKindWithTypeCheck(kind, MyType.class, "MyType");
```

### 3. Inconsistent Error Message Format
**❌ Mixed formats:**
```java
throw new KindUnwrapException("kind is null");
throw new KindUnwrapException("Kind instance is not valid");
throw new KindUnwrapException("Cannot process null Kind for MyType");
```

**✅ Consistent format:**
```java
throw new KindUnwrapException(String.format(NULL_KIND_TEMPLATE, "MyType"));
```


## Standard Validation Pattern
Apply this pattern to ALL Monad implementations:
```java
// Standard flatMap validation
@Override
public <A, B> Kind<F, B> flatMap(
Function<? super A, ? extends Kind<F, B>> f,
Kind<F, A> ma) {

    requireNonNullFunction(f, "function f for flatMap");
    requireNonNullKind(ma, "source Kind for flatMap");
    // ... implementation
}

// Standard ap validation  
@Override
public <A, B> Kind<F, B> ap(
Kind<F, ? extends Function<A, B>> ff,
Kind<F, A> fa) {

    requireNonNullKind(ff, "function Kind for ap");
    requireNonNullKind(fa, "argument Kind for ap");
    // ... implementation
}

// Standard map validation
@Override
public <A, B> Kind<F, B> map(
Function<? super A, ? extends B> f,
Kind<F, A> fa) {

    requireNonNullFunction(f, "function f for map");
    requireNonNullKind(fa, "source Kind for map");
    // ... implementation
}
```

## KindHelper Pattern
All KindHelper implementations now follow:
```java
@Override
public <A> Kind<Witness, A> widen(ConcreteType<A> value) {
    requireNonNullForWiden(value, TYPE_NAME);
    return new ConcreteTypeHolder<>(value);
}

@Override
public <A> ConcreteType<A> narrow(Kind<Witness, A> kind) {
    return narrowKind(kind, TYPE_NAME, this::extractFromKind);
    // OR for simpler cases:
    // return narrowKindWithTypeCheck(kind, ConcreteType.class, TYPE_NAME);
}
```

## Documentation Standards

### Method Documentation Template
```java
/**
 * Brief description of what the method does.
 *
 * <p>More detailed explanation if needed, including any important behavioral notes.
 *
 * @param <T> Generic type parameter description
 * @param paramName Parameter description, including constraints
 * @return Description of return value and any guarantees
 * @throws SpecificException Description of when this exception is thrown
 * @see RelatedClass Related classes or methods
 */
```

### Error Handling Documentation
Always document the validation behavior:

```java
/**
 * Maps a function over the value in the Kind context.
 *
 * @param f The transformation function. Must not be null.
 * @param fa The Kind to transform. Must not be null.
 * @return A new Kind with the function applied
 * @throws NullPointerException if f or fa is null
 * @throws KindUnwrapException if fa cannot be unwrapped
 */
```

## Conclusion

Standardized error handling provides:
- **Consistency**: Predictable error messages and behavior
- **Maintainability**: Centralized logic reduces code duplication
- **Developer Experience**: Clear, actionable error messages
- **Robustness**: Comprehensive validation reduces runtime failures
- **Performance**: Optimized validation patterns and lazy evaluation

