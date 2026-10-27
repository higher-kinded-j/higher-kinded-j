# Unreleased: 0.6.0

_Not released yet: these changes are on `main`, and the `latest` book describes them._

To try them, depend on `0.6.0-SNAPSHOT` from the snapshots repository, as [Gradle SNAPSHOT Configuration](../tooling/manual_setup.md#gradle-snapshot-configuration) shows.

~~~admonish info title="At a glance"
- **`@ImportOptics` pairs a JavaBeans-spelt wither with its getter**: a `witheMail` beside `geteMail()` gives an `eMail()` lens.
- **A `@GenerateFocus` message that offers a navigator asks first for the renames it needs.** Following its fix line no longer leads into another note or error.
~~~

---

## Mapping {#mapping}

---

## Optics {#optics}

- **`@ImportOptics` pairs a JavaBeans-spelt wither with its getter** ([#1148](https://github.com/higher-kinded-j/higher-kinded-j/issues/1148)). A `witheMail(String)` beside `geteMail()`, as Lombok's `beanspec` capitalisation writes them, gives an `eMail()` lens, as `withEMail` beside `getEMail()` does. The processor says why it refuses a class whose only wither is `with2D(int)`. See [Wither Classes to Lenses](../optics/importing_optics.md#wither-classes-to-lenses).
- **A spec lens reads a getter inherited from two interfaces as javac does** ([#1136](https://github.com/higher-kinded-j/higher-kinded-j/issues/1136)). An abstract source that inherits `String name()` from one interface and `CharSequence name()` from another is now read as returning `String`, so a `Lens<Src, String>` over it builds. A `@ViaBuilder` chain reads an inherited `toBuilder()` or `build()` the same way. See [Compiler Messages](../optics/compiler_errors.md#-reads--not-the-lenss-focus-).
- **Messages that offer a navigator ask first for the renames it needs** ([#1272](https://github.com/higher-kinded-j/higher-kinded-j/issues/1272)). The note to widen `items` beside an `Items` with a navigator, and five other messages, now ask for one of the two to be renamed first. They also name a field such as `last` that the navigator could not reach. See [Two fields with one navigator name](../optics/focus_navigation.md#two-fields-with-one-navigator-name).

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

- **`StreamPath` docs no longer call it lazy where the source is read at once** ([#1290](https://github.com/higher-kinded-j/higher-kinded-j/issues/1290)). The cheat sheet and home page describe sequences you can run more than once, and `toStreamPath` says the non-null focuses are collected into a list when it is called.
- **The Effect Paths glossary gains three entries** ([#1301](https://github.com/higher-kinded-j/higher-kinded-j/issues/1301)): `FreeApPath`, `FreePath` and `GenericPath`. Each gives a definition, its factories on `Path`, and its main operations. See [Effect Paths](../glossary/effect-paths.md#freeappath).
- **The Effect Paths glossary gains four more entries** ([#1258](https://github.com/higher-kinded-j/higher-kinded-j/issues/1258)): `ReaderPath`, `TrampolinePath`, `WithStatePath` and `WriterPath`. Each gives a definition, its factories on `Path`, its main operations and a link to its chapter page. See [Effect Paths](../glossary/effect-paths.md#readerpath).
- **Generated Javadoc uses an article only before a type the processor chose** ([#1307](https://github.com/higher-kinded-j/higher-kinded-j/issues/1307)). A record, class, property or operation is named with none, as in `field of {@link Address}` and `Lifts the {@code Charge} operation`. A path or optic type keeps the article its name takes, as in `Creates an {@link AffinePath}`.

---

## Upgrading from 0.5.0 {#upgrading}

### Before you upgrade {#before-you-upgrade}

### What a running program can notice {#runtime-changes}

#### Optics {#runtime-optics}

- **Of two withers for one getter, `@ImportOptics` sets through the capitalised spelling** ([#1148](https://github.com/higher-kinded-j/higher-kinded-j/issues/1148)): the lens for a class with both `withEMail` and `witheMail` beside `eMail()` set through whichever the class declared first. It now sets through `withEMail`, and a note names `witheMail`, which a spec interface's `@Wither` can choose.

### What stops a build that compiled {#build-changes}

| Area | Now fails the build | Do this | Issue |
|---|---|---|---|

### Deprecated for removal in 0.7.0 {#deprecated}

| Deprecated | Replacement | Recipe |
|---|---|---|

---

**Previous:** [Upgrading](upgrading.md)
**Next:** [v0.5.0](v0_5_0.md)
