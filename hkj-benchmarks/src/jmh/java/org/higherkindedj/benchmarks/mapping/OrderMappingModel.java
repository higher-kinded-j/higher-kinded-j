// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks.mapping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.mapstruct.Mapper;

/**
 * The nested pair the mapping benchmarks measure: an order with a nested customer, a list of line
 * items, an enum and several converted values, from the book's order-service cast. Each approach
 * the benchmarks compare maps the same pair: the generated mapper, a hand-written one, MapStruct,
 * and MapStruct behind Bean Validation.
 */
final class OrderMappingModel {

  private OrderMappingModel() {}

  static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
  static final String INSTANT_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";
  static final String PLAIN_DECIMAL_PATTERN = "-?\\d+(\\.\\d+)?";
  static final String INTEGER_PATTERN = "-?\\d+";

  /** The one hand-written check the pair needs; every other conversion is a stock codec. */
  static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);
}

// The domain.

enum OrderStatus {
  NEW,
  PAID,
  SHIPPED
}

record EmailAddress(String value) {}

record Customer(String name, EmailAddress email) {}

record LineItem(String sku, Integer quantity, BigDecimal price) {}

record Order(
    UUID id,
    Customer customer,
    List<LineItem> lines,
    Instant placedAt,
    Currency currency,
    OrderStatus status) {}

// The wire every approach but Bean Validation reads.

record CustomerDto(String name, String email) {}

record LineItemDto(String sku, String quantity, String price) {}

record OrderDto(
    String id,
    CustomerDto customer,
    List<LineItemDto> lines,
    String placedAt,
    String currency,
    String status) {}

// The same wire, annotated for Bean Validation.

record ValidatedCustomerDto(
    @NotNull String name, @NotNull @Pattern(regexp = ".*@.*") String email) {}

record ValidatedLineItemDto(
    @NotNull String sku,
    @NotNull @Pattern(regexp = OrderMappingModel.INTEGER_PATTERN) String quantity,
    @NotNull @Pattern(regexp = OrderMappingModel.PLAIN_DECIMAL_PATTERN) String price) {}

record ValidatedOrderDto(
    @NotNull @Pattern(regexp = OrderMappingModel.UUID_PATTERN) String id,
    @NotNull @Valid ValidatedCustomerDto customer,
    @NotNull List<@Valid ValidatedLineItemDto> lines,
    @NotNull @Pattern(regexp = OrderMappingModel.INSTANT_PATTERN) String placedAt,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotNull @Pattern(regexp = "NEW|PAID|SHIPPED") String status) {}

// The generated mapper: three specs.

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> email() {
    return OrderMappingModel.EMAIL;
  }
}

@GenerateMapping
interface LineItemMapping extends MappingSpec<LineItem, LineItemDto> {
  default ValidatedPrism<String, Integer> quantity() {
    return StandardCodecs.intFromString();
  }

  default ValidatedPrism<String, BigDecimal> price() {
    return StandardCodecs.bigDecimal();
  }
}

@GenerateMapping
interface OrderMapping extends MappingSpec<Order, OrderDto> {
  default ValidatedPrism<String, UUID> id() {
    return StandardCodecs.uuid();
  }

  default ValidatedPrism<String, Instant> placedAt() {
    return StandardCodecs.instant();
  }

  default ValidatedPrism<String, Currency> currency() {
    return StandardCodecs.currency();
  }

  default ValidatedPrism<String, OrderStatus> status() {
    return StandardCodecs.enumByName(OrderStatus.class);
  }
}

// MapStruct: one mapper for both wires. Built-in conversions cover UUID, Currency, BigDecimal,
// Integer and the enum; the email and the instant need a method each.

@Mapper
interface OrderMapstruct {
  OrderDto toDto(Order order);

  Order toDomain(OrderDto dto);

  Order toDomain(ValidatedOrderDto dto);

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
}

/** The mapper most codebases carry: throws on the first problem, with no field path. */
final class HandWrittenOrderMapper {

  private HandWrittenOrderMapper() {}

  static OrderDto toDto(Order order) {
    List<LineItemDto> lines = new ArrayList<>(order.lines().size());
    for (LineItem line : order.lines()) {
      lines.add(
          new LineItemDto(line.sku(), line.quantity().toString(), line.price().toPlainString()));
    }
    return new OrderDto(
        order.id().toString(),
        new CustomerDto(order.customer().name(), order.customer().email().value()),
        List.copyOf(lines),
        order.placedAt().toString(),
        order.currency().getCurrencyCode(),
        order.status().name());
  }

  static Order toDomain(OrderDto dto) {
    Objects.requireNonNull(dto.customer(), "customer required");
    if (!dto.customer().email().contains("@")) {
      throw new IllegalArgumentException("bad email");
    }
    List<LineItem> lines = new ArrayList<>(dto.lines().size());
    for (LineItemDto line : dto.lines()) {
      lines.add(
          new LineItem(
              line.sku(), Integer.parseInt(line.quantity()), new BigDecimal(line.price())));
    }
    return new Order(
        UUID.fromString(dto.id()),
        new Customer(dto.customer().name(), new EmailAddress(dto.customer().email())),
        List.copyOf(lines),
        Instant.parse(dto.placedAt()),
        Currency.getInstance(dto.currency()),
        OrderStatus.valueOf(dto.status()));
  }
}
