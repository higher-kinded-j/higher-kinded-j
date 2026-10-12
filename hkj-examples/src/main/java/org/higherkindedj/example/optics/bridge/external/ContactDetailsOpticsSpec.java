// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge.external;

import java.util.Optional;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.Wither;

/** Spec interface defining optics for the external {@link ContactDetails} type. */
@ImportOptics
public interface ContactDetailsOpticsSpec extends OpticsSpec<ContactDetails> {

  @Wither(value = "withEmail", getter = "email")
  Lens<ContactDetails, String> email();

  @Wither(value = "withPhone", getter = "phone")
  Lens<ContactDetails, String> phone();

  @Wither(value = "withFax", getter = "fax")
  Lens<ContactDetails, Optional<String>> fax();
}
