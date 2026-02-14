// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.configaudit;

import java.util.Base64;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.annotations.GenerateIsos;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A sealed interface (sum type) for different kinds of setting values. A Prism will be generated to
 * focus on the EncryptedValue case.
 */
@GeneratePrisms
public sealed interface SettingValue {}

record StringValue(String value) implements SettingValue {}

record IntValue(int value) implements SettingValue {}

@GenerateLenses
record EncryptedValue(String base64Value) implements SettingValue {
  /**
   * An Iso to handle Base64 encoding/decoding. This defines a lossless, two-way conversion between
   * a Base64 encoded string and its raw byte array.
   */
  @GenerateIsos
  public static Iso<String, byte[]> base64() {
    return Iso.of(
        base64Str -> Base64.getDecoder().decode(base64Str),
        bytes -> Base64.getEncoder().encodeToString(bytes));
  }
}
