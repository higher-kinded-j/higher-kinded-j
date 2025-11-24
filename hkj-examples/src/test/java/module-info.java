/**
 * Contains example usage of the Higher-Kinded-J library. This module is not intended for use as a
 * library dependency.
 */
@org.jspecify.annotations.NullMarked
module org.higherkindedj.tutorial {
  requires org.higherkindedj.core;
  requires org.higherkindedj.annotations;
  requires java.compiler;
  requires org.junit.jupiter.api;
  requires org.assertj.core;

  // Open packages to JUnit for reflection-based test discovery
  opens org.higherkindedj.tutorial.coretypes to
      org.junit.platform.commons;
  opens org.higherkindedj.tutorial.optics to
      org.junit.platform.commons;
  opens org.higherkindedj.tutorial.solutions.coretypes to
      org.junit.platform.commons;
  opens org.higherkindedj.tutorial.solutions.optics to
      org.junit.platform.commons;
}
