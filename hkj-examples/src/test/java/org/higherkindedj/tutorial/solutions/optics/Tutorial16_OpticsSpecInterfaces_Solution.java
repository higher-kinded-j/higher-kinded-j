// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.optics.external.CustomerRecord;
import org.higherkindedj.example.optics.external.LineItemRecord;
import org.higherkindedj.example.optics.external.OrderRecord;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 16: Optics Spec Interfaces
 *
 * @see org.higherkindedj.tutorial.optics.Tutorial16_OpticsSpecInterfaces
 */
public class Tutorial16_OpticsSpecInterfaces_Solution {

  // =========================================================================
  // Manual implementations showing what would be generated
  // =========================================================================

  static class LocalDateOptics {
    private LocalDateOptics() {}

    public static Lens<LocalDate, Integer> year() {
      return Lens.of(LocalDate::getYear, (date, year) -> date.withYear(year));
    }

    public static Lens<LocalDate, Integer> monthValue() {
      return Lens.of(LocalDate::getMonthValue, (date, month) -> date.withMonth(month));
    }

    public static Lens<LocalDate, Integer> dayOfMonth() {
      return Lens.of(LocalDate::getDayOfMonth, (date, day) -> date.withDayOfMonth(day));
    }

    public static Lens<LocalDate, Integer> dayOfYear() {
      return Lens.of(LocalDate::getDayOfYear, (date, day) -> date.withDayOfYear(day));
    }
  }

  static class JooqStyleRecordOptics {
    private JooqStyleRecordOptics() {}

    public static Lens<CustomerRecord, Long> id() {
      return Lens.of(CustomerRecord::id, (c, id) -> c.toBuilder().id(id).build());
    }

    public static Lens<CustomerRecord, String> name() {
      return Lens.of(CustomerRecord::name, (c, name) -> c.toBuilder().name(name).build());
    }

    public static Lens<CustomerRecord, String> email() {
      return Lens.of(CustomerRecord::email, (c, email) -> c.toBuilder().email(email).build());
    }

    public static Lens<CustomerRecord, BigDecimal> creditLimit() {
      return Lens.of(
          CustomerRecord::creditLimit, (c, limit) -> c.toBuilder().creditLimit(limit).build());
    }
  }

  sealed interface JsonValue permits JsonString, JsonNumber, JsonBool {}

  record JsonString(String value) implements JsonValue {}

  record JsonNumber(double value) implements JsonValue {}

  record JsonBool(boolean value) implements JsonValue {}

  static class JsonValueOptics {
    private JsonValueOptics() {}

    public static Prism<JsonValue, String> text() {
      return Prism.of(
          jv -> jv instanceof JsonString js ? Optional.of(js.value()) : Optional.empty(),
          JsonString::new);
    }

    public static Prism<JsonValue, Double> number() {
      return Prism.of(
          jv -> jv instanceof JsonNumber jn ? Optional.of(jn.value()) : Optional.empty(),
          JsonNumber::new);
    }

    public static Prism<JsonValue, Boolean> bool() {
      return Prism.of(
          jv -> jv instanceof JsonBool jb ? Optional.of(jb.value()) : Optional.empty(),
          JsonBool::new);
    }
  }

  static class LineItemRecordOptics {
    private LineItemRecordOptics() {}

    public static Lens<LineItemRecord, String> productId() {
      return Lens.of(
          LineItemRecord::productId, (item, id) -> item.toBuilder().productId(id).build());
    }

    public static Lens<LineItemRecord, String> productName() {
      return Lens.of(
          LineItemRecord::productName, (item, name) -> item.toBuilder().productName(name).build());
    }

    public static Lens<LineItemRecord, Integer> quantity() {
      return Lens.of(
          LineItemRecord::quantity, (item, qty) -> item.toBuilder().quantity(qty).build());
    }

    public static Lens<LineItemRecord, BigDecimal> unitPrice() {
      return Lens.of(
          LineItemRecord::unitPrice, (item, price) -> item.toBuilder().unitPrice(price).build());
    }
  }

  static class OrderRecordOptics {
    private OrderRecordOptics() {}

    public static Lens<OrderRecord, String> orderId() {
      return Lens.of(OrderRecord::orderId, (order, id) -> order.toBuilder().orderId(id).build());
    }

    public static Lens<OrderRecord, Long> customerId() {
      return Lens.of(
          OrderRecord::customerId, (order, id) -> order.toBuilder().customerId(id).build());
    }

    public static Lens<OrderRecord, LocalDate> orderDate() {
      return Lens.of(
          OrderRecord::orderDate, (order, date) -> order.toBuilder().orderDate(date).build());
    }

    public static Lens<OrderRecord, List<LineItemRecord>> items() {
      return Lens.of(OrderRecord::items, (order, items) -> order.toBuilder().items(items).build());
    }

    public static Traversal<OrderRecord, LineItemRecord> eachItem() {
      return items().andThen(Traversals.forList());
    }
  }

  // =========================================================================
  // Solutions
  // =========================================================================

  @Test
  void exercise1_witherPattern() {
    LocalDate date = LocalDate.of(2024, 6, 15);

    // SOLUTION: Use lens to get the year
    Integer year = LocalDateOptics.year().get(date);

    // SOLUTION: Use lens to set a new year
    LocalDate nextYear = LocalDateOptics.year().set(2025, date);

    // SOLUTION: Use lens to set the month (December = 12)
    LocalDate december = LocalDateOptics.monthValue().set(12, date);

    assertThat(year).isEqualTo(2024);
    assertThat(nextYear).isEqualTo(LocalDate.of(2025, 6, 15));
    assertThat(december).isEqualTo(LocalDate.of(2024, 12, 15));
  }

  @Test
  void exercise2_modifyWithWitherLens() {
    LocalDate date = LocalDate.of(2024, 6, 15);

    // SOLUTION: Increment year by 1
    LocalDate incremented = LocalDateOptics.year().modify(y -> y + 1, date);

    // SOLUTION: Set to last day of current month (June = 30 days)
    LocalDate lastDay =
        LocalDateOptics.dayOfMonth().modify(d -> date.getMonth().length(date.isLeapYear()), date);

    assertThat(incremented).isEqualTo(LocalDate.of(2025, 6, 15));
    assertThat(lastDay.getDayOfMonth()).isEqualTo(30);
  }

  @Test
  void exercise3_viaBuilderPattern() {
    CustomerRecord customer =
        CustomerRecord.builder()
            .id(1L)
            .name("Alice")
            .email("alice@example.com")
            .creditLimit(new BigDecimal("5000.00"))
            .build();

    // SOLUTION: Use lens to get the name
    String name = JooqStyleRecordOptics.name().get(customer);

    // SOLUTION: Use lens to set a new email
    CustomerRecord updated = JooqStyleRecordOptics.email().set("new@example.com", customer);

    assertThat(name).isEqualTo("Alice");
    assertThat(updated.email()).isEqualTo("new@example.com");
    assertThat(updated.name()).isEqualTo("Alice");
  }

  @Test
  void exercise4_modifyWithBuilderLens() {
    CustomerRecord customer =
        CustomerRecord.builder()
            .id(1L)
            .name("Alice Smith")
            .email("alice@example.com")
            .creditLimit(new BigDecimal("1000.00"))
            .build();

    // SOLUTION: Increase credit limit by 20%
    CustomerRecord increased =
        JooqStyleRecordOptics.creditLimit()
            .modify(limit -> limit.multiply(new BigDecimal("1.20")), customer);

    // SOLUTION: Uppercase the name
    CustomerRecord uppercased = JooqStyleRecordOptics.name().modify(String::toUpperCase, customer);

    assertThat(increased.creditLimit()).isEqualByComparingTo(new BigDecimal("1200.00"));
    assertThat(uppercased.name()).isEqualTo("ALICE SMITH");
  }

  @Test
  void exercise5_matchWhenPrisms() {
    JsonValue textValue = new JsonString("hello");
    JsonValue numberValue = new JsonNumber(42.0);
    JsonValue boolValue = new JsonBool(true);

    // SOLUTION: Extract text using prism
    Optional<String> text = JsonValueOptics.text().getOptional(textValue);

    // SOLUTION: Extract number using prism
    Optional<Double> number = JsonValueOptics.number().getOptional(numberValue);

    // SOLUTION: Extract bool using prism
    Optional<Boolean> bool = JsonValueOptics.bool().getOptional(boolValue);

    assertThat(text).contains("hello");
    assertThat(number).contains(42.0);
    assertThat(bool).contains(true);
  }

  @Test
  void exercise6_prismTypeSafety() {
    JsonValue textValue = new JsonString("hello");

    // SOLUTION: Attempt to extract number from text (returns empty)
    Optional<Double> numberFromText = JsonValueOptics.number().getOptional(textValue);

    // SOLUTION: Attempt to extract bool from text (returns empty)
    Optional<Boolean> boolFromText = JsonValueOptics.bool().getOptional(textValue);

    assertThat(numberFromText).isEmpty();
    assertThat(boolFromText).isEmpty();
  }

  @Test
  void exercise7_composingSpecOptics() {
    LocalDate date = LocalDate.of(2024, 6, 15);

    // SOLUTION: Compose multiple updates to create New Year's Day next year
    // Using monthValue (int) instead of month (Month enum)
    LocalDate newYearsDay =
        LocalDateOptics.year()
            .modify(
                y -> y + 1,
                LocalDateOptics.monthValue().set(1, LocalDateOptics.dayOfMonth().set(1, date)));

    assertThat(newYearsDay).isEqualTo(LocalDate.of(2025, 1, 1));

    CustomerRecord customer =
        CustomerRecord.builder()
            .id(1L)
            .name("Alice")
            .email("alice@old.com")
            .creditLimit(new BigDecimal("1000.00"))
            .build();

    // SOLUTION: Compose multiple CustomerRecord updates
    CustomerRecord fullyUpdated =
        JooqStyleRecordOptics.name()
            .set(
                "Bob",
                JooqStyleRecordOptics.email()
                    .set(
                        "bob@new.com",
                        JooqStyleRecordOptics.creditLimit()
                            .modify(limit -> limit.multiply(new BigDecimal("2")), customer)));

    assertThat(fullyUpdated.name()).isEqualTo("Bob");
    assertThat(fullyUpdated.email()).isEqualTo("bob@new.com");
    assertThat(fullyUpdated.creditLimit()).isEqualByComparingTo(new BigDecimal("2000.00"));
  }

  @Test
  void exercise8_buildingWithPrisms() {
    // SOLUTION: Build a JsonString using prism
    JsonValue builtText = JsonValueOptics.text().build("world");

    // SOLUTION: Build a JsonNumber using prism
    JsonValue builtNumber = JsonValueOptics.number().build(99.9);

    assertThat(builtText).isEqualTo(new JsonString("world"));
    assertThat(builtNumber).isEqualTo(new JsonNumber(99.9));
  }

  @Test
  void exercise9_jooqResultIntegration() {
    // JOOQ's Result<R> implements List<R>
    Result<Record> result = DSL.using(SQLDialect.DEFAULT).newResult();

    // SOLUTION: Verify that Result implements List
    boolean isResultAList = result instanceof List;

    assertThat(isResultAList).isTrue();

    // Key insight: Not all external types need spec interfaces!
    // - Result<R> -> use Traversals.list() directly
    // - Generated POJOs with builders -> use @ViaBuilder spec
    // - Types with withX() methods -> use @Wither spec
  }

  @Test
  void exercise10_throughFieldBasics() {
    OrderRecord order =
        OrderRecord.builder()
            .orderId("ORD-001")
            .customerId(42L)
            .orderDate(LocalDate.of(2024, 6, 15))
            .items(
                List.of(
                    LineItemRecord.builder()
                        .productId("SKU-001")
                        .productName("Widget")
                        .quantity(2)
                        .unitPrice(new BigDecimal("19.99"))
                        .build(),
                    LineItemRecord.builder()
                        .productId("SKU-002")
                        .productName("Gadget")
                        .quantity(1)
                        .unitPrice(new BigDecimal("49.99"))
                        .build()))
            .build();

    // SOLUTION: Get all product names using traversal composition
    List<String> productNames =
        Traversals.getAll(
            OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.productName()), order);

    // SOLUTION: Get all quantities using traversal composition
    List<Integer> quantities =
        Traversals.getAll(
            OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.quantity()), order);

    assertThat(productNames).containsExactly("Widget", "Gadget");
    assertThat(quantities).containsExactly(2, 1);
  }

  @Test
  void exercise11_throughFieldModify() {
    OrderRecord order =
        OrderRecord.builder()
            .orderId("ORD-002")
            .customerId(42L)
            .orderDate(LocalDate.of(2024, 6, 15))
            .items(
                List.of(
                    LineItemRecord.builder()
                        .productId("SKU-001")
                        .productName("widget")
                        .quantity(2)
                        .unitPrice(new BigDecimal("100.00"))
                        .build(),
                    LineItemRecord.builder()
                        .productId("SKU-002")
                        .productName("gadget")
                        .quantity(3)
                        .unitPrice(new BigDecimal("50.00"))
                        .build()))
            .build();

    // SOLUTION: Uppercase all product names
    OrderRecord uppercased =
        Traversals.modify(
            OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.productName()),
            String::toUpperCase,
            order);

    // SOLUTION: Double all quantities
    OrderRecord doubled =
        Traversals.modify(
            OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.quantity()),
            qty -> qty * 2,
            order);

    // SOLUTION: Apply 10% discount to all prices
    OrderRecord discounted =
        Traversals.modify(
            OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.unitPrice()),
            price -> price.multiply(new BigDecimal("0.90")),
            order);

    assertThat(
            Traversals.getAll(
                OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.productName()),
                uppercased))
        .containsExactly("WIDGET", "GADGET");

    assertThat(
            Traversals.getAll(
                OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.quantity()), doubled))
        .containsExactly(4, 6);

    assertThat(
            Traversals.getAll(
                OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.unitPrice()), discounted))
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactly(new BigDecimal("90.00"), new BigDecimal("45.00"));
  }

  @Test
  void exercise12_throughFieldComposition() {
    OrderRecord order =
        OrderRecord.builder()
            .orderId("ORD-003")
            .customerId(42L)
            .orderDate(LocalDate.of(2024, 1, 15))
            .items(
                List.of(
                    LineItemRecord.builder()
                        .productId("SKU-001")
                        .productName("Widget")
                        .quantity(5)
                        .unitPrice(new BigDecimal("20.00"))
                        .build()))
            .build();

    // SOLUTION: Compose @Wither lens with @ViaBuilder lens to update year
    OrderRecord nextYear =
        OrderRecordOptics.orderDate().andThen(LocalDateOptics.year()).set(2025, order);

    // SOLUTION: Compose @ThroughField traversal with lens to add suffix to product IDs
    OrderRecord versioned =
        Traversals.modify(
            OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.productId()),
            id -> id + "-v2",
            order);

    assertThat(nextYear.orderDate()).isEqualTo(LocalDate.of(2025, 1, 15));
    assertThat(
            Traversals.getAll(
                OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.productId()), versioned))
        .containsExactly("SKU-001-v2");
  }
}
