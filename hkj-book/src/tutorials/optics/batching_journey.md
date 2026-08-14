# Optics: Batching & Coupled Updates Journey

~~~admonish info title="What We'll Learn"
- Optic-driven request batching: one traversal, one batched call, no N+1
- Plan introspection and guardrails: inspecting and bounding a batch before it runs
- N-ary coupled lenses: atomic updates for fields that share an invariant
~~~

**Duration**: ~40 minutes | **Tutorials**: 3 (T21-T23) | **Exercises**: 13

~~~admonish tip title="Where This Fits in the Bigger Picture"
The Focus DSL journey taught paths that read and write one structure in memory. This journey covers what happens when those paths meet the outside world and each other: a traversal whose focuses each cost a remote call (batch it), a batch you want to see and bound before it runs (guardrails), and fields whose updates must move together or not at all (coupled lenses). The reference chapters are [Optic-Driven Batching](../../optics/optic_batching.md), [Plan Introspection and Guardrails](../../optics/optic_batching_guardrails.md), and [Coupled Fields](../../optics/coupled_fields.md).
~~~

**Prerequisites**: [Optics: Focus DSL Journey](focus_dsl_journey.md)

## Journey Overview

Three independent capabilities, each solving a failure mode that appears once optics leave the toy stage:

```
  traversal over N focuses            a plan you cannot see          two fields, one invariant
        │  N remote calls                  │  unbounded blast              │  torn updates
        ▼                                  ▼                               ▼
  T21: one batched dispatch         T22: introspect + guardrails     T23: Lens.paired, atomic
```

---

## Tutorial 21: Optic-Driven Request Batching (~15 minutes)
**File**: `Tutorial21_OpticBatching.java` | **Exercises**: 5

Loading data for each focus of a traversal is the classic N+1 problem. Optic-driven batching lets the optic plan one batched call instead.

**What you'll learn**:
- Turning a traversal's focuses into a single batched request
- How the plan is derived from the optic, not hand-assembled
- Where batching pays and where it does not

**Key insight**: the optic already knows every focus it will visit; batching just asks it before executing.

---

## Tutorial 22: Plan Introspection and Guardrails (~12 minutes)
**File**: `Tutorial22_OpticBatchingGuardrails.java` | **Exercises**: 5

A batch you cannot see is a batch you cannot bound. This tutorial inspects the plan Tutorial 21 built and puts limits around it.

**What you'll learn**:
- Reading a plan before it runs
- Bounding a batch's blast radius with guardrails
- Failing fast when a plan exceeds its bounds

**Key insight**: introspection turns "trust the batching" into "verify the batching", the same move the mapping laws make for codecs.

---

## Tutorial 23: N-ary Coupled Lenses (~10 minutes)
**File**: `Tutorial23_CoupledLenses.java` | **Exercises**: 3

A record with a cross-field invariant (a `Range` where `lo <= hi`) breaks under sequential single-field lens updates. Coupled lenses update the fields together, atomically.

**What you'll learn**:
- Why sequential `set` calls tear a cross-field invariant
- `Lens.paired` and its n-ary siblings for atomic multi-field updates
- Choosing between a coupled lens and one atomic edit

**Key insight**: if two fields must agree, they are one focus with two components, not two focuses.

---

~~~admonish tip title="See Also"
- [Optic-Driven Batching](../../optics/optic_batching.md) - Tutorial 21's reference page
- [Plan Introspection and Guardrails](../../optics/optic_batching_guardrails.md) - Tutorial 22's reference page
- [Coupled Fields](../../optics/coupled_fields.md) - Tutorial 23's reference page
~~~

---

**Previous:** [Optics: Focus DSL](focus_dsl_journey.md)
**Next:** [Optics: Boundary Mapping](boundary_mapping_journey.md)
