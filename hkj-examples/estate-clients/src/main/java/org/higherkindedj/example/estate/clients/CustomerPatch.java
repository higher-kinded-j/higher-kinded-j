// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.clients;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A PATCH request for a customer. Every property is {@code null} until a request sets it, so an
 * omitted field keeps its value. The nickname is an {@code Optional}: Jackson binds an explicit
 * {@code "nickname": null} to {@code Optional.empty()}, which clears it.
 */
public class CustomerPatch {
  private @Nullable String fullName;
  private @Nullable String email;
  private @Nullable Optional<String> nickname;
  private @Nullable AddressBean address;

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

  public @Nullable Optional<String> getNickname() {
    return nickname;
  }

  public void setNickname(@Nullable Optional<String> nickname) {
    this.nickname = nickname;
  }

  public @Nullable AddressBean getAddress() {
    return address;
  }

  public void setAddress(@Nullable AddressBean address) {
    this.address = address;
  }
}
