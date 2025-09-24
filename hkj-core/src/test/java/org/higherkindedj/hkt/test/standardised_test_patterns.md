## Test Structure Standards

### Required Nested Class Structure

All test classes should follow this standardized structure:



```java
@DisplayName("MyTypeClass Complete Test Suite")
class MyTypeClassTest extends TypeClassTestBase<F, A, B> {

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {
        @Test
        @DisplayName("Run complete [TypeClass] test pattern")
        void runComplete[TypeClass]TestPattern() { }
      
        @Test
        @DisplayName("Validate test structure follows standards")
        void validateTestStructure() { }
    }

    @Nested
    @DisplayName("Operation Tests")
    class OperationTests {
        // Specific operation tests
    }

    @Nested
    @DisplayName("Individual Components")
    class IndividualComponents {
        @Test void testOperationsOnly() { }
        @Test void testValidationsOnly() { }
        @Test void testExceptionPropagationOnly() { }
        @Test void testLawsOnly() { }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {
        // Edge case tests
    }

    @Nested
    @DisplayName("Performance Tests")  // Optional
    class PerformanceTests {
        // Performance tests (guarded by system property)
    }
}
```

### Validation Standards

The `CompleteTestSuite` must include a structure validation test:



```java
@Test
@DisplayName("Validate test structure follows standards")
void validateTestStructure() {
    TestPatternValidator.ValidationResult result =
        TestPatternValidator.validateAndReport(MyTypeClassTest.class);

    if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
    }
}
```

---

## Type Class Testing Patterns

### Functor Testing Pattern


```java
@DisplayName("MyFunctor Complete Test Suite")
class MyFunctorTest extends TypeClassTestBase<F, Integer, String> {

    private Functor<F> functor;

    @Override
    protected Kind<F, Integer> createValidKind() {
        return HELPER.widen(MyType.of(42));
    }

    @Override
    protected Function<Integer, String> createValidMapper() {
        return TestFunctions.INT_TO_STRING;
    }

    @Override
    protected BiPredicate<Kind<F, ?>, Kind<F, ?>> createEqualityChecker() {
        return (k1, k2) -> HELPER.narrow(k1).equals(HELPER.narrow(k2));
    }

    @BeforeEach
    void setUpFunctor() {
        functor = MyFunctor.instance();
        validateRequiredFixtures();
    }

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete Functor test pattern")
        void runCompleteFunctorTestPattern() {
            TypeClassTestPattern.testCompleteFunctor(
                functor,
                MyFunctor.class,
                validKind,
                validMapper,
                equalityChecker
            );
        }

        @Test
        @DisplayName("Validate test structure follows standards")
        void validateTestStructure() {
            TestPatternValidator.ValidationResult result =
                TestPatternValidator.validateAndReport(MyFunctorTest.class);

            if (result.hasErrors()) {
                result.printReport();
                throw new AssertionError("Test structure validation failed");
            }
        }
    }

    @Nested
    @DisplayName("Individual Components")
    class IndividualComponents {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTestPattern.testFunctorOperations(functor, validKind, validMapper);
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTestPattern.testFunctorValidations(
                functor, MyFunctor.class, validKind, validMapper);
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTestPattern.testFunctorExceptionPropagation(functor, validKind);
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTestPattern.testFunctorLaws(
                functor, validKind, validMapper, secondMapper, equalityChecker);
        }
    }
}
```

### Monad Testing Pattern


```java
@DisplayName("MyMonad Complete Test Suite")
class MyMonadTest extends TypeClassTestBase<F, Integer, String> {

    private Monad<F> monad;

    // Implement all required abstract methods from TypeClassTestBase

    @BeforeEach
    void setUpMonad() {
        monad = MyMonad.instance();
        validateMonadFixtures();
    }

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete Monad test pattern")
        void runCompleteMonadTestPattern() {
            TypeClassTestPattern.testCompleteMonad(
                monad,
                MyMonad.class,
                validKind,
                testValue,
                validMapper,
                validFlatMapper,
                validFunctionKind,
                testFunction,
                chainFunction,
                equalityChecker
            );
        }

        @Test
        @DisplayName("Validate test structure follows standards")
        void validateTestStructure() {
            TestPatternValidator.ValidationResult result =
                TestPatternValidator.validateAndReport(MyMonadTest.class);

            if (result.hasErrors()) {
                result.printReport();
                throw new AssertionError("Test structure validation failed");
            }
        }
    }

    @Nested
    @DisplayName("Individual Components")
    class IndividualComponents {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTestPattern.testMonadOperations(
                monad, validKind, validMapper, validFlatMapper, validFunctionKind);
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTestPattern.testMonadValidations(
                monad, MyMonad.class, validKind, validMapper, validFlatMapper, validFunctionKind);
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTestPattern.testMonadExceptionPropagation(monad, validKind);
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTestPattern.testMonadLaws(
                monad, validKind, testValue, testFunction, chainFunction, equalityChecker);
        }
    }
}
```

### MonadError Testing Pattern with Inheritance-Aware Validation


```java
@DisplayName("MyMonadError Complete Test Suite")
class MyMonadErrorTest extends TypeClassTestBase<F, Integer, String> {

    private MonadError<F, E> monadError;
    private Function<E, Kind<F, Integer>> validHandler;
    private Kind<F, Integer> validFallback;

    // Implement all required abstract methods from TypeClassTestBase

    @BeforeEach
    void setUpMonadError() {
        monadError = MyMonadError.instance();
        validHandler = err -> monadError.of(-1);
        validFallback = monadError.of(-999);
        validateMonadFixtures();
    }

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete MonadError test pattern")
        void runCompleteMonadErrorTestPattern() {
            // Create validation config with inheritance-aware validation
            FlexibleValidationConfig.MonadErrorValidation<F, E, Integer, String>
                validationConfig =
                    new FlexibleValidationConfig.MonadErrorValidation<>(
                            monadError,
                            validKind,
                            validKind2,
                            validMapper,
                            validFunctionKind,
                            validCombiningFunction,
                            validFlatMapper,
                            validHandler,
                            validFallback)
                        .mapWithClassContext(MyFunctor.class)      // If map is in parent
                        .apWithClassContext(MyMonad.class)         // If ap is in MyMonad
                        .map2WithoutClassContext()                  // If map2 has no class context
                        .flatMapWithClassContext(MyMonad.class)    // If flatMap is in MyMonad
                        .handleErrorWithClassContext(MyMonadError.class); // handleErrorWith in this class

            // Test operations
            TypeClassTestPattern.testMonadErrorOperations(
                monadError,
                validKind,
                validMapper,
                validFlatMapper,
                validFunctionKind,
                validHandler,
                validFallback);

            // Test validations using the configured validation expectations
            validationConfig.test();

            // Test exception propagation
            TypeClassTestPattern.testMonadErrorExceptionPropagation(monadError, validKind);

            // Test laws
            TypeClassTestPattern.testMonadLaws(
                monadError, validKind, testValue, testFunction, chainFunction, equalityChecker);
        }

        @Test
        @DisplayName("Validate test structure follows standards")
        void validateTestStructure() {
            TestPatternValidator.ValidationResult result =
                TestPatternValidator.validateAndReport(MyMonadErrorTest.class);

            if (result.hasErrors()) {
                result.printReport();
                throw new AssertionError("Test structure validation failed");
            }
        }
    }

    @Nested
    @DisplayName("Individual Components")
    class IndividualComponents {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTestPattern.testMonadErrorOperations(
                monadError,
                validKind,
                validMapper,
                validFlatMapper,
                validFunctionKind,
                validHandler,
                validFallback);
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            new FlexibleValidationConfig.MonadErrorValidation<>(
                    monadError,
                    validKind,
                    validKind2,
                    validMapper,
                    validFunctionKind,
                    validCombiningFunction,
                    validFlatMapper,
                    validHandler,
                    validFallback)
                .mapWithClassContext(MyFunctor.class)
                .apWithClassContext(MyMonad.class)
                .map2WithoutClassContext()
                .flatMapWithClassContext(MyMonad.class)
                .handleErrorWithClassContext(MyMonadError.class)
                .test();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTestPattern.testMonadErrorExceptionPropagation(monadError, validKind);
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTestPattern.testMonadLaws(
                monadError, validKind, testValue, testFunction, chainFunction, equalityChecker);
        }
    }
}
```

---

## Concrete Type Testing Patterns

### Testing Concrete Types (Either, Maybe, etc.)

Concrete types require different validation patterns than type class implementations:


```java
@DisplayName("Either<L, R> Core Functionality - Standardized Test Suite")
class EitherTest {

    private final String leftValue = "Error Message";
    private final Integer rightValue = 123;
    private final Either<String, Integer> leftInstance = Either.left(leftValue);
    private final Either<String, Integer> rightInstance = Either.right(rightValue);

    @Nested
    @DisplayName("map() Method - Comprehensive Right-Biased Testing")
    class MapMethodTests {

        @Test
        @DisplayName("map() applies function to Right values")
        void mapAppliesFunctionToRight() {
            Either<String, String> result = rightInstance.map(TestFunctions.INT_TO_STRING);
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isEqualTo("123");
        }

        @Test
        @DisplayName("map() preserves Left instances unchanged")
        void mapPreservesLeftInstances() {
            Either<String, String> result = leftInstance.map(TestFunctions.INT_TO_STRING);
            assertThat(result).isSameAs(leftInstance);
            assertThat(result.isLeft()).isTrue();
        }

        @Test
        @DisplayName("map() validates null mapper using standardized validation")
        void mapValidatesNullMapper() {
            // For concrete types, include the class context
            ValidationTestBuilder.create()
                .assertMapperNull(() -> rightInstance.map(null), Either.class, Operation.MAP)
                .assertMapperNull(() -> leftInstance.map(null), Either.class, Operation.MAP)
                .execute();
        }
    }

    @Nested
    @DisplayName("flatMap() Method - Comprehensive Monadic Testing")
    class FlatMapMethodTests {

        @Test
        @DisplayName("flatMap() applies function to Right values")
        void flatMapAppliesFunctionToRight() {
            Function<Integer, Either<String, String>> mapper = 
                i -> Either.right("Value: " + i);

            Either<String, String> result = rightInstance.flatMap(mapper);
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isEqualTo("Value: 123");
        }

        @Test
        @DisplayName("flatMap() validates parameters using standardized validation")
        void flatMapValidatesParameters() {
            ValidationTestBuilder.create()
                .assertFlatMapperNull(() -> rightInstance.flatMap(null), Either.class, Operation.FLAT_MAP)
                .assertFlatMapperNull(() -> leftInstance.flatMap(null), Either.class, Operation.FLAT_MAP)
                .execute();
        }
    }

    @Nested
    @DisplayName("fold() Method - Complete Validation and Edge Cases")
    class FoldMethodTests {

        private final Function<String, String> leftMapper = l -> "Left mapped: " + l;
        private final Function<Integer, String> rightMapper = r -> "Right mapped: " + r;

        @Test
        @DisplayName("fold() applies correct mapper based on Either type")
        void foldAppliesCorrectMapper() {
            String leftResult = leftInstance.fold(leftMapper, rightMapper);
            assertThat(leftResult).isEqualTo("Left mapped: " + leftValue);

            String rightResult = rightInstance.fold(leftMapper, rightMapper);
            assertThat(rightResult).isEqualTo("Right mapped: " + rightValue);
        }

        @Test
        @DisplayName("fold() validates null mappers using standardized validation")
        void foldValidatesNullMappers() {
            ValidationTestBuilder.create()
                .assertFunctionNull(() -> leftInstance.fold(null, rightMapper), "leftMapper", Either.class, Operation.FOLD)
                .assertFunctionNull(() -> rightInstance.fold(leftMapper, null), "rightMapper", Either.class, Operation.FOLD)
                .execute();
        }
    }

    @Nested
    @DisplayName("Side Effect Methods - Complete ifLeft/ifRight Testing")
    class SideEffectMethodsTests {

        @Test
        @DisplayName("ifLeft() executes action on Left instances")
        void ifLeftExecutesOnLeft() {
            AtomicBoolean executed = new AtomicBoolean(false);
            leftInstance.ifLeft(s -> executed.set(true));
            assertThat(executed).isTrue();
        }

        @Test
        @DisplayName("Side effect methods validate null actions")
        void sideEffectMethodsValidateNullActions() {
            ValidationTestBuilder.create()
                .assertFunctionNull(() -> leftInstance.ifLeft(null), "action", Either.class, Operation.IF_LEFT)
                .assertFunctionNull(() -> rightInstance.ifLeft(null), "action", Either.class, Operation.IF_LEFT)
                .assertFunctionNull(() -> rightInstance.ifRight(null), "action", Either.class, Operation.IF_RIGHT)
                .assertFunctionNull(() -> leftInstance.ifRight(null), "action", Either.class, Operation.IF_RIGHT)
                .execute();
        }
    }
}
```

**Key Difference**: Concrete types always use the class context in validation:


```java
// Concrete type (Either)
ValidationTestBuilder.create()
    .assertMapperNull(() -> either.map(null), Either.class, Operation.MAP)
    .execute();

// Type class (EitherMonad)
ValidationTestBuilder.create()
    .assertMapperNull(() -> monad.map(null, validKind), EitherFunctor.class, Operation.MAP)
    .execute();
```

---

## Validation Framework

### Using ValidationTestBuilder

The `ValidationTestBuilder` provides a fluent API for testing multiple validation conditions:


```java
@Test
void testValidations() {
    ValidationTestBuilder.create()
        .assertMapperNull(() -> functor.map(null, validKind), MyFunctor.class, Operation.MAP)
        .assertKindNull(() -> functor.map(validMapper, null), MyFunctor.class, Operation.MAP)
        .assertFlatMapperNull(() -> monad.flatMap(null, validKind), MyMonad.class, Operation.FLAT_MAP)
        .assertApplicativeNull(() -> traverse.traverse(null, validFunc, validKind), MyTraverse.class, Operation.TRAVERSE)
        .execute();
}
```

### Validation Assertions

#### Function Validation


```java
// Mapper function (for map operations)
.assertMapperNull(executable, contextClass, operation)

// FlatMapper function (for flatMap operations)
.assertFlatMapperNull(executable, contextClass, operation)

// Applicative validation
.assertApplicativeNull(executable, contextClass, operation)

// Monoid validation
.assertMonoidNull(executable, contextClass, operation)

// Custom function validation
.assertFunctionNull(executable, functionName, contextClass, operation)
```

#### Kind Validation


```java
// Basic Kind null validation
.assertKindNull(executable, contextClass, operation)

// Kind with descriptor (for multi-parameter operations)
.assertKindNull(executable, contextClass, operation, "descriptor")

// Narrow/Widen validation
.assertNarrowNull(executable, targetClass)
.assertWidenNull(executable, inputClass)
.assertInvalidKindType(executable, targetClass, invalidKind)
```

---

## Inheritance-Aware Validation

### Understanding Validation Context

When type classes use inheritance, different methods may have different validation contexts:


```java
// EitherMonad extends EitherFunctor
public class EitherMonad<L> extends EitherFunctor<L> implements MonadError<...> {
    // map() is inherited from EitherFunctor - validates with EitherFunctor.class
    // flatMap() is defined here - validates with EitherMonad.class
    // ap() is defined here - validates with EitherMonad.class
}
```

### FlexibleValidationConfig

Use `FlexibleValidationConfig` to specify which class context each operation uses:


```java
FlexibleValidationConfig.MonadErrorValidation<F, E, A, B> validationConfig =
    new FlexibleValidationConfig.MonadErrorValidation<>(
            monadError,
            validKind,
            validKind2,
            validMapper,
            validFunctionKind,
            validCombiningFunction,
            validFlatMapper,
            validHandler,
            validFallback)
        .mapWithClassContext(EitherFunctor.class)      // map() validates with parent class
        .apWithClassContext(EitherMonad.class)         // ap() validates with this class
        .map2WithoutClassContext()                      // map2() has no class in message
        .flatMapWithClassContext(EitherMonad.class)    // flatMap() validates with this class
        .handleErrorWithClassContext(EitherMonad.class); // handleErrorWith() validates with this class

validationConfig.test(); // Execute all configured validations
```

### Configuration Methods

#### For Applicative


```java
ApplicativeValidation<F, A, B> config = new ApplicativeValidation<>(...)
    .mapWithClassContext(MyFunctor.class)
    .mapWithoutClassContext()
    .apWithClassContext(MyApplicative.class)
    .map2WithClassContext(MyApplicative.class)
    .map2WithoutClassContext();
```

#### For Monad


```java
MonadValidation<F, A, B> config = new MonadValidation<>(...)
    .mapWithClassContext(MyFunctor.class)
    .apWithClassContext(MyMonad.class)
    .flatMapWithClassContext(MyMonad.class)
    .flatMapWithoutClassContext();
```

#### For MonadError


```java
MonadErrorValidation<F, E, A, B> config = new MonadErrorValidation<>(...)
    .mapWithClassContext(MyFunctor.class)
    .apWithClassContext(MyMonad.class)
    .flatMapWithClassContext(MyMonad.class)
    .handleErrorWithClassContext(MyMonadError.class);
```

---

## Test Base Classes

### TypeClassTestBase

The `TypeClassTestBase` provides standardized fixture setup:


```java
public abstract class MyTypeClassTest extends TypeClassTestBase<F, A, B> {

    // Required implementations
    @Override
    protected Kind<F, A> createValidKind() {
        return HELPER.widen(MyType.of(42));
    }

    @Override
    protected Kind<F, A> createValidKind2() {
        return HELPER.widen(MyType.of(24));
    }

    @Override
    protected Function<A, B> createValidMapper() {
        return TestFunctions.INT_TO_STRING;
    }

    @Override
    protected BiPredicate<Kind<F, ?>, Kind<F, ?>> createEqualityChecker() {
        return (k1, k2) -> HELPER.narrow(k1).equals(HELPER.narrow(k2));
    }

    // Optional overrides
    @Override
    protected Function<B, String> createSecondMapper() {
        return Object::toString;
    }

    @Override
    protected Function<A, Kind<F, B>> createValidFlatMapper() {
        return a -> HELPER.widen(MyType.of(validMapper.apply(a)));
    }

    @Override
    protected Kind<F, Function<A, B>> createValidFunctionKind() {
        return HELPER.widen(MyType.of(validMapper));
    }

    @Override
    protected BiFunction<A, A, B> createValidCombiningFunction() {
        return (a1, a2) -> validMapper.apply(a1);
    }

    @Override
    protected A createTestValue() {
        return /* test value */;
    }

    @Override
    protected Function<A, Kind<F, B>> createTestFunction() {
        return a -> HELPER.widen(MyType.of(validMapper.apply(a)));
    }

    @Override
    protected Function<B, Kind<F, B>> createChainFunction() {
        return b -> HELPER.widen(MyType.of(b));
    }
}
```

### Fixture Validation

Use the validation methods to ensure required fixtures are set:


```java
@BeforeEach
void setUp() {
    // Automatically called by TypeClassTestBase
    // validKind, validKind2, validMapper, equalityChecker are initialized
  
    myTypeClass = MyTypeClass.instance();
  
    // Validate required fixtures
    validateRequiredFixtures();  // For Functor tests
    validateMonadFixtures();     // For Monad tests
    validateApplicativeFixtures(); // For Applicative tests
}
```

---

## Builder Patterns

### Using Builder Pattern for Complex Tests

For complex test configurations, use the builder pattern:


```java
@Test
void testWithBuilder() {
    TypeClassTestPattern.functorTest(functor, MyFunctor.class)
        .withKind(validKind)
        .withMapper(validMapper)
        .withSecondMapper(secondMapper)
        .withEqualityChecker(equalityChecker)
        .skipExceptionPropagation() // Optional: skip specific tests
        .test();
}
```

### Available Builders

#### FunctorTestBuilder


```java
TypeClassTestPattern.functorTest(functor, MyFunctor.class)
    .withKind(validKind)
    .withMapper(validMapper)
    .withSecondMapper(secondMapper)
    .withEqualityChecker(equalityChecker)
    .skipOperations()
    .skipValidations()
    .skipExceptionPropagation()
    .skipLaws()
    .test();
```

#### MonadErrorTestBuilder


```java
TypeClassTestPattern.monadErrorTest(monadError, MyMonadError.class)
    .withKind(validKind)
    .withKind2(validKind2)
    .withTestValue(testValue)
    .withMapper(validMapper)
    .withFlatMapper(validFlatMapper)
    .withFunctionKind(validFunctionKind)
    .withCombiningFunction(validCombiningFunction)
    .withHandler(validHandler)
    .withFallback(validFallback)
    .withTestFunction(testFunction)
    .withChainFunction(chainFunction)
    .withEqualityChecker(equalityChecker)
    .withValidationConfig(validationConfig)
    .skipOperations()
    .skipValidations()
    .skipExceptionPropagation()
    .skipLaws()
    .test();
```
