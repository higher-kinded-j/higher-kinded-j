// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.Arrays;
import java.util.List;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Setter;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Edit / FallibleEdit / Edits - sparse, accumulating multi-edit over optics")
class EditsTest {

  record Order(String email, String sku, int quantity) {}

  private static final FocusPath<Order, String> EMAIL =
      FocusPath.of(Lens.of(Order::email, (o, e) -> new Order(e, o.sku(), o.quantity())));
  private static final FocusPath<Order, String> SKU =
      FocusPath.of(Lens.of(Order::sku, (o, s) -> new Order(o.email(), s, o.quantity())));
  private static final FocusPath<Order, Integer> QUANTITY =
      FocusPath.of(Lens.of(Order::quantity, (o, q) -> new Order(o.email(), o.sku(), q)));
  private static final Setter<Order, String> SKU_SETTER =
      Setter.fromGetSet(Order::sku, (o, s) -> new Order(o.email(), s, o.quantity()));

  private static final Order ORDER = new Order("Alice@Example.COM", " ab-123 ", 5);

  private static Validated<NonEmptyList<FieldError>, String> parseEmail(String raw) {
    return raw.contains("@")
        ? Validated.validNel(raw.toLowerCase())
        : Validated.invalidNel(FieldError.of("not an address"));
  }

  @Nested
  @DisplayName("Edits.combine - pure edits fold into one Update")
  class CombineTests {

    @Test
    @DisplayName("should apply every edit, left to right")
    void shouldApplyEveryEdit() {
      Update<Order> tidy =
          Edits.combine(Edit.modify(EMAIL, String::toLowerCase), Edit.modify(SKU, String::trim));

      assertThat(tidy.apply(ORDER)).isEqualTo(new Order("alice@example.com", "ab-123", 5));
    }

    @Test
    @DisplayName("should pin left-to-right order on an overlapping path")
    void shouldPinOrderOnOverlappingPath() {
      Update<Order> ab =
          Edits.combine(Edit.modify(SKU, s -> s + "a"), Edit.modify(SKU, s -> s + "b"));
      Update<Order> ba =
          Edits.combine(Edit.modify(SKU, s -> s + "b"), Edit.modify(SKU, s -> s + "a"));

      assertThat(ab.apply(new Order("e", "", 0)).sku()).isEqualTo("ab");
      assertThat(ba.apply(new Order("e", "", 0)).sku()).isEqualTo("ba");
    }

    @Test
    @DisplayName("should commute for disjoint paths")
    void shouldCommuteForDisjointPaths() {
      Edit<Order> setEmail = Edit.set(EMAIL, "new@example.com");
      Edit<Order> setQuantity = Edit.set(QUANTITY, 9);

      assertThat(Edits.combine(setEmail, setQuantity).apply(ORDER))
          .isEqualTo(Edits.combine(setQuantity, setEmail).apply(ORDER));
    }

    @Test
    @DisplayName("should be the identity with no edits")
    void shouldBeIdentityWithNoEdits() {
      assertThat(Edits.<Order>combine().apply(ORDER)).isSameAs(ORDER);
    }

    @Test
    @DisplayName("should accept a list of edits")
    void shouldAcceptList() {
      Update<Order> tidy = Edits.combine(List.of(Edit.modify(SKU, String::trim)));

      assertThat(tidy.apply(ORDER).sku()).isEqualTo("ab-123");
    }

    @Test
    @DisplayName("should reject null edits")
    void shouldRejectNulls() {
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.combine((Edit<Order>[]) null))
          .withMessage("edits must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.combine(Edit.set(QUANTITY, 1), null))
          .withMessage("edit must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.combine(Arrays.asList(Edit.set(QUANTITY, 1), null)))
          .withMessage("edit must not be null");
    }
  }

  @Nested
  @DisplayName("Edit factories - pure writes")
  class EditFactoryTests {

    @Test
    @DisplayName("set should write the value at a FocusPath and through a Setter")
    void setShouldWrite() {
      assertThat(Edit.set(QUANTITY, 7).toUpdate().apply(ORDER).quantity()).isEqualTo(7);
      assertThat(Edit.set(SKU_SETTER, "x").toUpdate().apply(ORDER).sku()).isEqualTo("x");
    }

    @Test
    @DisplayName("set should reject a null value, pointing at setIfPresent")
    void setShouldRejectNullValue() {
      assertThatNullPointerException()
          .isThrownBy(() -> Edit.set(EMAIL, null))
          .withMessageContaining("setIfPresent");
      assertThatNullPointerException()
          .isThrownBy(() -> Edit.set(SKU_SETTER, null))
          .withMessageContaining("setIfPresent");
    }

    @Test
    @DisplayName("modify should transform the current value at a FocusPath and through a Setter")
    void modifyShouldTransform() {
      assertThat(Edit.modify(EMAIL, String::toLowerCase).toUpdate().apply(ORDER).email())
          .isEqualTo("alice@example.com");
      assertThat(Edit.modify(SKU_SETTER, String::trim).toUpdate().apply(ORDER).sku())
          .isEqualTo("ab-123");
    }

    @Test
    @DisplayName("setIfPresent should write when present and do nothing when absent")
    void setIfPresentShouldSkipAbsent() {
      assertThat(Edit.setIfPresent(QUANTITY, 3).toUpdate().apply(ORDER).quantity()).isEqualTo(3);
      assertThat(Edit.setIfPresent(QUANTITY, null).toUpdate().apply(ORDER)).isSameAs(ORDER);
      assertThat(Edit.setIfPresent(SKU_SETTER, "y").toUpdate().apply(ORDER).sku()).isEqualTo("y");
      assertThat(Edit.setIfPresent(SKU_SETTER, null).toUpdate().apply(ORDER)).isSameAs(ORDER);
    }

    @Test
    @DisplayName("modifyIfPresent should combine (incoming, current) and skip when absent")
    void modifyIfPresentShouldCombineOrSkip() {
      assertThat(
              Edit.modifyIfPresent(QUANTITY, 10, Integer::sum).toUpdate().apply(ORDER).quantity())
          .isEqualTo(15);
      assertThat(
              Edit.modifyIfPresent(QUANTITY, (Integer) null, Integer::sum).toUpdate().apply(ORDER))
          .isSameAs(ORDER);
      assertThat(
              Edit.modifyIfPresent(SKU_SETTER, "!", (suffix, sku) -> sku.trim() + suffix)
                  .toUpdate()
                  .apply(ORDER)
                  .sku())
          .isEqualTo("ab-123!");
      assertThat(
              Edit.modifyIfPresent(SKU_SETTER, (String) null, (suffix, sku) -> sku + suffix)
                  .toUpdate()
                  .apply(ORDER))
          .isSameAs(ORDER);
    }

    @Test
    @DisplayName("modifyIfPresent should read the current value at application time")
    void modifyIfPresentShouldReadCurrentAtApplyTime() {
      Update<Order> pipeline =
          Edits.combine(
              Edit.modify(QUANTITY, q -> q + 1), Edit.modifyIfPresent(QUANTITY, 10, Integer::sum));

      assertThat(pipeline.apply(ORDER).quantity()).isEqualTo(16);
    }

    @Test
    @DisplayName("at on a pure edit should be a no-op returning the same edit")
    void atOnPureIsNoOp() {
      Edit<Order> edit = Edit.set(QUANTITY, 1);

      assertThat(edit.at("quantity")).isSameAs(edit);
      assertThatNullPointerException()
          .isThrownBy(() -> edit.at(null))
          .withMessage("label must not be null");
    }

    @Test
    @DisplayName("factories should eagerly reject null optics and functions")
    void factoriesShouldRejectNulls() {
      assertThatNullPointerException()
          .isThrownBy(() -> Edit.set((FocusPath<Order, String>) null, "v"))
          .withMessage("path must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edit.modify(EMAIL, null))
          .withMessage("fn must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edit.modifyIfPresent(QUANTITY, 1, null))
          .withMessage("fn must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> new Edit.Infallible<Order>(null))
          .withMessage("update must not be null");
    }
  }

  @Nested
  @DisplayName("Edit.parseIfPresent - fallible edits")
  class ParseIfPresentTests {

    @Test
    @DisplayName("should write the parsed value when the raw value parses")
    void shouldWriteParsedValue() {
      FallibleEdit<Order> edit =
          Edit.parseIfPresent(EMAIL, "New@Example.com", EditsTest::parseEmail);

      assertThatValidated(Edits.accumulate(edit).apply(ORDER))
          .isValid()
          .hasValue(new Order("new@example.com", " ab-123 ", 5));
    }

    @Test
    @DisplayName("should write through a Setter when the raw value parses")
    void shouldWriteThroughSetter() {
      FallibleEdit<Order> edit = Edit.parseIfPresent(SKU_SETTER, "OK@sku", EditsTest::parseEmail);

      assertThatValidated(Edits.accumulate(edit).apply(ORDER))
          .isValid()
          .hasValue(new Order("Alice@Example.COM", "ok@sku", 5));
    }

    @Test
    @DisplayName("should report the parse failure")
    void shouldReportParseFailure() {
      FallibleEdit<Order> edit = Edit.parseIfPresent(EMAIL, "nope", EditsTest::parseEmail);

      Validated<NonEmptyList<FieldError>, Order> result = Edits.accumulate(edit).apply(ORDER);

      assertThatValidated(result).isInvalid();
      assertThat(result.getError().toJavaList()).singleElement().hasToString("not an address");
    }

    @Test
    @DisplayName("should not invoke the parser when the raw value is absent")
    void shouldNotInvokeParserWhenAbsent() {
      FallibleEdit<Order> edit =
          Edit.parseIfPresent(
              EMAIL,
              (String) null,
              raw -> {
                throw new AssertionError("parser must not be invoked for an absent value");
              });

      assertThatValidated(Edits.accumulate(edit).apply(ORDER)).isValid().hasValue(ORDER);

      FallibleEdit<Order> viaSetter =
          Edit.parseIfPresent(
              SKU_SETTER,
              (String) null,
              raw -> {
                throw new AssertionError("parser must not be invoked for an absent value");
              });

      assertThatValidated(Edits.accumulate(viaSetter).apply(ORDER)).isValid().hasValue(ORDER);
    }

    @Test
    @DisplayName("at should locate failures, composing outward-in like FieldError.at")
    void atShouldLocateFailures() {
      FallibleEdit<Order> located =
          Edit.parseIfPresent(EMAIL, "nope", EditsTest::parseEmail).at("email").at("customer");

      Validated<NonEmptyList<FieldError>, Order> result = Edits.accumulate(located).apply(ORDER);

      assertThatValidated(result).isInvalid();
      assertThat(result.getError().toJavaList())
          .singleElement()
          .hasToString("customer.email: not an address");
    }

    @Test
    @DisplayName("at should relabel every error of a multi-error parse")
    void atShouldRelabelEveryError() {
      FallibleEdit<Order> edit =
          Edit.parseIfPresent(
                  EMAIL,
                  "raw",
                  raw ->
                      Validated.invalid(
                          NonEmptyList.of(FieldError.of("first"), FieldError.of("second"))))
              .at("email");

      Validated<NonEmptyList<FieldError>, Order> result = Edits.accumulate(edit).apply(ORDER);

      assertThatValidated(result).isInvalid();
      assertThat(result.getError().toJavaList())
          .extracting(FieldError::toString)
          .containsExactly("email: first", "email: second");
    }

    @Test
    @DisplayName("a labelled path auto-locates failures; explicit at prepends around them")
    void labelledPathAutoLocates() {
      FocusPath<Order, String> labelled =
          FocusPath.of(
              Lens.of(Order::email, (o, e) -> new Order(e, o.sku(), o.quantity())), "email");

      Validated<NonEmptyList<FieldError>, Order> auto =
          Edits.accumulate(Edit.parseIfPresent(labelled, "nope", EditsTest::parseEmail))
              .apply(ORDER);
      assertThatValidated(auto).isInvalid();
      assertThat(auto.getError().toJavaList()).singleElement().hasToString("email: not an address");

      FocusPath<Order, String> nested =
          FocusPath.of(Lens.<Order, Order>of(o -> o, (o, n) -> n), "customer").via(labelled);
      Validated<NonEmptyList<FieldError>, Order> composed =
          Edits.accumulate(Edit.parseIfPresent(nested, "nope", EditsTest::parseEmail)).apply(ORDER);
      assertThatValidated(composed).isInvalid();
      assertThat(composed.getError().toJavaList())
          .singleElement()
          .hasToString("customer.email: not an address");

      Validated<NonEmptyList<FieldError>, Order> wrapped =
          Edits.accumulate(
                  Edit.parseIfPresent(labelled, "nope", EditsTest::parseEmail).at("account"))
              .apply(ORDER);
      assertThatValidated(wrapped).isInvalid();
      assertThat(wrapped.getError().toJavaList())
          .singleElement()
          .hasToString("account.email: not an address");
    }

    @Test
    @DisplayName("should eagerly reject null arguments and a null-returning parser")
    void shouldRejectNulls() {
      assertThatNullPointerException()
          .isThrownBy(() -> Edit.parseIfPresent(EMAIL, "raw", null))
          .withMessage("parser must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edit.parseIfPresent(EMAIL, "raw", raw -> null))
          .withMessage("parser must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> new FallibleEdit.Parsed<Order>(null))
          .withMessage("validated must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edit.parseIfPresent(EMAIL, "nope", EditsTest::parseEmail).at(null))
          .withMessage("label must not be null");
    }
  }

  @Nested
  @DisplayName("Edits.accumulate - all errors at once, writes only if all valid")
  class AccumulateTests {

    @Test
    @DisplayName("should apply only the present fields when every edit validates")
    void shouldApplyPresentFields() {
      Validated<NonEmptyList<FieldError>, Order> result =
          Edits.accumulate(
                  Edit.setIfPresent(SKU, (String) null), // absent -> untouched
                  Edit.parseIfPresent(EMAIL, "New@Example.com", EditsTest::parseEmail).at("email"),
                  Edit.modifyIfPresent(QUANTITY, 10, Integer::sum))
              .apply(ORDER);

      assertThatValidated(result).isValid().hasValue(new Order("new@example.com", " ab-123 ", 15));
    }

    @Test
    @DisplayName("should report every failure at once, in edit order")
    void shouldReportEveryFailureInOrder() {
      Validated<NonEmptyList<FieldError>, Order> result =
          Edits.accumulate(
                  Edit.parseIfPresent(EMAIL, "bad-one", EditsTest::parseEmail).at("email"),
                  Edit.set(QUANTITY, 1),
                  Edit.parseIfPresent(SKU, "bad-two", EditsTest::parseEmail).at("sku"))
              .apply(ORDER);

      assertThatValidated(result).isInvalid();
      assertThat(result.getError().toJavaList())
          .extracting(FieldError::toString)
          .containsExactly("email: not an address", "sku: not an address");
    }

    @Test
    @DisplayName("should be valid and change nothing with no edits")
    void shouldBeValidWithNoEdits() {
      assertThatValidated(Edits.<Order>accumulate().apply(ORDER)).isValid().hasValue(ORDER);
    }

    @Test
    @DisplayName("should validate once and apply to many sources")
    void shouldApplyToManySources() {
      Edits.Accumulated<Order> patch =
          Edits.accumulate(Edit.parseIfPresent(EMAIL, "A@B.com", EditsTest::parseEmail));

      Order other = new Order("other@example.com", "sku", 1);

      assertThatValidated(patch.apply(ORDER))
          .isValid()
          .hasValue(new Order("a@b.com", " ab-123 ", 5));
      assertThatValidated(patch.apply(other)).isValid().hasValue(new Order("a@b.com", "sku", 1));
    }

    @Test
    @DisplayName("applyPath should mirror apply on the railway")
    void applyPathShouldMirrorApply() {
      Edits.Accumulated<Order> valid =
          Edits.accumulate(Edit.set(QUANTITY, 2), Edit.setIfPresent(SKU, "s"));
      Edits.Accumulated<Order> invalid =
          Edits.accumulate(Edit.parseIfPresent(EMAIL, "nope", EditsTest::parseEmail).at("email"));

      assertThat(valid.applyPath(ORDER).run()).isEqualTo(valid.apply(ORDER));
      assertThat(invalid.applyPath(ORDER).run()).isEqualTo(invalid.apply(ORDER));
    }

    @Test
    @DisplayName("toValidated should expose the folded update for reuse")
    void toValidatedShouldExposeFoldedUpdate() {
      Edits.Accumulated<Order> patch = Edits.accumulate(Edit.set(QUANTITY, 42));

      assertThatValidated(patch.toValidated())
          .isValid()
          .hasValueSatisfying(
              update -> update.apply(ORDER).quantity() == 42, "update sets quantity to 42");
    }

    @Test
    @DisplayName("should accept a list of edits")
    void shouldAcceptList() {
      List<FallibleEdit<Order>> edits =
          List.of(Edit.set(QUANTITY, 3), Edit.parseIfPresent(EMAIL, "a@b", EditsTest::parseEmail));

      assertThatValidated(Edits.accumulate(edits).apply(ORDER)).isValid();
    }

    @Test
    @DisplayName("should reject null edits and a null source")
    void shouldRejectNulls() {
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate((FallibleEdit<Order>[]) null))
          .withMessage("edits must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate(Edit.set(QUANTITY, 1), null))
          .withMessage("edit must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate(Arrays.asList(Edit.set(QUANTITY, 1), null)))
          .withMessage("edit must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate(Edit.set(QUANTITY, 1)).apply(null))
          .withMessage("source must not be null");
    }
  }

  @Nested
  @DisplayName("Edits.accumulate onto a focus - the edits land on the focus, set back once")
  class AccumulateOntoFocusTests {

    /** A record whose constructor checks its two fields against each other. */
    record Range(int lo, int hi) {
      Range {
        if (lo > hi) {
          throw new IllegalArgumentException("lo > hi");
        }
      }
    }

    /** The fields the edits set, with no check of their own. */
    record Ends(int lo, int hi) {}

    /** A record whose constructor refuses without a reason. */
    record Positive(int n) {
      Positive {
        if (n < 0) {
          throw new IllegalArgumentException();
        }
      }
    }

    /** A record whose constructor refuses with a blank reason. */
    record Even(int n) {
      Even {
        if (n % 2 != 0) {
          throw new IllegalArgumentException("  ");
        }
      }
    }

    private static final Lens<Range, Ends> ENDS =
        Lens.of(r -> new Ends(r.lo(), r.hi()), (r, e) -> new Range(e.lo(), e.hi()));
    private static final Setter<Ends, Integer> LO =
        Setter.fromGetSet(Ends::lo, (e, lo) -> new Ends(lo, e.hi()));
    private static final Setter<Ends, Integer> HI =
        Setter.fromGetSet(Ends::hi, (e, hi) -> new Ends(e.lo(), hi));
    private static final Range RANGE = new Range(1, 3);
    private static final Setter<Integer, Integer> WHOLE = Setter.fromGetSet(i -> i, (i, v) -> v);

    /** A class a source can subclass anonymously, so it has no simple name. */
    static class Counter {
      int count() {
        return 1;
      }
    }

    private static Validated<NonEmptyList<FieldError>, Integer> parseInt(String raw) {
      return raw.chars().allMatch(Character::isDigit)
          ? Validated.validNel(Integer.parseInt(raw))
          : Validated.invalidNel(FieldError.of("not a number"));
    }

    @Test
    @DisplayName("should construct once, so a valid result is reached through an invalid midpoint")
    void shouldConstructOnce() {
      Setter<Range, Integer> rangeLo =
          Setter.fromGetSet(Range::lo, (r, lo) -> new Range(lo, r.hi()));
      Setter<Range, Integer> rangeHi =
          Setter.fromGetSet(Range::hi, (r, hi) -> new Range(r.lo(), hi));

      // Field by field, lo = 5 is written first and builds Range(5, 3).
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  Edits.accumulate(Edit.setIfPresent(rangeLo, 5), Edit.setIfPresent(rangeHi, 10))
                      .apply(RANGE))
          .withMessage("lo > hi");

      assertThatValidated(
              Edits.accumulate(ENDS, Edit.setIfPresent(LO, 5), Edit.setIfPresent(HI, 10))
                  .apply(RANGE))
          .isValid()
          .hasValue(new Range(5, 10));
    }

    @Test
    @DisplayName("should report a refused result at the root, carrying the constructor's message")
    void shouldReportRefusalAtRoot() {
      assertThatValidated(Edits.accumulate(ENDS, Edit.setIfPresent(LO, 5)).apply(RANGE))
          .hasError(NonEmptyList.of(FieldError.of("lo > hi")));
    }

    @Test
    @DisplayName("should name the source's class when the refusal gives no reason")
    void shouldNameTheClassWithoutAReason() {
      Lens<Positive, Integer> n = Lens.of(Positive::n, (p, v) -> new Positive(v));
      Lens<Even, Integer> m = Lens.of(Even::n, (e, v) -> new Even(v));

      assertThatValidated(Edits.accumulate(n, Edit.set(WHOLE, -1)).apply(new Positive(1)))
          .hasFieldErrors("not a valid Positive");
      assertThatValidated(Edits.accumulate(m, Edit.set(WHOLE, 3)).apply(new Even(2)))
          .hasFieldErrors("not a valid Even");
    }

    @Test
    @DisplayName("should keep the source's value wherever an edit is absent")
    void shouldKeepAbsentFields() {
      assertThatValidated(
              Edits.accumulate(
                      ENDS, Edit.setIfPresent(LO, (Integer) null), Edit.setIfPresent(HI, 7))
                  .apply(RANGE))
          .isValid()
          .hasValue(new Range(1, 7));
    }

    @Test
    @DisplayName("should hand back the source itself, never setting, when every edit is absent")
    void shouldHandBackTheSourceWhenEveryEditIsAbsent() {
      Lens<Range, Ends> unsettable =
          Lens.of(
              ENDS::get,
              (r, e) -> {
                throw new IllegalStateException("set on a no-op");
              });
      Edits.Accumulated<Range> absent =
          Edits.accumulate(unsettable, Edit.setIfPresent(LO, (Integer) null));

      assertThat(absent.apply(RANGE).get()).isSameAs(RANGE);
      assertThat(Edits.accumulate(ENDS).apply(RANGE).get()).isSameAs(RANGE);
      assertThat(absent.toValidated().fold(errors -> Update.<Range>identity(), u -> u).apply(RANGE))
          .isSameAs(RANGE);
    }

    @Test
    @DisplayName("should name an anonymous source's class by its binary name")
    void shouldNameAnAnonymousClassByItsBinaryName() {
      Lens<Counter, Integer> count =
          Lens.of(
              Counter::count,
              (c, n) -> {
                throw new IllegalArgumentException();
              });
      Counter anonymous = new Counter() {};

      assertThatValidated(Edits.accumulate(count, Edit.set(WHOLE, 2)).apply(anonymous))
          .hasFieldErrors("not a valid " + anonymous.getClass().getName());
    }

    @Test
    @DisplayName("should catch only the set: a set handing back null still throws")
    void shouldNotCatchWhatTheSetHandsBack() {
      Lens<Range, Ends> nulling = Lens.of(ENDS::get, (r, e) -> null);

      assertThatThrownBy(() -> Edits.accumulate(nulling, Edit.set(LO, 2)).apply(RANGE))
          .isInstanceOf(RuntimeException.class)
          .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should report the edits' own errors alone, never constructing")
    void shouldNotConstructWhenAnEditFails() {
      assertThatValidated(
              Edits.accumulate(
                      ENDS,
                      Edit.parseIfPresent(LO, "x", AccumulateOntoFocusTests::parseInt).at("lo"),
                      Edit.setIfPresent(HI, 0))
                  .apply(RANGE))
          .hasFieldErrors("lo: not a number");
    }

    @Test
    @DisplayName("should catch only the set: an edit's own exception still propagates")
    void shouldCatchOnlyTheSet() {
      Edits.Accumulated<Range> failing =
          Edits.accumulate(
              ENDS,
              Edit.modify(
                  LO,
                  lo -> {
                    throw new IllegalStateException("edit bug");
                  }));
      Lens<Range, Ends> unreadable =
          Lens.of(
              r -> {
                throw new IllegalStateException("get bug");
              },
              (r, e) -> r);

      assertThatIllegalStateException()
          .isThrownBy(() -> failing.apply(RANGE))
          .withMessage("edit bug");
      assertThatIllegalStateException()
          .isThrownBy(() -> Edits.accumulate(unreadable, Edit.set(LO, 2)).apply(RANGE))
          .withMessage("get bug");
    }

    @Test
    @DisplayName("should validate once and apply to many sources, each judged on its own values")
    void shouldApplyToManySources() {
      Edits.Accumulated<Range> patch = Edits.accumulate(ENDS, Edit.setIfPresent(LO, 5));

      assertThatValidated(patch.apply(new Range(0, 9))).isValid().hasValue(new Range(5, 9));
      assertThatValidated(patch.apply(RANGE)).hasFieldErrors("lo > hi");
    }

    @Test
    @DisplayName("applyPath should mirror apply, a refusal included")
    void applyPathShouldMirrorApply() {
      Edits.Accumulated<Range> valid = Edits.accumulate(ENDS, Edit.setIfPresent(HI, 4));
      Edits.Accumulated<Range> refused = Edits.accumulate(ENDS, Edit.setIfPresent(LO, 5));

      assertThat(valid.applyPath(RANGE).run()).isEqualTo(valid.apply(RANGE));
      assertThat(refused.applyPath(RANGE).run()).isEqualTo(refused.apply(RANGE));
    }

    @Test
    @DisplayName("toValidated's update should construct once, and throw where apply reports")
    void toValidatedShouldConstructOnce() {
      Update<Range> widen =
          Edits.accumulate(ENDS, Edit.setIfPresent(LO, 5), Edit.setIfPresent(HI, 10))
              .toValidated()
              .fold(errors -> Update.identity(), update -> update);
      Update<Range> refused =
          Edits.accumulate(ENDS, Edit.setIfPresent(LO, 5))
              .toValidated()
              .fold(errors -> Update.identity(), update -> update);

      assertThat(widen.apply(RANGE)).isEqualTo(new Range(5, 10));
      assertThatIllegalArgumentException()
          .isThrownBy(() -> refused.apply(RANGE))
          .withMessage("lo > hi");
    }

    @Test
    @DisplayName("should accept a list of edits")
    void shouldAcceptList() {
      List<FallibleEdit<Ends>> edits = List.of(Edit.setIfPresent(LO, 5), Edit.setIfPresent(HI, 10));

      assertThatValidated(Edits.accumulate(ENDS, edits).apply(RANGE))
          .isValid()
          .hasValue(new Range(5, 10));
    }

    @Test
    @DisplayName("should reject a null focus, null edits and a null source")
    void shouldRejectNulls() {
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate((Lens<Range, Ends>) null, Edit.set(LO, 1)))
          .withMessage("focus must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate((Lens<Range, Ends>) null, List.of()))
          .withMessage("focus must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate(ENDS, (FallibleEdit<Ends>[]) null))
          .withMessage("edits must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate(ENDS, (List<FallibleEdit<Ends>>) null))
          .withMessage("edits must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate(ENDS, Edit.set(LO, 1), null))
          .withMessage("edit must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Edits.accumulate(ENDS, Edit.set(LO, 1)).apply(null))
          .withMessage("source must not be null");
    }
  }
}
