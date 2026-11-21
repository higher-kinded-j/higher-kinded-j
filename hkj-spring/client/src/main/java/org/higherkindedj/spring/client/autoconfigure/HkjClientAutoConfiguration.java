// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.autoconfigure;

import java.util.Map;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.spring.client.JsonResponseErrorDecoderFactory;
import org.higherkindedj.spring.client.ResponseErrorDecoderFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Auto-configuration for the Higher-Kinded-J Spring Boot client.
 *
 * <p>Activates when a Jackson {@link JsonMapper} and Spring's {@link RestClient} are on the
 * classpath. It contributes a default {@link ResponseErrorDecoderFactory} backed by the
 * application's {@code JsonMapper} (which carries {@code HkjJacksonModule} when the server-side
 * auto-configuration is present), so generated clients can build a typed-error decoder per method.
 */
@AutoConfiguration
@ConditionalOnClass({JsonMapper.class, RestClient.class})
@EnableConfigurationProperties(HkjClientProperties.class)
public class HkjClientAutoConfiguration implements BeanClassLoaderAware {

  /** Creates the auto-configuration; Spring Boot instantiates it. */
  public HkjClientAutoConfiguration() {}

  private @Nullable ClassLoader beanClassLoader;

  @Override
  public void setBeanClassLoader(ClassLoader classLoader) {
    this.beanClassLoader = classLoader;
  }

  /**
   * Default decoder factory backed by the application's Jackson mapper, applying any global {@code
   * hkj.client.status-error-mappings}. Replaceable by defining your own {@link
   * ResponseErrorDecoderFactory} bean.
   *
   * @param jsonMapper the application's Jackson 3.x mapper
   * @param properties the client configuration
   * @return the default decoder factory
   */
  @Bean
  @ConditionalOnMissingBean
  public ResponseErrorDecoderFactory hkjResponseErrorDecoderFactory(
      JsonMapper jsonMapper, HkjClientProperties properties) {
    return new JsonResponseErrorDecoderFactory(jsonMapper, resolveStatusErrorTypes(properties));
  }

  private Map<Integer, Class<?>> resolveStatusErrorTypes(HkjClientProperties properties) {
    return properties.getStatusErrorMappings().entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> resolveErrorType(entry.getKey(), entry.getValue())));
  }

  /**
   * Resolves a single configured error class, failing fast with context if it cannot be loaded, or
   * if it is {@code Void} or {@code void}, which no decoded error can be. Resolution uses the
   * application's bean classloader ({@link BeanClassLoaderAware}), not this auto-config's loader,
   * so user error types load under devtools/layered loaders.
   */
  private Class<?> resolveErrorType(int status, String className) {
    return Try.<Class<?>, ClassNotFoundException>attempt(
            () -> ClassUtils.forName(className, this.beanClassLoader))
        .fold(
            failure -> {
              throw new IllegalStateException(
                  "hkj.client.status-error-mappings: cannot resolve error type '"
                      + className
                      + "' for status "
                      + status,
                  failure);
            },
            type ->
                requireNonPrimitive(status, className, requireNonVoid(status, className, type)));
  }

  /**
   * Refuses a {@code Void} or {@code void} mapping here rather than in the factory, so the message
   * names the property the value came from.
   */
  private static Class<?> requireNonVoid(int status, String className, Class<?> type) {
    if (type == Void.class || type == void.class) {
      throw new IllegalStateException(
          "hkj.client.status-error-mappings: error type '"
              + className
              + "' for status "
              + status
              + " cannot hold a value, but a Left always holds one. Map the status to"
              + " org.higherkindedj.hkt.Unit, the error type that carries nothing, or remove the"
              + " mapping.");
    }
    return type;
  }

  /**
   * Refuses a primitive such as {@code int}, which {@code ClassUtils.forName} loads but no error
   * can be, so the mapping would otherwise apply to no method.
   */
  private static Class<?> requireNonPrimitive(int status, String className, Class<?> type) {
    if (type.isPrimitive()) {
      throw new IllegalStateException(
          "hkj.client.status-error-mappings: error type '"
              + className
              + "' for status "
              + status
              + " is a primitive, which no error can be. Map the status to an error class, or"
              + " remove the mapping.");
    }
    return type;
  }
}
