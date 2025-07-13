/**
 * Defines the annotation processor for generating optics code. This module is intended for use
 * during compilation and is not a runtime dependency.
 */
module org.higherkindedj.processor {
  uses org.higherkindedj.optics.processing.spi.TraversableGenerator;

  requires org.higherkindedj.annotations;
  requires org.higherkindedj.api;

  // Requires build tools for annotation processing and code generation
  requires java.compiler;
  requires com.palantir.javapoet;
  requires com.google.auto.service;

  // Registers LensProcessor as a service that can be discovered by the compiler
  provides javax.annotation.processing.Processor with
      org.higherkindedj.optics.processing.LensProcessor,
      org.higherkindedj.optics.processing.PrismProcessor,
      org.higherkindedj.optics.processing.TraversalProcessor;

  // It exports the SPI so the plugins module can implement it.
  exports org.higherkindedj.optics.processing.spi;
}
