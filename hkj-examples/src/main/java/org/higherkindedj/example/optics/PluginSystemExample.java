// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A runnable example demonstrating extensible plugin architectures using prisms for type-safe
 * plugin discovery and invocation.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Type-safe plugin variant handling with sealed interfaces
 *   <li>Plugin capability detection using {@code matches()}
 *   <li>Dynamic plugin dispatch with {@code getOptional()} and {@code mapOptional()}
 *   <li>Plugin chaining and composition using {@code orElse()}
 *   <li>Plugin configuration using {@code modifyWhen()}
 * </ul>
 */
public class PluginSystemExample {

  // Plugin type hierarchy
  @GeneratePrisms
  public sealed interface Plugin permits DataSourcePlugin, TransformPlugin, OutputPlugin {}

  // Data source plugins
  @GeneratePrisms
  public sealed interface DataSourcePlugin extends Plugin
      permits FileSourcePlugin, DatabaseSourcePlugin, ApiSourcePlugin {}

  @GenerateLenses
  public record FileSourcePlugin(String path, String format) implements DataSourcePlugin {}

  @GenerateLenses
  public record DatabaseSourcePlugin(String connectionString, String query)
      implements DataSourcePlugin {}

  @GenerateLenses
  public record ApiSourcePlugin(String endpoint, Map<String, String> headers)
      implements DataSourcePlugin {}

  // Transform plugins
  @GeneratePrisms
  public sealed interface TransformPlugin extends Plugin
      permits FilterPlugin, MapPlugin, AggregatePlugin {}

  public record FilterPlugin(String condition) implements TransformPlugin {}

  public record MapPlugin(String transformation) implements TransformPlugin {}

  public record AggregatePlugin(String aggregationType, String groupBy)
      implements TransformPlugin {}

  // Output plugins
  @GeneratePrisms
  public sealed interface OutputPlugin extends Plugin
      permits ConsoleOutputPlugin, FileOutputPlugin, DatabaseOutputPlugin {}

  public record ConsoleOutputPlugin(boolean prettyPrint) implements OutputPlugin {}

  public record FileOutputPlugin(String path, String format) implements OutputPlugin {}

  public record DatabaseOutputPlugin(String connectionString, String table)
      implements OutputPlugin {}

  // Plugin execution context
  public record PluginContext(Map<String, Object> config, List<String> executionLog) {}

  // Prisms for plugin type access
  private static final Prism<Plugin, DataSourcePlugin> DATA_SOURCE =
      PluginPrisms.dataSourcePlugin();
  private static final Prism<Plugin, TransformPlugin> TRANSFORM = PluginPrisms.transformPlugin();
  private static final Prism<Plugin, OutputPlugin> OUTPUT = PluginPrisms.outputPlugin();

  private static final Prism<DataSourcePlugin, FileSourcePlugin> FILE_SOURCE =
      DataSourcePluginPrisms.fileSourcePlugin();
  private static final Prism<DataSourcePlugin, DatabaseSourcePlugin> DB_SOURCE =
      DataSourcePluginPrisms.databaseSourcePlugin();
  private static final Prism<DataSourcePlugin, ApiSourcePlugin> API_SOURCE =
      DataSourcePluginPrisms.apiSourcePlugin();

  private static final Prism<TransformPlugin, FilterPlugin> FILTER =
      TransformPluginPrisms.filterPlugin();
  private static final Prism<TransformPlugin, MapPlugin> MAP = TransformPluginPrisms.mapPlugin();

  private static final Prism<OutputPlugin, ConsoleOutputPlugin> CONSOLE_OUTPUT =
      OutputPluginPrisms.consoleOutputPlugin();
  private static final Prism<OutputPlugin, FileOutputPlugin> FILE_OUTPUT =
      OutputPluginPrisms.fileOutputPlugin();

  public static void main(String[] args) {
    System.out.println("=== Plugin System with Prisms ===\n");

    demonstratePluginDiscovery();
    demonstratePluginExecution();
    demonstratePluginComposition();
    demonstratePluginConfiguration();
    demonstratePipelineConstruction();
  }

  private static void demonstratePluginDiscovery() {
    System.out.println("--- Plugin Discovery ---");

    List<Plugin> availablePlugins =
        List.of(
            new FileSourcePlugin("/data/input.json", "json"),
            new FilterPlugin("age > 18"),
            new MapPlugin("toUpperCase(name)"),
            new ConsoleOutputPlugin(true),
            new DatabaseSourcePlugin("jdbc:postgresql://localhost/db", "SELECT * FROM users"),
            new FileOutputPlugin("/data/output.csv", "csv"));

    // Discover plugins by capability
    List<Plugin> dataSources = availablePlugins.stream().filter(DATA_SOURCE::matches).toList();

    List<Plugin> transforms = availablePlugins.stream().filter(TRANSFORM::matches).toList();

    List<Plugin> outputs = availablePlugins.stream().filter(OUTPUT::matches).toList();

    System.out.println("Discovered plugins:");
    System.out.println("  Data sources: " + dataSources.size());
    System.out.println("  Transforms:   " + transforms.size());
    System.out.println("  Outputs:      " + outputs.size());

    // Find specific plugin types
    long fileSourceCount =
        dataSources.stream().filter(p -> DATA_SOURCE.andThen(FILE_SOURCE).matches(p)).count();

    long dbSourceCount =
        dataSources.stream().filter(p -> DATA_SOURCE.andThen(DB_SOURCE).matches(p)).count();

    System.out.println("\nData source breakdown:");
    System.out.println("  File sources:     " + fileSourceCount);
    System.out.println("  Database sources: " + dbSourceCount);

    System.out.println();
  }

  private static void demonstratePluginExecution() {
    System.out.println("--- Plugin Execution ---");

    Plugin fileSource = new FileSourcePlugin("/data/customers.json", "json");
    Plugin filter = new FilterPlugin("country == 'UK'");
    Plugin consoleOut = new ConsoleOutputPlugin(true);

    // Execute data source plugin
    System.out.println("Executing data source plugin:");
    String sourceResult = executeDataSource(fileSource);
    System.out.println("  " + sourceResult);

    // Execute transform plugin
    System.out.println("\nExecuting transform plugin:");
    String transformResult = executeTransform(filter, "sample data");
    System.out.println("  " + transformResult);

    // Execute output plugin
    System.out.println("\nExecuting output plugin:");
    String outputResult = executeOutput(consoleOut, "final data");
    System.out.println("  " + outputResult);

    System.out.println();
  }

  private static void demonstratePluginComposition() {
    System.out.println("--- Plugin Composition ---");

    // Build a composite data source that tries multiple sources
    Plugin primarySource = new FileSourcePlugin("/data/primary.json", "json");
    Plugin fallbackSource = new DatabaseSourcePlugin("jdbc:h2:mem:test", "SELECT * FROM backup");
    Plugin emergencySource = new ApiSourcePlugin("https://api.example.com/data", Map.of());

    // Extract source information using mapOptional for each plugin
    System.out.println("Composite source resolution:");
    String primaryPath =
        DATA_SOURCE
            .andThen(FILE_SOURCE)
            .mapOptional(FileSourcePlugin::path, primarySource)
            .or(
                () ->
                    DATA_SOURCE
                        .andThen(DB_SOURCE)
                        .mapOptional(DatabaseSourcePlugin::connectionString, primarySource))
            .or(
                () ->
                    DATA_SOURCE
                        .andThen(API_SOURCE)
                        .mapOptional(ApiSourcePlugin::endpoint, primarySource))
            .orElse("N/A");
    System.out.println("  Primary:   " + primaryPath);

    String fallbackPath =
        DATA_SOURCE
            .andThen(FILE_SOURCE)
            .mapOptional(FileSourcePlugin::path, fallbackSource)
            .or(
                () ->
                    DATA_SOURCE
                        .andThen(DB_SOURCE)
                        .mapOptional(DatabaseSourcePlugin::connectionString, fallbackSource))
            .or(
                () ->
                    DATA_SOURCE
                        .andThen(API_SOURCE)
                        .mapOptional(ApiSourcePlugin::endpoint, fallbackSource))
            .orElse("N/A");
    System.out.println("  Fallback:  " + fallbackPath);

    String emergencyPath =
        DATA_SOURCE
            .andThen(FILE_SOURCE)
            .mapOptional(FileSourcePlugin::path, emergencySource)
            .or(
                () ->
                    DATA_SOURCE
                        .andThen(DB_SOURCE)
                        .mapOptional(DatabaseSourcePlugin::connectionString, emergencySource))
            .or(
                () ->
                    DATA_SOURCE
                        .andThen(API_SOURCE)
                        .mapOptional(ApiSourcePlugin::endpoint, emergencySource))
            .orElse("N/A");
    System.out.println("  Emergency: " + emergencyPath);

    // Chain transform plugins
    Plugin filter = new FilterPlugin("status == 'active'");
    Plugin mapper = new MapPlugin("extract(email)");

    System.out.println("\nChained transforms:");
    String data = "raw data";
    String filtered = executeTransform(filter, data);
    String mapped = executeTransform(mapper, filtered);
    System.out.println("  Result: " + mapped);

    System.out.println();
  }

  private static void demonstratePluginConfiguration() {
    System.out.println("--- Plugin Configuration ---");

    Plugin consolePlugin = new ConsoleOutputPlugin(false);
    System.out.println("Original console plugin: " + consolePlugin);

    // Enable pretty printing conditionally
    Plugin configured =
        OUTPUT.modifyWhen(
            out -> CONSOLE_OUTPUT.matches(out),
            out ->
                Optional.ofNullable(
                        CONSOLE_OUTPUT.modify(console -> new ConsoleOutputPlugin(true), out))
                    .orElse(out),
            consolePlugin);

    System.out.println("Configured console plugin: " + configured);

    // Update file paths for all file-based plugins
    Plugin fileSource = new FileSourcePlugin("/tmp/old.json", "json");
    Plugin fileOutput = new FileOutputPlugin("/tmp/old.csv", "csv");

    Plugin updatedSource =
        DATA_SOURCE.modify(
            ds ->
                Optional.ofNullable(
                        FILE_SOURCE.modify(
                            fs ->
                                new FileSourcePlugin(
                                    fs.path().replace("/tmp/", "/data/"), fs.format()),
                            ds))
                    .orElse(ds),
            fileSource);

    Plugin updatedOutput =
        OUTPUT.modify(
            out ->
                Optional.ofNullable(
                        FILE_OUTPUT.modify(
                            fo ->
                                new FileOutputPlugin(
                                    fo.path().replace("/tmp/", "/data/"), fo.format()),
                            out))
                    .orElse(out),
            fileOutput);

    System.out.println("\nPath updates:");
    System.out.println("  Source: " + fileSource + " -> " + updatedSource);
    System.out.println("  Output: " + fileOutput + " -> " + updatedOutput);

    System.out.println();
  }

  private static void demonstratePipelineConstruction() {
    System.out.println("--- Data Processing Pipeline Construction ---");

    List<Plugin> pipeline =
        List.of(
            new FileSourcePlugin("/data/customers.json", "json"),
            new FilterPlugin("age >= 18"),
            new FilterPlugin("country == 'UK'"),
            new MapPlugin("anonymise(email)"),
            new AggregatePlugin("count", "city"),
            new FileOutputPlugin("/data/output.csv", "csv"));

    System.out.println("Pipeline stages:");

    for (int i = 0; i < pipeline.size(); i++) {
      Plugin plugin = pipeline.get(i);
      String stage = String.format("%d. ", i + 1);

      if (DATA_SOURCE.matches(plugin)) {
        String source =
            DATA_SOURCE
                .andThen(FILE_SOURCE)
                .mapOptional(fs -> "Read from file: " + fs.path(), plugin)
                .or(
                    () ->
                        DATA_SOURCE
                            .andThen(DB_SOURCE)
                            .mapOptional(db -> "Query database: " + db.query(), plugin))
                .or(
                    () ->
                        DATA_SOURCE
                            .andThen(API_SOURCE)
                            .mapOptional(api -> "Fetch from API: " + api.endpoint(), plugin))
                .orElse("Unknown source");
        System.out.println(stage + source);

      } else if (TRANSFORM.matches(plugin)) {
        String transform =
            TRANSFORM
                .andThen(FILTER)
                .mapOptional(f -> "Filter: " + f.condition(), plugin)
                .or(
                    () ->
                        TRANSFORM
                            .andThen(MAP)
                            .mapOptional(m -> "Map: " + m.transformation(), plugin))
                .or(
                    () ->
                        TRANSFORM
                            .andThen(TransformPluginPrisms.aggregatePlugin())
                            .mapOptional(
                                a -> "Aggregate: " + a.aggregationType() + " by " + a.groupBy(),
                                plugin))
                .orElse("Unknown transform");
        System.out.println(stage + transform);

      } else if (OUTPUT.matches(plugin)) {
        String output =
            OUTPUT
                .andThen(FILE_OUTPUT)
                .mapOptional(fo -> "Write to file: " + fo.path(), plugin)
                .or(
                    () ->
                        OUTPUT
                            .andThen(CONSOLE_OUTPUT)
                            .mapOptional(
                                co ->
                                    "Write to console"
                                        + (co.prettyPrint() ? " (pretty)" : " (compact)"),
                                plugin))
                .orElse("Unknown output");
        System.out.println(stage + output);
      }
    }

    // Validate pipeline structure
    boolean hasSource = pipeline.stream().anyMatch(DATA_SOURCE::matches);
    boolean hasOutput = pipeline.stream().anyMatch(OUTPUT::matches);
    long transformCount = pipeline.stream().filter(TRANSFORM::matches).count();

    System.out.println("\nPipeline validation:");
    System.out.println("  Has data source: " + hasSource);
    System.out.println("  Has output:      " + hasOutput);
    System.out.println("  Transform steps: " + transformCount);
    System.out.println("  Valid pipeline:  " + (hasSource && hasOutput));

    System.out.println();
  }

  // Helper methods for plugin execution

  private static String executeDataSource(Plugin plugin) {
    return DATA_SOURCE
        .andThen(FILE_SOURCE)
        .mapOptional(fs -> "Reading data from " + fs.path() + " (" + fs.format() + ")", plugin)
        .or(
            () ->
                DATA_SOURCE
                    .andThen(DB_SOURCE)
                    .mapOptional(db -> "Querying database: " + db.query(), plugin))
        .or(
            () ->
                DATA_SOURCE
                    .andThen(API_SOURCE)
                    .mapOptional(api -> "Fetching from " + api.endpoint(), plugin))
        .orElse("Unknown data source");
  }

  private static String executeTransform(Plugin plugin, String data) {
    return TRANSFORM
        .andThen(FILTER)
        .mapOptional(f -> "Filtering '" + data + "' with condition: " + f.condition(), plugin)
        .or(
            () ->
                TRANSFORM
                    .andThen(MAP)
                    .mapOptional(
                        m -> "Transforming '" + data + "' with: " + m.transformation(), plugin))
        .orElse("No transform applied to: " + data);
  }

  private static String executeOutput(Plugin plugin, String data) {
    return OUTPUT
        .andThen(CONSOLE_OUTPUT)
        .mapOptional(
            co -> "Writing to console" + (co.prettyPrint() ? " (pretty): " : ": ") + data, plugin)
        .or(
            () ->
                OUTPUT
                    .andThen(FILE_OUTPUT)
                    .mapOptional(
                        fo -> "Writing to file " + fo.path() + " (" + fo.format() + "): " + data,
                        plugin))
        .orElse("No output performed");
  }
}
