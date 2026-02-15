// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge.external;

import java.util.Optional;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.Wither;

/** Spec interface defining optics for the external {@link ContactInfo} type. */
@ImportOptics
public interface ContactInfoOpticsSpec extends OpticsSpec<ContactInfo> {

  @Wither(value = "withEmail", getter = "email")
  Lens<ContactInfo, String> email();

  @Wither(value = "withPhone", getter = "phone")
  Lens<ContactInfo, String> phone();

  @Wither(value = "withFax", getter = "fax")
  Lens<ContactInfo, Optional<String>> fax();
}
