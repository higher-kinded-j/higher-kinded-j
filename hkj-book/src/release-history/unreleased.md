# Unreleased: 0.5.0

_Not released yet: these changes are on `main`, and the `latest` book describes them._

To try them, depend on `0.5.0-SNAPSHOT` from the snapshots repository, as [Gradle SNAPSHOT Configuration](../tooling/manual_setup.md#gradle-snapshot-configuration) shows.

~~~admonish info title="At a glance"
- **`@PathSource` has a page of its own**, beside `GenericPath` in the Effect Path chapter.
~~~

---

## Mapping {#mapping}

---

## Optics {#optics}

---

## Effect Paths {#effect-paths}

- **`@PathSource` points an effect algebra to `FreePath`** ([#1008](https://github.com/higher-kinded-j/higher-kinded-j/issues/1008)): a Path over a witness that `@EffectAlgebra` generates now draws a note at the witness. The algebra has a `Functor` and no `Monad` to pass to the Path's `of` and `pure`, so the note shows how to wrap its programs in a `FreePath`. See [A witness another processor writes](../effect/path_source.md#a-witness-another-processor-writes).

---

## Spring {#spring}

---

## Testing {#testing}

---

## Build and tooling {#build-and-tooling}

---

## Documentation {#documentation}

- **`@PathSource` has a page of its own** ([#999](https://github.com/higher-kinded-j/higher-kinded-j/issues/999)): [Custom Paths with `@PathSource`](../effect/path_source.md) says when a generated Path is worth it over `GenericPath`, what each capability generates, and how `errorType` types recovery.

---

## Upgrading from 0.4.11 {#upgrading}

### Before you upgrade {#before-you-upgrade}

### What a running program can notice {#runtime-changes}

### What stops a build that compiled {#build-changes}

| Area | Now fails the build | Do this | Issue |
|---|---|---|---|

### Deprecated for removal in 0.6.0 {#deprecated}

| Deprecated | Replacement | Recipe |
|---|---|---|

---

**Previous:** [Upgrading](upgrading.md)
**Next:** [v0.4.11](v0_4_11.md)
