# The Mapping Chapter

Read `docs/MAPPING-CHAPTER-GUIDE.md` first. It holds the chapter's rules: its three reading lanes,
where each kind of rule lives, which refusals get a Compiler Messages entry, and how the examples
are laid out. This file adds the order to work in and the traps the chapter has met.

## A feature that adds a rule, a refusal or a diagnostic

Work in this order, on the feature's own branch:

1. **Write the rule's home first**: its heading on `hkj-book/src/mapping/rules.md`, with an explicit
   `{#id}`, or its "Not checked for you" warning on the teaching page. Everything else links to it.
2. **Add its row** to the matching table on Rules and Limits, as the guide's "Where a Rule Lives"
   table says.
3. **Add a Compiler Messages entry** if the guide says the refusal qualifies. Follow the generator's
   README (`hkj-book/tools/compiler-messages/README.md`): it covers the entry's fields, the `SHORT`
   line each entry needs, and the run order.
4. **Give the teaching page its one sentence and link.**
5. **Check the other places that describe the mapper**, as the guide lists them.
6. **Run the checks** from the skill's workflow. The anchor check proves every new link resolves.

## Traps the chapter has met

Where a trap names a page, the book states and proves it there. Check that page before repeating
the claim, since the processor or a library can change under it.

- **The tier question is "is it a plain copy", not "can it fail".** A rename or a flattened group is
  a plain copy. A leaf is not, even one that never fails, and neither is a nested spec, an
  `@OptionalBridge` component or a bean's reference property. `asIso()` and `asLens()` follow this
  rule, and a sealed pair never gets `asIso()`. (`tiers.md#which-methods-your-spec-gets`)
- **A leaf cannot fill a primitive.** A component a leaf converts is declared `Integer`, not `int`,
  and the processor's refusal asks for the wrapper type. (`codecs.md#standard-codecs`)
- **Jackson's defaults coerce numbers.** A number-typed wire field truncates `2.5` to `2`, takes
  `"042"` as `42`, and rejects only an unreadable value, with its own 400. A `String` wire field
  binds a JSON number as its digits, so a leaf sees every bad value. `StandardCodecsBookTest`
  asserts what Jackson binds and throws; the 400 is Spring's mapping of that exception.
  (`codecs.md#standard-codecs`)
- **Jackson cannot bind a sealed wire interface without type information.** A sealed request DTO
  needs `@JsonTypeInfo`; the example uses `DEDUCTION` with `@JsonSubTypes`.
  (`structure.md#sealed-hierarchies`)
- **Each producer writes timestamps its own way.** A browser's `toISOString()` always writes three
  fractional digits and `Z`. Python's `isoformat()` on an aware UTC `datetime` writes `+00:00`, with
  six fractional digits or none. A stock codec accepts only its own spelling, so the chooser table
  gives a leaf for each producer. (`codecs.md#canonical-forms-only`)
- **The Impl is bound in the caller, never on the spec.** A constant on the spec initialised from
  `XImpl.INSTANCE` can read `null`, depending on which class the program touches first.
  (`basics.md#bind-in-the-caller`)
- **A class-initialisation trap needs a fresh class loader to test.** Static initialisation is
  global to the JVM, so an earlier test can hide the trap. The mapping tests' `FreshPackage` helper
  loads a package afresh.
- **A generated companion whose static initialisation failed stays broken.** The first use throws
  `ExceptionInInitializerError`, and every later one `NoClassDefFoundError`. A test that proves it
  must be the only code touching that companion.
  (`merge_envelopes.md#generating-error-envelopes-generateerrorenvelope`)
- **Two `MappingLaws` overloads check `build` after `parse`**: the `asIso()` one and the fallible
  `asValidatedPrism()` one. So a fallible mapping's parsing sample through a normalising leaf must
  already be in its normal form. The other overloads do not check it.
  (`tiers.md#law-checked-in-the-repo-and-in-your-tests`)
- **Mockito mocks final classes, but refuses a sealed interface.** Say that. Do not say that no
  mocking framework can mock a generated Impl.
  (`testing.md#injecting-and-testing-generated-mappings`)
- **Preview features pin the build to one JDK release.** The book says Higher-Kinded-J is "built on
  Java 25 today". A consumer that uses `Either` or a plain `VStream` compiles and runs on Java 25
  with no preview flag. (`../quickstart.md#prerequisites`, the note "Where the flag is actually
  needed")
- **The mermaid check only parses.** It cannot see a missing `classDef` or a diagram wider than the
  page. A sequence diagram with four participants needs an `actorMargin` init directive and short
  labels to fit.
