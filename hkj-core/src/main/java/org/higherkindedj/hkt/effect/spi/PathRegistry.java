// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.capability.Chainable;

/**
 * Central registry for PathProvider instances.
 *
 * <p>Provides automatic discovery and lookup of PathProviders via ServiceLoader, enabling {@code
 * Path.from(kind)} to work with any registered effect type.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Automatic path creation from any registered type
 * Kind<ApiResultKind.Witness, User> kind = apiService.getUser(id);
 * Optional<Chainable<User>> path = PathRegistry.createPath(kind, ApiResultKind.Witness.class);
 *
 * // Or with the Path factory
 * Chainable<User> path = Path.from(kind, ApiResultKind.Witness.class);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is thread-safe. Providers are loaded lazily on first access.
 */
public final class PathRegistry {

  private static final Map<Class<?>, PathProvider<?>> providers = new ConcurrentHashMap<>();
  private static volatile boolean loaded = false;

  private PathRegistry() {
    // Utility class - no instantiation
  }

  /**
   * Creates a Path from a Kind value using the appropriate registered provider.
   *
   * @param value the Kind value to wrap; must not be null
   * @param witnessType the witness type class
   * @param <F> the witness type
   * @param <A> the value type
   * @return an Optional containing the path if a provider is found
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> Optional<Chainable<A>> createPath(
      Kind<F, A> value, Class<?> witnessType) {
    ensureLoaded();
    @SuppressWarnings("unchecked")
    PathProvider<F> provider = (PathProvider<F>) providers.get(witnessType);
    if (provider == null) {
      return Optional.empty();
    }
    return Optional.of(provider.createPath(value));
  }

  /**
   * Returns the provider for a given witness type, if registered.
   *
   * @param witnessType the witness type class
   * @return an Optional containing the provider if found
   */
  public static Optional<PathProvider<?>> getProvider(Class<?> witnessType) {
    ensureLoaded();
    return Optional.ofNullable(providers.get(witnessType));
  }

  /**
   * Registers a provider manually.
   *
   * <p>Useful for testing or when ServiceLoader is not available.
   *
   * @param provider the provider to register; must not be null
   */
  public static void register(PathProvider<?> provider) {
    if (provider == null) {
      throw new NullPointerException("provider must not be null");
    }
    providers.put(provider.witnessType(), provider);
  }

  /**
   * Unregisters a provider for a given witness type.
   *
   * <p>Useful for testing.
   *
   * @param witnessType the witness type class to unregister
   * @return the previously registered provider, or null if none
   */
  public static PathProvider<?> unregister(Class<?> witnessType) {
    return providers.remove(witnessType);
  }

  /**
   * Returns all registered providers.
   *
   * @return an unmodifiable collection of all providers
   */
  public static Collection<PathProvider<?>> allProviders() {
    ensureLoaded();
    return Collections.unmodifiableCollection(providers.values());
  }

  /**
   * Returns whether a provider is registered for the given witness type.
   *
   * @param witnessType the witness type class
   * @return true if a provider is registered
   */
  public static boolean hasProvider(Class<?> witnessType) {
    ensureLoaded();
    return providers.containsKey(witnessType);
  }

  /**
   * Clears all registered providers.
   *
   * <p>Useful for testing to reset state between tests.
   */
  public static void clear() {
    providers.clear();
    loaded = false;
  }

  /**
   * Forces reloading of providers from ServiceLoader.
   *
   * <p>This is useful if new providers have been added to the classpath.
   */
  public static void reload() {
    providers.clear();
    loaded = false;
    ensureLoaded();
  }

  private static void ensureLoaded() {
    if (!loaded) {
      synchronized (PathRegistry.class) {
        if (!loaded) {
          loadProviders();
          loaded = true;
        }
      }
    }
  }

  @SuppressWarnings("rawtypes")
  private static void loadProviders() {
    ServiceLoader<PathProvider> loader = ServiceLoader.load(PathProvider.class);
    for (PathProvider<?> provider : loader) {
      providers.put(provider.witnessType(), provider);
    }
  }
}
