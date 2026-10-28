# Unreleased: 0.6.0

_Not released yet: these changes are on `main`, and the `latest` book describes them._

To try them, depend on `0.6.0-SNAPSHOT` from the snapshots repository, as [Gradle SNAPSHOT Configuration](../tooling/manual_setup.md#gradle-snapshot-configuration) shows.

~~~admonish info title="At a glance"
- **`@ImportOptics` pairs a JavaBeans-spelt wither with its getter**: a `witheMail` beside `geteMail()` gives an `eMail()` lens.
- **A `@GenerateFocus` message that offers a navigator asks first for the renames it needs.** Following its fix line no longer leads into another note or error.
- **A `@GenerateMapping` bean refusal's fix line compiles once followed**, and names the near miss it passed over, such as `setx(int)` beside `Integer getX()`.
~~~

---

## Mapping {#mapping}

- **A bean property's fix line compiles once followed** ([#1149](https://github.com/higher-kinded-j/higher-kinded-j/issues/1149)). The setter offered for `eMails` is `seteMails`, not `setEMails`. A rename to a keyword such as `default` is offered no marker, and a marker replaces the spec's own rename. A near miss such as `setx(int)` beside `Integer getX()` is named. See [When an unpaired accessor is refused](../mapping/rules.md#unpaired-accessors).

---

## Optics {#optics}

- **`@ImportOptics` pairs a JavaBeans-spelt wither with its getter** ([#1148](https://github.com/higher-kinded-j/higher-kinded-j/issues/1148)). A `witheMail(String)` beside `geteMail()`, as Lombok's `beanspec` capitalisation writes them, gives an `eMail()` lens, as `withEMail` beside `getEMail()` does. The processor says why it refuses a class whose only wither is `with2D(int)`. See [Wither Classes to Lenses](../optics/importing_optics.md#wither-classes-to-lenses).
- **A spec lens reads a getter inherited from two interfaces as javac does** ([#1136](https://github.com/higher-kinded-j/higher-kinded-j/issues/1136)). An abstract source that inherits `String name()` from one interface and `CharSequence name()` from another is now read as returning `String`, so a `Lens<Src, String>` over it builds. A `@ViaBuilder` chain reads an inherited `toBuilder()` or `build()` the same way. See [Compiler Messages](../optics/compiler_errors.md#-reads--not-the-lenss-focus-).
- **Messages that offer a navigator ask first for the renames it needs** ([#1272](https://github.com/higher-kinded-j/higher-kinded-j/issues/1272)). The note to widen `items` beside an `Items` with a navigator, and five other messages, now ask for one of the two to be renamed first. They also name a field such as `last` that the navigator could not reach. See [Two fields with one navigator name](../optics/focus_navigation.md#two-fields-with-one-navigator-name).
- **`OpticOpFunctor.map` applies its function** ([#1154](https://github.com/higher-kinded-j/higher-kinded-j/issues/1154)). It returned the instruction unchanged, so code that mapped a lifted `OpticOp` failed later with `ClassCastException`. Each `OpticOp` record now carries a continuation `k`, as an `@EffectAlgebra` operation does, and an interpreter hands each result to `op.k()` in place of wrapping it in `Free.pure`. See [What an interpreter actually is](../optics/interpreters.md#what-an-interpreter-actually-is).

---

## Effect Paths {#effect-paths}

- **`TrampolinePath` and `FreePath` combine three paths with `zipWith3`** ([#1302](https://github.com/higher-kinded-j/higher-kinded-j/issues/1302)): both already combined two paths with `zipWith`. The three-way combine stays lazy until `run()` or `foldMap`, so a deep chain stays stack-safe. See [Combinable (Applicative)](../effect/capabilities.md#combinable-applicative).

---

## Spring {#spring}

---

## Testing {#testing}

---

## Build and tooling {#build-and-tooling}

---

## Documentation {#documentation}

- **StreamPath docs explain when source elements are read** ([#1278](https://github.com/higher-kinded-j/higher-kinded-j/issues/1278)): its Javadoc and streaming chapters explain when source elements are read. See [StreamPath factory timing](../monads/vstream.md#choosing-the-right-streaming-tool).
- **MaybePath now has a glossary entry** ([#1264](https://github.com/higher-kinded-j/higher-kinded-j/issues/1264)): it explains absence, the main factories, and where to read more. See [MaybePath](../glossary/effect-paths.md#maybepath).
- **EitherPath now has a glossary entry** ([#1237](https://github.com/higher-kinded-j/higher-kinded-j/issues/1237)): it explains typed failures, the main factories, and where to read more. See [EitherPath](../glossary/effect-paths.md#eitherpath).
- **Reader, State and Writer now have glossary entries** ([#1235](https://github.com/higher-kinded-j/higher-kinded-j/issues/1235)): each names its core operations and Effect Path equivalent. See [Data & Core Effect Types](../glossary/data-effects.md#reader).
- **The Optics chapter reads in order, from a first nested update to a PATCH** ([#1034](https://github.com/higher-kinded-j/higher-kinded-j/pull/1034)). The sidebar starts with the pages a newcomer needs, then groups the rest by task, reference pages last. The introduction sets a Lombok `@With` cascade beside the Focus path that replaces it. Every page keeps its address. See [Optics](../optics/ch_intro.md).
- **The Focus DSL page answers "what does my field give me?"** ([#1035](https://github.com/higher-kinded-j/higher-kinded-j/pull/1035)). A table keyed by the field you hold gives the generated path and what you write next, each row compiled. The Quickstart shows the lens class the processor writes, and Collections, Optionals and Sealed Types ends on its unusual cases. See [Find your field](../optics/focus_dsl.md#find-your-field).
- **Check an update as you make it, and see the optic inside a path** ([#1036](https://github.com/higher-kinded-j/higher-kinded-j/pull/1036)). Three rewritten pages end the Optics chapter's first lane: What a Path Is Made Of, Updates That Can Fail, which reports every bad value in one call, and Many Edits at Once. Old links still land. See [Updates That Can Fail](../optics/fluent_api.md).
- **The Optics chapter writes compositions as Java code** ([#1037](https://github.com/higher-kinded-j/higher-kinded-j/pull/1037)). The chapter and the `hkj-optics` skill spell a composition `Lens.andThen(Prism) = Affine` instead of the Haskell `>>>`. The glossary gains `andThen`, `modifyF`, navigator, Focus path type and path widening. See [Composition Rules](../optics/composition_rules.md).
- **The Optics chapter's first lane follows one order service** ([#1038](https://github.com/higher-kinded-j/higher-kinded-j/pull/1038)). From the introduction to Many Edits at Once, every example edits the same records, those of the Mapping chapter's capstone: an order of priced lines, placed by a customer. See [Optics](../optics/ch_intro.md).
- **The Optics chapter's first pages end in a capstone and a self-check** ([#1039](https://github.com/higher-kinded-j/higher-kinded-j/pull/1039)). Six pages each pause for two checkpoints after their "You can ship now" tip, and the build proves every answer. A capstone builds an order desk from those pages, and twelve questions turn a score into a next step. See [Check Your Understanding](../optics/self_check.md).
- **Two hard rules in the Optics chapter now come with a story** ([#1040](https://github.com/higher-kinded-j/higher-kinded-j/pull/1040)). Composition Rules opens on a short dialogue about why a lens and a prism make an affine. The Lenses page tells how a constructor that lowercases an email quietly breaks a lens. The old capstone page is now Deep Validation with `modifyF`. See [Optic Composition Rules](../optics/composition_rules.md).
- **Two optics tutorials now ask for the type their hints build** ([#1040](https://github.com/higher-kinded-j/higher-kinded-j/pull/1040)). In Tutorials 6 and 10, a lens and a prism compose to an `Affine`, which the exercises now declare in place of a `Traversal`. The Optics Journeys page shows which pages each tutorial practises. See [Optics Journeys](../tutorials/optics/ch_intro.md).
- **Practise the Optics chapter from its Quickstart to its Capstone** ([#1041](https://github.com/higher-kinded-j/higher-kinded-j/pull/1041)). Tutorial 00 names, reads and writes an order's fields through its generated paths in three exercises, and the optics Capstone builds the Capstone page's order desk in five. See [Optics Journeys](../tutorials/optics/ch_intro.md).
- **The Optics chapter's deeper pages say what you will be able to do** ([#1042](https://github.com/higher-kinded-j/higher-kinded-j/pull/1042)). Each opens with a one-line promise and at most five outcomes, and sets its optic beside the Java idiom it stands in for, in place of a list of metaphors. The Optics Extensions page no longer says the extensions catch exceptions. See [Choosing an optic](../optics/optics_intro.md#choosing-an-optic).
- **The Optics chapter's cost facts live in one place** ([#1043](https://github.com/higher-kinded-j/higher-kinded-j/pull/1043)). Production Readiness now says what each optic allocates and reads, naming the method behind each fact, in place of the pages' own performance notes. Eight programs the pages copied now come from compiled examples, with their shown output checked against what they print. See [Runtime cost](../optics/production_readiness.md#runtime-cost).
- **Production Readiness opens on the questions it answers** ([#1050](https://github.com/higher-kinded-j/higher-kinded-j/pull/1050)). Ten questions a senior engineer asks before adopting optics, from what a lens update allocates to whether generated classes stay stable across versions, each link to the section that answers it. See [Production Readiness](../optics/production_readiness.md).
- **The Optics chapter draws each optic in one shape** ([#1044](https://github.com/higher-kinded-j/higher-kinded-j/pull/1044)). A legend on the introduction gives every optic type its own shape, and every diagram uses it, fits a phone's screen and describes itself to a screen reader. Four optic pages show what a write rebuilds and reuses. See [How the optic types relate](../optics/ch_intro.md#how-the-optic-types-relate).
- **The Optics Cookbook opens on a "Find your recipe" table** ([#1045](https://github.com/higher-kinded-j/higher-kinded-j/pull/1045)). Each recipe leads with a Focus path where one exists, with the raw optics collapsed, and runs on the chapter's order service. A test checks every result the page states, and old recipe links still land. See [Find your recipe](../optics/cookbook.md#find-your-recipe).
- **Two lookup pages translate what you already write into optics** ([#1046](https://github.com/higher-kinded-j/higher-kinded-j/pull/1046)). Coming from Lombok, Streams and Switch sets each wither, stream and `switch` habit beside its path, and says when plain Java wins. Coming from Monocle or Haskell lens maps their names, and the six behaviours that differ. See [Look It Up](../optics/ch7_intro.md).
- **More of the Optics chapter's code is compiled and checked** ([#1047](https://github.com/higher-kinded-j/higher-kinded-j/pull/1047)). On the optic-type pages and the first group introductions, Java a reader would copy is now compiled, and every value the pages state is held by a test. Money is a `BigDecimal` throughout, and several snippets that no longer matched the library are fixed. See [Affines](../optics/affine.md).
- **The rest of the Optics chapter's code is compiled and checked** ([#1048](https://github.com/higher-kinded-j/higher-kinded-j/pull/1048)). The collection, extension, iso, interpreter and batching pages now include tested code for every value they state, and the Iso page checks its round trips with `IsoLaws`. The Focus pages now say that only a `maxNavigatorDepth` of 1 or less changes the generated navigators. See [Isomorphisms](../optics/iso.md).
- **The Optics chapter's examples come from the build** ([#1020](https://github.com/higher-kinded-j/higher-kinded-j/pull/1020)): composed optics declare the precise type `andThen` returns, and each runnable program and its output come from the examples module. The Affines page says what `set` does on an absent focus. See [When the focus is absent](../optics/affine.md#when-the-focus-is-absent).
- **The Focus pages say what a wildcard inside a container's element gives you** ([#813](https://github.com/higher-kinded-j/higher-kinded-j/issues/813)). A `Crate`'s `List<Box<? extends Widget>>` widens to `TraversalPath<Crate, Box<? extends Widget>>`, wildcard kept, whatever `widenCollections` and `generateNavigators` say. `BoxFocus.value()` needs a named type argument rather than a wildcard, so the page shows the lens to write instead. See [A wildcard inside the element](../optics/focus_containers.md#a-wildcard-inside-the-element).
- **`@PathSource` has a page of its own** ([#999](https://github.com/higher-kinded-j/higher-kinded-j/issues/999)): [Custom Paths with `@PathSource`](../effect/path_source.md) says when a generated Path is worth it over `GenericPath`, what each capability generates, and how `errorType` types recovery.
- **The Resource page gives the order finalisers run in** ([#1019](https://github.com/higher-kinded-j/higher-kinded-j/issues/1019)): the order they were added, where it said the reverse. It also says which exception is reported when a release and a finaliser both throw. See [Finaliser Behaviour](../monads/vtask_resource.md#finalizer-behaviour).
- **`modifyAllEither` says it keeps the first error, not that it stops there** ([#1031](https://github.com/higher-kinded-j/higher-kinded-j/pull/1031)): every focused value is still validated, and the result keeps the first error in traversal order. The javadoc of `allThroughEither` and `TraversalExtensions.modifyAllEither`, and the examples, now say so too. See [`modifyAllEither`: First Error Only](../optics/optics_extensions.md#modifyalleither-first-error-only).
- **`maxNavigatorDepth` says what it does** ([#1030](https://github.com/higher-kinded-j/higher-kinded-j/issues/1030)). Its javadoc and Tutorial 19 now say what the Focus Navigation page says. A value of `1` makes a navigator's own navigation methods return plain paths, and a larger value, the default included, ends no chain. See [Controlling navigator generation](../optics/focus_navigation.md#controlling-navigator-generation).
- **The Optics chapter follows one order service throughout** ([#1056](https://github.com/higher-kinded-j/higher-kinded-j/pull/1056)). Outside the compiler-error reproducers, `Order`, `Customer`, `LineItem`, `Address`, `OrderStatus`, `Payment` and `CustomerProfile` keep the same components on every page, and none is copied under another name. A page that needs more adds a supporting type named for its role. See [Optics](../optics/ch_intro.md).
- **Javadoc examples name monoids that exist** ([#1056](https://github.com/higher-kinded-j/higher-kinded-j/pull/1056)). `Fold`, `IndexedFold`, `TraversalPath`, `ConstApplicative` and the Focus package overview show `Monoids.integerAddition()` and its siblings, where they named `Monoids.intSum()` and `Monoid.of`.
- **The programs the Optics chapter links follow its order service too** ([#1086](https://github.com/higher-kinded-j/higher-kinded-j/pull/1086)). Filtered Optics now filters an order's lines, orders and customers. The example programs a page links to give the cast's names the cast's shapes, and `Product` has one shape throughout. The compiler-error examples on both chapters' message pages keep those shapes too. See [Filtered Optics](../optics/filtered_optics.md).
- **The Effect chapter's Previous and Next links follow its contents** ([#1086](https://github.com/higher-kinded-j/higher-kinded-j/pull/1086)). Fifteen pages had stale links, and one gap skipped VResultPath. The book check now holds the chapter to its contents.
- **Every chapter's Previous and Next links follow its contents** ([#1093](https://github.com/higher-kinded-j/higher-kinded-j/pull/1093)). Eighty pages across seven more chapters had stale or missing links, several skipping a page added since. The book check now finds every chapter itself, so a new one is held to its contents too.
- **Every type name in the Optics and Mapping chapters keeps one shape** ([#1101](https://github.com/higher-kinded-j/higher-kinded-j/pull/1101), [#1114](https://github.com/higher-kinded-j/higher-kinded-j/pull/1114)). Names such as `Config`, `Employee` and `User` meant different things on different pages. One declaration now keeps each name, and the others are named for their role. The Optics glossary's Prism example uses the chapter's `Payment`, and the Mapping intro's mapper builds the capstone's `Order`.
- **The `hkj-optics` and `hkj-mapping` skills use the book's types** ([#1119](https://github.com/higher-kinded-j/higher-kinded-j/pull/1119)). Their examples declared records such as `Employee`, `Order` and `User` in shapes of their own. Each now uses the book's type where one plays the same role, and names the rest for their role, so a skill and the book mean the same thing by a name.
- **The annotations' javadoc examples use the book's types, and three are corrected** ([#1120](https://github.com/higher-kinded-j/higher-kinded-j/pull/1120)). Examples such as `@GenerateMapping`'s and `@GenerateFocus`'s declared `User` and `Person` in shapes of their own. `@GenerateFolds` now says a `List` component folds over its elements, `@ViaBuilder`'s spec reads one source type, and `@GenerateSetters` names its parameters as generated.
- **The Optics glossary's Prism entry names the methods a `Prism` has** ([#1155](https://github.com/higher-kinded-j/higher-kinded-j/issues/1155)): `getOptional` and `build`, where it named `preview` and `review`. The check that compiles the book's examples passed the entry's example, which called `preview`, because it skipped any error mentioning that word, a rule meant for Java's preview features. It now counts every error. See [Prism](../glossary/optics.md#prism).
- **The cheat sheet names the methods that extract a `LazyPath`, a `GenericPath` and a `WithStatePath`** ([#1221](https://github.com/higher-kinded-j/higher-kinded-j/issues/1221)). `LazyPath` has `get()` and `GenericPath` has `runKind()`, where the table named a `run()` neither type has. `WithStatePath`'s `run(initial)` gives a `StateTuple<S, A>`. See [Path Types](../cheatsheet.md#path-types).
- **The Optics glossary's Fold and Traversal entries name methods those types have** ([#1225](https://github.com/higher-kinded-j/higher-kinded-j/issues/1225)): `getAll` and `preview` for a `Fold`, where it named `toList` and `headOption`, and `asFold().getAll(source)` for reading a `Traversal`, which has no `toList`. The Fold entry's Getter link now goes to the Getters page. See [Fold](../glossary/optics.md#fold) and [Traversal](../glossary/optics.md#traversal).
- **The Optics glossary has entries for `Getter`, `Ixed` and `Each`** ([#1226](https://github.com/higher-kinded-j/higher-kinded-j/issues/1226)). Each entry defines the optic, lists its core operations and links to its chapter page, and all three join the Optics list in the glossary index. See [Getter](../glossary/optics.md#getter), [Ixed](../glossary/optics.md#ixed) and [Each](../glossary/optics.md#each).
- **The Type Classes glossary gains four entries** ([#1227](https://github.com/higher-kinded-j/higher-kinded-j/issues/1227)). `Alternative`, `Foldable`, `MonadZero` and `Traverse` now each have an entry, naming the operations the interface holds and linking to its chapter page. See [Type Classes](../glossary/type-classes.md).
- **The Effect Paths glossary has entries for `CompletableFuturePath`, `EitherOrBothPath`, `IOPath` and `VTaskPath`** ([#1245](https://github.com/higher-kinded-j/higher-kinded-j/issues/1245)): each gives the Path's definition, its factories on `Path` and its main operations, and links to its chapter page. See [CompletableFuturePath](../glossary/effect-paths.md#completablefuturepath).
- **`StreamPath` docs no longer call it lazy where the source is read at once** ([#1290](https://github.com/higher-kinded-j/higher-kinded-j/issues/1290)). The cheat sheet and home page describe sequences you can run more than once, and `toStreamPath` says the non-null focuses are collected into a list when it is called.
- **The Effect Paths glossary gains three entries** ([#1301](https://github.com/higher-kinded-j/higher-kinded-j/issues/1301)): `FreeApPath`, `FreePath` and `GenericPath`. Each gives a definition, its factories on `Path`, and its main operations. See [Effect Paths](../glossary/effect-paths.md#freeappath).
- **The Effect Paths glossary gains four more entries** ([#1258](https://github.com/higher-kinded-j/higher-kinded-j/issues/1258)): `ReaderPath`, `TrampolinePath`, `WithStatePath` and `WriterPath`. Each gives a definition, its factories on `Path`, its main operations and a link to its chapter page. See [Effect Paths](../glossary/effect-paths.md#readerpath).
- **Five examples keep one kind of Path and one error type through their chains.** Composition Patterns ran an `IOPath` step in an `EitherPath` chain, which throws when it runs; it now builds an `IOPath` chain. The Quickstart, a migration recipe and the `hkj-guide` and `hkj-spring` skills now name the chain's error type with a witness. See [Composition Patterns](../effect/composition.md#mistake-2-side-effects-in-map).
- **Generated Javadoc uses an article only before a type the processor chose** ([#1307](https://github.com/higher-kinded-j/higher-kinded-j/issues/1307)). A record, class, property or operation is named with none, as in `field of {@link Address}` and `Lifts the {@code Charge} operation`. A path or optic type keeps the article its name takes, as in `Creates an {@link AffinePath}`.

---

## Upgrading from 0.5.0 {#upgrading}

### Before you upgrade {#before-you-upgrade}

### What a running program can notice {#runtime-changes}

#### Optics {#runtime-optics}

- **Of two withers for one getter, `@ImportOptics` sets through the capitalised spelling** ([#1148](https://github.com/higher-kinded-j/higher-kinded-j/issues/1148)): the lens for a class with both `withEMail` and `witheMail` beside `eMail()` set through whichever the class declared first. It now sets through `withEMail`, and a note names `witheMail`, which a spec interface's `@Wither` can choose.
- **An optic interpreter that wraps each result in `Free.pure` skips the operation's continuation** ([#1154](https://github.com/higher-kinded-j/higher-kinded-j/issues/1154)): a program from `OpticPrograms` runs as before, but a function mapped onto an `OpticOp`, through `OpticOpFunctor` or `Free.translate`, is not applied. Hand the result to `op.k()` and return what it gives.

### What stops a build that compiled {#build-changes}

| Area | Now fails the build | Do this | Issue |
|---|---|---|---|
| Optics | An optic interpreter or other code of your own that names an `OpticOp` record: a pattern or type such as `OpticOp.Get<?, ?>` or `OpticOp.Set<S, A>`, or a constructor call such as `new OpticOp.Get<>(source, getter)` | Add the third type argument, as in `OpticOp.Get<?, ?, ?>`, and pass a continuation, `Function.identity()`, to a constructor. Hand each result to `op.k()` and return what it gives, in place of `Free.pure(result)` | [#1154](https://github.com/higher-kinded-j/higher-kinded-j/issues/1154) |

### Deprecated for removal in 0.7.0 {#deprecated}

| Deprecated | Replacement | Recipe |
|---|---|---|

---

**Previous:** [Upgrading](upgrading.md)
**Next:** [v0.5.0](v0_5_0.md)
