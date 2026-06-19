# Draft GitHub Issues — Mapper Follow-ons (merge · third-party)

> **Two small, postable follow-ons to `@DeriveMapping`** (`DRAFT-ISSUE-derive-mapping.md`). Each is self-contained; post after the mapper. Reviewer notes inline.

---

## Issue A — multi-source `merge` (N sources → one target)

**Suggested title:** `[FEAT] Add @DeriveMapping merge — assemble one target from N source objects (forward-only)`
**Label:** `enhancement`

### The gap
`@DeriveMapping` maps one source ↔ one target. A common need is the *forward-only* assembly of one target from **several** sources (e.g. build a `DashboardDto` from `User`, `Account`, and `Settings`). Telescope offers `Telescope.merge(...)` with auto-backfill by name+type and no general inverse. HKJ has no equivalent.

### What “good” looks like
```java
@DeriveMerge(target = DashboardDto.class, from = { User.class, Account.class, Settings.class })
public interface DashboardAssembly {}
// generates:  DashboardDto assemble(User, Account, Settings);   // forward-only; fields backfilled by name+type
```
- Each target field is filled from the source that has a same-named, same-typed (or codec-mappable) field; **ambiguity or an unfilled field is a compile error**.
- **Forward-only** — the multi-source case has no general inverse (no `parse`).

### Things to explore / constraints
- Reuses the **assembly builder** fed from N sources; per-field provenance resolved at compile time.
- A validated variant (`Validated<NEL<FieldError>, Target>`) when any leaf parses.
- No inverse — document this (telescope’s `Merge` throws on `backward`; HKJ simply doesn’t generate one).

### Dependencies / scope
Depends on `@DeriveMapping` + the assembly builder. **In:** forward-only N-source assembly, compile-time provenance/exhaustiveness. **Out:** any inverse; runtime source bag.

---

## Issue B — third-party (un-owned) mapping

**Suggested title:** `[FEAT] Add @DeriveMapping for un-owned types (list foreign classes by .class, like @ImportOptics)`
**Label:** `enhancement`

### The gap
`@DeriveMapping` sits on a type you own. Mapping to/from a **third-party** type you cannot annotate (a library DTO, a `java.time` value) has no compile-time path — only the reflective escape. HKJ already solves the analogous navigation case with `@ImportOptics({Foreign.class, …})` on a `package-info`.

### What “good” looks like
```java
@DeriveMapping(from = com.vendor.OrderEntity.class, to = OrderDto.class)
package com.myapp.mapping;     // on package-info — neither side need be annotatable
// generates a compile-time, reflection-free OrderEntity <-> OrderDto mapping
```
- Closes the matrix row where telescope offers only reflection for un-owned types — HKJ stays compile-time, reflection-free.

### Things to explore / constraints
- Reuses the **`ImportOpticsProcessor`** pattern (`@Target({PACKAGE,TYPE})`, `Class<?>[]`) — the un-owned-type plumbing already exists.
- Same classifier/exhaustiveness as `@DeriveMapping`; both sides may be foreign (vs `@Bridge`-style requiring you own one side).
- Bean foreign types reuse `external/CopyStrategy` (ties to A3).

### Dependencies / scope
Depends on `@DeriveMapping`. **In:** `@DeriveMapping` listing foreign classes by `.class` on a `package-info`/holder. **Out:** generating optics *into* foreign modules that need JPMS `opens` (document the public-members constraint, as `@ImportOptics` does).
