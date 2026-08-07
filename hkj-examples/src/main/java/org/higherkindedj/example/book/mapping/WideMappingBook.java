// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * A flat 20-component mapping, wider than one {@code Validated.fields()} ladder: the processor
 * assembles {@code parse} from chunked ladders, so an externally fixed flat DTO maps without
 * grouping components into nested records. {@link
 * org.higherkindedj.example.book.mapping.RecordMappingBookLawsTest}'s wide-law companion proves it
 * behaves exactly like a narrow mapping.
 */
// ANCHOR: wide_spec
record WideAccount(
    String f1,
    String f2,
    String f3,
    String f4,
    String f5,
    String f6,
    String f7,
    String f8,
    String f9,
    String f10,
    String f11,
    String f12,
    String f13,
    String f14,
    String f15,
    String f16,
    String f17,
    String f18,
    String f19,
    EmailAddress email) {}

record WideAccountDto(
    String f1,
    String f2,
    String f3,
    String f4,
    String f5,
    String f6,
    String f7,
    String f8,
    String f9,
    String f10,
    String f11,
    String f12,
    String f13,
    String f14,
    String f15,
    String f16,
    String f17,
    String f18,
    String f19,
    String email) {}

@GenerateMapping
interface WideAccountMapping extends MappingSpec<WideAccount, WideAccountDto> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}
// ANCHOR_END: wide_spec
