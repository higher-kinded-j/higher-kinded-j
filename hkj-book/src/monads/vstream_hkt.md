# VStream HKT: Type Classes for Lazy Streams
## _Making VStream a First-Class Citizen of Higher-Kinded-J_

~~~admonish info title="What You'll Learn"
- How VStream participates in the HKT simulation via `VStreamKind`
- The widen/narrow pattern for moving between VStream and Kind
- VStream's type class hierarchy: Functor, Applicative, Monad, Alternative
- Foldable and Traverse instances (with finite-stream constraints)
- Writing generic functions that work with VStream and any other monadic type
~~~

~~~admonish example title="See Example Code"
[VStreamHKTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/vstream/VStreamHKTExample.java)
~~~

## Why Does VStream Need HKT?

Without HKT encoding, every function that operates on containers must be rewritten
for each type. A function that doubles every number inside a `Maybe` cannot also
double every number inside a `VStream` without a second, near-identical
implementation. HKT encoding solves this by letting VStream present itself as
`Kind<VStreamKind.Witness, A>`, the same shape that Maybe, Either, VTask, and every
other Higher-Kinded-J type uses. One generic function handles them all.

```java
// Generic: works with VStream, Maybe, Either, VTask, List, ...
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Integer> doubleAll(
        Functor<F> functor, Kind<F, Integer> fa) {
    return functor.map(n -> n * 2, fa);
}

// Using it with VStream
Kind<VStreamKind.Witness, Integer> doubled =
    doubleAll(VStreamFunctor.INSTANCE, VSTREAM.widen(VStream.of(1, 2, 3)));

List<Integer> result = VSTREAM.narrow(doubled).toList().run();
// [2, 4, 6]
```

**Package**: `org.higherkindedj.hkt.vstream`
**Module**: `hkj-core`

---

## The HKT Encoding

VStream uses the standard Higher-Kinded-J encoding pattern: a witness type, a Kind
interface, and a helper for safe conversions.

```
                     Kind<F, A>
                         │
                         │ extends
                         ▼
               VStreamKind<A>                 (marker interface)
                    │
                    │ extends
                    ▼
              VStream<A>                      (concrete type)

  VStreamKind.Witness                         (phantom type tag)
  └── implements WitnessArity<TypeArity.Unary>
```

### VStreamKind: The Witness Type

```java
public interface VStreamKind<A> extends Kind<VStreamKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}
```

`VStream<A>` extends `VStreamKind<A>`, so every VStream is already a
`Kind<VStreamKind.Witness, A>`. The `Witness` class is the phantom type tag that
tells the type class machinery "this is VStream".

### Widen and Narrow

`VStreamKindHelper` provides safe conversions between the concrete and HKT
representations:

```java
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

// Concrete → HKT (simple upcast, since VStream extends VStreamKind)
Kind<VStreamKind.Witness, String> widened = VSTREAM.widen(VStream.of("a", "b"));

// HKT → Concrete (validated downcast)
VStream<String> narrowed = VSTREAM.narrow(widened);
```

Narrowing performs a type check. If you accidentally pass a Kind with the wrong
witness type, you get a clear error rather than a silent ClassCastException.

---

## Type Class Hierarchy

VStream implements the full standard type class tower, from Functor up to
Alternative, plus Foldable and Traverse for finite streams.

```
                   ┌───────────────┐
                   │   Functor     │  map
                   └───────┬───────┘
                           │
                   ┌───────▼───────┐
                   │  Applicative  │  of, ap
                   └───────┬───────┘
                           │
                   ┌───────▼───────┐
                   │    Monad      │  flatMap
                   └───────┬───────┘
                           │
                   ┌───────▼───────┐
                   │  Alternative  │  empty, orElse
                   └───────────────┘

  Also:
                   ┌───────────────┐
                   │   Foldable    │  foldMap  (finite streams only)
                   └───────┬───────┘
                           │
                   ┌───────▼───────┐
                   │   Traverse    │  traverse (finite streams only)
                   └───────────────┘
```

Each level in the hierarchy extends the one above. `VStreamAlternative.INSTANCE`
provides access to all of Functor, Applicative, Monad, and Alternative in a single
object.

| Instance | Singleton | Key Operation | Semantics |
|----------|-----------|---------------|-----------|
| `VStreamFunctor` | `INSTANCE` | `map(f, fa)` | Transform each element lazily |
| `VStreamApplicative` | `INSTANCE` | `of(a)`, `ap(ff, fa)` | Lift value; Cartesian product |
| `VStreamMonad` | `INSTANCE` | `flatMap(f, ma)` | Substitute and flatten |
| `VStreamAlternative` | `INSTANCE` | `empty()`, `orElse(fa, fb)` | Empty stream; concatenation |
| `VStreamTraverse` | `INSTANCE` | `foldMap`, `traverse` | Materialise and fold/traverse |

---

## Functor: Transforming Elements

`VStreamFunctor` delegates to `VStream.map()`, preserving laziness. No elements are
produced until a terminal operation runs.

```java
Functor<VStreamKind.Witness> functor = VStreamFunctor.INSTANCE;

Kind<VStreamKind.Witness, String> stream = VSTREAM.widen(VStream.of(1, 2, 3));
Kind<VStreamKind.Witness, String> mapped = functor.map(n -> "#" + n, stream);

List<String> result = VSTREAM.narrow(mapped).toList().run();
// ["#1", "#2", "#3"]
```

**Functor laws satisfied**:
- Identity: `map(x -> x, stream)` produces the same elements as `stream`
- Composition: `map(g.compose(f), stream)` equals `map(g, map(f, stream))`

---

## Applicative: Lifting and Combining

`VStreamApplicative` uses **Cartesian product** semantics for `ap`. When you apply a
stream of functions to a stream of values, every function is applied to every value.
This is the standard choice for list-like monads, consistent with StreamMonad and
NonDetPath.

```java
Applicative<VStreamKind.Witness> applicative = VStreamApplicative.INSTANCE;

// Lift a pure value into a single-element stream
Kind<VStreamKind.Witness, String> single = applicative.of("hello");
// VStream.of("hello")

// Cartesian product: 2 functions x 3 values = 6 results
Kind<VStreamKind.Witness, Function<Integer, String>> fns =
    VSTREAM.widen(VStream.of(n -> "x" + n, n -> "y" + n));

Kind<VStreamKind.Witness, Integer> vals =
    VSTREAM.widen(VStream.of(1, 2, 3));

Kind<VStreamKind.Witness, String> applied = applicative.ap(fns, vals);

List<String> result = VSTREAM.narrow(applied).toList().run();
// ["x1", "x2", "x3", "y1", "y2", "y3"]
```

---

## Monad: Sequential Composition

`VStreamMonad` provides `flatMap`, which substitutes each element with a sub-stream
and flattens the results. This is the monadic bind for VStream, and it preserves
lazy evaluation throughout.

```java
Monad<VStreamKind.Witness> monad = VStreamMonad.INSTANCE;

Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

Kind<VStreamKind.Witness, Integer> expanded = monad.flatMap(
    n -> VSTREAM.widen(VStream.of(n, n * 10)),
    stream
);

List<Integer> result = VSTREAM.narrow(expanded).toList().run();
// [1, 10, 2, 20, 3, 30]
```

**Monad laws satisfied**:
- Left identity: `flatMap(of(a), f)` equals `f(a)`
- Right identity: `flatMap(stream, of)` equals `stream`
- Associativity: `flatMap(flatMap(stream, f), g)` equals `flatMap(stream, x -> flatMap(f(x), g))`

---

## Alternative: Empty and Concatenation

`VStreamAlternative` models choice for streams. The `empty()` method returns an
empty stream (the identity element), and `orElse` concatenates two streams. This
is consistent with list-like Alternative instances: "try all of stream A, then try
all of stream B".

```java
Alternative<VStreamKind.Witness> alt = VStreamAlternative.INSTANCE;

Kind<VStreamKind.Witness, Integer> first = VSTREAM.widen(VStream.of(1, 2));
Kind<VStreamKind.Witness, Integer> second = VSTREAM.widen(VStream.of(3, 4));

// Concatenation
Kind<VStreamKind.Witness, Integer> combined = alt.orElse(first, () -> second);
List<Integer> result = VSTREAM.narrow(combined).toList().run();
// [1, 2, 3, 4]

// Empty is the identity
Kind<VStreamKind.Witness, Integer> empty = alt.empty();
Kind<VStreamKind.Witness, Integer> same = alt.orElse(first, () -> empty);
// produces [1, 2]

// Guard: filter based on a boolean condition
Kind<VStreamKind.Witness, Unit> passed = alt.guard(true);   // single Unit
Kind<VStreamKind.Witness, Unit> failed = alt.guard(false);  // empty
```

---

## Foldable and Traverse: Finite Streams Only

~~~admonish warning title="Finite Streams Only"
`foldMap` and `traverse` materialise the entire stream before processing. They are
**not safe for infinite streams** and will not terminate if the stream has no end.
Use `take()` or `takeWhile()` to bound the stream before folding or traversing.
~~~

### Foldable

`foldMap` pulls every element, maps each one through a function, and combines the
results using a monoid. Because VStream elements are produced via VTask, this is a
terminal operation that executes the stream.

```java
VStreamTraverse traverse = VStreamTraverse.INSTANCE;

Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3, 4, 5));

// Sum using the integer addition monoid
int sum = traverse.foldMap(Monoid.intSum(), n -> n, stream);
// 15

// String concatenation
String csv = traverse.foldMap(
    Monoid.string(),
    n -> String.valueOf(n),
    stream
);
// "12345"
```

### Traverse

`traverse` applies an effectful function to each element and collects the results
inside an outer applicative context. For VStream, this materialises the stream to a
list first, traverses the list, and reconstructs the result as a VStream.

```java
// Traverse with Maybe: if any element maps to Nothing, the whole result is Nothing
Applicative<MaybeKind.Witness> maybeApp = MaybeMonad.INSTANCE;

Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(2, 4, 6));

Kind<MaybeKind.Witness, Kind<VStreamKind.Witness, String>> result =
    traverse.traverse(
        maybeApp,
        n -> n > 0
            ? MaybeKindHelper.MAYBE.widen(Maybe.just("+" + n))
            : MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
        stream
    );

// All positive, so result is Just(VStream.of("+2", "+4", "+6"))
```

---

## Writing Generic Functions

The real power of HKT encoding is writing functions that work with any monadic type.
Here is a function that triples every element in any Functor:

```java
static <F extends WitnessArity<TypeArity.Unary>>
Kind<F, Integer> tripleAll(Functor<F> functor, Kind<F, Integer> fa) {
    return functor.map(n -> n * 3, fa);
}

// Works with VStream
Kind<VStreamKind.Witness, Integer> tripled = tripleAll(
    VStreamMonad.INSTANCE,
    VSTREAM.widen(VStream.of(1, 2, 3))
);
// [3, 6, 9]

// Same function works with Maybe
Kind<MaybeKind.Witness, Integer> tripledMaybe = tripleAll(
    MaybeMonad.INSTANCE,
    MaybeKindHelper.MAYBE.widen(Maybe.just(7))
);
// Just(21)
```

And a function that uses Monad to compose sequential operations:

```java
static <F extends WitnessArity<TypeArity.Unary>>
Kind<F, String> fetchAndFormat(
        Monad<F> monad,
        Kind<F, Integer> ids) {
    return monad.flatMap(
        id -> monad.map(name -> name + " (id=" + id + ")", monad.of("User" + id)),
        ids
    );
}

// With VStream: processes each id, producing a formatted string per element
Kind<VStreamKind.Witness, String> users = fetchAndFormat(
    VStreamMonad.INSTANCE,
    VSTREAM.widen(VStream.of(1, 2, 3))
);
// ["User1 (id=1)", "User2 (id=2)", "User3 (id=3)"]
```

---

## How VStream Compares to Other Instances

| Aspect | VStream | Maybe | List/Stream | VTask |
|--------|---------|-------|-------------|-------|
| `of(a)` | Single-element stream | `Just(a)` | Single-element list | Succeed with value |
| `ap` semantics | Cartesian product | Apply if both present | Cartesian product | Sequential |
| `flatMap` | Substitute and flatten | Chain if present | Substitute and flatten | Chain effects |
| `empty` | Empty stream | `Nothing` | Empty list | N/A (no Alternative) |
| `orElse` | Concatenate streams | First non-empty | Concatenate lists | N/A |
| `foldMap` | Materialise and fold | Extract or identity | Fold list | N/A (no Foldable) |
| Lazy evaluation | Yes | N/A (single value) | Eager (materialised) | Yes (deferred) |

---

~~~admonish info title="Key Takeaways"
* **VStreamKind** is the HKT witness type that lets VStream participate in generic, type-class-based programming
* **Widen/narrow** via `VStreamKindHelper.VSTREAM` converts safely between VStream and Kind
* **Functor, Applicative, Monad** all preserve lazy evaluation; no elements are produced until a terminal operation runs
* **Applicative uses Cartesian product** semantics: every function applied to every value
* **Alternative uses concatenation**: `orElse` appends the second stream after the first
* **Foldable and Traverse materialise** the stream; use only on finite streams
* **Generic functions** written against Functor, Monad, or Alternative work with VStream alongside any other Higher-Kinded-J type
~~~

~~~admonish info title="Hands-On Learning"
Practice VStream HKT encoding in [TutorialVStreamHKT](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVStreamHKT.java) (10 exercises, ~12-15 minutes).
~~~

~~~admonish tip title="See Also"
- [VStream](vstream.md) - Core VStream type: factories, combinators, terminal operations
- [VStreamPath](../effect/path_vstream.md) - Fluent Effect Path wrapper for VStream
- [Higher-Kinded Types](../hkts/hkt_introduction.md) - How the HKT simulation works
- [Functor](../functional/functor.md) - The Functor type class
- [Monad](../functional/monad.md) - The Monad type class
- [Alternative](../functional/alternative.md) - The Alternative type class
- [Foldable and Traverse](../functional/foldable_and_traverse.md) - Folding and effectful traversal
~~~

---

**Previous:** [VStream](vstream.md)
**Next:** [Parallel Operations](vstream_parallel.md)
