# Decision Trees

## _Three trees, one page_

~~~admonish info title="What You'll Learn"
- Which optic type to choose for a given data shape and access pattern.
- Which API style (Focus DSL, manual composition, Fluent API, Free Monad DSL) to choose for a given task.
- Which advanced feature (filtered, indexed, profunctor) solves which specific problem.
~~~

The decision trees that appear in scattered form across the chapter intros are consolidated here. Use this page when you need to route quickly to the right tool.

---

## Tree 1: Which optic do I need?

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
    │ How many    │            │            │     ISO     │
    │ targets?    │            │            └─────────────┘
    └──────┬──────┘            │
           │                   │
    ┌──────┴──────┐            │
    ▼             ▼            ▼
┌───────┐   ┌──────────┐  ┌─────────────┐
│ One   │   │Zero-more │  │ How many    │
│       │   │          │  │ targets?    │
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

| You have... | You want to... | Reach for |
|---|---|---|
| A required field on a record | Get and set | [Lens](lenses.md) |
| A variant of a sealed type | Match and modify the variant | [Prism](prisms.md) |
| An optional field (nullable, `Optional`-wrapped) | Get and set if present | [Affine](affine.md) |
| Two equivalent representations | Convert losslessly | [Iso](iso.md) |
| A collection field | Apply an operation to every element | [Traversal](traversals.md) |
| A collection field, read-only | Query, search, aggregate | [Fold](folds.md) |
| Read-only access to a single field | Get only | [Getter](getters.md) |
| Write-only access to a single field | Set only | [Setter](setters.md) |

---

## Tree 2: Which API style?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CHOOSING YOUR API                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐                                                        │
│  │  Focus DSL      │ ◄─── START HERE                                        │
│  │  (Recommended)  │      Path-based navigation with full type safety       │
│  └────────┬────────┘      CompanyFocus.departments().employees().name()     │
│           │                                                                 │
│           │  Need validation-aware modifications?                           │
│           │  Working with Either/Maybe/Validated?                           │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │  Fluent API     │      Static methods + builders for effectful ops       │
│  │  (OpticOps)     │      OpticOps.modifyEither(user, lens, validator)      │
│  └─────────────────┘                                                        │
│                                                                             │
│  Need audit trails, dry-runs, or multiple execution strategies?             │
│  See Advanced Optics for the Free Monad DSL.                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Your task | Use |
|---|---|
| Update a nested record field | [Focus DSL](focus_dsl.md) |
| Compose optics across types you own | [Focus DSL](focus_dsl.md) |
| Validate as you modify (`Either`, `Validated`, `Maybe`) | [Fluent API](fluent_api.md) |
| Fan out an effect across a collection | [Fluent API `modifyAllF`](fluent_api.md) |
| Build optic operations as data, run later | [Free Monad DSL](free_monad_dsl.md) |
| Audit trail of every optic operation | [Free Monad DSL with logging interpreter](interpreters.md) |
| Reuse an optic for a type you cannot annotate | [`@ImportOptics`](importing_optics.md) or an [`OpticsSpec`](optics_spec_interfaces.md) interface |
| Adapt an optic to a different data shape | [Profunctor `contramap` / `map` / `dimap`](profunctor_optics.md) |

---

## Tree 3: Which advanced feature?

```
                  ┌─────────────────────────────┐
                  │ What is the constraint?     │
                  └──────────────┬──────────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        ▼                        ▼                        ▼
   ┌──────────┐           ┌────────────┐           ┌──────────────┐
   │ Subset   │           │ Position   │           │ Type adapter │
   │ matters  │           │ matters    │           │ for source/  │
   │          │           │            │           │ target       │
   └────┬─────┘           └─────┬──────┘           └──────┬───────┘
        │                       │                         │
        ▼                       ▼                         ▼
   ┌──────────┐           ┌────────────┐           ┌──────────────┐
   │ Filtered │           │  Indexed   │           │  Profunctor  │
   │ optics   │           │  optics    │           │  optics      │
   └──────────┘           └────────────┘           └──────────────┘
```

| Your problem | Reach for |
|---|---|
| "Apply only to elements matching a predicate" | [Filtered Optics](filtered_optics.md) |
| "I need the index alongside each element" | [Indexed Optics](indexed_optics.md) |
| "Access by key in a `Map`" | [Indexed Access](indexed_access.md) and the [`At`](each_typeclass.md) typeclass |
| "Apply over every element of a custom container" | [Each Typeclass](each_typeclass.md) |
| "Operate on individual characters of a `String`" | [String Traversals](string_traversals.md) |
| "Adapt a lens for a different source record type" | `lens.contramap(...)` ([Profunctor Optics](profunctor_optics.md)) |
| "Adapt a lens for a different target type" | `lens.map(...)` ([Profunctor Optics](profunctor_optics.md)) |
| "Both source and target need adapting" | `lens.dimap(...)` ([Profunctor Optics](profunctor_optics.md)) |
| "Match a sum-type variant by a predicate, not type" | [Advanced Prism Patterns: `nearly` and `doesNotMatch`](advanced_prism_patterns.md) |

---

## See also

- [Optic Capabilities](optic_capabilities.md), what each optic can do once you have chosen one.
- [Composition Rules](composition_rules.md), what type results from composing two optics.
- [Annotations at a Glance](annotations_at_a_glance.md), which annotation generates each optic.

---

**Previous:** [Production Readiness](production_readiness.md)
