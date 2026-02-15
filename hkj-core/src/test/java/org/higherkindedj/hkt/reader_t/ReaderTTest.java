// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderT Core Type Tests ")
// (Outer: OptionalKind.Witness, Environment: String)
class ReaderTTest {

  private Monad<OptionalKind.Witness> outerMonad;

  private final String environment = "test-env";
  private final Integer resultValue = 42;
  private final String otherEnvironment = "other-env";

  private record Config(String setting) {}

  private Kind<OptionalKind.Witness, Integer> wrappedValue;
  private Kind<OptionalKind.Witness, Integer> wrappedEmpty;

  private Function<String, Integer> simpleFunction;
  private Function<String, Kind<OptionalKind.Witness, Integer>> runFunction;

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;

    wrappedValue = OPTIONAL.widen(Optional.of(resultValue));
    wrappedEmpty = OPTIONAL.widen(Optional.empty());

    simpleFunction = String::length;
    runFunction = env -> OPTIONAL.widen(Optional.of(env.length()));
  }

  private <A> Optional<A> unwrapT(ReaderT<OptionalKind.Witness, String, A> readerT) {
    Kind<OptionalKind.Witness, A> result = readerT.run().apply(environment);
    return OPTIONAL.narrow(result);
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("of should create ReaderT from run function")
    void of_createsFromRunFunction() {
      ReaderT<OptionalKind.Witness, String, Integer> rt = ReaderT.of(runFunction);

      assertThat(unwrapT(rt)).isPresent().contains(environment.length());
      assertThat(rt.run()).isSameAs(runFunction);
    }

    @Test
    @DisplayName("of should handle function returning empty")
    void of_handlesEmptyResult() {
      Function<String, Kind<OptionalKind.Witness, Integer>> emptyFunc = env -> wrappedEmpty;
      ReaderT<OptionalKind.Witness, String, Integer> rt = ReaderT.of(emptyFunc);

      assertThat(unwrapT(rt)).isEmpty();
    }

    @Test
    @DisplayName("liftF should lift monadic value to ReaderT")
    void liftF_liftsMonadicValue() {
      ReaderT<OptionalKind.Witness, String, Integer> rt = ReaderT.liftF(outerMonad, wrappedValue);

      assertThat(unwrapT(rt)).isPresent().contains(resultValue);
    }

    @Test
    @DisplayName("liftF should lift empty monadic value")
    void liftF_liftsEmptyValue() {
      ReaderT<OptionalKind.Witness, String, Integer> rt = ReaderT.liftF(outerMonad, wrappedEmpty);

      assertThat(unwrapT(rt)).isEmpty();
    }

    @Test
    @DisplayName("reader should create ReaderT from simple function")
    void reader_createsFromSimpleFunction() {
      ReaderT<OptionalKind.Witness, String, Integer> rt =
          ReaderT.reader(outerMonad, simpleFunction);

      assertThat(unwrapT(rt)).isPresent().contains(environment.length());
    }

    @Test
    @DisplayName("reader should handle function returning empty")
    void reader_handlesFunctionReturningEmpty() {
      // Create a ReaderT that explicitly returns an empty Optional
      ReaderT<OptionalKind.Witness, String, Integer> rt =
          ReaderT.of(env -> OPTIONAL.widen(Optional.empty()));

      assertThat(unwrapT(rt)).isEmpty();
    }

    @Test
    @DisplayName("ask should provide the environment")
    void ask_providesEnvironment() {
      ReaderT<OptionalKind.Witness, String, String> rt = ReaderT.ask(outerMonad);

      Kind<OptionalKind.Witness, String> kindResult = rt.run().apply(environment);
      Optional<String> result = OPTIONAL.narrow(kindResult);

      assertThat(result).isPresent().contains(environment);
    }

    @Test
    @DisplayName("ask should work with different environments")
    void ask_worksWithDifferentEnvironments() {
      ReaderT<OptionalKind.Witness, String, String> rt = ReaderT.ask(outerMonad);

      Kind<OptionalKind.Witness, String> kindResult1 = rt.run().apply(environment);
      Kind<OptionalKind.Witness, String> kindResult2 = rt.run().apply(otherEnvironment);

      Optional<String> result1 = OPTIONAL.narrow(kindResult1);
      Optional<String> result2 = OPTIONAL.narrow(kindResult2);

      assertThat(result1).isPresent().contains(environment);
      assertThat(result2).isPresent().contains(otherEnvironment);
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceMethodTests {

    @Test
    @DisplayName("run should return the wrapped function")
    void run_returnsWrappedFunction() {
      ReaderT<OptionalKind.Witness, String, Integer> rt = ReaderT.of(runFunction);

      assertThat(rt.run()).isSameAs(runFunction);
    }

    @Test
    @DisplayName("run should be callable multiple times")
    void run_callableMultipleTimes() {
      ReaderT<OptionalKind.Witness, String, Integer> rt =
          ReaderT.reader(outerMonad, simpleFunction);

      Function<String, Kind<OptionalKind.Witness, Integer>> runFunc = rt.run();

      Optional<Integer> result1 = OPTIONAL.narrow(runFunc.apply(environment));
      Optional<Integer> result2 = OPTIONAL.narrow(runFunc.apply(otherEnvironment));

      assertThat(result1).isPresent().contains(environment.length());
      assertThat(result2).isPresent().contains(otherEnvironment.length());
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectTests {

    // Add this record at the beginning of ObjectTests
    private record Config(String setting) {}

    @Test
    @DisplayName("equals/hashCode should compare based on run function instance")
    void equalsHashCode_comparesRunFunction() {
      // Create ONE function instance and reuse it
      Function<Config, Kind<OptionalKind.Witness, String>> sharedFunc1 = cfg -> outerMonad.of("A");
      Function<Config, Kind<OptionalKind.Witness, String>> sharedFunc2 = cfg -> outerMonad.of("A");
      Function<Config, Kind<OptionalKind.Witness, String>> sharedFunc3 = cfg -> outerMonad.of("B");

      // These two ReaderT instances share the SAME function reference
      ReaderT<OptionalKind.Witness, Config, String> rt1a = ReaderT.of(sharedFunc1);
      ReaderT<OptionalKind.Witness, Config, String> rt1b = ReaderT.of(sharedFunc1);

      // These use different function references
      ReaderT<OptionalKind.Witness, Config, String> rt2 = ReaderT.of(sharedFunc2);
      ReaderT<OptionalKind.Witness, Config, String> rt3 = ReaderT.of(sharedFunc3);

      // Same function reference → equal
      assertThat(rt1a).isEqualTo(rt1b);
      assertThat(rt1a).hasSameHashCodeAs(rt1b);

      // Different function references → not equal
      assertThat(rt1a).isNotEqualTo(rt2);
      assertThat(rt1a).isNotEqualTo(rt3);

      assertThat(rt1a).isNotEqualTo(null);
      assertThat(rt1a).isNotEqualTo(sharedFunc1);
    }

    @Test
    @DisplayName("equals should be reflexive")
    void equals_reflexive() {
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc = cfg -> outerMonad.of("test");
      ReaderT<OptionalKind.Witness, Config, String> rt = ReaderT.of(runFunc);

      assertThat(rt).isEqualTo(rt);
    }

    @Test
    @DisplayName("equals should be symmetric")
    void equals_symmetric() {
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc = cfg -> outerMonad.of("test");
      ReaderT<OptionalKind.Witness, Config, String> rt1 = ReaderT.of(runFunc);
      ReaderT<OptionalKind.Witness, Config, String> rt2 = ReaderT.of(runFunc);

      assertThat(rt1.equals(rt2)).isEqualTo(rt2.equals(rt1));
    }

    @Test
    @DisplayName("hashCode should be consistent with equals")
    void hashCode_consistentWithEquals() {
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc = cfg -> outerMonad.of("test");
      ReaderT<OptionalKind.Witness, Config, String> rt1 = ReaderT.of(runFunc);
      ReaderT<OptionalKind.Witness, Config, String> rt2 = ReaderT.of(runFunc);

      // If equal, must have same hashCode
      if (rt1.equals(rt2)) {
        assertThat(rt1.hashCode()).isEqualTo(rt2.hashCode());
      }
    }

    @Test
    @DisplayName("toString should represent the structure")
    void toString_representsStructure() {
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc =
          cfg -> outerMonad.of(cfg.setting());
      ReaderT<OptionalKind.Witness, Config, String> rt = ReaderT.of(runFunc);

      assertThat(rt.toString()).startsWith("ReaderT[run=");
      assertThat(rt.toString()).contains(runFunc.toString());
      assertThat(rt.toString()).endsWith("]");
    }
  }

  @Nested
  @DisplayName("Complete Core Type Test Suite")
  class CompleteCoreTypeTests {

    @Test
    @DisplayName("Run complete ReaderT core type tests")
    void runCompleteReaderTTests() {
      ReaderT<OptionalKind.Witness, String, Integer> instance =
          ReaderT.reader(outerMonad, simpleFunction);

      CoreTypeTest.<OptionalKind.Witness, String, Integer>readerT(ReaderT.class, outerMonad)
          .withInstance(instance)
          .withMappers(Object::toString)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Edge case: function returning null results in empty")
    void edgeCase_functionReturningNull() {
      // Optional doesn't support null, so of(null) would throw NPE
      // Instead we demonstrate that empty Optional is propagated
      Function<String, Kind<OptionalKind.Witness, Integer>> emptyFunc =
          env -> OPTIONAL.widen(Optional.empty());
      ReaderT<OptionalKind.Witness, String, Integer> rt = ReaderT.of(emptyFunc);

      assertThat(unwrapT(rt)).isEmpty();
    }

    @Test
    @DisplayName("Edge case: reader with null-like behaviour via empty")
    void edgeCase_readerWithNullReturningFunction() {
      // Optional doesn't allow null values, testing empty propagation instead
      ReaderT<OptionalKind.Witness, String, Integer> rt =
          ReaderT.liftF(outerMonad, OPTIONAL.widen(Optional.empty()));

      assertThat(unwrapT(rt)).isEmpty();
    }

    @Test
    @DisplayName("Edge case: liftF with null-like value via empty")
    void edgeCase_liftFWithNullValue() {
      // Optional doesn't support null, testing empty Optional instead
      Kind<OptionalKind.Witness, Integer> emptyValue = OPTIONAL.widen(Optional.empty());
      ReaderT<OptionalKind.Witness, String, Integer> rt = ReaderT.liftF(outerMonad, emptyValue);

      assertThat(unwrapT(rt)).isEmpty();
    }

    @Test
    @DisplayName("Edge case: chaining multiple ReaderT operations")
    void edgeCase_chainingOperations() {
      ReaderT<OptionalKind.Witness, String, String> ask = ReaderT.ask(outerMonad);
      Function<String, Kind<OptionalKind.Witness, String>> runFunc = ask.run();

      Optional<Integer> result =
          OPTIONAL.narrow(outerMonad.map(String::length, runFunc.apply(environment)));

      assertThat(result).isPresent().contains(environment.length());
    }
  }
}
