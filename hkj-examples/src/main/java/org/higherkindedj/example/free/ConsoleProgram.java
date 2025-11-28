// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.free;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

/**
 * Example demonstrating the Free monad with a simple Console DSL.
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Define a DSL using sealed interfaces
 *   <li>Build programs using the Free monad
 *   <li>Interpret Free programs in different ways (IO, Test)
 *   <li>Compose Free programs
 * </ul>
 *
 * <p><b>Note on the IO type:</b> This example defines its own simple {@code IO<A>} record (see
 * {@link #IO}) rather than using the production {@link org.higherkindedj.hkt.io.IO} from hkj-core.
 * This is intentional and pedagogical:
 *
 * <ul>
 *   <li>The core {@code IO} is a lazy, deferred computation that only executes when {@code
 *       unsafeRunSync()} is called
 *   <li>This example's {@code IO} is just a simple value wrapper - it holds an already-computed
 *       value
 *   <li>In the interpreters (see {@link IOInterpreter#run} and {@link TestInterpreter#run}), side
 *       effects execute <i>immediately</i> during Free monad interpretation, and the {@code IO}
 *       wrapper just holds the result to satisfy the type requirements of {@code foldMap}
 *   <li>This keeps the example focused on Free monad mechanics without the added complexity of lazy
 *       evaluation
 * </ul>
 *
 * <p>In production code, you would typically interpret Free programs into the full {@link
 * org.higherkindedj.hkt.io.IO} type from hkj-core, which provides proper lazy evaluation,
 * composition, and error handling.
 */
public class ConsoleProgram {

  /** Console DSL instructions. */
  public sealed interface ConsoleOp<A> {
    record PrintLine(String text) implements ConsoleOp<Unit> {}

    record ReadLine() implements ConsoleOp<String> {}
  }

  /** Unit type for operations that return void. */
  public record Unit() {
    public static final Unit INSTANCE = new Unit();
  }

  /** Kind interface for ConsoleOp. */
  public interface ConsoleOpKind<A> extends Kind<ConsoleOpKind.Witness, A> {
    final class Witness {
      private Witness() {}
    }
  }

  /** Helper for ConsoleOp Kind operations. */
  public enum ConsoleOpKindHelper {
    CONSOLE;

    record ConsoleOpHolder<A>(ConsoleOp<A> op) implements ConsoleOpKind<A> {}

    public <A> Kind<ConsoleOpKind.Witness, A> widen(ConsoleOp<A> op) {
      return new ConsoleOpHolder<>(op);
    }

    public <A> ConsoleOp<A> narrow(Kind<ConsoleOpKind.Witness, A> kind) {
      return ((ConsoleOpHolder<A>) kind).op();
    }
  }

  /** DSL operations for building Console programs. */
  public static class ConsoleOps {
    /** Prints a line to the console. */
    public static Free<ConsoleOpKind.Witness, Unit> printLine(String text) {
      ConsoleOp<Unit> op = new ConsoleOp.PrintLine(text);
      Kind<ConsoleOpKind.Witness, Unit> kindOp = ConsoleOpKindHelper.CONSOLE.widen(op);
      return Free.liftF(kindOp, new ConsoleOpFunctor());
    }

    /** Reads a line from the console. */
    public static Free<ConsoleOpKind.Witness, String> readLine() {
      ConsoleOp<String> op = new ConsoleOp.ReadLine();
      Kind<ConsoleOpKind.Witness, String> kindOp = ConsoleOpKindHelper.CONSOLE.widen(op);
      return Free.liftF(kindOp, new ConsoleOpFunctor());
    }

    /** Pure value in the Free monad. */
    public static <A> Free<ConsoleOpKind.Witness, A> pure(A value) {
      return Free.pure(value);
    }
  }

  /** Simple Functor for ConsoleOp. */
  public static class ConsoleOpFunctor implements Functor<ConsoleOpKind.Witness> {
    private static final ConsoleOpKindHelper CONSOLE = ConsoleOpKindHelper.CONSOLE;

    @Override
    public <A, B> Kind<ConsoleOpKind.Witness, B> map(
        Function<? super A, ? extends B> f, Kind<ConsoleOpKind.Witness, A> fa) {
      ConsoleOp<A> op = CONSOLE.narrow(fa);
      // For this simple DSL, we create a mapped operation
      // In a real implementation, you'd wrap the operation with the mapping
      return (Kind<ConsoleOpKind.Witness, B>) fa;
    }
  }

  /** IO interpreter for Console programs. */
  public static class IOInterpreter {
    private final Scanner scanner = new Scanner(System.in);

    public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
      // Create a natural transformation from ConsoleOp to IO
      // The transform receives Kind<ConsoleOp.Witness, Free<ConsoleOp.Witness, X>>
      // and must return Kind<IO.Witness, Free<ConsoleOp.Witness, X>>
      Function<Kind<ConsoleOpKind.Witness, ?>, Kind<IOKind.Witness, ?>> transform =
          kind -> {
            ConsoleOp<?> op =
                ConsoleOpKindHelper.CONSOLE.narrow((Kind<ConsoleOpKind.Witness, Object>) kind);
            // Execute the instruction IMMEDIATELY and wrap the result in Free.pure
            // NOTE: Side effects happen here during interpretation, not deferred.
            // This is why we use a simple IO wrapper instead of the lazy core IO type.
            Free<ConsoleOpKind.Witness, ?> freeResult =
                switch (op) {
                  case ConsoleOp.PrintLine print -> {
                    System.out.println(print.text()); // <-- Side effect executes NOW
                    yield Free.pure(Unit.INSTANCE);
                  }
                  case ConsoleOp.ReadLine read -> {
                    String line = scanner.nextLine(); // <-- Side effect executes NOW
                    yield Free.pure(line);
                  }
                };
            // Wrap the Free result in the target monad (IO just holds the value)
            return IOKindHelper.IO.widen(new IO<>(freeResult));
          };

      Kind<IOKind.Witness, A> result = program.foldMap(transform, new IOMonad());
      return IOKindHelper.IO.narrow(result).value();
    }
  }

  /** Test interpreter that captures output and provides input. */
  public static class TestInterpreter {
    private final List<String> input;
    private final List<String> output = new ArrayList<>();
    private int inputIndex = 0;

    public TestInterpreter(List<String> input) {
      this.input = input;
    }

    public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
      // Create a natural transformation from ConsoleOp to TestResult
      // The transform receives Kind<ConsoleOp.Witness, Free<ConsoleOp.Witness, X>>
      // and must return Kind<TestResult.Witness, Free<ConsoleOp.Witness, X>>
      Function<Kind<ConsoleOpKind.Witness, ?>, Kind<TestResultKind.Witness, ?>> transform =
          kind -> {
            ConsoleOp<?> op =
                ConsoleOpKindHelper.CONSOLE.narrow((Kind<ConsoleOpKind.Witness, Object>) kind);
            // Execute the instruction and wrap the result in Free.pure
            Free<ConsoleOpKind.Witness, ?> freeResult =
                switch (op) {
                  case ConsoleOp.PrintLine print -> {
                    output.add(print.text());
                    yield Free.pure(Unit.INSTANCE);
                  }
                  case ConsoleOp.ReadLine read -> {
                    String line = inputIndex < input.size() ? input.get(inputIndex++) : "";
                    yield Free.pure(line);
                  }
                };
            // Wrap the Free result in the target monad
            return TestResultKindHelper.TEST.widen(new TestResult<>(freeResult));
          };

      Kind<TestResultKind.Witness, A> result = program.foldMap(transform, new TestResultMonad());
      return TestResultKindHelper.TEST.narrow(result).value();
    }

    public List<String> getOutput() {
      return output;
    }
  }

  /**
   * Simple value wrapper used as a target monad for Free monad interpretation.
   *
   * <p><b>Important:</b> This is NOT the same as {@link org.higherkindedj.hkt.io.IO} from hkj-core.
   * This is a simple record that just holds an already-computed value, whereas the core IO type is
   * a lazy, deferred computation. We use this simple wrapper here because:
   *
   * <ul>
   *   <li>In this example, side effects execute <i>immediately</i> during interpretation (see
   *       {@link IOInterpreter#run})
   *   <li>The IO wrapper just holds the result to satisfy the type requirements of {@link
   *       Free#foldMap}
   *   <li>This keeps the example focused on Free monad mechanics without the complexity of lazy
   *       evaluation
   * </ul>
   *
   * <p>In production code, you would typically interpret into the full {@link
   * org.higherkindedj.hkt.io.IO} type from hkj-core.
   */
  record IO<A>(A value) {}

  interface IOKind<A> extends Kind<IOKind.Witness, A> {
    final class Witness {
      private Witness() {}
    }
  }

  enum IOKindHelper {
    IO;

    record IOHolder<A>(IO<A> io) implements IOKind<A> {}

    <A> Kind<IOKind.Witness, A> widen(IO<A> io) {
      return new IOHolder<>(io);
    }

    <A> IO<A> narrow(Kind<IOKind.Witness, A> kind) {
      return ((IOHolder<A>) kind).io();
    }
  }

  static class IOMonad implements Monad<IOKind.Witness> {
    @Override
    public <A> Kind<IOKind.Witness, A> of(A value) {
      return IOKindHelper.IO.widen(new IO<>(value));
    }

    @Override
    public <A, B> Kind<IOKind.Witness, B> map(
        Function<? super A, ? extends B> f, Kind<IOKind.Witness, A> fa) {
      IO<A> io = IOKindHelper.IO.narrow(fa);
      return IOKindHelper.IO.widen(new IO<>(f.apply(io.value())));
    }

    @Override
    public <A, B> Kind<IOKind.Witness, B> flatMap(
        Function<? super A, ? extends Kind<IOKind.Witness, B>> f, Kind<IOKind.Witness, A> ma) {
      IO<A> io = IOKindHelper.IO.narrow(ma);
      return f.apply(io.value());
    }

    @Override
    public <A, B> Kind<IOKind.Witness, B> ap(
        Kind<IOKind.Witness, ? extends Function<A, B>> ff, Kind<IOKind.Witness, A> fa) {
      IO<? extends Function<A, B>> ioF = IOKindHelper.IO.narrow(ff);
      IO<A> ioA = IOKindHelper.IO.narrow(fa);
      return IOKindHelper.IO.widen(new IO<>(ioF.value().apply(ioA.value())));
    }
  }

  /**
   * Simple value wrapper used as a target monad for testing Free monad programs.
   *
   * <p>This demonstrates that Free programs can be interpreted into <i>any</i> monad, not just IO.
   * By interpreting into this test monad, we can verify program behavior without performing actual
   * I/O operations. The {@link TestInterpreter} uses this to capture console output and provide
   * pre-defined input, making it easy to test programs that would otherwise require user
   * interaction.
   */
  record TestResult<A>(A value) {}

  interface TestResultKind<A> extends Kind<TestResultKind.Witness, A> {
    final class Witness {
      private Witness() {}
    }
  }

  enum TestResultKindHelper {
    TEST;

    record TestResultHolder<A>(TestResult<A> result) implements TestResultKind<A> {}

    <A> Kind<TestResultKind.Witness, A> widen(TestResult<A> result) {
      return new TestResultHolder<>(result);
    }

    <A> TestResult<A> narrow(Kind<TestResultKind.Witness, A> kind) {
      return ((TestResultHolder<A>) kind).result();
    }
  }

  static class TestResultMonad implements Monad<TestResultKind.Witness> {
    @Override
    public <A> Kind<TestResultKind.Witness, A> of(A value) {
      return TestResultKindHelper.TEST.widen(new TestResult<>(value));
    }

    @Override
    public <A, B> Kind<TestResultKind.Witness, B> map(
        Function<? super A, ? extends B> f, Kind<TestResultKind.Witness, A> fa) {
      TestResult<A> result = TestResultKindHelper.TEST.narrow(fa);
      return TestResultKindHelper.TEST.widen(new TestResult<>(f.apply(result.value())));
    }

    @Override
    public <A, B> Kind<TestResultKind.Witness, B> flatMap(
        Function<? super A, ? extends Kind<TestResultKind.Witness, B>> f,
        Kind<TestResultKind.Witness, A> ma) {
      TestResult<A> result = TestResultKindHelper.TEST.narrow(ma);
      return f.apply(result.value());
    }

    @Override
    public <A, B> Kind<TestResultKind.Witness, B> ap(
        Kind<TestResultKind.Witness, ? extends Function<A, B>> ff,
        Kind<TestResultKind.Witness, A> fa) {
      TestResult<? extends Function<A, B>> resultF = TestResultKindHelper.TEST.narrow(ff);
      TestResult<A> resultA = TestResultKindHelper.TEST.narrow(fa);
      return TestResultKindHelper.TEST.widen(
          new TestResult<>(resultF.value().apply(resultA.value())));
    }
  }

  /** Example programs. */
  public static class Programs {
    /** A simple greeting program. */
    public static Free<ConsoleOpKind.Witness, Unit> greetingProgram() {
      return ConsoleOps.printLine("What is your name?")
          .flatMap(
              ignored ->
                  ConsoleOps.readLine()
                      .flatMap(name -> ConsoleOps.printLine("Hello, " + name + "!")));
    }

    /** A program that asks for two numbers and adds them. */
    public static Free<ConsoleOpKind.Witness, Unit> calculatorProgram() {
      return ConsoleOps.printLine("Enter first number:")
          .flatMap(
              ignored1 ->
                  ConsoleOps.readLine()
                      .flatMap(
                          num1 ->
                              ConsoleOps.printLine("Enter second number:")
                                  .flatMap(
                                      ignored2 ->
                                          ConsoleOps.readLine()
                                              .flatMap(
                                                  num2 -> {
                                                    try {
                                                      int sum =
                                                          Integer.parseInt(num1)
                                                              + Integer.parseInt(num2);
                                                      return ConsoleOps.printLine("Sum: " + sum);
                                                    } catch (NumberFormatException e) {
                                                      return ConsoleOps.printLine(
                                                          "Invalid numbers!");
                                                    }
                                                  }))));
    }
  }

  /** Main method demonstrating the Free monad. Java 25 instance main method. */
  void main() {
    System.out.println("=== Free Monad Console DSL Example ===\n");

    // Run the greeting program with test interpreter
    System.out.println("--- Testing Greeting Program ---");
    TestInterpreter testInterpreter = new TestInterpreter(List.of("Alice"));
    testInterpreter.run(Programs.greetingProgram());
    System.out.println("Output:");
    testInterpreter.getOutput().forEach(line -> System.out.println("  " + line));

    System.out.println("\n--- Testing Calculator Program ---");
    TestInterpreter calcTest = new TestInterpreter(List.of("10", "32"));
    calcTest.run(Programs.calculatorProgram());
    System.out.println("Output:");
    calcTest.getOutput().forEach(line -> System.out.println("  " + line));

    // Uncomment to run with real IO
    // System.out.println("\n--- Running with Real IO ---");
    // IOInterpreter ioInterpreter = new IOInterpreter();
    // ioInterpreter.run(Programs.greetingProgram());

    System.out.println("\n=== Benefits of Free Monad ===");
    System.out.println("1. Programs are data structures that can be inspected");
    System.out.println("2. Multiple interpreters (IO, Test, Optimization, etc.)");
    System.out.println("3. Separation of program description from execution");
    System.out.println("4. Easy testing without side effects");
  }
}
