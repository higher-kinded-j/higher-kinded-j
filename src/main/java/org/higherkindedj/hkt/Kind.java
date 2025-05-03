package org.higherkindedj.hkt;

/**
 * Represents a simulated Higher-Kinded Type (HKT) in Java.
 *
 * <p>Java's type system does not natively support type constructors like {@code F<_>} (e.g., {@code List<_>},
 * {@code Optional<_>}) as first-class type parameters. This interface serves as a marker or bridge to
 * simulate the concept of applying a type constructor {@code F} to a type argument {@code A},
 * conceptually representing {@code F<A>}.
 *
 * <p>Concrete 'kinds' (like ListKind, OptionalKind) implement this interface to participate in the
 * simulation, typically using a pattern like: {@code interface ListKind<T> extends
 * Kind<ListKind<?>, T> {}} where {@code ListKind<?>} acts as the witness type {@code F}.
 *
 * <p>Interfaces like {@link Functor} and {@code Monad} operate on instances of {@code Kind<F, A>}.
 *
 * @param <F> The witness type representing the type constructor (e.g., {@code ListKind<?>}, {@code
 *     OptionalKind<?>}). This acts as the 'F' in the conceptual type {@code F<A>}.
 * @param <A> The type argument applied to the type constructor F (e.g., {@code Integer} in
 *     {@code List<Integer>}). This acts as the 'A' in the conceptual type {@code F<A>}.
 */
public interface Kind<F, A> {}
