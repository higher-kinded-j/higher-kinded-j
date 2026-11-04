// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.resource.ResourceBook.ORDER;
import static org.higherkindedj.example.book.foundations.resource.ResourceBook.sessions;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Resource Management page makes about Resource. */
@DisplayName("Resource: acquire, use and release")
class ResourceBookTest {

  /** Waits for the latch, failing rather than hanging if it is never released. */
  private static void awaitOrFail(CountDownLatch latch) throws InterruptedException {
    if (!latch.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("the latch was never released");
    }
  }

  /** A Resource whose close() throws a checked exception. */
  private static Resource<AutoCloseable> closeFails() {
    return Resource.fromAutoCloseable(
        () ->
            () -> {
              throw new IOException("flush failed");
            });
  }

  @Nested
  @DisplayName("using a resource")
  class UsingAResource {

    @Test
    @DisplayName("use opens a session, books on it, and closes it, and building it opens nothing")
    void useOpensBooksAndCloses() {
      List<String> log = new ArrayList<>();
      VTask<String> booking = sessions(log).use(s -> VTask.of(() -> s.book(ORDER)));

      assertThat(log).isEmpty();
      assertThat(booking.run()).isEqualTo("booked on session-1");
      assertThat(log).containsExactly("open session-1", "book on session-1", "close session-1");
    }

    @Test
    @DisplayName("a failed use still closes the session, and its failure is the one reported")
    void aFailedUseStillCloses() {
      List<String> log = new ArrayList<>();
      IOException refused = new IOException("the courier refused");

      Try<String> outcome = sessions(log).use(s -> VTask.<String>fail(refused)).runSafe();

      assertThat(outcome).isEqualTo(Try.failure(refused));
      assertThat(log).containsExactly("open session-1", "close session-1");
    }

    @Test
    @DisplayName("a release that throws after a failed use is suppressed onto the use's failure")
    void aFailingReleaseIsSuppressed() {
      IllegalStateException useFailed = new IllegalStateException("use failed");

      assertThatTry(closeFails().use(c -> VTask.<String>fail(useFailed)).runSafe())
          .isFailure()
          .hasException(useFailed);
      assertThat(useFailed.getSuppressed()).singleElement().isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName(
        "close() throwing a checked exception fails the use: run wraps it, runSafe holds it")
    void aFailingCloseFailsTheUse() {
      VTask<String> use = closeFails().useSync(c -> "ok");

      assertThatThrownBy(use::run)
          .isInstanceOf(VTaskExecutionException.class)
          .cause()
          .isInstanceOf(IOException.class)
          .hasMessage("flush failed");
      assertThatTry(use.runSafe()).isFailure().hasExceptionOfType(IOException.class);
    }

    @Test
    @DisplayName("a Resource never holds null: pure(null) throws, a null acquire fails the use")
    void aResourceNeverHoldsNull() {
      assertThatNullPointerException().isThrownBy(() -> Resource.pure(null));
      assertThatTry(Resource.make(() -> null, _ -> {}).useSync(value -> "ok").runSafe())
          .isFailure()
          .hasExceptionOfType(NullPointerException.class);
    }

    @Test
    @DisplayName("a cancelled use still closes the session")
    void aCancelledUseStillCloses() {
      List<String> log = new CopyOnWriteArrayList<>();
      VTask<String> waits =
          sessions(log)
              .use(
                  s ->
                      VTask.of(
                          () -> {
                            awaitOrFail(new CountDownLatch(1));
                            return "never";
                          }));

      String first =
          Scope.<String>firstComplete().fork(waits).fork(VTask.succeed("other")).join().run();

      assertThat(first).isEqualTo("other");
      assertThat(log).containsExactly("open session-1", "close session-1");
    }
  }

  @Nested
  @DisplayName("composing resources")
  class ComposingResources {

    @Test
    @DisplayName("and acquires in order, and releases in reverse")
    void andReleasesInReverse() {
      List<String> log = new ArrayList<>();
      var session = sessions(log);

      session.and(session).useSync(both -> both.first().id() + both.second().id()).run();

      assertThat(log)
          .containsExactly(
              "open session-1", "open session-2", "close session-2", "close session-1");
    }

    @Test
    @DisplayName("flatMap releases the second resource first, by its own release")
    void flatMapReleasesInReverse() {
      List<String> log = new ArrayList<>();
      Resource<String> label =
          sessions(log)
              .flatMap(s -> Resource.make(() -> s.id() + "-label", l -> log.add("discard " + l)));

      label.useSync(String::length).run();

      assertThat(log)
          .containsExactly("open session-1", "discard session-1-label", "close session-1");
    }

    @Test
    @DisplayName("when the second acquire fails, the first resource is released")
    void aFailedSecondAcquireReleasesTheFirst() {
      List<String> log = new ArrayList<>();
      Resource<String> failing =
          Resource.make(
              () -> {
                throw new IOException("no label printer");
              },
              _ -> {});

      assertThatTry(sessions(log).and(failing).useSync(both -> "ok").runSafe())
          .isFailure()
          .hasExceptionOfType(IOException.class);
      assertThat(log).containsExactly("open session-1", "close session-1");
    }

    @Test
    @DisplayName("a composed Resource reports a failed release as a RuntimeException")
    void aComposedReleaseFailureIsWrapped() {
      assertThatTry(closeFails().and(Resource.pure("x")).useSync(both -> "ok").runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error ->
                  assertThat(error)
                      .isInstanceOf(RuntimeException.class)
                      .cause()
                      .isInstanceOf(IOException.class));
    }

    @Test
    @DisplayName("uses at the same time each acquire and release their own")
    void usesAtTheSameTimeEachHoldTheirOwn() {
      List<String> log = new CopyOnWriteArrayList<>();
      var session = sessions(log);
      CountDownLatch both = new CountDownLatch(2);

      Scope.<String>allSucceed()
          .fork(
              session.use(
                  s ->
                      VTask.of(
                          () -> {
                            both.countDown();
                            awaitOrFail(both);
                            return s.id();
                          })))
          .fork(
              session.use(
                  s ->
                      VTask.of(
                          () -> {
                            both.countDown();
                            awaitOrFail(both);
                            return s.id();
                          })))
          .join()
          .run();

      assertThat(log)
          .containsExactlyInAnyOrder(
              "open session-1", "open session-2", "close session-1", "close session-2");
    }
  }

  @Nested
  @DisplayName("finalisers and onFailure")
  class FinalisersAndOnFailure {

    @Test
    @DisplayName("a finaliser runs when the release throws, and the release's exception is kept")
    void aFinaliserRunsAfterAFailedRelease() {
      List<String> ran = new ArrayList<>();
      IllegalStateException finaliserFailed = new IllegalStateException("finaliser failed");
      Resource<String> handle =
          Resource.make(
                  () -> "handle",
                  _ -> {
                    throw new IllegalStateException("release failed");
                  })
              .withFinalizer(
                  () -> {
                    ran.add("finaliser");
                    throw finaliserFailed;
                  });

      assertThatTry(handle.useSync(String::length).runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error -> {
                assertThat(error).hasMessage("release failed");
                assertThat(error.getSuppressed()).containsExactly(finaliserFailed);
              });
      assertThat(ran).containsExactly("finaliser");
    }

    @Test
    @DisplayName(
        "a later finaliser runs when an earlier one throws, and the earlier one's exception is kept")
    void aLaterFinaliserRunsAfterAFailedOne() {
      List<String> ran = new ArrayList<>();
      IllegalStateException firstFailed = new IllegalStateException("first finaliser failed");
      Resource<String> handle =
          Resource.make(() -> "handle", _ -> ran.add("release"))
              .withFinalizer(
                  () -> {
                    ran.add("first finaliser");
                    throw firstFailed;
                  })
              .withFinalizer(() -> ran.add("later finaliser"));

      assertThatTry(handle.useSync(String::length).runSafe()).hasException(firstFailed);
      assertThat(ran).containsExactly("release", "first finaliser", "later finaliser");
    }

    @Test
    @DisplayName("onFailure actions run before the release, most recently added first")
    void onFailureRunsBeforeTheRelease() {
      List<String> ledger = new ArrayList<>();
      Resource<String> transaction =
          Resource.make(() -> "tx", tx -> ledger.add("close " + tx))
              .onFailure(tx -> ledger.add("rollback " + tx))
              .onFailure(tx -> ledger.add("alert for " + tx));

      transaction.use(_ -> VTask.fail(new IllegalStateException("insert failed"))).runSafe();

      assertThat(ledger).containsExactly("alert for tx", "rollback tx", "close tx");
    }

    @Test
    @DisplayName("a failing step in a Scope cancels the others, rolls back and closes")
    void aFailingStepRollsBackAndCloses() {
      List<String> ledger = new CopyOnWriteArrayList<>();
      CountDownLatch never = new CountDownLatch(1);
      Resource<String> transaction =
          Resource.make(() -> "tx", tx -> ledger.add("close " + tx))
              .onFailure(tx -> ledger.add("rollback " + tx));

      Try<List<String>> outcome =
          transaction
              .use(
                  tx ->
                      Scope.<String>allSucceed()
                          .fork(
                              VTask.of(
                                  () -> {
                                    try {
                                      awaitOrFail(never);
                                      return "charged";
                                    } catch (InterruptedException e) {
                                      ledger.add("charge cancelled");
                                      throw e;
                                    }
                                  }))
                          .fork(VTask.fail(new IllegalStateException("out of stock")))
                          .join())
              .runSafe();

      assertThatTry(outcome).isFailure().hasExceptionOfType(IllegalStateException.class);
      assertThat(ledger).containsExactly("charge cancelled", "rollback tx", "close tx");
    }

    @Test
    @DisplayName("a session held across a Scope closes after both bookings")
    void aSessionHeldAcrossAScope() {
      List<String> log = new CopyOnWriteArrayList<>();
      var session = sessions(log);

      List<String> bookings =
          session
              .use(
                  s ->
                      Scope.<String>allSucceed()
                          .fork(VTask.of(() -> s.book(ORDER)))
                          .fork(VTask.of(() -> s.book(ORDER)))
                          .join())
              .run();

      assertThat(bookings).containsExactly("booked on session-1", "booked on session-1");
      assertThat(log.getFirst()).isEqualTo("open session-1");
      assertThat(log.getLast()).isEqualTo("close session-1");
    }
  }
}
