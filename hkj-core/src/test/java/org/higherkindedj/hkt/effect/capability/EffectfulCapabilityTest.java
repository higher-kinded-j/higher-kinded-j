// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the polymorphic {@link Effectful} capability surface - {@code handleError}, {@code
 * handleErrorWith}, and {@code guarantee} - so the contract holds identically for every permitted
 * implementation ({@link IOPath} and {@link VTaskPath}).
 */
@DisplayName("Effectful capability (handleError / handleErrorWith / guarantee)")
class EffectfulCapabilityTest {

  /**
   * Factory: builds an {@code Effectful<A>} for each permitted implementation given a thunk. Used
   * to parameterise every capability test so IOPath and VTaskPath go through the exact same
   * assertions.
   */
  interface EffectfulFactory {
    <A> Effectful<A> of(Supplier<A> thunk);

    String label();
  }

  private static final EffectfulFactory IO_FACTORY =
      new EffectfulFactory() {
        @Override
        public <A> Effectful<A> of(Supplier<A> thunk) {
          return Path.io(thunk::get);
        }

        @Override
        public String label() {
          return "IOPath";
        }

        @Override
        public String toString() {
          return label();
        }
      };

  private static final EffectfulFactory VTASK_FACTORY =
      new EffectfulFactory() {
        @Override
        public <A> Effectful<A> of(Supplier<A> thunk) {
          return Path.vtask(thunk::get);
        }

        @Override
        public String label() {
          return "VTaskPath";
        }

        @Override
        public String toString() {
          return label();
        }
      };

  static Stream<Arguments> factories() {
    return Stream.of(Arguments.of(IO_FACTORY), Arguments.of(VTASK_FACTORY));
  }

  @Nested
  @DisplayName("handleError")
  class HandleErrorTests {

    @ParameterizedTest(name = "passes through success value [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void passesThroughSuccessValue(EffectfulFactory factory) {
      Effectful<String> e = factory.of(() -> "ok");

      Effectful<String> recovered = e.handleError(t -> "fallback");

      assertThat(recovered.unsafeRun()).isEqualTo("ok");
    }

    @ParameterizedTest(name = "substitutes recovery value on failure [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void substitutesRecoveryValueOnFailure(EffectfulFactory factory) {
      Effectful<String> e =
          factory.of(
              () -> {
                throw new RuntimeException("boom");
              });

      Effectful<String> recovered = e.handleError(t -> "fallback:" + t.getMessage());

      assertThat(recovered.unsafeRun()).isEqualTo("fallback:boom");
    }

    @ParameterizedTest(name = "rejects null recovery [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void rejectsNullRecovery(EffectfulFactory factory) {
      Effectful<String> e = factory.of(() -> "ok");
      assertThatNullPointerException()
          .isThrownBy(() -> e.handleError(null))
          .withMessageContaining("recovery");
    }
  }

  @Nested
  @DisplayName("handleErrorWith")
  class HandleErrorWithTests {

    @ParameterizedTest(name = "passes through success value [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void passesThroughSuccessValue(EffectfulFactory factory) {
      Effectful<String> e = factory.of(() -> "ok");

      Effectful<String> recovered = e.handleErrorWith(t -> factory.of(() -> "fallback"));

      assertThat(recovered.unsafeRun()).isEqualTo("ok");
    }

    @ParameterizedTest(name = "substitutes recovery effect on failure [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void substitutesRecoveryEffectOnFailure(EffectfulFactory factory) {
      Effectful<String> e =
          factory.of(
              () -> {
                throw new RuntimeException("boom");
              });

      Effectful<String> recovered =
          e.handleErrorWith(t -> factory.of(() -> "fallback:" + t.getMessage()));

      assertThat(recovered.unsafeRun()).isEqualTo("fallback:boom");
    }

    @Test
    @DisplayName("IOPath can recover with a VTaskPath-returning handler")
    void ioCanRecoverIntoVTaskPath() {
      Effectful<String> io =
          Path.io(
              () -> {
                throw new RuntimeException("io-failed");
              });

      Effectful<String> recovered = io.handleErrorWith(t -> Path.vtask(() -> "vtask-recovery"));

      assertThat(recovered).isInstanceOf(IOPath.class);
      assertThat(recovered.unsafeRun()).isEqualTo("vtask-recovery");
    }

    @Test
    @DisplayName("VTaskPath can recover with an IOPath-returning handler")
    void vtaskCanRecoverIntoIoPath() {
      Effectful<String> vtask =
          Path.vtask(
              () -> {
                throw new RuntimeException("vtask-failed");
              });

      Effectful<String> recovered = vtask.handleErrorWith(t -> Path.io(() -> "io-recovery"));

      assertThat(recovered).isInstanceOf(VTaskPath.class);
      assertThat(recovered.unsafeRun()).isEqualTo("io-recovery");
    }

    @ParameterizedTest(name = "rejects null recovery [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void rejectsNullRecovery(EffectfulFactory factory) {
      Effectful<String> e = factory.of(() -> "ok");
      assertThatNullPointerException()
          .isThrownBy(() -> e.handleErrorWith(null))
          .withMessageContaining("recovery");
    }

    @ParameterizedTest(name = "null-returning recovery surfaces as NPE on run [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void nullReturningRecoverySurfacesAsNpeOnRun(EffectfulFactory factory) {
      Effectful<String> e =
          factory.of(
              () -> {
                throw new RuntimeException("boom");
              });

      Effectful<String> recovered = e.handleErrorWith(t -> null);

      assertThatThrownBy(recovered::unsafeRun)
          .hasMessageContaining("recovery must not return null");
    }
  }

  @Nested
  @DisplayName("guarantee")
  class GuaranteeTests {

    @ParameterizedTest(name = "runs finalizer after success [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void runsFinalizerAfterSuccess(EffectfulFactory factory) {
      AtomicBoolean finalizerRan = new AtomicBoolean(false);
      Effectful<String> e = factory.of(() -> "ok").guarantee(() -> finalizerRan.set(true));

      assertThat(e.unsafeRun()).isEqualTo("ok");
      assertThat(finalizerRan).isTrue();
    }

    @ParameterizedTest(name = "runs finalizer after failure [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void runsFinalizerAfterFailure(EffectfulFactory factory) {
      AtomicBoolean finalizerRan = new AtomicBoolean(false);
      // Explicit type witness on .of: the throw-only lambda prevents Java from
      // inferring A from the argument, and target-type inference doesn't reach
      // through a chained .guarantee() call.
      Effectful<String> e =
          factory
              .<String>of(
                  () -> {
                    throw new RuntimeException("boom");
                  })
              .guarantee(() -> finalizerRan.set(true));

      assertThatThrownBy(e::unsafeRun).hasMessageContaining("boom");
      assertThat(finalizerRan).isTrue();
    }

    @ParameterizedTest(name = "runs finalizer exactly once [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void runsFinalizerExactlyOnce(EffectfulFactory factory) {
      AtomicInteger count = new AtomicInteger();
      Effectful<String> e = factory.of(() -> "ok").guarantee(count::incrementAndGet);

      e.unsafeRun();
      assertThat(count).hasValue(1);
    }

    @ParameterizedTest(name = "rejects null finalizer [{0}]")
    @MethodSource("org.higherkindedj.hkt.effect.capability.EffectfulCapabilityTest#factories")
    void rejectsNullFinalizer(EffectfulFactory factory) {
      Effectful<String> e = factory.of(() -> "ok");
      assertThatNullPointerException().isThrownBy(() -> e.guarantee(null));
    }
  }

  @Nested
  @DisplayName("Polymorphic usage")
  class PolymorphicUsageTests {

    // A method that takes ANY Effectful<A> and recovers polymorphically.
    // Before the interface widening, this would not have compiled.
    static <A> Effectful<A> safeOrDefault(Effectful<A> e, A defaultValue) {
      return e.handleError(t -> defaultValue);
    }

    @Test
    @DisplayName("can write helpers against the interface that work on IOPath")
    void polymorphicHelperWorksOnIoPath() {
      Effectful<String> failing =
          Path.io(
              () -> {
                throw new RuntimeException("nope");
              });

      Effectful<String> recovered = safeOrDefault(failing, "default");

      assertThat(recovered).isInstanceOf(IOPath.class);
      assertThat(recovered.unsafeRun()).isEqualTo("default");
    }

    @Test
    @DisplayName("can write helpers against the interface that work on VTaskPath")
    void polymorphicHelperWorksOnVTaskPath() {
      Effectful<String> failing =
          Path.vtask(
              () -> {
                throw new RuntimeException("nope");
              });

      Effectful<String> recovered = safeOrDefault(failing, "default");

      assertThat(recovered).isInstanceOf(VTaskPath.class);
      assertThat(recovered.unsafeRun()).isEqualTo("default");
    }
  }
}
