# Coming from Monocle or Haskell lens

_Your optics vocabulary in this library's names, and the six places where it behaves differently._

The optic types and most operation names carry over from Haskell's `lens` and Scala's Monocle. Six behaviours do not, two of them traps, and [Where the semantics differ](#where-the-semantics-differ) gives each one a section of its own.

---

## Operations {#operations}

| In `lens` | In Monocle | Here |
|---|---|---|
| `view l s`, `s ^. l` | `lens.get(s)` | `lens.get(s)`, or `path.get(s)` |
| `over l f s`, `s & l %~ f` | `lens.modify(f)(s)` | `lens.modify(f, s)`; a traversal has no method of its own, so `Traversals.modify(t, f, s)`, or a traversal path's `modifyAll(f, s)` |
| `set l b s`, `s & l .~ b` | `lens.replace(b)(s)`, which Monocle 2 called `set` | `lens.set(b, s)`, `path.set(b, s)`, or a traversal path's `setAll(b, s)` |
| `preview l s`, `s ^? l` | `optional.getOption(s)` | `getOptional(s)` on a prism or an affine, `preview(s)` on a fold or a traversal path |
| `review p b`, `p # b` | `prism.reverseGet(b)` | `prism.build(b)`, and `iso.reverseGet(b)` |
| `toListOf l s`, `s ^.. l` | `traversal.getAll(s)` | `fold.getAll(s)`, a traversal path's `getAll(s)`, or `Traversals.getAll(t, s)` |
| `foldMapOf l f s` | `fold.foldMap(f)(s)` | `fold.foldMap(monoid, f, s)`, with the `Monoid` passed as a value |
| `traverseOf l f s` | `lens.modifyF(f)(s)` | `optic.modifyF(f, s, applicative)`, with the `Applicative` passed as a value |
| `l1 . l2` | `l1.andThen(l2)` | `l1.andThen(l2)`, or `.via(l2)` on a path |

Each call in the third column takes the source after the function or value, as `set l b s` does. The optic is the receiver, or the first argument of a `Traversals` method, and `modifyF` takes its `Applicative` after the source. `OpticOps` restates them source first, as [Updates That Can Fail](fluent_api.md#part-1-reading-writing-querying) shows.

---

## Types {#types}

| In `lens` | In Monocle | Here |
|---|---|---|
| `Lens s t a b`, `Lens' s a` | `PLens[S, T, A, B]`, `Lens[S, A]` | `Lens<S, A>`, which [keeps its types](#every-optic-keeps-its-types) |
| `Prism'` | `Prism` | `Prism` |
| a `Traversal'` that reaches at most one value; there is no type of its own | `Optional` | `Affine` |
| `Traversal'` | `Traversal` | `Traversal` |
| `Fold` | `Fold` | `Fold` |
| `Getter`, and `to f` | `Getter` | `Getter`, and `Getter.to(f)` |
| `Setter'`, and `sets f` | `Setter` | `Setter`, and `Setter.of(f)` for `sets f` |
| `Iso'`, `iso f g` and `from` | `Iso`, and `reverse` | `Iso.of(get, reverseGet)`, and `reverse()` |
| `each`, from the `Each` class | `each`, from `Each` | `each()` on an `Each` instance such as `EachInstances.listEach()`, or `.each()` on a path |
| `at k`, from `At`: a lens onto a `Maybe` | `at(i)`, from `At` | `at(i)` on an `At` instance such as `AtInstances.mapAt()`: a lens onto an `Optional` |
| `ix i`, from `Ixed`: a traversal | `index(i)`, from `Index`: an `Optional` | `ix(i)` on an `Ixed` instance such as `IxedInstances.listIx()`: a traversal; on a `FocusPath`, `.at(i)` or `.atKey(k)` gives an `AffinePath` |
| `makeLenses`, `makePrisms` | `GenLens`, `GenPrism`, `Focus`, and `@Lenses` in Scala 2 | `@GenerateLenses`, `@GeneratePrisms`, `@GenerateFocus` |

Monocle's `Optional` is this library's `Affine`, a name chosen so it never reads as `java.util.Optional`.

---

## Combinators from `lens` {#combinators-from-lens}

| In `lens` | Here |
|---|---|
| `_Just` | `Prisms.some()`, a prism onto an `Optional`'s value, or `Prisms.just()` for a `Maybe` |
| `_Left`, `_Right` | `Prisms.left()`, `Prisms.right()`, on an `Either` |
| `only a`, `nearly a p` | `Prisms.only(a)`, `Prisms.nearly(a, p)` |
| `filtered p` | `Traversals.filtered(p)`, `traversal.filtered(p)`, or `.filter(p)` on a path |
| `partsOf t` | `Traversals.partsOf(t)` |
| `taking n`, `dropping n` | `ListTraversals.taking(n)`, `ListTraversals.dropping(n)`, on a list only; `taking n t` in `lens` limits any traversal |
| `itraversed`, `iover`, `itoListOf` | `IndexedTraversals.forList()`, `IndexedTraversals.imodify(t, f, s)`, `IndexedTraversals.toIndexedList(t, s)` |

[Filtered Optics](filtered_optics.md), [Limiting Traversals](limiting_traversals.md) and [Indexed Optics](indexed_optics.md) cover each family.

---

## Where the semantics differ {#where-the-semantics-differ}

| Difference | In `lens` and Monocle | Here |
|---|---|---|
| [`set` on an absent focus](#set-on-an-absent-focus) | changes nothing | an affine whose last step can build may write the value |
| [`set` on a prism](#a-prism-has-no-set) | writes a match | `Prism` declares no `set`; `modify` writes a match |
| [`headOption`](#headoption-writes-every-element) | a read of the first focus; `Cons`'s `headOption` writes only the head | an `AffinePath` that reads the first element and writes every one |
| [Changing types](#every-optic-keeps-its-types) | `Lens s t a b`, `PLens` | every optic keeps the types of its whole and its focus |
| [Encoding and composition](#encoding-and-composition) | `.` in `lens`, `andThen` in Monocle 3 | `modifyF` over an `Applicative` value, and an `andThen` overload per pair |
| [Where optics come from](#optics-are-generated-values) | `makeLenses`, `GenLens` | an annotation processor writes classes whose methods build an optic |

The first and the third are the traps: each compiles, and writes where a `lens` reader expects no write.

### `set` on an absent focus {#set-on-an-absent-focus}

In `lens`, `set` through an optic that finds no focus returns the structure unchanged, and Monocle 3's `replace` does the same. Here an affine's `set` can write to an absent focus, so a lens followed by a prism replaces whatever variant is there:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromMonocleBook.java:affine_set}}
```

`pending` is a consignment whose state is `Pending`. `written` comes back with a `Returned("damaged")` state, and `untouched` is `pending` itself. [When the focus is absent](affine.md#when-the-focus-is-absent) says which affines write. `hkj-test`'s `AffineLaws.assertAffineLaws` accepts either behaviour, and `AffineLaws.assertSetNoOpWhenAbsent` holds an affine to the rule of `lens`.

### A prism has no `set` {#a-prism-has-no-set}

In `lens` a prism is also a traversal, so `set` writes a match, and Monocle's `replace` does the same. Here `Prism` declares no `set`. `modify` writes a match and passes anything else through, `setWhen(p, value, source)` writes a match that passes `p`, and `build` makes the whole from the part:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromMonocleBook.java:prism_ops}}
```

`seen` is empty, `replaced` is `Returned("lost")`, and `passedOver` is `pendingState` itself. A prism composed after a lens is an affine, which does declare `set`, with the behaviour of [an absent focus](#set-on-an-absent-focus).

### `headOption()` writes every element {#headoption-writes-every-element}

In Monocle, `headOption` on a fold or a traversal reads the first focus, as `firstOf` does in `lens`, and the `Cons` instance's `headOption` optional writes only the head. Here `headOption()` on a `TraversalPath` gives an `AffinePath` that reads the first element and writes every one:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromMonocleBook.java:head_option}}
```

For Ada's order of one lamp and four bulbs, `read` holds 1, `everyLine` has quantities 7 and 7, and `firstLine` 7 and 4. Its `modify` applies the function to the first value and writes the result to every element, so incrementing gives both lines 2, the lamp's 1 plus one. `headOption()` suits a traversal that reaches at most one value, as [Kind Field Support](kind_field_support.md#headoption-narrowing-a-traversal) explains.

### Every optic keeps its types {#every-optic-keeps-its-types}

`Optic<S, T, A, B>` has the four type parameters of a type-changing optic, but every optic type fixes them: `Lens<S, A>` extends `Optic<S, S, A, A>`, and so does every other optic type, `Getter` by way of `Fold`. Outside the plain `Optic` that `dimap` returns, a write never changes the type of the whole or of the focus, so a change of type is an ordinary function.

### Encoding and composition {#encoding-and-composition}

`Optic`, the supertype every optic shares, has one abstract method, `modifyF(f, source, applicative)`. That is the van Laarhoven shape of `traverseOf`, over an `Applicative` passed as a value rather than found by type-class resolution. Each optic type adds operations of its own, such as a lens's `get` and `set`, and composition is not function composition. `Lens`, `Prism`, `Affine`, `Iso` and `Traversal` each overload `andThen` for all five, and the overload javac picks sets the result. A lens followed by a prism shows the difference:

| A lens, then a prism | Result |
|---|---|
| In `lens`, `l . p` | a `Traversal'` |
| In Monocle 3, `l.andThen(p)` | an `Optional` |
| Here, `lens.andThen(prism)` | an `Affine` |

[Composition Rules](composition_rules.md#composition-rules-table) gives the result for every pair, and a path's `.via(...)` takes a lens, prism, affine, iso or traversal. Monocle's older `composeLens`, and its symbol `^|->`, are `andThen` here too. The `contramap`, `map` and `dimap` that [Profunctor Optics](profunctor_optics.md) describes adapt an optic's outer types around `modifyF`; optics here are not encoded as functions over profunctors.

### Optics are values the processor writes {#optics-are-generated-values}

As `makeLenses` and Monocle's `GenLens` do, an annotation processor writes the optics at compile time: `@GenerateLenses` on a record gives a class such as `OrderLenses`, with a method per component. Each call builds a new optic, so keep one you reuse in a `static final` field, as [Caching optics](production_readiness.md#caching-optics) explains. A [Focus path](focus_dsl.md) plays the part of Monocle's `Focus`, with each hop a generated method the compiler checks.

---

## Migrating a Monocle habit {#migrating-a-monocle-habit}

1. **Start from a Focus path, not a raw optic.** `OrderFocus.customer().email()` is the `Focus` you know. Its `toPath()` hands over the path, whose `.via(...)` takes a lens, prism, affine, iso or traversal.
2. **Write `modify` where you mean the `set` of `lens`.** An affine's `set` may write to an absent focus, and `modify` never does.
3. **Pass the effect's instance yourself.** `modifyF` takes the `Applicative` as an argument; for validation, `OpticOps.modifyAllValidated` needs none.
4. **Index the list, not the traversal.** `.at(0)` on a path that focuses the list, such as `FocusPath.of(OrderLenses.lines())`, writes one element. On a `TraversalPath` of lines it indexes into each line instead, and fails when run.
5. **Check hand-written optics with `hkj-test`.** `LensLaws`, `PrismLaws`, `AffineLaws`, `TraversalLaws` and `IsoLaws` state the laws you already know.

**The code on this page is [FromMonocleBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/optics/comingfrom/FromMonocleBook.java) and its [FromMonocleBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/optics/comingfrom/FromMonocleBookTest.java)**: the page includes the first, and the test holds each difference this page states about the library.

---

~~~admonish info title="Where next"
- The Java a reader writes today, beside its optic: [Coming from Lombok, Streams and Switch](from_java.md)
- The type `andThen` returns for every pair: [Composition Rules](composition_rules.md)
- What each optic type declares: [Optic Capabilities](optic_capabilities.md)
~~~

---

**Previous:** [Coming from Lombok, Streams and Switch](from_java.md)
**Next:** [Focus DSL Reference](focus_reference.md)
