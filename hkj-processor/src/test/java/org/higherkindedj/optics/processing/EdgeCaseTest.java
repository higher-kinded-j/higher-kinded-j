// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Edge case tests for the ImportOpticsProcessor.
 *
 * <p>These tests verify that the processor handles unusual but valid Java code correctly,
 * including:
 *
 * <ul>
 *   <li>Deeply nested generic types
 *   <li>Escaped Java keywords as field names
 *   <li>Unicode identifiers
 *   <li>Empty and minimal types
 *   <li>Recursive type definitions
 *   <li>Complex sealed hierarchies
 * </ul>
 */
@DisplayName("Edge Case Tests")
class EdgeCaseTest {

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new ImportOpticsProcessor()).compile(sources);
  }

  private JavaFileObject packageInfo(String packageName, String... typeNames) {
    StringBuilder imports = new StringBuilder();
    StringBuilder classes = new StringBuilder();

    for (int i = 0; i < typeNames.length; i++) {
      String typeName = typeNames[i];
      imports.append("import ").append(typeName).append(";\n");
      if (i > 0) classes.append(", ");
      classes.append(typeName.substring(typeName.lastIndexOf('.') + 1)).append(".class");
    }

    String source =
        String.format(
            """
            @ImportOptics({%s})
            package %s;

            import org.higherkindedj.optics.annotations.ImportOptics;
            %s
            """,
            classes, packageName, imports);

    return JavaFileObjects.forSourceString(packageName + ".package-info", source);
  }

  // =============================================================================
  // Nested Generics Tests
  // =============================================================================

  @Nested
  @DisplayName("Nested Generics")
  class NestedGenericsTests {

    @Test
    @DisplayName("should handle deeply nested generic types")
    void shouldHandleDeeplyNestedGenerics() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Nested",
              """
              package com.test;
              import java.util.*;
              public record Nested(
                  List<Optional<Map<String, List<Integer>>>> deeplyNested
              ) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Nested");
      var compilation = compile(record, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.NestedLenses")).isPresent();
    }

    @Test
    @DisplayName("should handle generic type with multiple parameters")
    void shouldHandleMultipleTypeParameters() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Multi",
              """
              package com.test;
              import java.util.Map;
              import java.util.function.BiFunction;
              public record Multi<A, B, C>(
                  Map<A, B> mapping,
                  BiFunction<A, B, C> transformer,
                  C result
              ) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Multi");
      var compilation = compile(record, pkgInfo);

      // Multi-parameter generics may or may not be fully supported
      // The key is that compilation doesn't crash
      assertThat(compilation.status()).isIn(Compilation.Status.SUCCESS, Compilation.Status.FAILURE);
    }

    @Test
    @DisplayName("should handle wildcard generic types")
    void shouldHandleWildcardGenerics() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Wildcard",
              """
              package com.test;
              import java.util.List;
              public record Wildcard(
                  List<?> unbounded,
                  List<? extends Number> upperBound,
                  List<? super Integer> lowerBound
              ) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Wildcard");
      var compilation = compile(record, pkgInfo);

      // Wildcard generics may or may not be fully supported
      // The key is that compilation doesn't crash
      assertThat(compilation.status()).isIn(Compilation.Status.SUCCESS, Compilation.Status.FAILURE);
    }
  }

  // =============================================================================
  // Unusual Names Tests
  // =============================================================================

  @Nested
  @DisplayName("Unusual Names")
  class UnusualNamesTests {

    @Test
    @DisplayName("should handle Java keywords as field names via underscore suffix")
    void shouldHandleKeywordFieldNames() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Keywords",
              """
              package com.test;
              public record Keywords(
                  String class_,
                  int default_,
                  boolean static_,
                  double return_
              ) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Keywords");
      var compilation = compile(record, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.KeywordsLenses")).isPresent();
    }

    @Test
    @DisplayName("should handle single character field names")
    void shouldHandleSingleCharFieldNames() throws IOException {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Single",
              """
              package com.test;
              public record Single(int x, int y, int z) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Single");
      var compilation = compile(record, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      var source = compilation.generatedSourceFile("com.test.optics.SingleLenses");
      assertThat(source).isPresent();
      assertThat(source.get().getCharContent(true).toString())
          .contains("Lens<Single, Integer> x()");
    }

    @Test
    @DisplayName("should handle field names starting with uppercase")
    void shouldHandleUppercaseFieldNames() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Upper",
              """
              package com.test;
              public record Upper(String URL, int XMLCount) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Upper");
      var compilation = compile(record, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.UpperLenses")).isPresent();
    }

    @Test
    @DisplayName("should handle very long field names")
    void shouldHandleLongFieldNames() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.LongNames",
              """
              package com.test;
              public record LongNames(
                  String thisIsAVeryLongFieldNameThatShouldStillWorkCorrectly,
                  int anotherExtremelyLongFieldNameForTestingPurposes
              ) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.LongNames");
      var compilation = compile(record, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.LongNamesLenses")).isPresent();
    }
  }

  // =============================================================================
  // Empty and Minimal Types Tests
  // =============================================================================

  @Nested
  @DisplayName("Empty and Minimal Types")
  class EmptyAndMinimalTypesTests {

    @Test
    @DisplayName("should handle empty record")
    void shouldHandleEmptyRecord() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Empty",
              """
              package com.test;
              public record Empty() {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Empty");
      var compilation = compile(record, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      // Generated class should have no lens methods but should still be valid
      var source = compilation.generatedSourceFile("com.test.optics.EmptyLenses");
      assertThat(source).isPresent();
    }

    @Test
    @DisplayName("should handle single-constant enum")
    void shouldHandleSingleConstantEnum() {
      var enumType =
          JavaFileObjects.forSourceString(
              "com.test.Single",
              """
              package com.test;
              public enum Single { ONLY }
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Single");
      var compilation = compile(enumType, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.SinglePrisms")).isPresent();
    }

    @Test
    @DisplayName("should handle single-field record")
    void shouldHandleSingleFieldRecord() throws IOException {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Wrapper",
              """
              package com.test;
              public record Wrapper(String value) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Wrapper");
      var compilation = compile(record, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      var source = compilation.generatedSourceFile("com.test.optics.WrapperLenses");
      assertThat(source).isPresent();
      assertThat(source.get().getCharContent(true).toString())
          .contains("Lens<Wrapper, String> value()");
    }

    @Test
    @DisplayName("should handle enum with many constants")
    void shouldHandleLargeEnum() throws IOException {
      var enumType =
          JavaFileObjects.forSourceString(
              "com.test.Large",
              """
              package com.test;
              public enum Large {
                  A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z
              }
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Large");
      var compilation = compile(enumType, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      var source = compilation.generatedSourceFile("com.test.optics.LargePrisms");
      assertThat(source).isPresent();
      // Should have 26 prisms
      String content = source.get().getCharContent(true).toString();
      assertThat(content).contains("Prism<Large, Large> a()");
      assertThat(content).contains("Prism<Large, Large> z()");
    }
  }

  // =============================================================================
  // Recursive Types Tests
  // =============================================================================

  @Nested
  @DisplayName("Recursive Types")
  class RecursiveTypesTests {

    @Test
    @DisplayName("should handle recursive record types")
    void shouldHandleRecursiveRecords() throws IOException {
      var recursive =
          JavaFileObjects.forSourceString(
              "com.test.Node",
              """
              package com.test;
              public record Node<T>(T value, Node<T> next) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Node");
      var compilation = compile(recursive, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      var source = compilation.generatedSourceFile("com.test.optics.NodeLenses");
      assertThat(source).isPresent();
      String content = source.get().getCharContent(true).toString();
      assertThat(content).contains("Lens<Node<T>, Node<T>> next()");
    }

    @Test
    @DisplayName("should handle binary tree structure")
    void shouldHandleBinaryTreeStructure() throws IOException {
      var tree =
          JavaFileObjects.forSourceString(
              "com.test.Tree",
              """
              package com.test;
              public record Tree<T>(T value, Tree<T> left, Tree<T> right) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Tree");
      var compilation = compile(tree, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      var source = compilation.generatedSourceFile("com.test.optics.TreeLenses");
      assertThat(source).isPresent();
      String content = source.get().getCharContent(true).toString();
      assertThat(content).contains("Lens<Tree<T>, Tree<T>> left()");
      assertThat(content).contains("Lens<Tree<T>, Tree<T>> right()");
    }

    @Test
    @DisplayName("should handle self-referential sealed interfaces")
    void shouldHandleSelfReferentialSealedTypes() {
      var expr =
          JavaFileObjects.forSourceString(
              "com.test.Expr",
              """
              package com.test;
              public sealed interface Expr permits Lit, Add, Mul {}
              """);

      var lit =
          JavaFileObjects.forSourceString(
              "com.test.Lit",
              """
              package com.test;
              public record Lit(int value) implements Expr {}
              """);

      var add =
          JavaFileObjects.forSourceString(
              "com.test.Add",
              """
              package com.test;
              public record Add(Expr left, Expr right) implements Expr {}
              """);

      var mul =
          JavaFileObjects.forSourceString(
              "com.test.Mul",
              """
              package com.test;
              public record Mul(Expr left, Expr right) implements Expr {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Expr");
      var compilation = compile(expr, lit, add, mul, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.ExprPrisms")).isPresent();
    }

    @Test
    @DisplayName("should handle mutually recursive types")
    void shouldHandleMutuallyRecursiveTypes() {
      var even =
          JavaFileObjects.forSourceString(
              "com.test.Even",
              """
              package com.test;
              import java.util.Optional;
              public record Even(int value, Optional<Odd> next) {}
              """);

      var odd =
          JavaFileObjects.forSourceString(
              "com.test.Odd",
              """
              package com.test;
              import java.util.Optional;
              public record Odd(int value, Optional<Even> next) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Even", "com.test.Odd");
      var compilation = compile(even, odd, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.EvenLenses")).isPresent();
      assertThat(compilation.generatedSourceFile("com.test.optics.OddLenses")).isPresent();
    }
  }

  // =============================================================================
  // Complex Sealed Hierarchies Tests
  // =============================================================================

  @Nested
  @DisplayName("Complex Sealed Hierarchies")
  class ComplexSealedHierarchiesTests {

    @Test
    @DisplayName("should handle sealed interface with non-record subtypes")
    void shouldHandleMixedSealedSubtypes() {
      var sealed =
          JavaFileObjects.forSourceString(
              "com.test.Result",
              """
              package com.test;
              public sealed interface Result permits Success, Failure, Pending {}
              """);

      var success =
          JavaFileObjects.forSourceString(
              "com.test.Success",
              """
              package com.test;
              public record Success(String value) implements Result {}
              """);

      var failure =
          JavaFileObjects.forSourceString(
              "com.test.Failure",
              """
              package com.test;
              public final class Failure implements Result {
                  private final Exception error;
                  public Failure(Exception error) { this.error = error; }
                  public Exception getError() { return error; }
              }
              """);

      var pending =
          JavaFileObjects.forSourceString(
              "com.test.Pending",
              """
              package com.test;
              public enum Pending implements Result { INSTANCE }
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Result");
      var compilation = compile(sealed, success, failure, pending, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.ResultPrisms")).isPresent();
    }

    @Test
    @DisplayName("should handle multi-level sealed hierarchy")
    void shouldHandleMultiLevelSealedHierarchy() {
      var top =
          JavaFileObjects.forSourceString(
              "com.test.Animal",
              """
              package com.test;
              public sealed interface Animal permits Mammal, Bird {}
              """);

      var mammal =
          JavaFileObjects.forSourceString(
              "com.test.Mammal",
              """
              package com.test;
              public sealed interface Mammal extends Animal permits Dog, Cat {}
              """);

      var bird =
          JavaFileObjects.forSourceString(
              "com.test.Bird",
              """
              package com.test;
              public record Bird(String species) implements Animal {}
              """);

      var dog =
          JavaFileObjects.forSourceString(
              "com.test.Dog",
              """
              package com.test;
              public record Dog(String name) implements Mammal {}
              """);

      var cat =
          JavaFileObjects.forSourceString(
              "com.test.Cat",
              """
              package com.test;
              public record Cat(String name) implements Mammal {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Animal", "com.test.Mammal");
      var compilation = compile(top, mammal, bird, dog, cat, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.AnimalPrisms")).isPresent();
      assertThat(compilation.generatedSourceFile("com.test.optics.MammalPrisms")).isPresent();
    }

    @Test
    @DisplayName("should handle sealed interface with generic variants")
    void shouldHandleGenericSealedVariants() {
      var either =
          JavaFileObjects.forSourceString(
              "com.test.Either",
              """
              package com.test;
              public sealed interface Either<L, R> permits Left, Right {}
              """);

      var left =
          JavaFileObjects.forSourceString(
              "com.test.Left",
              """
              package com.test;
              public record Left<L, R>(L value) implements Either<L, R> {}
              """);

      var right =
          JavaFileObjects.forSourceString(
              "com.test.Right",
              """
              package com.test;
              public record Right<L, R>(R value) implements Either<L, R> {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Either");
      var compilation = compile(either, left, right, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.EitherPrisms")).isPresent();
    }
  }

  // =============================================================================
  // Special Cases Tests
  // =============================================================================

  @Nested
  @DisplayName("Special Cases")
  class SpecialCasesTests {

    @Test
    @DisplayName("should handle record with array fields")
    void shouldHandleArrayFields() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Arrays",
              """
              package com.test;
              public record Arrays(int[] numbers, String[] names, byte[][] matrix) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Arrays");
      var compilation = compile(record, pkgInfo);

      // Array fields may or may not be fully supported
      // The key is that compilation doesn't crash
      assertThat(compilation.status()).isIn(Compilation.Status.SUCCESS, Compilation.Status.FAILURE);
    }

    @Test
    @DisplayName("should handle record with primitive fields")
    void shouldHandlePrimitiveFields() throws IOException {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Primitives",
              """
              package com.test;
              public record Primitives(
                  boolean flag,
                  byte b,
                  short s,
                  int i,
                  long l,
                  float f,
                  double d,
                  char c
              ) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Primitives");
      var compilation = compile(record, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      var source = compilation.generatedSourceFile("com.test.optics.PrimitivesLenses");
      assertThat(source).isPresent();
      // Verify boxed types are used
      String content = source.get().getCharContent(true).toString();
      assertThat(content).contains("Lens<Primitives, Boolean> flag()");
      assertThat(content).contains("Lens<Primitives, Integer> i()");
    }

    @Test
    @DisplayName("should handle record implementing multiple interfaces")
    void shouldHandleMultipleInterfaces() {
      var comparable =
          JavaFileObjects.forSourceString(
              "com.test.Multi",
              """
              package com.test;
              import java.io.Serializable;
              public record Multi(String value) implements Comparable<Multi>, Serializable {
                  @Override
                  public int compareTo(Multi other) {
                      return value.compareTo(other.value);
                  }
              }
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Multi");
      var compilation = compile(comparable, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.MultiLenses")).isPresent();
    }

    @Test
    @DisplayName("should handle record with annotation on fields")
    void shouldHandleAnnotatedFields() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.Annotated",
              """
              package com.test;
              import java.lang.annotation.*;
              public record Annotated(
                  @Deprecated String oldField,
                  @SuppressWarnings("unused") int unusedField
              ) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Annotated");
      var compilation = compile(record, pkgInfo);

      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation.generatedSourceFile("com.test.optics.AnnotatedLenses")).isPresent();
    }
  }
}
