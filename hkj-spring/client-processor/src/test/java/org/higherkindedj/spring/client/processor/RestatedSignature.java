// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;

/**
 * One row of a table of signatures a client restates, each with what the generated files must carry
 * so that none of them draws a warning. The test that compiles the base alongside the client and
 * the one that reads it from a jar share each table, as the two must agree.
 *
 * <p>Every source the author writes suppresses its own warnings, as an author building with {@code
 * -Werror} must, so a compilation under {@link #LINT} fails on a warning in a generated file alone.
 */
sealed interface RestatedSignature
    permits DeprecatedSignatures.Shape, VarargsAndRawSignatures.Shape {

  /** Every lint, as a warning, but the one naming the annotations no processor claims. */
  List<String> LINT = List.of("-Xlint:all,-processing", "-Werror");

  /** The client most rows use: one that extends the base and declares nothing. */
  String CHILD =
      """
      package com.downstream;

      import com.upstream.BaseApi;
      import org.higherkindedj.spring.client.HkjHttpClient;
      import org.springframework.web.service.annotation.HttpExchange;

      @HttpExchange("/users")
      @HkjHttpClient
      public interface ChildApi extends BaseApi {}
      """;

  /** The facade generated for {@link #CHILD}. */
  String FACADE = "com.downstream.ChildApiClient";

  /** The native interface generated for {@link #CHILD}. */
  String EXCHANGE = "com.downstream.ChildApiHttpExchange";

  /** The base module's sources: its types and the {@code BaseApi} the row declares. */
  List<JavaFileObject> baseSources();

  /**
   * The client's source, the only one compiled against a base from a jar, named for the top-level
   * type it declares.
   */
  JavaFileObject client();

  /** What the generated files must hold. */
  List<Expected> expected();

  /**
   * What one generated file must hold: a match for {@code pattern}, or none where it is {@code
   * absent}.
   */
  record Expected(String type, String pattern, boolean absent) {

    static Expected in(String type, String pattern) {
      return new Expected(type, pattern, false);
    }

    static Expected notIn(String type, String pattern) {
      return new Expected(type, pattern, true);
    }
  }

  /** Asserts the compilation succeeded with no warning anywhere, and wrote what the row expects. */
  static void assertCompiledClean(RestatedSignature row, Compilation compilation) {
    assertThat(compilation).succeeded();
    assertThat(compilation).hadWarningCount(0);
    for (Expected expected : row.expected()) {
      String source = generatedSource(compilation, expected.type());
      if (expected.absent()) {
        Assertions.assertThat(source).doesNotContainPattern(expected.pattern());
      } else {
        Assertions.assertThat(source).containsPattern(expected.pattern());
      }
    }
  }

  private static String generatedSource(Compilation compilation, String qualifiedName) {
    Optional<JavaFileObject> file =
        compilation.generatedFile(
            StandardLocation.SOURCE_OUTPUT, qualifiedName.replace('.', '/') + ".java");
    Assertions.assertThat(file).as(qualifiedName).isPresent();
    try {
      return file.get().getCharContent(true).toString();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /** A suppression of one category, before what follows it. */
  static String suppressed(String category, String following) {
    return "@SuppressWarnings\\(\"" + category + "\"\\)\\s+" + following;
  }

  /** A facade method's annotations, suppressing {@code category}, before its signature. */
  static String overrides(String category, String signature) {
    return "@Override\\s+" + suppressed(category, "public " + signature);
  }

  /** A source in the base module's package. */
  static JavaFileObject upstream(String name, String declaration) {
    return JavaFileObjects.forSourceString(
        "com.upstream." + name, "package com.upstream;\n\n" + declaration);
  }
}
