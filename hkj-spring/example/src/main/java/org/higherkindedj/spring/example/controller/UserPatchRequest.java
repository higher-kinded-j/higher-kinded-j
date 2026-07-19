// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

/**
 * A partial-update (PATCH) request bean for {@link org.higherkindedj.spring.example.domain.User}.
 *
 * <p>A {@code null} property means <em>not provided, leave unchanged</em> — the REST PATCH
 * contract. The binder leaves any field the client omits as {@code null}. There is deliberately no
 * {@code id} property: a PATCH must not move the resource, and the sparse mapping's one-sided
 * coverage leaves an unmapped domain component (here {@code id}) untouched. See {@link
 * UserPatchMapping}.
 */
public class UserPatchRequest {
  private String email;
  private String firstName;
  private String lastName;

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
