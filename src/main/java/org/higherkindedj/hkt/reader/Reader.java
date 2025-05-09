package org.higherkindedj.hkt.reader;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a computation that depends on a read-only environment {@code R} to produce a value {@code A}.
 * The {@code Reader} monad is a functional programming pattern used for dependency injection,
 * allowing computations to "read" from a shared environment or configuration without explicitly
 * passing it through all layers of function calls.
 *
 * <p>Essentially, a {@code Reader<R, A>} is a wrapper around a function {@code Function<R, A>}.
 * It encapsulates a step in a program that requires some external configuration or service
 * (the environment {@code R}) to produce its result ({@code A}).
 *
 * <p><b>Key Use Cases:</b>
 * <ul>
 * <li><b>Dependency Injection:</b> Provide dependencies (like database connections, configuration
 * objects, or services) to computations in a clean and composable way.</li>
 * <li><b>Context Propagation:</b> Pass contextual information (like user identity or request-specific
 * data) through a series of operations.</li>
 * <li><b>Modular Design:</b> Decouple components from the direct creation or lookup of their
 * dependencies, making them more testable and reusable.</li>
 * </ul>
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * // Define an environment interface or record
 * interface AppConfig {
 * String getGreeting();
 * int getDefaultRetries();
 * }
 *
 * // A simple implementation of the environment
 * record MyConfig(String greeting, int retries) implements AppConfig {
 * @Override public String getGreeting() { return greeting; }
 * @Override public int getDefaultRetries() { return retries; }
 * }
 *
 * // A computation that reads the greeting from the environment
 * Reader<AppConfig, String> getGreetingMessage = Reader.ask().map(AppConfig::getGreeting);
 *
 * // Another computation that reads the number of retries
 * Reader<AppConfig, Integer> getRetries = Reader.ask().map(AppConfig::getDefaultRetries);
 *
 * // Combine computations
 * Reader<AppConfig, String> personalizedGreeting = getGreetingMessage.flatMap(greeting ->
 * getRetries.map(retries -> greeting + "! You have " + retries + " retries left.")
 * );
 *
 * // Provide the actual environment and run the computation
 * AppConfig productionConfig = new MyConfig("Hello from Reader", 3);
 * String message = personalizedGreeting.run(productionConfig);
 * System.out.println(message); // Output: Hello from Reader! You have 3 retries left.
 *
 * AppConfig testConfig = new MyConfig("Test Greeting", 0);
 * String testMessage = personalizedGreeting.run(testConfig);
 * System.out.println(testMessage); // Output: Test Greeting! You have 0 retries left.
 * }</pre>
 *
 * @param <R> The type of the read-only environment (e.g., configuration, context, dependencies).
 * This environment is provided when the {@code Reader} is eventually run.
 * @param <A> The type of the value produced by the computation within the {@code Reader}.
 */
@FunctionalInterface
public interface Reader<R, A> {

  /**
   * Executes the computation encapsulated by this {@code Reader} using the provided environment.
   * This is the method that "runs" the reader, supplying the necessary context or dependencies.
   *
   * @param r The environment of type {@code R}. While annotated as {@code @NonNull}, the specific
   * nullability contract for {@code r} depends on the design of the environment type {@code R}
   * and how it's intended to be used. It's generally good practice for {@code R} to be non-null.
   * @return The computed value of type {@code A}. The nullability of the result depends on the
   * specific computation defined within this {@code Reader}. It's annotated as {@code @Nullable}
   * to reflect that the function {@code Function<R, A>} might return null.
   */
  @Nullable A run(@NonNull R r);

  /**
   * Creates a {@code Reader} instance from a given function.
   * This is the most fundamental way to construct a {@code Reader}, by providing the function
   * that defines the computation based on the environment.
   *
   * @param runFunction The function {@code (R -> A)} that represents the core logic of the {@code Reader}.
   * It takes an environment {@code R} and produces a value {@code A}.
   * Must not be null.
   * @param <R> The type of the environment.
   * @param <A> The type of the value produced.
   * @return A new {@code @NonNull Reader<R, A>} instance.
   * @throws NullPointerException if {@code runFunction} is null.
   */
  static <R, A> @NonNull Reader<R, A> of(@NonNull Function<R, A> runFunction) {
    Objects.requireNonNull(runFunction, "runFunction cannot be null");
    return runFunction::apply;
  }

  /**
   * Transforms the result of this {@code Reader} using the provided mapping function.
   * If this {@code Reader} produces a value {@code A}, this method applies the function {@code f}
   * to {@code A} to produce a new value {@code B}, wrapped in a new {@code Reader}.
   * The environment {@code R} remains the same.
   *
   * <p>This is the Functor `map` operation for {@code Reader}.
   * <p>Equivalent to: {@code r -> f.apply(this.run(r))}
   *
   * @param f The non-null function to apply to the result of this {@code Reader}.
   * It takes a value of type {@code A} and returns a value of type {@code B}.
   * @param <B> The type of the value produced by the mapping function and thus by the new {@code Reader}.
   * @return A new {@code @NonNull Reader<R, B>} that, when run, will execute this {@code Reader} and then
   * apply the mapping function {@code f} to its result.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> @NonNull Reader<R, B> map(@NonNull Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "mapper function cannot be null");
    return (R r) -> f.apply(this.run(r));
  }

  /**
   * Composes this {@code Reader} with a function that takes the result of this {@code Reader}
   * and returns another {@code Reader}. The resulting {@code Reader} is then run with the same
   * initial environment.
   *
   * <p>This is the Monad `flatMap` (or `bind`) operation for {@code Reader}. It allows sequencing
   * computations where each step depends on the result of the previous one and also requires
   * access to the environment {@code R}.
   *
   * <p>Equivalent to: {@code r -> f.apply(this.run(r)).run(r)}
   *
   * @param f The non-null function to apply to the result of this {@code Reader}.
   * It takes a value of type {@code A} and returns a {@code Reader<R, ? extends B>}.
   * The returned {@code Reader} must not be null.
   * @param <B> The type of the value produced by the {@code Reader} returned by function {@code f}.
   * @return A new {@code @NonNull Reader<R, B>} that, when run, will execute this {@code Reader},
   * apply function {@code f} to its result to get a new {@code Reader}, and then
   * run that new {@code Reader} with the original environment.
   * @throws NullPointerException if {@code f} is null, or if {@code f} returns a null {@code Reader}.
   */
  default <B> @NonNull Reader<R, B> flatMap(
      @NonNull Function<? super A, ? extends Reader<R, ? extends B>> f) {
    Objects.requireNonNull(f, "flatMap mapper function cannot be null");
    return (R r) -> {
      @Nullable A a = this.run(r);
      Reader<R, ? extends B> readerB = f.apply(a);
      Objects.requireNonNull(readerB, "flatMap function returned null Reader");
      return readerB.run(r);
    };
  }

  /**
   * Creates a {@code Reader} that ignores the environment and always yields the given constant value.
   * This is useful for injecting a fixed value into a sequence of {@code Reader} computations
   * or for lifting a simple value into the {@code Reader} context.
   *
   * @param value The constant value (of type {@code A}) to be returned by the {@code Reader}.
   * Can be {@code null} if {@code A} is a nullable type.
   * @param <R> The type of the environment (which will be ignored).
   * @param <A> The type of the constant value.
   * @return A new {@code @NonNull Reader<R, A>} that always produces the given {@code value}
   * regardless of the environment it is run with.
   */
  static <R, A> @NonNull Reader<R, A> constant(@Nullable A value) {
    return r -> value;
  }

  /**
   * Creates a {@code Reader} that, when run, simply returns the environment itself.
   * This is a fundamental operation for accessing the environment {@code R} from within
   * a {@code Reader} computation. It's often used as the starting point for computations
   * that need to extract data from the environment.
   *
   * <p>In Category Theory terms, this is often called {@code ask} (from the Reader Monad).
   *
   * @param <R> The type of the environment, which is also the type of the value produced.
   * @return A new {@code @NonNull Reader<R, R>} that yields the environment it is run with.
   */
  static <R> @NonNull Reader<R, R> ask() {
    return r -> r;
  }
}