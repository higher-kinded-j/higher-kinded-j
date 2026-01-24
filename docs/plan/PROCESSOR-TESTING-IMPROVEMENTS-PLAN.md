# Processor Testing Improvements Plan

## Overview

This document outlines a comprehensive plan to improve confidence in the correctness of `hkj-processor`, focusing on the `@ImportOptics` annotation processor and related optics generation code.

## Current State Assessment

### Existing Test Coverage

| Test Type | Location | Coverage |
|-----------|----------|----------|
| Compile Testing | `ImportOpticsProcessorTest.java` | Records, sealed interfaces, enums, wither classes, error cases |
| Unit Tests | `TypeKindAnalyserTest.java` | Type analysis, container detection, wither detection |
| Architecture Rules | `ProcessorArchitectureRules.java` | Processor field immutability |
| Integration Tests | `hkj-examples/importoptics/` | Basic usage, composition, real-world scenarios |

### Identified Gaps

1. **No optic law verification** - Generated optics are not tested against Lens/Prism laws
2. **No property-based testing** - Random input testing is absent
3. **Limited edge case coverage** - Nested generics, unusual names, empty types
4. **No golden file testing** - No detection of unintended generation changes
5. **No runtime verification** - Generated code not executed in tests
6. **Limited error message testing** - Error quality not systematically verified
7. **No mutation testing** - Test quality not measured

---

## Phase 1: Optic Law Verification

### Objective
Verify that generated optics satisfy their mathematical laws.

### Background Knowledge
- **Lens Laws**:
  - Get-Put: `set(get(s), s) == s`
  - Put-Get: `get(set(a, s)) == a`
  - Put-Put: `set(b, set(a, s)) == set(b, s)`
- **Prism Laws**:
  - Review-Preview: `getOptional(build(a)) == Optional.of(a)`
  - Preview-Review: `getOptional(s).map(this::build).orElse(s) == s`

### Implementation

#### 1.1 Create Law Verification Test Infrastructure

Create a new test class that compiles generated code and verifies laws:

```java
// File: hkj-processor/src/test/java/org/higherkindedj/optics/processing/GeneratedOpticLawsTest.java

@DisplayName("Generated Optics Law Verification")
class GeneratedOpticLawsTest {

    @Nested
    @DisplayName("Generated Lens Laws")
    class GeneratedLensLaws {

        @Test
        @DisplayName("Record lenses satisfy Get-Put law")
        void recordLensesSatisfyGetPut() {
            // 1. Compile a test record with @ImportOptics
            // 2. Load generated CustomerLenses class
            // 3. For each lens, verify: set(get(s), s) == s
        }

        @Test
        @DisplayName("Record lenses satisfy Put-Get law")
        void recordLensesSatisfyPutGet() {
            // Verify: get(set(a, s)) == a
        }

        @Test
        @DisplayName("Record lenses satisfy Put-Put law")
        void recordLensesSatisfyPutPut() {
            // Verify: set(b, set(a, s)) == set(b, s)
        }
    }

    @Nested
    @DisplayName("Generated Prism Laws")
    class GeneratedPrismLaws {

        @Test
        @DisplayName("Sealed interface prisms satisfy Review-Preview law")
        void sealedPrismsSatisfyReviewPreview() {
            // Verify: getOptional(build(a)) == Optional.of(a)
        }

        @Test
        @DisplayName("Enum prisms satisfy Preview-Review law")
        void enumPrismsSatisfyPreviewReview() {
            // Verify: getOptional(s).map(this::build).orElse(s) == s
        }
    }
}
```

#### 1.2 Dynamic Class Loading for Generated Code

Create a helper to compile and load generated classes at runtime:

```java
// File: hkj-processor/src/test/java/org/higherkindedj/optics/processing/RuntimeCompilationHelper.java

public class RuntimeCompilationHelper {

    /**
     * Compiles sources with the processor and returns a ClassLoader
     * that can load the generated classes.
     */
    public static CompiledResult compile(JavaFileObject... sources) {
        Compilation compilation = javac()
            .withProcessors(new ImportOpticsProcessor())
            .compile(sources);

        assertThat(compilation).succeeded();

        return new CompiledResult(compilation, createClassLoader(compilation));
    }

    public record CompiledResult(
        Compilation compilation,
        ClassLoader classLoader
    ) {
        public <T> T loadAndInstantiate(String className, Class<T> type) {
            // Load class and create instance
        }

        public Object invokeStatic(String className, String methodName) {
            // Invoke static method (for lens/prism factory methods)
        }
    }
}
```

#### 1.3 Composed Optics Law Verification

Generated lenses and prisms should also satisfy laws when composed:

```java
@Nested
@DisplayName("Composed Optics Laws")
class ComposedOpticsLaws {

    @Test
    @DisplayName("Composed lenses satisfy all lens laws")
    void composedLensesSatisfyLaws() {
        // Given: Two lenses that can be composed
        // User -> Address, Address -> Street
        Lens<User, Address> userAddressLens = UserLenses.address();
        Lens<Address, String> addressStreetLens = AddressLenses.street();

        Lens<User, String> composed = userAddressLens.andThen(addressStreetLens);

        User testUser = new User("Alice", new Address("Main St"));

        // Get-Put law
        String currentStreet = composed.get(testUser);
        assertThat(composed.set(currentStreet, testUser)).isEqualTo(testUser);

        // Put-Get law
        String newStreet = "Oak Ave";
        assertThat(composed.get(composed.set(newStreet, testUser))).isEqualTo(newStreet);

        // Put-Put law
        String street1 = "First St";
        String street2 = "Second St";
        assertThat(composed.set(street2, composed.set(street1, testUser)))
            .isEqualTo(composed.set(street2, testUser));
    }

    @Test
    @DisplayName("Lens-Prism composition satisfies optional laws")
    void lensPrismCompositionSatisfiesLaws() {
        // Given: A lens and a prism that can be composed
        // User -> Result, Result -> Success (prism)
        Lens<User, Result> userResultLens = UserLenses.result();
        Prism<Result, Success> successPrism = ResultPrisms.success();

        // Composed as Optional (lens.andThen(prism) yields Optional behavior)
        // Verify getOptional/set semantics
    }
}
```

### Acceptance Criteria
- [ ] All generated lenses pass Get-Put, Put-Get, Put-Put laws
- [ ] All generated prisms pass Review-Preview, Preview-Review laws
- [ ] Composed lenses satisfy all lens laws
- [ ] Lens-Prism compositions satisfy optional access laws
- [ ] Tests cover records, sealed interfaces, enums, and wither classes
- [ ] Tests cover generic types with type parameters

### Estimated Effort
- Implementation: 3-4 days (increased for composed optics)
- Review and refinement: 1 day

---

## Phase 2: Property-Based Testing

### Objective
Use property-based testing to verify generated optics work correctly for arbitrary inputs.

### Dependencies
- Add `net.jqwik:jqwik` to test dependencies

### Implementation

#### 2.1 Add jqwik Dependency

```kotlin
// build.gradle.kts (hkj-processor)
dependencies {
    testImplementation("net.jqwik:jqwik:1.8.2")
}
```

#### 2.2 Property Tests for Generated Lenses

```java
// File: hkj-processor/src/test/java/org/higherkindedj/optics/processing/GeneratedLensPropertyTest.java

@DisplayName("Generated Lens Property Tests")
class GeneratedLensPropertyTest {

    // Pre-compiled test types and their generated lenses
    private static Lens<TestRecord, String> nameLens;
    private static Lens<TestRecord, Integer> ageLens;

    @BeforeAll
    static void compileAndLoad() {
        // Compile test record and load generated lenses
    }

    @Property
    void lensGetPutProperty(@ForAll String name, @ForAll @IntRange(min = 0, max = 150) int age) {
        TestRecord record = new TestRecord(name, age);

        // Get-Put: Setting what you get doesn't change anything
        assertThat(nameLens.set(nameLens.get(record), record)).isEqualTo(record);
        assertThat(ageLens.set(ageLens.get(record), record)).isEqualTo(record);
    }

    @Property
    void lensPutGetProperty(
            @ForAll String originalName,
            @ForAll String newName,
            @ForAll @IntRange(min = 0, max = 150) int age) {
        TestRecord record = new TestRecord(originalName, age);

        // Put-Get: Getting what you set returns what you set
        assertThat(nameLens.get(nameLens.set(newName, record))).isEqualTo(newName);
    }

    @Property
    void lensPutPutProperty(
            @ForAll String name,
            @ForAll String value1,
            @ForAll String value2,
            @ForAll @IntRange(min = 0, max = 150) int age) {
        TestRecord record = new TestRecord(name, age);

        // Put-Put: Second set wins
        TestRecord doubleSet = nameLens.set(value2, nameLens.set(value1, record));
        TestRecord singleSet = nameLens.set(value2, record);
        assertThat(doubleSet).isEqualTo(singleSet);
    }
}
```

#### 2.3 Property Tests for Generated Prisms

```java
// File: hkj-processor/src/test/java/org/higherkindedj/optics/processing/GeneratedPrismPropertyTest.java

@DisplayName("Generated Prism Property Tests")
class GeneratedPrismPropertyTest {

    @Property
    void prismReviewPreviewProperty(@ForAll @From("circles") Circle circle) {
        Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

        // Review-Preview: getOptional(build(a)) == Optional.of(a)
        assertThat(circlePrism.getOptional(circlePrism.build(circle)))
            .isEqualTo(Optional.of(circle));
    }

    @Property
    void prismPreviewReviewProperty(@ForAll @From("shapes") Shape shape) {
        Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

        // Preview-Review: getOptional(s).map(build).orElse(s) == s
        Shape result = circlePrism.getOptional(shape)
            .map(circlePrism::build)
            .orElse(shape);
        assertThat(result).isEqualTo(shape);
    }

    @Provide
    Arbitrary<Circle> circles() {
        return Arbitraries.doubles().between(0.1, 1000.0).map(Circle::new);
    }

    @Provide
    Arbitrary<Shape> shapes() {
        return Arbitraries.oneOf(
            circles().map(c -> (Shape) c),
            Arbitraries.doubles().between(0.1, 1000.0).map(Rectangle::new)
        );
    }
}
```

### Acceptance Criteria
- [ ] Property tests for all lens laws with random inputs
- [ ] Property tests for all prism laws with random inputs
- [ ] Edge cases: empty strings, boundary integers, null-safe handling
- [ ] At least 1000 test cases per property

### Estimated Effort
- Implementation: 2 days
- Review and refinement: 1 day

---

## Phase 3: Edge Case Testing

### Objective
Systematically test edge cases that might cause generation failures or incorrect code.

### Implementation

#### 3.1 Nested Generics

```java
@Test
@DisplayName("should handle deeply nested generic types")
void shouldHandleDeeplyNestedGenerics() {
    var record = JavaFileObjects.forSourceString("com.test.Nested", """
        package com.test;
        import java.util.*;
        public record Nested(
            List<Optional<Map<String, List<Integer>>>> deeplyNested
        ) {}
        """);

    var compilation = compile(record, packageInfo);
    assertThat(compilation).succeeded();
    // Verify generated traversal handles nesting correctly
}
```

#### 3.2 Unusual Names

```java
@Test
@DisplayName("should handle Java keywords as field names via escaping")
void shouldHandleKeywordFieldNames() {
    var record = JavaFileObjects.forSourceString("com.test.Keywords", """
        package com.test;
        public record Keywords(
            String class_,  // escaped keyword
            int default_,
            boolean static_
        ) {}
        """);

    var compilation = compile(record, packageInfo);
    assertThat(compilation).succeeded();
}

@Test
@DisplayName("should handle unicode field names")
void shouldHandleUnicodeFieldNames() {
    var record = JavaFileObjects.forSourceString("com.test.Unicode", """
        package com.test;
        public record Unicode(
            String 名前,
            int 年齢
        ) {}
        """);

    var compilation = compile(record, packageInfo);
    assertThat(compilation).succeeded();
}
```

#### 3.3 Empty and Minimal Types

```java
@Test
@DisplayName("should handle empty record")
void shouldHandleEmptyRecord() {
    var record = JavaFileObjects.forSourceString("com.test.Empty", """
        package com.test;
        public record Empty() {}
        """);

    var compilation = compile(record, packageInfo);
    assertThat(compilation).succeeded();
    // Generated class should have no lens methods
}

@Test
@DisplayName("should handle single-constant enum")
void shouldHandleSingleConstantEnum() {
    var enumType = JavaFileObjects.forSourceString("com.test.Single", """
        package com.test;
        public enum Single { ONLY }
        """);

    var compilation = compile(enumType, packageInfo);
    assertThat(compilation).succeeded();
}
```

#### 3.4 Recursive Types

```java
@Test
@DisplayName("should handle recursive record types")
void shouldHandleRecursiveRecords() {
    var recursive = JavaFileObjects.forSourceString("com.test.Node", """
        package com.test;
        public record Node<T>(T value, Node<T> next) {}
        """);

    var compilation = compile(recursive, packageInfo);
    assertThat(compilation).succeeded();
    // Verify generated lens handles recursive type correctly
    assertThat(compilation).generatedSourceFile("com.test.NodeLenses")
        .contentsAsUtf8String()
        .contains("Lens<Node<T>, Node<T>> next()");
}

@Test
@DisplayName("should handle self-referential sealed interfaces")
void shouldHandleSelfReferentialSealedTypes() {
    var expr = JavaFileObjects.forSourceString("com.test.Expr", """
        package com.test;
        public sealed interface Expr permits Lit, Add {}
        """);

    var lit = JavaFileObjects.forSourceString("com.test.Lit", """
        package com.test;
        public record Lit(int value) implements Expr {}
        """);

    var add = JavaFileObjects.forSourceString("com.test.Add", """
        package com.test;
        public record Add(Expr left, Expr right) implements Expr {}
        """);

    var compilation = compile(expr, lit, add, packageInfo);
    assertThat(compilation).succeeded();
    // Verify prisms for Lit and Add work correctly
    assertThat(compilation).generatedSourceFile("com.test.ExprPrisms").isNotNull();
}
```

#### 3.5 Complex Sealed Hierarchies

```java
@Test
@DisplayName("should handle sealed interface with non-record subtypes")
void shouldHandleMixedSealedSubtypes() {
    var sealed = JavaFileObjects.forSourceString("com.test.Result", """
        package com.test;
        public sealed interface Result permits Success, Failure, Pending {}
        """);

    var success = JavaFileObjects.forSourceString("com.test.Success", """
        package com.test;
        public record Success(String value) implements Result {}
        """);

    var failure = JavaFileObjects.forSourceString("com.test.Failure", """
        package com.test;
        public final class Failure implements Result {
            private final Exception error;
            public Failure(Exception error) { this.error = error; }
            public Exception getError() { return error; }
        }
        """);

    var pending = JavaFileObjects.forSourceString("com.test.Pending", """
        package com.test;
        public enum Pending implements Result { INSTANCE }
        """);

    var compilation = compile(sealed, success, failure, pending, packageInfo);
    assertThat(compilation).succeeded();
}
```

### Acceptance Criteria
- [ ] Tests for nested generics (2+ levels deep)
- [ ] Tests for escaped Java keywords as field names
- [ ] Tests for unicode identifiers
- [ ] Tests for empty records and single-value enums
- [ ] Tests for recursive record types (e.g., `Node<T>` with `Node<T> next`)
- [ ] Tests for self-referential sealed interfaces (e.g., expression trees)
- [ ] Tests for mixed sealed interface implementations
- [ ] Tests for diamond inheritance patterns

### Estimated Effort
- Implementation: 2 days (increased for recursive type handling)
- Review: 0.5 days

---

## Phase 4: Golden File Testing

### Objective
Detect unintended changes in generated code by comparing against known-good outputs.

### Implementation

#### 4.1 Golden File Structure

```
hkj-processor/src/test/resources/golden/
├── CustomerLenses.java.golden
├── ShapePrisms.java.golden
├── StatusPrisms.java.golden
├── ImmutableDateLenses.java.golden
└── GenericPairLenses.java.golden
```

#### 4.2 Golden File Test Implementation

```java
// File: hkj-processor/src/test/java/org/higherkindedj/optics/processing/GoldenFileTest.java

@DisplayName("Golden File Tests")
class GoldenFileTest {

    private static final String GOLDEN_RESOURCE_PATH = "/golden/";

    @ParameterizedTest
    @MethodSource("goldenFileTestCases")
    @DisplayName("Generated code matches golden file")
    void generatedCodeMatchesGolden(GoldenTestCase testCase) throws IOException {
        var compilation = compile(testCase.sources());
        assertThat(compilation).succeeded();

        String generated = getGeneratedSource(compilation, testCase.generatedClassName());
        String golden = readGoldenFile(testCase.goldenFileName());

        // Normalize whitespace and line endings for cross-platform comparison
        assertThat(normalizeForComparison(generated))
            .isEqualTo(normalizeForComparison(golden));
    }

    private String readGoldenFile(String fileName) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(GOLDEN_RESOURCE_PATH + fileName)) {
            if (is == null) {
                throw new IOException("Golden file not found: " + fileName);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalizeForComparison(String source) {
        // Normalize line endings and trailing whitespace
        return source.replace("\r\n", "\n")
                     .replaceAll("[ \t]+\n", "\n")
                     .trim();
    }

    static Stream<GoldenTestCase> goldenFileTestCases() {
        return Stream.of(
            new GoldenTestCase(
                "CustomerLenses",
                "CustomerLenses.java.golden",
                customerRecord(), packageInfo()
            ),
            new GoldenTestCase(
                "ShapePrisms",
                "ShapePrisms.java.golden",
                shapeSealed(), circleRecord(), rectangleRecord(), packageInfo()
            )
            // ... more cases
        );
    }

    record GoldenTestCase(
        String generatedClassName,
        String goldenFileName,
        JavaFileObject... sources
    ) {}
}
```

#### 4.3 Golden File Update Utility

```java
/**
 * Utility to regenerate golden files when intentional changes are made.
 * Run with: ./gradlew :hkj-processor:test --tests "*updateGoldenFiles*" -PupdateGolden=true
 *
 * Note: This writes to src/test/resources which requires running from the module root.
 * The test itself uses classpath resources for portability.
 */
@Test
@DisabledIfSystemProperty(named = "updateGolden", matches = "false")
void updateGoldenFiles() throws IOException {
    // Locate the source resources directory (for updating golden files)
    Path goldenDir = Path.of("src/test/resources/golden");
    if (!Files.exists(goldenDir)) {
        Files.createDirectories(goldenDir);
    }

    for (GoldenTestCase testCase : goldenFileTestCases().toList()) {
        var compilation = compile(testCase.sources());
        String generated = getGeneratedSource(compilation, testCase.generatedClassName());
        Files.writeString(
            goldenDir.resolve(testCase.goldenFileName()),
            generated,
            StandardCharsets.UTF_8
        );
        System.out.println("Updated: " + testCase.goldenFileName());
    }
}
```

### Acceptance Criteria
- [ ] Golden files for all major generation scenarios
- [ ] Automated comparison in CI
- [ ] Utility to update golden files intentionally
- [ ] Clear diff output when mismatches occur

### Estimated Effort
- Implementation: 1 day
- Creating golden files: 0.5 days

---

## Phase 5: Comprehensive Error Message Testing

### Objective
Ensure error messages are clear, actionable, and help users fix problems.

### Implementation

```java
// File: hkj-processor/src/test/java/org/higherkindedj/optics/processing/ErrorMessageTest.java

@DisplayName("Error Message Quality Tests")
class ErrorMessageTest {

    @Nested
    @DisplayName("Unsupported Type Errors")
    class UnsupportedTypeErrors {

        @Test
        @DisplayName("should suggest adding wither methods for plain class")
        void shouldSuggestWitherMethods() {
            var plainClass = JavaFileObjects.forSourceString("com.test.Plain", """
                package com.test;
                public class Plain {
                    private String value;
                    public String getValue() { return value; }
                }
                """);

            var compilation = compile(plainClass, packageInfo);

            assertThat(compilation).failed();
            assertThat(compilation)
                .hadErrorContaining("not a record, sealed interface, enum, or class with wither methods");
            assertThat(compilation)
                .hadErrorContaining("Consider adding withXxx() methods");
        }

        @Test
        @DisplayName("should identify specific missing wither for partial class")
        void shouldIdentifyMissingWither() {
            var partialClass = JavaFileObjects.forSourceString("com.test.Partial", """
                package com.test;
                public class Partial {
                    private String name;
                    private int age;

                    public String getName() { return name; }
                    public int getAge() { return age; }

                    // Only one wither - age is missing
                    public Partial withName(String name) {
                        return new Partial();
                    }
                }
                """);

            var compilation = compile(partialClass, packageInfo);

            // Should succeed with partial support, or provide helpful message
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Mutable Type Errors")
    class MutableTypeErrors {

        @Test
        @DisplayName("should list specific setter methods when rejecting mutable class")
        void shouldListSetterMethods() {
            var mutableClass = JavaFileObjects.forSourceString("com.test.Mutable", """
                package com.test;
                public class Mutable {
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                    public Mutable withName(String name) { return new Mutable(); }
                }
                """);

            var compilation = compile(mutableClass, packageInfo);

            assertThat(compilation).failed();
            assertThat(compilation)
                .hadErrorContaining("has mutable fields");
            assertThat(compilation)
                .hadErrorContaining("setName");
            assertThat(compilation)
                .hadErrorContaining("allowMutable = true");
        }
    }

    @Nested
    @DisplayName("Configuration Errors")
    class ConfigurationErrors {

        @Test
        @DisplayName("should report invalid target package")
        void shouldReportInvalidTargetPackage() {
            var packageInfo = JavaFileObjects.forSourceString("com.test.package-info", """
                @ImportOptics(
                    value = {java.time.LocalDate.class},
                    targetPackage = "123invalid"
                )
                package com.test;
                import org.higherkindedj.optics.annotations.ImportOptics;
                """);

            var compilation = compile(packageInfo);

            assertThat(compilation).failed();
            assertThat(compilation)
                .hadErrorContaining("Invalid package name");
        }
    }
}
```

### Acceptance Criteria
- [ ] All error messages suggest corrective action
- [ ] Error messages include relevant context (class name, field name, etc.)
- [ ] No cryptic or technical-only error messages
- [ ] Error location points to the correct source element

### Estimated Effort
- Implementation: 1 day

---

## Phase 6: Mutation Testing

### Objective
Measure and improve test quality using mutation testing.

### Implementation

#### 6.1 Add PIT Plugin

```kotlin
// build.gradle.kts (hkj-processor)
plugins {
    id("info.solidsoft.pitest") version "1.22.0"
}

pitest {
    targetClasses.set(setOf(
        "org.higherkindedj.optics.processing.*",
        "org.higherkindedj.optics.processing.external.*"
    ))
    targetTests.set(setOf(
        "org.higherkindedj.optics.processing.*Test",
        "org.higherkindedj.optics.processing.*Tests"
    ))
    mutators.set(setOf("STRONGER"))
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(70)  // Start at 70%, increase incrementally as test quality improves
}
```

**Reports Location:** `hkj-processor/build/reports/pitest/`

**Note:** Mutation testing is intended as a **local development tool** at this stage, not a CI gate. This allows developers to measure and improve test quality without blocking builds.

### Acceptance Criteria
- [ ] Mutation testing configured and running locally
- [ ] Mutation score > 70% (initial target, increase to 80% over time)
- [ ] Clear documentation on how to run and interpret results
- [ ] Reports generated locally for developer review

### Estimated Effort
- Configuration: 0.5 days
- Improving tests to meet threshold: 1-2 days

---

## Phase 7: Incremental Compilation Testing

### Objective
Ensure the processor works correctly with incremental builds and IDE integration.

### Implementation

```java
@DisplayName("Incremental Compilation Tests")
class IncrementalCompilationTest {

    @Test
    @DisplayName("should regenerate when source type changes")
    void shouldRegenerateOnSourceChange() {
        // First compilation
        var recordV1 = JavaFileObjects.forSourceString("com.test.Person", """
            package com.test;
            public record Person(String name) {}
            """);

        var compilation1 = compile(recordV1, packageInfo);
        assertThat(compilation1).succeeded();
        String generated1 = getGeneratedSource(compilation1, "PersonLenses");

        // Modified record
        var recordV2 = JavaFileObjects.forSourceString("com.test.Person", """
            package com.test;
            public record Person(String name, int age) {}
            """);

        var compilation2 = compile(recordV2, packageInfo);
        assertThat(compilation2).succeeded();
        String generated2 = getGeneratedSource(compilation2, "PersonLenses");

        // Verify new field appears
        assertThat(generated2).contains("public static Lens<Person, Integer> age()");
        assertThat(generated1).doesNotContain("age()");
    }

    @Test
    @DisplayName("should handle removed types gracefully")
    void shouldHandleRemovedTypes() {
        // Initial compilation with two types
        var record1 = JavaFileObjects.forSourceString("com.test.Person", """
            package com.test;
            public record Person(String name) {}
            """);

        var record2 = JavaFileObjects.forSourceString("com.test.Address", """
            package com.test;
            public record Address(String city) {}
            """);

        var packageInfoBoth = JavaFileObjects.forSourceString("com.test.package-info", """
            @ImportOptics({com.test.Person.class, com.test.Address.class})
            package com.test;
            import org.higherkindedj.optics.annotations.ImportOptics;
            """);

        var compilation1 = compile(record1, record2, packageInfoBoth);
        assertThat(compilation1).succeeded();

        // Recompile with only Person
        var packageInfoOne = JavaFileObjects.forSourceString("com.test.package-info", """
            @ImportOptics({com.test.Person.class})
            package com.test;
            import org.higherkindedj.optics.annotations.ImportOptics;
            """);

        var compilation2 = compile(record1, packageInfoOne);
        assertThat(compilation2).succeeded();
        assertThat(compilation2).generatedSourceFile("com.test.PersonLenses").isNotNull();
    }
}
```

### Acceptance Criteria
- [ ] Regeneration on source changes works correctly
- [ ] Removed types don't cause stale generated code issues
- [ ] Type parameter changes are reflected in regenerated code

### Estimated Effort
- Implementation: 1 day

---

## Summary

| Phase | Focus | Priority | Effort | Impact |
|-------|-------|----------|--------|--------|
| 1 | Optic Law Verification (incl. composed optics) | High | 4-5 days | High |
| 2 | Property-Based Testing | High | 3 days | High |
| 3 | Edge Case Testing (incl. recursive types) | Medium | 2-2.5 days | Medium |
| 4 | Golden File Testing | Medium | 1.5 days | Medium |
| 5 | Error Message Testing | Medium | 1 day | Medium |
| 6 | Mutation Testing (70% initial threshold) | Low | 1.5-2.5 days | Medium |
| 7 | Incremental Compilation | Low | 1 day | Low |

**Total Estimated Effort: 14.5-18.5 days**

## Dependencies

### External Libraries to Add

```kotlin
// hkj-processor/build.gradle.kts
dependencies {
    testImplementation("net.jqwik:jqwik:1.8.2")
}

plugins {
    id("info.solidsoft.pitest") version "1.22.0"
}
```

### Prerequisites
- Phase 1 should be completed before Phase 2 (property tests build on law verification infrastructure)
- Phase 4 requires Phase 1-3 to be stable (golden files should capture stable output)
- Phase 6 can be done independently after Phase 1

## Success Metrics

1. **Test Coverage**: Line coverage = 100% for processor code
2. **Mutation Score**: > 70% of mutants killed (initial target, aim for 80% over time)
3. **Law Compliance**: 100% of generated optics pass all applicable laws
4. **Property Tests**: > 10,000 test cases run without failure
5. **Golden Files**: All major scenarios have golden file coverage
6. **Error Quality**: All error paths have test coverage

## Documentation Updates

As phases are implemented, the project's `TESTING-GUIDE.md` should be updated to include:

### Phase 1 Completion → Add to TESTING-GUIDE.md:
- **Processor Optic Law Verification** section
- RuntimeCompilationHelper usage patterns
- Examples of composed optics law tests

### Phase 2 Completion → Add to TESTING-GUIDE.md:
- **Processor Property-Based Testing** section
- Arbitrary providers for generated optics
- Integration with existing jQwik patterns

### Phase 4 Completion → Add to TESTING-GUIDE.md:
- **Golden File Testing** section
- How to update golden files intentionally
- CI integration for golden file verification

### Phase 6 Completion → Add to TESTING-GUIDE.md:
- **Mutation Testing** section
- Running PIT locally (not CI-integrated)
- Interpreting mutation reports
- Using mutation testing to identify weak tests

This ensures the TESTING-GUIDE.md remains the authoritative reference for all testing patterns used in the project.

## Maintenance Considerations

- Golden files should be updated when intentional generation changes are made
- Property test seeds should be committed for reproducibility
- Edge case tests should be expanded as new issues are discovered
- TESTING-GUIDE.md should be updated after each phase is completed
