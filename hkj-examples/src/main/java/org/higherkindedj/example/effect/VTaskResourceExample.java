// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Examples demonstrating Resource management with the bracket pattern for VTask.
 *
 * <p>Resource provides safe resource management for VTask computations, implementing the bracket
 * pattern (acquire-use-release). Resources are always released, even when exceptions occur or tasks
 * are cancelled.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>fromAutoCloseable - wrapping AutoCloseable resources
 *   <li>make - custom acquire/release logic
 *   <li>pure - wrapping values with no cleanup
 *   <li>use - running computations with managed resources
 *   <li>map/flatMap - transforming and chaining resources
 *   <li>and - combining multiple resources (LIFO release)
 *   <li>withFinalizer - adding cleanup actions
 *   <li>Resource + Scope integration
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.VTaskResourceExample}
 *
 * @see org.higherkindedj.hkt.vtask.Resource
 * @see org.higherkindedj.hkt.vtask.Scope
 */
public class VTaskResourceExample {

  public static void main(String[] args) {
    System.out.println("=== Resource Management with Bracket Pattern ===\n");

    basicResourceUsage();
    makeWithCustomRelease();
    pureResourceExample();
    resourceComposition();
    finalizerExample();
    exceptionSafetyExample();
    resourceWithScopeIntegration();
    realWorldFileProcessing();
  }

  // ============================================================
  // Basic Resource Usage with fromAutoCloseable
  // ============================================================

  private static void basicResourceUsage() {
    System.out.println("--- Basic Resource Usage ---\n");

    // Create a temp file for demonstration
    Path tempFile;
    try {
      tempFile = Files.createTempFile("hkj-resource-", ".txt");
      Files.writeString(tempFile, "Hello from Higher-Kinded-J Resource!");
    } catch (IOException e) {
      System.out.println("Could not create temp file: " + e.getMessage());
      return;
    }

    // Create a Resource from AutoCloseable
    final Path finalTempFile = tempFile;
    Resource<BufferedReader> readerResource =
        Resource.fromAutoCloseable(
            () -> {
              try {
                System.out.println("  [Acquire] Opening BufferedReader");
                return new BufferedReader(new FileReader(finalTempFile.toFile()));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });

    // Use the resource - automatically closed after use
    VTask<String> readTask =
        readerResource.use(
            reader ->
                VTask.of(
                    () -> {
                      try {
                        System.out.println("  [Use] Reading file");
                        return reader.readLine();
                      } catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    }));

    // Execute - resource is managed automatically
    Try<String> result = readTask.runSafe();
    System.out.println("  [Released] Reader automatically closed");
    System.out.println("Content: " + result.orElse("error"));

    // Cleanup temp file
    try {
      Files.deleteIfExists(tempFile);
    } catch (IOException ignored) {
    }
    System.out.println();
  }

  // ============================================================
  // Custom Resource with make()
  // ============================================================

  private static void makeWithCustomRelease() {
    System.out.println("--- Custom Resource with make() ---\n");

    AtomicBoolean acquired = new AtomicBoolean(false);
    AtomicBoolean released = new AtomicBoolean(false);

    // Create a resource with explicit acquire and release functions
    Resource<SimulatedConnection> connResource =
        Resource.make(
            () -> {
              System.out.println("  [Acquire] Opening connection");
              acquired.set(true);
              return new SimulatedConnection("db-connection-1");
            },
            conn -> {
              System.out.println("  [Release] Closing " + conn.id);
              released.set(true);
            });

    System.out.println(
        "Before use - acquired: " + acquired.get() + ", released: " + released.get());

    VTask<String> queryTask =
        connResource.use(
            conn ->
                VTask.of(
                    () -> {
                      System.out.println("  [Use] Executing query on " + conn.id);
                      return "Query result from " + conn.id;
                    }));

    String result = queryTask.runSafe().orElse("error");

    System.out.println("After use - acquired: " + acquired.get() + ", released: " + released.get());
    System.out.println("Result: " + result + "\n");
  }

  // ============================================================
  // Pure Resource (no cleanup needed)
  // ============================================================

  private static void pureResourceExample() {
    System.out.println("--- Pure Resource (no cleanup) ---\n");

    // Use pure for values that don't need cleanup
    // Useful for configuration, constants, or pre-initialised values
    record Config(String host, int port) {}

    Resource<Config> configResource = Resource.pure(new Config("localhost", 8080));

    VTask<String> connectionString =
        configResource.use(config -> VTask.succeed(config.host + ":" + config.port));

    String result = connectionString.runSafe().orElse("error");
    System.out.println("Connection string: " + result + "\n");
  }

  // ============================================================
  // Resource Composition
  // ============================================================

  private static void resourceComposition() {
    System.out.println("--- Resource Composition ---\n");

    AtomicInteger releaseOrder = new AtomicInteger(0);

    // Create two resources
    Resource<SimulatedConnection> connResource =
        Resource.make(
            () -> {
              System.out.println("  [Acquire] Connection");
              return new SimulatedConnection("conn");
            },
            conn -> {
              int order = releaseOrder.incrementAndGet();
              System.out.println("  [Release #" + order + "] " + conn.id);
            });

    Resource<SimulatedFile> fileResource =
        Resource.make(
            () -> {
              System.out.println("  [Acquire] File");
              return new SimulatedFile("file.txt");
            },
            file -> {
              int order = releaseOrder.incrementAndGet();
              System.out.println("  [Release #" + order + "] " + file.name);
            });

    // Combine with and() - releases in LIFO order
    Resource<Par.Tuple2<SimulatedConnection, SimulatedFile>> combined =
        connResource.and(fileResource);

    VTask<String> task =
        combined.use(
            tuple -> {
              SimulatedConnection conn = tuple.first();
              SimulatedFile file = tuple.second();
              System.out.println("  [Use] Using " + conn.id + " and " + file.name);
              return VTask.succeed("Processed with " + conn.id + " and " + file.name);
            });

    String result = task.runSafe().orElse("error");
    System.out.println("Result: " + result);
    System.out.println("(Note: file released before conn - LIFO order)\n");

    // Demonstrate map
    Resource<String> connId = connResource.map(conn -> conn.id);
    VTask<String> idTask = connId.use(id -> VTask.succeed("Connection ID: " + id));
    System.out.println("map result: " + idTask.runSafe().orElse("error"));

    // Demonstrate flatMap for dependent resources
    releaseOrder.set(0);
    Resource<SimulatedStatement> stmtResource =
        connResource.flatMap(
            conn ->
                Resource.make(
                    () -> {
                      System.out.println("  [Acquire] Statement (depends on " + conn.id + ")");
                      return new SimulatedStatement(conn.id + "-stmt");
                    },
                    stmt -> {
                      int order = releaseOrder.incrementAndGet();
                      System.out.println("  [Release #" + order + "] " + stmt.id);
                    }));

    VTask<String> stmtTask = stmtResource.use(stmt -> VTask.succeed("Executed: " + stmt.id));
    System.out.println("flatMap result: " + stmtTask.runSafe().orElse("error") + "\n");
  }

  // ============================================================
  // Finalizer Example
  // ============================================================

  private static void finalizerExample() {
    System.out.println("--- Resource with Finalizers ---\n");

    AtomicInteger cleanupOrder = new AtomicInteger(0);

    Resource<SimulatedConnection> conn =
        Resource.make(
                () -> {
                  System.out.println("  [Acquire] Connection");
                  return new SimulatedConnection("conn");
                },
                c -> {
                  int order = cleanupOrder.incrementAndGet();
                  System.out.println("  [Release #" + order + "] Primary release");
                })
            .withFinalizer(
                () -> {
                  int order = cleanupOrder.incrementAndGet();
                  System.out.println("  [Finalizer #" + order + "] Logging cleanup");
                })
            .withFinalizer(
                () -> {
                  int order = cleanupOrder.incrementAndGet();
                  System.out.println("  [Finalizer #" + order + "] Metrics recording");
                });

    VTask<String> task = conn.use(c -> VTask.succeed("Used " + c.id));
    task.runSafe();
    System.out.println("(Finalizers run in reverse order of addition)\n");
  }

  // ============================================================
  // Exception Safety
  // ============================================================

  private static void exceptionSafetyExample() {
    System.out.println("--- Exception Safety ---\n");

    AtomicBoolean released = new AtomicBoolean(false);

    Resource<SimulatedConnection> conn =
        Resource.make(
            () -> {
              System.out.println("  [Acquire] Connection");
              return new SimulatedConnection("conn");
            },
            c -> {
              System.out.println("  [Release] Connection (even though use failed!)");
              released.set(true);
            });

    // Use function throws an exception
    VTask<String> failingTask =
        conn.use(
            c ->
                VTask.of(
                    () -> {
                      System.out.println("  [Use] About to throw...");
                      throw new RuntimeException("Simulated failure");
                    }));

    Try<String> result = failingTask.runSafe();
    String message =
        result.fold(value -> "Success: " + value, error -> "Failed: " + error.getMessage());

    System.out.println("Result: " + message);
    System.out.println("Resource was released: " + released.get() + "\n");
  }

  // ============================================================
  // Resource + Scope Integration
  // ============================================================

  private static void resourceWithScopeIntegration() {
    System.out.println("--- Resource + Scope Integration ---\n");

    // Create two connection resources
    Resource<SimulatedConnection> conn1 =
        Resource.make(
            () -> {
              System.out.println("  [Acquire] Connection 1");
              return new SimulatedConnection("conn-1");
            },
            c -> System.out.println("  [Release] " + c.id));

    Resource<SimulatedConnection> conn2 =
        Resource.make(
            () -> {
              System.out.println("  [Acquire] Connection 2");
              return new SimulatedConnection("conn-2");
            },
            c -> System.out.println("  [Release] " + c.id));

    // Use resources within a scope for parallel queries
    VTask<List<String>> parallelQueries =
        conn1
            .and(conn2)
            .use(
                conns ->
                    Scope.<String>allSucceed()
                        .fork(
                            VTask.of(
                                () -> {
                                  sleep(100);
                                  return "Result from " + conns.first().id;
                                }))
                        .fork(
                            VTask.of(
                                () -> {
                                  sleep(100);
                                  return "Result from " + conns.second().id;
                                }))
                        .join());

    long start = System.currentTimeMillis();
    List<String> results = parallelQueries.runSafe().orElse(List.of());
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("Results: " + results);
    System.out.println("Elapsed: " + elapsed + "ms (parallel, ~100ms expected)");
    System.out.println("(Both connections released after scope completes)\n");
  }

  // ============================================================
  // Real-World File Processing
  // ============================================================

  private static void realWorldFileProcessing() {
    System.out.println("--- Real-World: File Processing ---\n");

    // Create temp files
    Path inputFile;
    Path outputFile;
    try {
      inputFile = Files.createTempFile("hkj-input-", ".txt");
      outputFile = Files.createTempFile("hkj-output-", ".txt");
      Files.writeString(inputFile, "Line 1\nLine 2\nLine 3");
    } catch (IOException e) {
      System.out.println("Could not create temp files: " + e.getMessage());
      return;
    }

    final Path finalInputFile = inputFile;
    final Path finalOutputFile = outputFile;

    // Create reader resource
    Resource<BufferedReader> readerResource =
        Resource.fromAutoCloseable(
            () -> {
              try {
                System.out.println("  [Acquire] Reader");
                return new BufferedReader(new FileReader(finalInputFile.toFile()));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });

    // Create writer resource
    Resource<BufferedWriter> writerResource =
        Resource.fromAutoCloseable(
            () -> {
              try {
                System.out.println("  [Acquire] Writer");
                return new BufferedWriter(new FileWriter(finalOutputFile.toFile()));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });

    // Process: read lines, transform, write to output
    VTask<Integer> processTask =
        readerResource
            .and(writerResource)
            .use(
                tuple -> {
                  BufferedReader reader = tuple.first();
                  BufferedWriter writer = tuple.second();

                  return VTask.of(
                      () -> {
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
                      });
                });

    Try<Integer> result = processTask.runSafe();
    System.out.println("  [Released] Both reader and writer closed");

    String message =
        result.fold(
            count -> "Processed " + count + " lines", error -> "Error: " + error.getMessage());
    System.out.println(message);

    // Verify output
    try {
      String output = Files.readString(outputFile);
      System.out.println("Output file contents:\n" + output);
    } catch (IOException e) {
      System.out.println("Could not read output: " + e.getMessage());
    }

    // Cleanup
    try {
      Files.deleteIfExists(inputFile);
      Files.deleteIfExists(outputFile);
    } catch (IOException ignored) {
    }
    System.out.println();
  }

  // Helper records
  record SimulatedConnection(String id) {}

  record SimulatedFile(String name) {}

  record SimulatedStatement(String id) {}

  // Helper for sleep
  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
