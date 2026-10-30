// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.alternative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.alternative.AlternativeBook.catalogueFor;
import static org.higherkindedj.example.book.foundations.alternative.AlternativeBook.offerFor;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.instances.Witnesses.stream;
import static org.higherkindedj.hkt.instances.Witnesses.vstream;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamAlternative;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Alternative page makes about what each instance's orElse does. */
@DisplayName("Alternative: one orElse, read by each instance its own way")
class AlternativeBookTest {

  private static final Alternative<MaybeKind.Witness> MAYBE_ALT = Instances.alternative(maybe());
  private static final Alternative<ListKind.Witness> LIST_ALT = Instances.alternative(list());

  @Test
  @DisplayName(
      "Maybe's orElse falls back only when the first is Nothing, and builds no fallback otherwise")
  void maybeFallsBackLazily() {
    List<String> built = new ArrayList<>();

    Maybe<BigDecimal> bulb =
        MAYBE.narrow(
            MAYBE_ALT.orElse(
                offerFor("BULB"),
                () -> {
                  built.add("catalogue");
                  return catalogueFor("BULB");
                }));

    assertThat(bulb).isEqualTo(Maybe.just(new BigDecimal("2.00")));
    assertThat(built).isEmpty();
    assertThat(MAYBE.narrow(MAYBE_ALT.orElse(offerFor("LAMP"), () -> catalogueFor("LAMP"))))
        .isEqualTo(Maybe.just(new BigDecimal("40.00")));
  }

  @Test
  @DisplayName("Optional's orElse falls back as Maybe's does")
  void optionalFallsBack() {
    Alternative<OptionalKind.Witness> optionalAlt = Instances.alternative(optional());

    assertThat(
            OPTIONAL.narrow(
                optionalAlt.orElse(
                    OPTIONAL.widen(Optional.empty()), () -> OPTIONAL.widen(Optional.of("LAMP")))))
        .contains("LAMP");
  }

  @Test
  @DisplayName("orElseAll stops at the first present value, and builds no later one")
  @SuppressWarnings(
      "unchecked") // varargs orElseAll: Java allows no @SafeVarargs on a default method
  void orElseAllStopsAtTheFirst() {
    List<String> built = new ArrayList<>();

    Maybe<BigDecimal> price =
        MAYBE.narrow(
            MAYBE_ALT.orElseAll(
                offerFor("LAMP"),
                () -> catalogueFor("LAMP"),
                () -> {
                  built.add("default");
                  return MAYBE.just(new BigDecimal("60.00"));
                }));

    assertThat(price).isEqualTo(Maybe.just(new BigDecimal("40.00")));
    assertThat(built).isEmpty();
  }

  @Test
  @DisplayName("orElseAll over an Iterable gives the first present value")
  void orElseAllOverAnIterable() {
    List<Kind<MaybeKind.Witness, BigDecimal>> sources =
        List.of(offerFor("LAMP"), catalogueFor("LAMP"), MAYBE.just(new BigDecimal("60.00")));

    assertThat(MAYBE.narrow(MAYBE_ALT.orElseAll(sources)))
        .isEqualTo(Maybe.just(new BigDecimal("40.00")));
  }

  @Test
  @DisplayName("Stream's orElseAll walks an Iterable only as far as the result is read")
  void streamOrElseAllWalksLazily() {
    Iterable<Kind<StreamKind.Witness, String>> endless =
        () -> Stream.generate(() -> STREAM.widen(Stream.of("LAMP"))).iterator();

    Stream<String> skus = STREAM.narrow(Instances.alternative(stream()).orElseAll(endless));

    assertThat(skus.limit(3).toList()).containsExactly("LAMP", "LAMP", "LAMP");
  }

  @Test
  @DisplayName("List's orElse joins both lists, so it always builds the second")
  void listJoinsBoth() {
    List<String> built = new ArrayList<>();

    List<String> skus =
        LIST.narrow(
            LIST_ALT.orElse(
                LIST.widen(List.of("LAMP")),
                () -> {
                  built.add("second");
                  return LIST.widen(List.of("DESK"));
                }));

    assertThat(skus).containsExactly("LAMP", "DESK");
    assertThat(built).containsExactly("second");
  }

  @Test
  @DisplayName("each instance's empty value holds nothing")
  void emptyValues() {
    assertThat(MAYBE.narrow(MAYBE_ALT.<String>empty())).isEqualTo(Maybe.nothing());
    assertThat(LIST.narrow(LIST_ALT.<String>empty())).isEmpty();
    assertThat(OPTIONAL.narrow(Instances.alternative(optional()).<String>empty())).isEmpty();
    assertThat(STREAM.narrow(Instances.alternative(stream()).<String>empty()).toList()).isEmpty();
    assertThat(VSTREAM.narrow(VStreamAlternative.INSTANCE.<String>empty()).toList().run())
        .isEmpty();
  }

  @Test
  @DisplayName("VStream's orElse joins both streams, and builds the second only when it runs")
  void vstreamJoinsLazily() {
    List<String> built = new ArrayList<>();

    Kind<VStreamKind.Witness, String> joined =
        VStreamAlternative.INSTANCE.orElse(
            VSTREAM.widen(VStream.of("LAMP")),
            () -> {
              built.add("second");
              return VSTREAM.widen(VStream.of("DESK"));
            });

    assertThat(built).isEmpty();
    assertThat(VSTREAM.narrow(joined).toList().run()).containsExactly("LAMP", "DESK");
    assertThat(built).containsExactly("second");
  }

  @Test
  @DisplayName("Instances.alternative compiles for VStream's token, and fails when it runs")
  void vstreamTokenHasNoAlternative() {
    assertThatThrownBy(() -> Instances.alternative(vstream()))
        .isInstanceOf(ClassCastException.class);
  }

  @Test
  @DisplayName("Stream's orElse joins both streams")
  void streamJoinsBoth() {
    Alternative<StreamKind.Witness> streamAlt = Instances.alternative(stream());

    assertThat(
            STREAM
                .narrow(
                    streamAlt.orElse(
                        STREAM.widen(Stream.of("LAMP")), () -> STREAM.widen(Stream.of("DESK"))))
                .toList())
        .containsExactly("LAMP", "DESK");
  }

  @Test
  @DisplayName("Instances.alternative compiles for Either, and fails when it runs")
  void eitherHasNoAlternative() {
    assertThatThrownBy(() -> Instances.alternative(either()))
        .isInstanceOf(ClassCastException.class);
  }
}
