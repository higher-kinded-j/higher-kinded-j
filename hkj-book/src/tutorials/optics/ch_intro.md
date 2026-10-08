# Optics Journeys

> _"For the things we have to learn before we can do them, we learn by doing them."_
> — Aristotle, *Nicomachean Ethics*

Six journeys covering the full optics surface, from first-principles Lens and Prism through the Focus DSL to batching, coupled updates, and the generated DTO boundary. Work them in order: each builds on concepts introduced earlier. Reading the Optics chapter instead? [Practising alongside the Optics chapter](#practising-alongside-the-optics-chapter) pairs each of its first pages with the tutorials that practise it.

~~~admonish tip title="Where This Fits in the Bigger Picture"
The `.focus().attributes().at(key)` token in [One Line, Six Layers](../../hkts/one_line_six_layers.md) is the optic layer; these journeys teach the pieces that compose into that one fluent path. The reference material lives in the [Optics chapter](../../optics/ch_intro.md) and, for the final journey, the [Mapping at the Boundary chapter](../../mapping/ch_intro.md).
~~~

~~~admonish info title="In This Chapter"
- **Lens & Prism** – The foundations: focusing on one field of a record, one variant of a sealed type, and composing the two. Everything later builds on these exercises.
- **Traversals & Practice** – Zero-or-more focus: bulk operations over collections, composition with lenses and prisms, and the real-world shapes they unlock.
- **Fluent & Free DSL** – The ergonomic layer for validation-aware updates, advanced prism patterns, and optics as programs-as-data with multiple interpreters.
- **Focus DSL** – Type-safe path navigation with automatic type widening through optional values and collections; the way most day-to-day optics code is written.
- **Batching & Coupled Updates** – What happens when paths meet the outside world and each other: one batched call per traversal instead of N, plans you can inspect and bound, and atomic updates for fields that share an invariant.
- **Boundary Mapping** – The hands-on lane for the mapping chapter: hand-written multi-edits, the `ValidatedPrism` leaf, the whole DTO boundary generated and law-checked, and the edge cases a real request brings.
~~~

## Chapter Contents

1. [Lens & Prism](lens_prism_journey.md): Lens basics, composition, Prism, Affine
2. [Traversals & Practice](traversals_journey.md): Traversals, composition, real-world applications
3. [Fluent & Free DSL](fluent_free_journey.md): Fluent API, advanced Prisms, Free Monad DSL
4. [Focus DSL](focus_dsl_journey.md): Type-safe path navigation, container widening
5. [Batching & Coupled Updates](batching_journey.md): Request batching, guardrails, coupled lenses
6. [Boundary Mapping](boundary_mapping_journey.md): Multi-edit, ValidatedPrism, generated record mapping, boundary edge cases

## Practising alongside the Optics chapter {#practising-alongside-the-optics-chapter}

The Optics chapter's first pages each reach a "You can ship now" tip. These tutorials practise what each page taught, so you can work one after reading its page rather than waiting for a whole journey. Each still assumes the tutorials its own header names: the Focus DSL ones, for instance, assume Tutorials 01-06.

| After reading | Practise with |
|---|---|
| [Quickstart](../../optics/quickstart.md) | [Tutorial 00: Your First Path](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial00_FirstPath.java), [Tutorial 07: Generated Optics](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial07_GeneratedOptics.java) |
| [Focus DSL](../../optics/focus_dsl.md) | [Tutorial 12: Focus DSL](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial12_FocusDSL.java), [Tutorial 19: Navigator Generation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial19_NavigatorGeneration.java) |
| [Collections, Optionals and Sealed Types](../../optics/focus_navigation.md) | [Tutorial 13: Advanced Focus DSL](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial13_AdvancedFocusDSL.java), [Tutorial 20: Container Navigation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial20_ContainerNavigation.java) |
| [What a Path Is Made Of](../../optics/optics_intro.md) | [Tutorial 01: Lens Basics](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial01_LensBasics.java), [Tutorial 06: Optics Composition](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial06_OpticsComposition.java) |
| [Updates That Can Fail](../../optics/fluent_api.md) | [Tutorial 09: Fluent Optics API](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial09_FluentOpticsAPI.java) |
| [Many Edits at Once](../../optics/multi_edit.md) | [Tutorial 24: Multi-Edit and Sparse Updates](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial24_MultiEdit.java) |
| [Capstone: An Order Desk](../../optics/capstone.md) | [Capstone: The Order Desk](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/TutorialCapstone_OrderDesk.java) |

---

At a glance:

| Journey | Exercises |
|---------|-----------|
| [Lens & Prism](lens_prism_journey.md) | 33 |
| [Traversals & Practice](traversals_journey.md) | 28 |
| [Fluent & Free DSL](fluent_free_journey.md) | 22 |
| [Focus DSL](focus_dsl_journey.md) | 90 |
| [Batching & Coupled Updates](batching_journey.md) | 13 |
| [Boundary Mapping](boundary_mapping_journey.md) | 24 |

---

**Next:** [Lens & Prism](lens_prism_journey.md)
