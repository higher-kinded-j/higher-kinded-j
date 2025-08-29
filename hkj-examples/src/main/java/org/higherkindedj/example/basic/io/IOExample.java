// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.unit.Unit;

/** see {<a href="https://higher-kinded-j.github.io/io_monad.html">IO Monad</a>} */
public class IOExample {
  // Get the IOMonad instance
  IOMonad ioMonad = IOMonad.INSTANCE;

  // IO action to print a message
  Kind<IOKind.Witness, Unit> printHello =
      IO_OP.delay(
          () -> {
            System.out.println("Hello from IO!");
            return Unit.INSTANCE;
          });

  // IO action to read a line from the console
  Kind<IOKind.Witness, String> readLine =
      IO_OP.delay(
          () -> {
            System.out.print("Enter your name: ");
            // Scanner should ideally be managed more robustly in real apps
            Scanner scanner = new Scanner(System.in);
            return scanner.nextLine();
          });

  // IO action that returns a pure value (no side effect description here)
  Kind<IOKind.Witness, Integer> pureValueIO = ioMonad.of(42);

  // IO action that simulates getting the current time (a side effect)
  Kind<IOKind.Witness, Long> currentTime = IO_OP.delay(System::currentTimeMillis);

  // Creating an IO action that might fail internally
  Kind<IOKind.Witness, String> potentiallyFailingIO =
      IO_OP.delay(
          () -> {
            if (Math.random() < 0.5) {
              throw new RuntimeException("Simulated failure!");
            }
            return "Success!";
          });

  public static void main(String[] args) {
    IOExample ioExample = new IOExample();
    System.out.println("\nExecuting IO Example!");
    ioExample.executingIO();
    System.out.println("\nDone!");
    System.out.println("\nComposing with map and flatMap Example!");
    ioExample.composingWithMapAndFlatMap();
    System.out.println("\nDone!");
    System.out.println("\n--- Composition with Utility Methods Example ---");
    ioExample.utilityMethodsCompositionExample();
  }

  public void executingIO() {
    // Execute printHello
    System.out.println("Running printHello:");
    IO_OP.unsafeRunSync(printHello); // Actually prints "Hello from IO!"

    System.out.println("\nRunning readLine:");
    String name = IO_OP.unsafeRunSync(readLine);
    System.out.println("User entered: " + name);

    // Execute pureValueIO
    System.out.println("\nRunning pureValueIO:");
    Integer fetchedValue = IO_OP.unsafeRunSync(pureValueIO);
    System.out.println("Fetched pure value: " + fetchedValue); // Output: 42

    // Execute potentiallyFailingIO
    System.out.println("\nRunning potentiallyFailingIO:");
    try {
      String result = IO_OP.unsafeRunSync(potentiallyFailingIO);
      System.out.println("Succeeded: " + result);
    } catch (RuntimeException e) {
      System.err.println("Caught expected failure: " + e.getMessage());
    }

    // Notice that running the same IO action again executes the effect again
    System.out.println("\nRunning printHello again:");
    IO_OP.unsafeRunSync(printHello); // Prints "Hello from IO!" again
  }

  public void composingWithMapAndFlatMap() {

    // --- map example ---
    Kind<IOKind.Witness, String> readLineAction = IO_OP.delay(() -> "Test Input"); // Simulate input

    // Map the result of readLineAction without executing readLine yet
    Kind<IOKind.Witness, String> greetAction =
        ioMonad.map(
            name -> "Hello, " + name + "!", // Function to apply to the result
            readLineAction);

    System.out.println("Greet action created, not executed yet.");
    // Now execute the mapped action
    String greeting = IO_OP.unsafeRunSync(greetAction);
    System.out.println("Result of map: " + greeting); // Output: Hello, Test Input!

    // --- flatMap example ---
    // Action 1: Get name
    Kind<IOKind.Witness, String> getName =
        IO_OP.delay(
            () -> {
              System.out.println("Effect: Getting name...");
              return "Alice";
            });

    // Action 2 (depends on name): Print greeting
    Function<String, Kind<IOKind.Witness, Unit>> printGreeting =
        name ->
            IO_OP.delay(
                () -> {
                  System.out.println("Effect: Printing greeting for " + name);
                  System.out.println("Welcome, " + name + "!");
                  return Unit.INSTANCE;
                });

    // Combine using flatMap
    Kind<IOKind.Witness, Unit> combinedAction = ioMonad.flatMap(printGreeting, getName);

    System.out.println("\nCombined action created, not executed yet.");
    // Execute the combined action
    IO_OP.unsafeRunSync(combinedAction);
    // Output:
    // Effect: Getting name...
    // Effect: Printing greeting for Alice
    // Welcome, Alice!

    // --- Full Program Example ---
    Kind<IOKind.Witness, Unit> program =
        ioMonad.flatMap(
            ignored ->
                ioMonad.flatMap(
                    name ->
                        ioMonad.map(
                            ignored2 -> {
                              System.out.println("Program finished.");
                              return Unit.INSTANCE;
                            },
                            printGreeting.apply(name)),
                    readLine),
            printHello);

    System.out.println("\nComplete IO Program defined. Executing...");
    IO_OP.unsafeRunSync(program);
  }

  public void utilityMethodsCompositionExample() {
    // A simulated input for repeatable demonstration
    Kind<IOKind.Witness, String> getAdminName = ioMonad.of("admin");
    Kind<IOKind.Witness, String> getAliceName = ioMonad.of("Alice");

    // Action 2 (depends on name): Print greeting
    Function<String, Kind<IOKind.Witness, Unit>> printGreeting =
        name ->
            IO_OP.delay(
                () -> {
                  System.out.println("Welcome, " + name + "!");
                  return Unit.INSTANCE;
                });

    // ✨ 1. Use `peek` to log the name without mixing logging into business logic.
    Kind<IOKind.Witness, String> loggedGetAliceName =
        ioMonad.peek(name -> System.out.println("LOG: Name obtained -> " + name), getAliceName);

    // ✨ 2. Use `flatMapIf` to conditionally execute the greeting.
    // We will only greet the user if their name is not "admin".
    Kind<IOKind.Witness, Unit> conditionalGreeting =
        ioMonad.flatMapIf(
            name -> !name.equalsIgnoreCase("admin"), // Predicate
            printGreeting, // Action if true
            loggedGetAliceName // Monadic value to test
            );

    // ✨ 3. Use `as` to signal the program's end, replacing a final `map`.
    Kind<IOKind.Witness, Unit> finalMessage =
        ioMonad.as(
            Unit.INSTANCE,
            ioMonad.peek(_ -> System.out.println("Program finished."), conditionalGreeting));

    System.out.println("\nExecuting conditional program for 'Alice':");
    IO_OP.unsafeRunSync(finalMessage);

    System.out.println("\nExecuting conditional program for 'admin' (greeting should be skipped):");
    Kind<IOKind.Witness, Unit> adminFlow =
        ioMonad.flatMapIf(name -> !name.equalsIgnoreCase("admin"), printGreeting, getAdminName);
    IO_OP.unsafeRunSync(adminFlow);
  }
}
