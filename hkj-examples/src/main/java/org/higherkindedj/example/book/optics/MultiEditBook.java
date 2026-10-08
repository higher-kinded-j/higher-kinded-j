// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import static org.higherkindedj.optics.edit.Edit.modify;
import static org.higherkindedj.optics.edit.Edit.modifyIfPresent;
import static org.higherkindedj.optics.edit.Edit.parseIfPresent;
import static org.higherkindedj.optics.edit.Edit.setIfPresent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.edit.Edit;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.focus.FocusPath;
import org.jspecify.annotations.Nullable;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/multi_edit.html">Many Edits at Once</a>
 * page. The page {@code {{#include}}}s the anchored regions, so it cannot drift from the API.
 *
 * <p>The PATCH edits one line of an order: the chapter cast's {@code LineItem}, declared here with
 * the withers the "before" half calls, as Lombok's {@code @With} would generate them. The paths
 * come from the generated {@code LineItemFocus} companion, not hand-rolled optics: their labels are
 * what let a failed parse locate itself, which is precisely what the page claims.
 *
 * <p>The {@code accumulate} region here is the hand-written REST PATCH; when the DTO maps
 * one-to-one to a domain record, {@code @GenerateMapping} on an {@code UpdateSpec} generates the
 * same fold. See {@code SparsePatchBook}'s {@code update_spec} and {@code update_usage} regions.
 */
public final class MultiEditBook {

  // What the page calls SKU, QUANTITY and PRICE: generated, and therefore labelled.
  static final FocusPath<LineItem, String> SKU = LineItemFocus.sku();
  static final FocusPath<LineItem, Integer> QUANTITY = LineItemFocus.quantity();
  static final FocusPath<LineItem, BigDecimal> PRICE = LineItemFocus.price();

  private MultiEditBook() {}

  public static void main(String[] args) {
    LineItem line = new LineItem(" lamp ", 1, new BigDecimal("40.00"));
    LineItemPatch patch = new LineItemPatch(" LAMP-2 ", 3, "45.00");

    // ANCHOR: before
    LineItem updated = line;
    if (patch.sku() != null) {
      updated = updated.withSku(patch.sku().strip()); // thread the result...
    }
    if (patch.qtyDelta() != null) {
      updated =
          updated.withQuantity(updated.quantity() + patch.qtyDelta()); // ...through every step
    }
    if (patch.price() != null) {
      updated = updated.withPrice(new BigDecimal(patch.price())); // throws on a malformed price
    }
    // The SKU goes unchecked, and a malformed price throws, so the first bad field hides the rest.
    // ANCHOR_END: before
    System.out.println(updated);

    // ANCHOR: combine
    Update<LineItem> tidy =
        Edits.combine(
            modify(SKU, sku -> sku.strip().toUpperCase()),
            modify(PRICE, price -> price.setScale(2, RoundingMode.HALF_EVEN)));

    Update<LineItem> doubled = Edits.combine(modify(QUANTITY, quantity -> quantity * 2));

    LineItem lamp = tidy.apply(new LineItem(" lamp ", 1, new BigDecimal("40")));
    // LineItem[sku=LAMP, quantity=1, price=40.00]

    // an Update composes further: the bulbs are tidied, then their quantity doubled
    LineItem bulbs = tidy.andThen(doubled).apply(new LineItem("bulb", 4, new BigDecimal("2.5")));
    // LineItem[sku=BULB, quantity=8, price=2.50]
    // ANCHOR_END: combine
    System.out.println(lamp);
    System.out.println(bulbs);

    LineItem lampLine = new LineItem("LAMP", 1, new BigDecimal("40.00"));
    LineItemPatch sparsePatch = new LineItemPatch(null, 3, null);

    // ANCHOR: sparse
    Edit<LineItem> sku = setIfPresent(SKU, sparsePatch.sku()); // null -> no-op
    Edit<LineItem> quantity =
        modifyIfPresent(QUANTITY, sparsePatch.qtyDelta(), (delta, qty) -> qty + delta);

    LineItem restocked = Edits.combine(sku, quantity).apply(lampLine);
    // LineItem[sku=LAMP, quantity=4, price=40.00]
    // ANCHOR_END: sparse
    System.out.println(restocked);

    LineItemPatch badPatch = new LineItemPatch("lamp 2", 3, "forty");

    // ANCHOR: accumulate
    Validated<NonEmptyList<FieldError>, LineItem> patched =
        Edits.accumulate(
                parseIfPresent(SKU, badPatch.sku(), Sku::parse),
                modifyIfPresent(QUANTITY, badPatch.qtyDelta(), (delta, qty) -> qty + delta),
                parseIfPresent(PRICE, badPatch.price(), Price::parse))
            .apply(line);
    // Invalid(NonEmptyList[sku: not a SKU, price: not a price])
    //   <- or Valid(line) with only the present fields changed
    // ANCHOR_END: accumulate
    System.out.println(patched);

    BandRequest move = new BandRequest(500, 1000);

    // ANCHOR: focus
    Lens<PriceBand, Bounds> bounds =
        Lens.of(
            band -> new Bounds(band.floor(), band.ceiling()),
            (_, b) -> new PriceBand(b.floor(), b.ceiling()));

    Validated<NonEmptyList<FieldError>, PriceBand> moved =
        Edits.accumulate(
                bounds,
                setIfPresent(BoundsFocus.floor(), move.floor()),
                setIfPresent(BoundsFocus.ceiling(), move.ceiling()))
            .apply(new PriceBand(100, 300));
    // Valid(PriceBand[floor=500, ceiling=1000])
    //   <- both ends move together. Moving the floor alone would make PriceBand(500, 300), which
    //      the record's own constructor refuses, and the refusal would arrive as an error.
    // ANCHOR_END: focus
    System.out.println(moved);
  }
}

// The chapter cast's LineItem, with the withers the "before" half calls, as Lombok's @With
// would generate them.
@GenerateFocus
record LineItem(String sku, Integer quantity, BigDecimal price) {
  LineItem withSku(String sku) {
    return new LineItem(sku, quantity, price);
  }

  LineItem withQuantity(Integer quantity) {
    return new LineItem(sku, quantity, price);
  }

  LineItem withPrice(BigDecimal price) {
    return new LineItem(sku, quantity, price);
  }
}

/** A sparse PATCH of one order line: a null component means "not supplied", not "set to null". */
record LineItemPatch(@Nullable String sku, @Nullable Integer qtyDelta, @Nullable String price) {}

// ANCHOR: focus_records
record PriceBand(int floor, int ceiling) {
  PriceBand {
    if (floor > ceiling) {
      throw new IllegalArgumentException("floor above ceiling");
    }
  }
}

@GenerateFocus
record Bounds(int floor, int ceiling) {} // the fields the edits set, with no check of their own

// ANCHOR_END: focus_records

/** A sparse request moving a price band's ends: a null component means "not supplied". */
record BandRequest(@Nullable Integer floor, @Nullable Integer ceiling) {}

// ANCHOR: parsers
/** The boundary parsers the page hands to {@code parseIfPresent}. */
final class Sku {
  static Validated<NonEmptyList<FieldError>, String> parse(String raw) {
    String sku = raw.strip();
    return sku.matches("[A-Z0-9-]+")
        ? Validated.validNel(sku)
        : Validated.invalidNel(FieldError.of("not a SKU"));
  }

  private Sku() {}
}

final class Price {
  static Validated<NonEmptyList<FieldError>, BigDecimal> parse(String raw) {
    try {
      BigDecimal price = new BigDecimal(raw.strip());
      return price.signum() >= 0
          ? Validated.validNel(price)
          : Validated.invalidNel(FieldError.of("not a price"));
    } catch (NumberFormatException e) {
      return Validated.invalidNel(FieldError.of("not a price"));
    }
  }

  private Price() {}
}
// ANCHOR_END: parsers
