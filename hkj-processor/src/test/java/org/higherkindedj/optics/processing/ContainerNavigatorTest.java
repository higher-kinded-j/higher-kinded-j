// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A field whose Focus method steps through containers onto a navigable record gets a navigator, and
 * navigating through it reads and writes exactly what composing the record's own Focus method with
 * {@code .via()} does on the same record generated without navigators.
 *
 * <p>The probe below is compiled alongside the records, so each navigation it writes, {@code
 * OrderFocus.lines().quantity()} among them, is checked by javac as well as run. Its baseline is
 * {@code PlainOrder}, the same components under {@code generateNavigators = false}, whose methods
 * owe nothing to the navigator generator.
 */
@DisplayName("Navigators over containers of records")
class ContainerNavigatorTest {

  private static final JavaFileObject LINE_ITEM =
      JavaFileObjects.forSourceString(
          "com.example.LineItem",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus
          public record LineItem(String sku, int quantity) {}
          """);

  private static final String COMPONENTS =
      """
          List<LineItem> lines,
          Set<LineItem> lineSet,
          Collection<LineItem> bag,
          Optional<LineItem> first,
          Maybe<LineItem> gift,
          List<@Nullable LineItem> sparse,
          List<Optional<LineItem>> pending,
          Either<String, List<LineItem>> held,
          Map<String, List<LineItem>> byKey,
          List<@Nullable List<LineItem>> rows\
      """;

  /** The record under test, and its twin generated without navigators. */
  private static JavaFileObject order(String name, String annotation) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;

        import java.util.Collection;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import java.util.Set;
        import org.higherkindedj.hkt.either.Either;
        import org.higherkindedj.hkt.maybe.Maybe;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.jspecify.annotations.Nullable;

        %s
        public record %s(
        %s) {}
        """
            .formatted(annotation, name, COMPONENTS));
  }

  /**
   * Reads every quantity through each container and doubles it, by navigating on {@code Order} and
   * by composing with {@code .via()} on {@code PlainOrder}, keyed by field: the read, then the
   * field as the write left it.
   */
  private static final JavaFileObject PROBE =
      JavaFileObjects.forSourceString(
          "com.example.Probe",
          """
          package com.example;

          import java.util.Arrays;
          import java.util.LinkedHashSet;
          import java.util.List;
          import java.util.Map;
          import java.util.Optional;
          import org.higherkindedj.hkt.either.Either;
          import org.higherkindedj.hkt.maybe.Maybe;
          import org.higherkindedj.optics.util.Affines;

          public final class Probe {
            private Probe() {}

            private static final LineItem A = new LineItem("a", 2);
            private static final LineItem B = new LineItem("b", 3);

            public static Order order() {
              return new Order(
                  List.of(A, B),
                  new LinkedHashSet<>(List.of(A, B)),
                  List.of(A, B),
                  Optional.of(A),
                  Maybe.just(B),
                  Arrays.asList(A, null, B),
                  List.of(Optional.of(A), Optional.empty()),
                  Either.right(List.of(A, B)),
                  Map.of("k", List.of(A, B)),
                  Arrays.asList(List.of(A), null, List.of(B)));
            }

            public static PlainOrder plainOrder() {
              return new PlainOrder(
                  List.of(A, B),
                  new LinkedHashSet<>(List.of(A, B)),
                  List.of(A, B),
                  Optional.of(A),
                  Maybe.just(B),
                  Arrays.asList(A, null, B),
                  List.of(Optional.of(A), Optional.empty()),
                  Either.right(List.of(A, B)),
                  Map.of("k", List.of(A, B)),
                  Arrays.asList(List.of(A), null, List.of(B)));
            }

            public static Map<String, List<Object>> navigated(Order o) {
              return Map.of(
                  "lines", List.of(
                      OrderFocus.lines().quantity().getAll(o),
                      OrderFocus.lines().quantity().modifyAll(q -> q * 2, o).lines()),
                  "lineSet", List.of(
                      OrderFocus.lineSet().quantity().getAll(o),
                      OrderFocus.lineSet().quantity().modifyAll(q -> q * 2, o).lineSet()),
                  "bag", List.of(
                      OrderFocus.bag().quantity().getAll(o),
                      OrderFocus.bag().quantity().modifyAll(q -> q * 2, o).bag()),
                  "first", List.of(
                      OrderFocus.first().quantity().getOptional(o),
                      OrderFocus.first().quantity().modify(q -> q * 2, o).first()),
                  "gift", List.of(
                      OrderFocus.gift().quantity().getOptional(o),
                      OrderFocus.gift().quantity().modify(q -> q * 2, o).gift()),
                  "sparse", List.of(
                      OrderFocus.sparse().quantity().getAll(o),
                      OrderFocus.sparse().quantity().modifyAll(q -> q * 2, o).sparse()),
                  "pending", List.of(
                      OrderFocus.pending().quantity().getAll(o),
                      OrderFocus.pending().quantity().modifyAll(q -> q * 2, o).pending()),
                  "held", List.of(
                      OrderFocus.held().quantity().getAll(o),
                      OrderFocus.held().quantity().modifyAll(q -> q * 2, o).held()),
                  "byKey", List.of(
                      OrderFocus.byKey().quantity().getAll(o),
                      OrderFocus.byKey().quantity().modifyAll(q -> q * 2, o).byKey()),
                  "rows", List.of(
                      OrderFocus.rows().quantity().getAll(o),
                      OrderFocus.rows().quantity().modifyAll(q -> q * 2, o).rows()));
            }

            public static Map<String, List<Object>> composed(PlainOrder o) {
              return Map.of(
                  "lines", List.of(
                      PlainOrderFocus.lines().via(LineItemFocus.quantity()).getAll(o),
                      PlainOrderFocus.lines()
                          .via(LineItemFocus.quantity())
                          .modifyAll(q -> q * 2, o)
                          .lines()),
                  "lineSet", List.of(
                      PlainOrderFocus.lineSet().via(LineItemFocus.quantity()).getAll(o),
                      PlainOrderFocus.lineSet()
                          .via(LineItemFocus.quantity())
                          .modifyAll(q -> q * 2, o)
                          .lineSet()),
                  "bag", List.of(
                      PlainOrderFocus.bag().via(LineItemFocus.quantity()).getAll(o),
                      PlainOrderFocus.bag()
                          .via(LineItemFocus.quantity())
                          .modifyAll(q -> q * 2, o)
                          .bag()),
                  "first", List.of(
                      PlainOrderFocus.first().via(LineItemFocus.quantity()).getOptional(o),
                      PlainOrderFocus.first()
                          .via(LineItemFocus.quantity())
                          .modify(q -> q * 2, o)
                          .first()),
                  "gift", List.of(
                      PlainOrderFocus.gift().via(LineItemFocus.quantity()).getOptional(o),
                      PlainOrderFocus.gift()
                          .via(LineItemFocus.quantity())
                          .modify(q -> q * 2, o)
                          .gift()),
                  "sparse", List.of(
                      PlainOrderFocus.sparse()
                          .some(Affines.nullable())
                          .via(LineItemFocus.quantity())
                          .getAll(o),
                      PlainOrderFocus.sparse()
                          .some(Affines.nullable())
                          .via(LineItemFocus.quantity())
                          .modifyAll(q -> q * 2, o)
                          .sparse()),
                  "pending", List.of(
                      PlainOrderFocus.pending().via(LineItemFocus.quantity()).getAll(o),
                      PlainOrderFocus.pending()
                          .via(LineItemFocus.quantity())
                          .modifyAll(q -> q * 2, o)
                          .pending()),
                  "held", List.of(
                      PlainOrderFocus.held().via(LineItemFocus.quantity()).getAll(o),
                      PlainOrderFocus.held()
                          .via(LineItemFocus.quantity())
                          .modifyAll(q -> q * 2, o)
                          .held()),
                  "byKey", List.of(
                      PlainOrderFocus.byKey().via(LineItemFocus.quantity()).getAll(o),
                      PlainOrderFocus.byKey()
                          .via(LineItemFocus.quantity())
                          .modifyAll(q -> q * 2, o)
                          .byKey()),
                  "rows", List.of(
                      PlainOrderFocus.rows().via(LineItemFocus.quantity()).getAll(o),
                      PlainOrderFocus.rows()
                          .via(LineItemFocus.quantity())
                          .modifyAll(q -> q * 2, o)
                          .rows()));
            }
          }
          """);

  private static final CompiledResult RESULT =
      RuntimeCompilationHelper.compileWith(
          new FocusProcessor(),
          LINE_ITEM,
          order("Order", "@GenerateFocus(widenFields = \"byKey\")"),
          order(
              "PlainOrder", "@GenerateFocus(generateNavigators = false, widenFields = \"byKey\")"),
          PROBE);

  private static Object probe(String method, Object... args) {
    try {
      return RESULT.invokeStatic("com.example.Probe", method, args);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("could not run com.example.Probe." + method, e);
    }
  }

  @SuppressWarnings("unchecked") // the probe returns a Map<String, List<Object>>
  private static Map<String, List<Object>> navigated(Object order) {
    return (Map<String, List<Object>>) probe("navigated", order);
  }

  @Test
  @DisplayName("navigating reads and writes what via() does on the record without navigators")
  void navigatingAgreesWithVia() {
    @SuppressWarnings("unchecked") // the probe returns a Map<String, List<Object>>
    Map<String, List<Object>> composed =
        (Map<String, List<Object>>) probe("composed", probe("plainOrder"));
    Map<String, List<Object>> navigated = navigated(probe("order"));

    assertThat(navigated).isEqualTo(composed);
    // Spot-checks, so that two routes agreeing on nothing at all would fail here.
    assertThat(navigated.get("lines").getFirst()).isEqualTo(List.of(2, 3));
    assertThat(navigated.get("first").getFirst()).isEqualTo(Optional.of(2));
    assertThat(navigated.get("gift").getFirst()).isEqualTo(Optional.of(3));
    assertThat(navigated.get("pending").getFirst()).isEqualTo(List.of(2));
    assertThat(navigated.get("held").getFirst()).isEqualTo(List.of(2, 3));
  }

  @Test
  @DisplayName("writing through a navigator rebuilds each container and keeps a null in place")
  void writingRebuildsEachContainer() {
    Map<String, List<Object>> navigated = navigated(probe("order"));

    assertThat(navigated.get("lines").getLast())
        .hasToString("[LineItem[sku=a, quantity=4], LineItem[sku=b, quantity=6]]");
    Object lineSet = navigated.get("lineSet").getLast();
    assertThat(lineSet).isInstanceOf(Set.class);
    assertThat(lineSet.toString())
        .contains("LineItem[sku=a, quantity=4]", "LineItem[sku=b, quantity=6]");
    assertThat(navigated.get("sparse").getLast())
        .hasToString("[LineItem[sku=a, quantity=4], null, LineItem[sku=b, quantity=6]]");
    assertThat(navigated.get("pending").getLast())
        .hasToString("[Optional[LineItem[sku=a, quantity=4]], Optional.empty]");
    assertThat(navigated.get("rows").getLast())
        .hasToString("[[LineItem[sku=a, quantity=4]], null, [LineItem[sku=b, quantity=6]]]");
    assertThat(navigated.get("byKey").getLast())
        .hasToString("{k=[LineItem[sku=a, quantity=4], LineItem[sku=b, quantity=6]]}");
  }

  @Test
  @DisplayName("writing through a navigator leaves the source record as it was")
  void writingLeavesTheSourceAsItWas() {
    Object order = probe("order");
    String before = order.toString();

    navigated(order);

    assertThat(order).hasToString(before);
  }
}
