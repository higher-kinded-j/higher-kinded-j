# The Optics Chapter

Read `docs/OPTICS-CHAPTER-GUIDE.md` first. It holds the chapter's rules: its three reading lanes,
how a group introduction and a reference page are shaped, and how the examples are laid out. This
file adds the order to work in and the traps the chapter has met.

## Moving or adding a page

1. **Change `SUMMARY.md` first.** The order there is the reading order, and nesting a page under a
   group's introduction is what puts it in that group.
2. **Update the lists**: the chapter intro's Chapter Contents, and the group introduction's "Pages in
   this group".
3. **Regenerate the footers** with `python3 .github/scripts/chapter_footers.py optics`. Never edit a
   Previous or Next link by hand.
4. **Retitled a page?** Search the book for its old link text, `home.md` included, since it lists the
   chapter's groups.
5. **Removed a heading?** Add its old `page.html#id` to `hkj-book/theme/legacy-anchors.js`, pointing
   at the section that answers it now. Take the new id from the anchor check's `slug()`, never by
   hand: mdbook drops punctuation rather than turning it into a hyphen.

## Traps the chapter has met

Where a trap names a page, the book states and proves it there. Check that page before repeating
the claim, since the library can change under it.

- **Navigators are off by default.** A chained hop such as `UserFocus.address().street()` compiles
  only with `@GenerateFocus(generateNavigators = true)` on every record a hop leaves.
  `maxNavigatorDepth` limits nothing above 1, so never document a deeper limit.
- **`andThen` has an overload for every pair** of `Iso`, `Lens`, `Prism`, `Affine` and `Traversal`,
  so no chain needs `asTraversal()` first. Composition Rules' table is generated from the overloads
  by `BookCompositionTableTest`, which also checks every `X.andThen(Y) = Z` claim on the page and in the
  `hkj-optics` skill, and refuses the Haskell `X >>> Y` notation there.
- **An affine's `set` on an absent focus** writes the value only when its last step can build it and
  every step before that is present; otherwise it returns the source unchanged.
  (`affine.md#when-the-focus-is-absent`)
- **`modifyAllEither` keeps the first error but validates every value.** A traversal builds every
  element's effect before the applicative combines them, so `Either` shapes the answer, not the work.
- **A map an optic writes keeps the source's iteration order.** `forMapValuesCollecting(TreeMap::new)`
  keeps a `TreeMap`'s order only for keys in natural order.
- **An output comment is a claim** when it opens with `[`, `Name(` or `Name[`, or is only a number, a
  boolean or `Nothing`, inside the anchor of an example with a `main`. Lead an explanation with words.
