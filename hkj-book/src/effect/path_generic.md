# GenericPath

`GenericPath<F, A>` is the escape hatch. It wraps *any* `Kind<F, A>` with
a `Monad` instance, letting you use Path operations on custom types.

~~~admonish info title="What You'll Learn"
- Creating GenericPath instances
- Working with custom monads
- Extraction patterns
- When to use (and when not to)
~~~

---

## Creation

```java
Monad<ListKind.Witness> listMonad = ListMonad.INSTANCE;
Kind<ListKind.Witness, Integer> listKind =
    ListKindHelper.LIST.widen(List.of(1, 2, 3));

GenericPath<ListKind.Witness, Integer> listPath =
    Path.generic(listKind, listMonad);
```

---

## Core Operations

```java
GenericPath<ListKind.Witness, Integer> numbers = Path.generic(listKind, listMonad);

GenericPath<ListKind.Witness, String> strings = numbers.map(n -> "n" + n);

GenericPath<ListKind.Witness, Integer> doubled = numbers.via(n ->
    Path.generic(ListKindHelper.LIST.widen(List.of(n, n * 2)), listMonad));
```

---

## Extraction

```java
Kind<ListKind.Witness, Integer> kind = path.runKind();
List<Integer> list = ListKindHelper.LIST.narrow(kind);
```

---

## When to Use

`GenericPath` is right when:
- You have a custom monad not covered by specific Path types
- Writing highly generic code across multiple monad types
- Experimenting with new effect types

~~~admonish tip title="Extensibility by Design"
`GenericPath` demonstrates the power of higher-kinded types in Java: write
your algorithm once, and it works with `Maybe`, `Either`, `List`, `IO`, or
any custom monad. This is the same abstraction power that makes libraries
like Cats and ZIO flexible in Scala, now available in Java.
~~~

~~~admonish tip title="See Also"
- [HKT Introduction](../hkts/hkt_introduction.md) - Higher-kinded type basics
- [Extending](../hkts/extending-simulation.md) - Creating custom types
~~~

---

**Previous:** [OptionalPath](path_optional.md)
**Next:** [TrampolinePath](path_trampoline.md)
