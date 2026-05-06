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
 * Solution for Tutorial16 OpticsSpecInterfaces — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
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

  /**
   * Why this is idiomatic: {@code @Wither} optic specs target classes that already supply {@code
   * withX()} setters — {@code LocalDate.withYear(...)}. The generated lens reads via the getter and
   * writes via the wither, no record rebuild needed.
   *
   * <p>Alternative: hand-write the lens with {@code Lens.of(LocalDate::getYear, (d, y) ->
   * d.withYear(y))}. Same answer; the spec interface produces the same lens declaratively.
   *
   * <p>Common wrong attempt: try {@code @Wither} on a class without {@code withX()} methods. The
   * processor needs the wither to exist; reach for {@code @ViaBuilder} when only a builder is
   * available.
   */
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

  /**
   * Why this is idiomatic: {@code modify} on a {@code @Wither}-generated lens reads, applies the
   * function, and writes — all through the existing wither method. The class stays in charge of its
   * invariants.
   *
   * <p>Alternative: read with {@code getYear()}, compute, call {@code withYear(...)}. Three
   * statements, two mentions of the field; the lens collapses them.
   *
   * <p>Common wrong attempt: cache the {@code year} integer outside the modify. Stale data
   * surprises the next call; let the lens re-read each time.
   */
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

  /**
   * Why this is idiomatic: {@code @ViaBuilder} targets jOOQ-style records that ship a {@code
   * toBuilder()} method. The generated lens reads via the accessor and writes by building a fresh
   * record with the field replaced.
   *
   * <p>Alternative: hand-write {@code Lens.of(CustomerRecord::name, (c, n) ->
   * c.toBuilder().name(n).build())}. Same answer; the spec interface declares the intent.
   *
   * <p>Common wrong attempt: use {@code @Wither} on a builder-style record. Builders are not
   * setters; the processor would not find the {@code withX} method and fail.
   */
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

  /**
   * Why this is idiomatic: {@code modify} through a builder-backed lens reads, transforms, and
   * rebuilds via {@code toBuilder()}. The credit-limit uplift only touches the one field, the rest
   * of the record is preserved automatically.
   *
   * <p>Alternative: hand-call {@code customer.toBuilder().creditLimit(newLimit).build()}. Same
   * semantics; the lens-driven version composes with other lenses for deeper updates.
   *
   * <p>Common wrong attempt: forget that {@code modify} requires a function, not a value. Pass
   * {@code limit -> newLimit} when a constant is wanted, or use {@code set} directly.
   */
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

  /**
   * Why this is idiomatic: {@code @MatchWhen} on a sealed interface generates a prism per variant.
   * Reads return {@code Optional} of the matched payload; non-matching values come back empty.
   *
   * <p>Alternative: hand-roll prisms with {@code Prism.of(... instanceof ...)}. Same answer; the
   * spec interface keeps the prisms in lock-step with the sealed interface.
   *
   * <p>Common wrong attempt: extend the sealed interface and forget to update the spec. The new
   * variant has no prism; either re-run the processor or add a manual prism.
   */
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

  /**
   * Why this is idiomatic: a wrong-variant read returns {@code Optional.empty()} rather than
   * throwing — the prism's partiality is exactly what makes it safe.
   *
   * <p>Alternative: cast and catch {@code ClassCastException}. Possible but ugly; the prism is the
   * typed version.
   *
   * <p>Common wrong attempt: assume {@code getOptional} can short-circuit early in a pipeline. It
   * returns the empty optional and the next stage handles it; chain with {@code map} or {@code
   * flatMap} accordingly.
   */
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

  /**
   * Why this is idiomatic: optics produced from any spec interface compose with each other and with
   * hand-written optics. The lens-into-{@code LocalDate}-year and the lens-into-{@code
   * CustomerRecord}-name are the same shape and combine naturally.
   *
   * <p>Alternative: chain three {@code with*} calls or three {@code toBuilder()} calls. Works for
   * one schema; the optic composition keeps the path stable when the schema grows.
   *
   * <p>Common wrong attempt: assume optics from different specs cannot compose. They all return
   * {@code Lens}/{@code Prism}/{@code Traversal} types; composition is algebraic, not
   * implementation-specific.
   */
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

  /**
   * Why this is idiomatic: a {@code @MatchWhen}-generated prism's {@code build} creates the variant
   * from its payload. Pair {@code text().build("hello")} with {@code text().getOptional(...)} for
   * round-trip use.
   *
   * <p>Alternative: {@code new JsonString("hello")}. Same runtime; the prism form keeps the
   * building consistent with the rest of the optic API.
   *
   * <p>Common wrong attempt: try to build a sealed parent without picking a variant. The prism is
   * variant-specific by construction; pick the matching one.
   */
  @Test
  void exercise8_buildingWithPrisms() {
    // SOLUTION: Build a JsonString using prism
    JsonValue builtText = JsonValueOptics.text().build("world");

    // SOLUTION: Build a JsonNumber using prism
    JsonValue builtNumber = JsonValueOptics.number().build(99.9);

    assertThat(builtText).isEqualTo(new JsonString("world"));
    assertThat(builtNumber).isEqualTo(new JsonNumber(99.9));
  }

  /**
   * Why this is idiomatic: jOOQ's {@code Result<R>} already implements {@code List<R>}, so the
   * existing {@code Traversals.list()} works without a spec interface. Reach for the specs only
   * when the integration needs them.
   *
   * <p>Alternative: write a custom traversal that wraps jOOQ's API. Pointless duplication when
   * {@code List} interop covers the case.
   *
   * <p>Common wrong attempt: assume every external type needs a spec annotation. Many already
   * conform to standard interfaces; check for that first.
   */
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

  /**
   * Why this is idiomatic: a {@code @ThroughField}-generated traversal walks every line item of an
   * order. Compose with the line-item lenses to project per-row data without iterating by hand.
   *
   * <p>Alternative: stream-map twice (once to read items, once to read fields). Same answer; the
   * traversal composition keeps reads and writes symmetric.
   *
   * <p>Common wrong attempt: try to materialise the items first and operate on them separately. The
   * traversal abstracts the "for each item" detail; pre-materialising loses the optic
   * composability.
   */
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

  /**
   * Why this is idiomatic: the same composed traversal that reads also writes. Uppercase names,
   * double quantities, apply 10% discount — three independent transforms on the same path, each one
   * a single call.
   *
   * <p>Alternative: write a per-transform method that loops over items and calls {@code
   * toBuilder().field(...)}. Same answer; the traversal stays declarative.
   *
   * <p>Common wrong attempt: chain the three modifies into one nested expression. The staged form
   * keeps each effect inspectable and testable in isolation.
   */
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

  /**
   * Why this is idiomatic: a {@code @Wither} lens for the order date and a {@code @ThroughField}
   * traversal for the items compose end-to-end. The result reads "set the year next year" or
   * "version every product id" as a single expression.
   *
   * <p>Alternative: write a custom helper for each cross-cutting update. Works for one site; the
   * optic composition removes the need for the helper.
   *
   * <p>Common wrong attempt: assume composition only works within a single spec family. Lenses,
   * prisms, and traversals from different specs all return the same algebraic types and compose
   * cleanly.
   */
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
