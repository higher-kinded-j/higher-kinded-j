# Unreleased: 0.4.11

_Not released yet: these changes are on `main`, and the `latest` book describes them._

To try them, depend on `0.4.11-SNAPSHOT` from the snapshots repository, as [Gradle SNAPSHOT Configuration](../tooling/manual_setup.md#gradle-snapshot-configuration) shows.

This release maps the wires that generated clients produce: openapi-generator models, protobuf-java messages, Lombok builders, and beans that are only read or only written. The processor now stops at your declaration, with a fix that compiles, on shapes that used to fail inside generated code or map the wrong value without a word. Generated code no longer fails a `-Werror` build over raw types, redundant casts or Higher-Kinded-J's own annotations.

~~~admonish info title="At a glance"
- **Generated models map as they are generated.** That covers openapi-generator's `JsonNullable` companions, `JsonNullable` PATCH properties and read-only properties, protobuf-java messages with oneofs and `FieldMask` updates, and Lombok's `@Singular` and `@SuperBuilder`.
- **Mappings share more.** One mix-in vocabulary serves every spec that extends it, and specs nest, dispatch and merge across modules.
- **More failures are located errors.** A record's own invariant, which used to throw, and a `null` at any depth, which used to pass, now join the accumulated errors.
- **Parsing a bad wire got cheaper.** A wire with five bad fields parses in about half the time, and 100,000 failures accumulate in milliseconds.
- **Other areas gain smaller improvements.** `toEitherPath` and the new `toValidationPathGet` take a supplier for their error, `Maybe` gains `toOptional()` and `stream()`, and generated optics compile under `-Xlint:rawtypes` and `-Xlint:cast` with `-Werror`.
- **Some changes alter what a running program does.** Most surface as a compile error that names its fix, so read [Upgrading from 0.4.10](#upgrading) first.
~~~

---

## Mapping {#mapping}

### New wires and shapes {#new-wires}

- **protobuf-java messages map both ways** ([#954](https://github.com/higher-kinded-j/higher-kinded-j/issues/954)): the processor reads a message by its fields, on the full runtime and the lite one. A oneof maps to a sealed type, and an `UpdateSpec` generates `updateFrom(message, mask)`, which edits only the fields a `FieldMask` names. See [protobuf-java messages](../mapping/beans.md#protobuf-java-messages).
- **openapi-generator models map as the generator writes them** ([#936](https://github.com/higher-kinded-j/higher-kinded-j/issues/936), [#935](https://github.com/higher-kinded-j/higher-kinded-j/issues/935), [#992](https://github.com/higher-kinded-j/higher-kinded-j/issues/992)): the processor leaves out the `getX_JsonNullable()` pair that `openApiNullable=true` adds. A `@ReadOnly` marker reads a property that has a getter and no setter, and a model holding such a model maps both ways. See [Properties you only read](../mapping/beans.md#read-only-properties).
- **A `JsonNullable` PATCH property keeps, clears or sets** ([#680](https://github.com/higher-kinded-j/higher-kinded-j/issues/680)): an `UpdateSpec` over an openapi-generator `spring` model reads all three JSON Merge Patch states. An omitted field keeps its value, a sent `null` clears an `Optional` component, and a value sets it, through its leaf if it has one. See [A `JsonNullable` property keeps, clears or sets](../mapping/rules.md#no-jsonnullable-patch-property).
- **A bean that is only read, or only written, maps one way** ([#703](https://github.com/higher-kinded-j/higher-kinded-j/issues/703)): a read model with no writer generates `parse` alone, and a write model with no getters generates `build` alone. `ValidatedPrism` now extends two halves, `ValidatedParse` and `ValidatedBuild`, so a one-way mapping nests wherever its direction is used. See [One-directional beans](../mapping/beans.md#one-directional-beans).
- **Lombok `@Singular` and `@SuperBuilder` builders map** ([#934](https://github.com/higher-kinded-j/higher-kinded-j/issues/934)): a `@Singular` collection is written whole through its collection setter, and a `@SuperBuilder` bean, whose `build()` is declared `C build()`, maps both ways. See [A Lombok `@Singular` collection](../mapping/rules.md#singular-collections).
- **A field that is renamed and converted maps** ([#981](https://github.com/higher-kinded-j/higher-kinded-j/issues/981)): for a wire's `emailAddress` string against a domain's `email: EmailAddress`, put `@MapField` on the component's leaf. It then means the rename and the conversion together. See [Renamed and converted](../mapping/basics.md#renamed-and-converted).
- **A record with no components maps** ([#983](https://github.com/higher-kinded-j/higher-kinded-j/issues/983)): a sealed hierarchy can now have an empty subtype, such as `record Collected()`. See [Sealed hierarchies](../mapping/structure.md#sealed-hierarchies).
- **`@Flatten` spreads a nested component across a flat wire** ([#674](https://github.com/higher-kinded-j/higher-kinded-j/issues/674)): a customer's `Address` maps to a wire carrying `street`, `city` and `postcode`, in both directions. A failure locates under the domain path, such as `address.street`. See [Flattening a nested component](../mapping/structure.md#flattening-a-nested-component-onto-a-flat-wire).
- **`Set`, reference array and `Map`-key components lift element by element** ([#675](https://github.com/higher-kinded-j/higher-kinded-j/issues/675)): through the component's leaf or nested spec. A `Map`'s keys convert through `@MapKey`, an array element locates by index, and a set element by its own rendering. See [Converting Map keys](../mapping/structure.md#converting-map-keys).
- **An optional object or list maps through its elements' spec** ([#825](https://github.com/higher-kinded-j/higher-kinded-j/issues/825), [#860](https://github.com/higher-kinded-j/higher-kinded-j/issues/860)): a domain `Optional<Address>` against a nullable `AddressDto` nests through the spec for the pair, and so does an optional list. A record wire needs only the bare `@OptionalBridge` marker. See [Optional nested objects](../mapping/structure.md#optional-nested-objects).
- **`@OptionalBridge` maps an `Optional` to a nullable record component** ([#673](https://github.com/higher-kinded-j/higher-kinded-j/issues/673)): the component then behaves as a bean property does. `parse` reads `null` as empty, and `build` writes `null` for an empty `Optional`. See [Optional fields](../mapping/absence.md#optional-bridge).
- **A narrower bean wire with a reference-typed property takes the validated `patch`** ([#702](https://github.com/higher-kinded-j/higher-kinded-j/issues/702)): as a record projection does, validating every projected property and reading the rest from the domain. See [Bean projections](../mapping/beans.md#bean-projections).

### Sharing across specs and modules {#sharing}

- **Specs nest, dispatch and merge across modules** ([#676](https://github.com/higher-kinded-j/higher-kinded-j/issues/676)): a compilation finds the specs on its classpath through an index the processor writes beside each Impl. A spec in your own compilation wins over one from a dependency. See [Across modules](../mapping/structure.md#across-modules).
- **One mix-in vocabulary serves every spec that extends it** ([#826](https://github.com/higher-kinded-j/higher-kinded-j/issues/826), [#835](https://github.com/higher-kinded-j/higher-kinded-j/issues/835), [#836](https://github.com/higher-kinded-j/higher-kinded-j/issues/836)): an inherited member that binds to nothing stays inert. One interface then serves a full mapping, its projections, its PATCH sibling and its sealed dispatch. See [What an inherited member binds against](../mapping/rules.md#what-an-inherited-member-binds-against).
- **A vocabulary published from another module keeps its meaning** ([#844](https://github.com/higher-kinded-j/higher-kinded-j/issues/844)): `@MapField`, `@OptionalBridge` and `@MapKey` are now kept in the class file. One API module can own the vocabulary its service modules extend. See [Across modules](../mapping/structure.md#across-modules).
- **A spec can name a type another annotation processor generates** ([#861](https://github.com/higher-kinded-j/higher-kinded-j/issues/861)): the spec waits until the type is written, and so does any spec that nests it. See [Mapping over types other processors generate](../tooling/manual_setup.md#mapping-over-types-other-processors-generate).

### Nulls, copies and invariants {#nulls-copies-invariants}

- **A record's own invariant is reported as a located error** ([#872](https://github.com/higher-kinded-j/higher-kinded-j/issues/872)): a `RuntimeException` from a compact constructor becomes a `FieldError` at the record's path, such as `ranges.1: lo > hi`. One bad value no longer turns a 422 into a 500. See [A record's own invariants](../mapping/absence.md#constructor-invariants).
- **The `fields()` builders end in `construct` for a constructor that may refuse** ([#897](https://github.com/higher-kinded-j/higher-kinded-j/issues/897)): `construct(Range::new, "not a valid Range")` gives a hand-written assembly the same guard, on `Validated`, `Path` and `EitherOrBoth`. See [When the record refuses](../monads/validated_assembly.md#construct).
- **A sparse `UpdateSpec` constructs the domain record once** ([#893](https://github.com/higher-kinded-j/higher-kinded-j/issues/893)): a constructor that checks its fields against each other sees only the values the PATCH ends on. `Edits.accumulate(focus, edits...)` does the same for a hand-written PATCH. See [Fields a constructor checks together](../optics/multi_edit.md#fields-a-constructor-checks-together).
- **The null scan reaches every level of a copied container** ([#875](https://github.com/higher-kinded-j/higher-kinded-j/issues/875)): a `null` inside `List<List<String>>`, an `ArrayList` or any other `Collection` or `Map` locates at its full path, such as `grid.0.1`. See [The null contract, precisely](../mapping/rules.md#the-null-contract-precisely).
- **The wire and the domain no longer share a container** ([#852](https://github.com/higher-kinded-j/higher-kinded-j/issues/852)): a same-typed `List`, `Set`, `Collection` or `Map` crosses as an unmodifiable copy, and an array as a clone. A declared subtype, such as `ArrayList`, still crosses as it is. See [Same-typed containers cross as copies](../mapping/rules.md#same-typed-containers-cross-as-copies).
- **An empty `Optional` writes `null` to a bean** ([#871](https://github.com/higher-kinded-j/higher-kinded-j/issues/871)): a bean's own field defaults no longer read back through `parse` as present values. See [Bean-shaped wire targets](../mapping/beans.md#bean-shaped-wire-targets).

### Refused where you wrote it {#refused-at-the-spec}

- **The processor refuses more shapes at the spec, naming the fix.** Most used to fail inside generated code, or to map the wrong value silently. [Rules and Limits](../mapping/rules.md#find-your-limit) lists every limit, and the new ones are:
  - a type the spec's package cannot see ([#918](https://github.com/higher-kinded-j/higher-kinded-j/issues/918))
  - a bridged `Optional` onto a site declared non-null ([#881](https://github.com/higher-kinded-j/higher-kinded-j/issues/881))
  - a bean accessor with no partner, where leaving it out would drop a value, unless `@Unmapped` names it ([#868](https://github.com/higher-kinded-j/higher-kinded-j/issues/868))
  - a spec extending both `MappingSpec` and `UpdateSpec` ([#837](https://github.com/higher-kinded-j/higher-kinded-j/issues/837))
  - a spec member the generated Impl cannot carry ([#762](https://github.com/higher-kinded-j/higher-kinded-j/issues/762))
  - a getter-only `List` asked to carry absence, or declared raw or with a wildcard ([#830](https://github.com/higher-kinded-j/higher-kinded-j/issues/830), [#839](https://github.com/higher-kinded-j/higher-kinded-j/issues/839), [#841](https://github.com/higher-kinded-j/higher-kinded-j/issues/841))
- **A spec that holds its Impl in a constant draws a warning** ([#984](https://github.com/higher-kinded-j/higher-kinded-j/issues/984)): the constant can read `null` once the spec has a leaf. Bind the Impl in the calling code, or keep it with `@SuppressWarnings("impl-constant")`. See [A spec never holds its Impl in a constant](../mapping/rules.md#impl-constant-on-a-spec).
- **Refusals offer fixes you can paste** ([#882](https://github.com/higher-kinded-j/higher-kinded-j/issues/882), [#919](https://github.com/higher-kinded-j/higher-kinded-j/issues/919), [#927](https://github.com/higher-kinded-j/higher-kinded-j/issues/927), [#985](https://github.com/higher-kinded-j/higher-kinded-j/pull/985)): each fix line compiles, or the message says the shape is not supported yet. A spec's own `@MapKey` leaf beside a whole-`Map` leaf is now refused rather than bypassed. See [Compiler Messages](../mapping/compiler_errors.md).
- **A wider wire's refusal names the components nothing fills** ([#933](https://github.com/higher-kinded-j/higher-kinded-j/issues/933)): a generated class's extra accessors are named, where they used to be counted. See [Generated clients: a checklist](../mapping/beans.md#generated-client-checklist).
- **An overloaded bean setter pairs by the getter's type** ([#933](https://github.com/higher-kinded-j/higher-kinded-j/issues/933)): whatever order the overloads are declared in. See [How a bean is read and written](../mapping/rules.md#how-a-bean-is-read-and-written).
- **A PATCH refusal over a domain `Optional` suggests an `Optional` property** ([#680](https://github.com/higher-kinded-j/higher-kinded-j/issues/680)): for a plain property against a domain `Optional`, since that shape already carries JSON Merge Patch's three states. See [Sparse PATCH](../mapping/beans_patch.md#sparse-patch-write-back-updatespec).

### Performance {#mapping-performance}

- **A generated mapping reads each leaf once** ([#952](https://github.com/higher-kinded-j/higher-kinded-j/issues/952), [#947](https://github.com/higher-kinded-j/higher-kinded-j/pull/947)): a leaf that builds its codec builds it once per Impl, and the stock codecs reject most malformed values without throwing. A wire with five bad fields parses in about half the time, faster than Bean Validation. See [Your own canon](../mapping/codecs.md#your-own-canon).
- **Many failures accumulate in time proportional to their number** ([#982](https://github.com/higher-kinded-j/higher-kinded-j/issues/982)): 100,000 failing elements take milliseconds rather than seconds. `Edits.combine`, `Edits.accumulate` and `Monoids.update().combineAll` apply any number of edits without overflowing the stack. See [Multi-Edit and Sparse Updates](../optics/multi_edit.md).
- **A wide PATCH bean compiles in under a second** ([#893](https://github.com/higher-kinded-j/higher-kinded-j/issues/893)): one with 64 properties used to take about 15 seconds.

### Smaller changes {#mapping-smaller}

- **An element-mapped spec's `of(...)` takes the spec's own leaves first** ([#876](https://github.com/higher-kinded-j/higher-kinded-j/issues/876)): inherited leaves follow, in the order the `extends` clause names their mix-ins. The generated factory documents each parameter. See [Leaf order in `of(...)`](../mapping/rules.md#leaf-order-in-of).
- **A derived field fills a primitive wire component** ([#954](https://github.com/higher-kinded-j/higher-kinded-j/issues/954)): declare it over the wrapper, such as `Getter<Domain, Integer>` for an `int`. See [Derived wire fields](../mapping/basics.md#derived-wire-fields).
- **Mapping works on a compiler that cannot name a type's source file** ([#859](https://github.com/higher-kinded-j/higher-kinded-j/issues/859)): with two limits. A spec waits only for a generated type it names itself, and a package two modules declare reads as a class that already exists. Builds with javac are unaffected.
- **A domain `Optional` over a wildcard-carrying element compiles** ([#838](https://github.com/higher-kinded-j/higher-kinded-j/issues/838)): such as `Optional<List<? extends CharSequence>>`, and a present list is scanned for `null` elements.
- **A package that two modules declare is reported by name** ([#861](https://github.com/higher-kinded-j/higher-kinded-j/issues/861)): where javac refuses to write a generated class, the message names the shared package and the modules declaring it.

---

## Optics {#optics}

- **Every `@ImportOptics` generates what it describes, or says why** ([#908](https://github.com/higher-kinded-j/higher-kinded-j/issues/908)): a spec that inherits its optics, or an empty class list, is reported at the annotation. It waits for a type another processor writes, and the ten notes it printed per spec are gone. See [Compiler Errors](../optics/compiler_errors.md#importoptics-and-opticsspec-interfaces).
- **A `@Wither` lens calls the overload its focus binds** ([#887](https://github.com/higher-kinded-j/higher-kinded-j/issues/887)): where a wither is overloaded, the processor checks the method javac will choose. Every method name a copy strategy carries is checked at the spec, not left to `cannot find symbol`. See [Copy Strategies](../optics/copy_strategies.md#wither-types-with-withx-methods).
- **`@ImportOptics` imports an inner class of a generic class** ([#878](https://github.com/higher-kinded-j/higher-kinded-j/issues/878)): its lenses are declared under the enclosing class's type parameters, such as `<X> Lens<Outer<X>.Line, String>`. A wither pairs only when it hands back the class. See [Wither Classes to Lenses](../optics/importing_optics.md#wither-classes-to-lenses).
- **Generated code calls a record's canonical constructor** ([#874](https://github.com/higher-kinded-j/higher-kinded-j/issues/874)): even beside a same-arity overload, such as `Money(Number major, String currency)`. That covers generated lenses, setters and Focus paths, and the mapper's `parse`, merges and `assemble()`. See [Every Write Runs the Canonical Constructor](../optics/lenses.md#every-write-runs-the-canonical-constructor).
- **`@ThroughField` checks the container and the focus at the declaration** ([#773](https://github.com/higher-kinded-j/higher-kinded-j/issues/773), [#779](https://github.com/higher-kinded-j/higher-kinded-j/issues/779)): a field declared as `ArrayList` or `TreeMap`, which threw `ClassCastException` on first use, is refused with the interface to declare. So is a focus that does not match the element. See [`@ThroughField` auto-detection](../optics/copy_strategies.md#throughfield-auto-detection).
- **A dependency's `@GenerateFocus` record is navigable** ([#847](https://github.com/higher-kinded-j/higher-kinded-j/issues/847)): the annotation is now kept in the class file, so navigation reaches into a record from a jar. See [Which fields get a navigator](../optics/focus_navigation.md#which-fields-get-a-navigator).
- **`Kind` fields take a wildcard element** ([#787](https://github.com/higher-kinded-j/higher-kinded-j/issues/787)): `Kind<ListKind.Witness, ? extends Role>` widens to `TraversalPath<Holder, Role>`, as `List<? extends Role>` already did. See [Kind Field Support](../optics/kind_field_support.md#convention-based-detection).
- **An imported record's containers traverse a wildcard element** ([#873](https://github.com/higher-kinded-j/higher-kinded-j/issues/873)): `List<? extends Number>` focuses `Number`, as it does under `@GenerateTraversals`. See [Container Fields Get Traversals](../optics/importing_optics.md#container-fields-get-traversals).
- **Generator priority decides on every route** ([#774](https://github.com/higher-kinded-j/higher-kinded-j/issues/774)): the highest `priority()` wins for `@GenerateTraversals`, the Focus DSL and `@ImportOptics`, and a tie draws a warning on each. See [How Plugin Discovery Works](../tooling/generator_plugins.md#how-plugin-discovery-works).
- **`@TraverseField` says when it is not applied** ([#789](https://github.com/higher-kinded-j/higher-kinded-j/issues/789), [#790](https://github.com/higher-kinded-j/higher-kinded-j/issues/790)): a note names the component and what would make the annotation apply. See [Compiler Errors](../optics/compiler_errors.md#traversefield-the-annotation-on-record-component-xy-is-not-applied-a-note).
- **`@GenerateFocus` accepts a wildcard container that nothing would widen** ([#758](https://github.com/higher-kinded-j/higher-kinded-j/issues/758)): such a component stays a plain `FocusPath`. See [Supported container types](../optics/focus_containers.md#supported-container-types).
- **Optics messages point at your declaration** ([#759](https://github.com/higher-kinded-j/higher-kinded-j/issues/759), [#771](https://github.com/higher-kinded-j/higher-kinded-j/issues/771), [#900](https://github.com/higher-kinded-j/higher-kinded-j/pull/900)): an `OpticsSpec` over a raw type is refused where it is declared, and a raw lens under `@ThroughField` is named as raw. A message names a type without its type-use annotations. See [Compiler Errors](../optics/compiler_errors.md).

---

## Effect Paths {#effect-paths}

- **`MaybePath`, `OptionalPath` and `AffinePath` take a supplier for the error** ([#794](https://github.com/higher-kinded-j/higher-kinded-j/issues/794), [#821](https://github.com/higher-kinded-j/higher-kinded-j/issues/821)): `toEitherPath(() -> new NotFound(id))` builds the error only on the branch that uses it, and on `MaybePath` and `OptionalPath`, `toValidationPathGet(() -> new NotFound(id), firstError)` does the same for a `ValidationPath`. See [MaybePath → EitherPath](../effect/conversions.md#maybepath--eitherpath) and [MaybePath → ValidationPath](../effect/conversions.md#maybepath--validationpath).
- **`Maybe` bridges to the JDK** ([#784](https://github.com/higher-kinded-j/higher-kinded-j/pull/784)): `toOptional()` completes the round trip `fromOptional` started, and `stream()` lets `.flatMap(Maybe::stream)` keep a pipeline's hits. See [Maybe Monad](../monads/maybe_monad.md#interacting-with-maybe-values).
- **A `@PathSource` Path's `peek` runs with its effect** ([#949](https://github.com/higher-kinded-j/higher-kinded-j/issues/949)): on a lazy witness such as `IO`, the action now runs when the effect runs.
- **`@PathSource` applies to a record, and waits for a generated witness** ([#949](https://github.com/higher-kinded-j/higher-kinded-j/issues/949)): a witness another processor writes in the same build, such as one from `@EffectAlgebra`, now works.
- **`@PathSource` checks its attributes at the annotation** ([#945](https://github.com/higher-kinded-j/higher-kinded-j/issues/945)): a `suffix`, `targetPackage`, witness or primitive `errorType` it cannot use is refused with a message saying why. Each used to fail inside the compiler or the generated file.
- **`@PathConfig` and two `@PathSource` capabilities are deprecated for removal** ([#890](https://github.com/higher-kinded-j/higher-kinded-j/issues/890), [#945](https://github.com/higher-kinded-j/higher-kinded-j/issues/945)): no processor ever read `@PathConfig`, and `EFFECTFUL` and `ACCUMULATING` generate what `CHAINABLE` and `RECOVERABLE` do. See [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).

---

## Spring {#spring}

- **A client keeps the `@OnStatus` overrides it inherits from a jar** ([#849](https://github.com/higher-kinded-j/higher-kinded-j/issues/849)): the annotations are now kept in the class file, and an inherited override is checked as a local one is. See [Declarative HTTP clients](../spring/declarative_http_clients.md#1-per-method-onstatus).
- **`spring = true` on the build plugins also generates `@HkjHttpClient` clients** ([#1002](https://github.com/higher-kinded-j/higher-kinded-j/pull/1002)): the Gradle and Maven plugins now add `hkj-spring-boot-client-processor` beside the starter, since a dependency never adds to the processor path. A build that wires Higher-Kinded-J by hand adds the processor itself. See [Declarative HTTP Clients](../spring/declarative_http_clients.md#step-1-add-the-starter).

---

## Testing {#testing}

- **`hasFieldErrors` asserts an `Invalid`'s located errors as rendered lines** ([#691](https://github.com/higher-kinded-j/higher-kinded-j/issues/691)): `assertThatValidated(result).hasFieldErrors("email: not an email address")` checks every error, in declaration order. See [`MaybeAssert` and `ValidatedAssert`](../tooling/test_assertions.md#maybeassert-and-validatedassert).
- **`MappingLaws` checks each direction on its own** ([#703](https://github.com/higher-kinded-j/higher-kinded-j/issues/703), [#935](https://github.com/higher-kinded-j/higher-kinded-j/issues/935)): a mapping that only parses, only builds, or carries `asValidatedParse()` and `asValidatedBuild()` without `asValidatedPrism()` is law-checked one direction at a time. See [Injecting and testing generated mappings](../mapping/testing.md#injecting-and-testing-generated-mappings).

---

## Build and tooling {#build-and-tooling}

- **Generated code compiles under `-Xlint:rawtypes` and `-Xlint:cast` with `-Werror`** ([#865](https://github.com/higher-kinded-j/higher-kinded-j/issues/865), [#867](https://github.com/higher-kinded-j/higher-kinded-j/issues/867), [#873](https://github.com/higher-kinded-j/higher-kinded-j/issues/873), [#877](https://github.com/higher-kinded-j/higher-kinded-j/issues/877)): a strict build no longer fails inside generated code over a raw type you wrote, or a redundant cast. Only `rawtypes` is suppressed. See [Build-time impact](../optics/production_readiness.md#build-time-impact).
- **The annotations Higher-Kinded-J reads draw no `-Xlint:processing` warning** ([#877](https://github.com/higher-kinded-j/higher-kinded-j/issues/877)): a new `CompanionAnnotationProcessor` claims `@MapField`, `@Wither`, `@TraverseField` and the others the processors read without generating for. It generates nothing, and incremental compilation is unaffected. See [Build-time impact](../optics/production_readiness.md#build-time-impact).
- **Generated classes declare their constructors, and cast nothing redundant** ([#877](https://github.com/higher-kinded-j/higher-kinded-j/issues/877)): the classes `@EffectAlgebra` and `@HkjHttpClient` generate draw no `cast` or `missing-explicit-ctor` warning.
- **Type-use annotations in generated code follow what compiles** ([#895](https://github.com/higher-kinded-j/higher-kinded-j/pull/895)): one on a type variable, such as `@Nullable T`, is kept, and one the generated file cannot compile is left off. `TraversableGenerator` gains a `generateModifyF` overload carrying the package ([#900](https://github.com/higher-kinded-j/higher-kinded-j/pull/900)). See [Generator plugins](../tooling/generator_plugins.md).
- **A dependency's spec with a mix-in off the classpath is passed over** ([#895](https://github.com/higher-kinded-j/higher-kinded-j/pull/895)): the use site's error names the missing type, where javac used to report `cannot access` inside generated source. See [Multi-module builds](../tooling/manual_setup.md#multi-module-builds).
- **Every processor runs from the module path** ([#889](https://github.com/higher-kinded-j/higher-kinded-j/issues/889)): all twenty run there, and `Path.from()` finds a `VStreamPath`. Builds that use Gradle's `annotationProcessor` or Maven's `<annotationProcessorPaths>` see no change. See [PathProvider SPI Registration](../monads/vstream_advanced.md#pathprovider-spi-registration).
- **Coverage tools skip every generated type** ([#798](https://github.com/higher-kinded-j/higher-kinded-j/issues/798), [#817](https://github.com/higher-kinded-j/higher-kinded-j/issues/817)): nested generated types carry `@Generated` themselves, and traversals and folds are named nested classes rather than anonymous ones. See [Build-time impact](../optics/production_readiness.md#build-time-impact).
- **The Maven plugin adds the libraries at its own version** ([#1002](https://github.com/higher-kinded-j/higher-kinded-j/pull/1002)): without a `<version>` in its configuration it uses the plugin's version, as the Gradle plugin does, where it used your project's. See [Maven Users](../tooling/gradle_plugin.md#maven-users).
- **The Maven plugin configures the build's compile and test runs** ([#1002](https://github.com/higher-kinded-j/higher-kinded-j/pull/1002)): they get release 25, preview features, the HKJ processors and the checker, with no configuration of their own. `--enable-preview` joins surefire's `argLine` property, so JaCoCo's agent keeps its place, and `mvn hkj:diagnostics` prints the plugin's settings. See [Maven Users](../tooling/gradle_plugin.md#maven-users).
- **The Maven plugin installs skills and joins a named processor list** ([#1004](https://github.com/higher-kinded-j/higher-kinded-j/pull/1004)): `<skills>true</skills>` installs the skills once per build, into the reactor's root or else the first project that asks. A build naming its processors in `<annotationProcessors>` gets HKJ's while its HKJ processors are the plugin's release. The goals accept the plugin's `<configuration>`. See [Maven Users](../tooling/gradle_plugin.md#maven-users).
- **The Claude Code skills describe 0.4.11** ([#987](https://github.com/higher-kinded-j/higher-kinded-j/pull/987)): `hkj-mapping` covers protobuf-java messages, openapi-generator models, Lombok builders, `@Flatten`, `@ReadOnly` and one-directional beans, and the other skills follow this release's API changes. The build plugins install them with `skills = true`. See [Claude Code Skills](../tooling/claude_code_skills.md).
- **`hkj-openrewrite` is published, and `hkj-bom` manages every published module** ([#1002](https://github.com/higher-kinded-j/higher-kinded-j/pull/1002)): the migration recipes are on Maven Central from 0.4.11, and the BOM now covers `hkj-processor`, `hkj-spring-boot-client` and `hkj-spring-boot-client-processor`. See [Migration Recipes](../tooling/openrewrite.md).

---

## Documentation {#documentation}

- **The Mapping chapter starts with a quickstart to your first 422** ([#914](https://github.com/higher-kinded-j/higher-kinded-j/pull/914), [#921](https://github.com/higher-kinded-j/higher-kinded-j/pull/921)): [Quickstart: Your First 422](../mapping/quickstart.md) leads, and [Absent Fields and Record Invariants](../mapping/absence.md) and [Bean-Shaped Wires](../mapping/beans.md) get pages of their own.
- **Four lookup pages answer the questions mapper users arrive with**: [Mapper at a Glance](../mapping/at_a_glance.md), [Coming from MapStruct and Bean Validation](../mapping/from_mapstruct.md), [Rules and Limits](../mapping/rules.md) and [Compiler Messages](../mapping/compiler_errors.md).
- **A second capstone maps one domain across three modules**: [Capstone: An Estate in Three Modules](../mapping/estate.md), and [Check Your Understanding](../mapping/self_check.md) closes the first route.
- **The mapping benchmark sets parse and build against MapStruct** ([#948](https://github.com/higher-kinded-j/higher-kinded-j/pull/948)): see [The Mapping Benchmark](../benchmarks.md#the-mapping-benchmark).
- **The Lenses page says when a wither is enough** ([#997](https://github.com/higher-kinded-j/higher-kinded-j/pull/997)): a wither, Lombok's `@With` included, suits a change one level deep made once. A composed lens reaches any depth in one call, and updates by function or by effect. See [Why a lens, when you have `@With`?](../optics/lenses.md#lens-or-wither).
- **The error catalogues lead with the answer** ([#819](https://github.com/higher-kinded-j/higher-kinded-j/pull/819), [#822](https://github.com/higher-kinded-j/higher-kinded-j/pull/822)): the [Optics](../optics/compiler_errors.md), [Effect Path](../effect/compiler_errors.md) and [transformer](../transformers/common_errors.md) compiler errors each open with a table of messages. The transformer table says which mistakes javac catches and which nothing does.
- **An untyped sealed request body is documented as a 500** ([#942](https://github.com/higher-kinded-j/higher-kinded-j/pull/942)): Jackson's failure escapes the controller, where the book had said Jackson answers 400. See [Sealed hierarchies](../mapping/structure.md#sealed-hierarchies).
- **The tutorials give their size in exercises, not minutes** ([#1003](https://github.com/higher-kinded-j/higher-kinded-j/pull/1003)): every journey table, journey page and Hands-On Learning link drops its time estimate. The landing pages list all nineteen journeys, and the build holds every exercise count to the tutorial code. See [Interactive Tutorials](../tutorials/tutorials_intro.md).
- **The book says which Java it means, and where `--enable-preview` is needed** ([#915](https://github.com/higher-kinded-j/higher-kinded-j/pull/915), [#1005](https://github.com/higher-kinded-j/higher-kinded-j/pull/1005)): Higher-Kinded-J is built on Java 25. Code using `Either`, `Validated`, the mapper or sequential optics needs no flag; code reaching `VTask`'s `Scope` and `Par`, or `VStreamPar`, does. See [Quickstart](../quickstart.md).
- **AI assistants can read the book as Markdown** ([#1005](https://github.com/higher-kinded-j/higher-kinded-j/pull/1005)): every page has a `.md` copy beside it, each chapter is published whole as `llms-<chapter>.txt`, and `llms.txt` indexes them all. Each page also carries its own search description.
- **The release history is a chapter** ([#998](https://github.com/higher-kinded-j/higher-kinded-j/pull/998)): a page per release, an [Upgrading](upgrading.md) guide read top down, and [Since which version?](../release-history.md#since-which-version) for the release that first shipped a feature.
- **Tutorial 27, Boundary Edge Cases, is new** ([#959](https://github.com/higher-kinded-j/higher-kinded-j/pull/959)): it joins the [Boundary Mapping journey](../tutorials/optics/boundary_mapping_journey.md).

---

## Upgrading from 0.4.10 {#upgrading}

For most changes, the processor stops at your declaration on a shape that used to fail inside generated code or map the wrong value, and its message names the fix. [What a running program can notice](#runtime-changes) lists the changes it cannot point at: read that list first.

### Before you upgrade {#before-you-upgrade}

- **Rebuild libraries that publish specs with 0.4.11 first.** That means mapping specs, mix-ins, `@GenerateFocus` records and `@HkjHttpClient` base interfaces. Their class files now carry annotations a consuming build reads ([#844](https://github.com/higher-kinded-j/higher-kinded-j/issues/844), [#847](https://github.com/higher-kinded-j/higher-kinded-j/issues/847), [#849](https://github.com/higher-kinded-j/higher-kinded-j/issues/849)). Both sides must agree on a bean's properties and a leaf order ([#868](https://github.com/higher-kinded-j/higher-kinded-j/issues/868), [#876](https://github.com/higher-kinded-j/higher-kinded-j/issues/876)).
- **A module that declares specs ships index classes** ([#676](https://github.com/higher-kinded-j/higher-kinded-j/issues/676)): they live in `org.higherkindedj.mapping.index`. Two such jars cannot load together as automatic modules, so a library bound for a module path passes `-Ahkj.mapping.index=false`. A module with its own `module-info` writes none.
- **A build that names its processors adds the companion processors** ([#877](https://github.com/higher-kinded-j/higher-kinded-j/issues/877)): with `-processor` or Maven's `<annotationProcessors>`, add `org.higherkindedj.optics.processing.CompanionAnnotationProcessor`. A build that generates `@HkjHttpClient` clients also adds `org.higherkindedj.spring.client.processor.CompanionAnnotationProcessor` beside `org.higherkindedj.spring.client.processor.HkjHttpClientProcessor`. The HKJ Maven plugin adds them at its own release. Put a processor claiming every annotation, such as Lombok's, ahead of `hkj-processor`.

### What a running program can notice {#runtime-changes}

#### Mapping {#runtime-mapping}

- **A leaf's answer is kept for the life of the Impl** ([#952](https://github.com/higher-kinded-j/higher-kinded-j/issues/952)): a leaf that picks its codec per call, from a flag or a system property, keeps its first pick. Make that choice inside the codec's parse, and build it from thread-safe parts, such as `DateTimeFormatter`, since every thread shares it.
- **`currency()` refuses a code with a lower-case letter** ([#952](https://github.com/higher-kinded-j/higher-kinded-j/issues/952)): such as `XPt`, which the JDK could misread as a currency unequal to `XPT`.
- **Containers cross a mapping as unmodifiable copies** ([#852](https://github.com/higher-kinded-j/higher-kinded-j/issues/852)): a list `build` or `parse` hands over throws `UnsupportedOperationException` when added to, so set a new one. A sorted `Set` or `Map` loses its comparator. An array is cloned, so a record with an array component and no `equals` of its own no longer equals its round trip.
- **A `null` inside any copied container is a located error** ([#875](https://github.com/higher-kinded-j/higher-kinded-j/issues/875)): one nested deeper, in a raw container, or inside an `ArrayList` or a `LinkedHashMap`, used to parse as valid. A `null` key in a newly scanned map throws `NullPointerException`.
- **A constructor's exception becomes an `Invalid`** ([#872](https://github.com/higher-kinded-j/higher-kinded-j/issues/872), [#893](https://github.com/higher-kinded-j/higher-kinded-j/issues/893)): from `parse`, `patch`, a fallible merge, `assemble()` and a sparse update's `apply`. At a Spring boundary a 500 becomes a 400 or 422, and the message reaches the client. Keep constructors to argument checks with client-ready messages.
- **An empty `Optional` writes `null` to a bean** ([#871](https://github.com/higher-kinded-j/higher-kinded-j/issues/871)): an outbound DTO that relied on a field initialiser to send a default no longer sends it, so carry the default in the domain. A setter that refuses `null`, such as one calling `List.copyOf`, now throws from `build`.
- **An explicit leaf now runs on a bridged component** ([#673](https://github.com/higher-kinded-j/higher-kinded-j/issues/673), [#860](https://github.com/higher-kinded-j/higher-kinded-j/issues/860)): a leaf on a bridged component whose types already match now runs, so a normalising leaf changes the output. A bridged container comes back as a new unmodifiable list.
- **An element-mapped spec's `of(...)` can change parameter order** ([#876](https://github.com/higher-kinded-j/higher-kinded-j/issues/876)): only where its abstract leaves are spread across several interfaces. Where the leaf types coincide, or the result is held in `var`, an old call still compiles and swaps the prisms, so check each call against the parameters the Impl documents.
- **A `Boolean isX()` getter is a property** ([#868](https://github.com/higher-kinded-j/higher-kinded-j/issues/868)): a property read only through one now maps, so `build` writes it and `updateFrom` applies it.
- **A `JsonNullable` PATCH property is read only when sent** ([#680](https://github.com/higher-kinded-j/higher-kinded-j/issues/680)): an omitted field, which binds `undefined()`, now leaves the component unchanged. A leaf over the whole holder used to receive it, and clear, overwrite or throw. A domain component of the holder's own type used to take the `undefined()` holder as its value.
- **Reflection finds the moved `ValidatedPrism` methods on their half** ([#703](https://github.com/higher-kinded-j/higher-kinded-j/issues/703)): `getDeclaredMethod` finds `parse` on `ValidatedParse` and `build` on `ValidatedBuild`.

#### Optics {#runtime-optics}

- **Generated code calls a record's canonical constructor** ([#874](https://github.com/higher-kinded-j/higher-kinded-j/issues/874)): on a record with a same-arity overload, or a class rebuilt through an overloaded constructor, wither or setter, the value built may differ. Check stored data and test expectations recorded through the overload.
- **A lens calls an overloaded wither's primitive form** ([#887](https://github.com/higher-kinded-j/higher-kinded-j/issues/887)): on a class imported by class literal, the lens now calls `withN(int)` rather than a boxed overload. Setting `null` through it throws.
- **`combineAll` on the update monoid rejects a `null` update** ([#982](https://github.com/higher-kinded-j/higher-kinded-j/issues/982)): with `elements must not contain null`.

#### Effect Paths {#runtime-effect-paths}

- **A null error is refused** ([#821](https://github.com/higher-kinded-j/higher-kinded-j/issues/821)): `Path.left`, `Path.either`, `ErrorContext.failure`, `ErrorContext.fromEither`, `GenericPath.raiseError`, `MaybePath.toEitherPath`, `AffinePath.toEitherPath`, `EitherPath.focus`, and `toErrorContext` on `OptionalContext` and `JavaOptionalContext`, throw `NullPointerException` for one, even when the path holds a value. A path never holds `Left(null)`, so a re-raise such as `recoverWith(e -> Path.left(e))` is safe. Pass a real error, such as `Unit.INSTANCE`.
- **An error that is itself a `Supplier` now supplies it** ([#794](https://github.com/higher-kinded-j/higher-kinded-j/issues/794)): `toEitherPath` calls it for its value, and a bare `toEitherPath(null)` picks that overload and throws `NullPointerException`.
- **A function that builds an error must not return null** ([#821](https://github.com/higher-kinded-j/higher-kinded-j/issues/821)): `mapError`, `bimap`, `TryPath.toEitherPath`, `catching`, `ErrorContext.io`, and the error factories of `withTimeout`, `withCircuitBreaker` and `withBulkhead`, refuse a null result. `Throwable::toString` is never null. Eager paths throw `NullPointerException` at the call, deferred ones when run, keeping any mapped exception as the cause. `AffinePath.toTryPath` now checks its supplier even on a match.
- **`bracketOutcome` releases even when `onDefect` fails** ([#821](https://github.com/higher-kinded-j/higher-kinded-j/issues/821)): if `onDefect` throws or returns null, `release` still runs, seeing `Left(null)`. The path then fails with that exception or a `NullPointerException`, which a failing `release` carries as suppressed. A throwing `onDefect` used to skip `release` and leak the resource, and a null one returned `Left(null)`.
- **A `@PathSource` Path's `peek` returns a new Path** ([#949](https://github.com/higher-kinded-j/higher-kinded-j/issues/949)): on a lazy witness its action runs each time the effect runs. On an eager one it runs once, and an exception it throws reaches the returned Path.
- **`sequenceValidated` groups its combines in balanced pairs** ([#982](https://github.com/higher-kinded-j/higher-kinded-j/issues/982)): any associative `Semigroup` that leaves its arguments alone gives the same result, and `traverseValidated` matches it.
- **A Path with a custom `suffix` prints its own class name** ([#945](https://github.com/higher-kinded-j/higher-kinded-j/issues/945)): from `toString`, where it printed the annotated type's name.

#### Testing {#runtime-testing}

- **A `MappingLaws` rejection sample must fail on a field** ([#872](https://github.com/higher-kinded-j/higher-kinded-j/issues/872), [#893](https://github.com/higher-kinded-j/higher-kinded-j/issues/893)): a value only the constructor refuses now fails unlabelled, so a patch, parse-only or sparse law needs a sample with a bad component.

#### Build and tooling {#runtime-build}

- **A deprecated type-use annotation is no longer copied** ([#895](https://github.com/higher-kinded-j/higher-kinded-j/pull/895)): a deprecated nullness annotation inside a `@NullMarked` scope now leaves the generated type non-null. Move to a current one, such as JSpecify's.

### What stops a build that compiled {#build-changes}

Most of these fail at your declaration, or warn there under `-Werror`, naming the fix. The rest fail where your code calls a signature that changed.

| Area | Now fails the build | Do this | Issue |
|---|---|---|---|
| Mapping | A spec or mix-in holding its Impl in a constant (a warning) | Bind the Impl in the caller, or suppress `impl-constant` | [#984](https://github.com/higher-kinded-j/higher-kinded-j/issues/984) |
| Mapping and optics | A type the spec's package, or a generated companion's, cannot see | Remove `private`, or make it `public` | [#918](https://github.com/higher-kinded-j/higher-kinded-j/issues/918) |
| Mapping | A bridged `Optional` onto a site declared non-null, a Lombok `@NonNull` field or a Kotlin setter included | Mark the site `@Nullable` | [#881](https://github.com/higher-kinded-j/higher-kinded-j/issues/881) |
| Mapping | An unpaired bean accessor named after a domain component, or a PATCH setter with no getter | Pair it, remove it, or name it in `@Unmapped` | [#868](https://github.com/higher-kinded-j/higher-kinded-j/issues/868) |
| Mapping | A `Boolean isX()`, now a getter, unmodelled beside `setX(Boolean)` or beside `setX(boolean)` | Add a domain component or derived field, or align the types | [#868](https://github.com/higher-kinded-j/higher-kinded-j/issues/868) |
| Mapping | A spec extending both `MappingSpec` and `UpdateSpec` | Split it into two specs sharing a mix-in | [#837](https://github.com/higher-kinded-j/higher-kinded-j/issues/837) |
| Mapping | A spec's own `@MapKey` leaf beside a whole-`Map` leaf for one component | Keep the one you mean | [#882](https://github.com/higher-kinded-j/higher-kinded-j/issues/882) |
| Mapping | An abstract `@MapField` method returning `ValidatedPrism`, now read as a leaf | Give it a body, or another return type | [#981](https://github.com/higher-kinded-j/higher-kinded-j/issues/981) |
| Mapping | A rename or derived field naming a `JsonNullable` companion, or a domain component named after one (no `parse`) | Remove it, or rename the component | [#936](https://github.com/higher-kinded-j/higher-kinded-j/issues/936) |
| Mapping | A getter-only `List` on a PATCH bean, or bridged from an `Optional` | Give it a setter, and a getter that answers `null` until set | [#830](https://github.com/higher-kinded-j/higher-kinded-j/issues/830), [#841](https://github.com/higher-kinded-j/higher-kinded-j/issues/841) |
| Mapping | A raw or wildcard getter-only `List` where `build` is emitted | Declare its element type, or add a setter | [#839](https://github.com/higher-kinded-j/higher-kinded-j/issues/839) |
| Mapping | A bean that mapped both ways over a getter-only `List` beside other getters | It now maps parse-only: remove calls to `build` | [#703](https://github.com/higher-kinded-j/higher-kinded-j/issues/703) |
| Mapping | `parseAll(null)`, or `prism::parseAll` where nothing fixes the container type | Cast the `null`, or use a lambda | [#675](https://github.com/higher-kinded-j/higher-kinded-j/issues/675) |
| Optics | `@ThroughField` on a concrete container field, or with a focus the element does not match | Declare the interface, or name a `traversal` | [#773](https://github.com/higher-kinded-j/higher-kinded-j/issues/773), [#779](https://github.com/higher-kinded-j/higher-kinded-j/issues/779) |
| Optics | An `@ImportOptics` spec that inherits its optics or reaches `OpticsSpec` through another interface, a class written as a spec, a spec that also lists classes, a primitive, array or `void` literal, or an empty list (a warning) | Follow the message | [#908](https://github.com/higher-kinded-j/higher-kinded-j/issues/908) |
| Optics | An `OpticsSpec` over a raw type | Complete its type arguments | [#771](https://github.com/higher-kinded-j/higher-kinded-j/issues/771) |
| Optics | A wither returning a raw type, or a spec `@Wither` or strategy `setter` binding a static method | Return the class from an instance method | [#878](https://github.com/higher-kinded-j/higher-kinded-j/issues/878), [#887](https://github.com/higher-kinded-j/higher-kinded-j/issues/887) |
| Optics | A navigator's return type, or a wildcard container such as `Map<String, ? extends Address>`, once a dependency's `@GenerateFocus` record is rebuilt | Call `.toPath()`, declare exact type arguments, or list the field in `excludeFields` | [#847](https://github.com/higher-kinded-j/higher-kinded-j/issues/847) |
| Optics | Code holding a `FocusPath` for `Kind<? extends ListKind.Witness, Role>`, which now widens | Take the `TraversalPath` or `AffinePath`, and drop any hand-applied step | [#787](https://github.com/higher-kinded-j/higher-kinded-j/issues/787) |
| Optics | Two `TraversableGenerator` providers at one priority for one type (a warning) | Rank one of them | [#774](https://github.com/higher-kinded-j/higher-kinded-j/issues/774) |
| Effect Paths | A `toEitherPath` error whose own type is a functional interface, written as a lambda | Name the error type: `path.<MyError>toEitherPath(...)` | [#794](https://github.com/higher-kinded-j/higher-kinded-j/issues/794) |
| Effect Paths | `@PathConfig`, `EFFECTFUL` or `ACCUMULATING` (a `[removal]` warning), or a generic `errorType` on `RECOVERABLE` | Run `MigrateDeprecationsTo0_5_0`; use a non-generic error type | [#890](https://github.com/higher-kinded-j/higher-kinded-j/issues/890), [#945](https://github.com/higher-kinded-j/higher-kinded-j/issues/945), [#949](https://github.com/higher-kinded-j/higher-kinded-j/issues/949) |
| Spring | An inherited `@OnStatus` naming a type the method cannot return or the classpath lacks, or drawing the duplicate-status or `MaybePath` warning; a module reading it without `hkj-spring-boot-client` (a warning) | Fix the override, and expose the client with Gradle's `api` | [#849](https://github.com/higher-kinded-j/higher-kinded-j/issues/849) |
| Build and tooling | `spring = true` with `version` set below 0.4.7, which has no `hkj-spring-boot-client-processor` to resolve | Use 0.4.7 or later, or set `spring = false` and add the starter by hand | [#1002](https://github.com/higher-kinded-j/higher-kinded-j/pull/1002) |

### Deprecated for removal in 0.5.0 {#deprecated}

The `MigrateDeprecationsTo0_5_0` recipe removes `@PathConfig` and replaces each capability. See [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).

| Deprecated | Replacement | Recipe |
|---|---|---|
| `@PathConfig` | Nothing, since it has no effect; to rename a Path, set `suffix` on `@PathSource` | `RemovePathConfig` |
| `@PathSource` capability `EFFECTFUL` | `CHAINABLE`, which generates the same | `ReplaceDeprecatedPathSourceCapabilitiesRecipe` |
| `@PathSource` capability `ACCUMULATING` | `RECOVERABLE`, which generates the same | `ReplaceDeprecatedPathSourceCapabilitiesRecipe` |

---

**Previous:** [Upgrading](upgrading.md)
**Next:** [v0.4.10](v0_4_10.md)
