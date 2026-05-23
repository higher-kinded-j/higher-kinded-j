# Effectful Optic Batching — Delivery Plan

### From prototype to production: what exists, what survives, what must be hardened

*Status: revision 2. Revision 1 covered how the prototype fits the intended end state and the battle-hardening required to make it production-grade (§1–§6). Revision 2 appends the capability epic — per-deliverable scope, dependencies, and definition of done (§7).*

---

## 1. Purpose

There is a working prototype of effectful-optic batching in `hkj-core` (package `org.higherkindedj.optics.fetch`). It proves the architecture. It is **not** production code. This document states, honestly and concretely:

1. which parts of the prototype carry forward to production, and in what form;
2. what battle-hardening is required before any of it is fit for a real workload.

Everything here is a precondition for the capability epic. The epic outline is deliberately **not** in this revision — there is no point sequencing features on an unhardened substrate.

---

## 2. What exists today

| File | Role | Lines (approx) |
|---|---|---|
| `Fetch.java` | The substrate: `Done \| Blocked` ADT, `map`/`ap`/`flatMap`, `run`/`runCached`/`runAsync` | ~230 |
| `FetchApplicative.java` | The `Applicative` instance plugged into `Optic.modifyF` | ~55 |
| `FetchKind.java`, `FetchKindHelper.java` | HKT witness + `widen`/`narrow` (standard `Const`-style pattern) | ~60 |
| `BatchLoader.java` | The keyed batch contract (`Set<K> → CF<Map<K,V>>`) | ~25 |
| `BatchLoaders.java` | Sync/async adapters onto that contract | ~65 |
| `FetchApplicativeTest.java` | 10 tests: N+1 collapse, dedup, coupled-lens atomicity, applicative/monad boundary | ~340 |

The prototype's real achievement is **de-risking the architecture**: it proves the van Laarhoven `modifyF` seam accepts a batching applicative with zero changes to any optic, and it fixed the API shape (`Fetch.fetch`, `FetchApplicative.instance()`, `BatchLoader`). That is genuine value — but it is the first 15–20% of the work, not the foundation 80%.

---

## 3. How the prototype fits the end state

Per-file disposition. "Architecture" means the public shape survives; "internals" means the implementation behind it does not.

| Component | Disposition | Notes |
|---|---|---|
| `Fetch` ADT (`Done`/`Blocked`) | **Keep architecture, re-implement internals** | The two-constructor model is correct. The recursive `map`/`ap`/`flatMap` are not stack-safe (see §4-B1) and must be rebuilt on an explicit work-stack/trampoline. `Done`/`Blocked` should likely become non-public — users need only `fetch` + `run*`. |
| `run` (sync, no cache) | **Supersede** | `runCached` strictly dominates it. Drop, or keep only as a documented teaching path. |
| `runCached` / `runAsync` | **Keep architecture, harden internals** | Round loop is correct; needs missing-key policy, error channel, timeouts, batch-size limits, a defined concurrency model. |
| `RunResult` / `CachedRunResult` | **Consolidate** | One result type. Keep the observability fields (`rounds`, `backendCalls`, `cacheHits`); add metrics hooks. |
| `FetchApplicative` | **Keep, add rigour** | Needs applicative-law tests and input validation consistent with `ConstApplicative` (`Validation.kind()`...). |
| `FetchKind` / `FetchKindHelper` | **Keep essentially as-is** | Conforms to the existing `Const` witness pattern; production-acceptable. |
| `BatchLoader` | **Keep** | The contract is sound and neutral. |
| `BatchLoaders` | **Keep architecture, expand** | Add adapters/decorators for timeout, batch-size chunking, metrics, retry. |
| `FetchApplicativeTest` | **Keep as examples; ~5% of needed coverage** | See §4-G. |

Net: **every file's public shape survives; `Fetch`'s core internals and the entire test strategy do not.** Hardening here is substantial re-implementation behind a stable API, not polishing.

---

## 4. Battle-hardening gap register

Severity: **Blocker** (unsafe for any production use) · **Major** (needed for real workloads) · **Minor** (quality/consistency).

| ID | Gap | Why it matters | Severity |
|---|---|---|---|
| B1 | Stack safety + construction cost | Resolution recurses with traversal depth — `StackOverflow` confirmed at N=25,000; and construction is O(n²) because each `ap` copies the accumulated pending-key set. Both must be fixed to reach the 1M-foci DoD | **Blocker** |
| B2 | Missing-key policy | A resolver that omits a requested key yields `Done(null)` silently → NPE or corrupt data downstream | **Blocker** |
| B3 | Failure semantics | A failing load throws; no partial results, no railway value. Inconsistent with HKJ's entire model | **Blocker** |
| M1 | Concurrency model | The per-run cache is a bare `Map`; behaviour when shared across concurrent field resolvers is undefined | **Major** |
| M2 | Timeouts / cancellation | `runAsync` chains `thenCompose` with no timeout; one hung `loadAll` hangs the program | **Major** |
| M3 | Batch-size limits | A traversal yielding 1M keys produces one 1M-element `$in`/`IN`; needs chunking | **Major** |
| M4 | Applicative-law tests | `FetchApplicative` must satisfy identity/composition/homomorphism/interchange, or composed optics silently misbehave. Untested | **Major** |
| M5 | Cache lifecycle | Unbounded, no eviction/TTL, ownership/scope undefined | **Major** |
| m1 | Input validation | No null/contract checks; `ConstApplicative` uses `Validation.kind()` — be consistent | **Minor** |
| m2 | Observability | No metrics/tracing hooks (Micrometer spans, round counters as gauges) | **Minor** |
| m3 | API encapsulation | `Done`/`Blocked`/`resume` are public; decide the supported surface | **Minor** |
| m4 | jspecify nullness audit | `of(@Nullable A)` → `Done(null)`, `Map.get` nullable — verify under `@NullMarked` | **Minor** |
| m5 | Book documentation | Lives only in a `docs/` think-piece; production needs an `hkj-book` chapter | **Minor** |

### The three blockers, in detail

**B1 — Stack safety + construction cost.** `FocusPaths.listElements().modifyF` folds elements with `map2`, which is `ap(map(...), ...)`. For N elements this builds an N-deep nest of `Blocked` continuations. Two distinct scaling faults follow, both confirmed by the Phase 0 spec test (`FetchHardeningTest`): (1) collapsing one round descends the full N-deep `ap` recursion — `StackOverflowError` observed at N=25,000 (it passes at N=8,000, so the current code is *correct, not scalable*); (2) each `ap` does `new LinkedHashSet<>(bf.pending())`, copying the accumulated key set, making construction O(n²).

*Resolved (this branch).* Fault (1): each `Blocked.resume` now returns a `Trampoline<Fetch>` built from deferred nodes, and a round is collapsed by `Trampoline.run()` in constant stack. Fault (2): pending keys are held as `PendingKeys` — a deferred-union tree (`One`/`Union`) giving O(1) `ap` merge, flattened once per round. The public API is unchanged. The spec test asserts N=200,000 (8× the empirical failure point; trampolined stack use is N-independent, so this is conclusive). The trampolined run is O(n) time but currently O(n) memory with a sizeable per-element constant — million-element throughput/memory is a benchmark concern (`hkj-benchmarks`) and reducing the constant is a follow-up efficiency item, not a B1 blocker.

**B2 — Missing-key policy.** Previously a key absent from the resolver's returned `Map` became `Done(null)` — a silent corruption.

*Resolved (this branch).* Every round now verifies the resolver returned an entry for each requested key; an omission throws a typed `MissingKeyException` naming the missing keys, in all three runners (`run`, `runCached`, `runAsync`). This is the deterministic fail-fast policy: never a silent `null`. Richer per-call policies (an `Optional`/`Maybe` element, or surfacing the omission as an `Either`/`Validated` left rather than a throw) fold naturally into Capability A's railway error channel (A1) and are deferred to it rather than building a strategy abstraction twice.

**B3 — Failure semantics.** A failing batch load must compose as an `Either`/`Validated`, with partial-batch results preserved where the domain allows. **Note the overlap:** this blocker *is* Capability A's headline feature (the "railway error channel"). Hardening B3 and delivering epic item 2 are the same body of work — the plan should treat them as one.

---

## 5. Honest effort framing

The prototype is roughly **15–20% of Capability A's total effort**. The valuable part it delivered is disproportionate to its size: it proved the seam works and froze the API shape, removing the largest design risk. But the remaining 80%+ — stack safety, failure semantics, concurrency, resource limits, law-level test rigour — is real engineering, not finishing touches.

A blunt summary: **the prototype is a proof of architecture, not a base of code.** Treat its file *shapes* as the spec and its file *internals* as a reference sketch to be rebuilt.

---

## 6. Relationship to the capability epic

The hardening of §4 is not separate from the epic. Two facts bind them:

- **B3 ≡ epic item 2 (error channel).** Hardening the failure semantics and delivering Capability A's railway channel are the same work.
- B1, B2 and the foundational Major gaps are the price of admission for *any* of the epic to be production-real.

The epic — scope, dependencies, definition of done — is set out in §7 below.

---

## 7. The capability epic

### 7.1 Shape of the epic

The work is **one Phase 0 (substrate hardening) plus three capabilities on two axes.** This is not one monolith; it is four shippable units with a deliberate dependency order.

```
                 Phase 0 — Substrate hardening
                 (B1 stack-safety, B2 missing-key,
                  M1 concurrency, M4 laws, consolidation)
                            │
        ┌───────────────────┴───────────────────┐
        ▼                                        ▼
  Capability A  (strategy axis)            Capability C  (shape axis)
  Production batched data access           N-ary coupled optics
        │                                  — independent, parallel,
        │  A1 error channel (= item 2/B3)    shares no Fetch code —
        │  A2 multi-source   (= item 1)
        ▼
  Capability B  (strategy axis)
  Plan introspection (= item 6)
```

- **Strategy axis (A, B):** richer ways to *run* an optic path. Shared `Fetch`/`modifyF` substrate.
- **Shape axis (C):** richer *data models* the path runs over. Pure optics; touches no `Fetch` code.

One mental model unites them — *describe the shape, choose the strategy at the boundary* — but they ship and are documented as distinct units. A user can adopt A and never touch B or C.

### 7.2 Phase 0 — Substrate hardening

**Goal.** Make the prototype substrate correct and safe, so capabilities are built on solid ground.

**In scope.** B1 stack safety (re-express `ap`/`map`/`flatMap` resolution on an explicit work-stack/trampoline, API unchanged); B2 missing-key policy (explicit, configurable strategy); M1 concurrency model (define and document cache ownership); M4 applicative-law property tests; consolidate `RunResult`/`CachedRunResult`; drop sync `run`; m1 input validation; m3 API encapsulation (`Done`/`Blocked`/`resume` made non-public).

**Out of scope.** Any feature — error channel, multi-source, analyzability.

**Depends on.** Nothing (the prototype).

**Definition of done.**
- `ap`/`map`/`flatMap` resolve without `StackOverflow` for a traversal of 200,000 foci (stress test in the suite — conclusive, since trampolined stack use is N-independent and the unfixed code fails at 25,000).
- `FetchApplicative` passes tests for identity, homomorphism, interchange, composition.
- A missing key triggers a configured, tested behaviour — never a silent `null`.
- One result type; sync `run` removed (or documented teaching-only).
- Public API surface explicitly chosen; ADT internals not exposed.
- Cache concurrency contract documented (ownership = one program run).

**Status: complete.** B1 (trampoline + `PendingKeys` union-tree), B2 (`MissingKeyException` fail-fast), and M4 (the four applicative laws, in `FetchApplicativeLawsTestFactory` using the project's `@TestFactory`/`DynamicTest` idiom) are done and green. Consolidation: `run`/`RunResult` removed; `runCached`/`runAsync` are the runners and `RunResult` the single result type. m1: `requireNonNull` contract checks on the public entry points. m3: the package is intentionally non-exported (the explicit surface decision; `Fetch.Done`/`Blocked`/`PendingKeys` are not public API), recorded in `package-info`. M1: the cache/concurrency contract is documented in `package-info`. Coverage of the package is ~100% line; the residual uncovered branches are unreachable sealed-guard paths. Capability A may now build on this substrate.

### 7.3 Capability A — Production batched data access

**User capability unlocked.** *Navigate the domain with optics; loads batch, dedupe, cache, span multiple backends, and failures arrive as railway values.* This is the spine of the epic and the first shippable user-facing capability.

Two sub-deliverables, in order.

**A1 — Railway error channel** (= epic item 2 = blocker B3)
- *Goal.* A failing or partial batch composes as `Either`/`Validated`; no exceptions as control flow.
- *In scope.* Typed errors carried through `Fetch`; per-key failure signalling from `BatchLoader`; partial-batch success preserved; integration with HKJ `Validated`/`Either`; M2 timeouts (a timed-out load is a typed error).
- *DoD.* A failing `loadAll` yields a `Left`/`Invalid`, never a thrown exception; a batch with mixed key outcomes retains the successes; timeout path tested; a failure-injection test suite exists.

**A2 — Multi-source batching** (= epic item 1)
- *Goal.* One `Fetch` program batches across several `(K,V)` request families — one dispatch per family per round.
- *In scope.* A carrier for heterogeneous request domains (HKJ's free applicative `FreeAp` is the candidate); a registry mapping request-family → `BatchLoader`; M3 batch-size chunking per family.
- *Out of scope.* Cross-family de-duplication (distinct key spaces); distributed cache.
- *Depends on.* Phase 0, A1.
- *DoD.* A traversal touching users + products + prices resolves in one dispatch per family per round (tested); chunking splits an oversized family batch; the heterogeneous-type story is documented honestly (this is the hardest part in Java's type system).

**Capability A depends on.** Phase 0. **Ships when.** A1 and A2 are both done — that is the first milestone a real workload can use.

### 7.4 Capability B — Plan introspection

**User capability unlocked.** *Inspect a `Fetch` program before running it — which request families, how many round-trips, estimated shape/cost — for audit, dry-run, and guardrails.*

- **Goal.** Fold a `Fetch` program into a description without executing it.
- **In scope.** An interpreter producing a structural plan (request families, key counts, round-count lower bound); a dry-run mode against a stub backend; reuse of the existing `OpticPrograms`/Free machinery.
- **Out of scope.** Accurate latency/cost prediction (structural estimates only); query-plan optimisation.
- **Depends on.** Phase 0 technically; *meaningfully valuable only after A2* — a single-source plan is trivial. Sequence it after Capability A.
- **DoD.** Given a program, produce a structural plan with zero I/O; dry-run executes the plan shape with a stub; documented as the basis for audit/cost hooks.

### 7.5 Capability C — N-ary coupled optics

**User capability unlocked.** *Declare coupled-field groups of three or more fields with a cross-field invariant, rebuilt atomically — code-generated.*

- **Goal.** Generalise `Lens.paired` beyond binary.
- **In scope.** An N-ary coupled-group lens (`Lens<S, TupleN>` or a generated group lens); ideally an annotation (e.g. `@Invariant`) so the processor emits the coupled optic and the checked reconstruction.
- **Out of scope.** Anything in the `Fetch`/batching code — C shares no implementation with A or B. The batching interaction is a *consequence*, already covered by the existing coupled-lens test pattern.
- **Depends on.** The optics core only (`Lens`, codegen). **Fully parallel** — implementable any time by someone who never touches `Fetch`.
- **DoD.** A 3- and a 5-field coupled group update atomically with the invariant enforced once at reconstruction; the codegen path is tested; behaviour verified with and without a batching applicative.

### 7.6 Delivery order and milestones

| Milestone | Contents | Gate |
|---|---|---|
| **M0** ✅ | Phase 0 complete | Substrate stack-safe, law-tested, no silent nulls — **done** |
| **M1** | Capability A (A1 → A2) | First production-usable release: error-safe, multi-source batching |
| **M2** | Capability B | Plan introspection / dry-run available |
| **(C)** | Capability C | Lands independently on a parallel track, any time after the optics core is free |

Critical path: **Phase 0 → A1 → A2 → B.** Capability C runs alongside and gates nothing.

### 7.7 Explicitly out of scope

To keep the epic bounded:

- **Reactive (`Flux`/`Mono`) bridge** — against HKJ's virtual-thread grain; the blocking + `VTask`/`CompletableFuture` path is the idiomatic answer.
- **Distributed or cross-request shared cache** — the cache is per-program-run only.
- **Query predicate pushdown** — optics stay post-fetch; this batches by-id resolution, it does not generate queries.
- **Turning HKJ into a GraphQL or Mongo framework** — boundary adapters stay thin, optional, and outside `hkj-core`.
