// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.FreeApPath;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;

/**
 * Examples demonstrating FreePath and FreeApPath for building DSLs.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Building embedded DSLs (Domain Specific Languages)
 *   <li>Separating program description from execution
 *   <li>Multiple interpretation strategies for the same program
 *   <li>FreeApPath for applicative (parallel-friendly) computations
 *   <li>Testing with mock interpreters
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.FreePathExample}
 */
public class FreePathExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: FreePath & FreeApPath ===\n");

    basicFreePathExample();
    consoleDslExample();
    keyValueStoreDslExample();
    freeApPathExample();
    testingWithMockInterpreter();
  }

  private static void basicFreePathExample() {
    System.out.println("--- Basic FreePath Usage ---");

    Monad<MaybeKind.Witness> monad = MaybeMonad.INSTANCE;

    // Create a FreePath with a pure value
    FreePath<MaybeKind.Witness, Integer> pure = FreePath.pure(42, monad);

    // Lift a Maybe value into FreePath
    FreePath<MaybeKind.Witness, String> lifted = FreePath.liftF(MAYBE.just("Hello"), monad);

    // Chain operations
    FreePath<MaybeKind.Witness, String> program =
        pure.map(x -> x * 2).via(x -> FreePath.pure("Value: " + x, monad));

    // Interpret using identity transformation
    Natural<MaybeKind.Witness, MaybeKind.Witness> identity = Natural.identity();
    GenericPath<MaybeKind.Witness, String> result = program.foldMap(identity, monad);

    Maybe<String> maybe = MAYBE.narrow(result.runKind());
    System.out.println("Result: " + maybe.get());

    System.out.println();
  }

  // ===== Console DSL Example =====

  /**
   * A simple Console DSL - operations for printing and reading. This demonstrates how to build a
   * testable, interpretable DSL.
   */
  sealed interface ConsoleOp<A> {
    record PrintLine<A>(String line, A next) implements ConsoleOp<A> {}

    record ReadLine<A>(Function<String, A> cont) implements ConsoleOp<A> {}
  }

  /** Witness type for ConsoleOp. */
  interface ConsoleOpWitness extends WitnessArity<TypeArity.Unary> {}

  /** Kind wrapper for ConsoleOp to work with HKT system. */
  record ConsoleOpKind<A>(ConsoleOp<A> op) implements Kind<ConsoleOpWitness, A> {}

  /** Functor instance for ConsoleOp. */
  static final Functor<ConsoleOpWitness> CONSOLE_FUNCTOR =
      new Functor<ConsoleOpWitness>() {
        @Override
        @SuppressWarnings("unchecked")
        public <A, B> Kind<ConsoleOpWitness, B> map(
            Function<? super A, ? extends B> f, Kind<ConsoleOpWitness, A> fa) {

          ConsoleOp<A> op = ((ConsoleOpKind<A>) fa).op();
          ConsoleOp<B> mapped =
              switch (op) {
                case ConsoleOp.PrintLine<A>(String line, A next) ->
                    new ConsoleOp.PrintLine<>(line, f.apply(next));
                case ConsoleOp.ReadLine<A>(var cont) ->
                    new ConsoleOp.ReadLine<>(s -> f.apply(cont.apply(s)));
              };
          return new ConsoleOpKind<>(mapped);
        }
      };

  // Smart constructors for Console DSL
  static FreePath<ConsoleOpWitness, Void> printLine(String line) {
    return FreePath.liftF(
            new ConsoleOpKind<>(new ConsoleOp.PrintLine<>(line, null)), CONSOLE_FUNCTOR)
        .map(_ -> null);
  }

  static FreePath<ConsoleOpWitness, String> readLine() {
    return FreePath.liftF(new ConsoleOpKind<>(new ConsoleOp.ReadLine<>(s -> s)), CONSOLE_FUNCTOR);
  }

  private static void consoleDslExample() {
    System.out.println("--- Console DSL Example ---");

    // Build a program using the Console DSL
    // Note: This just DESCRIBES the program, doesn't execute it
    FreePath<ConsoleOpWitness, String> program =
        printLine("What is your name?")
            .then(FreePathExample::readLine)
            .via(
                name ->
                    printLine("Hello, " + name + "!")
                        .then(() -> FreePath.pure(name, CONSOLE_FUNCTOR)));

    System.out.println("Program built. Now interpreting with test data...\n");

    // Interpret to IO with simulated input
    Natural<ConsoleOpWitness, IO.Witness> testInterpreter = createTestConsoleInterpreter("Alice");

    GenericPath<IO.Witness, String> interpreted =
        program.foldMap(testInterpreter, IOMonad.INSTANCE);
    String result = IOKindHelper.IO_OP.narrow(interpreted.runKind()).unsafeRunSync();

    System.out.println("\nProgram returned: " + result);
    System.out.println();
  }

  @SuppressWarnings("unchecked")
  private static Natural<ConsoleOpWitness, IO.Witness> createTestConsoleInterpreter(
      String testInput) {
    return new Natural<>() {
      @Override
      public <A> Kind<IO.Witness, A> apply(Kind<ConsoleOpWitness, A> fa) {
        ConsoleOp<A> op = ((ConsoleOpKind<A>) fa).op();
        return switch (op) {
          case ConsoleOp.PrintLine<A>(String line, A next) ->
              IO.delay(
                  () -> {
                    System.out.println("[Console] " + line);
                    return next;
                  });
          case ConsoleOp.ReadLine<A>(var cont) ->
              IO.delay(
                  () -> {
                    System.out.println("[Input] " + testInput);
                    return cont.apply(testInput);
                  });
        };
      }
    };
  }

  // ===== Key-Value Store DSL Example =====

  /** A Key-Value Store DSL demonstrating CRUD operations. */
  sealed interface KVStoreOp<A> {
    record Get<A>(String key, Function<String, A> cont) implements KVStoreOp<A> {}

    record Put<A>(String key, String value, A next) implements KVStoreOp<A> {}

    record Delete<A>(String key, A next) implements KVStoreOp<A> {}
  }

  interface KVStoreWitness extends WitnessArity<TypeArity.Unary> {}

  record KVStoreKind<A>(KVStoreOp<A> op) implements Kind<KVStoreWitness, A> {}

  static final Functor<KVStoreWitness> KV_FUNCTOR =
      new Functor<KVStoreWitness>() {
        @Override
        @SuppressWarnings("unchecked")
        public <A, B> Kind<KVStoreWitness, B> map(
            Function<? super A, ? extends B> f, Kind<KVStoreWitness, A> fa) {

          KVStoreOp<A> op = ((KVStoreKind<A>) fa).op();
          KVStoreOp<B> mapped =
              switch (op) {
                case KVStoreOp.Get<A>(String key, var cont) ->
                    new KVStoreOp.Get<>(key, s -> f.apply(cont.apply(s)));
                case KVStoreOp.Put<A>(String key, String value, A next) ->
                    new KVStoreOp.Put<>(key, value, f.apply(next));
                case KVStoreOp.Delete<A>(String key, A next) ->
                    new KVStoreOp.Delete<>(key, f.apply(next));
              };
          return new KVStoreKind<>(mapped);
        }
      };

  // Smart constructors
  static FreePath<KVStoreWitness, String> kvGet(String key) {
    return FreePath.liftF(new KVStoreKind<>(new KVStoreOp.Get<>(key, s -> s)), KV_FUNCTOR);
  }

  static FreePath<KVStoreWitness, Void> kvPut(String key, String value) {
    return FreePath.liftF(new KVStoreKind<>(new KVStoreOp.Put<>(key, value, null)), KV_FUNCTOR)
        .map(_ -> null);
  }

  static FreePath<KVStoreWitness, Void> kvDelete(String key) {
    return FreePath.liftF(new KVStoreKind<>(new KVStoreOp.Delete<>(key, null)), KV_FUNCTOR)
        .map(_ -> null);
  }

  private static void keyValueStoreDslExample() {
    System.out.println("--- Key-Value Store DSL Example ---");

    // Build a program that manipulates a key-value store
    FreePath<KVStoreWitness, String> program =
        kvPut("name", "Alice")
            .then(() -> kvPut("age", "30"))
            .then(() -> kvGet("name"))
            .via(name -> kvPut("greeting", "Hello, " + name).then(() -> kvGet("greeting")));

    System.out.println("KV program built. Interpreting with in-memory store...\n");

    // Create an interpreter that uses an in-memory map
    Map<String, String> store = new HashMap<>();
    Natural<KVStoreWitness, IO.Witness> interpreter = createKVInterpreter(store);

    GenericPath<IO.Witness, String> interpreted = program.foldMap(interpreter, IOMonad.INSTANCE);
    String result = IOKindHelper.IO_OP.narrow(interpreted.runKind()).unsafeRunSync();

    System.out.println("\nProgram returned: " + result);
    System.out.println("Final store state: " + store);
    System.out.println();
  }

  @SuppressWarnings("unchecked")
  private static Natural<KVStoreWitness, IO.Witness> createKVInterpreter(
      Map<String, String> store) {
    return new Natural<>() {
      @Override
      public <A> Kind<IO.Witness, A> apply(Kind<KVStoreWitness, A> fa) {
        KVStoreOp<A> op = ((KVStoreKind<A>) fa).op();
        return switch (op) {
          case KVStoreOp.Get<A>(String key, var cont) ->
              IO.delay(
                  () -> {
                    String value = store.getOrDefault(key, "");
                    System.out.println("  GET " + key + " => " + value);
                    return cont.apply(value);
                  });
          case KVStoreOp.Put<A>(String key, String value, A next) ->
              IO.delay(
                  () -> {
                    System.out.println("  PUT " + key + " = " + value);
                    store.put(key, value);
                    return next;
                  });
          case KVStoreOp.Delete<A>(String key, A next) ->
              IO.delay(
                  () -> {
                    System.out.println("  DELETE " + key);
                    store.remove(key);
                    return next;
                  });
        };
      }
    };
  }

  // ===== FreeApPath Example =====

  private static void freeApPathExample() {
    System.out.println("--- FreeApPath (Applicative) Example ---");

    // FreeApPath is for independent computations that can run in parallel
    // Unlike FreePath, operations don't depend on each other's results

    Monad<MaybeKind.Witness> monad = MaybeMonad.INSTANCE;
    Natural<MaybeKind.Witness, MaybeKind.Witness> identity = Natural.identity();

    // Create independent computations
    FreeApPath<MaybeKind.Witness, Integer> getAge = FreeApPath.liftF(MAYBE.just(30), monad);
    FreeApPath<MaybeKind.Witness, String> getName = FreeApPath.liftF(MAYBE.just("Alice"), monad);
    FreeApPath<MaybeKind.Witness, String> getCity = FreeApPath.liftF(MAYBE.just("London"), monad);

    // Combine with zipWith - all three are independent
    FreeApPath<MaybeKind.Witness, String> combined =
        getName
            .zipWith(getAge, (name, age) -> name + " is " + age)
            .zipWith(getCity, (s, city) -> s + " from " + city);

    // Or use zipWith3 for three values at once
    FreeApPath<MaybeKind.Witness, String> combined3 =
        getName.zipWith3(
            getAge, getCity, (name, age, city) -> name + " (" + age + ") lives in " + city);

    Kind<MaybeKind.Witness, String> result = combined.foldMapKind(identity, monad);
    Kind<MaybeKind.Witness, String> result3 = combined3.foldMapKind(identity, monad);

    System.out.println("zipWith result: " + MAYBE.narrow(result).get());
    System.out.println("zipWith3 result: " + MAYBE.narrow(result3).get());

    System.out.println();
  }

  // ===== Testing with Mock Interpreter =====

  private static void testingWithMockInterpreter() {
    System.out.println("--- Testing with Mock Interpreter ---");

    // One of the key benefits of Free is testability
    // We can interpret the same program with different interpreters

    // Build a program
    FreePath<KVStoreWitness, String> program =
        kvPut("user", "Bob")
            .then(() -> kvGet("user"))
            .via(user -> kvPut("message", "Hello, " + user).then(() -> kvGet("message")));

    // Interpreter 1: Recording interpreter (for testing)
    List<String> operations = new ArrayList<>();
    Natural<KVStoreWitness, IO.Witness> recordingInterpreter =
        createRecordingInterpreter(operations);

    GenericPath<IO.Witness, String> testRun =
        program.foldMap(recordingInterpreter, IOMonad.INSTANCE);
    String testResult = IOKindHelper.IO_OP.narrow(testRun.runKind()).unsafeRunSync();

    System.out.println("Test result: " + testResult);
    System.out.println("Recorded operations:");
    operations.forEach(op -> System.out.println("  " + op));

    // In a real test, you could assert on the operations list
    System.out.println("\nWith a mock interpreter, we can verify:");
    System.out.println("  - Correct sequence of operations");
    System.out.println("  - Expected keys and values");
    System.out.println("  - No unexpected side effects");

    System.out.println();
  }

  @SuppressWarnings("unchecked")
  private static Natural<KVStoreWitness, IO.Witness> createRecordingInterpreter(List<String> log) {
    Map<String, String> mockStore = new HashMap<>();

    return new Natural<>() {
      @Override
      public <A> Kind<IO.Witness, A> apply(Kind<KVStoreWitness, A> fa) {
        KVStoreOp<A> op = ((KVStoreKind<A>) fa).op();
        return switch (op) {
          case KVStoreOp.Get<A>(String key, var cont) ->
              IO.delay(
                  () -> {
                    log.add("GET(" + key + ")");
                    return cont.apply(mockStore.getOrDefault(key, "mock-" + key));
                  });
          case KVStoreOp.Put<A>(String key, String value, A next) ->
              IO.delay(
                  () -> {
                    log.add("PUT(" + key + ", " + value + ")");
                    mockStore.put(key, value);
                    return next;
                  });
          case KVStoreOp.Delete<A>(String key, A next) ->
              IO.delay(
                  () -> {
                    log.add("DELETE(" + key + ")");
                    mockStore.remove(key);
                    return next;
                  });
        };
      }
    };
  }
}
