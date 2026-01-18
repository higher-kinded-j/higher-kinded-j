# When Lenses Assume Too Much

## _Atomic Updates for Fields with Shared Invariants_
![coupled_mr_robot.png](../images/coupled_mr_robot.png)
> *"A bug is never just a mistake. It represents something bigger. An error of thinking that makes you who you are."*
> — Elliot Alderson, Mr. Robot

When a lens update throws an exception, it is not the lens that is broken; it is our assumption about field independence. What looks like a bug often reveals a deeper truth about the relationship between fields in our data structures.

Consider a familiar scenario: you have a record with validation in its constructor. You have written (or generated) lenses for each field. Everything works perfectly, until you need to update two fields together. Suddenly, valid transformations become impossible.

The hidden culprit? Standard lens composition assumes fields are independent, that you can update `lo` without caring about `hi`. But some fields are *coupled* by invariants. They do not just coexist; they constrain each other. Lenses, in their elegant simplicity, do not know this.

~~~admonish info title="What You'll Learn"
- Why standard lens updates can fail with invariant-protected records
- The hidden assumption of field independence in lens composition
- How to use `Lens.paired` for atomic multi-field updates
- When to define paired lenses vs individual field lenses
- Limitations and alternative approaches
~~~

~~~admonish example title="See Example Code"
[PairedLensExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PairedLensExample.java)
~~~

---

## The Independence Assumption

When we compose lenses with `andThen`, we are drilling *vertically* through nested structures:

```
VERTICAL COMPOSITION (andThen)        HORIZONTAL COMPOSITION (paired)
══════════════════════════════        ══════════════════════════════

    Lens<S, A>                            Lens<S, A>    Lens<S, B>
         │                                     │            │
         ▼ andThen                             └─────┬──────┘
    Lens<A, B>                                       │
         │                                           ▼ paired
         ▼                                           │
    Lens<S, B>                            Lens<S, Pair<A, B>>


    Focus: NESTED fields                  Focus: SIBLING fields
    (drilling deeper)                     (same level, same source)

    Example:                              Example:
    User → Address → Street               Range → (lo, hi)
```

Vertical composition (`andThen`) assumes that once you have focused on a field, you can update it independently. This works beautifully for nested structures like `Employee → Company → Address → Street`.

But what about sibling fields at the same level that share an invariant?

---

## The Problem: Invariant Violation

Consider a simple bounded range:

```java
record Range(int lo, int hi) {
    Range {
        if (lo > hi) {
            throw new IllegalArgumentException(
                "lo (" + lo + ") must be <= hi (" + hi + ")");
        }
    }
}
```

We can create lenses for each field:

```java
Lens<Range, Integer> loLens =
    Lens.of(Range::lo, (r, lo) -> new Range(lo, r.hi()));

Lens<Range, Integer> hiLens =
    Lens.of(Range::hi, (r, hi) -> new Range(r.lo(), hi));
```

Now let us try to shift the range up by 10:

```java
Range range = new Range(1, 2);

// Goal: Range(1, 2) → Range(11, 12)

// Attempt 1: Update lo first
Range step1 = loLens.set(11, range);  // Range(11, 2)
// THROWS: "lo (11) must be <= hi (2)"
```

The update failed because the intermediate state `Range(11, 2)` violates the invariant.

What if we update `hi` first?

```java
Range step1 = hiLens.set(12, range);  // Range(1, 12) - OK!
Range step2 = loLens.set(11, step1);  // Range(11, 12) - OK!
```

That works! But now try shifting *down* by 10 from `Range(10, 11)`:

```java
Range narrow = new Range(10, 11);

// If we update hi first: Range(10, 1) - THROWS!
// If we update lo first: Range(0, 11) → Range(0, 1) - OK
```

The "correct" order depends on the direction of change!

---

## Why This Happens

```
Record: Range(lo=1, hi=2)          Invariant: lo ≤ hi
════════════════════════════════════════════════════════════════

Goal: Shift both bounds by +10 to get Range(11, 12)

SEQUENTIAL APPROACH                 PAIRED APPROACH
───────────────────                 ───────────────

    Range(1, 2)                         Range(1, 2)
         │                                   │
         ▼ loLens.set(11)                    ▼ boundsLens.get
    Range(11, 2)                        Pair(1, 2)
         │                                   │
    ╔════╧════════╗                          ▼ transform
    ║  INVALID!   ║                     Pair(11, 12)
    ║   11 > 2    ║                          │
    ║   THROWS    ║                          ▼ reconstruct via Range::new
    ╚═════════════╝                     Range(11, 12)
                                             │
                                        ╔════╧════╗
                                        ║ VALID!  ║
                                        ╚═════════╝

The paired lens bypasses the intermediate state entirely.
```

Sequential lens updates create intermediate states. When fields are coupled by an invariant, these intermediate states can be invalid, even when both the starting and ending states are perfectly valid.

---

## The Solution: Paired Lenses

> *"They're all tied in together."*
> — Sergeant Pinback, Dark Star

This boils down to: if fields are coupled, update them together. `Lens.paired` combines two lenses into one that focuses on both values as a `Pair`:

```java
Lens<Range, Pair<Integer, Integer>> boundsLens =
    Lens.paired(loLens, hiLens, Range::new);
```

Now we can shift safely:

```java
Range range = new Range(1, 2);

// Shift up by 10 - both values updated atomically
Range shifted = boundsLens.modify(
    p -> Pair.of(p.first() + 10, p.second() + 10),
    range
);
// Result: Range(11, 12)
```

The transformation happens in a single step:
1. Extract both values: `Pair(1, 2)`
2. Transform: `Pair(11, 12)`
3. Reconstruct via `Range::new`: `Range(11, 12)`

No intermediate state. No invariant violation. Order independence.

---

## API Reference

### `Lens.paired` with BiFunction

When your record has only the coupled fields:

```java
static <S, A, B> Lens<S, Pair<A, B>> paired(
    Lens<S, A> first,
    Lens<S, B> second,
    BiFunction<A, B, S> constructor
)
```

**Example:**
```java
Lens<Range, Pair<Integer, Integer>> boundsLens =
    Lens.paired(loLens, hiLens, Range::new);
```

### `Lens.paired` with Function3

When your record has additional fields that must be preserved:

```java
static <S, A, B> Lens<S, Pair<A, B>> paired(
    Lens<S, A> first,
    Lens<S, B> second,
    Function3<S, A, B, S> reconstructor
)
```

**Example:**
```java
record Transaction(String id, int min, int max, String note) {
    Transaction {
        if (min > max) throw new IllegalArgumentException("min > max");
    }
}

Lens<Transaction, Pair<Integer, Integer>> limitsLens = Lens.paired(
    minLens,
    maxLens,
    (txn, newMin, newMax) -> new Transaction(txn.id(), newMin, newMax, txn.note())
);
```

---

## Choosing the Right Approach

| Scenario | Recommended Approach |
|----------|---------------------|
| Independent fields | Use individual lenses |
| Fields with shared invariant | Use `Lens.paired` |
| Computed/derived fields | Don't expose a lens for the computed field |
| Cross-structure invariants | Use domain methods, not lenses |

### When NOT to Use Paired Lenses

**Computed fields:** If field B is always computed from field A, do not create a lens for B at all:

```java
// Data is the source of truth; checksum is derived
Lens<Packet, byte[]> dataLens = Lens.of(
    Packet::data,
    (p, newData) -> new Packet(newData, computeChecksum(newData))
);
// No checksumLens - it's always recomputed
```

**Cross-structure invariants:** When invariants span parent and child objects, use domain methods:

```java
// Don't use lenses - use domain operations
Order updated = order.withLine(lineId, line -> line.withPrice(newPrice));
// The withLine method recalculates totalPrice internally
```

---

## Practical Examples

### Bounded Ranges

```java
record Range(int lo, int hi) {
    Range { if (lo > hi) throw new IllegalArgumentException(); }
}

Lens<Range, Pair<Integer, Integer>> boundsLens =
    Lens.paired(loLens, hiLens, Range::new);

// Shift
Range shifted = boundsLens.modify(
    p -> Pair.of(p.first() + 10, p.second() + 10),
    range
);

// Scale
Range scaled = boundsLens.modify(
    p -> Pair.of(p.first() * 2, p.second() * 2),
    range
);

// Widen symmetrically
Range widened = boundsLens.modify(
    p -> Pair.of(p.first() - 5, p.second() + 5),
    range
);
```

### Rectangles with Constraints

```java
record Rectangle(Point topLeft, Point bottomRight) {
    Rectangle {
        if (topLeft.x() >= bottomRight.x() || topLeft.y() >= bottomRight.y()) {
            throw new IllegalArgumentException("Invalid rectangle bounds");
        }
    }
}

Lens<Rectangle, Pair<Point, Point>> cornersLens =
    Lens.paired(topLeftLens, bottomRightLens, Rectangle::new);

// Move the entire rectangle
Rectangle moved = cornersLens.modify(
    p -> Pair.of(
        p.first().translate(dx, dy),
        p.second().translate(dx, dy)
    ),
    rect
);
```

### Configuration with Port Ranges

```java
record ServerConfig(String host, int minPort, int maxPort) {
    ServerConfig {
        if (minPort > maxPort || minPort < 1024 || maxPort > 65535) {
            throw new IllegalArgumentException("Invalid port range");
        }
    }
}

Lens<ServerConfig, Pair<Integer, Integer>> portsLens = Lens.paired(
    minPortLens,
    maxPortLens,
    (cfg, min, max) -> new ServerConfig(cfg.host(), min, max)
);

// Shift port range up by 1000
ServerConfig updated = portsLens.modify(
    p -> Pair.of(p.first() + 1000, p.second() + 1000),
    config
);
```

---

## Composition with Paired Lenses

A paired lens is just a normal `Lens<S, Pair<A, B>>`, so it composes naturally:

```java
// Config contains ServerConfig which has coupled port range
Lens<Config, Pair<Integer, Integer>> configPorts =
    configServerLens.andThen(serverPortsLens);

// Shift both ports atomically through the nested structure
Config updated = configPorts.modify(
    p -> Pair.of(p.first() + 1000, p.second() + 1000),
    config
);
```

~~~admonish warning title="Anti-Pattern: Unpacking a Paired Lens"
Avoid composing a paired lens with a lens that extracts a single element:

```java
// DON'T DO THIS - defeats the purpose of pairing
Lens<Pair<Integer, Integer>, Integer> firstLens =
    Lens.of(Pair::first, (p, a) -> Pair.of(a, p.second()));
Lens<Range, Integer> justLo = boundsLens.andThen(firstLens);
// You're back to the original problem!
```

If you need single-field access, use the original individual lens directly.
~~~

---

## Three or More Coupled Fields

For records with three or more coupled fields, currently we can nest Pairs:

**Nest pairs:**
```java
// For RGB with constraint r + g + b <= 255
Lens<RGB, Pair<Integer, Pair<Integer, Integer>>> rgbLens = Lens.paired(
    rLens,
    Lens.paired(gLens, bLens, Pair::of),
    (rgb, r, gb) -> new RGB(r, gb.first(), gb.second())
);
```
~~~admonish help title="Wait, I need more!"
**Feature Request `paired3`:** If you have a compelling use case, `Lens.paired3` can be added to the library. Open a Github feature request.

**Future: Builder pattern:** A future version may support:
```java
// Potential future API
Lens.pairing(rLens).with(gLens).with(bLens).build(RGB::new)
```
~~~


## Lens Laws

Paired lenses satisfy the standard lens laws:

- **GetPut:** `set(get(s), s) == s`
- **PutGet:** `get(set(a, s)) == a`
- **PutPut:** `set(a2, set(a1, s)) == set(a2, s)`

These are verified by property-based tests in `LensPairedLawsPropertyTest.java`.

---

~~~admonish info title="Key Takeaways"
1. **Standard lenses assume field independence** - updating one field should not affect another
2. **Coupled fields violate this assumption** - invariants create dependencies between fields
3. **Sequential updates create invalid intermediate states** - even when start and end are valid
4. **`Lens.paired` provides atomic multi-field updates** - bypassing intermediate states entirely
5. **Order independence** - paired lenses do not care which direction you are transforming
~~~

~~~admonish tip title="See Also"
- [Lenses](lenses.md) - Core lens concepts and operations
- [Composition Rules](composition_rules.md) - How different optics compose
- [Isomorphisms](iso.md) - For transforming between constrained and unconstrained representations
~~~

~~~admonish tip title="Further Reading"
**Chris Penner**: [Virtual Record Fields Using Lenses](https://chrispenner.ca/posts/virtual-fields) - Introduces "virtual fields" as computed properties accessed through lenses. Penner demonstrates how hiding data constructors and exporting only lenses creates a stable public interface that absorbs internal refactoring. His treatment of data invariants is relevant here: where we use `Lens.paired` to *enforce* invariants during updates, Penner uses lenses to *hide* representation details and maintain invariants transparently. He also candidly notes that breaking lens laws is "usually perfectly fine" for pragmatism, echoing our observation that real-world records often have constraints that do not fit the idealised lens model.

**Gunnar Morling**: [Enforcing Java Record Invariants With Bean Validation](https://www.morling.dev/blog/enforcing-java-record-invariants-with-bean-validation/) - Tackles record invariants from a different angle, using Bean Validation annotations to enforce constraints automatically at construction time. The article explicitly discusses multi-field invariants like "end must be greater than begin", precisely the kind of coupled constraint that breaks sequential lens updates. Morling's approach guarantees the invariant holds but does not help you *transform* a valid object when both fields must change together. This is where `Lens.paired` complements Bean Validation: validation solves the construction problem; paired lenses solve the transformation problem. In a robust system, you would use both.
~~~

---

**Previous:** [Composition Rules](composition_rules.md)
**Next:** [Introduction to Collection Optics](ch2_intro.md)
