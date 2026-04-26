# Advanced Prism Patterns: Recipes

## _Optimisation and testing recipes_

~~~admonish info title="What You'll Learn"
- Caching strategies for expensive prism compositions in long-running applications.
- Bulk-processing helpers for extracting and partitioning items by prism match.
- Test patterns for prism matching, composed prisms, and conditional operations.
~~~

This page collects copy-paste-ready recipes for the production concerns that come up once you have prisms running in real systems. The narrative explanations live in [Advanced Prism Patterns](advanced_prism_patterns.md); these are the lookup snippets you return to when you need them.

---

## Performance Optimisation Patterns

### Caching Composed Prisms

```java
public class OptimisedPrismCache {
    // Cache expensive optic compositions
    private static final Map<String, Object> OPTIC_CACHE =
        new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T getCached(
        String key,
        Supplier<T> factory
    ) {
        return (T) OPTIC_CACHE.computeIfAbsent(key, k -> factory.get());
    }

    // Example usage: caching a composed traversal
    private static final Traversal<Config, String> DATABASE_HOST =
        getCached("config.database.host", () ->
            ConfigLenses.database()
                .asTraversal()
                .andThen(Prisms.some().asTraversal())
                .andThen(Prisms.right().asTraversal())
                .andThen(DatabaseSettingsLenses.host().asTraversal())
        );
}
```

### Bulk Operations with Prisms

```java
public class BulkProcessor {
    // Process multiple items efficiently
    public static <S, A> List<A> extractAll(
        Prism<S, A> prism,
        List<S> items
    ) {
        return items.stream()
            .flatMap(item -> prism.getOptional(item).stream())
            .collect(Collectors.toList());
    }

    // Partition items by prism match
    public static <S, A> Map<Boolean, List<S>> partitionByMatch(
        Prism<S, A> prism,
        List<S> items
    ) {
        return items.stream()
            .collect(Collectors.partitioningBy(prism::matches));
    }
}
```

---

## Testing Strategies

### Testing Prism-Based Logic

```java
public class PrismTestPatterns {
    @Test
    void testPrismMatching() {
        Prism<ApiResponse, Success> success = ApiResponsePrisms.success();

        ApiResponse successResponse = new Success(jsonData, 200);
        ApiResponse errorResponse = new ServerError("Error", "trace123");

        // Verify matching behaviour
        assertTrue(success.matches(successResponse));
        assertFalse(success.matches(errorResponse));

        // Verify extraction
        assertThat(success.getOptional(successResponse))
            .isPresent()
            .get()
            .extracting(Success::statusCode)
            .isEqualTo(200);
    }

    @Test
    void testComposedPrisms() {
        // Test deep prism compositions
        Prism<Config, String> hostPrism = buildHostPrism();

        Config validConfig = createValidConfig();
        Config invalidConfig = createInvalidConfig();

        assertThat(hostPrism.getOptional(validConfig)).isPresent();
        assertThat(hostPrism.getOptional(invalidConfig)).isEmpty();
    }

    @Test
    void testConditionalOperations() {
        Prism<ConfigValue, IntValue> intPrism = ConfigValuePrisms.intValue();

        ConfigValue value = new IntValue(42);

        // Test modifyWhen
        ConfigValue result = intPrism.modifyWhen(
            i -> i.value() > 0,
            i -> new IntValue(i.value() * 2),
            value
        );

        assertThat(intPrism.getOptional(result))
            .isPresent()
            .get()
            .extracting(IntValue::value)
            .isEqualTo(84);
    }
}
```

---

~~~admonish info title="Hands-On Learning"
Practice advanced prism patterns in [Tutorial 10: Advanced Prism Patterns](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial10_AdvancedPrismPatterns.java) (8 exercises, ~12 minutes).
~~~

~~~admonish tip title="Further Reading"
- **Monocle**: [Scala Optics Library](https://www.optics.dev/Monocle/) - Production-ready Scala optics with extensive patterns
- **Haskell Lens**: [Canonical Reference](https://hackage.haskell.org/package/lens) - The original comprehensive optics library
- **Lens Tutorial**: [A Little Lens Starter Tutorial](https://www.schoolofhaskell.com/school/to-infinity-and-beyond/pick-of-the-week/a-little-lens-starter-tutorial) - Beginner-friendly introduction
~~~

---

**Previous:** [Advanced Prism Patterns](advanced_prism_patterns.md)
**Next:** [Profunctor Optics](profunctor_optics.md)
