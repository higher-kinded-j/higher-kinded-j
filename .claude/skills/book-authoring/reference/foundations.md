# The Foundations Chapter

Read `docs/FOUNDATIONS-CHAPTER-GUIDE.md` first. It holds the chapter's rules: its three reading
lanes, which level a page works at, and how the examples are laid out. This file adds the order to
work in and the traps the chapter has met.

## Moving or adding a page

1. **Change `SUMMARY.md` first.** The chapter is the block under `hkts/foundations_intro.md`, and its
   pages live in three directories: `hkts/`, `functional/` and `monads/`.
2. **Update the lists**: the chapter introduction's Chapter Contents, and the group introduction's
   list.
3. **Regenerate the footers** with `python3 .github/scripts/chapter_footers.py hkts`, in a commit of
   their own. Never edit a Previous or Next link by hand.
4. **Retitled a page?** Search the book for its old link text, `home.md` and `where_to_start.md`
   included, since both route readers into this chapter.

## Traps the chapter has met

- **An Effect Path does not run on type class instances.** `EitherPath` is a final class that holds
  an `Either` and calls it directly, and so do the other concrete Paths. Two exceptions to "a Path is
  not a `Kind`": `VTaskPath` extends `VTaskKind`, yet `VTASK.narrow` of a `VTaskPath` throws
  `KindUnwrapException`, because its implementation holds a `VTask` rather than being one; and
  `GenericPath`, `FreePath` and `FreeApPath` hold a `Kind` with its instance. `GenericPath.of(kind,
  monad)` is the bridge from the `Kind` level to a Path. Use `MaybePath`, `EitherPath` or `IOPath`
  when a page needs an example of a concrete Path.
- **On a Path, chain with `via`.** `flatMap` on `EitherPath`, `MaybePath`, `TryPath` and
  `ValidationPath` is `Chainable`'s default, which returns `Chainable<P, B>` and loses the Path type.
- **`toEitherPath` takes the error, and its type.** `MaybePath.toEitherPath(error)` or
  `toEitherPath(() -> error)`; there is no zero-argument form. Mid-chain there is no target type, so
  the chain's error type is the argument's: `toEitherPath(new OrderError.NotFound(id))` types it as
  `NotFound`. Pass a value typed as the sealed error, as `OrderError.notFound(id)` is, or name the
  type with `.<OrderError>toEitherPath(...)`, as the Quickstart does.
- **`via` checks the Path and the error type at compile time.** A step of another kind of Path, or
  one that fails with an error the chain does not carry, does not compile; a step may fail with a
  variant of the chain's error. A chain typed on one variant rejects a later step on another, so
  give a page's chain its sealed error type from its first step. `effect/compiler_errors.md` §5
  documents the messages.
- **Every `Instances` method returns a value chosen at run time**, from the witness token passed in;
  the compiler resolves nothing. `Instances.monadError(witness)` adds an unchecked cast: whether the
  instance is a `MonadError` is checked only when it runs, and its error type `E` is never checked,
  so `MonadError<MaybeKind.Witness, String> m = monadError(maybe())` compiles although `Maybe`'s
  error type is `Unit`.
- **Behaviour belongs to the instance, not the type class.** `Either`'s `ap` short-circuits,
  `Validated`'s accumulates, `CompletableFuture`'s combines two futures already running
  (`thenCombine`), and `VTask`'s runs one step after the other; concurrent `VTask` work is `Par`'s.
- **Read the `extends` line before drawing a hierarchy.** `MonadZero` extends both `Monad` and
  `Alternative`, `Traverse` extends both `Functor` and `Foldable`, and there is no `Apply`.
- **`Path` clashes with `java.nio.file.Path`** under a module import of `java.base`. Import the
  library's `Path` by name.
- **`import module org.higherkindedj.core;` needs the library resolved as a module**: required in
  the application's `module-info.java`, or added with `--add-modules`. On the classpath, where most
  applications put it, javac reports "imported module not found", and on the module path alone,
  "unnamed module does not read". Show single-type imports, as the Style Guide's Code Blocks rule
  says.
