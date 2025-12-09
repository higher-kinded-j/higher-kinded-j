# Auditing Complex Data with Optics

## _A Real-World Deep Dive_: The Power of Optics

~~~admonish info title="What You'll Learn"
- Solving complex, real-world data processing challenges with optics
- Building conditional filtering and transformation pipelines
- Combining all four core optic types in a single, powerful composition
- Creating declarative, type-safe alternatives to nested loops and type casting
- Advanced patterns like safe decoding, profunctor adaptations, and audit trails
- When optic composition provides superior solutions to imperative approaches
~~~

In modern software, we often work with complex, nested data structures. Performing a seemingly simple task, like "find and decode all production database passwords", can lead to messy, error-prone code with nested loops, `if` statements, and manual type casting.

This tutorial demonstrates how to solve a sophisticated, real-world problem elegantly using the full power of **higher-kinded-j optics**. We'll build a single, declarative, type-safe optic that performs a deep, conditional data transformation.

~~~admonish title="Example Code"

All the example code for this tutorial can be found in the  `org.higherkindedj.example package in the [Config Audit example](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/configaudit).

Other examples of using Optics can be found here.
[Optics examples](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics).

~~~

---

## The Challenge: A Conditional Config Audit

Imagine you're responsible for auditing application configurations. Your task is:

> Find every encrypted database password, but **only** for applications deployed to the **Google Cloud Platform (`gcp`)** that are running in the **`live` environment**. For each password found, **decode it from Base64** into a raw `byte[]` for an audit service.

This single sentence implies several operations:

1. **Deep Traversal**: Navigate from a top-level config object down into a list of settings.
2. **Filtering**: Select only settings of a specific type (`EncryptedValue`).
3. **Conditional Logic**: Apply this logic *only* if the top-level config meets specific criteria (`gcp` and `live`).
4. **Data Transformation**: Decode the Base64 string into another type (`byte[]`).

Doing this imperatively is a recipe for complexity. Let's build it with optics instead.

---

## Think of This Problem Like...

- **A treasure hunt with conditional maps**: Only certain maps (GCP/Live configs) contain the treasures (encrypted passwords)
- **A selective mining operation**: Drill down only into the right geological formations (config types) to extract specific minerals (encrypted data)
- **A security scanner with filters**: Only scan certain types of systems (matching deployment criteria) for specific vulnerabilities (encrypted values)
- **A data archaeology expedition**: Excavate only specific sites (qualified configs) to uncover particular artifacts (encoded passwords)

---

## The Four Tools for the Job

Our solution will compose the four primary optic types, each solving a specific part of the problem.

### 1. **Lens**: The Magnifying Glass

A `Lens` provides focused access to a field within a product type (like a Java `record`). We'll use lenses to look inside our configuration objects.

* `AppConfigLenses.settings()`: Zooms from an `AppConfig` to its `List<Setting>`.
* `SettingLenses.value()`: Zooms from a `Setting` to its `SettingValue`.

### 2. **Iso**: The Universal Translator

An `Iso` (Isomorphism) defines a lossless, two-way conversion between two types. It's perfect for handling different representations of the same data.

* `DeploymentTarget <-> String`: We model our deployment target as a structured record but recognise it's isomorphic to a raw string like `"gcp|live"`. An `Iso` lets us switch between these representations.
* `String <-> byte[]`: Base64 is just an encoded representation of a byte array. An `Iso` is the perfect tool for handling this encoding and decoding.

### 3. **Prism**: The Safe Filter

A `Prism` provides focused access to a specific case within a sum type (like a `sealed interface`). It lets us safely attempt to "zoom in" on one variant, failing gracefully if the data is of a different kind.

* `SettingValuePrisms.encryptedValue()`: This is our key filter. It will look at a `SettingValue` and only succeed if it's the `EncryptedValue` variant.

### 4. **Traversal**: The Bulk Operator

A `Traversal` lets us operate on zero or more targets within a larger structure. It's the ideal optic for working with collections.

* `AppConfigTraversals.settings()`: This generated optic gives us a single tool to go from an `AppConfig` to every `Setting` inside its list.

---

## When to Use This Approach vs Alternatives

### Use Optic Composition When:

- **Complex conditional filtering** - Multiple levels of filtering based on different criteria
- **Reusable audit logic** - The same audit pattern applies to different config types
- **Type-safe data extraction** - Ensuring compile-time safety for complex transformations
- **Declarative data processing** - Building self-documenting processing pipelines

```java

// Perfect for reusable, conditional audit logic Traversal<ServerConfig, byte[]> sensitiveDataAuditor = ServerConfigTraversals.environments() .andThen(EnvironmentPrisms.production().asTraversal()) .andThen(EnvironmentTraversals.credentials()) .andThen(CredentialPrisms.encrypted().asTraversal()) .andThen(EncryptedCredentialIsos.base64ToBytes.asTraversal());

```

### Use Stream Processing When:

- **Simple filtering** - Basic collection operations without complex nesting
- **Performance critical paths** - Minimal abstraction overhead needed
- **Aggregation logic** - Computing statistics or summaries

```java

// Better with streams for simple collection processing List<String> allConfigNames = configs.stream() .map(AppConfig::name) .filter(name -> name.startsWith("prod-")) .collect(toList());

```

### Use Manual Iteration When:

- **Early termination** - You might want to stop processing on first match
- **Complex business logic** - Multiple conditions and branches that don't map cleanly
- **Legacy integration** - Working with existing imperative codebases

```java

// Sometimes manual loops are clearest for complex logic for (AppConfig config : configs) { if (shouldAudit(config) && hasEncryptedData(config)) { auditResults.add(performDetailedAudit(config)); if (auditResults.size() >= MAX\_AUDITS) break; } }

```

---

## Common Pitfalls

### Don't Do This:

```java

// Over-engineering simple cases Traversal<String, String> stringIdentity = Iso.of(s -> s, s -> s).asTraversal(); // Just use the string directly!

// Creating complex compositions inline var passwords = AppConfigLenses.settings().asTraversal() .andThen(SettingLenses.value().asTraversal()) .andThen(SettingValuePrisms.encryptedValue().asTraversal()) // ... 10 more lines of composition .getAll(config); // Hard to understand and reuse

// Ignoring error handling in transformations Iso<String, byte[]> unsafeBase64 = Iso.of( Base64.getDecoder()::decode,  // Can throw IllegalArgumentException! Base64.getEncoder()::encodeToString );

// Forgetting to test round-trip properties // No verification that encode(decode(x)) == x

```

### Do This Instead:

```java

// Use appropriate tools for simple cases String configName = config.name(); // Direct access is fine

// Create well-named, reusable compositions public static final Traversal<AppConfig, byte[]> GCP\_LIVE\_ENCRYPTED\_PASSWORDS = gcpLiveOnlyPrism.asTraversal() .andThen(AppConfigTraversals.settings()) .andThen(SettingLenses.value().asTraversal()) .andThen(SettingValuePrisms.encryptedValue().asTraversal()) .andThen(EncryptedValueLenses.base64Value().asTraversal()) .andThen(EncryptedValueIsos.base64.asTraversal());

// Handle errors gracefully Prism<String, byte[]> safeBase64Prism = Prism.of( str -> { try { return Optional.of(Base64.getDecoder().decode(str)); } catch (IllegalArgumentException e) { return Optional.empty(); } }, bytes -> Base64.getEncoder().encodeToString(bytes) );

// Test your compositions @Test public void testBase64RoundTrip() { String original = "test data"; String encoded = Base64.getEncoder().encodeToString(original.getBytes()); byte[] decoded = EncryptedValueIsos.base64.get(encoded); String roundTrip = new String(decoded); assertEquals(original, roundTrip); }

```

---

## Performance Notes

Optic compositions are optimised for complex data processing:

- **Lazy evaluation**: Complex filters only run when data actually matches
- **Single-pass processing**: Compositions traverse data structures only once
- **Memory efficient**: Only creates new objects for actual transformations
- **Compile-time optimisation**: Complex optic chains are inlined by the JVM
- **Structural sharing**: Unchanged parts of data structures are reused

**Best Practice**: Profile your specific use case and compare with stream-based alternatives:

```java

public class AuditPerformance { // For frequent auditing, create optics once and reuse 
    private static final Traversal<AppConfig, byte[]> AUDIT_TRAVERSAL = createAuditTraversal();

    @Benchmark
    public List<byte[]> opticBasedAudit(List<AppConfig> configs) {
        return configs.stream()
            .flatMap(config -> Traversals.getAll(AUDIT_TRAVERSAL, config).stream())
            .collect(toList());
    }
  
    @Benchmark  
    public List<byte[]> streamBasedAudit(List<AppConfig> configs) {
        return configs.stream()
            .filter(this::isGcpLive)
            .flatMap(config -> config.settings().stream())
            .map(Setting::value)
            .filter(EncryptedValue.class::isInstance)
            .map(EncryptedValue.class::cast)
            .map(encrypted -> Base64.getDecoder().decode(encrypted.base64Value()))
            .collect(toList());
    }

}
```

## Composing the Solution

Here's how we chain these optics together. To create the most robust and general-purpose optic (a `Traversal`), we convert each part of our chain into a `Traversal` using `.asTraversal()` before composing it. This ensures type-safety and clarity throughout the process.

The final composed optic has the type `Traversal<AppConfig, byte[]>` and reads like a declarative path: **`AppConfig -> (Filter for GCP/Live) -> each Setting -> its Value -> (Filter for Encrypted) -> the inner String -> the raw bytes`**

```java
// Inside ConfigAuditExample.java

// A. First, create a Prism to act as our top-level filter.
Prism<AppConfig, AppConfig> gcpLiveOnlyPrism = Prism.of(
    config -> {
        String rawTarget = DeploymentTarget.toRawString().get(config.target());
        return "gcp|live".equals(rawTarget) ? Optional.of(config) : Optional.empty();
    },
    config -> config // The 'build' function is just identity
);

// B. Define the main traversal path to get to the data we want to audit.
Traversal<AppConfig, byte[]> auditTraversal =
    AppConfigTraversals.settings()                             // Traversal<AppConfig, Setting>
        .andThen(SettingLenses.value().asTraversal())        // Traversal<AppConfig, SettingValue>
        .andThen(SettingValuePrisms.encryptedValue().asTraversal()) // Traversal<AppConfig, EncryptedValue>
        .andThen(EncryptedValueLenses.base64Value().asTraversal())  // Traversal<AppConfig, String>
        .andThen(EncryptedValueIsos.base64.asTraversal());   // Traversal<AppConfig, byte[]>

// C. Combine the filter and the main traversal into the final optic.
Traversal<AppConfig, byte[]> finalAuditor = gcpLiveOnlyPrism.asTraversal().andThen(auditTraversal);

// D. Using the final optic is now trivial.
// We call a static helper method from our Traversals utility class.
List<byte[]> passwords = Traversals.getAll(finalAuditor, someConfig);
```

When we call `Traversals.getAll(finalAuditor, config)`, it performs the entire, complex operation and returns a simple `List<byte[]>` containing only the data we care about.

---

## Why This is a Powerful Approach

* **Declarative & Readable**: The optic chain describes *what* data to get, not *how* to loop and check for it. The logic reads like a path, making it self-documenting.
* **Composable & Reusable**: Every optic, and every composition, is a reusable component. We could reuse `gcpLiveOnlyPrism` for other tasks, or swap out the final `base64` Iso to perform a different transformation.
* **Type-Safe**: The entire operation is checked by the Java compiler. It's impossible to, for example, try to decode a `StringValue` as if it were encrypted. A mismatch in the optic chain results in a compile-time error, not a runtime `ClassCastException`.
* **Architectural Purity**: By having all optics share a common abstract parent (`Optic`), the library provides universal, lawful composition while allowing for specialised, efficient implementations.
* **Testable**: Each component can be tested independently, and the composition can be tested as a whole.

---

## Taking It Further

This example is just the beginning. Here are some ideas for extending this solution into a real-world application:

### 1. **Safe Decoding with `Validated`**

The `Base64.getDecoder().decode()` can throw an `IllegalArgumentException`. Instead of an `Iso`, create an `AffineTraversal` (an optional `Prism`) that returns a `Validated<String, byte[]>`, separating successes from failures gracefully.


```java
public static final Prism<String, byte[]> SAFE_BASE64_PRISM = Prism.of(
    encoded -> {
        try {
            return Optional.of(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    },
    bytes -> Base64.getEncoder().encodeToString(bytes)
);

// Use in a traversal that accumulates both successes and failures
public static AuditResult auditWithErrorReporting(AppConfig config) {
    var validatedApplicative = ValidatedMonad.instance(Semigroups.list());
  
    Traversal<AppConfig, String> base64Strings = /* ... path to base64 strings ... */;
  
    Validated<List<String>, List<byte[]>> result = VALIDATED.narrow(
        base64Strings.modifyF(
            encoded -> SAFE_BASE64_PRISM.getOptional(encoded)
                .map(bytes -> VALIDATED.widen(Validated.valid(bytes)))
                .orElse(VALIDATED.widen(Validated.invalid(List.of("Invalid base64: " + encoded)))),
            config,
            validatedApplicative
        )
    );
  
    return new AuditResult(result);
}
```

### 2. **Data Migration with `modify`**

What if you need to re-encrypt all passwords with a new algorithm? The same `finalAuditor` optic can be used with a modify function from the `Traversals` utility class. You'd write a function `byte[] -> byte[]` and apply it:


```java
// A function that re-encrypts the raw password bytes
Function<byte[], byte[]> reEncryptFunction = oldBytes -> newCipher.encrypt(oldBytes);

// Use the *exact same optic* to update the config in-place
AppConfig updatedConfig = Traversals.modify(finalAuditor, reEncryptFunction, originalConfig);
```

### 3. **Profunctor Adaptations for Legacy Systems**

Suppose your audit service expects a different data format: perhaps it works with `ConfigDto` objects instead of `AppConfig`. Rather than rewriting your carefully crafted optic, you can adapt it using profunctor operations:


```java
// Adapt the auditor to work with legacy DTO format
Traversal<ConfigDto, byte[]> legacyAuditor = finalAuditor.contramap(dto -> convertToAppConfig(dto));

// Or adapt both input and output formats simultaneously
Traversal<ConfigDto, AuditRecord> fullyAdaptedAuditor = finalAuditor.dimap(
    dto -> convertToAppConfig(dto),           // Convert input format
    bytes -> new AuditRecord(bytes, timestamp()) // Convert output format
);
```

This profunctor capability means your core business logic (the auditing path) remains unchanged whilst adapting to different system interfaces: a powerful example of the [Profunctor Optics](profunctor_optics.md) capabilities.

### 4. **More Complex Filters**

Create an optic that filters for deployments on *either*`gcp` or `aws` but *only* in the `live` environment. The composable nature of optics makes building up these complex predicate queries straightforward.


```java
// Multi-cloud live environment filter
Prism<AppConfig, AppConfig> cloudLiveOnlyPrism = Prism.of(
    config -> {
        String rawTarget = DeploymentTarget.toRawString().get(config.target());
        boolean isLiveCloud = rawTarget.equals("gcp|live") || 
                             rawTarget.equals("aws|live") || 
                             rawTarget.equals("azure|live");
        return isLiveCloud ? Optional.of(config) : Optional.empty();
    },
    config -> config
);

// Environment-specific processing
public static final Map<String, Traversal<AppConfig, byte[]>> ENVIRONMENT_AUDITORS = Map.of(
    "development", devEnvironmentPrism.asTraversal().andThen(auditTraversal),
    "staging", stagingEnvironmentPrism.asTraversal().andThen(auditTraversal),
    "production", cloudLiveOnlyPrism.asTraversal().andThen(auditTraversal)
);

public static List<byte[]> auditForEnvironment(String environment, AppConfig config) {
    return ENVIRONMENT_AUDITORS.getOrDefault(environment, Traversal.empty())
        .getAll(config);
}
```

### 5. **Configuration Validation**

Use the same optics to validate your configuration. You could compose a traversal that finds all `IntValue` settings with the key `"server.port"` and use `.getAll()` to check if their values are within a valid range (e.g., > 1024).


```java
public static final Traversal<AppConfig, Integer> SERVER_PORTS = 
    AppConfigTraversals.settings()
        .andThen(settingWithKey("server.port"))
        .andThen(SettingLenses.value().asTraversal())
        .andThen(SettingValuePrisms.intValue().asTraversal())
        .andThen(IntValueLenses.value().asTraversal());

public static List<String> validatePorts(AppConfig config) {
    return Traversals.getAll(SERVER_PORTS, config).stream()
        .filter(port -> port <= 1024 || port > 65535)
        .map(port -> "Invalid port: " + port + " (must be 1024-65535)")
        .collect(toList());
}
```

### 6. **Audit Trail Generation**

Extend the auditor to generate comprehensive audit trails:


```java
public record AuditEntry(String configName, String settingKey, String encryptedValue, 
                        Instant auditTime, String auditorId) {}

public static final Traversal<AppConfig, AuditEntry> AUDIT_TRAIL_GENERATOR =
    gcpLiveOnlyPrism.asTraversal()
        .andThen(AppConfigTraversals.settings())
        .andThen(settingFilter)
        .andThen(auditEntryMapper);

// Generate complete audit report
public static AuditReport generateAuditReport(List<AppConfig> configs, String auditorId) {
    List<AuditEntry> entries = configs.stream()
        .flatMap(config -> Traversals.getAll(AUDIT_TRAIL_GENERATOR, config).stream())
        .collect(toList());
  
    return new AuditReport(entries, Instant.now(), auditorId);
}
```

This combination of composability, type safety, and profunctor adaptability makes higher-kinded-j optics incredibly powerful for real-world data processing scenarios, particularly in enterprise environments where data formats, security requirements, and compliance needs are constantly evolving.

---

**Previous:** [Cookbook](cookbook.md)
