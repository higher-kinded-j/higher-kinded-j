# Coming from MapStruct and Bean Validation

_Your existing vocabulary, translated, and the cases where you should keep what you have._

Most readers arrive already mapping DTOs, with a `@Mapper` interface and a set of `@Valid`
constraints. Almost every habit carries over; three do not, and one of them is a trap. This page
translates both vocabularies and marks where the analogy breaks.

---

## From MapStruct

The shapes are close enough that a first spec usually reads like the mapper it replaces.

| MapStruct | Here | Note |
|---|---|---|
| `@Mapper interface M` with `Mappers.getMapper(M.class)` | `@GenerateMapping interface M extends MappingSpec<Domain, Wire>`, then `MImpl.INSTANCE` | one declaration gives both directions |
| `@Mapping(target = "name", source = "fullName")` | `@MapField(to = "fullName") String name();` | the method is named after the **domain** component |
| Built-in `String` to `UUID`, enum or date conversion | a `default` leaf returning `StandardCodecs.uuid()` and friends | never implicit: a conversion exists where a spec declares it |
| `uses = UuidMapper.class` | the same leaf, shared through a [mix-in vocabulary](codecs.md#shared-vocabulary-mix-in-interfaces) | shared by name |
| `@Named` plus `qualifiedByName` | the leaf **is** the named method | |
| `expression = "java(...)"`, or `@AfterMapping` filling a target field | a `default` method returning `Getter<Domain, T>` | [a derived field](basics.md#derived-wire-fields), build-side only |
| `uses = CustomerMapper.class` for a nested type | nothing: a spec for the pair nests automatically | failures gain the outer component's path |
| `@Mapping(target = "a.b", source = ...)`, deep target paths | [`@Flatten`](structure.md#flattening-a-nested-component-onto-a-flat-wire), one level | deeper flattening stays MapStruct's |
| `@SubclassMapping` | [sealed dispatch](structure.md#sealed-hierarchies) | exhaustive both ways, or it does not compile |
| `@MappingTarget` plus `NullValuePropertyMappingStrategy.IGNORE` | [`UpdateSpec`](beans_patch.md#sparse-patch-write-back-updatespec) and `updateFrom(wire).apply(current)` | returns `Validated`; nested objects replace wholesale |
| `@MappingTarget` for a dense write-back | a projection's [`patch(domain, wire)`](tiers.md#leaf-carrying-projections-the-validated-patch) or `asLens()` | every projected field written, and validated |
| `ignore = true` on a target | [`@Unmapped`](beans_patch.md#accessors-meant-to-stay-out) on a bean accessor; a narrower wire is simply a projection | |
| `unmappedTargetPolicy = ERROR` | always on | an unmapped wire component is a compile error |
| `@InheritInverseConfiguration` | not needed | the inverse is the same declaration |
| `componentModel = "spring"` | a `@Bean` of `ValidatedPrism<Wire, Domain>` from `asValidatedPrism()` | [Injecting and testing](testing.md#injecting-and-testing-generated-mappings) |
| A conversion that throws | a located `FieldError` in an accumulating `parse` | nothing throws for bad data |
| A mutable JPA entity as the target | **not supported**: the domain must be a record | keep MapStruct here |

### The three that do not carry over

1. **No implicit conversions.** MapStruct converts `String` to `UUID` because it can. Here a
   conversion exists only where a spec declares it, so a first migration adds a leaf per converted
   field. A [mix-in vocabulary](codecs.md#shared-vocabulary-mix-in-interfaces) pays that back: declare
   the house conversions once, extend them everywhere.
2. **Error paths are domain-named.** A renamed field reports at the domain's name, not the wire's.
   Clients that map errors onto their own payload keys need the rename applied in reverse.
3. **`Mappers.getMapper` has no equivalent on the interface.** Declaring
   `M MAPPER = MImpl.INSTANCE;` on the spec compiles and then reads `null`, intermittently, because
   of the class-initialisation cycle: [bind it in the caller](basics.md#bind-in-the-caller).

### Migrating one pair

1. Write the spec beside the existing mapper. Both can live in one module.
2. Assert they agree on a golden set of inputs, then delete the old assertions.
3. Add [one `MappingLaws` call](tiers.md#law-checked-in-the-repo-and-in-your-tests), which checks
   the round trip the old mapper never promised.
4. Switch the controller to return `parse` and let [the 422
   leg](../spring/spring_boot_integration.md#the-422-leg) render the failures.

---

## From Bean Validation

`@Valid` already accumulates errors, and they already carry field names. The difference is what you
hold afterwards: a set of violations about a DTO, against a domain value that has been built and
checked in one step.

| Bean Validation | Here |
|---|---|
| `@NotNull` on a wire field | automatic: every reference `parse` reads is null-guarded, and a `null` is a located error |
| `@Valid` on a nested object | automatic: a nested spec parses it, and failures gain its component's path |
| `@Valid` on a collection's elements | automatic: containers lift their element's leaf or spec, and locate by index or key |
| `@Email`, `@Pattern`, `@Size`, `@Min` | a [leaf](basics.md#validated-leaves) on that component, or a stock codec |
| A cross-field `@AssertTrue` | the domain record's own compact constructor: [its refusal is located too](basics.md#constructor-invariants) |
| `Set<ConstraintViolation<T>>` | `Validated<NonEmptyList<FieldError>, Domain>`, which carries the built value on success |
| `violation.getPropertyPath()` | `FieldError.path()`, or its structured `segments` |
| `@ControllerAdvice` translating violations | nothing: return the parse result and the starter renders it |

~~~admonish warning title="Validate, do not normalise"
A leaf must accept exactly what it renders, so trimming or case-folding inside one breaks the law
the round trip rests on: the value would no longer rebuild to what the client sent. Normalise
before the boundary, or model the normalised form as its own domain type with its own canon.
~~~

Two constraints have no equivalent, and both are deliberate. A constraint group (`groups = ...`)
has no counterpart: a spec is one contract, and a second contract is a second spec, which is how
the [sparse PATCH tier](beans_patch.md#sparse-patch-write-back-updatespec) works. And a validator
that reaches a database or another service does not belong in a leaf, which is a pure function;
that check belongs after the boundary, on the [effect railway](../effect/ch_intro.md).

---

~~~admonish info title="Where next"
- Five steps to a working endpoint: [Quickstart](quickstart.md)
- What the generated code looks like, and what it costs: [Mapper at a Glance](at_a_glance.md)
- The stock conversions, so most fields need no leaf: [Standard Codecs](codecs.md)
~~~

---

**Previous:** [Mapper at a Glance](at_a_glance.md)
