# Extending Higher Kinded Type Simulation

You can add support for new Java types (type constructors) to the Higher-Kinded-J simulation framework, allowing them to be used with type classes like `Functor`, `Monad`, etc.

There are two main scenarios:

1.  **Adapting External Types**: For types you don't own (e.g., JDK classes like `java.util.Set`, `java.util.Map`, or classes from other libraries).
2.  **Integrating Custom Library Types**: For types defined within your own project or a library you control, where you can modify the type itself.

The core pattern involves creating a `XxxKind` interface with a nested `Witness` type, an `XxxKindHelper` class, and type class instances. For external types, an additional `XxxHolder` record is typically used internally by the helper.

## Scenario 1: Adapting an External Type (e.g., `java.util.Set<A>`)

Since we cannot modify `java.util.Set` to directly implement our `Kind` structure, we need a wrapper (a `Holder`).

**Goal:** Simulate `java.util.Set<A>` as `Kind<SetKind.Witness, A>` and provide `Functor`, `Applicative`, and `Monad` instances for it.

~~~admonish example title="Adapting an External Type"


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
        private Witness() {} // Prevents instantiation
      }
    }
    ```

2.  **Create the `KindHelper` Class with an Internal `Holder` (`SetKindHelper.java`)**:
    * Define a `final` class (e.g., `SetKindHelper`).
    * Inside this helper, define a package-private or private `record SetHolder<A>(@NonNull Set<A> set) implements SetKind<A> {}`. This record wraps the actual `java.util.Set` and implements your `SetKind<A>` interface.
    * **`wrap` method**: Takes the Java type (e.g., `Set<A>`), performs null checks, and returns a new `SetHolder<>(set)` cast to `Kind<SetKind.Witness, A>`.
    * **`unwrap` method**: Takes `Kind<SetKind.Witness, A> kind`, performs null checks, verifies `kind instanceof SetHolder`, extracts the underlying `Set<A>`, and returns it. It throws `KindUnwrapException` for any structural invalidity (null kind, wrong holder type, null internal set if that's disallowed).

    ```java
    package org.higherkindedj.hkt.set;

    import org.jspecify.annotations.NonNull;
    import org.jspecify.annotations.Nullable;
    import org.higherkindedj.hkt.Kind;
    import org.higherkindedj.hkt.exception.KindUnwrapException;

    import java.util.Objects;
    import java.util.Set;

    public final class SetKindHelper {

        private static final String ERR_INVALID_KIND_NULL = "Cannot unwrap null Kind for Set";
        private static final String ERR_INVALID_KIND_TYPE = "Kind instance is not a SetHolder: ";
        // Optional: if SetHolder should never contain null
        // private static final String ERR_INVALID_HOLDER_STATE = "SetHolder contained null Set instance";

        private SetKindHelper() { /* No instantiation */ }

        // Holder Record (typically package-private or private)
        record SetHolder<AVal>(@NonNull Set<AVal> set) implements SetKind<AVal> { }

        // Wrap Method
        @SuppressWarnings("unchecked") // Safe cast due to SetHolder implementing SetKind
        public static <A> @NonNull Kind<SetKind.Witness, A> wrap(@NonNull Set<A> set) {
            Objects.requireNonNull(set, "Input Set cannot be null for wrap");
            // SetHolder implements SetKind<A>, which is Kind<SetKind.Witness, A>
            return (SetKind<A>) new SetHolder<>(set);
        }

        // Unwrap Method
        @SuppressWarnings("unchecked")
        public static <A> @NonNull Set<A> unwrap(@Nullable Kind<SetKind.Witness, A> kind) {
            if (kind == null) {
                throw new KindUnwrapException(ERR_INVALID_KIND_NULL);
            }
            if (kind instanceof SetHolder<?> holder) { // Pattern match
                // Objects.requireNonNull(holder.set, ERR_INVALID_HOLDER_STATE); // If holder.set can't be null
                return (Set<A>) holder.set();
            } else {
                throw new KindUnwrapException(ERR_INVALID_KIND_TYPE + kind.getClass().getName());
            }
        }
    }
    ```

3.  **Implement Type Class Instances (e.g., `SetMonad.java`)**:
    * Create classes implementing `Functor<SetKind.Witness>`, `Applicative<SetKind.Witness>`, `Monad<SetKind.Witness>`, etc.
    * These implementations will use `SetKindHelper.wrap` and `SetKindHelper.unwrap` to interact with the underlying `java.util.Set`.

    ```java
    package org.higherkindedj.hkt.set;

    import org.higherkindedj.hkt.*;
    import java.util.HashSet;
    import java.util.Set;
    import java.util.function.Function;
    import java.util.stream.Collectors;
    import static org.higherkindedj.hkt.set.SetKindHelper.*; // Import static helpers

    public class SetMonad extends SetApplicative implements Monad<SetKind.Witness> {
        // Assumes SetApplicative and SetFunctor are defined similarly

        @Override
        public <A, B> Kind<SetKind.Witness, B> flatMap(
            Function<A, Kind<SetKind.Witness, B>> f,
            Kind<SetKind.Witness, A> ma) {

            Set<A> setA = unwrap(ma);
            Set<B> resultSet = setA.stream()
                                   .map(f) // A -> Kind<SetKind.Witness, B>
                                   .map(SetKindHelper::unwrap) // Kind -> Set<B>
                                   .flatMap(Set::stream)      // Set<B> -> Stream<B>, then flatten
                                   .collect(Collectors.toSet());
            return wrap(resultSet);
        }
    }

    // SetApplicative.java (Example part)
    class SetApplicative extends SetFunctor implements Applicative<SetKind.Witness> {
        @Override
        public <A> Kind<SetKind.Witness, A> of(A value) {
            // Note: Set.of() creates an immutable set.
            // If value can be null and Set cannot contain null, handle it.
            // For this example, assume Set can hold the 'value' type.
            // If 'value' is null and Set doesn't allow null, an empty set might be more appropriate.
            return wrap(value == null ? Set.of() : Set.of(value));
        }

        @Override
        public <A, B> Kind<SetKind.Witness, B> ap(
            Kind<SetKind.Witness, Function<A, B>> ff,
            Kind<SetKind.Witness, A> fa) {
            Set<Function<A, B>> setF = unwrap(ff);
            Set<A> setA = unwrap(fa);
            Set<B> result = new HashSet<>();
            for (Function<A, B> func : setF) {
                for (A a : setA) {
                    result.add(func.apply(a));
                }
            }
            return wrap(result);
        }
    }

    // SetFunctor.java (Example part)
    class SetFunctor implements Functor<SetKind.Witness> {
        @Override
        public <A, B> Kind<SetKind.Witness, B> map(
            Function<A, B> f,
            Kind<SetKind.Witness, A> fa) {
            Set<A> setA = unwrap(fa);
            Set<B> setB = setA.stream().map(f).collect(Collectors.toSet());
            return wrap(setB);
        }
    }
    ```
~~~

----



## Scenario 2: Integrating a Custom Library Type

If you are defining a new type *within your library* (e.g., a custom `MyType<A>`), you can design it to directly participate in the HKT simulation.

~~~admonish example title="Integrating a Custom Library Type"

1.  **Define Your Type and its `Kind` Interface**:
    * Your custom type (e.g., `MyType<A>`) directly implements its corresponding `MyTypeKind<A>` interface.
    * `MyTypeKind<A>` extends `Kind<MyType.Witness, A>` and defines the nested `Witness` class.

    ```java
    package org.example.mytype;

    import org.higherkindedj.hkt.Kind;
    import org.jspecify.annotations.NullMarked;

    // 1. The Kind Interface with Witness
    @NullMarked
    public interface MyTypeKind<A> extends Kind<MyType.Witness, A> {
      /**
       * Witness type for MyType.
       */
      final class Witness {
        private Witness() {}
      }
    }

    // 2. Your Custom Type directly implements its Kind interface
    public record MyType<A>(A value) implements MyTypeKind<A> {
        // ... constructors, methods for MyType ...
    }
    ```

2.  **Create the `KindHelper` Class**:
    * `wrap(MyType<A> myTypeValue)`: Since `MyType<A>` *is* already a `MyTypeKind<A>` (which is a `Kind<MyType.Witness, A>`), this method can simply perform a null check and then a cast (or often, no cast is even needed if the return type is `MyTypeKind<A>`).
    * `unwrap(Kind<MyType.Witness, A> kind)`: This method will check `if (kind instanceof MyType<?> myTypeInstance)` and then cast and return `myTypeInstance`.

    ```java
    package org.example.mytype;

    import org.higherkindedj.hkt.Kind;
    import org.higherkindedj.hkt.exception.KindUnwrapException;
    import org.jspecify.annotations.NonNull;
    import org.jspecify.annotations.Nullable;
    import java.util.Objects;

    public final class MyTypeKindHelper {
        private static final String ERR_INVALID_KIND_NULL = "Cannot unwrap null Kind for MyType";
        private static final String ERR_INVALID_KIND_TYPE = "Kind instance is not a MyType: ";

        private MyTypeKindHelper() {}

        @SuppressWarnings("unchecked") // MyType<A> is MyTypeKind<A> is Kind<MyType.Witness, A>
        public static <A> @NonNull Kind<MyType.Witness, A> wrap(@NonNull MyType<A> myTypeValue) {
            Objects.requireNonNull(myTypeValue, "Input MyType cannot be null for wrap");
            return (MyTypeKind<A>) myTypeValue; // Direct cast
        }

        @SuppressWarnings("unchecked")
        public static <A> @NonNull MyType<A> unwrap(@Nullable Kind<MyType.Witness, A> kind) {
            if (kind == null) {
                throw new KindUnwrapException(ERR_INVALID_KIND_NULL);
            }
            // Check if it's an instance of your actual type
            if (kind instanceof MyType<?> myTypeInstance) {
                return (MyType<A>) myTypeInstance; // Direct cast
            } else {
                throw new KindUnwrapException(ERR_INVALID_KIND_TYPE + kind.getClass().getName());
            }
        }
    }
    ```

3.  **Implement Type Class Instances**:
    * These will be similar to the external type scenario (e.g., `MyTypeMonad implements Monad<MyType.Witness>`), using your `MyTypeKindHelper.wrap` and `MyTypeKindHelper.unwrap` (which now involve casts).
~~~

~~~admonish important title="General Considerations"

* **Immutability**: Favor immutable data structures for your `Holder` or custom type if possible, as this aligns well with functional programming principles.
* **Null Handling**: Be very clear about null handling. Can the wrapped Java type be null? Can the value `A` inside be null? `KindHelper.wrap` should typically reject a null container itself. `Monad.of(null)` behavior depends on the specific monad (e.g., `OptionalMonad.of(null)` is empty, `ListMonad.of(null)` might be an empty list or a list with a null element based on its definition).
* **Testing**: Thoroughly test your `KindHelper` (especially `unwrap` with invalid inputs) and your type class instances (Functor, Applicative, Monad laws).

By following these patterns, you can integrate new or existing types into the Higher-Kinded-J framework, enabling them to be used with generic functional abstractions. The primary difference lies in whether a `Holder` record is needed (for external types) or if your type can directly implement its `Kind` interface.
~~~