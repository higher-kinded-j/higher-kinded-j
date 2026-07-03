// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.lang.model.SourceVersion;
import org.higherkindedj.optics.processing.effect.ComposeEffectsProcessor;
import org.higherkindedj.optics.processing.effect.EffectAlgebraProcessor;
import org.higherkindedj.optics.processing.effect.PathProcessor;
import org.higherkindedj.optics.processing.effect.PathSourceProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the repo-wide processor convention (issue #588): every processor reports {@link
 * SourceVersion#latestSupported()} rather than a hardcoded release, so no warnings are raised when
 * running on newer JDKs.
 */
@DisplayName("Processor source-version convention")
class ProcessorSourceVersionTest {

  static Stream<AbstractProcessor> processors() {
    return Stream.of(
        new LensProcessor(),
        new PrismProcessor(),
        new IsoProcessor(),
        new TraversalProcessor(),
        new GetterProcessor(),
        new SetterProcessor(),
        new FoldProcessor(),
        new FocusProcessor(),
        new ImportOpticsProcessor(),
        new ForComprehensionProcessor(),
        new AccumulatorProcessor(),
        new PathProcessor(),
        new PathSourceProcessor(),
        new EffectAlgebraProcessor(),
        new ComposeEffectsProcessor());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("processors")
  @DisplayName("reports the latest supported source version")
  void reportsLatestSupported(AbstractProcessor processor) {
    assertThat(processor.getSupportedSourceVersion()).isEqualTo(SourceVersion.latestSupported());
  }
}
