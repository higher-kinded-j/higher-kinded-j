# List - Monadic Operations on Java Lists

## Purpose

The `ListMonad` in the `Higher-Kinded-J` library provides a monadic interface for Java's standard `java.util.List`. It allows developers to work with lists in a more functional style, enabling operations like `map`, `flatMap`, and `ap` (apply) within the higher-kinded type system. This is particularly useful for sequencing operations that produce lists, transforming list elements, and applying functions within a list context, all while integrating with the generic `Kind<F, A>` abstractions.

Key benefits include:

* **Functional Composition:** Easily chain operations on lists, where each operation might return a list itself.
* **HKT Integration:** `ListKind` (the higher-kinded wrapper for `List`) and `ListMonad` allow `List` to be used with generic functions and type classes expecting `Kind<F, A>`, `Functor<F>`, `Applicative<F>`, or `Monad<F>`.
* **Standard List Behavior:** Leverages the familiar behavior of Java lists, such as non-uniqueness of elements and order preservation. `flatMap` corresponds to applying a function that returns a list to each element and then concatenating the results.

It implements `Monad<ListKind<?>>`, inheriting from `Functor<ListKind<?>>` and `Applicative<ListKind<?>>`.

## Structure

![list_monad.svg](./images/puml/list_monad.svg)


## How to Use `ListMonad` and `ListKind`

### Creating Instances

`ListKind<A>` is the higher-kinded type representation for `java.util.List<A>`. You typically create `ListKind` instances using the `ListKindHelper` utility class or the `of` method from `ListMonad`.

1. **`ListKindHelper.wrap(List<A>)`:** Converts a standard `java.util.List<A>` into a `ListKind<A>`.

   ```java
   import org.higherkindedj.hkt.list.ListKind;
   import org.higherkindedj.hkt.list.ListKindHelper;
   import java.util.Arrays;
   import java.util.Collections;
   import java.util.List;

   List<String> stringList = Arrays.asList("a", "b", "c");
   ListKind<String> listKind1 = ListKindHelper.wrap(stringList);

   List<Integer> intList = Collections.singletonList(10);
   ListKind<Integer> listKind2 = ListKindHelper.wrap(intList);

   List<Object> emptyList = Collections.emptyList();
   ListKind<Object> listKindEmpty = ListKindHelper.wrap(emptyList);
   ```
2. **`listMonad.of(A value)`:** Lifts a single value into the `ListKind` context, creating a singleton list. A `null` input value results in an empty `ListKind`.

   ```java
   import org.higherkindedj.hkt.list.ListMonad;
   import org.higherkindedj.hkt.list.ListKind;

   ListMonad listMonad = new ListMonad();

   ListKind<String> listKindOneItem = listMonad.of("hello"); // Contains a list with one element: "hello"
   ListKind<Integer> listKindAnotherItem = listMonad.of(42);  // Contains a list with one element: 42
   ListKind<Object> listKindFromNull = listMonad.of(null); // Contains an empty list
   ```

### Unwrapping `ListKind`

To get the underlying `java.util.List<A>` from a `ListKind<A>`, use `ListKindHelper.unwrap()`:

```java
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import java.util.List;
import java.util.Arrays;

ListKind<String> listKind = ListKindHelper.wrap(Arrays.asList("example"));
List<String> unwrappedList = ListKindHelper.unwrap(listKind); // Returns Arrays.asList("example")
System.out.println(unwrappedList);
```

### Key Operations

The `ListMonad` provides standard monadic operations:

* **`map(Function<A, B> f, Kind<ListKind<?>, A> fa)`:** Applies a function `f` to each element of the list within `fa`, returning a new `ListKind` containing the transformed elements.

```java
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

ListMonad listMonad = new ListMonad();
ListKind<Integer> numbers = ListKindHelper.wrap(Arrays.asList(1, 2, 3));

Function<Integer, String> intToString = i -> "Number: " + i;
ListKind<String> strings = listMonad.map(intToString, numbers);

// ListKindHelper.unwrap(strings) would be: ["Number: 1", "Number: 2", "Number: 3"]
System.out.println(ListKindHelper.unwrap(strings));
```

* **`flatMap(Function<A, Kind<ListKind<?>, B>> f, Kind<ListKind<?>, A> ma)`:** Applies a function `f` to each element of the list within `ma`. The function `f` itself returns a `ListKind<B>`. `flatMap` then concatenates (flattens) all these resulting lists into a single `ListKind<B>`.

```java
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.Kind;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.ArrayList;

ListMonad listMonad = new ListMonad();
ListKind<Integer> initialValues = ListKindHelper.wrap(Arrays.asList(1, 2, 3));

// Function that takes an integer and returns a list of itself and itself + 10
Function<Integer, Kind<ListKind<?>, Integer>> replicateAndAddTen =
    i -> ListKindHelper.wrap(Arrays.asList(i, i + 10));

ListKind<Integer> flattenedList = listMonad.flatMap(replicateAndAddTen, initialValues);

// ListKindHelper.unwrap(flattenedList) would be: [1, 11, 2, 12, 3, 13]
System.out.println(ListKindHelper.unwrap(flattenedList));

// Example with empty list results
Function<Integer, Kind<ListKind<?>, String>> toWordsIfEven =
    i -> (i % 2 == 0) ?
         ListKindHelper.wrap(Arrays.asList("even", String.valueOf(i))) :
         ListKindHelper.wrap(new ArrayList<>()); // empty list for odd numbers

ListKind<String> wordsList = listMonad.flatMap(toWordsIfEven, initialValues);
// ListKindHelper.unwrap(wordsList) would be: ["even", "2"]
 System.out.println(ListKindHelper.unwrap(wordsList));
```

* **`ap(Kind<ListKind<?>, Function<A, B>> ff, Kind<ListKind<?>, A> fa)`:** Applies a list of functions `ff` to a list of values `fa`. This results in a new list where each function from `ff` is applied to each value in `fa` (Cartesian product style).

```java
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.Kind;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

ListMonad listMonad = new ListMonad();

Function<Integer, String> addPrefix = i -> "Val: " + i;
Function<Integer, String> multiplyAndString = i -> "Mul: " + (i * 2);

ListKind<Function<Integer, String>> functions =
    ListKindHelper.wrap(Arrays.asList(addPrefix, multiplyAndString));
ListKind<Integer> values = ListKindHelper.wrap(Arrays.asList(10, 20));

ListKind<String> appliedResults = listMonad.ap(functions, values);

// ListKindHelper.unwrap(appliedResults) would be:
// ["Val: 10", "Val: 20", "Mul: 20", "Mul: 40"]
System.out.println(ListKindHelper.unwrap(appliedResults));
```

### Using `ListMonad` with Higher-Kinded-J (Generic Code)

To use `ListMonad` in generic contexts that operate over `Kind<F, A>`:

1. **Get an instance of `ListMonad`:**

```java
ListMonad listMonad = new ListMonad();
```

2. **Wrap your List into `ListKind`:**

```java
List<Integer> myList = Arrays.asList(10, 20, 30);
Kind<ListKind<?>, Integer> listKind = ListKindHelper.wrap(myList);
```

3. **Use `ListMonad` methods:**

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListMonad;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListMonadExample {
    public static void main(String[] args) {
        ListMonad listMonad = new ListMonad();

        // 1. Create a ListKind
        Kind<ListKind<?>, Integer> numbersKind = ListKindHelper.wrap(Arrays.asList(1, 2, 3, 4));

        // 2. Use map
        Function<Integer, String> numberToDecoratedString = n -> "*" + n + "*";
        Kind<ListKind<?>, String> stringsKind = listMonad.map(numberToDecoratedString, numbersKind);
        System.out.println("Mapped: " + ListKindHelper.unwrap(stringsKind));
        // Expected: Mapped: [*1*, *2*, *3*, *4*]

        // 3. Use flatMap
        // Function: integer -> ListKind of [integer, integer*10] if even, else empty ListKind
        Function<Integer, Kind<ListKind<?>, Integer>> duplicateIfEven = n -> {
            if (n % 2 == 0) {
                return ListKindHelper.wrap(Arrays.asList(n, n * 10));
            } else {
                return ListKindHelper.wrap(List.of()); // Empty list
            }
        };
        Kind<ListKind<?>, Integer> flatMappedKind = listMonad.flatMap(duplicateIfEven, numbersKind);
        System.out.println("FlatMapped: " + ListKindHelper.unwrap(flatMappedKind));
        // Expected: FlatMapped: [2, 20, 4, 40]

        // 4. Use of
        Kind<ListKind<?>, String> singleValueKind = listMonad.of("hello world");
        System.out.println("From 'of': " + ListKindHelper.unwrap(singleValueKind));
        // Expected: From 'of': [hello world]

        Kind<ListKind<?>, String> fromNullOf = listMonad.of(null);
         System.out.println("From 'of' with null: " + ListKindHelper.unwrap(fromNullOf));
        // Expected: From 'of' with null: []


        // 5. Use ap
        ListKind<Function<Integer, String>> listOfFunctions =
            ListKindHelper.wrap(Arrays.asList(
                i -> "F1:" + i,
                i -> "F2:" + (i*i)
            ));
        ListKind<Integer> inputNumbersForAp = ListKindHelper.wrap(Arrays.asList(5, 6));

        Kind<ListKind<?>, String> apResult = listMonad.ap(listOfFunctions, inputNumbersForAp);
        System.out.println("Ap result: " + ListKindHelper.unwrap(apResult));
        // Expected: Ap result: [F1:5, F1:6, F2:25, F2:36]


        // Unwrap to get back the standard List
        List<Integer> finalFlatMappedList = ListKindHelper.unwrap(flatMappedKind);
        System.out.println("Final unwrapped flatMapped list: " + finalFlatMappedList);
    }
}
```

This example demonstrates how to wrap Java Lists into `ListKind`, apply monadic operations using `ListMonad`, and then unwrap them back to standard Lists.
