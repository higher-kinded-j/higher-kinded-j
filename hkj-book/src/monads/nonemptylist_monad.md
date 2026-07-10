# NonEmptyList:
## _A List That Is Never Empty_

~~~admonish info title="What You'll Learn"
- Why `List<Error>` is the wrong type for an accumulating error channel, and how `NonEmptyList` fixes it
- Total operations (`head`, `last`, `reduce`, `min`, `max`) that never throw, because there is always at least one element
- Constructing a `NonEmptyList` safely: `of`, `single`, and the checked `fromList` / `fromIterable` that return `Maybe`
- Using `NonEmptyList` as the default validation error channel with `Path.validNel` / `Path.invalidNel`: no `Semigroup` argument, no manual single-error wrapping
- Why there is deliberately **no** empty `NonEmptyList` and **no** `Monoid` instance
~~~

~~~admonish example title="See Example Code:"
[NonEmptyListExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/nonemptylist/NonEmptyListExample.java)
~~~

## The Problem: `List<Error>` Lies

Accumulating validation is one of Higher-Kinded-J's headline stories: `Validated` and `ValidationPath` collect *all* the errors instead of stopping at the first. But the type the errors are carried in matters. The common choice, `List<Error>`, permits an impossible state:

```java
// An "invalid" result always has at least one error, yet the type allows zero.
ValidationPath<List<Error>, User> failure = Path.invalid(List.of(error), Semigroups.list());
Error first = failure.run().getError().getFirst();   // partial: getFirst() throws on empty
```

Three things hurt here:

- **It permits an impossible state.** An *invalid* result always has one or more errors, yet `List<Error>` allows the empty list. Consumers must defensively guard (`errors.isEmpty()`), and `getFirst()` is partial.
- **It is ceremony-heavy.** Every entry point hands in `Semigroups.list()` by hand, and every single-error leaf wraps its error in `List.of(...)`.
- **There is no total `head`.** Reading "the first error" can throw.

`NonEmptyList<A>` is a list guaranteed by its type to contain at least one element. The invalid branch proves non-emptiness at compile time, and the common case loses its boilerplate:

```java
// NonEmptyList error channel: no Semigroup argument, no List.of wrapping.
ValidationPath<NonEmptyList<Error>, User> failure = Path.invalidNel(error);
Error first = failure.run().getError().head();   // total: never throws, returns Error
```

This is the canonical companion to `Validated` in comparable libraries (Cats' `NonEmptyList` / `ValidatedNel`) precisely because it removes the empty-error-list footgun.

---

## Core Components

| Component | Role |
|-----------|------|
| `NonEmptyList<A>` | An immutable `record` of a `head` plus a (possibly empty) `tail`; always at least one element. Implements `Iterable<A>`. |
| `NonEmptyListKind<A>` / `NonEmptyListKindHelper` | HKT bridge. `NonEmptyList` implements its own `Kind` directly, so `widen()` is a cast-free upcast and `narrow()` a direct cast. |
| `NonEmptyListMonad` | `Monad<NonEmptyListKind.Witness>`: `map`, `flatMap`, `of`, and a Cartesian `ap`. Deliberately **not** a `MonadZero`. |
| `NonEmptyListTraverse` | `Traverse` and `Foldable`; results are non-empty by construction. |
| `NonEmptyList.semigroup()` | The concatenating `Semigroup<NonEmptyList<A>>` used as the accumulating error channel. |

---

## Construction

Java has no non-empty literal, so construction is explicit about where the guaranteed element comes from:

```java
NonEmptyList<Integer> a = NonEmptyList.of(1, 2, 3);        // head + varargs tail
NonEmptyList<Integer> b = NonEmptyList.of(1, List.of(2));  // head + explicit tail
NonEmptyList<Integer> c = NonEmptyList.single(42);         // single element
```

To build one from data that *might* be empty, use the checked factories. They never throw; they return `Maybe`:

```java
Maybe<NonEmptyList<Integer>> maybe = NonEmptyList.fromList(List.of(1, 2, 3)); // Just([1, 2, 3])
Maybe<NonEmptyList<Integer>> none  = NonEmptyList.fromList(List.of());        // Nothing
```

~~~admonish note title="Immutable and null-safe"
The `tail` is defensively copied on construction, `tail()` returns an unmodifiable view, and elements are never `null`; any attempt to introduce a `null` element is rejected at construction time.
~~~

---

## Total Accessors

Because non-emptiness is part of the type, operations that are *partial* on an ordinary `List` are **total** here: they always return a value and never throw, with no `Optional` to unwrap:

```java
NonEmptyList<Integer> nel = NonEmptyList.of(3, 1, 2);

int head = nel.head();                          // 3   (never throws)
int last = nel.last();                          // 2   (never throws)
int sum  = nel.reduce((x, y) -> x + y);         // 6   (reduce without an identity)
int min  = nel.min(Comparator.naturalOrder());  // 1
int max  = nel.max(Comparator.naturalOrder());  // 3
```

It is also `Iterable`, so it works directly in for-each loops and streams, and `toJavaList()` gives an immutable `java.util.List` for interop.

---

## The Validation Error Channel

`NonEmptyList` is the natural carrier for accumulating validation. The `Path` factories bake in `NonEmptyList.semigroup()`, so the common case needs no `Semigroup` argument and no manual `List.of(error)` wrapping:

```java
// A single-error leaf wraps its error in a singleton NonEmptyList; that is the whole idiom.
ValidationPath<NonEmptyList<String>, String> name  = Path.invalidNel("name is blank");
ValidationPath<NonEmptyList<String>, String> email = Path.invalidNel("email is invalid");

// Accumulation just concatenates the two NonEmptyLists, non-empty by construction.
ValidationPath<NonEmptyList<String>, String> both = name.andAlso(email);
both.run().getError().toJavaList();   // ["name is blank", "email is invalid"]  (left-to-right)
```

The same conveniences exist directly on `Validated`:

```java
Validated<NonEmptyList<String>, Integer> ok  = Validated.validNel(42);
Validated<NonEmptyList<String>, Integer> bad = Validated.invalidNel("must be positive");
```

~~~admonish tip title="Two distinct 'combines'"
Do not confuse `NonEmptyList`'s applicative `ap` (a Cartesian product, like `List`) with `NonEmptyList.semigroup()` (concatenation). Error accumulation in `ValidationPath` uses the **semigroup**: left-to-right concatenation, which is associative but *not* commutative, so error order is preserved.
~~~

The existing `Semigroups.list()` channel keeps working unchanged; `NonEmptyList` is an additive, less error-prone default.

---

## Using It as a Higher-Kinded Type

Casual use never requires `Kind`: `NonEmptyList.of(a, b).map(f).flatMap(g)` is a plain fluent chain. When you need the type-class instances for generic code, reach them through the usual registry:

```java
Monad<NonEmptyListKind.Witness> monad = Instances.monad(nonEmptyList());
Kind<NonEmptyListKind.Witness, Integer> lifted = monad.of(1);
```

~~~admonish warning title="No empty, by design"
There is no factory that produces an empty `NonEmptyList`, and therefore **no `Monoid` instance** and **no `MonadZero`/`Alternative`**: those carry an empty identity, which would reintroduce the very footgun the type exists to remove. `NonEmptyList` is a `Functor`, `Applicative`, `Monad`, `Foldable`, `Traverse`, and `Semigroup`, but never a `Monoid`. The absence is the point.
~~~

~~~admonish info title="Key Takeaways"
- `NonEmptyList<A>` encodes "at least one element" in the type, making `head`/`last`/`reduce`/`min`/`max` **total**.
- Build with `of` / `single`; cross the boundary from possibly-empty data with `fromList` / `fromIterable`, which return `Maybe` and never throw.
- It is the streamlined validation error channel: `Path.validNel` / `Path.invalidNel` and `Validated.validNel` / `Validated.invalidNel` drop the `Semigroup` argument and the single-error wrapping.
- Accumulation is left-to-right concatenation via `NonEmptyList.semigroup()`.
- Deliberately no empty, no `Monoid`, no `MonadZero`.
~~~

~~~admonish tip title="See Also"
- [Validated](validated_monad.md): the accumulating-error type `NonEmptyList` pairs with
- [List](list_monad.md): the unconstrained sibling
- [ValidationPath](../effect/path_validation.md): the fluent validation API
- [Accumulating Assembly](validated_assembly.md): the `fields()` / `accumulate()` builder this channel was built for
- [Semigroups and Monoids](../functional/semigroup_and_monoid.md): where `NonEmptyList.semigroup()` fits
- [Advanced Paths](../effect/advanced_topics.md): the `race` and first-success combinators take `NonEmptyList` for a total, guard-free call
~~~

---

**Previous:** [List](list_monad.md)
**Next:** [Maybe](maybe_monad.md)
