# Common Compiler Errors

## _Diagnosing what the annotation processor and type checker tell you_

~~~admonish info title="What You'll Learn"
- The most common errors from the `@Generate*` annotations and how to fix them.
- Errors that surface from `@ImportOptics` and `OpticsSpec` interfaces, including the spec-method hint annotations.
- Type-inference traps when chaining the Focus DSL through `.each()`, `.via()`, and `traverseOver`.
- Free Monad DSL pitfalls and the witness-type errors they produce.
~~~

This page is for the moment a build fails and you want to know what the message means. Errors are grouped by the annotation or feature most likely to have produced them.

---

## `@GenerateLenses` / `@GenerateFocus` / `@GenerateTraversals`

### "cannot find symbol: class XLenses"

**Cause.** The annotation processor has not run yet, or the IDE has not picked up the generated sources directory.

**Fix.** Run a build (`./gradlew build` or `mvn compile`). After the build completes, refresh the project in your IDE so it indexes `build/generated/sources/annotationProcessor/java/main` (Gradle) or `target/generated-sources/annotations` (Maven).

### "annotation @GenerateLenses not allowed on this type"

**Cause.** `@GenerateLenses`, `@GenerateFocus`, `@GenerateFolds`, `@GenerateGetters`, `@GenerateSetters`, and `@GenerateTraversals` only apply to records.

**Fix.** Convert the class to a record. If the type is third-party and you cannot change it, use [`@ImportOptics`](importing_optics.md) on a `package-info.java` or a spec interface instead.

### "annotation @GeneratePrisms not allowed on this type"

**Cause.** `@GeneratePrisms` requires either a `sealed interface` or an `enum`. Plain interfaces and abstract classes are not supported.

**Fix.** Make the type sealed and declare its `permits` clause, or convert to an enum.

### "method must return Iso<...>"

**Cause.** `@GenerateIsos` is applied to a method whose return type is not `Iso`.

**Fix.** Ensure the annotated method returns `Iso<A, B>`. The processor reads the type parameters to generate the static field.

---

## `@ImportOptics` and `OpticsSpec` interfaces

### "no copy strategy specified for lens method"

**Cause.** A method on an `OpticsSpec` interface returning `Lens<S, A>` lacks one of `@Wither`, `@ViaBuilder`, `@ViaConstructor`, or `@ViaCopyAndSet`.

**Fix.** Add the appropriate hint based on how the source type is copied. See [Optics for External Types](importing_optics.md) and [Database Records with JOOQ](copy_strategies.md) for the full strategy table.

### "@InstanceOf: target subtype not assignable to source type"

**Cause.** The class passed to `@InstanceOf(SubType.class)` is not a subclass of the optic's source type.

**Fix.** Verify that `SubType` extends or implements the spec's `<S>` parameter. If you are working with sum types that don't use a sealed hierarchy (such as Jackson's pre-3.x `JsonNode`), use `@MatchWhen` with predicate and getter method names instead.

### "@MatchWhen: predicate / getter method not found on source"

**Cause.** The string passed to `@MatchWhen(predicate = "isFoo", getter = "asFoo")` does not match a real method on the source type.

**Fix.** Check the method names against the source type's API. Both methods must take no arguments; the predicate returns `boolean` and the getter returns the prism's target type.

---

## Focus DSL chains

### `traverseOver` and the higher-kinded witness type

**Cause.** `traverseOver` is generic in the higher-kinded witness type.
This is the same phantom-type-parameter family as
[Effect §1](../effect/compiler_errors.md#1-the-phantom-error-type-e-on-pathright):
on the supported compiler `javac` usually resolves the witness from
context rather than emitting a hard `cannot infer type arguments`
error. The reliable failure mode is not a guaranteed compile error but
*ambiguity* in long Focus chains, where the witness should be stated
explicitly for clarity and to avoid `Object` leaking in.

**Fix.** State the type parameters explicitly when the witness is not
obvious from context:

```java
TraversalPath<User, Role> allRoles =
    rolesPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);
```

### "Incompatible types when chaining .each().via()"

**Cause.** Long Focus DSL chains overflow Java's type inference budget.

**Fix.** Break the chain into intermediate variables; each one carries a concrete type the compiler can reason about:

```java
TraversalPath<Company, Department> depts = CompanyFocus.departments().each();
TraversalPath<Company, Employee>   employees = depts.via(DepartmentFocus.employees()).each();
TraversalPath<Company, Integer>    salaries = employees.via(EmployeeFocus.salary());
```

### "Method reference ::new doesn't work with single-field records as BiFunction"

**Cause.** Java's overload resolution struggles to pick `Foo::new` as a `BiFunction` when the record has a single component.

**Fix.** Use an explicit lambda:

```java
Lens<Outer, Inner> lens = Lens.of(Outer::inner, (o, i) -> new Outer(i));
```

### "Sealed or non-sealed local classes are not allowed"

**Cause.** Defining a sealed interface inside a method body. Java does not permit this regardless of HKJ.

**Fix.** Hoist the sealed interface to class or top level.

---

## Free Monad DSL programs

### "Cannot resolve method 'flatMap(Function<...>)'"

**Cause.** The `Free<F, A>` value's witness type does not match what the surrounding interpreter expects, or you are mixing `Free<OpticOpKind.Witness, ...>` with another `Free` instance.

**Fix.** Confirm that every step in the program uses the same `OpticPrograms` factory methods, and that interpreter calls are paired with the matching witness.

### "Type mismatch: Free<F, A> cannot be converted to A"

**Cause.** Forgetting to call an interpreter. A `Free` program is data; you must run it to get a result.

**Fix.** Pass the program to an interpreter:

```java
Person result = OpticInterpreters.direct().run(program);
```

---

## When the message does not match anything here

1. Is the project rebuilt from clean? Many "cannot find symbol" errors clear after `./gradlew clean build`.
2. Is the annotation processor on the classpath? See [Build Plugins](../tooling/gradle_plugin.md) for the canonical setup.
3. Is the IDE indexing the generated sources directory? Refresh the project after a build.
4. If it is none of these, please file an issue at the [Higher-Kinded-J GitHub repository](https://github.com/higher-kinded-j/higher-kinded-j) with the minimal reproducer and the full error.

---

**Previous:** [Conversions](conversions.md)
**Next:** [Production Readiness](production_readiness.md)
