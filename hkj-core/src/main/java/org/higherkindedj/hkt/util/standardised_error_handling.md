# Standardized Validation Patterns for Higher-Kinded-J

## Overview

This document describes the standardized approach for error handling and validation across Higher-Kinded-J. The pattern uses context objects and specialized validators to provide consistent, type-safe validation with clear error messages.

## Core Philosophy

1. **Separation of Concerns**: Validation logic is separated from business logic
2. **Context-Aware Messaging**: Error messages include relevant context automatically
3. **Type Safety**: Validators are type-safe and prevent common mistakes
4. **Scalability**: Pattern scales easily to new typeclasses and operations
5. **Consistency**: All typeclass implementations follow the same patterns

## Architecture

### Validation Contexts

Context objects carry semantic information about what is being validated. They implement the `ValidationContext` interface and provide standardized error messages.

#### Available Contexts

```java
// For Kind operations (widen/narrow)
KindContext.narrow(CompletableFuture.class)
KindContext.widen(CompletableFuture.class)

// For function parameters in monad operations
FunctionContext.mapper("map")
FunctionContext.flatMapper("flatMap")
FunctionContext.applicative("traverse")

// For collection validations
CollectionContext.collection("parameterName")
CollectionContext.array("parameterName")

// For condition/range validations
ConditionContext.range("parameterName")
ConditionContext.custom("parameterName", "description")

// For domain-specific validations
DomainContext.transformer("transformerName")
DomainContext.witness("operation")
```

### Specialized Validators

Each validator handles a specific category of validation with appropriate error messaging:

- **`KindValidator`**: Kind operations (widen/narrow/typecheck)
- **`FunctionValidator`**: Function parameters in monad operations
- **`CollectionValidator`**: Collection and array validations
- **`ConditionValidator`**: Conditional logic and range checks
- **`DomainValidator`**: Domain-specific validations (transformers, witnesses)
- **`ExceptionValidator`**: Exception wrapping and context preservation
- **`CompositeValidator`**: Complex multi-step validations
- **`TextValidator`**: String and text-based validations
- **`NumericValidator`**: Numeric constraint validations

## Standard Implementation Patterns

### Pattern 1: KindHelper Implementation

All KindHelper classes follow this standardized structure:

```java
public enum TypeKindHelper implements TypeConverterOps {
  INSTANCE; // or descriptive name like FUTURE, OPTIONAL, etc.

  private static final Class<ConcreteType> TYPE = ConcreteType.class;

  // Internal holder record
  record TypeHolder<A>(ConcreteType<A> value) implements TypeKind<A> {
    TypeHolder {
      KindValidator.requireForWiden(value, TYPE);
    }
  }

  @Override
  public <A> Kind<TypeKind.Witness, A> widen(ConcreteType<A> value) {
    return new TypeHolder<>(value);
  }

  @Override
  public <A> ConcreteType<A> narrow(@Nullable Kind<TypeKind.Witness, A> kind) {
    return KindValidator.narrow(kind, TYPE, this::extractValue);
  }

  private <A> ConcreteType<A> extractValue(Kind<TypeKind.Witness, A> kind) {
    return switch (kind) {
      case TypeHolder<A> holder -> holder.value();
      default -> throw new ClassCastException(); // Caught by KindValidator
    };
  }
}
```

**Key Points:**
- Use a `static final Class` constant for the type
- Holder validation uses `KindValidator.requireForWiden()`
- Narrowing uses `KindValidator.narrow()` with extraction function
- Extraction function throws `ClassCastException` for invalid types (automatically wrapped)

### Pattern 2: Functor Implementation

```java
public class TypeFunctor implements Functor<TypeKind.Witness> {

  @Override
  public <A, B> Kind<TypeKind.Witness, B> map(
      Function<? super A, ? extends B> f,
      Kind<TypeKind.Witness, A> fa) {
    
    FunctionValidator.requireMapper(f, "map");
    KindValidator.requireNonNull(fa, "map");

    ConcreteType<A> concrete = HELPER.narrow(fa);
    ConcreteType<B> result = concrete.map(f);
    return HELPER.widen(result);
  }
}
```

**Key Points:**
- Validate function parameter with `FunctionValidator.requireMapper()`
- Validate Kind parameter with `KindValidator.requireNonNull()`
- Operation name provided for context in error messages

### Pattern 3: Applicative Implementation

```java
public class TypeApplicative extends TypeFunctor 
    implements Applicative<TypeKind.Witness> {

  @Override
  public <A> Kind<TypeKind.Witness, A> of(@Nullable A value) {
    return HELPER.widen(ConcreteType.of(value));
  }

  @Override
  public <A, B> Kind<TypeKind.Witness, B> ap(
      Kind<TypeKind.Witness, ? extends Function<A, B>> ff,
      Kind<TypeKind.Witness, A> fa) {

    KindValidator.requireNonNull(ff, "ap", "function");
    KindValidator.requireNonNull(fa, "ap", "argument");

    ConcreteType<? extends Function<A, B>> funcType = HELPER.narrow(ff);
    ConcreteType<A> argType = HELPER.narrow(fa);
    
    ConcreteType<B> result = funcType.flatMap(argType::map);
    return HELPER.widen(result);
  }
}
```

**Key Points:**
- Validate both Kind parameters separately with descriptive context
- Use three-parameter validation with descriptors like "function" and "argument"
- Descriptors appear in parentheses in error messages

### Pattern 4: Monad Implementation

```java
public class TypeMonad extends TypeApplicative 
    implements Monad<TypeKind.Witness> {

  public static final TypeMonad INSTANCE = new TypeMonad();

  private TypeMonad() {
    // Singleton pattern
  }

  @Override
  public <A, B> Kind<TypeKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<TypeKind.Witness, B>> f,
      Kind<TypeKind.Witness, A> ma) {

    FunctionValidator.requireFlatMapper(f, "flatMap");
    KindValidator.requireNonNull(ma, "flatMap");

    ConcreteType<A> concrete = HELPER.narrow(ma);
    ConcreteType<B> result = concrete.flatMap(a -> {
      Kind<TypeKind.Witness, B> kindB = f.apply(a);
      FunctionValidator.requireNonNullResult(kindB, "flatMap", "Kind");
      return HELPER.narrow(kindB);
    });
    return HELPER.widen(result);
  }
}
```

**Key Points:**
- Use `FunctionValidator.requireFlatMapper()` for flatMap functions
- Singleton pattern for stateless monads
- Validate that function result is not null using `requireNonNullResult()`
- Consistent validation before any operations

### Pattern 5: MonadError Implementation

```java
public class TypeMonad extends TypeApplicative 
    implements MonadError<TypeKind.Witness, ErrorType> {

  @Override
  public <A> Kind<TypeKind.Witness, A> raiseError(ErrorType error) {
    // For Throwable types, validate non-null
    if (error instanceof Throwable) {
      FunctionValidator.requireFunction(error, "error", "raiseError");
    }
    // For Unit types, no validation needed
    // For other nullable types (like Either), allow null
    
    return HELPER.widen(ConcreteType.error(error));
  }

  @Override
  public <A> Kind<TypeKind.Witness, A> handleErrorWith(
      Kind<TypeKind.Witness, A> ma,
      Function<? super ErrorType, ? extends Kind<TypeKind.Witness, A>> handler) {

    KindValidator.requireNonNull(ma, "handleErrorWith", "source");
    FunctionValidator.requireFunction(handler, "handler", "handleErrorWith");

    ConcreteType<A> concrete = HELPER.narrow(ma);
    ConcreteType<A> result = concrete.handleErrorWith(
        e -> HELPER.narrow(handler.apply(e))
    );
    return HELPER.widen(result);
  }
}
```

**Key Points:**
- Different validation strategies for different error types:
    - `Throwable`: Always validate non-null
    - `Unit`: No validation (marker type)
    - Other types: Allow null (Either semantics)
- Use parameter descriptors for clarity ("source", "handler")
- Three-parameter validation for handler functions

### Pattern 6: Traverse Implementation

```java
public class TypeTraverse implements Traverse<TypeKind.Witness> {

  public static final TypeTraverse INSTANCE = new TypeTraverse();

  @Override
  public <A, B> Kind<TypeKind.Witness, B> map(
      Function<? super A, ? extends B> f,
      Kind<TypeKind.Witness, A> fa) {
    
    FunctionValidator.requireMapper(f, "map");
    KindValidator.requireNonNull(fa, "map");

    // Implementation...
  }

  @Override
  public <G, A, B> Kind<G, Kind<TypeKind.Witness, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<TypeKind.Witness, A> ta) {

    FunctionValidator.requireApplicative(applicative, "traverse");
    FunctionValidator.requireMapper(f, "traverse");
    KindValidator.requireNonNull(ta, "traverse");

    // Implementation...
  }

  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid,
      Function<? super A, ? extends M> f,
      Kind<TypeKind.Witness, A> fa) {

    FunctionValidator.requireMonoid(monoid, "foldMap");
    FunctionValidator.requireMapper(f, "foldMap");
    KindValidator.requireNonNull(fa, "foldMap");

    // Implementation...
  }
}
```

**Key Points:**
- Use `FunctionValidator.requireApplicative()` for applicative instances
- Use `FunctionValidator.requireMonoid()` for monoid instances
- Validate all parameters before any operations
- Consistent operation name in all validators
- **Validation order**: Applicative/Monoid → Functions → Kinds

### Pattern 7: Core Type Validation (IO, Lazy, Either)

```java
// In core type interfaces/classes
public interface IO<A> {
  
  static <A> IO<A> delay(Supplier<A> thunk) {
    FunctionValidator.requireFunction(thunk, "thunk", "IO.delay");
    return thunk::get;
  }
  
  default <B> IO<B> map(Function<? super A, ? extends B> f) {
    FunctionValidator.requireMapper(f, "IO.map");
    return IO.delay(() -> f.apply(this.unsafeRunSync()));
  }
  
  default <B> IO<B> flatMap(Function<? super A, ? extends IO<B>> f) {
    FunctionValidator.requireFlatMapper(f, "IO.flatMap");
    return IO.delay(() -> {
      A a = this.unsafeRunSync();
      IO<B> nextIO = f.apply(a);
      FunctionValidator.requireNonNullResult(nextIO, "IO.flatMap", "IO");
      return nextIO.unsafeRunSync();
    });
  }
}
```

**Key Points:**
- Use qualified operation names (`IO.map`, `Lazy.flatMap`, `Either.map`)
- Validate function parameters in core type methods
- Check for null results from functions using `requireNonNullResult()`
- Maintain consistency with typeclass implementations

## Validation Order

Always validate in this order:

1. **Applicative/Monoid instances** (if present)
2. **Function parameters**
3. **Kind parameters**

This order ensures that the most fundamental requirements are checked first.

**Example:**
```java
// ✅ Correct order
FunctionValidator.requireApplicative(applicative, "traverse");
FunctionValidator.requireMapper(f, "traverse");
KindValidator.requireNonNull(ta, "traverse");

// ❌ Wrong order
KindValidator.requireNonNull(ta, "traverse");
FunctionValidator.requireMapper(f, "traverse");
FunctionValidator.requireApplicative(applicative, "traverse");
```

## Enhanced Validation Features

### Parameter Descriptors

For operations with multiple Kind parameters, use descriptive parameter names:

```java
// Standard pattern
KindValidator.requireNonNull(ff, "ap", "function");
KindValidator.requireNonNull(fa, "ap", "argument");

// Error message will be:
// "Kind for ap (function) cannot be null"
// "Kind for ap (argument) cannot be null"
```

**When to use descriptors:**
- Multiple Kind parameters (always use descriptors)
- Complex operations (use for clarity)
- Single Kind parameters (optional, but recommended for consistency)

### Result Validation

Always validate that functions return non-null values when required:

```java
// Pattern for flatMap and similar operations
ConcreteType<B> result = concrete.flatMap(a -> {
  Kind<TypeKind.Witness, B> kindB = f.apply(a);
  FunctionValidator.requireNonNullResult(kindB, "flatMap", "Kind");
  return HELPER.narrow(kindB);
});
```

**When to use result validation:**
- `flatMap` operations (always validate)
- Any operation that chains computations
- Factory methods that return instances

### Error Type Validation Strategy

Different error types require different validation approaches:

| Error Type | Validation | Rationale |
|-----------|-----------|-----------|
| `Throwable` | Validate non-null | Throwables should never be null |
| `Unit` | No validation | Marker type, single valid value |
| Nullable `E` (Either) | Allow null | Null is a valid error value |
| Other `E` | Context-dependent | Follow type semantics |

**Example:**
```java
// CompletableFuture (Throwable error type)
@Override
public <A> Kind<Witness, A> raiseError(Throwable error) {
  FunctionValidator.requireFunction(error, "error", "raiseError");
  return FUTURE.widen(CompletableFuture.failedFuture(error));
}

// Optional (Unit error type)
@Override
public <A> Kind<Witness, A> raiseError(Unit error) {
  // No validation - Unit is always valid
  return OPTIONAL.widen(Optional.empty());
}

// Either (nullable error type)
@Override
public <A> Kind<Witness<L>, A> raiseError(@Nullable L error) {
  // No validation - null is allowed for Either
  return EITHER.widen(Either.left(error));
}
```

## Error Message Standards

### Context Provides Automatic Messages

Contexts automatically generate appropriate error messages:

```java
// KindContext generates:
"Cannot narrow null Kind for CompletableFuture"
"Kind instance is not a CompletableFuture: SomeOtherClass"

// FunctionContext generates:
"function f for map cannot be null"
"applicative instance for traverse cannot be null"

// With descriptors:
"Kind for ap (function) cannot be null"
"Kind for ap (argument) cannot be null"
```

### Custom Messages

For custom validation requirements:

```java
var context = ConditionContext.custom("value", "positivity check");
if (value <= 0) {
  throw new IllegalArgumentException(
    context.customMessage("Value must be positive, got %d", value)
  );
}
```

## Complete Example: Either Implementation

Here's how the Either typeclass follows all these patterns:

```java
// KindHelper
public enum EitherKindHelper implements EitherConverterOps {
  EITHER;

  private static final Class<Either> TYPE = Either.class;

  record EitherHolder<L, R>(Either<L, R> either) implements EitherKind<L, R> {
    EitherHolder {
      KindValidator.requireForWiden(either, TYPE);
    }
  }

  @Override
  public <L, R> Kind<EitherKind.Witness<L>, R> widen(Either<L, R> either) {
    return new EitherHolder<>(either);
  }

  @Override
  public <L, R> Either<L, R> narrow(@Nullable Kind<EitherKind.Witness<L>, R> kind) {
    return KindValidator.narrow(kind, TYPE, this::extractEither);
  }

  private <L, R> Either<L, R> extractEither(Kind<EitherKind.Witness<L>, R> kind) {
    return switch (kind) {
      case EitherHolder<L, R> holder -> holder.either();
      default -> throw new ClassCastException();
    };
  }
}

// Functor
public class EitherFunctor<L> implements Functor<EitherKind.Witness<L>> {

  @Override
  public <A, B> Kind<EitherKind.Witness<L>, B> map(
      Function<? super A, ? extends B> f,
      Kind<EitherKind.Witness<L>, A> fa) {
    
    FunctionValidator.requireMapper(f, "map");
    KindValidator.requireNonNull(fa, "map");

    Either<L, A> eitherA = EITHER.narrow(fa);
    Either<L, B> resultEither = eitherA.map(f);
    return EITHER.widen(resultEither);
  }
}

// Monad with MonadError
public class EitherMonad<L> extends EitherFunctor<L>
    implements MonadError<EitherKind.Witness<L>, L> {

  @Override
  public <R> Kind<EitherKind.Witness<L>, R> of(@Nullable R value) {
    return EITHER.widen(Either.right(value));
  }

  @Override
  public <A, B> Kind<EitherKind.Witness<L>, B> ap(
      Kind<EitherKind.Witness<L>, ? extends Function<A, B>> ff,
      Kind<EitherKind.Witness<L>, A> fa) {

    KindValidator.requireNonNull(ff, "ap", "function");
    KindValidator.requireNonNull(fa, "ap", "argument");

    Either<L, ? extends Function<A, B>> eitherF = EITHER.narrow(ff);
    Either<L, A> eitherA = EITHER.narrow(fa);
    Either<L, B> resultEither = eitherF.flatMap(eitherA::map);
    return EITHER.widen(resultEither);
  }

  @Override
  public <A, B> Kind<EitherKind.Witness<L>, B> flatMap(
      Function<? super A, ? extends Kind<EitherKind.Witness<L>, B>> f,
      Kind<EitherKind.Witness<L>, A> ma) {

    FunctionValidator.requireFlatMapper(f, "flatMap");
    KindValidator.requireNonNull(ma, "flatMap");

    Either<L, A> eitherA = EITHER.narrow(ma);
    Either<L, B> resultEither = eitherA.flatMap(
        a -> EITHER.narrow(f.apply(a))
    );
    return EITHER.widen(resultEither);
  }

  @Override
  public <A> Kind<EitherKind.Witness<L>, A> raiseError(@Nullable L error) {
    // Either allows null error values
    return EITHER.widen(Either.left(error));
  }

  @Override
  public <A> Kind<EitherKind.Witness<L>, A> handleErrorWith(
      Kind<EitherKind.Witness<L>, A> ma,
      Function<? super L, ? extends Kind<EitherKind.Witness<L>, A>> handler) {

    KindValidator.requireNonNull(ma, "handleErrorWith", "source");
    FunctionValidator.requireFunction(handler, "handler", "handleErrorWith");

    Either<L, A> either = EITHER.narrow(ma);
    return either.fold(handler, _ -> ma);
  }
}

// Traverse
public final class EitherTraverse<E> implements Traverse<EitherKind.Witness<E>> {

  @Override
  public <A, B> Kind<EitherKind.Witness<E>, B> map(
      Function<? super A, ? extends B> f,
      Kind<EitherKind.Witness<E>, A> fa) {
    
    FunctionValidator.requireMapper(f, "map");
    KindValidator.requireNonNull(fa, "map");

    Either<E, A> either = EITHER.narrow(fa);
    Either<E, B> resultEither = either.map(f);
    return EITHER.widen(resultEither);
  }

  @Override
  public <G, A, B> Kind<G, Kind<EitherKind.Witness<E>, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<EitherKind.Witness<E>, A> ta) {

    FunctionValidator.requireApplicative(applicative, "traverse");
    FunctionValidator.requireMapper(f, "traverse");
    KindValidator.requireNonNull(ta, "traverse");

    Either<E, A> either = EITHER.narrow(ta);
    return either.fold(
        leftValue -> applicative.of(EITHER.widen(Either.left(leftValue))),
        rightValue -> applicative.map(
            b -> EITHER.widen(Either.right(b)), 
            f.apply(rightValue)
        )
    );
  }

  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid,
      Function<? super A, ? extends M> f,
      Kind<EitherKind.Witness<E>, A> fa) {

    FunctionValidator.requireMonoid(monoid, "foldMap");
    FunctionValidator.requireMapper(f, "foldMap");
    KindValidator.requireNonNull(fa, "foldMap");

    Either<E, A> either = EITHER.narrow(fa);
    return either.fold(left -> monoid.empty(), f);
  }
}

// Core Type
public sealed interface Either<L, R> permits Either.Left, Either.Right {
  
  default <R2> Either<L, R2> map(Function<? super R, ? extends R2> mapper) {
    FunctionValidator.requireMapper(mapper, "Either.map");
    return switch (this) {
      case Left<L, R> l -> (Either<L, R2>) l;
      case Right<L, R>(var rValue) -> Either.right(mapper.apply(rValue));
    };
  }

  <R2> Either<L, R2> flatMap(Function<? super R, ? extends Either<L, ? extends R2>> mapper);
  
  // ... other methods
}
```

## Migration Checklist

When migrating existing code to the standardized pattern:

### For KindHelper Classes

- [ ] Replace `Objects.requireNonNull()` with `KindValidator.requireForWiden()`
- [ ] Replace custom narrowing logic with `KindValidator.narrow()`
- [ ] Use `Class` constant for type references
- [ ] Simplify extraction to throw `ClassCastException` only
- [ ] Remove custom error message strings

### For Functor/Applicative/Monad Classes

- [ ] Replace function validation with `FunctionValidator.requireMapper()`
- [ ] Replace flatMap function validation with `FunctionValidator.requireFlatMapper()`
- [ ] Replace Kind validation with `KindValidator.requireNonNull()`
- [ ] Add operation name to all validators
- [ ] Add parameter descriptors for multiple Kind parameters
- [ ] Ensure validation happens before any operations
- [ ] Add result validation for flatMap operations

### For Traverse/Foldable Classes

- [ ] Use `FunctionValidator.requireApplicative()` for Applicative instances
- [ ] Use `FunctionValidator.requireMonoid()` for Monoid instances
- [ ] Use `FunctionValidator.requireMapper()` for mapping functions
- [ ] Ensure consistent validation order (Applicative/Monoid → Functions → Kinds)

### For MonadError Classes

- [ ] Implement appropriate error validation strategy based on error type
- [ ] Use parameter descriptors ("source", "handler") for clarity
- [ ] Validate Throwable errors as non-null
- [ ] Allow null for Either-style errors
- [ ] Skip validation for Unit marker types

### For Core Type Classes

- [ ] Use qualified operation names in validation (`IO.map`, `Lazy.flatMap`)
- [ ] Validate function parameters consistently
- [ ] Add result validation for flatMap-style operations
- [ ] Maintain consistency with typeclass implementations

## Testing Recommendations

### Unit Tests
```java
@Test
void shouldRejectNullFunction() {
  assertThrows(NullPointerException.class,
      () -> monad.map(null, validKind));
  
  var exception = assertThrows(NullPointerException.class,
      () -> monad.map(null, validKind));
  assertTrue(exception.getMessage().contains("function f for map"));
}

@Test
void shouldRejectNullKind() {
  assertThrows(NullPointerException.class,
      () -> monad.map(validFunction, null));
}

@Test
void shouldProvideDescriptiveErrorForApFunction() {
  var exception = assertThrows(NullPointerException.class,
      () -> applicative.ap(null, validKind));
  assertTrue(exception.getMessage().contains("ap (function)"));
}

@Test
void shouldProvideDescriptiveErrorForApArgument() {
  var exception = assertThrows(NullPointerException.class,
      () -> applicative.ap(validFunctionKind, null));
  assertTrue(exception.getMessage().contains("ap (argument)"));
}

@Test
void shouldValidateNullResultFromFlatMap() {
  Function<String, Kind<Witness, Integer>> returnsNull = x -> null;
  assertThrows(IllegalStateException.class,
      () -> monad.flatMap(returnsNull, validKind));
}
```

### Integration Tests
```java
@Test
void shouldMaintainValidationAcrossChainedOperations() {
  Kind<Witness, Integer> kind1 = helper.widen(value(10));
  
  Kind<Witness, Integer> kind2 = monad.flatMap(
      x -> helper.widen(value(x * 2)),
      kind1);
  
  Kind<Witness, String> kind3 = monad.map(
      x -> "Result: " + x,
      kind2);
  
  // All validations should have occurred
  assertEquals("Result: 20", helper.narrow(kind3).get());
}
```

### Consistency Tests
```java
@Test
void shouldFollowStandardValidationPattern() {
  // Verify all Functor implementations use requireMapper
  assertMethodCallsValidator(
      EitherFunctor.class, 
      "map",
      FunctionValidator.class,
      "requireMapper"
  );
  
  // Verify all Monad implementations use requireFlatMapper
  assertMethodCallsValidator(
      EitherMonad.class,
      "flatMap",
      FunctionValidator.class,
      "requireFlatMapper"
  );
}
```

## Common Pitfalls to Avoid

### ❌ Don't: Mix validation styles
```java
Objects.requireNonNull(f, "function cannot be null");
KindValidator.requireNonNull(fa, "map");
```

### ✅ Do: Use consistent validators
```java
FunctionValidator.requireMapper(f, "map");
KindValidator.requireNonNull(fa, "map");
```

### ❌ Don't: Validate after operations
```java
ConcreteType<A> concrete = HELPER.narrow(fa);
FunctionValidator.requireMapper(f, "map"); // Too late!
```

### ✅ Do: Validate before operations
```java
FunctionValidator.requireMapper(f, "map");
KindValidator.requireNonNull(fa, "map");
ConcreteType<A> concrete = HELPER.narrow(fa);
```

### ❌ Don't: Forget parameter descriptors for multiple Kinds
```java
KindValidator.requireNonNull(ff, "ap");
KindValidator.requireNonNull(fa, "ap");
```

### ✅ Do: Use descriptive parameter names
```java
KindValidator.requireNonNull(ff, "ap", "function");
KindValidator.requireNonNull(fa, "ap", "argument");
```

### ❌ Don't: Ignore null results from functions
```java
IO<B> ioB = ioA.flatMap(a -> f.apply(a)); // f might return null!
```

### ✅ Do: Validate function results
```java
IO<B> ioB = ioA.flatMap(a -> {
  IO<B> result = f.apply(a);
  FunctionValidator.requireNonNullResult(result, "flatMap", "IO");
  return result;
});
```

### ❌ Don't: Use custom error messages
```java
throw new KindUnwrapException("The kind is null and cannot be narrowed");
```

### ✅ Do: Use context-generated messages
```java
KindValidator.requireNonNull(kind, "narrow");
```

## Validator API Reference

### FunctionValidator Methods

```java
// Basic validators
<T> T requireMapper(Function<?, ?> f, String operation)
<T> T requireFlatMapper(Function<?, ?> f, String operation)
<T> T requireFunction(T function, String functionName, String operation)

// Specialized validators
<T> T requireApplicative(Applicative<?> applicative, String operation)
<T> T requireMonoid(Monoid<?> monoid, String operation)

// Result validation
<T> T requireNonNullResult(T result, String operation, String expectedType)
```

### KindValidator Methods

```java
// Basic validation
<F, A> Kind<F, A> requireNonNull(Kind<F, A> kind, String operation)

// Enhanced validation with descriptor
<F, A> Kind<F, A> requireNonNull(
    Kind<F, A> kind, 
    String operation,
    @Nullable String descriptor)

// Widen validation
<T> T requireForWiden(T input, Class<T> type)

// Narrow validation
<F, A, T> T narrow(
    @Nullable Kind<F, A> kind,
    Class<T> targetType,
    Function<Kind<F, A>, T> extractor)
```

## Benefits of This Approach

### 1. Consistency
- All typeclasses follow identical validation patterns
- Error messages are predictable and clear
- Developers know exactly what to expect

### 2. Type Safety
- Contexts prevent passing description strings as type names
- Validators enforce correct parameter types
- Compile-time safety where possible

### 3. Maintainability
- Centralized validation logic
- Easy to update error messages globally
- Clear separation of concerns

### 4. Scalability
- New typeclasses follow the same blueprint
- Adding new validation types is straightforward
- Patterns compose naturally

### 5. Developer Experience
- Clear error messages with context
- Consistent API across all types
- Self-documenting code
- Enhanced debugging with descriptive parameter names

## Conclusion

This standardized validation pattern provides a robust, scalable foundation for error handling across Higher-Kinded-J. By following these patterns consistently:

- Error messages become predictable and helpful
- Code becomes more maintainable and less error-prone
- New typeclasses integrate seamlessly
- Developers have a clear blueprint to follow
- Enhanced features like parameter descriptors and result validation catch errors early

The pattern has been successfully applied to Either, CompletableFuture, IO, Lazy, and Optional, demonstrating its effectiveness and universal applicability across diverse functional programming constructs.
# Standardized Validation Patterns for Higher-Kinded-J

## Overview

This document describes the standardized approach for error handling and validation across Higher-Kinded-J. The pattern uses context objects and specialized validators to provide consistent, type-safe validation with clear error messages.

## Core Philosophy

1. **Separation of Concerns**: Validation logic is separated from business logic
2. **Context-Aware Messaging**: Error messages include relevant context automatically
3. **Type Safety**: Validators are type-safe and prevent common mistakes
4. **Scalability**: Pattern scales easily to new typeclasses and operations
5. **Consistency**: All typeclass implementations follow the same patterns

## Architecture

### Validation Contexts

Context objects carry semantic information about what is being validated. They implement the `ValidationContext` interface and provide standardized error messages.

#### Available Contexts

```java
// For Kind operations (widen/narrow)
KindContext.narrow(CompletableFuture.class)
KindContext.widen(CompletableFuture.class)

// For function parameters in monad operations
FunctionContext.mapper("map")
FunctionContext.flatMapper("flatMap")
FunctionContext.applicative("traverse")

// For collection validations
CollectionContext.collection("parameterName")
CollectionContext.array("parameterName")

// For condition/range validations
ConditionContext.range("parameterName")
ConditionContext.custom("parameterName", "description")

// For domain-specific validations
DomainContext.transformer("transformerName")
DomainContext.witness("operation")
```

### Specialized Validators

Each validator handles a specific category of validation with appropriate error messaging:

- **`KindValidator`**: Kind operations (widen/narrow/typecheck)
- **`FunctionValidator`**: Function parameters in monad operations
- **`CollectionValidator`**: Collection and array validations
- **`ConditionValidator`**: Conditional logic and range checks
- **`DomainValidator`**: Domain-specific validations (transformers, witnesses)
- **`ExceptionValidator`**: Exception wrapping and context preservation
- **`CompositeValidator`**: Complex multi-step validations
- **`TextValidator`**: String and text-based validations
- **`NumericValidator`**: Numeric constraint validations

## Standard Implementation Patterns

### Pattern 1: KindHelper Implementation

All KindHelper classes follow this standardized structure:

```java
public enum TypeKindHelper implements TypeConverterOps {
  INSTANCE; // or descriptive name like FUTURE, OPTIONAL, etc.

  private static final Class<ConcreteType> TYPE = ConcreteType.class;

  // Internal holder record
  record TypeHolder<A>(ConcreteType<A> value) implements TypeKind<A> {
    TypeHolder {
      KindValidator.requireForWiden(value, TYPE);
    }
  }

  @Override
  public <A> Kind<TypeKind.Witness, A> widen(ConcreteType<A> value) {
    return new TypeHolder<>(value);
  }

  @Override
  public <A> ConcreteType<A> narrow(@Nullable Kind<TypeKind.Witness, A> kind) {
    return KindValidator.narrow(kind, TYPE, this::extractValue);
  }

  private <A> ConcreteType<A> extractValue(Kind<TypeKind.Witness, A> kind) {
    return switch (kind) {
      case TypeHolder<A> holder -> holder.value();
      default -> throw new ClassCastException(); // Caught by KindValidator
    };
  }
}
```

**Key Points:**
- Use a `static final Class` constant for the type
- Holder validation uses `KindValidator.requireForWiden()`
- Narrowing uses `KindValidator.narrow()` with extraction function
- Extraction function throws `ClassCastException` for invalid types (automatically wrapped)

### Pattern 2: Functor Implementation

```java
public class TypeFunctor implements Functor<TypeKind.Witness> {

  @Override
  public <A, B> Kind<TypeKind.Witness, B> map(
      Function<? super A, ? extends B> f,
      Kind<TypeKind.Witness, A> fa) {
    
    FunctionValidator.requireMapper(f, "map");
    KindValidator.requireNonNull(fa, "map");

    ConcreteType<A> concrete = HELPER.narrow(fa);
    ConcreteType<B> result = concrete.map(f);
    return HELPER.widen(result);
  }
}
```

**Key Points:**
- Validate function parameter with `FunctionValidator.requireMapper()`
- Validate Kind parameter with `KindValidator.requireNonNull()`
- Operation name provided for context in error messages

### Pattern 3: Applicative Implementation

```java
public class TypeApplicative extends TypeFunctor 
    implements Applicative<TypeKind.Witness> {

  @Override
  public <A> Kind<TypeKind.Witness, A> of(@Nullable A value) {
    return HELPER.widen(ConcreteType.of(value));
  }

  @Override
  public <A, B> Kind<TypeKind.Witness, B> ap(
      Kind<TypeKind.Witness, ? extends Function<A, B>> ff,
      Kind<TypeKind.Witness, A> fa) {

    KindValidator.requireNonNull(ff, "ap (function)");
    KindValidator.requireNonNull(fa, "ap (argument)");

    ConcreteType<? extends Function<A, B>> funcType = HELPER.narrow(ff);
    ConcreteType<A> argType = HELPER.narrow(fa);
    
    ConcreteType<B> result = funcType.flatMap(argType::map);
    return HELPER.widen(result);
  }
}
```

**Key Points:**
- Validate both Kind parameters separately with descriptive context
- Use parenthetical descriptors like "(function)" and "(argument)" for clarity

### Pattern 4: Monad Implementation

```java
public class TypeMonad extends TypeApplicative 
    implements Monad<TypeKind.Witness> {

  public static final TypeMonad INSTANCE = new TypeMonad();

  private TypeMonad() {
    // Singleton pattern
  }

  @Override
  public <A, B> Kind<TypeKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<TypeKind.Witness, B>> f,
      Kind<TypeKind.Witness, A> ma) {

    FunctionValidator.requireFlatMapper(f, "flatMap");
    KindValidator.requireNonNull(ma, "flatMap");

    ConcreteType<A> concrete = HELPER.narrow(ma);
    ConcreteType<B> result = concrete.flatMap(
        a -> HELPER.narrow(f.apply(a))
    );
    return HELPER.widen(result);
  }
}
```

**Key Points:**
- Use `FunctionValidator.requireFlatMapper()` for flatMap functions
- Singleton pattern for stateless monads
- Consistent validation before any operations

### Pattern 5: MonadError Implementation

```java
public class TypeMonad extends TypeApplicative 
    implements MonadError<TypeKind.Witness, ErrorType> {

  @Override
  public <A> Kind<TypeKind.Witness, A> raiseError(ErrorType error) {
    FunctionValidator.requireFunction(error, "error", "raiseError");
    return HELPER.widen(ConcreteType.error(error));
  }

  @Override
  public <A> Kind<TypeKind.Witness, A> handleErrorWith(
      Kind<TypeKind.Witness, A> ma,
      Function<? super ErrorType, ? extends Kind<TypeKind.Witness, A>> handler) {

    KindValidator.requireNonNull(ma, "handleErrorWith");
    FunctionValidator.requireFunction(handler, "handler", "handleErrorWith");

    ConcreteType<A> concrete = HELPER.narrow(ma);
    ConcreteType<A> result = concrete.handleErrorWith(
        e -> HELPER.narrow(handler.apply(e))
    );
    return HELPER.widen(result);
  }
}
```

**Key Points:**
- Validate error values as functions (they're input parameters)
- Three-parameter validation for handler functions
- Consistent ordering: validate Kind first, then functions

### Pattern 6: Traverse Implementation

```java
public class TypeTraverse implements Traverse<TypeKind.Witness> {

  public static final TypeTraverse INSTANCE = new TypeTraverse();

  @Override
  public <A, B> Kind<TypeKind.Witness, B> map(
      Function<? super A, ? extends B> f,
      Kind<TypeKind.Witness, A> fa) {
    
    FunctionValidator.requireMapper(f, "map");
    KindValidator.requireNonNull(fa, "map");

    // Implementation...
  }

  @Override
  public <G, A, B> Kind<G, Kind<TypeKind.Witness, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<TypeKind.Witness, A> ta) {

    FunctionValidator.requireApplicative(applicative, "traverse");
    FunctionValidator.requireMapper(f, "traverse");
    KindValidator.requireNonNull(ta, "traverse");

    // Implementation...
  }

  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid,
      Function<? super A, ? extends M> f,
      Kind<TypeKind.Witness, A> fa) {

    FunctionValidator.requireMonoid(monoid, "foldMap");
    FunctionValidator.requireMapper(f, "foldMap");
    KindValidator.requireNonNull(fa, "foldMap");

    // Implementation...
  }
}
```

**Key Points:**
- Use `FunctionValidator.requireApplicative()` for applicative instances
- Use `FunctionValidator.requireMonoid()` for monoid instances
- Validate all parameters before any operations
- Consistent operation name in all validators

## Validation Order

Always validate in this order:

1. **Applicative/Monoid instances** (if present)
2. **Function parameters**
3. **Kind parameters**

This order ensures that the most fundamental requirements are checked first.

## Error Message Standards

### Context Provides Automatic Messages

Contexts automatically generate appropriate error messages:

```java
// KindContext generates:
"Cannot narrow null Kind for CompletableFuture"
"Kind instance is not a CompletableFuture: SomeOtherClass"

// FunctionContext generates:
"function f for map cannot be null"
"applicative instance for traverse cannot be null"

// CollectionContext generates:
"items cannot be null"
"items cannot be empty"
```

### Custom Messages

For custom validation requirements:

```java
var context = ConditionContext.custom("value", "positivity check");
if (value <= 0) {
  throw new IllegalArgumentException(
    context.customMessage("Value must be positive, got %d", value)
  );
}
```

## Migration Checklist

When migrating existing code to the standardized pattern:

### For KindHelper Classes

- [ ] Replace `Objects.requireNonNull()` with `KindValidator.requireForWiden()`
- [ ] Replace custom narrowing logic with `KindValidator.narrow()`
- [ ] Use `Class` constant for type references
- [ ] Simplify extraction to throw `ClassCastException` only
- [ ] Remove custom error message strings

### For Functor/Applicative/Monad Classes

- [ ] Replace function validation with `FunctionValidator.requireMapper()`
- [ ] Replace flatMap function validation with `FunctionValidator.requireFlatMapper()`
- [ ] Replace Kind validation with `KindValidator.requireNonNull()`
- [ ] Add operation name to all validators
- [ ] Ensure validation happens before any operations
- [ ] Use parenthetical descriptors for multiple Kinds

### For Traverse/Foldable Classes

- [ ] Use `FunctionValidator.requireApplicative()` for Applicative instances
- [ ] Use `FunctionValidator.requireMonoid()` for Monoid instances
- [ ] Use `FunctionValidator.requireMapper()` for mapping functions
- [ ] Ensure consistent validation order

## Complete Example: Either Implementation

Here's how the Either typeclass follows all these patterns:

```java
// KindHelper
public enum EitherKindHelper implements EitherConverterOps {
  EITHER;

  record EitherHolder<L, R>(Either<L, R> either) implements EitherKind<L, R> {
    EitherHolder {
      KindValidator.requireForWiden(either, Either.class);
    }
  }

  @Override
  public <L, R> Kind<EitherKind.Witness<L>, R> widen(Either<L, R> either) {
    return new EitherHolder<>(either);
  }

  @Override
  public <L, R> Either<L, R> narrow(@Nullable Kind<EitherKind.Witness<L>, R> kind) {
    return KindValidator.narrow(kind, Either.class, this::extractEither);
  }

  private <L, R> Either<L, R> extractEither(Kind<EitherKind.Witness<L>, R> kind) {
    return switch (kind) {
      case EitherHolder<L, R> holder -> holder.either();
      default -> throw new ClassCastException();
    };
  }
}

// Functor
public class EitherFunctor<L> implements Functor<EitherKind.Witness<L>> {

  @Override
  public <A, B> Kind<EitherKind.Witness<L>, B> map(
      Function<? super A, ? extends B> f,
      Kind<EitherKind.Witness<L>, A> fa) {
    
    FunctionValidator.requireMapper(f, "map");
    KindValidator.requireNonNull(fa, "map");

    Either<L, A> eitherA = EITHER.narrow(fa);
    Either<L, B> resultEither = eitherA.map(f);
    return EITHER.widen(resultEither);
  }
}

// Monad with MonadError
public class EitherMonad<L> extends EitherFunctor<L>
    implements MonadError<EitherKind.Witness<L>, L> {

  @Override
  public <R> Kind<EitherKind.Witness<L>, R> of(@Nullable R value) {
    return EITHER.widen(Either.right(value));
  }

  @Override
  public <A, B> Kind<EitherKind.Witness<L>, B> flatMap(
      Function<? super A, ? extends Kind<EitherKind.Witness<L>, B>> f,
      Kind<EitherKind.Witness<L>, A> ma) {

    FunctionValidator.requireFlatMapper(f, "flatMap");
    KindValidator.requireNonNull(ma, "flatMap");

    Either<L, A> eitherA = EITHER.narrow(ma);
    Either<L, B> resultEither = eitherA.flatMap(
        a -> EITHER.narrow(f.apply(a))
    );
    return EITHER.widen(resultEither);
  }

  @Override
  public <A> Kind<EitherKind.Witness<L>, A> raiseError(@Nullable L error) {
    return EITHER.widen(Either.left(error));
  }

  @Override
  public <A> Kind<EitherKind.Witness<L>, A> handleErrorWith(
      Kind<EitherKind.Witness<L>, A> ma,
      Function<? super L, ? extends Kind<EitherKind.Witness<L>, A>> handler) {

    KindValidator.requireNonNull(ma, "handleErrorWith");
    FunctionValidator.requireFunction(handler, "handler", "handleErrorWith");

    Either<L, A> either = EITHER.narrow(ma);
    return either.fold(handler, _ -> ma);
  }
}

// Traverse
public final class EitherTraverse<E> implements Traverse<EitherKind.Witness<E>> {

  @Override
  public <A, B> Kind<EitherKind.Witness<E>, B> map(
      Function<? super A, ? extends B> f,
      Kind<EitherKind.Witness<E>, A> fa) {
    
    FunctionValidator.requireMapper(f, "map");
    KindValidator.requireNonNull(fa, "map");

    Either<E, A> either = EITHER.narrow(fa);
    Either<E, B> resultEither = either.map(f);
    return EITHER.widen(resultEither);
  }

  @Override
  public <G, A, B> Kind<G, Kind<EitherKind.Witness<E>, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<EitherKind.Witness<E>, A> ta) {

    FunctionValidator.requireApplicative(applicative, "traverse");
    FunctionValidator.requireMapper(f, "traverse");
    KindValidator.requireNonNull(ta, "traverse");

    Either<E, A> either = EITHER.narrow(ta);
    return either.fold(
        leftValue -> applicative.of(EITHER.widen(Either.left(leftValue))),
        rightValue -> applicative.map(
            b -> EITHER.widen(Either.right(b)), 
            f.apply(rightValue)
        )
    );
  }

  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid,
      Function<? super A, ? extends M> f,
      Kind<EitherKind.Witness<E>, A> fa) {

    FunctionValidator.requireMonoid(monoid, "foldMap");
    FunctionValidator.requireMapper(f, "foldMap");
    KindValidator.requireNonNull(fa, "foldMap");

    Either<E, A> either = EITHER.narrow(fa);
    return either.fold(left -> monoid.empty(), f);
  }
}
```

## Benefits of This Approach

### 1. Consistency
- All typeclasses follow identical validation patterns
- Error messages are predictable and clear
- Developers know exactly what to expect

### 2. Type Safety
- Contexts prevent passing description strings as type names
- Validators enforce correct parameter types
- Compile-time safety where possible

### 3. Maintainability
- Centralized validation logic
- Easy to update error messages globally
- Clear separation of concerns

### 4. Scalability
- New typeclasses follow the same blueprint
- Adding new validation types is straightforward
- Patterns compose naturally

### 5. Developer Experience
- Clear error messages with context
- Consistent API across all types
- Self-documenting code

## Common Pitfalls to Avoid

### ❌ Don't: Mix validation styles
```java
Objects.requireNonNull(f, "function cannot be null");
KindValidator.requireNonNull(fa, "map");
```

### ✅ Do: Use consistent validators
```java
FunctionValidator.requireMapper(f, "map");
KindValidator.requireNonNull(fa, "map");
```

### ❌ Don't: Validate after operations
```java
ConcreteType<A> concrete = HELPER.narrow(fa);
FunctionValidator.requireMapper(f, "map"); // Too late!
```

### ✅ Do: Validate before operations
```java
FunctionValidator.requireMapper(f, "map");
KindValidator.requireNonNull(fa, "map");
ConcreteType<A> concrete = HELPER.narrow(fa);
```

### ❌ Don't: Use custom error messages
```java
throw new KindUnwrapException("The kind is null and cannot be narrowed");
```

### ✅ Do: Use context-generated messages
```java
KindValidator.requireNonNull(kind, "narrow");
```

### ❌ Don't: Implement complex narrowing logic
```java
if (kind == null) throw new KindUnwrapException(...);
if (!(kind instanceof Holder)) throw new KindUnwrapException(...);
return ((Holder<A>) kind).value();
```

### ✅ Do: Use validator with simple extraction
```java
return KindValidator.narrow(kind, TYPE, this::extract);
```

## Testing Validation

### Test Null Parameters
```java
@Test
void shouldRejectNullFunction() {
    assertThrows(NullPointerException.class,
        () -> monad.map(null, validKind));
}

@Test
void shouldRejectNullKind() {
    assertThrows(NullPointerException.class,
        () -> monad.map(validFunction, null));
}
```

### Test Error Messages
```java
@Test
void shouldProvideContextInErrorMessage() {
    var exception = assertThrows(NullPointerException.class,
        () -> monad.map(null, validKind));
    assertTrue(exception.getMessage().contains("function f for map"));
}
```

### Test Invalid Types
```java
@Test
void shouldRejectInvalidKindType() {
    Kind<WrongWitness, String> wrongKind = // ...
    var exception = assertThrows(KindUnwrapException.class,
        () -> helper.narrow(wrongKind));
    assertTrue(exception.getMessage().contains("Kind instance is not"));
}
```

## Conclusion

This standardized validation pattern provides a robust, scalable foundation for error handling across Higher-Kinded-J. By following these patterns consistently:

- Error messages become predictable and helpful
- Code becomes more maintainable and less error-prone
- New typeclasses integrate seamlessly
- Developers have a clear blueprint to follow

The pattern has been successfully applied to `Either`, `CompletableFuture`, `Optional`, and other core types, demonstrating its effectiveness and scalability.