// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for ReaderPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. ReaderPath represents
 * computations that depend on an environment, so tests verify behavior with different environments.
 */
@Label("ReaderPath Property-Based Tests")
class ReaderPathPropertyTest {

  // Test environment record
  record Config(String host, int port, boolean debug) {}

  private static final Config TEST_CONFIG = new Config("localhost", 8080, true);
  private static final Config ALT_CONFIG = new Config("remote", 443, false);

  @Provide
  Arbitrary<ReaderPath<Config, Integer>> readerPaths() {
    return Arbitraries.oneOf(
        // Pure values
        Arbitraries.integers().between(-1000, 1000).map(ReaderPath::pure),
        // Environment-dependent values
        Arbitraries.just(ReaderPath.asks(Config::port)),
        Arbitraries.just(ReaderPath.asks(c -> c.host().length())),
        Arbitraries.just(ReaderPath.asks(c -> c.debug() ? 1 : 0)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(
        i -> "value:" + i, i -> String.valueOf(i * 2), i -> "n" + i, Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(String::length, String::hashCode, s -> s.isEmpty() ? 0 : 1);
  }

  @Provide
  Arbitrary<Function<Integer, ReaderPath<Config, String>>> intToReaderStringFunctions() {
    return Arbitraries.of(
        i -> ReaderPath.asks(c -> c.host() + ":" + i),
        i -> ReaderPath.pure("value:" + i),
        i -> ReaderPath.asks(c -> c.debug() ? "debug:" + i : "prod:" + i));
  }

  @Provide
  Arbitrary<Function<String, ReaderPath<Config, String>>> stringToReaderStringFunctions() {
    return Arbitraries.of(
        s -> ReaderPath.pure(s.toUpperCase()),
        s -> ReaderPath.asks(c -> s + "@" + c.host()),
        s -> ReaderPath.asks(c -> c.debug() ? s + "!" : s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("readerPaths") ReaderPath<Config, Integer> path) {
    ReaderPath<Config, Integer> result = path.map(Function.identity());
    assertThat(result.run(TEST_CONFIG)).isEqualTo(path.run(TEST_CONFIG));
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("readerPaths") ReaderPath<Config, Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    ReaderPath<Config, Integer> leftSide = path.map(f).map(g);
    ReaderPath<Config, Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: ReaderPath.pure(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToReaderStringFunctions") Function<Integer, ReaderPath<Config, String>> f) {

    ReaderPath<Config, String> leftSide = ReaderPath.<Config, Integer>pure(value).via(f);
    ReaderPath<Config, String> rightSide = f.apply(value);

    assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
  }

  @Property
  @Label("Monad Right Identity Law: path.via(ReaderPath::pure) == path")
  void rightIdentityLaw(@ForAll("readerPaths") ReaderPath<Config, Integer> path) {
    ReaderPath<Config, Integer> result = path.via(ReaderPath::pure);
    assertThat(result.run(TEST_CONFIG)).isEqualTo(path.run(TEST_CONFIG));
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("readerPaths") ReaderPath<Config, Integer> path,
      @ForAll("intToReaderStringFunctions") Function<Integer, ReaderPath<Config, String>> f,
      @ForAll("stringToReaderStringFunctions") Function<String, ReaderPath<Config, String>> g) {

    ReaderPath<Config, String> leftSide = path.via(f).via(g);
    ReaderPath<Config, String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
  }

  // ===== Derived Properties =====

  @Property
  @Label("pure ignores environment")
  void pureIgnoresEnvironment(@ForAll @IntRange(min = -100, max = 100) int value) {
    ReaderPath<Config, Integer> path = ReaderPath.pure(value);
    assertThat(path.run(TEST_CONFIG)).isEqualTo(value);
    assertThat(path.run(ALT_CONFIG)).isEqualTo(value);
  }

  @Property
  @Label("ask returns the environment")
  void askReturnsEnvironment() {
    ReaderPath<Config, Config> path = ReaderPath.ask();
    assertThat(path.run(TEST_CONFIG)).isEqualTo(TEST_CONFIG);
    assertThat(path.run(ALT_CONFIG)).isEqualTo(ALT_CONFIG);
  }

  @Property
  @Label("asks extracts from environment")
  void asksExtractsFromEnvironment() {
    ReaderPath<Config, String> path = ReaderPath.asks(Config::host);
    assertThat(path.run(TEST_CONFIG)).isEqualTo("localhost");
    assertThat(path.run(ALT_CONFIG)).isEqualTo("remote");
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("readerPaths") ReaderPath<Config, Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    ReaderPath<Config, Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    ReaderPath<Config, Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.run(TEST_CONFIG)).isEqualTo(composed.run(TEST_CONFIG));
  }

  @Property
  @Label("local modifies environment for computation")
  void localModifiesEnvironment() {
    ReaderPath<Config, Integer> path = ReaderPath.asks(Config::port);
    ReaderPath<Config, Integer> modified =
        path.local(c -> new Config(c.host(), c.port() + 100, c.debug()));

    assertThat(path.run(TEST_CONFIG)).isEqualTo(8080);
    assertThat(modified.run(TEST_CONFIG)).isEqualTo(8180);
  }
}
