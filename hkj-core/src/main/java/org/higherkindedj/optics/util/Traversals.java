// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdentityMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.optics.Traversal;

public final class Traversals {
  private Traversals() {}

  public static <S, A> S modify(Traversal<S, A> traversal, Function<A, A> f, S source) {
    Function<A, Kind<Id.Witness, A>> fId = a -> Id.of(f.apply(a));
    Kind<Id.Witness, S> resultInId = traversal.modifyF(fId, source, IdentityMonad.instance());
    return IdKindHelper.ID.narrow(resultInId).value();
  }

  public static <S, A> List<A> getAll(final Traversal<S, A> traversal, final S source) {
    final List<A> results = new ArrayList<>();
    // Use the Id monad to traverse without any real "effect", just for the side-effect of
    // collection.
    traversal.modifyF(
        a -> {
          results.add(a);
          return Id.of(a); // Return the original value in an Id context
        },
        source,
        IdentityMonad.instance());
    return results;
  }

  public static <A> Traversal<List<A>, A> forList() {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, List<A>> modifyF(
          Function<A, Kind<F, A>> f, List<A> source, Applicative<F> applicative) {
        // This is the standard, lawful implementation of traverse for a List.
        Kind<F, Kind<ListKind.Witness, A>> traversed =
            ListTraverse.INSTANCE.traverse(applicative, ListKindHelper.LIST.widen(source), f);
        return applicative.map(ListKindHelper.LIST::narrow, traversed);
      }
    };
  }

  public static <F, A, B> Kind<F, List<B>> traverseList(
      final List<A> list, final Function<A, Kind<F, B>> f, final Applicative<F> applicative) {

    final List<Kind<F, B>> listOfEffects = list.stream().map(f).collect(Collectors.toList());
    final Kind<ListKind.Witness, Kind<F, B>> effectsAsKind =
        ListKindHelper.LIST.widen(listOfEffects);
    final var effectOfKindList = ListTraverse.INSTANCE.sequenceA(applicative, effectsAsKind);

    return applicative.map(ListKindHelper.LIST::narrow, effectOfKindList);
  }
}
