// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ServiceLoader;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the repo-wide processor convention (issue #588): every processor reports {@link
 * SourceVersion#latestSupported()} rather than a hardcoded release, so no warnings are raised when
 * running on newer JDKs.
 */
@DisplayName("Processor source-version convention")
class ProcessorSourceVersionTest {

  /** Every processor this module registers, so a new one is held to the convention unasked. */
  static Stream<Arguments> processors() {
    return ServiceLoader.load(Processor.class, LensProcessor.class.getClassLoader()).stream()
        .filter(provider -> provider.type().getName().startsWith("org.higherkindedj."))
        .map(ServiceLoader.Provider::get)
        .map(processor -> Arguments.of(processor.getClass().getSimpleName(), processor));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("processors")
  @DisplayName("reports the latest supported source version")
  void reportsLatestSupported(final String name, final Processor processor) {
    assertThat(processor.getSupportedSourceVersion()).isEqualTo(SourceVersion.latestSupported());
  }
}
