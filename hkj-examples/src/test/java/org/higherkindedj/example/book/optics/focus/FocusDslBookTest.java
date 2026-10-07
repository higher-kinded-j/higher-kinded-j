// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/** Holds the claims the Focus DSL page makes about its examples. */
@DisplayName("the Focus DSL page")
class FocusDslBookTest {

  private static final User ALICE = new User("Alice", new Address("Old Street", "London"));
  private static final Employee BOB = new Employee("Bob", 41, Optional.of("Bob@Example.com"));
  private static final Employee CAROL = new Employee("Carol", 35, Optional.empty());
  private static final Department ENGINEERING = new Department("Engineering", List.of(BOB, CAROL));
  private static final Company ACME = new Company("Acme", List.of(ENGINEERING));

  @Test
  @DisplayName("a path reads, sets and modifies a field two records down")
  void firstUse() {
    FocusDslBook.FirstUse result = FocusDslBook.firstUse(ALICE);

    assertThat(result.city()).isEqualTo("London");
    assertThat(result.moved().address().city()).isEqualTo("Paris");
    assertThat(result.shouty().address().city()).isEqualTo("LONDON");
  }

  @Test
  @DisplayName("the field type picks the path type, and each reads what the page says")
  void perComponent() {
    FocusDslBook.PerComponent result = FocusDslBook.perComponent(ACME, BOB);

    assertThat(result.companyName()).isEqualTo("Acme");
    assertThat(result.departments()).containsExactly(ENGINEERING);
    assertThat(result.email()).contains("Bob@Example.com");
  }

  @Test
  @DisplayName("a chained path reads and updates every employee's name")
  void chained() {
    FocusDslBook.Chained result = FocusDslBook.chained(ACME);

    assertThat(result.names()).containsExactly("Bob", "Carol");
    assertThat(FocusDslBook.chained(result.updated()).names()).containsExactly("BOB", "CAROL");
  }

  @Test
  @DisplayName("a FocusPath always reads, sets and modifies")
  void focusOps() {
    FocusDslBook.FocusOps result = FocusDslBook.focusOps(CAROL);

    assertThat(result.name()).isEqualTo("Carol");
    assertThat(result.updated().name()).isEqualTo("Bob");
    assertThat(result.modified().name()).isEqualTo("CAROL");
  }

  @Test
  @DisplayName("an AffinePath set writes even when the focus is absent; modify does not")
  void affineOpsOnAbsent() {
    FocusDslBook.AffineOps result = FocusDslBook.affineOps(CAROL);

    assertThat(result.email()).isEmpty();
    assertThat(result.updated().email()).contains("new@example.com");
    assertThat(result.modified()).isEqualTo(CAROL);
    assertThat(result.hasEmail()).isFalse();
  }

  @Test
  @DisplayName("an AffinePath on a present focus reads, sets and modifies it")
  void affineOpsOnPresent() {
    FocusDslBook.AffineOps result = FocusDslBook.affineOps(BOB);

    assertThat(result.email()).contains("Bob@Example.com");
    assertThat(result.modified().email()).contains("bob@example.com");
    assertThat(result.hasEmail()).isTrue();
  }

  @Test
  @DisplayName("a TraversalPath reads, sets, modifies and counts every element")
  void traversalOps() {
    FocusDslBook.TraversalOps result = FocusDslBook.traversalOps(ENGINEERING, BOB);

    assertThat(result.all()).containsExactly(BOB, CAROL);
    assertThat(result.updated().employees()).containsExactly(BOB, BOB);
    assertThat(result.modified().employees()).extracting(Employee::age).containsExactly(42, 36);
    assertThat(result.headcount()).isEqualTo(2);
  }

  @Nested
  @DisplayName("Find your field")
  class FindYourField {

    private final FocusDslBook.Fields fields = FocusDslBook.findYourField();

    private final Order order = order("SAVE10", Either.right("Dana"), new Payment.Card("4242"));

    private static Order order(
        @Nullable String couponCode, Either<String, String> approvedBy, Payment payment) {
      return new Order(
          new Customer("Alice", "alice@example.com"),
          "ORD-1",
          List.of(new LineItem("a", 2), new LineItem("b", 3)),
          Optional.of("Happy birthday"),
          couponCode,
          null,
          Map.of("channel", "web"),
          new String[] {"gift", "priority"},
          approvedBy,
          payment,
          JsonNodeFactory.instance.objectNode().put("source", "web"));
    }

    @Test
    @DisplayName("each generated method returns the path type its row names")
    void generatedTypes() {
      TraversalPath<Order, LineItem> lines = OrderFocus.lines();
      AffinePath<Order, String> couponCode = OrderFocus.couponCode();
      FocusPath<Order, String> legacyNote = OrderFocus.legacyNote();
      FocusPath<Order, Map<String, String>> attributes = OrderFocus.attributes();
      FocusPath<Order, String[]> tags = OrderFocus.tags();
      FocusPath<Order, Payment> payment = OrderFocus.payment();
      FocusPath<Order, JsonNode> payload = OrderFocus.payload();

      assertThat(lines.count(order)).isEqualTo(2);
      assertThat(couponCode.matches(order)).isTrue();
      assertThat(legacyNote.get(order)).isNull();
      assertThat(attributes.get(order)).containsEntry("channel", "web");
      assertThat(tags.get(order)).containsExactly("gift", "priority");
      assertThat(payment.get(order)).isEqualTo(new Payment.Card("4242"));
      assertThat(payload.get(order).isObject()).isTrue();
    }

    @Test
    @DisplayName("each row's path reads the field it names")
    void eachRowReads() {
      assertThat(fields.customerEmail().get(order)).isEqualTo("alice@example.com");
      assertThat(fields.reference().get(order)).isEqualTo("ORD-1");
      assertThat(fields.quantities().getAll(order)).containsExactly(2, 3);
      assertThat(fields.giftMessage().getOptional(order)).contains("Happy birthday");
      assertThat(fields.couponCode().getOptional(order)).contains("SAVE10");
      assertThat(fields.legacyNote().getOptional(order)).isEmpty();
      assertThat(fields.channel().getOptional(order)).contains("web");
      assertThat(fields.tags().getAll(order)).containsExactly("gift", "priority");
      assertThat(fields.approvedBy().getOptional(order)).contains("Dana");
      assertThat(fields.card().getOptional(order)).contains(new Payment.Card("4242"));
      assertThat(fields.sameCard().getOptional(order)).contains(new Payment.Card("4242"));
      assertThat(
              fields
                  .payloadObject()
                  .getOptional(order)
                  .map(node -> node.get("source").stringValue()))
          .contains("web");
    }

    @Test
    @DisplayName("a null coupon, a Left approval and an invoice each read as absent")
    void absentRows() {
      Order sparse = order(null, Either.left("awaiting review"), new Payment.Invoice("30 days"));

      assertThat(fields.couponCode().getOptional(sparse)).isEmpty();
      assertThat(fields.approvedBy().getOptional(sparse)).isEmpty();
      assertThat(fields.card().getOptional(sparse)).isEmpty();
      assertThat(fields.sameCard().getOptional(sparse)).isEmpty();
    }
  }
}
