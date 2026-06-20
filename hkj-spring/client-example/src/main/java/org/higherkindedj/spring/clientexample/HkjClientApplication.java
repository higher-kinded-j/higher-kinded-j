// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.clientexample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A standalone client application that calls the {@code HkjSpringExampleApplication} users service
 * over HTTP via a generated {@link UserClientApi} ({@code @HkjHttpClient}).
 *
 * <p>This is the outbound counterpart to the server example: where that application returns Effect
 * Paths from its controllers, this one consumes them across the network, decoding a remote failure
 * back into a typed {@link ApiError}. It runs as a console application (no embedded web server)
 * and, on startup, performs a handful of calls and logs the results (see {@link UserClientRunner}).
 *
 * <p>Run the server first, then this client:
 *
 * <pre>{@code
 * ./gradlew :hkj-spring:example:bootRun          # server on :8080
 * ./gradlew :hkj-spring:client-example:bootRun   # this client calls it
 * }</pre>
 */
@SpringBootApplication
public class HkjClientApplication {

  /** Creates an HkjClientApplication instance. */
  public HkjClientApplication() {}

  /**
   * Application entry point.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(HkjClientApplication.class, args);
  }
}
