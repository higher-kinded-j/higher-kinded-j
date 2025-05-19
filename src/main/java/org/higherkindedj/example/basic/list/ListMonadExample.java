// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.list;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListMonad;

public class ListMonadExample {
  public static void main(String[] args) {
    ListMonad listMonad = ListMonad.INSTANCE;

    // 1. Create a ListKind
    Kind<ListKind.Witness, Integer> numbersKind = ListKindHelper.wrap(Arrays.asList(1, 2, 3, 4));

    // 2. Use map
    Function<Integer, String> numberToDecoratedString = n -> "*" + n + "*";
    Kind<ListKind.Witness, String> stringsKind =
        listMonad.map(numberToDecoratedString, numbersKind);
    System.out.println("Mapped: " + ListKindHelper.unwrap(stringsKind));
    // Expected: Mapped: [*1*, *2*, *3*, *4*]

    // 3. Use flatMap
    // Function: integer -> ListKind of [integer, integer*10] if even, else empty ListKind
    Function<Integer, Kind<ListKind.Witness, Integer>> duplicateIfEven =
        n -> {
          if (n % 2 == 0) {
            return ListKindHelper.wrap(Arrays.asList(n, n * 10));
          } else {
            return ListKindHelper.wrap(List.of()); // Empty list
          }
        };
    Kind<ListKind.Witness, Integer> flatMappedKind =
        listMonad.flatMap(duplicateIfEven, numbersKind);
    System.out.println("FlatMapped: " + ListKindHelper.unwrap(flatMappedKind));
    // Expected: FlatMapped: [2, 20, 4, 40]

    // 4. Use of
    Kind<ListKind.Witness, String> singleValueKind = listMonad.of("hello world");
    System.out.println("From 'of': " + ListKindHelper.unwrap(singleValueKind));
    // Expected: From 'of': [hello world]

    Kind<ListKind.Witness, String> fromNullOf = listMonad.of(null);
    System.out.println("From 'of' with null: " + ListKindHelper.unwrap(fromNullOf));
    // Expected: From 'of' with null: []

    // 5. Use ap
    Kind<ListKind.Witness, Function<Integer, String>> listOfFunctions =
        ListKindHelper.wrap(Arrays.asList(i -> "F1:" + i, i -> "F2:" + (i * i)));
    Kind<ListKind.Witness, Integer> inputNumbersForAp = ListKindHelper.wrap(Arrays.asList(5, 6));

    Kind<ListKind.Witness, String> apResult = listMonad.ap(listOfFunctions, inputNumbersForAp);
    System.out.println("Ap result: " + ListKindHelper.unwrap(apResult));
    // Expected: Ap result: [F1:5, F1:6, F2:25, F2:36]

    // Unwrap to get back the standard List
    List<Integer> finalFlatMappedList = ListKindHelper.unwrap(flatMappedKind);
    System.out.println("Final unwrapped flatMapped list: " + finalFlatMappedList);
  }
}
