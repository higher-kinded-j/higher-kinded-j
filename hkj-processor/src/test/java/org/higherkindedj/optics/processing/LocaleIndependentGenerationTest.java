// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.LOCALE)
class LocaleIndependentGenerationTest {
  @Test
  @DisplayName("tuple and for-comprehension sources are identical under ROOT and Turkish locales")
  void generatedSourcesAreLocaleIndependent() throws IOException {
    Locale previous = Locale.getDefault();
    Locale display = Locale.getDefault(Locale.Category.DISPLAY);
    Locale format = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Map<String, String> root = generateUnder(Locale.ROOT);
      Map<String, String> turkish = generateUnder(Locale.forLanguageTag("tr-TR"));
      assertThat(root.keySet()).anyMatch(name -> name.endsWith("/MonadicSteps8.java"));
      assertThat(root.keySet()).anyMatch(name -> name.endsWith("/FilterableSteps8.java"));
      assertThat(root.keySet()).anyMatch(name -> name.endsWith("/EitherPathSteps7.java"));
      assertThat(root.keySet()).anyMatch(name -> name.endsWith("/Tuple9.java"));
      assertThat(turkish.keySet()).isEqualTo(root.keySet());
      root.forEach(
          (name, source) ->
              assertThat(turkish.get(name)).as("source for %s", name).isEqualTo(source));
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, display);
      Locale.setDefault(Locale.Category.FORMAT, format);
    }
  }

  private Map<String, String> generateUnder(Locale locale) throws IOException {
    Locale.setDefault(locale);
    Compilation compilation =
        javac()
            .withProcessors(new ForComprehensionProcessor())
            .compile(
                JavaFileObjects.forSourceString(
                    "org.higherkindedj.hkt.expression.package-info",
                    """
            @GenerateForComprehensions(minArity = 7, maxArity = 9)
            package org.higherkindedj.hkt.expression;
            import org.higherkindedj.optics.annotations.GenerateForComprehensions;
            """));
    assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
    Map<String, String> sources = new TreeMap<>();
    for (JavaFileObject source : compilation.generatedSourceFiles()) {
      sources.put(source.getName(), source.getCharContent(true).toString());
    }
    return sources;
  }
}
