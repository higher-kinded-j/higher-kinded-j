// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks.mapping;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.mapstruct.Mapper;

/**
 * The flat pair the mapping benchmarks measure: ten fields and no nesting, nine of them converted.
 * It is a second data point beside the nested order pair, with no nesting and no list.
 */
final class FlatMappingModel {

  private FlatMappingModel() {}

  static final String LOCAL_DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}";
}

record FlatRecord(
    UUID id,
    String name,
    EmailAddress email,
    Integer quantity,
    BigDecimal price,
    Instant createdAt,
    Currency currency,
    OrderStatus status,
    LocalDate dueDate,
    Boolean active) {}

record FlatDto(
    String id,
    String name,
    String email,
    String quantity,
    String price,
    String createdAt,
    String currency,
    String status,
    String dueDate,
    String active) {}

record ValidatedFlatDto(
    @NotNull @Pattern(regexp = OrderMappingModel.UUID_PATTERN) String id,
    @NotNull String name,
    @NotNull @Pattern(regexp = ".*@.*") String email,
    @NotNull @Pattern(regexp = OrderMappingModel.INTEGER_PATTERN) String quantity,
    @NotNull @Pattern(regexp = OrderMappingModel.PLAIN_DECIMAL_PATTERN) String price,
    @NotNull @Pattern(regexp = OrderMappingModel.INSTANT_PATTERN) String createdAt,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotNull @Pattern(regexp = "NEW|PAID|SHIPPED") String status,
    @NotNull @Pattern(regexp = FlatMappingModel.LOCAL_DATE_PATTERN) String dueDate,
    @NotNull @Pattern(regexp = "true|false") String active) {}

@GenerateMapping
interface FlatMapping extends MappingSpec<FlatRecord, FlatDto> {
  default ValidatedPrism<String, UUID> id() {
    return StandardCodecs.uuid();
  }

  default ValidatedPrism<String, EmailAddress> email() {
    return OrderMappingModel.EMAIL;
  }

  default ValidatedPrism<String, Integer> quantity() {
    return StandardCodecs.intFromString();
  }

  default ValidatedPrism<String, BigDecimal> price() {
    return StandardCodecs.bigDecimal();
  }

  default ValidatedPrism<String, Instant> createdAt() {
    return StandardCodecs.instant();
  }

  default ValidatedPrism<String, Currency> currency() {
    return StandardCodecs.currency();
  }

  default ValidatedPrism<String, OrderStatus> status() {
    return StandardCodecs.enumByName(OrderStatus.class);
  }

  default ValidatedPrism<String, LocalDate> dueDate() {
    return StandardCodecs.localDate();
  }

  default ValidatedPrism<String, Boolean> active() {
    return StandardCodecs.booleanStrict();
  }
}

@Mapper
interface FlatMapstruct {
  FlatDto toDto(FlatRecord flat);

  FlatRecord toDomain(FlatDto dto);

  FlatRecord toDomain(ValidatedFlatDto dto);

  default EmailAddress email(String value) {
    return new EmailAddress(value);
  }

  default String email(EmailAddress email) {
    return email.value();
  }

  default Instant instant(String value) {
    return Instant.parse(value);
  }

  default String instant(Instant value) {
    return value.toString();
  }

  default LocalDate localDate(String value) {
    return LocalDate.parse(value);
  }

  default String localDate(LocalDate value) {
    return value.toString();
  }

  default String price(BigDecimal value) {
    return value.toPlainString();
  }
}

/** The hand-written flat mapper: throws on the first problem, with no field path. */
final class HandWrittenFlatMapper {

  private HandWrittenFlatMapper() {}

  static FlatDto toDto(FlatRecord flat) {
    return new FlatDto(
        flat.id().toString(),
        flat.name(),
        flat.email().value(),
        flat.quantity().toString(),
        flat.price().toPlainString(),
        flat.createdAt().toString(),
        flat.currency().getCurrencyCode(),
        flat.status().name(),
        flat.dueDate().toString(),
        flat.active().toString());
  }

  static FlatRecord toDomain(FlatDto dto) {
    if (!dto.email().contains("@")) {
      throw new IllegalArgumentException("bad email");
    }
    if (!dto.active().equals("true") && !dto.active().equals("false")) {
      throw new IllegalArgumentException("bad boolean");
    }
    return new FlatRecord(
        UUID.fromString(dto.id()),
        dto.name(),
        new EmailAddress(dto.email()),
        Integer.parseInt(dto.quantity()),
        new BigDecimal(dto.price()),
        Instant.parse(dto.createdAt()),
        Currency.getInstance(dto.currency()),
        OrderStatus.valueOf(dto.status()),
        LocalDate.parse(dto.dueDate()),
        Boolean.parseBoolean(dto.active()));
  }
}
