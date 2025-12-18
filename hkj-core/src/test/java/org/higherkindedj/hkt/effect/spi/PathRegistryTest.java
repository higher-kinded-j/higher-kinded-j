// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.spi;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
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
}
