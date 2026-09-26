// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks.mapping;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.mapstruct.factory.Mappers;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * What the generated mapper costs, against the alternatives a team already has: a hand-written
 * mapper that throws on the first problem, MapStruct, and MapStruct behind Bean Validation.
 *
 * <p>Two pairs: a nested order (a customer, a list of line items, an enum, several converted
 * values) and a flat record of ten fields. Each is measured for {@code build}, a {@code parse} of a
 * valid wire, and a {@code parse} of a wire with five bad fields. On the bad wire the approaches do
 * different work, and the benchmark measures that difference: the generated mapper and Bean
 * Validation report all five problems; the hand-written mapper and plain MapStruct stop at the
 * first exception.
 *
 * <p>Run with: {@code ./gradlew :hkj-benchmarks:jmh -Pincludes=".*MappingBenchmark.*"}, and add
 * {@code -Pjmh.profilers=gc} for allocation per operation.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class MappingBenchmark {

  private static final int BAD_FIELDS = 5;

  private final OrderMapstruct orderMapstruct = Mappers.getMapper(OrderMapstruct.class);
  private final FlatMapstruct flatMapstruct = Mappers.getMapper(FlatMapstruct.class);

  private ValidatorFactory validatorFactory;
  private Validator validator;

  private Order order;
  private OrderDto validOrder;
  private OrderDto badOrder;
  private ValidatedOrderDto validOrderForValidation;
  private ValidatedOrderDto badOrderForValidation;

  private FlatRecord flat;
  private FlatDto validFlat;
  private FlatDto badFlat;
  private ValidatedFlatDto validFlatForValidation;
  private ValidatedFlatDto badFlatForValidation;

  @Setup(Level.Trial)
  public void setUp() {
    validatorFactory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
    validator = validatorFactory.getValidator();

    order =
        new Order(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            new Customer("Ada Lovelace", new EmailAddress("ada@example.org")),
            List.of(
                new LineItem("SKU-1", 2, new BigDecimal("9.99")),
                new LineItem("SKU-2", 1, new BigDecimal("24.50"))),
            Instant.parse("2026-07-28T12:34:56Z"),
            Currency.getInstance("GBP"),
            OrderStatus.PAID);
    validOrder = OrderMappingImpl.INSTANCE.build(order);
    badOrder =
        new OrderDto(
            "NOPE",
            new CustomerDto("Ada Lovelace", "not-an-email"),
            List.of(new LineItemDto("SKU-1", "2", "9.99"), new LineItemDto("SKU-2", "1", "1E+3")),
            "28/07/2026",
            "GBP",
            "DISPATCHED");
    validOrderForValidation = forValidation(validOrder);
    badOrderForValidation = forValidation(badOrder);

    flat =
        new FlatRecord(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            "Ada Lovelace",
            new EmailAddress("ada@example.org"),
            2,
            new BigDecimal("9.99"),
            Instant.parse("2026-07-28T12:34:56Z"),
            Currency.getInstance("GBP"),
            OrderStatus.PAID,
            LocalDate.parse("2026-08-01"),
            true);
    validFlat = FlatMappingImpl.INSTANCE.build(flat);
    badFlat =
        new FlatDto(
            "NOPE",
            "Ada Lovelace",
            "not-an-email",
            "two",
            "9.99",
            "28/07/2026",
            "GBP",
            "PAID",
            "2026-08-01",
            "yes");
    validFlatForValidation = forValidation(validFlat);
    badFlatForValidation = forValidation(badFlat);

    checkFixtures();
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    validatorFactory.close();
  }

  // The nested order pair.

  @Benchmark
  public OrderDto orderBuildHkj() {
    return OrderMappingImpl.INSTANCE.build(order);
  }

  @Benchmark
  public OrderDto orderBuildHandWritten() {
    return HandWrittenOrderMapper.toDto(order);
  }

  @Benchmark
  public OrderDto orderBuildMapstruct() {
    return orderMapstruct.toDto(order);
  }

  @Benchmark
  public Validated<NonEmptyList<FieldError>, Order> orderParseValidHkj() {
    return OrderMappingImpl.INSTANCE.parse(validOrder);
  }

  @Benchmark
  public Order orderParseValidHandWritten() {
    return HandWrittenOrderMapper.toDomain(validOrder);
  }

  @Benchmark
  public Order orderParseValidMapstruct() {
    return orderMapstruct.toDomain(validOrder);
  }

  @Benchmark
  public Object orderParseValidMapstructBeanValidation() {
    Set<ConstraintViolation<ValidatedOrderDto>> violations =
        validator.validate(validOrderForValidation);
    return violations.isEmpty() ? orderMapstruct.toDomain(validOrderForValidation) : violations;
  }

  @Benchmark
  public Validated<NonEmptyList<FieldError>, Order> orderParseInvalidHkj() {
    return OrderMappingImpl.INSTANCE.parse(badOrder);
  }

  @Benchmark
  public Object orderParseInvalidHandWritten() {
    try {
      return HandWrittenOrderMapper.toDomain(badOrder);
    } catch (RuntimeException firstProblem) {
      return firstProblem;
    }
  }

  @Benchmark
  public Object orderParseInvalidMapstruct() {
    try {
      return orderMapstruct.toDomain(badOrder);
    } catch (RuntimeException firstProblem) {
      return firstProblem;
    }
  }

  @Benchmark
  public Object orderParseInvalidMapstructBeanValidation() {
    Set<ConstraintViolation<ValidatedOrderDto>> violations =
        validator.validate(badOrderForValidation);
    return violations.isEmpty() ? orderMapstruct.toDomain(badOrderForValidation) : violations;
  }

  // The flat pair.

  @Benchmark
  public FlatDto flatBuildHkj() {
    return FlatMappingImpl.INSTANCE.build(flat);
  }

  @Benchmark
  public FlatDto flatBuildHandWritten() {
    return HandWrittenFlatMapper.toDto(flat);
  }

  @Benchmark
  public FlatDto flatBuildMapstruct() {
    return flatMapstruct.toDto(flat);
  }

  @Benchmark
  public Validated<NonEmptyList<FieldError>, FlatRecord> flatParseValidHkj() {
    return FlatMappingImpl.INSTANCE.parse(validFlat);
  }

  @Benchmark
  public FlatRecord flatParseValidHandWritten() {
    return HandWrittenFlatMapper.toDomain(validFlat);
  }

  @Benchmark
  public FlatRecord flatParseValidMapstruct() {
    return flatMapstruct.toDomain(validFlat);
  }

  @Benchmark
  public Object flatParseValidMapstructBeanValidation() {
    Set<ConstraintViolation<ValidatedFlatDto>> violations =
        validator.validate(validFlatForValidation);
    return violations.isEmpty() ? flatMapstruct.toDomain(validFlatForValidation) : violations;
  }

  @Benchmark
  public Validated<NonEmptyList<FieldError>, FlatRecord> flatParseInvalidHkj() {
    return FlatMappingImpl.INSTANCE.parse(badFlat);
  }

  @Benchmark
  public Object flatParseInvalidHandWritten() {
    try {
      return HandWrittenFlatMapper.toDomain(badFlat);
    } catch (RuntimeException firstProblem) {
      return firstProblem;
    }
  }

  @Benchmark
  public Object flatParseInvalidMapstruct() {
    try {
      return flatMapstruct.toDomain(badFlat);
    } catch (RuntimeException firstProblem) {
      return firstProblem;
    }
  }

  @Benchmark
  public Object flatParseInvalidMapstructBeanValidation() {
    Set<ConstraintViolation<ValidatedFlatDto>> violations =
        validator.validate(badFlatForValidation);
    return violations.isEmpty() ? flatMapstruct.toDomain(badFlatForValidation) : violations;
  }

  // Fixtures.

  private static ValidatedOrderDto forValidation(OrderDto dto) {
    return new ValidatedOrderDto(
        dto.id(),
        new ValidatedCustomerDto(dto.customer().name(), dto.customer().email()),
        dto.lines().stream()
            .map(line -> new ValidatedLineItemDto(line.sku(), line.quantity(), line.price()))
            .toList(),
        dto.placedAt(),
        dto.currency(),
        dto.status());
  }

  private static ValidatedFlatDto forValidation(FlatDto dto) {
    return new ValidatedFlatDto(
        dto.id(),
        dto.name(),
        dto.email(),
        dto.quantity(),
        dto.price(),
        dto.createdAt(),
        dto.currency(),
        dto.status(),
        dto.dueDate(),
        dto.active());
  }

  /**
   * Holds each fixture to the work the benchmark claims it does, so a broken fixture fails the run
   * rather than measuring the wrong thing: every approach maps the valid wires back to the same
   * domain value, the generated mapper and Bean Validation each find five problems on the bad
   * wires, and the other two stop at an exception.
   */
  private void checkFixtures() {
    require(orderParseValidHkj().isValid(), "the generated mapper parses the valid order");
    require(order.equals(orderParseValidHandWritten()), "the hand-written mapper round-trips");
    require(order.equals(orderParseValidMapstruct()), "MapStruct round-trips the order");
    require(order.equals(orderParseValidMapstructBeanValidation()), "Bean Validation passes it");
    require(validOrder.equals(orderBuildHandWritten()), "the hand-written build agrees");
    require(validOrder.equals(orderBuildMapstruct()), "the MapStruct build agrees");
    require(errorCount(orderParseInvalidHkj()) == BAD_FIELDS, "five located order errors");
    require(violationCount(orderParseInvalidMapstructBeanValidation()) == BAD_FIELDS, "five BV");
    require(orderParseInvalidHandWritten() instanceof RuntimeException, "hand-written throws");
    require(orderParseInvalidMapstruct() instanceof RuntimeException, "MapStruct throws");

    require(flatParseValidHkj().isValid(), "the generated mapper parses the valid flat record");
    require(flat.equals(flatParseValidHandWritten()), "the hand-written mapper round-trips");
    require(flat.equals(flatParseValidMapstruct()), "MapStruct round-trips the flat record");
    require(flat.equals(flatParseValidMapstructBeanValidation()), "Bean Validation passes it");
    require(validFlat.equals(flatBuildHandWritten()), "the hand-written build agrees");
    require(validFlat.equals(flatBuildMapstruct()), "the MapStruct build agrees");
    require(errorCount(flatParseInvalidHkj()) == BAD_FIELDS, "five located flat errors");
    require(violationCount(flatParseInvalidMapstructBeanValidation()) == BAD_FIELDS, "five BV");
    require(flatParseInvalidHandWritten() instanceof RuntimeException, "hand-written throws");
    require(flatParseInvalidMapstruct() instanceof RuntimeException, "MapStruct throws");
  }

  private static int errorCount(Validated<NonEmptyList<FieldError>, ?> parsed) {
    return parsed.fold(NonEmptyList::size, value -> 0);
  }

  private static int violationCount(Object result) {
    return result instanceof Set<?> violations ? violations.size() : 0;
  }

  private static void require(boolean holds, String what) {
    if (!holds) {
      throw new IllegalStateException("benchmark fixture broken: " + what);
    }
  }
}
