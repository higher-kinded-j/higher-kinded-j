package org.higherkindedj.hkt.io;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull; // Assuming usage of JSpecify for annotations

/**
 * Represents a computation that, when executed, can perform side effects and produce a value of
 * type {@code A}. The {@code IO} monad is a core pattern in functional programming for isolating
 * and managing side effects (e.g., network requests, file system operations, database calls,
 * console I/O) in a controlled manner.
 *
 * <p>An {@code IO<A>} instance does not perform any action when it's created. Instead, it acts as a
 * description or a "recipe" for a computation that will be executed only when explicitly
 * instructed, typically by calling {@link #unsafeRunSync()}. This deferred execution allows for
 * referential transparency and enables building complex effectful programs that remain testable and
 * composable.
 *
 * <p><b>Key Characteristics:</b>
 *
 * <ul>
 *   <li><b>Laziness:</b> Effects are not executed upon creation of an {@code IO} value, but only
 *       when explicitly run.
 *   <li><b>Purity:</b> Constructing and combining {@code IO} values are pure operations. The
 *       impurity (side effect) is deferred until execution.
 *   <li><b>Composability:</b> {@code IO} operations can be easily chained and combined using
 *       methods like {@link #map(Function)} and {@link #flatMap(Function)} to build more complex
 *       effectful workflows.
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * // Describes reading a line from the console
 * IO<String> readLine = IO.delay(() -> {
 * System.out.print("Enter your name: ");
 * return new java.util.Scanner(System.in).nextLine();
 * });
 *
 * // Describes printing a string to the console
 * IO<Void> printLine(String message) {
 * return IO.delay(() -> {
 * System.out.println(message);
 * return null; // Void actions typically return null
 * });
 * }
 *
 * // Combine descriptions
 * IO<Void> greetUser = readLine.flatMap(name ->
 * printLine("Hello, " + name + "!")
 * );
 *
 * // Nothing has happened yet.
 * // To actually perform the I/O:
 * // greetUser.unsafeRunSync(); // This will prompt for input and then print the greeting.
 * }</pre>
 *
 * <p>The name {@code unsafeRunSync} highlights that executing the {@code IO} operation can have
 * side effects and, in a synchronous context, may block. In more advanced scenarios, {@code IO}
 * types often provide asynchronous execution methods (e.g., {@code unsafeRunAsync}).
 *
 * @param <A> The type of the value produced by the computation when it is executed. For {@code IO}
 *     actions that don't produce a meaningful value (e.g., printing to console), {@link Void} is
 *     often used as the type parameter {@code A}.
 */
@FunctionalInterface
public interface IO<A> {

  /**
   * Executes the described computation synchronously, potentially performing side effects and
   * returning a result of type {@code A}.
   *
   * <p>This method is typically called at the "end of the world" in an application, where the
   * declarative description of the program (built using {@code IO}) is finally interpreted and
   * executed.
   *
   * <p><b>Warning:</b> As the name suggests, calling this method can be "unsafe" in a purely
   * functional context because it triggers the actual side effects. If the computation involves
   * blocking operations, this method will block the calling thread. Any exceptions thrown by the
   * underlying computation will propagate out of this method.
   *
   * @return The result of the computation of type {@code A}. If {@code A} is {@link Void}, this
   *     typically returns {@code null}.
   */
  A unsafeRunSync();

  /**
   * Creates an {@code IO<A>} instance that defers a computation described by the given {@link
   * Supplier}. The provided {@code Supplier} (often referred to as a "thunk") encapsulates the
   * effectful operation. It will only be executed when {@link #unsafeRunSync()} is called on the
   * resulting {@code IO} instance.
   *
   * <p>This is the primary way to lift an arbitrary block of code (especially one with side
   * effects) into an {@code IO} context, making it lazy and composable.
   *
   * @param thunk A {@link Supplier} that, when called, will execute the desired computation and
   *     produce a value of type {@code A}. Must not be null.
   * @param <A> The type of the value produced by the thunk.
   * @return A new {@code @NonNull IO<A>} instance representing the deferred computation.
   * @throws NullPointerException if {@code thunk} is null.
   */
  static <A> @NonNull IO<A> delay(@NonNull Supplier<A> thunk) {
    Objects.requireNonNull(thunk, "Supplier (thunk) cannot be null for IO.delay");
    // The lambda () -> thunk.get() becomes the implementation of unsafeRunSync
    // for the returned IO instance.
    return thunk::get;
  }

  /**
   * Transforms the result of this {@code IO} computation using the provided mapping function {@code
   * f}, without altering its effectful nature. The original {@code IO} action is performed, and its
   * result is then passed to the function {@code f}. The entire operation remains deferred until
   * {@link #unsafeRunSync()} is called.
   *
   * <p>This is the Functor {@code map} operation for {@code IO}.
   *
   * <p>If this {@code IO} instance represents the computation {@code effectfulGetA()}, then {@code
   * map(f)} represents {@code effectfulGetA().thenApply(f)}.
   *
   * @param f A non-null function to apply to the result of this {@code IO} computation. It takes a
   *     value of type {@code A} and returns a value of type {@code B}.
   * @param <B> The type of the value produced by the mapping function and thus by the new {@code
   *     IO}.
   * @return A new {@code @NonNull IO<B>} that, when run, will execute this {@code IO}'s computation
   *     and then apply the mapping function {@code f} to its result.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> @NonNull IO<B> map(@NonNull Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "mapper function cannot be null");
    return IO.delay(() -> f.apply(this.unsafeRunSync()));
  }

  /**
   * Composes this {@code IO} computation with another {@code IO}-producing function {@code f}. This
   * method allows sequencing of {@code IO} operations, where the next operation depends on the
   * result of the current one.
   *
   * <p>First, this {@code IO} computation is run (when the resulting {@code IO} is eventually run).
   * Its result (of type {@code A}) is then passed to the function {@code f}, which produces a new
   * {@code IO<B>}. This new {@code IO<B>} is then also run. The entire sequence is deferred.
   *
   * <p>This is the Monad {@code flatMap} (or {@code bind}) operation for {@code IO}. It is
   * essential for chaining operations that themselves produce {@code IO} values, ensuring that
   * effects are properly sequenced and encapsulated.
   *
   * @param f A non-null function that takes the result of this {@code IO} computation (type {@code
   *     A}) and returns a new {@code IO<B>} representing the next computation. The {@code IO}
   *     returned by this function must not be null.
   * @param <B> The type of the value produced by the {@code IO} returned by function {@code f}.
   * @return A new {@code @NonNull IO<B>} that, when run, will execute this {@code IO}'s
   *     computation, apply function {@code f} to its result to get a new {@code IO}, and then
   *     execute that new {@code IO}.
   * @throws NullPointerException if {@code f} is null, or if {@code f} returns a null {@code IO}.
   */
  default <B> @NonNull IO<B> flatMap(@NonNull Function<? super A, ? extends IO<B>> f) {
    Objects.requireNonNull(f, "flatMap mapper function cannot be null");
    return IO.delay(
        () -> {
          // When the resulting IO is run, this.unsafeRunSync() is called first.
          A a = this.unsafeRunSync();
          // Then, f is applied to its result to get the next IO action.
          IO<B> nextIO = f.apply(a);
          Objects.requireNonNull(nextIO, "flatMap function returned a null IO instance");
          // Finally, the next IO action is run.
          return nextIO.unsafeRunSync();
        });
  }
}
