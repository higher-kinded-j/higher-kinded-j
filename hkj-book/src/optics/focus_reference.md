# Focus DSL Reference

~~~admonish info title="What You'll Learn"
- When to reach for the Focus DSL and when to compose optics by hand
- The patterns that come up most: batch updates, safe deep access, validation
- Performance considerations, and how to keep hot paths cheap
- Customising the generated code: target package, navigators, depth limits, field filters
- Common pitfalls, compiler errors, and the answers to the questions that keep recurring
~~~

This page is the lookup shelf. The teaching lives in [Focus DSL](focus_dsl.md), [Navigation and Composition](focus_navigation.md) and [Type Class and Effect Integration](focus_effects.md); come here when you already know the shape of what you want.

---

## Focus DSL or Manual Composition?

**Reach for the Focus DSL** when you are navigating nested structures and want the IDE to lead:

<!-- verify -->
```java
List<String> names =
    CompanyFocus.departments()
        .via(DepartmentFocus.employees())
        .via(EmployeeFocus.name())
        .getAll(company);
```

**Compose optics by hand** for custom optics (computed properties, validated updates), for reusable optic libraries, and where the optic itself carries conditional logic.

**In practice, do both.** Navigate with Focus, then extract the composed optic once and reuse it:

<!-- verify -->
```java
// Build the path once, keep the optic
Traversal<Company, String> allEmails =
    CompanyFocus.departments()
        .via(DepartmentFocus.employees())
        .via(EmployeeFocus.email())
        .toTraversal();

List<String> emails = Traversals.getAll(allEmails, company);
```

---

## Common Patterns

### Pattern 1: Batch Updates, Narrowed by a Predicate

`filter` lives on `TraversalPath`, so narrow first and compose afterwards:

<!-- verify -->
```java
Company updated =
    CompanyFocus.departments()
        .filter(d -> d.name().equals("Engineering"))
        .via(DepartmentFocus.employees())
        .via(EmployeeFocus.age())
        .modifyAll(age -> age + 1, company);
```

### Pattern 2: Safe Deep Access

Index into a collection from the path that still focuses it, then keep navigating:

<!-- verify -->
```java
Optional<String> firstEmail =
    FocusPath.of(CompanyLenses.departments())
        .<Department>at(0)
        .via(DepartmentFocus.employees())
        .via(EmployeeFocus.email())
        .preview(company);

String email = firstEmail.orElse("nobody@example.com");
```

Two details earn their place. `.at(0)` needs its element type spelled out, because nothing in the argument list mentions `Department`. And `.at(0)` yields an `AffinePath`, which composing with a traversal widens back out, so the read is `preview` (the first focus, if any) rather than `getOptional`.

### Pattern 3: Validation Across a Traversal

<!-- verify -->
```java
Validated<List<String>, Company> checked =
    OpticOps.modifyAllValidated(
        company,
        CompanyFocus.departments()
            .via(DepartmentFocus.employees())
            .via(EmployeeFocus.age())
            .toTraversal(),
        Fixture::validateAge);
// Invalid(["Invalid age: 17"]) for the fixture above
```

---

## Performance Considerations

A Focus path is a thin wrapper over the underlying optic:

- **Path creation**: a few small objects, one per segment
- **Traversal**: identical to the optic it wraps
- **Memory**: one extra object per path segment

The rule that matters is *build the path once*. Rebuilding it inside a loop re-runs the composition on every iteration:

<!-- verify -->
```java
// Compose once, outside the loop
Traversal<Company, String> deptNames =
    CompanyFocus.departments().via(DepartmentFocus.name()).toTraversal();

for (Company c : companies) {
  List<String> names = Traversals.getAll(deptNames, c);
}
```

---

## Customising Generated Code

```java
// Where the generated class lands
@GenerateFocus(targetPackage = "com.myapp.optics.focus")
record User(String name) {}

// Fluent cross-type navigation
@GenerateFocus(generateNavigators = true)
record Company(String name, Address headquarters) {}

// How deep navigator generation goes (default 3)
@GenerateFocus(generateNavigators = true, maxNavigatorDepth = 2)
record Organisation(Division division) {}

// Which fields get a navigator
@GenerateFocus(generateNavigators = true, includeFields = {"homeAddress"})
record Person(String name, Address homeAddress, Address workAddress) {}

@GenerateFocus(generateNavigators = true, excludeFields = {"backup"})
record Config(Settings main, Settings backup) {}

// Widen Map, array and third-party collection fields at the static method
@GenerateFocus(widenCollections = true)
record Warehouse(Map<String, Integer> inventory) {}
```

---

## Integration with the Free Monad DSL

`OpticPrograms` accepts Focus paths directly, so a path can become a step in a program that is interpreted later:

<!-- verify -->
```java
Free<OpticOpKind.Witness, Company> program =
    OpticPrograms.get(company, CompanyFocus.name())
        .flatMap(
            name ->
                name.startsWith("Acme")
                    ? OpticPrograms.modifyAll(
                        company,
                        CompanyFocus.departments()
                            .via(DepartmentFocus.employees())
                            .via(EmployeeFocus.age())
                            .toTraversal(),
                        age -> age + 1)
                    : OpticPrograms.pure(company));

Company result = OpticInterpreters.direct().run(program);
```

Swap `direct()` for `logging()` or `validating()` to run the same program another way. See [Free Monad DSL](free_monad_dsl.md) and [Interpreters](interpreters.md).

---

## Common Pitfalls

**Do not rebuild paths in a loop.** Hoist the path (or the extracted optic) above the loop, as in the performance section above.

**Do not reach for `get` on a path that may miss.** Use the operation the path type guarantees:

<!-- verify -->
```java
String name = EmployeeFocus.name().get(alice);              // FocusPath: always there
Optional<String> email = EmployeeFocus.email().getOptional(alice);   // AffinePath: may be empty
List<Employee> all = DepartmentFocus.employees().getAll(department); // TraversalPath: many
```

**Do not expect a collection field to be container-level.** `CompanyFocus.departments()` focuses each department; the `List` itself lives behind `FocusPath.of(CompanyLenses.departments())`.

---

## Troubleshooting

### "Cannot infer type arguments for traverseOver"

Java cannot recover the witness from the `Traverse` argument. Supply it:

```java
TraversalPath<User, Role> allRoles =
    rolesPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);
```

The same applies to receiver-position generics generally: `Instances.validated(Semigroups.<String>list())` needs its witness for the same reason.

### "Incompatible types" on a long chain

Break the chain into intermediate variables with declared types. The first line that will not compile is the step that widened differently from your expectation, and naming the types makes it obvious which one.

### "Sealed or non-sealed local classes are not allowed"

Sealed interfaces cannot be declared inside a method. Move them to class level, alongside their permitted records.

### "Method reference `::new` does not work as a BiFunction"

A single-component record's canonical constructor is a `Function`, not a `BiFunction`, so `Lens.of(Outer::inner, Outer::new)` will not compile. Write the lambda: `Lens.of(Outer::inner, (o, i) -> new Outer(i))`.

### `getAll()` returns an empty list

Check, in order: whether an `AffinePath` in the chain actually matches (`matches(source)`), whether an `instanceOf` step matches the runtime type, and whether the source collection is empty. `traced()` shows you which step lost the focus, and `count(source)` tells you how many survived.

---

## FAQ

### When should I use `each()` versus `traverseOver()`?

| Field is | Use |
|----------|-----|
| `List<T>`, `Set<T>` or `Collection<T>` | `each()`, already applied by the generated method with the `Each` that rebuilds the container |
| `Kind<F, T>` on a `@GenerateFocus` record | nothing: the processor generates the traversal |
| `Kind<F, T>` behind a hand-written lens | `traverseOver(SomeTraverse.INSTANCE)` |

### Why pass `Instances.monadError(maybe())` to `modifyF()` rather than an Applicative?

`modifyF` asks for a `Functor` (on `FocusPath`) or an `Applicative` (on the wider paths), and every `Monad` is both. Higher-Kinded-J does not ship a separate `MaybeApplicative` because the monad instance already provides those operations:

<!-- verify -->
```java
Kind<MaybeKind.Witness, Employee> result =
    EmployeeFocus.email().modifyF(Fixture::checkEmail, alice, Instances.monadError(maybe()));
```

### Can I use the Focus DSL with third-party types?

Yes. Navigate as far as the generated paths go, then `.via()` a hand-written or generated optic for the external type. [Optics for External Types](importing_optics.md) generates those optics for you, and [Focus DSL with External Libraries](focus_external_bridging.md) works through the bridge end to end.

### How do I handle nullable fields?

In order of preference:

1. **Model the absence as `Optional<T>`.** The generated method applies `.some()` and hands you an `AffinePath`, with no extra step at all.
2. **Annotate the component `@Nullable`.** Any of the six recognised annotations will do, wherever that annotation's own `@Target` puts it, and the generated method applies `.nullable()` for you.
3. **Chain `.nullable()`** on the generated `FocusPath`. It turns null into an empty focus.
4. **Build the path with `AffinePath.ofNullable(getter, setter)`** when there is no generated companion to start from.

Two rules govern the annotated form. A container decides its own widening, so `@Nullable List<T>` is `.each()` and `@Nullable Optional<T>` is `.some()`. And position counts as Java defines it: `String @Nullable []` is a nullable array, while `@Nullable String[]` and `List<@Nullable String>` annotate the elements and leave the field itself non-null.

### Can I build Focus paths at runtime?

Focus paths are designed for compile-time type safety. When the path is only known at runtime, build the optic dynamically and wrap it: `TraversalPath.of(someTraversal)` and `FocusPath.of(someLens)` accept any optic.

---

~~~admonish info title="Key Takeaways"
* **Build the path once.** Path creation is cheap but not free, and hoisting it out of a loop is the only performance rule that matters.
* **`filter` narrows, then you keep composing.** It is on `TraversalPath`, so apply it before the next `.via()`.
* **Let the path type pick the read.** `get`, `getOptional`, `preview` and `getAll` are not interchangeable; the type is telling you what it can promise.
* **The annotation has more knobs than most people use.** `targetPackage`, `generateNavigators`, `maxNavigatorDepth`, `includeFields`, `excludeFields` and `widenCollections` between them cover nearly every generated-code complaint.
* **A path is an optic underneath.** `toLens()`, `toAffine()` and `toTraversal()` hand it to `OpticOps`, `OpticPrograms` or anything else that speaks optics.
~~~

~~~admonish info title="Hands-On Learning"
- [Tutorial 12: Focus DSL](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial12_FocusDSL.java) (10 exercises, ~10 minutes)
- [Tutorial 13: Advanced Focus DSL](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial13_AdvancedFocusDSL.java) (8 exercises, ~10 minutes)
- [Tutorial 19: Navigator Generation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial19_NavigatorGeneration.java) (8 exercises, ~10 minutes)
- [Tutorial 20: Custom Container Navigation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial20_ContainerNavigation.java) (4 exercises, ~10 minutes)
~~~

~~~admonish tip title="See Also"
- [Focus DSL](focus_dsl.md): core concepts and path types
- [Lenses](lenses.md): the optic underneath a `FocusPath`
- [Fluent API](fluent_api.md): validation-aware modification through `OpticOps`
- [Free Monad DSL](free_monad_dsl.md): optic programs and interpreters
~~~

~~~admonish tip title="Further Reading"
- **Monocle**: [Focus DSL](https://www.optics.dev/Monocle/docs/focus): scala's equivalent, and the inspiration for this design
~~~

---

**Previous:** [Custom Containers and Code Generation](focus_containers.md)
**Next:** [Optics for External Types](importing_optics.md)
