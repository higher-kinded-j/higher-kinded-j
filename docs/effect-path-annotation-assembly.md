# EffectPath Annotation-Driven Assembly

> **Status**: Living Document (v1.0)
> **Focus**: Detailed design of annotation processing for code generation
> **Rationale**: Building annotation support from day one avoids costly rewrites

## Why Annotation-Driven from Day One

### Problem: Manual Boilerplate

Without annotations, every service that uses effects requires manual bridge code:

```java
// Manual bridge - must be written for every service
public final class UserServicePaths {
    public static MaybePath<User> findById(UserService service, Long id) {
        return MaybePath.of(service.findById(id));
    }

    public static EitherPath<ValidationError, User> validate(UserService service, User user) {
        return EitherPath.of(service.validate(user));
    }

    // ... 20 more methods ...
}
```

### Solution: Generate from Annotations

```java
@GeneratePathBridge
public interface UserService {
    @PathVia Maybe<User> findById(Long id);
    @PathVia Either<ValidationError, User> validate(User user);
}
// UserServicePaths.java generated automatically
```

### Why Not Add Later?

1. **API Surface Changes**: Annotations influence method signatures
2. **Package Structure**: Annotation package needed early for cross-cutting use
3. **Testing Infrastructure**: Annotation processor tests need setup
4. **Documentation**: Users expect annotations in initial docs
5. **Backwards Compatibility**: Adding annotations later may break existing code

## Architecture

### Module Structure

```
hkj-core/
├── src/main/java/org/higherkindedj/hkt/path/
│   ├── annotation/                          # Annotation definitions
│   │   ├── PathSource.java
│   │   ├── GeneratePathBridge.java
│   │   ├── PathVia.java
│   │   ├── PathFactory.java
│   │   └── PathConfig.java
│   ├── processor/                           # Annotation processor
│   │   ├── PathProcessor.java               # Main processor
│   │   ├── PathSourceGenerator.java         # Generates Path wrappers
│   │   ├── PathBridgeGenerator.java         # Generates bridge methods
│   │   ├── PathCodeModel.java               # AST model for generation
│   │   └── PathValidations.java             # Compile-time validations
│   └── spi/                                 # Service Provider Interface
│       ├── PathProvider.java
│       ├── PathProviderRegistry.java
│       └── TypeMapping.java
└── src/main/resources/
    └── META-INF/services/
        └── javax.annotation.processing.Processor
```

### Processing Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         ANNOTATION PROCESSING FLOW                        │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│   Source Code                  Processor                  Generated Code  │
│   ───────────                  ─────────                  ──────────────  │
│                                                                           │
│   @PathSource                                                             │
│   interface MyEffect ──────►  PathSourceGenerator  ──────► MyEffectPath   │
│                                     │                                     │
│                                     ▼                                     │
│                               PathValidations                             │
│                               - Check Monad exists                        │
│                               - Verify type params                        │
│                               - Validate methods                          │
│                                     │                                     │
│   @GeneratePathBridge               │                                     │
│   interface Service ───────►  PathBridgeGenerator  ──────► ServicePaths   │
│                                     │                                     │
│                                     ▼                                     │
│                               PathCodeModel                               │
│                               - Build AST                                 │
│                               - Apply templates                           │
│                               - Write source files                        │
│                                                                           │
└──────────────────────────────────────────────────────────────────────────┘
```

## Annotation Definitions

### @PathSource

Marks a type as a source for Path wrapper generation.

```java
package org.higherkindedj.hkt.path.annotation;

import java.lang.annotation.*;

/**
 * Marks a higher-kinded type as a source for Path wrapper generation.
 *
 * <p>When applied to an interface or class, the annotation processor generates
 * a corresponding Path wrapper class that provides fluent composition methods.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @PathSource(witness = MaybeKind.Witness.class)
 * public sealed interface Maybe<A> permits Just, Nothing {
 *     // ...
 * }
 * // Generates: MaybePath.java
 * }</pre>
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>The annotated type must have a corresponding Monad instance</li>
 *   <li>The witness class must be a valid HKT witness type</li>
 *   <li>The type must have standard functional methods (map, flatMap)</li>
 * </ul>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Documented
public @interface PathSource {

    /**
     * The witness type for HKT simulation.
     *
     * @return the witness class (e.g., MaybeKind.Witness.class)
     */
    Class<?> witness();

    /**
     * Custom name for the generated Path class.
     * If empty, defaults to {@code TypeNamePath} (e.g., Maybe -> MaybePath).
     *
     * @return custom class name or empty for default
     */
    String pathClassName() default "";

    /**
     * Package for generated Path class.
     * If empty, uses same package as source type.
     *
     * @return custom package or empty for default
     */
    String pathPackage() default "";

    /**
     * Whether to generate factory methods on the Path class.
     *
     * @return true to generate factories (default: true)
     */
    boolean generateFactories() default true;

    /**
     * Error type for fallible effects (e.g., Either, Try).
     * Use Void.class for non-fallible effects.
     *
     * @return the error type class
     */
    Class<?> errorType() default Void.class;
}
```

### @GeneratePathBridge

Generates bridge methods for services that return effect types.

```java
package org.higherkindedj.hkt.path.annotation;

import java.lang.annotation.*;

/**
 * Generates a companion class with Path-returning bridge methods.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @GeneratePathBridge
 * public interface UserRepository {
 *     @PathVia Maybe<User> findById(Long id);
 *     @PathVia Either<DbError, User> save(User user);
 * }
 *
 * // Generates: UserRepositoryPaths.java
 * public final class UserRepositoryPaths {
 *     public static MaybePath<User> findById(UserRepository repo, Long id) {
 *         return MaybePath.of(repo.findById(id));
 *     }
 *     public static EitherPath<DbError, User> save(UserRepository repo, User user) {
 *         return EitherPath.of(repo.save(user));
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Documented
public @interface GeneratePathBridge {

    /**
     * Custom name for the generated Paths class.
     * Defaults to {@code TypeNamePaths}.
     */
    String className() default "";

    /**
     * Package for generated class.
     * Defaults to same package as source.
     */
    String packageName() default "";

    /**
     * Whether to generate composed operations.
     * If true, generates methods that chain multiple @PathVia methods.
     */
    boolean generateComposed() default true;

    /**
     * Visibility of generated class.
     */
    Visibility visibility() default Visibility.PUBLIC;

    enum Visibility {
        PUBLIC, PACKAGE_PRIVATE
    }
}
```

### @PathVia

Marks a method as a path composition point.

```java
package org.higherkindedj.hkt.path.annotation;

import java.lang.annotation.*;

/**
 * Marks a method for inclusion in generated Path bridges.
 *
 * <h2>Method Requirements</h2>
 * <ul>
 *   <li>Return type must be a supported effect type (Maybe, Either, Try, IO)</li>
 *   <li>Or a type annotated with @PathSource</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @GeneratePathBridge
 * public interface OrderService {
 *     @PathVia("find")  // Custom name in generated code
 *     Maybe<Order> findOrderById(Long id);
 *
 *     @PathVia  // Uses method name
 *     Either<ValidationError, Order> validateOrder(Order order);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
@Documented
public @interface PathVia {

    /**
     * Custom method name in generated bridge.
     * Defaults to the source method name.
     */
    String value() default "";

    /**
     * Documentation for the generated method.
     */
    String doc() default "";

    /**
     * Whether this method should be included in composed operations.
     */
    boolean composable() default true;
}
```

### @PathConfig

Global configuration for path generation.

```java
package org.higherkindedj.hkt.path.annotation;

import java.lang.annotation.*;

/**
 * Global configuration for Path generation, applied to package-info.java.
 *
 * <pre>{@code
 * @PathConfig(
 *     nullSafety = NullSafety.JSPECIFY,
 *     defaultVisibility = Visibility.PUBLIC
 * )
 * package com.example.services;
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PACKAGE)
@Documented
public @interface PathConfig {

    /**
     * Null safety annotation style.
     */
    NullSafety nullSafety() default NullSafety.JSPECIFY;

    /**
     * Default visibility for generated classes.
     */
    GeneratePathBridge.Visibility defaultVisibility()
        default GeneratePathBridge.Visibility.PUBLIC;

    /**
     * Whether to generate @Generated annotations.
     */
    boolean includeGeneratedAnnotation() default true;

    /**
     * Custom header comment for generated files.
     */
    String headerComment() default "";

    enum NullSafety {
        NONE,
        JSPECIFY,
        JSR305,
        JETBRAINS
    }
}
```

## Annotation Processor Implementation

### Main Processor

```java
package org.higherkindedj.hkt.path.processor;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.util.*;

@SupportedAnnotationTypes({
    "org.higherkindedj.hkt.path.annotation.PathSource",
    "org.higherkindedj.hkt.path.annotation.GeneratePathBridge"
})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class PathProcessor extends AbstractProcessor {

    private PathSourceGenerator sourceGenerator;
    private PathBridgeGenerator bridgeGenerator;
    private PathValidations validations;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.validations = new PathValidations(env);
        this.sourceGenerator = new PathSourceGenerator(env);
        this.bridgeGenerator = new PathBridgeGenerator(env);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Process @PathSource annotations
        for (Element element : roundEnv.getElementsAnnotatedWith(PathSource.class)) {
            processPathSource((TypeElement) element);
        }

        // Process @GeneratePathBridge annotations
        for (Element element : roundEnv.getElementsAnnotatedWith(GeneratePathBridge.class)) {
            processPathBridge((TypeElement) element);
        }

        return true;
    }

    private void processPathSource(TypeElement element) {
        // Validate the annotated type
        List<String> errors = validations.validatePathSource(element);
        if (!errors.isEmpty()) {
            errors.forEach(e -> processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, e, element));
            return;
        }

        // Generate Path wrapper
        try {
            sourceGenerator.generate(element);
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to generate Path: " + e.getMessage(),
                element
            );
        }
    }

    private void processPathBridge(TypeElement element) {
        // Validate the annotated type
        List<String> errors = validations.validatePathBridge(element);
        if (!errors.isEmpty()) {
            errors.forEach(e -> processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, e, element));
            return;
        }

        // Generate bridge class
        try {
            bridgeGenerator.generate(element);
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to generate bridge: " + e.getMessage(),
                element
            );
        }
    }
}
```

### Validations

```java
package org.higherkindedj.hkt.path.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.util.*;

public class PathValidations {

    private final ProcessingEnvironment env;
    private final Types types;
    private final Elements elements;

    public PathValidations(ProcessingEnvironment env) {
        this.env = env;
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
    }

    public List<String> validatePathSource(TypeElement element) {
        List<String> errors = new ArrayList<>();

        // Must be interface or class
        if (element.getKind() != ElementKind.INTERFACE &&
            element.getKind() != ElementKind.CLASS) {
            errors.add("@PathSource can only be applied to interfaces or classes");
        }

        // Must have exactly one type parameter
        if (element.getTypeParameters().size() < 1) {
            errors.add("@PathSource type must have at least one type parameter");
        }

        // Check for required methods
        boolean hasMap = false;
        boolean hasFlatMap = false;
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                String name = enclosed.getSimpleName().toString();
                if (name.equals("map")) hasMap = true;
                if (name.equals("flatMap")) hasFlatMap = true;
            }
        }
        if (!hasMap) errors.add("@PathSource type must have a map() method");
        if (!hasFlatMap) errors.add("@PathSource type must have a flatMap() method");

        // Validate witness type exists
        PathSource annotation = element.getAnnotation(PathSource.class);
        // Note: accessing annotation.witness() at compile time requires mirror API

        return errors;
    }

    public List<String> validatePathBridge(TypeElement element) {
        List<String> errors = new ArrayList<>();

        // Must be interface
        if (element.getKind() != ElementKind.INTERFACE) {
            errors.add("@GeneratePathBridge can only be applied to interfaces");
        }

        // Check @PathVia methods
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                PathVia pathVia = method.getAnnotation(PathVia.class);
                if (pathVia != null) {
                    errors.addAll(validatePathViaMethod(method));
                }
            }
        }

        return errors;
    }

    private List<String> validatePathViaMethod(ExecutableElement method) {
        List<String> errors = new ArrayList<>();

        TypeMirror returnType = method.getReturnType();

        // Return type must be a supported effect type
        if (!isSupportedEffectType(returnType)) {
            errors.add("Method " + method.getSimpleName() +
                " must return a supported effect type (Maybe, Either, Try, IO) " +
                "or a type annotated with @PathSource");
        }

        return errors;
    }

    private boolean isSupportedEffectType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        DeclaredType declared = (DeclaredType) type;
        String typeName = declared.asElement().getSimpleName().toString();

        return Set.of("Maybe", "Either", "Try", "IO", "Validated")
            .contains(typeName) ||
            declared.asElement().getAnnotation(PathSource.class) != null;
    }
}
```

### Code Generator

```java
package org.higherkindedj.hkt.path.processor;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.io.*;

public class PathBridgeGenerator {

    private final ProcessingEnvironment env;

    public PathBridgeGenerator(ProcessingEnvironment env) {
        this.env = env;
    }

    public void generate(TypeElement element) throws IOException {
        GeneratePathBridge annotation = element.getAnnotation(GeneratePathBridge.class);

        // Determine output class name
        String className = annotation.className().isEmpty()
            ? element.getSimpleName() + "Paths"
            : annotation.className();

        // Determine package
        String packageName = annotation.packageName().isEmpty()
            ? env.getElementUtils().getPackageOf(element).getQualifiedName().toString()
            : annotation.packageName();

        // Build the code model
        PathCodeModel model = buildCodeModel(element, className, packageName);

        // Generate source file
        String qualifiedName = packageName.isEmpty()
            ? className
            : packageName + "." + className;
        JavaFileObject sourceFile = env.getFiler().createSourceFile(qualifiedName, element);

        try (PrintWriter writer = new PrintWriter(sourceFile.openWriter())) {
            writeSource(writer, model);
        }
    }

    private PathCodeModel buildCodeModel(TypeElement element, String className, String packageName) {
        PathCodeModel model = new PathCodeModel(className, packageName);

        // Add imports
        model.addImport("org.higherkindedj.hkt.path.*");
        model.addImport("javax.annotation.processing.Generated");

        // Process each @PathVia method
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                PathVia pathVia = method.getAnnotation(PathVia.class);
                if (pathVia != null) {
                    model.addBridgeMethod(buildBridgeMethod(element, method, pathVia));
                }
            }
        }

        return model;
    }

    private PathCodeModel.BridgeMethod buildBridgeMethod(
            TypeElement serviceElement,
            ExecutableElement method,
            PathVia annotation) {

        String methodName = annotation.value().isEmpty()
            ? method.getSimpleName().toString()
            : annotation.value();

        String returnType = determinePathType(method.getReturnType());
        String serviceType = serviceElement.getSimpleName().toString();

        List<PathCodeModel.Parameter> params = new ArrayList<>();
        params.add(new PathCodeModel.Parameter(serviceType, "service"));
        for (VariableElement param : method.getParameters()) {
            params.add(new PathCodeModel.Parameter(
                param.asType().toString(),
                param.getSimpleName().toString()
            ));
        }

        return new PathCodeModel.BridgeMethod(
            methodName,
            returnType,
            params,
            buildMethodBody(serviceElement, method)
        );
    }

    private String determinePathType(TypeMirror returnType) {
        String typeName = returnType.toString();
        if (typeName.startsWith("Maybe<")) return "MaybePath<" + extractTypeArg(typeName) + ">";
        if (typeName.startsWith("Either<")) return "EitherPath<" + extractTypeArgs(typeName) + ">";
        if (typeName.startsWith("Try<")) return "TryPath<" + extractTypeArg(typeName) + ">";
        if (typeName.startsWith("IO<")) return "IOPath<" + extractTypeArg(typeName) + ">";
        return "GenericPath<?, " + extractTypeArg(typeName) + ">";
    }

    private void writeSource(PrintWriter writer, PathCodeModel model) {
        // Package declaration
        if (!model.packageName().isEmpty()) {
            writer.println("package " + model.packageName() + ";");
            writer.println();
        }

        // Imports
        for (String imp : model.imports()) {
            writer.println("import " + imp + ";");
        }
        writer.println();

        // Class declaration
        writer.println("@Generated(\"org.higherkindedj.hkt.path.processor.PathProcessor\")");
        writer.println("public final class " + model.className() + " {");
        writer.println();
        writer.println("    private " + model.className() + "() {}");
        writer.println();

        // Bridge methods
        for (PathCodeModel.BridgeMethod method : model.bridgeMethods()) {
            writeBridgeMethod(writer, method);
        }

        writer.println("}");
    }

    private void writeBridgeMethod(PrintWriter writer, PathCodeModel.BridgeMethod method) {
        writer.print("    public static " + method.returnType() + " " + method.name() + "(");
        writer.print(method.parameters().stream()
            .map(p -> p.type() + " " + p.name())
            .collect(java.util.stream.Collectors.joining(", ")));
        writer.println(") {");
        writer.println("        " + method.body());
        writer.println("    }");
        writer.println();
    }
}
```

## Generated Code Examples

### From @PathSource

```java
// Input
@PathSource(witness = MaybeKind.Witness.class)
public sealed interface Maybe<A> permits Just, Nothing {
    <B> Maybe<B> map(Function<? super A, ? extends B> f);
    <B> Maybe<B> flatMap(Function<? super A, ? extends Maybe<B>> f);
}

// Generated: MaybePath.java
@Generated("org.higherkindedj.hkt.path.processor.PathProcessor")
@NullMarked
public final class MaybePath<A> implements Recoverable<Unit, A> {

    private final Maybe<A> value;

    private MaybePath(Maybe<A> value) {
        this.value = Objects.requireNonNull(value);
    }

    public Maybe<A> run() {
        return value;
    }

    @Override
    public <B> MaybePath<B> map(Function<? super A, ? extends B> f) {
        return new MaybePath<>(value.map(f));
    }

    public <B> MaybePath<B> via(Function<? super A, ? extends Maybe<B>> f) {
        return new MaybePath<>(value.flatMap(f));
    }

    public <B> MaybePath<B> then(Function<? super A, ? extends Maybe<B>> f) {
        return via(f);
    }

    // ... additional methods ...

    public static <A> MaybePath<A> of(Maybe<A> maybe) {
        return new MaybePath<>(maybe);
    }

    public static <A> MaybePath<A> just(A value) {
        return new MaybePath<>(Maybe.just(value));
    }

    public static <A> MaybePath<A> nothing() {
        return new MaybePath<>(Maybe.nothing());
    }
}
```

### From @GeneratePathBridge

```java
// Input
@GeneratePathBridge
public interface UserRepository {

    @PathVia("find")
    Maybe<User> findById(Long id);

    @PathVia
    Maybe<User> findByEmail(String email);

    @PathVia
    Either<DbError, User> save(User user);

    @PathVia
    Either<DbError, List<User>> findAll();
}

// Generated: UserRepositoryPaths.java
@Generated("org.higherkindedj.hkt.path.processor.PathProcessor")
@NullMarked
public final class UserRepositoryPaths {

    private UserRepositoryPaths() {}

    public static MaybePath<User> find(UserRepository repository, Long id) {
        return MaybePath.of(repository.findById(id));
    }

    public static MaybePath<User> findByEmail(UserRepository repository, String email) {
        return MaybePath.of(repository.findByEmail(email));
    }

    public static EitherPath<DbError, User> save(UserRepository repository, User user) {
        return EitherPath.of(repository.save(user));
    }

    public static EitherPath<DbError, List<User>> findAll(UserRepository repository) {
        return EitherPath.of(repository.findAll());
    }

    // Composed operations (if generateComposed = true)
    public static EitherPath<DbError, User> findAndSave(
            UserRepository repository,
            Long id,
            Function<User, User> modifier) {
        return find(repository, id)
            .toEitherPath(DbError.notFound(id))
            .map(modifier)
            .via(user -> save(repository, user).run());
    }
}
```

## Testing Annotation Processor

### Compile-Testing Setup

```java
package org.higherkindedj.hkt.path.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.*;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class PathProcessorTest {

    @Test
    @DisplayName("@PathSource generates valid Path wrapper")
    void pathSourceGeneratesWrapper() {
        Compilation compilation = javac()
            .withProcessors(new PathProcessor())
            .compile(JavaFileObjects.forSourceString("test.MyEffect",
                """
                package test;
                import org.higherkindedj.hkt.path.annotation.PathSource;
                import java.util.function.Function;

                @PathSource(witness = MyEffectKind.Witness.class)
                public interface MyEffect<A> {
                    <B> MyEffect<B> map(Function<? super A, ? extends B> f);
                    <B> MyEffect<B> flatMap(Function<? super A, ? extends MyEffect<B>> f);
                }

                interface MyEffectKind {
                    interface Witness {}
                }
                """
            ));

        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("test.MyEffectPath")
            .contentsAsUtf8String()
            .contains("public final class MyEffectPath<A>");
    }

    @Test
    @DisplayName("@GeneratePathBridge generates bridge methods")
    void pathBridgeGeneratesMethods() {
        Compilation compilation = javac()
            .withProcessors(new PathProcessor())
            .compile(JavaFileObjects.forSourceString("test.MyService",
                """
                package test;
                import org.higherkindedj.hkt.path.annotation.*;
                import org.higherkindedj.hkt.maybe.Maybe;

                @GeneratePathBridge
                public interface MyService {
                    @PathVia Maybe<String> findName(Long id);
                }
                """
            ));

        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("test.MyServicePaths")
            .contentsAsUtf8String()
            .contains("public static MaybePath<String> findName");
    }

    @Test
    @DisplayName("Invalid @PathSource fails with clear error")
    void invalidPathSourceFails() {
        Compilation compilation = javac()
            .withProcessors(new PathProcessor())
            .compile(JavaFileObjects.forSourceString("test.BadEffect",
                """
                package test;
                import org.higherkindedj.hkt.path.annotation.PathSource;

                @PathSource(witness = Object.class)
                public interface BadEffect<A> {
                    // Missing map() and flatMap()
                }
                """
            ));

        assertThat(compilation).failed();
        assertThat(compilation)
            .hadErrorContaining("must have a map() method");
    }
}
```

## Summary

Annotation-driven assembly provides:

1. **Zero Boilerplate**: Service bridges generated automatically
2. **Type Safety**: Compile-time validation of annotations
3. **Consistency**: All generated code follows same patterns
4. **Extensibility**: Custom types get Path support via @PathSource
5. **IDE Support**: Generated code is indexed and navigable
6. **Future-Proof**: Foundation for additional code generation

Building this from day one ensures the API surface is designed for generation, avoiding costly rewrites later.
