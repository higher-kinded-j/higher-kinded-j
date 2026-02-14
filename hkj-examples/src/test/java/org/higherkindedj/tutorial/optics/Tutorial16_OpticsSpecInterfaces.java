// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
 * Tutorial 16: Optics Spec Interfaces - Defining Optics for External Types
 *
 * <p>When you need optics for types you don't own (third-party libraries, JDK types, etc.), you
 * cannot annotate them directly with @GenerateLenses or @GeneratePrisms. Spec interfaces solve this
 * problem.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>{@code OpticsSpec<S>} - Marker interface for spec definitions
 *   <li>{@code @ImportOptics} - Triggers code generation for the spec
 *   <li>{@code @Wither} - For types with withX() methods (like java.time.LocalDate)
 *   <li>{@code @ViaBuilder} - For types with toBuilder().build() pattern
 *   <li>{@code @InstanceOf} - For prisms using instanceof checks on subtypes
 *   <li>{@code @MatchWhen} - For prisms using predicate methods (isX()/asX())
 *   <li>{@code @ThroughField} - For traversals into collection fields (auto-detects List, Set,
 *       etc.)
 * </ul>
 *
 * <p>The hkj-examples module includes working spec interfaces for real third-party types:
 *
 * <ul>
 *   <li>LocalDateOpticsSpec - java.time.LocalDate with @Wither
 *   <li>JsonNodeOpticsSpec - Jackson JsonNode with @InstanceOf prisms
 *   <li>JooqStyleRecordOpticsSpec - Builder-based types with @ViaBuilder
 *   <li>OrderRecordOpticsSpec - @ThroughField with auto-detected List traversal
 * </ul>
 *
 * @see org.higherkindedj.optics.annotations.OpticsSpec
 * @see org.higherkindedj.optics.annotations.ImportOptics
 */
public class Tutorial16_OpticsSpecInterfaces {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // =========================================================================
  // Manual implementations showing what would be generated
  // In production, these are created by the annotation processor
  // =========================================================================

  /**
   * Simulates what LocalDateOptics would generate from LocalDateOpticsSpec.
   *
   * <p>The @Wither annotation produces lenses using wither methods:
   *
   * <pre>{@code
   * Lens.of(
   *     source -> source.getYear(),
   *     (source, newValue) -> source.withYear(newValue)
   * )
   * }</pre>
   */
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

  /**
   * Simulates what JooqStyleRecordOptics would generate from JooqStyleRecordOpticsSpec.
   *
   * <p>The @ViaBuilder annotation produces lenses using the builder pattern: Lens.of( source ->
   * source.name(), (source, newValue) -> source.toBuilder().name(newValue).build() )
   */
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

  // Simulated external type with predicate-based type checking
  // (like Jackson's JsonNode, but simplified for the tutorial)
  sealed interface JsonValue permits JsonString, JsonNumber, JsonBool {}

  record JsonString(String value) implements JsonValue {}

  record JsonNumber(double value) implements JsonValue {}

  record JsonBool(boolean value) implements JsonValue {}

  /**
   * Simulates what might be generated for a JsonNode-like type with @MatchWhen.
   *
   * <p>The @MatchWhen annotation produces prisms using predicate methods: Prism.of( source ->
   * source.isText() ? Optional.of(source.asText()) : Optional.empty(), text -> text )
   */
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

  /**
   * Simulates what LineItemRecordOptics would generate from LineItemRecordOpticsSpec.
   *
   * <p>Uses @ViaBuilder to generate lenses for the builder-based LineItemRecord.
   */
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

  /**
   * Simulates what OrderRecordOptics would generate from OrderRecordOpticsSpec.
   *
   * <p>Demonstrates @ThroughField auto-detection: The eachItem() traversal is generated by
   * composing the items() lens with Traversals.forList(), which is auto-detected from the
   * List&lt;LineItemRecord&gt; field type.
   */
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

    /**
     * This is what @ThroughField(field = "items") generates.
     *
     * <p>The processor auto-detects that 'items' is a List and uses Traversals.forList(). Generated
     * code is equivalent to: items().andThen(Traversals.forList())
     */
    public static Traversal<OrderRecord, LineItemRecord> eachItem() {
      return items().andThen(Traversals.forList());
    }
  }

  // =========================================================================
  // Exercises
  // =========================================================================

  /**
   * Exercise 1: Using @Wither with java.time.LocalDate
   *
   * <p>The @Wither annotation generates lenses for types that use wither methods like withYear(),
   * withMonth(), etc. This is common in the java.time package.
   *
   * <p>Task: Use the generated lenses to manipulate LocalDate
   */
  @Test
  void exercise1_witherPattern() {
    LocalDate date = LocalDate.of(2024, 6, 15);

    // TODO: Replace null with lens to get the year
    // Hint: LocalDateOptics.year().get(date)
    Integer year = answerRequired();

    // TODO: Replace null with lens to set a new year
    // Hint: LocalDateOptics.year().set(2025, date)
    LocalDate nextYear = answerRequired();

    // TODO: Replace null with lens to set the month (December = 12)
    // Hint: LocalDateOptics.monthValue().set(12, date)
    LocalDate december = answerRequired();

    assertThat(year).isEqualTo(2024);
    assertThat(nextYear).isEqualTo(LocalDate.of(2025, 6, 15));
    assertThat(december).isEqualTo(LocalDate.of(2024, 12, 15));
  }

  /**
   * Exercise 2: Using modify with @Wither lenses
   *
   * <p>Lenses support modify() to transform values using a function.
   *
   * <p>Task: Use modify to increment/transform date components
   */
  @Test
  void exercise2_modifyWithWitherLens() {
    LocalDate date = LocalDate.of(2024, 6, 15);

    // TODO: Replace null with a modified date that increments year by 1
    // Hint: LocalDateOptics.year().modify(y -> y + 1, date)
    LocalDate incremented = answerRequired();

    // TODO: Replace null with a date set to the last day of the current month
    // Hint: Use dayOfMonth lens with modify
    LocalDate lastDay = answerRequired();

    assertThat(incremented).isEqualTo(LocalDate.of(2025, 6, 15));
    assertThat(lastDay.getDayOfMonth()).isEqualTo(30); // June has 30 days
  }

  /**
   * Exercise 3: Using @ViaBuilder with builder-pattern types
   *
   * <p>The @ViaBuilder annotation generates lenses for types that use toBuilder().build() patterns,
   * common in Lombok, AutoValue, Immutables, and similar libraries.
   *
   * <p>Task: Use the generated lenses to manipulate CustomerRecord
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

    // TODO: Replace null with lens to get the name
    // Hint: JooqStyleRecordOptics.name().get(customer)
    String name = answerRequired();

    // TODO: Replace null with lens to set a new email
    // Hint: JooqStyleRecordOptics.email().set("new@example.com", customer)
    CustomerRecord updated = answerRequired();

    assertThat(name).isEqualTo("Alice");
    assertThat(updated.email()).isEqualTo("new@example.com");
    assertThat(updated.name()).isEqualTo("Alice"); // Other fields unchanged
  }

  /**
   * Exercise 4: Modifying values with @ViaBuilder lenses
   *
   * <p>Task: Use modify to transform CustomerRecord fields
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

    // TODO: Replace null with a customer with 20% increased credit limit
    // Hint: JooqStyleRecordOptics.creditLimit().modify(...)
    CustomerRecord increased = answerRequired();

    // TODO: Replace null with a customer with uppercased name
    // Hint: JooqStyleRecordOptics.name().modify(String::toUpperCase, customer)
    CustomerRecord uppercased = answerRequired();

    assertThat(increased.creditLimit()).isEqualByComparingTo(new BigDecimal("1200.00"));
    assertThat(uppercased.name()).isEqualTo("ALICE SMITH");
  }

  /**
   * Exercise 5: Using @MatchWhen prisms for type-safe extraction
   *
   * <p>The @MatchWhen annotation generates prisms for types that use predicate-based matching
   * (isX()/asX() methods) instead of sealed type hierarchies. This is common in JSON libraries.
   *
   * <p>Task: Use prisms to safely extract typed values
   */
  @Test
  void exercise5_matchWhenPrisms() {
    JsonValue textValue = new JsonString("hello");
    JsonValue numberValue = new JsonNumber(42.0);
    JsonValue boolValue = new JsonBool(true);

    // TODO: Replace Optional.empty() with prism extraction of text
    // Hint: JsonValueOptics.text().getOptional(textValue)
    Optional<String> text = answerRequired();

    // TODO: Replace Optional.empty() with prism extraction of number
    // Hint: JsonValueOptics.number().getOptional(numberValue)
    Optional<Double> number = answerRequired();

    // TODO: Replace Optional.empty() with prism extraction of bool
    // Hint: JsonValueOptics.bool().getOptional(boolValue)
    Optional<Boolean> bool = answerRequired();

    assertThat(text).contains("hello");
    assertThat(number).contains(42.0);
    assertThat(bool).contains(true);
  }

  /**
   * Exercise 6: Prisms return empty for mismatched types
   *
   * <p>Prisms safely handle type mismatches by returning Optional.empty().
   *
   * <p>Task: Verify prism safety with mismatched types
   */
  @Test
  void exercise6_prismTypeSafety() {
    JsonValue textValue = new JsonString("hello");

    // TODO: Replace Optional.of(0.0) with prism attempt to extract number from text
    // Hint: JsonValueOptics.number().getOptional(textValue)
    Optional<Double> numberFromText = answerRequired();

    // TODO: Replace Optional.of(false) with prism attempt to extract bool from text
    // Hint: JsonValueOptics.bool().getOptional(textValue)
    Optional<Boolean> boolFromText = answerRequired();

    // Both should be empty - type mismatch handled safely
    assertThat(numberFromText).isEmpty();
    assertThat(boolFromText).isEmpty();
  }

  /**
   * Exercise 7: Composing spec interface optics
   *
   * <p>Optics generated from spec interfaces compose just like any other optics.
   *
   * <p>Task: Compose lenses to perform complex updates
   */
  @Test
  void exercise7_composingSpecOptics() {
    LocalDate date = LocalDate.of(2024, 6, 15);

    // Compose multiple updates: next year, January, first day
    // TODO: Replace null with composed updates to create New Year's Day next year
    // Hint: Chain multiple lens operations or use modify
    LocalDate newYearsDay = answerRequired();

    assertThat(newYearsDay).isEqualTo(LocalDate.of(2025, 1, 1));

    // Compose CustomerRecord updates
    CustomerRecord customer =
        CustomerRecord.builder()
            .id(1L)
            .name("Alice")
            .email("alice@old.com")
            .creditLimit(new BigDecimal("1000.00"))
            .build();

    // TODO: Replace null with composed updates:
    // - Change name to "Bob"
    // - Change email to "bob@new.com"
    // - Double the credit limit
    CustomerRecord fullyUpdated = answerRequired();

    assertThat(fullyUpdated.name()).isEqualTo("Bob");
    assertThat(fullyUpdated.email()).isEqualTo("bob@new.com");
    assertThat(fullyUpdated.creditLimit()).isEqualByComparingTo(new BigDecimal("2000.00"));
  }

  /**
   * Exercise 8: Building values with prisms
   *
   * <p>Prisms can construct values as well as extract them using build() or reverseGet().
   *
   * <p>Task: Use prisms to construct typed values
   */
  @Test
  void exercise8_buildingWithPrisms() {
    // TODO: Replace null with prism to build a JsonString from "world"
    // Hint: JsonValueOptics.text().build("world")
    JsonValue builtText = answerRequired();

    // TODO: Replace null with prism to build a JsonNumber from 99.9
    // Hint: JsonValueOptics.number().build(99.9)
    JsonValue builtNumber = answerRequired();

    assertThat(builtText).isEqualTo(new JsonString("world"));
    assertThat(builtNumber).isEqualTo(new JsonNumber(99.9));
  }

  /**
   * Exercise 9: JOOQ Integration - Result<R> implements List
   *
   * <p>JOOQ's {@code Result<R>} implements {@code List<R>}, so you don't need a spec interface -
   * you can use standard traversals directly!
   *
   * <p>Task: Understand how JOOQ types integrate with optics
   */
  @Test
  void exercise9_jooqResultIntegration() {
    // JOOQ's Result<R> implements List<R>
    // This means you can use Traversals.list() directly - no spec interface needed!

    // In real code, you'd get a Result from a query:
    // Result<Record> result = ctx.select(...).from(...).fetch();

    // TODO: Verify that Result implements List
    // Hint: Check with instanceof
    Result<Record> result = DSL.using(SQLDialect.DEFAULT).newResult();
    boolean isResultAList = answerRequired();

    assertThat(isResultAList).isTrue();

    // For JOOQ-generated immutable POJOs with builder pattern,
    // use @ViaBuilder spec interfaces (like JooqStyleRecordOpticsSpec)

    // Key insight: Not all external types need spec interfaces!
    // - Result<R> -> use Traversals.list() directly
    // - Generated POJOs with builders -> use @ViaBuilder spec
    // - Types with withX() methods -> use @Wither spec
  }

  /**
   * Exercise 10: Using @ThroughField for collection traversals
   *
   * <p>The @ThroughField annotation creates traversals into collection fields. It composes a lens
   * (to the collection) with a traversal (into the collection's elements).
   *
   * <p><b>Auto-detection:</b> The processor automatically detects which traversal to use:
   *
   * <ul>
   *   <li>List&lt;A&gt; → Traversals.forList()
   *   <li>Set&lt;A&gt; → Traversals.forSet()
   *   <li>Optional&lt;A&gt; → Traversals.forOptional()
   *   <li>A[] → Traversals.forArray()
   *   <li>Map&lt;K,V&gt; → Traversals.forMapValues()
   * </ul>
   *
   * <p>Task: Use the eachItem() traversal to extract data from an order
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

    // TODO: Replace null with traversal to get all product names
    // Hint:
    // Traversals.getAll(OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.productName()),
    // order)
    List<String> productNames = answerRequired();

    // TODO: Replace null with traversal to get all quantities
    // Hint:
    // Traversals.getAll(OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.quantity()),
    // order)
    List<Integer> quantities = answerRequired();

    assertThat(productNames).containsExactly("Widget", "Gadget");
    assertThat(quantities).containsExactly(2, 1);
  }

  /**
   * Exercise 11: Modifying collection elements with @ThroughField
   *
   * <p>Traversals support modify() to transform all targeted elements.
   *
   * <p>Task: Use traversals to bulk-update order items
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

    // TODO: Replace null with an order where all product names are uppercased
    // Hint: Traversals.modify(
    //           OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.productName()),
    //           String::toUpperCase, order)
    OrderRecord uppercased = answerRequired();

    // TODO: Replace null with an order where all quantities are doubled
    // Hint: Traversals.modify(eachItem().andThen(quantity()), qty -> qty * 2, order)
    OrderRecord doubled = answerRequired();

    // TODO: Replace null with an order where all prices are reduced by 10%
    // Hint: Traversals.modify(eachItem().andThen(unitPrice()), price -> price.multiply(...), order)
    OrderRecord discounted = answerRequired();

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
   * Exercise 12: Composing @ThroughField with other optics
   *
   * <p>@ThroughField traversals compose naturally with lenses, prisms, and other traversals.
   *
   * <p>Task: Build complex transformations by composing optics
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

    // Compose @Wither lens (for LocalDate) with @ViaBuilder lens (for OrderRecord)
    // Task: Move the order to next year while keeping month and day
    // TODO: Replace null with order date set to 2025
    // Hint: OrderRecordOptics.orderDate()
    //           .andThen(LocalDateOptics.year())
    //           .set(2025, order)
    OrderRecord nextYear = answerRequired();

    // Compose @ThroughField traversal with multiple @ViaBuilder lenses
    // Task: Add a suffix to all product IDs
    // TODO: Replace null with order where all product IDs have "-v2" suffix
    // Hint: Traversals.modify(
    //           OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.productId()),
    //           id -> id + "-v2", order)
    OrderRecord versioned = answerRequired();

    assertThat(nextYear.orderDate()).isEqualTo(LocalDate.of(2025, 1, 15));
    assertThat(
            Traversals.getAll(
                OrderRecordOptics.eachItem().andThen(LineItemRecordOptics.productId()), versioned))
        .containsExactly("SKU-001-v2");
  }

  /**
   * Congratulations! You've completed Tutorial 16: Optics Spec Interfaces
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How OpticsSpec&lt;S&gt; defines optics for external types
   *   <li>How @Wither generates lenses using wither methods (java.time.LocalDate)
   *   <li>How @ViaBuilder generates lenses using builder patterns (JOOQ, Lombok, etc.)
   *   <li>How @InstanceOf generates prisms for subtype hierarchies (Jackson JsonNode)
   *   <li>How @ThroughField creates traversals into collection fields with auto-detection
   *   <li>How some types (like JOOQ Result) work with standard traversals directly
   *   <li>How spec interface optics compose like any other optics
   * </ul>
   *
   * <p>See the working examples in org.higherkindedj.example.optics.external:
   *
   * <ul>
   *   <li>LocalDateOpticsSpec - java.time.LocalDate with @Wither
   *   <li>JsonNodeOpticsSpec - Jackson JsonNode with @InstanceOf prisms
   *   <li>JooqStyleRecordOpticsSpec - JOOQ-style POJOs with @ViaBuilder
   *   <li>OrderRecordOpticsSpec - @ThroughField with auto-detected List traversal
   *   <li>LineItemRecordOpticsSpec - Composable optics for nested traversals
   *   <li>SpecInterfaceUsageExample - runnable demonstration with real JOOQ integration
   * </ul>
   */
}
