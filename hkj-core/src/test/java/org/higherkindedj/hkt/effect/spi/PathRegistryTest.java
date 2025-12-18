// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.spi;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for PathRegistry and Path.from() factory method.
 *
 * <p>Tests cover provider registration, lookup, and creation of paths from registered providers.
 */
@DisplayName("PathRegistry Test Suite")
class PathRegistryTest {

  /** Test implementation of PathProvider for MaybeKind */
  private static class MaybePathProvider implements PathProvider<MaybeKind.Witness> {
    private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;

    @Override
    public Class<?> witnessType() {
      return MaybeKind.Witness.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> Chainable<A> createPath(Kind<MaybeKind.Witness, A> kind) {
      Maybe<A> maybe = MAYBE.narrow(kind);
      return (Chainable<A>) Path.maybe(maybe);
    }

    @Override
    public Monad<MaybeKind.Witness> monad() {
      return MONAD;
    }
  }

  @BeforeEach
  void setUp() {
    PathRegistry.clear();
  }

  @AfterEach
  void tearDown() {
    PathRegistry.clear();
  }

  @Nested
  @DisplayName("Provider Registration")
  class ProviderRegistrationTests {

    @Test
    @DisplayName("register() adds provider to registry")
    void registerAddsProvider() {
      PathRegistry.register(new MaybePathProvider());

      assertThat(PathRegistry.hasProvider(MaybeKind.Witness.class)).isTrue();
    }

    @Test
    @DisplayName("register() validates non-null provider")
    void registerValidatesNonNullProvider() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathRegistry.register(null))
          .withMessageContaining("provider must not be null");
    }

    @Test
    @DisplayName("unregister() removes provider from registry")
    void unregisterRemovesProvider() {
      PathRegistry.register(new MaybePathProvider());
      assertThat(PathRegistry.hasProvider(MaybeKind.Witness.class)).isTrue();

      PathRegistry.unregister(MaybeKind.Witness.class);

      assertThat(PathRegistry.hasProvider(MaybeKind.Witness.class)).isFalse();
    }

    @Test
    @DisplayName("getProvider() returns registered provider")
    void getProviderReturnsRegisteredProvider() {
      MaybePathProvider provider = new MaybePathProvider();
      PathRegistry.register(provider);

      Optional<PathProvider<?>> result = PathRegistry.getProvider(MaybeKind.Witness.class);

      assertThat(result).isPresent();
      assertThat(result.get()).isSameAs(provider);
    }

    @Test
    @DisplayName("getProvider() returns empty for unregistered type")
    void getProviderReturnsEmptyForUnregisteredType() {
      Optional<PathProvider<?>> result = PathRegistry.getProvider(MaybeKind.Witness.class);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("allProviders() returns all registered providers")
    void allProvidersReturnsAllRegistered() {
      MaybePathProvider provider = new MaybePathProvider();
      PathRegistry.register(provider);

      assertThat(PathRegistry.allProviders()).contains(provider);
    }

    @Test
    @DisplayName("clear() removes all providers")
    void clearRemovesAllProviders() {
      PathRegistry.register(new MaybePathProvider());
      assertThat(PathRegistry.allProviders()).isNotEmpty();

      PathRegistry.clear();

      assertThat(PathRegistry.hasProvider(MaybeKind.Witness.class)).isFalse();
    }
  }

  @Nested
  @DisplayName("Path Creation")
  class PathCreationTests {

    @Test
    @DisplayName("createPath() creates path from registered provider")
    void createPathCreatesFromProvider() {
      PathRegistry.register(new MaybePathProvider());
      Kind<MaybeKind.Witness, String> kind = MAYBE.just("hello");

      Optional<Chainable<String>> result = PathRegistry.createPath(kind, MaybeKind.Witness.class);

      assertThat(result).isPresent();
      assertThat(result.get()).isInstanceOf(MaybePath.class);
      MaybePath<String> maybePath = (MaybePath<String>) result.get();
      assertThat(maybePath.run().get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("createPath() returns empty for unregistered type")
    void createPathReturnsEmptyForUnregisteredType() {
      Kind<MaybeKind.Witness, String> kind = MAYBE.just("hello");

      Optional<Chainable<String>> result = PathRegistry.createPath(kind, MaybeKind.Witness.class);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Path.from() Factory Method")
  class PathFromFactoryMethodTests {

    @Test
    @DisplayName("Path.from() creates path from registered provider")
    void pathFromCreatesFromProvider() {
      PathRegistry.register(new MaybePathProvider());
      Kind<MaybeKind.Witness, String> kind = MAYBE.just("hello");

      Optional<Chainable<String>> result = Path.from(kind, MaybeKind.Witness.class);

      assertThat(result).isPresent();
      assertThat(result.get()).isInstanceOf(MaybePath.class);
    }

    @Test
    @DisplayName("Path.from() returns empty for unregistered type")
    void pathFromReturnsEmptyForUnregisteredType() {
      Kind<MaybeKind.Witness, String> kind = MAYBE.just("hello");

      Optional<Chainable<String>> result = Path.from(kind, MaybeKind.Witness.class);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Path.from() validates non-null kind")
    void pathFromValidatesNonNullKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.from(null, MaybeKind.Witness.class))
          .withMessageContaining("kind must not be null");
    }

    @Test
    @DisplayName("Path.from() validates non-null witnessType")
    void pathFromValidatesNonNullWitnessType() {
      Kind<MaybeKind.Witness, String> kind = MAYBE.just("hello");

      assertThatNullPointerException()
          .isThrownBy(() -> Path.from(kind, null))
          .withMessageContaining("witnessType must not be null");
    }
  }

  @Nested
  @DisplayName("PathProvider Default Methods")
  class PathProviderDefaultMethodsTests {

    @Test
    @DisplayName("monadError() returns null by default")
    void monadErrorReturnsNullByDefault() {
      MaybePathProvider provider = new MaybePathProvider();

      assertThat(provider.monadError()).isNull();
    }

    @Test
    @DisplayName("supportsRecovery() returns false when monadError() is null")
    void supportsRecoveryReturnsFalseByDefault() {
      MaybePathProvider provider = new MaybePathProvider();

      assertThat(provider.supportsRecovery()).isFalse();
    }

    @Test
    @DisplayName("name() returns witness type simple name with PathProvider suffix")
    void nameReturnsDefaultName() {
      MaybePathProvider provider = new MaybePathProvider();

      assertThat(provider.name()).isEqualTo("WitnessPathProvider");
    }

    @Test
    @DisplayName("supportsRecovery() returns true when monadError() returns non-null")
    void supportsRecoveryReturnsTrueWhenMonadErrorProvided() {
      // Create a provider that overrides monadError() to return a non-null value
      PathProvider<MaybeKind.Witness> providerWithRecovery =
          new PathProvider<>() {
            @Override
            public Class<?> witnessType() {
              return MaybeKind.Witness.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <A> Chainable<A> createPath(Kind<MaybeKind.Witness, A> kind) {
              Maybe<A> maybe = MAYBE.narrow(kind);
              return (Chainable<A>) Path.maybe(maybe);
            }

            @Override
            public Monad<MaybeKind.Witness> monad() {
              return MaybeMonad.INSTANCE;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <E> MonadError<MaybeKind.Witness, E> monadError() {
              // MaybeMonad implements MonadError<MaybeKind.Witness, Unit>
              return (MonadError<MaybeKind.Witness, E>) MaybeMonad.INSTANCE;
            }
          };

      assertThat(providerWithRecovery.supportsRecovery()).isTrue();
    }
  }

  @Nested
  @DisplayName("PathRegistry Reload")
  class PathRegistryReloadTests {

    @Test
    @DisplayName("reload() clears and reloads providers from ServiceLoader")
    void reloadClearsAndReloadsProviders() {
      // Register a provider manually
      PathRegistry.register(new MaybePathProvider());
      assertThat(PathRegistry.hasProvider(MaybeKind.Witness.class)).isTrue();

      // Reload - this clears manually registered providers and loads SPI providers
      PathRegistry.reload();

      // Manual MaybePath provider should be cleared
      assertThat(PathRegistry.hasProvider(MaybeKind.Witness.class)).isFalse();

      // But the SPI-registered OptionalPathProvider should be loaded
      // (from META-INF/services/org.higherkindedj.hkt.effect.spi.PathProvider)
      assertThat(PathRegistry.hasProvider(OptionalKind.Witness.class)).isTrue();
    }

    @Test
    @DisplayName("reload() loads providers from ServiceLoader")
    void reloadLoadsProvidersFromServiceLoader() {
      // Clear to reset state
      PathRegistry.clear();

      // Reload to trigger ServiceLoader discovery
      PathRegistry.reload();

      // Verify the test SPI provider was discovered
      assertThat(PathRegistry.hasProvider(OptionalKind.Witness.class)).isTrue();

      // Verify we can get the provider
      Optional<PathProvider<?>> provider = PathRegistry.getProvider(OptionalKind.Witness.class);
      assertThat(provider).isPresent();
      assertThat(provider.get().witnessType()).isEqualTo(OptionalKind.Witness.class);
    }

    @Test
    @DisplayName("createPath() works with ServiceLoader-discovered provider")
    void createPathWorksWithServiceLoaderProvider() {
      // Clear and reload to use ServiceLoader
      PathRegistry.clear();
      PathRegistry.reload();

      // Create a path using the SPI-discovered provider
      Kind<OptionalKind.Witness, String> kind = OPTIONAL.widen(Optional.of("hello"));
      Optional<Chainable<String>> result =
          PathRegistry.createPath(kind, OptionalKind.Witness.class);

      assertThat(result).isPresent();
      assertThat(result.get()).isInstanceOf(OptionalPath.class);
      OptionalPath<String> optionalPath = (OptionalPath<String>) result.get();
      assertThat(optionalPath.run()).hasValue("hello");
    }
  }

  @Nested
  @DisplayName("ensureLoaded() Thread Safety")
  class EnsureLoadedThreadSafetyTests {

    @Test
    @DisplayName("ensureLoaded() is thread-safe with concurrent access")
    void ensureLoadedIsThreadSafeWithConcurrentAccess() throws Exception {
      // Clear to reset state
      PathRegistry.clear();

      int threadCount = 10;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);

      List<Future<Boolean>> futures = new ArrayList<>();

      // All threads will call hasProvider concurrently, which triggers ensureLoaded()
      for (int i = 0; i < threadCount; i++) {
        futures.add(
            executor.submit(
                () -> {
                  try {
                    startLatch.await(); // Wait for signal to start together
                    // This call internally invokes ensureLoaded()
                    return PathRegistry.hasProvider(OptionalKind.Witness.class);
                  } finally {
                    doneLatch.countDown();
                  }
                }));
      }

      // Start all threads at once
      startLatch.countDown();

      // Wait for all to complete
      boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
      assertThat(completed).isTrue();

      // All threads should get the same result - OptionalPathProvider is registered via SPI
      for (Future<Boolean> future : futures) {
        assertThat(future.get()).isTrue();
      }

      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("ensureLoaded() loads providers only once")
    void ensureLoadedLoadsProvidersOnlyOnce() throws Exception {
      // Clear to reset state
      PathRegistry.clear();

      int threadCount = 5;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);
      AtomicInteger successCount = new AtomicInteger(0);

      // Multiple threads accessing registry concurrently
      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                // Multiple calls to methods that invoke ensureLoaded()
                PathRegistry.hasProvider(OptionalKind.Witness.class);
                PathRegistry.getProvider(OptionalKind.Witness.class);
                PathRegistry.allProviders();
                successCount.incrementAndGet();
              } catch (Exception e) {
                // Ignore - just track success
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown();
      boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
      assertThat(completed).isTrue();

      // All threads should complete successfully without exceptions
      assertThat(successCount.get()).isEqualTo(threadCount);

      // Verify provider is loaded correctly
      assertThat(PathRegistry.hasProvider(OptionalKind.Witness.class)).isTrue();

      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("concurrent createPath calls work correctly")
    void concurrentCreatePathCallsWorkCorrectly() throws Exception {
      // Clear and reload to ensure SPI providers are loaded
      PathRegistry.clear();
      PathRegistry.reload();

      int threadCount = 10;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);

      List<Future<Optional<Chainable<String>>>> futures = new ArrayList<>();
      Kind<OptionalKind.Witness, String> kind = OPTIONAL.widen(Optional.of("test"));

      for (int i = 0; i < threadCount; i++) {
        futures.add(
            executor.submit(
                () -> {
                  try {
                    startLatch.await();
                    return PathRegistry.createPath(kind, OptionalKind.Witness.class);
                  } finally {
                    doneLatch.countDown();
                  }
                }));
      }

      startLatch.countDown();
      boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
      assertThat(completed).isTrue();

      // All threads should successfully create paths
      for (Future<Optional<Chainable<String>>> future : futures) {
        Optional<Chainable<String>> result = future.get();
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(OptionalPath.class);
      }

      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("double-checked locking prevents race conditions after clear()")
    void doubleCheckedLockingPreventsRaceConditionsAfterClear() throws Exception {
      int iterations = 5;

      for (int iteration = 0; iteration < iterations; iteration++) {
        PathRegistry.clear();

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger loadedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  if (PathRegistry.hasProvider(OptionalKind.Witness.class)) {
                    loadedCount.incrementAndGet();
                  }
                } catch (Exception e) {
                  // Ignore
                } finally {
                  doneLatch.countDown();
                }
              });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        // All threads should see the provider as loaded
        assertThat(loadedCount.get()).isEqualTo(threadCount);

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
      }
    }
  }
}
