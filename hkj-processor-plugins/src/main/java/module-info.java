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
  // Add support for the Avaje SPI processor (see description in Gradle build file)
  requires static io.avaje.spi;

  // This clause makes the generator implementations available to the
  // ServiceLoader running in the main hkj-processor module.
  provides org.higherkindedj.optics.processing.spi.TraversableGenerator with
      org.higherkindedj.optics.processing.generator.apache.ApacheHashBagGenerator,
      org.higherkindedj.optics.processing.generator.apache.ApacheUnmodifiableListGenerator,
      org.higherkindedj.optics.processing.generator.basejdk.ArrayGenerator,
      org.higherkindedj.optics.processing.generator.basejdk.ListGenerator,
      org.higherkindedj.optics.processing.generator.basejdk.MapValueGenerator,
      org.higherkindedj.optics.processing.generator.basejdk.OptionalGenerator,
      org.higherkindedj.optics.processing.generator.basejdk.SetGenerator,
      org.higherkindedj.optics.processing.generator.eclipse.EclipseImmutableBagGenerator,
      org.higherkindedj.optics.processing.generator.eclipse.EclipseImmutableListGenerator,
      org.higherkindedj.optics.processing.generator.eclipse.EclipseImmutableSetGenerator,
      org.higherkindedj.optics.processing.generator.eclipse.EclipseImmutableSortedSetGenerator,
      org.higherkindedj.optics.processing.generator.eclipse.EclipseMutableBagGenerator,
      org.higherkindedj.optics.processing.generator.eclipse.EclipseMutableListGenerator,
      org.higherkindedj.optics.processing.generator.eclipse.EclipseMutableSetGenerator,
      org.higherkindedj.optics.processing.generator.eclipse.EclipseMutableSortedSetGenerator,
      org.higherkindedj.optics.processing.generator.guava.GuavaImmutableListGenerator,
      org.higherkindedj.optics.processing.generator.guava.GuavaImmutableSetGenerator,
      org.higherkindedj.optics.processing.generator.hkj.EitherGenerator,
      org.higherkindedj.optics.processing.generator.hkj.MaybeGenerator,
      org.higherkindedj.optics.processing.generator.hkj.TryGenerator,
      org.higherkindedj.optics.processing.generator.hkj.ValidatedGenerator,
      org.higherkindedj.optics.processing.generator.vavr.VavrListGenerator,
      org.higherkindedj.optics.processing.generator.vavr.VavrSetGenerator;
}
