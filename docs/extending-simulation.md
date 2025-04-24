# Extending the Simulation

You can add support for new Java types (type constructors) to this HKT simulation framework. Here’s a general guide on how to do it, using a hypothetical `java.util.Set<A>` as an example.

**Goal:** Simulate `java.util.Set<A>` as a `Kind<SetKind<?>, A>` and provide `Functor`, `Applicative`, and `Monad` instances for it.

## Steps

1. **Create the `*Kind` Interface:**
   * Define a marker interface that extends `Kind<F, A>`, where `F` is the witness type (usually the interface name with wildcards).
   * **Example (`SetKind.java`):****Java**

     ```java
     package org.simulation.hkt.set; // Create a new package

     import org.simulation.hkt.Kind;

     /**
      * Kind interface marker for java.util.Set<A>.
      * Witness type F = SetKind<?>
      * Value type A = A
      */
     public interface SetKind<A> extends Kind<SetKind<?>, A> {
     }
     ```
2. **Create the `*Holder` Record:**
   * Define an internal (usually package-private) record that implements the `*Kind` interface and holds the actual Java type instance.
   * **Example (inside `SetKindHelper.java`):****Java**

     ```
     // Inside SetKindHelper.java
     record SetHolder<A>(java.util.Set<A> set) implements SetKind<A> { }
     ```
3. **Create the `*KindHelper` Class:**
   * Define a `final` class with static `wrap` and `unwrap` methods.
   * `wrap`: Takes the Java type (e.g., `Set<A>`), checks for null, and returns a new `*Holder` instance wrapped as `Kind<F, A>`. Use appropriate annotations (`@NonNull`, `@Nullable`).
   * `unwrap`: Takes `Kind<F, A>`, checks for null, checks `instanceof *Holder`, extracts the internal Java type, checks if *that* is null, and returns it. Throws `KindUnwrapException` for any structural invalidity. Use appropriate annotations.
   * **Example (`SetKindHelper.java`):****Java**

     ```java 
     package org.simulation.hkt.set;

     import org.jspecify.annotations.NonNull;
     import org.jspecify.annotations.Nullable;
     import org.simulation.hkt.Kind;
     import org.simulation.hkt.exception.KindUnwrapException; // Import exception

     import java.util.Objects;
     import java.util.Set; // Import Set

     public final class SetKindHelper {

         // Error Messages
         private static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Set";
         private static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a SetHolder: ";
         private static final String INVALID_HOLDER_STATE_MSG = "SetHolder contained null Set instance";

         private SetKindHelper() { /* No instantiation */ }

         // Holder Record
         record SetHolder<A>(@NonNull Set<A> set) implements SetKind<A> { }

         // Wrap Method
         public static <A> @NonNull SetKind<A> wrap(@NonNull Set<A> set) {
             Objects.requireNonNull(set, "Input Set cannot be null for wrap");
             return new SetHolder<>(set);
         }

         // Unwrap Method
         @SuppressWarnings("unchecked") // For casting holder.set()
         public static <A> @NonNull Set<A> unwrap(@Nullable Kind<SetKind<?>, A> kind) {
             if (kind == null) {
                 throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
             }
             if (kind instanceof SetHolder<?> holder) { // Use wildcard pattern
                 Set<?> internalSet = holder.set(); // Raw type Set<?>
                 if (internalSet == null) { // Should not happen if wrap enforces non-null
                      throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
                 }
                 return (Set<A>) internalSet; // Cast is safe here
             } else {
                 throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
             }
         }
     }
     ```
4. **Implement Type Class Instances:**
   * Create classes implementing `Functor<F>`, `Applicative<F>`, `Monad<F>`, etc., using the specific witness type `F` (e.g., `SetKind<?>`).
   * Implement the required methods (`map`, `of`, `ap`, `flatMap`) using the `wrap` and `unwrap` helpers to interact with the underlying Java type.
   * **Example (`SetFunctor.java`, `SetApplicative.java`, `SetMonad.java`):**
     **Java**

     ```java
     // SetFunctor.java
     package org.simulation.hkt.set;
     import org.simulation.hkt.*;
     import java.util.Set;
     import java.util.function.Function;
     import java.util.stream.Collectors;
     import static org.simulation.hkt.set.SetKindHelper.*;

     public class SetFunctor implements Functor<SetKind<?>> {
         @Override
         public <A, B> Kind<SetKind<?>, B> map(Function<A, B> f, Kind<SetKind<?>, A> fa) {
             Set<A> setA = unwrap(fa);
             Set<B> setB = setA.stream().map(f).collect(Collectors.toSet());
             return wrap(setB);
         }
     }

     // SetApplicative.java
     package org.simulation.hkt.set;
     import org.simulation.hkt.*;
     import java.util.HashSet;
     import java.util.Set;
     import java.util.function.Function;
     import static org.simulation.hkt.set.SetKindHelper.*;

     public class SetApplicative extends SetFunctor implements Applicative<SetKind<?>> {
         @Override
         public <A> Kind<SetKind<?>, A> of(A value) {
             // Note: Set.of() might be better if immutability is desired
             return wrap(Set.of(value)); // Create singleton set
         }

         @Override
         public <A, B> Kind<SetKind<?>, B> ap(Kind<SetKind<?>, Function<A, B>> ff, Kind<SetKind<?>, A> fa) {
             Set<Function<A, B>> setF = unwrap(ff);
             Set<A> setA = unwrap(fa);
             Set<B> result = new HashSet<>();
             for (Function<A, B> f : setF) {
                 for (A a : setA) {
                     result.add(f.apply(a));
                 }
             }
             return wrap(result);
         }
     }

     // SetMonad.java
     package org.simulation.hkt.set;
     import org.simulation.hkt.*;
     import java.util.Set;
     import java.util.function.Function;
     import java.util.stream.Collectors;
     import static org.simulation.hkt.set.SetKindHelper.*;

     public class SetMonad extends SetApplicative implements Monad<SetKind<?>> {
         @Override
         public <A, B> Kind<SetKind<?>, B> flatMap(Function<A, Kind<SetKind<?>, B>> f, Kind<SetKind<?>, A> ma) {
             Set<A> setA = unwrap(ma);
             Set<B> resultSet = setA.stream()
                                    .map(f) // Apply A -> Kind<SetKind<?>, B>
                                    .map(SetKindHelper::unwrap) // Unwrap Kind -> Set<B>
                                    .flatMap(Set::stream) // Flatten Set<Set<B>> -> Stream<B>
                                    .collect(Collectors.toSet()); // Collect to Set<B>
             return wrap(resultSet);
         }
     }
     ```
5. **(Optional) Implement `MonadError`:**
   * If the type constructor has a natural error state (like `Optional.empty`, `Either.Left`), implement `MonadError<F, E>`.
   * Define the error type `E`.
   * Implement `raiseError(E error)` and `handleErrorWith(Kind<F, A> ma, Function<E, Kind<F, A>> handler)`.
   * *Note: `Set` doesn't have a standard monadic error state, so `MonadError` isn't typically implemented for it.*
6. **Add Tests:** Create corresponding test classes (`SetKindHelperTest`, `SetMonadTest`) to verify the `wrap`/`unwrap` behavior (including `KindUnwrapException` cases) and ensure the type class instances adhere to the Functor/Applicative/Monad laws.

By following these steps, you can integrate new types into the HKT simulation, allowing you to use them with the generic functional abstractions provided.
