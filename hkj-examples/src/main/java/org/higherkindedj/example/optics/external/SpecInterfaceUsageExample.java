// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.external;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.NumericNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * A runnable example demonstrating spec interfaces for external third-party types.
 *
 * <p>This example shows how to use generated optics from spec interfaces to work with:
 *
 * <ul>
 *   <li>{@code java.time.LocalDate} - using {@code @Wither} for date components
 *   <li>{@code tools.jackson.databind.JsonNode} - using {@code @InstanceOf} for type prisms
 *   <li>{@code CustomerRecord} - using {@code @ViaBuilder} for builder-based types
 * </ul>
 *
 * <p>Each section demonstrates the generated optics working with real third-party types that you
 * cannot annotate directly.
 */
public class SpecInterfaceUsageExample {

  public static void main(String[] args) throws Exception {
    System.out.println("=".repeat(70));
    System.out.println("Spec Interface Examples - Optics for External Types");
    System.out.println("=".repeat(70));
    System.out.println();

    demonstrateLocalDateOptics();
    demonstrateJsonNodeOptics();
    demonstrateJooqOptics();
  }

  // =========================================================================
  // EXAMPLE 1: java.time.LocalDate with @Wither
  // =========================================================================
  private static void demonstrateLocalDateOptics() {
    System.out.println("-".repeat(70));
    System.out.println("1. LocalDate Optics (@Wither pattern)");
    System.out.println("-".repeat(70));

    LocalDate date = LocalDate.of(2024, 6, 15);
    System.out.println("Original date: " + date);
    System.out.println();

    // --- Using generated lenses to get values ---
    System.out.println("Getting date components:");
    System.out.println("  Year:        " + LocalDateOptics.year().get(date));
    System.out.println("  Month value: " + LocalDateOptics.monthValue().get(date));
    System.out.println("  Day of month:" + LocalDateOptics.dayOfMonth().get(date));
    System.out.println("  Day of year: " + LocalDateOptics.dayOfYear().get(date));
    System.out.println();

    // --- Using generated lenses to set values ---
    System.out.println("Setting new values (each creates a new LocalDate):");
    LocalDate nextYear = LocalDateOptics.year().set(2025, date);
    System.out.println("  Set year to 2025:     " + nextYear);

    LocalDate decemberDate = LocalDateOptics.monthValue().set(12, date); // December = 12
    System.out.println("  Set month to December:" + decemberDate);

    LocalDate firstDay = LocalDateOptics.dayOfMonth().set(1, date);
    System.out.println("  Set day to 1:         " + firstDay);
    System.out.println();

    // --- Using modify for transformations ---
    System.out.println("Modifying values:");
    LocalDate incremented = LocalDateOptics.year().modify(y -> y + 1, date);
    System.out.println("  Increment year:       " + incremented);

    LocalDate endOfMonth =
        LocalDateOptics.dayOfMonth().set(date.getMonth().length(date.isLeapYear()), date);
    System.out.println("  Set to end of month:  " + endOfMonth);
    System.out.println();

    // --- Composing lenses for complex updates ---
    System.out.println("Composing multiple updates:");
    LocalDate newYearsDay =
        LocalDateOptics.monthValue()
            .set(
                1,
                LocalDateOptics.dayOfMonth()
                    .set(1, LocalDateOptics.year().modify(y -> y + 1, date)));
    System.out.println("  Next New Year's Day:  " + newYearsDay);
    System.out.println();
  }

  // =========================================================================
  // EXAMPLE 2: Jackson JsonNode with @InstanceOf
  // =========================================================================
  private static void demonstrateJsonNodeOptics() throws Exception {
    System.out.println("-".repeat(70));
    System.out.println("2. JsonNode Optics (@InstanceOf prism pattern)");
    System.out.println("-".repeat(70));

    ObjectMapper mapper = new ObjectMapper();

    // Create various JSON structures
    String jsonString =
        """
        {
          "name": "Alice",
          "age": 30,
          "active": true,
          "balance": 1234.56,
          "tags": ["java", "optics", "functional"],
          "address": {
            "city": "London",
            "postcode": "SW1A 1AA"
          }
        }
        """;

    JsonNode root = mapper.readTree(jsonString);
    System.out.println("JSON document loaded");
    System.out.println();

    // --- Using prisms to safely extract node types ---
    System.out.println("Extracting node types with prisms:");

    // Text prism - returns StringNode, then extract value
    Optional<StringNode> maybeName = JsonNodeOptics.text().getOptional(root.get("name"));
    System.out.println(
        "  name (text):     " + maybeName.map(StringNode::stringValue).orElse("not found"));

    // Numeric prism - returns NumericNode (base class for all numeric nodes)
    Optional<NumericNode> maybeAge = JsonNodeOptics.numeric().getOptional(root.get("age"));
    System.out.println(
        "  age (numeric):   "
            + maybeAge.map(NumericNode::intValue).map(String::valueOf).orElse("not found"));

    Optional<NumericNode> maybeBalance = JsonNodeOptics.numeric().getOptional(root.get("balance"));
    System.out.println(
        "  balance (numeric):"
            + maybeBalance.map(NumericNode::doubleValue).map(String::valueOf).orElse("not found"));

    // Boolean prism - returns BooleanNode
    Optional<BooleanNode> maybeActive = JsonNodeOptics.bool().getOptional(root.get("active"));
    System.out.println(
        "  active (bool):   "
            + maybeActive.map(BooleanNode::booleanValue).map(String::valueOf).orElse("not found"));
    System.out.println();

    // --- Using prisms for container types ---
    System.out.println("Extracting container types:");

    Optional<ArrayNode> maybeTags = JsonNodeOptics.array().getOptional(root.get("tags"));
    System.out.println(
        "  tags (array):    " + maybeTags.map(a -> a.size() + " elements").orElse("not found"));

    Optional<ObjectNode> maybeAddress = JsonNodeOptics.object().getOptional(root.get("address"));
    System.out.println(
        "  address (object):" + maybeAddress.map(o -> o.size() + " fields").orElse("not found"));
    System.out.println();

    // --- Prisms correctly return empty for mismatched types ---
    System.out.println("Type-safe extraction (wrong type returns empty):");
    Optional<NumericNode> nameAsNumeric = JsonNodeOptics.numeric().getOptional(root.get("name"));
    System.out.println(
        "  name as numeric: "
            + nameAsNumeric.map(n -> String.valueOf(n.intValue())).orElse("empty (correct!)"));

    Optional<ArrayNode> ageAsArray = JsonNodeOptics.array().getOptional(root.get("age"));
    System.out.println(
        "  age as array:    " + ageAsArray.map(ArrayNode::toString).orElse("empty (correct!)"));
    System.out.println();

    // --- Working with extracted containers ---
    if (maybeAddress.isPresent()) {
      ObjectNode address = maybeAddress.get();
      System.out.println("Nested object access:");
      Optional<StringNode> city = JsonNodeOptics.text().getOptional(address.get("city"));
      System.out.println(
          "  address.city:    " + city.map(StringNode::stringValue).orElse("not found"));
    }
    System.out.println();
  }

  // =========================================================================
  // EXAMPLE 3: JOOQ Integration with @ViaBuilder and Traversals
  // =========================================================================
  private static void demonstrateJooqOptics() {
    System.out.println("-".repeat(70));
    System.out.println("3. JOOQ Integration (@ViaBuilder for generated POJOs)");
    System.out.println("-".repeat(70));

    // -------------------------------------------------------------------------
    // PART A: JOOQ-Generated POJOs with @ViaBuilder
    // -------------------------------------------------------------------------
    // When JOOQ generates immutable POJOs (with configuration), they have:
    // - Getter methods: id(), name(), email(), etc.
    // - toBuilder() method returning a mutable builder
    // - Builder with setter methods and build()
    //
    // CustomerRecord simulates this pattern. In real code, this would be
    // a JOOQ-generated class from your database schema.
    // -------------------------------------------------------------------------

    System.out.println("PART A: JOOQ-style generated POJO with builder pattern");
    System.out.println();

    CustomerRecord customer =
        CustomerRecord.builder()
            .id(1L)
            .name("Alice Smith")
            .email("alice@example.com")
            .creditLimit(new BigDecimal("5000.00"))
            .build();

    System.out.println("Original customer: " + customer);
    System.out.println();

    // --- Using generated lenses to get values ---
    System.out.println("Getting customer fields via lenses:");
    System.out.println("  ID:           " + JooqStyleRecordOptics.id().get(customer));
    System.out.println("  Name:         " + JooqStyleRecordOptics.name().get(customer));
    System.out.println("  Email:        " + JooqStyleRecordOptics.email().get(customer));
    System.out.println("  Credit limit: " + JooqStyleRecordOptics.creditLimit().get(customer));
    System.out.println();

    // --- Using generated lenses to set values ---
    System.out.println("Setting new values (each creates a new CustomerRecord):");
    CustomerRecord renamed = JooqStyleRecordOptics.name().set("Alice Johnson", customer);
    System.out.println("  New name:     " + renamed);

    CustomerRecord newEmail =
        JooqStyleRecordOptics.email().set("alice.johnson@example.com", customer);
    System.out.println("  New email:    " + newEmail);
    System.out.println();

    // --- Using modify for transformations ---
    System.out.println("Modifying values:");
    CustomerRecord increased =
        JooqStyleRecordOptics.creditLimit()
            .modify(limit -> limit.multiply(new BigDecimal("1.20")), customer);
    System.out.println("  +20% credit:  " + increased);

    CustomerRecord uppercased = JooqStyleRecordOptics.name().modify(String::toUpperCase, customer);
    System.out.println("  Upper name:   " + uppercased);
    System.out.println();

    // -------------------------------------------------------------------------
    // PART B: JOOQ Result<R> with Standard Traversals
    // -------------------------------------------------------------------------
    // JOOQ's Result<R> implements List<R>, so you can use Traversals.list()
    // directly - no spec interface needed!
    // -------------------------------------------------------------------------

    System.out.println("PART B: JOOQ Result<R> with standard traversals");
    System.out.println();

    // Create a simulated JOOQ Result (in real code, this comes from a query)
    // Result<Record> result = ctx.select(...).from(...).fetch();
    Result<Record> result = DSL.using(SQLDialect.DEFAULT).newResult();

    // Add some records using JOOQ DSL
    result.add(
        DSL.using(SQLDialect.DEFAULT)
            .newRecord(
                DSL.field("id", Long.class),
                DSL.field("name", String.class),
                DSL.field("balance", BigDecimal.class)));

    System.out.println("JOOQ Result traversal pattern:");
    System.out.println("  // Result<R> implements List<R>, so use Traversals.list() directly:");
    System.out.println("  // List<Record> all = Traversals.getAll(Traversals.list(), result);");
    System.out.println();

    // Demonstrate that Result<R> is just a List
    System.out.println("  Result size: " + result.size() + " records");
    System.out.println("  Result is List: " + (result instanceof List));
    System.out.println();

    // Show the traversal pattern
    System.out.println("Common JOOQ + optics patterns:");
    System.out.println("  1. Use Traversals.list() for Result<R> iteration");
    System.out.println("  2. Use @ViaBuilder spec for JOOQ-generated immutable POJOs");
    System.out.println("  3. Compose record lenses with result traversals");
    System.out.println();

    System.out.println("Original customer unchanged: " + customer);
  }
}
