# Diagnostics: Understanding Your HKJ Configuration

~~~admonish info title="What You'll Learn"
- How to run the `hkjDiagnostics` task to inspect your configuration
- How to read the diagnostics output
- Common configuration issues and their fixes
~~~

---

## Running the Diagnostics Task

### Gradle

The HKJ Gradle plugin registers an `hkjDiagnostics` task in the `help` group. Run it from the command line:

```bash
./gradlew hkjDiagnostics
```

### Maven

The HKJ Maven plugin provides a `diagnostics` goal. Run it from the command line:

```bash
mvn hkj:diagnostics
```

---

## Reading the Output

With default configuration, the task prints:

```
HKJ Configuration:
  Version:            0.3.7-SNAPSHOT
  Preview features:   enabled
  Spring integration: disabled
  Compile-time checks:
    Path type mismatch: enabled
  Dependencies added:
    implementation:          io.github.higher-kinded-j:hkj-core:0.3.7-SNAPSHOT
    annotationProcessor:     io.github.higher-kinded-j:hkj-processor-plugins:0.3.7-SNAPSHOT
    annotationProcessor:     io.github.higher-kinded-j:hkj-checker:0.3.7-SNAPSHOT
  Compiler args added:
    --enable-preview
    -Xplugin:HKJChecker
```

### Sections Explained

| Section | What It Shows |
|---------|--------------|
| **Version** | The HKJ library version used for all dependencies |
| **Preview features** | Whether `--enable-preview` is added to compile, test, exec, and javadoc tasks |
| **Spring integration** | Whether `hkj-spring-boot-starter` is included |
| **Compile-time checks** | Which compile-time checks are active |
| **Dependencies added** | The exact Maven coordinates added to each configuration |
| **Compiler args added** | Additional arguments passed to `javac` |

---

## Common Issues and Fixes

### Dependencies Not Resolving

**Symptom:** Build fails with "Could not resolve" errors for HKJ dependencies.

**Check:** Run `hkjDiagnostics` to verify the version is correct, then ensure Maven Central (or the Sonatype snapshots repository for SNAPSHOTs) is configured:

```gradle
repositories {
    mavenCentral()
}
```

### Preview Features Not Working

**Symptom:** Compilation fails with errors about preview features.

**Check:** Run `hkjDiagnostics` and verify "Preview features: enabled" appears. If you have set `preview = false`, you must configure preview flags manually.

### Checker Not Running

**Symptom:** Path type mismatches are not caught at compile time.

**Check:** Run `hkjDiagnostics` and verify:
- "Path type mismatch: enabled" appears under Compile-time checks
- `hkj-checker` appears in the Dependencies section
- `-Xplugin:HKJChecker` appears in the Compiler args section

If any of these are missing, check your `hkj { }` block for `checks { pathTypeMismatch = false }`.

### Version Mismatch Between Modules

**Symptom:** Runtime errors from incompatible HKJ module versions.

**Check:** Run `hkjDiagnostics` and confirm all dependencies show the same version. The plugin always uses a single version for all modules. If you have manual dependency declarations elsewhere in your build file, they may override the plugin's version.

---

~~~admonish info title="Key Takeaways"
* **`hkjDiagnostics` (Gradle) or `mvn hkj:diagnostics` (Maven)** provides a single view of your entire HKJ configuration
* **Check it first** when troubleshooting build or compilation issues
* **All dependencies** use the same version, eliminating version mismatch problems
~~~

---

**Previous:** [Compile-Time Checks](compile_checks.md)
**Next:** [Spring Boot Integration](../spring/spring_boot_integration.md)
