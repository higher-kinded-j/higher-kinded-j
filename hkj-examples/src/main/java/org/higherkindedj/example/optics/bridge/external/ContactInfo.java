// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge.external;

import java.util.Objects;
import java.util.Optional;

/**
 * An immutable ContactInfo value object following the Immutables library style.
 *
 * <p>This class simulates what the Immutables annotation processor generates from:
 *
 * <pre>{@code
 * @Value.Immutable
 * public interface ContactInfo {
 *     String email();
 *     String phone();
 *     Optional<String> fax();
 * }
 * }</pre>
 */
public final class ContactInfo {

  private final String email;
  private final String phone;
  private final Optional<String> fax;

  private ContactInfo(String email, String phone, Optional<String> fax) {
    this.email = Objects.requireNonNull(email, "email");
    this.phone = Objects.requireNonNull(phone, "phone");
    this.fax = Objects.requireNonNull(fax, "fax");
  }

  public String email() {
    return email;
  }

  public String phone() {
    return phone;
  }

  public Optional<String> fax() {
    return fax;
  }

  public ContactInfo withEmail(String email) {
    return new ContactInfo(email, this.phone, this.fax);
  }

  public ContactInfo withPhone(String phone) {
    return new ContactInfo(this.email, phone, this.fax);
  }

  public ContactInfo withFax(Optional<String> fax) {
    return new ContactInfo(this.email, this.phone, fax);
  }

  public ContactInfo withFax(String fax) {
    return withFax(Optional.ofNullable(fax));
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ContactInfo that)) return false;
    return email.equals(that.email) && phone.equals(that.phone) && fax.equals(that.fax);
  }

  @Override
  public int hashCode() {
    return Objects.hash(email, phone, fax);
  }

  @Override
  public String toString() {
    return "ContactInfo{email='%s', phone='%s', fax=%s}".formatted(email, phone, fax);
  }

  public static final class Builder {
    private String email;
    private String phone;
    private Optional<String> fax = Optional.empty();

    public Builder email(String email) {
      this.email = email;
      return this;
    }

    public Builder phone(String phone) {
      this.phone = phone;
      return this;
    }

    public Builder fax(String fax) {
      this.fax = Optional.ofNullable(fax);
      return this;
    }

    public Builder fax(Optional<String> fax) {
      this.fax = fax;
      return this;
    }

    public ContactInfo build() {
      return new ContactInfo(email, phone, fax);
    }
  }
}
