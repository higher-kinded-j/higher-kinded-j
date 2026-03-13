// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryKindHelper;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.hkt.vtask.VTaskKindHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for traverse, sequence, and flatTraverse methods on ForPath comprehension steps.
 *
 * <p>These tests verify that the path-specific traverse methods work correctly for each path type
 * (MaybePath, EitherPath, IdPath, NonDetPath, GenericPath, etc.).
 */
@DisplayName("ForPath Comprehension Traverse Tests")
class ForPathTraverseTest {

  private final ListTraverse listTraverse = ListTraverse.INSTANCE;
  private final MaybeTraverse maybeTraverse = MaybeTraverse.INSTANCE;

  @Nested
  @DisplayName("MaybePath traverse")
  class MaybePathTraverseTests {

    @Test
    @DisplayName("traverse: should traverse a list within MaybePath")
    void traverseListInMaybe() {
      MaybePath<List<Integer>> result =
          ForPath.from(Path.just(Arrays.asList(1, 2, 3)))
              .traverse(listTraverse, list -> LIST.widen(list), (Integer i) -> MAYBE.just(i * 2))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Maybe<List<Integer>> maybe = result.run();
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("traverse: should fail when any element yields Nothing")
    void traverseWithNothing() {
      MaybePath<List<Integer>> result =
          ForPath.from(Path.just(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) -> i == 2 ? MAYBE.<Integer>nothing() : MAYBE.just(i * 2))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.run().isJust()).isFalse();
    }

    @Test
    @DisplayName("sequence: should turn List<Maybe<Int>> into Maybe<List<Int>>")
    void sequenceListOfMaybes() {
      List<Kind<MaybeKind.Witness, Integer>> listOfMaybes =
          Arrays.asList(MAYBE.just(10), MAYBE.just(20), MAYBE.just(30));
      Kind<ListKind.Witness, Kind<MaybeKind.Witness, Integer>> kindList = LIST.widen(listOfMaybes);

      MaybePath<List<Integer>> result =
          ForPath.from(Path.just(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));
      Maybe<List<Integer>> maybe = result.run();
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten nested lists")
    void flatTraverseListInMaybe() {
      MaybePath<List<Integer>> result =
          ForPath.from(Path.just(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      MAYBE.<Kind<ListKind.Witness, Integer>>just(
                          LIST.widen(Arrays.asList(i, i * 10))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Maybe<List<Integer>> maybe = result.run();
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("traverse: should propagate Nothing from initial path")
    void traverseNothingInitial() {
      MaybePath<List<Integer>> result =
          ForPath.from(Path.<List<Integer>>nothing())
              .traverse(listTraverse, list -> LIST.widen(list), (Integer i) -> MAYBE.just(i * 2))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.run().isJust()).isFalse();
    }
  }

  @Nested
  @DisplayName("OptionalPath traverse")
  class OptionalPathTraverseTests {

    @Test
    @DisplayName("traverse: should traverse a list within OptionalPath")
    void traverseListInOptional() {
      OptionalPath<List<Integer>> result =
          ForPath.from(Path.present(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) -> OPTIONAL.widen(Optional.of(i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.run().isPresent()).isTrue();
      assertThat(result.run().get()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("sequence: should sequence a list of Optionals within OptionalPath")
    void sequenceListInOptional() {
      List<Kind<OptionalKind.Witness, Integer>> listOfOpts =
          Arrays.asList(
              OPTIONAL.widen(Optional.of(10)),
              OPTIONAL.widen(Optional.of(20)),
              OPTIONAL.widen(Optional.of(30)));
      Kind<ListKind.Witness, Kind<OptionalKind.Witness, Integer>> kindList = LIST.widen(listOfOpts);

      OptionalPath<List<Integer>> result =
          ForPath.from(Path.present(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));
      assertThat(result.run().isPresent()).isTrue();
      assertThat(result.run().get()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten within OptionalPath")
    void flatTraverseListInOptional() {
      OptionalPath<List<Integer>> result =
          ForPath.from(Path.present(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      OPTIONAL.widen(
                          Optional.<Kind<ListKind.Witness, Integer>>of(
                              LIST.widen(Arrays.asList(i, i * 10)))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.run().isPresent()).isTrue();
      assertThat(result.run().get()).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  @Nested
  @DisplayName("EitherPath traverse")
  class EitherPathTraverseTests {

    @Test
    @DisplayName("traverse: should traverse a list within EitherPath")
    void traverseListInEither() {
      EitherPath<String, List<Integer>> result =
          ForPath.from(Path.<String, List<Integer>>right(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      EitherKindHelper.EITHER.widen(Either.<String, Integer>right(i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Either<String, List<Integer>> either = result.run();
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("traverse: should fail when any element returns Left")
    void traverseWithLeft() {
      EitherPath<String, List<Integer>> result =
          ForPath.from(Path.<String, List<Integer>>right(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      i == 2
                          ? EitherKindHelper.EITHER.widen(
                              Either.<String, Integer>left("error at 2"))
                          : EitherKindHelper.EITHER.widen(Either.<String, Integer>right(i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Either<String, List<Integer>> either = result.run();
      assertThat(either.isLeft()).isTrue();
    }

    @Test
    @DisplayName("sequence: should sequence a list of Eithers within EitherPath")
    void sequenceInEither() {
      List<Kind<EitherKind.Witness<String>, Integer>> listOfEithers =
          Arrays.asList(
              EitherKindHelper.EITHER.widen(Either.<String, Integer>right(10)),
              EitherKindHelper.EITHER.widen(Either.<String, Integer>right(20)),
              EitherKindHelper.EITHER.widen(Either.<String, Integer>right(30)));
      Kind<ListKind.Witness, Kind<EitherKind.Witness<String>, Integer>> kindList =
          LIST.widen(listOfEithers);

      EitherPath<String, List<Integer>> result =
          ForPath.from(
                  Path
                      .<String, Kind<ListKind.Witness, Kind<EitherKind.Witness<String>, Integer>>>
                          right(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));
      Either<String, List<Integer>> either = result.run();
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten within EitherPath")
    void flatTraverseInEither() {
      EitherPath<String, List<Integer>> result =
          ForPath.from(Path.<String, List<Integer>>right(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      EitherKindHelper.EITHER.widen(
                          Either.<String, Kind<ListKind.Witness, Integer>>right(
                              LIST.widen(Arrays.asList(i, i * 10)))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Either<String, List<Integer>> either = result.run();
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  @Nested
  @DisplayName("TryPath traverse")
  class TryPathTraverseTests {

    @Test
    @DisplayName("traverse: should traverse a list within TryPath")
    void traverseListInTry() {
      TryPath<List<Integer>> result =
          ForPath.from(Path.success(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) -> TryKindHelper.TRY.widen(Try.success(i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Try<List<Integer>> tryResult = result.run();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("sequence: should sequence a list of Trys within TryPath")
    void sequenceListInTry() {
      List<Kind<TryKind.Witness, Integer>> listOfTrys =
          Arrays.asList(
              TryKindHelper.TRY.widen(Try.success(10)),
              TryKindHelper.TRY.widen(Try.success(20)),
              TryKindHelper.TRY.widen(Try.success(30)));
      Kind<ListKind.Witness, Kind<TryKind.Witness, Integer>> kindList = LIST.widen(listOfTrys);

      TryPath<List<Integer>> result =
          ForPath.from(Path.success(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));
      Try<List<Integer>> tryResult = result.run();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten within TryPath")
    void flatTraverseListInTry() {
      TryPath<List<Integer>> result =
          ForPath.from(Path.success(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      TryKindHelper.TRY.widen(
                          Try.<Kind<ListKind.Witness, Integer>>success(
                              LIST.widen(Arrays.asList(i, i * 10)))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Try<List<Integer>> tryResult = result.run();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  @Nested
  @DisplayName("IdPath traverse")
  class IdPathTraverseTests {

    @Test
    @DisplayName("traverse: should traverse a list within IdPath")
    void traverseListInId() {
      IdPath<List<Integer>> result =
          ForPath.from(Path.id(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) -> IdKindHelper.ID.widen(Id.of(i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.run().value()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("sequence: should sequence a list of Ids within IdPath")
    void sequenceListInId() {
      List<Kind<IdKind.Witness, Integer>> listOfIds =
          Arrays.asList(Id.of(10), Id.of(20), Id.of(30));
      Kind<ListKind.Witness, Kind<IdKind.Witness, Integer>> kindList = LIST.widen(listOfIds);

      IdPath<List<Integer>> result =
          ForPath.from(Path.id(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));
      assertThat(result.run().value()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten within IdPath")
    void flatTraverseListInId() {
      IdPath<List<Integer>> result =
          ForPath.from(Path.id(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      IdKindHelper.ID.widen(
                          Id.<Kind<ListKind.Witness, Integer>>of(
                              LIST.widen(Arrays.asList(i, i * 10)))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.run().value()).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  @Nested
  @DisplayName("NonDetPath traverse")
  class NonDetPathTraverseTests {

    @Test
    @DisplayName("traverse: should traverse Maybe within NonDetPath")
    void traverseMaybeInNonDet() {
      NonDetPath<Maybe<String>> result =
          ForPath.from(Path.list(MAYBE.just(42)))
              .traverse(
                  maybeTraverse,
                  val -> val,
                  (Integer i) -> LIST.widen(Arrays.asList("v" + i, "w" + i)))
              .yield((original, traversed) -> MAYBE.narrow(traversed));
      List<Maybe<String>> list = result.run();
      assertThat(list).hasSize(2);
      assertThat(list.get(0)).isEqualTo(Maybe.just("v42"));
      assertThat(list.get(1)).isEqualTo(Maybe.just("w42"));
    }

    @Test
    @DisplayName("sequence: should sequence a Maybe containing a List within NonDetPath")
    void sequenceMaybeInNonDet() {
      // Start with a NonDetPath containing a Maybe<Kind<ListKind.Witness, Integer>>
      // sequence with MaybeTraverse will turn Maybe<List<Int>> into List<Maybe<Int>>
      Kind<MaybeKind.Witness, Kind<ListKind.Witness, Integer>> maybeOfList =
          MAYBE.just(LIST.widen(Arrays.asList(10, 20)));

      NonDetPath<Maybe<Integer>> result =
          ForPath.from(Path.list(maybeOfList))
              .sequence(maybeTraverse, Function.identity())
              .yield((original, sequenced) -> MAYBE.narrow(sequenced));
      List<Maybe<Integer>> list = result.run();
      assertThat(list).hasSize(2);
      assertThat(list.get(0)).isEqualTo(Maybe.just(10));
      assertThat(list.get(1)).isEqualTo(Maybe.just(20));
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten within NonDetPath")
    void flatTraverseMaybeInNonDet() {
      // flatTraverse with Maybe: traverse each Maybe element, then flatten
      NonDetPath<Maybe<String>> result =
          ForPath.from(Path.list(MAYBE.just(42)))
              .flatTraverse(
                  maybeTraverse,
                  MaybeMonad.INSTANCE,
                  val -> val,
                  (Integer i) ->
                      LIST.<Kind<MaybeKind.Witness, String>>widen(
                          Arrays.asList(MAYBE.just("r" + i))))
              .yield((original, traversed) -> MAYBE.narrow(traversed));
      List<Maybe<String>> list = result.run();
      assertThat(list).hasSize(1);
      assertThat(list.get(0)).isEqualTo(Maybe.just("r42"));
    }
  }

  @Nested
  @DisplayName("GenericPath traverse")
  class GenericPathTraverseTests {

    @Test
    @DisplayName("traverse: should traverse a list within GenericPath using IdMonad")
    void traverseListInGeneric() {
      GenericPath<IdKind.Witness, List<Integer>> result =
          ForPath.from(
                  Path.generic(
                      IdKindHelper.ID.widen(Id.of(Arrays.asList(1, 2, 3))), IdMonad.instance()))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) -> IdKindHelper.ID.widen(Id.of(i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));
      List<Integer> list = IdKindHelper.ID.unwrap(result.runKind());
      assertThat(list).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("sequence: should sequence a list of Ids within GenericPath")
    void sequenceListInGeneric() {
      List<Kind<IdKind.Witness, Integer>> listOfIds =
          Arrays.asList(Id.of(10), Id.of(20), Id.of(30));
      Kind<ListKind.Witness, Kind<IdKind.Witness, Integer>> kindList = LIST.widen(listOfIds);

      GenericPath<IdKind.Witness, List<Integer>> result =
          ForPath.from(Path.generic(IdKindHelper.ID.widen(Id.of(kindList)), IdMonad.instance()))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));
      List<Integer> list = IdKindHelper.ID.unwrap(result.runKind());
      assertThat(list).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten within GenericPath")
    void flatTraverseListInGeneric() {
      GenericPath<IdKind.Witness, List<Integer>> result =
          ForPath.from(
                  Path.generic(
                      IdKindHelper.ID.widen(Id.of(Arrays.asList(1, 2, 3))), IdMonad.instance()))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      IdKindHelper.ID.widen(
                          Id.<Kind<ListKind.Witness, Integer>>of(
                              LIST.widen(Arrays.asList(i, i * 10)))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      List<Integer> list = IdKindHelper.ID.unwrap(result.runKind());
      assertThat(list).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  @Nested
  @DisplayName("IOPath traverse")
  class IOPathTraverseTests {

    @Test
    @DisplayName("traverse: should traverse a list within IOPath")
    void traverseListInIO() {
      IOPath<List<Integer>> result =
          ForPath.from(Path.ioPure(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) -> IOKindHelper.IO_OP.widen(IO.delay(() -> i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.unsafeRun()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("sequence: should sequence a list of IOs within IOPath")
    void sequenceListInIO() {
      List<Kind<IOKind.Witness, Integer>> listOfIOs =
          Arrays.asList(
              IOKindHelper.IO_OP.widen(IO.delay(() -> 10)),
              IOKindHelper.IO_OP.widen(IO.delay(() -> 20)),
              IOKindHelper.IO_OP.widen(IO.delay(() -> 30)));
      Kind<ListKind.Witness, Kind<IOKind.Witness, Integer>> kindList = LIST.widen(listOfIOs);

      IOPath<List<Integer>> result =
          ForPath.from(Path.ioPure(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));
      assertThat(result.unsafeRun()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten within IOPath")
    void flatTraverseListInIO() {
      IOPath<List<Integer>> result =
          ForPath.from(Path.ioPure(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      IOKindHelper.IO_OP.widen(
                          IO.<Kind<ListKind.Witness, Integer>>delay(
                              () -> LIST.widen(Arrays.asList(i, i * 10)))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.unsafeRun()).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  @Nested
  @DisplayName("VTaskPath traverse")
  class VTaskPathTraverseTests {

    @Test
    @DisplayName("traverse: should traverse a list within VTaskPath")
    void traverseListInVTask() {
      VTaskPath<List<Integer>> result =
          ForPath.from(Path.vtaskPure(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) -> VTaskKindHelper.VTASK.widen(VTask.of(() -> i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.unsafeRun()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("sequence: should sequence a list of VTasks within VTaskPath")
    void sequenceListInVTask() {
      List<Kind<VTaskKind.Witness, Integer>> listOfVTasks =
          Arrays.asList(
              VTaskKindHelper.VTASK.widen(VTask.of(() -> 10)),
              VTaskKindHelper.VTASK.widen(VTask.of(() -> 20)),
              VTaskKindHelper.VTASK.widen(VTask.of(() -> 30)));
      Kind<ListKind.Witness, Kind<VTaskKind.Witness, Integer>> kindList = LIST.widen(listOfVTasks);

      VTaskPath<List<Integer>> result =
          ForPath.from(Path.vtaskPure(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));
      assertThat(result.unsafeRun()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten within VTaskPath")
    void flatTraverseListInVTask() {
      VTaskPath<List<Integer>> result =
          ForPath.from(Path.vtaskPure(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      VTaskKindHelper.VTASK.widen(
                          VTask.<Kind<ListKind.Witness, Integer>>of(
                              () -> LIST.widen(Arrays.asList(i, i * 10)))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(result.unsafeRun()).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }
}
