// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ADA;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.BULB;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.LAMP;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.consignment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.Bank;
import org.higherkindedj.example.book.optics.cast.Card;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.CustomerProfileFocus;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.EmailAddressFocus;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.example.book.optics.cast.Payment;
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

  private static final CustomerProfile WITH_NICKNAME =
      new CustomerProfile("Ada", Optional.of("Countess"), Optional.empty());
  private static final CustomerProfile WITHOUT_NICKNAME =
      new CustomerProfile("Grace", Optional.empty(), Optional.empty());

  @Test
  @DisplayName("a path reads, sets and modifies a field two records down")
  void firstUse() {
    Order padded =
        new Order(
            ORDER.id(),
            new Customer("  Ada ", ADA.email()),
            ORDER.lines(),
            ORDER.placedAt(),
            ORDER.currency(),
            ORDER.status());

    FocusDslBook.FirstUse result = FocusDslBook.firstUse(padded);

    assertThat(result.name()).isEqualTo("  Ada ");
    assertThat(result.renamed().customer().name()).isEqualTo("Ada Lovelace");
    assertThat(result.tidied().customer()).isEqualTo(ADA);
    assertThat(result.renamed().lines()).isSameAs(ORDER.lines());
  }

  @Test
  @DisplayName("the field type picks the path type, and each reads what the page says")
  void perComponent() {
    FocusDslBook.PerComponent result = FocusDslBook.perComponent(ORDER, WITH_NICKNAME);

    assertThat(result.status()).isEqualTo(OrderStatus.NEW);
    assertThat(result.lines()).containsExactly(LAMP, BULB);
    assertThat(result.nickname()).contains("Countess");
  }

  @Test
  @DisplayName("a chained path reads and updates every line's SKU")
  void chained() {
    FocusDslBook.Chained result = FocusDslBook.chained(ORDER);

    assertThat(result.skus()).containsExactly("LAMP", "BULB");
    assertThat(result.lowered().lines()).extracting(LineItem::sku).containsExactly("lamp", "bulb");
  }

  @Test
  @DisplayName("a FocusPath always reads, sets and modifies")
  void focusOps() {
    FocusDslBook.FocusOps result = FocusDslBook.focusOps(ADA);

    assertThat(result.name()).isEqualTo("Ada");
    assertThat(result.updated().name()).isEqualTo("Grace");
    assertThat(result.modified().name()).isEqualTo("ADA");
  }

  @Test
  @DisplayName("an AffinePath set writes even when the focus is absent; modify does not")
  void affineOpsOnAbsent() {
    FocusDslBook.AffineOps result = FocusDslBook.affineOps(WITHOUT_NICKNAME);

    assertThat(result.nickname()).isEmpty();
    assertThat(result.updated().nickname()).contains("Countess");
    assertThat(result.modified()).isEqualTo(WITHOUT_NICKNAME);
    assertThat(result.hasNickname()).isFalse();
  }

  @Test
  @DisplayName("an AffinePath on a present focus reads, sets and modifies it")
  void affineOpsOnPresent() {
    FocusDslBook.AffineOps result = FocusDslBook.affineOps(WITH_NICKNAME);

    assertThat(result.nickname()).contains("Countess");
    assertThat(result.modified().nickname()).contains("COUNTESS");
    assertThat(result.hasNickname()).isTrue();
  }

  @Test
  @DisplayName("a TraversalPath reads, sets, modifies and counts every element")
  void traversalOps() {
    FocusDslBook.TraversalOps result = FocusDslBook.traversalOps(ORDER, BULB);

    assertThat(result.all()).containsExactly(LAMP, BULB);
    assertThat(result.updated().lines()).containsExactly(BULB, BULB);
    assertThat(result.modified().lines()).extracting(LineItem::quantity).containsExactly(2, 5);
    assertThat(result.lineCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("checkpoint: a miss at an earlier step skips the whole set")
  void checkpointEarlierMissSkipsTheSet() {
    AffinePath<CustomerProfile, String> altEmailValue =
        CustomerProfileFocus.altEmail().via(EmailAddressFocus.value());

    assertThat(altEmailValue.set("ada@work.example", WITHOUT_NICKNAME)).isEqualTo(WITHOUT_NICKNAME);
  }

  @Test
  @DisplayName(
      "checkpoint: a record field is a navigator, a sealed field a FocusPath, an Optional an AffinePath")
  void checkpointPathTypes() {
    Consignment pending = consignment(new ConsignmentState.Pending());

    FocusPath<Consignment, Address> to = ConsignmentFocus.to().toPath();
    FocusPath<Consignment, String> city = ConsignmentFocus.to().city();
    FocusPath<Consignment, ConsignmentState> state = ConsignmentFocus.state();
    AffinePath<CustomerProfile, EmailAddress> altEmail = CustomerProfileFocus.altEmail();

    assertThat(to.get(pending)).isEqualTo(pending.to());
    assertThat(city.get(pending)).isEqualTo("London");
    assertThat(state.get(pending)).isEqualTo(new ConsignmentState.Pending());
    assertThat(altEmail.getOptional(WITH_NICKNAME)).isEmpty();
  }

  @Nested
  @DisplayName("Find your field")
  class FindYourField {

    private final FocusDslBook.Fields fields = FocusDslBook.findYourField();

    private final Basket basket = basket("SAVE10", Either.right("Dana"), new Card("4242"));

    private static Basket basket(
        @Nullable String couponCode, Either<String, String> approvedBy, Payment payment) {
      return new Basket(
          ADA,
          "CHK-1",
          List.of(LAMP, BULB),
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
      TraversalPath<Basket, LineItem> lines = BasketFocus.lines();
      AffinePath<Basket, String> couponCode = BasketFocus.couponCode();
      FocusPath<Basket, String> legacyNote = BasketFocus.legacyNote();
      FocusPath<Basket, Map<String, String>> attributes = BasketFocus.attributes();
      FocusPath<Basket, String[]> tags = BasketFocus.tags();
      FocusPath<Basket, Payment> payment = BasketFocus.payment();
      FocusPath<Basket, JsonNode> payload = BasketFocus.payload();
      FocusPath<Basket, Customer> customer = BasketFocus.customer().toPath();

      assertThat(lines.count(basket)).isEqualTo(2);
      assertThat(couponCode.matches(basket)).isTrue();
      assertThat(legacyNote.get(basket)).isNull();
      assertThat(attributes.get(basket)).containsEntry("channel", "web");
      assertThat(tags.get(basket)).containsExactly("gift", "priority");
      assertThat(payment.get(basket)).isEqualTo(new Card("4242"));
      assertThat(payload.get(basket).isObject()).isTrue();
      assertThat(customer.get(basket)).isEqualTo(ADA);
    }

    @Test
    @DisplayName("each row's path reads the field it names")
    void eachRowReads() {
      assertThat(fields.customerEmail().get(basket)).isEqualTo("ada@example.com");
      assertThat(fields.reference().get(basket)).isEqualTo("CHK-1");
      assertThat(fields.quantities().getAll(basket)).containsExactly(1, 4);
      assertThat(fields.giftMessage().getOptional(basket)).contains("Happy birthday");
      assertThat(fields.couponCode().getOptional(basket)).contains("SAVE10");
      assertThat(fields.legacyNote().getOptional(basket)).isEmpty();
      assertThat(fields.channel().getOptional(basket)).contains("web");
      assertThat(fields.tags().getAll(basket)).containsExactly("gift", "priority");
      assertThat(fields.approvedBy().getOptional(basket)).contains("Dana");
      assertThat(fields.card().getOptional(basket)).contains(new Card("4242"));
      assertThat(fields.sameCard().getOptional(basket)).contains(new Card("4242"));
      assertThat(fields.payloadObject().getOptional(basket).map(n -> n.get("source").stringValue()))
          .contains("web");
    }

    @Test
    @DisplayName("a null coupon, a Left approval and a bank payment each read as absent")
    void absentRows() {
      Basket sparse = basket(null, Either.left("awaiting review"), new Bank("GB00TEST"));

      assertThat(fields.couponCode().getOptional(sparse)).isEmpty();
      assertThat(fields.approvedBy().getOptional(sparse)).isEmpty();
      assertThat(fields.card().getOptional(sparse)).isEmpty();
      assertThat(fields.sameCard().getOptional(sparse)).isEmpty();
    }
  }
}
