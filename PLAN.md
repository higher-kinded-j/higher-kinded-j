# Plan: Release Quality Gate & Benchmark Documentation

## Summary

Four changes addressing observability, correctness, and release readiness:

1. **Benchmark tests FAIL instead of skip** when results are missing
2. **`releaseReadiness` Gradle task** — single-command quality gate ordered fast to slow
3. **Pitest uses full profile** in the release task
4. **Improve hkj-book benchmarks.md** — add section documenting what assertion tests validate and the release quality gate
5. **`get()` prefix match fix** for parameterized benchmarks (regression from previous params fix)

---

## 1. BenchmarkAssertionsTest: fail instead of skip

**File:** `hkj-benchmarks/src/test/java/.../BenchmarkAssertionsTest.java`

- Change `assumeThat` to `assertThat` in `assumeResultsAvailable()` and `assumeBenchmarkPresent()`
- Rename to `assertResultsAvailable()` and `assertBenchmarkPresent()`
- Change all individual `assumeThat(result).isPresent()` to `assertThat(result).as("...").isPresent()`
- Remove the `assumeThat` import; update class javadoc
- Fix `get()` to fall back to prefix match for benchmarks with `@Param` annotations

**Rationale:** Running `:hkj-benchmarks:test` without `:hkj-benchmarks:jmh` first should fail loudly, not silently skip everything and report green.

---

## 2. `releaseReadiness` Gradle task

**File:** `build.gradle.kts` (root)

Register a new `releaseReadiness` task in the `verification` group. Ordered fast to slow:

1. `spotlessCheck` — code formatting (seconds)
2. `build` — compile + all unit tests + jacoco (minutes)
3. `:hkj-benchmarks:jmh` — JMH benchmarks (minutes)
4. `:hkj-benchmarks:test` — benchmark assertion tests (seconds, must follow jmh)
5. Pitest with full profile (slowest) — via `Exec` task calling `./gradlew :hkj-processor:pitest -Ppitest.profile=full`

Use `mustRunAfter` chains to enforce the ordering. The `doLast` block prints a summary with paths to all generated reports.

The pitest step uses an `Exec` task (same pattern as `longBenchmark`) because the `-Ppitest.profile=full` property must be set at Gradle invocation time for the conditional logic in `hkj-processor/build.gradle.kts` to resolve correctly.

---

## 3. Update hkj-book benchmarks.md

**File:** `hkj-book/src/benchmarks.md`

Add two new sections after "Running Benchmarks":

### "Benchmark Assertion Tests" section
- What the 18 test groups validate (table with group name → what it checks)
- How to run: `./gradlew :hkj-benchmarks:jmh` then `./gradlew :hkj-benchmarks:test`
- That tests **fail** (not skip) if benchmarks haven't been run — this is intentional
- Link to the assertion test source for reference

### "Release Quality Gate" section
- The `releaseReadiness` command
- What it runs and in what order (fast → slow)
- That pitest uses the full (STRONGER mutators) profile
- Report locations for each tool

---

## Files Modified

| File | Change |
|------|--------|
| `hkj-benchmarks/src/test/java/.../BenchmarkAssertionsTest.java` | `assume` → `assert`, `get()` prefix match |
| `build.gradle.kts` (root) | Add `releaseReadiness` task with pitest full profile |
| `hkj-book/src/benchmarks.md` | Add assertion tests + release quality gate sections |
