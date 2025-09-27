# Standardized Validation Patterns for Higher-Kinded-J

Overview
This document describes the standardized approach for error handling and validation across Higher-Kinded-J. The pattern uses context objects and specialized validators to provide consistent, type-safe validation with clear error messages.
Core Philosophy

Separation of Concerns: Validation logic is separated from business logic
Context-Aware Messaging: Error messages include relevant context automatically
Type Safety: Validators are type-safe and prevent common mistakes
Scalability: Pattern scales easily to new typeclasses and operations
Consistency: All typeclass implementations follow the same patterns

Architecture
Validation Contexts
Context objects carry semantic information about what is being validated. They implement the ValidationContext interface and provide standardized error messages.
Available Contexts

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
Specialized Validators
Each validator handles a specific category of validation with appropriate error messaging:

KindValidator: Kind operations (widen/narrow/typecheck)
FunctionValidator: Function parameters in monad operations
CollectionValidator: Collection and array validations
ConditionValidator: Conditional logic and range checks
DomainValidator: Domain-specific validations (transformers, witnesses)
ExceptionValidator: Exception wrapping and context preservation
CompositeValidator: Complex multi-step validations
TextValidator: String and text-based validations
NumericValidator: Numeric constraint validations

Standard Implementation Patterns
Pattern 1: KindHelper Implementation
All KindHelper classes follow this standardized structure:
```java
public enum TypeKindHelper implements TypeConverterOps {
INSTANCE; // or descriptive name like EITHER, READER, STATE, VALIDATED

private static final Class<ConcreteType> TYPE = ConcreteType.class;

// Internal holder record (for types that need holders)
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
Special Case - Direct Implementation (Validated):
For types where the concrete type already implements the Kind interface:

```java
public enum ValidatedKindHelper implements ValidatedConverterOps {
VALIDATED;

private static final Class<Validated> TYPE = Validated.class;

@Override
@SuppressWarnings("unchecked")
public <E, A> Kind<ValidatedKind.Witness<E>, A> widen(Validated<E, A> validated) {
KindValidator.requireForWiden(validated, TYPE);
return (Kind<ValidatedKind.Witness<E>, A>) validated;
}

@Override
@SuppressWarnings("unchecked")
public <E, A> Validated<E, A> narrow(@Nullable Kind<ValidatedKind.Witness<E>, A> kind) {
return KindValidator.narrowWithTypeCheck(kind, TYPE);
}
}
```
Key Points:

Use a static final Class constant for the type
Holder validation uses KindValidator.requireForWiden()
Narrowing uses KindValidator.narrow() with extraction function OR KindValidator.narrowWithTypeCheck() for direct implementations
Extraction function throws ClassCastException for invalid types (automatically wrapped)

Pattern 2: Functor Implementation
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
Key Points:

Validate function parameter with FunctionValidator.requireMapper()
Validate Kind parameter with KindValidator.requireNonNull()
Operation name provided for context in error messages

Pattern 3: Applicative Implementation
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
Key Points:

Validate both Kind parameters separately with descriptive context
Use three-parameter validation with descriptors like "function" and "argument"
Descriptors appear in parentheses in error messages

Pattern 4: Monad Implementation
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
      FunctionValidator.requireNonNullResult(kindB, "flatMap", ConcreteType.class);
      return HELPER.narrow(kindB);
    });
    return HELPER.widen(result);
}
}
```
Key Points:

Use FunctionValidator.requireFlatMapper() for flatMap functions
Singleton pattern for stateless monads
Validate that function result is not null using requireNonNullResult()
Consistent validation before any operations

Pattern 5: MonadError Implementation
```java
public class TypeMonad extends TypeApplicative
implements MonadError<TypeKind.Witness, ErrorType> {

@Override
public <A> Kind<TypeKind.Witness, A> raiseError(ErrorType error) {
// For Throwable types, validate non-null
if (error instanceof Throwable) {
Objects.requireNonNull(error, "error for raiseError cannot be null");
}
// For Unit types, no validation needed
// For other nullable types (like Either/Validated), validate non-null
else {
Objects.requireNonNull(error, "error for raiseError cannot be null");
}

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
Key Points:

Error validation strategy depends on error type (see table below)
Use parameter descriptors for clarity ("source", "handler")
Three-parameter validation for handler functions

Pattern 6: Traverse Implementation
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
Key Points:

Use FunctionValidator.requireApplicative() for applicative instances
Use FunctionValidator.requireMonoid() for monoid instances
Validate all parameters before any operations
Consistent operation name in all validators
Validation order: Applicative/Monoid → Functions → Kinds

Pattern 7: Core Type Validation
```java
// In core type interfaces/classes (Either, Reader, State, Validated)
public interface ConcreteType<A> {

static <A> ConcreteType<A> of(Function<...> fn) {
FunctionValidator.requireFunction(fn, "function parameter name", "ConcreteType.of");
return ...;
}

default <B> ConcreteType<B> map(Function<? super A, ? extends B> f) {
FunctionValidator.requireMapper(f, "ConcreteType.map");
return ...;
}

default <B> ConcreteType<B> flatMap(Function<? super A, ? extends ConcreteType<B>> f) {
FunctionValidator.requireFlatMapper(f, "ConcreteType.flatMap");
ConcreteType<B> result = ...;
Objects.requireNonNull(result, "ConcreteType.flatMap function returned null");
return result;
}
}
```
Key Points:

Use qualified operation names (ConcreteType.map, ConcreteType.flatMap, ConcreteType.of)
Validate function parameters in core type methods
Check for null results from functions using requireNonNullResult() or Objects.requireNonNull()
Maintain consistency with typeclass implementations

Validation Order
Always validate in this order:

Applicative/Monoid instances (if present)
Function parameters
Kind parameters

This order ensures that the most fundamental requirements are checked first.
Example:
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