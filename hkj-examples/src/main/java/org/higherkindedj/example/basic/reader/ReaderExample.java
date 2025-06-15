// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.reader.ReaderMonad;

/** see {<a href="https://higher-kinded-j.github.io/reader_monad.html">Reader Monad</a>} */
public class ReaderExample {

  // Reader that retrieves the database URL from the config
  Kind<ReaderKind.Witness<AppConfig>, String> getDbUrl = READER.reader(AppConfig::databaseUrl);
  // Reader that retrieves the timeout
  Kind<ReaderKind.Witness<AppConfig>, Integer> getTimeout = READER.reader(AppConfig::timeoutMillis);
  // Reader that returns a constant value, ignoring the environment
  Kind<ReaderKind.Witness<AppConfig>, String> getDefaultUser = READER.constant("guest");
  // Reader that returns the entire configuration environment
  Kind<ReaderKind.Witness<AppConfig>, AppConfig> getConfig = READER.ask();
  // Monad instance for computations depending on AppConfig
  ReaderMonad<AppConfig> readerMonad = ReaderMonad.instance();
  // Example 1: Map the timeout value
  Kind<ReaderKind.Witness<AppConfig>, String> timeoutMessage =
      readerMonad.map(
          timeout -> "Timeout is: " + timeout + "ms",
          getTimeout // Input: Kind<ReaderKind.Witness<AppConfig>, Integer>
          );
  // Example 2: Use flatMap to get DB URL and then construct a connection string (depends on URL)
  Function<String, Kind<ReaderKind.Witness<AppConfig>, String>> buildConnectionString =
      dbUrl ->
          READER.reader( // <- We return a new Reader computation
              config -> dbUrl + "?apiKey=" + config.apiKey() // Access apiKey via the 'config' env
              );
  Kind<ReaderKind.Witness<AppConfig>, String> connectionStringReader =
      readerMonad.flatMap(
          buildConnectionString, // Function: String -> Kind<ReaderKind.Witness<AppConfig>, String>
          getDbUrl // Input: Kind<ReaderKind.Witness<AppConfig>, String>
          );
  // Example 3: Combine multiple values using mapN (from Applicative)
  Kind<ReaderKind.Witness<AppConfig>, String> dbInfo =
      readerMonad.map2(
          getDbUrl, getTimeout, (url, timeout) -> "DB: " + url + " (Timeout: " + timeout + ")");

  public static void main(String... args) {
    ReaderExample example = new ReaderExample();
    example.runExample();
  }

  public void runExample() {
    AppConfig productionConfig = new AppConfig("prod-db.example.com", 5000, "prod-key-123");
    AppConfig stagingConfig = new AppConfig("stage-db.example.com", 10000, "stage-key-456");

    // Run the composed computations with different environments
    String prodTimeoutMsg = READER.runReader(timeoutMessage, productionConfig);
    String stageTimeoutMsg = READER.runReader(timeoutMessage, stagingConfig);

    String prodConnectionString = READER.runReader(connectionStringReader, productionConfig);
    String stageConnectionString = READER.runReader(connectionStringReader, stagingConfig);

    String prodDbInfo = READER.runReader(dbInfo, productionConfig);
    String stageDbInfo = READER.runReader(dbInfo, stagingConfig);

    // Get the raw config using ask()
    AppConfig retrievedProdConfig = READER.runReader(getConfig, productionConfig);

    System.out.println("Prod Timeout: " + prodTimeoutMsg); // Output: Timeout is: 5000ms
    System.out.println("Stage Timeout: " + stageTimeoutMsg); // Output: Timeout is: 10000ms
    System.out.println(
        "Prod Connection: "
            + prodConnectionString); // Output: prod-db.example.com?apiKey=prod-key-123
    System.out.println(
        "Stage Connection: "
            + stageConnectionString); // Output: stage-db.example.com?apiKey=stage-key-456
    System.out.println(
        "Prod DB Info: " + prodDbInfo); // Output: DB: prod-db.example.com (Timeout: 5000)
    System.out.println(
        "Stage DB Info: " + stageDbInfo); // Output: DB: stage-db.example.com (Timeout: 10000)
    System.out.println(
        "Retrieved Prod Config: "
            + retrievedProdConfig); // Output: AppConfig[databaseUrl=prod-db.example.com,
    // timeoutMillis=5000, apiKey=prod-key-123]
  }

  // Define Environment
  record AppConfig(String databaseUrl, int timeoutMillis, String apiKey) {}
}
