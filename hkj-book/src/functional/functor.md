# Functor: The "Mappable" Type Class 🎁

At the heart of functional programming is the ability to transform data within a container without having to open it. The **`Functor`** type class provides exactly this capability. It's the simplest and most common abstraction for any data structure that can be "mapped over."

If you've ever used `Optional.map()` or `Stream.map()`, you've already been using the Functor pattern! `higher-kinded-j` simply formalizes this concept so you can apply it to any data structure.

---

## What is it?

A **`Functor`** is a type class for any data structure `F` that supports a `map` operation. This operation takes a function from `A -> B` and applies it to the value(s) inside a container `F<A>`, producing a new container `F<B>` of the same shape.

Think of a `Functor` as a generic "box" that holds a value. The `map` function lets you transform the contents of the box without ever taking the value out. Whether the box is an `Optional` that might be empty, a `List` with many items, or a `Try` that might hold an error, the mapping logic remains the same.

The interface for `Functor` in `hkj-api` is simple and elegant:


``` java 
public interface Functor<F> {
  <A, B> @NonNull Kind<F, B> map(final Function<? super A, ? extends B> f, final Kind<F, A> fa);
}
```

* `f`: The function to apply to the value inside the Functor.
* `fa`: The higher-kinded `Functor` instance (e.g., a `Kind<Optional.Witness, String>`).

---

### The Functor Laws

For a `Functor` implementation to be lawful, it must obey two simple rules. These ensure that the `map` operation is predictable and doesn't have unexpected side effects.

1. **Identity Law**: Mapping with the identity function (`x -> x`) should change nothing.

   ``` java
   functor.map(x -> x, fa); // This must be equivalent to fa
   ```
2. **Composition Law**: Mapping with two functions composed together is the same as mapping with each function one after the other.

   ``` java
   Function<A, B> f = ...;
   Function<B, C> g = ...;

   // This...
   functor.map(g.compose(f), fa);

   // ...must be equivalent to this:
   functor.map(g, functor.map(f, fa));
   ```

These laws ensure that `map` is only about transformation and preserves the structure of the data type.

---

### Why is it useful?

`Functor` allows you to write generic, reusable code that transforms values inside any "mappable" data structure. This is the first step toward abstracting away the boilerplate of dealing with different container types.

**Example: Mapping over an `Optional` and a `List`**

Let's see how we can use the `Functor` instances for `Optional` and `List` to apply the same logic to different data structures.



``` java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListFunctor;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.optional.OptionalFunctor;
import org.higherkindedj.hkt.optional.OptionalKind;
import java.util.List;
import java.util.Optional;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;


// Our function that we want to apply
Function<String, Integer> stringLength = String::length;

// --- Scenario 1: Mapping over an Optional ---
Functor<OptionalKind.Witness> optionalFunctor = OptionalFunctor.INSTANCE;

// The data
Kind<OptionalKind.Witness, String> optionalWithValue = OPTIONAL.widen(Optional.of("Hello"));
Kind<OptionalKind.Witness, String> optionalEmpty = OPTIONAL.widen(Optional.empty());

// Apply the map
Kind<OptionalKind.Witness, Integer> lengthWithValue = optionalFunctor.map(stringLength, optionalWithValue);
Kind<OptionalKind.Witness, Integer> lengthEmpty = optionalFunctor.map(stringLength, optionalEmpty);

// Result: Optional[5]
System.out.println(OPTIONAL.narrow(lengthWithValue));
// Result: Optional.empty
System.out.println(OPTIONAL.narrow(lengthEmpty));


// --- Scenario 2: Mapping over a List ---
Functor<ListKind.Witness> listFunctor = ListFunctor.INSTANCE;

// The data
Kind<ListKind.Witness, String> listOfStrings = LIST.widen(List.of("one", "two", "three"));

// Apply the map
Kind<ListKind.Witness, Integer> listOfLengths = listFunctor.map(stringLength, listOfStrings);

// Result: [3, 3, 5]
System.out.println(LIST.narrow(listOfLengths));
```

As you can see, the `Functor` provides a consistent API for transformation, regardless of the underlying data structure. This is the first and most essential step on the path to more powerful abstractions like `Applicative` and `Monad`.
