# Auditing Complex Data with Optics
## _A Real-World Deep Dive_: The Power of Optics

In modern software, we often work with complex, nested data structures. Performing a seemingly simple task—like "find and decode all production database passwords"—can lead to messy, error-prone code with nested loops, `if` statements, and manual type casting.

This tutorial demonstrates how to solve a sophisticated, real-world problem elegantly using the full power of **higher-kinded-j optics**. We'll build a single, declarative, type-safe optic that performs a deep, conditional data transformation.

~~~ admonish info

All the example code for this tutorial can be found in the  `org.higherkindedj.example package in the [Config Audit example](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/configaudit).

Other examples of using Optics can be found here.
[Optics examples](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/optics).

~~~

---

## 🎯 The Challenge: A Conditional Config Audit

Imagine you're responsible for auditing application configurations. Your task is:

> Find every encrypted database password, but **only** for applications deployed to the **Google Cloud Platform (`gcp`)** that are running in the **`live` environment**. For each password found, **decode it from Base64** into a raw `byte[]` for an audit service.

This single sentence implies several operations:

1. **Deep Traversal**: Navigate from a top-level config object down into a list of settings.
2. **Filtering**: Select only settings of a specific type (`EncryptedValue`).
3. **Conditional Logic**: Apply this logic *only* if the top-level config meets specific criteria (`gcp` and `live`).
4. **Data Transformation**: Decode the Base64 string into another type (`byte[]`).

Doing this imperatively is a recipe for complexity. Let's build it with optics instead.

---

## 🛠️ The Four Tools for the Job

Our solution will compose the four primary optic types, each solving a specific part of the problem.

### 1. **Lens**: The Magnifying Glass 🔎

A `Lens` provides focused access to a field within a product type (like a Java `record`). We'll use lenses to look inside our configuration objects.

* `AppConfigLenses.settings()`: Zooms from an `AppConfig` to its `List<Setting>`.
* `SettingLenses.value()`: Zooms from a `Setting` to its `SettingValue`.

### 2. **Iso**: The Universal Translator 🔄

An `Iso` (Isomorphism) defines a lossless, two-way conversion between two types. It's perfect for handling different representations of the same data.

* `DeploymentTarget <-> String`: We model our deployment target as a structured record but recognize it's isomorphic to a raw string like `"gcp|live"`. An `Iso` lets us switch between these representations.
* `String <-> byte[]`: Base64 is just an encoded representation of a byte array. An `Iso` is the perfect tool for handling this encoding and decoding.

### 3. **Prism**: The Safe Filter 🔬

A `Prism` provides focused access to a specific case within a sum type (like a `sealed interface`). It lets us safely attempt to "zoom in" on one variant, failing gracefully if the data is of a different kind.

* `SettingValuePrisms.encryptedValue()`: This is our key filter. It will look at a `SettingValue` and only succeed if it's the `EncryptedValue` variant.

### 4. **Traversal**: The Bulk Operator 🗺️

A `Traversal` lets us operate on zero or more targets within a larger structure. It's the ideal optic for working with collections.

* `AppConfigTraversals.settings()`: This generated optic gives us a single tool to go from an `AppConfig` to every `Setting` inside its list.

---

## ✨ Composing the Solution

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

## 🚀 Why This is a Powerful Approach

* **Declarative & Readable**: The optic chain describes *what* data to get, not *how* to loop and check for it. The logic reads like a path, making it self-documenting.
* **Composable & Reusable**: Every optic, and every composition, is a reusable component. We could reuse `gcpLiveOnlyPrism` for other tasks, or swap out the final `base64` Iso to perform a different transformation.
* **Type-Safe**: The entire operation is checked by the Java compiler. It's impossible to, for example, try to decode a `StringValue` as if it were encrypted. A mismatch in the optic chain results in a compile-time error, not a runtime `ClassCastException`.
* **Architectural Purity**: By having all optics share a common abstract parent (`Optic`), the library provides universal, lawful composition while allowing for specialized, efficient implementations.

---

## 🧠 Taking It Further

This example is just the beginning. Here are some ideas for extending this solution into a real-world application:

1. **Safe Decoding with `Validated`**: The `Base64.getDecoder().decode()` can throw an `IllegalArgumentException`. Instead of an `Iso`, create an `AffineTraversal` (an optional `Prism`) that returns a `Validated<String, byte[]>`, separating successes from failures gracefully.
2. **Data Migration with `modify`**: What if you need to re-encrypt all passwords with a new algorithm? The same `finalAuditor` optic can be used with a modify function from the `Traversals` utility class. You'd write a function `byte[] -> byte[]` and apply it:
```java
// A function that re-encrypts the raw password bytes
Function<byte[], byte[]> reEncryptFunction = (oldBytes) -> newCipher.encrypt(oldBytes);

// Use the *exact same optic* to update the config in-place
AppConfig updatedConfig = Traversals.modify(finalAuditor, reEncryptFunction, originalConfig);
```

3.  **More Complex Filters**: Create an optic that filters for deployments on *either*`gcp` or `aws` but *only* in the `live` environment. The composable nature of optics makes building up these complex predicate queries straightforward.
4.  **Configuration Validation**: Use the same optics to validate your configuration. You could compose a traversal that finds all `IntValue` settings with the key `"server.port"` and use `.getAll()` to check if their values are within a valid range (e.g., > 1024).
