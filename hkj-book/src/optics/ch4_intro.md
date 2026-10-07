<!-- description: Run a Focus path under an effect, reach custom containers and Kind<F, A> record fields, and join optics to the library's core types. -->

# The Focus DSL in Depth

> *"There was no particular reason to respect the language of the Establishment."*
>
> – Norman Mailer, *The Armies of the Night*

---

The [Focus DSL](focus_dsl.md) and [Collections, Optionals and Sealed Types](focus_navigation.md) cover the paths most code needs. This group covers what those pages leave out: running a path under an effect, containers the processor does not know, and record fields typed `Kind<F, A>`. It also has the prisms and extension methods that join optics to the library's core types. Read a page when one of those comes up, and skip the group until then. As a reminder of where the everyday pages left off, here are a deep read and a bulk update over a company graph whose records carry `@GenerateFocus(generateNavigators = true)`. Every line compiles against the real library and the real annotation processor on every build:

<!-- verify -->
```java
// Company -> headquarters -> city: a single field access, generated
String hq = CompanyFocus.headquarters().city().get(acme);
// "London"

// Company -> departments[] -> staff[] -> salary: every salary, in one expression
Company afterRise =
    CompanyFocus.departments()
        .via(DepartmentFocus.staff())
        .via(EmployeeFocus.salary())
        .modifyAll(s -> s.multiply(new BigDecimal("1.10")), acme);
// every salary is 10% higher; acme itself is untouched
```

~~~admonish tip title="Why this matters"
That chain is not a string, a reflective path expression, or a runtime lookup. `headquarters()`, `city()`, `staff()` and `salary()` are ordinary methods generated at compile time, so a renamed field is a compilation error rather than a `NullPointerException` in production, and the IDE autocompletes each step. It is also a *value*: store the path in a static field, pass it to a method, reuse it. The chain reads like a field access and behaves like an optic.
~~~

`headquarters` chains `.city()` straight off a generated *navigator*, while `departments` is a `List`, which the processor unwraps to a `TraversalPath` over its elements, so its next hop is spelled `.via(...)`. [Collections, Optionals and Sealed Types](focus_navigation.md#fluent-navigation-with-generated-navigators) sets out which fields get which. A path hands its underlying optic to `OpticOps` through `toLens()`, `toAffine()` or `toTraversal()`, so starting with the Focus DSL never locks the other APIs out.

---

## Pages in this group

1. [Type Class and Effect Integration](focus_effects.md): Effectful updates, monoid aggregation and Effect Paths
2. [Custom Containers and Code Generation](focus_containers.md): Container types the processor does not know
3. [Kind Field Support](kind_field_support.md): Automatic traversal for `Kind<F, A>` fields
4. [Core Type Integration](core_type_integration.md): Optics with `Maybe`, `Either`, `Validated` and `Try`
5. [Optics Extensions](optics_extensions.md): Extension methods for lenses and traversals

~~~admonish info title="Hands-On Learning"
Practise the Focus DSL in the [Focus DSL Journey](../tutorials/optics/focus_dsl_journey.md) (9 tutorials, 90 exercises).
~~~

---

**Previous:** [Indexed Access](indexed_access.md)
**Next:** [Type Class and Effect Integration](focus_effects.md)
