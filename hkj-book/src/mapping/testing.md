# Injecting, Testing, and Diagnostics

_Register the surface you consume, fake it with values, and read the processor's what/why/fix rejections._

A generated Impl is a pure function, so most code should just call it. This page covers the seams around that: what to register when you *do* want a Spring bean or a test double, how fakes work without mocks, and the diagnostics and limits that bound the feature.

~~~admonish info title="What You'll Learn"
- Why the spec interface deliberately injects nothing, and which surface to register per tier
- Test doubles as two-line `ValidatedPrism.of(...)` values, no mocking framework involved
- The width story: no component ceiling, chunked `fields()` ladders past 16 legs
- The remaining limits, each with a what/why/fix diagnostic
~~~

## Injecting and testing generated mappings

A concrete or threaded Impl is a stateless pure function reached through statics (`INSTANCE`, `instance()`); an element-mapped Impl is an immutable value built by `of(...)`, carrying its element prisms. The spec interface deliberately declares nothing either way (`@Autowired UserMapping` injects nothing useful, by design). When you do want a Spring bean or a test double, register the **surface you consume**, per tier:

| Tier surface | Injectable shape | From |
|---|---|---|
| parse-capable mapping | `ValidatedPrism<UserDto, User>` | `UserMappingImpl.INSTANCE.asValidatedPrism()` |
| build only | `Function<User, UserDto>` | `UserMappingImpl.INSTANCE::build` |
| validated `patch` | `BiFunction<User, UserCardDto, Validated<NonEmptyList<FieldError>, User>>` | `UserCardMappingImpl.INSTANCE::patch` |
| sparse `updateFrom` | `Function<UserPatchDto, Edits.Accumulated<User>>` | `UserPatchMappingImpl.INSTANCE::updateFrom` |

```java
@Configuration
class MappingConfiguration {
  @Bean
  ValidatedPrism<UserDto, User> userCodec() {
    return UserMappingImpl.INSTANCE.asValidatedPrism();
  }
}
```

Spring resolves the full generic type, so codecs for different pairs coexist without ceremony; only two codecs for the *same* pair need a `@Qualifier`. An element-mapped Impl (`of(...)`) carries its prisms as state: construct it once, in the `@Bean` method.

**Fakes are values, not mocks.** `ValidatedPrism` is sealed, so it cannot be hand-implemented, and a mocking framework cannot mock it either (sealed types are unmockable). That is the design, not a limitation: a test double is two lines of `ValidatedPrism.of(...)`:

```java
@Bean
ValidatedPrism<UserDto, User> userCodec() {
  return ValidatedPrism.of(
      dto -> Validated.invalidNel(FieldError.of("rejected by the fake codec").at("email")),
      user -> new UserDto());
}
```

The [hkj-spring example app](../spring/spring_boot_integration.md) demonstrates the seam end to end: `MappingConfiguration` registers the codec, `UserController`'s parse endpoint injects it, and `UserParseFakeCodecSliceTest` substitutes the fake above in a `@WebMvcTest` slice and asserts the located 422 it produces. The same controller's PATCH endpoint deliberately calls `UserPatchMappingImpl.INSTANCE` directly: injection buys substitution, not lifecycle, and a team comfortable calling the Impl directly (`INSTANCE`, `instance()`, or one shared `of(...)` instance) loses nothing.

---

## Diagnostics and limits

There is no component ceiling. `parse` (and the validated `patch`, and `@GenerateMerge`'s fallible merge) is assembled with [`Validated.fields()`](../monads/validated_assembly.md) ladders, chunked and combined applicatively past 16 legs, so an externally fixed flat 20-or-30-field wire maps without grouping components into nested records, and behaves exactly like a narrow one (same located labels, same declaration-order accumulation, across chunk boundaries):

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/WideMappingLawsTest.java:wide_laws}}
```

The only width bound left is the JVM's constructor parameter-slot limit on the record itself (254 components in practice, fewer with `long`/`double`), which javac enforces at the record declaration. The hand-written `fields()` ladder keeps its 16-field arity; wider hand-written assemblies nest sub-records.

Every rejection follows the processor's what/why/fix standard: the message states what is wrong, why the mapper needs it, and the code to write. The remaining limits, each with its own diagnostic:

- Nested and sealed resolution sees specs **in the same compilation**. A spec extends `MappingSpec` directly, plus any plain [mix-in interfaces](codecs.md#shared-vocabulary-mix-in-interfaces); a mix-in that is itself a mapping spec, or a generic one, is diagnosed.
- `Map` components lift values only: keys are identity, so differing key types, raw `Map`s and wildcard type arguments are compile errors.
- A projection with any fallible correspondence emits the [validated `patch`](tiers.md#leaf-carrying-projections-the-validated-patch) write-back rather than `asLens()`; projections cannot carry derived fields (the write-back could never honour a recomputed component); generic records map as concrete instantiations, threaded specs or element-mapped specs ([Generic Specs](generics.md)), all three nestable; generic mappings stay record-to-record only.

---

~~~admonish info title="Key Takeaways"
* **Register the surface, not the spec**: `asValidatedPrism()`, `::build`, `::patch`, or `::updateFrom`, per tier
* **Fakes are two-line values**: `ValidatedPrism.of(...)` replaces the mocking framework, by design
* **No component ceiling**: chunked `fields()` ladders carry flat 20-or-30-field wires; only the JVM's 254-slot record limit remains
* **Rejections are what/why/fix**: every limit states what is wrong, why the mapper needs it, and the code to write
~~~

~~~admonish tip title="See Also"
- [Testing With hkj-test](../tooling/test_assertions.md#optic-laws) - `MappingLaws` and `assertThatFieldError`
- [Spring Boot Integration](../spring/spring_boot_integration.md) - The example app the injection seam comes from
- [Accumulating Assembly](../monads/validated_assembly.md) - The `fields()` builder behind the generated `parse`
~~~

---

**Previous:** [Merge and Error Envelopes](merge_envelopes.md)
**Next:** [Capstone: One 422, Every Bad Field](capstone.md)
