// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.ErrorHandling.TypeMatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TypeMatcher Tests")
class TypeMatchersTest {

  @Nested
  @DisplayName("TypeMatcher Interface Tests")
  class TypeMatcherInterfaceTests {

    @Test
    void shouldWorkWithCustomImplementations() {
      TypeMatcher<Object, String> stringMatcher =
          new TypeMatcher<Object, String>() {
            @Override
            public boolean matches(Object source) {
              return source instanceof String;
            }

            @Override
            public String extract(Object source) {
              return (String) source;
            }
          };

      assertThat(stringMatcher.matches("test")).isTrue();
      assertThat(stringMatcher.matches(123)).isFalse();
      assertThat(stringMatcher.extract("test")).isEqualTo("test");
    }

    @Test
    void shouldHandleComplexMatching() {
      TypeMatcher<Object, Integer> evenIntegerMatcher =
          new TypeMatcher<Object, Integer>() {
            @Override
            public boolean matches(Object source) {
              return source instanceof Integer && ((Integer) source) % 2 == 0;
            }

            @Override
            public Integer extract(Object source) {
              return (Integer) source;
            }
          };

      assertThat(evenIntegerMatcher.matches(4)).isTrue();
      assertThat(evenIntegerMatcher.matches(3)).isFalse();
      assertThat(evenIntegerMatcher.matches("4")).isFalse();
      assertThat(evenIntegerMatcher.extract(6)).isEqualTo(6);
    }

    @Test
    void shouldHandleNullInputsGracefully() {
      TypeMatcher<Object, String> nullSafeMatcher =
          new TypeMatcher<Object, String>() {
            @Override
            public boolean matches(Object source) {
              return source instanceof String;
            }

            @Override
            public String extract(Object source) {
              return source == null ? "null" : source.toString();
            }
          };

      assertThat(nullSafeMatcher.matches(null)).isFalse();
      assertThat(nullSafeMatcher.extract(null)).isEqualTo("null");
    }

    @Test
    void shouldAllowTransformation() {
      TypeMatcher<Object, String> transformingMatcher =
          new TypeMatcher<Object, String>() {
            @Override
            public boolean matches(Object source) {
              return source instanceof Number;
            }

            @Override
            public String extract(Object source) {
              Number num = (Number) source;
              return "Number: " + num.toString();
            }
          };

      assertThat(transformingMatcher.matches(42)).isTrue();
      assertThat(transformingMatcher.matches(3.14)).isTrue();
      assertThat(transformingMatcher.matches("42")).isFalse();
      assertThat(transformingMatcher.extract(42)).isEqualTo("Number: 42");
      assertThat(transformingMatcher.extract(3.14)).isEqualTo("Number: 3.14");
    }
  }

  @Nested
  @DisplayName("TypeMatchers Static Methods Tests")
  class TypeMatchersStaticMethodsTests {

    @Test
    void forClass_shouldCreateWorkingMatcher() {
      TypeMatcher<Object, String> stringMatcher =
          ErrorHandling.TypeMatchers.forClass(String.class, obj -> ((String) obj).toUpperCase());

      assertThat(stringMatcher.matches("test")).isTrue();
      assertThat(stringMatcher.matches(123)).isFalse();
      assertThat(stringMatcher.extract("hello")).isEqualTo("HELLO");
    }

    @Test
    void forClass_shouldHandleNullExtractor() {
      // The method creates a TypeMatcher, but the NPE occurs when we try to use the extractor
      TypeMatcher<Object, String> matcher = ErrorHandling.TypeMatchers.forClass(String.class, null);

      assertThat(matcher.matches("test")).isTrue();
      assertThatThrownBy(() -> matcher.extract("test")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void forClass_shouldHandleNullTargetClass() {
      // The method creates a TypeMatcher, but the NPE occurs when we try to use matches()
      TypeMatcher<Object, String> matcher =
          ErrorHandling.TypeMatchers.forClass(null, obj -> obj.toString());

      assertThatThrownBy(() -> matcher.matches("test")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void forClassWithCast_shouldCreateWorkingMatcher() {
      TypeMatcher<Object, String> stringCastMatcher =
          ErrorHandling.TypeMatchers.forClassWithCast(String.class);

      assertThat(stringCastMatcher.matches("test")).isTrue();
      assertThat(stringCastMatcher.matches(123)).isFalse();
      assertThat(stringCastMatcher.extract("hello")).isEqualTo("hello");
    }

    @Test
    void forClassWithCast_shouldHandleNullTargetClass() {
      // The method creates a TypeMatcher, but the NPE occurs when we try to use matches()
      TypeMatcher<Object, String> matcher = ErrorHandling.TypeMatchers.forClassWithCast(null);

      assertThatThrownBy(() -> matcher.matches("test")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void forClass_shouldWorkWithExtractorExceptions() {
      ErrorHandling.TypeMatcher<Object, String> throwingExtractor =
          ErrorHandling.TypeMatchers.forClass(
              String.class,
              obj -> {
                throw new RuntimeException("Extractor failed");
              });

      assertThat(throwingExtractor.matches("test")).isTrue();
      assertThatThrownBy(() -> throwingExtractor.extract("test"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Extractor failed");
    }

    @Test
    void shouldWorkWithDifferentTypes() {
      // Test with Integer
      TypeMatcher<Object, Integer> intDoublerMatcher =
          ErrorHandling.TypeMatchers.forClass(Integer.class, i -> ((Integer) i) * 2);

      assertThat(intDoublerMatcher.matches(5)).isTrue();
      assertThat(intDoublerMatcher.extract(5)).isEqualTo(10);

      // Test with List - use custom matcher to avoid type inference issues
      TypeMatcher<Object, java.util.List<?>> listMatcher =
          new TypeMatcher<Object, java.util.List<?>>() {
            @Override
            public boolean matches(Object source) {
              return source instanceof java.util.List;
            }

            @Override
            @SuppressWarnings("unchecked")
            public java.util.List<?> extract(Object source) {
              return (java.util.List<?>) source;
            }
          };

      java.util.List<String> testList = java.util.List.of("a", "b");
      assertThat(listMatcher.matches(testList)).isTrue();
      assertThat(listMatcher.extract(testList)).isSameAs(testList);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Conditions")
  class EdgeCasesTests {

    @Test
    void shouldHandleInheritanceCorrectly() {
      TypeMatcher<Object, Number> numberMatcher =
          ErrorHandling.TypeMatchers.forClassWithCast(Number.class);

      assertThat(numberMatcher.matches(42)).isTrue(); // Integer extends Number
      assertThat(numberMatcher.matches(3.14)).isTrue(); // Double extends Number
      assertThat(numberMatcher.matches("42")).isFalse(); // String doesn't extend Number
    }

    @Test
    void shouldHandleInterfaceMatching() {
      // Use custom matcher to avoid generic type inference issues
      TypeMatcher<Object, java.util.Collection<?>> collectionMatcher =
          new TypeMatcher<Object, java.util.Collection<?>>() {
            @Override
            public boolean matches(Object source) {
              return source instanceof java.util.Collection;
            }

            @Override
            @SuppressWarnings("unchecked")
            public java.util.Collection<?> extract(Object source) {
              return (java.util.Collection<?>) source;
            }
          };

      assertThat(collectionMatcher.matches(java.util.List.of("a"))).isTrue();
      assertThat(collectionMatcher.matches(java.util.Set.of("a"))).isTrue();
      assertThat(collectionMatcher.matches("not a collection")).isFalse();
    }

    @Test
    void shouldHandleArrayTypes() {
      TypeMatcher<Object, String[]> stringArrayMatcher =
          ErrorHandling.TypeMatchers.forClassWithCast(String[].class);

      String[] stringArray = {"a", "b", "c"};
      Integer[] intArray = {1, 2, 3};

      assertThat(stringArrayMatcher.matches(stringArray)).isTrue();
      assertThat(stringArrayMatcher.matches(intArray)).isFalse();
      assertThat(stringArrayMatcher.extract(stringArray)).isSameAs(stringArray);
    }

    @Test
    void shouldHandlePrimitiveWrapperTypes() {
      TypeMatcher<Object, Integer> integerMatcher =
          ErrorHandling.TypeMatchers.forClassWithCast(Integer.class);

      assertThat(integerMatcher.matches(42)).isTrue();
      assertThat(integerMatcher.matches(42L)).isFalse(); // Long is different from Integer
      assertThat(integerMatcher.extract(42)).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {

    @Test
    void shouldHandleLargeNumberOfMatches() {
      TypeMatcher<Object, String> stringMatcher =
          ErrorHandling.TypeMatchers.forClassWithCast(String.class);

      // Test with many objects
      for (int i = 0; i < 1000; i++) {
        String testString = "test" + i;
        assertThat(stringMatcher.matches(testString)).isTrue();
        assertThat(stringMatcher.extract(testString)).isEqualTo(testString);

        assertThat(stringMatcher.matches(i)).isFalse(); // Integer shouldn't match
      }
    }

    @Test
    void shouldHandleLargeObjects() {
      ErrorHandling.TypeMatcher<Object, String> stringMatcher =
          ErrorHandling.TypeMatchers.forClass(String.class, s -> ((String) s).toUpperCase());

      String largeString = "x".repeat(10000);
      assertThat(stringMatcher.matches(largeString)).isTrue();
      assertThat(stringMatcher.extract(largeString)).isEqualTo("X".repeat(10000));
    }
  }
}
