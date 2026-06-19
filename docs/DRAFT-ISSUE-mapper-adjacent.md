# Draft GitHub Issues — Adjacent Quality Work (A4 diagnostics · A5 on-ramp)

> **Two small, cross-cutting issues** that ride alongside the mapper, not blocked by it. Postable independently. Reviewer notes inline.

---

## Issue A — A4: telescope-grade processor diagnostics

**Suggested title:** `[FEAT] Processor diagnostics — what/why/fix messages across the optics annotation processors`
**Label:** `enhancement` · good-first-area

### The gap
HKJ’s annotation processors report via terse one-liners — e.g. `FocusProcessor.java:147` *“The @GenerateFocus annotation can only be applied to records.”* — with no *why* and no *fix*. Telescope’s diagnostics are exemplary: they name the offending element, list what was found, and prescribe the exact remedy (including JPMS `opens`). The new mapper/refraction processors will emit many new diagnostics (exhaustiveness failure, same-name/different-type with no codec, non-invertible-declared-`Iso`, missing builder); these should meet a *what / why / fix* bar from day one — far cheaper than retrofitting.

### What “good” looks like
> `@DeriveMapping: target field 'UserDto.fullName' has no source. Found on User: [name, email, age]. Add @Rename(domain="name", wire="fullName"), supply @Via(field="fullName", codec=…), or @Drop("fullName").`

### Things to explore / constraints
- A small diagnostics helper (`Diagnostics.error(element, what, why, fix)`) shared across processors via `ProcessorUtils`.
- Bake into the **new** processors first; then audit `FocusProcessor`/`LensProcessor`/`ImportOpticsProcessor`.
- Coordinate with `hkj-checker` (exhaustiveness/raw-Kind rules) so build-time messages are consistent.

### Scope
**In:** a shared diagnostics format + adoption in the new processors + an audit pass on the existing ones. **Out:** a full message-catalogue/i18n.

---

## Issue B — A5: codegen on-ramp (plugin auto-wiring + quickstart)

**Suggested title:** `[FEAT] Frictionless codegen setup — auto-wire the optics processor via the Gradle/Maven plugins + a sub-minute quickstart`
**Label:** `enhancement` · docs

### The gap
HKJ’s optics/Focus DSL is codegen-only — without the annotation processor wired, there is no `FocusPath`/`Mapping` to call. The mapper (A1) is the marquee entry point that draws new users; high-friction setup loses them at the first step (§ reflection/codegen axis — HKJ keeps codegen-first *by design*, so the answer is a frictionless on-ramp, not a reflective fallback).

### What “good” looks like
- `hkj-gradle-plugin` / `hkj-maven-plugin` (which already exist) **auto-wire** the annotation processor when applied — one line, no manual `annotationProcessor`/`-processor` configuration.
- A copy-pasteable quickstart that goes from zero to a working `FocusPath`/`Mapping` in under a minute.

### Things to explore / constraints
- Plugin applies the processor dependency + `-parameters` (needed for some bean strategies) automatically.
- Quickstart in the book’s “where to start”; verify on a fresh project.
- Optional: a reflective `Focus.of(Class)` fallback **only** if onboarding data shows the build step is a real drop-off (keeps codegen the default).

### Scope
**In:** plugin auto-wiring + quickstart + a fresh-project smoke test. **Out:** a reflective core (explicitly not the default — see Part D of the telescope analysis).
