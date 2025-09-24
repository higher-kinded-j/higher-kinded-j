## Best Practices

### 1. Always Use TypeClassTestBase

Extend `TypeClassTestBase` for consistent fixture management:



```java
class MyTest extends TypeClassTestBase<F, A, B> {
    // Automatic fixture setup
    // Validation helpers available
    // Standard naming conventions enforced
}
```

### 2. Validate Test Structure

Always include structure validation:


```java
@Test
@DisplayName("Validate test structure follows standards")
void validateTestStructure() {
    TestPatternValidator.ValidationResult result =
        TestPatternValidator.validateAndReport(MyTest.class);

    if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
    }
}
```

### 3. Use Inheritance-Aware Validation

For type classes with inheritance, always use `FlexibleValidationConfig`:


```java
FlexibleValidationConfig.MonadValidation<F, A, B> validationConfig =
    new FlexibleValidationConfig.MonadValidation<>(...)
        .mapWithClassContext(ParentFunctor.class)
        .flatMapWithClassContext(ThisMonad.class)
        .test();
```

### 4. Distinguish Type Class vs Concrete Type Tests

**Type Class Tests:**

java

```java
// Methods operate on Kind<F, A>
// Validation includes type class name
ValidationTestBuilder.create()
    .assertMapperNull(() -> monad.map(null, validKind), MyMonad.class, Operation.MAP)
    .execute();
```

**Concrete Type Tests:**


```java
// Methods operate directly on the type
// Validation includes concrete type name
ValidationTestBuilder.create()
    .assertMapperNull(() -> either.map(null), Either.class, Operation.MAP)
    .execute();
```

### 5. Test All Components

Always test four aspects:

1. **Operations** - Basic functionality works
2. **Validations** - Null parameters are caught
3. **Exception Propagation** - Exceptions flow correctly
4. **Laws** - Algebraic laws are satisfied

### 6. Use Descriptive Test Names

Follow the pattern:


```java
@DisplayName("[Method]() [action] [condition]")
void [method][Action][Condition]() { }
```

Example:


```java
@DisplayName("map() applies function to Right values")
void mapAppliesFunctionToRight() { }
```

### 7. Guard Performance Tests

Always guard expensive tests:


```java
@Test
@DisplayName("Performance test")
void performanceTest() {
    if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        // Expensive test code
    }
}
```

Run with: `mvn test -Dtest.performance=true`

### 8. Use Test Data Utilities

Leverage the test data utilities:


```java
// Standard test functions
TestFunctions.INT_TO_STRING
TestFunctions.MULTIPLY_BY_2
TestFunctions.throwingFunction(exception)

// Standard test data
TestData.TEST_INT
TestData.generateIntegerList(1, 100)

// Standard test exceptions
TestExceptions.runtime("test scenario")
```

---

## Common Patterns Quick Reference

### Validation Pattern Matrix

<pre class="font-ui border-border-100/50 overflow-x-scroll w-full rounded border-[0.5px] shadow-[0_2px_12px_hsl(var(--always-black)/5%)]"><table class="bg-bg-100 min-w-full border-separate border-spacing-0 text-sm leading-[1.88888] whitespace-normal"><thead class="border-b-border-100/50 border-b-[0.5px] text-left"><tr class="[tbody>&]:odd:bg-bg-500/10"><th class="text-text-000 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] px-2 [&:not(:first-child)]:border-l-[0.5px]">Context</th><th class="text-text-000 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] px-2 [&:not(:first-child)]:border-l-[0.5px]">Type</th><th class="text-text-000 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] px-2 [&:not(:first-child)]:border-l-[0.5px]">Assertion Pattern</th></tr></thead><tbody><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Type Class</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Mapper</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">.assertMapperNull(executable, TypeClass.class, Operation.MAP)</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Type Class</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">FlatMapper</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">.assertFlatMapperNull(executable, TypeClass.class, Operation.FLAT_MAP)</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Type Class</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Kind</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">.assertKindNull(executable, TypeClass.class, Operation.MAP)</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Concrete Type</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Mapper</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">.assertMapperNull(executable, ConcreteType.class, Operation.MAP)</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Concrete Type</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Function</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">.assertFunctionNull(executable, "name", ConcreteType.class, Operation.OP)</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Inheritance</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Parent Method</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">.mapWithClassContext(ParentClass.class)</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Inheritance</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">This Method</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">.flatMapWithClassContext(ThisClass.class)</code></td></tr></tbody></table></pre>

### Test Method Naming Patterns

<pre class="font-ui border-border-100/50 overflow-x-scroll w-full rounded border-[0.5px] shadow-[0_2px_12px_hsl(var(--always-black)/5%)]"><table class="bg-bg-100 min-w-full border-separate border-spacing-0 text-sm leading-[1.88888] whitespace-normal"><thead class="border-b-border-100/50 border-b-[0.5px] text-left"><tr class="[tbody>&]:odd:bg-bg-500/10"><th class="text-text-000 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] px-2 [&:not(:first-child)]:border-l-[0.5px]">Pattern</th><th class="text-text-000 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] px-2 [&:not(:first-child)]:border-l-[0.5px]">Example</th></tr></thead><tbody><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Operation Test</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">mapAppliesFunctionToRight()</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Validation Test</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">mapValidatesNullMapper()</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Exception Test</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">mapHandlesExceptionPropagation()</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Law Test</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">testFunctorIdentityLaw()</code></td></tr><tr class="[tbody>&]:odd:bg-bg-500/10"><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]">Edge Case</td><td class="border-t-border-100/50 [&:not(:first-child)]:-x-[hsla(var(--border-100) / 0.5)] border-t-[0.5px] px-2 [&:not(:first-child)]:border-l-[0.5px]"><code class="bg-text-200/5 border border-0.5 border-border-300 text-danger-000 whitespace-pre-wrap rounded-[0.4rem] px-1 py-px text-[0.9rem]">mapWithNullValuesInRight()</code></td></tr></tbody></table></pre>

---

## Troubleshooting

### Issue: Validation Message Mismatch

**Problem:** Test expects `"function f for MyMonad.map cannot be null"` but gets `"function f for ParentFunctor.map cannot be null"`

**Solution:** Use `FlexibleValidationConfig` with correct class contexts:

java

```java
validationConfig
    .mapWithClassContext(ParentFunctor.class)  // map is in parent
    .flatMapWithClassContext(MyMonad.class)    // flatMap is in this class
```

### Issue: Missing Nested Class

**Problem:**`TestPatternValidator` reports missing `CompleteTestSuite`

**Solution:** Ensure nested class is named exactly `CompleteTestSuite` (or matches pattern `Complete.*TestSuite`):

java

```java
@Nested
@DisplayName("Complete Test Suite")
class CompleteTestSuite {  // Must match this name
    // ...
}
```

### Issue: Test Structure Validation Fails

**Problem:** Structure validation reports missing components

**Solution:** Ensure all required nested classes exist:

* `CompleteTestSuite` (or matching pattern)
* `OperationTests` (or matching pattern)
* `IndividualComponents` (or matching pattern)

### Issue: Records Flagged as Test Classes

**Problem:** Validator warns about missing `@Nested` on record types

**Solution:** This is a known limitation. Records and enums should not need `@Nested`. The validator will be improved to skip non-test nested types.
