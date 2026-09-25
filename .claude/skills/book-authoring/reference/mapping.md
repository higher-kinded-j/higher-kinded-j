# The Mapping Chapter

Read `docs/MAPPING-CHAPTER-GUIDE.md` first. It sets out the chapter's three lanes, where each kind
of rule lives, the page shape and how claims are proved. This file adds the procedure for a feature
that changes the chapter, and the traps the chapter has already met.

## A feature that adds a rule or a refusal

1. **An enforced rule** gets one heading on `hkj-book/src/mapping/rules.md` and a row in its "Find
   your limit" or "Find your symptom" table. The teaching page links to it in one sentence.
2. **A refusal readers commonly meet** gets an entry in
   `hkj-book/tools/compiler-messages/messages.py`, and the page is regenerated. Run the script once
   before editing, because it reproduces the committed page exactly, and any diff after that is
   yours:

   ```bash
   ./gradlew :hkj-examples:bookMessagesClasspath
   python3 hkj-book/tools/compiler-messages/build_messages.py
   ```

3. **A rule the processor cannot check** becomes a "Not checked for you: …" warning on the lane page
   that teaches the feature, with an example the build proves.
4. **A capability that does not exist** is "not supported yet", in the processor's own words. Copy
   the wording from the diagnostic.

## Traps the chapter has met

- **The tier question is "is it a plain copy", not "can it fail".** A rename or a flattened group is
  a plain copy. A leaf is not, even one that never fails, and neither is a nested spec, an
  `@OptionalBridge` component or a bean's reference property. `asIso()` and `asLens()` follow this
  rule, and a sealed pair never gets `asIso()`.
- **A leaf cannot fill a primitive.** A component a leaf converts is declared `Integer`, not `int`,
  and the processor's refusal asks for the wrapper type.
- **Jackson's defaults coerce numbers.** A number-typed wire field truncates `2.5` to `2`, takes
  `"042"` as `42`, and rejects only an unreadable value, with its own 400. A `String` wire field
  binds a JSON number as its digits, so a leaf sees every bad value. `StandardCodecsBookTest` proves
  both.
- **Jackson cannot bind a sealed wire interface without type information.** A sealed request DTO needs
  `@JsonTypeInfo`. The structure page's example uses `DEDUCTION` with `@JsonSubTypes`.
- **Each producer writes timestamps its own way.** A browser's `toISOString()` always writes three
  fractional digits and `Z`. Python's `isoformat()` writes `+00:00`, with six fractional digits or
  none. A stock codec accepts only its own spelling, so the codecs page's chooser table gives the
  leaf for each producer.
- **The Impl is bound in the caller, never on the spec.** A constant on the spec initialised from
  `XImpl.INSTANCE` can read `null`, depending on which class the program touches first.
- **A generated companion whose static initialisation failed stays broken.** The first use throws
  `ExceptionInInitializerError`, and every later one `NoClassDefFoundError`. A test that proves it
  must be the only code touching that companion.
- **The fallible `MappingLaws` overload checks `build` after `parse`.** A sample for a normalising leaf
  must already be in its normal form. Only the patch tier leaves that law out.
- **Mockito mocks final classes, but refuses a sealed interface.** Say that. Do not say that no mocking
  framework can mock a generated Impl.
- **Preview features pin the build to one JDK release.** The book says Higher-Kinded-J is "built on
  Java 25 today". A consumer that uses `Either` or a plain `VStream` compiles and runs with no
  preview flag.
- **The mermaid check only parses.** It cannot see a missing `classDef` or a diagram wider than the
  page. A sequence diagram with four participants needs an `actorMargin` init directive and short
  labels to fit.
