// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import java.util.List;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The API-response pipeline shown on the book's <a
 * href="https://higher-kinded-j.github.io/optics/optics_spec_interfaces.html">Taming JSON with
 * Optics</a> page. The page {@code {{#include}}}s the anchored regions, so the code cannot drift
 * from the API and running this class proves the outputs the page asserts.
 */
public final class JsonApiBook {

  private static final String RESPONSE =
      """
      {
        "status": "success",
        "data": {
          "users": [
            { "id": 1, "name": "Alice", "email": "alice@example.com", "age": 32 },
            { "id": 2, "name": "Bob",   "email": "bob@example.com",   "age": 28 },
            { "id": 3, "name": "Carol", "email": "carol@example.com", "age": 45 }
          ],
          "page": 1
        }
      }
      """;

  // ANCHOR: paths
  /** Each user object inside data.users. */
  static final Traversal<JsonNode, JsonNode> EACH_USER =
      JsonPaths.field("data").andThen(JsonPaths.field("users")).andThen(JsonPaths.elements());

  // A Traversal composes with an Affine through asTraversal(): there is no
  // Traversal.andThen(Affine) overload, because the result is a Traversal either way.
  /** Every user's email address, as a String. */
  static final Traversal<JsonNode, String> USER_EMAILS =
      EACH_USER.andThen(JsonPaths.field("email").andThen(JsonPaths.textValue()).asTraversal());

  /** Every user's age, as a double. */
  static final Traversal<JsonNode, Double> USER_AGES =
      EACH_USER.andThen(JsonPaths.field("age").andThen(JsonPaths.numericValue()).asTraversal());

  /** The page number: a single value, so an Affine rather than a Traversal. */
  static final Affine<JsonNode, Double> PAGE =
      JsonPaths.field("data").andThen(JsonPaths.field("page")).andThen(JsonPaths.numericValue());

  // ANCHOR_END: paths

  private JsonApiBook() {}

  public static void main(String[] args) {
    JsonNode response = new ObjectMapper().readTree(RESPONSE);

    // ANCHOR: read
    List<String> emails = Traversals.getAll(USER_EMAILS, response);
    // [alice@example.com, bob@example.com, carol@example.com]

    List<Double> overForty = Traversals.getAll(USER_AGES.filtered(age -> age > 40), response);
    // [45.0]

    double page = PAGE.getOrElse(1.0, response);
    // 1.0
    // ANCHOR_END: read

    // ANCHOR: write
    JsonNode anonymised = Traversals.modify(USER_EMAILS, JsonApiBook::mask, response);
    // every data.users[].email is masked; response itself is unchanged

    JsonNode aged = Traversals.modify(USER_AGES, age -> age + 1, response);
    // [33.0, 29.0, 46.0]
    // ANCHOR_END: write

    System.out.println("emails      : " + emails);
    System.out.println("over forty  : " + overForty);
    System.out.println("page        : " + page);
    System.out.println("anonymised  : " + Traversals.getAll(USER_EMAILS, anonymised));
    System.out.println("aged        : " + Traversals.getAll(USER_AGES, aged));
    System.out.println("original    : " + Traversals.getAll(USER_EMAILS, response));
  }

  private static String mask(String email) {
    int at = email.indexOf('@');
    if (at < 0) {
      return "***";
    }
    return at <= 1 ? "***" + email.substring(at) : email.charAt(0) + "***" + email.substring(at);
  }
}
