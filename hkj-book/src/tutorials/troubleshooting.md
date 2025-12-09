# Tutorial Troubleshooting Guide

~~~admonish info title="What You'll Learn"
- How to fix compilation errors including missing generated lenses, type mismatches, and method not found errors
- Solutions for runtime issues like KindUnwrapException, NullPointerException, and unexpected test failures
- How to configure annotation processing in IntelliJ IDEA, Eclipse, and VS Code
- Troubleshooting build failures in Gradle and Maven
- When to use Functor, Applicative, or Monad and understanding the Kind abstraction
~~~

This guide addresses common issues you might encounter whilst working through the Higher-Kinded-J tutorials.

## Compilation Issues

### Generated Lenses/Prisms Not Found

**Symptom**: `cannot find symbol: variable UserLenses`

**Causes and Solutions**:

#### 1. Annotation Processor Not Configured
**Check**: `build.gradle` or `pom.xml` includes annotation processors:

```gradle
dependencies {
    annotationProcessor("io.github.higher-kinded-j:hkj-processor:VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:VERSION")
}
```

#### 2. Project Needs Rebuilding
**Fix**: Clean and rebuild to trigger annotation processing:
```bash
./gradlew clean build
```

**In IDE**: Build → Rebuild Project

#### 3. Local Class Limitation
**Problem**: Annotation processor cannot generate code for local classes (defined inside methods).

**Example of what doesn't work**:
```java
@Test
void someTest() {
    @GenerateLenses  // ❌ Won't work - local class
    record User(String name) {}

    // UserLenses doesn't exist!
}
```

**Solution**: Manually create the lens within the method:
```java
@Test
void someTest() {
    record User(String name) {}

    // Manual lens creation
    class UserLenses {
        public static Lens<User, String> name() {
            return Lens.of(
                User::name,
                (user, newName) -> new User(newName)
            );
        }
    }

    // Now you can use UserLenses.name()
}
```

#### 4. IDE Not Detecting Generated Code
**Fix**: Enable annotation processing in your IDE:

**IntelliJ IDEA**:
1. Preferences → Build → Compiler → Annotation Processors
2. Enable "Enable annotation processing"
3. File → Invalidate Caches → Invalidate and Restart

**Eclipse**:
1. Project → Properties → Java Compiler → Annotation Processing
2. Enable "Enable project specific settings"
3. Enable "Enable annotation processing"

---

### Type Mismatch Errors with Kind

**Symptom**: `incompatible types: Either<String,Integer> cannot be converted to Kind<...>`

**Cause**: Forgot to widen concrete type before passing to generic code.

**Fix**: Use the appropriate `KindHelper`:

```java
// ❌ Wrong - passing concrete type to generic method
Kind<F, Integer> result = functor.map(i -> i + 1, Either.right(42));

// ✅ Correct - widen first
Either<String, Integer> either = Either.right(42);
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);
Kind<EitherKind.Witness<String>, Integer> result = functor.map(i -> i + 1, kind);

// Then narrow back if needed
Either<String, Integer> resultEither = EITHER.narrow(result);
```

---

### "Cannot Find map2/map3/map4/map5"

**Symptom**: `cannot find symbol: method map2(...)`

**Cause**: These are typeclass methods, not instance methods on `Either`/`Validated`.

**Fix**: Access through the typeclass instance:

```java
// ❌ Wrong - map2 is not an instance method
Either<String, Integer> result = value1.map2(value2, (a, b) -> a + b);

// ✅ Correct - use the typeclass
EitherMonad<String> applicative = EitherMonad.instance();
Either<String, Integer> result = EITHER.narrow(
    applicative.map2(
        EITHER.widen(value1),
        EITHER.widen(value2),
        (a, b) -> a + b
    )
);
```

---

### "Maybe.getOrElse() Method Not Found"

**Symptom**: `cannot find symbol: method getOrElse(T)`

**Cause**: The method is called `orElse()`, not `getOrElse()`.

**Fix**:
```java
// ❌ Wrong
String result = maybe.getOrElse("default");

// ✅ Correct
String result = maybe.orElse("default");
```

---

### Optic Composition Type Errors

**Symptom**: `incompatible types: Optic<...> cannot be converted to Prism<...>`

**Cause**: Cross-type optic composition returns the more general `Optic` type.

**Composition Rules**:
- Lens + Lens = Lens
- Lens + Prism = **Optic** (not Prism!)
- Lens + Traversal = Traversal
- Prism + Prism = Prism
- Traversal + Lens = Traversal

**Fix**: Use the correct return type:
```java
// ❌ Wrong - expecting Prism
Prism<Order, CreditCard> orderToCreditCard =
    orderToPayment.andThen(creditCardPrism);

// ✅ Correct - Lens + Prism = Optic
Optic<Order, Order, CreditCard, CreditCard> orderToCreditCard =
    orderToPayment.andThen(creditCardPrism);
```

---

## Runtime Issues

### "Answer Required" Exception

**Symptom**: Test fails with `RuntimeException: Answer required`

**Cause**: This is expected! You haven't replaced the placeholder yet.

**Fix**: Replace `answerRequired()` with your solution:
```java
// ❌ Placeholder - will throw exception
Either<String, Integer> result = answerRequired();

// ✅ Your solution
Either<String, Integer> result = Either.right(42);
```

---

### NullPointerException in Free Monad Validation

**Symptom**: NPE when calling `.equals()` or other methods in Free Monad DSL

**Example**:
```java
OpticPrograms.get(config, envLens)
    .flatMap(env -> {
        if (env.equals("production")) {  // ❌ NPE here!
            // ...
        }
    });
```

**Cause**: Validation interpreter returns `null` for `get` operations (it's a dry-run, not an execution).

**Fix**: Add null check:
```java
OpticPrograms.get(config, envLens)
    .flatMap(env -> {
        if (env != null && env.equals("production")) {  // ✅ Safe
            return OpticPrograms.set(config, debugLens, false);
        } else {
            return OpticPrograms.pure(config);
        }
    });
```

---

### KindUnwrapException

**Symptom**: `KindUnwrapException: Cannot narrow null Kind` or type mismatch

**Cause**: Trying to narrow a `Kind<F, A>` with the wrong helper or passing `null`.

**Fix**: Ensure witness types match:
```java
// ❌ Wrong - mismatched witnesses
Either<String, Integer> either = MAYBE.narrow(someKind);

// ✅ Correct - matching witnesses
Either<String, Integer> either = EITHER.narrow(someKind);
Maybe<Integer> maybe = MAYBE.narrow(someOtherKind);
```

---

### Unexpected Test Failures with Null Values

**Symptom**: Test expects a value but gets `null`

**Cause**: Forgot to replace a `null` placeholder in a lambda or return statement.

**Common locations**:
```java
// In lambdas
Function<Integer, String> fn = i -> null;  // ❌ Replace with actual logic

// In return statements
return null;  // ❌ Replace with answerRequired() or actual value

// In function arguments
someMethod.apply(null);  // ❌ Replace with answerRequired() or actual value
```

**Fix**: Search for all `null` in your solutions and replace appropriately.

---

## IDE-Specific Issues

### IntelliJ IDEA: "Cannot Resolve Symbol"

Even though code compiles, IDE shows red underlines.

**Fixes**:
1. **Invalidate Caches**: File → Invalidate Caches → Invalidate and Restart
2. **Reimport Project**: Right-click `build.gradle` → Reload Gradle Project
3. **Rebuild**: Build → Rebuild Project
4. **Check Annotation Processing**: Preferences → Build → Compiler → Annotation Processors → Enable

---

### Eclipse: Generated Code Not Visible

**Fixes**:
1. **Refresh Project**: Right-click project → Refresh
2. **Clean Build**: Project → Clean → Clean all projects
3. **Enable Annotation Processing**:
   - Project → Properties → Java Compiler → Annotation Processing
   - Enable "Enable project specific settings"
   - Enable "Enable annotation processing"

---

### VS Code: Cannot Find Generated Classes

**Fixes**:
1. **Reload Window**: Cmd/Ctrl+Shift+P → "Reload Window"
2. **Clean Java Workspace**: Cmd/Ctrl+Shift+P → "Java: Clean Java Language Server Workspace"
3. **Rebuild**: Run `./gradlew clean build` in terminal

---

## Test Execution Issues

### Tests Don't Run

**Symptom**: Clicking "Run" does nothing or test runner can't find tests

**Fixes**:

#### Gradle:
```bash
# Run specific tutorial
./gradlew :hkj-examples:test --tests "*Tutorial01_KindBasics*"

# Run all core types tutorials
./gradlew :hkj-examples:test --tests "*coretypes*"

# Run all optics tutorials
./gradlew :hkj-examples:test --tests "*optics*"
```

#### IDE:
- Ensure JUnit 5 is configured (not JUnit 4)
- Check test runner is set to use JUnit Platform
- Verify `@Test` import is `org.junit.jupiter.api.Test`

---

### Tests Pass Locally But Fail in CI

**Common causes**:
1. **Java Version Mismatch**: Ensure CI uses Java 25+
2. **Annotation Processor Not Running**: CI build must run `clean build`, not just `test`
3. **Encoding Issues**: Ensure UTF-8 encoding in build configuration

---

## Performance Issues

### Slow Test Execution

**Symptom**: Tests take a long time to run

**Solutions**:
1. **Run Specific Tests**: Don't run all tests when debugging one exercise
   ```bash
   ./gradlew :hkj-examples:test --tests "*Tutorial01*"
   ```

2. **Use IDE Test Runner**: Faster than Gradle for individual tests

3. **Parallel Execution**: Enable in `gradle.properties`:
   ```properties
   org.gradle.parallel=true
   org.gradle.caching=true
   ```

---

### Slow IDE Auto-Completion

**Cause**: Annotation processing running on every keystroke.

**Fix in IntelliJ**:
1. Preferences → Build → Compiler → Annotation Processors
2. Set "Obtain processors from project classpath"
3. Uncheck "Run annotation processors on sources in test folders" (for quicker editing)
4. Re-enable when running tests

---

## Build Issues

### Gradle Build Fails

**Common errors**:

#### "Could not resolve dependencies"
**Fix**: Check your Maven Central connection and version numbers:
```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")
}
```

#### "Execution failed for task ':compileJava'"
**Fix**: Verify Java 25+ is configured:
```bash
java -version  # Should be 25 or later
```

Update `build.gradle`:
```gradle
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }
```

---

### Maven Build Fails

**Fix**: Ensure annotation processors are configured in `pom.xml`:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.14.1</version>
            <configuration>
                <source>25</source>
                <target>25</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.higher-kinded-j</groupId>
                        <artifactId>hkj-processor</artifactId>
                        <version>${hkj.version}</version>
                    </path>
                    <path>
                        <groupId>io.github.higher-kinded-j</groupId>
                        <artifactId>hkj-processor-plugins</artifactId>
                        <version>${hkj.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## Conceptual Confusion

### "I Don't Understand Kind<F, A>"

**Start here**: [HKT Introduction](../hkts/hkt_introduction.md)

**Key insight**: `Kind<F, A>` is just a wrapper. The actual data is unchanged:
```java
Either<String, Integer> either = Either.right(42);

// Widening just changes the type signature - data is the same
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);

// Narrowing restores the original type - data is still the same
Either<String, Integer> back = EITHER.narrow(kind);

// either == back (same object!)
```

---

### "When Do I Use Functor vs Applicative vs Monad?"

**Decision tree**:
1. **Just transforming values?** → Functor (`map`)
2. **Combining independent operations?** → Applicative (`map2`, `map3`, etc.)
3. **Chaining dependent operations?** → Monad (`flatMap`)

**Example scenario**: Form validation
- Each field validation is **independent** → Use Applicative to combine them
- After validation, processing depends on success → Use Monad to chain

---

### "Why Can't I Just Use Either.right().map()?"

**You can!** The tutorials teach the typeclass abstraction, but concrete types have convenience methods:

```java
// Concrete API (easier for simple cases)
Either<String, Integer> result = Either.right(42)
    .map(i -> i * 2)
    .flatMap(i -> Either.right(i + 10));

// Typeclass API (more powerful for generic code)
EitherMonad<String> monad = EitherMonad.instance();
Either<String, Integer> result = EITHER.narrow(
    monad.flatMap(
        EITHER.widen(Either.right(42)),
        i -> EITHER.widen(Either.right(i * 2 + 10))
    )
);
```

The typeclass version lets you write code that works for **any** monad, not just `Either`.

---

## Getting Help

If this guide doesn't solve your problem:

1. **Search GitHub Issues**: Someone may have encountered this before
   - [Open Issues](https://github.com/higher-kinded-j/higher-kinded-j/issues)
   - [Closed Issues](https://github.com/higher-kinded-j/higher-kinded-j/issues?q=is%3Aissue+is%3Aclosed)

2. **Ask in Discussions**: Describe your problem with code samples
   - [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions)

3. **File an Issue**: If you've found a bug
   - Include: Java version, IDE, build tool, minimal reproduction

4. **Check Documentation**: The main docs cover advanced topics
   - [Documentation Home](../home.md)

---

**Remember**: Most "bugs" are actually learning opportunities. Take time to understand *why* something isn't working before asking for help. The debugging process itself builds understanding!

---

**Previous:** [Solutions Guide](solutions_guide.md)
