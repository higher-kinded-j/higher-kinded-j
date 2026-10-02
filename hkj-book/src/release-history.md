# Release History

What changed in each version of Higher-Kinded-J, and what to do when you move to it. Each release has notes, most with a link from each change to the issue or pull request behind it.

- **Upgrading?** [Upgrading](release-history/upgrading.md) lists, release by release, what can stop a build that compiled or change what a program does. It also lists every API due for removal in 0.5.0.
- **Following `main`?** [Unreleased: 0.4.12](release-history/unreleased.md) collects the changes since 0.4.11.
- **Looking for a feature?** [Since which version?](#since-which-version) names the release that first shipped it.
- **Searching every release at once?** The book's search reads every page of this chapter.

---

## Releases at a glance {#releases-at-a-glance}

| Version | Released | Headline | Upgrade notes |
|---|---|---|---|
| [0.4.11](release-history/v0_4_11.md) | 3 October 2026 | Generated client models map as generated, and the processor stops more shapes at the declaration | [To 0.4.11](release-history/upgrading.md#to-0411) |
| [0.4.10](release-history/v0_4_10.md) | 30 August 2026 | Standard codecs, generic optics that compile, one answer from the Focus DSL | [To 0.4.10](release-history/upgrading.md#to-0410) |
| [0.4.9](release-history/v0_4_9.md) | 31 July 2026 | The record mapper: every wire shape, both PATCH styles, one rule for null | [To 0.4.9](release-history/upgrading.md#to-049) |
| [0.4.8](release-history/v0_4_8.md) | 17 July 2026 | Async typed errors, path-native resilience, accumulating validation and record mapping | None |
| [0.4.7](release-history/v0_4_7.md) | 26 June 2026 | Declarative Spring HTTP clients and type-safety improvements | [To 0.4.1 through 0.4.8](release-history/upgrading.md#to-041-048) |
| [0.4.6](release-history/v0_4_6.md) | 7 June 2026 | Optic-driven request batching, guardrails, and n-ary coupled lenses | [Removals in 0.5.0](release-history/upgrading.md#removals-in-050) |
| [0.4.5](release-history/v0_4_5.md) | 22 May 2026 | The `Instances` facade, more compile-time checks, and hardened migration recipes | [To 0.4.1 through 0.4.8](release-history/upgrading.md#to-041-048) |
| [0.4.4](release-history/v0_4_4.md) | 16 May 2026 | The hkj-test module, PCollections integration, and type class enrichments | [Removals in 0.5.0](release-history/upgrading.md#removals-in-050) |
| [0.4.3](release-history/v0_4_3.md) | 7 May 2026 | Pluggable HTTP error status, header carriers, and a documentation refresh | [To 0.4.1 through 0.4.8](release-history/upgrading.md#to-041-048) |
| [0.4.2](release-history/v0_4_2.md) | 18 April 2026 | EffectBoundary, Claude Code skills, and Spring HTTP ergonomics | [To 0.4.1 through 0.4.8](release-history/upgrading.md#to-041-048) |
| [0.4.1](release-history/v0_4_1.md) | 8 April 2026 | Effect handlers, Spring observability, and monad transformer enhancements | [To 0.4.1 through 0.4.8](release-history/upgrading.md#to-041-048) |
| [0.4.0](release-history/v0_4_0.md) | 22 March 2026 | SPI-aware path widening, an expanded generator plugin ecosystem, and a Focus DSL restructure | None |
| [0.3.7](release-history/v0_3.md#v037-15-march-2026) | 15 March 2026 | WriterT, MTL capabilities, smarter comprehensions, and one-line build setup | None |
| [0.3.6](release-history/v0_3.md#v036-6-march-2026) | 6 March 2026 | VStream lazy streaming, resilience patterns, and ForState comprehensions | None |
| [0.3.5](release-history/v0_3.md#v035-15-february-2026) | 15 February 2026 | Extended for-comprehensions, a VTask API refinement, and a documentation restructure | [To 0.3.5](release-history/upgrading.md#to-035) |
| [0.3.4](release-history/v0_3.md#v034-31-january-2026) | 31 January 2026 | External type optics and the examples gallery | None |
| [0.3.3](release-history/v0_3.md#v033-24-january-2026) | 24 January 2026 | Structured concurrency, atomic optics, and enhanced examples | None |
| [0.3.2](release-history/v0_3.md#v032-17-january-2026) | 17 January 2026 | Virtual thread concurrency with VTask | None |
| [0.3.1](release-history/v0_3.md#v031-15-january-2026) | 15 January 2026 | Static analysis utilities | None |
| [0.3.0](release-history/v0_3.md#v030-4-january-2026) | 4 January 2026 | The Effect Path API with Focus DSL integration | [To 0.3.0](release-history/upgrading.md#to-030) |
| [0.1.0 to 0.2.8](release-history/earlier.md) | May to December 2025 | The HKT core types, optics, the Free monad, and the first Effect Path API and Focus DSL | [v0.1.4 notes](release-history/earlier.md#v014-5-june-2025) |

---

## Since which version? {#since-which-version}

The release that first shipped each feature, with the page that teaches it.

### Types and effects {#since-types-and-effects}

| Feature | Since | Read about it |
|---|---|---|
| `Either`, `Try`, `IO`, `Lazy`, `Reader`, `State`, `Writer` and `EitherT` | [0.1.0](release-history/earlier.md#v010-3-may-2025) | [Monads](monads/ch_intro.md) |
| For comprehensions | [0.1.5](release-history/earlier.md#v015-12-june-2025) | [For Comprehension](functional/for_comprehension.md) |
| The Free monad and Trampoline | [0.2.0](release-history/earlier.md#v020-21-november-2025) | [Free Monad](monads/free_monad.md) |
| The Effect Path API | [0.2.6](release-history/earlier.md#v026-19-december-2025) | [Effect Path Overview](effect/effect_path_overview.md) |
| `ForPath` comprehensions | [0.2.8](release-history/earlier.md#v028-26-december-2025) | [ForPath Comprehension](effect/forpath_comprehension.md) |
| `WriterT` and the MTL capability interfaces | [0.3.7](release-history/v0_3.md#v037-15-march-2026) | [WriterT](transformers/writert_transformer.md) |
| Effect handlers: `@EffectAlgebra` and `@ComposeEffects` | [0.4.1](release-history/v0_4_1.md) | [Effect Handlers](effect/effect_handlers.md) |
| The `Instances` facade | [0.4.5](release-history/v0_4_5.md) | [Obtaining Instances](functional/instances_facade.md) |
| `VResultPath` | [0.4.8](release-history/v0_4_8.md) | [VResultPath](effect/path_vresult.md) |
| `NonEmptyList` and `EitherOrBoth` | [0.4.8](release-history/v0_4_8.md) | [NonEmptyList](monads/nonemptylist_monad.md), [EitherOrBoth](monads/either_or_both_monad.md) |

### Optics {#since-optics}

| Feature | Since | Read about it |
|---|---|---|
| Lens, Iso, Prism and Traversal, generated from annotations | [0.1.6](release-history/earlier.md#v016-14-july-2025) | [Optics](optics/ch_intro.md) |
| The Focus DSL | [0.2.5](release-history/earlier.md#v025-9-december-2025) | [Focus DSL](optics/focus_dsl.md) |
| Coupled fields: `Lens.paired` | [0.3.3](release-history/v0_3.md#v033-24-january-2026) | [Coupled Fields](optics/coupled_fields.md) |
| Optics for types you cannot change: `@ImportOptics` | [0.3.4](release-history/v0_3.md#v034-31-january-2026) | [Importing Optics](optics/importing_optics.md) |
| Traversal generator plugins | [0.1.6](release-history/earlier.md#v016-14-july-2025) | [Generator Plugins](tooling/generator_plugins.md) |
| SPI path widening, and generators for Guava, Vavr, Eclipse Collections and Apache Commons | [0.4.0](release-history/v0_4_0.md) | [Generator Plugins](tooling/generator_plugins.md) |
| Optic-driven request batching | [0.4.6](release-history/v0_4_6.md) | [Optic-Driven Batching](optics/optic_batching.md) |

### Validation and mapping {#since-validation-and-mapping}

| Feature | Since | Read about it |
|---|---|---|
| Accumulating assembly: `fields()`, `accumulate()` and `@GenerateAssembly` | [0.4.8](release-history/v0_4_8.md) | [Validated Assembly](monads/validated_assembly.md) |
| `@GenerateMapping` and `@GenerateMerge` | [0.4.8](release-history/v0_4_8.md) | [Mapping at the Boundary](mapping/ch_intro.md) |
| Bean-shaped wires and sparse PATCH with `UpdateSpec` | [0.4.9](release-history/v0_4_9.md) | [Bean-Shaped Wires](mapping/beans.md), [Sparse PATCH](mapping/beans_patch.md) |
| Standard codecs | [0.4.10](release-history/v0_4_10.md) | [Standard codecs](mapping/codecs.md#standard-codecs) |
| protobuf-java messages and openapi-generator models | [0.4.11](release-history/v0_4_11.md) | [Bean-Shaped Wires](mapping/beans.md#protobuf-java-messages) |
| One-directional beans and `@ReadOnly` properties | [0.4.11](release-history/v0_4_11.md) | [One-directional beans](mapping/beans.md#one-directional-beans), [Properties you only read](mapping/beans.md#read-only-properties) |
| `JsonNullable` properties in a sparse PATCH | [0.4.11](release-history/v0_4_11.md) | [What each JSON state does](mapping/beans_patch.md#what-each-json-state-does) |
| `@Flatten`: a nested record on a flat wire | [0.4.11](release-history/v0_4_11.md) | [Flattening a nested component](mapping/structure.md#flattening-a-nested-component-onto-a-flat-wire) |
| Specs that nest, dispatch and merge across modules | [0.4.11](release-history/v0_4_11.md) | [Across modules](mapping/structure.md#across-modules) |

### Concurrency and resilience {#since-concurrency-and-resilience}

| Feature | Since | Read about it |
|---|---|---|
| `VTask` on virtual threads | [0.3.2](release-history/v0_3.md#v032-17-january-2026) | [VTask](monads/vtask_monad.md) |
| Structured concurrency with `Scope` | [0.3.3](release-history/v0_3.md#v033-24-january-2026) | [Structured Concurrency](monads/vtask_scope.md) |
| `VStream` lazy streaming | [0.3.6](release-history/v0_3.md#v036-6-march-2026) | [VStream](monads/vstream.md) |
| Retry policies | [0.2.6](release-history/earlier.md#v026-19-december-2025) | [Retry](resilience/retry.md) |
| Circuit breaker, bulkhead and saga | [0.3.6](release-history/v0_3.md#v036-6-march-2026) | [Resilience](resilience/ch_intro.md) |
| One `with*` resilience vocabulary across every Path | [0.4.8](release-history/v0_4_8.md) | [Resilience](resilience/ch_intro.md) |

### Spring {#since-spring}

| Feature | Since | Read about it |
|---|---|---|
| `EffectBoundary` for Free programs | [0.4.2](release-history/v0_4_2.md) | [EffectBoundary Integration](spring/effect_boundary_integration.md) |
| Pluggable error status: `ErrorStatusCodeStrategy` | [0.4.3](release-history/v0_4_3.md) | [Spring Boot Integration](spring/spring_boot_integration.md) |
| Declarative HTTP clients: `@HkjHttpClient` | [0.4.7](release-history/v0_4_7.md) | [Declarative HTTP Clients](spring/declarative_http_clients.md) |
| The 422 response for every bad field | [0.4.9](release-history/v0_4_9.md) | [The 422 leg](spring/spring_boot_integration.md#the-422-leg) |

### Tooling {#since-tooling}

| Feature | Since | Read about it |
|---|---|---|
| Build plugins, `hkj-bom` and compile-time checks | [0.3.7](release-history/v0_3.md#v037-15-march-2026) | [Build Plugins](tooling/gradle_plugin.md), [Compile-Time Checks](tooling/compile_checks.md) |
| Claude Code skills | [0.4.2](release-history/v0_4_2.md) | [Claude Code Skills](tooling/claude_code_skills.md) |
| `hkj-test` assertions | [0.4.4](release-history/v0_4_4.md) | [Testing With hkj-test](tooling/test_assertions.md) |
| PCollections integration | [0.4.4](release-history/v0_4_4.md) | [PCollections Integration](tooling/pcollections_integration.md) |
| Migration recipes on Maven Central: `hkj-openrewrite` | [0.4.11](release-history/v0_4_11.md) | [Migration Recipes](tooling/openrewrite.md) |

---

~~~admonish tip title="See Also"
- [GitHub Releases](https://github.com/higher-kinded-j/higher-kinded-j/releases): Full changelogs and assets
- [Contributing](CONTRIBUTING.md): How to contribute to Higher-Kinded-J
~~~

---

**Previous:** [Concurrency & Resilience](glossary/concurrency.md)
**Next:** [Upgrading](release-history/upgrading.md)
