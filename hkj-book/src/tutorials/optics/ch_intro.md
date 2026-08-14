# Optics Journeys

> _"For the things we have to learn before we can do them, we learn by doing them."_
> — Aristotle, *Nicomachean Ethics*

Six journeys covering the full optics surface, from first-principles Lens and Prism through the Focus DSL to batching, coupled updates, and the generated DTO boundary. Work them in order: each builds on concepts introduced earlier.

~~~admonish tip title="Where This Fits in the Bigger Picture"
The `.focus().attributes().at(key)` token in [One Line, Six Layers](../../hkts/one_line_six_layers.md) is the optic layer; these journeys teach the pieces that compose into that one fluent path. The reference material lives in the [Optics chapter](../../optics/ch_intro.md) and, for the final journey, the [Mapping at the Boundary chapter](../../mapping/ch_intro.md).
~~~

~~~admonish info title="In This Chapter"
- **Lens & Prism** – The foundations: focusing on one field of a record, one variant of a sealed type, and composing the two. Everything later builds on these thirty exercises.
- **Traversals & Practice** – Zero-or-more focus: bulk operations over collections, composition with lenses and prisms, and the real-world shapes they unlock.
- **Fluent & Free DSL** – The ergonomic layer for validation-aware updates, advanced prism patterns, and optics as programs-as-data with multiple interpreters.
- **Focus DSL** – Type-safe path navigation with automatic type widening through optional values and collections; the way most day-to-day optics code is written.
- **Batching & Coupled Updates** – What happens when paths meet the outside world and each other: one batched call per traversal instead of N, plans you can inspect and bound, and atomic updates for fields that share an invariant.
- **Boundary Mapping** – The hands-on lane for the mapping chapter: hand-written multi-edits, the `ValidatedPrism` leaf, and the whole DTO boundary generated and law-checked.
~~~

## Chapter Contents

1. [Lens & Prism](lens_prism_journey.md) - Lens basics, composition, Prism, Affine
2. [Traversals & Practice](traversals_journey.md) - Traversals, composition, real-world applications
3. [Fluent & Free DSL](fluent_free_journey.md) - Fluent API, advanced Prisms, Free Monad DSL
4. [Focus DSL](focus_dsl_journey.md) - Type-safe path navigation, container widening
5. [Batching & Coupled Updates](batching_journey.md) - Request batching, guardrails, coupled lenses
6. [Boundary Mapping](boundary_mapping_journey.md) - Multi-edit, ValidatedPrism, generated record mapping

---

At a glance:

| Journey | Duration | Exercises |
|---------|----------|-----------|
| [Lens & Prism](lens_prism_journey.md) | ~40 min | 30 |
| [Traversals & Practice](traversals_journey.md) | ~40 min | 27 |
| [Fluent & Free DSL](fluent_free_journey.md) | ~35 min | 22 |
| [Focus DSL](focus_dsl_journey.md) | ~35 min | 29 |
| [Batching & Coupled Updates](batching_journey.md) | ~40 min | 13 |
| [Boundary Mapping](boundary_mapping_journey.md) | ~35 min | 13 |

---

**Next:** [Lens & Prism](lens_prism_journey.md)
