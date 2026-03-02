// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.DefaultVStreamPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VStreamPathProvider;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.spi.PathProvider;
import org.higherkindedj.hkt.effect.spi.PathRegistry;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VStreamPathProvider} SPI registration and path creation.
 *
 * <p>Verifies that VStream is correctly discoverable via ServiceLoader and produces valid
 * VStreamPath instances through the PathRegistry.
 */
@DisplayName("VStreamPathProvider Test Suite")
class VStreamPathProviderTest {

  @BeforeEach
  void setUp() {
    PathRegistry.clear();
  }

  @AfterEach
  void tearDown() {
    PathRegistry.clear();
  }

  @Nested
  @DisplayName("Provider Properties")
  class ProviderPropertiesTests {

    @Test
    @DisplayName("witnessType() returns VStreamKind.Witness.class")
    void witnessTypeReturnsCorrectClass() {
      VStreamPathProvider provider = new VStreamPathProvider();

      assertThat(provider.witnessType()).isEqualTo(VStreamKind.Witness.class);
    }

    @Test
    @DisplayName("name() returns 'VStream'")
    void nameReturnsVStream() {
      VStreamPathProvider provider = new VStreamPathProvider();

      assertThat(provider.name()).isEqualTo("VStream");
    }

    @Test
    @DisplayName("monad() returns VStreamMonad.INSTANCE")
    void monadReturnsVStreamMonadInstance() {
      VStreamPathProvider provider = new VStreamPathProvider();

      assertThat(provider.monad()).isSameAs(VStreamMonad.INSTANCE);
    }

    @Test
    @DisplayName("monadError() returns null (default)")
    void monadErrorReturnsNull() {
      VStreamPathProvider provider = new VStreamPathProvider();

      assertThat(provider.monadError()).isNull();
    }

    @Test
    @DisplayName("supportsRecovery() returns false")
    void supportsRecoveryReturnsFalse() {
      VStreamPathProvider provider = new VStreamPathProvider();

      assertThat(provider.supportsRecovery()).isFalse();
    }
  }

  @Nested
  @DisplayName("Path Creation")
  class PathCreationTests {

    @Test
    @DisplayName("createPath() produces DefaultVStreamPath")
    void createPathProducesVStreamPath() {
      VStreamPathProvider provider = new VStreamPathProvider();
      VStream<String> stream = VStream.of("a", "b", "c");
      Kind<VStreamKind.Witness, String> kind = VSTREAM.widen(stream);

      Chainable<String> result = provider.createPath(kind);

      assertThat(result).isInstanceOf(DefaultVStreamPath.class);
    }

    @Test
    @DisplayName("createPath() preserves stream elements")
    void createPathPreservesElements() {
      VStreamPathProvider provider = new VStreamPathProvider();
      VStream<Integer> stream = VStream.of(1, 2, 3);
      Kind<VStreamKind.Witness, Integer> kind = VSTREAM.widen(stream);

      Chainable<Integer> result = provider.createPath(kind);
      VStreamPath<Integer> vstreamPath = (VStreamPath<Integer>) result;

      assertThat(vstreamPath.run().toList().run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("createPath() handles empty stream")
    void createPathHandlesEmptyStream() {
      VStreamPathProvider provider = new VStreamPathProvider();
      VStream<String> stream = VStream.empty();
      Kind<VStreamKind.Witness, String> kind = VSTREAM.widen(stream);

      Chainable<String> result = provider.createPath(kind);
      VStreamPath<String> vstreamPath = (VStreamPath<String>) result;

      assertThat(vstreamPath.run().toList().run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Manual Registration")
  class ManualRegistrationTests {

    @Test
    @DisplayName("register() makes provider discoverable via hasProvider()")
    void registerMakesProviderDiscoverable() {
      PathRegistry.register(new VStreamPathProvider());

      assertThat(PathRegistry.hasProvider(VStreamKind.Witness.class)).isTrue();
    }

    @Test
    @DisplayName("getProvider() returns the registered provider")
    void getProviderReturnsRegisteredProvider() {
      VStreamPathProvider provider = new VStreamPathProvider();
      PathRegistry.register(provider);

      Optional<PathProvider<?>> result = PathRegistry.getProvider(VStreamKind.Witness.class);

      assertThat(result).isPresent();
      assertThat(result.get()).isSameAs(provider);
    }

    @Test
    @DisplayName("createPath() via registry produces VStreamPath")
    void createPathViaRegistryProducesVStreamPath() {
      PathRegistry.register(new VStreamPathProvider());
      VStream<String> stream = VStream.of("hello", "world");
      Kind<VStreamKind.Witness, String> kind = VSTREAM.widen(stream);

      Optional<Chainable<String>> result = PathRegistry.createPath(kind, VStreamKind.Witness.class);

      assertThat(result).isPresent();
      assertThat(result.get()).isInstanceOf(DefaultVStreamPath.class);
    }
  }

  @Nested
  @DisplayName("ServiceLoader Discovery")
  class ServiceLoaderDiscoveryTests {

    @Test
    @DisplayName("reload() discovers VStreamPathProvider via ServiceLoader")
    void reloadDiscoversVStreamPathProvider() {
      PathRegistry.reload();

      assertThat(PathRegistry.hasProvider(VStreamKind.Witness.class)).isTrue();
    }

    @Test
    @DisplayName("ServiceLoader-discovered provider has correct name")
    void serviceLoaderProviderHasCorrectName() {
      PathRegistry.reload();

      Optional<PathProvider<?>> provider = PathRegistry.getProvider(VStreamKind.Witness.class);

      assertThat(provider).isPresent();
      assertThat(provider.get().name()).isEqualTo("VStream");
    }

    @Test
    @DisplayName("createPath() works with ServiceLoader-discovered provider")
    void createPathWorksWithServiceLoaderProvider() {
      PathRegistry.reload();

      VStream<String> stream = VStream.of("x", "y", "z");
      Kind<VStreamKind.Witness, String> kind = VSTREAM.widen(stream);

      Optional<Chainable<String>> result = PathRegistry.createPath(kind, VStreamKind.Witness.class);

      assertThat(result).isPresent();
      VStreamPath<String> vstreamPath = (VStreamPath<String>) result.get();
      assertThat(vstreamPath.run().toList().run()).containsExactly("x", "y", "z");
    }
  }

  @Nested
  @DisplayName("Path.from() Integration")
  class PathFromIntegrationTests {

    @Test
    @DisplayName("Path.from() produces Chainable for VStream kind")
    void pathFromProducesChainableForVStreamKind() {
      PathRegistry.register(new VStreamPathProvider());
      VStream<String> stream = VStream.of("a", "b");
      Kind<VStreamKind.Witness, String> kind = VSTREAM.widen(stream);

      Optional<Chainable<String>> result = Path.from(kind, VStreamKind.Witness.class);

      assertThat(result).isPresent();
      assertThat(result.get()).isInstanceOf(DefaultVStreamPath.class);
    }

    @Test
    @DisplayName("Path.from() returns empty when provider not registered")
    void pathFromReturnsEmptyWhenNotRegistered() {
      // Use MaybeKind.Witness which has no ServiceLoader provider
      Kind<MaybeKind.Witness, String> kind = MAYBE.widen(Maybe.just("a"));

      Optional<Chainable<String>> result = Path.from(kind, MaybeKind.Witness.class);

      assertThat(result).isEmpty();
    }
  }
}
