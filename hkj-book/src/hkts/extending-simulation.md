# Extending Higher Kinded Type Simulation

![extending-crane.png](../images/extending-crane.png)

You can add support for new Java types (type constructors) to the Higher-Kinded-J simulation framework, allowing them to be used with type classes like `Functor`, `Monad`, etc.

There are two main scenarios:

1. **Adapting External Types**: For types you don't own (e.g., JDK classes like `java.util.Set`, `java.util.Map`, or classes from other libraries).
2. **Integrating Custom Library Types**: For types defined within your own project or a library you control, where you can modify the type itself.

> **Note:** Within Higher-Kinded-J, core library types like `IO`, `Maybe`, and `Either` follow Scenario 2—they directly implement their respective Kind interfaces (`IOKind`, `MaybeKind`, `EitherKind`). This provides zero runtime overhead for widen/narrow operations.

The core pattern involves creating:

* An `XxxKind` interface with a nested `Witness` type (this remains the same).
* An `XxxConverterOps` interface defining the `widen` and `narrow` operations for the specific type.
* An `XxxKindHelper` **enum** that implements `XxxConverterOps` and provides a singleton instance (e.g., `SET`, `MY_TYPE`) for accessing these operations as instance methods.
* Type class instances (e.g., for `Functor`, `Monad`).

For external types, an additional `XxxHolder` record is typically used internally by the helper enum to wrap the external type.

## Scenario 1: Adapting an External Type (e.g., `java.util.Set<A>`)

Since we cannot modify `java.util.Set` to directly implement our `Kind` structure, we need a wrapper (a `Holder`).

**Goal:** Simulate `java.util.Set<A>` as `Kind<SetKind.Witness, A>` and provide `Functor`, `Applicative`, and `Monad` instances for it.

**Note:** This pattern is useful when integrating third-party libraries or JDK types that you cannot modify directly.

~~~admonish


1.  **Create the `Kind` Interface with Witness (`SetKind.java`)**:
    * Define a marker interface that extends `Kind<SetKind.Witness, A>`.
    * Inside this interface, define a `static final class Witness {}` which will serve as the phantom type `F` for `Set`.

    ```java
    package org.higherkindedj.hkt.set; // Example package

    import org.higherkindedj.hkt.Kind;
    import org.jspecify.annotations.NullMarked;

    /**
     * Kind interface marker for java.util.Set<A>.
     * The Witness type F = SetKind.Witness
     * The Value type A = A
     */
    @NullMarked
    public interface SetKind<A> extends Kind<SetKind.Witness, A> {
      /**
       * Witness type for {@link java.util.Set} to be used with {@link Kind}.
       */
      final class Witness {
        private Witness() {} 
      }
    }
    ```

2.  **Create the `ConverterOps` Interface (`SetConverterOps.java`)**: 
    *   Define an interface specifying the `widen` and `narrow` methods for `Set`.

    ``` java
    package org.higherkindedj.hkt.set;

    import java.util.Set;
    import org.higherkindedj.hkt.Kind;
    import org.higherkindedj.hkt.exception.KindUnwrapException; // If narrow throws it
    import org.jspecify.annotations.NonNull;
    import org.jspecify.annotations.Nullable;

    public interface SetConverterOps {
      <A> @NonNull Kind<SetKind.Witness, A> widen(@NonNull Set<A> set);
      <A> @NonNull Set<A> narrow(@Nullable Kind<SetKind.Witness, A> kind) throws KindUnwrapException;
    }
    ```

3.  **Create the `KindHelper` Enum with an Internal `Holder` (`SetKindHelper.java`)**:
    * Define an `enum` (e.g., `SetKindHelper`) that implements `SetConverterOps`.
    * Provide a singleton instance (e.g., `SET`).
    * Inside this helper, define a package-private `record SetHolder<A>(@NonNull Set<A> set) implements SetKind<A> {}`. This record wraps the actual `java.util.Set`.
    * **`widen` method**: Takes the Java type (e.g., `Set<A>`), performs null checks, and returns a new `SetHolder<>(set)` cast to `Kind<SetKind.Witness, A>`.
    * **`narrow` method**: Takes `Kind<SetKind.Witness, A> kind`, performs null checks, verifies `kind instanceof SetHolder`, extracts the underlying `Set<A>`, and returns it. It throws `KindUnwrapException` for any structural invalidity.

    ```java
    package org.higherkindedj.hkt.set;

    import java.util.Objects;
    import java.util.Set;
    import org.higherkindedj.hkt.Kind;
    import org.higherkindedj.hkt.exception.KindUnwrapException;
    import org.jspecify.annotations.NonNull;
    import org.jspecify.annotations.Nullable;

    public enum SetKindHelper implements SetConverterOps {
        SET; // Singleton instance

        // Error messages can be static final within the enum
        private static final String ERR_INVALID_KIND_NULL = "Cannot narrow null Kind for Set";
        private static final String ERR_INVALID_KIND_TYPE = "Kind instance is not a SetHolder: ";
        private static final String ERR_INVALID_KIND_TYPE_NULL = "Input Set cannot be null for widen";
      
        // Holder Record (package-private for testability if needed)
        record SetHolder<AVal>(@NonNull Set<AVal> set) implements SetKind<AVal> { }

        @Override
        public <A> @NonNull Kind<SetKind.Witness, A> widen(@NonNull Set<A> set) {
            Objects.requireNonNull(set, ERR_INVALID_KIND_TYPE_NULL);
            return  new SetHolder<>(set);
        }

        @Override
        public <A> @NonNull Set<A> narrow(@Nullable Kind<SetKind.Witness, A> kind) {
            if (kind == null) {
                throw new KindUnwrapException(ERR_INVALID_KIND_NULL);
            }
            if (kind instanceof SetHolder<?> holder) { 
                // SetHolder's 'set' component is @NonNull, so holder.set() is guaranteed non-null.
                return (Set<A>) holder.set();
            } else {
                throw new KindUnwrapException(ERR_INVALID_KIND_TYPE + kind.getClass().getName());
            }
        }
    }
    ```
~~~


## Scenario 2: Integrating a Custom Library Type

If you are defining a new type *within your library* (e.g., a custom `MyType<A>`), you can design it to directly participate in the HKT simulation. This approach typically doesn't require an explicit `Holder` record if your type can directly implement the `XxxKind` interface.

> **Examples in Higher-Kinded-J:** `IO<A>`, `Maybe<A>` (via `Just<T>` and `Nothing<T>`), `Either<L,R>` (via `Left` and `Right`), `Validated<E,A>`, `Id<A>`, and monad transformers all use this pattern. Their widen/narrow operations are simple type-safe casts with no wrapper object allocation.

~~~admonish

1.  **Define Your Type and its `Kind` Interface**:
    * Your custom type (e.g., `MyType<A>`) directly implements its corresponding `MyTypeKind<A>` interface.
    * `MyTypeKind<A>` extends `Kind<MyType.Witness, A>` and defines the nested `Witness` class. (This part remains unchanged).

    ```java
    package org.example.mytype;

    import org.higherkindedj.hkt.Kind;
    import org.jspecify.annotations.NullMarked;

    // 1. The Kind Interface with Witness
    @NullMarked
    public interface MyTypeKind<A> extends Kind<MyType.Witness, A> {
      /** Witness type for MyType. */
      final class Witness { private Witness() {} }
    }

    // 2. Your Custom Type directly implements its Kind interface
    public record MyType<A>(A value) implements MyTypeKind<A> {
        // ... constructors, methods for MyType ...
    }
    ```

2.  **Create the `ConverterOps` Interface (`MyTypeConverterOps.java`)**:
    * Define an interface specifying the `widen` and `narrow` methods for `MyType`.

    ```java
    package org.example.mytype;

    import org.higherkindedj.hkt.Kind;
    import org.higherkindedj.hkt.exception.KindUnwrapException;
    import org.jspecify.annotations.NonNull;
    import org.jspecify.annotations.Nullable;

    public interface MyTypeConverterOps {
        <A> @NonNull Kind<MyType.Witness, A> widen(@NonNull MyType<A> myTypeValue);
        <A> @NonNull MyType<A> narrow(@Nullable Kind<MyType.Witness, A> kind) throws KindUnwrapException;
    }
    ```

3.  **Create the `KindHelper` Enum (`MyTypeKindHelper.java`)**:
    * Define an `enum` (e.g., `MyTypeKindHelper`) that implements `MyTypeConverterOps`.
    * Provide a singleton instance (e.g., `MY_TYPE`).
    * **`widen(MyType<A> myTypeValue)`**: Since `MyType<A>` *is* already a `MyTypeKind<A>` (and thus a `Kind`), this method performs a null check and then a direct cast.
    * **`narrow(Kind<MyType.Witness, A> kind)`**: This method checks `if (kind instanceof MyType<?> myTypeInstance)` and then casts and returns `myTypeInstance`.

    ```java
    package org.example.mytype;

    import org.higherkindedj.hkt.Kind;
    import org.higherkindedj.hkt.exception.KindUnwrapException;
    import org.jspecify.annotations.NonNull;
    import org.jspecify.annotations.Nullable;
    import java.util.Objects;

    public enum MyTypeKindHelper implements MyTypeConverterOps {
        MY_TYPE; // Singleton instance

        private static final String ERR_INVALID_KIND_NULL = "Cannot narrow null Kind for MyType";
        private static final String ERR_INVALID_KIND_TYPE = "Kind instance is not a MyType: ";

        @Override
        @SuppressWarnings("unchecked") // MyType<A> is MyTypeKind<A> is Kind<MyType.Witness, A>
        public <A> @NonNull Kind<MyType.Witness, A> widen(@NonNull MyType<A> myTypeValue) {
            Objects.requireNonNull(myTypeValue, "Input MyType cannot be null for widen");
            return (MyTypeKind<A>) myTypeValue; // Direct cast
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A> @NonNull MyType<A> narrow(@Nullable Kind<MyType.Witness, A> kind) {
            if (kind == null) {
                throw new KindUnwrapException(ERR_INVALID_KIND_NULL);
            }
            // Check if it's an instance of your actual type
            if (kind instanceof MyType<?> myTypeInstance) { // Pattern match for MyType
                return (MyType<A>) myTypeInstance; // Direct cast
            } else {
                throw new KindUnwrapException(ERR_INVALID_KIND_TYPE + kind.getClass().getName());
            }
        }
    }
    ```

4.  **Implement Type Class Instances**:
    * These will be similar to the external type scenario (e.g., `MyTypeMonad implements Monad<MyType.Witness>`), using `MyTypeKindHelper.MY_TYPE.widen(...)` and `MyTypeKindHelper.MY_TYPE.narrow(...)` (or with static import `MY_TYPE.widen(...)`).
~~~

~~~admonish

* **Immutability**: Favour immutable data structures for your `Holder` or custom type if possible, as this aligns well with functional programming principles.
* **Null Handling**: Be very clear about null handling. Can the wrapped Java type be null? Can the value `A` inside be null? `KindHelper`'s `widen` method should typically reject a null container itself. `Monad.of(null)` behaviour depends on the specific monad (e.g., `OptionalMonad.OPTIONAL_MONAD.of(null)` is empty via `OPTIONAL.widen(Optional.empty())`, `ListMonad.LIST_MONAD.of(null)` might be an empty list or a list with a null element based on its definition).
* **Testing**: Thoroughly test your `XxxKindHelper` enum (especially `narrow` with invalid inputs) and your type class instances (Functor, Applicative, Monad laws).

By following these patterns, you can integrate new or existing types into the Higher-Kinded-J framework, enabling them to be used with generic functional abstractions. The `KindHelper` enums, along with their corresponding `ConverterOps` interfaces, provide a standardised way to handle the `widen` and `narrow` conversions.
~~~
