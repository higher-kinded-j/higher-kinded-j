package org.simulation.hkt.maybe;

import static org.assertj.core.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Maybe<T> Direct Tests")
class MaybeTest {

  private final String justValue = "Present Value";
  private final Maybe<String> justInstance = Maybe.just(justValue);
  private final Maybe<String> nothingInstance = Maybe.nothing();
  private final Maybe<String> fromNullableJust = Maybe.fromNullable(justValue);
  private final Maybe<String> fromNullableNothing = Maybe.fromNullable(null);

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {
    @Test
    void just_shouldCreateJustInstance() {
      assertThat(justInstance).isInstanceOf(Just.class);
      assertThat(justInstance.isJust()).isTrue();
      assertThat(justInstance.isNothing()).isFalse();
      assertThat(justInstance.get()).isEqualTo(justValue);
    }

    @Test
    void just_shouldThrowNPEForNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Maybe.just(null))
          .withMessageContaining("Value for Just cannot be null");
    }

    @Test
    void nothing_shouldReturnNothingSingleton() {
      assertThat(nothingInstance).isInstanceOf(Nothing.class);
      assertThat(nothingInstance.isNothing()).isTrue();
      assertThat(nothingInstance.isJust()).isFalse();
      // Verify singleton behavior
      Maybe<Integer> nothingInt = Maybe.nothing();
      assertThat(nothingInstance).isSameAs(nothingInt);
    }

    @Test
    void fromNullable_shouldCreateJustForNonNull() {
      assertThat(fromNullableJust).isInstanceOf(Just.class);
      assertThat(fromNullableJust.get()).isEqualTo(justValue);
    }

    @Test
    void fromNullable_shouldCreateNothingForNull() {
      assertThat(fromNullableNothing).isInstanceOf(Nothing.class);
      assertThat(fromNullableNothing.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Getters and Checks")
  class GettersAndChecks {
    @Test
    void isJust_returnsCorrectValue() {
      assertThat(justInstance.isJust()).isTrue();
      assertThat(nothingInstance.isJust()).isFalse();
    }

    @Test
    void isNothing_returnsCorrectValue() {
      assertThat(justInstance.isNothing()).isFalse();
      assertThat(nothingInstance.isNothing()).isTrue();
    }

    @Test
    void get_onJust_shouldReturnValue() {
      assertThat(justInstance.get()).isEqualTo(justValue);
    }

    @Test
    void get_onNothing_shouldThrowException() {
      assertThatThrownBy(nothingInstance::get)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot call get() on Nothing");
    }
  }

  @Nested
  @DisplayName("orElse / orElseGet")
  class OrElseTests {
    final String defaultValue = "Default";
    final Supplier<String> defaultSupplier = () -> defaultValue;
    final Supplier<String> nullSupplier = null;

    @Test
    void orElse_onJust_shouldReturnValue() {
      assertThat(justInstance.orElse(defaultValue)).isEqualTo(justValue);
    }

    @Test
    void orElse_onNothing_shouldReturnDefault() {
      assertThat(nothingInstance.orElse(defaultValue)).isEqualTo(defaultValue);
    }

    @Test
    void orElseGet_onJust_shouldReturnValueAndNotCallSupplier() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return defaultValue;
          };
      assertThat(justInstance.orElseGet(trackingSupplier)).isEqualTo(justValue);
      assertThat(supplierCalled).isFalse();
    }

    @Test
    void orElseGet_onNothing_shouldCallSupplierAndReturnResult() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return defaultValue;
          };
      assertThat(nothingInstance.orElseGet(trackingSupplier)).isEqualTo(defaultValue);
      assertThat(supplierCalled).isTrue();
    }

    @Test
    void orElseGet_onNothing_shouldThrowIfSupplierIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> nothingInstance.orElseGet(nullSupplier))
          .withMessageContaining("orElseGet supplier cannot be null");
    }

    @Test
    void orElseGet_onJust_shouldNotThrowIfSupplierIsNull() {
      // Supplier isn't called for Just, so null supplier is ok
      assertThatCode(() -> justInstance.orElseGet(nullSupplier)).doesNotThrowAnyException();
      assertThat(justInstance.orElseGet(nullSupplier)).isEqualTo(justValue);
    }
  }

  @Nested
  @DisplayName("map()")
  class MapTests {
    final Function<String, Integer> mapper = String::length;
    final Function<String, String> mapperToNull = s -> null;

    @Test
    void map_onJust_shouldApplyMapperAndReturnJust() {
      Maybe<Integer> result = justInstance.map(mapper);
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo(justValue.length());
    }

    @Test
    void map_onJust_shouldReturnNothingIfMapperReturnsNull() {
      Maybe<String> result = justInstance.map(mapperToNull);
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    void map_onNothing_shouldReturnNothing() {
      Maybe<Integer> result = nothingInstance.map(mapper);
      assertThat(result.isNothing()).isTrue();
      assertThat(result).isSameAs(nothingInstance); // Should return singleton
    }

    @Test
    void map_shouldThrowIfMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> justInstance.map(null))
          .withMessageContaining("mapper function cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> nothingInstance.map(null))
          .withMessageContaining("mapper function cannot be null");
    }
  }

  @Nested
  @DisplayName("flatMap()")
  class FlatMapTests {
    final Function<String, Maybe<Integer>> mapperJust = s -> Maybe.just(s.length());
    final Function<String, Maybe<Integer>> mapperNothing = s -> Maybe.nothing();
    final Function<String, Maybe<Integer>> mapperNull = s -> null;

    @Test
    void flatMap_onJust_shouldApplyMapperAndReturnResult() {
      Maybe<Integer> resultJust = justInstance.flatMap(mapperJust);
      assertThat(resultJust.isJust()).isTrue();
      assertThat(resultJust.get()).isEqualTo(justValue.length());

      Maybe<Integer> resultNothing = justInstance.flatMap(mapperNothing);
      assertThat(resultNothing.isNothing()).isTrue();
    }

    @Test
    void flatMap_onNothing_shouldReturnNothing() {
      Maybe<Integer> result = nothingInstance.flatMap(mapperJust);
      assertThat(result.isNothing()).isTrue();
      assertThat(result).isSameAs(nothingInstance); // Should return singleton
    }

    @Test
    void flatMap_shouldThrowIfMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> justInstance.flatMap(null))
          .withMessageContaining("mapper function cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> nothingInstance.flatMap(null))
          .withMessageContaining("mapper function cannot be null");
    }

    @Test
    void flatMap_shouldThrowIfMapperReturnsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> justInstance.flatMap(mapperNull))
          .withMessageContaining("flatMap mapper returned null Maybe");
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {
    @Test
    void toString_onJust() {
      assertThat(justInstance.toString()).isEqualTo("Just(" + justValue + ")");
    }

    @Test
    void toString_onNothing() {
      assertThat(nothingInstance.toString()).isEqualTo("Nothing");
    }
  }

  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsHashCodeTests {
    @Test
    void justEquals() {
      Maybe<String> just1 = Maybe.just("A");
      Maybe<String> just2 = Maybe.just("A");
      Maybe<String> just3 = Maybe.just("B");
      Maybe<String> nothing1 = Maybe.nothing();

      assertThat(just1).isEqualTo(just2);
      assertThat(just1).hasSameHashCodeAs(just2);
      assertThat(just1).isNotEqualTo(just3);
      assertThat(just1).isNotEqualTo(nothing1);
      assertThat(just1).isNotEqualTo(null);
      assertThat(just1).isNotEqualTo("A"); // Different type
    }

    @Test
    void nothingEquals() {
      Maybe<String> nothing1 = Maybe.nothing();
      Maybe<Integer> nothing2 = Maybe.nothing(); // Different type param, same instance
      Maybe<String> just1 = Maybe.just("A");

      assertThat(nothing1).isEqualTo(nothing2); // Should be equal due to singleton
      assertThat(nothing1).isSameAs(nothing2); // Should be same instance
      assertThat(nothing1).hasSameHashCodeAs(nothing2);
      assertThat(nothing1).isNotEqualTo(just1);
      assertThat(nothing1).isNotEqualTo(null);
    }
  }
}
