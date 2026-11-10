// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.ORDERS;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.orders;
import static org.higherkindedj.example.book.foundations.vstream.VStreamHktBook.POSTCODES;
import static org.higherkindedj.example.book.foundations.vstream.VStreamHktBook.customers;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.vstream;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.PagedFeed;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.KindHelperLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.laws.TraverseLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamAlternative;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamMonad;
import org.higherkindedj.hkt.vstream.VStreamTraverse;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the VStream HKT and Type Classes page makes, and checks the laws it states. */
@DisplayName("VStream: HKT and type classes")
class VStreamHktBookTest {

  private static List<String> elements(Kind<VStreamKind.Witness, String> kind) {
    return VSTREAM.narrow(kind).toList().run();
  }

  @Nested
  @DisplayName("one function for every container")
  class OneFunction {

    @Test
    @DisplayName("customers serves a stream, a list and a Maybe")
    void everyContainer() {
      Kind<VStreamKind.Witness, String> streamed =
          customers(Instances.functor(vstream()), VSTREAM.widen(orders(new PagedFeed(ORDERS))));
      List<String> fromAList =
          LIST.narrow(customers(Instances.functor(list()), LIST.widen(ORDERS)));
      Maybe<String> fromAMaybe =
          MAYBE.narrow(
              customers(Instances.functor(maybe()), MAYBE.widen(Maybe.just(ORDERS.getFirst()))));

      assertThat(elements(streamed))
          .containsExactly("Ada", "Grace", "Alan", "Edsger", "Barbara", "Ken");
      assertThat(fromAList).containsExactly("Ada", "Grace", "Alan", "Edsger", "Barbara", "Ken");
      assertThat(fromAMaybe).isEqualTo(Maybe.just("Ada"));
    }

    @Test
    @DisplayName("a stream's map reads nothing until the stream runs")
    void mapStaysLazy() {
      PagedFeed feed = new PagedFeed(ORDERS);

      Kind<VStreamKind.Witness, String> streamed =
          customers(Instances.functor(vstream()), VSTREAM.widen(orders(feed)));

      assertThat(feed.fetched()).isEmpty();
      elements(streamed);
      assertThat(feed.fetched()).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("the instances")
  class TheInstances {

    @Test
    @DisplayName("monad(vstream()) and alternative(vstream()) give the one VStreamAlternative")
    void oneObject() {
      Monad<VStreamKind.Witness> monad = Instances.monad(vstream());
      Alternative<VStreamKind.Witness> alternative = Instances.alternative(vstream());

      assertThat(monad).isSameAs(alternative).isSameAs(VStreamAlternative.INSTANCE);
      assertThat(monad).isInstanceOf(VStreamMonad.class);
      assertThat(Instances.functor(vstream())).isSameAs(monad);
      assertThat(Instances.applicative(vstream())).isSameAs(monad);
    }

    @Test
    @DisplayName("VStreamTraverse is a separate instance, and not a Monad")
    void traverseStandsApart() {
      assertThat((Object) VStreamTraverse.INSTANCE).isNotInstanceOf(Monad.class);
    }

    @Test
    @DisplayName("there is no MonadZero or Selective, so monadZero(vstream()) throws")
    void noMonadZeroOrSelective() {
      assertThat(Instances.monad(vstream()))
          .isNotInstanceOf(MonadZero.class)
          .isNotInstanceOf(Selective.class);
      assertThatThrownBy(() -> Instances.monadZero(vstream()))
          .isInstanceOf(ClassCastException.class);
    }
  }

  @Nested
  @DisplayName("ap pairs every function with every value")
  class Ap {

    private final Kind<VStreamKind.Witness, Function<String, String>> services =
        VSTREAM.widen(
            VStream.<Function<String, String>>of(
                name -> "standard for " + name, name -> "express for " + name));

    @Test
    @DisplayName("each function in turn, over every value, reading the values once")
    void functionMajor() {
      PagedFeed feed = new PagedFeed(ORDERS);
      Kind<VStreamKind.Witness, String> firstTwoPaid =
          VSTREAM.widen(orders(feed).filter(VStreamBook::paid).map(VStreamBook::customer).take(2));

      List<String> offers = elements(Instances.applicative(vstream()).ap(services, firstTwoPaid));

      assertThat(offers)
          .containsExactly(
              "standard for Ada", "standard for Alan", "express for Ada", "express for Alan");
      assertThat(feed.fetched()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("reads every value before it gives its first element")
    void readsEveryValueFirst() {
      AtomicInteger read = new AtomicInteger();
      Kind<VStreamKind.Witness, String> names =
          VSTREAM.widen(VStream.of("Ada", "Alan", "Edsger").peek(_ -> read.incrementAndGet()));

      VSTREAM.narrow(Instances.applicative(vstream()).ap(services, names)).headOption().run();

      assertThat(read).hasValue(3);
    }

    @Test
    @DisplayName("builds nothing until it is run")
    void lazyUntilRun() {
      PagedFeed feed = new PagedFeed(ORDERS);

      Instances.applicative(vstream())
          .ap(services, VSTREAM.widen(orders(feed).map(VStreamBook::customer)));

      assertThat(feed.fetched()).isEmpty();
    }
  }

  @Nested
  @DisplayName("orElse joins two streams")
  class OrElse {

    private final Alternative<VStreamKind.Witness> alternative = Instances.alternative(vstream());
    private final Kind<VStreamKind.Witness, String> london =
        VSTREAM.widen(VStream.of("Ada", "Alan"));

    @Test
    @DisplayName("every element of the first, then every element of the second")
    void joinsBoth() {
      Kind<VStreamKind.Witness, String> edinburgh = VSTREAM.widen(VStream.of("Edsger", "Ken"));

      assertThat(elements(alternative.orElse(london, () -> edinburgh)))
          .containsExactly("Ada", "Alan", "Edsger", "Ken");
      assertThat(elements(alternative.orElse(alternative.empty(), () -> london)))
          .containsExactly("Ada", "Alan");
    }

    @Test
    @DisplayName("joins the second even when the first has elements, unlike Maybe's orElse")
    void notAFallback() {
      Kind<VStreamKind.Witness, String> joined = alternative.orElse(london, () -> london);

      assertThat(elements(joined)).containsExactly("Ada", "Alan", "Ada", "Alan");
    }

    @Test
    @DisplayName("builds the second stream only once the first has ended")
    void buildsTheSecondLate() {
      AtomicInteger built = new AtomicInteger();
      Kind<VStreamKind.Witness, String> joined =
          alternative.orElse(
              london,
              () -> {
                built.incrementAndGet();
                return london;
              });

      VSTREAM.narrow(joined).take(2).toList().run();
      assertThat(built).hasValue(0);
      elements(joined);
      assertThat(built).hasValue(1);
    }

    @Test
    @DisplayName("guard(false) is empty and guard(true) holds one element")
    void guard() {
      assertThat(VSTREAM.narrow(alternative.guard(false)).toList().run()).isEmpty();
      assertThat(VSTREAM.narrow(alternative.guard(true)).toList().run()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("foldMap and traverse read the whole stream")
  class FoldAndTraverse {

    private final VStreamTraverse traverse = VStreamTraverse.INSTANCE;

    @Test
    @DisplayName("foldMap adds up what the paid orders are worth")
    void foldMapSums() {
      BigDecimal paidValue =
          traverse.foldMap(
              Monoids.bigDecimalAddition(),
              VStreamHktBook::value,
              VSTREAM.widen(orders(new PagedFeed(ORDERS)).filter(VStreamBook::paid)));

      assertThat(paidValue).isEqualByComparingTo("160.00");
    }

    @Test
    @DisplayName("foldMap runs the stream when it is called, and wraps a checked failure")
    void foldMapRunsAtOnce() {
      PagedFeed downAtTwo = new PagedFeed(ORDERS, 2);

      assertThatThrownBy(
              () ->
                  traverse.foldMap(
                      Monoids.bigDecimalAddition(),
                      VStreamHktBook::value,
                      VSTREAM.widen(orders(downAtTwo))))
          .isInstanceOf(VTaskExecutionException.class)
          .hasCauseInstanceOf(IOException.class);
      assertThat(downAtTwo.fetched()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("foldMap reads the stream on the calling thread")
    void foldMapOnTheCallingThread() {
      List<Thread> readers = new CopyOnWriteArrayList<>();

      traverse.foldMap(
          Monoids.bigDecimalAddition(),
          order -> {
            readers.add(Thread.currentThread());
            return VStreamHktBook.value(order);
          },
          VSTREAM.widen(orders(new PagedFeed(ORDERS))));

      assertThat(readers).isNotEmpty().containsOnly(Thread.currentThread());
    }

    @Test
    @DisplayName("traverse gives Just when every element has a value, and Nothing otherwise")
    void traverseWithMaybe() {
      Function<Order, Kind<MaybeKind.Witness, String>> postcode =
          order -> MAYBE.widen(Maybe.fromNullable(POSTCODES.get(VStreamBook.customer(order))));
      Applicative<MaybeKind.Witness> maybes = Instances.applicative(maybe());

      Maybe<List<String>> forThePaid =
          MAYBE
              .narrow(
                  traverse.traverse(
                      maybes,
                      postcode,
                      VSTREAM.widen(orders(new PagedFeed(ORDERS)).filter(VStreamBook::paid))))
              .map(VStreamHktBookTest::elements);
      Maybe<Kind<VStreamKind.Witness, String>> forEveryOrder =
          MAYBE.narrow(
              traverse.traverse(maybes, postcode, VSTREAM.widen(orders(new PagedFeed(ORDERS)))));

      assertThat(forThePaid)
          .isEqualTo(Maybe.just(List.of("N1 9GU", "M13 9PL", "CB2 1TN", "EH8 9YL")));
      assertThatMaybe(forEveryOrder).isNothing();
    }

    @Test
    @DisplayName("traverse reads the stream when it is called, before its result is used")
    void traverseRunsAtOnce() {
      PagedFeed feed = new PagedFeed(ORDERS);

      traverse.traverse(
          Instances.applicative(maybe()),
          order -> MAYBE.widen(Maybe.just(order)),
          VSTREAM.widen(orders(feed)));

      assertThat(feed.fetched()).containsExactly(1, 2, 3, 4);
    }
  }

  // ANCHOR: laws
  // Two streams are the same when a run of each gives the same elements in the same order
  private static final BiPredicate<Kind<VStreamKind.Witness, ?>, Kind<VStreamKind.Witness, ?>>
      SAME = KindEquivalence.byEqualsAfter(kind -> VSTREAM.narrow(kind).toList().run());

  @Test
  @DisplayName("VStream's instances keep the Functor, Applicative, Monad and Traverse laws")
  void laws() {
    Monad<VStreamKind.Witness> monad = Instances.monad(vstream());
    Order ada = ORDERS.getFirst();
    Kind<VStreamKind.Witness, Order> orders = VSTREAM.widen(VStream.fromList(ORDERS));
    Function<Order, Kind<VStreamKind.Witness, LineItem>> lines =
        order -> VSTREAM.widen(VStream.fromList(order.lines()));
    Function<LineItem, Kind<VStreamKind.Witness, String>> skus =
        line -> VSTREAM.widen(VStream.of(line.sku(), line.sku() + " spare"));
    Kind<VStreamKind.Witness, Function<Order, Customer>> customer =
        VSTREAM.widen(VStream.of(Order::customer));
    Kind<VStreamKind.Witness, Function<Customer, String>> name =
        VSTREAM.widen(VStream.of(Customer::name));

    FunctorLaws.assertIdentity(monad, orders, SAME);
    FunctorLaws.assertComposition(monad, orders, Order::customer, Customer::name, SAME);
    ApplicativeLaws.assertIdentity(monad, orders, SAME);
    ApplicativeLaws.assertHomomorphism(monad, ada, Order::customer, SAME);
    ApplicativeLaws.assertInterchange(monad, customer, ada, SAME);
    ApplicativeLaws.assertComposition(monad, name, customer, orders, SAME);
    MonadLaws.assertLeftIdentity(monad, ada, lines, SAME);
    MonadLaws.assertRightIdentity(monad, orders, SAME);
    MonadLaws.assertAssociativity(monad, orders, lines, skus, SAME);
    TraverseLaws.assertIdentity(VStreamTraverse.INSTANCE, orders, SAME);
    KindHelperLaws.assertRoundTrip(VStream.fromList(ORDERS), VSTREAM::widen, VSTREAM::narrow);
  }

  // ANCHOR_END: laws
}
