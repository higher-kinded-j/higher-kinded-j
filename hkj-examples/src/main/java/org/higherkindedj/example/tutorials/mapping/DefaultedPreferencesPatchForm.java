// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

/**
 * Tutorial 27's PATCH request bean as a code generator renders it from a schema that declares
 * {@code default: false}: the default becomes a field initialiser. {@code getMarketingOptIn()} then
 * answers {@code false} on a request that never mentioned the field, and a PATCH reads that as
 * sent. No compiler can check this. The sparse identity law can, given a sample whose opt-in
 * differs from the default.
 */
public final class DefaultedPreferencesPatchForm {
  private String language;
  private Boolean marketingOptIn = false;

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public Boolean getMarketingOptIn() {
    return marketingOptIn;
  }

  public void setMarketingOptIn(Boolean marketingOptIn) {
    this.marketingOptIn = marketingOptIn;
  }
}
