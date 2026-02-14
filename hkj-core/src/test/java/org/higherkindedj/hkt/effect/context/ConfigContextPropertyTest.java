// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.io.IOKind;

/**
 * Property-based tests for ConfigContext using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs.
 */
class ConfigContextPropertyTest {

  record TestConfig(int value, String name) {}

  @Provide
  Arbitrary<ConfigContext<IOKind.Witness, TestConfig, Integer>> configContexts() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .map(ConfigContext::<TestConfig, Integer>pure);
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(i -> "value:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(String::length, String::hashCode, s -> s.isEmpty() ? 0 : 1);
  }

  @Provide
  Arbitrary<Function<Integer, ConfigContext<IOKind.Witness, TestConfig, String>>>
      intToConfigContextStringFunctions() {
    return Arbitraries.of(
        i -> ConfigContext.pure("even:" + i),
        i -> ConfigContext.io(config -> "value:" + i + ",config:" + config.value()),
        i -> ConfigContext.pure("transformed:" + i));
  }

  @Provide
  Arbitrary<Function<String, ConfigContext<IOKind.Witness, TestConfig, String>>>
      stringToConfigContextStringFunctions() {
    return Arbitraries.of(
        s -> ConfigContext.pure(s.toUpperCase()),
        s -> ConfigContext.io(config -> s + ":" + config.name()),
        s -> ConfigContext.pure("result:" + s));
  }

  private static final TestConfig TEST_CONFIG = new TestConfig(42, "test");

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: ctx.map(id) == ctx")
  void functorIdentityLaw(
      @ForAll("configContexts") ConfigContext<IOKind.Witness, TestConfig, Integer> ctx) {
    ConfigContext<IOKind.Witness, TestConfig, Integer> result = ctx.map(Function.identity());
    assertThat(result.runWithSync(TEST_CONFIG)).isEqualTo(ctx.runWithSync(TEST_CONFIG));
  }

  @Property
  @Label("Functor Composition Law: ctx.map(f).map(g) == ctx.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("configContexts") ConfigContext<IOKind.Witness, TestConfig, Integer> ctx,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    ConfigContext<IOKind.Witness, TestConfig, Integer> leftSide = ctx.map(f).map(g);
    ConfigContext<IOKind.Witness, TestConfig, Integer> rightSide = ctx.map(f.andThen(g));

    assertThat(leftSide.runWithSync(TEST_CONFIG)).isEqualTo(rightSide.runWithSync(TEST_CONFIG));
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: ConfigContext.pure(a).flatMap(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToConfigContextStringFunctions")
          Function<Integer, ConfigContext<IOKind.Witness, TestConfig, String>> f) {

    ConfigContext<IOKind.Witness, TestConfig, String> leftSide =
        ConfigContext.<TestConfig, Integer>pure(value).flatMap(f);
    ConfigContext<IOKind.Witness, TestConfig, String> rightSide = f.apply(value);

    assertThat(leftSide.runWithSync(TEST_CONFIG)).isEqualTo(rightSide.runWithSync(TEST_CONFIG));
  }

  @Property
  @Label("Monad Right Identity Law: ctx.flatMap(ConfigContext::pure) == ctx")
  void rightIdentityLaw(
      @ForAll("configContexts") ConfigContext<IOKind.Witness, TestConfig, Integer> ctx) {
    ConfigContext<IOKind.Witness, TestConfig, Integer> result = ctx.flatMap(ConfigContext::pure);
    assertThat(result.runWithSync(TEST_CONFIG)).isEqualTo(ctx.runWithSync(TEST_CONFIG));
  }

  @Property
  @Label("Monad Associativity Law: ctx.flatMap(f).flatMap(g) == ctx.flatMap(x -> f(x).flatMap(g))")
  void associativityLaw(
      @ForAll("configContexts") ConfigContext<IOKind.Witness, TestConfig, Integer> ctx,
      @ForAll("intToConfigContextStringFunctions")
          Function<Integer, ConfigContext<IOKind.Witness, TestConfig, String>> f,
      @ForAll("stringToConfigContextStringFunctions")
          Function<String, ConfigContext<IOKind.Witness, TestConfig, String>> g) {

    ConfigContext<IOKind.Witness, TestConfig, String> leftSide = ctx.flatMap(f).flatMap(g);
    ConfigContext<IOKind.Witness, TestConfig, String> rightSide =
        ctx.flatMap(x -> f.apply(x).flatMap(g));

    assertThat(leftSide.runWithSync(TEST_CONFIG)).isEqualTo(rightSide.runWithSync(TEST_CONFIG));
  }

  // ===== Reader-specific Properties =====

  @Property
  @Label("ask() returns the configuration")
  void askReturnsConfig() {
    ConfigContext<IOKind.Witness, TestConfig, TestConfig> ctx = ConfigContext.ask();
    assertThat(ctx.runWithSync(TEST_CONFIG)).isEqualTo(TEST_CONFIG);
  }

  @Property
  @Label("pure() ignores configuration")
  void pureIgnoresConfig(@ForAll @IntRange(min = -100, max = 100) int value) {
    ConfigContext<IOKind.Witness, TestConfig, Integer> ctx = ConfigContext.pure(value);

    TestConfig config1 = new TestConfig(1, "a");
    TestConfig config2 = new TestConfig(2, "b");

    assertThat(ctx.runWithSync(config1)).isEqualTo(ctx.runWithSync(config2));
  }

  @Property
  @Label("io() uses configuration in computation")
  void ioUsesConfig(@ForAll @IntRange(min = 1, max = 100) int configValue) {
    TestConfig config = new TestConfig(configValue, "test");
    ConfigContext<IOKind.Witness, TestConfig, Integer> ctx = ConfigContext.io(c -> c.value() * 2);

    assertThat(ctx.runWithSync(config)).isEqualTo(configValue * 2);
  }

  @Property
  @Label("local() modifies configuration for computation")
  void localModifiesConfig(@ForAll @IntRange(min = 1, max = 100) int originalValue) {
    TestConfig config = new TestConfig(originalValue, "test");
    ConfigContext<IOKind.Witness, TestConfig, Integer> ctx =
        ConfigContext.<TestConfig, Integer>io(c -> c.value())
            .local(c -> new TestConfig(c.value() * 3, c.name()));

    assertThat(ctx.runWithSync(config)).isEqualTo(originalValue * 3);
  }

  @Property
  @Label("contramap() adapts to different configuration type")
  void contramapAdaptsConfig(@ForAll @IntRange(min = 1, max = 100) int value) {
    record SimpleConfig(int num) {}

    ConfigContext<IOKind.Witness, TestConfig, Integer> original =
        ConfigContext.<TestConfig, Integer>io(c -> c.value());
    ConfigContext<IOKind.Witness, SimpleConfig, Integer> adapted =
        original.contramap(s -> new TestConfig(s.num(), "adapted"));

    assertThat(adapted.runWithSync(new SimpleConfig(value))).isEqualTo(value);
  }

  @Property
  @Label("via and flatMap are equivalent")
  void viaAndFlatMapEquivalent(
      @ForAll("configContexts") ConfigContext<IOKind.Witness, TestConfig, Integer> ctx,
      @ForAll("intToConfigContextStringFunctions")
          Function<Integer, ConfigContext<IOKind.Witness, TestConfig, String>> f) {

    String viaResult = ctx.via(f).runWithSync(TEST_CONFIG);
    String flatMapResult = ctx.flatMap(f).runWithSync(TEST_CONFIG);

    assertThat(viaResult).isEqualTo(flatMapResult);
  }
}
