// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.clients;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A customer resource in the shape an OpenAPI generator writes a client model: a no-argument
 * constructor, a getter and a setter per property, a nullable property for an optional field, and
 * value equality.
 */
public class CustomerResource {
  private @Nullable String id;
  private @Nullable String fullName;
  private @Nullable String email;
  private @Nullable String nickname;
  private @Nullable AddressBean address;

  public @Nullable String getId() {
    return id;
  }

  public void setId(@Nullable String id) {
    this.id = id;
  }

  public @Nullable String getFullName() {
    return fullName;
  }

  public void setFullName(@Nullable String fullName) {
    this.fullName = fullName;
  }

  public @Nullable String getEmail() {
    return email;
  }

  public void setEmail(@Nullable String email) {
    this.email = email;
  }

  public @Nullable String getNickname() {
    return nickname;
  }

  public void setNickname(@Nullable String nickname) {
    this.nickname = nickname;
  }

  public @Nullable AddressBean getAddress() {
    return address;
  }

  public void setAddress(@Nullable AddressBean address) {
    this.address = address;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof CustomerResource that
        && Objects.equals(id, that.id)
        && Objects.equals(fullName, that.fullName)
        && Objects.equals(email, that.email)
        && Objects.equals(nickname, that.nickname)
        && Objects.equals(address, that.address);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, fullName, email, nickname, address);
  }

  @Override
  public String toString() {
    return "CustomerResource[id=%s, fullName=%s, email=%s, nickname=%s, address=%s]"
        .formatted(id, fullName, email, nickname, address);
  }
}
