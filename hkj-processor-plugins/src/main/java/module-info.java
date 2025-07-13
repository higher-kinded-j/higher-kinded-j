import org.higherkindedj.optics.processing.generator.ArrayGenerator;
import org.higherkindedj.optics.processing.generator.EitherGenerator;
import org.higherkindedj.optics.processing.generator.ListGenerator;
import org.higherkindedj.optics.processing.generator.MapValueGenerator;
import org.higherkindedj.optics.processing.generator.MaybeGenerator;
import org.higherkindedj.optics.processing.generator.OptionalGenerator;
import org.higherkindedj.optics.processing.generator.SetGenerator;
import org.higherkindedj.optics.processing.generator.TryGenerator;
import org.higherkindedj.optics.processing.generator.ValidatedGenerator;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * Defines the module for the optics generator plugins. This module provides concrete
 * implementations of the TraversableGenerator SPI.
 */
module org.higherkindedj.processor.plugins {
  // This module requires the core library to know about the types
  // for which it provides generators (e.g., List, Optional, Maybe).
  requires org.higherkindedj.core;

  // This module requires the processor to access the SPI
  // (e.g., TraversableGenerator).
  requires org.higherkindedj.processor;
  requires com.palantir.javapoet;
  requires java.compiler;

  // This clause makes the generator implementations available to the
  // ServiceLoader running in the main hkj-processor module.
  provides TraversableGenerator with
      ArrayGenerator,
      EitherGenerator,
      ListGenerator,
      MapValueGenerator,
      MaybeGenerator,
      OptionalGenerator,
      SetGenerator,
      TryGenerator,
      ValidatedGenerator;
}
