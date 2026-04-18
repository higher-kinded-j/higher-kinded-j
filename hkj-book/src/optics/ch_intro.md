# Optics

<img src="../images/The-crystal-ball-of-data.jpg" alt="A crystal ball revealing paths through nested data structures" style="width: 100%;" />

> _"What we see depends mainly on what we look for."_
>
> – John Lubbock, *The Beauties of Nature and the Wonders of the World We Live In*

---

Immutable records in Java are safer, easier to reason about, and, when you need to change something three layers down, a bit of an ordeal. The conventional approach is to copy and rebuild each layer by hand; the result is the sort of code nobody enjoys writing and reviewers quietly resent reading.

An **optic** is a first-class, composable path from a whole structure to one or more of its parts. Once you have the path, reading, writing, and transforming the focused value all come for free, and the paths themselves compose: a lens into a record composed with a prism into a sealed field composed with a traversal over a list is a single optic that knows how to operate on the whole route.

Higher-Kinded-J provides optics for every common access pattern: single required fields, variants of sealed types, optional fields, lossless conversions, and bulk operations on collections. The five sections of this chapter take you from the foundational optics through the Java-friendly APIs and into a cookbook of practical recipes.

---

## The Optics Hierarchy

Before diving in, it helps to see how the optic types relate to one another:

```
                    ┌─────────┐
                    │  Fold   │  (read-only, zero-or-more)
                    └────┬────┘
                         │
              ┌──────────┴──────────┐
              │                     │
         ┌────┴────┐          ┌─────┴─────┐
         │ Getter  │          │ Traversal │  (read-write, zero-or-more)
         └────┬────┘          └─────┬─────┘
              │                     │
              │               ┌─────┴─────┐
              │               │           │
         ┌────┴────┐    ┌─────┴─────┐ ┌───┴───┐
         │  Lens   │    │  Affine   │ │Setter │
         └────┬────┘    └─────┬─────┘ └───────┘
              │         (zero-or-one)
              │               │
              │         ┌─────┴─────┐
              │         │   Prism   │
              │         └─────┬─────┘
              │               │
              └───────┬───────┘
                 ┌────┴────┐
                 │   Iso   │  (exactly-one, reversible)
                 └─────────┘
```

Arrows indicate "can be used as" relationships. A Lens can stand in wherever a Getter or Fold is expected; an Iso, the most specific optic, can stand in as any of the others. Affine sits between Traversal and Prism, representing precisely zero-or-one focus, which makes it ideal for optional fields.

---

~~~admonish info title="In This Chapter"
- **Fundamentals** – Lens, Prism, Affine, and Iso: the four optics for working with single values. Introduces the composition rules and the paired-lens pattern for fields that share invariants. Start here if you are new to optics.
- **Collections** – Traversals and Folds for zero-or-more focus, plus the asymmetric specialists Getter (read-only) and Setter (write-only). Covers the ready-made traversals for Java's standard collections and monoid-based aggregation.
- **Precision and Filtering** – Narrow focus by predicate or index. Filtered and indexed traversals, the `Each`, `At`, and `Ixed` type classes, character-level string traversals, and advanced Prism patterns like `nearly` and `doesNotMatch`.
- **Java-Friendly APIs** – Three complementary APIs that make optics feel native to Java: the Focus DSL for path-based navigation, the Fluent API for validation-aware updates, and the Free Monad DSL for programs-as-data. Backed by annotation-driven code generation (`@GenerateLenses`, `@GenerateFocus`, `@GeneratePrisms`, and friends).
- **Integration and Recipes** – A complete walkthrough composing Lens, Prism, and Traversal into a validation pipeline, integration with the library's core types (Either, Maybe, Validated, Optional), and a cookbook of ready-to-use solutions for the nested-update problems you will actually meet in production.
~~~

---

## Chapter Contents

1. [Fundamentals](ch1_intro.md) - Lens, Prism, Affine, Iso, composition rules, coupled fields
2. [Collections](ch2_intro.md) - Traversal, Fold, Getter, Setter, and collection patterns
3. [Precision and Filtering](ch3_intro.md) - Filtered, indexed, and predicate-based optics
4. [Java-Friendly APIs](ch4_intro.md) - Focus DSL, Fluent API, Free Monad DSL, code generation
5. [Integration and Recipes](ch5_intro.md) - Validation workflows, core-type integration, cookbook

---

~~~admonish tip title="Start Here"
New to optics? Begin with [Fundamentals](ch1_intro.md). If you only want to update a nested field in a record right now, skip straight to the [Focus DSL](focus_dsl.md) in Java-Friendly APIs and come back to the foundational material when you need it.
~~~

---

**Next:** [Fundamentals](ch1_intro.md)
