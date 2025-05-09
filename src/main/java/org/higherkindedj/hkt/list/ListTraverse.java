package org.higherkindedj.hkt.list;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.typeclass.Traverse;
import org.jspecify.annotations.NonNull;

public class ListTraverse implements Traverse<ListKind.Witness> {

  public static final ListTraverse INSTANCE = new ListTraverse();

  private ListTraverse() {}

  @Override
  public <A, B> @NonNull Kind<ListKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<ListKind.Witness, A> fa) {
    return ListFunctor.INSTANCE.map(f, fa);
  }

  @Override
  public <G, A, B> @NonNull Kind<G, Kind<ListKind.Witness, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<ListKind.Witness, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f) {

    List<A> listA = ListKind.narrow(ta).unwrap();

    if (listA.isEmpty()) {
      ListKind<B> emptyListKind = ListKind.of(Collections.emptyList());
      return applicative.of(emptyListKind);
    }

    Kind<G, List<B>> accumulatedEffects = applicative.of(new ArrayList<>());

    for (A item : listA) {
      // effectOfItemRaw is Kind<G, ? extends B>
      Kind<G, ? extends B> effectOfItemRaw = f.apply(item);
      // Ensure effectOfItem is Kind<G, B> for ap.
      // The cast (B)val is safe because 'val' is from '? extends B'.
      Kind<G, B> effectOfItem = applicative.map(val -> (B) val, effectOfItemRaw);

      // We want to combine:
      // accumulatedEffects: Kind<G, List<B>>
      // effectOfItem:       Kind<G, B>
      // into a new Kind<G, List<B>>

      // Create a function: List<B> -> Function<B, List<B>>
      // This function takes the current accumulated list and returns a new function
      // that takes the new item and returns the combined list.
      Function<List<B>, Function<B, List<B>>> combineToListFn =
          currentList ->
              newItem -> {
                List<B> newList = new ArrayList<>(currentList.size() + 1);
                newList.addAll(currentList);
                newList.add(newItem);
                return newList;
              };

      // Lift combineToListFn into the applicative context G, applying it to accumulatedEffects.
      // map(combineToListFn, accumulatedEffects) yields Kind<G, Function<B, List<B>>>
      Kind<G, Function<B, List<B>>> liftedCombineFn =
          applicative.map(combineToListFn, accumulatedEffects);

      // Apply the lifted function to the effect of the current item.
      // ap( Kind<G, Function<B, List<B>>> , Kind<G, B> ) results in Kind<G, List<B>>
      accumulatedEffects = applicative.ap(liftedCombineFn, effectOfItem);
    }
    // Map the final Kind<G, List<B>> to Kind<G, Kind<ListKind.Witness, B>>
    // by wrapping the List<B> with ListKind.of()
    return applicative.map(ListKind::of, accumulatedEffects);
  }
}
