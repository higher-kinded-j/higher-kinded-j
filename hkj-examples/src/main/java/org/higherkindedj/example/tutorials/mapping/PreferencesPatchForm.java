// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

/**
 * Tutorial 27's PATCH request bean, written the way a PATCH bean must be: no field has an
 * initialiser, so every getter answers {@code null} until its setter is called, and a property the
 * request leaves out reads as absent.
 */
public final class PreferencesPatchForm {
  private String language;
  private Boolean marketingOptIn;

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
