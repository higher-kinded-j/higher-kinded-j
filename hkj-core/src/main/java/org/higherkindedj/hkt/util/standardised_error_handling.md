# Standardized Validation Patterns for Higher-Kinded-J

## Overview
This document describes the standardized approach for error handling and validation across Higher-Kinded-J. The pattern uses context objects and specialized validators to provide consistent, type-safe validation with clear error messages.
Core Philosophy

Separation of Concerns: Validation logic is separated from business logic
Context-Aware Messaging: Error messages include relevant context automatically
Type Safety: Validators are type-safe and prevent common mistakes
Scalability: Pattern scales easily to new typeclasses and operations
Consistency: All typeclass implementations follow the same patterns

## Architecture
### Validation Contexts
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
### Specialized Validators
Each validator handles a specific category of validation with appropriate error messaging:

- KindValidator: Kind operations (widen/narrow/typecheck)
- FunctionValidator: Function parameters in monad operations
- CollectionValidator: Collection and array validations
- ConditionValidator: Conditional logic and range checks
- DomainValidator: Domain-specific validations (transformers, witnesses)
- ExceptionValidator: Exception wrapping and context preservation
- CompositeValidator: Complex multi-step validations
- TextValidator: String and text-based validations
- NumericValidator: Numeric constraint validations
- CoreTypeValidator: Validate the core type's own operations and constructors

## Standard Implementation Patterns
### Pattern 1: KindHelper Implementation
All KindHelper classes follow this standardised structure:
```java
public enum TypeKindHelper implements TypeConverterOps {
    INSTANCE; // or descriptive name like EITHER, FUTURE, MAYBE, LAZY

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
#### Special Case - Direct Implementation (Validated):
For types where the concrete type already implements the Kind interface (e.g., Id, MaybeT, EitherT):

```java
@Override
public <A> Kind<TypeKind.Witness, A> widen(ConcreteType<A> value) {
    KindValidator.requireForWiden(value, TYPE);
    return value; // Direct cast since type implements Kind
}

@Override
public <A> ConcreteType<A> narrow(@Nullable Kind<TypeKind.Witness, A> kind) {
    return KindValidator.narrowWithTypeCheck(kind, TYPE);
}
```


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

#### Pattern 4: Monad Implementation
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

#### Pattern 5: MonadError Implementation
```java
public class TypeMonad extends TypeApplicative
        implements MonadError<TypeKind.Witness, ErrorType> {

    @Override
    public <A> Kind<TypeKind.Witness, A> raiseError(@Nullable ErrorType error) {
        // Validation strategy by error type:
        // - Throwable: Always validate non-null
        // - Unit: Never validate (singleton)
        // - Others: Context-dependent validation
        
        if (error instanceof Throwable) {
            FunctionValidator.requireFunction(error, "error", TYPE, "raiseError");
        }
        // Unit types need no validation
        // Domain types may allow null based on semantics
        
        return HELPER.widen(ConcreteType.error(error));
    }

    @Override
    public <A> Kind<TypeKind.Witness, A> handleErrorWith(
            Kind<TypeKind.Witness, A> ma,
            Function<? super ErrorType, ? extends Kind<TypeKind.Witness, A>> handler) {

        KindValidator.requireNonNull(ma, "handleErrorWith", "source");
        FunctionValidator.requireFunction(handler, "handler", "handleErrorWith");

        ConcreteType<A> concrete = HELPER.narrow(ma);
        // Implementation...
        return HELPER.widen(result);
    }
}
```


### Pattern 6: Traverse Implementation
```java
public class TypeTraverse implements Traverse<TypeKind.Witness> {

    public static final TypeTraverse INSTANCE = new TypeTraverse();

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

### Pattern 7: Transformer Implementation
```java
public class TransformerTMonad<F> implements MonadError<TransformerTKind.Witness<F>, ErrorType> {

    private final Monad<F> outerMonad;

    public TransformerTMonad(Monad<F> outerMonad) {
        this.outerMonad = DomainValidator.requireOuterMonad(
            outerMonad, TransformerT.class, "construction"
        );
    }

    // Factory methods validate components
    public static <F, A> TransformerT<F, A> fromInner(Monad<F> outerMonad, InnerType<A> inner) {
        DomainValidator.requireOuterMonad(outerMonad, TransformerT.class, "fromInner");
        DomainValidator.requireTransformerComponent(
            inner, "inner value", TransformerT.class, "fromInner"
        );
        // Implementation...
    }
}
```
### Pattern 8: Core Type Validation
```java
// In core type interfaces/classes (Either, Maybe, Lazy, etc.)
public interface ConcreteType<A> {

    static <A> ConcreteType<A> of(A value) {
        FunctionValidator.requireFunction(value, "value", "ConcreteType.of");
        return new ConcreteImpl<>(value);
    }

    default <B> ConcreteType<B> map(Function<? super A, ? extends B> f) {
        FunctionValidator.requireMapper(f, "ConcreteType.map");
        return /* implementation */;
    }

    default <B> ConcreteType<B> flatMap(
            Function<? super A, ? extends ConcreteType<? extends B>> f) {
        FunctionValidator.requireFlatMapper(f, "ConcreteType.flatMap");
        
        ConcreteType<? extends B> result = f.apply(value);
        FunctionValidator.requireNonNullResult(result, "ConcreteType.flatMap", ConcreteType.class);
        
        return (ConcreteType<B>) result;
    }
}

```


### Validation Order
Always validate in this order:
1. Applicative/Monoid instances (if present)
2. Function parameters 
3. Kind parameters

This order ensures that the most fundamental requirements are checked first.
Example:
```java
// ✅ Correct order
FunctionValidator.requireApplicative(applicative, "traverse");
FunctionValidator.requireMapper(f, "traverse");
KindValidator.requireNonNull(ta, "traverse");

// ❌ Wrong order - validate core dependencies first
KindValidator.requireNonNull(ta, "traverse");
FunctionValidator.requireMapper(f, "traverse");
FunctionValidator.requireApplicative(applicative, "traverse");
```

### Error Type Validation Strategy



| Error Type | Validation Strategy        | Example                     |
|------------|----------------------------|-----------------------------|
| Throwable  | Always validate non-null   | CompletableFutureMonad      |
| Unit       | Never validate (singleton) | MaybeMonad, MaybeTMonad     |
| Domain types (L/E) | Context-dependent  | Either, EitherT allow null  |

Key Points

- Use static `TYPE` constants for class references
- Holder validation uses `KindValidator.requireForWiden()`
- Narrowing uses `KindValidator.narrow()` with extraction or `narrowWithTypeCheck()`
- Descriptors in parentheses clarify multi-parameter operations
- Transformer classes use DomainValidator for outer monad validation
- Core types validate their own operations with qualified names