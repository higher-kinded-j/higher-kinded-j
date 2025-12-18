// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Examples demonstrating resource management with IOPath.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>{@code bracket} - acquire/use/release pattern for any resource
 *   <li>{@code withResource} - simplified pattern for AutoCloseable resources
 *   <li>{@code guarantee} - ensure cleanup runs regardless of success/failure
 *   <li>Exception-safe resource handling
 *   <li>Nested resource management
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ResourceManagementExample}
 */
public class ResourceManagementExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Resource Management ===\n");

    bracketBasicExample();
    bracketWithExceptionExample();
    withResourceExample();
    guaranteeExample();
    nestedResourcesExample();
    realWorldFileExample();
  }

  private static void bracketBasicExample() {
    System.out.println("--- Basic bracket() Usage ---");

    // bracket(acquire, use, release) guarantees:
    // 1. acquire runs first
    // 2. use runs with the acquired resource
    // 3. release ALWAYS runs, even if use throws

    AtomicBoolean resourceAcquired = new AtomicBoolean(false);
    AtomicBoolean resourceReleased = new AtomicBoolean(false);

    IOPath<String> program =
        IOPath.bracket(
            // Acquire
            () -> {
              System.out.println("  [Acquire] Opening resource");
              resourceAcquired.set(true);
              return new SimulatedResource("MyResource");
            },
            // Use
            resource -> {
              System.out.println("  [Use] Using " + resource.name);
              return "Result from " + resource.name;
            },
            // Release
            resource -> {
              System.out.println("  [Release] Closing " + resource.name);
              resourceReleased.set(true);
            });

    System.out.println(
        "Before run - acquired: "
            + resourceAcquired.get()
            + ", released: "
            + resourceReleased.get());

    String result = program.unsafeRun();

    System.out.println("Result: " + result);
    System.out.println(
        "After run - acquired: "
            + resourceAcquired.get()
            + ", released: "
            + resourceReleased.get());
    System.out.println();
  }

  private static void bracketWithExceptionExample() {
    System.out.println("--- bracket() with Exception ---");

    // Even when use throws, release is guaranteed to run

    AtomicBoolean released = new AtomicBoolean(false);

    IOPath<String> failingProgram =
        IOPath.bracket(
            // Acquire
            () -> {
              System.out.println("  [Acquire] Opening resource");
              return new SimulatedResource("CriticalResource");
            },
            // Use - throws exception!
            resource -> {
              System.out.println("  [Use] About to fail...");
              throw new RuntimeException("Simulated failure!");
            },
            // Release - still runs!
            resource -> {
              System.out.println("  [Release] Cleaning up despite error");
              released.set(true);
            });

    try {
      failingProgram.unsafeRun();
    } catch (RuntimeException e) {
      System.out.println("Caught: " + e.getMessage());
    }

    System.out.println("Resource was released: " + released.get());
    System.out.println();
  }

  private static void withResourceExample() {
    System.out.println("--- withResource() for AutoCloseable ---");

    // withResource is a simplified bracket for AutoCloseable resources
    // Automatically calls close() in the release phase

    // Create a temp file for demonstration
    java.nio.file.Path tempFile;
    try {
      tempFile = Files.createTempFile("hkj-example-", ".txt");
      Files.writeString(tempFile, "Hello from Higher-Kinded-J!");
    } catch (IOException e) {
      System.out.println("Could not create temp file: " + e.getMessage());
      return;
    }

    final java.nio.file.Path finalTempFile = tempFile;
    IOPath<String> program =
        IOPath.withResource(
            // Acquire - returns an AutoCloseable
            () -> {
              try {
                System.out.println("  [Acquire] Opening BufferedReader");
                return new BufferedReader(new FileReader(finalTempFile.toFile()));
              } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
              }
            },
            // Use - reader will be auto-closed after
            reader -> {
              try {
                System.out.println("  [Use] Reading file");
                return reader.readLine();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });

    try {
      String content = program.unsafeRun();
      System.out.println("File content: " + content);
      System.out.println("Reader was automatically closed!");
    } catch (Exception e) {
      System.out.println("Error: " + e.getMessage());
    }

    // Cleanup temp file
    try {
      Files.deleteIfExists(tempFile);
    } catch (IOException ignored) {
    }

    System.out.println();
  }

  private static void guaranteeExample() {
    System.out.println("--- guarantee() for Cleanup ---");

    // guarantee ensures a finalizer runs regardless of success or failure
    // Useful for cleanup that doesn't depend on the operation's result

    AtomicInteger cleanupCount = new AtomicInteger(0);

    // Success case
    IOPath<String> successProgram =
        Path.io(() -> "Success!")
            .guarantee(
                () -> {
                  System.out.println("  [Cleanup] Running after success");
                  cleanupCount.incrementAndGet();
                });

    String result = successProgram.unsafeRun();
    System.out.println("Result: " + result);

    // Failure case
    IOPath<String> failProgram =
        Path.<String>io(
                () -> {
                  throw new RuntimeException("Oops!");
                })
            .guarantee(
                () -> {
                  System.out.println("  [Cleanup] Running after failure");
                  cleanupCount.incrementAndGet();
                });

    try {
      failProgram.unsafeRun();
    } catch (RuntimeException e) {
      System.out.println("Caught: " + e.getMessage());
    }

    System.out.println("Total cleanups run: " + cleanupCount.get());
    System.out.println();
  }

  private static void nestedResourcesExample() {
    System.out.println("--- Nested Resources ---");

    // Multiple resources can be nested safely
    // Each will be released in reverse order of acquisition

    AtomicInteger releaseOrder = new AtomicInteger(0);

    IOPath<String> nestedProgram =
        IOPath.bracket(
            // Acquire outer
            () -> {
              System.out.println("  [Acquire] Outer resource");
              return new SimulatedResource("Outer");
            },
            // Use outer - which acquires inner
            outer ->
                IOPath.bracket(
                        // Acquire inner
                        () -> {
                          System.out.println("  [Acquire] Inner resource");
                          return new SimulatedResource("Inner");
                        },
                        // Use both
                        inner -> {
                          System.out.println("  [Use] Using " + outer.name + " and " + inner.name);
                          return "Combined: " + outer.name + " + " + inner.name;
                        },
                        // Release inner (runs FIRST)
                        inner -> {
                          int order = releaseOrder.incrementAndGet();
                          System.out.println("  [Release #" + order + "] " + inner.name);
                        })
                    .unsafeRun(), // Run inner bracket
            // Release outer (runs SECOND)
            outer -> {
              int order = releaseOrder.incrementAndGet();
              System.out.println("  [Release #" + order + "] " + outer.name);
            });

    String result = nestedProgram.unsafeRun();
    System.out.println("Result: " + result);
    System.out.println();
  }

  private static void realWorldFileExample() {
    System.out.println("--- Real-World: File Processing ---");

    // A realistic example: read from one file, write to another

    java.nio.file.Path inputFile;
    java.nio.file.Path outputFile;
    try {
      inputFile = Files.createTempFile("hkj-input-", ".txt");
      outputFile = Files.createTempFile("hkj-output-", ".txt");
      Files.writeString(inputFile, "Line 1\nLine 2\nLine 3");
    } catch (IOException e) {
      System.out.println("Could not create temp files: " + e.getMessage());
      return;
    }

    final java.nio.file.Path finalInputFile = inputFile;
    final java.nio.file.Path finalOutputFile = outputFile;
    // Process: read lines, transform, write to output
    IOPath<Integer> processProgram =
        IOPath.bracket(
            // Acquire reader
            () -> {
              try {
                return new BufferedReader(new FileReader(finalInputFile.toFile()));
              } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
              }
            },
            reader ->
                IOPath.bracket(
                        // Acquire writer
                        () -> {
                          try {
                            return new BufferedWriter(new FileWriter(finalOutputFile.toFile()));
                          } catch (IOException e) {
                            throw new RuntimeException(e);
                          }
                        },
                        writer -> {
                          try {
                            int lineCount = 0;
                            String line;
                            while ((line = reader.readLine()) != null) {
                              writer.write(line.toUpperCase());
                              writer.newLine();
                              lineCount++;
                            }
                            return lineCount;
                          } catch (IOException e) {
                            throw new RuntimeException(e);
                          }
                        },
                        writer -> {
                          try {
                            writer.close();
                            System.out.println("  Writer closed");
                          } catch (IOException e) {
                            System.out.println("  Error closing writer: " + e.getMessage());
                          }
                        })
                    .unsafeRun(),
            reader -> {
              try {
                reader.close();
                System.out.println("  Reader closed");
              } catch (IOException e) {
                System.out.println("  Error closing reader: " + e.getMessage());
              }
            });

    try {
      int count = processProgram.unsafeRun();
      System.out.println("Processed " + count + " lines");

      String output = Files.readString(finalOutputFile);
      System.out.println("Output file contents:\n" + output);
    } catch (Exception e) {
      System.out.println("Error: " + e.getMessage());
    }

    // Cleanup
    try {
      Files.deleteIfExists(finalInputFile);
      Files.deleteIfExists(finalOutputFile);
    } catch (IOException ignored) {
    }

    System.out.println();
  }

  // Helper class
  private record SimulatedResource(String name) {}
}
