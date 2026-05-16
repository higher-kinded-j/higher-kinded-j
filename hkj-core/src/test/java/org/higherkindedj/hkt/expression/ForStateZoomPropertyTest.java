// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * Property-based tests for the optic-polymorphic {@code ForState.zoom} overloads added in issue
 * #506.
 *
 * <p>Two properties:
 *
 * <ol>
 *   <li><b>Iso round-trip preservation:</b> when the zoom block does not modify the inner state,
 *       the outer state is reconstructed unchanged via {@code iso.reverseGet(iso.get(s))}.
 *   <li><b>FocusPath / Lens parity:</b> {@code zoom(focusPath)} produces the same result as {@code
 *       zoom(focusPath.toLens())}, confirming that the {@code FocusPath} overload is a behavioural
 *       alias of the {@code Lens} overload.
 * </ol>
 */
@Label("ForState zoom optic-polymorphic properties")
class ForStateZoomPropertyTest {

  // Test fixtures: a small two-field state and a swap iso between two equivalent shapes.
  record Counter(int value, String label) {}

  record SwappedCounter(String label, int value) {}

  private static final Iso<Counter, SwappedCounter> swapIso =
      Iso.of(c -> new SwappedCounter(c.label(), c.value()), s -> new Counter(s.value(), s.label()));

  private static final Lens<Counter, Integer> valueLens =
      Lens.of(Counter::value, (c, v) -> new Counter(v, c.label()));

  private static final Lens<SwappedCounter, Integer> swappedValueLens =
      Lens.of(SwappedCounter::value, (s, v) -> new SwappedCounter(s.label(), v));

  private final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  @Provide
  Arbitrary<Counter> counters() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .flatMap(
            v ->
                Arbitraries.strings()
                    .alpha()
                    .ofMinLength(1)
                    .ofMaxLength(20)
                    .map(label -> new Counter(v, label)));
  }

  @Property
  @Label("Iso zoom round-trip: empty zoom block leaves the outer state unchanged")
  void isoZoomRoundTripPreservesState(@ForAll("counters") Counter initial) {
    Kind<IdKind.Witness, Counter> result =
        ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
            .zoom(swapIso)
            // No operations inside the zoom block; the iso must reconstruct the outer state.
            .endZoom()
            .yield();

    Counter finalCounter = IdKindHelper.ID.unwrap(result);
    assertThat(finalCounter).isEqualTo(initial);
    // Also verify the iso laws on the input directly, as a sanity gate.
    assertThat(swapIso.reverseGet(swapIso.get(initial))).isEqualTo(initial);
  }

  @Property
  @Label("Iso zoom updates: setting the inner value round-trips through the iso")
  void isoZoomUpdateRoundTripsCorrectly(
      @ForAll("counters") Counter initial,
      @ForAll @IntRange(min = -1000, max = 1000) int newValue) {
    Kind<IdKind.Witness, Counter> result =
        ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
            .zoom(swapIso)
            .update(swappedValueLens, newValue)
            .endZoom()
            .yield();

    Counter finalCounter = IdKindHelper.ID.unwrap(result);
    assertThat(finalCounter.value()).isEqualTo(newValue);
    assertThat(finalCounter.label()).isEqualTo(initial.label());
  }

  @Property
  @Label("FocusPath / Lens parity: zoom(focusPath) == zoom(focusPath.toLens())")
  void focusPathDelegatesToLens(
      @ForAll("counters") Counter initial,
      @ForAll @IntRange(min = -1000, max = 1000) int newValue) {
    FocusPath<Counter, Integer> valuePath = FocusPath.of(valueLens);

    Kind<IdKind.Witness, Counter> viaPath =
        ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
            .zoom(valuePath)
            // FocusPath<Integer, Integer> is the identity on the focused integer; we exercise
            // the inner update via a lens on the (sub-state == focused value) shape using a
            // helper Lens<Integer, Integer> that simply replaces.
            .update(Lens.of(Function.identity(), (s, v) -> v), newValue)
            .endZoom()
            .yield();

    Kind<IdKind.Witness, Counter> viaLens =
        ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
            .zoom(valuePath.toLens())
            .update(Lens.of(Function.identity(), (s, v) -> v), newValue)
            .endZoom()
            .yield();

    assertThat(IdKindHelper.ID.unwrap(viaPath)).isEqualTo(IdKindHelper.ID.unwrap(viaLens));
  }

  @Property
  @Label("Iso / asLens parity: zoom(iso) == zoom(iso.asLens())")
  void isoDelegatesToAsLens(
      @ForAll("counters") Counter initial,
      @ForAll @IntRange(min = -1000, max = 1000) int newValue,
      @ForAll @StringLength(min = 1, max = 10) String newLabel) {
    Kind<IdKind.Witness, Counter> viaIso =
        ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
            .zoom(swapIso)
            .update(swappedValueLens, newValue)
            .endZoom()
            .yield();

    Kind<IdKind.Witness, Counter> viaAsLens =
        ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
            .zoom(swapIso.asLens())
            .update(swappedValueLens, newValue)
            .endZoom()
            .yield();

    assertThat(IdKindHelper.ID.unwrap(viaIso)).isEqualTo(IdKindHelper.ID.unwrap(viaAsLens));
  }
}
