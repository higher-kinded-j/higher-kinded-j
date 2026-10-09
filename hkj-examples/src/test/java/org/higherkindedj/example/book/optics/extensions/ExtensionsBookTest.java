// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllEither;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Optics Extensions page's lens and traversal examples show. */
@DisplayName("the Optics Extensions page: what each extension returns")
class ExtensionsBookTest {

  @Test
  @DisplayName("the page's affiliate is Alice, and its items are two order lines")
  void inputsThePageDescribes() {
    assertThat(ExtensionsBook.AFFILIATE)
        .isEqualTo(new Affiliate("a1", "Alice", "alice@example.com", 10, "Lighting blogger"));
    assertThat(ExtensionsBook.ITEMS)
        .containsExactly(
            new LineItem("SKU-1", 1, new BigDecimal("999.99")),
            new LineItem("SKU-2", 2, new BigDecimal("29.99")));
  }

  @Test
  @DisplayName("getMaybe gives Just for a set field and Nothing for a null one")
  void getMaybe() {
    assertThat(ExtensionsBook.getMaybeExample())
        .containsExactly(Maybe.just("Lighting blogger"), Maybe.nothing(), "Lighting blogger");
    assertThat(ExtensionsBook.getMaybeExample().get(0)).hasToString("Just(Lighting blogger)");
    assertThat(ExtensionsBook.getMaybeExample().get(1)).hasToString("Nothing");
  }

  @Test
  @DisplayName("getEither gives Right for a set commission and Left with the message for null")
  void getEither() {
    List<Object> results = ExtensionsBook.getEitherExample(ExtensionsBook.AFFILIATE);

    assertThat(results.get(0)).isEqualTo(Either.right(10)).hasToString("Right(10)");
    assertThat(results.get(1))
        .isEqualTo(Either.left("Commission not agreed"))
        .hasToString("Left(Commission not agreed)");
    assertThat(results.get(2)).isEqualTo("Commission: 10%");
  }

  @Test
  @DisplayName("getValidated gives Valid for a set email and Invalid with the message for null")
  void getValidated() {
    List<Validated<String, String>> results =
        ExtensionsBook.getValidatedExample(ExtensionsBook.AFFILIATE);

    assertThat(results.get(0)).hasToString("Valid(alice@example.com)");
    assertThat(results.get(1)).hasToString("Invalid(Email is required)");
  }

  @Test
  @DisplayName("modifyMaybe uppercases a long enough name, and gives Nothing for a short one")
  void modifyMaybeUppercasesTheName() {
    Maybe<Affiliate> updated = ExtensionsBook.modifyMaybeExample(ExtensionsBook.AFFILIATE);

    assertThat(updated)
        .isEqualTo(
            Maybe.just(new Affiliate("a1", "ALICE", "alice@example.com", 10, "Lighting blogger")));
    assertThat(updated.toString()).startsWith("Just(Affiliate[id=a1, name=ALICE, ");

    Affiliate shortName = new Affiliate("a5", "A", "a@example.com", 5, null);
    assertThat(ExtensionsBook.modifyMaybeExample(shortName)).isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName("getAllMaybe gives every price, or Nothing for no lines")
  void getAllMaybe() {
    assertThat(ExtensionsBook.getAllMaybeExample(ExtensionsBook.ITEMS))
        .containsExactly(
            Maybe.just(List.of(new BigDecimal("999.99"), new BigDecimal("29.99"))),
            Maybe.nothing());
    assertThat(ExtensionsBook.getAllMaybeExample(ExtensionsBook.ITEMS).get(0))
        .hasToString("Just([999.99, 29.99])");
  }

  @Test
  @DisplayName("modifyAllMaybe raises every price by 10%, or gives Nothing when one is under 10")
  void modifyAllMaybe() {
    List<Maybe<List<LineItem>>> results =
        ExtensionsBook.modifyAllMaybeExample(ExtensionsBook.ALL_PRICES, ExtensionsBook.ITEMS);

    assertThat(results.get(0))
        .as("BigDecimal equality is scale-sensitive: each price is rounded back to two places")
        .isEqualTo(
            Maybe.just(
                List.of(
                    new LineItem("SKU-1", 1, new BigDecimal("1099.99")),
                    new LineItem("SKU-2", 2, new BigDecimal("32.99")))))
        .hasToString(
            "Just([LineItem[sku=SKU-1, quantity=1, price=1099.99],"
                + " LineItem[sku=SKU-2, quantity=2, price=32.99]])");
    assertThat(results.get(1)).isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName("modifyAllEither keeps the first error")
  void modifyAllEitherKeepsTheFirstError() {
    assertThat(ExtensionsBook.modifyAllEitherExample(ExtensionsBook.ALL_PRICES))
        .isEqualTo(Either.left("Price cannot be negative: -5.00"))
        .hasToString("Left(Price cannot be negative: -5.00)");
  }

  @Test
  @DisplayName("modifyAllEither still checks every price, past the first failure")
  void modifyAllEitherChecksEveryPrice() {
    AtomicInteger checked = new AtomicInteger();
    List<LineItem> withRefunds =
        List.of(
            new LineItem("SKU-1", 1, new BigDecimal("999.99")),
            new LineItem("SKU-4", 1, new BigDecimal("-5.00")),
            new LineItem("SKU-5", 1, new BigDecimal("-1.50")));

    Either<String, List<LineItem>> result =
        modifyAllEither(
            ExtensionsBook.ALL_PRICES,
            price -> {
              checked.incrementAndGet();
              return price.signum() < 0 ? Either.left("negative: " + price) : Either.right(price);
            },
            withRefunds);

    assertThat(result).isEqualTo(Either.left("negative: -5.00"));
    assertThat(checked).hasValue(3);
  }
}
