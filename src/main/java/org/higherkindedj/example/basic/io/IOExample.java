// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.io;

import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;

public class IOExample {
  // Get the IOMonad instance
  IOMonad ioMonad = new IOMonad();

  // IO action to print a message
  Kind<IOKind.Witness, Void> printHello =
      IOKindHelper.delay(
          () -> {
            System.out.println("Hello from IO!");
            return null; // Return type is Void
          });

  // IO action to read a line from the console
  Kind<IOKind.Witness, String> readLine =
      IOKindHelper.delay(
          () -> {
            System.out.print("Enter your name: ");
            // Scanner should ideally be managed more robustly in real apps
            try (Scanner scanner = new Scanner(System.in)) {
              return scanner.nextLine();
            }
          });

  // IO action that returns a pure value (no side effect description here)
  Kind<IOKind.Witness, Integer> pureValueIO = ioMonad.of(42);

  // IO action that simulates getting the current time (a side effect)
  Kind<IOKind.Witness, Long> currentTime = IOKindHelper.delay(System::currentTimeMillis);

  // Creating an IO action that might fail internally
  Kind<IOKind.Witness, String> potentiallyFailingIO =
      IOKindHelper.delay(
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
  }

  public void executingIO() {
    // Execute printHello
    System.out.println("Running printHello:");
    IOKindHelper.unsafeRunSync(printHello); // Actually prints "Hello from IO!"

    System.out.println("\nRunning readLine:");
    String name = IOKindHelper.unsafeRunSync(readLine);
    System.out.println("User entered: " + name);

    // Execute pureValueIO
    System.out.println("\nRunning pureValueIO:");
    Integer fetchedValue = IOKindHelper.unsafeRunSync(pureValueIO);
    System.out.println("Fetched pure value: " + fetchedValue); // Output: 42

    // Execute potentiallyFailingIO
    System.out.println("\nRunning potentiallyFailingIO:");
    try {
      String result = IOKindHelper.unsafeRunSync(potentiallyFailingIO);
      System.out.println("Succeeded: " + result);
    } catch (RuntimeException e) {
      System.err.println("Caught expected failure: " + e.getMessage());
    }

    // Notice that running the same IO action again executes the effect again
    System.out.println("\nRunning printHello again:");
    IOKindHelper.unsafeRunSync(printHello); // Prints "Hello from IO!" again
  }

  public void composingWithMapAndFlatMap() {

    // --- map example ---
    Kind<IOKind.Witness, String> readLineAction =
        IOKindHelper.delay(() -> "Test Input"); // Simulate input

    // Map the result of readLineAction without executing readLine yet
    Kind<IOKind.Witness, String> greetAction =
        ioMonad.map(
            name -> "Hello, " + name + "!", // Function to apply to the result
            readLineAction);

    System.out.println("Greet action created, not executed yet.");
    // Now execute the mapped action
    String greeting = IOKindHelper.unsafeRunSync(greetAction);
    System.out.println("Result of map: " + greeting); // Output: Hello, Test Input!

    // --- flatMap example ---
    // Action 1: Get name
    Kind<IOKind.Witness, String> getName =
        IOKindHelper.delay(
            () -> {
              System.out.println("Effect: Getting name...");
              return "Alice";
            });

    // Action 2 (depends on name): Print greeting
    Function<String, Kind<IOKind.Witness, Void>> printGreeting =
        name ->
            IOKindHelper.delay(
                () -> {
                  System.out.println("Effect: Printing greeting for " + name);
                  System.out.println("Welcome, " + name + "!");
                  return null;
                });

    // Combine using flatMap
    Kind<IOKind.Witness, Void> combinedAction = ioMonad.flatMap(printGreeting, getName);

    System.out.println("\nCombined action created, not executed yet.");
    // Execute the combined action
    IOKindHelper.unsafeRunSync(combinedAction);
    // Output:
    // Effect: Getting name...
    // Effect: Printing greeting for Alice
    // Welcome, Alice!

    // --- Full Program Example ---
    Kind<IOKind.Witness, Void> program =
        ioMonad.flatMap(
            ignored ->
                ioMonad.flatMap( // Chain after printing hello
                    name ->
                        ioMonad.map( // Map the result of printing the greeting
                            ignored2 -> {
                              System.out.println("Program finished.");
                              return null;
                            },
                            printGreeting.apply(name) // Action 3: Print greeting based on name
                            ),
                    readLine // Action 2: Read line
                    ),
            printHello // Action 1: Print Hello
            );

    System.out.println("\nComplete IO Program defined. Executing...");
    IOKindHelper.unsafeRunSync(program); // Uncomment to run the full program
  }
}
