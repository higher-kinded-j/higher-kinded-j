# Usage Guide: Working with Higher-Kinded-J

![oa-movements.png](../images/oa-movements.png)

~~~admonish info title="What You'll Learn"
- The five-step workflow for using Higher-Kinded-J effectively
- How to identify the right context (witness type) for your use case
- Using widen() and narrow() to convert between Java types and Kind representations
- When and how to handle KindUnwrapException safely
- Writing generic functions that work with any Functor or Monad
~~~

This guide explains the step-by-step process of using Higher-Kinded-J's simulated Higher-Kinded Types (HKTs) and associated type classes like `Functor`, `Applicative`, `Monad`, and `MonadError`.

- [GenericExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/GenericExample.java)

## Core Workflow

The general process involves these steps:

~~~admonish title="Step 1: Identify the Context (_F_WITNESS_)"

Determine which type constructor (computational context) you want to work with abstractly. This context is represented by its *witness type*. 

Examples:

* `ListKind.Witness` for `java.util.List`
* `OptionalKind.Witness` for `java.util.Optional`
* `MaybeKind.Witness` for the custom `Maybe` type
* `EitherKind.Witness<L>` for the custom `Either<L, R>` type (where `L` is fixed)
* `TryKind.Witness` for the custom `Try` type
* `CompletableFutureKind.Witness` for `java.util.concurrent.CompletableFuture`
* `IOKind.Witness` for the custom `IO` type
* `LazyKind.Witness` for the custom `Lazy` type
* `ReaderKind.Witness<R_ENV>` for the custom `Reader<R_ENV, A>` type
* `StateKind.Witness<S>` for the custom `State<S, A>` type
* `WriterKind.Witness<W>` for the custom `Writer<W, A>` type
* For transformers, e.g., `EitherTKind.Witness<F_OUTER_WITNESS, L_ERROR>`
~~~

~~~admonish title="Step 2: Find the Type Class Instance"

Obtain an instance of the required type class (`Functor<F_WITNESS>`, `Applicative<F_WITNESS>`, `Monad<F_WITNESS>`, `MonadError<F_WITNESS, E>`) for your chosen context's witness type `F_WITNESS`. 

These are concrete classes provided in the corresponding package.

Examples:

* **`Optional`**: 
`OptionalMonad optionalMonad = OptionalMonad.INSTANCE;` (This implements `MonadError<OptionalKind.Witness, Unit>`)
* **`List`**: `ListMonad listMonad = ListMonad.INSTANCE;` (This implements `Monad<ListKind.Witness>`)
* **`CompletableFuture`**: `CompletableFutureMonad futureMonad = CompletableFutureMonad.INSTANCE;` (This implements `MonadError<CompletableFutureKind.Witness, Throwable>`)
* **`Either<String, ?>`**: `EitherMonad<String> eitherMonad =  EitherMonad.instance();` (This implements `MonadError<EitherKind.Witness<String>, String>`)
* **`IO`**: `IOMonad ioMonad = IOMonad.INSTANCE;` (This implements `Monad<IOKind.Witness>`)
* **`Writer<String, ?>`**: `WriterMonad<String> writerMonad = new WriterMonad<>(new StringMonoid());` (This implements `Monad<WriterKind.Witness<String>>`)
~~~

~~~admonish title="Step 3: Wrap Your Value (_JavaType<A>_ -> _Kind<F_WITNESS, A>_)"


Convert your standard Java object (e.g., a `List<Integer>`, an `Optional<String>`, an `IO<String>`) into Higher-Kinded-J's `Kind` representation using the `widen` instance method from the corresponding `XxxKindHelper` enum's singleton instance. You'll typically use a static import for the singleton instance for brevity.

   ```java
    import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL; 
    // ...
    Optional<String> myOptional = Optional.of("test");
    // Widen it into the Higher-Kinded-J Kind type
    // F_WITNESS here is OptionalKind.Witness
    Kind<OptionalKind.Witness, String> optionalKind = OPTIONAL.widen(myOptional);
   ```

* Helper enums provide convenience factory methods that also return `Kind` instances, e.g., `MAYBE.just("value")`, `TRY.failure(ex)`, `IO_OP.delay(() -> ...)`, `LAZY.defer(() -> ...)`. Remember to import thes statically from the XxxKindHelper classes.
* **Note on Widening**:
  * For JDK types (like `List`, `Optional`), `widen` typically creates an internal `Holder` object that wraps the JDK type and implements the necessary `XxxKind` interface.
  * For library-defined types (`Id`, `IO`, `Maybe`, `Either`, `Validated`, Transformers like `EitherT`) that directly implement their `XxxKind` interface (which in turn extends `Kind`), the `widen` method on the helper enum performs a null check and then a direct (and safe) cast to the `Kind` type. This provides zero runtime overhead: no wrapper object allocation needed.
~~~

~~~admonish title="Step 4: Apply Type Class Methods"

Use the methods defined by the type class interface (`map`, `flatMap`, `of`, `ap`, `raiseError`, `handleErrorWith`, etc.) by calling them on the **type class instance** obtained in *Step 2*, passing your `Kind` value(s) as arguments. **Do not call `map`/`flatMap` directly on the `Kind` object itself if it's just the `Kind` interface.** (Some concrete `Kind` implementations like `Id` or `Maybe` might offer direct methods, but for generic programming, use the type class instance).

   ```java
    import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
    // ...
    OptionalMonad optionalMonad = OptionalMonad.INSTANCE;
    Kind<OptionalKind.Witness, String> optionalKind = OPTIONAL.widen(Optional.of("test")); // from previous step

    // --- Using map ---
    Function<String, Integer> lengthFunc = String::length;
    // Apply map using the monad instance
    Kind<OptionalKind.Witness, Integer> lengthKind = optionalMonad.map(lengthFunc, optionalKind);
    // lengthKind now represents Kind<OptionalKind.Witness, Integer> containing Optional.of(4)

    // --- Using flatMap ---
    // Function A -> Kind<F_WITNESS, B>
    Function<Integer, Kind<OptionalKind.Witness, String>> checkLength =
        len -> OPTIONAL.widen(len > 3 ? Optional.of("Long enough") : Optional.empty());
    // Apply flatMap using the monad instance
    Kind<OptionalKind.Witness, String> checkedKind = optionalMonad.flatMap(checkLength, lengthKind);
    // checkedKind now represents Kind<OptionalKind.Witness, String> containing Optional.of("Long enough")

    // --- Using MonadError (for Optional, error type is Unit) ---
    Kind<OptionalKind.Witness, String> emptyKind = optionalMonad.raiseError(Unit.INSTANCE); // Represents Optional.empty()
    // Handle the empty case (error state) using handleErrorWith
    Kind<OptionalKind.Witness, String> handledKind = optionalMonad.handleErrorWith(
        emptyKind,
        ignoredError -> OPTIONAL.widen(Optional.of("Default Value")) // Ensure recovery function also returns a Kind
    );
   
   ```

**Note**: For complex chains of monadic operations, consider using [For Comprehensions](../functional/for_comprehension.md) which provide more readable syntax than nested `flatMap` calls.
~~~
~~~admonish title="Step 5: Unwrap/Narrow the Result (_Kind<F_WITNESS, A> -> JavaType<A>_)"

When you need the underlying Java value back (e.g., to return from a method boundary, perform side effects like printing or running `IO`), use the `narrow` instance method from the corresponding `XxxKindHelper` enum's singleton instance.
 
    ```java
    import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL; 
    import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP; 
 
    // ...
    // Continuing the Optional example:
     Kind<OptionalKind.Witness, String> checkedKind = /* from previous step */;
     Kind<OptionalKind.Witness, String> handledKind = /* from previous step */;
 
     Optional<String> finalOptional = OPTIONAL.narrow(checkedKind);
     System.out.println("Final Optional: " + finalOptional); 
     // Output: Optional[Long enough]
 
     Optional<String> handledOptional = OPTIONAL.narrow(handledKind);
     System.out.println("Handled Optional: " + handledOptional); 
     // Output: Optional[Default Value]
 
     // Example for IO:
      IOMonad ioMonad = IOMonad.INSTANCE;
      Kind<IOKind.Witness, String> ioKind = IO_OP.delay(() -> "Hello from IO!"); 
      // Use IO_OP.delay
      // unsafeRunSync is an instance method on IOKindHelper.IO_OP
      String ioResult = IO_OP.unsafeRunSync(ioKind);
      System.out.println(ioResult);
    ```   
~~~

-----

~~~admonish title="Handling _KindUnwrapException_"


The `narrow` instance methods in all `KindHelper` enums are designed to be robust against *structural* errors within the HKT simulation layer.

* **When it's thrown**: If you pass `null` to `narrow`. For external types using a `Holder` (like `Optional` with `OptionalHolder`), if the `Kind` instance is not the expected `Holder` type, an exception is also thrown. For types that directly implement their `XxxKind` interface, `narrow` will throw if the `Kind` is not an instance of that specific concrete type.
* **What it means**: This exception signals a problem with how you are using Higher-Kinded-J itself – usually a programming error in creating or passing `Kind` objects.
* **How to handle**: You generally **should not** need to catch `KindUnwrapException` in typical application logic. Its occurrence points to a bug that needs fixing in the code using Higher-Kinded-J.

```java
  // import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
  public void handlingUnwrapExceptions() {
    try {
      // ERROR: Attempting to narrow null
      Optional<String> result = OPTIONAL.narrow(null);
    } catch(KindUnwrapException e) {
      System.err.println("Higher-Kinded-J Usage Error: " + e.getMessage());
      // Example Output (message from OptionalKindHelper.INVALID_KIND_NULL_MSG):
      // Usage Error: Cannot narrow null Kind for Optional
    }
  }
```

**Important Distinction:**

* **`KindUnwrapException`**: Signals a problem with the Higher-Kinded-J structure itself (e.g., invalid `Kind` object passed to `narrow`). Fix the code using Higher-Kinded-J.
* **Domain Errors / Absence**: Represented *within* a valid `Kind` structure (e.g., `Optional.empty()` widened to `Kind<OptionalKind.Witness, A>`, `Either.Left` widened to `Kind<EitherKind.Witness<L>, R>`). These should be handled using the monad's specific methods (`orElse`, `fold`, `handleErrorWith`, etc.) or by using the `MonadError` methods *before* narrowing back to the final Java type.
~~~

~~~admonish example title="Example: Generic Function"

- [GenericExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/GenericExample.java)

Higher-Kinded-J allows writing functions generic over the simulated type constructor (represented by its witness `F_WITNESS`).

```java
// import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
// import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
// ...

// Generic function: Applies a function within any Functor context F_WITNESS.
// Requires the specific Functor<F_WITNESS> instance to be passed in.
public static <F_WITNESS, A, B> Kind<F_WITNESS, B> mapWithFunctor(
    Functor<F_WITNESS> functorInstance, // Pass the type class instance for F_WITNESS
    Function<A, B> fn,
    Kind<F_WITNESS, A> kindABox) { 

  // Use the map method from the provided Functor instance
  return functorInstance.map(fn, kindABox);
}

public void genericExample() { 
  // Get instances of the type classes for the specific types (F_WITNESS) we want to use
  ListMonad listMonad = new ListMonad(); // Implements Functor<ListKind.Witness>
  OptionalMonad optionalMonad = OptionalMonad.INSTANCE; // Implements Functor<OptionalKind.Witness>

  Function<Integer, Integer> doubleFn = x -> x * 2;

  // --- Use with List ---
  List<Integer> nums = List.of(1, 2, 3);
  // Widen the List. F_WITNESS is ListKind.Witness
  Kind<ListKind.Witness, Integer> listKind = LIST.widen(nums); 
  // Call the generic function, passing the ListMonad instance and the widened List
  Kind<ListKind.Witness, Integer> doubledListKind = mapWithFunctor(listMonad, doubleFn, listKind);
  System.out.println("Doubled List: " + LIST.narrow(doubledListKind)); // Output: [2, 4, 6]

  // --- Use with Optional (Present) ---
  Optional<Integer> optNum = Optional.of(10);
  // Widen the Optional. F_WITNESS is OptionalKind.Witness
  Kind<OptionalKind.Witness, Integer> optKind = OPTIONAL.widen(optNum); 
  // Call the generic function, passing the OptionalMonad instance and the widened Optional
  Kind<OptionalKind.Witness, Integer> doubledOptKind = mapWithFunctor(optionalMonad, doubleFn, optKind);
  System.out.println("Doubled Optional: " + OPTIONAL.narrow(doubledOptKind)); // Output: Optional[20]

  // --- Use with Optional (Empty) ---
  Optional<Integer> emptyOpt = Optional.empty();
  Kind<OptionalKind.Witness, Integer> emptyOptKind = OPTIONAL.widen(emptyOpt); 
  // Call the generic function, map does nothing on empty
  Kind<OptionalKind.Witness, Integer> doubledEmptyOptKind = mapWithFunctor(optionalMonad, doubleFn, emptyOptKind);
  System.out.println("Doubled Empty Optional: " + OPTIONAL.narrow(doubledEmptyOptKind)); // Output: Optional.empty
}

```

---

**Previous:** [Concepts](core-concepts.md)
**Next:** [Basic HKT Examples](hkt_basic_examples.md)