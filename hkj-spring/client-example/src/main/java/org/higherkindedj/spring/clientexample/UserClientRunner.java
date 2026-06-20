// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.clientexample;

import org.higherkindedj.hkt.either.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * On startup, calls the {@code HkjSpringExampleApplication} users service through the generated
 * {@link UserClientApi} and logs each outcome, demonstrating the typed error surviving a real HTTP
 * hop.
 *
 * <p>Excluded under the {@code test} profile so {@code @SpringBootTest} contexts (which use stub
 * servers) do not trigger a live call.
 */
@Component
@Profile("!test")
public class UserClientRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(UserClientRunner.class);

  private final UserClientApi users;

  /**
   * Creates the runner.
   *
   * @param users the autowired client (the generated {@code UserClientApiClient})
   */
  public UserClientRunner(UserClientApi users) {
    this.users = users;
  }

  @Override
  public void run(String... args) {
    log.info("Calling the HkjSpringExampleApplication users service over HTTP...");
    try {
      // An existing user: 2xx -> Right(UserDto).
      log.info("  GET  /api/users/1   -> {}", describe(users.getUser("1").run()));

      // A missing user: the server's 404 envelope decodes back into a typed Left(ApiError), so the
      // error survives the hop instead of collapsing into a bare status code.
      log.info("  GET  /api/users/999 -> {}", describe(users.getUser("999").run()));

      // Create is deferred on a virtual thread; .withRetry(...)/.timeout(...) could be chained
      // before unsafeRun().
      log.info(
          "  POST /api/users     -> {}",
          describe(users.create(new NewUser("grace@example.com", "Grace", "Hopper")).unsafeRun()));
    } catch (RestClientException ex) {
      log.error(
          "Could not reach the users service. Start HkjSpringExampleApplication on :8080 first. ({})",
          ex.getMessage());
    }
  }

  /** Renders either arm of the outcome for logging. */
  private static String describe(Either<ApiError, UserDto> result) {
    return result.fold(
        error -> "Left(ApiError): userId=" + error.userId() + ", message=" + error.message(),
        user ->
            "Right(UserDto): "
                + user.firstName()
                + " "
                + user.lastName()
                + " <"
                + user.email()
                + ">");
  }
}
