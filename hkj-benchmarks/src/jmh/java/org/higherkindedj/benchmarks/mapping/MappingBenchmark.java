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
import java.util.function.Supplier;
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
import org.openjdk.jmh.annotations.Param;
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
 * <p>Three things shape the numbers:
 *
 * <ul>
 *   <li>The generated mapper calls each leaf method on every {@code build} and {@code parse}, as a
 *       spec written the way the book teaches does, so a leaf that builds its codec per call is
 *       part of the cost.
 *   <li>A rejection that throws inside a codec pays for a stack trace, which grows with the
 *       caller's stack. The bad-wire benchmarks therefore run at two depths ({@link Caller}).
 *   <li>Bean Validation runs with {@link ParameterMessageInterpolator}, lighter than the
 *       expression-language interpolator a default Spring Boot set-up uses, so its figures are
 *       favourable to it. Its patterns check each field's shape, where a codec also checks the
 *       value and its canonical form, so a wire that passes validation can still fail MapStruct's
 *       conversion.
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-benchmarks:jmh -Pincludes=".*MappingBenchmark.*"}. For figures
 * worth quoting, run the benchmark jar with more iterations, two forks and {@code -prof gc}, as
 * {@code hkj-book/src/benchmarks.md} shows.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class MappingBenchmark {

  private static final int BAD_FIELDS = 5;

  /**
   * How deep the caller's stack is when a bad wire is parsed: shallow, as JMH calls a benchmark,
   * and 100 frames deeper, nearer the depth a web framework calls a controller at.
   */
  @State(Scope.Thread)
  public static class Caller {
    @Param({"0", "100"})
    public int depth;
  }

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
  public Validated<NonEmptyList<FieldError>, Order> orderParseInvalidHkj(Caller caller) {
    return atDepth(caller.depth, this::parseBadOrderHkj);
  }

  @Benchmark
  public Object orderParseInvalidHandWritten(Caller caller) {
    return atDepth(caller.depth, this::parseBadOrderHandWritten);
  }

  @Benchmark
  public Object orderParseInvalidMapstruct(Caller caller) {
    return atDepth(caller.depth, this::parseBadOrderMapstruct);
  }

  @Benchmark
  public Object orderParseInvalidMapstructBeanValidation(Caller caller) {
    return atDepth(caller.depth, this::parseBadOrderMapstructBeanValidation);
  }

  private Validated<NonEmptyList<FieldError>, Order> parseBadOrderHkj() {
    return OrderMappingImpl.INSTANCE.parse(badOrder);
  }

  private Object parseBadOrderHandWritten() {
    try {
      return HandWrittenOrderMapper.toDomain(badOrder);
    } catch (RuntimeException firstProblem) {
      return firstProblem;
    }
  }

  private Object parseBadOrderMapstruct() {
    try {
      return orderMapstruct.toDomain(badOrder);
    } catch (RuntimeException firstProblem) {
      return firstProblem;
    }
  }

  private Object parseBadOrderMapstructBeanValidation() {
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
  public Validated<NonEmptyList<FieldError>, FlatRecord> flatParseInvalidHkj(Caller caller) {
    return atDepth(caller.depth, this::parseBadFlatHkj);
  }

  @Benchmark
  public Object flatParseInvalidHandWritten(Caller caller) {
    return atDepth(caller.depth, this::parseBadFlatHandWritten);
  }

  @Benchmark
  public Object flatParseInvalidMapstruct(Caller caller) {
    return atDepth(caller.depth, this::parseBadFlatMapstruct);
  }

  @Benchmark
  public Object flatParseInvalidMapstructBeanValidation(Caller caller) {
    return atDepth(caller.depth, this::parseBadFlatMapstructBeanValidation);
  }

  private Validated<NonEmptyList<FieldError>, FlatRecord> parseBadFlatHkj() {
    return FlatMappingImpl.INSTANCE.parse(badFlat);
  }

  private Object parseBadFlatHandWritten() {
    try {
      return HandWrittenFlatMapper.toDomain(badFlat);
    } catch (RuntimeException firstProblem) {
      return firstProblem;
    }
  }

  private Object parseBadFlatMapstruct() {
    try {
      return flatMapstruct.toDomain(badFlat);
    } catch (RuntimeException firstProblem) {
      return firstProblem;
    }
  }

  private Object parseBadFlatMapstructBeanValidation() {
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
    require(
        orderParseValidHkj().fold(errors -> false, order::equals),
        "the generated mapper round-trips");
    require(order.equals(orderParseValidHandWritten()), "the hand-written mapper round-trips");
    require(order.equals(orderParseValidMapstruct()), "MapStruct round-trips the order");
    require(order.equals(orderParseValidMapstructBeanValidation()), "Bean Validation passes it");
    require(validOrder.equals(orderBuildHandWritten()), "the hand-written build agrees");
    require(validOrder.equals(orderBuildMapstruct()), "the MapStruct build agrees");
    require(errorCount(parseBadOrderHkj()) == BAD_FIELDS, "five located order errors");
    require(violationCount(parseBadOrderMapstructBeanValidation()) == BAD_FIELDS, "five BV");
    require(parseBadOrderHandWritten() instanceof RuntimeException, "hand-written throws");
    require(parseBadOrderMapstruct() instanceof RuntimeException, "MapStruct throws");

    require(
        flatParseValidHkj().fold(errors -> false, flat::equals),
        "the generated mapper round-trips");
    require(flat.equals(flatParseValidHandWritten()), "the hand-written mapper round-trips");
    require(flat.equals(flatParseValidMapstruct()), "MapStruct round-trips the flat record");
    require(flat.equals(flatParseValidMapstructBeanValidation()), "Bean Validation passes it");
    require(validFlat.equals(flatBuildHandWritten()), "the hand-written build agrees");
    require(validFlat.equals(flatBuildMapstruct()), "the MapStruct build agrees");
    require(errorCount(parseBadFlatHkj()) == BAD_FIELDS, "five located flat errors");
    require(violationCount(parseBadFlatMapstructBeanValidation()) == BAD_FIELDS, "five BV");
    require(parseBadFlatHandWritten() instanceof RuntimeException, "hand-written throws");
    require(parseBadFlatMapstruct() instanceof RuntimeException, "MapStruct throws");
  }

  /** Calls {@code call} with {@code depth} more frames on the stack than the caller has. */
  private static <T> T atDepth(int depth, Supplier<T> call) {
    return depth == 0 ? call.get() : atDepth(depth - 1, call);
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
