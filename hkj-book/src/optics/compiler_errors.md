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

### "@GenerateIsos: the iso returned by 'x' names a type variable"

**Cause.** One of the returned `Iso`'s two type arguments is, or contains, a type variable — `<T> Iso<Box<T>, T> boxIso()`, or an instance method of a `Holder<X>` returning `Iso<Box<X>, X>`. What gets generated is a `public static final` field, and a field has nowhere to declare one, so it would name a variable nothing brings into scope.

Note this is about what the *iso* names, not what the method declares: `<T> Iso<Box, String> boxIso()` is fine, because `T` is inferred at the call and never reaches the field's type.

**Fix.** Give the iso concrete type arguments where the method is declared (`Iso<Box<String>, String>`), or drop `@GenerateIsos` and call the method directly.

### "@GenerateIsos: 'x' is not static"

**Cause.** The annotated method is an instance method. The generated field initialises itself with a static call, and there is no instance to make it on.

**Fix.** Make the method `static`.

### "@GenerateIsos: 'x' takes parameters"

**Cause.** The generated field initialises itself by calling the method with no arguments, and there is nothing for it to pass.

**Fix.** Take the arguments away, or drop `@GenerateIsos` and call the method directly.

### "@GenerateIsos: 'x' does not return an Iso with both type arguments"

**Cause.** The generated field is typed from the two arguments of the returned `Iso`. A `void`, primitive, array, raw or non-`Iso` return has nothing to read them off.

**Fix.** Return `Iso<S, A>` naming both, as `Iso<Point, Tuple2<Integer, Integer>>`.

### "@GenerateIsos: 'x' cannot be reached from 'p'"

**Cause.** The generated class lives in package `p` and calls the method from there, but the method — or a type enclosing it — is `private`, `protected` or package-private somewhere else. Most often seen with `targetPackage`.

**Fix.** Make the method and its enclosing types public, or generate into the package they are already visible from.

---

## `@ImportOptics` and `OpticsSpec` interfaces

### "@ImportOptics: Lens method 'x' carries no copy strategy annotation"

**Cause.** A method on an `OpticsSpec` interface returning `Lens<S, A>` lacks one of `@Wither`, `@ViaBuilder`, `@ViaConstructor`, or `@ViaCopyAndSet`.

**Fix.** Add the appropriate hint based on how the source type is copied. See [Optics for External Types](importing_optics.md) and [Database Records with JOOQ](copy_strategies.md) for the full strategy table.

### "'XOpticsSpec.foo' is a default method"

**Cause.** A spec interface declares a `default` method. A method body cannot be read during annotation processing, so there is nothing for the generated class to carry.

**Fix.** Keep the spec interface to annotated abstract methods. Composed optics belong in a `static` method on the interface, or in an ordinary utility class; either one calls the generated statics by name, for example `JsonNodeOptics.object().andThen(...)`.

### "'XOpticsSpec' declares OpticsSpec<S>, which is a type variable"

**Cause.** The spec interface is generic, and its own type parameter is the source type: `interface BoxOpticsSpec<S extends Box> extends OpticsSpec<S>`. Optics are generated against one named type, read for its members and rebuilt through its constructor, wither or setter, so a type parameter standing for whatever a caller picks has nothing to generate from. An array source type produces the same diagnostic with a different opening, `declares OpticsSpec<String[]>, which is an array type`, and the same remedy.

**Fix.** Name the type the optics are for as the type argument: `OpticsSpec<Box>`. Where the bound names a single type, the message suggests it for you.

A source type that is itself generic is supported, and the spec names its own type parameters: `interface BoxOpticsSpec<U> extends OpticsSpec<Box<U>>` generates `static <U> Lens<Box<U>, String> label()`. See [Spec Interfaces](optics_spec_interfaces.md#generic-spec-interfaces) for which parameters a generated method declares. It is only a bare type variable, standing for the whole source type, that has no source to read.

### "@InstanceOf: target subtype not assignable to source type"

**Cause.** The class passed to `@InstanceOf(SubType.class)` is not a subclass of the optic's source type.

**Fix.** Verify that `SubType` extends or implements the spec's `<S>` parameter. If you are working with sum types that don't use a sealed hierarchy (such as Jackson's pre-3.x `JsonNode`), use `@MatchWhen` with predicate and getter method names instead.

### "@ViaCopyAndSet: copyConstructor names '...', which does not resolve to a type"

**Cause.** `copyConstructor` is a plain string, resolved as a fully qualified class name only: it is not read against the spec interface's imports, and it takes no type arguments.

**Fix.** Give the class's fully qualified name (`com.example.BaseConfig`; a nested class is `com.example.Outer.Base`), the class alone without type arguments — the processor supplies those from the source type's own `extends` clause. Drop the attribute to pass the source unchanged.

### "@ViaCopyAndSet: copyConstructor names '...', which 'S' does not extend or implement"

**Cause.** The generated setter passes the source to the copy constructor as `(ParameterType) source`, so only a supertype of `S` can be named there.

**Fix.** Name a class or interface `S` extends or implements, or drop the attribute.

### "@ViaCopyAndSet: copyConstructor names '...', which is not public and so cannot be named from '...'"

**Cause.** The generated optics class has to write the cast, so it has to be able to name the type. A package-private supertype is invisible from the package the optics class is generated into, even though `new S(source)` — which never names it — would have compiled.

**Fix.** Name a public supertype, generate into that package with `@ImportOptics(targetPackage = ...)`, or drop the attribute.

### "@ViaCopyAndSet: copyConstructor names '...', which '...' reaches as '...', and no constructor accepts"

**Cause.** The name is a genuine supertype, but no single-argument constructor of `S` takes the type `S` actually reaches it as — `java.lang.Object` and marker interfaces such as `Serializable` reach this often. The message names both the type you gave and the one `S` reaches, which differ when `S`'s own `extends` clause pins the arguments: `class PNode<X> extends PBase<String>` reaches `PBase` as `PBase<String>`, whatever `X` is.

**Fix.** Name a supertype of `S` that one of the listed constructors takes, as the class alone without type arguments, or drop the attribute. The list carries type arguments and the attribute does not, so read it to recognise your supertype in it rather than to copy from it — and a listed type that is not a supertype of `S` cannot be named at all. The attribute is only needed when the copy constructor is overloaded — see [Copy Strategies](copy_strategies.md#viacopyandset-legacy-types-with-a-copy-constructor-and-setters).

### "@ViaCopyAndSet: '...' is written with a wildcard type argument"

**Cause.** The source type carries a wildcard, `OpticsSpec<Node<?>>`, and the strategy rebuilds it through a constructor. `new Node<?>(...)` is not something that can be written, whatever the arguments. `@ViaConstructor` reports the same thing for the same reason. An inner class draws the sibling message, because its constructor call needs an enclosing instance the generated class has no way to reach.

**Fix.** Name the type the wildcard stands for, or switch to `@Wither`, which rebuilds through a method and names no constructor — a wildcard source type is no obstacle there.

### "@MatchWhen: predicate / getter method not found on source"

**Cause.** The string passed to `@MatchWhen(predicate = "isFoo", getter = "asFoo")` does not match a real method on the source type.

**Fix.** Check the method names against the source type's API. Both methods must take no arguments; the predicate returns `boolean` and the getter returns the prism's target type.

---

## `@GeneratePathBridge` and `@PathVia`

### "Unsupported return type for @PathVia"

**Cause.** The method returns something outside the bridged table — `Optional`, `Maybe`, `Either`, `Try`, `Validated`, `IO`. `CompletableFuture` is the one most often met here.

**Fix.** Return one of the six, or drop `@PathVia` and wrap the call by hand.

### "@PathVia: the return type is a raw 'Optional'"

**Cause.** The effect is written without its type argument. The bridge would pass it to the `Path` factory as an unchecked conversion, and the warning lands in generated source — the one file where a `@SuppressWarnings` cannot be added.

**Fix.** Name the type argument: `Optional<Item>` rather than `Optional`.

### "@PathVia: the error type of the returned 'Validated' is the wildcard '...'"

**Cause.** A `Validated` bridge names its error type twice — in the `ValidationPath` it returns, and in the `Semigroup` it asks the caller for. A wildcard is a *different* captured type at each mention, so there is no argument a caller could pass that satisfies both.

Only the error position is affected. `Validated<String, ? extends Number>` is fine, and so are wildcards in `Optional`, `Maybe`, `Either` and `Try` returns: those name the captured type once and javac unifies it.

**Fix.** Name the error type.

### "@PathVia: a static / private interface method cannot be called that way"

**Cause.** The bridge reaches its delegate through an interface reference, which gets at abstract and `default` members and nothing else.

**Fix.** Make the method an abstract or `default` instance method, or drop `@PathVia` from it.

### "@GeneratePathBridge: the bound on 'T' names '...', which cannot be reached from '...'"

**Cause.** The bridge repeats the interface's bounds in its own declaration, and `targetPackage` has put that declaration somewhere the bound is not visible.

**Fix.** Make the bound's type public, or drop `targetPackage` so the bridge is written beside the interface.

### "@GeneratePathBridge: no @PathVia method was found" (a warning)

**Cause.** Neither the interface nor anything it extends has a `@PathVia` method, so the bridge is written with a constructor and nothing else.

Inherited methods do count — a bridge for `StringStore extends Store<String>` picks up `Store`'s `@PathVia` methods, read under `String`. Seeing this warning means there were none anywhere.

**Fix.** Put `@PathVia` on the methods to bridge, or drop `@GeneratePathBridge`.

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
