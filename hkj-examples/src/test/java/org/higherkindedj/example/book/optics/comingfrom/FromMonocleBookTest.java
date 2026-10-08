// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.comingfrom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.consignment;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Optic;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Setter;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.laws.AffineLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Coming from Monocle or Haskell lens page makes. */
@DisplayName("Coming from Monocle or Haskell lens: where the semantics differ")
class FromMonocleBookTest {

  private static final Consignment PENDING = consignment(new ConsignmentState.Pending());

  private static final Consignment RETURNED =
      consignment(new ConsignmentState.Returned("wrong size"));

  @Test
  @DisplayName("set through a lens and a prism writes an absent focus; modify leaves it alone")
  void affineSetWritesAnAbsentFocus() {
    FromMonocleBook.AffineSet result = FromMonocleBook.affineSet(PENDING);

    assertThat(result.written().state()).isEqualTo(new ConsignmentState.Returned("damaged"));
    assertThat(result.untouched()).isSameAs(PENDING);
  }

  @Test
  @DisplayName("assertAffineLaws accepts that affine, and assertSetNoOpWhenAbsent refuses it")
  void affineLawsAllowTheWrite() {
    Affine<Consignment, ConsignmentState.Returned> returned =
        ConsignmentLenses.state().andThen(ConsignmentStatePrisms.returned());
    ConsignmentState.Returned damaged = new ConsignmentState.Returned("damaged");
    ConsignmentState.Returned lost = new ConsignmentState.Returned("lost");

    AffineLaws.assertAffineLaws(returned, RETURNED, PENDING, damaged, lost);
    assertThatThrownBy(() -> AffineLaws.assertSetNoOpWhenAbsent(returned, PENDING, damaged))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  @DisplayName("a prism previews, builds and modifies, and passes another state through")
  void prismOperations() {
    ConsignmentState pending = new ConsignmentState.Pending();
    FromMonocleBook.PrismOps ops = FromMonocleBook.prismOps(pending);

    assertThat(ops.seen()).isEmpty();
    assertThat(ops.built()).isEqualTo(new ConsignmentState.Returned("damaged"));
    assertThat(ops.replaced()).isEqualTo(new ConsignmentState.Returned("lost"));
    assertThat(ops.passedOver()).isSameAs(pending);
  }

  @Test
  @DisplayName("Prism declares no set")
  void prismHasNoSet() {
    assertThat(Arrays.stream(Prism.class.getMethods()).map(Method::getName))
        .doesNotContain("set")
        .contains("getOptional", "build", "modify", "setWhen");
  }

  @Test
  @DisplayName("headOption reads the first element and writes every one")
  void headOptionWritesEveryElement() {
    FromMonocleBook.HeadOption result = FromMonocleBook.headOption(ORDER);

    assertThat(result.read()).contains(1);
    assertThat(result.everyLine().lines()).extracting(LineItem::quantity).containsExactly(7, 7);
    assertThat(result.firstLine().lines()).extracting(LineItem::quantity).containsExactly(7, 4);
  }

  @Test
  @DisplayName("headOption's modify applies the function to the first value and writes it to all")
  void headOptionModifyWritesTheFirstResultEverywhere() {
    Order bumped =
        OrderFocus.lines().via(LineItemFocus.quantity()).headOption().modify(q -> q + 1, ORDER);

    assertThat(bumped.lines()).extracting(LineItem::quantity).containsExactly(2, 2);
  }

  @Test
  @DisplayName("every optic type fixes T = S and B = A on Optic<S, T, A, B>")
  void everyOpticIsSimple() {
    for (Class<?> optic :
        List.of(
            Lens.class,
            Prism.class,
            Affine.class,
            Traversal.class,
            Fold.class,
            Setter.class,
            Iso.class)) {
      ParameterizedType supertype =
          Arrays.stream(optic.getGenericInterfaces())
              .filter(ParameterizedType.class::isInstance)
              .map(ParameterizedType.class::cast)
              .filter(type -> type.getRawType() == Optic.class)
              .findFirst()
              .orElseThrow();
      assertThat(Arrays.stream(supertype.getActualTypeArguments()).map(Type::getTypeName))
          .as("%s's Optic supertype", optic.getSimpleName())
          .containsExactly("S", "S", "A", "A");
    }
  }

  @Test
  @DisplayName("Optic has one abstract method, modifyF over an Applicative")
  void opticIsEncodedByModifyF() {
    List<Method> abstractMethods =
        Arrays.stream(Optic.class.getMethods())
            .filter(method -> Modifier.isAbstract(method.getModifiers()))
            .toList();

    assertThat(abstractMethods).extracting(Method::getName).containsExactly("modifyF");
    assertThat(abstractMethods.getFirst().getParameterTypes()[2].getSimpleName())
        .isEqualTo("Applicative");
  }

  @Test
  @DisplayName("the andThen overload picks the type: a lens then a prism is an Affine")
  void andThenOverloadPicksTheType() throws NoSuchMethodException {
    assertThat(Lens.class.getMethod("andThen", Prism.class).getReturnType())
        .isEqualTo(Affine.class);
  }

  @Test
  @DisplayName("a generated accessor builds a new optic on every call")
  void generatedAccessorIsAFactory() {
    assertThat(OrderLenses.customer()).isNotSameAs(OrderLenses.customer());
  }

  @Test
  @DisplayName("an affine's modify ignoring its argument is the set of lens: present writes")
  void modifyIsLensSetWhenPresent() {
    Affine<Consignment, ConsignmentState.Returned> returned =
        ConsignmentLenses.state().andThen(ConsignmentStatePrisms.returned());

    assertThat(returned.modify(_ -> new ConsignmentState.Returned("lost"), RETURNED).state())
        .isEqualTo(new ConsignmentState.Returned("lost"));
    assertThat(returned.getOptional(PENDING)).isEqualTo(Optional.empty());
  }
}
