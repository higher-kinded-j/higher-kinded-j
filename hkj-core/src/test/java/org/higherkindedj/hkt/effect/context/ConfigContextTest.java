// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.io.IOKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for ConfigContext.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Factory methods: io(), ioDeferred(), ask(), pure()
 *   <li>Chainable operations: map(), via(), flatMap(), then()
 *   <li>Config-specific operations: contramap(), local()
 *   <li>Execution methods: runWith(), runWithSync()
 *   <li>Escape hatch: toReaderT()
 * </ul>
 */
@DisplayName("ConfigContext")
class ConfigContextTest {

  record AppConfig(String apiUrl, int timeout) {}

  private static final AppConfig TEST_CONFIG = new AppConfig("https://api.example.com", 30);
  private static final Integer TEST_VALUE = 42;

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("io() uses config in computation")
    void ioUsesConfig() {
      ConfigContext<IOKind.Witness, AppConfig, String> ctx =
          ConfigContext.io(config -> config.apiUrl());

      String result = ctx.runWithSync(TEST_CONFIG);
      assertThat(result).isEqualTo("https://api.example.com");
    }

    @Test
    @DisplayName("io() validates non-null computation")
    void ioValidatesComputation() {
      assertThatNullPointerException()
          .isThrownBy(() -> ConfigContext.io(null))
          .withMessageContaining("computation must not be null");
    }

    @Test
    @DisplayName("ioDeferred() defers computation until run")
    void ioDeferredDefersComputation() {
      AtomicBoolean called = new AtomicBoolean(false);

      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx =
          ConfigContext.ioDeferred(
              config ->
                  () -> {
                    called.set(true);
                    return config.timeout();
                  });

      assertThat(called.get()).isFalse();
      ctx.runWithSync(TEST_CONFIG);
      assertThat(called.get()).isTrue();
    }

    @Test
    @DisplayName("ask() returns the config itself")
    void askReturnsConfig() {
      ConfigContext<IOKind.Witness, AppConfig, AppConfig> ctx = ConfigContext.ask();

      AppConfig result = ctx.runWithSync(TEST_CONFIG);
      assertThat(result).isEqualTo(TEST_CONFIG);
    }

    @Test
    @DisplayName("pure() ignores config and returns value")
    void pureIgnoresConfig() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      Integer result = ctx.runWithSync(TEST_CONFIG);
      assertThat(result).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Chainable Operations")
  class ChainableOperationsTests {

    @Test
    @DisplayName("map() transforms value")
    void mapTransformsValue() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx =
          ConfigContext.<AppConfig, Integer>pure(21).map(x -> x * 2);

      Integer result = ctx.runWithSync(TEST_CONFIG);
      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("map() validates non-null mapper")
    void mapValidatesMapper() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() chains contexts with config access")
    void viaChainsContexts() {
      ConfigContext<IOKind.Witness, AppConfig, String> result =
          ConfigContext.<AppConfig>ask()
              .via(config -> ConfigContext.pure(config.apiUrl()))
              .via(
                  url ->
                      ConfigContext.<AppConfig, String>io(config -> url + ":" + config.timeout()));

      String value = result.runWithSync(TEST_CONFIG);
      assertThat(value).isEqualTo("https://api.example.com:30");
    }

    @Test
    @DisplayName("via() validates non-null function")
    void viaValidatesFunction() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.via(null))
          .withMessageContaining("fn must not be null");
    }

    @Test
    @DisplayName("via() throws when function returns wrong context type")
    void viaThrowsOnWrongContextType() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(
              () -> ctx.via(x -> ErrorContext.success(x.toString())).runWithSync(TEST_CONFIG))
          .withMessageContaining("via function must return a ConfigContext");
    }

    @Test
    @DisplayName("flatMap() chains contexts")
    void flatMapChainsContexts() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> result =
          ConfigContext.<AppConfig, Integer>pure(10).flatMap(x -> ConfigContext.pure(x * 2));

      Integer value = result.runWithSync(TEST_CONFIG);
      assertThat(value).isEqualTo(20);
    }

    @Test
    @DisplayName("then() sequences independent computation")
    void thenSequences() {
      AtomicBoolean firstCalled = new AtomicBoolean(false);
      AtomicBoolean secondCalled = new AtomicBoolean(false);

      ConfigContext<IOKind.Witness, AppConfig, String> result =
          ConfigContext.<AppConfig, Integer>ioDeferred(
                  config ->
                      () -> {
                        firstCalled.set(true);
                        return 1;
                      })
              .then(
                  () ->
                      ConfigContext.ioDeferred(
                          config ->
                              () -> {
                                secondCalled.set(true);
                                return "done";
                              }));

      // Before running, neither should be called
      assertThat(firstCalled.get()).isFalse();
      assertThat(secondCalled.get()).isFalse();

      // After running
      result.runWithSync(TEST_CONFIG);
      assertThat(firstCalled.get()).isTrue();
      assertThat(secondCalled.get()).isTrue();
    }
  }

  @Nested
  @DisplayName("Config-specific Operations")
  class ConfigSpecificOperationsTests {

    record BaseConfig(String host) {}

    @Test
    @DisplayName("contramap() adapts to different config type")
    void contramapAdaptsConfig() {
      ConfigContext<IOKind.Witness, AppConfig, String> original =
          ConfigContext.io(config -> config.apiUrl());

      ConfigContext<IOKind.Witness, BaseConfig, String> adapted =
          original.contramap(base -> new AppConfig(base.host(), 60));

      String result = adapted.runWithSync(new BaseConfig("https://adapted.com"));
      assertThat(result).isEqualTo("https://adapted.com");
    }

    @Test
    @DisplayName("contramap() validates non-null function")
    void contramapValidatesFunction() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.contramap(null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("local() modifies config for computation")
    void localModifiesConfig() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx =
          ConfigContext.<AppConfig, Integer>io(config -> config.timeout())
              .local(config -> new AppConfig(config.apiUrl(), config.timeout() * 2));

      Integer result = ctx.runWithSync(TEST_CONFIG);
      assertThat(result).isEqualTo(60); // 30 * 2
    }

    @Test
    @DisplayName("local() validates non-null modifier")
    void localValidatesModifier() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.local(null))
          .withMessageContaining("modifier must not be null");
    }
  }

  @Nested
  @DisplayName("Execution Methods")
  class ExecutionMethodsTests {

    @Test
    @DisplayName("runWith() returns IOPath")
    void runWithReturnsIOPath() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      var ioPath = ctx.runWith(TEST_CONFIG);

      assertThat(ioPath).isNotNull();
      assertThat(ioPath.unsafeRun()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runWithSync() runs synchronously")
    void runWithSyncRunsSynchronously() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx =
          ConfigContext.io(config -> config.timeout());

      Integer result = ctx.runWithSync(TEST_CONFIG);
      assertThat(result).isEqualTo(30);
    }
  }

  @Nested
  @DisplayName("Escape Hatch")
  class EscapeHatchTests {

    @Test
    @DisplayName("toReaderT() returns underlying transformer")
    void toReaderTReturnsTransformer() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      var readerT = ctx.toReaderT();

      assertThat(readerT).isNotNull();
    }

    @Test
    @DisplayName("underlying() returns Kind")
    void underlyingReturnsKind() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      var underlying = ctx.underlying();

      assertThat(underlying).isNotNull();
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() contains ConfigContext")
    void toStringContainsClassName() {
      ConfigContext<IOKind.Witness, AppConfig, Integer> ctx = ConfigContext.pure(TEST_VALUE);

      assertThat(ctx.toString()).contains("ConfigContext");
    }
  }
}
