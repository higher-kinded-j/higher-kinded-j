// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.identity.IdentityBook.BULBS;
import static org.higherkindedj.example.book.foundations.identity.IdentityBook.LAMP;
import static org.higherkindedj.example.book.foundations.identity.IdentityBook.SOFA;
import static org.higherkindedj.example.book.foundations.identity.IdentityBook.add;
import static org.higherkindedj.example.book.foundations.identity.IdentityBook.withDelivery;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.foundations.Basket;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.id.IdSelective;
import org.higherkindedj.hkt.id.IdTraverse;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Identity page makes about the monad that adds nothing. */
@DisplayName("Identity: the monad that adds nothing")
class IdentityBookTest {

  private static final Monad<IdKind.Witness> IDS = Instances.monad(id());

  @Nested
  @DisplayName("a step that cannot fail")
  class ThePath {

    @Test
    @DisplayName("an IdPath holds the basket's total, and get gives it")
    void totalOnAPath() {
      assertThat(Path.id(Basket.EMPTY.add(LAMP).add(BULBS)).map(Basket::total).get())
          .isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("as the first step of an EitherPath, it is a Right")
    void joinsAChainThatCanFail() {
      assertThat(Path.id(new BigDecimal("50.00")).<String>toEitherPath().run())
          .isEqualTo(Either.right(new BigDecimal("50.00")));
    }
  }

  @Nested
  @DisplayName("code written for any monad")
  class AnyMonad {

    @Test
    @DisplayName("with Id, a total under 50.00 gains the 4.99 delivery charge")
    void chargedUnderTheThreshold() {
      assertThat(ID.narrow(withDelivery(IDS, Id.of(new BigDecimal("12.50")))).value())
          .isEqualByComparingTo("17.49");
    }

    @Test
    @DisplayName("with Id, a total of 50.00 or more goes free")
    void freeFromTheThreshold() {
      assertThat(ID.narrow(withDelivery(IDS, Id.of(new BigDecimal("50.00")))).value())
          .isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("with IO, the same method gives the same total, and only when the IO runs")
    void sameMethodWithIo() {
      AtomicInteger reads = new AtomicInteger();
      IO<BigDecimal> charged =
          IO_OP.narrow(
              withDelivery(
                  Instances.monad(io()),
                  IO_OP.widen(
                      IO.delay(
                          () -> {
                            reads.incrementAndGet();
                            return new BigDecimal("12.50");
                          }))));

      assertThat(reads).hasValue(0);
      assertThat(charged.unsafeRunSync()).isEqualByComparingTo("17.49");
      assertThat(reads).hasValue(1);
    }
  }

  @Nested
  @DisplayName("a transformer over Id")
  class OverId {

    @Test
    @DisplayName("the StateT over Id adds a line that keeps the basket within the limit")
    void addsWithinTheLimit() {
      StateTuple<Basket, Boolean> after = ID.narrow(add(LAMP).runStateT(Basket.EMPTY)).value();

      assertThat(after.value()).isTrue();
      assertThat(after.state().total()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("the StateT over Id refuses the sofa, and leaves the basket as it was")
    void refusesOverTheLimit() {
      StateTuple<Basket, Boolean> after = ID.narrow(add(SOFA).runStateT(Basket.EMPTY)).value();

      assertThat(after.value()).isFalse();
      assertThat(after.state()).isEqualTo(Basket.EMPTY);
    }
  }

  @Nested
  @DisplayName("what Id does with null")
  class Null {

    @Test
    @DisplayName("Id.of(null) holds the null")
    void idHoldsNull() {
      assertThat(Id.of(null).value()).isNull();
    }

    @Test
    @DisplayName("the monad's map keeps a null its function returns")
    void mapKeepsNull() {
      assertThat(ID.narrow(IDS.map(_ -> null, Id.of("LAMP"))).value()).isNull();
    }

    @Test
    @DisplayName("Path.id(null) and Path.idPath of an Id holding null throw")
    void pathRefusesNull() {
      assertThatThrownBy(() -> Path.id(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Path.idPath(Id.of(null))).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("Instances.monad(id()) is IdMonad's instance")
    void instance() {
      assertThat(IDS).isSameAs(IdMonad.instance());
    }

    @Test
    @DisplayName("widen and narrow give back the same Id, with no holder between")
    void noHolder() {
      Id<String> lamp = Id.of("LAMP");

      assertThat(ID.widen(lamp)).isSameAs(lamp);
      assertThat(ID.narrow(ID.widen(lamp))).isSameAs(lamp);
    }

    @Test
    @DisplayName("IdTraverse is Id's Foldable too, and IdSelective its Selective")
    void foldableAndSelective() {
      assertThat(IdTraverse.INSTANCE).isInstanceOf(Foldable.class);
      assertThat(IdSelective.instance()).isInstanceOf(Selective.class);
    }

    @Test
    @DisplayName("IdTraverse traverses an Id with any applicative")
    void traverse() {
      assertThat(
              IdTraverse.INSTANCE.traverse(
                  Instances.applicative(io()),
                  sku -> IO_OP.widen(IO.delay(() -> sku.length())),
                  Id.of("LAMP")))
          .satisfies(
              io -> assertThat(ID.narrow(IO_OP.narrow(io).unsafeRunSync()).value()).isEqualTo(4));
    }
  }
}
