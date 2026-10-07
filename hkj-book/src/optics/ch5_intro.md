<!-- description: Parse at a boundary with validated prisms, validate a whole structure with modifyF, batch reads into one call and keep an audit trail. -->

# Validation, Batching and Auditing

> *"Anything worth doing is worth doing right."*
>
> – Hunter S. Thompson, *Fear and Loathing in Las Vegas*

---

This group covers the edges of a service, and you can skip it until one of them comes up. A value is parsed at a boundary, an update is checked by a validating effect, a batch of reads goes out as one call, and a record keeps what changed. The everyday versions are among the chapter's first pages: [Updates That Can Fail](fluent_api.md) for one checked update, and [Many Edits at Once](multi_edit.md) for a PATCH. Here is the mechanism under the first of those, before any theory. One path runs from a form, through a sealed principal, across a list of permissions, down to each permission's name; one call validates every one of them and collects the failures. Every line compiles and runs on every build, and a test holds its result:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/validation/ValidationBook.java:payoff}}
```

The sample `Form` holds a `User` with two permissions, `PERM_READ` and `PERM_FLY`, and only the first is on the allowed list. `ValidationBook` is the compiled example's own class, not library API.

~~~admonish tip title="Why this matters"
Four optics of three different kinds compose into one value, and that value is reusable in both directions: run it with a plain function to update every permission, or with an [`Applicative`](../glossary/type-classes.md#applicative) to validate them and accumulate the failures. The prism in the middle is what makes it safe. A `Form` holding a `Guest` has nothing in focus, so the same expression returns a clean result rather than a `ClassCastException`, and no branch had to be written for that case.
~~~

---

## Pages in this group

1. [Validated Prisms](validated_prism.md): Parse at a boundary, build back without failing
2. [Deep Validation with `modifyF`](composing_optics.md): A validation pipeline through `modifyF`
3. [Optic-Driven Batching](optic_batching.md): N foci, one backend call
4. [Plan Introspection and Guardrails](optic_batching_guardrails.md): See and bound a batch before it runs
5. [Auditing Complex Data](auditing_complex_data_example.md): An audit trail of every change

[Capstone: Effects Meet Optics](../effect/capstone_focus_effect.md) combines optics with [Effect Paths](../glossary/effect-paths.md#effect-path) in a single pipeline.

---

**Previous:** [Focus DSL with External Libraries](focus_external_bridging.md)
**Next:** [Validated Prisms](validated_prism.md)
