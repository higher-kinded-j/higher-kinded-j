/**
 * Contains example usage of the Higher-Kinded-J library. This module is not intended for use as a
 * library dependency.
 */
module org.higherkindedj.examples {
  // Depends on the main library to use its features
  requires org.higherkindedj.core;
  requires org.higherkindedj.annotations;
  requires java.compiler;

// The examples module does not export any packages for other modules to use
}
