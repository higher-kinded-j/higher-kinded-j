# Integration and Recipes

> *"Anything worth doing is worth doing right."*
>
> – Hunter S. Thompson, *Fear and Loathing in Las Vegas*

---

Theory is useful; working code is better.

This section brings together everything from the previous four into practical patterns you can apply directly. The capstone example demonstrates a complete validation workflow: composing Lens, Prism, and Traversal to validate permissions nested deep within a form structure. It's the sort of problem that would require dozens of lines of imperative code, handled in a few declarative compositions.

The integration sections cover how optics work with higher-kinded-j's core types: extending Lenses and Traversals with additional capabilities, using Prisms for Optional, Either, and other standard containers. If you've wondered how to combine optics with the rest of the library, this is where you'll find answers.

The cookbook provides ready-to-use recipes for common problems: updating nested optionals, modifying specific sum type variants, bulk collection operations with filtering, configuration management, and audit trail generation. Each recipe includes the problem statement, solution code, and explanation of why it works.

Copy freely. That's what they're for.

~~~admonish info title="Hands-On Learning"
The [Optics Tutorial Track](../tutorials/optics/ch_intro.md) groups all four journeys (108 exercises, ~150 minutes).
~~~

~~~admonish tip title="See Also"
- [Annotations at a Glance](annotations_at_a_glance.md), every optic in the recipes below is annotation-generated.
~~~

---

## Which Optic Do I Need?

When facing a new problem, this flowchart helps:

```
                     ┌─────────────────────┐
                     │ What are you doing? │
                     └──────────┬──────────┘
                                │
           ┌────────────────────┼────────────────────┐
           ▼                    ▼                    ▼
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │   Reading   │     │  Modifying  │     │ Transforming│
    │    only?    │     │   values?   │     │   types?    │
    └──────┬──────┘     └──────┬──────┘     └──────┬──────┘
           │                   │                   │
           ▼                   │                   ▼
    ┌─────────────┐            │            ┌─────────────┐
    │How many     │            │            │    ISO      │
    │targets?     │            │            └─────────────┘
    └──────┬──────┘            │
           │                   │
    ┌──────┴──────┐            │
    ▼             ▼            ▼
┌───────┐   ┌──────────┐  ┌─────────────┐
│ One   │   │Zero-more │  │How many     │
│       │   │          │  │targets?     │
└───┬───┘   └────┬─────┘  └──────┬──────┘
    │            │               │
    ▼            ▼        ┌──────┴──────┐
┌───────┐   ┌────────┐    ▼             ▼
│GETTER │   │ FOLD   │ ┌───────┐  ┌──────────┐
└───────┘   └────────┘ │ One   │  │Zero-more │
                       └───┬───┘  └────┬─────┘
                           │           │
                 ┌─────────┴───┐       │
                 ▼             ▼       ▼
           ┌──────────┐ ┌─────────┐ ┌──────────┐
           │ Required │ │Optional │ │TRAVERSAL │
           └────┬─────┘ └────┬────┘ └──────────┘
                │            │
                ▼            ▼
           ┌────────┐   ┌─────────┐
           │  LENS  │   │ PRISM   │
           └────────┘   └─────────┘
```

---

## The Complete Pipeline

Optics compose to handle complex real-world scenarios:

```
    Form
     │
     │ FormLenses.principal()        ← LENS (required field)
     ▼
    Principal (sealed interface)
     │
     │ PrincipalPrisms.user()        ← PRISM (might be Guest)
     ▼
    User
     │
     │ UserTraversals.permissions()  ← TRAVERSAL (list of perms)
     ▼
    List<Permission>
     │
     │ each                          ← focus on each
     ▼
    Permission
     │
     │ PermissionLenses.name()       ← LENS (required field)
     ▼
    String
     │
     │ validate(name)                ← effectful modification
     ▼
    Validated<Error, String>

    ═══════════════════════════════════════════════════════════
    Result: Validated<List<Error>, Form>
```

All permissions validated. All errors accumulated. Original structure preserved.

---

~~~admonish info title="In This Chapter"
- **Composing Optics** – A complete walkthrough building a validation pipeline that composes Lens, Prism, and Traversal to validate deeply nested permissions in a form structure.
- **Core Type Integration** – How optics work with the library's functional types. Use Prisms to focus on Right values in Either, or Some values in Maybe.
- **Multi-Edit and Sparse Updates** – Apply N independent edits at different paths in one reusable operation with `Edits.combine`, including the sparse, all-errors-at-once REST PATCH shape via `Edits.accumulate`.
- **Optics Extensions** – Additional capabilities beyond the basics. Extended Lens operations, Traversal utilities, and convenience methods for common patterns.
- **Optic-Driven Batching** – Plan and execute bulk reads and writes through optics, so one traversal drives one batched operation instead of N round trips.
- **Plan Introspection and Guardrails** – Inspect a batching plan before running it, and bound its blast radius with guardrails.
- **Cookbook** – Copy-paste solutions for frequent problems. Updating nested optionals, modifying specific sum type variants, bulk collection operations, configuration management.
- **Auditing Complex Data** – A production-ready example generating audit trails. Track every change to a complex nested structure with full before/after comparisons.

See also [Capstone: Effects Meet Optics](../effect/capstone_focus_effect.md) for a complete example combining optics with effect paths in a single pipeline.
~~~

---

## Chapter Contents

1. [Composing Optics](composing_optics.md) - A complete validation workflow example
2. [Core Type Integration](core_type_integration.md) - Using optics with Either, Maybe, Validated, and Optional
3. [Multi-Edit and Sparse Updates](multi_edit.md) - N edits in one operation, including validated REST PATCH
4. [Optics Extensions](optics_extensions.md) - Extended capabilities for Lens and Traversal
5. [Optic-Driven Batching](optic_batching.md) - Bulk reads and writes planned through optics
6. [Plan Introspection and Guardrails](optic_batching_guardrails.md) - Inspecting and bounding batch plans
7. [Cookbook](cookbook.md) - Ready-to-use recipes for common problems
8. [Auditing Complex Data](auditing_complex_data_example.md) - Real-world audit trail generation

---

**Next:** [Composing Optics](composing_optics.md)
