// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Dynamic test factory for the applicative laws of {@link FetchApplicative}.
 *
 * <p>Follows the project's {@code @TestFactory}/{@link DynamicTest} law-testing idiom (cf. {@code
 * FunctorLawsTestFactory}, {@code MonadLawsTestFactory}). Each law is checked across several data
 * cases; because {@code Fetch} structures hold continuations and are not value-comparable, the two
 * sides of each law are compared by running them and comparing the resulting values.
 */
@DisplayName("FetchApplicative Laws - Dynamic Test Factory")
class FetchApplicativeLawsTestFactory {

  private static final FetchApplicative<String, String> APP = FetchApplicative.instance();

  /** Echoes every requested key as {@code "val:" + key}. */
  private static final Function<Set<String>, Map<String, String>> ECHO =
      keys -> {
        Map<String, String> out = new HashMap<>();
        keys.forEach(k -> out.put(k, "val:" + k));
        return out;
      };

  /** Runs a Fetch program to its value under the echo resolver. */
  private static <A> A run(Kind<FetchKind.Witness<String, String>, A> k) {
    return Fetch.runCached(FETCH.narrow(k), ECHO).value();
  }

  /** A function-valued Fetch: resolves {@code key}, then prefixes its argument with the result. */
  private static Kind<FetchKind.Witness<String, String>, Function<String, String>> prefixFn(
      String key) {
    return APP.map(
        s -> (Function<String, String>) arg -> s + ">" + arg, FETCH.widen(Fetch.fetch(key)));
  }

  @TestFactory
  @DisplayName("Identity: ap(of(id), v) = v")
  Stream<DynamicTest> identityLaw() {
    return Stream.of("a", "b", "c")
        .map(
            key ->
                DynamicTest.dynamicTest(
                    "v = fetch(" + key + ")",
                    () -> {
                      Kind<FetchKind.Witness<String, String>, String> v =
                          FETCH.widen(Fetch.fetch(key));
                      Kind<FetchKind.Witness<String, String>, String> lhs =
                          APP.ap(APP.of(Function.<String>identity()), v);
                      assertThat(run(lhs)).isEqualTo(run(v));
                    }));
  }

  @TestFactory
  @DisplayName("Homomorphism: ap(of(f), of(x)) = of(f(x))")
  Stream<DynamicTest> homomorphismLaw() {
    record Case(String name, Function<String, String> f, String x) {}
    return Stream.of(
            new Case("toUpperCase / hello", String::toUpperCase, "hello"),
            new Case("append-bang / hi", s -> s + "!", "hi"))
        .map(
            c ->
                DynamicTest.dynamicTest(
                    c.name(),
                    () -> {
                      Kind<FetchKind.Witness<String, String>, String> lhs =
                          APP.ap(APP.of(c.f()), APP.of(c.x()));
                      Kind<FetchKind.Witness<String, String>, String> rhs =
                          APP.of(c.f().apply(c.x()));
                      assertThat(run(lhs)).isEqualTo(run(rhs));
                    }));
  }

  @TestFactory
  @DisplayName("Interchange: ap(u, of(y)) = ap(of(g -> g(y)), u)")
  Stream<DynamicTest> interchangeLaw() {
    return Stream.of("Y", "Z")
        .map(
            y ->
                DynamicTest.dynamicTest(
                    "y = " + y,
                    () -> {
                      Kind<FetchKind.Witness<String, String>, Function<String, String>> u =
                          prefixFn("u");
                      Kind<FetchKind.Witness<String, String>, String> lhs = APP.ap(u, APP.of(y));
                      Function<Function<String, String>, String> applyY = g -> g.apply(y);
                      Kind<FetchKind.Witness<String, String>, String> rhs =
                          APP.ap(APP.of(applyY), u);
                      assertThat(run(lhs)).isEqualTo(run(rhs));
                    }));
  }

  @TestFactory
  @DisplayName("Composition: ap(ap(map(compose, u), v), w) = ap(u, ap(v, w))")
  Stream<DynamicTest> compositionLaw() {
    return Stream.of("w1", "w2")
        .map(
            wKey ->
                DynamicTest.dynamicTest(
                    "w = fetch(" + wKey + ")",
                    () -> {
                      Kind<FetchKind.Witness<String, String>, Function<String, String>> u =
                          prefixFn("u");
                      Kind<FetchKind.Witness<String, String>, Function<String, String>> v =
                          prefixFn("v");
                      Kind<FetchKind.Witness<String, String>, String> w =
                          FETCH.widen(Fetch.fetch(wKey));
                      Function<
                              Function<String, String>,
                              Function<Function<String, String>, Function<String, String>>>
                          compose = g -> h -> x -> g.apply(h.apply(x));

                      Kind<FetchKind.Witness<String, String>, String> lhs =
                          APP.ap(APP.ap(APP.map(compose, u), v), w);
                      Kind<FetchKind.Witness<String, String>, String> rhs = APP.ap(u, APP.ap(v, w));
                      assertThat(run(lhs)).isEqualTo(run(rhs));
                    }));
  }
}
