// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

/**
 * The wire-side representation of a {@link org.higherkindedj.spring.example.domain.User} for the
 * full bidirectional mapping ({@link UserMapping}).
 *
 * <p>Deliberately a <em>bean</em> (getters/setters), not a record: the generated bean-shaped {@code
 * parse} null-guards every reference read, so a field the client omits — which Jackson leaves
 * {@code null} — becomes a located {@code FieldError} ({@code "must not be null"}) instead of an
 * exception, and {@code parse} stays total and accumulating over untrusted input. {@code email} and
 * {@code firstName} are additionally validated through leaves; {@code id} and {@code lastName} copy
 * as-is once present. Contrast with {@link UserPatchRequest}, where {@code null} means <em>absent,
 * leave unchanged</em> — here every field is required.
 */
public class UserDto {
  private String id;
  private String email;
  private String firstName;
  private String lastName;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }
}
