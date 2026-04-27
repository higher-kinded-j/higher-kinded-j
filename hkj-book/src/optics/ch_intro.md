# Optics

<img src="../images/The-crystal-ball-of-data.jpg" alt="A crystal ball revealing paths through nested data structures" style="width: 100%;" />

> _"What we see depends mainly on what we look for."_
>
> – John Lubbock, *The Beauties of Nature and the Wonders of the World We Live In*

---

Immutable records in Java are safer, easier to reason about, and, when you need to change something three layers down, a bit of an ordeal. The conventional approach is to copy and rebuild each layer by hand; the result is the sort of code nobody enjoys writing and reviewers quietly resent reading.

An **optic** is a first-class, composable path from a whole structure to one or more of its parts. Once you have the path, reading, writing, and transforming the focused value all come for free, and the paths themselves compose: a lens into a record composed with a prism into a sealed field composed with a traversal over a list is a single optic that knows how to operate on the whole route.

Higher-Kinded-J's optics are **annotation-driven**. You write a record, add `@GenerateLenses` and `@GenerateFocus`, and the processor writes a typed path builder for you. The same applies to sealed types (`@GeneratePrisms`), collections (`@GenerateTraversals`), and even types you can't modify (`@ImportOptics` for Jackson, JOOQ, JDK types). No boilerplate, no runtime reflection, no manual composition unless you want it.

```java
@GenerateLenses @GenerateFocus
public record Street(String name, int number) {}

@GenerateLenses @GenerateFocus
public record Address(Street street, String city) {}

@GenerateLenses @GenerateFocus
public record User(String name, Address address) {}

User updated = UserFocus.address().street().name().set("New Street", user);
```

The same record can carry several annotations, each generating its own companion class for a different use case. The five sections of this chapter take you from the foundational optics through the Java-friendly APIs and into a cookbook of practical recipes.

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

1. [Quickstart](quickstart.md) - Three runnable examples in 100 lines
2. [Annotations at a Glance](annotations_at_a_glance.md) - Every annotation, what it generates, and when to use it
3. [Fundamentals](ch1_intro.md) - Lens, Prism, Affine, Iso, composition rules, coupled fields
4. [Collections](ch2_intro.md) - Traversal, Fold, Getter, Setter, and collection patterns
5. [Precision and Filtering](ch3_intro.md) - Filtered, indexed, and predicate-based optics
6. [Java-Friendly APIs](ch4_intro.md) - Focus DSL, Fluent API, Free Monad DSL, code generation
7. [Integration and Recipes](ch5_intro.md) - Validation workflows, core-type integration, cookbook

---

~~~admonish tip title="Start Here"
- **Want to see optics in action?** Read the [Quickstart](quickstart.md), three runnable examples in 100 lines.
- **Looking for a specific annotation?** [Annotations at a Glance](annotations_at_a_glance.md) is the lookup table.
- **Just need to update a nested record right now?** Skip straight to the [Focus DSL](focus_dsl.md) and come back to the foundational material when you need it.
- **New to the concepts?** Start with [Fundamentals](ch1_intro.md).
~~~

---

**Next:** [Quickstart](quickstart.md)
