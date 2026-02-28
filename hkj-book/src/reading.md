# More Functional Thinking

~~~admonish info title="Two Blog Series, One Journey"
These companion blog series chart a path from foundational type theory to production-ready optics in Java. Start with the **Foundations** if you're new to functional programming in Java, or jump straight to the **Functional Optics** series to see Higher-Kinded-J in action.
~~~

---

## Functional Optics for Modern Java <span style="font-size:0.7em; vertical-align:middle; background:#a6da95; color:#24273a; padding:2px 8px; border-radius:4px; font-weight:bold;">NEW — 6 Part Series</span>

Java records and sealed interfaces make immutable data modelling elegant — but **updating** deeply nested immutable structures still means tedious copy-constructor cascades. This series closes that gap. Across six posts you'll move from the problem, through the theory, and into a fully working production pipeline built with Higher-Kinded-J.

### Part 1 — [The Immutability Gap: Why Java Records Need Optics](https://blog.scottlogic.com/2026/01/09/java-the-immutability-gap.html)

Pattern matching solves the *read* side beautifully — but what about writes? This opening post reveals how operations that should be one-liners balloon into 25+ lines of manual reconstruction, and introduces optics as the composable answer.

> *"Pattern matching is half the puzzle; optics complete it."*

### Part 2 — [Optics Fundamentals: Lenses, Prisms, and Traversals](https://blog.scottlogic.com/2026/01/16/optics-fundamentals.html)

Meet the three core optic types: **Lenses** for product-type fields, **Prisms** for sum-type variants, and **Traversals** for collections. Learn the lens laws, see how `@GenerateLenses` and `@GeneratePrisms` eliminate boilerplate, and discover how small, focused optics compose into powerful navigation paths.

### Part 3 — [Optics in Practice: An Expression Language AST](https://blog.scottlogic.com/2026/01/23/ast-basic-optics.html)

Theory meets code. Build a complete expression language using sealed interfaces and records, then apply lenses, prisms, and the **Focus DSL** to implement constant folding and identity simplification — all without hand-written recursion.

### Part 4 — [The Focus DSL: Traversals and Pattern Rewrites](https://blog.scottlogic.com/2026/01/30/traversals-rewrites.html)

Scale up from single nodes to entire trees. `TraversalPath` and bottom-up/top-down strategies handle recursive descent, while `modifyWhen` and `foldMap` enable filtered updates and aggregation. A multi-pass optimisation pipeline brings constant folding, dead-branch elimination, and common-subexpression detection together.

### Part 5 — [The Effect Path API: Railway-Style Error Handling](https://blog.scottlogic.com/2026/02/09/effect-polymorphic-optics.html)

Introduce effects into optics. `MaybePath`, `EitherPath`, `ValidationPath`, and `VTaskPath` let the same traversal code work across different computational contexts — fail-fast for quick feedback, accumulating for comprehensive validation, and concurrent via virtual threads.

### Part 6 — [From Theory to Practice](https://blog.scottlogic.com/2026/02/12/mfj-from-theory-to-practice.html)

The capstone. Wire Focus DSL + Effect Paths into a four-phase expression pipeline, integrate with **Spring Boot** via `hkj-spring-boot-starter`, generate optics for third-party types with `@ImportOptics`, and map out a pragmatic incremental migration path for real teams.

~~~admonish tip title="Companion Code"
All six parts have runnable examples in the [expression-language-example](https://github.com/higher-kinded-j/expression-language-example) repository. Clone it and follow along.
~~~

---

## Foundations: Types and Functional Patterns

This earlier series explores the foundational ideas that inspired Higher-Kinded-J's development. Each post builds the theoretical knowledge that underpins the optics series above.

- [Algebraic Data Types and Pattern Matching with Java](https://blog.scottlogic.com/2025/01/20/algebraic-data-types-with-java.html) — How ADTs and pattern matching model complex domains and improve on the traditional Visitor Pattern.

- [Variance in Generics, Phantom and Existential Types with Java and Scala](https://blog.scottlogic.com/2025/02/17/variance-in-java-and-scala.html) — Use-site vs declaration-site variance, erasure trade-offs, and how phantom and existential types extend the type system.

- [Intersection and Union Types with Java and Scala](https://blog.scottlogic.com/2025/03/05/intersection-and-union-types-with-java-and-scala.html) — Modelling tighter constraints with intersection types and increasing flexibility with union types.

- [Functors and Monads with Java and Scala](https://blog.scottlogic.com/2025/03/31/functors-monads-with-java-and-scala.html) — Cleaner, more composable code for null handling, error management, and asynchronous sequencing.

- [Higher Kinded Types with Java and Scala](https://blog.scottlogic.com/2025/04/11/higher-kinded-types-with-java-and-scala.html) — How higher-kinded types reduce duplication and unlock flexible, generic abstractions.

- [Recursion, Thunks and Trampolines with Java and Scala](https://blog.scottlogic.com/2025/05/02/recursion-thunks-trampolines-with-java-and-scala.html) — Converting deep stack-based recursion into safe, heap-based iteration.

---

**Previous:** [Const](monads/const_type.md)
**Next:** [Glossary](glossary.md)
