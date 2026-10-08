<!-- description: Lens, Prism, Affine and Iso, the single-value optics underneath Focus paths, with a page for each. -->

# The Optic Types

> *"The best way to predict the future is to invent it... The second best way is to fund it. The third best way is to map it."*
>
> – Neal Stephenson, *Cryptonomicon*

---

That ends the everyday pages. This group and the ones after it are On demand: read a page when a task needs it, and skip it until then.

Every Focus path is built from optics, and this group gives each single-value optic a page of its own. Read one when a path does something you want to understand, or when you need an optic the Focus DSL does not generate for you. Here is the destination, before any theory: a reusable path from a `User` down to the name of the street they live on, composed by hand from generated lenses, and two one-line updates through it. Every line compiles against the real library on every build:

<!-- verify -->
```java
var streetName = UserLenses.address()
    .andThen(AddressLenses.street())
    .andThen(StreetLenses.name());

User moved = streetName.set("Baker Street", user);
User shouted = streetName.modify(String::toUpperCase, user);
// moved.address().street().name()   -> "Baker Street"
// shouted.address().street().name() -> "FLEET STREET"
// the whole graph is rebuilt for you; user itself is untouched
```

A **Lens** focuses a field that is always there. A **Prism** focuses one variant of a sealed type, which might not match. An **Affine** focuses a value that may be absent, such as an `Optional` field. An **Iso** converts between two shapes of the same information. [Decision Trees](decision_trees.md#tree-1-which-optic-do-i-need) picks one for your data, and [Composition Rules](composition_rules.md) says what type two of them compose to.

~~~admonish tip title="Why this matters"
Three things separate these optics from a bag of getter helpers. They are **generated**: annotate a record and the boilerplate is the processor's problem, forever in sync with the fields. They are **effect-ready**: the same path that does a pure `set` today runs a validating, accumulating, or asynchronous update tomorrow through `modifyF`, because every settable optic is generic over an `Applicative`. And they are **lawful**: the round-trip laws each optic must satisfy are published in `hkj-test` and checked, not assumed.
~~~

---

## Pages in this group

1. [Lenses](lenses.md): A field that is always there
   - [Coupled Fields](coupled_fields.md): Two fields that share an invariant, updated as one
2. [Prisms](prisms.md): One variant of a sealed type
   - [Prism Toolkit](prism_toolkit.md): Ready-made prisms and matching combinators
   - [Advanced Prism Patterns](advanced_prism_patterns.md): Routing, `nearly` and `doesNotMatch`
     - [Advanced Prism Patterns: Recipes](advanced_prism_patterns_recipes.md): Caching and testing recipes
3. [Affines](affine.md): A value that may be absent
4. [Isomorphisms](iso.md): Two shapes of the same information
   - [Profunctor Optics](profunctor_optics.md): Adapting an optic to different types
     - [Profunctor Optics: Recipes](profunctor_optics_recipes.md): Wrapper and migration adapters

~~~admonish info title="Hands-On Learning"
Practise this group in the [Lens & Prism Journey](../tutorials/optics/lens_prism_journey.md) (30 exercises).
~~~

---

**Previous:** [Many Edits at Once](multi_edit.md)
**Next:** [Lenses](lenses.md)
